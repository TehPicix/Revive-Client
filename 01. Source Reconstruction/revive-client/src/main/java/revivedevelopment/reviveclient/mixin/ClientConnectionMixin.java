package revivedevelopment.reviveclient.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import java.util.Iterator;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.packets.PacketEvent;

@Mixin({ClientConnection.class})
public abstract class ClientConnectionMixin {
   @Inject(
      method = {"method_10770"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_2535;method_10759(Lnet/minecraft/class_2596;Lnet/minecraft/class_2547;)V",
         shift = Shift.BEFORE
      )},
      cancellable = true
   )
   private void onHandlePacket(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
      if (packet instanceof BundleS2CPacket bundle) {
         Iterator<Packet<? super ClientPlayPacketListener>> it = bundle.getPackets().iterator();

         while (it.hasNext()) {
            if (((PacketEvent.Receive)ReviveClient.EVENT_BUS.post(new PacketEvent.Receive(it.next(), (ClientConnection)(Object)this))).isCancelled()) {
               it.remove();
            }
         }
      } else if (((PacketEvent.Receive)ReviveClient.EVENT_BUS.post(new PacketEvent.Receive(packet, (ClientConnection)(Object)this))).isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"method_10752"},
      cancellable = true
   )
   private void onSendPacketHead(Packet<?> packet, ChannelFutureListener callbacks, CallbackInfo ci) {
      if (((PacketEvent.Send)ReviveClient.EVENT_BUS.post(new PacketEvent.Send(packet, (ClientConnection)(Object)this))).isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_10752"},
      at = {@At("TAIL")}
   )
   private void onSendPacketTail(Packet<?> packet, @Nullable ChannelFutureListener callbacks, CallbackInfo ci) {
      ReviveClient.EVENT_BUS.post(new PacketEvent.Sent(packet, (ClientConnection)(Object)this));
   }
}
