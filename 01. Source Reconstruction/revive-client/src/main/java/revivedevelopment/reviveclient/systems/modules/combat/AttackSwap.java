package revivedevelopment.reviveclient.systems.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.AttackEntityEvent;
import revivedevelopment.reviveclient.events.entity.player.PostAttackEntityEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.FindItemResult;

public class AttackSwap extends Module {
   private boolean swapping;

   public AttackSwap() {
      super(Categories.Combat, "attack-swap", "Silently swaps to your sword for manual attacks made with a non-tool item.");
   }

   @Override
   public void onDeactivate() {
      this.release();
   }

   @EventHandler(
      priority = -100
   )
   private void onAttack(AttackEntityEvent event) {
      if (!event.isCancelled() && !this.swapping) {
         if (this.mc.player != null && event.entity instanceof LivingEntity) {
            ItemStack held = this.mc.player.getMainHandStack();
            if (!held.contains(DataComponentTypes.TOOL)) {
               if (!held.isOf(Items.MACE) && !held.isOf(Items.TRIDENT)) {
                  FindItemResult sword = ReviveClient.SWAP.getSlot(Items.NETHERITE_SWORD);
                  if (!sword.found()) {
                     sword = ReviveClient.SWAP.getSlot(Items.DIAMOND_SWORD);
                  }

                  if (sword.found() && !sword.isMainHand() && !sword.isOffhand()) {
                     this.swapping = ReviveClient.SWAP.beginSwap(sword, true);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onPostAttack(PostAttackEntityEvent event) {
      this.release();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.release();
   }

   private void release() {
      if (this.swapping) {
         this.swapping = false;
         ReviveClient.SWAP.endSwap(true);
      }
   }
}
