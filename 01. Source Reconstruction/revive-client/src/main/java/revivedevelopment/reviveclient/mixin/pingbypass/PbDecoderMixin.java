package revivedevelopment.reviveclient.mixin.pingbypass;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.state.NetworkState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;

@Mixin({DecoderHandler.class})
public abstract class PbDecoderMixin {
   @Shadow
   @Final
   private NetworkState<?> field_48536;

   @Inject(
      method = {"decode"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$pbStateFrame(ChannelHandlerContext ctx, ByteBuf buf, List<Object> objects, CallbackInfo ci) {
      if (PbPredict.onFrame(ctx, buf, objects, this.field_48536)) {
         ci.cancel();
      }
   }
}
