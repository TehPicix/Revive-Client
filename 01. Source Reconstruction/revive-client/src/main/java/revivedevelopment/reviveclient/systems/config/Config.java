package revivedevelopment.reviveclient.systems.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.renderer.Fonts;
import revivedevelopment.reviveclient.renderer.text.FontFace;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.FontFaceSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ModuleListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.Settings;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.System;
import revivedevelopment.reviveclient.systems.Systems;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Config extends System<Config> {
   public final Settings settings = new Settings();
   private final SettingGroup sgVisual = this.settings.createGroup("Visual");
   private final SettingGroup sgModules = this.settings.createGroup("Modules");
   private final SettingGroup sgChat = this.settings.createGroup("Chat");
   private final SettingGroup sgMisc = this.settings.createGroup("Misc");
   private final SettingGroup sgPops = this.settings.createGroup("Totem Pops");
   public final Setting<Boolean> customFont = this.sgVisual
      .add(new BoolSetting.Builder().name("custom-font").description("Use a custom font.").defaultValue(true).build());
   public final Setting<FontFace> font = this.sgVisual
      .add(new FontFaceSetting.Builder().name("font").description("Custom font to use.").visible(this.customFont::get).onChanged(Fonts::load).build());
   public final Setting<Double> rainbowSpeed = this.sgVisual
      .add(new DoubleSetting.Builder().name("rainbow-speed").description("The global rainbow speed.").defaultValue(0.5).range(0.0, 10.0).sliderMax(5.0).build());
   public final Setting<Boolean> titleScreenCredits = this.sgVisual
      .add(new BoolSetting.Builder().name("title-screen-credits").description("Show Revive credits on title screen").defaultValue(true).build());
   public final Setting<Boolean> titleScreenSplashes = this.sgVisual
      .add(new BoolSetting.Builder().name("title-screen-splashes").description("Show Revive splash texts on title screen").defaultValue(true).build());
   public final Setting<Boolean> customWindowTitle = this.sgVisual
      .add(
         new BoolSetting.Builder()
            .name("custom-window-title")
            .description("Show your own text in the window title instead of the default \"Revive Client <version>\".")
            .defaultValue(false)
            .onModuleActivated(setting -> {
               if (ReviveClient.mc.getWindow() != null) {
                  ReviveClient.mc.updateWindowTitle();
               }
            })
            .onChanged(value -> {
               if (ReviveClient.mc.getWindow() != null) {
                  ReviveClient.mc.updateWindowTitle();
               }
            })
            .build()
      );
   public final Setting<String> customWindowTitleText = this.sgVisual
      .add(
         new StringSetting.Builder()
            .name("window-title-text")
            .description("The text it displays in the window title.")
            .visible(this.customWindowTitle::get)
            .defaultValue("Revive Client {mc_version} - {revive.version}")
            .onChanged(value -> {
               if (ReviveClient.mc.getWindow() != null) {
                  ReviveClient.mc.updateWindowTitle();
               }
            })
            .build()
      );
   public final Setting<SettingColor> friendColor = this.sgVisual
      .add(new ColorSetting.Builder().name("friend-color").description("The color used to show friends.").defaultValue(new SettingColor(0, 255, 180)).build());
   public final Setting<List<Module>> hiddenModules = this.sgModules
      .add(new ModuleListSetting.Builder().name("hidden-modules").description("Prevent these modules from being rendered as options in the clickgui.").build());
   public final Setting<Integer> moduleSearchCount = this.sgModules
      .add(
         new IntSetting.Builder()
            .name("module-search-count")
            .description("Amount of modules and settings to be shown in the module search bar.")
            .defaultValue(8)
            .min(1)
            .sliderMax(12)
            .build()
      );
   public final Setting<Boolean> moduleAliases = this.sgModules
      .add(
         new BoolSetting.Builder()
            .name("search-module-aliases")
            .description("Whether or not module aliases will be used in the module search bar.")
            .defaultValue(true)
            .build()
      );
   public final Setting<String> prefix = this.sgChat.add(new StringSetting.Builder().name("prefix").description("Prefix.").defaultValue(".").build());
   public final Setting<Boolean> unsignedChat = this.sgChat
      .add(
         new BoolSetting.Builder()
            .name("unsigned-chat")
            .description(
               "Send chat messages without a cryptographic signature. Required to talk on servers reached through ViaVersion (e.g. 2b2t via ViaFabricPlus), where the modern client cannot sign chat and the server silently drops signed messages. Leave on unless a server explicitly requires signed chat."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> showSentMessages = this.sgChat
      .add(
         new BoolSetting.Builder()
            .name("show-sent-messages")
            .description(
               "Draw your own chat messages locally the moment you send them. On servers reached through ViaVersion (e.g. 2b2t via ViaFabricPlus) the server does not echo your own line back in a form the client shows, so without this you see everyone else's chat but not your own. Turn off on servers that already show your own messages, or you will see them twice."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> chatFeedback = this.sgChat
      .add(new BoolSetting.Builder().name("chat-feedback").description("Sends chat feedback when revive performs certain actions.").defaultValue(true).build());
   public final Setting<Boolean> deleteChatFeedback = this.sgChat
      .add(
         new BoolSetting.Builder()
            .name("delete-chat-feedback")
            .description("Delete previous matching chat feedback to keep chat clear.")
            .visible(this.chatFeedback::get)
            .defaultValue(true)
            .build()
      );
   public final Setting<Integer> rotationHoldTicks = this.sgMisc
      .add(
         new IntSetting.Builder()
            .name("rotation-hold")
            .description("Hold long to hold server side rotation when not sending any packets.")
            .defaultValue(4)
            .build()
      );
   public final Setting<Boolean> useTeamColor = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("use-team-color")
            .description("Uses player's team color for rendering things like esp and tracers.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> popResetOutOfRender = this.sgPops
      .add(
         new BoolSetting.Builder()
            .name("reset-pops-out-of-render")
            .description("Reset a player's totem pop count once they've been out of render distance for a while.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Integer> popResetOutOfRenderTime = this.sgPops
      .add(
         new IntSetting.Builder()
            .name("out-of-render-reset-time")
            .description("Seconds out of render distance before a player's pop count resets.")
            .defaultValue(120)
            .range(10, 900)
            .sliderRange(30, 600)
            .visible(this.popResetOutOfRender::get)
            .build()
      );
   public final Setting<Boolean> popResetRegear = this.sgPops
      .add(
         new BoolSetting.Builder()
            .name("reset-pops-on-regear")
            .description("Reset a player's pop count when they place an ender chest and then open a shulker box (regearing).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Integer> regearWindow = this.sgPops
      .add(
         new IntSetting.Builder()
            .name("regear-window")
            .description("Max seconds between the ender chest placement and the shulker opening for it to count as a regear.")
            .defaultValue(120)
            .range(5, 600)
            .sliderRange(10, 300)
            .visible(this.popResetRegear::get)
            .build()
      );
   public final Setting<Double> regearRange = this.sgPops
      .add(
         new DoubleSetting.Builder()
            .name("regear-range")
            .description("Max distance from the ender chest / shulker for the nearest player to be credited with it.")
            .defaultValue(6.0)
            .range(2.0, 12.0)
            .sliderRange(3.0, 10.0)
            .visible(this.popResetRegear::get)
            .build()
      );
   public final Setting<Boolean> popResetDeathMessages = this.sgPops
      .add(
         new BoolSetting.Builder()
            .name("reset-pops-on-death-message")
            .description("Reset a player's pop count when a chat death message names them - catches deaths out of render distance.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> popResetFriends = this.sgPops
      .add(
         new BoolSetting.Builder()
            .name("reset-friends")
            .description("Also apply these resets to friends. Off by default: friend data is meant to be shared server-side instead.")
            .defaultValue(false)
            .build()
      );
   public List<String> dontShowAgainPrompts = new ArrayList<>();

   public Config() {
      super("config");
   }

   public static Config get() {
      return Systems.get(Config.class);
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("version", ReviveClient.VERSION.toString());
      tag.put("settings", this.settings.toTag());
      tag.put("dontShowAgainPrompts", this.listToTag(this.dontShowAgainPrompts));
      return tag;
   }

   public Config fromTag(NbtCompound tag) {
      if (tag.contains("settings")) {
         this.settings.fromTag(tag.getCompoundOrEmpty("settings"));
      }

      if (tag.contains("dontShowAgainPrompts")) {
         this.dontShowAgainPrompts = this.listFromTag(tag, "dontShowAgainPrompts");
      }

      return this;
   }

   private NbtList listToTag(List<String> list) {
      NbtList nbt = new NbtList();

      for (String item : list) {
         nbt.add(NbtString.of(item));
      }

      return nbt;
   }

   private List<String> listFromTag(NbtCompound tag, String key) {
      List<String> list = new ArrayList<>();

      for (NbtElement item : tag.getListOrEmpty(key)) {
         list.add(item.asString().orElse(""));
      }

      return list;
   }
}
