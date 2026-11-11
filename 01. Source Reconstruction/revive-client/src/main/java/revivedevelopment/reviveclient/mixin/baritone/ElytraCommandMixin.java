package revivedevelopment.reviveclient.mixin.baritone;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import revivedevelopment.reviveclient.systems.modules.movement.BaritoneElytraOverworld;

@Mixin(
   targets = {"baritone/command/defaults/ElytraCommand"},
   remap = false
)
public abstract class ElytraCommandMixin {
   @Redirect(
      method = {"execute(Ljava/lang/String;Lbaritone/api/command/argument/IArgConsumer;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1937;method_27983()Lnet/minecraft/class_5321;"
      )
   )
   @Dynamic("Baritone implementation class; obfuscated member verified at runtime by BaritoneMixinGuard")
   private RegistryKey<World> revive$unlockDimension(World world) {
      RegistryKey<World> key = world.getRegistryKey();
      return BaritoneElytraOverworld.isUnlocked() ? World.NETHER : key;
   }
}
