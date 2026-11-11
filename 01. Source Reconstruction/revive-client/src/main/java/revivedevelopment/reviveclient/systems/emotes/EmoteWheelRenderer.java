package revivedevelopment.reviveclient.systems.emotes;

import net.minecraft.client.gui.DrawContext;
import revivedevelopment.reviveclient.ReviveClient;

public final class EmoteWheelRenderer {
   private static final double EDGE_EPSILON = 1.0E-9;

   private EmoteWheelRenderer() {
   }

   public static void drawCenteredLabel(DrawContext context, String label, int x, int y, float scale, int color) {
      int textWidth = ReviveClient.mc.textRenderer.getWidth(label);
      context.getMatrices().pushMatrix();
      context.getMatrices().translate(x, y);
      context.getMatrices().scale(scale, scale);
      context.drawText(ReviveClient.mc.textRenderer, label, -textWidth / 2, 0, color, false);
      context.getMatrices().popMatrix();
   }

   public static void fillCircle(DrawContext context, int centerX, int centerY, int radius, int color) {
      int radiusSq = radius * radius;
      int runX = 0;
      int runTop = 0;

      for (int y = -radius; y <= radius; y++) {
         int xMax = (int)Math.sqrt(radiusSq - y * y);
         if (xMax != runX) {
            if (runX > 0) {
               context.fill(centerX - runX, centerY + runTop, centerX + runX + 1, centerY + y, color);
            }

            runX = xMax;
            runTop = y;
         }
      }

      if (runX > 0) {
         context.fill(centerX - runX, centerY + runTop, centerX + runX + 1, centerY + radius + 1, color);
      }
   }

   public static void fillRing(DrawContext context, int centerX, int centerY, int outerRadius, int innerRadius, int color) {
      int outerSq = outerRadius * outerRadius;
      int innerSq = innerRadius * innerRadius;
      int runOuter = 0;
      int runInner = 0;
      int runTop = 0;

      for (int y = -outerRadius; y <= outerRadius; y++) {
         int ySq = y * y;
         int xOuter = (int)Math.sqrt(outerSq - ySq);
         int xInner = ySq < innerSq ? (int)Math.sqrt(innerSq - ySq) : 0;
         if (xOuter != runOuter || xInner != runInner) {
            fillRingRun(context, centerX, centerY, runOuter, runInner, runTop, y, color);
            runOuter = xOuter;
            runInner = xInner;
            runTop = y;
         }
      }

      fillRingRun(context, centerX, centerY, runOuter, runInner, runTop, outerRadius + 1, color);
   }

   private static void fillRingRun(DrawContext context, int centerX, int centerY, int xOuter, int xInner, int top, int bottom, int color) {
      if (xOuter > 0) {
         if (xInner <= 0) {
            context.fill(centerX - xOuter, centerY + top, centerX + xOuter + 1, centerY + bottom, color);
         } else {
            context.fill(centerX - xOuter, centerY + top, centerX - xInner, centerY + bottom, color);
            context.fill(centerX + xInner + 1, centerY + top, centerX + xOuter + 1, centerY + bottom, color);
         }
      }
   }

   public static void fillSlice(DrawContext context, int centerX, int centerY, int radius, int innerRadius, int index, int slots, int color) {
      double sliceAngle = (Math.PI * 2) / slots;
      double startAngle = index * sliceAngle - (Math.PI / 2) - sliceAngle / 2.0;
      double endAngle = startAngle + sliceAngle;
      double startNx = -Math.sin(startAngle);
      double startNy = Math.cos(startAngle);
      double endNx = Math.sin(endAngle);
      double endNy = -Math.cos(endAngle);
      int radiusSq = radius * radius;
      int innerSq = innerRadius * innerRadius;

      for (int y = -radius; y <= radius; y++) {
         int ySq = y * y;
         int xMax = (int)Math.sqrt(radiusSq - ySq);
         if (xMax > 0) {
            double lo = -xMax;
            double hi = xMax;
            if (startNx > 1.0E-9) {
               lo = Math.max(lo, -startNy * y / startNx);
            } else if (startNx < -1.0E-9) {
               hi = Math.min(hi, -startNy * y / startNx);
            } else if (startNy * y < 0.0) {
               continue;
            }

            if (endNx > 1.0E-9) {
               lo = Math.max(lo, -endNy * y / endNx);
            } else if (endNx < -1.0E-9) {
               hi = Math.min(hi, -endNy * y / endNx);
            } else if (endNy * y < 0.0) {
               continue;
            }

            int xa = Math.max(-xMax, (int)Math.ceil(lo - 1.0E-9));
            int xb = Math.min(xMax, (int)Math.floor(hi + 1.0E-9));
            if (xa <= xb) {
               int xInner = ySq < innerSq ? (int)Math.sqrt(innerSq - ySq) : 0;
               if (xInner > 0 && xa <= xInner && xb >= -xInner) {
                  if (xa <= -xInner - 1) {
                     context.fill(centerX + xa, centerY + y, centerX - xInner, centerY + y + 1, color);
                  }

                  if (xb >= xInner + 1) {
                     context.fill(centerX + xInner + 1, centerY + y, centerX + xb + 1, centerY + y + 1, color);
                  }
               } else {
                  context.fill(centerX + xa, centerY + y, centerX + xb + 1, centerY + y + 1, color);
               }
            }
         }
      }
   }
}
