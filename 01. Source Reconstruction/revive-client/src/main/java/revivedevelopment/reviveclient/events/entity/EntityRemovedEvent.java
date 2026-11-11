package revivedevelopment.reviveclient.events.entity;

import net.minecraft.entity.Entity;

public class EntityRemovedEvent {
   private static final EntityRemovedEvent INSTANCE = new EntityRemovedEvent();
   public Entity entity;

   public static void release() {
      INSTANCE.entity = null;
   }

   public static EntityRemovedEvent get(Entity entity) {
      INSTANCE.entity = entity;
      return INSTANCE;
   }
}
