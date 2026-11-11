package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.gl.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;

@Mixin({ShaderLoader.class})
public abstract class ShaderLoaderMixin {
   @Inject(
      method = {"method_62945"},
      at = {@At("TAIL")}
   )
   private void revive$reloadPipelines(CallbackInfo info) {
      ReviveRenderPipelines.precompile();
   }
}
