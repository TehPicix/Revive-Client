package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.PlayerInput;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.input.Input;

public class AutoReplenish extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> threshold = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("threshold")
            .description("Refills a hotbar stack when its count drops to or below this.")
            .defaultValue(16)
            .min(1)
            .max(63)
            .sliderRange(1, 63)
            .build()
      );
   private final Setting<Integer> threshold16 = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("threshold-16")
            .description("Refill threshold for items that only stack to 16 (pearls, eggs, snowballs, honey...).")
            .defaultValue(4)
            .min(1)
            .max(15)
            .sliderRange(1, 15)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("delay").description("Ticks to wait between refills.").defaultValue(2).min(0).max(20).sliderRange(0, 20).build());
   private final Setting<Boolean> selected = this.sgGeneral
      .add(new BoolSetting.Builder().name("selected").description("Also refill the slot you're currently holding.").defaultValue(true).build());
   private final Setting<Boolean> pauseUse = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-use")
            .description("Don't refill while you're using an item (eating, blocking, drawing a bow, etc.).")
            .defaultValue(true)
            .build()
      );
   private final SettingGroup sgGrim = this.settings.createGroup("Grim");
   private final Setting<Boolean> grim = this.sgGrim
      .add(
         new BoolSetting.Builder()
            .name("grim")
            .description("Pauses movement around each refill so Grim accepts the clicks even while you're running.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> resumeDelay = this.sgGrim
      .add(
         new IntSetting.Builder()
            .name("resume-delay")
            .description("How many ticks after the last refill movement resumes.")
            .defaultValue(4)
            .min(0)
            .sliderMax(20)
            .visible(this.grim::get)
            .build()
      );
   private int delayLeft;
   private boolean grimPaused;
   private boolean grimServerClear;
   private boolean grimWasSprinting;
   private int grimResumeTimer;
   private int grimWaitTimeout;
   private static final int GRIM_WAIT_TIMEOUT = 10;

   public AutoReplenish() {
      super(Categories.Player, "auto-replenish", "Refills low hotbar stacks from your inventory.");
   }

   @Override
   public void onActivate() {
      this.delayLeft = 0;
      this.grimPaused = false;
      this.grimServerClear = false;
      this.grimWasSprinting = false;
      this.grimResumeTimer = 0;
      this.grimWaitTimeout = 0;
   }

   @Override
   public void onDeactivate() {
      if (this.grimPaused) {
         this.resumeGrim();
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.interactionManager != null && this.mc.currentScreen == null) {
         if (!this.pauseUse.get() || !this.mc.player.isUsingItem()) {
            if (this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
               if (this.delayLeft > 0) {
                  this.delayLeft--;
               } else {
                  AutoReplenish.Refill refill = this.findRefill();
                  if (refill == null) {
                     if (this.grimPaused && --this.grimResumeTimer <= 0) {
                        this.resumeGrim();
                     }
                  } else {
                     if (this.grim.get() && !this.grimServerClear) {
                        this.engageGrimPause();
                        if (++this.grimWaitTimeout <= 10) {
                           return;
                        }
                     }

                     if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                        this.mergeIntoSlot(refill.source(), refill.slot());
                        this.delayLeft = this.delay.get();
                        this.grimWaitTimeout = 0;
                        if (this.grimPaused) {
                           this.grimResumeTimer = this.resumeDelay.get();
                        }
                     }
                  }
               }
            }
         }
      } else {
         if (this.grimPaused) {
            this.resumeGrim();
         }
      }
   }

   @EventHandler
   private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         PlayerInput input = player.input.playerInput;
         this.grimServerClear = !input.forward()
            && !input.backward()
            && !input.left()
            && !input.right()
            && !input.jump()
            && !input.sneak()
            && !input.sprint()
            && !player.isSprinting();
      }
   }

   private void engageGrimPause() {
      if (!this.grimPaused) {
         this.grimPaused = true;
         this.grimWaitTimeout = 0;
         this.grimResumeTimer = this.resumeDelay.get();
         this.grimWasSprinting = this.mc.player.isSprinting();
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.jumpKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         this.mc.options.sprintKey.setPressed(false);
         this.mc.player.setSprinting(false);
      }
   }

   private void resumeGrim() {
      this.grimPaused = false;
      this.grimResumeTimer = 0;
      this.grimWaitTimeout = 0;
      boolean restoreSprint = this.grimWasSprinting;
      this.grimWasSprinting = false;
      if (this.mc.currentScreen == null) {
         this.mc.options.forwardKey.setPressed(Input.isPressed(this.mc.options.forwardKey));
         this.mc.options.backKey.setPressed(Input.isPressed(this.mc.options.backKey));
         this.mc.options.leftKey.setPressed(Input.isPressed(this.mc.options.leftKey));
         this.mc.options.rightKey.setPressed(Input.isPressed(this.mc.options.rightKey));
         this.mc.options.jumpKey.setPressed(Input.isPressed(this.mc.options.jumpKey));
         this.mc.options.sneakKey.setPressed(Input.isPressed(this.mc.options.sneakKey));
         this.mc.options.sprintKey.setPressed(Input.isPressed(this.mc.options.sprintKey));
         if (restoreSprint && this.mc.player != null && Input.isPressed(this.mc.options.forwardKey)) {
            this.mc.player.setSprinting(true);
         }
      }
   }

   private AutoReplenish.Refill findRefill() {
      int selectedSlot = this.mc.player.getInventory().getSelectedSlot();

      for (int slot = 0; slot <= 8; slot++) {
         if (this.selected.get() || slot != selectedSlot) {
            ItemStack stack = this.mc.player.getInventory().getStack(slot);
            if (this.isLow(stack)) {
               int source = this.findSource(stack);
               if (source != -1) {
                  return new AutoReplenish.Refill(slot, source);
               }
            }
         }
      }

      return null;
   }

   private void mergeIntoSlot(int source, int hotbarSlot) {
      int syncId = this.mc.player.currentScreenHandler.syncId;
      int target = hotbarSlot + 36;
      this.mc.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, this.mc.player);
      this.mc.interactionManager.clickSlot(syncId, target, 0, SlotActionType.PICKUP, this.mc.player);
      if (!this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
         this.mc.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, this.mc.player);
      }
   }

   private boolean isLow(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && stack.getMaxCount() > 1) {
         int limit = stack.getMaxCount() <= 16 ? this.threshold16.get() : this.threshold.get();
         return stack.getCount() <= limit && stack.getCount() < stack.getMaxCount();
      } else {
         return false;
      }
   }

   private int findSource(ItemStack target) {
      for (int slot = 9; slot <= 35; slot++) {
         ItemStack stack = this.mc.player.getInventory().getStack(slot);
         if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, target)) {
            return slot;
         }
      }

      return -1;
   }

   private record Refill(int slot, int source) {
   }
}
