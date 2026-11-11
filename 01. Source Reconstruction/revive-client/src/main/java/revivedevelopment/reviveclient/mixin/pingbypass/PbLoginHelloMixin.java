package revivedevelopment.reviveclient.mixin.pingbypass;

import javax.crypto.Cipher;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.PbFakes;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

@Mixin({ClientLoginNetworkHandler.class})
public abstract class PbLoginHelloMixin {
   @Shadow
   @Final
   private ClientConnection field_3707;

   @Inject(
      method = {"method_12587"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$pbCheckVpsKey(LoginHelloS2CPacket packet, CallbackInfo ci) {
      if (!PbRouting.isTrustedServerKey(this.field_3707, ((LoginHelloS2CPacketAccessor)packet).revive$getPublicKey())) {
         this.field_3707.disconnect(Text.literal("PingBypass: the VPS's key doesn't match - the connection may be intercepted."));
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_56151"},
      at = {@At("HEAD")}
   )
   private void revive$pbRememberKeyExchange(LoginKeyC2SPacket packet, Cipher decrypt, Cipher encrypt, CallbackInfo ci) {
      PbRouting.onKeySent(this.field_3707, ((LoginKeyC2SPacketAccessor)packet).revive$getEncryptedSecretKey());
   }

   @Inject(
      method = {"method_12588"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$pbRequireEncryption(LoginSuccessS2CPacket packet, CallbackInfo ci) {
      if (!PbRouting.mayFinishLogin(this.field_3707)) {
         this.field_3707.disconnect(Text.literal("PingBypass: the login wasn't encrypted with the VPS's key - the connection may be intercepted."));
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_12588"},
      at = {@At("TAIL")}
   )
   private void revive$pbCountWorldsAnew(LoginSuccessS2CPacket packet, CallbackInfo ci) {
      PbFakes.onLoggedIn();
   }
}
