package revivedevelopment.reviveclient.mixin;

import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.utils.misc.input.KeyBinds;

@Mixin({GameOptions.class})
public abstract class GameOptionsMixin {
   @Shadow
   @Final
   @Mutable
   public KeyBinding[] field_1839;

   @Inject(
      method = {"<init>"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_315;field_1839:[Lnet/minecraft/class_304;",
         opcode = 181,
         shift = Shift.AFTER
      )}
   )
   private void onInitAfterKeysAll(MinecraftClient client, File optionsFile, CallbackInfo info) {
      this.field_1839 = KeyBinds.apply(this.field_1839);
   }
}
