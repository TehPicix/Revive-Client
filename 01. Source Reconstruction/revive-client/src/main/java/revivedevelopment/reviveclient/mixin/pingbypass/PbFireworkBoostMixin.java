package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;

@Mixin({FireworkRocketEntity.class})
public abstract class PbFireworkBoostMixin {
   @Redirect(
      method = {"method_5773"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1309;method_18799(Lnet/minecraft/class_243;)V"
      )
   )
   private void revive$pbBoost(LivingEntity entity, Vec3d velocity) {
      if (entity != MinecraftClient.getInstance().player || !PbPredict.serverPushesOnly()) {
         entity.setVelocity(velocity);
      }
   }
}
