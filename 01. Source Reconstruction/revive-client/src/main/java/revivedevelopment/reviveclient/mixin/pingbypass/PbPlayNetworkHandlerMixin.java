package revivedevelopment.reviveclient.mixin.pingbypass;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.EnterReconfigurationS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.LookAtS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRotationS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.PbDrive;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;

@Mixin({ClientPlayNetworkHandler.class})
public abstract class PbPlayNetworkHandlerMixin {
   @Inject(
      method = {"method_11152"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$pbState(CustomPayload payload, CallbackInfo ci) {
      if (payload instanceof PbDrive.StatePayload state) {
         PbPredict.onState(state.state());
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_11157"},
      at = {@At("HEAD")}
   )
   private void revive$pbTeleported(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PbPredict.onTeleported();
      }
   }

   @Redirect(
      method = {"method_11124"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"
      )
   )
   private void revive$pbExplosionKnockback(Optional<Object> knockback, Consumer<Object> apply, ExplosionS2CPacket packet) {
      if (!PbPredict.serverPushesOnly()) {
         knockback.ifPresent(apply);
      }
   }

   @Inject(
      method = {"method_64554"},
      at = {@At("HEAD")}
   )
   private void revive$pbRotated(PlayerRotationS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PbPredict.onRotated();
      }
   }

   @Inject(
      method = {"method_11092"},
      at = {@At("HEAD")}
   )
   private void revive$pbLookedAt(LookAtS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PbPredict.onRotated();
      }
   }

   @Inject(
      method = {"method_52798"},
      at = {@At("TAIL")}
   )
   private void revive$pbLeftPlay(EnterReconfigurationS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PbPredict.onLeftPlay();
      }
   }

   @Inject(
      method = {"method_11135"},
      at = {@At("HEAD")}
   )
   private void revive$pbSlotPacket(UpdateSelectedSlotS2CPacket packet, CallbackInfo ci) {
      if (MinecraftClient.getInstance().isOnThread()) {
         PbPredict.onSlotPacket();
      }
   }
}
