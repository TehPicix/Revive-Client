package revivedevelopment.reviveclient.systems.modules.render;

import java.util.List;
import java.util.UUID;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.revive.MouseScrollEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.emotes.EmoteAutoPlay;
import revivedevelopment.reviveclient.systems.emotes.EmoteEngine;
import revivedevelopment.reviveclient.systems.emotes.EmoteLibrary;
import revivedevelopment.reviveclient.systems.emotes.EmoteManager;
import revivedevelopment.reviveclient.systems.emotes.EmoteWheelRenderer;
import revivedevelopment.reviveclient.systems.managers.EmoteSyncManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Emotes extends Module {
   private static final double DEAD_ZONE = 18.0;
   private static final int MAX_LABEL_LENGTH = 14;
   private final SettingGroup sgServer = this.settings.createGroup("Server");
   private final SettingGroup sgEmotes = this.settings.createGroup("Emotes");
   private final SettingGroup sgWheel = this.settings.createGroup("Wheel");
   private final SettingGroup sgMisc = this.settings.getDefaultGroup();
   public final Setting<String> serverUrl = this.sgServer
      .add(
         new StringSetting.Builder()
            .name("server-url")
            .description("Emote server address. Accepts host, host:port, ws:// or wss:// URLs.")
            .defaultValue("wss://emotes.revive-client.com/ws")
            .onChanged(v -> this.configChanged())
            .build()
      );
   public final Setting<Emotes.AuthMode> authMode = this.sgServer
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("auth-mode"))
                     .description("Revive Account for the official server, Server Key for a self-hosted one."))
                  .defaultValue(Emotes.AuthMode.ReviveAccount))
               .onChanged(v -> this.configChanged()))
            .build()
      );
   public final Setting<String> key = this.sgServer
      .add(
         new StringSetting.Builder()
            .name("key")
            .description("Password of a self-hosted emote server (SERVER_KEY).")
            .defaultValue("")
            .visible(() -> this.authMode.get() == Emotes.AuthMode.ServerKey)
            .onChanged(v -> this.configChanged())
            .build()
      );
   public final Setting<String> token = this.sgServer
      .add(
         new StringSetting.Builder()
            .name("token")
            .description("Optional Revive access token override. Empty = read revive-token.txt written by the launcher.")
            .defaultValue("")
            .visible(() -> this.authMode.get() == Emotes.AuthMode.ReviveAccount)
            .onChanged(v -> this.configChanged())
            .build()
      );
   public final Setting<Boolean> sendEmotes = this.sgEmotes
      .add(new BoolSetting.Builder().name("send-emotes").description("Broadcast your emotes so other Revive users see them.").defaultValue(true).build());
   public final Setting<Boolean> showOthers = this.sgEmotes
      .add(new BoolSetting.Builder().name("show-others").description("Play the emotes other Revive users send.").defaultValue(true).build());
   public final Setting<Boolean> stopOnMove = this.sgEmotes
      .add(
         new BoolSetting.Builder().name("stop-on-move").description("Stop your emote as soon as you press a movement or jump key.").defaultValue(true).build()
      );
   public final Setting<Keybind> wheelKey = this.sgWheel
      .add(
         new KeybindSetting.Builder()
            .name("wheel-key")
            .description("Hold to open the emote wheel, aim at an emote and release to play it. Release in the middle to cancel.")
            .defaultValue(Keybind.fromKey(66))
            .build()
      );
   public final Setting<Integer> wheelRadius = this.sgWheel
      .add(
         new IntSetting.Builder()
            .name("wheel-radius")
            .description("Radius of the wheel in pixels.")
            .defaultValue(110)
            .min(60)
            .max(400)
            .sliderRange(60, 300)
            .build()
      );
   public final Setting<Integer> slotsPerPage = this.sgWheel
      .add(
         new IntSetting.Builder()
            .name("slots-per-page")
            .description("Wheel slices per page, including the Stop slice. Scroll while open to change page.")
            .defaultValue(8)
            .min(4)
            .max(12)
            .sliderRange(4, 12)
            .build()
      );
   public final Setting<Double> textScale = this.sgWheel
      .add(
         new DoubleSetting.Builder()
            .name("text-scale")
            .description("Scale of the emote labels.")
            .defaultValue(1.0)
            .min(0.5)
            .max(3.0)
            .sliderRange(0.5, 2.0)
            .build()
      );
   public final Setting<Integer> wheelX = this.sgWheel
      .add(
         new IntSetting.Builder()
            .name("x-offset")
            .description("X offset from the center of the screen.")
            .defaultValue(0)
            .min(-500)
            .max(500)
            .sliderRange(-500, 500)
            .build()
      );
   public final Setting<Integer> wheelY = this.sgWheel
      .add(
         new IntSetting.Builder()
            .name("y-offset")
            .description("Y offset from the center of the screen.")
            .defaultValue(0)
            .min(-500)
            .max(500)
            .sliderRange(-500, 500)
            .build()
      );
   public final Setting<SettingColor> backgroundColor = this.sgWheel
      .add(
         new ColorSetting.Builder()
            .name("background-color")
            .description("Background color of the wheel.")
            .defaultValue(new SettingColor(40, 40, 40, 120))
            .build()
      );
   public final Setting<SettingColor> selectedColor = this.sgWheel
      .add(
         new ColorSetting.Builder().name("selected-color").description("Color of the hovered slice.").defaultValue(new SettingColor(145, 61, 226, 160)).build()
      );
   public final Setting<SettingColor> textColor = this.sgWheel
      .add(new ColorSetting.Builder().name("text-color").description("Color of the emote labels.").defaultValue(new SettingColor(255, 255, 255, 255)).build());
   public final Setting<SettingColor> playingColor = this.sgWheel
      .add(
         new ColorSetting.Builder()
            .name("playing-color")
            .description("Label color of the emote that is currently playing.")
            .defaultValue(new SettingColor(100, 255, 100, 255))
            .build()
      );
   public final Setting<Double> fadeDuration = this.sgWheel
      .add(
         new DoubleSetting.Builder()
            .name("fade-duration")
            .description("How long the wheel takes to fade in and out, in seconds (0 = snap).")
            .defaultValue(0.15)
            .min(0.0)
            .max(1.0)
            .sliderRange(0.0, 1.0)
            .build()
      );
   public final Setting<Boolean> chatNotices = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("chat-notices")
            .description("Show emote server notices (connection status, library updates) in chat.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> debug = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Log connection events to the game log (credentials are never logged).")
            .defaultValue(false)
            .build()
      );
   private List<EmoteLibrary.Emote> emotes = List.of();
   private boolean wheelActive;
   private int page;
   private int selectedSlot = -1;
   private double cursorX;
   private double cursorY;
   private double lastRawX;
   private double lastRawY;
   private float fade;
   private long fadeUpdatedAt;

   public Emotes() {
      super(Categories.Render, "emotes", "Play emotes on your player and see other Revive users emote, shared through the emote server.");
   }

   @Override
   public void onActivate() {
      this.wheelActive = false;
      this.selectedSlot = -1;
      int count = EmoteLibrary.count();
      if (count == 0) {
         this.info("No emotes loaded yet - they download from the emote server once connected.", new Object[0]);
      } else {
         this.info(
            "(highlight)%d(default) emotes. Hold (highlight)%s(default) for the wheel or use (highlight).emote <name>(default).",
            new Object[]{count, this.wheelKey.get()}
         );
      }
   }

   @Override
   public void onDeactivate() {
      this.wheelActive = false;
      this.selectedSlot = -1;
      UUID playing = EmoteManager.stopLocal();
      if (ReviveClient.EMOTES != null) {
         ReviveClient.EMOTES.onLocalStop(playing);
      }

      EmoteEngine.get().clear();
   }

   private void configChanged() {
      if (ReviveClient.EMOTES != null) {
         ReviveClient.EMOTES.markConfigDirty();
      }
   }

   public EmoteSyncManager.ConnectConfig buildConfig() {
      UUID uuid = null;
      String name = "";
      if (this.mc.getSession() != null) {
         uuid = this.mc.getSession().getUuidOrNull();
         name = this.mc.getSession().getUsername();
      }

      if (uuid == null && this.mc.player != null) {
         uuid = this.mc.player.getUuid();
      }

      return new EmoteSyncManager.ConnectConfig(
         EmoteSyncManager.normalizeUrl(this.serverUrl.get()),
         this.authMode.get() == Emotes.AuthMode.ServerKey ? EmoteSyncManager.AuthMode.KEY : EmoteSyncManager.AuthMode.REVIVE,
         this.key.get(),
         this.token.get(),
         uuid,
         name
      );
   }

   public boolean play(String name) {
      UUID uuid = EmoteManager.playLocal(name);
      if (uuid == null) {
         return false;
      } else {
         if (ReviveClient.EMOTES != null) {
            ReviveClient.EMOTES.onLocalPlay(uuid);
         }

         return true;
      }
   }

   public void stop() {
      UUID playing = EmoteManager.stopLocal();
      if (ReviveClient.EMOTES != null) {
         ReviveClient.EMOTES.onLocalStop(playing);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.stopOnMove.get() && EmoteManager.localPlaying() != null && !EmoteAutoPlay.isDriving() && this.isMoving()) {
            this.stop();
         }

         if (this.mc.currentScreen != null) {
            this.wheelActive = false;
         } else {
            boolean pressed = this.wheelKey.get().isPressed();
            if (pressed && !this.wheelActive) {
               this.emotes = EmoteLibrary.emotes();
               if (this.emotes.isEmpty()) {
                  this.info("No emotes loaded.", new Object[0]);
                  return;
               }

               this.wheelActive = true;
               this.selectedSlot = -1;
               if (this.page >= this.pageCount()) {
                  this.page = 0;
               }

               this.resetCursor();
               KeyBinding.unpressAll();
            } else if (!pressed && this.wheelActive) {
               this.wheelActive = false;
               this.executeSelection();
            }

            if (this.wheelActive) {
               this.updateCursor();
            }
         }
      } else {
         this.wheelActive = false;
      }
   }

   private boolean isMoving() {
      return this.mc.options.forwardKey.isPressed()
         || this.mc.options.backKey.isPressed()
         || this.mc.options.leftKey.isPressed()
         || this.mc.options.rightKey.isPressed()
         || this.mc.options.jumpKey.isPressed();
   }

   @EventHandler(
      priority = 100
   )
   private void onMouseScroll(MouseScrollEvent event) {
      if (this.wheelActive) {
         event.setCancelled(true);
         if (this.pageCount() > 1) {
            this.page = Math.floorMod(this.page + (event.value < 0.0 ? 1 : -1), this.pageCount());
            this.selectedSlot = -1;
         }
      }
   }

   private void executeSelection() {
      if (this.selectedSlot >= 0) {
         if (this.selectedSlot == 0) {
            this.stop();
         } else {
            int index = this.page * this.emotesPerPage() + this.selectedSlot - 1;
            if (index < this.emotes.size()) {
               this.play(this.emotes.get(index).name());
            }
         }
      }
   }

   private int emotesPerPage() {
      return this.slotsPerPage.get() - 1;
   }

   private int pageCount() {
      return Math.max(1, (this.emotes.size() + this.emotesPerPage() - 1) / this.emotesPerPage());
   }

   private int slotCount() {
      return 1 + Math.min(this.emotesPerPage(), Math.max(0, this.emotes.size() - this.page * this.emotesPerPage()));
   }

   private void resetCursor() {
      this.cursorX = 0.0;
      this.cursorY = 0.0;
      this.lastRawX = this.mc.mouse.getX();
      this.lastRawY = this.mc.mouse.getY();
   }

   private void updateCursor() {
      double rawX = this.mc.mouse.getX();
      double rawY = this.mc.mouse.getY();
      int screenWidth = this.mc.getWindow().getWidth();
      int screenHeight = this.mc.getWindow().getHeight();
      if (screenWidth > 0 && screenHeight > 0) {
         this.cursorX = this.cursorX + (rawX - this.lastRawX) * this.mc.getWindow().getScaledWidth() / screenWidth;
         this.cursorY = this.cursorY + (rawY - this.lastRawY) * this.mc.getWindow().getScaledHeight() / screenHeight;
      }

      this.lastRawX = rawX;
      this.lastRawY = rawY;
      double distance = Math.sqrt(this.cursorX * this.cursorX + this.cursorY * this.cursorY);
      double radius = this.wheelRadius.get().intValue();
      if (distance > radius) {
         this.cursorX = this.cursorX / distance * radius;
         this.cursorY = this.cursorY / distance * radius;
         distance = radius;
      }

      if (distance < 18.0) {
         this.selectedSlot = -1;
      } else {
         int slots = this.slotCount();
         double slice = 360.0 / slots;
         double degrees = Math.toDegrees(Math.atan2(this.cursorY, this.cursorX));
         if (degrees < 0.0) {
            degrees += 360.0;
         }

         degrees = (degrees + 90.0) % 360.0;
         this.selectedSlot = (int)((degrees + slice / 2.0) / slice) % slots;
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      this.updateFade();
      if (this.wheelActive || !(this.fade <= 0.0F)) {
         if (this.wheelActive) {
            this.updateCursor();
         }

         DrawContext context = event.drawContext;
         int centerX = this.mc.getWindow().getScaledWidth() / 2 + this.wheelX.get();
         int centerY = this.mc.getWindow().getScaledHeight() / 2 + this.wheelY.get();
         int radius = this.wheelRadius.get();
         int slots = this.slotCount();
         EmoteWheelRenderer.fillCircle(context, centerX, centerY, radius, this.faded(this.backgroundColor.get().getPacked()));
         if (this.selectedSlot >= 0 && this.selectedSlot < slots) {
            EmoteWheelRenderer.fillSlice(context, centerX, centerY, radius, 18, this.selectedSlot, slots, this.faded(this.selectedColor.get().getPacked()));
         }

         int border = this.faded(-1879048192);
         EmoteWheelRenderer.fillRing(context, centerX, centerY, radius, radius - 2, border);
         EmoteWheelRenderer.fillRing(context, centerX, centerY, 18, 16, border);
         UUID playing = EmoteManager.localPlaying();
         float scale = this.textScale.get().floatValue();

         for (int i = 0; i < slots; i++) {
            String label;
            int color;
            if (i == 0) {
               label = "Stop";
               color = this.faded(this.textColor.get().getPacked());
            } else {
               int emoteIndex = this.page * this.emotesPerPage() + i - 1;
               if (emoteIndex >= this.emotes.size()) {
                  continue;
               }

               EmoteLibrary.Emote emote = this.emotes.get(emoteIndex);
               label = emote.name();
               if (label.length() > 14) {
                  label = label.substring(0, 12) + "..";
               }

               color = this.faded((emote.uuid().equals(playing) ? this.playingColor : this.textColor).get().getPacked());
            }

            double midAngle = Math.toRadians(i * 360.0 / slots - 90.0);
            int labelRadius = radius * 2 / 3;
            int labelX = centerX + (int)(Math.cos(midAngle) * labelRadius);
            int labelY = centerY + (int)(Math.sin(midAngle) * labelRadius);
            EmoteWheelRenderer.drawCenteredLabel(context, label, labelX, labelY - (int)(9.0F * scale / 2.0F), scale, color);
         }

         if (this.pageCount() > 1) {
            EmoteWheelRenderer.drawCenteredLabel(
               context,
               "Page " + (this.page + 1) + "/" + this.pageCount() + " (scroll)",
               centerX,
               centerY + radius + 6,
               scale,
               this.faded(this.textColor.get().getPacked())
            );
         }

         int cx = centerX + (int)Math.round(this.cursorX);
         int cy = centerY + (int)Math.round(this.cursorY);
         EmoteWheelRenderer.fillCircle(context, cx, cy, 4, this.faded(-16777216));
         EmoteWheelRenderer.fillCircle(context, cx, cy, 2, this.faded(this.textColor.get().getPacked()));
      }
   }

   private void updateFade() {
      long now = System.nanoTime();
      float dt = this.fadeUpdatedAt == 0L ? 0.0F : (float)(now - this.fadeUpdatedAt) / 1.0E9F;
      this.fadeUpdatedAt = now;
      double duration = this.fadeDuration.get();
      float target = this.wheelActive ? 1.0F : 0.0F;
      if (duration <= 0.0) {
         this.fade = target;
      } else {
         float step = (float)(dt / duration);
         this.fade = target > this.fade ? Math.min(target, this.fade + step) : Math.max(target, this.fade - step);
      }
   }

   private int faded(int argb) {
      int alpha = Math.round((argb >>> 24 & 0xFF) * this.fade);
      return alpha << 24 | argb & 16777215;
   }

   @Override
   public String getInfoString() {
      EmoteSyncManager emotes = ReviveClient.EMOTES;
      if (emotes == null) {
         return null;
      } else if (!emotes.isConnected()) {
         return "connecting";
      } else {
         UUID playing = EmoteManager.localPlaying();
         if (playing != null) {
            EmoteLibrary.Emote e = EmoteLibrary.get(playing);
            if (e != null) {
               return e.name();
            }
         }

         return EmoteLibrary.count() + " emotes";
      }
   }

   public static enum AuthMode {
      ReviveAccount("Revive Account"),
      ServerKey("Server Key");

      private final String title;

      private AuthMode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
