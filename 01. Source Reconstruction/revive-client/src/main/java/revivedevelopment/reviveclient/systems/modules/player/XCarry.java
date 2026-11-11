package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class XCarry extends Module {
   public XCarry() {
      super(Categories.Player, "x-carry", "Keeps items in your 2x2 crafting grid as extra storage when the inventory closes.");
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (event.packet instanceof CloseHandledScreenC2SPacket packet && packet.getSyncId() == 0) {
         event.cancel();
      }
   }
}
