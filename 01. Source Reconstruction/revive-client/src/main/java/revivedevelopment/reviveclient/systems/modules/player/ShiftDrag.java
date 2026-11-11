package revivedevelopment.reviveclient.systems.modules.player;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.input.Input;

public class ShiftDrag extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> containerOnly = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("container-only")
            .description("Only sweep the container's own slots, never your inventory rows.")
            .defaultValue(false)
            .build()
      );
   private final Set<Integer> handled = new HashSet<>();
   private boolean running;
   private int runSyncId = -1;
   private boolean lastButtonDown;

   public ShiftDrag() {
      super(Categories.Player, "shift-drag", "Shift-click a container by holding shift and dragging the mouse over it.");
   }

   @Override
   public void onDeactivate() {
      this.endRun();
      this.lastButtonDown = false;
   }

   public void onHoveredSlot(Slot slot) {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         ScreenHandler handler = this.mc.player.currentScreenHandler;
         if (handler != null) {
            boolean shift = Input.isKeyPressed(340) || Input.isKeyPressed(344);
            boolean buttonDown = Input.isButtonPressed(0);
            boolean pressedNow = buttonDown && !this.lastButtonDown;
            this.lastButtonDown = buttonDown;
            if (!shift || !buttonDown || handler.syncId != this.runSyncId) {
               this.endRun();
            }

            if (shift && buttonDown) {
               this.beginRun(handler.syncId);
            }

            if (this.running) {
               if (pressedNow) {
                  this.handled.add(slot.id);
               } else if (handler.getCursorStack().isEmpty()) {
                  if (slot.hasStack()) {
                     if (!this.containerOnly.get() || slot.inventory != this.mc.player.getInventory()) {
                        if (!this.handled.contains(slot.id)) {
                           if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                              this.handled.add(slot.id);
                              this.mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void beginRun(int syncId) {
      if (!this.running || this.runSyncId != syncId) {
         this.running = true;
         this.runSyncId = syncId;
         this.handled.clear();
      }
   }

   private void endRun() {
      if (this.running) {
         this.running = false;
         this.runSyncId = -1;
         this.handled.clear();
      }
   }
}
