package revivedevelopment.reviveclient.mixin.pingbypass;

import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkingBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

@Mixin(
   value = {ClientConnection.class},
   priority = 900
)
public abstract class PbConnectVersionMixin {
   @Inject(
      method = {"method_52271"},
      at = {@At("HEAD")}
   )
   private static void revive$pbBeforeConnect(
      InetSocketAddress address, NetworkingBackend backend, ClientConnection connection, CallbackInfoReturnable<ChannelFuture> cir
   ) {
      PbRouting.beforeConnect(address, connection);
   }

   @Inject(
      method = {"method_52271"},
      at = {@At("RETURN")}
   )
   private static void revive$pbAfterConnect(
      InetSocketAddress address, NetworkingBackend backend, ClientConnection connection, CallbackInfoReturnable<ChannelFuture> cir
   ) {
      PbRouting.afterConnect(connection, (ChannelFuture)cir.getReturnValue());
   }
}
