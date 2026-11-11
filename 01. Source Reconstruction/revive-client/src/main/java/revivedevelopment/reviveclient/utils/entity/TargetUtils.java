package revivedevelopment.reviveclient.utils.entity;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameMode;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerManager;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;

public class TargetUtils {
   private TargetUtils() {
   }

   @Nullable
   public static Entity get(Predicate<Entity> isGood, SortPriority sortPriority) {
      Entity[] best = new Entity[]{null};
      forEachCandidate(isGood, entity -> {
         if (best[0] == null || sortPriority.compare(entity, best[0]) < 0) {
            best[0] = entity;
         }
      });
      return best[0];
   }

   public static void getList(List<Entity> targetList, Predicate<Entity> isGood, SortPriority sortPriority, int maxCount) {
      targetList.clear();
      forEachCandidate(isGood, targetList::add);
      targetList.sort(sortPriority);
      if (targetList.size() > maxCount) {
         targetList.subList(maxCount, targetList.size()).clear();
      }
   }

   private static void forEachCandidate(Predicate<Entity> isGood, Consumer<Entity> out) {
      for (Entity entity : ReviveClient.mc.world.getEntities()) {
         if (entity != null && isGood.test(entity)) {
            out.accept(entity);
         }
      }

      FakePlayerManager.forEach(fp -> {
         if (fp != null && ReviveClient.mc.world.getEntityById(fp.getId()) != fp) {
            if (isGood.test(fp)) {
               out.accept(fp);
            }
         }
      });
   }

   @Nullable
   public static PlayerEntity getPlayerTarget(double range, SortPriority priority) {
      return !Utils.canUpdate()
         ? null
         : (PlayerEntity)get(
            entity -> {
               if (entity instanceof PlayerEntity && entity != ReviveClient.mc.player) {
                  if (((PlayerEntity)entity).isDead() || ((PlayerEntity)entity).getHealth() <= 0.0F) {
                     return false;
                  } else if (!PlayerUtils.isWithin(entity, range)) {
                     return false;
                  } else {
                     return !Friends.get().shouldAttack((PlayerEntity)entity)
                        ? false
                        : EntityUtils.getGameMode((PlayerEntity)entity) == GameMode.SURVIVAL || entity instanceof FakePlayerEntity;
                  }
               } else {
                  return false;
               }
            },
            priority
         );
   }

   public static boolean isBadTarget(PlayerEntity target, double range) {
      if (target == null) {
         return true;
      } else {
         return !target.isRemoved() && target.getEntityWorld() == ReviveClient.mc.world
            ? !PlayerUtils.isWithin(target, range) || !target.isAlive() || target.isDead() || target.getHealth() <= 0.0F
            : true;
      }
   }
}
