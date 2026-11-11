package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Chams;

@Mixin({EndCrystalEntityRenderer.class})
public abstract class EndCrystalEntityRendererMixin {
   @Unique
   private Chams chams;
   @Shadow
   @Final
   @Mutable
   private static RenderLayer field_21736;
   @Shadow
   @Final
   private static Identifier field_4663;

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void init$chams(CallbackInfo info) {
      this.chams = Modules.get().get(Chams.class);
   }

   @Inject(
      method = {"method_3908"},
      at = {@At("HEAD")}
   )
   private void render$texture(
      EndCrystalEntityRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, CameraRenderState arg, CallbackInfo ci
   ) {
      boolean tweaks = this.chams != null && this.chams.isActive() && this.chams.crystals.get();
      field_21736 = RenderLayers.entityTranslucent(tweaks && !this.chams.crystalsTexture.get() ? Chams.BLANK : field_4663);
   }

   @Inject(
      method = {"method_3908"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4587;method_22905(FFF)V"
      )}
   )
   private void render$scale(
      EndCrystalEntityRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, CameraRenderState arg, CallbackInfo ci
   ) {
      if (this.chams != null && this.chams.isActive() && this.chams.crystals.get()) {
         float v = this.chams.crystalsScale.get().floatValue();
         matrixStack.scale(v, v, v);
      }
   }
}
