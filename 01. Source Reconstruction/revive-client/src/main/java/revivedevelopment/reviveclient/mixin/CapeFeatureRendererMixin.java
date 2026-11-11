package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.AssetInfo.TextureAsset;
import net.minecraft.util.AssetInfo.TextureAssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import revivedevelopment.reviveclient.mixininterface.IEntityRenderState;
import revivedevelopment.reviveclient.utils.network.Capes;

@Mixin({CapeFeatureRenderer.class})
public abstract class CapeFeatureRendererMixin {
   @ModifyExpressionValue(
      method = {"method_4177"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_8685;comp_1627()Lnet/minecraft/class_12079$class_12081;"
      )}
   )
   private TextureAsset modifyCapeTexture(
      TextureAsset original, MatrixStack matrices, OrderedRenderCommandQueue entityRenderCommandQueue, int i, PlayerEntityRenderState state, float f, float g
   ) {
      if (((IEntityRenderState)state).revive$getEntity() instanceof PlayerEntity player) {
         Identifier id = Capes.get(player);
         return (TextureAsset)(id == null ? original : new TextureAssetInfo(id, id));
      } else {
         return original;
      }
   }
}
