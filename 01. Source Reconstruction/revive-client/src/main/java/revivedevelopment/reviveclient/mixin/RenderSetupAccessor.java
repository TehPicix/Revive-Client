package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({RenderSetup.class})
public interface RenderSetupAccessor {
   @Accessor("field_63989")
   OutputTarget getOutputTarget();
}
