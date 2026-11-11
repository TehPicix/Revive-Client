package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import revivedevelopment.reviveclient.mixininterface.IEntityRenderState;
import revivedevelopment.reviveclient.utils.network.Capes;

@Mixin({ElytraFeatureRenderer.class})
public abstract class ElytraFeatureRendererMixin<S extends BipedEntityRenderState, M extends EntityModel<S>> extends FeatureRenderer<S, M> {
   public ElytraFeatureRendererMixin(FeatureRendererContext<S, M> context) {
      super(context);
   }

   @ModifyExpressionValue(
      method = {"method_17161"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_979;method_64084(Lnet/minecraft/class_10034;)Lnet/minecraft/class_2960;"
      )}
   )
   private Identifier modifyCapeTexture(Identifier original, MatrixStack matrices, OrderedRenderCommandQueue consumers, int i, S state, float f, float g) {
      if (state instanceof PlayerEntityRenderState playerState
         && playerState.capeVisible
         && ((IEntityRenderState)state).revive$getEntity() instanceof PlayerEntity player) {
         Identifier id = Capes.get(player);
         return id == null ? original : id;
      } else {
         return original;
      }
   }
}
