package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IEmoteRenderState;
import revivedevelopment.reviveclient.systems.emotes.EmoteApplier;
import revivedevelopment.reviveclient.systems.emotes.EmoteAutoPlay;
import revivedevelopment.reviveclient.systems.emotes.EmoteEngine;
import revivedevelopment.reviveclient.systems.emotes.EmotePose;

@Mixin({PlayerEntityRenderer.class})
public abstract class PlayerEntityRendererEmoteMixin {
   @Inject(
      method = {"method_62604"},
      at = {@At("TAIL")}
   )
   private void revive$sampleEmote(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
      ((IEmoteRenderState)state).revive$setEmotePose(EmoteEngine.get().pose(entity.getUuid(), tickDelta));
      if (EmoteAutoPlay.standsUpright(entity.getUuid())) {
         state.isGliding = false;
         state.glidingTicks = 0.0F;
         state.leaningPitch = 0.0F;
      }
   }

   @Inject(
      method = {"method_4212"},
      at = {@At("TAIL")}
   )
   private void revive$applyRootBone(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float scale, CallbackInfo ci) {
      EmotePose pose = ((IEmoteRenderState)state).revive$getEmotePose();
      if (pose != null) {
         EmotePose.Bone body = pose.bone("body");
         if (body != null && body.animated) {
            float weight = pose.weight();
            matrices.translate(body.x / 16.0F * weight, body.y / 16.0F * weight, body.z / 16.0F * weight);
            if (body.rotX != 0.0F || body.rotY != 0.0F || body.rotZ != 0.0F) {
               matrices.peek().getPositionMatrix().rotateZYX(body.rotZ * weight, body.rotY * weight, body.rotX * weight);
            }
         }
      }
   }

   @Inject(
      method = {"method_23205"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_11659;method_73491(Lnet/minecraft/class_630;Lnet/minecraft/class_4587;Lnet/minecraft/class_1921;IILnet/minecraft/class_1058;)V"
      )},
      require = 0
   )
   private void revive$animateFirstPersonArm(
      MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Identifier texture, ModelPart arm, boolean sleeveVisible, CallbackInfo ci
   ) {
      if (ReviveClient.mc.player != null) {
         EmotePose pose = EmoteEngine.get().pose(ReviveClient.mc.player.getUuid(), ReviveClient.mc.getRenderTickCounter().getTickProgress(true));
         if (pose != null) {
            PlayerEntityModel model = (PlayerEntityModel)((PlayerEntityRenderer)(Object)this).getModel();
            String bone = arm == model.leftArm ? "left_arm" : "right_arm";
            EmoteApplier.apply(pose, bone, arm);
         }
      }
   }
}
