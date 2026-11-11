package revivedevelopment.reviveclient.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;

@Mixin({BlockRenderManager.class})
public abstract class BlockRenderManagerMixin {
   @Inject(
      method = {"method_23071"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderBlockBreakOverlay(
      BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices, VertexConsumer vertexConsumer, CallbackInfo info
   ) {
      if (Modules.get().get(NoRender.class).noBlockBreakOverlay()) {
         info.cancel();
      }
   }
}
