package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.events.revive.MouseScrollEvent;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

@Mixin({Mouse.class})
public abstract class MouseMixin {
   @Inject(
      method = {"method_1601"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseButton(long window, MouseInput mouseInput, int action, CallbackInfo info) {
      Input.setButtonState(mouseInput.button(), action != 0);
      if (((MouseButtonEvent)ReviveClient.EVENT_BUS.post(MouseButtonEvent.get(mouseInput.button(), KeyAction.get(action)))).isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"method_1598"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo info) {
      if (((MouseScrollEvent)ReviveClient.EVENT_BUS.post(MouseScrollEvent.get(vertical))).isCancelled()) {
         info.cancel();
      }
   }
}
