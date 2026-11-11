package revivedevelopment.reviveclient.utils;

import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.PathSegment;
import revivedevelopment.reviveclient.systems.modules.movement.BaritoneElytraOverworld;

public final class BaritoneOverworldPathfinder {
   public static final int MAX_BLOCK_Y = 127;
   private static final double RAY_MAX_Y = 127.99;
   private static final double RAY_MIN_Y = 0.0;
   private static final double MIN_RAY_LENGTH_SQ = 1.0E-12;

   private BaritoneOverworldPathfinder() {
   }

   public static PathSegment pathFind(
      long context, int x1, int y1, int z1, int x2, int y2, int z2, boolean x4Min, boolean refineResult, int timeoutMs, boolean airIfFake
   ) {
      if (BaritoneElytraOverworld.isUnlocked()) {
         int startY = clampBlockY(y1);
         if (startY != y1) {
            BaritoneElytraOverworld.onPathStartClamped(y1, startY);
         }

         y1 = startY;
         y2 = clampBlockY(y2);
      }

      return NetherPathfinder.pathFind(context, x1, y1, z1, x2, y2, z2, x4Min, refineResult, timeoutMs, airIfFake);
   }

   public static boolean isVisible(long context, int fakeChunkMode, double x1, double y1, double z1, double x2, double y2, double z2) {
      if (BaritoneElytraOverworld.isUnlocked() && (!insideRayRange(y1) || !insideRayRange(y2))) {
         double[] ray = clip(x1, y1, z1, x2, y2, z2);
         return ray == null ? true : NetherPathfinder.isVisible(context, fakeChunkMode, ray[0], ray[1], ray[2], ray[3], ray[4], ray[5]);
      } else {
         return NetherPathfinder.isVisible(context, fakeChunkMode, x1, y1, z1, x2, y2, z2);
      }
   }

   public static int isVisibleMulti(long context, int fakeChunkMode, int count, double[] src, double[] dst, boolean modeAny) {
      if (BaritoneElytraOverworld.isUnlocked() && !allInsideRayRange(count, src, dst)) {
         for (int i = 0; i < count; i++) {
            int o = i * 3;
            boolean visible = isVisible(context, fakeChunkMode, src[o], src[o + 1], src[o + 2], dst[o], dst[o + 1], dst[o + 2]);
            if (visible == modeAny) {
               return i;
            }
         }

         return -1;
      } else {
         return NetherPathfinder.isVisibleMulti(context, fakeChunkMode, count, src, dst, modeAny);
      }
   }

   public static void raytrace(long context, int fakeChunkMode, int count, double[] src, double[] dst, boolean[] hitsOut, double[] hitPosOut) {
      if (BaritoneElytraOverworld.isUnlocked() && !allInsideRayRange(count, src, dst)) {
         double[] clippedSrc = new double[count * 3];
         double[] clippedDst = new double[count * 3];
         int[] originalIndex = new int[count];
         int traced = 0;

         for (int i = 0; i < count; i++) {
            int o = i * 3;
            double[] ray = clip(src[o], src[o + 1], src[o + 2], dst[o], dst[o + 1], dst[o + 2]);
            if (ray != null) {
               int t = traced * 3;
               clippedSrc[t] = ray[0];
               clippedSrc[t + 1] = ray[1];
               clippedSrc[t + 2] = ray[2];
               clippedDst[t] = ray[3];
               clippedDst[t + 1] = ray[4];
               clippedDst[t + 2] = ray[5];
               originalIndex[traced++] = i;
            }
         }

         if (traced != 0) {
            boolean[] hits = new boolean[traced];
            double[] hitPos = hitPosOut != null ? new double[traced * 3] : null;
            NetherPathfinder.raytrace(context, fakeChunkMode, traced, clippedSrc, clippedDst, hits, hitPos);

            for (int t = 0; t < traced; t++) {
               if (hits[t]) {
                  int ix = originalIndex[t];
                  hitsOut[ix] = true;
                  if (hitPos != null) {
                     hitPosOut[ix * 3] = hitPos[t * 3];
                     hitPosOut[ix * 3 + 1] = hitPos[t * 3 + 1];
                     hitPosOut[ix * 3 + 2] = hitPos[t * 3 + 2];
                  }
               }
            }
         }
      } else {
         NetherPathfinder.raytrace(context, fakeChunkMode, count, src, dst, hitsOut, hitPosOut);
      }
   }

   static int clampBlockY(int y) {
      return Math.max(0, Math.min(127, y));
   }

   private static boolean insideRayRange(double y) {
      return y >= 0.0 && y <= 127.99;
   }

   private static boolean allInsideRayRange(int count, double[] src, double[] dst) {
      for (int i = 0; i < count; i++) {
         if (!insideRayRange(src[i * 3 + 1]) || !insideRayRange(dst[i * 3 + 1])) {
            return false;
         }
      }

      return true;
   }

   static double[] clip(double x1, double y1, double z1, double x2, double y2, double z2) {
      double dy = y2 - y1;
      double t0 = 0.0;
      double t1 = 1.0;
      if (Math.abs(dy) < 1.0E-9) {
         if (!insideRayRange(y1)) {
            return null;
         }
      } else {
         double ta = (0.0 - y1) / dy;
         double tb = (127.99 - y1) / dy;
         t0 = Math.max(0.0, Math.min(ta, tb));
         t1 = Math.min(1.0, Math.max(ta, tb));
         if (t0 >= t1) {
            return null;
         }
      }

      double dx = x2 - x1;
      double dz = z2 - z1;
      double[] out = new double[]{x1 + dx * t0, clampRayY(y1 + dy * t0), z1 + dz * t0, x1 + dx * t1, clampRayY(y1 + dy * t1), z1 + dz * t1};
      double lx = out[3] - out[0];
      double ly = out[4] - out[1];
      double lz = out[5] - out[2];
      return lx * lx + ly * ly + lz * lz < 1.0E-12 ? null : out;
   }

   private static double clampRayY(double y) {
      return Math.max(0.0, Math.min(127.99, y));
   }
}
