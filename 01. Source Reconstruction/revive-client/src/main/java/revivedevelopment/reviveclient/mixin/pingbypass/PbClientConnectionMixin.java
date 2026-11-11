package revivedevelopment.reviveclient.mixin.pingbypass;

import io.netty.channel.ChannelFutureListener;
import java.net.InetSocketAddress;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

@Mixin({ClientConnection.class})
public abstract class PbClientConnectionMixin {
   @ModifyVariable(
      method = {"method_52271"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private static InetSocketAddress revive$pbRedirectSocket(InetSocketAddress address) {
      return PbRouting.redirectSocket(address);
   }

   @Inject(
      method = {"method_52906"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$pbMarkOwnMove(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      PbPredict.onSend((ClientConnection)(Object)this, packet);
      if (PbRouting.markOwnMove((ClientConnection)(Object)this, packet, listener, flush)) {
         ci.cancel();
      }
   }

   @ModifyVariable(
      method = {"method_56326"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private String revive$pbTagHandshake(String address) {
      return PbRouting.tagHandshake((ClientConnection)(Object)this, address);
   }
}
