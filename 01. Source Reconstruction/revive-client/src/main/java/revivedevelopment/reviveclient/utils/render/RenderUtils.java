package revivedevelopment.reviveclient.utils.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.utils.PostInit;
import revivedevelopment.reviveclient.utils.misc.Pool;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class RenderUtils {
   public static Vec3d center;
   public static final Matrix4f projection = new Matrix4f();
   private static final Pool<RenderUtils.RenderBlock> renderBlockPool = new Pool<>(RenderUtils.RenderBlock::new);
   private static final List<RenderUtils.RenderBlock> renderBlocks = new ObjectArrayList();

   private RenderUtils() {
   }

   @PostInit
   public static void init() {
      ReviveClient.EVENT_BUS.subscribe(RenderUtils.class);
   }

   public static void drawItem(
      DrawContext drawContext, ItemStack itemStack, int x, int y, float scale, boolean overlay, String countOverride, boolean disableGuiScale
   ) {
      Matrix3x2fStack matrices = drawContext.getMatrices();
      matrices.pushMatrix();
      if (disableGuiScale) {
         matrices.scale(1.0F / ReviveClient.mc.getWindow().getScaleFactor());
      }

      matrices.scale(scale, scale);
      int scaledX = (int)(x / scale);
      int scaledY = (int)(y / scale);
      drawContext.drawItem(itemStack, scaledX, scaledY);
      if (overlay) {
         drawContext.drawStackOverlay(ReviveClient.mc.textRenderer, itemStack, scaledX, scaledY, countOverride);
      }

      matrices.popMatrix();
   }

   public static void drawItem(DrawContext drawContext, ItemStack itemStack, int x, int y, float scale, boolean overlay) {
      drawItem(drawContext, itemStack, x, y, scale, overlay, null, true);
   }

   public static void updateScreenCenter(Matrix4f projection, Matrix4f view) {
      RenderUtils.projection.set(projection);
      Matrix4f invProjection = new Matrix4f(projection).invert();
      Matrix4f invView = new Matrix4f(view).invert();
      Vector4f center4 = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F).mul(invProjection).mul(invView);
      center4.div(center4.w);
      Vec3d camera = ReviveClient.mc.gameRenderer.getCamera().getCameraPos();
      center = new Vec3d(camera.x + center4.x, camera.y + center4.y, camera.z + center4.z);
   }

   public static void renderTickingBlock(
      BlockPos blockPos, Color sideColor, Color lineColor, ShapeMode shapeMode, int excludeDir, int duration, boolean fade, boolean shrink
   ) {
      renderBlocks.removeIf(next -> {
         if (next.pos.equals(blockPos)) {
            renderBlockPool.free(next);
            return true;
         } else {
            return false;
         }
      });
      renderBlocks.add(renderBlockPool.get().set(blockPos, sideColor, lineColor, shapeMode, excludeDir, duration, fade, shrink));
   }

   @EventHandler
   private static void onTick(TickEvent.Pre event) {
      if (!renderBlocks.isEmpty()) {
         renderBlocks.removeIf(next -> {
            next.tick();
            if (next.ticks <= 0) {
               renderBlockPool.free(next);
               return true;
            } else {
               return false;
            }
         });
      }
   }

   @EventHandler
   private static void onRender(Render3DEvent event) {
      renderBlocks.forEach(block -> block.render(event));
   }

   public static class RenderBlock {
      public Mutable pos = new Mutable();
      public Color sideColor;
      public Color lineColor;
      public ShapeMode shapeMode;
      public int excludeDir;
      public int ticks;
      public int duration;
      public boolean fade;
      public boolean shrink;

      public RenderUtils.RenderBlock set(
         BlockPos blockPos, Color sideColor, Color lineColor, ShapeMode shapeMode, int excludeDir, int duration, boolean fade, boolean shrink
      ) {
         this.pos.set(blockPos);
         this.sideColor = sideColor;
         this.lineColor = lineColor;
         this.shapeMode = shapeMode;
         this.excludeDir = excludeDir;
         this.fade = fade;
         this.shrink = shrink;
         this.ticks = duration;
         this.duration = duration;
         return this;
      }

      public void tick() {
         this.ticks--;
      }

      public void render(Render3DEvent event) {
         int preSideA = this.sideColor.a;
         int preLineA = this.lineColor.a;
         double x1 = this.pos.getX();
         double y1 = this.pos.getY();
         double z1 = this.pos.getZ();
         double x2 = this.pos.getX() + 1;
         double y2 = this.pos.getY() + 1;
         double z2 = this.pos.getZ() + 1;
         double d = (double)(this.ticks - event.tickDelta) / this.duration;
         if (this.fade) {
            this.sideColor.a = (int)(this.sideColor.a * d);
            this.lineColor.a = (int)(this.lineColor.a * d);
         }

         if (this.shrink) {
            x1 += d;
            y1 += d;
            z1 += d;
            x2 -= d;
            y2 -= d;
            z2 -= d;
         }

         event.renderer.box(x1, y1, z1, x2, y2, z2, this.sideColor, this.lineColor, this.shapeMode, this.excludeDir);
         this.sideColor.a = preSideA;
         this.lineColor.a = preLineA;
      }
   }
}
