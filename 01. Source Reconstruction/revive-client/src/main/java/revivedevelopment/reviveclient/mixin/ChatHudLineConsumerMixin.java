package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.BetterChat;

@Mixin(
   targets = {"net/minecraft/class_338$1"}
)
public abstract class ChatHudLineConsumerMixin {
   @Inject(
      method = {"accept"},
      at = {@At("HEAD")},
      require = 0
   )
   private void revive$rememberLine(Visible line, int index, float opacity, CallbackInfo ci) {
      Modules modules = Modules.get();
      if (modules != null) {
         BetterChat betterChat = modules.get(BetterChat.class);
         if (betterChat != null) {
            betterChat.line = line;
         }
      }
   }
}
