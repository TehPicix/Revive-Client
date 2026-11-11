package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.ParticleEvent;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;

@Mixin({ParticleManager.class})
public abstract class ParticleManagerMixin {
   @Shadow
   @Nullable
   protected abstract <T extends ParticleEffect> Particle method_3055(T var1, double var2, double var4, double var6, double var8, double var10, double var12);

   @Inject(
      method = {"method_3056"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddParticle(
      ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> info
   ) {
      ParticleEvent event = (ParticleEvent)ReviveClient.EVENT_BUS.post(ParticleEvent.get(parameters));
      if (event.isCancelled()) {
         NoRender noRender = Modules.get().get(NoRender.class);
         if (parameters.getType() != ParticleTypes.FLASH || noRender != null && noRender.noExplosions()) {
            info.cancel();
         } else {
            info.setReturnValue(this.method_3055(parameters, x, y, z, velocityX, velocityY, velocityZ));
         }
      }
   }
}
