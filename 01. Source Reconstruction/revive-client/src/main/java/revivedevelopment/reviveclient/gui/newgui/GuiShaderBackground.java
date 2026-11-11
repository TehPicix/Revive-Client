package revivedevelopment.reviveclient.gui.newgui;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;
import org.lwjgl.glfw.GLFW;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.utils.render.color.Color;

public final class GuiShaderBackground {
   private static SimpleFramebuffer fbo;
   private static int fboWidth;
   private static int fboHeight;
   private static final Color CLEAR = new Color(0, 0, 0, 0);
   private static final int POST_SIZE = new Std140SizeCalculator().putVec2().putFloat().get();
   private static DynamicUniformStorage<GuiShaderBackground.PostData> POST;
   private static final int GUI_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
   private static DynamicUniformStorage<GuiShaderBackground.GuiData> GUI;

   private GuiShaderBackground() {
   }

   private static DynamicUniformStorage<GuiShaderBackground.PostData> post() {
      if (POST == null) {
         POST = new DynamicUniformStorage("Revive - GuiBg Post UBO", POST_SIZE, 16);
      }

      return POST;
   }

   private static DynamicUniformStorage<GuiShaderBackground.GuiData> gui() {
      if (GUI == null) {
         GUI = new DynamicUniformStorage("Revive - GuiBg UBO", GUI_SIZE, 16);
      }

      return GUI;
   }

   public static void flipFrame() {
      post().clear();
      gui().clear();
   }

   public static void renderFullscreen(int mode, float opacity, int primaryRgb, int secondaryRgb) {
      if (mode > 0 && !(opacity <= 0.0F) && ReviveClient.mc.getFramebuffer() != null) {
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
               ensureFbo(fbWidth, fbHeight);
               GpuBufferSlice postSlice1 = post()
                  .write(new GuiShaderBackground.PostData((float)fbWidth, (float)fbHeight, (float)(GLFW.glfwGetTime() % 3600.0)));
               GpuBufferSlice guiSlice = gui().write(new GuiShaderBackground.GuiData(pr, pg, pb, opacity, sr, sg, sb, (float)mode));
               MeshRenderer.begin()
                  .attachments(fbo)
                  .clearColor(CLEAR)
                  .pipeline(ReviveRenderPipelines.POST_GUI_BG)
                  .fullscreen()
                  .uniform("GuiPost", postSlice1)
                  .uniform("GuiData", guiSlice)
                  .end();
               GpuBufferSlice postSlice2 = post()
                  .write(new GuiShaderBackground.PostData((float)fbWidth, (float)fbHeight, (float)(GLFW.glfwGetTime() % 3600.0)));
               MeshRenderer.begin()
                  .attachments(ReviveClient.mc.getFramebuffer())
                  .pipeline(ReviveRenderPipelines.POST_GUI_COMPOSITE)
                  .fullscreen()
                  .uniform("GuiPost", postSlice2)
                  .sampler("u_Texture", fbo.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST))
                  .end();
            } catch (Throwable var15) {
            }
         }
      }
   }

   private static void ensureFbo(int width, int height) {
      if (fbo == null) {
         fbo = new SimpleFramebuffer(ReviveClient.NAME + " GuiShaderBackground", width, height, false);
         fboWidth = width;
         fboHeight = height;
      } else if (fboWidth != width || fboHeight != height) {
         fbo.resize(width, height);
         fboWidth = width;
         fboHeight = height;
      }
   }

   private record GuiData(float pr, float pg, float pb, float opacity, float sr, float sg, float sb, float mode) implements Uploadable {
      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer).putVec4(this.pr, this.pg, this.pb, this.opacity).putVec4(this.sr, this.sg, this.sb, this.mode);
      }
   }

   public static enum Mode {
      Off,
      Warp,
      Nebula,
      Plasma,
      Space,
      Gspace;
   }

   private record PostData(float sizeX, float sizeY, float time) implements Uploadable {
      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer).putVec2(this.sizeX, this.sizeY).putFloat(this.time);
      }
   }
}
