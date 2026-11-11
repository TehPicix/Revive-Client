package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.revive.KeyEvent;
import revivedevelopment.reviveclient.gui.GuiKeyEvents;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.InventorySearch;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

@Mixin({Keyboard.class})
public abstract class KeyboardMixin {
   @Shadow
   @Final
   private MinecraftClient field_1678;

   @Inject(
      method = {"method_1457"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$onCharSearch(long window, CharInput input, CallbackInfo info) {
      if (this.field_1678.currentScreen instanceof HandledScreen) {
         Modules modules = Modules.get();
         if (modules != null) {
            InventorySearch search = modules.get(InventorySearch.class);
            if (search != null && search.isActive() && search.onCharTyped((char)input.codepoint())) {
               info.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"method_1466"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void onKey(long window, int action, KeyInput input, CallbackInfo info) {
      int key = input.key();
      int modifiers = input.modifiers();
      if (key != -1) {
         if (action == 1) {
            modifiers |= Input.getModifier(key);
         } else if (action == 0) {
            modifiers &= ~Input.getModifier(key);
         }

         if (this.field_1678.currentScreen instanceof WidgetScreen && action == 2) {
            ((WidgetScreen)this.field_1678.currentScreen).keyRepeated(key, modifiers);
         }

         if (GuiKeyEvents.canUseKeys) {
            Input.setKeyState(key, action != 0);
            if (((KeyEvent)ReviveClient.EVENT_BUS.post(KeyEvent.get(key, modifiers, KeyAction.get(action)))).isCancelled()) {
               info.cancel();
            }
         }
      }
   }
}
