package revivedevelopment.reviveclient.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import java.util.HashMap;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Pair;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class MeshRenderer {
   private static final MeshRenderer INSTANCE = new MeshRenderer();
   private static boolean taken;
   private GpuTextureView colorAttachment;
   private GpuTextureView depthAttachment;
   private Color clearColor;
   private RenderPipeline pipeline;
   @Nullable
   private MeshBuilder mesh;
   @Nullable
   private GpuBuffer vertexBuffer;
   @Nullable
   private GpuBuffer indexBuffer;
   private Matrix4f matrix;
   private final HashMap<String, GpuBufferSlice> uniforms = new HashMap<>();
   private final HashMap<String, Pair<GpuTextureView, GpuSampler>> samplers = new HashMap<>();

   private MeshRenderer() {
   }

   public static MeshRenderer begin() {
      if (taken) {
         throw new IllegalStateException("Previous instance of MeshRenderer was not ended");
      } else {
         taken = true;
         return INSTANCE;
      }
   }

   public MeshRenderer attachments(GpuTextureView color, GpuTextureView depth) {
      this.colorAttachment = color;
      this.depthAttachment = depth;
      return this;
   }

   public MeshRenderer attachments(Framebuffer framebuffer) {
      this.colorAttachment = framebuffer.getColorAttachmentView();
      this.depthAttachment = framebuffer.getDepthAttachmentView();
      return this;
   }

   public MeshRenderer clearColor(Color color) {
      this.clearColor = color;
      return this;
   }

   public MeshRenderer pipeline(RenderPipeline pipeline) {
      this.pipeline = pipeline;
      return this;
   }

   public MeshRenderer mesh(GpuBuffer vertices, GpuBuffer indices) {
      this.vertexBuffer = vertices;
      this.indexBuffer = indices;
      return this;
   }

   public MeshRenderer mesh(MeshBuilder mesh) {
      this.mesh = mesh;
      return this;
   }

   public MeshRenderer mesh(MeshBuilder mesh, Matrix4f matrix) {
      this.mesh = mesh;
      return this.transform(matrix);
   }

   public MeshRenderer mesh(MeshBuilder mesh, MatrixStack matrices) {
      this.mesh = mesh;
      return this.transform(matrices);
   }

   public MeshRenderer transform(Matrix4f matrix) {
      this.matrix = matrix;
      return this;
   }

   public MeshRenderer transform(MatrixStack matrices) {
      this.matrix = matrices.peek().getPositionMatrix();
      return this;
   }

   public MeshRenderer fullscreen() {
      FullScreenRenderer.ensureLoaded();
      return this.mesh(FullScreenRenderer.vbo, FullScreenRenderer.ibo);
   }

   public MeshRenderer uniform(String name, GpuBufferSlice slice) {
      this.uniforms.put(name, slice);
      return this;
   }

   public MeshRenderer sampler(String name, GpuTextureView view, GpuSampler sampler) {
      if (name != null && view != null && sampler != null) {
         this.samplers.put(name, new Pair(view, sampler));
      }

      return this;
   }

   public void end() {
      try {
         this.endInternal();
      } finally {
         this.colorAttachment = null;
         this.depthAttachment = null;
         this.clearColor = null;
         this.pipeline = null;
         this.mesh = null;
         this.vertexBuffer = null;
         this.indexBuffer = null;
         this.matrix = null;
         this.uniforms.clear();
         this.samplers.clear();
         taken = false;
      }
   }

   private void endInternal() {
      if (this.mesh != null && this.mesh.isBuilding()) {
         this.mesh.end();
      }

      int indexCount = this.mesh != null ? this.mesh.getIndicesCount() : (int)(this.indexBuffer != null ? this.indexBuffer.size() / 4L : -1L);
      if (indexCount > 0) {
         if (Utils.rendering3D || this.matrix != null) {
            RenderSystem.getModelViewStack().pushMatrix();
         }

         if (this.matrix != null) {
            RenderSystem.getModelViewStack().mul(this.matrix);
         }

         if (Utils.rendering3D) {
            applyCameraPos();
         }

         GpuBuffer vertexBuffer = this.mesh != null ? this.mesh.getVertexBuffer() : this.vertexBuffer;
         GpuBuffer indexBuffer = this.mesh != null ? this.mesh.getIndexBuffer() : this.indexBuffer;
         OptionalInt clearColor = this.clearColor != null
            ? OptionalInt.of(ColorHelper.getArgb(this.clearColor.a, this.clearColor.r, this.clearColor.g, this.clearColor.b))
            : OptionalInt.empty();
         GpuBufferSlice meshData = MeshUniforms.write(RenderUtils.projection, RenderSystem.getModelViewStack());
         RenderPass pass = this.depthAttachment != null && this.pipeline.wantsDepthTexture()
            ? RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> "Revive MeshRenderer", this.colorAttachment, clearColor, this.depthAttachment, OptionalDouble.empty())
            : RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Revive MeshRenderer", this.colorAttachment, clearColor);
         pass.setPipeline(this.pipeline);
         pass.setUniform("MeshData", meshData);

         for (String name : this.uniforms.keySet()) {
            pass.setUniform(name, this.uniforms.get(name));
         }

         for (String name : this.samplers.keySet()) {
            pass.bindTexture(name, (GpuTextureView)this.samplers.get(name).getLeft(), (GpuSampler)this.samplers.get(name).getRight());
         }

         pass.setVertexBuffer(0, vertexBuffer);
         pass.setIndexBuffer(indexBuffer, IndexType.INT);
         pass.drawIndexed(0, 0, indexCount, 1);
         pass.close();
         if (Utils.rendering3D || this.matrix != null) {
            RenderSystem.getModelViewStack().popMatrix();
         }
      }
   }

   private static void applyCameraPos() {
      Vec3d cameraPos = ReviveClient.mc.gameRenderer.getCamera().getCameraPos();
      RenderSystem.getModelViewStack().translate(0.0F, (float)(-cameraPos.y), 0.0F);
   }
}
