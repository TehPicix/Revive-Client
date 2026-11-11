package revivedevelopment.reviveclient.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import org.apache.commons.io.IOUtils;
import revivedevelopment.reviveclient.ReviveClient;

public abstract class ReviveRenderPipelines {
   private static final List<RenderPipeline> PIPELINES = new ArrayList<>();
   private static final Snippet MESH_UNIFORMS = RenderPipeline.builder(new Snippet[0]).withUniform("MeshData", UniformType.UNIFORM_BUFFER).buildSnippet();
   public static final RenderPipeline WORLD_COLORED = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/world_colored"))
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/pos_color.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline WORLD_TEXTURED = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/world_textured"))
         .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/pos_tex_color.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/pos_tex_color.frag"))
         .withSampler("u_Texture")
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline WORLD_COLORED_LINES = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLineSmooth()
         .withLocation(ReviveClient.identifier("pipeline/world_colored_lines"))
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
         .withVertexShader(ReviveClient.identifier("shaders/pos_color.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline WORLD_COLORED_DEPTH = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/world_colored_depth"))
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/pos_color.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline WORLD_COLORED_LINES_DEPTH = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLineSmooth()
         .withLocation(ReviveClient.identifier("pipeline/world_colored_lines_depth"))
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
         .withVertexShader(ReviveClient.identifier("shaders/pos_color.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline UI_COLORED = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/ui_colored"))
         .withVertexFormat(ReviveVertexFormats.POS2_COLOR, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/pos_color.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline UI_COLORED_LINES = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/ui_colored_lines"))
         .withVertexFormat(ReviveVertexFormats.POS2_COLOR, DrawMode.DEBUG_LINES)
         .withVertexShader(ReviveClient.identifier("shaders/pos_color.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline UI_TEXTURED = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/ui_textured"))
         .withVertexFormat(ReviveVertexFormats.POS2_TEXTURE_COLOR, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/pos_tex_color.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/pos_tex_color.frag"))
         .withSampler("u_Texture")
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline UI_TEXT = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/ui_text"))
         .withVertexFormat(ReviveVertexFormats.POS2_TEXTURE_COLOR, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/text.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/text.frag"))
         .withSampler("u_Texture")
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline POST_OUTLINE = add(
      new ExtendedRenderPipelineBuilder()
         .withLocation(ReviveClient.identifier("pipeline/post/outline"))
         .withVertexFormat(ReviveVertexFormats.POS2, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/post-process/base.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/post-process/outline.frag"))
         .withSampler("u_Texture")
         .withUniform("PostData", UniformType.UNIFORM_BUFFER)
         .withUniform("OutlineData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline POST_IMAGE = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/post/image"))
         .withVertexFormat(ReviveVertexFormats.POS2, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/post-process/base.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/post-process/image.frag"))
         .withSampler("u_Texture")
         .withSampler("u_TextureI")
         .withUniform("PostData", UniformType.UNIFORM_BUFFER)
         .withUniform("ImageData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline POST_SKY = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/post/sky"))
         .withVertexFormat(ReviveVertexFormats.POS2, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/post-process/sky.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/post-process/sky.frag"))
         .withUniform("PostData", UniformType.UNIFORM_BUFFER)
         .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline POST_GUI_BG = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/post/gui_bg"))
         .withVertexFormat(ReviveVertexFormats.POS2, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/post-process/gui-bg.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/post-process/gui-bg.frag"))
         .withUniform("GuiPost", UniformType.UNIFORM_BUFFER)
         .withUniform("GuiData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline POST_GUI_COMPOSITE = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/post/gui_composite"))
         .withVertexFormat(ReviveVertexFormats.POS2, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/post-process/gui-bg.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/post-process/gui-composite.frag"))
         .withSampler("u_Texture")
         .withUniform("GuiPost", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline BLUR_DOWN = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/blur/down"))
         .withVertexFormat(ReviveVertexFormats.POS2, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/blur.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/blur_down.frag"))
         .withSampler("u_Texture")
         .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline BLUR_UP = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/blur/up"))
         .withVertexFormat(ReviveVertexFormats.POS2, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/blur.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/blur_up.frag"))
         .withSampler("u_Texture")
         .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline BLUR_PASSTHROUGH = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(ReviveClient.identifier("pipeline/blur/up"))
         .withVertexFormat(ReviveVertexFormats.POS2, DrawMode.TRIANGLES)
         .withVertexShader(ReviveClient.identifier("shaders/passthrough.vert"))
         .withFragmentShader(ReviveClient.identifier("shaders/passthrough.frag"))
         .withSampler("u_Texture")
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );

   private static RenderPipeline add(RenderPipeline pipeline) {
      PIPELINES.add(pipeline);
      return pipeline;
   }

   public static void precompile() {
      GpuDevice device = RenderSystem.getDevice();
      ResourceManager resources = MinecraftClient.getInstance().getResourceManager();

      for (RenderPipeline pipeline : PIPELINES) {
         device.precompilePipeline(pipeline, (identifier, shaderType) -> {
            Resource resource = (Resource)resources.getResource(identifier).get();

            try {
               String var5;
               try (InputStream in = resource.getInputStream()) {
                  var5 = IOUtils.toString(in, StandardCharsets.UTF_8);
               }

               return var5;
            } catch (IOException var9) {
               throw new RuntimeException(var9);
            }
         });
      }
   }

   private ReviveRenderPipelines() {
   }
}
