package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.font.DrawnTextConsumer.Transformation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.BetterChat;

@Mixin(
   targets = {"net/minecraft/class_338$class_12235"}
)
public abstract class ChatHudInteractableMixin {
   @Shadow
   @Final
   private DrawContext field_63876;

   @ModifyReceiver(
      method = {"method_75807"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_12225;method_75766(Lnet/minecraft/class_11735;IILnet/minecraft/class_12225$class_12227;Lnet/minecraft/class_5481;)V"
      )},
      require = 0
   )
   private DrawnTextConsumer revive$beforeDrawText(
      DrawnTextConsumer drawer, Alignment alignment, int x, int y, Transformation transformation, OrderedText text, @Local(argsOnly = true) float opacity
   ) {
      BetterChat betterChat = revive$betterChat();
      if (betterChat != null) {
         betterChat.beforeDrawMessage(this.field_63876, y, BetterChat.whiteWithOpacity(opacity));
      }

      return drawer;
   }

   @ModifyArg(
      method = {"method_75807"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_12225;method_75766(Lnet/minecraft/class_11735;IILnet/minecraft/class_12225$class_12227;Lnet/minecraft/class_5481;)V"
      ),
      index = 1,
      require = 0
   )
   private int revive$shiftText(int x) {
      BetterChat betterChat = revive$betterChat();
      return betterChat != null ? betterChat.modifyChatWidth(x) : x;
   }

   @Inject(
      method = {"method_75807"},
      at = {@At("TAIL")},
      require = 0
   )
   private void revive$afterDrawText(int textTop, float opacity, OrderedText text, CallbackInfoReturnable<Boolean> cir) {
      BetterChat betterChat = revive$betterChat();
      if (betterChat != null) {
         betterChat.afterDrawMessage();
      }
   }

   @Unique
   private static BetterChat revive$betterChat() {
      Modules modules = Modules.get();
      return modules != null ? modules.get(BetterChat.class) : null;
   }
}
