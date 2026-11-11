package revivedevelopment.reviveclient.systems.hud.elements;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixin.WorldRendererAccessor;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;

public class HoleHud extends HudElement {
   public static final HudElementInfo<HoleHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "hole", "Displays information about the hole you are standing in.", HoleHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   public final Setting<List<Block>> safe = this.sgGeneral
      .add(
         new BlockListSetting.Builder()
            .name("safe-blocks")
            .description("Which blocks to consider safe.")
            .defaultValue(Blocks.OBSIDIAN, Blocks.BEDROCK, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK)
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(2.0)
            .onChanged(aDouble -> this.calculateSize())
            .min(1.0)
            .sliderRange(1.0, 5.0)
            .build()
      );
   private final Setting<Integer> border = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("border")
            .description("How much space to add around the element.")
            .defaultValue(0)
            .onChanged(integer -> this.calculateSize())
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());
   private static final int BG_COLOR = 1694439705;
   private static final int OL_COLOR = -59111;

   public HoleHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   private void calculateSize() {
      this.setSize(48.0 * this.scale.get(), 48.0 * this.scale.get());
   }

   @Override
   public void render(HudRenderer renderer) {
      renderer.post(() -> {
         double x = this.x + this.border.get();
         double y = this.y + this.border.get();
         this.drawBlock(renderer, this.get(HoleHud.Facing.Left), x, y + 16.0 * this.scale.get());
         this.drawBlock(renderer, this.get(HoleHud.Facing.Front), x + 16.0 * this.scale.get(), y);
         this.drawBlock(renderer, this.get(HoleHud.Facing.Right), x + 32.0 * this.scale.get(), y + 16.0 * this.scale.get());
         this.drawBlock(renderer, this.get(HoleHud.Facing.Back), x + 16.0 * this.scale.get(), y + 32.0 * this.scale.get());
      });
      if (this.background.get()) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
      }
   }

   private Direction get(HoleHud.Facing dir) {
      if (this.isInEditor()) {
         return Direction.DOWN;
      } else {
         float yaw = MathHelper.wrapDegrees(ReviveClient.mc.player.getYaw() + dir.offset);
         return Direction.fromHorizontalDegrees(yaw).getOpposite();
      }
   }

   private void drawBlock(HudRenderer renderer, Direction dir, double x, double y) {
      Block block = dir == Direction.DOWN ? Blocks.OBSIDIAN : ReviveClient.mc.world.getBlockState(ReviveClient.mc.player.getBlockPos().offset(dir)).getBlock();
      if (this.safe.get().contains(block)) {
         renderer.item(block.asItem().getDefaultStack(), (int)x, (int)y, this.scale.get().floatValue(), false);
         if (dir != Direction.DOWN) {
            ((WorldRendererAccessor)ReviveClient.mc.worldRenderer).getBlockBreakingInfos().values().forEach(info -> {
               if (info.getPos().equals(ReviveClient.mc.player.getBlockPos().offset(dir))) {
                  this.renderBreaking(renderer, x, y, info.getStage() / 9.0F);
               }
            });
         }
      }
   }

   private void renderBreaking(HudRenderer renderer, double x, double y, double percent) {
      DrawContext ctx = renderer.drawContext;
      if (ctx != null) {
         float sf = ReviveClient.mc.getWindow().getScaleFactor();
         Matrix3x2fStack matrices = ctx.getMatrices();
         matrices.pushMatrix();
         matrices.scale(1.0F / sf, 1.0F / sf);
         double s = this.scale.get();
         fill(ctx, x, y, 16.0 * percent * s, 16.0 * s, 1694439705);
         fill(ctx, x, y, 16.0 * s, s, -59111);
         fill(ctx, x, y + 15.0 * s, 16.0 * s, s, -59111);
         fill(ctx, x, y, s, 16.0 * s, -59111);
         fill(ctx, x + 15.0 * s, y, s, 16.0 * s, -59111);
         matrices.popMatrix();
      }
   }

   private static void fill(DrawContext ctx, double x, double y, double w, double h, int color) {
      ctx.fill((int)x, (int)y, (int)(x + w), (int)(y + h), color);
   }

   private static enum Facing {
      Left(-90),
      Right(90),
      Front(0),
      Back(180);

      public final int offset;

      private Facing(int offset) {
         this.offset = offset;
      }
   }
}
