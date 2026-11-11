package revivedevelopment.reviveclient.systems.emotes;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import revivedevelopment.reviveclient.mixin.ModelPartAccessor;

public final class EmoteButt {
   public static final String PART_NAME = "revive_butt";
   private static final float ROUND = (float) (Math.PI / 4);

   private EmoteButt() {
   }

   public static ModelPart attach(ModelPart body) {
      ModelData data = new ModelData();
      ModelPartData butt = data.getRoot()
         .addChild(
            "revive_butt",
            ModelPartBuilder.create()
               .uv(0, 20)
               .cuboid(-3.6F, -1.0F, -0.8F, 3.4F, 3.4F, 2.6F)
               .uv(16, 52)
               .cuboid(0.2F, -1.0F, -0.8F, 3.4F, 3.4F, 2.6F)
               .uv(2, 20)
               .cuboid(-1.2F, -0.6F, -0.8F, 2.4F, 2.4F, 2.0F),
            ModelTransform.of(0.0F, 11.0F, 2.0F, 0.0F, 0.0F, 0.0F)
         );
      butt.addChild(
         "revive_butt_r1",
         ModelPartBuilder.create().uv(0, 20).cuboid(-1.55F, -1.55F, -1.1F, 3.1F, 3.1F, 2.2F),
         ModelTransform.of(-1.9F, 0.7F, 0.5F, 0.0F, 0.0F, (float) (Math.PI / 4))
      );
      butt.addChild(
         "revive_butt_r2",
         ModelPartBuilder.create().uv(0, 20).cuboid(-1.55F, -1.5F, -1.1F, 3.1F, 3.0F, 2.2F),
         ModelTransform.of(-1.9F, 0.7F, 0.5F, (float) (Math.PI / 4), 0.0F, 0.0F)
      );
      butt.addChild(
         "revive_butt_l1",
         ModelPartBuilder.create().uv(16, 52).cuboid(-1.55F, -1.55F, -1.1F, 3.1F, 3.1F, 2.2F),
         ModelTransform.of(1.9F, 0.7F, 0.5F, 0.0F, 0.0F, (float) (Math.PI / 4))
      );
      butt.addChild(
         "revive_butt_l2",
         ModelPartBuilder.create().uv(16, 52).cuboid(-1.55F, -1.5F, -1.1F, 3.1F, 3.0F, 2.2F),
         ModelTransform.of(1.9F, 0.7F, 0.5F, (float) (Math.PI / 4), 0.0F, 0.0F)
      );
      ModelPart part = TexturedModelData.of(data, 64, 64).createModel().getChild("revive_butt");
      part.visible = false;
      ((ModelPartAccessor)(Object)body).revive$getChildren().put("revive_butt", part);
      return part;
   }
}
