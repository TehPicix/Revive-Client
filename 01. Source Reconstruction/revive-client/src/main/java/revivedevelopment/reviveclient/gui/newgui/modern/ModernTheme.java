package revivedevelopment.reviveclient.gui.newgui.modern;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.StyleSpriteSource.Font;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import revivedevelopment.reviveclient.gui.newgui.GuiTextMesh;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public final class ModernTheme {
   public int win;
   public int winBorder;
   public int card;
   public int cardHover;
   public int cardActive;
   public int field;
   public int fieldBorder;
   public int line;
   public int lineStrong;
   public int text;
   public int text2;
   public int text3;
   public int accent;
   public int onTrack;
   public int onKnob;
   public int offTrack;
   public int offKnob;
   public int pillBg;
   public int scrim;
   public int shadow;
   public boolean accentControls;
   public float s = 1.0F;
   public float alpha = 1.0F;
   public float textFactor = 1.0F;
   public static final float T_LG = 17.0F;
   public static final float T_MD = 13.0F;
   public static final float T_SM = 11.5F;
   public static final float T_XS = 10.0F;
   public static final float T_XXS = 9.0F;
   private static final int[] PX = new int[]{8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 26, 28, 32};
   private static final Identifier[] FONT_IDS = new Identifier[PX.length];
   private static final String MESH_FONT = "/assets/revive-client/font/inter.ttf";
   private static final float ASCENT = 0.95F;
   private static final float CAP = 0.727F;
   private final MinecraftClient mc = MinecraftClient.getInstance();

   public void refresh(Gui gui, float scale, float textFactor) {
      this.s = scale;
      this.textFactor = textFactor;
      int w = rgb(gui.modernWindow.get());
      int c = rgb(gui.modernCard.get());
      int tx = rgb(gui.modernText.get());
      int t2 = rgb(gui.modernTextDim.get());
      int ac = rgb(gui.modernAccent.get());
      this.accentControls = gui.accentOnControls.get();
      this.win = withA(w, 248);
      this.winBorder = withA(16777215, 18);
      this.card = c;
      this.cardHover = lift(c, 7);
      this.cardActive = lift(c, 11);
      this.field = lift(w, 12);
      this.fieldBorder = withA(16777215, 15);
      this.line = withA(16777215, 15);
      this.lineStrong = withA(16777215, 40);
      this.text = tx;
      this.text2 = t2;
      this.text3 = mix(t2, w, 0.35F);
      this.accent = ac;
      this.onTrack = this.accentControls ? ac : tx;
      this.onKnob = this.accentControls ? -1 : mix(w, 0, 0.4F);
      this.offTrack = lift(w, 31);
      this.offKnob = mix(t2, w, 0.25F);
      this.pillBg = lift(w, 20);
      this.scrim = withA(0, 90);
      this.shadow = withA(0, 110);
   }

   public static int rgb(SettingColor c) {
      return 0xFF000000 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
   }

   public static int withA(int argb, int a) {
      return (a & 0xFF) << 24 | argb & 16777215;
   }

   public static int lift(int argb, int d) {
      int r = clamp255((argb >> 16 & 0xFF) + d);
      int g = clamp255((argb >> 8 & 0xFF) + d);
      int b = clamp255((argb & 0xFF) + d);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   public static int mix(int a, int b, float t) {
      int ra = a >> 16 & 0xFF;
      int ga = a >> 8 & 0xFF;
      int ba = a & 0xFF;
      int rb = b >> 16 & 0xFF;
      int gb = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      int r = (int)(ra * (1.0F - t) + rb * t);
      int g = (int)(ga * (1.0F - t) + gb * t);
      int bl = (int)(ba * (1.0F - t) + bb * t);
      return 0xFF000000 | r << 16 | g << 8 | bl;
   }

   private static int clamp255(int v) {
      return Math.max(0, Math.min(255, v));
   }

   public int a(int argb) {
      int ca = argb >>> 24;
      int na = Math.max(5, Math.round(ca * this.alpha));
      return na << 24 | argb & 16777215;
   }

   public int a(int argb, float f) {
      int ca = argb >>> 24;
      int na = Math.max(5, Math.round(ca * this.alpha * f));
      return na << 24 | argb & 16777215;
   }

   public int u(float v) {
      return Math.round(v * this.s);
   }

   private float sf() {
      return this.mc.getWindow().getScaleFactor();
   }

   public float ts(float basePx) {
      return basePx * this.textFactor;
   }

   private int pick(float px) {
      int best = 0;
      float bd = Float.MAX_VALUE;

      for (int i = 0; i < PX.length; i++) {
         float d = Math.abs(PX[i] - px);
         if (d < bd) {
            bd = d;
            best = i;
         }
      }

      return best;
   }

   private float k() {
      return 2.0F / this.sf();
   }

   private Text styled(String str, int idx) {
      return Text.literal(str).setStyle(Style.EMPTY.withFont(new Font(FONT_IDS[idx])));
   }

   public int th(float px) {
      int P = PX[this.pick(px)];
      return Math.round(this.k() * (P / 2.0F) * 0.95F);
   }

   public int tw(String str, float px) {
      if (str != null && !str.isEmpty()) {
         int idx = this.pick(px);
         if (GuiTextMesh.canRender("/assets/revive-client/font/inter.ttf", str)) {
            return Math.round((float)(GuiTextMesh.width("/assets/revive-client/font/inter.ttf", str, PX[idx]) / this.sf()));
         } else {
            TextRenderer tr = this.mc.textRenderer;
            return Math.round(tr.getWidth(this.styled(str, idx)) * this.k());
         }
      } else {
         return 0;
      }
   }

   public void text(DrawContext ctx, String str, int x, int y, int color, float px) {
      if (str != null && !str.isEmpty()) {
         int idx = this.pick(px);
         if (GuiTextMesh.canRender("/assets/revive-client/font/inter.ttf", str)) {
            float sc = this.sf();
            if (GuiTextMesh.queue("/assets/revive-client/font/inter.ttf", str, x * sc, y * sc, PX[idx], this.a(color))) {
               return;
            }
         }

         float k = this.k();
         Matrix3x2fStack m = ctx.getMatrices();
         m.pushMatrix();
         m.translate(x, y);
         m.scale(k, k);
         ctx.drawText(this.mc.textRenderer, this.styled(str, idx), 0, 0, this.a(color), false);
         m.popMatrix();
      }
   }

   public void textC(DrawContext ctx, String str, int x, int y, int h, int color, float px) {
      int idx = this.pick(px);
      float em = PX[idx] / 2.0F;
      float capCentre = this.k() * em * 0.5865F;
      this.text(ctx, str, x, Math.round(y + h / 2.0F - capCentre), color, px);
   }

   public void textR(DrawContext ctx, String str, int right, int y, int color, float px) {
      this.text(ctx, str, right - this.tw(str, px), y, color, px);
   }

   public String ellipsize(String str, int maxW, float px) {
      if (str == null) {
         return "";
      } else if (this.tw(str, px) <= maxW) {
         return str;
      } else {
         String dots = "...";
         int dw = this.tw(dots, px);
         int n = str.length();

         while (n > 0 && this.tw(str.substring(0, n), px) + dw > maxW) {
            n--;
         }

         return n <= 0 ? "" : str.substring(0, n).trim() + dots;
      }
   }

   private static int cornerInset(int r, int i) {
      double dy = r - i - 0.5;
      return r - (int)Math.round(Math.sqrt((double)r * r - dy * dy));
   }

   public void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
      if (w > 0 && h > 0) {
         ctx.fill(x, y, x + w, y + h, this.a(color));
      }
   }

   public void rrect(DrawContext ctx, int x, int y, int w, int h, float r, int color) {
      if (w > 0 && h > 0) {
         int col = this.a(color);
         float sc = this.sf();
         Matrix3x2fStack m = ctx.getMatrices();
         m.pushMatrix();
         m.scale(1.0F / sc, 1.0F / sc);
         int X = Math.round(x * sc);
         int Y = Math.round(y * sc);
         int W = Math.round(w * sc);
         int H = Math.round(h * sc);
         int R = Math.max(0, Math.min(Math.round(r * sc), Math.min(W / 2, H / 2)));
         ctx.fill(X, Y + R, X + W, Y + H - R, col);

         for (int i = 0; i < R; i++) {
            int in = cornerInset(R, i);
            ctx.fill(X + in, Y + i, X + W - in, Y + i + 1, col);
            ctx.fill(X + in, Y + H - i - 1, X + W - in, Y + H - i, col);
         }

         m.popMatrix();
      }
   }

   public void rborder(DrawContext ctx, int x, int y, int w, int h, float r, int color) {
      if (w > 0 && h > 0) {
         int col = this.a(color);
         float sc = this.sf();
         Matrix3x2fStack m = ctx.getMatrices();
         m.pushMatrix();
         m.scale(1.0F / sc, 1.0F / sc);
         int X = Math.round(x * sc);
         int Y = Math.round(y * sc);
         int W = Math.round(w * sc);
         int H = Math.round(h * sc);
         int R = Math.max(1, Math.min(Math.round(r * sc), Math.min(W, H) / 2));
         ctx.fill(X + R, Y, X + W - R, Y + 1, col);
         ctx.fill(X + R, Y + H - 1, X + W - R, Y + H, col);
         ctx.fill(X, Y + R, X + 1, Y + H - R, col);
         ctx.fill(X + W - 1, Y + R, X + W, Y + H - R, col);

         for (int i = 0; i < R; i++) {
            int in = cornerInset(R, i);
            int prev = i == 0 ? R : cornerInset(R, i - 1);
            int x2 = Math.max(in + 1, prev);
            ctx.fill(X + in, Y + i, X + x2, Y + i + 1, col);
            ctx.fill(X + W - x2, Y + i, X + W - in, Y + i + 1, col);
            ctx.fill(X + in, Y + H - i - 1, X + x2, Y + H - i, col);
            ctx.fill(X + W - x2, Y + H - i - 1, X + W - in, Y + H - i, col);
         }

         m.popMatrix();
      }
   }

   public void hline(DrawContext ctx, int x, int y, int w, int color) {
      if (w > 0) {
         int col = this.a(color);
         float sc = this.sf();
         Matrix3x2fStack m = ctx.getMatrices();
         m.pushMatrix();
         m.scale(1.0F / sc, 1.0F / sc);
         int X = Math.round(x * sc);
         int Y = Math.round(y * sc);
         int W = Math.round(w * sc);
         ctx.fill(X, Y, X + W, Y + 1, col);
         m.popMatrix();
      }
   }

   public void vline(DrawContext ctx, int x, int y, int h, int color) {
      if (h > 0) {
         int col = this.a(color);
         float sc = this.sf();
         Matrix3x2fStack m = ctx.getMatrices();
         m.pushMatrix();
         m.scale(1.0F / sc, 1.0F / sc);
         int X = Math.round(x * sc);
         int Y = Math.round(y * sc);
         int H = Math.round(h * sc);
         ctx.fill(X, Y, X + 1, Y + H, col);
         m.popMatrix();
      }
   }

   public void circle(DrawContext ctx, float cx, float cy, float r, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int R = Math.max(1, Math.round(r * sc));
      int CX = Math.round(cx * sc);
      int CY = Math.round(cy * sc);

      for (int yy = -R; yy < R; yy++) {
         double dy = yy + 0.5;
         int half = (int)Math.round(Math.sqrt(Math.max(0.0, (double)R * R - dy * dy)));
         if (half > 0) {
            ctx.fill(CX - half, CY + yy, CX + half, CY + yy + 1, col);
         }
      }

      m.popMatrix();
   }

   public void ring(DrawContext ctx, float cx, float cy, float r, int tPx, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int R = Math.max(2, Math.round(r * sc));
      int CX = Math.round(cx * sc);
      int CY = Math.round(cy * sc);
      int Ri = Math.max(0, R - Math.max(1, tPx));

      for (int yy = -R; yy < R; yy++) {
         double dy = yy + 0.5;
         int ho = (int)Math.round(Math.sqrt(Math.max(0.0, (double)R * R - dy * dy)));
         int hi = (int)Math.round(Math.sqrt(Math.max(0.0, (double)Ri * Ri - dy * dy)));
         if (ho > 0) {
            if (hi <= 0) {
               ctx.fill(CX - ho, CY + yy, CX + ho, CY + yy + 1, col);
            } else {
               ctx.fill(CX - ho, CY + yy, CX - hi, CY + yy + 1, col);
               ctx.fill(CX + hi, CY + yy, CX + ho, CY + yy + 1, col);
            }
         }
      }

      m.popMatrix();
   }

   public void tri(DrawContext ctx, int x, int y, float size, int dir, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int X = Math.round(x * sc);
      int Y = Math.round(y * sc);
      int S = Math.max(3, Math.round(size * sc));
      int hh = S / 2;
      switch (dir) {
         case 0:
            for (int i = 0; i <= hh; i++) {
               ctx.fill(X + i, Y + i, X + S - i, Y + i + 1, col);
            }
            break;
         case 1:
            for (int i = 0; i <= hh; i++) {
               ctx.fill(X + i, Y + i, X + i + 1, Y + S - i, col);
            }
            break;
         case 2:
            for (int i = 0; i <= hh; i++) {
               ctx.fill(X + i, Y + hh - i, X + S - i, Y + hh - i + 1, col);
            }
            break;
         default:
            for (int i = 0; i <= hh; i++) {
               ctx.fill(X + hh - i, Y + i, X + hh - i + 1, Y + S - i, col);
            }
      }

      m.popMatrix();
   }

   public void chevron(DrawContext ctx, float cx, float cy, float size, boolean down, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int n = Math.max(2, Math.round(size * sc / 2.0F));
      int CX = Math.round(cx * sc);
      int CY = Math.round(cy * sc);

      for (int i = 0; i <= n; i++) {
         if (down) {
            ctx.fill(CX - n + i, CY - n / 2 + i, CX - n + i + 2, CY - n / 2 + i + 1, col);
            ctx.fill(CX + n - i - 1, CY - n / 2 + i, CX + n - i + 1, CY - n / 2 + i + 1, col);
         } else {
            ctx.fill(CX - n / 2 + i, CY - n + i, CX - n / 2 + i + 1, CY - n + i + 2, col);
            ctx.fill(CX - n / 2 + i, CY + n - i - 1, CX - n / 2 + i + 1, CY + n - i + 1, col);
         }
      }

      m.popMatrix();
   }

   public void kebab(DrawContext ctx, float cx, float cy, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int d = Math.max(1, Math.round(sc * 0.9F));
      int gap = d * 2 + 1;
      int CX = Math.round(cx * sc) - d / 2;
      int CY = Math.round(cy * sc) - d / 2;
      ctx.fill(CX, CY - gap, CX + d, CY - gap + d, col);
      ctx.fill(CX, CY, CX + d, CY + d, col);
      ctx.fill(CX, CY + gap, CX + d, CY + gap + d, col);
      m.popMatrix();
   }

   public void xmark(DrawContext ctx, float cx, float cy, float size, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int n = Math.max(2, Math.round(size * sc));
      int CX = Math.round(cx * sc);
      int CY = Math.round(cy * sc);

      for (int i = -n; i <= n; i++) {
         ctx.fill(CX + i, CY + i, CX + i + 1, CY + i + 1, col);
         ctx.fill(CX - i, CY + i, CX - i + 1, CY + i + 1, col);
      }

      m.popMatrix();
   }

   public void star(DrawContext ctx, float cx, float cy, float r, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int R = Math.max(3, Math.round(r * sc));
      int CX = Math.round(cx * sc);
      int CY = Math.round(cy * sc);
      double[] px = new double[10];
      double[] py = new double[10];

      for (int i = 0; i < 10; i++) {
         double ang = (-Math.PI / 2) + i * Math.PI / 5.0;
         double rr = i % 2 == 0 ? R : R * 0.45;
         px[i] = CX + Math.cos(ang) * rr;
         py[i] = CY + Math.sin(ang) * rr;
      }

      for (int yy = CY - R; yy <= CY + R; yy++) {
         int runStart = -1;

         for (int xx = CX - R; xx <= CX + R + 1; xx++) {
            boolean in = xx <= CX + R && inPoly(xx + 0.5, yy + 0.5, px, py);
            if (in && runStart < 0) {
               runStart = xx;
            }

            if (!in && runStart >= 0) {
               ctx.fill(runStart, yy, xx, yy + 1, col);
               runStart = -1;
            }
         }
      }

      m.popMatrix();
   }

   private static boolean inPoly(double x, double y, double[] px, double[] py) {
      boolean in = false;
      int i = 0;

      for (int j = px.length - 1; i < px.length; j = i++) {
         if (py[i] > y != py[j] > y && x < (px[j] - px[i]) * (y - py[i]) / (py[j] - py[i]) + px[i]) {
            in = !in;
         }
      }

      return in;
   }

   public void gear(DrawContext ctx, float cx, float cy, float r, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int R = Math.max(4, Math.round(r * sc));
      int CX = Math.round(cx * sc);
      int CY = Math.round(cy * sc);
      double ro = R * 0.72;
      double ri = R * 0.4;

      for (int yy = -R; yy <= R; yy++) {
         for (int xx = -R; xx <= R; xx++) {
            double dx = xx + 0.5;
            double dy = yy + 0.5;
            double d = Math.sqrt(dx * dx + dy * dy);
            boolean on = d >= ri && d <= ro;
            if (!on && d > ro && d <= R) {
               double ang = Math.atan2(dy, dx);
               double sector = Math.abs((ang / (Math.PI / 4) % 1.0 + 1.0) % 1.0 - 0.5);
               on = sector < 0.22;
            }

            if (on) {
               ctx.fill(CX + xx, CY + yy, CX + xx + 1, CY + yy + 1, col);
            }
         }
      }

      m.popMatrix();
   }

   public void reset(DrawContext ctx, float cx, float cy, float r, int color) {
      int col = this.a(color);
      float sc = this.sf();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / sc, 1.0F / sc);
      int R = Math.max(3, Math.round(r * sc));
      int CX = Math.round(cx * sc);
      int CY = Math.round(cy * sc);
      double ro = R;
      double ri = Math.max(1.0, R - Math.max(1.0, sc * 0.8));

      for (int yy = -R; yy <= R; yy++) {
         for (int xx = -R; xx <= R; xx++) {
            double dx = xx + 0.5;
            double dy = yy + 0.5;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (!(d < ri) && !(d > ro)) {
               double ang = Math.atan2(dy, dx);
               if (!(ang > -Math.PI * 3.0 / 4.0) || !(ang < -Math.PI / 5)) {
                  ctx.fill(CX + xx, CY + yy, CX + xx + 1, CY + yy + 1, col);
               }
            }
         }
      }

      int hx = CX - (int)Math.round(R * 0.6);
      int hy = CY - (int)Math.round(R * 0.85);
      int hs = Math.max(2, R / 2);

      for (int i = 0; i <= hs / 2; i++) {
         ctx.fill(hx + i, hy + i, hx + i + 1, hy + hs - i, col);
      }

      m.popMatrix();
   }

   public void switchCtl(DrawContext ctx, int x, int y, int w, int h, float t) {
      int track = mix(this.offTrack, this.onTrack, t);
      int knob = mix(this.offKnob, this.onKnob, t);
      this.rrect(ctx, x, y, w, h, h / 2.0F, track);
      float pad = Math.max(1.0F, h * 0.16F);
      float d = h - pad * 2.0F;
      float kx = x + pad + (w - pad * 2.0F - d) * t;
      this.circle(ctx, kx + d / 2.0F, y + h / 2.0F, d / 2.0F, knob);
   }

   public void slider(DrawContext ctx, int x, int y, int w, float frac, boolean active) {
      frac = Math.max(0.0F, Math.min(1.0F, frac));
      int fillCol = this.accentControls ? this.accent : this.text;
      this.hline(ctx, x, y, w, this.lineStrong);
      int fw = Math.round(w * frac);
      if (fw > 0) {
         this.hline(ctx, x, y, fw, fillCol);
      }

      float kr = this.u(3.0F) + (active ? 0.6F : 0.0F);
      this.circle(ctx, x + fw, y + 0.5F, kr, fillCol);
   }

   public int chip(DrawContext ctx, String label, int x, int cy, int color, int border) {
      float px = this.ts(9.0F);
      int pw = this.u(3.0F);
      int w = this.tw(label, px) + pw * 2;
      int h = this.th(px) + this.u(2.0F);
      this.rborder(ctx, x, cy - h / 2, w, h, 1.5F, border);
      this.textC(ctx, label, x + pw, cy - h / 2, h, color, px);
      return w;
   }

   static {
      for (int i = 0; i < PX.length; i++) {
         FONT_IDS[i] = Identifier.of("revive-client", "inter_p" + PX[i]);
      }
   }
}
