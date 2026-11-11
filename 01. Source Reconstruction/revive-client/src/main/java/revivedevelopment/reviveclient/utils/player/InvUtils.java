package revivedevelopment.reviveclient.utils.player;

import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IClientPlayerInteractionManager;

public class InvUtils {
   private static final InvUtils.Action ACTION = new InvUtils.Action();
   public static int previousSlot = -1;

   private InvUtils() {
   }

   public static boolean testInMainHand(Predicate<ItemStack> predicate) {
      return predicate.test(ReviveClient.mc.player.getMainHandStack());
   }

   public static boolean testInMainHand(Item... items) {
      return testInMainHand(itemStack -> {
         for (Item item : items) {
            if (itemStack.isOf(item)) {
               return true;
            }
         }

         return false;
      });
   }

   public static boolean testInOffHand(Predicate<ItemStack> predicate) {
      return predicate.test(ReviveClient.mc.player.getOffHandStack());
   }

   public static boolean testInOffHand(Item... items) {
      return testInOffHand(itemStack -> {
         for (Item item : items) {
            if (itemStack.isOf(item)) {
               return true;
            }
         }

         return false;
      });
   }

   public static boolean testInHands(Predicate<ItemStack> predicate) {
      return testInMainHand(predicate) || testInOffHand(predicate);
   }

   public static boolean testInHands(Item... items) {
      return testInMainHand(items) || testInOffHand(items);
   }

   public static boolean testInHotbar(Predicate<ItemStack> predicate) {
      if (testInHands(predicate)) {
         return true;
      } else {
         for (int i = 0; i < 8; i++) {
            ItemStack stack = ReviveClient.mc.player.getInventory().getStack(i);
            if (predicate.test(stack)) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean testInHotbar(Item... items) {
      return testInHotbar(itemStack -> {
         for (Item item : items) {
            if (itemStack.isOf(item)) {
               return true;
            }
         }

         return false;
      });
   }

   public static FindItemResult findEmpty() {
      return find(ItemStack::isEmpty);
   }

   public static FindItemResult findInHotbar(Item... items) {
      return findInHotbar(itemStack -> {
         for (Item item : items) {
            if (itemStack.getItem() == item) {
               return true;
            }
         }

         return false;
      });
   }

   public static FindItemResult findInHotbar(Predicate<ItemStack> isGood) {
      if (testInOffHand(isGood)) {
         return new FindItemResult(45, ReviveClient.mc.player.getOffHandStack().getCount());
      } else {
         return testInMainHand(isGood)
            ? new FindItemResult(ReviveClient.mc.player.getInventory().selectedSlot, ReviveClient.mc.player.getMainHandStack().getCount())
            : find(isGood, 0, 8);
      }
   }

   public static FindItemResult find(Item... items) {
      return find(itemStack -> {
         for (Item item : items) {
            if (itemStack.getItem() == item) {
               return true;
            }
         }

         return false;
      });
   }

   public static FindItemResult find(Predicate<ItemStack> isGood) {
      return ReviveClient.mc.player == null ? new FindItemResult(0, 0) : find(isGood, 0, 35);
   }

   public static FindItemResult find(Predicate<ItemStack> isGood, int start, int end) {
      if (ReviveClient.mc.player == null) {
         return new FindItemResult(0, 0);
      } else {
         int slot = -1;
         int count = 0;

         for (int i = start; i <= end; i++) {
            ItemStack stack = ReviveClient.mc.player.getInventory().getStack(i);
            if (isGood.test(stack)) {
               if (slot == -1) {
                  slot = i;
               }

               count += stack.getCount();
            }
         }

         return new FindItemResult(slot, count);
      }
   }

   public static FindItemResult findFastestToolHotbar(BlockState state) {
      float bestScore = 1.0F;
      int slot = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = ReviveClient.mc.player.getInventory().getStack(i);
         if (stack.isSuitableFor(state)) {
            float score = stack.getMiningSpeedMultiplier(state);
            if (score > bestScore) {
               bestScore = score;
               slot = i;
            }
         }
      }

      return new FindItemResult(slot, 1);
   }

   public static FindItemResult findFastestTool(BlockState state) {
      float bestScore = 1.0F;
      int slot = -1;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = ReviveClient.mc.player.getInventory().getStack(i);
         if (stack.isSuitableFor(state)) {
            float score = stack.getMiningSpeedMultiplier(state);
            if (score > bestScore) {
               bestScore = score;
               slot = i;
            }
         }
      }

      return new FindItemResult(slot, 1);
   }

   public static boolean swap(int slot, boolean swapBack) {
      if (slot == 45) {
         return true;
      } else if (slot >= 0 && slot <= 8) {
         if (swapBack && previousSlot == -1) {
            previousSlot = ReviveClient.mc.player.getInventory().selectedSlot;
         } else if (!swapBack) {
            previousSlot = -1;
         }

         ReviveClient.mc.player.getInventory().selectedSlot = slot;
         ((IClientPlayerInteractionManager)ReviveClient.mc.interactionManager).revive$syncSelected();
         return true;
      } else {
         return false;
      }
   }

   public static boolean swapBack() {
      if (previousSlot == -1) {
         return false;
      } else {
         boolean return_ = swap(previousSlot, false);
         previousSlot = -1;
         return return_;
      }
   }

   public static InvUtils.Action move() {
      ACTION.type = SlotActionType.PICKUP;
      ACTION.two = true;
      return ACTION;
   }

   public static InvUtils.Action click() {
      ACTION.type = SlotActionType.PICKUP;
      return ACTION;
   }

   public static InvUtils.Action quickSwap() {
      ACTION.type = SlotActionType.SWAP;
      return ACTION;
   }

   public static InvUtils.Action shiftClick() {
      ACTION.type = SlotActionType.QUICK_MOVE;
      return ACTION;
   }

   public static InvUtils.Action drop() {
      ACTION.type = SlotActionType.THROW;
      ACTION.data = 1;
      return ACTION;
   }

   public static void dropHand() {
      if (!ReviveClient.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
         ReviveClient.mc
            .interactionManager
            .clickSlot(ReviveClient.mc.player.currentScreenHandler.syncId, -999, 0, SlotActionType.PICKUP, ReviveClient.mc.player);
      }
   }

   public static class Action {
      private SlotActionType type = null;
      private boolean two = false;
      private int from = -1;
      private int to = -1;
      private int data = 0;
      private boolean isRecursive = false;

      private Action() {
      }

      public InvUtils.Action fromId(int id) {
         this.from = id;
         return this;
      }

      public InvUtils.Action from(int index) {
         return this.fromId(SlotUtils.indexToId(index));
      }

      public InvUtils.Action fromHotbar(int i) {
         return this.from(0 + i);
      }

      public InvUtils.Action fromOffhand() {
         return this.from(45);
      }

      public InvUtils.Action fromMain(int i) {
         return this.from(9 + i);
      }

      public InvUtils.Action fromArmor(int i) {
         return this.from(36 + (3 - i));
      }

      public void toId(int id) {
         this.to = id;
         this.run();
      }

      public void to(int index) {
         this.toId(SlotUtils.indexToId(index));
      }

      public void toHotbar(int i) {
         this.to(0 + i);
      }

      public void toOffhand() {
         this.to(45);
      }

      public void toMain(int i) {
         this.to(9 + i);
      }

      public void toArmor(int i) {
         this.to(36 + (3 - i));
      }

      public void slotId(int id) {
         this.from = this.to = id;
         this.run();
      }

      public void slot(int index) {
         this.slotId(SlotUtils.indexToId(index));
      }

      public void slotHotbar(int i) {
         this.slot(0 + i);
      }

      public void slotOffhand() {
         this.slot(45);
      }

      public void slotMain(int i) {
         this.slot(9 + i);
      }

      public void slotArmor(int i) {
         this.slot(36 + (3 - i));
      }

      private void run() {
         boolean hadEmptyCursor = ReviveClient.mc.player.currentScreenHandler.getCursorStack().isEmpty();
         if (this.type == SlotActionType.SWAP) {
            ReviveClient.mc
               .interactionManager
               .clickSlot(ReviveClient.mc.player.playerScreenHandler.syncId, this.to, this.from, this.type, ReviveClient.mc.player);
         } else {
            if (this.type != null && this.from != -1 && this.to != -1) {
               this.click(this.from);
               if (this.two) {
                  this.click(this.to);
               }
            }

            SlotActionType preType = this.type;
            boolean preTwo = this.two;
            int preFrom = this.from;
            int preTo = this.to;
            this.type = null;
            this.two = false;
            this.from = -1;
            this.to = -1;
            this.data = 0;
            if (!this.isRecursive
               && hadEmptyCursor
               && preType == SlotActionType.PICKUP
               && preTwo
               && preFrom != -1
               && preTo != -1
               && !ReviveClient.mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
               this.isRecursive = true;
               InvUtils.click().slotId(preFrom);
               this.isRecursive = false;
            }
         }
      }

      private void click(int id) {
         ReviveClient.mc.interactionManager.clickSlot(ReviveClient.mc.player.currentScreenHandler.syncId, id, this.data, this.type, ReviveClient.mc.player);
      }
   }
}
