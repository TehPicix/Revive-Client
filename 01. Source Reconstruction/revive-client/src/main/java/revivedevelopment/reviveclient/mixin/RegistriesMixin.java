package revivedevelopment.reviveclient.mixin;

import java.util.function.Supplier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Registries.class})
public abstract class RegistriesMixin {
   @Redirect(
      method = {"method_47478"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_2966;method_36235(Ljava/util/function/Supplier;)V"
      )
   )
   private static void ignoreBootstrap(Supplier<String> callerGetter) {
   }
}
