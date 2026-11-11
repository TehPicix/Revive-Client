package revivedevelopment.reviveclient.gui.newgui.modern;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import revivedevelopment.reviveclient.gui.newgui.components.NewGuiBindCapture;
import revivedevelopment.reviveclient.gui.newgui.components.SettingGroupRenderer;
import revivedevelopment.reviveclient.settings.ActionSetting;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.FontFaceSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.ProvidedStringSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.settings.Vector3dSetting;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public final class ModernSettings {
   private final ModernTheme t;
   private final List<ModernSettings.Row> rows = new ArrayList<>();
   private Module module;
   private int lastX;
   private int lastW;
   private StringSetting editing;
   private String buffer = "";
   private Setting<?> dragSlider;
   private int dragX;
   private int dragW;
   private ColorSetting dragColor;
   private int dragComp = -1;
   private int dragSvTop;
   private int dragSvH;
   private final Map<ColorSetting, float[]> pickers = new HashMap<>();
   private final Map<Object, Float> knob = new HashMap<>();
   private long lastAnim = System.nanoTime();

   public ModernSettings(ModernTheme theme) {
      this.t = theme;
   }

   public void reset() {
      this.commit();
      this.dragSlider = null;
      this.dragColor = null;
      this.dragComp = -1;
      this.pickers.clear();
      NewGuiBindCapture.get().cancelSettingListen();
   }

   public boolean isEditingText() {
      return this.editing != null;
   }

   public int layout(Iterable<SettingGroup> groups, Module module, int width) {
      this.module = module;
      this.rows.clear();
      int gap = this.t.u(3.0F);
      int y = 0;
      if (module != null) {
         if (module.description != null && !module.description.isEmpty()) {
            ModernSettings.Row d = new ModernSettings.Row();
            d.kind = ModernSettings.Kind.DESC;
            d.label = module.description;
            d.lines = this.wrap(module.description, width - this.t.u(4.0F), this.t.ts(10.0F));
            d.h = d.lines.size() * (this.t.th(this.t.ts(10.0F)) + this.t.u(2.0F)) + this.t.u(2.0F);
            d.y = y;
            y += d.h + gap;
            this.rows.add(d);
         }

         ModernSettings.Row kb = new ModernSettings.Row();
         kb.kind = ModernSettings.Kind.MODBIND;
         kb.label = "Keybind";
         kb.h = this.t.u(17.0F);
         kb.y = y;
         y += kb.h + gap;
         this.rows.add(kb);
      }

      for (SettingGroup g : groups) {
         boolean any = false;

         for (Setting<?> s : g) {
            if (s.isVisible()) {
               any = true;
               break;
            }
         }

         if (any) {
            ModernSettings.Row gr = new ModernSettings.Row();
            gr.kind = ModernSettings.Kind.GROUP;
            gr.group = g;
            gr.label = g.name;
            gr.h = this.t.u(12.0F);
            gr.y = y + (this.rows.isEmpty() ? 0 : this.t.u(3.0F));
            y = gr.y + gr.h + this.t.u(1.0F);
            this.rows.add(gr);

            for (Setting<?> sx : g) {
               if (sx.isVisible()) {
                  ModernSettings.Row r = new ModernSettings.Row();
                  r.setting = sx;
                  r.label = sx.title;
                  if (sx instanceof BoolSetting) {
                     r.kind = ModernSettings.Kind.BOOL;
                     r.h = this.t.u(15.0F);
                  } else if (!(sx instanceof EnumSetting) && !(sx instanceof ProvidedStringSetting) && !(sx instanceof FontFaceSetting)) {
                     if (sx instanceof IntSetting is && !is.noSlider) {
                        r.kind = ModernSettings.Kind.SLIDER;
                        r.h = this.t.u(22.0F);
                     } else if (sx instanceof DoubleSetting ds && !ds.noSlider) {
                        r.kind = ModernSettings.Kind.SLIDER;
                        r.h = this.t.u(22.0F);
                     } else if (sx instanceof StringSetting) {
                        r.kind = ModernSettings.Kind.STRING;
                        r.h = this.t.u(17.0F);
                     } else if (sx instanceof KeybindSetting) {
                        r.kind = ModernSettings.Kind.KEYBIND;
                        r.h = this.t.u(17.0F);
                     } else if (sx instanceof ColorSetting) {
                        r.kind = ModernSettings.Kind.COLOR;
                        r.h = this.t.u(15.0F);
                     } else if (sx instanceof ActionSetting) {
                        r.kind = ModernSettings.Kind.ACTION;
                        r.h = this.t.u(17.0F);
                     } else {
                        r.kind = ModernSettings.Kind.JUMP;
                        r.h = this.t.u(17.0F);
                     }
                  } else {
                     r.kind = ModernSettings.Kind.DROPDOWN;
                     r.h = this.t.u(17.0F);
                  }

                  r.y = y;
                  y += r.h + gap;
                  this.rows.add(r);
                  if (sx instanceof ColorSetting cs && this.pickers.containsKey(cs)) {
                     ModernSettings.Row p = new ModernSettings.Row();
                     p.kind = ModernSettings.Kind.PICKER;
                     p.setting = cs;
                     p.h = this.t.u(62.0F);
                     p.y = y;
                     y += p.h + gap;
                     this.rows.add(p);
                  }
               }
            }
         }
      }

      return Math.max(0, y - gap);
   }

   public void render(DrawContext ctx, int x, int y0, int width, int mx, int my, int clipTop, int clipBot) {
      this.lastX = x;
      this.lastW = width;
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (float)(now - this.lastAnim) / 1.0E9F);
      this.lastAnim = now;
      this.tickDrag(mx, my);
      float md = this.t.ts(13.0F);
      float sm = this.t.ts(11.5F);
      float xs = this.t.ts(10.0F);

      for (ModernSettings.Row r : this.rows) {
         int y = y0 + r.y;
         if (y + r.h >= clipTop && y <= clipBot) {
            boolean hov = mx >= x && mx < x + width && my >= y && my < y + r.h && my >= clipTop && my < clipBot;
            switch (r.kind) {
               case GROUP:
                  this.t.text(ctx, r.label.toUpperCase(), x + this.t.u(2.0F), y + r.h - this.t.th(xs) - this.t.u(1.0F), this.t.text3, xs);
                  break;
               case BOOL:
                  BoolSetting bs = (BoolSetting)r.setting;
                  if (hov) {
                     this.t.rrect(ctx, x, y, width, r.h, 2.0F, ModernTheme.withA(16777215, 8));
                  }

                  int sw = this.t.u(16.0F);
                  int sh = this.t.u(9.0F);
                  this.t.textC(ctx, this.t.ellipsize(r.label, width - sw - this.t.u(14.0F), md), x + this.t.u(4.0F), y, r.h, this.t.text, md);
                  this.t.switchCtl(ctx, x + width - sw - this.t.u(3.0F), y + (r.h - sh) / 2, sw, sh, this.anim(bs, bs.get(), dt));
                  break;
               case DROPDOWN:
               case STRING:
               case KEYBIND:
               case JUMP:
               case ACTION:
               case MODBIND:
                  this.renderField(ctx, r, x, y, width, hov, md, sm);
                  break;
               case SLIDER:
                  Setting<?> s = r.setting;
                  String val;
                  float frac;
                  if (s instanceof IntSetting is) {
                     int lo = Math.max(is.min, is.sliderMin);
                     int hi = Math.min(is.max, is.sliderMax);
                     frac = hi > lo ? (float)(is.get() - lo) / (hi - lo) : 0.0F;
                     val = String.valueOf(is.get());
                  } else {
                     DoubleSetting ds = (DoubleSetting)s;
                     double lo = Math.max(ds.min, ds.sliderMin);
                     double hi = Math.min(ds.max, ds.sliderMax);
                     frac = hi > lo ? (float)((ds.get() - lo) / (hi - lo)) : 0.0F;
                     val = fmt(ds);
                  }

                  if (hov || this.dragSlider == s) {
                     this.t.rrect(ctx, x, y, width, r.h, 2.0F, ModernTheme.withA(16777215, 6));
                  }

                  int vw = this.t.tw(val, sm);
                  this.t.text(ctx, this.t.ellipsize(r.label, width - vw - this.t.u(12.0F), md), x + this.t.u(4.0F), y + this.t.u(2.0F), this.t.text, md);
                  this.t.text(ctx, val, x + width - vw - this.t.u(4.0F), y + this.t.u(2.0F) + (this.t.th(md) - this.t.th(sm)) / 2, this.t.text2, sm);
                  this.t.slider(ctx, x + this.t.u(6.0F), y + r.h - this.t.u(6.0F), width - this.t.u(12.0F), frac, this.dragSlider == s);
                  break;
               case COLOR:
                  ColorSetting cs = (ColorSetting)r.setting;
                  if (hov) {
                     this.t.rrect(ctx, x, y, width, r.h, 2.0F, ModernTheme.withA(16777215, 8));
                  }

                  int sz = this.t.u(10.0F);
                  SettingColor c = cs.get();
                  int argb = (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
                  int sx = x + width - sz - this.t.u(4.0F);
                  int sy = y + (r.h - sz) / 2;
                  this.checker(ctx, sx, sy, sz, sz);
                  this.t.rrect(ctx, sx, sy, sz, sz, 2.0F, argb);
                  this.t.rborder(ctx, sx, sy, sz, sz, 2.0F, this.t.lineStrong);
                  String hex = String.format("#%02X%02X%02X", c.r & 0xFF, c.g & 0xFF, c.b & 0xFF);
                  int hw = this.t.tw(hex, xs);
                  this.t.textC(ctx, hex, sx - hw - this.t.u(5.0F), y, r.h, this.t.text3, xs);
                  this.t.textC(ctx, this.t.ellipsize(r.label, width - sz - hw - this.t.u(16.0F), md), x + this.t.u(4.0F), y, r.h, this.t.text, md);
                  break;
               case PICKER:
                  this.renderPicker(ctx, (ColorSetting)r.setting, x, y, width, r.h);
                  break;
               case DESC:
                  int ly = y + this.t.u(1.0F);

                  for (String line : r.lines) {
                     this.t.text(ctx, line, x + this.t.u(2.0F), ly, this.t.text2, xs);
                     ly += this.t.th(xs) + this.t.u(2.0F);
                  }
            }
         }
      }
   }

   private void renderField(DrawContext ctx, ModernSettings.Row r, int x, int y, int width, boolean hov, float md, float sm) {
      this.t.rrect(ctx, x, y, width, r.h, 2.5F, this.t.field);
      this.t.rborder(ctx, x, y, width, r.h, 2.5F, hov ? this.t.lineStrong : this.t.fieldBorder);
      int px = x + this.t.u(5.0F);
      int right = x + width - this.t.u(5.0F);
      switch (r.kind) {
         case DROPDOWN: {
            String val = dropdownValue(r.setting);
            this.t.chevron(ctx, right - this.t.u(3.0F), y + r.h / 2.0F, this.t.u(4.0F), true, this.t.text3);
            int availx = right - this.t.u(10.0F) - px;
            String labx = r.label;
            int lwx = this.t.tw(labx, md);
            String v = " - " + val;
            if (lwx + this.t.tw(v, sm) > availx) {
               v = this.t.ellipsize(v, Math.max(this.t.u(20.0F), availx - lwx), sm);
            }

            this.t.textC(ctx, this.t.ellipsize(labx, availx, md), px, y, r.h, this.t.text, md);
            this.t.textC(ctx, v, px + Math.min(lwx, availx), y, r.h, this.t.text2, sm);
         }
         case SLIDER:
         case COLOR:
         case PICKER:
         default:
            break;
         case STRING: {
            StringSetting ss = (StringSetting)r.setting;
            boolean ed = this.editing == ss;
            String lab = r.label;
            int lw = this.t.tw(lab, md) + this.t.u(4.0F);
            this.t.textC(ctx, lab, px, y, r.h, this.t.text, md);
            String v = ed ? this.buffer : ss.get();
            int tx = px + lw;
            int avail = right - tx;
            ctx.enableScissor(tx, y, right + 1, y + r.h);
            int vw = this.t.tw(v, sm);
            int drawX = tx;
            if (ed && vw > avail) {
               drawX = right - vw;
            }

            this.t.textC(ctx, v, drawX, y, r.h, ed ? this.t.text : this.t.text2, sm);
            if (ed && (System.currentTimeMillis() / 500L & 1L) == 0L) {
               this.t.rect(ctx, drawX + vw + 1, y + this.t.u(4.0F), 1, r.h - this.t.u(8.0F), this.t.text);
            }

            ctx.disableScissor();
            break;
         }
         case KEYBIND: {
            KeybindSetting ks = (KeybindSetting)r.setting;
            boolean listening = NewGuiBindCapture.get().getListeningSetting() == ks;
            Keybind kb = ks.get();
            String v = listening ? "Press a key..." : (kb.isSet() ? kb.toString() : "None");
            this.t.textC(ctx, this.t.ellipsize(r.label, width - this.t.u(12.0F) - this.t.tw(v, sm), md), px, y, r.h, this.t.text, md);
            this.t.textC(ctx, v, right - this.t.tw(v, sm), y, r.h, listening ? this.t.text : this.t.text2, sm);
            break;
         }
         case JUMP: {
            String v = jumpValue(r.setting);
            this.t.chevron(ctx, right - this.t.u(3.0F), y + r.h / 2.0F, this.t.u(4.0F), false, this.t.text3);
            int vw = this.t.tw(v, sm);
            this.t.textC(ctx, v, right - this.t.u(9.0F) - vw, y, r.h, this.t.text2, sm);
            this.t.textC(ctx, this.t.ellipsize(r.label, width - vw - this.t.u(20.0F), md), px, y, r.h, this.t.text, md);
            break;
         }
         case ACTION:
            ActionSetting as = (ActionSetting)r.setting;
            String labx = as.buttonLabel != null && !as.buttonLabel.isEmpty() ? as.buttonLabel : r.label;
            this.t.textC(ctx, labx, x + (width - this.t.tw(labx, md)) / 2, y, r.h, this.t.text, md);
            break;
         case MODBIND: {
            boolean listening = this.module != null && Modules.get().isBinding();
            String v = listening ? "Press a key..." : (this.module != null && this.module.keybind.isSet() ? this.module.keybind.toString() : "None");
            this.t.textC(ctx, "Keybind", px, y, r.h, this.t.text, md);
            this.t.textC(ctx, v, right - this.t.tw(v, sm), y, r.h, listening ? this.t.text : this.t.text2, sm);
         }
      }
   }

   private void renderPicker(DrawContext ctx, ColorSetting cs, int x, int y, int width, int h) {
      float[] hsb = this.picker(cs);
      int pad = this.t.u(2.0F);
      int px = x + pad;
      int pw = width - pad * 2;
      int svH = this.t.u(36.0F);
      int barH = this.t.u(6.0F);
      int g = this.t.u(3.0F);
      int svTop = y;
      int pure = Color.HSBtoRGB(hsb[0], 1.0F, 1.0F);
      int hr = pure >> 16 & 0xFF;
      int hg = pure >> 8 & 0xFF;
      int hb = pure & 0xFF;

      for (int i = 0; i < pw; i++) {
         float sat = (float)i / Math.max(1, pw - 1);
         int rr = (int)((1.0F - sat) * 255.0F + sat * hr);
         int gg = (int)((1.0F - sat) * 255.0F + sat * hg);
         int bb = (int)((1.0F - sat) * 255.0F + sat * hb);
         this.t.rect(ctx, px + i, svTop, 1, svH, 0xFF000000 | rr << 16 | gg << 8 | bb);
      }

      for (int j = 0; j < svH; j++) {
         int al = (int)((float)j / Math.max(1, svH - 1) * 255.0F);
         this.t.rect(ctx, px, svTop + j, pw, 1, al << 24);
      }

      int cx = px + Math.round(hsb[1] * (pw - 1));
      int cy = svTop + Math.round((1.0F - hsb[2]) * (svH - 1));
      this.t.ring(ctx, cx, cy, this.t.u(2.5F), 1, -16777216);
      this.t.ring(ctx, cx, cy, this.t.u(2.0F), 1, -1);
      int hueY = svTop + svH + g;

      for (int i = 0; i < pw; i++) {
         int rgb = Color.HSBtoRGB((float)i / Math.max(1, pw - 1), 1.0F, 1.0F);
         this.t.rect(ctx, px + i, hueY, 1, barH, 0xFF000000 | rgb & 16777215);
      }

      this.marker(ctx, px + Math.round(hsb[0] * (pw - 1)), hueY, barH);
      int aY = hueY + barH + g;
      SettingColor c = cs.get();
      this.checker(ctx, px, aY, pw, barH);

      for (int i = 0; i < pw; i++) {
         int al = (int)((float)i / Math.max(1, pw - 1) * 255.0F);
         this.t.rect(ctx, px + i, aY, 1, barH, al << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF);
      }

      this.marker(ctx, px + Math.round((c.a & 0xFF) / 255.0F * (pw - 1)), aY, barH);
      float xs = this.t.ts(10.0F);
      this.t.text(ctx, "A " + (c.a & 0xFF), px + this.t.u(2.0F), aY + barH + this.t.u(2.0F), this.t.text3, xs);
   }

   private void marker(DrawContext ctx, int mx, int y, int h) {
      this.t.rect(ctx, mx - 1, y - 1, 3, h + 2, -16777216);
      this.t.rect(ctx, mx, y - 1, 1, h + 2, -1);
   }

   private void checker(DrawContext ctx, int x, int y, int w, int h) {
      int sq = Math.max(2, this.t.u(2.0F));
      this.t.rect(ctx, x, y, w, h, -3355444);

      for (int i = 0; i < w; i += sq) {
         for (int j = 0; j < h; j += sq) {
            if ((i / sq + j / sq & 1) != 0) {
               this.t.rect(ctx, x + i, y + j, Math.min(sq, w - i), Math.min(sq, h - j), -8355712);
            }
         }
      }
   }

   public boolean click(int x, int y0, int width, int mx, int my, int btn) {
      if (mx >= x && mx < x + width) {
         for (ModernSettings.Row r : this.rows) {
            int y = y0 + r.y;
            if (my >= y && my < y + r.h) {
               Setting<?> s = r.setting;
               switch (r.kind) {
                  case GROUP:
                  case DESC:
                     return true;
                  case BOOL:
                     if (btn == 0) {
                        this.commit();
                        BoolSetting bs = (BoolSetting)s;
                        bs.set(!bs.get());
                     }

                     return true;
                  case DROPDOWN:
                     this.commit();
                     if (s instanceof EnumSetting<?> es) {
                        if (btn == 0) {
                           cycleEnum(es, 1);
                        } else if (btn == 1) {
                           cycleEnum(es, -1);
                        }
                     } else {
                        SettingGroupRenderer.handleSettingClick(s, x, y, width, mx, y, btn, null);
                     }

                     return true;
                  case SLIDER:
                     if (btn != 0) {
                        return true;
                     }

                     this.commit();
                     this.dragSlider = s;
                     this.dragX = x + this.t.u(6.0F);
                     this.dragW = width - this.t.u(12.0F);
                     this.applySlider(mx);
                     return true;
                  case STRING:
                     if (btn != 0) {
                        return true;
                     }

                     StringSetting ss = (StringSetting)s;
                     if (this.editing != ss) {
                        this.commit();
                        this.editing = ss;
                        this.buffer = ss.get();
                     }

                     return true;
                  case KEYBIND:
                     if (btn != 0) {
                        return true;
                     }

                     this.commit();
                     KeybindSetting ks = (KeybindSetting)s;
                     if (NewGuiBindCapture.get().getListeningSetting() == ks) {
                        NewGuiBindCapture.get().cancelSettingListen();
                     } else {
                        Modules.get().setModuleToBind(null);
                        NewGuiBindCapture.get().startListeningForSetting(ks);
                     }

                     return true;
                  case COLOR:
                     this.commit();
                     ColorSetting cs = (ColorSetting)s;
                     if (this.pickers.containsKey(cs)) {
                        this.pickers.remove(cs);
                     } else {
                        this.picker(cs);
                     }

                     return true;
                  case PICKER:
                     if (btn != 0) {
                        return true;
                     }

                     // NOTE (source reconstruction): renamed from "cs" — the COLOR case above
                     // already declares a "cs" in the same (brace-less) switch, which javac
                     // rejects as a redefinition.
                     ColorSetting pickCs = (ColorSetting)s;
                     int pad = this.t.u(2.0F);
                     int px = x + pad;
                     int pw = width - pad * 2;
                     int svH = this.t.u(36.0F);
                     int barH = this.t.u(6.0F);
                     int g = this.t.u(3.0F);
                     int hueY = y + svH + g;
                     int aY = hueY + barH + g;
                     this.dragColor = pickCs;
                     this.dragX = px;
                     this.dragW = pw;
                     this.dragSvTop = y;
                     this.dragSvH = svH;
                     if (my < y + svH) {
                        this.dragComp = 0;
                     } else if (my >= hueY - 1 && my < hueY + barH + 1) {
                        this.dragComp = 1;
                     } else {
                        if (my < aY - 1 || my >= aY + barH + 2) {
                           this.dragColor = null;
                           this.dragComp = -1;
                           return true;
                        }

                        this.dragComp = 2;
                     }

                     this.applyColor(mx, my);
                     return true;
                  case JUMP:
                  case ACTION:
                     if (btn != 0) {
                        return true;
                     }

                     this.commit();
                     SettingGroupRenderer.handleSettingClick(s, x, y, width, mx, y, 0, null);
                     return true;
                  case MODBIND:
                     if (btn == 0 && this.module != null) {
                        this.commit();
                        if (Modules.get().isBinding()) {
                           Modules.get().setModuleToBind(null);
                        } else {
                           NewGuiBindCapture.get().cancelSettingListen();
                           Modules.get().setModuleToBind(this.module);
                        }

                        return true;
                     }

                     return true;
               }
            }
         }

         this.commit();
         return true;
      } else {
         this.commit();
         return false;
      }
   }

   public void drag(int mx, int my) {
      this.tickDrag(mx, my);
   }

   private void tickDrag(int mx, int my) {
      if (this.dragSlider != null) {
         this.applySlider(mx);
      } else if (this.dragColor != null && this.dragComp >= 0) {
         this.applyColor(mx, my);
      }
   }

   public void release() {
      this.dragSlider = null;
      this.dragColor = null;
      this.dragComp = -1;
   }

   public boolean isDragging() {
      return this.dragSlider != null || this.dragColor != null;
   }

   public boolean keyPressed(int key, int mods) {
      if (this.editing == null) {
         return false;
      } else {
         boolean ctrl = (mods & 2) != 0 || (mods & 8) != 0;
         if (key == 257 || key == 335) {
            this.commit();
            return true;
         } else if (key == 256) {
            this.editing = null;
            this.buffer = "";
            return true;
         } else if (key == 259) {
            if (!this.buffer.isEmpty()) {
               this.buffer = this.buffer.substring(0, this.buffer.length() - 1);
            }

            return true;
         } else if (ctrl && key == 86) {
            String c = MinecraftClient.getInstance().keyboard.getClipboard();
            if (c != null) {
               this.buffer = this.buffer + c.replace("\n", "").replace("\r", "");
            }

            return true;
         } else if (ctrl && key == 67) {
            MinecraftClient.getInstance().keyboard.setClipboard(this.buffer);
            return true;
         } else if (ctrl && key == 65) {
            this.buffer = "";
            return true;
         } else {
            return true;
         }
      }
   }

   public boolean charTyped(char chr) {
      if (this.editing == null) {
         return false;
      } else {
         if (chr >= ' ') {
            this.buffer = this.buffer + chr;
         }

         return true;
      }
   }

   public void commit() {
      if (this.editing != null) {
         if (!this.buffer.equals(this.editing.get())) {
            this.editing.set(this.buffer);
         }

         this.editing = null;
         this.buffer = "";
      }
   }

   private float anim(Object key, boolean on, float dt) {
      float cur = this.knob.getOrDefault(key, on ? 1.0F : 0.0F);
      float target = on ? 1.0F : 0.0F;
      cur += (target - cur) * Math.min(1.0F, dt * 14.0F);
      if (Math.abs(cur - target) < 0.01F) {
         cur = target;
      }

      this.knob.put(key, cur);
      return cur;
   }

   private void applySlider(int mx) {
      float p = Math.max(0.0F, Math.min(1.0F, (float)(mx - this.dragX) / Math.max(1, this.dragW)));
      if (this.dragSlider instanceof IntSetting is) {
         int lo = Math.max(is.min, is.sliderMin);
         int hi = Math.min(is.max, is.sliderMax);
         int v = Math.round(lo + (hi - lo) * p);
         v = Math.max(is.min, Math.min(is.max, v));
         if (!is.get().equals(v)) {
            is.set(v);
         }
      } else if (this.dragSlider instanceof DoubleSetting ds) {
         double lo = Math.max(ds.min, ds.sliderMin);
         double hi = Math.min(ds.max, ds.sliderMax);
         double v = lo + (hi - lo) * p;
         int dp = Math.max(0, Math.min(6, ds.decimalPlaces));
         double f = Math.pow(10.0, dp);
         v = Math.round(v * f) / f;
         v = Math.max(ds.min, Math.min(ds.max, v));
         if (!ds.get().equals(v)) {
            ds.set(v);
         }
      }
   }

   private void applyColor(int mx, int my) {
      if (this.dragColor != null) {
         float[] hsb = this.picker(this.dragColor);
         float px = Math.max(0.0F, Math.min(1.0F, (float)(mx - this.dragX) / Math.max(1, this.dragW)));
         SettingColor c = this.dragColor.get();
         switch (this.dragComp) {
            case 0:
               hsb[1] = px;
               float py = Math.max(0.0F, Math.min(1.0F, (float)(my - this.dragSvTop) / Math.max(1, this.dragSvH)));
               hsb[2] = 1.0F - py;
               break;
            case 1:
               hsb[0] = px;
               break;
            case 2:
               c.a = Math.round(px * 255.0F);
               this.dragColor.onChanged();
               return;
            default:
               return;
         }

         int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
         c.r = rgb >> 16 & 0xFF;
         c.g = rgb >> 8 & 0xFF;
         c.b = rgb & 0xFF;
         this.dragColor.onChanged();
      }
   }

   private float[] picker(ColorSetting cs) {
      float[] hsb = this.pickers.get(cs);
      if (hsb == null) {
         SettingColor c = cs.get();
         hsb = Color.RGBtoHSB(c.r & 0xFF, c.g & 0xFF, c.b & 0xFF, null);
         this.pickers.put(cs, hsb);
      }

      return hsb;
   }

   private static void cycleEnum(EnumSetting<?> es, int dir) {
      Enum<?> cur = es.get();
      Enum<?>[] vals = (Enum<?>[])cur.getDeclaringClass().getEnumConstants();
      int i = (cur.ordinal() + dir + vals.length) % vals.length;
      ((EnumSetting<Enum<?>>)es).set(vals[i]);
   }

   private static String dropdownValue(Setting<?> s) {
      Object v = s.get();
      return v == null ? "None" : v.toString();
   }

   private static String jumpValue(Setting<?> s) {
      Object v;
      try {
         v = s.get();
      } catch (Throwable var3) {
         return "Edit";
      }

      if (v instanceof Collection<?> c) {
         return c.size() + (c.size() == 1 ? " item" : " items");
      } else if (v instanceof Map<?, ?> m) {
         return m.size() + (m.size() == 1 ? " entry" : " entries");
      } else if (v == null) {
         return "None";
      } else if (!(s instanceof BlockPosSetting) && !(s instanceof Vector3dSetting)) {
         String str = v.toString();
         if (str.length() > 18) {
            str = str.substring(0, 18) + "...";
         }

         return str.contains("@") ? "Edit" : str;
      } else {
         return "Edit";
      }
   }

   private static String fmt(DoubleSetting ds) {
      int dp = Math.max(1, Math.min(3, ds.decimalPlaces));
      return String.format("%." + dp + "f", ds.get());
   }

   private List<String> wrap(String text, int maxW, float sc) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : text.split(" ")) {
         String cand = line.isEmpty() ? word : line + " " + word;
         if (this.t.tw(cand, sc) > maxW && !line.isEmpty()) {
            lines.add(line.toString());
            line = new StringBuilder(word);
         } else {
            line = new StringBuilder(cand);
         }
      }

      if (!line.isEmpty()) {
         lines.add(line.toString());
      }

      return lines;
   }

   public static String summary(Module m) {
      StringBuilder sb = new StringBuilder();
      int n = 0;

      for (SettingGroup g : m.settings) {
         for (Setting<?> s : g) {
            if (s.isVisible()) {
               String part = null;
               if (s instanceof BoolSetting b) {
                  if (b.get()) {
                     part = s.title;
                  }
               } else if (s instanceof EnumSetting<?> e) {
                  part = String.valueOf(e.get());
               } else if (s instanceof IntSetting i) {
                  part = i.get() + " " + s.title;
               } else if (s instanceof DoubleSetting d) {
                  part = fmt(d) + " " + s.title;
               } else if (s instanceof ProvidedStringSetting p) {
                  part = p.get();
               }

               if (part != null && !part.isEmpty()) {
                  if (n > 0) {
                     sb.append(", ");
                  }

                  sb.append(part);
                  if (++n >= 6) {
                     return n == 0 ? "Default" : sb.toString();
                  }
               }
            }
         }
      }

      return n == 0 ? "Default" : sb.toString();
   }

   private static enum Kind {
      GROUP,
      BOOL,
      DROPDOWN,
      SLIDER,
      STRING,
      KEYBIND,
      COLOR,
      PICKER,
      JUMP,
      ACTION,
      MODBIND,
      DESC;
   }

   private static final class Row {
      ModernSettings.Kind kind;
      Setting<?> setting;
      SettingGroup group;
      String label;
      List<String> lines;
      int y;
      int h;
   }
}
