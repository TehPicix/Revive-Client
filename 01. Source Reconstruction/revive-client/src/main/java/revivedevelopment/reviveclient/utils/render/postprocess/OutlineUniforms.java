package revivedevelopment.reviveclient.utils.render.postprocess;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.DynamicUniformStorage.Uploadable;

public class OutlineUniforms {
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putInt().putFloat().putInt().putFloat().get();
   private static DynamicUniformStorage<OutlineUniforms.Data> STORAGE;

   private static DynamicUniformStorage<OutlineUniforms.Data> storage() {
      if (STORAGE == null) {
         STORAGE = new DynamicUniformStorage("Revive - Outline UBO", UNIFORM_SIZE, 16);
      }

      return STORAGE;
   }

   public static void flipFrame() {
      storage().clear();
   }

   public static GpuBufferSlice write(int width, float fillOpacity, int shapeMode, float glowMultiplier) {
      return storage().write(new OutlineUniforms.Data(width, fillOpacity, shapeMode, glowMultiplier));
   }

   private record Data(int width, float fillOpacity, int shapeMode, float glowMultiplier) implements Uploadable {
      public void write(ByteBuffer buffer) {
         Std140Builder.intoBuffer(buffer).putInt(this.width).putFloat(this.fillOpacity).putInt(this.shapeMode).putFloat(this.glowMultiplier);
      }
   }
}
