package revivedevelopment.reviveclient.systems.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.PlayerScreenHandler;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class BootSwap extends Module {
   public BootSwap() {
      super(Categories.Combat, "boot-swap", "Protection boots while phased, Blast Protection boots otherwise.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         if (this.mc.player.currentScreenHandler instanceof PlayerScreenHandler) {
            if (this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
               RegistryKey<Enchantment> want = this.phased() ? Enchantments.PROTECTION : Enchantments.BLAST_PROTECTION;
               if (!Utils.hasEnchantment(this.mc.player.getEquippedStack(EquipmentSlot.FEET), want)) {
                  FindItemResult boots = InvUtils.find(s -> isBoots(s) && Utils.hasEnchantment(s, want));
                  if (boots.found() && !boots.isArmor()) {
                     if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                        InvUtils.move().from(boots.slot()).toArmor(0);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean phased() {
      BlockState state = this.mc.world.getBlockState(this.mc.player.getBlockPos());
      return !state.isAir() && !state.isReplaceable();
   }

   private static boolean isBoots(ItemStack stack) {
      EquippableComponent eq = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
      return eq != null && eq.slot() == EquipmentSlot.FEET;
   }
}
