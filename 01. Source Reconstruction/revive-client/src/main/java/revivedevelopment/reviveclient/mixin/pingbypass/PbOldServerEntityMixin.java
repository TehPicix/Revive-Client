package revivedevelopment.reviveclient.mixin.pingbypass;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.pingbypass.PbOldServer;

@Mixin({Entity.class})
public abstract class PbOldServerEntityMixin {
   @Inject(
      method = {"method_17835"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void revive$oldStepUp(Vec3d movement, CallbackInfoReturnable<Vec3d> cir) {
      Entity self = (Entity)(Object)this;
      if (PbOldServer.emulatesMovement(self, 766)) {
         cir.setReturnValue(PbOldServer.collide1_20_5(self, movement));
      }
   }

   @WrapOperation(
      method = {"method_5630"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1297;method_23320()D"
      )},
      require = 0
   )
   private double revive$oldEyeInWater(Entity instance, Operation<Double> original) {
      double eyeY = (Double)original.call(new Object[]{instance});
      return PbOldServer.emulates(instance, 765) ? eyeY - 0.11111111F : eyeY;
   }
}
