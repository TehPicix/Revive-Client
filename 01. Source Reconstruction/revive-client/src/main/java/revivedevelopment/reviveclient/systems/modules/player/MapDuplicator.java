package revivedevelopment.reviveclient.systems.modules.player;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.SlotUtils;

public class MapDuplicator extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgLimits = this.settings.createGroup("Limits");
   private final Setting<Boolean> showStatus = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-status").description("Show status messages in chat.").defaultValue(true).build());
   private final Setting<Boolean> silentCrafting = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("silent-crafting")
            .description("Allow crafting without opening inventory (may not work on all servers).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> craftingLoops = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("crafting-loops")
            .description("Number of times to duplicate each map stack.")
            .defaultValue(1)
            .min(1)
            .max(64)
            .sliderRange(1, 64)
            .build()
      );
   private final Setting<Integer> clickDelay = this.sgLimits
      .add(new IntSetting.Builder().name("click-delay").description("Delay between clicks in ticks.").defaultValue(2).min(1).max(10).sliderRange(1, 10).build());
   private int tickCounter = 0;
   private boolean isCrafting = false;
   private int mapsToDuplicate = 0;
   private int emptyMapsAvailable = 0;
   private final List<MapDuplicator.CraftingTask> craftingQueue = new ArrayList<>();
   private int currentTaskIndex = 0;
   private int craftingStep = 0;

   public MapDuplicator() {
      super(Categories.Player, "map-duplicator", "Automatically duplicates all maps using inventory crafting.");
   }

   @Override
   public void onActivate() {
      this.tickCounter = 0;
      this.isCrafting = false;
      this.currentTaskIndex = 0;
      this.craftingStep = 0;
      this.craftingQueue.clear();
      if (!this.silentCrafting.get()) {
         this.mc.execute(() -> {
            if (this.mc.player != null) {
               this.mc.setScreen(new InventoryScreen(this.mc.player));
            }
         });
      }
   }

   @Override
   public void onDeactivate() {
      this.isCrafting = false;
      this.craftingQueue.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.tickCounter++;
         if (!this.isCrafting) {
            if (!this.silentCrafting.get()
               && !(this.mc.player.currentScreenHandler instanceof PlayerScreenHandler)
               && !(this.mc.player.currentScreenHandler instanceof CraftingScreenHandler)) {
               return;
            }

            this.analyzeInventory();
            int totalEmptyMapsNeeded = this.mapsToDuplicate * this.craftingLoops.get();
            if (this.mapsToDuplicate == 0 || this.emptyMapsAvailable < totalEmptyMapsNeeded) {
               if (this.showStatus.get()) {
                  if (this.mapsToDuplicate == 0) {
                     this.info("No maps to duplicate found.", new Object[0]);
                  } else {
                     this.error(
                        "Insufficient empty maps! Need "
                           + totalEmptyMapsNeeded
                           + " ("
                           + this.mapsToDuplicate
                           + " stacks x "
                           + this.craftingLoops.get()
                           + ") but only have "
                           + this.emptyMapsAvailable
                           + ".",
                        new Object[0]
                     );
                  }
               }

               this.toggle();
               return;
            }

            this.startCrafting();
         }

         if (this.isCrafting && this.tickCounter >= this.clickDelay.get()) {
            this.tickCounter = 0;
            this.processCrafting();
         }
      }
   }

   private void analyzeInventory() {
      this.mapsToDuplicate = 0;
      this.emptyMapsAvailable = 0;
      this.craftingQueue.clear();
      int invSize = this.mc.player.getInventory().size();

      for (int i = 0; i < invSize; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.FILLED_MAP && stack.getCount() > 0) {
            this.mapsToDuplicate++;

            for (int loop = 0; loop < this.craftingLoops.get(); loop++) {
               this.craftingQueue.add(new MapDuplicator.CraftingTask(SlotUtils.indexToId(i)));
            }
         }
      }

      for (int ix = 0; ix < invSize; ix++) {
         ItemStack stack = this.mc.player.getInventory().getStack(ix);
         if (stack.getItem() == Items.MAP) {
            this.emptyMapsAvailable = this.emptyMapsAvailable + stack.getCount();
         }
      }

      ScreenHandler handler = this.mc.player.currentScreenHandler;
      if (handler instanceof PlayerScreenHandler) {
         for (int ixx = 1; ixx <= 4; ixx++) {
            try {
               if (ixx < handler.slots.size()) {
                  ItemStack stack = handler.getSlot(ixx).getStack();
                  if (stack.getItem() == Items.MAP) {
                     this.emptyMapsAvailable = this.emptyMapsAvailable + stack.getCount();
                  }
               }
            } catch (Exception var6) {
            }
         }
      } else if (handler instanceof CraftingScreenHandler) {
         for (int ixx = 1; ixx <= 9; ixx++) {
            try {
               if (ixx < handler.slots.size()) {
                  ItemStack stack = handler.getSlot(ixx).getStack();
                  if (stack.getItem() == Items.MAP) {
                     this.emptyMapsAvailable = this.emptyMapsAvailable + stack.getCount();
                  }
               }
            } catch (Exception var5) {
            }
         }
      }

      int totalEmptyMapsNeeded = this.mapsToDuplicate * this.craftingLoops.get();
      if (this.emptyMapsAvailable >= totalEmptyMapsNeeded && this.showStatus.get()) {
         this.info(
            "Found "
               + this.mapsToDuplicate
               + " filled map stacks and "
               + this.emptyMapsAvailable
               + " empty maps. Will duplicate "
               + this.mapsToDuplicate
               + " stacks "
               + this.craftingLoops.get()
               + " times each.",
            new Object[0]
         );
      }
   }

   private void startCrafting() {
      if (this.craftingQueue.isEmpty()) {
         if (this.showStatus.get()) {
            this.error("Cannot start crafting: no maps to duplicate.", new Object[0]);
         }
      } else {
         int totalEmptyMapsNeeded = this.mapsToDuplicate * this.craftingLoops.get();
         if (this.emptyMapsAvailable < totalEmptyMapsNeeded) {
            if (this.showStatus.get()) {
               this.error(
                  "Cannot start crafting: insufficient empty maps. Need " + totalEmptyMapsNeeded + " but only have " + this.emptyMapsAvailable + ".",
                  new Object[0]
               );
            }
         } else {
            this.currentTaskIndex = 0;
            this.craftingStep = 0;
            this.isCrafting = true;
            if (this.showStatus.get()) {
               this.info(
                  "Starting map duplication process for "
                     + this.craftingQueue.size()
                     + " crafting tasks ("
                     + this.mapsToDuplicate
                     + " stacks x "
                     + this.craftingLoops.get()
                     + " loops)...",
                  new Object[0]
               );
            }
         }
      }
   }

   private void processCrafting() {
      if (!this.isCrafting || this.currentTaskIndex >= this.craftingQueue.size()) {
         this.finishCrafting();
      } else if (!this.silentCrafting.get()
         && !(this.mc.player.currentScreenHandler instanceof PlayerScreenHandler)
         && !(this.mc.player.currentScreenHandler instanceof CraftingScreenHandler)) {
         if (this.showStatus.get()) {
            this.error("Please open your inventory or a crafting table to continue duplication.", new Object[0]);
         }

         this.finishCrafting();
      } else {
         this.performCraftingStep();
      }
   }

   private void performCraftingStep() {
      if (this.currentTaskIndex >= this.craftingQueue.size()) {
         this.finishCrafting();
      } else {
         MapDuplicator.CraftingTask currentTask = this.craftingQueue.get(this.currentTaskIndex);
         switch (this.craftingStep) {
            case 0:
               if (this.isValidSlot(currentTask.mapSlot)) {
                  InvUtils.click().slotId(currentTask.mapSlot);
                  if (this.isValidSlot(1)) {
                     InvUtils.click().slotId(1);
                  }

                  this.craftingStep = 1;
               } else {
                  this.info("Invalid source slot: " + currentTask.mapSlot, new Object[0]);
                  this.finishCrafting();
               }
               break;
            case 1:
               int emptyMapSlot = this.findNextEmptyMap();
               if (emptyMapSlot != -1) {
                  if (this.isValidSlot(emptyMapSlot)) {
                     InvUtils.click().slotId(emptyMapSlot);
                     if (this.isValidSlot(2)) {
                        InvUtils.click().slotId(2);
                     }

                     this.craftingStep = 2;
                  } else {
                     this.info("Invalid empty map slot: " + emptyMapSlot, new Object[0]);
                     this.finishCrafting();
                  }
               } else {
                  this.info("No empty maps available", new Object[0]);
                  this.finishCrafting();
               }
               break;
            case 2:
               if (this.isValidSlot(0)) {
                  InvUtils.click().slotId(0);
                  this.craftingStep = 3;
               } else {
                  this.info("Invalid output slot", new Object[0]);
                  this.finishCrafting();
               }
               break;
            case 3:
               if (this.isValidSlot(currentTask.mapSlot)) {
                  InvUtils.click().slotId(currentTask.mapSlot);
                  this.craftingStep = 4;
               } else {
                  this.info("Invalid target slot: " + currentTask.mapSlot, new Object[0]);
                  this.finishCrafting();
               }
               break;
            case 4:
               if (this.isValidSlot(1) && this.isValidSlot(currentTask.mapSlot)) {
                  InvUtils.shiftClick().slotId(1);
               }

               this.craftingStep = 5;
               break;
            case 5:
               MapDuplicator.CraftingTask nextTask = this.currentTaskIndex + 1 < this.craftingQueue.size()
                  ? this.craftingQueue.get(this.currentTaskIndex + 1)
                  : null;
               if ((nextTask == null || nextTask.mapSlot != currentTask.mapSlot) && this.showStatus.get()) {
                  this.info("Map stack completed all " + this.craftingLoops.get() + " loop(s)!", new Object[0]);
               }

               this.currentTaskIndex++;
               this.craftingStep = 0;
         }
      }
   }

   private boolean isValidSlot(int slotId) {
      try {
         if (this.mc.player != null && this.mc.player.currentScreenHandler != null) {
            ScreenHandler handler = this.mc.player.currentScreenHandler;
            return slotId >= 0 && slotId < handler.slots.size() && handler.getSlot(slotId) != null;
         } else {
            return false;
         }
      } catch (Exception var3) {
         return false;
      }
   }

   private void clearCraftingGrid() {
      try {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (handler instanceof PlayerScreenHandler || handler instanceof CraftingScreenHandler) {
            int startSlot = 1;
            int endSlot = handler instanceof PlayerScreenHandler ? 4 : 9;

            for (int slot = startSlot; slot <= endSlot; slot++) {
               if (this.isValidSlot(slot) && handler.getSlot(slot).hasStack()) {
                  InvUtils.click().slotId(slot);
                  int emptySlot = this.findEmptyInventorySlot();
                  if (emptySlot >= 0) {
                     int emptySlotId = SlotUtils.indexToId(emptySlot);
                     if (this.isValidSlot(emptySlotId)) {
                        InvUtils.click().slotId(emptySlotId);
                     }
                  }
               }
            }

            if (this.showStatus.get()) {
               this.info("Crafting grid cleared - all items moved to inventory", new Object[0]);
            }
         }
      } catch (Exception var7) {
         if (this.showStatus.get()) {
            this.error("Error clearing crafting grid: " + var7.getMessage(), new Object[0]);
         }
      }
   }

   private int findEmptyInventorySlot() {
      int invSize = this.mc.player.getInventory().size();

      for (int i = 0; i < invSize; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   private int findNextEmptyMap() {
      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.MAP) {
            return SlotUtils.indexToId(i);
         }
      }

      ScreenHandler handler = this.mc.player.currentScreenHandler;
      if (handler instanceof PlayerScreenHandler) {
         for (int ix = 1; ix <= 4; ix++) {
            if (handler.getSlot(ix).getStack().getItem() == Items.MAP) {
               return ix;
            }
         }
      } else if (handler instanceof CraftingScreenHandler) {
         for (int ixx = 1; ixx <= 9; ixx++) {
            if (handler.getSlot(ixx).getStack().getItem() == Items.MAP) {
               return ixx;
            }
         }
      }

      return -1;
   }

   private void finishCrafting() {
      this.clearCraftingGrid();
      this.isCrafting = false;
      if (this.showStatus.get()) {
         this.info("Map duplication completed! Duplicated " + this.currentTaskIndex + " maps.", new Object[0]);
      }

      this.toggle();
   }

   @Override
   public String getInfoString() {
      return this.mapsToDuplicate + "/" + this.emptyMapsAvailable;
   }

   private static class CraftingTask {
      public final int mapSlot;

      public CraftingTask(int mapSlot) {
         this.mapSlot = mapSlot;
      }
   }
}
