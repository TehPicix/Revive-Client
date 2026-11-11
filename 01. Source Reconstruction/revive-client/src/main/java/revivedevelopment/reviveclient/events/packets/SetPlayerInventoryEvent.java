package revivedevelopment.reviveclient.events.packets;

import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;

public class SetPlayerInventoryEvent {
   private static final SetPlayerInventoryEvent INSTANCE = new SetPlayerInventoryEvent();
   public SetPlayerInventoryS2CPacket packet;

   public static SetPlayerInventoryEvent get(SetPlayerInventoryS2CPacket packet) {
      INSTANCE.packet = packet;
      return INSTANCE;
   }
}
