package revivedevelopment.reviveclient.mixin;

import com.mojang.authlib.GameProfile;
import java.time.Instant;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.mixininterface.IMessageHandler;

@Mixin({MessageHandler.class})
public abstract class MessageHandlerMixin implements IMessageHandler {
   @Unique
   private GameProfile sender;

   @Inject(
      method = {"method_44943"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_338;method_44811(Lnet/minecraft/class_2561;Lnet/minecraft/class_7469;Lnet/minecraft/class_7591;)V",
         shift = Shift.BEFORE
      )}
   )
   private void onProcessChatMessageInternal_beforeAddMessage(
      Parameters params,
      SignedMessage message,
      Text decorated,
      GameProfile sender,
      boolean onlyShowSecureChat,
      Instant receptionTimestamp,
      CallbackInfoReturnable<Boolean> info
   ) {
      this.sender = sender;
   }

   @Inject(
      method = {"method_44943"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_338;method_44811(Lnet/minecraft/class_2561;Lnet/minecraft/class_7469;Lnet/minecraft/class_7591;)V",
         shift = Shift.AFTER
      )}
   )
   private void onProcessChatMessageInternal_afterAddMessage(
      Parameters params,
      SignedMessage message,
      Text decorated,
      GameProfile sender,
      boolean onlyShowSecureChat,
      Instant receptionTimestamp,
      CallbackInfoReturnable<Boolean> info
   ) {
      this.sender = null;
   }

   @Override
   public GameProfile revive$getSender() {
      return this.sender;
   }
}
