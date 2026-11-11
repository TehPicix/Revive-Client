package revivedevelopment.reviveclient.gui.newgui.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public class RenderUtils {
   private static float getGuiScale() {
      return MinecraftClient.getInstance().getWindow().getScaleFactor();
   }

   public static void drawThinHLine(DrawContext context, int x, int y, int length, int color) {
      float scale = getGuiScale();
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(1.0F / scale, 1.0F / scale);
      int sx = (int)(x * scale);
      int sy = (int)(y * scale);
      int sw = (int)(length * scale);
      context.fill(sx, sy, sx + sw, sy + 1, color);
      matrices.popMatrix();
   }

   public static void drawThinVLine(DrawContext context, int x, int y, int length, int color) {
      float scale = getGuiScale();
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(1.0F / scale, 1.0F / scale);
      int sx = (int)(x * scale);
      int sy = (int)(y * scale);
      int sh = (int)(length * scale);
      context.fill(sx, sy, sx + 1, sy + sh, color);
      matrices.popMatrix();
   }

   public static void drawThinOutline(DrawContext context, int x, int y, int w, int h, int color) {
      float scale = getGuiScale();
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(1.0F / scale, 1.0F / scale);
      int sx = (int)(x * scale);
      int sy = (int)(y * scale);
      int sw = (int)(w * scale);
      int sh = (int)(h * scale);
      context.fill(sx, sy, sx + sw, sy + 1, color);
      context.fill(sx, sy + sh - 1, sx + sw, sy + sh, color);
      context.fill(sx, sy, sx + 1, sy + sh, color);
      context.fill(sx + sw - 1, sy, sx + sw, sy + sh, color);
      matrices.popMatrix();
   }

   public static void drawThickOutline(DrawContext context, int x, int y, int w, int h, int thicknessPx, int color) {
      float scale = getGuiScale();
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(1.0F / scale, 1.0F / scale);
      int sx = (int)(x * scale);
      int sy = (int)(y * scale);
      int sw = (int)(w * scale);
      int sh = (int)(h * scale);
      int t = Math.max(1, thicknessPx);
      context.fill(sx, sy, sx + sw, sy + t, color);
      context.fill(sx, sy + sh - t, sx + sw, sy + sh, color);
      context.fill(sx, sy, sx + t, sy + sh, color);
      context.fill(sx + sw - t, sy, sx + sw, sy + sh, color);
      matrices.popMatrix();
   }

   public static void fillNative(DrawContext context, int x, int y, int w, int h, int color) {
      float scale = getGuiScale();
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(1.0F / scale, 1.0F / scale);
      int sx = (int)(x * scale);
      int sy = (int)(y * scale);
      int sw = (int)(w * scale);
      int sh = (int)(h * scale);
      context.fill(sx, sy, sx + sw, sy + sh, color);
      matrices.popMatrix();
   }

   public static void fillNativeHInset(DrawContext context, int x, int y, int w, int h, int sideInsetPx, int color) {
      float scale = getGuiScale();
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(1.0F / scale, 1.0F / scale);
      int sx = (int)(x * scale) + sideInsetPx;
      int sy = (int)(y * scale);
      int sw = (int)(w * scale) - 2 * sideInsetPx;
      int sh = (int)(h * scale);
      if (sw > 0 && sh > 0) {
         context.fill(sx, sy, sx + sw, sy + sh, color);
      }

      matrices.popMatrix();
   }

   public static void drawRoundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
      context.fill(x + radius, y, x + width - radius, y + height, color);
      context.fill(x, y + radius, x + radius, y + height - radius, color);
      context.fill(x + width - radius, y + radius, x + width, y + height - radius, color);
      drawCorner(context, x, y, radius, color, true, true);
      drawCorner(context, x + width - radius, y, radius, color, false, true);
      drawCorner(context, x, y + height - radius, radius, color, true, false);
      drawCorner(context, x + width - radius, y + height - radius, radius, color, false, false);
   }

   private static void drawCorner(DrawContext context, int x, int y, int radius, int color, boolean left, boolean top) {
      for (int cx = 0; cx < radius; cx++) {
         for (int cy = 0; cy < radius; cy++) {
            double dx = left ? radius - cx - 0.5 : cx + 0.5;
            double dy = top ? radius - cy - 0.5 : cy + 0.5;
            if (dx * dx + dy * dy <= (double)radius * radius) {
               context.fill(x + cx, y + cy, x + cx + 1, y + cy + 1, color);
            }
         }
      }
   }

   public static void drawHorizontalLine(DrawContext context, int x, int y, int width, int color) {
      context.fill(x, y, x + width, y + 1, color);
   }

   public static void drawVerticalGradient(DrawContext context, int x, int y, int width, int height, int colorTop, int colorBottom) {
      context.fillGradient(x, y, x + width, y + height, colorTop, colorBottom);
   }

   public static void drawOutlineRect(DrawContext context, int x, int y, int width, int height, int color) {
      context.fill(x, y, x + width, y + 1, color);
      context.fill(x, y + height - 1, x + width, y + height, color);
      context.fill(x, y, x + 1, y + height, color);
      context.fill(x + width - 1, y, x + width, y + height, color);
   }
}
