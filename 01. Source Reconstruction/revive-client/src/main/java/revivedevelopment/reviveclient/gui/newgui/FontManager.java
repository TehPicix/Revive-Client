package revivedevelopment.reviveclient.gui.newgui;

import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.StyleSpriteSource.Font;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import revivedevelopment.reviveclient.ReviveClient;

public class FontManager {
   private static FontManager instance;
   private float scale = 0.5F;
   private float guiScale = 0.8F;
   private FontManager.MenuFont font = FontManager.MenuFont.Comfortaa;
   private FontManager.MenuFontStyle fontStyle = FontManager.MenuFontStyle.Regular;
   private int primaryColor = -38466;
   private int secondaryColor = -38466;
   private int textColor = -1;
   private int textSecondary = -1322271;
   private int hudTextPrimary = -1;
   private int hudTextSecondary = -5197648;
   private boolean hudTextBackground = true;
   private boolean blur = true;
   private boolean textShadow = false;
   private static boolean fontsAudited = false;
   private static final int BASELINE = 7;
   private static final Matrix3x2f ITALIC_SHEAR = new Matrix3x2f(1.0F, 0.0F, -0.25F, 1.0F, 0.0F, 0.0F);
   private final WeakHashMap<Object, Long> marqueeStart = new WeakHashMap<>();

   public static FontManager get() {
      if (instance == null) {
         instance = new FontManager();
      }

      return instance;
   }

   public float getScale() {
      return this.scale;
   }

   public void setScale(float scale) {
      this.scale = Math.max(0.3F, Math.min(1.5F, scale));
   }

   public float getGuiScale() {
      return this.guiScale;
   }

   public void setGuiScale(float guiScale) {
      this.guiScale = Math.max(0.4F, Math.min(2.0F, guiScale));
   }

   public int getRowHeight() {
      return (int)(12.0F * this.guiScale);
   }

   public int getHeaderHeight() {
      return (int)(12.0F * this.guiScale);
   }

   public int getPrimaryColor() {
      return this.primaryColor;
   }

   public void setPrimaryColor(int color) {
      this.primaryColor = color;
   }

   public int getSecondaryColor() {
      return this.secondaryColor;
   }

   public void setSecondaryColor(int color) {
      this.secondaryColor = color;
   }

   public int getTextColor() {
      return this.textColor;
   }

   public void setTextColor(int color) {
      this.textColor = color;
   }

   public int getTextSecondary() {
      return this.textSecondary;
   }

   public void setTextSecondary(int color) {
      this.textSecondary = color;
   }

   public int getHudTextPrimary() {
      return this.hudTextPrimary;
   }

   public void setHudTextPrimary(int color) {
      this.hudTextPrimary = color;
   }

   public int getHudTextSecondary() {
      return this.hudTextSecondary;
   }

   public void setHudTextSecondary(int color) {
      this.hudTextSecondary = color;
   }

   public boolean isHudTextBackground() {
      return this.hudTextBackground;
   }

   public void setHudTextBackground(boolean b) {
      this.hudTextBackground = b;
   }

   public boolean isBlur() {
      return this.blur;
   }

   public void setBlur(boolean blur) {
      this.blur = blur;
   }

   public boolean isTextShadow() {
      return this.textShadow;
   }

   public void setTextShadow(boolean textShadow) {
      this.textShadow = textShadow;
   }

   public int primaryAlpha(int alpha) {
      int userA = this.primaryColor >> 24 & 0xFF;
      int finalA = userA * (alpha & 0xFF) / 255;
      return finalA << 24 | this.primaryColor & 16777215;
   }

   public int secondaryAlpha(int alpha) {
      int userA = this.secondaryColor >> 24 & 0xFF;
      int finalA = userA * (alpha & 0xFF) / 255;
      return finalA << 24 | this.secondaryColor & 16777215;
   }

   public FontManager.MenuFont getFont() {
      return this.font;
   }

   public void setFont(FontManager.MenuFont font) {
      if (font != null) {
         if (this.font != font) {
            ReviveClient.LOG.info("[ClickGui] font -> {}", font);
         }

         this.font = font;
      }
   }

   public FontManager.MenuFontStyle getFontStyle() {
      return this.fontStyle;
   }

   public void setFontStyle(FontManager.MenuFontStyle style) {
      if (style != null) {
         if (this.fontStyle != style) {
            ReviveClient.LOG.info("[ClickGui] style -> {}", style);
         }

         this.fontStyle = style;
      }
   }

   public void auditFonts() {
      if (!fontsAudited) {
         fontsAudited = true;
         ResourceManager rm = MinecraftClient.getInstance().getResourceManager();

         for (FontManager.MenuFont f : FontManager.MenuFont.values()) {
            if (f.id != null) {
               Identifier json = Identifier.of(f.id.getNamespace(), "font/" + f.id.getPath() + ".json");
               boolean found = rm.getResource(json).isPresent();
               ReviveClient.LOG.info("[ClickGui] font provider {} ({}): {}", new Object[]{f, json, found ? "FOUND" : "MISSING"});
            }
         }

         ReviveClient.LOG.info("[ClickGui] active font={} style={} scale={}", new Object[]{this.font, this.fontStyle, this.scale});
      }
   }

   public String getFontName() {
      return this.font.toString();
   }

   public void cycleFont() {
      FontManager.MenuFont[] values = FontManager.MenuFont.values();
      this.font = values[(this.font.ordinal() + 1) % values.length];
   }

   private boolean isPlain() {
      return this.font.id == null;
   }

   private String meshFontResource() {
      if (this.font.id == null) {
         return null;
      } else {
         return !"revive-client".equals(this.font.id.getNamespace()) ? null : "/assets/revive-client/font/" + this.font.id.getPath() + ".ttf";
      }
   }

   private int meshVanillaWidth(String res, String text) {
      float sf = MinecraftClient.getInstance().getWindow().getScaleFactor();
      int h = Math.max(1, Math.round(9.0F * sf));
      return (int)Math.round(GuiTextMesh.width(res, text, h) / sf);
   }

   private int baseWidth(String text) {
      String res = this.meshFontResource();
      if (res != null && !this.fontStyle.italic() && GuiTextMesh.canRender(res, text)) {
         return this.meshVanillaWidth(res, text);
      } else {
         TextRenderer tr = MinecraftClient.getInstance().textRenderer;
         return this.isPlain() ? tr.getWidth(text) : tr.getWidth(this.styledText(text));
      }
   }

   public Text styledText(String text) {
      return this.font.id == null ? Text.literal(text) : Text.literal(text).setStyle(Style.EMPTY.withFont(new Font(this.font.id)));
   }

   public int getGuiTextWidth(String text) {
      int w = this.baseWidth(text);
      if (this.fontStyle.bold()) {
         w++;
      }

      return (int)(w * this.guiScale);
   }

   public int getTextWidth(String text) {
      int w = this.baseWidth(text);
      if (this.fontStyle.bold()) {
         w++;
      }

      return (int)(w * this.scale);
   }

   public int getTextHeight() {
      return (int)(8.0F * this.scale);
   }

   public void drawText(DrawContext context, String text, int x, int y, int color) {
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(this.scale, this.scale);
      float invScale = 1.0F / this.scale;
      this.drawStyled(context, text, Math.round(x * invScale), Math.round(y * invScale), color);
      matrices.popMatrix();
   }

   public int menuTextWidth(String text) {
      int w = this.baseWidth(text);
      if (this.fontStyle.bold()) {
         w++;
      }

      return w;
   }

   public void drawStyled(DrawContext context, String text, int x, int y, int color) {
      if (text != null && !text.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         String res = this.meshFontResource();
         if (res != null && !this.fontStyle.italic() && GuiTextMesh.canRender(res, text)) {
            Matrix3x2fStack m = context.getMatrices();
            float sf = mc.getWindow().getScaleFactor();
            float ms = Math.abs(m.m00());
            float nx = (m.m00() * x + m.m20()) * sf;
            float ny = (m.m11() * y + m.m21()) * sf;
            int h = Math.max(1, Math.round(9.0F * ms * sf));
            int col = color >>> 24 == 0 ? 0xFF000000 | color : color;
            if (GuiTextMesh.queue(res, text, nx, ny, h, col)) {
               if (this.fontStyle.bold()) {
                  GuiTextMesh.queue(res, text, nx + Math.max(1.0F, ms * sf), ny, h, col);
               }

               return;
            }
         }

         TextRenderer tr = mc.textRenderer;
         Matrix3x2fStack matrices = context.getMatrices();
         matrices.pushMatrix();
         if (this.fontStyle.italic()) {
            matrices.translate(x, y + 7);
            matrices.mul(ITALIC_SHEAR);
            x = 0;
            y = -7;
         }

         this.drawPass(context, tr, text, x, y, color);
         if (this.fontStyle.bold()) {
            float onScreenScale = Math.abs(matrices.m00()) * mc.getWindow().getScaleFactor();
            matrices.translate(1.0F / Math.max(0.05F, onScreenScale), 0.0F);
            this.drawPass(context, tr, text, x, y, color);
         }

         matrices.popMatrix();
      }
   }

   private void drawPass(DrawContext context, TextRenderer tr, String text, int x, int y, int color) {
      if (this.isPlain()) {
         if (this.textShadow) {
            context.drawTextWithShadow(tr, text, x, y, color);
         } else {
            context.drawText(tr, text, x, y, color, false);
         }
      } else {
         Text styled = this.styledText(text);
         if (this.textShadow) {
            context.drawTextWithShadow(tr, styled, x, y, color);
         } else {
            context.drawText(tr, styled, x, y, color, false);
         }
      }
   }

   public void drawTextMarquee(DrawContext context, Object id, String text, int textStartX, int y, int textEndX, int color, boolean hovered) {
      int textW = this.getTextWidth(text);
      int avail = textEndX - textStartX;
      if (!hovered) {
         this.marqueeStart.remove(id);
         this.drawText(context, text, textStartX, y, color);
      } else if (textW <= avail) {
         this.drawText(context, text, textStartX, y, color);
      } else {
         long now = System.currentTimeMillis();
         Long start = this.marqueeStart.get(id);
         if (start == null) {
            this.marqueeStart.put(id, now);
            start = now;
         }

         int overflow = textW - avail;
         float pauseSec = 1.5F;
         float pxPerSec = 30.0F;
         float scrollSec = overflow / pxPerSec;
         float cycleSec = pauseSec + scrollSec + pauseSec + scrollSec;
         float elapsed = (float)(now - start) / 1000.0F;
         float t = elapsed % cycleSec;
         float offset;
         if (t < pauseSec) {
            offset = 0.0F;
         } else if (t < pauseSec + scrollSec) {
            offset = -((t - pauseSec) * pxPerSec);
         } else if (t < pauseSec + scrollSec + pauseSec) {
            offset = -overflow;
         } else {
            offset = -overflow + (t - pauseSec - scrollSec - pauseSec) * pxPerSec;
         }

         this.drawText(context, text, textStartX + (int)offset, y, color);
      }
   }

   public void drawTextMarquee(DrawContext context, String text, int textStartX, int y, int textEndX, int color, boolean hovered) {
      this.drawTextMarquee(context, text, text, textStartX, y, textEndX, color, hovered);
   }

   public static enum MenuFont {
      Default(null),
      Uniform(Identifier.of("minecraft", "uniform")),
      Comfortaa(Identifier.of("revive-client", "comfortaa")),
      Inter(Identifier.of("revive-client", "inter")),
      Lexend(Identifier.of("revive-client", "lexend")),
      JetBrainsMono(Identifier.of("revive-client", "jetbrains_mono")),
      TwCenMT(Identifier.of("revive-client", "tw_cen_mt")),
      Pixelation(Identifier.of("revive-client", "pixelation"));

      public final Identifier id;

      private MenuFont(Identifier id) {
         this.id = id;
      }

      @Override
      public String toString() {
         return switch (this) {
            case JetBrainsMono -> "JetBrains Mono";
            case TwCenMT -> "Tw Cen MT";
            default -> this.name();
         };
      }
   }

   public static enum MenuFontStyle {
      Regular,
      Bold,
      Italic,
      BoldItalic;

      public boolean bold() {
         return this == Bold || this == BoldItalic;
      }

      public boolean italic() {
         return this == Italic || this == BoldItalic;
      }

      @Override
      public String toString() {
         return this == BoldItalic ? "Bold Italic" : this.name();
      }
   }
}
