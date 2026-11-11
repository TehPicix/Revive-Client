package revivedevelopment.reviveclient.systems.modules.player;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.chars.Char2CharMap;
import it.unimi.dsi.fastutil.chars.Char2CharOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.text.StyleSpriteSource.Font;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.ReceiveMessageEvent;
import revivedevelopment.reviveclient.events.game.SendMessageEvent;
import revivedevelopment.reviveclient.mixin.ChatHudAccessor;
import revivedevelopment.reviveclient.mixininterface.IChatHudLine;
import revivedevelopment.reviveclient.mixininterface.IChatHudLineVisible;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringListSetting;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.friends.Friend;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.text.RunnableClickEvent;
import revivedevelopment.reviveclient.utils.misc.text.TextVisitor;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class BetterChat extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgFilter = this.settings.createGroup("Filter");
   private final SettingGroup sgLongerChat = this.settings.createGroup("Longer Chat");
   private final SettingGroup sgPrefix = this.settings.createGroup("Prefix");
   private final SettingGroup sgSuffix = this.settings.createGroup("Suffix");
   private final SettingGroup sgNameHighlight = this.settings.createGroup("Name Highlight");
   private final SettingGroup sgFriendHighlight = this.settings.createGroup("Friend Highlight");
   private final Setting<Boolean> annoy = this.sgGeneral
      .add(new BoolSetting.Builder().name("annoy").description("Makes your messages aNnOyInG.").defaultValue(false).build());
   private final Setting<Boolean> fancy = this.sgGeneral
      .add(new BoolSetting.Builder().name("fancy-chat").description("Makes your messages ꜰᴀɴᴄʏ!").defaultValue(false).build());
   private final Setting<Boolean> fancyReceived = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("fancy-received")
            .description("Shows every incoming chat message in small caps. Only changes what you see.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> fancyUniform = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("fancy-uniform")
            .description("Small-caps capital letters too, so every letter in fancy text is the same height. Digits stay full size.")
            .defaultValue(false)
            .visible(() -> this.fancy.get() || this.fancyReceived.get())
            .build()
      );
   private final Setting<BetterChat.ChatFont> chatFont = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("chat-font"))
                  .description(
                     "Font every incoming chat message is drawn in, including the client's own fonts. Only changes what you see. Fancy-received draws real small caps in these."
                  ))
               .defaultValue(BetterChat.ChatFont.DEFAULT))
            .build()
      );
   private final Setting<Boolean> timestamps = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("timestamps")
            .description("Adds client-side time stamps to the beginning of chat messages.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> playerHeads = this.sgGeneral
      .add(new BoolSetting.Builder().name("player-heads").description("Displays player heads next to their messages.").defaultValue(true).build());
   private final Setting<Boolean> coordsProtection = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("coords-protection")
            .description("Prevents you from sending messages in chat that may contain coordinates.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> keepHistory = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-history")
            .description("Prevents the chat history from being cleared when disconnecting.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> globalRemoveBrackets = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("remove-brackets")
            .description("Removes < > brackets from all player names in chat globally.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> antiSpam = this.sgFilter
      .add(new BoolSetting.Builder().name("anti-spam").description("Blocks duplicate messages from filling your chat.").defaultValue(true).build());
   private final Setting<Integer> antiSpamDepth = this.sgFilter
      .add(
         new IntSetting.Builder()
            .name("depth")
            .description("How many messages to filter.")
            .defaultValue(20)
            .min(1)
            .sliderMin(1)
            .visible(this.antiSpam::get)
            .build()
      );
   private final Setting<Boolean> antiClear = this.sgFilter
      .add(new BoolSetting.Builder().name("anti-clear").description("Prevents servers from clearing chat.").defaultValue(true).build());
   private final Setting<Boolean> filterRegex = this.sgFilter
      .add(new BoolSetting.Builder().name("filter-regex").description("Filter out chat messages that match the regex filter.").defaultValue(false).build());
   private final Setting<List<String>> regexFilters = this.sgFilter
      .add(
         new StringListSetting.Builder()
            .name("regex-filter")
            .description("Regex filter used for filtering chat messages.")
            .visible(this.filterRegex::get)
            .onChanged(strings -> this.compileFilterRegexList())
            .build()
      );
   private final Setting<Boolean> infiniteChatBox = this.sgLongerChat
      .add(new BoolSetting.Builder().name("infinite-chat-box").description("Lets you type infinitely long messages.").defaultValue(true).build());
   private final Setting<Boolean> longerChatHistory = this.sgLongerChat
      .add(new BoolSetting.Builder().name("longer-chat-history").description("Extends chat length.").defaultValue(true).build());
   private final Setting<Integer> longerChatLines = this.sgLongerChat
      .add(
         new IntSetting.Builder()
            .name("extra-lines")
            .description("The amount of extra chat lines.")
            .defaultValue(1000)
            .min(0)
            .sliderRange(0, 1000)
            .visible(this.longerChatHistory::get)
            .build()
      );
   private final Setting<Boolean> prefix = this.sgPrefix
      .add(new BoolSetting.Builder().name("prefix").description("Adds a prefix to your chat messages.").defaultValue(false).build());
   private final Setting<Boolean> prefixRandom = this.sgPrefix
      .add(new BoolSetting.Builder().name("random").description("Uses a random number as your prefix.").defaultValue(false).build());
   private final Setting<String> prefixText = this.sgPrefix
      .add(
         new StringSetting.Builder()
            .name("text")
            .description("The text to add as your prefix.")
            .defaultValue("> ")
            .visible(() -> !this.prefixRandom.get())
            .build()
      );
   private final Setting<Boolean> prefixSmallCaps = this.sgPrefix
      .add(
         new BoolSetting.Builder()
            .name("small-caps")
            .description("Uses small caps in the prefix.")
            .defaultValue(false)
            .visible(() -> !this.prefixRandom.get())
            .build()
      );
   private final Setting<Boolean> suffix = this.sgSuffix
      .add(new BoolSetting.Builder().name("suffix").description("Adds a suffix to your chat messages.").defaultValue(false).build());
   private final Setting<Boolean> suffixRandom = this.sgSuffix
      .add(new BoolSetting.Builder().name("random").description("Uses a random number as your suffix.").defaultValue(false).build());
   private final Setting<String> suffixText = this.sgSuffix
      .add(
         new StringSetting.Builder()
            .name("text")
            .description("The text to add as your suffix.")
            .defaultValue(" | revive on crack!")
            .visible(() -> !this.suffixRandom.get())
            .build()
      );
   private final Setting<Boolean> suffixSmallCaps = this.sgSuffix
      .add(
         new BoolSetting.Builder()
            .name("small-caps")
            .description("Uses small caps in the suffix.")
            .defaultValue(true)
            .visible(() -> !this.suffixRandom.get())
            .build()
      );
   private final Setting<Boolean> nameHighlight = this.sgNameHighlight
      .add(new BoolSetting.Builder().name("name-highlight").description("Highlights your username when it appears in chat.").defaultValue(true).build());
   public final Setting<SettingColor> nameHighlightColor = this.sgNameHighlight
      .add(
         new ColorSetting.Builder()
            .name("highlight-color")
            .description("Color used to highlight your name in chat.")
            .defaultValue(new SettingColor(255, 255, 0, 255))
            .visible(this.nameHighlight::get)
            .build()
      );
   public final Setting<Boolean> nameHighlightShadow = this.sgNameHighlight
      .add(
         new BoolSetting.Builder()
            .name("name-shadow")
            .description("Renders a drop shadow behind your highlighted name.")
            .defaultValue(true)
            .visible(this.nameHighlight::get)
            .build()
      );
   public final Setting<Boolean> nameHighlightRemoveBrackets = this.sgNameHighlight
      .add(
         new BoolSetting.Builder()
            .name("remove-brackets")
            .description("Removes the < > brackets surrounding your name specifically. Overrides the global setting.")
            .defaultValue(false)
            .visible(this.nameHighlight::get)
            .build()
      );
   public final Setting<BetterChat.ChatFont> nameHighlightFont = this.sgNameHighlight
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("name-font"))
                     .description("Font used to render your highlighted name. Alt = enchanting table font."))
                  .defaultValue(BetterChat.ChatFont.DEFAULT))
               .visible(this.nameHighlight::get))
            .build()
      );
   private final Setting<Boolean> friendHighlight = this.sgFriendHighlight
      .add(new BoolSetting.Builder().name("friend-highlight").description("Highlights friends names when they appear in chat.").defaultValue(true).build());
   public final Setting<SettingColor> friendHighlightColor = this.sgFriendHighlight
      .add(
         new ColorSetting.Builder()
            .name("highlight-color")
            .description("Color used to highlight friend names in chat.")
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .visible(this.friendHighlight::get)
            .build()
      );
   public final Setting<Boolean> friendHighlightShadow = this.sgFriendHighlight
      .add(
         new BoolSetting.Builder()
            .name("friend-shadow")
            .description("Renders a drop shadow behind highlighted friend names.")
            .defaultValue(true)
            .visible(this.friendHighlight::get)
            .build()
      );
   public final Setting<Boolean> friendHighlightRemoveBrackets = this.sgFriendHighlight
      .add(
         new BoolSetting.Builder()
            .name("remove-brackets")
            .description("Removes the < > brackets surrounding friend names specifically. Overrides the global setting.")
            .defaultValue(false)
            .visible(this.friendHighlight::get)
            .build()
      );
   public final Setting<BetterChat.ChatFont> friendHighlightFont = this.sgFriendHighlight
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("friend-font"))
                     .description("Font used to render highlighted friend names. Alt = enchanting table font."))
                  .defaultValue(BetterChat.ChatFont.DEFAULT))
               .visible(this.friendHighlight::get))
            .build()
      );
   private static final Pattern globalBracketRegex = Pattern.compile("<(?!\\d{1,2}:\\d{2}>)([^>]+)>");
   private static final Pattern antiSpamRegex = Pattern.compile(" \\(([0-9]{1,9})\\)$");
   private static final Pattern antiClearRegex = Pattern.compile("\\n(\\n|\\s)+\\n");
   private static final Pattern timestampRegex = Pattern.compile("^(<[0-9]{2}:[0-9]{2}>\\s)");
   private static final Pattern usernameRegex = Pattern.compile("^(?:<[0-9]{2}:[0-9]{2}>\\s)?<(.*?)>.*");
   private final Char2CharMap SMALL_CAPS = new Char2CharOpenHashMap();
   private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
   private final List<Pattern> filterRegexList = new ArrayList<>();
   public final IntList lines = new IntArrayList();
   private boolean bypassNextSend;
   private static final List<BetterChat.CustomHeadEntry> CUSTOM_HEAD_ENTRIES = new ArrayList<>();
   private static final Pattern TIMESTAMP_REGEX = Pattern.compile("^<\\d{1,2}:\\d{1,2}>");
   public Visible line;
   private static final Pattern coordRegex = Pattern.compile("(?<x>-?\\d{3,}(?:\\.\\d*)?)(?:\\s+(?<y>-?\\d{1,3}(?:\\.\\d*)?))?\\s+(?<z>-?\\d{3,}(?:\\.\\d*)?)");

   public BetterChat() {
      super(Categories.Player, "better-chat", "Improves your chat experience in various ways.");
      String[] a = "abcdefghijklmnopqrstuvwxyz".split("");
      String[] b = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢ".split("");

      for (int i = 0; i < a.length; i++) {
         this.SMALL_CAPS.put(a[i].charAt(0), b[i].charAt(0));
      }

      this.compileFilterRegexList();
   }

   @EventHandler
   private void onMessageReceive(ReceiveMessageEvent event) {
      Text message = event.getMessage();
      if (this.filterRegex.get()) {
         String messageString = message.getString();

         for (Pattern pattern : this.filterRegexList) {
            if (pattern.matcher(messageString).find()) {
               event.setCancelled(true);
               return;
            }
         }
      }

      if (this.antiClear.get()) {
         String messageString = message.getString();
         if (antiClearRegex.matcher(messageString).find()) {
            MutableText newMessage = Text.empty();
            TextVisitor.visit(message, (text, style, string) -> {
               Matcher antiClearMatcher = antiClearRegex.matcher(string);
               if (antiClearMatcher.find()) {
                  newMessage.append(Text.literal(antiClearMatcher.replaceAll("\n\n")).setStyle(style));
               } else {
                  newMessage.append(Text.literal(string).setStyle(style));
               }

               return Optional.empty();
            }, Style.EMPTY);
            message = newMessage;
         }
      }

      if (this.antiSpam.get()) {
         Text antiSpammed = this.appendAntiSpam(message);
         if (antiSpammed != null) {
            message = antiSpammed;
         }
      }

      if (this.timestamps.get()) {
         Text timestamp = Text.literal("<" + this.dateFormat.format(new Date()) + "> ").formatted(Formatting.GRAY);
         message = Text.empty().append(timestamp).append(message);
      }

      if (this.globalRemoveBrackets.get()) {
         message = this.applyGlobalBracketRemoval(message);
      }

      if (this.nameHighlight.get() && this.mc.player != null) {
         String playerName = this.mc.player.getName().getString();
         String msgStr = message.getString();
         if (msgStr.contains("<" + playerName + ">") || msgStr.contains(playerName)) {
            message = this.highlightName(message, playerName);
         }
      }

      if (this.friendHighlight.get()) {
         message = this.highlightListNames(message);
      }

      if (this.fancyReceived.get()) {
         message = this.applyFancyReceived(message);
      }

      Font font = this.chatFont.get().source();
      if (font != null) {
         message = this.applyFont(message, font);
      }

      event.setMessage(message);
   }

   @EventHandler
   private void onMessageSend(SendMessageEvent event) {
      if (!this.bypassNextSend) {
         String message = event.message;
         if (this.annoy.get()) {
            message = this.applyAnnoy(message);
         }

         if (this.fancy.get()) {
            message = this.applyFancy(message);
         }

         message = this.getPrefix() + message + this.getSuffix();
         if (this.coordsProtection.get() && this.containsCoordinates(message)) {
            MutableText warningMessage = Text.literal("It looks like there are coordinates in your message! ");
            warningMessage.append(this.getSendButton(message));
            ChatUtils.sendMsg(warningMessage);
            event.setCancelled(true);
         } else {
            event.message = message;
         }
      }
   }

   private MutableText applyGlobalBracketRemoval(Text message) {
      List<BetterChat.StyledSegment> segments = new ArrayList<>();

      record Replacement(int start, int end, String text) {
      }

      TextVisitor.visit(message, (text, stylex, string) -> {
         segments.add(new BetterChat.StyledSegment(stylex, string));
         return Optional.empty();
      }, Style.EMPTY);
      StringBuilder full = new StringBuilder();
      int[] segmentIndex = new int[segments.stream().mapToInt(s -> s.text.length()).sum()];
      int charPos = 0;

      for (int si = 0; si < segments.size(); si++) {
         String seg = segments.get(si).text;

         for (int ci = 0; ci < seg.length(); ci++) {
            segmentIndex[charPos++] = si;
         }

         full.append(seg);
      }

      String fullStr = full.toString();
      Matcher m = globalBracketRegex.matcher(fullStr);
      List<Replacement> replacements = new ArrayList<>();

      while (m.find()) {
         replacements.add(new Replacement(m.start(), m.end(), m.group(1)));
      }

      if (replacements.isEmpty()) {
         return Text.literal("").append(message);
      } else {
         List<BetterChat.StyledSegment> outputSpans = new ArrayList<>();
         int pos = 0;
         int repIdx = 0;

         while (pos < fullStr.length()) {
            if (repIdx < replacements.size() && pos == replacements.get(repIdx).start()) {
               Replacement rep = replacements.get(repIdx);
               Style repStyle = segments.get(segmentIndex[Math.min(pos, segmentIndex.length - 1)]).style;
               outputSpans.add(new BetterChat.StyledSegment(repStyle, rep.text));
               pos = rep.end();
               repIdx++;
            } else {
               int segIdx = segmentIndex[pos];
               Style style = segments.get(segIdx).style;
               int endPos = pos;
               int nextRepStart = repIdx < replacements.size() ? replacements.get(repIdx).start() : fullStr.length();

               while (endPos < fullStr.length() && segmentIndex[endPos] == segIdx && endPos < nextRepStart) {
                  endPos++;
               }

               outputSpans.add(new BetterChat.StyledSegment(style, fullStr.substring(pos, endPos)));
               pos = endPos;
            }
         }

         MutableText result = Text.literal("");

         for (BetterChat.StyledSegment span : outputSpans) {
            if (!span.text.isEmpty()) {
               result.append(Text.literal(span.text).setStyle(span.style));
            }
         }

         return result;
      }
   }

   private MutableText highlightName(Text message, String name) {
      Style style = highlightStyle(this.nameHighlightColor.get(), this.nameHighlightFont.get(), this.nameHighlightShadow.get());
      boolean strippedAlready = this.globalRemoveBrackets.get();
      boolean stripHere = !strippedAlready && this.nameHighlightRemoveBrackets.get();
      return stripHere ? this.applyHighlightStripBrackets(message, name, style) : this.applyHighlight(message, name, style);
   }

   public MutableText highlightListNames(Text message) {
      String msgStr = message.getString();
      MutableText result = Text.literal("").append(message);

      for (Friend friend : Friends.get()) {
         String name = friend.name;
         if (name != null && !name.isBlank() && (msgStr.contains("<" + name + ">") || msgStr.contains(name))) {
            Style style = highlightStyle(this.friendHighlightColor.get(), this.friendHighlightFont.get(), this.friendHighlightShadow.get());
            boolean strippedAlready = this.globalRemoveBrackets.get();
            boolean stripHere = !strippedAlready && this.friendHighlightRemoveBrackets.get();
            result = stripHere ? this.applyHighlightStripBrackets(result, name, style) : this.applyHighlight(result, name, style);
         }
      }

      return result;
   }

   private static Style highlightStyle(SettingColor color, BetterChat.ChatFont font, boolean shadow) {
      int rgb = color.r << 16 | color.g << 8 | color.b;
      Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true);
      Font source = font.source();
      if (source != null) {
         style = style.withFont(source);
      }

      if (!shadow) {
         style = style.withShadowColor(0);
      }

      return style;
   }

   public MutableText applyHighlight(Text message, String name, Style nameStyle) {
      MutableText result = Text.literal("");
      TextVisitor.visit(message, (text, style, string) -> {
         if (string.contains(name)) {
            appendHighlighted(result, string, name, style, nameStyle);
         } else {
            result.append(Text.literal(string).setStyle(style));
         }

         return Optional.empty();
      }, Style.EMPTY);
      return result;
   }

   private MutableText applyHighlightStripBrackets(Text message, String name, Style nameStyle) {
      String bracketedName = "<" + name + ">";
      MutableText result = Text.literal("");
      TextVisitor.visit(message, (text, style, string) -> {
         if (string.contains(bracketedName)) {
            int start = 0;

            int idx;
            while ((idx = string.indexOf(bracketedName, start)) != -1) {
               if (idx > start) {
                  result.append(Text.literal(string.substring(start, idx)).setStyle(style));
               }

               result.append(Text.literal(" ").setStyle(style));
               result.append(Text.literal(name).setStyle(nameStyle));
               result.append(Text.literal(" ").setStyle(style));
               start = idx + bracketedName.length();
            }

            if (start < string.length()) {
               result.append(Text.literal(string.substring(start)).setStyle(style));
            }
         } else if (string.contains(name)) {
            appendHighlighted(result, string, name, style, nameStyle);
         } else {
            result.append(Text.literal(string).setStyle(style));
         }

         return Optional.empty();
      }, Style.EMPTY);
      return result;
   }

   private static void appendHighlighted(MutableText result, String string, String name, Style style, Style nameStyle) {
      int start = 0;

      int idx;
      while ((idx = string.indexOf(name, start)) != -1) {
         if (idx > start) {
            result.append(Text.literal(string.substring(start, idx)).setStyle(style));
         }

         result.append(Text.literal(name).setStyle(nameStyle));
         start = idx + name.length();
      }

      if (start < string.length()) {
         result.append(Text.literal(string.substring(start)).setStyle(style));
      }
   }

   private Text appendAntiSpam(Text text) {
      String textString = this.fancyReceived.get() ? this.fancyReceivedString(text.getString()) : text.getString();
      Text returnText = null;
      int messageIndex = -1;
      List<ChatHudLine> messages = ((ChatHudAccessor)this.mc.inGameHud.getChatHud()).getMessages();
      if (messages.isEmpty()) {
         return null;
      } else {
         for (int i = 0; i < Math.min(this.antiSpamDepth.get(), messages.size()); i++) {
            String stringToCheck = ((IChatHudLine)(Object)messages.get(i)).revive$getText();
            Matcher timestampMatcher = timestampRegex.matcher(stringToCheck);
            if (timestampMatcher.find()) {
               stringToCheck = stringToCheck.substring(8);
            }

            if (textString.equals(stringToCheck)) {
               messageIndex = i;
               returnText = text.copy().append(Text.literal(" (2)").formatted(Formatting.GRAY));
               break;
            }

            Matcher matcher = antiSpamRegex.matcher(stringToCheck);
            if (matcher.find()) {
               String group = matcher.group(matcher.groupCount());
               int number = Integer.parseInt(group);
               if (stringToCheck.substring(0, matcher.start()).equals(textString)) {
                  messageIndex = i;
                  returnText = text.copy().append(Text.literal(" (" + (number + 1) + ")").formatted(Formatting.GRAY));
                  break;
               }
            }
         }

         if (returnText != null) {
            if (messageIndex >= this.lines.size()) {
               this.removeLine(messageIndex);
               return returnText;
            }

            List<Visible> visible = ((ChatHudAccessor)this.mc.inGameHud.getChatHud()).getVisibleMessages();
            int start = -1;

            for (int i = 0; i < messageIndex; i++) {
               start += this.lines.getInt(i);
            }

            for (int i = this.lines.getInt(messageIndex); i > 0 && start + 1 < visible.size(); i--) {
               visible.remove(start + 1);
            }

            messages.remove(messageIndex);
            this.lines.removeInt(messageIndex);
         }

         return returnText;
      }
   }

   public void removeLine(int index) {
      if (index >= this.lines.size()) {
         if (this.antiSpam.get()) {
            this.error(
               "Issue detected with the anti-spam system! Likely a compatibility issue with another mod. Disabling anti-spam to protect chat integrity.",
               new Object[0]
            );
            this.antiSpam.set(false);
         }
      } else {
         this.lines.removeInt(index);
      }
   }

   public static void registerCustomHead(String prefix, Identifier texture) {
      CUSTOM_HEAD_ENTRIES.add(new BetterChat.CustomHeadEntry(prefix, texture));
   }

   public static int whiteWithOpacity(float opacity) {
      int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
      return alpha << 24 | 16777215;
   }

   public int modifyChatWidth(int width) {
      return this.isActive() && this.playerHeads.get() ? width + 10 : width;
   }

   public void beforeDrawMessage(DrawContext context, int y, int color) {
      if (this.isActive() && this.playerHeads.get() && this.line != null) {
         if (((IChatHudLineVisible)(Object)this.line).revive$isStartOfEntry()) {
            this.drawHead(context, (IChatHudLine)(Object)this.line, y, color);
         }
      }
   }

   public void afterDrawMessage() {
      if (this.isActive() && this.playerHeads.get()) {
         this.line = null;
      }
   }

   private void drawHead(DrawContext context, IChatHudLine line, int y, int color) {
      String text = line.revive$getText().trim();
      int startOffset = 0;

      try {
         Matcher m = TIMESTAMP_REGEX.matcher(text);
         if (m.find()) {
            startOffset = m.end() + 1;
         }
      } catch (IllegalStateException var10) {
      }

      for (BetterChat.CustomHeadEntry entry : CUSTOM_HEAD_ENTRIES) {
         String prefix = this.fancyReceived.get() ? this.fancyReceivedString(entry.prefix()) : entry.prefix();
         if (text.startsWith(prefix, startOffset)) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, entry.texture(), 0, y, 0.0F, 0.0F, 8, 8, 64, 64, 64, 64, color);
            return;
         }
      }

      GameProfile sender = this.getSender(line, text);
      if (sender != null && this.mc.getNetworkHandler() != null) {
         PlayerListEntry entryx = this.mc.getNetworkHandler().getPlayerListEntry(sender.id());
         if (entryx != null) {
            PlayerSkinDrawer.draw(context, entryx.getSkinTextures(), 0, y, 8, color);
         }
      }
   }

   private GameProfile getSender(IChatHudLine line, String text) {
      GameProfile sender = line.revive$getSender();
      if (sender == null && this.mc.getNetworkHandler() != null) {
         Matcher usernameMatcher = usernameRegex.matcher(text);
         if (usernameMatcher.matches()) {
            String username = usernameMatcher.group(1);
            PlayerListEntry entry = this.mc.getNetworkHandler().getPlayerListEntry(username);
            if (entry == null && this.fancyReceived.get()) {
               entry = this.findPlayerListEntryFancy(username);
            }

            if (entry != null) {
               sender = entry.getProfile();
            }
         }
      }

      return sender;
   }

   private PlayerListEntry findPlayerListEntryFancy(String fancyName) {
      if (this.mc.getNetworkHandler() != null && this.mc.getNetworkHandler().getPlayerList() != null) {
         for (PlayerListEntry entry : this.mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (this.applyFancy(name).equals(fancyName) || toSmallCapsUpper(name).equals(fancyName)) {
               return entry;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private String applyAnnoy(String message) {
      StringBuilder sb = new StringBuilder(message.length());
      boolean upperCase = true;

      for (int cp : message.codePoints().toArray()) {
         if (upperCase) {
            sb.appendCodePoint(Character.toUpperCase(cp));
         } else {
            sb.appendCodePoint(Character.toLowerCase(cp));
         }

         upperCase = !upperCase;
      }

      return sb.toString();
   }

   private String applyFancy(String message) {
      boolean uniform = this.fancyUniform.get();
      StringBuilder sb = new StringBuilder();

      for (char ch : message.toCharArray()) {
         if (uniform && ch >= 'A' && ch <= 'Z') {
            ch = (char)(ch - 'A' + 97);
         }

         sb.append(this.SMALL_CAPS.getOrDefault(ch, ch));
      }

      return sb.toString();
   }

   private MutableText applyFancy(Text message) {
      MutableText result = Text.empty();
      TextVisitor.visit(message, (text, style, string) -> {
         result.append(Text.literal(this.applyFancy(string)).setStyle(style));
         return Optional.empty();
      }, Style.EMPTY);
      return result;
   }

   private MutableText applyFancyReceived(Text message) {
      BetterChat.ChatFont selected = this.chatFont.get();
      MutableText result = Text.empty();
      TextVisitor.visit(message, (text, style, string) -> {
         BetterChat.ChatFont leafFont = isDefaultFont(style) ? selected : BetterChat.ChatFont.bySource(style.getFont());
         Font small = leafFont == null ? null : leafFont.smallCapsSource();
         if (small == null) {
            result.append(Text.literal(this.applyFancy(string)).setStyle(style));
            return Optional.empty();
         } else {
            Style smallStyle = style.withFont(small);
            int i = 0;

            while (i < string.length()) {
               boolean lower = this.isSmallCapsLetter(string.charAt(i));
               int j = i + 1;

               while (j < string.length() && this.isSmallCapsLetter(string.charAt(j)) == lower) {
                  j++;
               }

               String run = string.substring(i, j);
               result.append(lower ? Text.literal(toSmallCapsUpper(run)).setStyle(smallStyle) : Text.literal(run).setStyle(style));
               i = j;
            }

            return Optional.empty();
         }
      }, Style.EMPTY);
      return result;
   }

   private String fancyReceivedString(String message) {
      return this.chatFont.get().smallCapsSource() != null ? toSmallCapsUpper(message) : this.applyFancy(message);
   }

   private boolean isSmallCapsLetter(char ch) {
      return ch >= 'a' && ch <= 'z' || this.fancyUniform.get() && ch >= 'A' && ch <= 'Z';
   }

   private static String toSmallCapsUpper(String text) {
      StringBuilder sb = new StringBuilder(text.length());

      for (char ch : text.toCharArray()) {
         sb.append(ch >= 'a' && ch <= 'z' ? (char)(ch - 'a' + 65) : ch);
      }

      return sb.toString();
   }

   private static boolean isDefaultFont(Style style) {
      return Style.EMPTY.getFont().equals(style.getFont());
   }

   private MutableText applyFont(Text message, Font font) {
      MutableText result = Text.empty();
      TextVisitor.visit(message, (text, style, string) -> {
         Style styled = isDefaultFont(style) ? style.withFont(font) : style;
         result.append(Text.literal(string).setStyle(styled));
         return Optional.empty();
      }, Style.EMPTY);
      return result;
   }

   private void compileFilterRegexList() {
      this.filterRegexList.clear();
      List<String> filters = this.regexFilters.get();
      if (filters != null) {
         for (String filter : filters) {
            try {
               this.filterRegexList.add(Pattern.compile(filter));
            } catch (PatternSyntaxException var5) {
               this.error("Ignoring invalid regex: %s", new Object[]{filter});
            }
         }
      }
   }

   private String getPrefix() {
      return this.prefix.get() ? this.getAffix(this.prefixText.get(), this.prefixSmallCaps.get(), this.prefixRandom.get()) : "";
   }

   private String getSuffix() {
      return this.suffix.get() ? this.getAffix(this.suffixText.get(), this.suffixSmallCaps.get(), this.suffixRandom.get()) : "";
   }

   private String getAffix(String text, boolean smallcaps, boolean random) {
      if (random) {
         return String.format("(%03d) ", Utils.random(0, 1000));
      } else {
         return smallcaps ? this.applyFancy(text) : text;
      }
   }

   private boolean containsCoordinates(String message) {
      return coordRegex.matcher(message).find();
   }

   private MutableText getSendButton(String message) {
      MutableText sendButton = Text.literal("[SEND ANYWAY]");
      MutableText hintBaseText = Text.literal("");
      MutableText hintMsg = Text.literal("Send your message to the global chat even if there are coordinates:");
      hintMsg.setStyle(hintBaseText.getStyle().withFormatting(Formatting.GRAY));
      hintBaseText.append(hintMsg);
      hintBaseText.append(Text.literal("\n" + message));
      sendButton.setStyle(
         sendButton.getStyle()
            .withFormatting(Formatting.DARK_RED)
            .withClickEvent(new RunnableClickEvent(() -> this.sendAnyway(message)))
            .withHoverEvent(new ShowText(hintBaseText))
      );
      return sendButton;
   }

   private void sendAnyway(String message) {
      if (this.mc.getNetworkHandler() != null) {
         this.bypassNextSend = true;

         try {
            this.mc.getNetworkHandler().sendChatMessage(message);
         } finally {
            this.bypassNextSend = false;
         }
      }
   }

   public boolean isInfiniteChatBox() {
      return this.isActive() && this.infiniteChatBox.get();
   }

   public boolean isLongerChat() {
      return this.isActive() && this.longerChatHistory.get();
   }

   public boolean keepHistory() {
      return this.isActive() && this.keepHistory.get();
   }

   public int getExtraChatLines() {
      return this.longerChatLines.get();
   }

   static {
      registerCustomHead("[R]", ReviveClient.identifier("textures/icons/chat/revive.png"));
      registerCustomHead("[Baritone]", ReviveClient.identifier("textures/icons/chat/baritone.png"));
   }

   public static enum ChatFont {
      DEFAULT("minecraft:default", "Default", false),
      UNIFORM("minecraft:uniform", "Uniform", false),
      ALT("minecraft:alt", "Alt", false),
      ILLAGERALT("minecraft:illageralt", "Illageralt", false),
      COMFORTAA("revive-client:comfortaa", "Comfortaa", true),
      INTER("revive-client:inter", "Inter", true),
      LEXEND("revive-client:lexend", "Lexend", true),
      JETBRAINS_MONO("revive-client:jetbrains_mono", "JetBrains Mono", true),
      TW_CEN_MT("revive-client:tw_cen_mt", "Tw Cen MT", true),
      PIXELATION("revive-client:pixelation", "Pixelation", true);

      public final String id;
      private final String displayName;
      private final boolean hasSmallCaps;

      private ChatFont(String id, String displayName, boolean hasSmallCaps) {
         this.id = id;
         this.displayName = displayName;
         this.hasSmallCaps = hasSmallCaps;
      }

      public Font source() {
         return this == DEFAULT ? null : new Font(Identifier.of(this.id));
      }

      public Font smallCapsSource() {
         return this.hasSmallCaps ? new Font(Identifier.of(this.id + "_small")) : null;
      }

      public static BetterChat.ChatFont bySource(StyleSpriteSource font) {
         for (BetterChat.ChatFont value : values()) {
            Font source = value.source();
            if (source != null && source.equals(font)) {
               return value;
            }
         }

         return null;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   private record CustomHeadEntry(String prefix, Identifier texture) {
   }

   private record StyledSegment(Style style, String text) {
   }
}
