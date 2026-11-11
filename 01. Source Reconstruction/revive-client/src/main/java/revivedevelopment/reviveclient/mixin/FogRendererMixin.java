package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;

@Mixin({FogRenderer.class})
public abstract class FogRendererMixin {
   @ModifyExpressionValue(
      method = {"method_71109"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_758;field_54018:Z",
         opcode = 178
      )},
      require = 0
   )
   private boolean revive$noFog(boolean original) {
      Modules modules = Modules.get();
      if (modules == null) {
         return original;
      } else {
         NoRender noRender = modules.get(NoRender.class);
         return noRender != null && noRender.noFog() ? false : original;
      }
   }
}
