package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.ChunkDataEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class LavaColumns extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> minHeight = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("min-height")
            .description("Minimum flowing lava stack height to highlight.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Integer> greenAtHeight = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("green-at-height")
            .description("Column height at which the highlight is fully green. Below min-height is red.")
            .defaultValue(12)
            .min(2)
            .sliderRange(2, 40)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("Which parts of the box to draw."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Integer> fillAlpha = this.sgRender
      .add(new IntSetting.Builder().name("fill-alpha").description("Opacity of the filled faces.").defaultValue(35).min(0).sliderRange(0, 255).build());
   private final Setting<Integer> outlineAlpha = this.sgRender
      .add(new IntSetting.Builder().name("outline-alpha").description("Opacity of the outline edges.").defaultValue(210).min(0).sliderRange(0, 255).build());
   private final Map<Long, List<int[]>> cache = new ConcurrentHashMap<>();
   private String lastDimension = "";
   private final Color sideColor = new Color();
   private final Color outlineColor = new Color();

   public LavaColumns() {
      super(Categories.Hunting, "lava-columns", "Highlights flowing lava columns in the Nether. Green = tall, red = short/low.");
   }

   @Override
   public void onDeactivate() {
      this.cache.clear();
      this.lastDimension = "";
   }

   private boolean inNether() {
      return this.mc.world != null && this.mc.world.getRegistryKey().equals(World.NETHER);
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.world != null && this.mc.player != null) {
         String dim = this.mc.world.getRegistryKey().getValue().toString();
         if (!dim.equals(this.lastDimension)) {
            this.cache.clear();
            this.lastDimension = dim;
         }

         this.cache.keySet().removeIf(key -> {
            ChunkPos cp = new ChunkPos(key);
            return !this.mc.world.getChunkManager().isChunkLoaded(cp.x, cp.z);
         });
      }
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      if (this.inNether()) {
         this.scanChunk(event.chunk());
      }
   }

   private void scanChunk(WorldChunk chunk) {
      long key = ChunkPos.toLong(chunk.getPos().x, chunk.getPos().z);
      int baseX = chunk.getPos().getStartX();
      int baseZ = chunk.getPos().getStartZ();
      int bottomY = this.mc.world.getBottomY();
      int topY = this.mc.world.getBottomY() + this.mc.world.getHeight();
      List<int[]> found = new ArrayList<>();
      Mutable pos = new Mutable();

      for (int lx = 0; lx < 16; lx++) {
         for (int lz = 0; lz < 16; lz++) {
            int wx = baseX + lx;
            int wz = baseZ + lz;
            int y = bottomY;

            while (y < topY) {
               pos.set(wx, y, wz);
               if (isFlowingLava(chunk, pos)) {
                  int startY = y;

                  do {
                     pos.set(wx, ++y, wz);
                  } while (y < topY && isFlowingLava(chunk, pos));

                  int height = y - startY;
                  if (height >= this.minHeight.get()) {
                     found.add(new int[]{wx, startY, wz, height});
                  }
               } else {
                  y++;
               }
            }
         }
      }

      this.cache.put(key, found);
   }

   private static boolean isFlowingLava(WorldChunk chunk, BlockPos pos) {
      return chunk.getFluidState(pos).getFluid() == Fluids.FLOWING_LAVA;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.inNether()) {
         int minH = this.minHeight.get();
         int maxH = this.greenAtHeight.get();
         float heightRange = Math.max(1, maxH - minH);

         for (List<int[]> columns : this.cache.values()) {
            for (int[] col : columns) {
               int wx = col[0];
               int minY = col[1];
               int wz = col[2];
               int height = col[3];
               float t = Math.max(0.0F, Math.min(1.0F, (height - minH) / heightRange));
               int r = Math.round(255.0F * (1.0F - t));
               int g = Math.round(255.0F * t);
               this.sideColor.set(r, g, 0, this.fillAlpha.get());
               this.outlineColor.set(r, g, 0, this.outlineAlpha.get());
               event.renderer.box(wx, minY, wz, wx + 1, minY + height, wz + 1, this.sideColor, this.outlineColor, this.shapeMode.get(), 0);
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      int total = 0;

      for (List<int[]> v : this.cache.values()) {
         total += v.size();
      }

      return total > 0 ? total + " columns" : null;
   }
}
