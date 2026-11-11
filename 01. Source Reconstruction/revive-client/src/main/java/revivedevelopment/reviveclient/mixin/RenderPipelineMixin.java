package revivedevelopment.reviveclient.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import revivedevelopment.reviveclient.mixininterface.IRenderPipeline;

@Mixin({RenderPipeline.class})
public abstract class RenderPipelineMixin implements IRenderPipeline {
   @Unique
   private boolean lineSmooth;

   @Override
   public void revive$setLineSmooth(boolean lineSmooth) {
      this.lineSmooth = lineSmooth;
   }

   @Override
   public boolean revive$getLineSmooth() {
      return this.lineSmooth;
   }
}
