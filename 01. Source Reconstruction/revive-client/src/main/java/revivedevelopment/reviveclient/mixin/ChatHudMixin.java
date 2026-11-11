package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.ReceiveMessageEvent;
import revivedevelopment.reviveclient.mixininterface.IChatHud;
import revivedevelopment.reviveclient.mixininterface.IChatHudLine;
import revivedevelopment.reviveclient.mixininterface.IChatHudLineVisible;
import revivedevelopment.reviveclient.mixininterface.IMessageHandler;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.BetterChat;

@Mixin({ChatHud.class})
public abstract class ChatHudMixin implements IChatHud {
   @Shadow
   @Final
   private MinecraftClient field_2062;
   @Shadow
   @Final
   private List<Visible> field_2064;
   @Shadow
   @Final
   private List<ChatHudLine> field_2061;
   @Unique
   private int nextId;
   @Unique
   private boolean skipOnAddMessage;
   @Unique
   private BetterChat betterChat;
   @Unique
   private int revive$linesAdded;

   @Shadow
   public abstract void method_44811(Text var1, @Nullable MessageSignatureData var2, @Nullable MessageIndicator var3);

   @Shadow
   public abstract void method_1812(Text var1);

   @Override
   public void revive$add(Text message, int id) {
      this.nextId = id;
      this.method_1812(message);
      this.nextId = 0;
   }

   @Inject(
      method = {"method_1815"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V",
         shift = Shift.AFTER
      )}
   )
   private void onAddMessageAfterNewChatHudLineVisible(ChatHudLine message, CallbackInfo ci) {
      ((IChatHudLine)(Object)this.field_2064.getFirst()).revive$setId(this.nextId);
      this.revive$linesAdded++;
   }

   @Inject(
      method = {"method_58744"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V",
         shift = Shift.AFTER
      )}
   )
   private void onAddMessageAfterNewChatHudLine(ChatHudLine message, CallbackInfo ci) {
      ((IChatHudLine)(Object)this.field_2061.getFirst()).revive$setId(this.nextId);
   }

   @ModifyExpressionValue(
      method = {"method_1815"},
      at = {@At(
         value = "NEW",
         target = "(ILnet/minecraft/class_5481;Lnet/minecraft/class_7591;Z)Lnet/minecraft/class_303$class_7590;"
      )}
   )
   private Visible onAddMessage_modifyChatHudLineVisible(Visible line, @Local(ordinal = 1) int j) {
      IMessageHandler handler = (IMessageHandler)this.field_2062.getMessageHandler();
      if (handler == null) {
         return line;
      } else {
         IChatHudLineVisible reviveLine = (IChatHudLineVisible)(Object)line;
         reviveLine.revive$setSender(handler.revive$getSender());
         reviveLine.revive$setStartOfEntry(j == 0);
         return line;
      }
   }

   @ModifyExpressionValue(
      method = {"method_44811"},
      at = {@At(
         value = "NEW",
         target = "(ILnet/minecraft/class_2561;Lnet/minecraft/class_7469;Lnet/minecraft/class_7591;)Lnet/minecraft/class_303;"
      )}
   )
   private ChatHudLine onAddMessage_modifyChatHudLine(ChatHudLine line) {
      IMessageHandler handler = (IMessageHandler)this.field_2062.getMessageHandler();
      if (handler == null) {
         return line;
      } else {
         ((IChatHudLine)(Object)line).revive$setSender(handler.revive$getSender());
         return line;
      }
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"method_44811"},
      cancellable = true
   )
   private void onAddMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
      if (!this.skipOnAddMessage) {
         ReceiveMessageEvent event = (ReceiveMessageEvent)ReviveClient.EVENT_BUS.post(ReceiveMessageEvent.get(message, indicator, this.nextId));
         if (event.isCancelled()) {
            ci.cancel();
         } else {
            this.field_2064.removeIf(msg -> ((IChatHudLine)(Object)msg).revive$getId() == this.nextId && this.nextId != 0);

            for (int i = this.field_2061.size() - 1; i > -1; i--) {
               if (((IChatHudLine)(Object)this.field_2061.get(i)).revive$getId() == this.nextId && this.nextId != 0) {
                  this.field_2061.remove(i);
                  BetterChat betterChat = this.revive$betterChat();
                  if (betterChat != null) {
                     betterChat.removeLine(i);
                  }
               }
            }

            if (event.isModified()) {
               ci.cancel();
               this.skipOnAddMessage = true;
               this.method_44811(event.getMessage(), signatureData, event.getIndicator());
               this.skipOnAddMessage = false;
            }
         }
      }
   }

   @ModifyExpressionValue(
      method = {"method_58744"},
      at = {@At(
         value = "CONSTANT",
         args = {"intValue=100"}
      )}
   )
   private int revive$maxMessages(int size) {
      BetterChat betterChat = this.revive$betterChat();
      return betterChat != null && betterChat.isLongerChat() ? size + betterChat.getExtraChatLines() : size;
   }

   @ModifyExpressionValue(
      method = {"method_1815"},
      at = {@At(
         value = "CONSTANT",
         args = {"intValue=100"}
      )}
   )
   private int revive$maxVisibleLines(int size) {
      BetterChat betterChat = this.revive$betterChat();
      return betterChat != null && betterChat.isLongerChat() ? size + betterChat.getExtraChatLines() : size;
   }

   @ModifyExpressionValue(
      method = {"method_1805"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_3532;method_15386(F)I"
      )},
      require = 0
   )
   private int revive$chatWidth(int width) {
      BetterChat betterChat = this.revive$betterChat();
      return betterChat != null ? betterChat.modifyChatWidth(width) : width;
   }

   @Inject(
      method = {"method_1815"},
      at = {@At("HEAD")}
   )
   private void revive$onAddVisibleMessageHead(ChatHudLine message, CallbackInfo ci) {
      this.revive$linesAdded = 0;
   }

   @Inject(
      method = {"method_1815"},
      at = {@At("TAIL")}
   )
   private void revive$onAddVisibleMessageTail(ChatHudLine message, CallbackInfo ci) {
      BetterChat betterChat = this.revive$betterChat();
      if (betterChat != null) {
         betterChat.lines.add(0, this.revive$linesAdded);

         while (betterChat.lines.size() > this.field_2061.size()) {
            betterChat.lines.removeInt(betterChat.lines.size() - 1);
         }
      }
   }

   @Inject(
      method = {"method_1808"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$onClear(boolean clearHistory, CallbackInfo ci) {
      BetterChat betterChat = this.revive$betterChat();
      if (betterChat != null) {
         if (!clearHistory && betterChat.keepHistory()) {
            ci.cancel();
         } else {
            betterChat.lines.clear();
         }
      }
   }

   @Inject(
      method = {"method_44813"},
      at = {@At("HEAD")}
   )
   private void revive$onRefresh(CallbackInfo ci) {
      BetterChat betterChat = this.revive$betterChat();
      if (betterChat != null) {
         betterChat.lines.clear();
      }
   }

   @Unique
   private BetterChat revive$betterChat() {
      if (this.betterChat == null) {
         Modules modules = Modules.get();
         if (modules == null) {
            return null;
         }

         this.betterChat = modules.get(BetterChat.class);
      }

      return this.betterChat;
   }
}
