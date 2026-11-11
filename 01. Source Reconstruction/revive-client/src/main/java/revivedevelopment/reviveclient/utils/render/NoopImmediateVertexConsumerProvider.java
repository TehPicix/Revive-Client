package revivedevelopment.reviveclient.utils.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;

public class NoopImmediateVertexConsumerProvider extends Immediate {
   public static final NoopImmediateVertexConsumerProvider INSTANCE = new NoopImmediateVertexConsumerProvider();

   private NoopImmediateVertexConsumerProvider() {
      super(null, null);
   }

   public VertexConsumer getBuffer(RenderLayer layer) {
      return NoopVertexConsumer.INSTANCE;
   }

   public void draw() {
   }

   public void draw(RenderLayer layer) {
   }
}
