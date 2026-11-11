package revivedevelopment.reviveclient.utils.player;

import com.mojang.brigadier.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IChatHud;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ChatUtils {
   private static final List<Pair<String, Supplier<Text>>> customPrefixes = new ArrayList<>();
   private static String forcedPrefixClassName;

   private ChatUtils() {
   }

   private static Text buildPrefix() {
      int brackets = 16738750;
      int letter = 11579568;

      try {
         Gui gui = Modules.get().get(Gui.class);
         if (gui != null) {
            SettingColor p = gui.primary.get();
            SettingColor s = gui.hudTextSecondary.get();
            brackets = (p.r & 0xFF) << 16 | (p.g & 0xFF) << 8 | p.b & 0xFF;
            letter = (s.r & 0xFF) << 16 | (s.g & 0xFF) << 8 | s.b & 0xFF;
         }
      } catch (Exception var5) {
      }

      return Text.empty()
         .append(Text.literal("[").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(brackets))))
         .append(Text.literal("R").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(letter))))
         .append(Text.literal("] ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(brackets))));
   }

   public static Text getRevivePrefix() {
      return buildPrefix();
   }

   public static void registerCustomPrefix(String packageName, Supplier<Text> supplier) {
      for (Pair<String, Supplier<Text>> pair : customPrefixes) {
         if (((String)pair.getLeft()).equals(packageName)) {
            pair.setRight(supplier);
            return;
         }
      }

      customPrefixes.add(new Pair(packageName, supplier));
   }

   public static void unregisterCustomPrefix(String packageName) {
      customPrefixes.removeIf(pair -> ((String)pair.getLeft()).equals(packageName));
   }

   public static void forceNextPrefixClass(Class<?> klass) {
      forcedPrefixClassName = klass.getName();
   }

   public static void sendPlayerMsg(String message) {
      ReviveClient.mc.inGameHud.getChatHud().addToMessageHistory(message);
      if (message.startsWith("/")) {
         ReviveClient.mc.player.networkHandler.sendChatCommand(message.substring(1));
      } else {
         ReviveClient.mc.player.networkHandler.sendChatMessage(message);
      }
   }

   public static void info(String message, Object... args) {
      sendMsg(Formatting.GRAY, message, args);
   }

   public static void infoPrefix(String prefix, String message, Object... args) {
      sendMsg(0, prefix, Formatting.LIGHT_PURPLE, Formatting.GRAY, message, args);
   }

   public static void warning(String message, Object... args) {
      sendMsg(Formatting.YELLOW, message, args);
   }

   public static void warningPrefix(String prefix, String message, Object... args) {
      sendMsg(0, prefix, Formatting.LIGHT_PURPLE, Formatting.YELLOW, message, args);
   }

   public static void error(String message, Object... args) {
      sendMsg(Formatting.RED, message, args);
   }

   public static void errorPrefix(String prefix, String message, Object... args) {
      sendMsg(0, prefix, Formatting.LIGHT_PURPLE, Formatting.RED, message, args);
   }

   public static void sendMsg(Text message) {
      sendMsg(null, message);
   }

   public static void sendMsg(String prefix, Text message) {
      sendMsg(0, prefix, Formatting.LIGHT_PURPLE, message);
   }

   public static void sendMsg(Formatting color, String message, Object... args) {
      sendMsg(0, null, null, color, message, args);
   }

   public static void sendMsg(int id, Formatting color, String message, Object... args) {
      sendMsg(id, null, null, color, message, args);
   }

   public static void sendMsg(
      int id, @Nullable String prefixTitle, @Nullable Formatting prefixColor, Formatting messageColor, String messageContent, Object... args
   ) {
      MutableText message = formatMsg(String.format(messageContent, args), messageColor);
      sendMsg(id, prefixTitle, prefixColor, message);
   }

   public static void sendMsg(int id, @Nullable String prefixTitle, @Nullable Formatting prefixColor, String messageContent, Formatting messageColor) {
      MutableText message = formatMsg(messageContent, messageColor);
      sendMsg(id, prefixTitle, prefixColor, message);
   }

   public static void sendMsg(int id, @Nullable String prefixTitle, @Nullable Formatting prefixColor, Text msg) {
      if (ReviveClient.mc.world != null) {
         MutableText message = Text.empty();
         message.append(getPrefix());
         if (prefixTitle != null) {
            message.append(getCustomPrefix(prefixTitle, prefixColor));
         }

         message.append(msg);
         if (!Config.get().deleteChatFeedback.get()) {
            id = 0;
         }

         ((IChatHud)ReviveClient.mc.inGameHud.getChatHud()).revive$add(message, id);
      }
   }

   private static MutableText getCustomPrefix(String prefixTitle, Formatting prefixColor) {
      MutableText prefix = Text.empty();
      prefix.setStyle(prefix.getStyle().withFormatting(Formatting.GRAY));
      MutableText moduleTitle = Text.literal(prefixTitle);
      moduleTitle.setStyle(moduleTitle.getStyle().withFormatting(prefixColor));
      prefix.append(moduleTitle);
      prefix.append(": ");
      return prefix;
   }

   private static Text getPrefix() {
      if (customPrefixes.isEmpty()) {
         forcedPrefixClassName = null;
         return buildPrefix();
      } else {
         boolean foundChatUtils = false;
         String className = null;
         if (forcedPrefixClassName != null) {
            className = forcedPrefixClassName;
            forcedPrefixClassName = null;
         } else {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
               if (foundChatUtils) {
                  if (!element.getClassName().equals(ChatUtils.class.getName())) {
                     className = element.getClassName();
                     break;
                  }
               } else if (element.getClassName().equals(ChatUtils.class.getName())) {
                  foundChatUtils = true;
               }
            }
         }

         if (className == null) {
            return buildPrefix();
         } else {
            for (Pair<String, Supplier<Text>> pair : customPrefixes) {
               if (className.startsWith((String)pair.getLeft())) {
                  Text prefix = (Text)((Supplier)pair.getRight()).get();
                  return prefix != null ? prefix : buildPrefix();
               }
            }

            return buildPrefix();
         }
      }
   }

   private static MutableText formatMsg(String message, Formatting defaultColor) {
      StringReader reader = new StringReader(message);
      MutableText text = Text.empty();
      Style style = Style.EMPTY.withFormatting(defaultColor);
      StringBuilder result = new StringBuilder();
      boolean formatting = false;

      while (reader.canRead()) {
         char c = reader.read();
         if (c == '(') {
            text.append(Text.literal(result.toString()).setStyle(style));
            result.setLength(0);
            result.append(c);
            formatting = true;
         } else {
            result.append(c);
            if (formatting && c == ')') {
               String var8 = result.toString();
               switch (var8) {
                  case "(default)":
                     style = style.withFormatting(defaultColor);
                     result.setLength(0);
                     break;
                  case "(highlight)":
                     style = style.withFormatting(Formatting.WHITE);
                     result.setLength(0);
                     break;
                  case "(underline)":
                     style = style.withFormatting(Formatting.UNDERLINE);
                     result.setLength(0);
                     break;
                  case "(bold)":
                     style = style.withFormatting(Formatting.BOLD);
                     result.setLength(0);
               }

               formatting = false;
            }
         }
      }

      if (!result.isEmpty()) {
         text.append(Text.literal(result.toString()).setStyle(style));
      }

      return text;
   }

   public static MutableText formatCoords(Vec3d pos) {
      String coordsString = String.format("(highlight)(underline)%.0f, %.0f, %.0f(default)", pos.x, pos.y, pos.z);
      return formatMsg(coordsString, Formatting.GRAY);
   }
}
