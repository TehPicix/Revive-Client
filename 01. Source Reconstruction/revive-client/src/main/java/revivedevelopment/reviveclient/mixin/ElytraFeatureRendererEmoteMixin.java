package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.mixininterface.IEmoteRenderState;
import revivedevelopment.reviveclient.systems.emotes.EmotePose;

@Mixin({ElytraFeatureRenderer.class})
public abstract class ElytraFeatureRendererEmoteMixin<S extends BipedEntityRenderState, M extends EntityModel<S>> extends FeatureRenderer<S, M> {
   public ElytraFeatureRendererEmoteMixin(FeatureRendererContext<S, M> context) {
      super(context);
   }

   @Inject(
      method = {"method_17161"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_10197;method_64078(Lnet/minecraft/class_10186$class_10190;Lnet/minecraft/class_5321;Lnet/minecraft/class_3879;Ljava/lang/Object;Lnet/minecraft/class_1799;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;ILnet/minecraft/class_2960;II)V"
      )},
      require = 0
   )
   private void revive$followAnimatedChest(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, S state, float yaw, float pitch, CallbackInfo ci) {
      if (state instanceof IEmoteRenderState access) {
         EmotePose pose = access.revive$getEmotePose();
         if (pose != null && this.getContextModel() instanceof PlayerEntityModel playerModel) {
            playerModel.body.applyTransform(matrices);
            EmotePose.Bone cape = pose.bone("cape");
            if (cape != null && cape.animated) {
               float weight = pose.weight();
               matrices.translate(0.0F, 0.0F, 0.125F);
               matrices.translate(cape.x * weight / 16.0F, -cape.y * weight / 16.0F, cape.z * weight / 16.0F);
               matrices.peek().getPositionMatrix().rotateZYX(cape.rotZ * weight, cape.rotY * weight, cape.rotX * weight);
               matrices.translate(0.0F, 0.0F, -0.125F);
            }
         }
      }
   }
}
