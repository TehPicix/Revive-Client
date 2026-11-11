package revivedevelopment.reviveclient.events.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

public class SubmitEntitiesEvent {
   private static final SubmitEntitiesEvent INSTANCE = new SubmitEntitiesEvent();
   public MatrixStack matrices;
   public OrderedRenderCommandQueue queue;
   public CameraRenderState cameraState;

   public static SubmitEntitiesEvent get(MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
      INSTANCE.matrices = matrices;
      INSTANCE.queue = queue;
      INSTANCE.cameraState = cameraState;
      return INSTANCE;
   }
}
