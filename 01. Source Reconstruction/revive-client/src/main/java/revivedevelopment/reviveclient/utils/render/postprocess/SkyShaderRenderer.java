package revivedevelopment.reviveclient.utils.render.postprocess;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;

public final class SkyShaderRenderer {
   private static final int POST_SIZE = new Std140SizeCalculator().putVec2().putFloat().get();
   private static DynamicUniformStorage<SkyShaderRenderer.PostData> POST;
   private static final int SKY_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4().get();
   private static DynamicUniformStorage<SkyShaderRenderer.SkyData> SKY;

   private SkyShaderRenderer() {
   }

   private static DynamicUniformStorage<SkyShaderRenderer.PostData> post() {
      if (POST == null) {
         POST = new DynamicUniformStorage("Revive - Sky Post UBO", POST_SIZE, 16);
      }

      return POST;
   }

   private static DynamicUniformStorage<SkyShaderRenderer.SkyData> sky() {
      if (SKY == null) {
         SKY = new DynamicUniformStorage("Revive - Sky UBO", SKY_SIZE, 16);
      }

      return SKY;
   }

   public static void flipFrame() {
      post().clear();
      sky().clear();
   }

   public static void render(
      int mode, float opacity, int primaryRgb, int secondaryRgb, Vector3f forward, Vector3f right, Vector3f up, float tanHalfFov, float aspect
   ) {
      if (mode >= 0 && !(opacity <= 0.0F) && ReviveClient.mc.getFramebuffer() != null) {
         int fbWidth = ReviveClient.mc.getWindow().getFramebufferWidth();
         int fbHeight = ReviveClient.mc.getWindow().getFramebufferHeight();
         if (fbWidth > 0 && fbHeight > 0) {
            float pr = (primaryRgb >> 16 & 0xFF) / 255.0F;
            float pg = (primaryRgb >> 8 & 0xFF) / 255.0F;
            float pb = (primaryRgb & 0xFF) / 255.0F;
            float sr = (secondaryRgb >> 16 & 0xFF) / 255.0F;
            float sg = (secondaryRgb >> 8 & 0xFF) / 255.0F;
            float sb = (secondaryRgb & 0xFF) / 255.0F;

            try {
               GpuBufferSlice postSlice = post().write(new SkyShaderRenderer.PostData((float)fbWidth, (float)fbHeight, (float)(GLFW.glfwGetTime() % 3600.0)));
               GpuBufferSlice skySlice = sky()
                  .write(
                     new SkyShaderRenderer.SkyData(
                        pr,
                        pg,
                        pb,
                        opacity,
                        sr,
                        sg,
                        sb,
                        (float)mode,
                        forward.x,
                        forward.y,
                        forward.z,
                        tanHalfFov,
                        right.x,
                        right.y,
                        right.z,
                        aspect,
                        up.x,
                        up.y,
                        up.z
                     )
                  );
               MeshRenderer.begin()
                  .attachments(ReviveClient.mc.getFramebuffer())
                  .pipeline(ReviveRenderPipelines.POST_SKY)
                  .fullscreen()
                  .uniform("PostData", postSlice)
                  .uniform("SkyData", skySlice)
                  .end();
            } catch (Throwable var19) {
            }
         }
      }
   }

   private record PostData(float sizeX, float sizeY, float time) implements Uploadable {
      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer).putVec2(this.sizeX, this.sizeY).putFloat(this.time);
      }
   }

   private record SkyData(
      float pr,
      float pg,
      float pb,
      float opacity,
      float sr,
      float sg,
      float sb,
      float mode,
      float fx,
      float fy,
      float fz,
      float tanHalfFov,
      float rx,
      float ry,
      float rz,
      float aspect,
      float ux,
      float uy,
      float uz
   ) implements Uploadable {
      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer)
            .putVec4(this.pr, this.pg, this.pb, this.opacity)
            .putVec4(this.sr, this.sg, this.sb, this.mode)
            .putVec4(this.fx, this.fy, this.fz, this.tanHalfFov)
            .putVec4(this.rx, this.ry, this.rz, this.aspect)
            .putVec4(this.ux, this.uy, this.uz, 0.0F);
      }
   }
}
