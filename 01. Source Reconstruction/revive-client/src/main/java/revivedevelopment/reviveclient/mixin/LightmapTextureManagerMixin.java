package revivedevelopment.reviveclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Fullbright;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;

@Mixin({LightmapTextureManager.class})
public abstract class LightmapTextureManagerMixin {
   @Shadow
   @Final
   private GpuTexture field_57927;

   @Inject(
      method = {"method_3313"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$fullbright(float tickProgress, CallbackInfo ci) {
      Fullbright fullbright = Modules.get().get(Fullbright.class);
      if (fullbright != null && fullbright.getGamma()) {
         RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.field_57927, -1);
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_42596"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderDarkness(LivingEntity entity, float factor, float tickProgress, CallbackInfoReturnable<Float> info) {
      if (Modules.get().get(NoRender.class).noDarkness()) {
         info.setReturnValue(0.0F);
      }
   }
}
