package revivedevelopment.reviveclient.gui.titlemenu;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.util.animation.Ascii;
import revivedevelopment.reviveclient.util.animation.TitleAnimation;
import revivedevelopment.reviveclient.util.audio.TitleAudioAnalyzer;

public class CustomTitleScreen extends Screen {
   private static final String CLIENT_NAME = "R  E  V  I  V  E     ";
   private static final String CLIENT_VERSION = "PRE-RELEASE-0.0.2";
   private static final String NEKO_GIF_PATH = "textures/gui/neko.gif";
   private Color ACCENT = new Color(255, 80, 170);
   private float animationTime = 0.0F;
   private final TitleAnimation pulseAnimation = new TitleAnimation(2000L, 0.0F, 1.0F, TitleAnimation.Easing.LINEAR);
   private final TitleAnimation bassAnimation = new TitleAnimation(300L, 0.0F, 1.0F, TitleAnimation.Easing.EASE_OUT_EXPO);
   private final TitleAnimation trebleAnimation = new TitleAnimation(200L, 0.0F, 1.0F, TitleAnimation.Easing.EASE_OUT_CUBIC);
   private float bassVizLevel = 0.0F;
   private float midVizLevel = 0.0F;
   private float trebleVizLevel = 0.0F;
   private float logoScalePulse = 0.0F;
   private final Random random = new Random();
   private TitleAnimation bassImpactAnimation = null;
   private TitleAnimation trebleImpactAnimation = null;
   private static final int BASS_IMPACT_DURATION = 500;
   private static final int TREBLE_IMPACT_DURATION = 400;
   private final int MAX_WARP_POINTS = 3;
   private final float[] warpX = new float[3];
   private final float[] warpY = new float[3];
   private final float[] warpPhase = new float[3];
   private final float[] warpAmplitude = new float[3];
   private float chromaticOffset = 0.0F;
   private long lastBassTime = 0L;
   private long lastTrebleTime = 0L;
   private final List<CustomTitleScreen.MusicParticle> particles = new ArrayList<>();
   private final int MAX_PARTICLES = 60;
   private int particleSpawnCounter = 0;
   private final List<CustomTitleScreen.DeveloperInfo> developers = new ArrayList<>();
   private float scrollX = 0.0F;
   private final List<CustomTitleScreen.Droplet> activeDroplets = new ArrayList<>();
   private Ascii nekoAscii;
   private TitleMusicControls musicControls;

   private static Color themeAccent() {
      int s = FontManager.get().getSecondaryColor();
      return new Color(s >> 16 & 0xFF, s >> 8 & 0xFF, s & 0xFF);
   }

   public CustomTitleScreen() {
      super(Text.literal("R  E  V  I  V  E     "));

      for (int i = 0; i < 3; i++) {
         this.warpX[i] = this.random.nextFloat();
         this.warpY[i] = this.random.nextFloat();
         this.warpPhase[i] = this.random.nextFloat() * (float) Math.PI * 2.0F;
         this.warpAmplitude[i] = 0.1F + this.random.nextFloat() * 0.3F;
      }

      this.initDevelopers();
   }

   private void initDevelopers() {
      this.developers.add(new CustomTitleScreen.DeveloperInfo("Crownizzle", new Color(145, 61, 226)));
      this.developers.add(new CustomTitleScreen.DeveloperInfo("_Synful", new Color(90, 200, 255)));
      this.developers.add(new CustomTitleScreen.DeveloperInfo("Zandax", new Color(255, 215, 0)));
      this.developers.add(new CustomTitleScreen.DeveloperInfo("King_Soft", new Color(80, 220, 120)));
   }

   protected void init() {
      int buttonWidth = 200;
      int buttonHeight = 20;
      int spacing = 25;
      int startY = this.height / 2 + 4;
      int centerX = (this.width - buttonWidth) / 2;
      this.addDrawableChild(
         new CustomTitleScreen.ThemedButton(
            centerX, startY, buttonWidth, buttonHeight, Text.literal("Singleplayer"), () -> this.client.setScreen(new SelectWorldScreen(this))
         )
      );
      this.addDrawableChild(
         new CustomTitleScreen.ThemedButton(
            centerX, startY + spacing, buttonWidth, buttonHeight, Text.literal("Multiplayer"), () -> this.client.setScreen(new MultiplayerScreen(this))
         )
      );
      int half = buttonWidth / 2 - 5;
      this.addDrawableChild(
         new CustomTitleScreen.ThemedButton(
            centerX,
            startY + 2 * spacing,
            half,
            buttonHeight,
            Text.literal("Options"),
            () -> this.client.setScreen(new OptionsScreen(this, this.client.options))
         )
      );
      this.addDrawableChild(
         new CustomTitleScreen.ThemedButton(centerX + half + 10, startY + 2 * spacing, half, buttonHeight, Text.literal("Quit"), () -> this.client.stop())
      );
      this.musicControls = new TitleMusicControls(this);
      this.nekoAscii = new Ascii(this.width, this.height, "textures/gui/neko.gif", 70);
      MinecraftClient.getInstance().getMusicTracker().stop();
      if (!TitleMusicPlayer.getInstance().isPlaying()) {
         TitleMusicPlayer.getInstance().playMenuMusic();
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      this.ACCENT = themeAccent();
      this.animationTime += deltaTicks * 0.01F;
      TitleAudioAnalyzer analyzer = TitleAudioAnalyzer.getInstance();
      float bassLevel = analyzer.getBassLevel();
      float midLevel = analyzer.getMidLevel();
      float trebleLevel = analyzer.getTrebleLevel();
      this.midVizLevel = this.midVizLevel + (midLevel - this.midVizLevel) * 0.25F;
      long now = System.currentTimeMillis();
      if (analyzer.consumeBassBeat()) {
         this.bassImpactAnimation = new TitleAnimation(500L, 1.0F, 0.0F, TitleAnimation.Easing.EASE_OUT_EXPO);
         this.lastBassTime = now;
      }

      if (analyzer.consumeTrebleBeat()) {
         this.trebleImpactAnimation = new TitleAnimation(400L, 1.0F, 0.0F, TitleAnimation.Easing.EASE_OUT_CUBIC);
         this.chromaticOffset = 1.5F + trebleLevel * 2.5F;
         this.lastTrebleTime = now;
      }

      if (this.bassImpactAnimation != null) {
         this.bassImpactAnimation.update(deltaTicks);
         if (this.bassImpactAnimation.isDone()) {
            this.bassImpactAnimation = null;
         }
      }

      if (this.trebleImpactAnimation != null) {
         this.trebleImpactAnimation.update(deltaTicks);
         if (this.trebleImpactAnimation.isDone()) {
            this.trebleImpactAnimation = null;
         }

         this.chromaticOffset *= 0.92F;
      } else {
         this.chromaticOffset *= 0.8F;
      }

      if (bassLevel > this.bassVizLevel) {
         this.bassVizLevel = bassLevel * 0.9F + this.bassVizLevel * 0.1F;
         this.bassAnimation.reset(this.bassVizLevel * 0.7F, this.bassVizLevel);
      } else {
         this.bassVizLevel = Math.max(bassLevel, this.bassVizLevel * 0.78F);
      }

      if (trebleLevel > this.trebleVizLevel) {
         this.trebleVizLevel = trebleLevel * 0.7F + this.trebleVizLevel * 0.3F;
         this.trebleAnimation.reset(this.trebleVizLevel * 0.7F, this.trebleVizLevel);
      } else {
         this.trebleVizLevel = Math.max(trebleLevel, this.trebleVizLevel * 0.8F);
      }

      if (this.pulseAnimation.isDone()) {
         this.pulseAnimation.reset();
      }

      float pulseValue = (float)(0.5 + 0.5 * Math.sin(this.pulseAnimation.getValue() * Math.PI * 2.0));
      float targetPulse = this.bassVizLevel * 0.9F + (this.bassImpactAnimation != null ? this.bassImpactAnimation.getValue() * 0.6F : 0.0F);
      if (targetPulse > this.logoScalePulse) {
         this.logoScalePulse = this.logoScalePulse + (targetPulse - this.logoScalePulse) * 0.45F;
      } else {
         this.logoScalePulse = this.logoScalePulse + (targetPulse - this.logoScalePulse) * 0.28F;
      }

      this.drawBackground(context);
      context.getMatrices().pushMatrix();
      float shake = bassLevel * 2.5F;
      context.getMatrices().translate((this.random.nextFloat() - 0.5F) * shake, (this.random.nextFloat() - 0.5F) * shake);
      this.drawLogoEffect(context, this.bassVizLevel, this.trebleVizLevel, pulseValue);
      this.updateParticles(deltaTicks, this.bassVizLevel, this.trebleVizLevel);
      this.drawParticles(context);
      this.drawDeveloperScroll(context);
      if (this.nekoAscii != null) {
         this.nekoAscii.updatePosition(this.width, this.height);
         this.nekoAscii.render(context, mouseX, mouseY, deltaTicks);
      }

      context.getMatrices().popMatrix();
      super.render(context, mouseX, mouseY, deltaTicks);
      this.drawVersionText(context);
      this.musicControls.render(context, mouseX, mouseY, deltaTicks);
      String credits = "|Made with ♥| ";
      int cw = this.client.textRenderer.getWidth(credits);
      context.drawTextWithShadow(this.client.textRenderer, credits, this.width - cw - 5, this.height - 15, -1);
   }

   private void drawBackground(DrawContext context) {
      context.fill(0, 0, this.width, this.height, toARGB(new Color(20, 20, 22)));
      Color darkened = new Color(Math.max(0, this.ACCENT.getRed() - 180), Math.max(0, this.ACCENT.getGreen() - 180), Math.max(0, this.ACCENT.getBlue() - 180));
      int gradH = this.height / 2;

      for (int y = 0; y < gradH; y++) {
         float alpha = 0.1F * (1.0F - (float)y / gradH);
         Color grad = new Color(darkened.getRed(), darkened.getGreen(), darkened.getBlue(), (int)(alpha * 255.0F));
         context.fill(0, this.height / 4 + y, this.width, this.height / 4 + y + 1, toARGB(grad));
         context.fill(0, this.height / 4 - y, this.width, this.height / 4 - y + 1, toARGB(grad));
      }
   }

   private void drawLogoEffect(DrawContext context, float bassLevel, float trebleLevel, float pulseValue) {
      float breath = (float)Math.sin(this.animationTime * 0.6F) * 0.07F;
      float logoScale = 4.0F + breath + this.logoScalePulse;
      int textWidth = (int)(this.client.textRenderer.getWidth("R  E  V  I  V  E     ") * logoScale * 0.9F);
      int x = (this.width - textWidth) / 2;
      int y = this.height / 4;
      float shakeX = 0.0F;
      float shakeY = 0.0F;
      if (this.bassImpactAnimation != null) {
         float impact = this.bassImpactAnimation.getValue();
         shakeX = (this.random.nextFloat() * 2.0F - 1.0F) * impact * 8.0F;
         shakeY = (this.random.nextFloat() * 2.0F - 1.0F) * impact * 5.0F;
      }

      if (trebleLevel > 0.3F || bassLevel > 0.4F) {
         int glowAlpha = (int)(30.0F + bassLevel * 90.0F);
         Color glow = new Color(this.ACCENT.getRed(), this.ACCENT.getGreen(), this.ACCENT.getBlue(), glowAlpha);
         this.drawWarpedText(
            context, "R  E  V  I  V  E     ", x + shakeX + 3.0F, y + shakeY + 3.0F, logoScale, toARGB(glow), bassLevel * 0.5F, trebleLevel * 0.5F, false
         );
      }

      Color highlight = new Color(
         Math.min(255, this.ACCENT.getRed() + 40), Math.min(255, this.ACCENT.getGreen() + 40), Math.min(255, this.ACCENT.getBlue() + 40)
      );
      Color finalColor = new Color(
         clamp((int)(this.ACCENT.getRed() + (highlight.getRed() - this.ACCENT.getRed()) * (pulseValue + bassLevel * 0.4F))),
         clamp((int)(this.ACCENT.getGreen() + (highlight.getGreen() - this.ACCENT.getGreen()) * (pulseValue + trebleLevel * 0.25F + this.midVizLevel * 0.3F))),
         clamp((int)(this.ACCENT.getBlue() + (highlight.getBlue() - this.ACCENT.getBlue()) * (pulseValue + (bassLevel + trebleLevel) * 0.2F))),
         clamp((int)(240.0F + bassLevel * 15.0F))
      );
      this.drawWarpedText(
         context, "R  E  V  I  V  E     ", x + shakeX, y + shakeY, logoScale, toARGB(finalColor), bassLevel, trebleLevel, this.chromaticOffset > 0.5F
      );
      this.drawSludgeDrips(context, "R  E  V  I  V  E     ", x + shakeX, y + shakeY, logoScale, finalColor, bassLevel);
      String subtitle = "B E T A";
      int sw = this.client.textRenderer.getWidth(subtitle);
      float sx = this.width / 2.0F - sw / 2.0F + (float)Math.sin(this.animationTime * 0.8) * 3.0F;
      float sy = y + 30.0F * logoScale / 4.0F + 10.0F + (float)Math.cos(this.animationTime * 0.5) * 2.0F;
      float strobeSpeed = 5.0F;

      for (int i = 0; i < subtitle.length(); i++) {
         String ch = String.valueOf(subtitle.charAt(i));
         float phase = this.animationTime * strobeSpeed + i * 0.3F;
         int r = clamp((int)(100.0 + Math.sin(phase) * 155.0));
         int g = clamp((int)(100.0 + Math.sin(phase + 2.0F) * 155.0));
         int b = clamp((int)(220.0 + Math.cos(phase) * 35.0));
         float cx = sx + this.client.textRenderer.getWidth(subtitle.substring(0, i));
         context.drawText(this.client.textRenderer, ch, (int)cx, (int)sy, toARGB(new Color(r, g, b)), false);
      }
   }

   private void drawSludgeDrips(DrawContext context, String text, float x, float y, float scale, Color color, float bassLevel) {
      if (this.bassImpactAnimation != null && this.bassImpactAnimation.getValue() > 0.7F) {
         float charSpacing = this.client.textRenderer.getWidth("X") * scale * 0.9F;
         int dropsToSpawn = 1 + this.random.nextInt(2);

         for (int i = 0; i < dropsToSpawn; i++) {
            int ci = this.random.nextInt(text.length());
            float dx = x + ci * charSpacing + this.random.nextFloat() * 0.8F * this.client.textRenderer.getWidth(String.valueOf(text.charAt(ci))) * scale;
            float dy = y + 9.0F * scale;
            this.activeDroplets
               .add(
                  new CustomTitleScreen.Droplet(
                     dx,
                     dy,
                     2.0F + this.random.nextFloat() * 2.0F,
                     1.0F + this.random.nextFloat() * 3.0F,
                     new Color(color.getRed(), color.getGreen(), color.getBlue(), 180 + this.random.nextInt(75))
                  )
               );
         }
      }

      for (int i = this.activeDroplets.size() - 1; i >= 0; i--) {
         CustomTitleScreen.Droplet d = this.activeDroplets.get(i);
         d.y = d.y + d.speed;
         if (d.y > this.height) {
            this.activeDroplets.remove(i);
         } else {
            context.fill((int)(d.x - d.size / 2.0F), (int)(d.y - d.size / 2.0F), (int)(d.x + d.size / 2.0F), (int)(d.y + d.size / 2.0F), toARGB(d.color));

            for (int t = 1; t <= 3; t++) {
               float ta = d.color.getAlpha() * (0.5F - t * 0.15F);
               if (!(ta <= 0.0F)) {
                  float ts = d.size * (0.7F - t * 0.15F);
                  context.fill(
                     (int)(d.x - ts / 2.0F),
                     (int)(d.y - ts / 2.0F - t * 2),
                     (int)(d.x + ts / 2.0F),
                     (int)(d.y + ts / 2.0F - t * 2),
                     toARGB(new Color(d.color.getRed(), d.color.getGreen(), d.color.getBlue(), (int)ta))
                  );
               }
            }
         }
      }
   }

   private void drawDeveloperScroll(DrawContext context) {
      this.scrollX = this.scrollX - 0.5F * (1.0F + this.trebleVizLevel * 0.5F);
      int y = 10;
      int totalWidth = 0;

      for (CustomTitleScreen.DeveloperInfo d : this.developers) {
         totalWidth += this.client.textRenderer.getWidth(d.name + "   •   ");
      }

      if (this.scrollX < -totalWidth) {
         this.scrollX = this.width;
      }

      context.fill(0, y - 2, this.width, y + 12, toARGB(new Color(20, 20, 20, 120)));
      float cx = this.scrollX;

      for (int i = 0; i < this.developers.size(); i++) {
         CustomTitleScreen.DeveloperInfo d = this.developers.get(i);
         int w = this.client.textRenderer.getWidth(d.name);
         if (cx + w > 0.0F && cx < this.width) {
            float s = 1.0F + this.bassVizLevel * 0.5F;
            float ccx = cx + w / 2.0F;
            float ccy = y + 4.0F;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(ccx, ccy);
            context.getMatrices().scale(s, s);
            context.getMatrices().translate(-ccx, -ccy);
            context.drawTextWithShadow(this.client.textRenderer, d.name, (int)cx, y, toARGB(d.color));
            context.getMatrices().popMatrix();
         }

         cx += w;
         if (i < this.developers.size() - 1) {
            String sep = "   •   ";
            int sw = this.client.textRenderer.getWidth(sep);
            if (cx + sw > 0.0F && cx < this.width) {
               context.drawTextWithShadow(this.client.textRenderer, sep, (int)cx, y, toARGB(Color.GRAY));
            }

            cx += sw;
         }
      }
   }

   private void drawWarpedText(
      DrawContext context, String text, float x, float y, float scale, int color, float bassLevel, float trebleLevel, boolean chromatic
   ) {
      for (int i = 0; i < 3; i++) {
         this.warpPhase[i] = this.warpPhase[i] + (0.003F + bassLevel * 0.005F + this.midVizLevel * 0.012F + trebleLevel * 0.009F * (i % 2 == 0 ? 1 : -1));
         if (this.warpPhase[i] > Math.PI * 2) {
            this.warpPhase[i] = this.warpPhase[i] - (float) (Math.PI * 2);
         }

         this.warpAmplitude[i] = 0.12F + bassLevel * 0.28F + this.midVizLevel * 0.2F + trebleLevel * 0.12F;
      }

      if (chromatic && this.chromaticOffset > 0.5F) {
         int alpha = color >> 24 & 0xFF;
         this.drawWarpedTextColor(context, text, x - this.chromaticOffset, y, scale, toARGB(new Color(255, 50, 50, alpha)), bassLevel, trebleLevel);
         this.drawWarpedTextColor(context, text, x, y, scale, toARGB(new Color(50, 255, 50, alpha)), bassLevel, trebleLevel);
         this.drawWarpedTextColor(context, text, x + this.chromaticOffset, y, scale, toARGB(new Color(50, 50, 255, alpha)), bassLevel, trebleLevel);
      } else {
         this.drawWarpedTextColor(context, text, x, y, scale, color, bassLevel, trebleLevel);
      }
   }

   private void drawWarpedTextColor(DrawContext context, String text, float x, float y, float scale, int color, float bassLevel, float trebleLevel) {
      for (int i = 0; i < text.length(); i++) {
         String ch = String.valueOf(text.charAt(i));
         float charX = x + i * (this.client.textRenderer.getWidth("X") * scale * 0.9F);
         float ox = 0.0F;
         float oy = 0.0F;

         for (int j = 0; j < 3; j++) {
            float posX = (float)i / text.length();
            float dx = posX - this.warpX[j];
            float dy = 0.5F - this.warpY[j];
            float dist = (float)Math.sqrt(dx * dx + dy * dy);
            float inf = (1.0F - Math.min(1.0F, dist * 2.0F)) * this.warpAmplitude[j];
            ox += (float)Math.sin(this.warpPhase[j] + i * 0.2F) * inf * 3.0F + (float)Math.sin(this.animationTime * 3.0F + i * 0.8F) * trebleLevel * 1.5F;
            oy += (float)Math.cos(this.warpPhase[j] + i * 0.3F) * inf * 3.0F + (float)Math.sin(this.animationTime * 1.5 + i * 0.15F) * bassLevel * 2.0F;
         }

         int charColor = color;
         if (trebleLevel > 0.5F) {
            float boost = (trebleLevel - 0.5F) * 2.0F;
            int r = Math.min(255, (color >> 16 & 0xFF) + (int)(boost * 80.0F));
            int g = Math.min(255, (color >> 8 & 0xFF) + (int)(boost * 80.0F));
            int b = Math.min(255, (color & 0xFF) + (int)(boost * 80.0F));
            charColor = color & 0xFF000000 | r << 16 | g << 8 | b;
         }

         context.getMatrices().pushMatrix();
         context.getMatrices().translate(charX + ox, y + oy);
         context.getMatrices().scale(scale, scale);
         context.drawText(this.client.textRenderer, ch, 0, 0, charColor, false);
         context.getMatrices().popMatrix();
      }
   }

   private void updateParticles(float delta, float bassLevel, float trebleLevel) {
      this.particleSpawnCounter++;
      int rate = Math.max(3, (int)(40.0F - Math.min(28.0F, (bassLevel + trebleLevel) * 14.0F)));
      if (this.particleSpawnCounter % rate == 0 && this.particles.size() < 60) {
         float speedF = 0.5F + bassLevel * 1.6F;
         float angle = this.random.nextFloat() * (float) Math.PI * 2.0F;
         float speed = (0.3F + this.random.nextFloat() * 1.2F) * speedF;
         Color c = bassLevel > trebleLevel
            ? new Color(
               this.ACCENT.getRed(), (int)(this.ACCENT.getGreen() * 0.7F), (int)(this.ACCENT.getBlue() * 0.5F), 120 + (int)(this.random.nextFloat() * 80.0F)
            )
            : new Color(
               (int)(this.ACCENT.getRed() * 0.5F), (int)(this.ACCENT.getGreen() * 0.7F), this.ACCENT.getBlue(), 120 + (int)(this.random.nextFloat() * 80.0F)
            );
         CustomTitleScreen.MusicParticle p = new CustomTitleScreen.MusicParticle(
            this.width / 2.0F + (this.random.nextFloat() * 2.0F - 1.0F) * 120.0F,
            this.height / 4.0F + (this.random.nextFloat() * 2.0F - 1.0F) * 72.0F,
            1.0F + this.random.nextFloat() * 2.0F + bassLevel * 3.0F,
            c
         );
         p.vx = (float)Math.cos(angle) * speed;
         p.vy = (float)Math.sin(angle) * speed;
         this.particles.add(p);
      }

      for (int i = this.particles.size() - 1; i >= 0; i--) {
         CustomTitleScreen.MusicParticle p = this.particles.get(i);
         p.x = p.x + p.vx * delta;
         p.y = p.y + p.vy * delta;
         float dx = this.width / 2.0F - p.x;
         float dy = this.height / 4.0F - p.y;
         float dist = (float)Math.sqrt(dx * dx + dy * dy);
         if (dist > 5.0F) {
            p.vx += dx / dist * bassLevel * 0.3F * delta * 0.5F;
            p.vy += dy / dist * bassLevel * 0.3F * delta * 0.5F;
         }

         p.vx = p.vx + (this.random.nextFloat() * 2.0F - 1.0F) * trebleLevel * 0.2F * delta * 0.5F;
         p.vy = p.vy + (this.random.nextFloat() * 2.0F - 1.0F) * trebleLevel * 0.2F * delta * 0.5F;
         p.vx *= 0.99F;
         p.vy *= 0.99F;
         p.life -= delta * (0.15F + (bassLevel + trebleLevel) * 0.15F);
         if (p.life <= 0.0F || p.x < -20.0F || p.x > this.width + 20 || p.y < -20.0F || p.y > this.height + 20) {
            this.particles.remove(i);
         }
      }
   }

   private void drawParticles(DrawContext context) {
      for (CustomTitleScreen.MusicParticle p : this.particles) {
         int alpha = (int)(p.color.getAlpha() * p.life);
         float size = p.size * p.life;
         context.fill(
            (int)(p.x - size / 2.0F),
            (int)(p.y - size / 2.0F),
            (int)(p.x + size / 2.0F),
            (int)(p.y + size / 2.0F),
            toARGB(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha))
         );
      }
   }

   private void drawVersionText(DrawContext context) {
      String version = "R  E  V  I  V  E      PRE-RELEASE-0.0.2";
      context.drawTextWithShadow(this.client.textRenderer, version, 5, this.height - 15, -1);
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      return this.musicControls != null && this.musicControls.mouseClicked(mouseX, mouseY, button) ? true : super.mouseClicked(click, doubled);
   }

   public boolean mouseDragged(Click click, double offsetX, double offsetY) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      return this.musicControls != null && this.musicControls.mouseDragged(mouseX, mouseY, button, offsetX, offsetY)
         ? true
         : super.mouseDragged(click, offsetX, offsetY);
   }

   public boolean mouseReleased(Click click) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      return this.musicControls != null && this.musicControls.mouseReleased(mouseX, mouseY, button) ? true : super.mouseReleased(click);
   }

   public void removed() {
      TitleMusicPlayer.getInstance().stop();
      super.removed();
   }

   public void tick() {
      super.tick();
      if (this.client != null && this.client.currentScreen != this && TitleMusicPlayer.getInstance().isMenuActive()) {
         TitleMusicPlayer.getInstance().stop();
      }
   }

   public boolean shouldCloseOnEsc() {
      return true;
   }

   private static int toARGB(Color c) {
      return (c.getAlpha() & 0xFF) << 24 | (c.getRed() & 0xFF) << 16 | (c.getGreen() & 0xFF) << 8 | c.getBlue() & 0xFF;
   }

   private static int clamp(int v) {
      return Math.max(0, Math.min(255, v));
   }

   private static class DeveloperInfo {
      String name;
      Color color;

      DeveloperInfo(String name, Color color) {
         this.name = name;
         this.color = color;
      }
   }

   private static class Droplet {
      float x;
      float y;
      float size;
      float speed;
      Color color;

      Droplet(float x, float y, float size, float speed, Color color) {
         this.x = x;
         this.y = y;
         this.size = size;
         this.speed = speed;
         this.color = color;
      }
   }

   private static class MusicParticle {
      float x;
      float y;
      float vx;
      float vy;
      float size;
      float life = 1.0F;
      Color color;

      MusicParticle(float x, float y, float size, Color color) {
         this.x = x;
         this.y = y;
         this.size = size;
         this.color = color;
      }
   }

   private class ThemedButton extends ClickableWidget {
      private final Runnable onPress;

      ThemedButton(int x, int y, int w, int h, Text message, Runnable onPress) {
         super(x, y, w, h, message);
         this.onPress = onPress;
      }

      protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
         int x = this.getX();
         int y = this.getY();
         int w = this.getWidth();
         int h = this.getHeight();
         FontManager fm = FontManager.get();
         int rgb = fm.getSecondaryColor() & 16777215;
         int r = rgb >> 16 & 0xFF;
         int g = rgb >> 8 & 0xFF;
         int b = rgb & 0xFF;
         int baseBg = -939524096 | (int)(r * 0.55F) << 16 | (int)(g * 0.55F) << 8 | (int)(b * 0.55F);
         int hoverBg = -603979776 | Math.min(255, (int)(r * 0.85F)) << 16 | Math.min(255, (int)(g * 0.85F)) << 8 | Math.min(255, (int)(b * 0.85F));
         int bgColor = this.isHovered() ? hoverBg : baseBg;
         int borderColor = 0xFF000000 | rgb;
         context.fill(x, y, x + w, y + h, bgColor);
         context.fill(x, y, x + w, y + 1, borderColor);
         context.fill(x, y + h - 1, x + w, y + h, borderColor);
         context.fill(x, y, x + 1, y + h, borderColor);
         context.fill(x + w - 1, y, x + w, y + h, borderColor);
         context.drawCenteredTextWithShadow(CustomTitleScreen.this.client.textRenderer, this.getMessage(), x + w / 2, y + (h - 8) / 2, fm.getTextColor());
      }

      public boolean mouseClicked(Click click, boolean doubled) {
         if (this.active && this.visible && click.button() == 0 && this.isMouseOver(click.x(), click.y())) {
            this.onPress.run();
            return true;
         } else {
            return false;
         }
      }

      protected void appendClickableNarrations(NarrationMessageBuilder builder) {
         this.appendDefaultNarrations(builder);
      }
   }
}
