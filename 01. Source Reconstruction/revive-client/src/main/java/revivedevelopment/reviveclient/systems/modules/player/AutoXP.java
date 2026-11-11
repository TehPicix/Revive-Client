package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class AutoXP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRepair = this.settings.createGroup("Auto Repair");
   private static final String ROTATION_ID = "auto-xp";
   private static final int THROWS_PER_BATCH = 9;
   private static final long THROW_INTERVAL_MS = 300L;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private final Setting<Keybind> throwBind = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("throw-bind")
            .description("Toggle to throw experience bottles until everything is fully mended.")
            .defaultValue(Keybind.none())
            .action(this::toggleThrow)
            .build()
      );
   private final Setting<AutoXP.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("STREAM = one bottle every tick. GROUP = a batch of 9 every 300ms."))
               .defaultValue(AutoXP.Mode.STREAM))
            .build()
      );
   private final Setting<Integer> preferredSlot = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("preferred-slot")
            .description("Hotbar slot (1-9) to use when bottles have to be moved out of the main inventory.")
            .defaultValue(8)
            .min(1)
            .max(9)
            .sliderRange(1, 9)
            .build()
      );
   private final Setting<Boolean> autoRepair = this.sgRepair
      .add(
         new BoolSetting.Builder()
            .name("auto-repair")
            .description("Automatically repairs armor and hand items with Mending using experience bottles.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> minThreshold = this.sgRepair
      .add(
         new IntSetting.Builder()
            .name("min-threshold")
            .description("Begin repairing when durability falls below this %.")
            .defaultValue(30)
            .min(1)
            .max(100)
            .sliderRange(1, 100)
            .visible(this.autoRepair::get)
            .build()
      );
   private final Setting<Integer> maxThreshold = this.sgRepair
      .add(
         new IntSetting.Builder()
            .name("max-threshold")
            .description("Stop repairing when durability exceeds this %.")
            .defaultValue(80)
            .min(1)
            .max(100)
            .sliderRange(1, 100)
            .visible(this.autoRepair::get)
            .build()
      );
   private int repairingSlot = -1;
   private boolean throwToggled;
   private long lastThrowMs;
   private int relocatedSourceSlot = -1;
   private int relocatedToHotbar = -1;

   public AutoXP() {
      super(Categories.Player, "auto-xp", "Auto-mend: throws XP bottles at your feet to repair Mending gear.");
   }

   @Override
   public void onActivate() {
      this.repairingSlot = -1;
      this.throwToggled = false;
      this.lastThrowMs = 0L;
      this.relocatedSourceSlot = -1;
      this.relocatedToHotbar = -1;
   }

   @Override
   public void onDeactivate() {
      this.throwToggled = false;
      this.restoreRelocatedStack();
   }

   @Override
   public String getInfoString() {
      return !this.throwToggled ? null : this.calculateTotalXpNeeded() + " XP";
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.world != null) {
         if (this.throwToggled) {
            this.tickThrowToggle();
         } else if (!this.autoRepair.get()) {
            this.repairingSlot = -1;
         } else {
            if (this.repairingSlot == -1) {
               this.findRepairTarget(player);
            }

            if (this.repairingSlot != -1) {
               ItemStack repairing = this.getStackByRepairSlot(player, this.repairingSlot);
               if (!this.needsRepair(repairing, this.maxThreshold.get().intValue())) {
                  this.repairingSlot = -1;
               } else if (this.shouldThrowNow()) {
                  this.throwXpBottles(this.getThrowAmount(), false);
               }
            }
         }
      }
   }

   private void toggleThrow() {
      if (this.mc.player != null && this.mc.world != null) {
         this.throwToggled = !this.throwToggled;
         if (this.throwToggled) {
            this.lastThrowMs = 0L;
            this.info("Mending... (%d XP required)", new Object[]{this.calculateTotalXpNeeded()});
         } else {
            this.restoreRelocatedStack();
            this.info("Throw disabled.", new Object[0]);
         }
      }
   }

   private void tickThrowToggle() {
      if (this.calculateTotalXpNeeded() <= 0) {
         this.throwToggled = false;
         this.restoreRelocatedStack();
         this.info("Mending complete.", new Object[0]);
      } else if (!InvUtils.find(Items.EXPERIENCE_BOTTLE).found()) {
         this.throwToggled = false;
         this.restoreRelocatedStack();
         this.info("No XP bottles remaining.", new Object[0]);
      } else if (this.shouldThrowNow()) {
         this.throwXpBottles(this.getThrowAmount(), true);
      }
   }

   private int calculateTotalXpNeeded() {
      ClientPlayerEntity player = this.mc.player;
      if (player == null) {
         return 0;
      } else {
         int total = 0;

         for (EquipmentSlot slot : ARMOR_SLOTS) {
            total += this.xpForItem(player.getEquippedStack(slot));
         }

         total += this.xpForItem(player.getMainHandStack());
         return total + this.xpForItem(player.getOffHandStack());
      }
   }

   private int xpForItem(ItemStack stack) {
      if (stack.isEmpty() || !stack.isDamageable()) {
         return 0;
      } else if (Utils.getEnchantmentLevel(stack, Enchantments.MENDING) <= 0) {
         return 0;
      } else {
         int damage = stack.getDamage();
         return damage <= 0 ? 0 : (int)Math.ceil(damage / 2.0);
      }
   }

   private boolean needsRepair(ItemStack stack, double threshold) {
      if (stack.isEmpty() || Utils.getEnchantmentLevel(stack, Enchantments.MENDING) <= 0) {
         return false;
      } else if (stack.getMaxDamage() <= 0) {
         return false;
      } else {
         double durabilityPct = (double)(stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage() * 100.0;
         return durabilityPct <= threshold;
      }
   }

   private void findRepairTarget(ClientPlayerEntity player) {
      for (int i = 0; i < ARMOR_SLOTS.length; i++) {
         if (this.needsRepair(player.getEquippedStack(ARMOR_SLOTS[i]), this.minThreshold.get().intValue())) {
            this.repairingSlot = i;
            return;
         }
      }

      if (this.needsRepair(player.getMainHandStack(), this.minThreshold.get().intValue())) {
         this.repairingSlot = 100;
      } else {
         if (this.needsRepair(player.getOffHandStack(), this.minThreshold.get().intValue())) {
            this.repairingSlot = 101;
         }
      }
   }

   private ItemStack getStackByRepairSlot(ClientPlayerEntity player, int slot) {
      if (slot >= 0 && slot < ARMOR_SLOTS.length) {
         return player.getEquippedStack(ARMOR_SLOTS[slot]);
      } else if (slot == 100) {
         return player.getMainHandStack();
      } else {
         return slot == 101 ? player.getOffHandStack() : ItemStack.EMPTY;
      }
   }

   private boolean shouldThrowNow() {
      if (this.mode.get() == AutoXP.Mode.STREAM) {
         return true;
      } else {
         long now = System.currentTimeMillis();
         if (now - this.lastThrowMs < 300L) {
            return false;
         } else {
            this.lastThrowMs = now;
            return true;
         }
      }
   }

   private int getThrowAmount() {
      return this.mode.get() == AutoXP.Mode.STREAM ? 1 : 9;
   }

   private void throwXpBottles(int amount, boolean keepRelocated) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.world != null && this.mc.getNetworkHandler() != null) {
         if (!player.isUsingItem()) {
            if (ReviveClient.PACKET.canSend(PacketManager.Category.GLOBAL)) {
               FindItemResult bottles = InvUtils.find(Items.EXPERIENCE_BOTTLE);
               if (bottles.found()) {
                  if (bottles.isOffhand()) {
                     this.sendThrows(player, Hand.OFF_HAND, amount);
                  } else {
                     int tempHotbar = -1;
                     int tempSource = -1;
                     if (!bottles.isHotbar()) {
                        int hotbarSlot = keepRelocated && this.relocatedToHotbar != -1 ? this.relocatedToHotbar : this.pickHotbarSlot(player);
                        if (hotbarSlot == -1) {
                           return;
                        }

                        if (keepRelocated) {
                           if (this.relocatedSourceSlot == -1) {
                              this.relocatedSourceSlot = bottles.slot();
                           }

                           this.relocatedToHotbar = hotbarSlot;
                        } else {
                           tempHotbar = hotbarSlot;
                           tempSource = bottles.slot();
                        }

                        InvUtils.quickSwap().fromId(hotbarSlot).to(bottles.slot());
                        ItemStack stack = player.getInventory().getStack(hotbarSlot);
                        bottles = new FindItemResult(hotbarSlot, stack.getCount());
                     }

                     if (ReviveClient.SWAP.beginSwap(bottles, true)) {
                        try {
                           this.sendThrows(player, Hand.MAIN_HAND, amount);
                        } finally {
                           ReviveClient.SWAP.endSwap(true);
                        }
                     }

                     if (tempHotbar != -1) {
                        InvUtils.quickSwap().fromId(tempHotbar).to(tempSource);
                     }
                  }
               }
            }
         }
      }
   }

   private void sendThrows(ClientPlayerEntity player, Hand hand, int amount) {
      float yaw = player.getYaw();
      float pitch = this.feetPitch(player);
      ReviveClient.ROTATION.requestSilentRotation("auto-xp", 100.0, yaw, pitch);

      for (int i = 0; i < amount; i++) {
         this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, this.nextSequence(), yaw, pitch));
      }

      player.swingHand(hand);
   }

   private float feetPitch(ClientPlayerEntity player) {
      float[] rot = ReviveClient.ROTATION.getRotation(player.getEyePos(), player.getEntityPos());
      return rot[1];
   }

   private int nextSequence() {
      return ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
   }

   private int pickHotbarSlot(ClientPlayerEntity player) {
      int selected = player.getInventory().selectedSlot;

      for (int i = 0; i < 9; i++) {
         if (i != selected) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(Items.TOTEM_OF_UNDYING) && stack.isEmpty()) {
               return i;
            }
         }
      }

      int preferredIndex = this.preferredSlot.get() - 1;
      if (this.isHotbarSlotUsable(player, preferredIndex)) {
         return preferredIndex;
      } else {
         for (int ix = 0; ix < 9; ix++) {
            if (this.isHotbarSlotUsable(player, ix)) {
               return ix;
            }
         }

         return -1;
      }
   }

   private boolean isHotbarSlotUsable(ClientPlayerEntity player, int slot) {
      if (slot < 0 || slot > 8) {
         return false;
      } else {
         return slot == player.getInventory().selectedSlot ? false : !player.getInventory().getStack(slot).isOf(Items.TOTEM_OF_UNDYING);
      }
   }

   private void restoreRelocatedStack() {
      if (this.mc.player != null) {
         if (this.relocatedSourceSlot != -1 && this.relocatedToHotbar != -1) {
            InvUtils.quickSwap().fromId(this.relocatedToHotbar).to(this.relocatedSourceSlot);
            this.relocatedSourceSlot = -1;
            this.relocatedToHotbar = -1;
         } else {
            this.relocatedSourceSlot = -1;
            this.relocatedToHotbar = -1;
         }
      }
   }

   public static enum Mode {
      GROUP,
      STREAM;
   }
}
