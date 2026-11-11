package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import revivedevelopment.reviveclient.mixininterface.IEntityRenderState;

@Mixin({EntityRenderManager.class})
public abstract class EntityRenderManagerMixin {
   @ModifyExpressionValue(
      method = {"method_72977"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_897;method_62425(Lnet/minecraft/class_1297;F)Lnet/minecraft/class_10017;"
      )}
   )
   private <E extends Entity> EntityRenderState getAndUpdateRenderState$setEntity(EntityRenderState state, E entity, float tickProgress) {
      ((IEntityRenderState)state).revive$setEntity(entity);
      return state;
   }
}
