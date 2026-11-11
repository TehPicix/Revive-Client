package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Chams;

@Mixin({EndCrystalEntityModel.class})
public abstract class EndCrystalEntityModelMixin {
   @ModifyExpressionValue(
      method = {"method_62083"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_892;method_23155(F)F"
      )}
   )
   private float setAngles$bounce(float original, EndCrystalEntityRenderState state) {
      Chams chams = Modules.get().get(Chams.class);
      if (chams != null && chams.isActive() && chams.crystals.get()) {
         float g = MathHelper.sin(state.age * 0.2F) / 2.0F + 0.5F;
         g = (g * g + g) * 0.4F * chams.crystalsBounce.get().floatValue();
         return g - 1.4F;
      } else {
         return original;
      }
   }

   @ModifyExpressionValue(
      method = {"method_62083"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_10014;field_53328:F",
         ordinal = 0
      )}
   )
   private float setAngles$speed(float original) {
      Chams chams = Modules.get().get(Chams.class);
      return chams != null && chams.isActive() && chams.crystals.get() ? original * chams.crystalsRotationSpeed.get().floatValue() : original;
   }
}
