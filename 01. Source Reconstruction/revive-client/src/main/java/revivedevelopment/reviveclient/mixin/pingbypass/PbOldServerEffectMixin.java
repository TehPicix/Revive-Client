package revivedevelopment.reviveclient.mixin.pingbypass;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import revivedevelopment.reviveclient.mixin.StatusEffectInstanceAccessor;
import revivedevelopment.reviveclient.pingbypass.PbVia;

@Mixin({ClientPlayNetworkHandler.class})
public abstract class PbOldServerEffectMixin {
   @Shadow
   public abstract ClientConnection method_48296();

   @ModifyExpressionValue(
      method = {"method_11084"},
      at = {@At(
         value = "NEW",
         target = "Lnet/minecraft/class_1293;"
      )},
      require = 0
   )
   private StatusEffectInstance revive$oldNegativeAmplifier(StatusEffectInstance effect, @Local(argsOnly = true) EntityStatusEffectS2CPacket packet) {
      if (packet.getAmplifier() < 0 && PbVia.emulatedServerProtocol(this.method_48296()) <= 765) {
         ((StatusEffectInstanceAccessor)effect).setAmplifier(packet.getAmplifier());
      }

      return effect;
   }
}
