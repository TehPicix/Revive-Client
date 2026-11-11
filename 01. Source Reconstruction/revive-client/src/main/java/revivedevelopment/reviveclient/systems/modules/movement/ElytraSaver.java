package revivedevelopment.reviveclient.systems.modules.movement;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;

public class ElytraSaver extends Module {
   public ElytraSaver() {
      super(Categories.Movement, "elytra-saver", "Master switch for the Elytra Saver. Off: no saving, elytra equipped.");
   }

   @Override
   public boolean activeByDefault() {
      return true;
   }

   @Override
   public void onDeactivate() {
      if (this.mc.player != null && !PbPredict.serverPushesOnly() && !this.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         PlayerUtils.silentSwapEquipElytra();
      }
   }

   public static boolean allowed() {
      Modules modules = Modules.get();
      if (modules == null) {
         return true;
      } else {
         ElytraSaver m = modules.get(ElytraSaver.class);
         return m == null || m.isActive();
      }
   }
}
