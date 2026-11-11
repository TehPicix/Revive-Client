package revivedevelopment.reviveclient.systems.modules.player;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientPlayerInteractionManager;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;

public class AutoEat extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgFood = this.settings.createGroup("Food");
   private final SettingGroup sgGapples = this.settings.createGroup("Golden Apples");
   private static final int HOTBAR_SIZE = 9;
   private static final int OFFHAND_SLOT = 45;
   private final Setting<Boolean> continueUntilFull = this.sgGeneral
      .add(new BoolSetting.Builder().name("eat-to-full").description("Continue eating food until you are completely full.").defaultValue(false).build());
   private final Setting<Boolean> swapBack = this.sgGeneral
      .add(new BoolSetting.Builder().name("swap-back").description("Swap back to your previous slot after eating.").defaultValue(true).build());
   private final Setting<Boolean> eatFood = this.sgFood
      .add(new BoolSetting.Builder().name("eat-food").description("Automatically eat food when hungry.").defaultValue(true).build());
   private final Setting<List<Item>> foodWhitelist = this.sgFood
      .add(
         new ItemListSetting.Builder()
            .name("foods")
            .description("Food items that may be eaten automatically.")
            .defaultValue(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.GOLDEN_CARROT, Items.COOKED_MUTTON, Items.BAKED_POTATO)
            .filter(item -> item.getComponents().contains(DataComponentTypes.FOOD))
            .visible(this.eatFood::get)
            .build()
      );
   private final Setting<Integer> hungerThreshold = this.sgFood
      .add(
         new IntSetting.Builder()
            .name("hunger-threshold")
            .description("Start eating food when hunger falls below this level.")
            .defaultValue(16)
            .min(1)
            .max(20)
            .sliderRange(1, 20)
            .visible(this.eatFood::get)
            .build()
      );
   private final Setting<Boolean> eatGapples = this.sgGapples
      .add(new BoolSetting.Builder().name("eat-gapples").description("Automatically eat golden apples when low.").defaultValue(true).build());
   private final Setting<Double> gappleHealthThreshold = this.sgGapples
      .add(
         new DoubleSetting.Builder()
            .name("min-health")
            .description("Eat a golden apple when health (with absorption) is at or below this value.")
            .defaultValue(12.0)
            .min(1.0)
            .max(36.0)
            .sliderRange(1.0, 36.0)
            .visible(this.eatGapples::get)
            .build()
      );
   private final Setting<Boolean> gappleOnFire = this.sgGapples
      .add(
         new BoolSetting.Builder()
            .name("on-fire")
            .description("Eat a golden apple whenever you start burning (skipped with Fire Resistance).")
            .defaultValue(true)
            .visible(this.eatGapples::get)
            .build()
      );
   private boolean eating;
   private AutoEat.EatMode eatMode = AutoEat.EatMode.NONE;
   private int currentSlot = -1;
   private boolean holdingSwap;
   private boolean lastBurning;

   public AutoEat() {
      super(Categories.Player, "auto-eat", "Automatically eats food or golden apples.");
   }

   public boolean isEating() {
      return this.eating;
   }

   @Override
   public void onDeactivate() {
      if (this.eating) {
         this.stopEating();
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (Utils.canUpdate() && this.mc.player != null) {
         boolean burning = this.mc.player.isOnFire();
         boolean burningStarted = !this.lastBurning && burning;
         if (this.eating) {
            if (this.currentSlot >= 0 && this.currentSlot != 45 && this.mc.player.getInventory().selectedSlot != this.currentSlot) {
               this.stopEating();
               this.lastBurning = burning;
               return;
            }

            if (!this.shouldContinue(burning) || !this.ensureValidItem()) {
               this.stopEating();
               this.lastBurning = burning;
               return;
            }

            this.eat();
         } else {
            if (this.shouldEatGapple(burningStarted)) {
               int slot = this.findGappleSlot();
               if (slot != -1) {
                  this.startEating(AutoEat.EatMode.GAPPLE, slot);
                  this.lastBurning = burning;
                  return;
               }
            }

            if (this.shouldEatFood()) {
               int slot = this.findFoodSlot();
               if (slot != -1) {
                  this.startEating(AutoEat.EatMode.FOOD, slot);
               }
            }
         }

         this.lastBurning = burning;
      }
   }

   private void startEating(AutoEat.EatMode mode, int slot) {
      this.eatMode = mode;
      this.currentSlot = slot;
      this.holdingSwap = false;
      if (slot != 45 && !this.bringToHand(slot)) {
         this.eatMode = AutoEat.EatMode.NONE;
         this.currentSlot = -1;
      } else {
         this.eat();
      }
   }

   private boolean bringToHand(int slot) {
      if (this.swapBack.get()) {
         ItemStack stack = this.mc.player.getInventory().getStack(slot);
         if (!ReviveClient.SWAP.beginSwap(new FindItemResult(slot, stack.getCount()), false)) {
            return false;
         } else {
            this.holdingSwap = true;
            return true;
         }
      } else if (ReviveClient.SWAP.isSlotLocked(slot)) {
         return false;
      } else {
         this.mc.player.getInventory().selectedSlot = slot;
         ((IClientPlayerInteractionManager)this.mc.interactionManager).revive$syncSelected();
         return true;
      }
   }

   private void stopEating() {
      if (this.eating || this.eatMode != AutoEat.EatMode.NONE) {
         this.mc.options.useKey.setPressed(false);
         this.eating = false;
         if (this.holdingSwap) {
            ReviveClient.SWAP.endSwap(false);
         }

         this.holdingSwap = false;
         this.currentSlot = -1;
         this.eatMode = AutoEat.EatMode.NONE;
      }
   }

   private void eat() {
      this.mc.options.useKey.setPressed(true);
      if (!this.mc.player.isUsingItem() && this.mc.interactionManager != null) {
         Hand hand = this.currentSlot == 45 ? Hand.OFF_HAND : Hand.MAIN_HAND;
         this.mc.interactionManager.interactItem(this.mc.player, hand);
      }

      this.eating = true;
   }

   private boolean shouldEatFood() {
      return !this.eatFood.get() ? false : this.mc.player.getHungerManager().getFoodLevel() <= this.hungerThreshold.get();
   }

   private boolean shouldEatGapple(boolean burningStarted) {
      if (!this.eatGapples.get()) {
         return false;
      } else {
         double health = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
         return health <= this.gappleHealthThreshold.get() ? true : this.gappleOnFire.get() && burningStarted && !this.hasFireResistance();
      }
   }

   private boolean shouldContinue(boolean burning) {
      return switch (this.eatMode) {
         case FOOD -> this.shouldContinueFood();
         case GAPPLE -> this.shouldContinueGapple(burning);
         default -> false;
      };
   }

   private boolean shouldContinueFood() {
      if (!this.eatFood.get()) {
         return false;
      } else {
         int hunger = this.mc.player.getHungerManager().getFoodLevel();
         return this.continueUntilFull.get() ? hunger < 20 : hunger <= this.hungerThreshold.get();
      }
   }

   private boolean shouldContinueGapple(boolean burning) {
      if (!this.eatGapples.get()) {
         return false;
      } else {
         double health = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
         return health <= this.gappleHealthThreshold.get() ? true : this.gappleOnFire.get() && burning && !this.hasFireResistance();
      }
   }

   private boolean hasFireResistance() {
      return this.mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
   }

   private boolean ensureValidItem() {
      return switch (this.eatMode) {
         case FOOD -> this.ensureItem(this::isFoodStack, this::findFoodSlot);
         case GAPPLE -> this.ensureItem(this::isGapple, this::findGappleSlot);
         default -> false;
      };
   }

   private boolean ensureItem(Predicate<ItemStack> valid, IntSupplier finder) {
      if (this.currentSlot != -1 && valid.test(this.stackInSlot(this.currentSlot))) {
         return true;
      } else {
         int nextSlot = finder.getAsInt();
         if (nextSlot == -1) {
            return false;
         } else {
            if (this.holdingSwap) {
               ReviveClient.SWAP.endSwap(false);
               this.holdingSwap = false;
            }

            this.currentSlot = nextSlot;
            return nextSlot == 45 ? true : this.bringToHand(nextSlot);
         }
      }
   }

   private ItemStack stackInSlot(int slot) {
      return slot == 45 ? this.mc.player.getOffHandStack() : this.mc.player.getInventory().getStack(slot);
   }

   private int findFoodSlot() {
      int slot = -1;
      int bestNutrition = -1;
      float bestSaturation = -1.0F;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (this.isFoodStack(stack)) {
            FoodComponent food = (FoodComponent)stack.get(DataComponentTypes.FOOD);
            if (food != null && (food.nutrition() > bestNutrition || food.nutrition() == bestNutrition && food.saturation() > bestSaturation)) {
               slot = i;
               bestNutrition = food.nutrition();
               bestSaturation = food.saturation();
            }
         }
      }

      ItemStack offHand = this.mc.player.getOffHandStack();
      if (this.isFoodStack(offHand)) {
         FoodComponent food = (FoodComponent)offHand.get(DataComponentTypes.FOOD);
         if (food != null && (food.nutrition() > bestNutrition || food.nutrition() == bestNutrition && food.saturation() > bestSaturation)) {
            slot = 45;
         }
      }

      return slot;
   }

   private int findGappleSlot() {
      int goldenAppleSlot = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (!stack.isEmpty()) {
            if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
               return i;
            }

            if (stack.isOf(Items.GOLDEN_APPLE) && goldenAppleSlot == -1) {
               goldenAppleSlot = i;
            }
         }
      }

      ItemStack offHand = this.mc.player.getOffHandStack();
      if (offHand.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
         return 45;
      } else {
         return offHand.isOf(Items.GOLDEN_APPLE) && goldenAppleSlot == -1 ? 45 : goldenAppleSlot;
      }
   }

   private boolean isFoodStack(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return !this.foodWhitelist.get().contains(stack.getItem()) ? false : stack.getComponents().contains(DataComponentTypes.FOOD);
      }
   }

   private boolean isGapple(ItemStack stack) {
      return stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
   }

   private static enum EatMode {
      NONE,
      FOOD,
      GAPPLE;
   }
}
