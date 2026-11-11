package revivedevelopment.reviveclient.utils.render;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;

public class NoopOutlineVertexConsumerProvider extends OutlineVertexConsumerProvider {
   public static final NoopOutlineVertexConsumerProvider INSTANCE = new NoopOutlineVertexConsumerProvider();

   private NoopOutlineVertexConsumerProvider() {
   }

   public VertexConsumer getBuffer(RenderLayer layer) {
      return NoopVertexConsumer.INSTANCE;
   }

   public void draw() {
   }
}
