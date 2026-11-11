package revivedevelopment.reviveclient.mixin.pingbypass;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

@Mixin(
   targets = {"net/minecraft/class_412$1"}
)
public abstract class PbConnectScreenThreadMixin {
   @Inject(
      method = {"run"},
      at = {@At("HEAD")}
   )
   private void revive$pbPrepareTicket(CallbackInfo ci) {
      PbRouting.prepareTicket();
   }
}
