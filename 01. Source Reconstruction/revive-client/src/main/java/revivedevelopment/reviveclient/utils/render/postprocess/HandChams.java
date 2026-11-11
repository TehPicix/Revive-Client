package revivedevelopment.reviveclient.utils.render.postprocess;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderDispatcher;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IWorldRenderer;
import revivedevelopment.reviveclient.utils.OutlineRenderCommandQueue;
import revivedevelopment.reviveclient.utils.render.CustomOutlineVertexConsumerProvider;
import revivedevelopment.reviveclient.utils.render.NoopImmediateVertexConsumerProvider;
import revivedevelopment.reviveclient.utils.render.NoopOutlineVertexConsumerProvider;
import revivedevelopment.reviveclient.utils.render.WrapperImmediateVertexConsumerProvider;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class HandChams {
   public static final HandChams INSTANCE = new HandChams();
   private final OutlineRenderCommandQueue queue = new OutlineRenderCommandQueue();
   private final CustomOutlineVertexConsumerProvider provider = new CustomOutlineVertexConsumerProvider();
   private RenderDispatcher dispatcher;

   private HandChams() {
   }

   public OrderedRenderCommandQueue begin() {
      this.queue.setColor(Color.WHITE);
      return this.queue;
   }

   public void end() {
      EntityShader shader = PostProcessShaders.CHAMS;
      if (shader != null && ReviveClient.mc.worldRenderer != null) {
         if (this.dispatcher == null) {
            this.dispatcher = new RenderDispatcher(
               this.queue,
               ReviveClient.mc.getBlockRenderManager(),
               new WrapperImmediateVertexConsumerProvider(() -> this.provider),
               ReviveClient.mc.getAtlasManager(),
               NoopOutlineVertexConsumerProvider.INSTANCE,
               NoopImmediateVertexConsumerProvider.INSTANCE,
               ReviveClient.mc.textRenderer
            );
         }

         IWorldRenderer wr = (IWorldRenderer)ReviveClient.mc.worldRenderer;
         wr.revive$pushEntityOutlineFramebuffer(shader.framebuffer);

         try {
            this.dispatcher.render();
            this.provider.draw();
         } finally {
            this.queue.onNextFrame();
            wr.revive$popEntityOutlineFramebuffer();
         }
      } else {
         this.queue.onNextFrame();
      }
   }
}
