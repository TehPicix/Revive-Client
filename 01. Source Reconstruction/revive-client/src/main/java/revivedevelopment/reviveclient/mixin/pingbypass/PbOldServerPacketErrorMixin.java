package revivedevelopment.reviveclient.mixin.pingbypass;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.pingbypass.PbVia;

@Mixin({ClientCommonNetworkHandler.class})
public abstract class PbOldServerPacketErrorMixin {
   @Shadow
   @Final
   protected ClientConnection field_45589;

   @WrapWithCondition(
      method = {"method_59807"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_2535;method_60924(Lnet/minecraft/class_9812;)V"
      )},
      require = 0
   )
   private boolean revive$oldServerSkipsBadPacket(ClientConnection instance, DisconnectionInfo info) {
      return PbVia.emulatedServerProtocol(this.field_45589) > 765;
   }

   @Inject(
      method = {"method_60882"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void revive$oldServerNoPacketErrorReport(Packet<?> packet, Throwable error, CallbackInfoReturnable<Optional<Path>> cir) {
      if (PbVia.emulatedServerProtocol(this.field_45589) <= 765) {
         cir.setReturnValue(Optional.empty());
      }
   }
}
