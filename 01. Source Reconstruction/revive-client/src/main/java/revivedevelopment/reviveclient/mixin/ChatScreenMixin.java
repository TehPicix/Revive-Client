package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.BetterChat;

@Mixin(
   value = {ChatScreen.class},
   priority = 1001
)
public abstract class ChatScreenMixin {
   @Shadow
   protected TextFieldWidget field_2382;

   @Inject(
      method = {"method_25426"},
      at = {@At("RETURN")}
   )
   private void revive$onInit(CallbackInfo ci) {
      Modules modules = Modules.get();
      if (modules != null) {
         BetterChat betterChat = modules.get(BetterChat.class);
         if (betterChat != null && betterChat.isInfiniteChatBox()) {
            this.field_2382.setMaxLength(Integer.MAX_VALUE);
         }
      }
   }
}
