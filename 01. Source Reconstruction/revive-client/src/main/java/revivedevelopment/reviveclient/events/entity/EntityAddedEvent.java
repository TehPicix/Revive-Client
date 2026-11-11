package revivedevelopment.reviveclient.events.entity;

import net.minecraft.entity.Entity;

public class EntityAddedEvent {
   private static final EntityAddedEvent INSTANCE = new EntityAddedEvent();
   public Entity entity;

   public static void release() {
      INSTANCE.entity = null;
   }

   public static EntityAddedEvent get(Entity entity) {
      INSTANCE.entity = entity;
      return INSTANCE;
   }
}
