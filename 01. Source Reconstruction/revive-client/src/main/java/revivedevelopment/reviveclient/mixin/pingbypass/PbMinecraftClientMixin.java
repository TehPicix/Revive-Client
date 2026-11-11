package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.PbFakes;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

@Mixin({MinecraftClient.class})
public abstract class PbMinecraftClientMixin {
   @Inject(
      method = {"method_1490"},
      at = {@At("HEAD")}
   )
   private void revive$pbRestoreVia(CallbackInfo ci) {
      PbRouting.onClientStop();
   }

   @Inject(
      method = {"method_1574"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_9836;field_52333:Lnet/minecraft/class_9836;",
         opcode = 178
      )}
   )
   private void revive$pbBeforeTickEnd(CallbackInfo ci) {
      PbPredict.beforeTickEnd();
   }

   @Inject(
      method = {"method_1481"},
      at = {@At("HEAD")}
   )
   private void revive$pbCountWorld(ClientWorld world, CallbackInfo ci) {
      PbFakes.onWorldEntered();
   }
}
