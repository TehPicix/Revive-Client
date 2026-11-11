package revivedevelopment.reviveclient.utils.render;

import net.minecraft.client.render.VertexConsumer;

public class NoopVertexConsumer implements VertexConsumer {
   public static final NoopVertexConsumer INSTANCE = new NoopVertexConsumer();

   private NoopVertexConsumer() {
   }

   public VertexConsumer vertex(float x, float y, float z) {
      return this;
   }

   public VertexConsumer color(int red, int green, int blue, int alpha) {
      return this;
   }

   public VertexConsumer color(int argb) {
      return this;
   }

   public VertexConsumer texture(float u, float v) {
      return this;
   }

   public VertexConsumer overlay(int u, int v) {
      return this;
   }

   public VertexConsumer light(int u, int v) {
      return this;
   }

   public VertexConsumer normal(float x, float y, float z) {
      return this;
   }

   public VertexConsumer lineWidth(float width) {
      return this;
   }
}
