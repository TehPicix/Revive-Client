package revivedevelopment.reviveclient.events.render;

import net.minecraft.client.util.math.MatrixStack;
import revivedevelopment.reviveclient.renderer.Renderer3D;
import revivedevelopment.reviveclient.utils.Utils;

public class Render3DEvent {
   private static final Render3DEvent INSTANCE = new Render3DEvent();
   public MatrixStack matrices;
   public Renderer3D renderer;
   public Renderer3D depthRenderer;
   public double frameTime;
   public float tickDelta;
   public double offsetX;
   public double offsetY;
   public double offsetZ;

   public static Render3DEvent get(
      MatrixStack matrices, Renderer3D renderer, Renderer3D depthRenderer, float tickDelta, double offsetX, double offsetY, double offsetZ
   ) {
      INSTANCE.matrices = matrices;
      INSTANCE.renderer = renderer;
      INSTANCE.depthRenderer = depthRenderer;
      INSTANCE.frameTime = Utils.frameTime;
      INSTANCE.tickDelta = tickDelta;
      INSTANCE.offsetX = offsetX;
      INSTANCE.offsetY = offsetY;
      INSTANCE.offsetZ = offsetZ;
      return INSTANCE;
   }
}
