package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
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
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class RotationDetector extends Module {
   private final SettingGroup sgBlocks = this.settings.getDefaultGroup();
   private final SettingGroup sgFilters = this.settings.createGroup("Filters");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final SettingGroup sgPerformance = this.settings.createGroup("Performance");
   private final Setting<Boolean> detectLogs = this.sgBlocks
      .add(new BoolSetting.Builder().name("logs").description("Trees only generate vertical logs.").defaultValue(true).build());
   private final Setting<Boolean> detectBasalt = this.sgBlocks
      .add(new BoolSetting.Builder().name("basalt").description("Basalt deltas/pillars only generate vertical.").defaultValue(true).build());
   private final Setting<Boolean> detectPurpur = this.sgBlocks
      .add(new BoolSetting.Builder().name("purpur-pillars").description("End cities only generate vertical purpur pillars.").defaultValue(true).build());
   private final Setting<Boolean> detectHayBlocks = this.sgBlocks
      .add(new BoolSetting.Builder().name("hay-blocks").description("Village hay bales only generate vertical.").defaultValue(true).build());
   private final Setting<Integer> minY = this.sgFilters
      .add(new IntSetting.Builder().name("min-y").description("Minimum Y level to scan.").defaultValue(-64).sliderRange(-64, 320).build());
   private final Setting<Integer> maxY = this.sgFilters
      .add(new IntSetting.Builder().name("max-y").description("Maximum Y level to scan.").defaultValue(320).sliderRange(-64, 320).build());
   private final Setting<Boolean> ignoreNearAir = this.sgFilters
      .add(new BoolSetting.Builder().name("ignore-near-air").description("Ignore blocks with air above. Filters surface builds.").defaultValue(true).build());
   private final Setting<Integer> airCheckDistance = this.sgFilters
      .add(
         new IntSetting.Builder()
            .name("air-check-distance")
            .description("Blocks above to check for air.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 20)
            .visible(this.ignoreNearAir::get)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(new ColorSetting.Builder().name("side-color").description("Side color for detected blocks.").defaultValue(new SettingColor(255, 0, 0, 40)).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(new ColorSetting.Builder().name("line-color").description("Line color for detected blocks.").defaultValue(new SettingColor(255, 0, 0, 200)).build());
   private final Setting<Integer> renderDistance = this.sgRender
      .add(new IntSetting.Builder().name("render-distance").description("Maximum render distance.").defaultValue(256).min(16).sliderRange(16, 512).build());
   private final Setting<Integer> chunksPerTick = this.sgPerformance
      .add(new IntSetting.Builder().name("chunks-per-tick").description("Chunks to scan per tick.").defaultValue(4).min(1).sliderRange(1, 16).build());
   private final Setting<Integer> maxCacheSize = this.sgPerformance
      .add(new IntSetting.Builder().name("max-cache-size").description("Maximum cached chunks.").defaultValue(4000).min(100).sliderRange(100, 10000).build());
   private final Map<Long, Set<BlockPos>> chunkCache = new ConcurrentHashMap<>();
   private final Set<Long> pendingChunks = Collections.synchronizedSet(new LinkedHashSet<>());
   private int totalDetected = 0;

   public RotationDetector() {
      super(Categories.Hunting, "rotation-detector", "Detects horizontal blocks that only spawn vertical naturally.");
   }

   @Override
   public void onActivate() {
      this.chunkCache.clear();
      this.pendingChunks.clear();
      this.totalDetected = 0;
      if (this.mc.world != null && this.mc.player != null) {
         int viewDist = (Integer)this.mc.options.getViewDistance().getValue();
         ChunkPos playerChunk = new ChunkPos(this.mc.player.getBlockPos());

         for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
               int cx = playerChunk.x + dx;
               int cz = playerChunk.z + dz;
               if (this.mc.world.getChunk(cx, cz, ChunkStatus.FULL, false) != null) {
                  this.pendingChunks.add(ChunkPos.toLong(cx, cz));
               }
            }
         }
      }
   }

   @Override
   public void onDeactivate() {
      this.chunkCache.clear();
      this.pendingChunks.clear();
      this.totalDetected = 0;
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      long key = ChunkPos.toLong(event.chunk().getPos().x, event.chunk().getPos().z);
      this.chunkCache.remove(key);
      this.pendingChunks.add(key);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.world != null && this.mc.player != null && !this.pendingChunks.isEmpty()) {
         int processed = 0;
         Iterator<Long> iter = this.pendingChunks.iterator();

         while (iter.hasNext() && processed < this.chunksPerTick.get()) {
            long key = iter.next();
            iter.remove();
            Chunk chunk = this.mc.world.getChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key), ChunkStatus.FULL, false);
            if (chunk != null) {
               Set<BlockPos> detected = this.scanChunk(chunk);
               if (!detected.isEmpty()) {
                  this.chunkCache.put(key, detected);
               } else {
                  this.chunkCache.remove(key);
               }

               processed++;
            }
         }

         this.enforceCacheLimit();
         this.updateCount();
      }
   }

   private Set<BlockPos> scanChunk(Chunk chunk) {
      Set<BlockPos> detected = new HashSet<>();
      if (this.mc.world == null) {
         return detected;
      } else {
         ChunkPos chunkPos = chunk.getPos();
         int startX = chunkPos.getStartX();
         int startZ = chunkPos.getStartZ();
         int scanMinY = Math.max(this.minY.get(), this.mc.world.getBottomY());
         int scanMaxY = Math.min(this.maxY.get(), this.mc.world.getBottomY() + this.mc.world.getHeight() - 1);
         Mutable pos = new Mutable();

         for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
               for (int y = scanMinY; y <= scanMaxY; y++) {
                  pos.set(startX + x, y, startZ + z);
                  BlockState state = chunk.getBlockState(pos);
                  if (!state.isAir() && this.isHorizontalUnnatural(state) && (!this.ignoreNearAir.get() || !this.hasAirAbove(pos))) {
                     detected.add(pos.toImmutable());
                  }
               }
            }
         }

         return detected;
      }
   }

   private boolean isHorizontalUnnatural(BlockState state) {
      if (!state.contains(Properties.AXIS)) {
         return false;
      } else {
         Axis axis = (Axis)state.get(Properties.AXIS);
         if (axis == Axis.Y) {
            return false;
         } else {
            Block block = state.getBlock();
            if (this.detectLogs.get() && this.isLog(block)) {
               return true;
            } else if (this.detectBasalt.get() && this.isBasalt(block)) {
               return true;
            } else {
               return this.detectPurpur.get() && block == Blocks.PURPUR_PILLAR ? true : this.detectHayBlocks.get() && block == Blocks.HAY_BLOCK;
            }
         }
      }
   }

   private boolean isLog(Block block) {
      return block == Blocks.SPRUCE_LOG
         || block == Blocks.BIRCH_LOG
         || block == Blocks.JUNGLE_LOG
         || block == Blocks.ACACIA_LOG
         || block == Blocks.DARK_OAK_LOG
         || block == Blocks.MANGROVE_LOG
         || block == Blocks.CHERRY_LOG
         || block == Blocks.PALE_OAK_LOG
         || block == Blocks.CRIMSON_STEM
         || block == Blocks.WARPED_STEM;
   }

   private boolean isBasalt(Block block) {
      return block == Blocks.BASALT || block == Blocks.POLISHED_BASALT;
   }

   private boolean hasAirAbove(BlockPos pos) {
      if (this.mc.world == null) {
         return false;
      } else {
         Mutable check = new Mutable();
         int distance = this.airCheckDistance.get();

         for (int dy = 1; dy <= distance; dy++) {
            check.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (this.mc.world.getBlockState(check).isAir()) {
               return true;
            }
         }

         return false;
      }
   }

   private void enforceCacheLimit() {
      while (this.chunkCache.size() > this.maxCacheSize.get()) {
         Iterator<Long> iter = this.chunkCache.keySet().iterator();
         if (iter.hasNext()) {
            iter.next();
            iter.remove();
            continue;
         }
         break;
      }
   }

   private void updateCount() {
      this.totalDetected = 0;

      for (Set<BlockPos> blocks : this.chunkCache.values()) {
         this.totalDetected = this.totalDetected + blocks.size();
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.world != null && this.mc.player != null) {
         double maxDistSq = (double)this.renderDistance.get().intValue() * this.renderDistance.get().intValue();
         double px = this.mc.player.getX();
         double py = this.mc.player.getY();
         double pz = this.mc.player.getZ();

         for (Set<BlockPos> blocks : this.chunkCache.values()) {
            for (BlockPos pos : blocks) {
               double dx = pos.getX() + 0.5 - px;
               double dy = pos.getY() + 0.5 - py;
               double dz = pos.getZ() + 0.5 - pz;
               if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                  event.renderer.box(pos, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
               }
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      return String.valueOf(this.totalDetected);
   }
}
