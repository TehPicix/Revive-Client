package revivedevelopment.reviveclient.utils.render;

import java.util.Optional;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.BufferAllocator;

public class CustomOutlineVertexConsumerProvider implements VertexConsumerProvider {
   private final Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(1536));

   public VertexConsumer getBuffer(RenderLayer layer) {
      if (layer.isOutline()) {
         return new CustomOutlineVertexConsumerProvider.CustomVertexConsumer(this.immediate.getBuffer(layer));
      } else {
         Optional<RenderLayer> optional = layer.getAffectedOutline();
         return (VertexConsumer)(optional.isPresent()
            ? new CustomOutlineVertexConsumerProvider.CustomVertexConsumer(this.immediate.getBuffer(optional.get()))
            : NoopVertexConsumer.INSTANCE);
      }
   }

   public void draw() {
      this.immediate.draw();
   }

   private record CustomVertexConsumer(VertexConsumer consumer) implements VertexConsumer {
      public VertexConsumer vertex(float x, float y, float z) {
         this.consumer.vertex(x, y, z);
         return this;
      }

      public VertexConsumer color(int red, int green, int blue, int alpha) {
         this.consumer.color(red, green, blue, alpha);
         return this;
      }

      public VertexConsumer color(int argb) {
         this.consumer.color(argb);
         return this;
      }

      public VertexConsumer texture(float u, float v) {
         this.consumer.texture(u, v);
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
}
