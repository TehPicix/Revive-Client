package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.pingbypass.PbOldServer;

@Mixin({PlayerEntity.class})
public abstract class PbOldServerPlayerEntityMixin {
   @Inject(
      method = {"method_59818"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void revive$oldSneakEdge(double offsetX, double offsetZ, double depth, CallbackInfoReturnable<Boolean> cir) {
      PlayerEntity self = (PlayerEntity)(Object)this;
      if (PbOldServer.emulates(self, 769)) {
         cir.setReturnValue(PbOldServer.spaceBelowEmpty(self, offsetX, offsetZ, depth, PbOldServer.emulates(self, 765)));
      }
   }
}
