package revivedevelopment.reviveclient.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;
import org.joml.Matrix4f;

public class MeshUniforms {
   public static final int SIZE = new Std140SizeCalculator().putMat4f().putMat4f().get();
   private static final MeshUniforms.Data DATA = new MeshUniforms.Data();
   private static DynamicUniformStorage<MeshUniforms.Data> STORAGE;

   private static DynamicUniformStorage<MeshUniforms.Data> storage() {
      if (STORAGE == null) {
         STORAGE = new DynamicUniformStorage("Revive - Mesh UBO", SIZE, 16);
      }

      return STORAGE;
   }

   public static void flipFrame() {
      storage().clear();
   }

   public static GpuBufferSlice write(Matrix4f proj, Matrix4f modelView) {
      DATA.proj = proj;
      DATA.modelView = modelView;
      return storage().write(DATA);
   }

   private static final class Data implements Uploadable {
      private Matrix4f proj;
      private Matrix4f modelView;

      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer).putMat4f(this.proj).putMat4f(this.modelView);
      }

      @Override
      public boolean equals(Object o) {
         return false;
      }
   }
}
