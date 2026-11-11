package revivedevelopment.reviveclient.utils.render.postprocess;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;
import org.lwjgl.glfw.GLFW;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.renderer.MeshRenderer;

public abstract class PostProcessShader {
   protected final RenderPipeline pipeline;
   public final Framebuffer framebuffer;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec2().putFloat().get();
   private static DynamicUniformStorage<PostProcessShader.UniformData> UNIFORM_STORAGE;

   protected PostProcessShader(RenderPipeline pipeline) {
      this.pipeline = pipeline;
      this.framebuffer = new SimpleFramebuffer(
         ReviveClient.NAME + " PostProcessShader " + this.getClass().getSimpleName(),
         ReviveClient.mc.getWindow().getFramebufferWidth(),
         ReviveClient.mc.getWindow().getFramebufferHeight(),
         true
      );
   }

   public abstract boolean shouldDraw();

   protected void preDraw() {
   }

   protected void postDraw() {
   }

   protected abstract void setupPass(MeshRenderer var1);

   public void clearTexture() {
      if (this.shouldDraw()) {
         RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.framebuffer.getColorAttachment(), 0);
      }
   }

   public void submitVertices(Runnable draw) {
      if (this.shouldDraw()) {
         this.preDraw();

         try {
            draw.run();
         } finally {
            this.postDraw();
         }
      }
   }

   public void render() {
      if (this.shouldDraw()) {
         MeshRenderer renderer = MeshRenderer.begin()
            .attachments(ReviveClient.mc.getFramebuffer())
            .pipeline(this.pipeline)
            .fullscreen()
            .uniform(
               "PostData",
               uniformStorage()
                  .write(
                     new PostProcessShader.UniformData(
                        (float)ReviveClient.mc.getWindow().getFramebufferWidth(),
                        (float)ReviveClient.mc.getWindow().getFramebufferHeight(),
                        (float)(GLFW.glfwGetTime() % 3600.0)
                     )
                  )
            )
            .sampler("u_Texture", this.framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
         this.setupPass(renderer);
         renderer.end();
      }
   }

   public void onResized(int width, int height) {
      if (this.framebuffer != null) {
         this.framebuffer.resize(width, height);
      }
   }

   private static DynamicUniformStorage<PostProcessShader.UniformData> uniformStorage() {
      if (UNIFORM_STORAGE == null) {
         UNIFORM_STORAGE = new DynamicUniformStorage("Revive - Post UBO", UNIFORM_SIZE, 16);
      }

      return UNIFORM_STORAGE;
   }

   public static void flipFrame() {
      uniformStorage().clear();
   }

   private record UniformData(float sizeX, float sizeY, float time) implements Uploadable {
      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer).putVec2(this.sizeX, this.sizeY).putFloat(this.time);
      }
   }
}
