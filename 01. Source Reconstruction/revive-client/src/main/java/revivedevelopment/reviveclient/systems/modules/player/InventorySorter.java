package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.slot.Slot;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.ISlot;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.player.SlotUtils;

public class InventorySorter extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> middleClickSort = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("middle-click-sort")
            .description("Middle-click a slot to sort the inventory section it belongs to.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Keybind> sortBind = this.sgGeneral
      .add(new KeybindSetting.Builder().name("sort-bind").description("Sorts the open container, or your main inventory when no container is open.").build());
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("delay")
            .description("Ticks between each move. Higher is slower but less packet-spammy.")
            .defaultValue(1)
            .min(0)
            .sliderRange(0, 10)
            .build()
      );
   private revivedevelopment.reviveclient.utils.player.InventorySorter sorter;
   private HandledScreen<?> sorterScreen;
   private boolean sortKeyUp;

   public InventorySorter() {
      super(Categories.Player, "inventory-sorter", "Sorts inventories, chests and shulkers so like items are grouped together.");
   }

   @Override
   public void onActivate() {
      this.cancelSort();
      this.sortKeyUp = false;
   }

   @Override
   public void onDeactivate() {
      this.cancelSort();
   }

   private void cancelSort() {
      this.sorter = null;
      this.sorterScreen = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null) {
         this.pollKeybind();
         if (this.sorter != null) {
            if (this.mc.currentScreen != this.sorterScreen) {
               this.cancelSort();
            } else if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
               if (this.sorter.tick(this.delay.get())) {
                  this.cancelSort();
               }
            }
         }
      }
   }

   private void pollKeybind() {
      if (!this.sortBind.get().isPressed()) {
         this.sortKeyUp = true;
      } else if (this.sortKeyUp) {
         this.sortKeyUp = false;
         if (this.mc.currentScreen instanceof HandledScreen<?> screen) {
            Slot origin = this.findOriginSlot(screen, true);
            if (origin == null) {
               origin = this.findOriginSlot(screen, false);
            }

            if (origin != null) {
               this.startSort(screen, origin);
            }
         }
      }
   }

   public boolean onSlotMiddleClick(HandledScreen<?> screen, Slot slot) {
      if (!this.middleClickSort.get() || this.mc.player == null) {
         return false;
      } else if (!this.isSortable(screen, slot)) {
         return false;
      } else {
         this.startSort(screen, slot);
         return true;
      }
   }

   private void startSort(HandledScreen<?> screen, Slot origin) {
      this.sorter = new revivedevelopment.reviveclient.utils.player.InventorySorter(screen, origin);
      this.sorterScreen = screen;
   }

   private boolean isSortable(HandledScreen<?> screen, Slot slot) {
      return slot.inventory instanceof PlayerInventory
         ? SlotUtils.isMain(((ISlot)slot).revive$getIndex())
         : (screen instanceof GenericContainerScreen || screen instanceof ShulkerBoxScreen) && slot.inventory instanceof SimpleInventory;
   }

   private Slot findOriginSlot(HandledScreen<?> screen, boolean container) {
      for (Slot slot : screen.getScreenHandler().slots) {
         if (container) {
            if (slot.inventory instanceof SimpleInventory && this.isSortable(screen, slot)) {
               return slot;
            }
         } else if (slot.inventory instanceof PlayerInventory && this.isSortable(screen, slot)) {
            return slot;
         }
      }

      return null;
   }
}
