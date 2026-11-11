package revivedevelopment.reviveclient.systems.modules.combat;

import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.FindItemResult;

public class KeyPotion extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> onGround = this.sgGeneral
      .add(new BoolSetting.Builder().name("only-on-ground").description("Only throw while standing on the ground.").defaultValue(false).build());

   public KeyPotion() {
      super(Categories.Combat, "key-potion", "Throws a splash potion at your feet on keybind press.");
   }

   @Override
   public void onActivate() {
      try {
         this.throwPotion();
      } finally {
         this.toggle();
      }
   }

   private void throwPotion() {
      if (this.mc.player != null && this.mc.world != null && this.mc.getNetworkHandler() != null) {
         if (!this.onGround.get() || this.mc.player.isOnGround()) {
            FindItemResult potion = ReviveClient.SWAP.getSlot(Items.SPLASH_POTION);
            if (!potion.found()) {
               this.warning("No splash potion.", new Object[0]);
            } else {
               float yaw = this.mc.player.getYaw();
               if (potion.isOffhand() || potion.isMainHand()) {
                  this.send(potion.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND, yaw);
               } else if (ReviveClient.SWAP.beginSwap(potion, true)) {
                  try {
                     this.send(Hand.MAIN_HAND, yaw);
                  } finally {
                     ReviveClient.SWAP.endSwap(true);
                  }
               }
            }
         }
      }
   }

   private void send(Hand hand, float yaw) {
      int seq = ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
      this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, seq, yaw, 90.0F));
      this.mc.player.swingHand(hand);
   }
}
