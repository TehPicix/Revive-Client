package revivedevelopment.reviveclient.systems.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class ElytraReplace extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> threshold = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("durability")
            .description("Swap when the worn elytra has this much durability left or less.")
            .defaultValue(15)
            .range(1, 431)
            .sliderRange(1, 100)
            .build()
      );
   private final Setting<Boolean> preferMending = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("prefer-mending")
            .description("Pick a Mending elytra over a non-Mending one, even if it has a bit less durability.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> notify = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("notify")
            .description("Say in chat when it swaps, and warn once when there's no spare left.")
            .defaultValue(true)
            .build()
      );
   private boolean warned;

   public ElytraReplace() {
      super(Categories.Movement, "elytra-replace", "Swaps in a fresh elytra from your inventory when the worn one gets low.");
   }

   @Override
   public void onActivate() {
      this.warned = false;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         if (this.mc.player.currentScreenHandler instanceof PlayerScreenHandler) {
            if (this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
               ElytraDurabilitySaver saver = ElytraDurabilitySaver.get();
               if (saver == null || !saver.isFlashing()) {
                  ItemStack worn = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
                  if (worn.isOf(Items.ELYTRA) && left(worn) <= this.threshold.get()) {
                     int best = -1;
                     int bestScore = Integer.MIN_VALUE;

                     for (int i = 0; i < 36; i++) {
                        ItemStack s = this.mc.player.getInventory().getStack(i);
                        if (s.isOf(Items.ELYTRA) && left(s) > this.threshold.get()) {
                           int score = left(s) + (this.preferMending.get() && Utils.hasEnchantment(s, Enchantments.MENDING) ? 10000 : 0);
                           if (score > bestScore) {
                              bestScore = score;
                              best = i;
                           }
                        }
                     }

                     if (best == -1) {
                        if (this.notify.get() && !this.warned) {
                           this.warning("Elytra at %d durability and no spare to swap in.", new Object[]{left(worn)});
                        }

                        this.warned = true;
                     } else if (!PbPredict.serverPushesOnly()) {
                        if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                           int before = left(worn);
                           int after = left(this.mc.player.getInventory().getStack(best));
                           InvUtils.move().from(best).toArmor(2);
                           if (this.notify.get()) {
                              this.info("Swapped elytra (%d left) for one with %d.", new Object[]{before, after});
                           }

                           this.warned = false;
                        }
                     }
                  } else {
                     this.warned = false;
                  }
               }
            }
         }
      }
   }

   private static int left(ItemStack stack) {
      Integer max = (Integer)stack.get(DataComponentTypes.MAX_DAMAGE);
      return (max != null ? max : stack.getMaxDamage()) - stack.getDamage();
   }
}
