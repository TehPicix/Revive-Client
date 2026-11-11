package revivedevelopment.reviveclient.systems.hud.elements;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage.Format;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.other.SpotifyArtFetcher;
import revivedevelopment.reviveclient.utils.other.SpotifyGrabber;
import revivedevelopment.reviveclient.utils.other.SpotifyMediaBridge;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class SpotifyHud extends HudElement {
   public static final HudElementInfo<SpotifyHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "Spotify HUD", "Displays the currently playing song on Spotify with animations and customization.", SpotifyHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final SettingGroup sgStyle = this.settings.createGroup("Style");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private boolean recalculateSize;
   private boolean firstTick = true;
   private int ticksSinceSizeCheck;
   private float opacity = 0.0F;
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("shadow")
            .description("Renders a shadow behind text.")
            .defaultValue(true)
            .onChanged(aBoolean -> this.recalculateSize = true)
            .build()
      );
   private final Setting<Integer> border = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("border")
            .description("How much space to add around the element.")
            .defaultValue(2)
            .onChanged(integer -> this.recalculateSize = true)
            .build()
      );
   private final Setting<Integer> maxWidth = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-width")
            .description("Maximum width of the text block — longer titles are trimmed with an ellipsis.")
            .defaultValue(280)
            .min(60)
            .sliderMax(800)
            .onChanged(integer -> this.recalculateSize = true)
            .build()
      );
   private final Setting<Boolean> autoHide = this.sgGeneral
      .add(new BoolSetting.Builder().name("auto-hide").description("Hides the HUD when nothing is playing.").defaultValue(true).build());
   private final Setting<Boolean> enableFade = this.sgGeneral
      .add(new BoolSetting.Builder().name("fade-animation").description("Enables fade-in/fade-out animation when song changes.").defaultValue(true).build());
   public final Setting<Boolean> customScale = this.sgScale
      .add(
         new BoolSetting.Builder()
            .name("custom-scale")
            .description("Applies custom text scale rather than the global one.")
            .defaultValue(false)
            .onChanged(val -> this.recalculateSize = true)
            .build()
      );
   public final Setting<Double> scale = this.sgScale
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Custom text scale.")
            .visible(this.customScale::get)
            .defaultValue(1.0)
            .onChanged(val -> this.recalculateSize = true)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   private final Setting<Double> spinSpeed = this.sgStyle
      .add(
         new DoubleSetting.Builder()
            .name("spin-speed")
            .description("How fast the vinyl spins, in degrees per second.")
            .defaultValue(40.0)
            .min(0.0)
            .sliderRange(0.0, 180.0)
            .build()
      );
   private final Setting<Boolean> albumColors = this.sgStyle
      .add(
         new BoolSetting.Builder()
            .name("album-colors")
            .description("Tints the artist name and progress bar with a gradient extracted from the album cover.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> gradientLeft = this.sgStyle
      .add(
         new ColorSetting.Builder()
            .name("gradient-left")
            .description("Left gradient color, used when no album colors are available.")
            .defaultValue(new SettingColor(30, 215, 96))
            .build()
      );
   private final Setting<SettingColor> gradientRight = this.sgStyle
      .add(
         new ColorSetting.Builder()
            .name("gradient-right")
            .description("Right gradient color, used when no album colors are available.")
            .defaultValue(new SettingColor(25, 160, 255))
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays a background behind the text.").defaultValue(false).build());
   private final Setting<Boolean> bgAlbumColors = this.sgBackground
      .add(
         new BoolSetting.Builder()
            .name("background-album-colors")
            .description("Tints the background with a dimmed gradient from the album cover instead of the colors below.")
            .defaultValue(false)
            .visible(this.background::get)
            .build()
      );
   private final Setting<SettingColor> bgLeft = this.sgBackground
      .add(
         new ColorSetting.Builder()
            .name("background-left")
            .description("Left side of the background gradient. Use the same color on both sides for a solid background.")
            .defaultValue(new SettingColor(12, 12, 16, 200))
            .visible(() -> this.background.get() && !this.bgAlbumColors.get())
            .build()
      );
   private final Setting<SettingColor> bgRight = this.sgBackground
      .add(
         new ColorSetting.Builder()
            .name("background-right")
            .description("Right side of the background gradient.")
            .defaultValue(new SettingColor(24, 12, 36, 200))
            .visible(() -> this.background.get() && !this.bgAlbumColors.get())
            .build()
      );
   private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
   private static volatile String sharedSong = "Not Playing";
   private static ScheduledExecutorService scheduler;
   private String song = "";
   private String artist = "";
   private String displayedTrack = "";
   private byte[] artRgba;
   private int artW;
   private int artH;
   private Color paletteLeft;
   private Color paletteRight;
   private String loadedArtTag = "";
   private final Identifier vinylTexture;
   private boolean vinylBaked;
   private String bakedKey = "\u0000unbaked";
   private static final int VINYL_SIZE = 256;
   private static int TEXTURE_COUNT;
   private static final ArrayDeque<Identifier> FREE_TEXTURE_IDS = new ArrayDeque<>();
   private double rotation;
   private static volatile long lastTickMs;

   public SpotifyHud() {
      super(INFO);
      Identifier reused;
      synchronized (FREE_TEXTURE_IDS) {
         reused = FREE_TEXTURE_IDS.poll();
      }

      this.vinylTexture = reused != null ? reused : ReviveClient.identifier("spotify-vinyl/" + TEXTURE_COUNT++);
      SpotifyMediaBridge.start();
      ensurePolling();
   }

   private static synchronized void ensurePolling() {
      if (scheduler == null && WINDOWS) {
         scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "revive-spotify-poll");
            t.setDaemon(true);
            return t;
         });
         scheduler.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() - lastTickMs <= 5000L) {
               try {
                  sharedSong = SpotifyGrabber.getSpotifySongTitle();
               } catch (Throwable var1) {
                  sharedSong = "Not Playing";
               }
            }
         }, 0L, 2L, TimeUnit.SECONDS);
      }
   }

   @Override
   public void onRemoved() {
      this.vinylBaked = false;
      synchronized (FREE_TEXTURE_IDS) {
         if (!FREE_TEXTURE_IDS.contains(this.vinylTexture)) {
            FREE_TEXTURE_IDS.add(this.vinylTexture);
         }
      }
   }

   @Override
   public void tick(HudRenderer renderer) {
      lastTickMs = System.currentTimeMillis();
      SpotifyMediaBridge.Snapshot snap = SpotifyMediaBridge.snapshot();
      if (snap != null && !snap.title().isEmpty()) {
         this.song = snap.title();
         this.artist = snap.artist();
      } else {
         String[] parts = sharedSong.split(" - ", 2);
         this.song = sharedSong.equals("Not Playing") ? "" : parts[0];
         this.artist = parts.length > 1 ? parts[1] : "";
      }

      String track = this.song + "\u0000" + this.artist;
      if (!track.equals(this.displayedTrack)) {
         this.displayedTrack = track;
         this.recalculateSize = true;
         if (!this.song.isEmpty()) {
            SpotifyArtFetcher.request(track, this.song, this.artist);
         }
      }

      byte[] desired = null;
      String desiredTag = "";
      SpotifyMediaBridge.ArtData bridgeArt = SpotifyMediaBridge.art();
      SpotifyArtFetcher.Result fetched = SpotifyArtFetcher.result();
      if (bridgeArt != null && bridgeArt.bytes().length > 0 && !this.song.isEmpty()) {
         desired = bridgeArt.bytes();
         desiredTag = "bridge:" + bridgeArt.seq();
      } else if (fetched != null && fetched.key().equals(track) && fetched.bytes().length > 0) {
         desired = fetched.bytes();
         desiredTag = "itunes:" + track;
      }

      if (!desiredTag.equals(this.loadedArtTag)) {
         this.loadedArtTag = desiredTag;
         this.loadArt(desired);
      }

      if (!this.loadedArtTag.equals(this.bakedKey)) {
         this.bakedKey = this.loadedArtTag;
         this.bakeVinyl();
      }

      if (++this.ticksSinceSizeCheck >= 20) {
         this.ticksSinceSizeCheck = 0;
         this.recalculateSize = true;
      }

      if (this.recalculateSize || this.firstTick) {
         this.calculateSize(renderer);
         this.recalculateSize = false;
         this.firstTick = false;
      }

      boolean nothingPlaying = this.song.isEmpty();
      if (this.enableFade.get()) {
         float targetOpacity = nothingPlaying && this.autoHide.get() ? 0.0F : 1.0F;
         this.opacity = this.opacity + (targetOpacity - this.opacity) * 0.2F;
      } else {
         this.opacity = nothingPlaying && this.autoHide.get() ? 0.0F : 1.0F;
      }
   }

   @Override
   public void render(HudRenderer renderer) {
      boolean editor = this.isInEditor();
      if (!(this.opacity <= 0.01F) || editor) {
         float alpha = editor ? 1.0F : this.opacity;
         SpotifyMediaBridge.Snapshot snap = SpotifyMediaBridge.snapshot();
         String song = this.song;
         String artist = this.artist;
         long posMs;
         long durMs;
         boolean playing;
         if (snap != null) {
            posMs = snap.positionNowMs();
            durMs = snap.durationMs();
            playing = snap.playing();
         } else {
            posMs = 0L;
            durMs = 0L;
            playing = false;
         }

         if (editor && song.isEmpty()) {
            song = "Song Name";
            artist = "Artist";
            posMs = 83000L;
            durMs = 225000L;
            playing = true;
         }

         if (song.isEmpty()) {
            song = "Not Playing";
         }

         Color c1 = this.gradientColor(true);
         Color c2 = this.gradientColor(false);
         double s = this.resolvedScale();
         double th = renderer.textHeight(this.shadow.get(), s);
         double pad = this.border.get().intValue() + th * 0.35;
         double d = th * 3.2;
         double gap = th * 0.55;
         if (this.background.get()) {
            Color bl;
            Color br;
            if (this.bgAlbumColors.get() && this.paletteLeft != null) {
               bl = new Color((int)(this.paletteLeft.r * 0.3), (int)(this.paletteLeft.g * 0.3), (int)(this.paletteLeft.b * 0.3), 210);
               br = new Color((int)(this.paletteRight.r * 0.3), (int)(this.paletteRight.g * 0.3), (int)(this.paletteRight.b * 0.3), 210);
            } else {
               bl = this.bgLeft.get();
               br = this.bgRight.get();
            }

            bl = new Color(bl.r, bl.g, bl.b, (int)(bl.a * alpha));
            br = new Color(br.r, br.g, br.b, (int)(br.a * alpha));
            renderer.quad(this.x, this.y, this.getWidth(), this.getHeight(), bl, br, br, bl);
            Color el = new Color((int)(bl.r * 0.55), (int)(bl.g * 0.55), (int)(bl.b * 0.55), (int)(alpha * 200.0F));
            Color er = new Color((int)(br.r * 0.55), (int)(br.g * 0.55), (int)(br.b * 0.55), (int)(alpha * 200.0F));
            renderer.line(this.x, this.y, this.x + this.getWidth(), this.y, el);
            renderer.line(this.x + this.getWidth(), this.y, this.x + this.getWidth(), this.y + this.getHeight(), er);
            renderer.line(this.x + this.getWidth(), this.y + this.getHeight(), this.x, this.y + this.getHeight(), er);
            renderer.line(this.x, this.y + this.getHeight(), this.x, this.y, el);
         }

         double cy = this.y + this.getHeight() / 2.0;
         double rightX = this.x + pad + d + gap;
         double rightEnd = this.x + this.getWidth() - pad;
         double timeScale = s * 0.85;
         double timeH = renderer.textHeight(this.shadow.get(), timeScale);
         double barH = Math.max(2.0, th * 0.38);
         double rowGap = th * 0.4;
         double row2H = Math.max(timeH, barH);
         boolean hasBar = durMs > 0L;
         double blockH = hasBar ? th + rowGap + row2H : th;
         double titleY = cy - blockH / 2.0;
         String titleLine = artist.isEmpty() ? song : song + " - " + artist;
         titleLine = this.fitText(renderer, titleLine, rightEnd - rightX, s);
         this.gradientText(renderer, titleLine, rightX, titleY, c1, c2, alpha, s);
         if (hasBar) {
            double row2Y = titleY + th + rowGap;
            String posStr = formatTime(posMs);
            String durStr = formatTime(durMs);
            double durW = renderer.textWidth(durStr, this.shadow.get(), timeScale);
            double textY = row2Y + (row2H - timeH) / 2.0;
            renderer.text(posStr, rightX, textY, alpha(HudStyle.TEXT_SECONDARY, alpha), this.shadow.get(), timeScale);
            renderer.text(durStr, rightEnd - durW, textY, alpha(HudStyle.TEXT_SECONDARY, alpha), this.shadow.get(), timeScale);
            double posW = renderer.textWidth(posStr, this.shadow.get(), timeScale);
            double barX = rightX + posW + th * 0.35;
            double barW = rightEnd - durW - th * 0.35 - barX;
            if (barW > 4.0) {
               double barY = row2Y + (row2H - barH) / 2.0;
               Color d1 = mul(c1, 0.42F, alpha);
               Color d2 = mul(c2, 0.42F, alpha);
               renderer.quad(barX, barY, barW, barH, d1, d2, d2, d1);
               double progress = Math.min(1.0, Math.max(0.0, (double)posMs / durMs));
               if (progress > 0.0) {
                  Color fill1 = alpha(c1, alpha);
                  Color fillMid = alpha(lerp(c1, c2, progress), alpha);
                  renderer.quad(barX, barY, barW * progress, barH, fill1, fillMid, fillMid, fill1);
               }
            }
         }

         if (playing || editor) {
            this.rotation = (this.rotation + renderer.delta * this.spinSpeed.get()) % 360.0;
         }

         double vinylCx = this.x + pad + d / 2.0;
         double vinylR = d / 2.0;
         double rot = this.rotation;
         renderer.post(() -> this.drawVinyl(renderer, vinylCx, cy, vinylR, rot, alpha));
      }
   }

   private void drawVinyl(HudRenderer renderer, double cx, double cy, double radius, double rotDeg, float alpha) {
      DrawContext ctx = renderer.drawContext;
      if (ctx != null && this.vinylBaked) {
         float sf = ReviveClient.mc.getWindow().getScaleFactor();
         Matrix3x2fStack matrices = ctx.getMatrices();
         matrices.pushMatrix();
         matrices.scale(1.0F / sf, 1.0F / sf);
         matrices.translate((float)cx, (float)cy);
         matrices.rotate((float)Math.toRadians(rotDeg));
         int r = (int)Math.round(radius);
         int tint = new Color(255, 255, 255, (int)(alpha * 255.0F)).getPacked();
         ctx.drawTexture(RenderPipelines.GUI_TEXTURED, this.vinylTexture, -r, -r, 0.0F, 0.0F, r * 2, r * 2, 256, 256, 256, 256, tint);
         matrices.popMatrix();
      }
   }

   private void bakeVinyl() {
      int size = 256;
      double R = size / 2.0;
      ByteBuffer pixels = MemoryUtil.memAlloc(size * size * 4);

      try {
         for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
               double dx = px - R + 0.5;
               double dy = py - R + 0.5;
               double dist = Math.sqrt(dx * dx + dy * dy) / R;
               if (dist > 1.0) {
                  pixels.put((byte)0).put((byte)0).put((byte)0).put((byte)0);
               } else {
                  int r;
                  int g;
                  int b;
                  if (this.artRgba != null) {
                     int ax = Math.min(this.artW - 1, px * this.artW / size);
                     int ay = Math.min(this.artH - 1, py * this.artH / size);
                     int o = (ay * this.artW + ax) * 4;
                     r = this.artRgba[o] & 255;
                     g = this.artRgba[o + 1] & 255;
                     b = this.artRgba[o + 2] & 255;
                  } else {
                     r = 24;
                     g = 24;
                     b = 28;
                  }

                  if (dist > 0.955) {
                     r = (int)(r * 0.35);
                     g = (int)(g * 0.35);
                     b = (int)(b * 0.35);
                  } else if (Math.abs(dist - 0.86) < 0.012 || Math.abs(dist - 0.74) < 0.012 || Math.abs(dist - 0.62) < 0.012) {
                     r = (int)(r * 0.55);
                     g = (int)(g * 0.55);
                     b = (int)(b * 0.55);
                  }

                  if (dist < 0.09) {
                     r = 12;
                     g = 12;
                     b = 14;
                  }

                  double edge = (1.0 - dist) * R;
                  int a = edge >= 1.5 ? 255 : (int)(255.0 * Math.max(0.0, edge / 1.5));
                  pixels.put((byte)r).put((byte)g).put((byte)b).put((byte)a);
               }
            }
         }

         pixels.rewind();
         NativeImage img = new NativeImage(Format.RGBA, size, size, false);
         MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), img.imageId(), (long)size * size * 4L);
         ReviveClient.mc.getTextureManager().registerTexture(this.vinylTexture, new NativeImageBackedTexture(null, img));
         this.vinylBaked = true;
      } catch (Throwable var22) {
         System.out.println("[SpotifyHud] failed to bake vinyl texture: " + var22);
      } finally {
         MemoryUtil.memFree(pixels);
      }
   }

   private void loadArt(byte[] bytes) {
      this.artRgba = null;
      this.paletteLeft = null;
      this.paletteRight = null;
      if (bytes != null && bytes.length != 0) {
         ByteBuffer data = null;

         try {
            data = MemoryUtil.memAlloc(bytes.length);
            data.put(bytes).rewind();
            MemoryStack stack = MemoryStack.stackPush();

            label135: {
               try {
                  IntBuffer w = stack.mallocInt(1);
                  IntBuffer h = stack.mallocInt(1);
                  IntBuffer comp = stack.mallocInt(1);
                  ByteBuffer image = STBImage.stbi_load_from_memory(data, w, h, comp, 4);
                  if (image == null) {
                     System.out.println("[SpotifyHud] failed to decode album art: " + STBImage.stbi_failure_reason());
                     break label135;
                  }

                  this.artW = w.get(0);
                  this.artH = h.get(0);
                  this.artRgba = new byte[this.artW * this.artH * 4];
                  image.get(0, this.artRgba);
                  STBImage.stbi_image_free(image);
                  this.extractPalette(this.artRgba, this.artW, this.artH);
               } catch (Throwable var14) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable var13) {
                        var14.addSuppressed(var13);
                     }
                  }

                  throw var14;
               }

               if (stack != null) {
                  stack.close();
               }

               return;
            }

            if (stack != null) {
               stack.close();
            }
         } catch (Throwable var15) {
            System.out.println("[SpotifyHud] failed to load album art: " + var15);
            this.artRgba = null;
            this.paletteLeft = null;
            this.paletteRight = null;
            return;
         } finally {
            if (data != null) {
               MemoryUtil.memFree(data);
            }
         }
      }
   }

   private void extractPalette(byte[] rgba, int width, int height) {
      int[] count = new int[512];
      long[] rSum = new long[512];
      long[] gSum = new long[512];
      long[] bSum = new long[512];
      int pixels = width * height;
      int stride = Math.max(1, pixels / 8192);

      for (int i = 0; i < pixels; i += stride) {
         int o = i * 4;
         int r = rgba[o] & 255;
         int g = rgba[o + 1] & 255;
         int b = rgba[o + 2] & 255;
         if ((rgba[o + 3] & 255) >= 128) {
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            if (max >= 26 && min <= 235) {
               int bucket = r >> 5 << 6 | g >> 5 << 3 | b >> 5;
               count[bucket]++;
               rSum[bucket] += r;
               gSum[bucket] += g;
               bSum[bucket] += b;
            }
         }
      }

      int best = this.pickBucket(count, rSum, gSum, bSum, -1, null);
      if (best != -1) {
         Color first = this.bucketColor(best, count, rSum, gSum, bSum);
         int second = this.pickBucket(count, rSum, gSum, bSum, best, first);
         Color secondColor = second == -1 ? lerp(first, new Color(255, 255, 255, 255), 0.45) : this.bucketColor(second, count, rSum, gSum, bSum);
         this.paletteLeft = this.brighten(first);
         this.paletteRight = this.brighten(secondColor);
      }
   }

   private int pickBucket(int[] count, long[] rSum, long[] gSum, long[] bSum, int exclude, Color awayFrom) {
      int best = -1;
      double bestScore = 0.0;

      for (int i = 0; i < 512; i++) {
         if (count[i] != 0 && i != exclude) {
            int r = (int)(rSum[i] / count[i]);
            int g = (int)(gSum[i] / count[i]);
            int b = (int)(bSum[i] / count[i]);
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            double saturation = max == 0 ? 0.0 : (double)(max - min) / max;
            double score = count[i] * (0.15 + saturation);
            if (awayFrom != null) {
               int dr = r - awayFrom.r;
               int dg = g - awayFrom.g;
               int db = b - awayFrom.b;
               if (dr * dr + dg * dg + db * db < 8100) {
                  continue;
               }
            }

            if (score > bestScore) {
               bestScore = score;
               best = i;
            }
         }
      }

      return best;
   }

   private Color bucketColor(int bucket, int[] count, long[] rSum, long[] gSum, long[] bSum) {
      return new Color((int)(rSum[bucket] / count[bucket]), (int)(gSum[bucket] / count[bucket]), (int)(bSum[bucket] / count[bucket]), 255);
   }

   private Color brighten(Color c) {
      int max = Math.max(c.r, Math.max(c.g, c.b));
      if (max < 165 && max != 0) {
         float factor = 165.0F / max;
         return new Color(Math.min(255, (int)(c.r * factor)), Math.min(255, (int)(c.g * factor)), Math.min(255, (int)(c.b * factor)), 255);
      } else {
         return c;
      }
   }

   private Color gradientColor(boolean left) {
      if (this.albumColors.get()) {
         Color palette = left ? this.paletteLeft : this.paletteRight;
         if (palette != null) {
            return palette;
         }
      }

      return left ? this.gradientLeft.get() : this.gradientRight.get();
   }

   private String fitText(HudRenderer renderer, String text, double maxWidth, double scale) {
      double total = 0.0;
      int[] codePoints = text.codePoints().toArray();
      double[] widths = new double[codePoints.length];

      for (int i = 0; i < codePoints.length; i++) {
         widths[i] = renderer.textWidth(new String(Character.toChars(codePoints[i])), this.shadow.get(), scale);
         total += widths[i];
      }

      if (total <= maxWidth) {
         return text;
      } else {
         double ellipsisWidth = renderer.textWidth("...", this.shadow.get(), scale);
         double width = 0.0;
         StringBuilder out = new StringBuilder();

         for (int i = 0; i < codePoints.length && !(width + widths[i] > maxWidth - ellipsisWidth); i++) {
            width += widths[i];
            out.appendCodePoint(codePoints[i]);
         }

         return out + "...";
      }
   }

   private void gradientText(HudRenderer renderer, String text, double x, double y, Color from, Color to, float alpha, double scale) {
      double total = renderer.textWidth(text, this.shadow.get(), scale);
      double cx = x;
      int[] codePoints = text.codePoints().toArray();

      for (int cp : codePoints) {
         double t = total <= 0.0 ? 0.0 : (cx - x) / total;
         Color color = alpha(lerp(from, to, t), alpha);
         cx = renderer.text(new String(Character.toChars(cp)), cx, y, color, this.shadow.get(), scale);
      }
   }

   private static Color lerp(Color a, Color b, double t) {
      return new Color((int)(a.r + (b.r - a.r) * t), (int)(a.g + (b.g - a.g) * t), (int)(a.b + (b.b - a.b) * t), 255);
   }

   private static Color alpha(Color c, float alpha) {
      return new Color(c.r, c.g, c.b, (int)(alpha * 255.0F));
   }

   private static Color mul(Color c, float factor, float alpha) {
      return new Color((int)(c.r * factor), (int)(c.g * factor), (int)(c.b * factor), (int)(alpha * 255.0F));
   }

   private static String formatTime(long ms) {
      long totalSeconds = Math.max(0L, ms / 1000L);
      return totalSeconds / 60L + ":" + String.format("%02d", totalSeconds % 60L);
   }

   private void calculateSize(HudRenderer renderer) {
      double s = this.resolvedScale();
      double th = renderer.textHeight(this.shadow.get(), s);
      double pad = this.border.get().intValue() + th * 0.35;
      double d = th * 3.2;
      double gap = th * 0.55;
      String sizeSong = this.song.isEmpty() ? "Song Name" : this.song;
      String sizeArtist = this.song.isEmpty() ? "Artist" : this.artist;
      String titleLine = sizeArtist.isEmpty() ? sizeSong : sizeSong + " - " + sizeArtist;
      double titleWidth = 0.0;

      for (int cp : titleLine.codePoints().toArray()) {
         titleWidth += renderer.textWidth(new String(Character.toChars(cp)), this.shadow.get(), s);
         if (titleWidth >= this.maxWidth.get().intValue()) {
            titleWidth = this.maxWidth.get().intValue();
            break;
         }
      }

      double timeScale = s * 0.85;
      double timesWidth = renderer.textWidth("00:00", this.shadow.get(), timeScale) * 2.0 + th * 0.7 + th * 4.0;
      double rightWidth = Math.max(Math.max(titleWidth, timesWidth), th * 7.0);
      this.setSize(pad * 2.0 + d + gap + rightWidth, pad * 2.0 + d);
   }

   private double resolvedScale() {
      return this.customScale.get() ? this.scale.get() : Hud.get().getTextScale();
   }
}
