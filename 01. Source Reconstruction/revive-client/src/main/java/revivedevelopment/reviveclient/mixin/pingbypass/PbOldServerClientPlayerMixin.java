package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.PbOldServer;

@Mixin({ClientPlayerEntity.class})
public abstract class PbOldServerClientPlayerMixin {
   @Inject(
      method = {"method_5773"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$oldEnchantmentAttributes(CallbackInfo ci) {
      PbOldServer.tickAttributes((ClientPlayerEntity)(Object)this);
   }
}
