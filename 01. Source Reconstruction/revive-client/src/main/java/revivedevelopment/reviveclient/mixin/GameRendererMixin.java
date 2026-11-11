package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.profiler.Profilers;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.GetFovEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.renderer.Renderer3D;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Freecam;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.NametagUtils;
import revivedevelopment.reviveclient.utils.render.RenderUtils;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Shadow
   @Final
   private MinecraftClient field_4015;
   @Shadow
   @Final
   private Camera field_18765;
   @Unique
   private Renderer3D renderer;
   @Unique
   private Renderer3D depthRenderer;
   @Unique
   private final MatrixStack matrices = new MatrixStack();

   @Shadow
   public abstract void method_3190(float var1);

   @Shadow
   public abstract void method_3203();

   @Shadow
   protected abstract void method_3186(MatrixStack var1, float var2);

   @Shadow
   protected abstract void method_3198(MatrixStack var1, float var2);

   @Inject(
      method = {"method_3198"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderHurtCam(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noHurtCam()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_3189"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderTotemAnimation(ItemStack floatingItem, CallbackInfo ci) {
      if (floatingItem.getItem() == Items.TOTEM_OF_UNDYING && Modules.get().get(NoRender.class).noTotemAnimation()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_3172"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
      if (!Modules.get().get(Freecam.class).renderHands()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_3190"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void freecamUpdateCrosshairTarget(float tickDelta, CallbackInfo ci) {
      Freecam freecam = Modules.get().get(Freecam.class);
      if (freecam != null && freecam.retargetsCrosshair()) {
         this.field_4015.targetedEntity = null;
         this.field_4015.crosshairTarget = freecam.raycastFromCamera(tickDelta);
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_3188"},
      at = {@At(
         value = "INVOKE_STRING",
         target = "Lnet/minecraft/class_3695;method_15405(Ljava/lang/String;)V",
         args = {"ldc=hand"}
      )}
   )
   private void onRenderWorld(
      RenderTickCounter tickCounter,
      CallbackInfo ci,
      @Local(ordinal = 0) Matrix4f projection,
      @Local(ordinal = 1) Matrix4f view,
      @Local(ordinal = 0) float tickDelta,
      @Local MatrixStack matrixStack
   ) {
      if (Utils.canUpdate()) {
         Profilers.get().push("revive-client_render");
         if (this.renderer == null) {
            this.renderer = new Renderer3D(ReviveRenderPipelines.WORLD_COLORED_LINES, ReviveRenderPipelines.WORLD_COLORED);
         }

         if (this.depthRenderer == null) {
            this.depthRenderer = new Renderer3D(ReviveRenderPipelines.WORLD_COLORED_LINES_DEPTH, ReviveRenderPipelines.WORLD_COLORED_DEPTH);
         }

         Render3DEvent event = Render3DEvent.get(
            matrixStack,
            this.renderer,
            this.depthRenderer,
            tickDelta,
            this.field_18765.getCameraPos().x,
            this.field_18765.getCameraPos().y,
            this.field_18765.getCameraPos().z
         );
         RenderUtils.updateScreenCenter(projection, view);
         NametagUtils.onRender(view);
         RenderSystem.getModelViewStack().pushMatrix().mul(view);
         this.matrices.push();
         this.method_3198(this.matrices, this.field_18765.getLastTickProgress());
         if ((Boolean)this.field_4015.options.getBobView().getValue()) {
            this.method_3186(this.matrices, this.field_18765.getLastTickProgress());
         }

         RenderSystem.getModelViewStack().mul(this.matrices.peek().getPositionMatrix().invert());
         this.matrices.pop();
         this.renderer.begin();
         this.depthRenderer.begin();
         ReviveClient.EVENT_BUS.post(event);
         this.renderer.render(matrixStack);
         this.depthRenderer.render(matrixStack);
         RenderSystem.getModelViewStack().popMatrix();
         Profilers.get().pop();
      }
   }

   @ModifyReturnValue(
      method = {"method_3196"},
      at = {@At("RETURN")}
   )
   private float modifyFov(float original) {
      return ((GetFovEvent)ReviveClient.EVENT_BUS.post(GetFovEvent.get(original))).fov;
   }
}
