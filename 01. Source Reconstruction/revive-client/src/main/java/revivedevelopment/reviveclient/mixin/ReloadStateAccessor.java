package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.resource.ResourceReloadLogger.ReloadState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ReloadState.class})
public interface ReloadStateAccessor {
   @Accessor("field_33710")
   boolean isFinished();
}
