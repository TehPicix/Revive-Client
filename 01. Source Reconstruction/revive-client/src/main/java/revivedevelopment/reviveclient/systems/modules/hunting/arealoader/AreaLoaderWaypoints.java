package revivedevelopment.reviveclient.systems.modules.hunting.arealoader;

import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.utils.compat.XaeroCompat;

final class AreaLoaderWaypoints {
   private static final String NAME = "AreaLoader Goal";

   private AreaLoaderWaypoints() {
   }

   private static Object getWaypointSet() {
      return XaeroCompat.getWaypointSet();
   }

   static void add(BlockPos pos) {
      try {
         Object set = getWaypointSet();
         if (set == null) {
            return;
         }

         removeInternal(set, pos.getX(), pos.getZ());
         XaeroCompat.addWaypoint(set, pos.getX(), pos.getY(), pos.getZ(), "AreaLoader Goal", "→", 6, 0, true);
         XaeroCompat.refreshWaypoints();
      } catch (Throwable var2) {
      }
   }

   static void remove(int x, int z) {
      try {
         Object set = getWaypointSet();
         if (set == null) {
            return;
         }

         if (removeInternal(set, x, z)) {
            XaeroCompat.refreshWaypoints();
         }
      } catch (Throwable var3) {
      }
   }

   private static boolean removeInternal(Object set, int x, int z) {
      Object found = XaeroCompat.findWaypoint(set, x, z, "AreaLoader Goal");
      if (found != null) {
         XaeroCompat.removeWaypoint(set, found);
         return true;
      } else {
         return false;
      }
   }
}
