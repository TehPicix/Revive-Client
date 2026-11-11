package revivedevelopment.reviveclient.events.entity.player;

import net.minecraft.entity.Entity;
import revivedevelopment.reviveclient.events.Cancellable;

public class AttackEntityEvent extends Cancellable {
   private static final AttackEntityEvent INSTANCE = new AttackEntityEvent();
   public Entity entity;

   public static void release() {
      INSTANCE.entity = null;
   }

   public static AttackEntityEvent get(Entity entity) {
      INSTANCE.setCancelled(false);
      INSTANCE.entity = entity;
      return INSTANCE;
   }
}
