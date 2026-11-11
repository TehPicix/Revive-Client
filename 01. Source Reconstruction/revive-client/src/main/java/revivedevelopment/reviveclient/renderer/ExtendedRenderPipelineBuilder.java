package revivedevelopment.reviveclient.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import revivedevelopment.reviveclient.mixininterface.IRenderPipeline;

public class ExtendedRenderPipelineBuilder extends Builder {
   private boolean lineSmooth;

   public ExtendedRenderPipelineBuilder(Snippet... snippets) {
      for (Snippet snippet : snippets) {
         this.withSnippet(snippet);
      }
   }

   public ExtendedRenderPipelineBuilder withLineSmooth() {
      this.lineSmooth = true;
      return this;
   }

   public RenderPipeline build() {
      RenderPipeline pipeline = super.build();
      ((IRenderPipeline)pipeline).revive$setLineSmooth(this.lineSmooth);
      return pipeline;
   }
}
