package revivedevelopment.reviveclient.mixin;

import net.minecraft.util.StringHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.BetterChat;

@Mixin({StringHelper.class})
public abstract class StringHelperMixin {
   @ModifyArg(
      method = {"method_43681"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_3544;method_34963(Ljava/lang/String;IZ)Ljava/lang/String;"
      ),
      index = 1
   )
   private static int revive$infiniteChatBox(int maxLength) {
      Modules modules = Modules.get();
      if (modules == null) {
         return maxLength;
      } else {
         BetterChat betterChat = modules.get(BetterChat.class);
         return betterChat != null && betterChat.isInfiniteChatBox() ? Integer.MAX_VALUE : maxLength;
      }
   }
}
