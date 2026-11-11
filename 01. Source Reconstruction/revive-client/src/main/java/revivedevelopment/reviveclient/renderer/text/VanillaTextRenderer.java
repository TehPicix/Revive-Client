package revivedevelopment.reviveclient.renderer.text;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class VanillaTextRenderer implements TextRenderer {
   public static final VanillaTextRenderer INSTANCE = new VanillaTextRenderer();
   private final BufferAllocator buffer = new BufferAllocator(2048);
   private final Immediate immediate = VertexConsumerProvider.immediate(this.buffer);
   private final MatrixStack matrices = new MatrixStack();
   private final Matrix4f emptyMatrix = new Matrix4f();
   public double scale = 2.0;
   public boolean scaleIndividually;
   private boolean building;
   private double alpha = 1.0;

   private VanillaTextRenderer() {
   }

   @Override
   public void setAlpha(double a) {
      this.alpha = a;
   }

   @Override
   public double getWidth(String text, int length, boolean shadow) {
      if (text.isEmpty()) {
         return 0.0;
      } else {
         if (length != text.length()) {
            text = text.substring(0, length);
         }

         return (ReviveClient.mc.textRenderer.getWidth(text) + (shadow ? 1 : 0)) * this.scale;
      }
   }

   @Override
   public double getHeight(boolean shadow) {
      return (9 + (shadow ? 1 : 0)) * this.scale;
   }

   @Override
   public void begin(double scale, boolean scaleOnly, boolean big) {
      if (this.building) {
         throw new RuntimeException("VanillaTextRenderer.begin() called twice");
      } else {
         this.scale = scale * 2.0;
         this.building = true;
      }
   }

   @Override
   public double render(String text, double x, double y, Color color, boolean shadow) {
      boolean wasBuilding = this.building;
      if (!wasBuilding) {
         this.begin();
      }

      x += 0.5 * this.scale;
      y += 0.5 * this.scale;
      int preA = color.a;
      color.a = (int)(color.a / 255.0 * this.alpha * 255.0);
      Matrix4f matrix = this.emptyMatrix;
      if (this.scaleIndividually) {
         this.matrices.push();
         this.matrices.scale((float)this.scale, (float)this.scale, 1.0F);
         matrix = this.matrices.peek().getPositionMatrix();
      }

      ReviveClient.mc
         .textRenderer
         .draw(text, (float)(x / this.scale), (float)(y / this.scale), color.getPacked(), shadow, matrix, this.immediate, TextLayerType.NORMAL, 0, 15728880);
      double x2 = x / this.scale + ReviveClient.mc.textRenderer.getWidth(text);
      if (this.scaleIndividually) {
         this.matrices.pop();
      }

      color.a = preA;
      if (!wasBuilding) {
         this.end();
      }

      return (x2 - 1.0) * this.scale;
   }

   @Override
   public boolean isBuilding() {
      return this.building;
   }

   @Override
   public void end() {
      if (!this.building) {
         throw new RuntimeException("VanillaTextRenderer.end() called without calling begin()");
      } else {
         Matrix4fStack matrixStack = RenderSystem.getModelViewStack();
         matrixStack.pushMatrix();
         if (!this.scaleIndividually) {
            matrixStack.scale((float)this.scale, (float)this.scale, 1.0F);
         }

         this.immediate.draw();
         matrixStack.popMatrix();
         this.scale = 2.0;
         this.building = false;
      }
   }
}
