package revivedevelopment.reviveclient.gui.titlemenu;

import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.util.animation.TitleAnimation;

public class TitleMusicControls {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private final Screen parentScreen;
   private final int posX = 10;
   private int posY;
   private final int width = 24;
   private final int height = 40;
   private final int skipButtonX = 10;
   private int skipButtonY;
   private final TitleMusicControls.PixelButton muteButton;
   private final TitleMusicControls.PixelButton skipButton;
   private final TitleMusicControls.VolumeSlider volumeSlider;
   private final TitleAnimation hoverAnimation = new TitleAnimation(150L, 0.0F, 1.0F, TitleAnimation.Easing.EASE_OUT_CUBIC);
   private boolean isHovered = false;
   private float alpha = 0.7F;
   private boolean isMuted = false;
   private float volume = 0.6F;
   private Color ACCENT = new Color(255, 105, 190);
   private final String[] musicTracks = TitleMusicPlayer.getTrackPaths();
   private int currentTrackIndex = 0;

   private static Color themeAccent() {
      int s = FontManager.get().getSecondaryColor();
      return new Color(s >> 16 & 0xFF, s >> 8 & 0xFF, s & 0xFF);
   }

   public TitleMusicControls(Screen parentScreen) {
      this.parentScreen = parentScreen;
      this.posY = parentScreen.height / 2 - 20;
      this.skipButtonY = parentScreen.height - 50;
      this.muteButton = new TitleMusicControls.PixelButton(10, this.posY, 24, 24, "MUTE");
      this.skipButton = new TitleMusicControls.PixelButton(10, this.skipButtonY, 24, 24, "SKIP");
      this.volumeSlider = new TitleMusicControls.VolumeSlider(10, this.posY + 24 + 5, 24, 6);
      this.volumeSlider.setValue(this.volume);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.ACCENT = themeAccent();
      int baseX = 20;
      int baseY = this.parentScreen.height / 2 - 40;
      int spacing = 28;
      this.muteButton.x = baseX + 60;
      this.muteButton.y = baseY + 108;
      this.volumeSlider.x = baseX + 30;
      this.volumeSlider.y = baseY + spacing + 90;
      this.skipButton.x = baseX - 10;
      this.skipButton.y = baseY + spacing + 80;
      boolean newHovered = this.isMouseOver(mouseX, mouseY);
      if (newHovered != this.isHovered) {
         this.isHovered = newHovered;
         this.hoverAnimation.reset();
      }

      this.hoverAnimation.update(delta);
      float hp = this.hoverAnimation.getValue();
      this.alpha = this.isHovered ? 0.7F + 0.3F * hp : 0.5F - 0.2F * hp;
      this.muteButton.render(context, mouseX, mouseY, delta);
      this.volumeSlider.render(context, mouseX, mouseY, delta);
      this.skipButton.render(context, mouseX, mouseY, delta);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         if (this.muteButton.isMouseOver((int)mouseX, (int)mouseY)) {
            this.toggleMute();
            return true;
         }

         if (this.skipButton.isMouseOver((int)mouseX, (int)mouseY)) {
            this.skipTrack();
            return true;
         }

         if (this.volumeSlider.mouseClicked(mouseX, mouseY)) {
            return true;
         }
      }

      return false;
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (button == 0 && this.volumeSlider.mouseDragged(mouseX, mouseY)) {
         this.volume = this.volumeSlider.getValue();
         this.updateVolume();
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0) {
         this.volumeSlider.mouseReleased();
      }

      return false;
   }

   private boolean isMouseOver(int mouseX, int mouseY) {
      boolean main = mouseX >= 7 && mouseX <= 37 && mouseY >= this.posY - 3 && mouseY <= this.posY + 40 + 3;
      boolean skip = mouseX >= 7 && mouseX <= 37 && mouseY >= this.skipButtonY - 3 && mouseY <= this.skipButtonY + 24 + 3;
      return main || skip;
   }

   private void toggleMute() {
      this.isMuted = !this.isMuted;
      TitleMusicPlayer.getInstance().setVolume(this.isMuted ? 0.0F : this.volume);
      this.muteButton.setText(this.isMuted ? "UNMUTE" : "MUTE");
   }

   private void updateVolume() {
      if (!this.isMuted) {
         TitleMusicPlayer.getInstance().setVolume(this.volume);
      }
   }

   private void skipTrack() {
      this.currentTrackIndex = (this.currentTrackIndex + 1) % this.musicTracks.length;
      TitleMusicPlayer.getInstance().playFromResource(this.musicTracks[this.currentTrackIndex]);
   }

   private static int toARGB(Color c) {
      return (c.getAlpha() & 0xFF) << 24 | (c.getRed() & 0xFF) << 16 | (c.getGreen() & 0xFF) << 8 | c.getBlue() & 0xFF;
   }

   private class PixelButton {
      int x;
      int y;
      int width;
      int height;
      String text;
      boolean isHovered = false;
      TitleAnimation pressAnimation = new TitleAnimation(150L, 0.0F, 1.0F, TitleAnimation.Easing.EASE_OUT_CUBIC);

      PixelButton(int x, int y, int width, int height, String text) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.text = text;
      }

      void render(DrawContext context, int mouseX, int mouseY, float delta) {
         boolean wasHovered = this.isHovered;
         this.isHovered = this.isMouseOver(mouseX, mouseY);
         if (wasHovered != this.isHovered) {
            this.pressAnimation.reset();
         }

         this.pressAnimation.update(delta);
         float progress = this.pressAnimation.getValue();
         Color base = TitleMusicControls.this.ACCENT;
         Color hover = new Color(Math.min(255, base.getRed() + 40), Math.min(255, base.getGreen() + 40), Math.min(255, base.getBlue() + 40));
         if (this.isHovered) {
            new Color(
               base.getRed() + (int)((hover.getRed() - base.getRed()) * progress),
               base.getGreen() + (int)((hover.getGreen() - base.getGreen()) * progress),
               base.getBlue() + (int)((hover.getBlue() - base.getBlue()) * progress),
               (int)(255.0F * TitleMusicControls.this.alpha)
            );
         } else {
            new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(180.0F * TitleMusicControls.this.alpha));
         }

         context.fill(
            this.x,
            this.y,
            this.x + this.width,
            this.y + this.height,
            TitleMusicControls.toARGB(new Color(25, 25, 35, (int)(180.0F * TitleMusicControls.this.alpha)))
         );
         if (this.isHovered) {
            context.fill(this.x - 1, this.y - 1, this.x + this.width + 1, this.y, TitleMusicControls.toARGB(TitleMusicControls.this.ACCENT));
            context.fill(
               this.x - 1, this.y + this.height, this.x + this.width + 1, this.y + this.height + 1, TitleMusicControls.toARGB(TitleMusicControls.this.ACCENT)
            );
         }

         if (this.text.equals("MUTE") || this.text.equals("UNMUTE")) {
            this.drawMuteIcon(context, TitleMusicControls.this.isMuted);
         } else if (this.text.equals("SKIP")) {
            this.drawSkipIcon(context);
         }
      }

      void drawPixelatedButton(DrawContext context, Color color) {
         context.fill(this.x, this.y, this.x + this.width, this.y + this.height, TitleMusicControls.toARGB(color));
         Color light = new Color(
            Math.min(255, color.getRed() + 70), Math.min(255, color.getGreen() + 70), Math.min(255, color.getBlue() + 90), color.getAlpha()
         );
         Color dark = new Color(Math.max(0, color.getRed() - 70), Math.max(0, color.getGreen() - 70), Math.max(0, color.getBlue() - 50), color.getAlpha());
         context.fill(this.x, this.y, this.x + this.width, this.y + 1, TitleMusicControls.toARGB(light));
         context.fill(this.x, this.y + 1, this.x + 1, this.y + this.height - 1, TitleMusicControls.toARGB(light));
         context.fill(this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, TitleMusicControls.toARGB(dark));
         context.fill(this.x + this.width - 1, this.y + 1, this.x + this.width, this.y + this.height - 1, TitleMusicControls.toARGB(dark));
      }

      void drawMuteIcon(DrawContext context, boolean muted) {
         int is = 14;
         int sx = this.x + (this.width - is) / 2;
         int sy = this.y + (this.height - is) / 2;
         context.fill(sx + 2, sy + 4, sx + 5, sy + 10, TitleMusicControls.toARGB(new Color(220, 220, 220)));
         context.fill(sx + 3, sy + 5, sx + 4, sy + 9, TitleMusicControls.toARGB(new Color(150, 150, 150)));
         context.fill(sx + 3, sy + 6, sx + 4, sy + 8, TitleMusicControls.toARGB(new Color(50, 50, 50)));

         for (int i = 0; i < 3; i++) {
            context.fill(sx + 5, sy + 4 + i, sx + 8 - i, sy + 5 + i, TitleMusicControls.toARGB(new Color(200, 200, 200)));
         }

         for (int i = 0; i < 3; i++) {
            context.fill(sx + 5, sy + 9 - i, sx + 8 - i, sy + 10 - i, TitleMusicControls.toARGB(new Color(200, 200, 200)));
         }

         if (!muted) {
            for (int i = 0; i < 4; i++) {
               context.fill(
                  sx + 8 + i,
                  sy + 5 + i / 2,
                  sx + 9 + i,
                  sy + 9 - i / 2,
                  TitleMusicControls.toARGB(
                     new Color(
                        TitleMusicControls.this.ACCENT.getRed(),
                        TitleMusicControls.this.ACCENT.getGreen(),
                        TitleMusicControls.this.ACCENT.getBlue(),
                        (int)(255.0F * (1.0F - i / 7.0F))
                     )
                  )
               );
            }

            for (int i = 0; i < 4; i++) {
               context.fill(
                  sx + 10 + i,
                  sy + 4 + i / 2,
                  sx + 11 + i,
                  sy + 10 - i / 2,
                  TitleMusicControls.toARGB(new Color(100, 170, 255, (int)(200.0F * (1.0F - i / 7.0F))))
               );
            }
         } else {
            for (int i = 0; i < 7; i++) {
               context.fill(sx + 8 + i, sy + 3 + i, sx + 9 + i, sy + 4 + i, TitleMusicControls.toARGB(new Color(255, 70, 70)));
            }

            for (int i = 0; i < 7; i++) {
               context.fill(sx + 8 + i, sy + 10 - i, sx + 9 + i, sy + 11 - i, TitleMusicControls.toARGB(new Color(255, 50, 50)));
            }
         }
      }

      void drawSkipIcon(DrawContext context) {
         int is = 14;
         int sx = this.x + (this.width - is) / 2;
         int sy = this.y + (this.height - is) / 2;
         context.fill(sx + 2, sy + 3, sx + 3, sy + 11, -1);
         context.fill(sx + 2, sy + 3, sx + 7, sy + 7, -1);
         context.fill(sx + 2, sy + 11, sx + 7, sy + 7, -1);
         context.fill(sx + 7, sy + 3, sx + 8, sy + 11, -1);
         context.fill(sx + 7, sy + 3, sx + 12, sy + 7, -1);
         context.fill(sx + 7, sy + 11, sx + 12, sy + 7, -1);
         context.fill(sx + 12, sy + 3, sx + 13, sy + 11, -1);
      }

      boolean isMouseOver(int mx, int my) {
         return mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height;
      }

      void setText(String t) {
         this.text = t;
      }
   }

   private class VolumeSlider {
      int x;
      int y;
      int width;
      int height;
      float value = 0.6F;
      boolean isDragging = false;
      TitleAnimation hoverAnim = new TitleAnimation(150L, 0.0F, 1.0F, TitleAnimation.Easing.EASE_OUT_CUBIC);
      boolean isHovered = false;

      VolumeSlider(int x, int y, int width, int height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }

      void render(DrawContext context, int mouseX, int mouseY, float delta) {
         boolean wasHovered = this.isHovered;
         this.isHovered = this.isMouseOver(mouseX, mouseY) || this.isDragging;
         int panelX = this.x - 10;
         int panelY = this.y - 20;
         int panelW = this.width + 20;
         int panelH = this.height + 40;
         int barY = this.y + this.height / 2;
         context.fill(
            this.x, barY - 2, this.x + this.width, barY + 2, TitleMusicControls.toARGB(new Color(20, 20, 30, (int)(200.0F * TitleMusicControls.this.alpha)))
         );
         int fillX = this.x + (int)(this.value * this.width);
         context.fill(this.x, barY - 2, fillX, barY + 2, TitleMusicControls.toARGB(TitleMusicControls.this.ACCENT));
         if (wasHovered != this.isHovered) {
            this.hoverAnim.reset();
         }

         this.hoverAnim.update(delta);
         float p = this.hoverAnim.getValue();
         int centerX = this.x + this.width / 2;
         context.fill(
            centerX - 1,
            this.y,
            centerX + 1,
            this.y + this.height,
            TitleMusicControls.toARGB(new Color(255, 255, 255, (int)(80.0F * TitleMusicControls.this.alpha)))
         );
         int fillY = this.y + this.height - (int)(this.value * this.height);
         context.fill(
            centerX - 1,
            fillY,
            centerX + 1,
            this.y + this.height,
            TitleMusicControls.toARGB(
               new Color(
                  TitleMusicControls.this.ACCENT.getRed(),
                  TitleMusicControls.this.ACCENT.getGreen(),
                  TitleMusicControls.this.ACCENT.getBlue(),
                  (int)(220.0F * TitleMusicControls.this.alpha)
               )
            )
         );
         if (this.isHovered) {
            Color glow = new Color(
               TitleMusicControls.this.ACCENT.getRed(), TitleMusicControls.this.ACCENT.getGreen(), TitleMusicControls.this.ACCENT.getBlue(), (int)(80.0F * p)
            );
            context.fill(centerX - 4, fillY - 2, centerX + 4, fillY + 2, TitleMusicControls.toARGB(glow));
         }

         int knobX = this.x + (int)(this.value * this.width);
         context.fill(knobX - 4, barY - 4, knobX + 4, barY + 4, TitleMusicControls.toARGB(new Color(20, 20, 20, 255)));
         context.fill(knobX - 3, barY - 3, knobX + 3, barY + 3, TitleMusicControls.toARGB(new Color(200, 200, 200, 255)));
      }

      boolean mouseClicked(double mx, double my) {
         if (this.isMouseOver((int)mx, (int)my)) {
            this.isDragging = true;
            this.updateFromMouse(mx);
            return true;
         } else {
            return false;
         }
      }

      boolean mouseDragged(double mx, double my) {
         if (this.isDragging) {
            this.updateFromMouse(mx);
            return true;
         } else {
            return false;
         }
      }

      void mouseReleased() {
         this.isDragging = false;
      }

      private void updateFromMouse(double mx) {
         this.value = (float)Math.max(0.0, Math.min(1.0, (mx - this.x) / this.width));
      }

      boolean isMouseOver(int mx, int my) {
         return mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height;
      }

      float getValue() {
         return this.value;
      }

      void setValue(float v) {
         this.value = Math.max(0.0F, Math.min(1.0F, v));
      }
   }
}
