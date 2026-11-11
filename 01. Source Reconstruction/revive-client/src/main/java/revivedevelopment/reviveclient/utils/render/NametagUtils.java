package revivedevelopment.reviveclient.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector4f;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.Utils;

public class NametagUtils {
   private static final Vector4f vec4 = new Vector4f();
   private static final Vector4f mmMat4 = new Vector4f();
   private static final Vector4f pmMat4 = new Vector4f();
   private static final Vector3d camera = new Vector3d();
   private static final Vector3d cameraNegated = new Vector3d();
   private static final Matrix4f model = new Matrix4f();
   private static final Matrix4f projection = new Matrix4f();
   private static double windowScale;
   public static double scale;

   private NametagUtils() {
   }

   public static void onRender(Matrix4f modelView) {
      model.set(modelView);
      projection.set(RenderUtils.projection);
      Utils.set(camera, ReviveClient.mc.gameRenderer.getCamera().getCameraPos());
      cameraNegated.set(camera);
      cameraNegated.negate();
      windowScale = ReviveClient.mc.getWindow().calculateScaleFactor(1, false);
   }

   public static boolean to2D(Vector3d pos, double scale) {
      return to2D(pos, scale, true);
   }

   public static boolean to2D(Vector3d pos, double scale, boolean distanceScaling) {
      return to2D(pos, scale, distanceScaling, false);
   }

   public static boolean to2D(Vector3d pos, double scale, boolean distanceScaling, boolean allowBehind) {
      NametagUtils.scale = scale;
      if (distanceScaling) {
         NametagUtils.scale = NametagUtils.scale * getScale(pos);
      }

      vec4.set(cameraNegated.x + pos.x, cameraNegated.y + pos.y, cameraNegated.z + pos.z, 1.0);
      vec4.mul(model, mmMat4);
      mmMat4.mul(projection, pmMat4);
      boolean behind = pmMat4.w <= 0.0F;
      if (behind && !allowBehind) {
         return false;
      } else {
         toScreen(pmMat4);
         double x = pmMat4.x * ReviveClient.mc.getWindow().getFramebufferWidth();
         double y = pmMat4.y * ReviveClient.mc.getWindow().getFramebufferHeight();
         if (behind) {
            x = ReviveClient.mc.getWindow().getFramebufferWidth() - x;
            y = ReviveClient.mc.getWindow().getFramebufferHeight() - y;
         }

         if (!Double.isInfinite(x) && !Double.isInfinite(y)) {
            pos.set(x / windowScale, ReviveClient.mc.getWindow().getFramebufferHeight() - y / windowScale, allowBehind ? pmMat4.w : pmMat4.z);
            return true;
         } else {
            return false;
         }
      }
   }

   public static void begin(Vector3d pos) {
      Matrix4fStack matrices = RenderSystem.getModelViewStack();
      begin(matrices, pos);
   }

   public static void begin(Vector3d pos, DrawContext drawContext) {
      begin(pos);
      float gui = ReviveClient.mc.getWindow().getScaleFactor();
      Matrix3x2fStack matrices = drawContext.getMatrices();
      matrices.pushMatrix();
      matrices.translate((float)(pos.x / gui), (float)(pos.y / gui));
      matrices.scale((float)(scale / gui), (float)(scale / gui));
   }

   private static void begin(Matrix4fStack matrices, Vector3d pos) {
      matrices.pushMatrix();
      matrices.translate((float)pos.x, (float)pos.y, 0.0F);
      matrices.scale((float)scale, (float)scale, 1.0F);
   }

   public static void end() {
      RenderSystem.getModelViewStack().popMatrix();
   }

   public static void end(DrawContext drawContext) {
      end();
      drawContext.getMatrices().popMatrix();
   }

   private static double getScale(Vector3d pos) {
      double dist = camera.distance(pos);
      return MathHelper.clamp(1.0 - dist * 0.01, 0.5, 2.147483647E9);
   }

   private static void toScreen(Vector4f vec) {
      float newW = 1.0F / vec.w * 0.5F;
      vec.x = vec.x * newW + 0.5F;
      vec.y = vec.y * newW + 0.5F;
      vec.z = vec.z * newW + 0.5F;
      vec.w = newW;
   }
}
