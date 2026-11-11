package revivedevelopment.reviveclient.mixin.baritone;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(
   targets = {"baritone/process/elytra/ElytraBehavior"},
   remap = false
)
public abstract class ElytraBehaviorMixin {
}
