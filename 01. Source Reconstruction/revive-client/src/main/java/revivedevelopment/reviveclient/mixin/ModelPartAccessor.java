package revivedevelopment.reviveclient.mixin;

import java.util.Map;
import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ModelPart.class})
public interface ModelPartAccessor {
   @Accessor("field_3661")
   Map<String, ModelPart> revive$getChildren();
}
