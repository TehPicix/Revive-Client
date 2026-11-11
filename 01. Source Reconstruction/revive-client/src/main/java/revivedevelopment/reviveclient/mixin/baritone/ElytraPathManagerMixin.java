package revivedevelopment.reviveclient.mixin.baritone;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
   targets = {"baritone/process/elytra/ElytraBehavior$PathManager"},
   remap = false
)
public abstract class ElytraPathManagerMixin {
   @ModifyArg(
      method = {"a(Ljava/util/List;J)V"},
      at = @At(
         value = "INVOKE",
         target = "Lbaritone/process/elytra/NetherPath;a(I)Lbaritone/api/utils/BetterBlockPos;"
      ),
      index = 0
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private int revive$clampSegmentLogIndex(int index) {
      return Math.max(0, index);
   }
}
