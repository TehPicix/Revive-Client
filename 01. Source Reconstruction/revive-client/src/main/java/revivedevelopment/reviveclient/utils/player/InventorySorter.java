package revivedevelopment.reviveclient.utils.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Pair;
import revivedevelopment.reviveclient.mixininterface.ISlot;

public class InventorySorter {
   private final HandledScreen<?> screen;
   private final InventorySorter.InvPart originInvPart;
   private boolean invalid;
   private List<InventorySorter.Action> actions;
   private int timer;
   private int currentActionI;

   public InventorySorter(HandledScreen<?> screen, Slot originSlot) {
      this.screen = screen;
      this.originInvPart = this.getInvPart(originSlot);
      if (this.originInvPart != InventorySorter.InvPart.Invalid && this.originInvPart != InventorySorter.InvPart.Hotbar) {
         this.actions = new ArrayList<>();
         this.generateActions();
      } else {
         this.invalid = true;
      }
   }

   public boolean tick(int delay) {
      if (this.invalid) {
         return true;
      } else if (this.currentActionI >= this.actions.size()) {
         return true;
      } else if (this.timer >= delay) {
         this.timer = 0;
         InventorySorter.Action action = this.actions.get(this.currentActionI);
         InvUtils.move().fromId(action.from).toId(action.to);
         this.currentActionI++;
         return false;
      } else {
         this.timer++;
         return false;
      }
   }

   private void generateActions() {
      List<InventorySorter.MySlot> slots = new ArrayList<>();

      for (Slot slot : this.screen.getScreenHandler().slots) {
         if (this.getInvPart(slot) == this.originInvPart) {
            slots.add(new InventorySorter.MySlot(((ISlot)slot).revive$getId(), slot.getStack()));
         }
      }

      slots.sort(Comparator.comparingInt(value -> value.id));
      this.generateStackingActions(slots);
      this.generateSortingActions(slots);
   }

   private void generateStackingActions(List<InventorySorter.MySlot> slots) {
      InventorySorter.SlotMap slotMap = new InventorySorter.SlotMap();

      for (InventorySorter.MySlot slot : slots) {
         if (!slot.itemStack.isEmpty() && slot.itemStack.isStackable() && slot.itemStack.getCount() < slot.itemStack.getMaxCount()) {
            slotMap.get(slot.itemStack).add(slot);
         }
      }

      for (Pair<ItemStack, List<InventorySorter.MySlot>> entry : slotMap.map) {
         List<InventorySorter.MySlot> slotsToStack = (List<InventorySorter.MySlot>)entry.getRight();
         InventorySorter.MySlot slotToStackTo = null;

         for (int i = 0; i < slotsToStack.size(); i++) {
            InventorySorter.MySlot slotx = slotsToStack.get(i);
            if (slotToStackTo == null) {
               slotToStackTo = slotx;
            } else {
               this.actions.add(new InventorySorter.Action(slotx.id, slotToStackTo.id));
               if (slotToStackTo.itemStack.getCount() + slotx.itemStack.getCount() <= slotToStackTo.itemStack.getMaxCount()) {
                  slotToStackTo.itemStack = new ItemStack(slotToStackTo.itemStack.getItem(), slotToStackTo.itemStack.getCount() + slotx.itemStack.getCount());
                  slotx.itemStack = ItemStack.EMPTY;
                  if (slotToStackTo.itemStack.getCount() >= slotToStackTo.itemStack.getMaxCount()) {
                     slotToStackTo = null;
                  }
               } else {
                  int needed = slotToStackTo.itemStack.getMaxCount() - slotToStackTo.itemStack.getCount();
                  slotToStackTo.itemStack = new ItemStack(slotToStackTo.itemStack.getItem(), slotToStackTo.itemStack.getMaxCount());
                  slotx.itemStack = new ItemStack(slotx.itemStack.getItem(), slotx.itemStack.getCount() - needed);
                  slotToStackTo = null;
                  i--;
               }
            }
         }
      }
   }

   private void generateSortingActions(List<InventorySorter.MySlot> slots) {
      for (int i = 0; i < slots.size(); i++) {
         InventorySorter.MySlot bestSlot = null;

         for (int j = i; j < slots.size(); j++) {
            InventorySorter.MySlot slot = slots.get(j);
            if (bestSlot == null) {
               bestSlot = slot;
            } else if (this.isSlotBetter(bestSlot, slot)) {
               bestSlot = slot;
            }
         }

         if (!bestSlot.itemStack.isEmpty()) {
            InventorySorter.MySlot toSlot = slots.get(i);
            int from = bestSlot.id;
            int to = toSlot.id;
            if (from != to) {
               ItemStack temp = bestSlot.itemStack;
               bestSlot.itemStack = toSlot.itemStack;
               toSlot.itemStack = temp;
               this.actions.add(new InventorySorter.Action(from, to));
            }
         }
      }
   }

   private boolean isSlotBetter(InventorySorter.MySlot best, InventorySorter.MySlot slot) {
      ItemStack bestI = best.itemStack;
      ItemStack slotI = slot.itemStack;
      if (bestI.isEmpty() && !slotI.isEmpty()) {
         return true;
      } else if (!bestI.isEmpty() && slotI.isEmpty()) {
         return false;
      } else {
         int c = Registries.ITEM.getId(bestI.getItem()).compareTo(Registries.ITEM.getId(slotI.getItem()));
         if (c == 0) {
            if (slotI.getCount() != bestI.getCount()) {
               return slotI.getCount() > bestI.getCount();
            }

            if (slotI.getDamage() != bestI.getDamage()) {
               return slotI.getDamage() > bestI.getDamage();
            }
         }

         return c > 0;
      }
   }

   private InventorySorter.InvPart getInvPart(Slot slot) {
      int i = ((ISlot)slot).revive$getIndex();
      if (!(slot.inventory instanceof PlayerInventory) || this.screen instanceof CreativeInventoryScreen && ((ISlot)slot).revive$getId() <= 8) {
         if ((this.screen instanceof GenericContainerScreen || this.screen instanceof ShulkerBoxScreen) && slot.inventory instanceof SimpleInventory) {
            return InventorySorter.InvPart.Main;
         }
      } else {
         if (SlotUtils.isHotbar(i)) {
            return InventorySorter.InvPart.Hotbar;
         }

         if (SlotUtils.isMain(i)) {
            return InventorySorter.InvPart.Player;
         }
      }

      return InventorySorter.InvPart.Invalid;
   }

   private record Action(int from, int to) {
   }

   private static enum InvPart {
      Hotbar,
      Player,
      Main,
      Invalid;
   }

   private static class MySlot {
      public final int id;
      public ItemStack itemStack;

      public MySlot(int id, ItemStack itemStack) {
         this.id = id;
         this.itemStack = itemStack;
      }
   }

   private static class SlotMap {
      private final List<Pair<ItemStack, List<InventorySorter.MySlot>>> map = new ArrayList<>();

      public List<InventorySorter.MySlot> get(ItemStack itemStack) {
         for (Pair<ItemStack, List<InventorySorter.MySlot>> entry : this.map) {
            if (ItemStack.areItemsAndComponentsEqual(itemStack, (ItemStack)entry.getLeft())) {
               return (List<InventorySorter.MySlot>)entry.getRight();
            }
         }

         List<InventorySorter.MySlot> list = new ArrayList<>();
         this.map.add(new Pair(itemStack, list));
         return list;
      }
   }
}
