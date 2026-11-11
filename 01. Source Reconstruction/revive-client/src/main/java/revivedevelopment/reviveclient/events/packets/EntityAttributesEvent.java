package revivedevelopment.reviveclient.events.packets;

import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;

public class EntityAttributesEvent {
   private static final EntityAttributesEvent INSTANCE = new EntityAttributesEvent();
   public EntityAttributesS2CPacket packet;

   public static EntityAttributesEvent get(EntityAttributesS2CPacket packet) {
      INSTANCE.packet = packet;
      return INSTANCE;
   }
}
