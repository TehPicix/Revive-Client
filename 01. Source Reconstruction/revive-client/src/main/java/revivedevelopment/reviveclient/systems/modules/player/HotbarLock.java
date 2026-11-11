package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.PlayerInput;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.PbState;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class HotbarLock extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> checkDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("check-delay")
            .description("The delay in ticks between hotbar checks.")
            .defaultValue(5)
            .min(1)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Boolean> lockPickaxe = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("lock-pickaxe")
            .description("Keeps a pickaxe in the pickaxe slot, pulling one from your inventory if needed.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> pickaxeSlot = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("pickaxe-slot")
            .description("The slot to lock your pickaxe to (1-9).")
            .defaultValue(1)
            .min(1)
            .max(9)
            .sliderRange(1, 9)
            .visible(this.lockPickaxe::get)
            .build()
      );
   private final Setting<Boolean> ignoreTotems = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ignore-totems")
            .description("Never move a totem out of a hotbar slot (lets Offhand / hotbar-totem setups work).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> pauseUse = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-use")
            .description("Don't correct the hotbar while you're using an item (eating, drinking, etc.).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> grimSafe = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("grim-safe")
            .description(
               "Only click while the server sees you standing still (no movement / jump / sneak / sprint), since Grim drops inventory clicks made while moving."
            )
            .defaultValue(true)
            .build()
      );
   private final ItemStack[] hotbarSnapshot = new ItemStack[9];
   private int checkTimer;
   private boolean wasScreenOpen;
   private boolean serverInputClear;

   public HotbarLock() {
      super(Categories.Player, "hotbar-lock", "Locks your hotbar layout so items stay in their slots.");

      for (int i = 0; i < this.hotbarSnapshot.length; i++) {
         this.hotbarSnapshot[i] = ItemStack.EMPTY;
      }
   }

   @Override
   public void onActivate() {
      if (this.mc.player != null) {
         this.saveHotbar();
      }

      this.checkTimer = 0;
      this.wasScreenOpen = false;
      this.serverInputClear = false;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         boolean screenOpen = this.mc.currentScreen != null;
         if (this.wasScreenOpen && !screenOpen) {
            this.saveHotbar();
         }

         this.wasScreenOpen = screenOpen;
         if (!screenOpen) {
            if (!this.pauseUse.get() || !this.mc.player.isUsingItem()) {
               if (!ReviveClient.SWAP.isSilentSwapActive() && !ReviveClient.SWAP.isMultiTickSwapActive()) {
                  if (!PbState.playerSwapHeld) {
                     if (this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        if (this.checkTimer > 0) {
                           this.checkTimer--;
                        } else {
                           this.checkTimer = this.checkDelay.get();
                           if (!this.grimSafe.get() || this.serverInputClear) {
                              if (!this.lockPickaxe.get() || !this.fixPickaxe()) {
                                 this.restoreOne();
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         PlayerInput input = player.input.playerInput;
         this.serverInputClear = !input.forward()
            && !input.backward()
            && !input.left()
            && !input.right()
            && !input.jump()
            && !input.sneak()
            && !input.sprint()
            && !player.isSprinting();
      }
   }

   private void saveHotbar() {
      for (int i = 0; i < 9; i++) {
         this.hotbarSnapshot[i] = this.mc.player.getInventory().getStack(i).copy();
      }
   }

   private boolean fixPickaxe() {
      int slot = this.pickaxeSlot.get() - 1;
      ItemStack current = this.mc.player.getInventory().getStack(slot);
      if (isPickaxe(current)) {
         this.hotbarSnapshot[slot] = current.copy();
         return false;
      } else if (this.ignoreTotems.get() && current.isOf(Items.TOTEM_OF_UNDYING)) {
         return false;
      } else {
         for (int i = 9; i < 36; i++) {
            if (isPickaxe(this.mc.player.getInventory().getStack(i))) {
               if (!this.swapIntoHotbar(i, slot)) {
                  return false;
               }

               this.hotbarSnapshot[slot] = this.mc.player.getInventory().getStack(slot).copy();
               return true;
            }
         }

         for (int ix = 0; ix < 9; ix++) {
            if (ix != slot && isPickaxe(this.mc.player.getInventory().getStack(ix))) {
               if (!this.swapIntoHotbar(ix, slot)) {
                  return false;
               }

               this.hotbarSnapshot[slot] = this.mc.player.getInventory().getStack(slot).copy();
               this.hotbarSnapshot[ix] = this.mc.player.getInventory().getStack(ix).copy();
               return true;
            }
         }

         return false;
      }
   }

   private void restoreOne() {
      int lockedPick = this.lockPickaxe.get() ? this.pickaxeSlot.get() - 1 : -1;

      for (int i = 0; i < 9; i++) {
         if (i != lockedPick) {
            ItemStack snapshot = this.hotbarSnapshot[i];
            if (!snapshot.isEmpty()) {
               ItemStack current = this.mc.player.getInventory().getStack(i);
               if (!ItemStack.areItemsEqual(current, snapshot) && (!this.ignoreTotems.get() || !current.isOf(Items.TOTEM_OF_UNDYING))) {
                  int source = this.findInMain(snapshot);
                  if (source != -1) {
                     this.swapIntoHotbar(source, i);
                     return;
                  }
               }
            }
         }
      }
   }

   private int findInMain(ItemStack wanted) {
      for (int i = 9; i < 36; i++) {
         if (ItemStack.areItemsEqual(this.mc.player.getInventory().getStack(i), wanted)) {
            return i;
         }
      }

      return -1;
   }

   private boolean swapIntoHotbar(int from, int hotbar) {
      if (!ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
         return false;
      } else {
         int slotId = from < 9 ? 36 + from : from;
         this.mc.interactionManager.clickSlot(this.mc.player.playerScreenHandler.syncId, slotId, hotbar, SlotActionType.SWAP, this.mc.player);
         return true;
      }
   }

   private static boolean isPickaxe(ItemStack stack) {
      return !stack.isEmpty() && stack.isIn(ItemTags.PICKAXES);
   }
}
