package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.BlockUpdateEvent;
import revivedevelopment.reviveclient.events.world.ChunkDataEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class PortalESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final Setting<Boolean> netherPortals = this.sgGeneral
      .add(new BoolSetting.Builder().name("nether-portals").description("Highlight nether portal blocks.").defaultValue(true).build());
   private final Setting<Boolean> endPortals = this.sgGeneral
      .add(new BoolSetting.Builder().name("end-portals").description("Highlight end portal blocks.").defaultValue(true).build());
   private final Setting<Boolean> endGateways = this.sgGeneral
      .add(new BoolSetting.Builder().name("end-gateways").description("Highlight end gateway blocks.").defaultValue(true).build());
   private final Setting<Boolean> endPortalFrames = this.sgGeneral
      .add(new BoolSetting.Builder().name("end-portal-frames").description("Highlight end portal frame blocks.").defaultValue(false).build());
   private final Setting<Integer> renderDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("render-distance")
            .description("Maximum distance to render portals, in blocks.")
            .defaultValue(256)
            .sliderRange(16, 512)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the boxes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Integer> fillOpacity = this.sgGeneral
      .add(
         new IntSetting.Builder().name("fill-opacity").description("The opacity of the box sides.").defaultValue(50).range(0, 255).sliderRange(0, 255).build()
      );
   private final Setting<Boolean> tracers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("tracers")
            .description("Draws a tracer line to every portal, matching its highlight color.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> tracerOpacity = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("tracer-opacity")
            .description("The opacity of the tracer lines.")
            .defaultValue(255)
            .range(0, 255)
            .sliderRange(0, 255)
            .visible(this.tracers::get)
            .build()
      );
   private final Setting<SettingColor> netherPortalColor = this.sgColors
      .add(new ColorSetting.Builder().name("nether-portal").defaultValue(new SettingColor(170, 0, 255)).build());
   private final Setting<SettingColor> endPortalColor = this.sgColors
      .add(new ColorSetting.Builder().name("end-portal").defaultValue(new SettingColor(0, 255, 180)).build());
   private final Setting<SettingColor> endGatewayColor = this.sgColors
      .add(new ColorSetting.Builder().name("end-gateway").defaultValue(new SettingColor(255, 0, 200)).build());
   private final Setting<SettingColor> endPortalFrameColor = this.sgColors
      .add(new ColorSetting.Builder().name("end-portal-frame").defaultValue(new SettingColor(0, 200, 100)).build());
   private final Map<Long, List<PortalESP.PortalGroup>> chunkCache = new ConcurrentHashMap<>();
   private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
   private RegistryKey<World> lastDimension;
   private int tick;
   private final Color side = new Color();
   private final Color line = new Color();
   private final Color tracer = new Color();
   private static final int[][] OFFSETS = new int[][]{{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

   public PortalESP() {
      super(Categories.Render, "portal-esp", "Highlights nether portals, end portals, end gateways and end portal frames.");
   }

   @Override
   public void onActivate() {
      this.chunkCache.clear();
      this.pendingChunks.clear();
      this.lastDimension = this.mc.world != null ? this.mc.world.getRegistryKey() : null;
      this.tick = 0;
      this.queueLoadedChunks();
   }

   @Override
   public void onDeactivate() {
      this.chunkCache.clear();
      this.pendingChunks.clear();
   }

   private void queueLoadedChunks() {
      if (this.mc.world != null && this.mc.player != null) {
         int px = this.mc.player.getChunkPos().x;
         int pz = this.mc.player.getChunkPos().z;
         int radius = (Integer)this.mc.options.getViewDistance().getValue() + 1;

         for (int cx = px - radius; cx <= px + radius; cx++) {
            for (int cz = pz - radius; cz <= pz + radius; cz++) {
               this.pendingChunks.add(ChunkPos.toLong(cx, cz));
            }
         }
      }
   }

   private boolean isPortalBlock(Block block) {
      return block == Blocks.NETHER_PORTAL || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY || block == Blocks.END_PORTAL_FRAME;
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.chunkCache.clear();
      this.pendingChunks.clear();
      this.lastDimension = null;
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      this.pendingChunks.add(event.chunk().getPos().toLong());
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if (this.isPortalBlock(event.oldState.getBlock()) || this.isPortalBlock(event.newState.getBlock())) {
         this.pendingChunks.add(ChunkPos.toLong(event.pos.getX() >> 4, event.pos.getZ() >> 4));
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.world != null && this.mc.player != null) {
         RegistryKey<World> dimension = this.mc.world.getRegistryKey();
         if (dimension != this.lastDimension) {
            this.lastDimension = dimension;
            this.chunkCache.clear();
            this.pendingChunks.clear();
            this.queueLoadedChunks();
         }

         this.processPendingChunks();
         this.tick++;
         if (this.tick >= 100) {
            this.tick = 0;
            this.evictDistantChunks();
         }
      }
   }

   private void processPendingChunks() {
      if (!this.pendingChunks.isEmpty()) {
         int processed = 0;

         for (Iterator<Long> it = this.pendingChunks.iterator(); it.hasNext() && processed < 8; processed++) {
            long key = it.next();
            it.remove();
            this.scanChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
         }
      }
   }

   private void scanChunk(int cx, int cz) {
      if (this.mc.world != null) {
         Chunk chunk;
         try {
            chunk = this.mc.world.getChunk(cx, cz);
         } catch (Exception var17) {
            return;
         }

         if (chunk != null) {
            long key = ChunkPos.toLong(cx, cz);
            int bottomY = this.mc.world.getBottomY();
            ChunkSection[] sections = chunk.getSectionArray();
            Set<Long> positions = new HashSet<>();

            for (int i = 0; i < sections.length; i++) {
               ChunkSection section = sections[i];
               if (section != null && !section.isEmpty()) {
                  int baseY = bottomY + (i << 4);

                  for (int lx = 0; lx < 16; lx++) {
                     for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                           Block block = section.getBlockState(lx, ly, lz).getBlock();
                           PortalESP.PortalType type = this.typeOf(block);
                           if (type != null) {
                              positions.add(packPos(lx, baseY + ly, lz, type));
                           }
                        }
                     }
                  }
               }
            }

            if (positions.isEmpty()) {
               this.chunkCache.remove(key);
            } else {
               this.chunkCache.put(key, this.clusterPortals(positions, cx << 4, cz << 4));
            }
         }
      }
   }

   private PortalESP.PortalType typeOf(Block block) {
      if (block == Blocks.NETHER_PORTAL) {
         return PortalESP.PortalType.NetherPortal;
      } else if (block == Blocks.END_PORTAL) {
         return PortalESP.PortalType.EndPortal;
      } else if (block == Blocks.END_GATEWAY) {
         return PortalESP.PortalType.EndGateway;
      } else {
         return block == Blocks.END_PORTAL_FRAME ? PortalESP.PortalType.EndPortalFrame : null;
      }
   }

   private static long packPos(int lx, int y, int lz, PortalESP.PortalType type) {
      return (long)type.ordinal() << 20 | (long)(y + 2048) << 8 | (long)lx << 4 | lz;
   }

   private static int unpackLX(long packed) {
      return (int)(packed >> 4) & 15;
   }

   private static int unpackY(long packed) {
      return ((int)(packed >> 8) & 4095) - 2048;
   }

   private static int unpackLZ(long packed) {
      return (int)packed & 15;
   }

   private static PortalESP.PortalType unpackType(long packed) {
      return PortalESP.PortalType.VALUES[(int)(packed >> 20) & 3];
   }

   private List<PortalESP.PortalGroup> clusterPortals(Set<Long> positions, int baseX, int baseZ) {
      List<PortalESP.PortalGroup> groups = new ArrayList<>();
      Set<Long> visited = new HashSet<>();

      for (long start : positions) {
         if (visited.add(start)) {
            PortalESP.PortalType type = unpackType(start);
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            Queue<Long> queue = new ArrayDeque<>();
            queue.add(start);

            while (!queue.isEmpty()) {
               long current = queue.poll();
               int lx = unpackLX(current);
               int y = unpackY(current);
               int lz = unpackLZ(current);
               minX = Math.min(minX, lx);
               maxX = Math.max(maxX, lx);
               minY = Math.min(minY, y);
               maxY = Math.max(maxY, y);
               minZ = Math.min(minZ, lz);
               maxZ = Math.max(maxZ, lz);

               for (int[] offset : OFFSETS) {
                  int nx = lx + offset[0];
                  int nz = lz + offset[2];
                  if (nx >= 0 && nx <= 15 && nz >= 0 && nz <= 15) {
                     long neighbor = packPos(nx, y + offset[1], nz, type);
                     if (positions.contains(neighbor) && visited.add(neighbor)) {
                        queue.add(neighbor);
                     }
                  }
               }
            }

            groups.add(new PortalESP.PortalGroup(type, baseX + minX, minY, baseZ + minZ, baseX + maxX + 1, maxY + 1, baseZ + maxZ + 1));
         }
      }

      return groups;
   }

   private boolean isTypeEnabled(PortalESP.PortalType type) {
      return switch (type) {
         case NetherPortal -> this.netherPortals.get();
         case EndPortal -> this.endPortals.get();
         case EndGateway -> this.endGateways.get();
         case EndPortalFrame -> this.endPortalFrames.get();
      };
   }

   private SettingColor colorOf(PortalESP.PortalType type) {
      return switch (type) {
         case NetherPortal -> (SettingColor)this.netherPortalColor.get();
         case EndPortal -> (SettingColor)this.endPortalColor.get();
         case EndGateway -> (SettingColor)this.endGatewayColor.get();
         case EndPortalFrame -> (SettingColor)this.endPortalFrameColor.get();
      };
   }

   private void evictDistantChunks() {
      if (this.mc.player != null) {
         int px = this.mc.player.getChunkPos().x;
         int pz = this.mc.player.getChunkPos().z;
         int dist = Math.max(this.renderDistance.get() / 16 + 2, (Integer)this.mc.options.getViewDistance().getValue() + 2);
         int distSq = dist * dist;
         this.chunkCache.keySet().removeIf(key -> {
            int dx = ChunkPos.getPackedX(key) - px;
            int dz = ChunkPos.getPackedZ(key) - pz;
            return dx * dx + dz * dz > distSq;
         });
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.world != null && this.mc.player != null) {
         Vec3d playerPos = this.mc.player.getEyePos();
         double rdistSq = (double)this.renderDistance.get().intValue() * this.renderDistance.get().intValue();
         ShapeMode shape = this.shapeMode.get();

         for (List<PortalESP.PortalGroup> groups : this.chunkCache.values()) {
            for (PortalESP.PortalGroup group : groups) {
               if (this.isTypeEnabled(group.type())) {
                  double centerX = (group.minX() + group.maxX()) / 2.0;
                  double centerY = (group.minY() + group.maxY()) / 2.0;
                  double centerZ = (group.minZ() + group.maxZ()) / 2.0;
                  if (!(playerPos.squaredDistanceTo(centerX, centerY, centerZ) > rdistSq)) {
                     SettingColor base = this.colorOf(group.type());
                     this.line.set((Color)base);
                     this.side.set((Color)base);
                     this.side.a = shape.sides() ? this.fillOpacity.get() : 0;
                     event.renderer.box(group.minX(), group.minY(), group.minZ(), group.maxX(), group.maxY(), group.maxZ(), this.side, this.line, shape, 0);
                     if (this.tracers.get()) {
                        this.tracer.set((Color)base);
                        this.tracer.a = this.tracerOpacity.get();
                        event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, centerX, centerY, centerZ, this.tracer);
                     }
                  }
               }
            }
         }
      }
   }

   private record PortalGroup(PortalESP.PortalType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
   }

   public static enum PortalType {
      NetherPortal,
      EndPortal,
      EndGateway,
      EndPortalFrame;

      static final PortalESP.PortalType[] VALUES = values();
   }
}
