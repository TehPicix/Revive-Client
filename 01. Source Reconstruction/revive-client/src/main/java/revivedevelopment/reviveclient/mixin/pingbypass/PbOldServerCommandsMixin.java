package revivedevelopment.reviveclient.mixin.pingbypass;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerSessionC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import revivedevelopment.reviveclient.pingbypass.PbVia;
import revivedevelopment.reviveclient.pingbypass.client.PbOldServerChat;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

@Mixin({ClientPlayNetworkHandler.class})
public abstract class PbOldServerCommandsMixin implements PbOldServerChat {
   @Unique
   private boolean revive$sessionSent;

   @Shadow
   public abstract void method_45730(String var1);

   @Shadow
   public abstract ClientConnection method_48296();

   @Unique
   private boolean revive$signsEveryCommand() {
      return PbRouting.routedAtMost(this.method_48296(), 765);
   }

   @Unique
   private boolean revive$serverKicksUnsignedChat() {
      if (this.revive$signsEveryCommand()) {
         return true;
      } else {
         ClientConnection connection = this.method_48296();
         if (PbRouting.isRouted(connection)) {
            return false;
         } else {
            PbVia.Version target = PbVia.describe(PbVia.connectionTarget(connection));
            return target != null && target.atMost(765);
         }
      }
   }

   @Override
   public boolean revive$keepsChatSigned() {
      return this.revive$sessionSent;
   }

   @WrapOperation(
      method = {"method_45730"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/List;isEmpty()Z"
      )},
      require = 0
   )
   private boolean revive$signEveryCommand(List<?> arguments, Operation<Boolean> original) {
      return !this.revive$signsEveryCommand() && (Boolean)original.call(new Object[]{arguments});
   }

   @WrapOperation(
      method = {"method_71927", "method_71928"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V"
      )},
      require = 0
   )
   private void revive$signClickCommand(ClientPlayNetworkHandler handler, Packet<?> packet, Operation<Void> original, @Local(argsOnly = true) String command) {
      if (packet instanceof CommandExecutionC2SPacket && this.revive$signsEveryCommand()) {
         this.method_45730(command);
      } else {
         original.call(new Object[]{handler, packet});
      }
   }

   @WrapOperation(
      method = {"method_47657"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_634;method_52787(Lnet/minecraft/class_2596;)V"
      )},
      require = 0
   )
   private void revive$noSessionForUnsignedChat(ClientPlayNetworkHandler handler, Packet<?> packet, Operation<Void> original) {
      if (packet instanceof PlayerSessionC2SPacket && this.revive$serverKicksUnsignedChat()) {
         Config config = Config.get();
         if (config != null && config.unsignedChat.get()) {
            ChatUtils.info(
               "This server runs 1.20.4 or older. With unsigned-chat on it gets no chat session from you (it would kick you for an unsigned message), so if it says chat is disabled, run .config unsigned-chat off and rejoin."
            );
            return;
         }

         this.revive$sessionSent = true;
      }

      original.call(new Object[]{handler, packet});
   }
}
