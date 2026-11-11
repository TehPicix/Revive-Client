package revivedevelopment.reviveclient.systems.hud.elements;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.other.WindowsMediaController;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class MediaHud extends HudElement {
   public static final HudElementInfo<MediaHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "Media HUD", "Displays currently playing media and allows playback control.", MediaHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgControls = this.settings.createGroup("Controls");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
   private static volatile String sharedMedia = "Not Playing";
   private static ScheduledExecutorService scheduler;
   private String currentMedia = "Not Playing";
   private boolean recalculateSize;
   private boolean firstTick = true;
   private float opacity = 0.0F;
   private float lastWidth = 100.0F;
   private float lastHeight = 10.0F;
   private final Setting<Keybind> playPauseKey = this.sgControls
      .add(new KeybindSetting.Builder().name("play-pause").description("Keybind to play/pause media.").defaultValue(Keybind.fromKey(80)).build());
   private final Setting<Keybind> nextTrackKey = this.sgControls
      .add(new KeybindSetting.Builder().name("next-track").description("Keybind to skip to next track.").defaultValue(Keybind.fromKey(78)).build());
   private final Setting<Keybind> prevTrackKey = this.sgControls
      .add(
         new KeybindSetting.Builder().name("previous-track").description("Keybind to go back to the previous track.").defaultValue(Keybind.fromKey(66)).build()
      );
   private final Setting<Boolean> autoHide = this.sgGeneral
      .add(new BoolSetting.Builder().name("auto-hide").description("Hides the HUD when nothing is playing.").defaultValue(true).build());
   private final Setting<Boolean> enableFade = this.sgGeneral
      .add(new BoolSetting.Builder().name("fade-animation").description("Enables fade-in/fade-out animation when song changes.").defaultValue(true).build());
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays a background behind the text.").defaultValue(true).build());
   private final Map<Keybind, Boolean> keyPressedState = new HashMap<>();

   public MediaHud() {
      super(INFO);
      ensurePolling();
   }

   private static synchronized void ensurePolling() {
      if (scheduler == null && WINDOWS) {
         scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "revive-media-poll");
            t.setDaemon(true);
            return t;
         });
         scheduler.scheduleAtFixedRate(() -> {
            try {
               sharedMedia = WindowsMediaController.getCurrentMedia();
            } catch (Throwable var1) {
            }
         }, 0L, 2L, TimeUnit.SECONDS);
      }
   }

   @Override
   public void tick(HudRenderer renderer) {
      String newMedia = sharedMedia;
      if (!newMedia.equals(this.currentMedia)) {
         this.currentMedia = newMedia;
         this.recalculateSize = true;
      }

      if (this.recalculateSize) {
         this.calculateSize(renderer);
         this.recalculateSize = false;
      }

      if (this.firstTick) {
         this.calculateSize(renderer);
         this.firstTick = false;
      }

      boolean isPlaying = this.currentMedia.contains(" - ");
      float targetOpacity = !isPlaying && this.autoHide.get() ? 0.0F : 1.0F;
      if (this.enableFade.get()) {
         this.opacity = this.opacity + (targetOpacity - this.opacity) * 0.2F;
      } else {
         this.opacity = targetOpacity;
      }

      this.checkKeyPress(this.playPauseKey.get(), WindowsMediaController::playPause);
      this.checkKeyPress(this.nextTrackKey.get(), WindowsMediaController::nextTrack);
      this.checkKeyPress(this.prevTrackKey.get(), WindowsMediaController::previousTrack);
   }

   private void checkKeyPress(Keybind keybind, Runnable action) {
      boolean isPressed = ReviveClient.mc.currentScreen == null && keybind.isPressed();
      if (isPressed && !this.keyPressedState.getOrDefault(keybind, false)) {
         action.run();
      }

      this.keyPressedState.put(keybind, isPressed);
   }

   @Override
   public void render(HudRenderer renderer) {
      boolean editor = this.isInEditor();
      if (!(this.opacity <= 0.0F) || editor) {
         float alpha = editor ? 1.0F : this.opacity;
         String text = this.currentMedia;
         double xPos = this.x + 4;
         double yPos = this.y + 4;
         if (this.background.get()) {
            Color bg = HudStyle.withAlpha(HudStyle.BG_PANEL, alpha);
            renderer.panel(
               this.x,
               this.y,
               this.getWidth(),
               this.getHeight(),
               bg,
               HudStyle.alpha(HudStyle.BORDER, (int)(alpha * 220.0F)),
               HudStyle.alpha(HudStyle.ACCENT, (int)(alpha * 255.0F))
            );
         }

         Color textC = HudStyle.TEXT_PRIMARY;
         Color textCol = new Color(textC.r, textC.g, textC.b, (int)(alpha * 255.0F));
         renderer.text(text, xPos, yPos, textCol, true);
      }
   }

   private void calculateSize(HudRenderer renderer) {
      this.lastWidth = (float)renderer.textWidth(this.currentMedia, true) + 8.0F;
      this.lastHeight = (float)renderer.textHeight(true) + 8.0F;
      this.setSize(this.lastWidth, this.lastHeight);
   }
}
