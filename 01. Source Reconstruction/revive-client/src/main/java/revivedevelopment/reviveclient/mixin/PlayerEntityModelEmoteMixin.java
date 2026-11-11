package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerCapeModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IEmoteRenderState;
import revivedevelopment.reviveclient.systems.emotes.EmoteApplier;
import revivedevelopment.reviveclient.systems.emotes.EmoteButt;
import revivedevelopment.reviveclient.systems.emotes.EmotePose;

@Mixin({PlayerEntityModel.class})
public abstract class PlayerEntityModelEmoteMixin {
   @Unique
   private ModelPart revive$butt;

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void revive$attachButt(CallbackInfo ci) {
      if (!((Object)this instanceof PlayerCapeModel)) {
         try {
            this.revive$butt = EmoteButt.attach(((PlayerEntityModel)(Object)this).body);
         } catch (Throwable var3) {
            ReviveClient.LOG.warn("[Emotes] could not attach the emote butt part: {}", var3.toString());
         }
      }
   }

   @Inject(
      method = {"method_62110"},
      at = {@At("TAIL")}
   )
   private void revive$applyEmote(PlayerEntityRenderState state, CallbackInfo ci) {
      EmotePose pose = ((IEmoteRenderState)state).revive$getEmotePose();
      boolean butt = pose != null && pose.isAnimated("butt") && this.revive$butt != null;
      if (this.revive$butt != null) {
         this.revive$butt.visible = butt;
      }

      if (pose != null) {
         PlayerEntityModel self = (PlayerEntityModel)(Object)this;
         EmoteApplier.apply(pose, "head", self.head);
         EmoteApplier.apply(pose, "torso", self.body);
         EmoteApplier.apply(pose, "right_arm", self.rightArm);
         EmoteApplier.apply(pose, "left_arm", self.leftArm);
         EmoteApplier.apply(pose, "right_leg", self.rightLeg);
         EmoteApplier.apply(pose, "left_leg", self.leftLeg);
         if (butt) {
            EmoteApplier.apply(pose, "butt", this.revive$butt);
            this.revive$butt.xScale = this.revive$butt.yScale = this.revive$butt.zScale = pose.weight();
         }
      }
   }
}
