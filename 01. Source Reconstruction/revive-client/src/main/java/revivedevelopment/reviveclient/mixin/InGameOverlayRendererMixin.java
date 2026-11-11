package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;

@Mixin({InGameOverlayRenderer.class})
public abstract class InGameOverlayRendererMixin {
   @Inject(
      method = {"method_23070"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noFireOverlay()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_23069"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderUnderwaterOverlay(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noLiquidOverlay()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_23068"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderInWallOverlay(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noInWallOverlay()) {
         ci.cancel();
      }
   }
}
