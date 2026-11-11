package revivedevelopment.reviveclient.renderer;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormatElement.Type;
import com.mojang.blaze3d.vertex.VertexFormatElement.Usage;

public abstract class ReviveVertexFormatElements {
   public static final VertexFormatElement POS2 = VertexFormatElement.register(getNextVertexFormatElementId(), 0, Type.FLOAT, Usage.POSITION, 2);

   private ReviveVertexFormatElements() {
   }

   private static int getNextVertexFormatElementId() {
      int id = 0;

      while (VertexFormatElement.byId(id) != null) {
         if (++id >= 32) {
            throw new RuntimeException("Too many mods registering VertexFormatElements");
         }
      }

      return id;
   }
}
