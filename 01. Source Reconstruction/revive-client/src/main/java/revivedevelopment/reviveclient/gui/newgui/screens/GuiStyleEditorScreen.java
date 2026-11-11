package revivedevelopment.reviveclient.gui.newgui.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiShaderBackground;
import revivedevelopment.reviveclient.gui.newgui.style.GuiStyleDef;
import revivedevelopment.reviveclient.gui.newgui.style.GuiStyles;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class GuiStyleEditorScreen extends Screen {
   private GuiStyleDef selected;
   private GuiStyleEditorScreen.Mode mode = GuiStyleEditorScreen.Mode.NONE;
   private String input = "";
   private String toast = null;
   private long toastUntil = 0L;
   private int listScroll = 0;
   private int ctrlScroll = 0;
   private int ctrlContentH = 0;
   private int ctrlViewH = 0;
   private String expandedColor = null;
   private int hovMx;
   private int hovMy;
   private final List<GuiStyleEditorScreen.Hit> hits = new ArrayList<>();
   private final List<GuiStyleEditorScreen.Slider> sliders = new ArrayList<>();
   private boolean dragging = false;
   private int dsX;
   private int dsW;
   private double dsMin;
   private double dsMax;
   private boolean dsInt;
   private DoubleConsumer dsSet;
   private static final int RM = 6;

   public GuiStyleEditorScreen() {
      super(Text.literal("GUI Styles"));
   }

   private static Gui gui() {
      return Modules.get().get(Gui.class);
   }

   protected void init() {
      if (this.selected == null) {
         this.selected = GuiStyles.active();
      }
   }

   public boolean shouldPause() {
      return false;
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      context.fill(0, 0, this.width, this.height, -939524096);
      this.hits.clear();
      this.sliders.clear();
      this.hovMx = mouseX;
      this.hovMy = mouseY;
      FontManager fm = FontManager.get();
      int accent = fm.getPrimaryColor();
      int text = fm.getTextColor();
      int dim = fm.getTextSecondary();
      int lh = Math.max(10, fm.getTextHeight() + 5);
      int cardW = Math.min(560, this.width - 24);
      int cardH = Math.min(Math.max(240, lh * 15), this.height - 24);
      int x0 = (this.width - cardW) / 2;
      int y0 = (this.height - cardH) / 2;
      fill(context, x0, y0, x0 + cardW, y0 + cardH, withA(921106, 245));
      border(context, x0, y0, cardW, cardH, withA(accent, 210));
      int pad = 10;
      fm.drawText(context, "GUI STYLES", x0 + pad, y0 + pad, text);
      String hint = "Esc to close";
      fm.drawText(context, hint, x0 + cardW - pad - fm.getTextWidth(hint), y0 + pad, dim);
      hline(context, x0 + pad, x0 + cardW - pad, y0 + pad + lh, withA(accent, 90));
      int bodyTop = y0 + pad + lh + 6;
      int bodyBot = y0 + cardH - pad;
      int listW = 108;
      int previewW = Math.max(150, (cardW - pad * 4 - listW) * 42 / 100);
      int listX = x0 + pad;
      int ctrlX = listX + listW + pad;
      int previewX = x0 + cardW - pad - previewW;
      int ctrlW = previewX - pad - ctrlX;
      this.renderList(context, fm, listX, listW, bodyTop, bodyBot, lh, accent, text, dim);
      this.renderControls(context, fm, ctrlX, ctrlW, bodyTop, bodyBot, lh, accent, text, dim);
      this.renderPreview(context, fm, previewX, previewW, bodyTop, bodyBot, accent, text, dim);
      if (this.mode != GuiStyleEditorScreen.Mode.NONE) {
         this.renderNaming(context, fm, x0, y0, cardW, cardH, lh, accent, text, dim);
      }

      if (this.toast != null && System.currentTimeMillis() < this.toastUntil) {
         int tw = fm.getTextWidth(this.toast) + 12;
         int tx = x0 + (cardW - tw) / 2;
         int ty = y0 + cardH + 6;
         fill(context, tx, ty, tx + tw, ty + lh, withA(0, 220));
         border(context, tx, ty, tw, lh, withA(accent, 160));
         fm.drawText(context, this.toast, tx + 6, ty + 2, text);
      }
   }

   private void renderList(DrawContext ctx, FontManager fm, int x, int w, int top, int bot, int lh, int accent, int text, int dim) {
      int listBot = bot - lh * 2 - 6;
      List<GuiStyleDef> styles = GuiStyles.all();
      int visible = Math.max(1, (listBot - top) / lh);
      this.listScroll = Math.max(0, Math.min(this.listScroll, Math.max(0, styles.size() - visible)));
      int ry = top;

      for (int i = this.listScroll; i < styles.size() && ry + lh <= listBot; i++) {
         GuiStyleDef d = styles.get(i);
         boolean sel = d == this.selected;
         boolean act = GuiStyles.isActive(d);
         boolean hov = this.hovMx >= x && this.hovMx < x + w && this.hovMy >= ry && this.hovMy < ry + lh;
         if (sel) {
            fill(ctx, x, ry, x + w, ry + lh, withA(accent, 75));
         } else if (hov) {
            fill(ctx, x, ry, x + w, ry + lh, withA(accent, 32));
         }

         if (act) {
            fill(ctx, x, ry + 1, x + 2, ry + lh - 1, accent);
         }

         fm.drawText(ctx, clip(fm, d.name, w - 14), x + 6, ry + 2, !sel && !act ? dim : text);
         if (d.builtin) {
            fill(ctx, x + w - 8, ry + lh / 2 - 2, x + w - 4, ry + lh / 2 + 2, withA(dim, 160));
         }

         this.hits.add(new GuiStyleEditorScreen.Hit(x, ry, w, lh, () -> this.selectStyle(d)));
         ry += lh;
      }

      border(ctx, x, top, w, listBot - top, withA(accent, 60));
      int by = listBot + 4;
      int hw = (w - 4) / 2;
      this.button(ctx, fm, x, by, hw, lh, "New", accent, text, () -> {
         this.mode = GuiStyleEditorScreen.Mode.NEW;
         this.input = "My Style";
      });
      this.button(ctx, fm, x + hw + 4, by, w - hw - 4, lh, "Import", accent, text, () -> {
         GuiStyleDef n = GuiStyles.importFromClipboard();
         if (n != null) {
            this.selected = n;
            this.setToast("Imported: " + n.name);
         } else {
            this.setToast("No style on clipboard");
         }
      });
   }

   private void renderControls(DrawContext ctx, FontManager fm, int x, int w, int top, int bot, int lh, int accent, int text, int dim) {
      GuiStyleDef s = this.selected;
      if (s != null) {
         Gui g = gui();
         int bw = (w - 8) / 3;
         this.button(ctx, fm, x, top, bw, lh, "Duplicate", accent, text, () -> {
            GuiStyleDef n = GuiStyles.duplicate(s);
            if (n != null) {
               this.selected = n;
            }

            this.setToast("Duplicated");
         });
         this.button(ctx, fm, x + bw + 4, top, bw, lh, s.builtin ? "Locked" : "Rename", s.builtin ? dim : accent, text, () -> {
            if (!s.builtin) {
               this.mode = GuiStyleEditorScreen.Mode.RENAME;
               this.input = s.name;
            }
         });
         this.button(
            ctx,
            fm,
            x + (bw + 4) * 2,
            top,
            w - (bw + 4) * 2,
            lh,
            "Share",
            accent,
            text,
            () -> this.setToast(GuiStyles.exportToClipboard(s) ? "Share code copied" : "Copy failed")
         );
         int ay = top + lh + 3;
         this.button(ctx, fm, x, ay, bw, lh, "Open GUI", accent, text, () -> {
            this.close();
            MinecraftClient.getInstance().setScreen(ReviveClient.openGuiScreen());
         });
         if (!s.builtin) {
            this.button(ctx, fm, x + bw + 4, ay, bw, lh, "Delete", withA(14701914, 255), text, () -> {
               GuiStyles.delete(s);
               this.selected = GuiStyles.active();
               this.setToast("Deleted");
            });
         }

         ay += lh + 5;
         hline(ctx, x, x + w, ay - 2, withA(accent, 60));
         if (s.builtin) {
            fm.drawText(ctx, "Built-in style (locked).", x, ay + 2, dim);
            fm.drawText(ctx, "Duplicate it to edit.", x, ay + 2 + lh, dim);
         } else {
            this.ctrlViewH = bot - ay;
            int hits0 = this.hits.size();
            int sliders0 = this.sliders.size();
            ctx.enableScissor(x, ay, x + w, bot);
            int cy = ay - this.ctrlScroll;
            cy = this.label(ctx, fm, x, cy, lh, "Base layout", dim);
            cy = this.segment(ctx, fm, x, cy, w, lh, accent, text, dim, new String[]{"Classic", "Clean", "Modern"}, g.style.get().ordinal(), i -> {
               g.style.set(Gui.GuiStyle.values()[i]);
               this.afterEdit();
            });
            boolean modern = g.style.get() == Gui.GuiStyle.Modern;
            cy = this.cycleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Font", g.font.get().name(), () -> this.cycleEnum(g.font));
            cy = this.cycleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Font style", g.fontStyle.get().name(), () -> this.cycleEnum(g.fontStyle));
            cy = this.toggleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Text shadow", g.textShadow.get(), () -> {
               g.textShadow.set(!g.textShadow.get());
               this.afterEdit();
            });
            if (!modern) {
               cy = this.section(ctx, fm, x, cy, lh, "LAYOUT", dim);
               cy = this.sliderRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Text size", g.textSize.get(), 0.5, 1.6, false, v -> {
                  g.textSize.set(v);
                  this.afterEdit();
               });
               cy = this.sliderRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Panel opacity %", g.panelOpacity.get().intValue(), 20.0, 100.0, true, v -> {
                  g.panelOpacity.set((int)v);
                  this.afterEdit();
               });
               cy = this.sliderRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Row height (0=auto)", g.rowHeight.get().intValue(), 0.0, 24.0, true, v -> {
                  g.rowHeight.set((int)v);
                  this.afterEdit();
               });
               cy = this.sliderRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Header height (0=auto)", g.headerHeight.get().intValue(), 0.0, 28.0, true, v -> {
                  g.headerHeight.set((int)v);
                  this.afterEdit();
               });
               cy = this.sliderRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Panel width (0=auto)", g.panelWidth.get().intValue(), 0.0, 240.0, true, v -> {
                  g.panelWidth.set((int)v);
                  this.afterEdit();
               });
               cy = this.sliderRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Corner radius", g.cornerRadius.get().intValue(), 0.0, 6.0, true, v -> {
                  g.cornerRadius.set((int)v);
                  this.afterEdit();
               });
               cy = this.cycleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Accent bar", g.accentBar.get().name(), () -> this.cycleEnum(g.accentBar));
               cy = this.cycleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Row striping", g.rowStriping.get().name(), () -> this.cycleEnum(g.rowStriping));
            } else {
               cy = this.section(ctx, fm, x, cy, lh, "MODERN", dim);
               cy = this.cycleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Layout", g.modernLayout.get().name(), () -> this.cycleEnum(g.modernLayout));
               cy = this.sliderRow(ctx, fm, x, cy, w, lh, accent, text, dim, "GUI scale", g.modernScale.get(), 0.6, 1.6, false, v -> {
                  g.modernScale.set(v);
                  this.afterEdit();
               });
               cy = this.toggleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Animations", g.modernAnimations.get(), () -> {
                  g.modernAnimations.set(!g.modernAnimations.get());
                  this.afterEdit();
               });
               cy = this.toggleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Blur game", g.modernBlur.get(), () -> {
                  g.modernBlur.set(!g.modernBlur.get());
                  this.afterEdit();
               });
               cy = this.toggleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Accent on controls", g.accentOnControls.get(), () -> {
                  g.accentOnControls.set(!g.accentOnControls.get());
                  this.afterEdit();
               });
            }

            cy = this.section(ctx, fm, x, cy, lh, "BACKGROUND", dim);
            cy = this.cycleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Shader", g.shader.get().name(), () -> this.cycleEnum(g.shader));
            if (g.shader.get() != GuiShaderBackground.Mode.Off) {
               cy = this.sliderRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Shader opacity", g.shaderOpacity.get(), 0.0, 1.0, false, v -> {
                  g.shaderOpacity.set(v);
                  this.afterEdit();
               });
            }

            cy = this.toggleRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Gradient background", g.bgGradient.get(), () -> {
               g.bgGradient.set(!g.bgGradient.get());
               this.afterEdit();
            });
            if (g.bgGradient.get()) {
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Gradient top", g.bgGradientTop);
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Gradient bottom", g.bgGradientBottom);
               cy = this.sliderRow(
                  ctx, fm, x, cy, w, lh, accent, text, dim, "Gradient opacity %", g.bgGradientOpacity.get().intValue(), 0.0, 100.0, true, v -> {
                     g.bgGradientOpacity.set((int)v);
                     this.afterEdit();
                  }
               );
            }

            cy = this.section(ctx, fm, x, cy, lh, "COLOURS", dim);
            if (modern) {
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Window", g.modernWindow);
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Cards", g.modernCard);
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Text", g.modernText);
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Text dim", g.modernTextDim);
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Accent", g.modernAccent);
            } else {
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Primary", g.primary);
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Secondary", g.secondary);
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Text", g.textColor);
               cy = this.colorRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Text secondary", g.textSecondary);
               cy = this.section(ctx, fm, x, cy, lh, "ADVANCED COLOURS (toggle to override)", dim);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Panel bg", g.ovPanel, g.colPanel);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Header bg", g.ovHeader, g.colHeader);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Hover", g.ovHover, g.colHover);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Active row", g.ovActive, g.colActive);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Stripe", g.ovStripe, g.colStripe);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Border", g.ovBorder, g.colBorder);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Glow", g.ovGlow, g.colGlow);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Scrollbar", g.ovScroll, g.colScroll);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Popup bg", g.ovPopup, g.colPopup);
               cy = this.overrideRow(ctx, fm, x, cy, w, lh, accent, text, dim, "Tooltip bg", g.ovTooltip, g.colTooltip);
            }

            this.ctrlContentH = cy + this.ctrlScroll - ay;
            ctx.disableScissor();
            int ayF = ay;
            this.hits.subList(hits0, this.hits.size()).removeIf(hh -> hh.y() + hh.h() <= ayF || hh.y() >= bot);
            this.sliders.subList(sliders0, this.sliders.size()).removeIf(sl -> sl.y() + sl.h() <= ayF || sl.y() >= bot);
            if (this.ctrlContentH > this.ctrlViewH) {
               int trackH = this.ctrlViewH;
               int barH = Math.max(12, trackH * this.ctrlViewH / this.ctrlContentH);
               int barY = ay + (trackH - barH) * this.ctrlScroll / Math.max(1, this.ctrlContentH - this.ctrlViewH);
               fill(ctx, x + w - 2, barY, x + w, barY + barH, withA(accent, 120));
            }
         }
      }
   }

   private void renderPreview(DrawContext ctx, FontManager fm, int x, int w, int top, int bot, int accent, int text, int dim) {
      Gui g = gui();
      Gui.GuiStyle layout = g.style.get();
      boolean modern = layout == Gui.GuiStyle.Modern;
      boolean clean = layout == Gui.GuiStyle.Clean;
      fm.drawText(ctx, "PREVIEW", x, top + 2, dim);
      int py = top + fm.getTextHeight() + 6;
      int h = bot - py;
      fill(ctx, x, py, x + w, py + h, withA(0, 90));
      border(ctx, x, py, w, h, withA(accent, 60));
      int bg = modern ? argb(g.modernWindow.get()) : withA(1314839, clean ? 250 : 235);
      int card = modern ? argb(g.modernCard.get()) : withA(mix(argb(g.secondary.get()), 0, clean ? 0.9F : 0.86F), 255);
      int tcol = modern ? argb(g.modernText.get()) : argb(g.textColor.get());
      int tdim = modern ? argb(g.modernTextDim.get()) : argb(g.textSecondary.get());
      int acc = modern ? argb(g.modernAccent.get()) : argb(g.primary.get());
      if (!modern && g.ovPanel.get()) {
         bg = argb(g.colPanel.get());
      }

      if (!modern && g.ovHeader.get()) {
         card = argb(g.colHeader.get());
      }

      int activeBg = !modern && g.ovActive.get() ? argb(g.colActive.get()) : withA(acc, 26);
      int borderC = !modern && g.ovBorder.get() ? argb(g.colBorder.get()) : withA(acc, 150);
      int glowC = !modern && g.ovGlow.get() ? argb(g.colGlow.get()) : acc;
      int stripeC = !modern && g.ovStripe.get() ? argb(g.colStripe.get()) : withA(tcol, 8);
      int r = modern ? 3 : g.cornerRadius.get();
      int rowH = modern ? 12 : (g.rowHeight.get() > 0 ? g.rowHeight.get() : (clean ? 12 : 11));
      int hh = modern ? 16 : (g.headerHeight.get() > 0 ? g.headerHeight.get() : (clean ? 14 : 18));
      Gui.AccentBar ab = g.accentBar.get();
      if (ab == Gui.AccentBar.Auto) {
         ab = clean ? Gui.AccentBar.Rail : Gui.AccentBar.Glow;
      }

      Gui.Striping st = g.rowStriping.get();
      boolean stripe = st == Gui.Striping.On || st == Gui.Striping.Auto && !clean && !modern;
      int pct = modern ? 100 : g.panelOpacity.get();
      int pw = !modern && g.panelWidth.get() > 0 ? Math.max(50, Math.min(w - 16, g.panelWidth.get())) : Math.min(w - 20, 122);
      int ph = hh + rowH * 3 + 6;
      int px = x + (w - pw) / 2;
      int ppy = py + (h - ph) / 2;
      if (!modern && g.bgGradient.get()) {
         int go = g.bgGradientOpacity.get();
         ctx.fillGradient(x + 2, py + 2, x + w - 2, py + h - 2, scaleAlpha(argb(g.bgGradientTop.get()), go), scaleAlpha(argb(g.bgGradientBottom.get()), go));
      }

      rrect(ctx, px, ppy, pw, ph, r, borderC);
      rrect(ctx, px + 1, ppy + 1, pw - 2, ph - 2, Math.max(0, r - 1), scaleAlpha(bg, pct));
      rrect(ctx, px + 1, ppy + 1, pw - 2, hh - 1, Math.max(0, r - 1), scaleAlpha(card, pct));
      fm.drawText(ctx, "COMBAT", px + 6, ppy + Math.max(2, (hh - fm.getTextHeight()) / 2), tcol);
      if (!modern) {
         fm.drawText(ctx, clean ? "1/3" : "1/3", px + pw - fm.getTextWidth("1/3") - 6, ppy + Math.max(2, (hh - fm.getTextHeight()) / 2), tdim);
      }

      if (clean) {
         fill(ctx, px + 1, ppy + hh, px + pw - 1, ppy + hh + 1, withA(acc, 90));
      }

      String[] mods = new String[]{"Kill Aura", "Reach", "Velocity"};
      boolean[] on = new boolean[]{true, false, true};
      int ry = ppy + hh + (clean ? 2 : 1);

      for (int i = 0; i < mods.length; i++) {
         if (stripe && i % 2 == 1) {
            fill(ctx, px + 1, ry, px + pw - 1, ry + rowH, stripeC);
         }

         if (on[i]) {
            fill(ctx, px + 1, ry, px + pw - 1, ry + rowH, activeBg);
         }

         if (!modern && on[i]) {
            if (ab == Gui.AccentBar.Glow) {
               fill(ctx, px, ry, px + 2, ry + rowH, acc);
               fill(ctx, px + 2, ry, px + 4, ry + rowH, withA(glowC, 160));
               fill(ctx, px + 4, ry, px + 6, ry + rowH, withA(glowC, 60));
            } else if (ab == Gui.AccentBar.Rail) {
               fill(ctx, px, ry + 1, px + 2, ry + rowH - 1, acc);
            }
         } else if (!modern && ab != Gui.AccentBar.None) {
            fill(ctx, px, ry + (clean ? 1 : 0), clean ? px + 1 : px + 2, ry + rowH - (clean ? 1 : 0), withA(tdim, clean ? 90 : 255));
         }

         fm.drawText(ctx, mods[i], px + 7, ry + Math.max(1, (rowH - fm.getTextHeight()) / 2), on[i] ? tcol : tdim);
         ry += rowH;
      }
   }

   private int label(DrawContext ctx, FontManager fm, int x, int y, int lh, String s, int dim) {
      fm.drawText(ctx, s, x, y + 1, dim);
      return y + lh;
   }

   private int section(DrawContext ctx, FontManager fm, int x, int y, int lh, String title, int dim) {
      y += 4;
      fm.drawText(ctx, title, x, y + 1, dim);
      return y + lh;
   }

   private int sliderRow(
      DrawContext ctx,
      FontManager fm,
      int x,
      int y,
      int w,
      int lh,
      int accent,
      int text,
      int dim,
      String name,
      double cur,
      double min,
      double max,
      boolean isInt,
      DoubleConsumer set
   ) {
      fm.drawText(ctx, name, x, y + 1, dim);
      String val = isInt ? String.valueOf((int)cur) : String.format("%.2f", cur);
      fm.drawText(ctx, val, x + w - fm.getTextWidth(val) - 6, y + 1, text);
      int tw = w - 6;
      int ty = y + lh - 1;
      int th = 3;
      fill(ctx, x, ty, x + tw, ty + th, withA(accent, 40));
      double f = max > min ? (cur - min) / (max - min) : 0.0;
      int fillW = (int)Math.round(Math.max(0.0, Math.min(1.0, f)) * tw);
      fill(ctx, x, ty, x + fillW, ty + th, accent);
      int knob = x + Math.max(1, Math.min(tw - 1, fillW));
      fill(ctx, knob - 1, ty - 2, knob + 2, ty + th + 2, text);
      this.sliders.add(new GuiStyleEditorScreen.Slider(x, ty, tw, th, min, max, isInt, set));
      return y + lh + 4;
   }

   private int toggleRow(DrawContext ctx, FontManager fm, int x, int y, int w, int lh, int accent, int text, int dim, String name, boolean on, Runnable toggle) {
      fm.drawText(ctx, name, x, y + 1, dim);
      int sw = 20;
      int sh = lh - 3;
      int sx = x + w - sw;
      fill(ctx, sx, y, sx + sw, y + sh, withA(on ? accent : dim, on ? 200 : 70));
      int kx = on ? sx + sw - sh + 1 : sx + 1;
      fill(ctx, kx, y + 1, kx + sh - 2, y + sh - 1, text);
      this.hits.add(new GuiStyleEditorScreen.Hit(sx, y, sw, sh, toggle));
      return y + lh + 3;
   }

   private int cycleRow(DrawContext ctx, FontManager fm, int x, int y, int w, int lh, int accent, int text, int dim, String name, String value, Runnable next) {
      fm.drawText(ctx, name, x, y + 1, dim);
      int bw = Math.min(w * 55 / 100, 120);
      int bx = x + w - bw - 6;
      fill(ctx, bx, y, bx + bw, y + lh - 2, withA(accent, this.hovMx >= bx && this.hovMx < bx + bw && this.hovMy >= y && this.hovMy < y + lh - 2 ? 60 : 30));
      border(ctx, bx, y, bw, lh - 2, withA(accent, 90));
      fm.drawText(ctx, clip(fm, value, bw - 6), bx + 3, y + 1, text);
      this.hits.add(new GuiStyleEditorScreen.Hit(bx, y, bw, lh - 2, next));
      return y + lh + 3;
   }

   private int colorRow(DrawContext ctx, FontManager fm, int x, int y, int w, int lh, int accent, int text, int dim, String name, Setting<SettingColor> setting) {
      SettingColor c = setting.get();
      fm.drawText(ctx, name, x, y + 1, dim);
      int sw = 26;
      int sx = x + w - sw - 14;
      fill(ctx, sx, y + 1, sx + sw, y + lh - 3, 0xFF000000 | c.r << 16 | c.g << 8 | c.b);
      border(ctx, sx, y + 1, sw, lh - 4, withA(text, 120));
      boolean open = name.equals(this.expandedColor);
      fm.drawText(ctx, open ? "-" : "+", x + w - 10, y + 1, text);
      this.hits.add(new GuiStyleEditorScreen.Hit(x, y, w, lh - 2, () -> this.expandedColor = open ? null : name));
      y += lh;
      if (open) {
         y = this.chan(ctx, fm, x, y, w, lh, accent, text, dim, "R", c.r, v -> this.setColor(setting, (int)v, -1, -1, -1));
         y = this.chan(ctx, fm, x, y, w, lh, accent, text, dim, "G", c.g, v -> this.setColor(setting, -1, (int)v, -1, -1));
         y = this.chan(ctx, fm, x, y, w, lh, accent, text, dim, "B", c.b, v -> this.setColor(setting, -1, -1, (int)v, -1));
         y = this.chan(ctx, fm, x, y, w, lh, accent, text, dim, "A", c.a, v -> this.setColor(setting, -1, -1, -1, (int)v));
      }

      return y;
   }

   private int overrideRow(
      DrawContext ctx, FontManager fm, int x, int y, int w, int lh, int accent, int text, int dim, String name, Setting<Boolean> en, Setting<SettingColor> col
   ) {
      boolean on = en.get();
      fm.drawText(ctx, name, x, y + 1, on ? text : dim);
      int cbs = lh - 6;
      int cbx = x + w - cbs - 6;
      int cby = y + 1;
      fill(ctx, cbx, cby, cbx + cbs, cby + cbs, withA(on ? accent : dim, on ? 180 : 45));
      if (on) {
         fill(ctx, cbx + 2, cby + 2, cbx + cbs - 2, cby + cbs - 2, text);
      }

      this.hits.add(new GuiStyleEditorScreen.Hit(cbx - 2, y, cbs + 6 + 2, lh, () -> {
         en.set(!en.get());
         this.afterEdit();
      }));
      SettingColor c = col.get();
      int sw = 22;
      int sx = cbx - sw - 6;
      fill(ctx, sx, y + 1, sx + sw, y + lh - 4, on ? 0xFF000000 | c.r << 16 | c.g << 8 | c.b : withA(dim, 40));
      border(ctx, sx, y + 1, sw, lh - 5, withA(text, on ? 120 : 40));
      boolean open = name.equals(this.expandedColor);
      if (on) {
         this.hits.add(new GuiStyleEditorScreen.Hit(sx, y, sw, lh - 2, () -> this.expandedColor = open ? null : name));
      }

      y += lh;
      if (on && open) {
         y = this.chan(ctx, fm, x, y, w, lh, accent, text, dim, "R", c.r, v -> this.setColor(col, (int)v, -1, -1, -1));
         y = this.chan(ctx, fm, x, y, w, lh, accent, text, dim, "G", c.g, v -> this.setColor(col, -1, (int)v, -1, -1));
         y = this.chan(ctx, fm, x, y, w, lh, accent, text, dim, "B", c.b, v -> this.setColor(col, -1, -1, (int)v, -1));
         y = this.chan(ctx, fm, x, y, w, lh, accent, text, dim, "A", c.a, v -> this.setColor(col, -1, -1, -1, (int)v));
      }

      return y;
   }

   private int chan(DrawContext ctx, FontManager fm, int x, int y, int w, int lh, int accent, int text, int dim, String ch, int cur, DoubleConsumer set) {
      fm.drawText(ctx, ch, x + 6, y + 1, dim);
      int valW = fm.getTextWidth("255");
      int tx = x + 18;
      int tw = w - 18 - valW - 6 - 4;
      int ty = y + (lh - 3) / 2;
      int th = 3;
      fill(ctx, tx, ty, tx + tw, ty + th, withA(accent, 40));
      int fillW = (int)Math.round(cur / 255.0 * tw);
      fill(ctx, tx, ty, tx + fillW, ty + th, accent);
      int knob = tx + Math.max(0, Math.min(tw, fillW));
      fill(ctx, knob - 1, ty - 2, knob + 2, ty + th + 2, text);
      fm.drawText(ctx, String.valueOf(cur), x + w - valW - 6, y + 1, text);
      this.sliders.add(new GuiStyleEditorScreen.Slider(tx, ty, tw, th, 0.0, 255.0, true, set));
      return y + lh - 1;
   }

   private int segment(DrawContext ctx, FontManager fm, int x, int y, int w, int lh, int accent, int text, int dim, String[] opts, int cur, IntConsumer pick) {
      int each = w / opts.length;

      for (int i = 0; i < opts.length; i++) {
         int bx = x + i * each;
         int bw = i == opts.length - 1 ? w - each * i : each;
         boolean on = i == cur;
         fill(ctx, bx, y, bx + bw, y + lh - 2, withA(accent, on ? 90 : 28));
         border(ctx, bx, y, bw, lh - 2, withA(accent, on ? 200 : 70));
         fm.drawText(ctx, clip(fm, opts[i], bw - 4), bx + (bw - Math.min(fm.getTextWidth(opts[i]), bw - 4)) / 2, y + 1, on ? text : dim);
         int idx = i;
         this.hits.add(new GuiStyleEditorScreen.Hit(bx, y, bw, lh - 2, () -> pick.accept(idx)));
      }

      return y + lh + 4;
   }

   private void renderNaming(DrawContext ctx, FontManager fm, int x0, int y0, int cardW, int cardH, int lh, int accent, int text, int dim) {
      fill(ctx, x0, y0, x0 + cardW, y0 + cardH, withA(0, 160));
      int bw = Math.min(260, cardW - 40);
      int bh = lh * 3 + 12;
      int bx = x0 + (cardW - bw) / 2;
      int byy = y0 + (cardH - bh) / 2;
      fill(ctx, bx, byy, bx + bw, byy + bh, withA(1315864, 250));
      border(ctx, bx, byy, bw, bh, withA(accent, 220));
      fm.drawText(ctx, this.mode == GuiStyleEditorScreen.Mode.RENAME ? "Rename style" : "New style name", bx + 8, byy + 6, text);
      int fy = byy + 6 + lh + 2;
      fill(ctx, bx + 8, fy, bx + bw - 8, fy + lh, withA(0, 120));
      border(ctx, bx + 8, fy, bw - 16, lh, withA(accent, 120));
      boolean caret = System.currentTimeMillis() / 500L % 2L == 0L;
      fm.drawText(ctx, this.input + (caret ? "_" : ""), bx + 12, fy + 2, text);
      fm.drawText(ctx, "Enter: confirm.  Esc: cancel", bx + 8, fy + lh + 4, dim);
   }

   private int button(DrawContext ctx, FontManager fm, int x, int y, int w, int h, String label, int accent, int text, Runnable action) {
      boolean hov = this.hovMx >= x && this.hovMx < x + w && this.hovMy >= y && this.hovMy < y + h;
      fill(ctx, x, y, x + w, y + h, withA(accent, hov ? 70 : 34));
      border(ctx, x, y, w, h, withA(accent, hov ? 220 : 110));
      fm.drawText(ctx, clip(fm, label, w - 6), x + (w - Math.min(fm.getTextWidth(label), w - 6)) / 2, y + 2, text);
      this.hits.add(new GuiStyleEditorScreen.Hit(x, y, w, h, action));
      return y + h + 3;
   }

   private void selectStyle(GuiStyleDef d) {
      this.selected = d;
      this.expandedColor = null;
      this.ctrlScroll = 0;
      GuiStyles.select(d);
   }

   private void afterEdit() {
      Gui g = gui();
      if (g != null) {
         g.applyToFontManager();
      }
   }

   private void persistEdit() {
      if (this.selected != null && !this.selected.builtin) {
         GuiStyles.syncFromCurrent(this.selected);
         GuiStyles.persist(this.selected);
      }
   }

   private void setColor(Setting<SettingColor> setting, int r, int gg, int b, int a) {
      SettingColor c = setting.get();
      setting.set(new SettingColor(r < 0 ? c.r : r, gg < 0 ? c.g : gg, b < 0 ? c.b : b, a < 0 ? c.a : a));
      this.afterEdit();
   }

   private <T extends Enum<T>> void cycleEnum(Setting<T> setting) {
      T cur = (T)setting.get();
      T[] vals = (T[])cur.getDeclaringClass().getEnumConstants();
      setting.set(vals[(cur.ordinal() + 1) % vals.length]);
      this.afterEdit();
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.mode != GuiStyleEditorScreen.Mode.NONE) {
         return true;
      } else {
         double mx = click.x();
         double my = click.y();

         for (int i = this.sliders.size() - 1; i >= 0; i--) {
            GuiStyleEditorScreen.Slider sl = this.sliders.get(i);
            if (sl.in(mx, my)) {
               this.dragging = true;
               this.dsX = sl.x();
               this.dsW = sl.w();
               this.dsMin = sl.min();
               this.dsMax = sl.max();
               this.dsInt = sl.isInt();
               this.dsSet = sl.set();
               sl.apply(mx);
               return true;
            }
         }

         for (int ix = this.hits.size() - 1; ix >= 0; ix--) {
            GuiStyleEditorScreen.Hit h = this.hits.get(ix);
            if (h.in(mx, my)) {
               h.action().run();
               this.persistEdit();
               return true;
            }
         }

         return super.mouseClicked(click, doubled);
      }
   }

   public boolean mouseDragged(Click click, double offsetX, double offsetY) {
      if (this.dragging && this.dsSet != null) {
         double f = Math.max(0.0, Math.min(1.0, (click.x() - this.dsX) / Math.max(1, this.dsW)));
         double v = this.dsMin + f * (this.dsMax - this.dsMin);
         this.dsSet.accept(this.dsInt ? Math.round(v) : v);
         return true;
      } else {
         return super.mouseDragged(click, offsetX, offsetY);
      }
   }

   public boolean mouseReleased(Click click) {
      if (this.dragging) {
         this.dragging = false;
         this.dsSet = null;
         this.persistEdit();
         return true;
      } else {
         return super.mouseReleased(click);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int step = 12;
      this.ctrlScroll = Math.max(0, Math.min(this.ctrlScroll - (int)Math.signum(verticalAmount) * step, Math.max(0, this.ctrlContentH - this.ctrlViewH)));
      if (verticalAmount > 0.0) {
         this.listScroll = Math.max(0, this.listScroll - 1);
      }

      return true;
   }

   public boolean keyPressed(KeyInput input) {
      int key = input.key();
      if (this.mode != GuiStyleEditorScreen.Mode.NONE) {
         if (key == 256) {
            this.mode = GuiStyleEditorScreen.Mode.NONE;
            this.input = "";
            return true;
         } else if (key == 257 || key == 335) {
            this.confirmName();
            return true;
         } else if (key == 259 && !this.input.isEmpty()) {
            this.input = this.input.substring(0, this.input.length() - 1);
            return true;
         } else {
            return true;
         }
      } else if (key == 256) {
         this.close();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public boolean charTyped(CharInput input) {
      if (this.mode != GuiStyleEditorScreen.Mode.NONE) {
         char c = (char)input.codepoint();
         if (c >= ' ' && this.input.length() < 40) {
            this.input = this.input + c;
         }

         return true;
      } else {
         return super.charTyped(input);
      }
   }

   private void confirmName() {
      String name = this.input.trim();
      if (!name.isEmpty()) {
         if (this.mode == GuiStyleEditorScreen.Mode.RENAME && this.selected != null) {
            GuiStyles.rename(this.selected, name);
            this.setToast("Renamed");
         } else if (this.mode == GuiStyleEditorScreen.Mode.NEW) {
            GuiStyleDef n = GuiStyles.saveCurrentAs(name);
            if (n != null) {
               this.selected = n;
               this.setToast("Saved: " + n.name);
            }
         }
      }

      this.mode = GuiStyleEditorScreen.Mode.NONE;
      this.input = "";
   }

   private void setToast(String t) {
      this.toast = t;
      this.toastUntil = System.currentTimeMillis() + 2200L;
   }

   private static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int argb) {
      ctx.fill(x1, y1, x2, y2, argb);
   }

   private static void border(DrawContext ctx, int x, int y, int w, int h, int argb) {
      ctx.fill(x, y, x + w, y + 1, argb);
      ctx.fill(x, y + h - 1, x + w, y + h, argb);
      ctx.fill(x, y, x + 1, y + h, argb);
      ctx.fill(x + w - 1, y, x + w, y + h, argb);
   }

   private static void hline(DrawContext ctx, int x1, int x2, int y, int argb) {
      ctx.fill(x1, y, x2, y + 1, argb);
   }

   private static void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int argb) {
      r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
      ctx.fill(x, y + r, x + w, y + h - r, argb);

      for (int i = 0; i < r; i++) {
         int inset = r - i;
         ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
         ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, argb);
      }
   }

   private static int withA(int rgb, int a) {
      return rgb & 16777215 | (a & 0xFF) << 24;
   }

   private static int argb(SettingColor c) {
      return (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
   }

   private static int scaleAlpha(int color, int pct) {
      if (pct >= 100) {
         return color;
      } else {
         int a = (color >>> 24 & 0xFF) * pct / 100;
         return color & 16777215 | a << 24;
      }
   }

   private static int mix(int a, int b, float t) {
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      int r = Math.round(ar + (br - ar) * t);
      int g = Math.round(ag + (bg - ag) * t);
      int bl = Math.round(ab + (bb - ab) * t);
      return r << 16 | g << 8 | bl;
   }

   private static String clip(FontManager fm, String s, int maxW) {
      if (fm.getTextWidth(s) <= maxW) {
         return s;
      } else {
         String e = "..";

         while (s.length() > 1 && fm.getTextWidth(s + e) > maxW) {
            s = s.substring(0, s.length() - 1);
         }

         return s + e;
      }
   }

   private record Hit(int x, int y, int w, int h, Runnable action) {
      boolean in(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum Mode {
      NONE,
      RENAME,
      NEW;
   }

   private record Slider(int x, int y, int w, int h, double min, double max, boolean isInt, DoubleConsumer set) {
      boolean in(double mx, double my) {
         return mx >= this.x - 2 && mx < this.x + this.w + 2 && my >= this.y - 2 && my < this.y + this.h + 4;
      }

      void apply(double mx) {
         double f = Math.max(0.0, Math.min(1.0, (mx - this.x) / Math.max(1, this.w)));
         double v = this.min + f * (this.max - this.min);
         this.set.accept(this.isInt ? Math.round(v) : v);
      }
   }
}
