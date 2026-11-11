package revivedevelopment.reviveclient.systems.modules.hunting;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public final class StashScene {
   public static final int SIDE = 128;
   public static final int WIDTH = 896;
   public static final int HEIGHT = 704;
   public static final int ROOF = 1;
   public static final int STAIRS = 2;
   public static final int BIRCH = 4;
   public static final int COBBLE = 8;
   public static final int GLASS = 16;

   public static boolean likelyMansion(StashScene.Snapshot var0) {
      if (!var0.complete()) {
         return false;
      } else {
         int var1 = 0;
         int var2 = 0;
         int var3 = 0;
         int var4 = 0;
         int var5 = 0;
         int var6 = 0;
         int var7 = 128;
         int var8 = -1;
         int var9 = 128;
         int var10 = -1;

         for (int var11 = 32; var11 < 96; var11++) {
            for (int var12 = 32; var12 < 96; var12++) {
               int var13 = var11 * 128 + var12;
               int var14 = var0.evidence()[var13];
               if (var0.colors()[var13] != 0) {
                  var6++;
               }

               if ((var14 & 1) != 0) {
                  var1++;
                  var7 = Math.min(var7, var12);
                  var8 = Math.max(var8, var12);
                  var9 = Math.min(var9, var11);
                  var10 = Math.max(var10, var11);
               }

               if ((var14 & 2) != 0) {
                  var2++;
               }

               if ((var14 & 4) != 0) {
                  var3++;
               }

               if ((var14 & 8) != 0) {
                  var4++;
               }

               if ((var14 & 16) != 0) {
                  var5++;
               }
            }
         }

         return var6 >= 3584 && var1 >= 256 && var2 >= 12 && var3 >= 12 && var4 >= 16 && var5 >= 8 && var8 - var7 >= 24 && var10 - var9 >= 24;
      }
   }

   public static BufferedImage render(StashScene.Snapshot var0) {
      BufferedImage var1 = new BufferedImage(896, 704, 1);
      Graphics2D var2 = var1.createGraphics();

      try {
         var2.setColor(new Color(1580580));
         var2.fillRect(0, 0, 896, 704);
         var2.setColor(Color.WHITE);
         var2.setFont(new Font("SansSerif", 1, 20));
         var2.drawString("STASH / 3D SURFACE", 24, 32);
         var2.setFont(new Font("Monospaced", 0, 15));
         var2.drawString("X " + var0.x() + "   Z " + var0.z() + "   |   128 x 128 blocks", 24, 57);
         int var3 = Integer.MAX_VALUE;
         int var4 = Integer.MIN_VALUE;

         for (int var5 = 0; var5 < var0.colors().length; var5++) {
            if (var0.colors()[var5] != 0) {
               var3 = Math.min(var3, var0.heights()[var5]);
               var4 = Math.max(var4, var0.heights()[var5]);
            }
         }

         if (var0.known() == 0) {
            var2.drawString("Terrain unavailable at capture time", 260, 330);
         } else {
            double var27 = 2.9;
            double var7 = Math.min(2.6, 165.0 / Math.max(1, var4 - var3));
            double var9 = 275.0 + (var4 - var3) * var7;

            for (int var11 = 0; var11 <= 254; var11++) {
               for (int var12 = Math.max(0, var11 - 127); var12 <= Math.min(127, var11); var12++) {
                  int var13 = var11 - var12;
                  int var14 = var13 * 128 + var12;
                  if (var0.colors()[var14] != 0) {
                     double var15 = 448.0 + (var12 - var13) * var27;
                     double var17 = var9 + (var12 + var13 - 128) * var27 / 2.0 - (var0.heights()[var14] - var3) * var7;
                     int var19 = var0.colors()[var14];
                     int var20 = var12 < 127 && var0.colors()[var14 + 1] != 0 ? var0.heights()[var14 + 1] : var3 - 2;
                     int var21 = var13 < 127 && var0.colors()[var14 + 128] != 0 ? var0.heights()[var14 + 128] : var3 - 2;
                     if (var20 < var0.heights()[var14]) {
                        double var22 = (var0.heights()[var14] - var20) * var7;
                        var2.setColor(shade(var19, 0.62));
                        var2.fillPolygon(
                           poly(
                              var15 + var27,
                              var17 + var27 / 2.0,
                              var15,
                              var17 + var27,
                              var15,
                              var17 + var27 + var22,
                              var15 + var27,
                              var17 + var27 / 2.0 + var22
                           )
                        );
                     }

                     if (var21 < var0.heights()[var14]) {
                        double var30 = (var0.heights()[var14] - var21) * var7;
                        var2.setColor(shade(var19, 0.8));
                        var2.fillPolygon(
                           poly(
                              var15 - var27,
                              var17 + var27 / 2.0,
                              var15,
                              var17 + var27,
                              var15,
                              var17 + var27 + var30,
                              var15 - var27,
                              var17 + var27 / 2.0 + var30
                           )
                        );
                     }

                     var2.setColor(new Color(var19));
                     Polygon var31 = poly(var15, var17, var15 + var27, var17 + var27 / 2.0, var15, var17 + var27, var15 - var27, var17 + var27 / 2.0);
                     var2.fillPolygon(var31);
                     var2.drawPolygon(var31);
                  }
               }
            }

            int var28 = 8256;
            if (var0.colors()[var28] != 0) {
               int var29 = (int)(var9 - (var0.heights()[var28] - var3) * var7);
               var2.setColor(new Color(16731573));
               var2.drawLine(448, var29 - 30, 448, var29);
               var2.fillOval(443, var29 - 37, 10, 10);
            }
         }

         var2.setFont(new Font("SansSerif", 0, 14));
         var2.setColor(new Color(12635093));
         var2.drawString("Pink: hit X/Z  |  Surface height model, not an underground view or textured block model", 24, 654);
         var2.drawString("Loaded coverage: " + Math.round(var0.known() * 100.0 / 16384.0) + "%  |  Blank: unavailable  |  N (-Z): upper right", 24, 680);
      } finally {
         var2.dispose();
      }

      return var1;
   }

   private static Polygon poly(double... var0) {
      Polygon var1 = new Polygon();

      for (int var2 = 0; var2 < var0.length; var2 += 2) {
         var1.addPoint((int)Math.round(var0[var2]), (int)Math.round(var0[var2 + 1]));
      }

      return var1;
   }

   private static Color shade(int var0, double var1) {
      return new Color((int)((var0 >> 16 & 0xFF) * var1), (int)((var0 >> 8 & 0xFF) * var1), (int)((var0 & 0xFF) * var1));
   }

   public static byte[] png(StashScene.Snapshot var0) throws IOException {
      ByteArrayOutputStream var1 = new ByteArrayOutputStream();
      ImageWriter var2 = ImageIO.getImageWritersByFormatName("png").next();

      try (MemoryCacheImageOutputStream var3 = new MemoryCacheImageOutputStream(var1)) {
         var2.setOutput(var3);
         var2.write(render(var0));
         var3.flush();
      } finally {
         var2.dispose();
      }

      return var1.toByteArray();
   }

   public static final class Capture {
      private final int x;
      private final int z;
      private final int[] colors = new int[16384];
      private final int[] heights = new int[16384];
      private final int[] evidence = new int[16384];
      private int cursor;
      private int known;

      public Capture(int var1, int var2) {
         if (Math.abs((long)var1) <= 30000000L && Math.abs((long)var2) <= 30000000L) {
            this.x = var1;
            this.z = var2;
         } else {
            throw new IllegalArgumentException("Outside world border");
         }
      }

      public boolean step(StashScene.Reader var1, BooleanSupplier var2) {
         int var3 = 1024;

         while (this.cursor < this.colors.length && var3-- > 0 && !var2.getAsBoolean()) {
            int var4 = this.cursor / 256;
            int var5 = this.cursor++ % 256;
            int var6 = 36;
            if (var4 == 0) {
               var4 = var6;
            } else if (var4 <= var6) {
               var4--;
            }

            int var7 = var4 % 8 * 16 + var5 % 16;
            int var8 = var4 / 8 * 16 + var5 / 16;
            StashScene.Column var9 = var1.read(this.x - 64 + var7, this.z - 64 + var8);
            if (var9 != null) {
               int var10 = var8 * 128 + var7;
               this.colors[var10] = 0xFF000000 | var9.rgb();
               this.heights[var10] = var9.y();
               this.evidence[var10] = var9.evidence();
               this.known++;
            }
         }

         return this.cursor == this.colors.length;
      }

      public StashScene.Snapshot snapshot() {
         return new StashScene.Snapshot(
            this.x,
            this.z,
            (int[])this.colors.clone(),
            (int[])this.heights.clone(),
            (int[])this.evidence.clone(),
            this.known,
            this.cursor == this.colors.length
         );
      }
   }

   public record Column(int rgb, int y, int evidence) {
   }

   @FunctionalInterface
   public interface Reader {
      StashScene.Column read(int var1, int var2);
   }

   public record Snapshot(int x, int z, int[] colors, int[] heights, int[] evidence, int known, boolean complete) {
      public int minX() {
         return this.x - 64;
      }

      public int minZ() {
         return this.z - 64;
      }
   }
}
