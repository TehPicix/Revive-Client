package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;
import net.minecraft.client.render.model.json.Transformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LayerRenderState.class})
public interface LayerRenderStateAccessor {
   @Accessor("field_56967")
   Transformation getTransform();
}
