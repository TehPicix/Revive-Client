package revivedevelopment.reviveclient.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import revivedevelopment.reviveclient.utils.PreInit;

public class FullScreenRenderer {
   public static GpuBuffer vbo;
   public static GpuBuffer ibo;
   @Deprecated(
      forRemoval = true
   )
   public static MeshBuilder mesh;
   private static boolean loaded;

   private FullScreenRenderer() {
   }

   @PreInit
   public static void init() {
   }

   public static void ensureLoaded() {
      if (!loaded) {
         loaded = true;
         mesh = new MeshBuilder(ReviveVertexFormats.POS2, DrawMode.TRIANGLES, 4, 6);
         mesh.begin();
         mesh.quad(mesh.vec2(-1.0, -1.0).next(), mesh.vec2(-1.0, 1.0).next(), mesh.vec2(1.0, 1.0).next(), mesh.vec2(1.0, -1.0).next());
         mesh.end();
         vbo = mesh.getVertexBuffer();
         ibo = mesh.getIndexBuffer();
      }
   }
}
