package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;

public class ChestSwap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Keybind> swapBind = this.sgGeneral.add(new KeybindSetting.Builder().name("swap-bind").description("Swaps on this key press.").build());
   private final SettingGroup sgCombat = this.settings.createGroup("Combat Protection");
   private final Setting<Boolean> swapOnHit = this.sgCombat
      .add(
         new BoolSetting.Builder()
            .name("swap-on-hit")
            .description("Automatically swap the elytra to your best chestplate when you take a hit, then back.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> protectionDuration = this.sgCombat
      .add(
         new IntSetting.Builder()
            .name("protection-duration")
            .description("Ticks to keep the chestplate equipped after being hit.")
            .defaultValue(60)
            .min(20)
            .sliderRange(20, 200)
            .visible(this.swapOnHit::get)
            .build()
      );
   private final Setting<Boolean> autoSwapBack = this.sgCombat
      .add(
         new BoolSetting.Builder()
            .name("auto-swap-back")
            .description("Swap back to the elytra after the protection window ends.")
            .defaultValue(true)
            .visible(this.swapOnHit::get)
            .build()
      );
   private boolean keyUnpressed = false;
   private boolean protecting = false;
   private int protectTimer = 0;
   private int lastHurtTime = 0;

   public ChestSwap() {
      super(Categories.Player, "chest-swap", "Automatically swaps between a chestplate and an elytra.");
   }

   @Override
   public void onActivate() {
      this.protecting = false;
      this.protectTimer = 0;
      this.lastHurtTime = 0;
   }

   @Override
   public void onDeactivate() {
      this.protecting = false;
      this.protectTimer = 0;
      this.lastHurtTime = 0;
   }

   @EventHandler
   private void onCombatTick(TickEvent.Pre event) {
      if (this.swapOnHit.get() && this.mc.player != null) {
         int hurtTime = this.mc.player.hurtTime;
         if (hurtTime > 0 && hurtTime > this.lastHurtTime) {
            boolean wearingElytra = this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
            if (wearingElytra && !this.protecting && ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
               this.swap();
               this.protecting = true;
               this.protectTimer = this.protectionDuration.get();
            } else if (this.protecting) {
               this.protectTimer = this.protectionDuration.get();
            }
         }

         this.lastHurtTime = hurtTime;
         if (this.protecting) {
            if (this.protectTimer > 0) {
               this.protectTimer--;
            }

            if (this.protectTimer <= 0 && this.autoSwapBack.get()) {
               boolean wearingChestplate = this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA;
               if (wearingChestplate && ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                  this.swap();
               }

               this.protecting = false;
            }
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onRender(Render3DEvent event) {
      this.update();
   }

   private void update() {
      if (!this.swapBind.get().isPressed()) {
         this.keyUnpressed = true;
      }

      if (this.swapBind.get().isPressed() && this.keyUnpressed && !(this.mc.currentScreen instanceof ChatScreen)) {
         if (!ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
            return;
         }

         this.swap();
         this.keyUnpressed = false;
      }
   }

   public void swap() {
      Item currentItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem();
      if (currentItem == Items.ELYTRA) {
         PlayerUtils.silentSwapEquipChestplate();
      } else if (this.isChestplate(this.mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
         PlayerUtils.silentSwapEquipElytra();
      } else if (!PlayerUtils.silentSwapEquipChestplate()) {
         PlayerUtils.silentSwapEquipElytra();
      }
   }

   private boolean isChestplate(ItemStack stack) {
      EquippableComponent equippable = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
   }
}
