package revivedevelopment.reviveclient.mixin;

import java.util.Map;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Key;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({KeyBinding.class})
public interface KeyBindingAccessor {
   @Accessor("field_1657")
   static Map<String, KeyBinding> getKeysById() {
      return null;
   }

   @Accessor("field_1655")
   Key getKey();

   @Accessor("field_1661")
   int revive$getTimesPressed();

   @Accessor("field_1661")
   void revive$setTimesPressed(int var1);

   @Invoker("method_1425")
   void invokeReset();
}
