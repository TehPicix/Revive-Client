package revivedevelopment.reviveclient.systems.modules.player;

import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.Keybind;

public class ChestStealer extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<Item>> whitelist = this.sgGeneral
      .add(
         new ItemListSetting.Builder().name("whitelist").description("The \"selected\" items — what \"Steal Selected\", the bind and auto-steal take.").build()
      );
   private final Setting<Boolean> buttons = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("buttons")
            .description("Draw \"Steal All\" and \"Steal Selected\" buttons to the right of the container.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> auto = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto")
            .description("Automatically steal the selected (whitelisted) items whenever a container opens.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Keybind> stealBind = this.sgGeneral
      .add(new KeybindSetting.Builder().name("steal-bind").description("Hold this key to steal the selected (whitelisted) items.").build());
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         new IntSetting.Builder().name("delay").description("Ticks to wait between each item moved.").defaultValue(2).min(0).max(20).sliderRange(0, 20).build()
      );
   private final Setting<Boolean> close = this.sgGeneral
      .add(new BoolSetting.Builder().name("close").description("Close the container once the steal has finished.").defaultValue(true).build());
   private int timer;
   private ChestStealer.Request request = ChestStealer.Request.NONE;

   public ChestStealer() {
      super(Categories.Player, "chest-stealer", "Steal whitelisted items out of chests.");
   }

   @Override
   public void onActivate() {
      this.timer = 0;
      this.request = ChestStealer.Request.NONE;
   }

   @Override
   public void onDeactivate() {
      this.request = ChestStealer.Request.NONE;
   }

   public void requestStealAll() {
      this.request = ChestStealer.Request.ALL;
   }

   public void requestStealSelected() {
      this.request = ChestStealer.Request.SELECTED;
   }

   public boolean showButtons() {
      return this.isActive() && this.buttons.get();
   }

   public boolean isStealableHandler(ScreenHandler handler) {
      return handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.currentScreen != null) {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (!this.isStealableHandler(handler)) {
            this.request = ChestStealer.Request.NONE;
         } else {
            boolean bindHeld = this.stealBind.get().isSet() && this.stealBind.get().isPressed();
            boolean pending = this.request != ChestStealer.Request.NONE;
            if (pending || this.auto.get() || bindHeld) {
               if (this.timer > 0) {
                  this.timer--;
               } else if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                  Inventory playerInv = this.mc.player.getInventory();

                  for (Slot slot : handler.slots) {
                     if (slot.inventory != playerInv && slot.hasStack() && this.matches(slot.getStack().getItem())) {
                        this.mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                        this.timer = this.delay.get();
                        return;
                     }
                  }

                  boolean shouldClose = pending || bindHeld || !this.whitelist.get().isEmpty();
                  this.request = ChestStealer.Request.NONE;
                  if (this.close.get() && shouldClose) {
                     this.mc.player.closeHandledScreen();
                  }
               }
            }
         }
      } else {
         this.request = ChestStealer.Request.NONE;
      }
   }

   private boolean matches(Item item) {
      return this.request == ChestStealer.Request.ALL ? true : this.whitelist.get().contains(item);
   }

   private static enum Request {
      NONE,
      ALL,
      SELECTED;
   }
}
