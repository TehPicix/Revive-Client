package revivedevelopment.reviveclient.utils.entity;

import java.util.Comparator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.player.Rotations;

public enum SortPriority implements Comparator<Entity> {
   LowestDistance(SortPriority::sortDistance),
   HighestDistance((e1, e2) -> sortDistance(e2, e1)),
   LowestHealth(SortPriority::sortHealth),
   HighestHealth((e1, e2) -> sortHealth(e2, e1)),
   ClosestAngle(SortPriority::sortAngle),
   ClosestAngleDistance(SortPriority::sortAngleDistance);

   private final Comparator<Entity> comparator;

   private SortPriority(Comparator<Entity> comparator) {
      this.comparator = comparator;
   }

   public int compare(Entity o1, Entity o2) {
      int c = this.comparator.compare(o1, o2);
      if (c != 0) {
         return c;
      } else {
         c = Double.compare(PlayerUtils.squaredDistanceTo(o1), PlayerUtils.squaredDistanceTo(o2));
         return c != 0 ? c : Integer.compare(o1.getId(), o2.getId());
      }
   }

   private static int sortDistance(Entity e1, Entity e2) {
      return Double.compare(PlayerUtils.squaredDistanceTo(e1), PlayerUtils.squaredDistanceTo(e2));
   }

   private static int sortHealth(Entity e1, Entity e2) {
      boolean e1l = e1 instanceof LivingEntity;
      boolean e2l = e2 instanceof LivingEntity;
      if (!e1l && !e2l) {
         return 0;
      } else if (e1l && !e2l) {
         return 1;
      } else {
         return !e1l ? -1 : Float.compare(effectiveHealth((LivingEntity)e1), effectiveHealth((LivingEntity)e2));
      }
   }

   private static float effectiveHealth(LivingEntity e) {
      return e.getHealth() + e.getAbsorptionAmount();
   }

   private static int sortAngle(Entity e1, Entity e2) {
      boolean e1l = e1 instanceof LivingEntity;
      boolean e2l = e2 instanceof LivingEntity;
      if (!e1l && !e2l) {
         return 0;
      } else if (e1l && !e2l) {
         return 1;
      } else if (!e1l) {
         return -1;
      } else {
         double e1yaw = Math.abs(Rotations.getYaw(e1) - ReviveClient.mc.player.getYaw());
         double e2yaw = Math.abs(Rotations.getYaw(e2) - ReviveClient.mc.player.getYaw());
         double e1pitch = Math.abs(Rotations.getPitch(e1) - ReviveClient.mc.player.getPitch());
         double e2pitch = Math.abs(Rotations.getPitch(e2) - ReviveClient.mc.player.getPitch());
         return Double.compare(e1yaw * e1yaw + e1pitch * e1pitch, e2yaw * e2yaw + e2pitch * e2pitch);
      }
   }

   private static int sortAngleDistance(Entity e1, Entity e2) {
      boolean e1l = e1 instanceof LivingEntity;
      boolean e2l = e2 instanceof LivingEntity;
      if (!e1l && !e2l) {
         return 0;
      } else if (e1l && !e2l) {
         return 1;
      } else {
         return !e1l ? -1 : Double.compare(angleDistanceScore(e1), angleDistanceScore(e2));
      }
   }

   private static double angleDistanceScore(Entity e) {
      double yaw = Math.abs(MathHelper.wrapDegrees(Rotations.getYaw(e) - ReviveClient.mc.player.getYaw()));
      double pitch = Math.abs(MathHelper.wrapDegrees(Rotations.getPitch(e) - ReviveClient.mc.player.getPitch()));
      double dist = Math.sqrt(PlayerUtils.squaredDistanceTo(e));
      return yaw + pitch * 0.5 + dist * 0.01;
   }
}
