package revivedevelopment.reviveclient.utils.render.postprocess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.entity.Entity;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IWorldRenderer;
import revivedevelopment.reviveclient.utils.render.CustomOutlineVertexConsumerProvider;

public abstract class EntityShader extends PostProcessShader {
   public final CustomOutlineVertexConsumerProvider vertexConsumerProvider = new CustomOutlineVertexConsumerProvider();

   protected EntityShader(RenderPipeline pipeline) {
      super(pipeline);
   }

   public abstract boolean shouldDraw(Entity var1);

   @Override
   protected void preDraw() {
      ((IWorldRenderer)ReviveClient.mc.worldRenderer).revive$pushEntityOutlineFramebuffer(this.framebuffer);
   }

   @Override
   protected void postDraw() {
      ((IWorldRenderer)ReviveClient.mc.worldRenderer).revive$popEntityOutlineFramebuffer();
   }

   public void submitVertices() {
      this.submitVertices(this.vertexConsumerProvider::draw);
   }
}
