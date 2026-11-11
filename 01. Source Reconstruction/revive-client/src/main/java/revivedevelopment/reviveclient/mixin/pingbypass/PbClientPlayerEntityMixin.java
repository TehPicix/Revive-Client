package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

@Mixin({ClientPlayerEntity.class})
public abstract class PbClientPlayerEntityMixin {
   @Inject(
      method = {"method_3136"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V"
      )}
   )
   private void revive$pbMoveStart(CallbackInfo ci) {
      PbRouting.setVanillaMove(true);
   }

   @Inject(
      method = {"method_3136"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V",
         shift = Shift.AFTER
      )}
   )
   private void revive$pbMoveEnd(CallbackInfo ci) {
      PbRouting.setVanillaMove(false);
   }

   @Inject(
      method = {"method_5773"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V",
         ordinal = 1
      )}
   )
   private void revive$pbRidingLookStart(CallbackInfo ci) {
      PbRouting.setVanillaMove(true);
   }

   @Inject(
      method = {"method_5773"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V",
         ordinal = 1,
         shift = Shift.AFTER
      )}
   )
   private void revive$pbRidingLookEnd(CallbackInfo ci) {
      PbRouting.setVanillaMove(false);
   }
}
