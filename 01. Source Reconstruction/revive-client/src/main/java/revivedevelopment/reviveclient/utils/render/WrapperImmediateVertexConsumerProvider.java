package revivedevelopment.reviveclient.utils.render;

import java.util.function.Supplier;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;

public class WrapperImmediateVertexConsumerProvider extends Immediate {
   private final Supplier<VertexConsumerProvider> supplier;

   public WrapperImmediateVertexConsumerProvider(Supplier<VertexConsumerProvider> supplier) {
      super(null, null);
      this.supplier = supplier;
   }

   public VertexConsumer getBuffer(RenderLayer layer) {
      return this.supplier.get().getBuffer(layer);
   }

   public void draw() {
   }

   public void draw(RenderLayer layer) {
   }
}
