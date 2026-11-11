package revivedevelopment.reviveclient.systems.modules.hunting;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public final class StashSurfaceMap {
   public static final int WIDTH = 640;
   public static final int HEIGHT = 736;
   public static final int BLANK = 1382686;

   public static StashSurfaceMap.Snapshot capture(int var0, int var1, int var2, StashSurfaceMap.Reader var3, BooleanSupplier var4) {
      if (var2 >= 1 && var2 <= 4 && Math.abs((long)var0) <= 30000000L && Math.abs((long)var1) <= 30000000L) {
         int var5 = Math.floorDiv(var0, 16);
         int var6 = Math.floorDiv(var1, 16);
         int var7 = (var2 * 2 + 1) * 16;
         int var8 = (var5 - var2) * 16;
         int var9 = (var6 - var2) * 16;
         int var10 = 0;
         int[] var11 = new int[var7 * var7];
         int[] var12 = new int[var7 * var7];

         for (int var13 = 0; var13 <= var2; var13++) {
            for (int var14 = -var13; var14 <= var13; var14++) {
               for (int var15 = -var13; var15 <= var13; var15++) {
                  if (Math.max(Math.abs(var15), Math.abs(var14)) == var13) {
                     for (int var16 = 0; var16 < 16; var16++) {
                        if (var4.getAsBoolean()) {
                           return var10 == 0 ? null : new StashSurfaceMap.Snapshot(var0, var1, var8, var9, var7, var10, var11, var12);
                        }

                        for (int var17 = 0; var17 < 16; var17++) {
                           int var18 = (var5 + var15) * 16 + var17;
                           int var19 = (var6 + var14) * 16 + var16;
                           StashSurfaceMap.Column var20 = var3.read(var18, var19);
                           if (var20 != null) {
                              int var21 = (var19 - var9) * var7 + var18 - var8;
                              var11[var21] = 0xFF000000 | var20.rgb;
                              var12[var21] = var20.height;
                              var10++;
                           }
                        }
                     }
                  }
               }
            }
         }

         return var10 == 0 ? null : new StashSurfaceMap.Snapshot(var0, var1, var8, var9, var7, var10, var11, var12);
      } else {
         throw new IllegalArgumentException("Invalid surface map bounds");
      }
   }

   public static BufferedImage render(StashSurfaceMap.Snapshot var0) {
      BufferedImage var1 = new BufferedImage(640, 736, 1);
      Graphics2D var2 = var1.createGraphics();

      try {
         var2.setColor(new Color(2106667));
         var2.fillRect(0, 0, 640, 736);
         var2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         var2.setColor(Color.WHITE);
         var2.setFont(new Font("SansSerif", 1, 20));
         var2.drawString("STASH SURFACE  /  OVERWORLD", 28, 30);
         var2.setFont(new Font("Monospaced", 0, 16));
         var2.drawString("Hit: X " + var0.hitX + "   Z " + var0.hitZ, 28, 55);
         var2.setFont(new Font("SansSerif", 0, 13));
         var2.setColor(new Color(12109773));
         var2.drawString(
            var0.side + " x " + var0.side + " blocks | " + var0.side / 16 + " x " + var0.side / 16 + " chunks | NORTH (-Z) UP | EAST (+X) RIGHT", 28, 78
         );
         int var3 = 576 / var0.side;
         int var4 = var3 * var0.side;
         int var5 = (640 - var4) / 2;
         int var6 = 96;
         var2.setColor(new Color(1382686));
         var2.fillRect(var5, var6, var4, var4);

         for (int var7 = 0; var7 < var0.side; var7++) {
            for (int var8 = 0; var8 < var0.side; var8++) {
               if (var0.known(var8, var7)) {
                  int var9 = var7 * var0.side + var8;
                  int var10 = var0.rgb(var8, var7);
                  double var11 = var7 > 0 && var0.known(var8, var7 - 1)
                     ? 1.0 + Math.max(-0.2, Math.min(0.16, (var0.heights[var9] - var0.heights[var9 - var0.side]) * 0.045))
                     : 1.0;
                  var2.setColor(new Color(shade(var10 >> 16 & 0xFF, var11), shade(var10 >> 8 & 0xFF, var11), shade(var10 & 0xFF, var11)));
                  var2.fillRect(var5 + var8 * var3, var6 + var7 * var3, var3, var3);
               }
            }
         }

         var2.setColor(new Color(255, 255, 255, 38));

         for (int var16 = 0; var16 <= var0.side; var16 += 16) {
            var2.drawLine(var5 + var16 * var3, var6, var5 + var16 * var3, var6 + var4);
            var2.drawLine(var5, var6 + var16 * var3, var5 + var4, var6 + var16 * var3);
         }

         int var17 = (Math.floorDiv(var0.hitX, 16) * 16 - var0.minX) * var3;
         int var18 = (Math.floorDiv(var0.hitZ, 16) * 16 - var0.minZ) * var3;
         var2.setColor(new Color(16731573));
         var2.setStroke(new BasicStroke(2.0F));
         var2.drawRect(var5 + var17, var6 + var18, 16 * var3, 16 * var3);
         int var19 = var5 + (var0.hitX - var0.minX) * var3 + var3 / 2;
         int var20 = var6 + (var0.hitZ - var0.minZ) * var3 + var3 / 2;
         var2.setColor(Color.BLACK);
         var2.setStroke(new BasicStroke(5.0F));
         var2.drawLine(var19 - 8, var20, var19 + 8, var20);
         var2.drawLine(var19, var20 - 8, var19, var20 + 8);
         var2.setColor(Color.WHITE);
         var2.setStroke(new BasicStroke(2.0F));
         var2.drawLine(var19 - 8, var20, var19 + 8, var20);
         var2.drawLine(var19, var20 - 8, var19, var20 + 8);
         var2.setColor(new Color(14213863));
         var2.setFont(new Font("SansSerif", 0, 13));
         var2.drawString("Pink: hit chunk | Surface only | Blank: unloaded or not sampled", 28, 699);
         var2.drawString(
            "Captured "
               + var0.captured.toString().replaceFirst("\\.\\d+Z$", "Z")
               + " | "
               + Math.round(var0.known * 100.0 / (var0.side * var0.side))
               + "% sampled",
            28,
            721
         );
      } finally {
         var2.dispose();
      }

      return var1;
   }

   private static int shade(int var0, double var1) {
      return Math.min(255, Math.max(0, (int)(var0 * var1)));
   }

   public static byte[] png(StashSurfaceMap.Snapshot var0) throws IOException {
      ByteArrayOutputStream var1 = new ByteArrayOutputStream();
      Iterator var2 = ImageIO.getImageWritersByFormatName("png");
      if (!var2.hasNext()) {
         throw new IOException("PNG encoder unavailable");
      } else {
         ImageWriter var3 = (ImageWriter)var2.next();

         try (MemoryCacheImageOutputStream var4 = new MemoryCacheImageOutputStream(var1)) {
            var3.setOutput(var4);
            var3.write(render(var0));
            var4.flush();
         } finally {
            var3.dispose();
         }

         return var1.toByteArray();
      }
   }

   public record Column(int rgb, int height) {
   }

   @FunctionalInterface
   public interface Reader {
      StashSurfaceMap.Column read(int var1, int var2);
   }

   public static final class Snapshot {
      public final int hitX;
      public final int hitZ;
      public final int minX;
      public final int minZ;
      public final int side;
      public final int known;
      public final Instant captured;
      private final int[] colors;
      private final int[] heights;

      private Snapshot(int var1, int var2, int var3, int var4, int var5, int var6, int[] var7, int[] var8) {
         this.hitX = var1;
         this.hitZ = var2;
         this.minX = var3;
         this.minZ = var4;
         this.side = var5;
         this.known = var6;
         this.colors = var7;
         this.heights = var8;
         this.captured = Instant.now();
      }

      public boolean known(int var1, int var2) {
         return this.colors[var2 * this.side + var1] != 0;
      }

      public int rgb(int var1, int var2) {
         return this.colors[var2 * this.side + var1] & 16777215;
      }
   }
}
