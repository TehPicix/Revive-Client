package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import revivedevelopment.reviveclient.mixininterface.IEmoteRenderState;
import revivedevelopment.reviveclient.systems.emotes.EmotePose;

@Mixin({PlayerEntityRenderState.class})
public abstract class PlayerEntityRenderStateEmoteMixin implements IEmoteRenderState {
   @Unique
   private EmotePose revive$emotePose;

   @Override
   public EmotePose revive$getEmotePose() {
      return this.revive$emotePose;
   }

   @Override
   public void revive$setEmotePose(EmotePose pose) {
      this.revive$emotePose = pose;
   }
}
