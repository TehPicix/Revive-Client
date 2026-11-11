package revivedevelopment.reviveclient.events.entity.player;

import net.minecraft.entity.Entity;

public class PostAttackEntityEvent {
   private static final PostAttackEntityEvent INSTANCE = new PostAttackEntityEvent();
   public Entity entity;

   public static void release() {
      INSTANCE.entity = null;
   }

   public static PostAttackEntityEvent get(Entity entity) {
      INSTANCE.entity = entity;
      return INSTANCE;
   }
}
