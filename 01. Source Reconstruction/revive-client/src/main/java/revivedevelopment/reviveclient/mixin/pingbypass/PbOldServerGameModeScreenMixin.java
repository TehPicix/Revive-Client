package revivedevelopment.reviveclient.mixin.pingbypass;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.GameModeSwitcherScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

@Mixin({GameModeSwitcherScreen.class})
public abstract class PbOldServerGameModeScreenMixin {
   @WrapOperation(
      method = {"method_28064"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V"
      )},
      require = 0
   )
   private static void revive$gameModeAsCommand(ClientPlayNetworkHandler handler, Packet<?> packet, Operation<Void> original) {
      if (!PbRouting.sendGameModeCommand(handler, packet)) {
         original.call(new Object[]{handler, packet});
      }
   }
}
