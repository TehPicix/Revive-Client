package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.resource.ResourceReloadLogger;
import net.minecraft.client.resource.ResourceReloadLogger.ReloadState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ResourceReloadLogger.class})
public interface ResourceReloadLoggerAccessor {
   @Accessor("field_33699")
   ReloadState getReloadState();
}
