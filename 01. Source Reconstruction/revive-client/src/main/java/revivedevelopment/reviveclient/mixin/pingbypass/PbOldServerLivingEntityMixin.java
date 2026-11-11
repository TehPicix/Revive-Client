package revivedevelopment.reviveclient.mixin.pingbypass;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.pingbypass.PbOldServer;

@Mixin({LivingEntity.class})
public abstract class PbOldServerLivingEntityMixin {
   @WrapOperation(
      method = {"method_6007"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/lang/Object;equals(Ljava/lang/Object;)Z"
      )},
      require = 0
   )
   private boolean revive$oldVelocityFloor(Object instance, Object other, Operation<Boolean> original) {
      return instance instanceof EntityType && PbOldServer.emulates((LivingEntity)(Object)this, 769) ? false : (Boolean)original.call(new Object[]{instance, other});
   }

   @WrapOperation(
      method = {"method_6043"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/lang/Math;max(DD)D"
      )},
      require = 0
   )
   private double revive$oldJump(double jump, double current, Operation<Double> original) {
      return PbOldServer.emulates((LivingEntity)(Object)this, 767) ? jump : (Double)original.call(new Object[]{jump, current});
   }

   @Inject(
      method = {"method_23326"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$oldSoulSpeed(CallbackInfoReturnable<Float> cir) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (PbOldServer.emulates(self, 766)) {
         PbOldServer.setMovementEfficiency(self);
      }
   }
}
