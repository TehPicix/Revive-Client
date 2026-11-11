package revivedevelopment.reviveclient.systems.managers;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.SortPriority;
import revivedevelopment.reviveclient.utils.entity.TargetUtils;

public class TargetManager {
   private Entity target;

   @Nullable
   public PlayerEntity getPlayerTarget(double range, SortPriority priority) {
      return TargetUtils.getPlayerTarget(range, priority);
   }

   @Nullable
   public Entity get(Predicate<Entity> isGood, SortPriority priority) {
      return TargetUtils.get(isGood, priority);
   }

   public void getList(List<Entity> out, Predicate<Entity> isGood, SortPriority priority, int maxCount) {
      TargetUtils.getList(out, isGood, priority, maxCount);
   }

   public boolean isBadTarget(PlayerEntity target, double range) {
      return TargetUtils.isBadTarget(target, range);
   }

   @Nullable
   public PlayerEntity selectPlayer(double range, SortPriority priority) {
      PlayerEntity selected = this.getPlayerTarget(range, priority);
      this.target = selected;
      return selected;
   }

   @Nullable
   public Entity getTarget() {
      return this.target;
   }

   public void setTarget(@Nullable Entity target) {
      this.target = target;
   }

   public void clear() {
      this.target = null;
   }

   public static boolean isSwimming(LivingEntity entity) {
      return entity.isSwimming();
   }

   public static boolean isGliding(LivingEntity entity) {
      return entity.isGliding();
   }

   public static boolean isHardToHit(LivingEntity entity) {
      return isSwimming(entity) || isGliding(entity);
   }

   public Vec3d predictedPos(LivingEntity target) {
      AntiCheatConfig c = AntiCheatConfig.get();
      Vec3d pos = target.getEntityPos();
      if (!c.predictionEnabled.get()) {
         return pos;
      } else {
         int ticks = isGliding(target) ? c.elytraTicks.get() : c.predictionTicks.get();
         Vec3d vel = target.getVelocity();
         double y = c.groundRaycast.get() ? pos.y : pos.y + vel.y * ticks;
         return new Vec3d(pos.x + vel.x * ticks, y, pos.z + vel.z * ticks);
      }
   }

   public int effectiveLatencyMs(PlayerEntity player) {
      AntiCheatConfig c = AntiCheatConfig.get();
      return c.latencyMode.get() == AntiCheatConfig.LatencyMode.Fast ? c.fastLatency.get() : EntityUtils.getPing(player);
   }
}
