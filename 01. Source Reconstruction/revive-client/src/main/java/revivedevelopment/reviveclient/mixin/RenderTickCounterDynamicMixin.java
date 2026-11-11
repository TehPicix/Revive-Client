package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.RenderTickCounter.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.Timer;

@Mixin({Dynamic.class})
public abstract class RenderTickCounterDynamicMixin {
   @Shadow
   private float field_51958;

   @Inject(
      method = {"method_60639"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_9779$class_9781;field_51962:J",
         opcode = 181
      )}
   )
   private void onBeginRenderTick(long a, CallbackInfoReturnable<Integer> info) {
      if (!PbPredict.serverPushesOnly()) {
         this.field_51958 = this.field_51958 * (float)Modules.get().get(Timer.class).getMultiplier();
      }
   }
}
