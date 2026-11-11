package revivedevelopment.reviveclient.gui.newgui.modern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiShaderBackground;
import revivedevelopment.reviveclient.gui.newgui.GuiTextMesh;
import revivedevelopment.reviveclient.gui.newgui.NewGuiLayout;
import revivedevelopment.reviveclient.gui.newgui.NewGuiScreen;
import revivedevelopment.reviveclient.gui.newgui.components.NewGuiBindCapture;
import revivedevelopment.reviveclient.gui.newgui.screens.NewHudEditorScreen;
import revivedevelopment.reviveclient.systems.modules.Category;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;
import revivedevelopment.reviveclient.systems.profiles.Profile;
import revivedevelopment.reviveclient.systems.profiles.Profiles;

public class ModernGuiScreen extends Screen {
   private static final Category FAVORITES = new Category("Favorites");
   private static final String KEY_NAV = "modern:nav";
   private static final String KEY_PROFILES = "modern:profiles";
   private static final String KEY_SETTINGS = "modern:settings";
   private static final String KEY_POPUP = "modern:popup";
   private static ModernGuiScreen.Tab tab = ModernGuiScreen.Tab.MODULES;
   private static String selectedCatName = null;
   private static final List<String> panelFront = new ArrayList<>();
   private static boolean settingsPanelOpen = true;
   private static boolean layoutLoaded = false;
   private static final Map<String, Integer> scrolls = new HashMap<>();
   private static final Map<String, Boolean> hiddenPanels = new HashMap<>();
   private static String selectedProfile = null;
   private final ModernTheme t = new ModernTheme();
   private final ModernSettings drawer = new ModernSettings(this.t);
   private final ModernSettings guiSettings = new ModernSettings(this.t);
   private String search = "";
   private boolean searchFocused = false;
   private Module openModule = null;
   private float drawerT = 0.0F;
   private int drawerScroll = 0;
   private int drawerContentH = 0;
   private int guiScroll = 0;
   private int guiContentH = 0;
   private long lastFrame = System.nanoTime();
   private float openT = 0.0F;
   private float inkX = -1.0F;
   private float inkW = 0.0F;
   private final Map<Object, Float> knobs = new HashMap<>();
   private int lastMx;
   private int lastMy;
   private boolean centeredNow;
   private String dragKey = null;
   private int dragOffX;
   private int dragOffY;
   private final List<ModernGuiScreen.Panel> panelsThisFrame = new ArrayList<>();
   private String profileName = "";
   private boolean profileNameFocused = false;
   private final List<ModernGuiScreen.Hit> hits = new ArrayList<>();
   private int wx;
   private int wy;
   private int W;
   private int H;
   private int bodyTop;
   private int listX;
   private int listY;
   private int listW;
   private int listH;
   private int drawerX;
   private int drawerW;
   private Module lastDrawerModule;
   private static final int PANEL_W = 98;
   private static final int NAV_W = 115;
   private static final int PROF_W = 116;
   private static final int SET_W = 130;
   private static final int POP_W = 128;

   public ModernGuiScreen() {
      super(Text.literal("REVIVE"));
   }

   protected void init() {
      FontManager.get().auditFonts();
      super.init();
      NewGuiBindCapture.ensureSubscribed();
      if (!layoutLoaded) {
         NewGuiLayout.load();
         layoutLoaded = true;
      }

      this.lastFrame = System.nanoTime();
   }

   public boolean shouldPause() {
      return false;
   }

   public void close() {
      this.drawer.reset();
      this.guiSettings.reset();
      NewGuiBindCapture.get().cancelSettingListen();

      try {
         Modules mods = Modules.get();
         if (mods != null) {
            if (mods.isBinding()) {
               mods.setModuleToBind(null);
            }

            mods.save();
         }
      } catch (Throwable var2) {
         ReviveClient.LOG.error("Failed to save modules on gui close", var2);
      }

      NewGuiLayout.save();
      super.close();
   }

   private Gui gui() {
      return Modules.get().get(Gui.class);
   }

   private int u(float v) {
      return this.t.u(v);
   }

   private static float ease(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
   }

   private boolean anim() {
      return this.gui().modernAnimations.get();
   }

   private float knob(Object key, boolean on, float dt) {
      float target = on ? 1.0F : 0.0F;
      if (!this.anim()) {
         this.knobs.put(key, target);
         return target;
      } else {
         float cur = this.knobs.getOrDefault(key, target);
         cur += (target - cur) * Math.min(1.0F, dt * 14.0F);
         if (Math.abs(cur - target) < 0.01F) {
            cur = target;
         }

         this.knobs.put(key, cur);
         return cur;
      }
   }

   private boolean shaderActive() {
      return this.gui().shader.get() != GuiShaderBackground.Mode.Off;
   }

   private List<Category> categories() {
      List<Category> out = new ArrayList<>();

      for (Category c : Modules.loopCategories()) {
         out.add(c);
      }

      return out;
   }

   private Category selectedCat() {
      if (selectedCatName != null) {
         if (selectedCatName.equals(FAVORITES.name)) {
            return FAVORITES;
         }

         for (Category c : this.categories()) {
            if (c.name.equals(selectedCatName)) {
               return c;
            }
         }
      }

      List<Category> cats = this.categories();
      return cats.isEmpty() ? FAVORITES : cats.get(0);
   }

   private List<Module> modsOf(Category cat) {
      List<Module> out = new ArrayList<>();
      if (cat == FAVORITES) {
         for (Module m : Modules.get().getAll()) {
            if (m.favorite) {
               out.add(m);
            }
         }
      } else {
         out.addAll(Modules.get().getGroup(cat));
      }

      if (!this.search.isEmpty()) {
         String q = this.search.toLowerCase();
         out.removeIf(mx -> !mx.title.toLowerCase().contains(q) && !mx.name.toLowerCase().contains(q));
      }

      return out;
   }

   private List<Module> searchAll() {
      List<Module> out = new ArrayList<>();
      String q = this.search.toLowerCase();

      for (Category c : this.categories()) {
         for (Module m : Modules.get().getGroup(c)) {
            if (m.title.toLowerCase().contains(q) || m.name.toLowerCase().contains(q)) {
               out.add(m);
            }
         }
      }

      return out;
   }

   private static String compactBind(String kb) {
      if (kb.startsWith("Mouse ")) {
         return "M" + kb.substring(6);
      } else {
         switch (kb) {
            case "Grave Accent":
               return "`";
            case "Left Bracket":
               return "[";
            case "Right Bracket":
               return "]";
            case "Semicolon":
               return ";";
            case "Apostrophe":
               return "'";
            case "Comma":
               return ",";
            case "Period":
               return ".";
            case "Slash":
               return "/";
            case "Backslash":
               return "\\";
            case "Minus":
               return "-";
            case "Equal":
               return "=";
            case "Space":
               return "SPC";
            case "Enter":
               return "ENT";
            case "Backspace":
               return "BKSP";
            case "Caps Lock":
               return "CAPS";
            case "Left Shift":
               return "LSHFT";
            case "Right Shift":
               return "RSHFT";
            case "Left Control":
               return "LCTRL";
            case "Right Control":
               return "RCTRL";
            case "Left Alt":
               return "LALT";
            case "Right Alt":
               return "RALT";
            default:
               return kb.length() > 6 ? kb.substring(0, 6) : kb;
         }
      }
   }

   private void openSettingsFor(Module m) {
      if (this.openModule != m) {
         this.drawer.reset();
         this.openModule = m;
         this.drawerScroll = 0;
         Modules.get().setModuleToBind(null);
      }
   }

   private void closeSettings() {
      if (this.openModule != null) {
         this.drawer.reset();
         this.openModule = null;
         Modules.get().setModuleToBind(null);
      }
   }

   private void toggle(Module m) {
      m.toggle();
   }

   private void drawItemIcon(DrawContext ctx, ItemStack icon, int x, int y, int size) {
      if (icon != null && !icon.isEmpty()) {
         Matrix3x2fStack m = ctx.getMatrices();
         m.pushMatrix();
         m.translate(x, y);
         m.scale(size / 16.0F, size / 16.0F);
         ctx.drawItem(icon, 0, 0);
         m.popMatrix();
      }
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (!this.shaderActive() && this.gui().modernBlur.get()) {
         super.renderBackground(context, mouseX, mouseY, deltaTicks);
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      Gui gui = this.gui();
      if (gui.style.get() != Gui.GuiStyle.Modern) {
         this.client.setScreen(new NewGuiScreen());
      } else {
         this.lastMx = mouseX;
         this.lastMy = mouseY;
         long now = System.nanoTime();
         float dt = Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
         this.lastFrame = now;
         float g = this.client.getWindow().getScaleFactor();
         float user = gui.modernScale.get().floatValue();
         float geo = Math.max(0.35F, 0.575F * this.width / 460.0F * user);
         float txt = Math.max(0.5F, this.width * g / 1920.0F * user);
         this.t.refresh(gui, geo, txt);
         if (this.anim()) {
            this.openT = Math.min(1.0F, this.openT + dt / 0.16F);
         } else {
            this.openT = 1.0F;
         }

         this.t.alpha = ease(this.openT);
         boolean shaderOn = this.shaderActive();
         if (shaderOn) {
            GuiShaderBackground.renderFullscreen(
               gui.shader.get().ordinal(),
               gui.shaderOpacity.get().floatValue(),
               FontManager.get().getPrimaryColor() & 16777215,
               FontManager.get().getSecondaryColor() & 16777215
            );
         }

         super.render(context, mouseX, mouseY, deltaTicks);
         context.fill(0, 0, this.width, this.height, this.t.a(ModernTheme.withA(0, shaderOn ? 70 : (gui.modernBlur.get() ? 110 : 60))));
         this.hits.clear();
         this.centeredNow = gui.modernLayout.get() == Gui.ModernLayout.Centered;
         if (this.centeredNow) {
            this.renderCentered(context, mouseX, mouseY, dt);
         } else {
            this.renderPanels(context, mouseX, mouseY, dt);
         }
      }
   }

   private void renderCentered(DrawContext ctx, int mx, int my, float dt) {
      float breathe = this.anim() ? 0.965F + 0.035F * ease(this.openT) : 1.0F;
      this.W = Math.min(this.u(460.0F * breathe), this.width - this.u(16.0F));
      this.H = Math.min(this.u(272.0F * breathe), this.height - this.u(16.0F));
      this.wx = (this.width - this.W) / 2;
      this.wy = (this.height - this.H) / 2;
      this.t.rrect(ctx, this.wx - 1, this.wy + this.u(3.0F), this.W + 2, this.H + this.u(1.0F), 6.0F, this.t.shadow);
      this.t.rrect(ctx, this.wx, this.wy, this.W, this.H, 4.0F, this.t.win);
      this.t.rborder(ctx, this.wx, this.wy, this.W, this.H, 4.0F, this.t.winBorder);
      int hdrH = this.u(23.0F);
      float lg = this.t.ts(17.0F);
      int bx = this.wx + this.u(14.0F);
      this.t.textC(ctx, "REVIVE", bx, this.wy, hdrH, this.t.text, lg);
      int bw = this.t.tw("REVIVE", lg);
      this.t.chip(ctx, "1.21.11", bx + bw + this.u(6.0F), this.wy + hdrH / 2, this.t.text3, this.t.line);
      int gearX = this.wx + this.W - this.u(30.0F);
      int closeX = this.wx + this.W - this.u(12.0F);
      int cy = this.wy + hdrH / 2;
      boolean gearHov = Math.abs(mx - gearX) < this.u(7.0F) && Math.abs(my - cy) < this.u(7.0F);
      boolean closeHov = Math.abs(mx - closeX) < this.u(7.0F) && Math.abs(my - cy) < this.u(7.0F);
      this.t.gear(ctx, gearX, cy, this.u(4.5F), gearHov ? this.t.text : this.t.text3);
      this.t.xmark(ctx, closeX, cy, this.u(2.6F), closeHov ? this.t.text : this.t.text3);
      this.hits
         .add(
            new ModernGuiScreen.Hit(
               gearX - this.u(7.0F), cy - this.u(7.0F), this.u(14.0F), this.u(14.0F), () -> tab = ModernGuiScreen.Tab.SETTINGS, null, "gear"
            )
         );
      this.hits.add(new ModernGuiScreen.Hit(closeX - this.u(7.0F), cy - this.u(7.0F), this.u(14.0F), this.u(14.0F), this::close, null, "close"));
      int tabY = this.wy + hdrH;
      int tabH = this.u(16.0F);
      float md = this.t.ts(13.0F);
      int tx = this.wx + this.u(14.0F);
      int activeX = tx;
      int activeW = 0;

      for (ModernGuiScreen.Tab tb : ModernGuiScreen.Tab.values()) {
         String label = tb == ModernGuiScreen.Tab.MODULES
            ? "Modules"
            : (tb == ModernGuiScreen.Tab.HUD ? "HUD" : (tb == ModernGuiScreen.Tab.PROFILES ? "Profiles" : "Settings"));
         int lw = this.t.tw(label, md);
         boolean hov = mx >= tx - this.u(4.0F) && mx < tx + lw + this.u(4.0F) && my >= tabY && my < tabY + tabH;
         this.t.textC(ctx, label, tx, tabY - this.u(1.0F), tabH, tb == tab ? this.t.text : (hov ? this.t.text2 : this.t.text3), md);
         if (tb == tab) {
            activeX = tx;
            activeW = lw;
         }

         this.hits.add(new ModernGuiScreen.Hit(tx - this.u(4.0F), tabY, lw + this.u(8.0F), tabH, () -> {
            tab = tb;
            this.searchFocused = false;
            this.profileNameFocused = false;
         }, null, "tab"));
         tx += lw + this.u(18.0F);
      }

      if (!(this.inkX < 0.0F) && this.anim()) {
         this.inkX = this.inkX + (activeX - this.inkX) * Math.min(1.0F, dt * 16.0F);
         this.inkW = this.inkW + (activeW - this.inkW) * Math.min(1.0F, dt * 16.0F);
      } else {
         this.inkX = activeX;
         this.inkW = activeW;
      }

      this.t
         .rect(
            ctx,
            Math.round(this.inkX),
            tabY + tabH - this.u(1.2F),
            Math.round(this.inkW),
            Math.max(1, this.u(1.2F)),
            this.t.accentControls ? this.t.accent : this.t.text
         );
      this.t.hline(ctx, this.wx, tabY + tabH, this.W, this.t.line);
      this.bodyTop = tabY + tabH + 1;
      switch (tab) {
         case MODULES:
            this.renderModulesTab(ctx, mx, my, dt);
            break;
         case HUD:
            this.renderHudTab(ctx, mx, my);
            break;
         case PROFILES:
            this.renderProfilesTab(ctx, mx, my);
            break;
         case SETTINGS:
            this.renderSettingsTab(ctx, mx, my);
      }
   }

   private void renderModulesTab(DrawContext ctx, int mx, int my, float dt) {
      float lg = this.t.ts(17.0F);
      float md = this.t.ts(13.0F);
      float sm = this.t.ts(11.5F);
      float xs = this.t.ts(10.0F);
      int sideW = this.u(118.0F);
      this.t.text(ctx, "Modules", this.wx + this.u(16.0F), this.bodyTop + this.u(9.0F), this.t.text, lg);
      int rx = this.wx + this.u(10.0F);
      int rw = sideW - this.u(16.0F);
      int ry = this.bodyTop + this.u(27.0F);
      int rh = this.u(19.0F);
      Category sel = this.selectedCat();
      List<Category> cats = new ArrayList<>();
      cats.add(FAVORITES);
      cats.addAll(this.categories());

      for (Category c : cats) {
         boolean active = c == sel && this.search.isEmpty();
         boolean hov = mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
         if (active) {
            this.t.rrect(ctx, rx, ry, rw, rh, 2.5F, this.t.cardActive);
         } else if (hov) {
            this.t.rrect(ctx, rx, ry, rw, rh, 2.5F, ModernTheme.withA(16777215, 6));
         }

         int iconX = rx + this.u(5.0F);
         int iconS = this.u(10.0F);
         if (c == FAVORITES) {
            this.t.star(ctx, iconX + iconS / 2.0F, ry + rh / 2.0F, this.u(4.2F), this.t.accent);
         } else {
            this.drawItemIcon(ctx, c.icon, iconX, ry + (rh - iconS) / 2, iconS);
         }

         int col = active ? this.t.text : (hov ? this.t.text2 : this.t.text3);
         this.t.textC(ctx, c.name, rx + this.u(20.0F), ry, rh, col, md);
         List<Module> all = c == FAVORITES ? this.modsOfNoSearch(FAVORITES) : Modules.get().getGroup(c);
         int on = 0;

         for (Module m : all) {
            if (m.isActive()) {
               on++;
            }
         }

         if (active || hov) {
            String cnt = on + "/" + all.size();
            this.t.textC(ctx, cnt, rx + rw - this.u(14.0F) - this.t.tw(cnt, xs), ry, rh, this.t.text3, xs);
         }

         this.t.chevron(ctx, rx + rw - this.u(6.0F), ry + rh / 2.0F, this.u(3.5F), false, ModernTheme.withA(col, 150));
         this.hits.add(new ModernGuiScreen.Hit(rx, ry, rw, rh, () -> {
            selectedCatName = c.name;
            this.search = "";
            this.searchFocused = false;
            this.closeSettings();
            scrolls.put("list", 0);
         }, null, "cat"));
         ry += rh + this.u(1.0F);
      }

      int mx0 = this.wx + sideW + this.u(4.0F);
      int mw = this.W - sideW - this.u(4.0F) - this.u(10.0F);
      int sy = this.bodyTop + this.u(9.0F);
      int sh = this.u(19.0F);
      this.t.rrect(ctx, mx0, sy, mw, sh, 2.5F, this.t.field);
      this.t.rborder(ctx, mx0, sy, mw, sh, 2.5F, this.searchFocused ? this.t.lineStrong : this.t.fieldBorder);
      this.t.ring(ctx, mx0 + this.u(9.0F), sy + sh / 2.0F - this.u(0.5F), this.u(2.8F), 1, this.t.text3);
      this.t.rect(ctx, mx0 + this.u(11.0F), sy + sh / 2 + this.u(1.5F), this.u(2.0F), 1, this.t.text3);
      String disp;
      int dcol;
      if (this.searchFocused) {
         boolean b = (System.currentTimeMillis() / 530L & 1L) == 0L;
         disp = this.search + (b ? "|" : "");
         dcol = this.t.text;
      } else if (this.search.isEmpty()) {
         disp = "Search modules...";
         dcol = this.t.text3;
      } else {
         disp = this.search;
         dcol = this.t.text;
      }

      ctx.enableScissor(mx0 + this.u(16.0F), sy, mx0 + mw - this.u(14.0F), sy + sh);
      this.t.textC(ctx, disp, mx0 + this.u(17.0F), sy, sh, dcol, sm);
      ctx.disableScissor();
      this.t.chip(ctx, "/", mx0 + mw - this.u(11.0F), sy + sh / 2, this.t.text3, this.t.line);
      this.hits.add(new ModernGuiScreen.Hit(mx0, sy, mw, sh, () -> {
         this.searchFocused = true;
         this.profileNameFocused = false;
      }, null, "search"));
      this.listX = mx0;
      this.listY = sy + sh + this.u(6.0F);
      this.listW = mw;
      this.listH = this.wy + this.H - this.listY - this.u(6.0F);
      List<Module> mods = this.search.isEmpty() ? this.modsOf(sel) : this.searchAll();
      int rowH = this.u(22.0F);
      int gap = this.u(3.0F);
      int contentH = mods.size() * (rowH + gap) - gap;
      int maxScroll = Math.max(0, contentH - this.listH);
      int scroll = Math.max(0, Math.min(scrolls.getOrDefault("list", 0), maxScroll));
      scrolls.put("list", scroll);
      boolean drawerBlocks = this.drawerT > 0.05F;
      ctx.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
      int y = this.listY - scroll;
      if (mods.isEmpty()) {
         this.t
            .text(
               ctx,
               this.search.isEmpty() ? (sel == FAVORITES ? "Star a module in its settings to pin it here." : "No modules here.") : "No modules match.",
               this.listX + this.u(6.0F),
               this.listY + this.u(8.0F),
               this.t.text3,
               sm
            );
      }

      for (Module mxx : mods) {
         if (y + rowH >= this.listY && y <= this.listY + this.listH) {
            boolean hovx = !drawerBlocks
               && mx >= this.listX
               && mx < this.listX + this.listW
               && my >= y
               && my < y + rowH
               && my >= this.listY
               && my < this.listY + this.listH;
            boolean open = mxx == this.openModule;
            this.t.rrect(ctx, this.listX, y, this.listW, rowH, 2.5F, open ? this.t.cardActive : (hovx ? this.t.cardHover : this.t.card));
            if (open) {
               this.t.rborder(ctx, this.listX, y, this.listW, rowH, 2.5F, this.t.lineStrong);
            }

            int nx = this.listX + this.u(8.0F);
            int nameW = Math.min(this.t.tw(mxx.title, md), this.u(70.0F));
            String name = this.t.ellipsize(mxx.title, this.u(70.0F), md);
            this.t.textC(ctx, name, nx, y, rowH, this.t.text, md);
            int after = nx + nameW + this.u(5.0F);
            if (mxx.keybind.isSet()) {
               after += this.t.chip(ctx, compactBind(mxx.keybind.toString()), after, y + rowH / 2, this.t.text3, this.t.line) + this.u(5.0F);
            }

            int swW = this.u(16.0F);
            int swH = this.u(9.0F);
            int swX = this.listX + this.listW - this.u(28.0F);
            int kebX = this.listX + this.listW - this.u(9.0F);
            int sumX = Math.max(after, this.listX + this.u(80.0F));
            int sumW = swX - this.u(8.0F) - sumX;
            if (sumW > this.u(20.0F)) {
               ctx.enableScissor(sumX, y, sumX + sumW, y + rowH);
               this.t.textC(ctx, this.t.ellipsize(ModernSettings.summary(mxx), sumW, sm), sumX, y, rowH, this.t.text3, sm);
               ctx.disableScissor();
            }

            this.t.switchCtl(ctx, swX, y + (rowH - swH) / 2, swW, swH, this.knob(mxx, mxx.isActive(), dt));
            boolean kebHov = hovx && mx >= kebX - this.u(6.0F);
            if (kebHov) {
               this.t.rrect(ctx, kebX - this.u(6.0F), y + this.u(4.0F), this.u(12.0F), rowH - this.u(8.0F), 2.0F, ModernTheme.withA(16777215, 12));
            }

            this.t.kebab(ctx, kebX, y + rowH / 2.0F, kebHov ? this.t.text : this.t.text3);
            if (!drawerBlocks && y >= this.listY - rowH && y < this.listY + this.listH) {
               int yy = Math.max(y, this.listY);
               int hh = Math.min(y + rowH, this.listY + this.listH) - Math.max(y, this.listY);
               this.hits.add(new ModernGuiScreen.Hit(this.listX, yy, this.listW, hh, () -> this.openSettingsFor(mxx), () -> this.openSettingsFor(mxx), "row"));
               this.hits
                  .add(new ModernGuiScreen.Hit(swX - this.u(4.0F), yy, swW + this.u(8.0F), hh, () -> this.toggle(mxx), () -> this.openSettingsFor(mxx), "sw"));
               this.hits
                  .add(new ModernGuiScreen.Hit(kebX - this.u(7.0F), yy, this.u(14.0F), hh, () -> this.openSettingsFor(mxx), () -> this.openSettingsFor(mxx), "keb"));
            }
         }

         y += rowH + gap;
      }

      ctx.disableScissor();
      if (maxScroll > 0) {
         int trackH = this.listH;
         int barH = Math.max(this.u(10.0F), trackH * this.listH / Math.max(1, contentH));
         int barY = this.listY + (trackH - barH) * scroll / Math.max(1, maxScroll);
         this.t.rrect(ctx, this.listX + this.listW - this.u(2.0F), barY, this.u(1.5F), barH, 1.0F, this.t.lineStrong);
      }

      if (this.drawerT > 0.001F) {
         this.t
            .rect(
               ctx,
               this.listX - this.u(2.0F),
               this.listY - this.u(2.0F),
               this.listW + this.u(4.0F),
               this.listH + this.u(4.0F),
               ModernTheme.withA(this.t.win & 16777215, Math.round(165.0F * ease(this.drawerT)))
            );
      }

      if (drawerBlocks) {
         this.hits
            .add(
               new ModernGuiScreen.Hit(this.wx, this.bodyTop, this.W, this.H - (this.bodyTop - this.wy), this::closeSettings, this::closeSettings, "dimclose")
            );
      }

      this.renderDrawer(ctx, mx, my, dt);
   }

   private List<Module> modsOfNoSearch(Category cat) {
      List<Module> out = new ArrayList<>();

      for (Module m : Modules.get().getAll()) {
         if (m.favorite) {
            out.add(m);
         }
      }

      return out;
   }

   private void renderDrawer(DrawContext ctx, int mx, int my, float dt) {
      float target = this.openModule != null ? 1.0F : 0.0F;
      if (!this.anim()) {
         this.drawerT = target;
      } else {
         this.drawerT = this.drawerT + (target - this.drawerT) * Math.min(1.0F, dt * 14.0F);
         if (Math.abs(this.drawerT - target) < 0.005F) {
            this.drawerT = target;
         }
      }

      if (!(this.drawerT <= 0.001F)) {
         Module m = this.openModule != null ? this.openModule : this.lastDrawerModule;
         if (m != null) {
            this.lastDrawerModule = m;
            this.drawerW = this.u(159.0F);
            this.drawerX = this.wx + this.W - Math.round(this.drawerW * ease(this.drawerT));
            int top = this.bodyTop;
            int bottom = this.wy + this.H;
            float md = this.t.ts(13.0F);
            float xxs = this.t.ts(9.0F);
            ctx.enableScissor(this.drawerX, top, this.wx + this.W, bottom);
            this.t.rect(ctx, this.drawerX, top, this.drawerW, bottom - top, ModernTheme.withA(ModernTheme.lift(this.t.win, -2), 250));
            this.t.vline(ctx, this.drawerX, top, bottom - top, this.t.line);
            int hh = this.u(24.0F);
            boolean resetHov = mx >= this.drawerX + this.u(3.0F) && mx < this.drawerX + this.u(15.0F) && my >= top && my < top + hh;
            this.t.reset(ctx, this.drawerX + this.u(9.0F), top + hh / 2.0F, this.u(3.6F), resetHov ? this.t.text : this.t.text3);
            int starX = this.drawerX + this.drawerW - this.u(38.0F);
            boolean starHov = Math.abs(mx - starX) < this.u(6.0F) && my >= top && my < top + hh;
            this.t.star(ctx, starX, top + hh / 2.0F, this.u(4.2F), m.favorite ? this.t.accent : (starHov ? this.t.text2 : this.t.text3));
            int pillW = this.u(22.0F);
            int pillH = this.u(9.0F);
            int pillX = this.drawerX + this.drawerW - this.u(27.0F);
            int pillY = top + (hh - pillH) / 2;
            boolean on = m.isActive();
            this.t.rrect(ctx, pillX, pillY, pillW, pillH, pillH / 2.0F, on ? this.t.onTrack : this.t.pillBg);
            String pl = on ? "ON" : "OFF";
            this.t.textC(ctx, pl, pillX + (pillW - this.t.tw(pl, xxs)) / 2, pillY, pillH, on ? this.t.onKnob : this.t.text2, xxs);
            this.t
               .textC(
                  ctx,
                  this.t.ellipsize(m.title, starX - this.u(8.0F) - (this.drawerX + this.u(18.0F)), md),
                  this.drawerX + this.u(18.0F),
                  top,
                  hh,
                  this.t.text,
                  md
               );
            this.t.hline(ctx, this.drawerX, top + hh, this.drawerW, this.t.line);
            this.hits.add(new ModernGuiScreen.Hit(this.drawerX, top, this.drawerW, hh, () -> {}, () -> {}, "drawerhdr"));
            this.hits.add(new ModernGuiScreen.Hit(this.drawerX + this.u(3.0F), top, this.u(12.0F), hh, () -> {
               m.settings.reset();
               this.drawer.reset();
            }, null, "reset"));
            this.hits.add(new ModernGuiScreen.Hit(starX - this.u(6.0F), top, this.u(12.0F), hh, () -> m.favorite = !m.favorite, null, "star"));
            this.hits.add(new ModernGuiScreen.Hit(pillX, top, pillW + this.u(4.0F), hh, () -> this.toggle(m), null, "pill"));
            int cx = this.drawerX + this.u(8.0F);
            int cw = this.drawerW - this.u(16.0F);
            int areaTop = top + hh + 1;
            int areaBot = bottom - this.u(1.0F);
            this.drawerContentH = this.drawer.layout(m.settings, m, cw);
            int maxScroll = Math.max(0, this.drawerContentH + this.u(12.0F) - (areaBot - areaTop));
            this.drawerScroll = Math.max(0, Math.min(this.drawerScroll, maxScroll));
            int cy0 = areaTop + this.u(6.0F) - this.drawerScroll;
            ctx.enableScissor(this.drawerX + 1, areaTop, this.wx + this.W, areaBot);
            this.drawer.render(ctx, cx, cy0, cw, mx, my, areaTop, areaBot);
            ctx.disableScissor();
            if (maxScroll > 0) {
               int trackH = areaBot - areaTop;
               int total = this.drawerContentH + this.u(12.0F);
               int barH = Math.max(this.u(10.0F), trackH * trackH / Math.max(1, total));
               int barY = areaTop + (trackH - barH) * this.drawerScroll / Math.max(1, maxScroll);
               this.t.rrect(ctx, this.drawerX + this.drawerW - this.u(2.5F), barY, this.u(1.5F), barH, 1.0F, this.t.lineStrong);
            }

            ctx.disableScissor();
            this.hits
               .add(
                  new ModernGuiScreen.Hit(
                     this.drawerX, areaTop, this.drawerW, areaBot - areaTop, null, null, new Object[]{"drawer", cx, cy0, cw, areaTop, areaBot}
                  )
               );
         }
      }
   }

   private void renderHudTab(DrawContext ctx, int mx, int my) {
      float lg = this.t.ts(17.0F);
      float sm = this.t.ts(11.5F);
      float md = this.t.ts(13.0F);
      int x = this.wx + this.u(16.0F);
      this.t.text(ctx, "HUD", x, this.bodyTop + this.u(9.0F), this.t.text, lg);
      this.t
         .text(
            ctx,
            "Arrange and configure the in-game overlay: module list, coordinates, armor, totem count and more.",
            x,
            this.bodyTop + this.u(24.0F),
            this.t.text2,
            sm
         );
      this.t
         .text(
            ctx,
            "Elements are edited on the HUD editor screen, where they sit exactly where they render in game.",
            x,
            this.bodyTop + this.u(33.0F),
            this.t.text3,
            sm
         );
      int bw = this.u(92.0F);
      int bh = this.u(18.0F);
      int by = this.bodyTop + this.u(48.0F);
      boolean hov = mx >= x && mx < x + bw && my >= by && my < by + bh;
      this.t.rrect(ctx, x, by, bw, bh, 3.0F, hov ? ModernTheme.lift(this.t.text, -20) : this.t.text);
      String label = "OPEN HUD EDITOR";
      this.t.textC(ctx, label, x + (bw - this.t.tw(label, this.t.ts(10.0F))) / 2, by, bh, this.t.onKnob, this.t.ts(10.0F));
      this.hits.add(new ModernGuiScreen.Hit(x, by, bw, bh, () -> this.client.setScreen(new NewHudEditorScreen()), null, "hud"));
      Module hud = Modules.get().get("hud");
      if (hud != null) {
         int sy = by + bh + this.u(12.0F);
         int sw = Math.min(this.u(230.0F), this.W - this.u(32.0F));
         this.t.text(ctx, "HUD settings", x, sy, this.t.text3, this.t.ts(10.0F));
         int top = sy + this.u(10.0F);
         int bot = this.wy + this.H - this.u(4.0F);
         this.guiContentH = this.guiSettings.layout(hud.settings, null, sw);
         int maxScroll = Math.max(0, this.guiContentH - (bot - top));
         this.guiScroll = Math.max(0, Math.min(this.guiScroll, maxScroll));
         int cy0 = top - this.guiScroll;
         ctx.enableScissor(x - this.u(2.0F), top, x + sw + this.u(4.0F), bot);
         this.guiSettings.render(ctx, x, cy0, sw, mx, my, top, bot);
         ctx.disableScissor();
         this.hits.add(new ModernGuiScreen.Hit(x, top, sw, bot - top, null, null, new Object[]{"gui", x, cy0, sw, top, bot}));
      }
   }

   private void renderProfilesTab(DrawContext ctx, int mx, int my) {
      float lg = this.t.ts(17.0F);
      float md = this.t.ts(13.0F);
      float sm = this.t.ts(11.5F);
      float xs = this.t.ts(10.0F);
      float xxs = this.t.ts(9.0F);
      int colW = (this.W - this.u(44.0F)) / 2;
      int lx = this.wx + this.u(16.0F);
      int rxx = lx + colW + this.u(12.0F);
      this.t.text(ctx, "Profiles", lx, this.bodyTop + this.u(9.0F), this.t.text, lg);
      int fy = this.bodyTop + this.u(24.0F);
      int fh = this.u(17.0F);
      int btnW = this.u(58.0F);
      int fw = colW - btnW - this.u(6.0F);
      this.t.rrect(ctx, lx, fy, fw, fh, 2.5F, this.t.field);
      this.t.rborder(ctx, lx, fy, fw, fh, 2.5F, this.profileNameFocused ? this.t.lineStrong : this.t.fieldBorder);
      String disp = this.profileNameFocused
         ? this.profileName + ((System.currentTimeMillis() / 530L & 1L) == 0L ? "|" : "")
         : (this.profileName.isEmpty() ? "New profile name..." : this.profileName);
      ctx.enableScissor(lx + this.u(2.0F), fy, lx + fw - this.u(2.0F), fy + fh);
      this.t.textC(ctx, disp, lx + this.u(6.0F), fy, fh, this.profileName.isEmpty() && !this.profileNameFocused ? this.t.text3 : this.t.text, sm);
      ctx.disableScissor();
      this.hits.add(new ModernGuiScreen.Hit(lx, fy, fw, fh, () -> {
         this.profileNameFocused = true;
         this.searchFocused = false;
      }, null, "pname"));
      int bxx = lx + fw + this.u(6.0F);
      boolean bhov = mx >= bxx && mx < bxx + btnW && my >= fy && my < fy + fh;
      this.t.rrect(ctx, bxx, fy, btnW, fh, 3.0F, bhov ? ModernTheme.lift(this.t.text, -20) : this.t.text);
      String bl = "+ NEW PROFILE";
      this.t.textC(ctx, bl, bxx + (btnW - this.t.tw(bl, xxs)) / 2, fy, fh, this.t.onKnob, xxs);
      this.hits.add(new ModernGuiScreen.Hit(bxx, fy, btnW, fh, this::createProfile, null, "pcreate"));
      List<Profile> profiles = new ArrayList<>(Profiles.get().getAll());
      int ry = fy + fh + this.u(8.0F);
      int rh = this.u(20.0F);
      int gap = this.u(3.0F);
      int listBottom = this.wy + this.H - this.u(6.0F);
      ctx.enableScissor(lx, ry, lx + colW, listBottom);
      int lscroll = Math.max(0, scrolls.getOrDefault("profiles", 0));
      ry -= lscroll;
      if (profiles.isEmpty()) {
         this.t
            .text(
               ctx, "No profiles yet. Name one above and press New Profile to save your current setup.", lx + this.u(2.0F), ry + this.u(2.0F), this.t.text3, xs
            );
      }

      for (Profile p : profiles) {
         String name = p.name.get();
         boolean selp = name.equals(selectedProfile);
         boolean hov = mx >= lx && mx < lx + colW && my >= ry && my < ry + rh;
         this.t.rrect(ctx, lx, ry, colW, rh, 2.5F, selp ? this.t.cardActive : (hov ? this.t.cardHover : this.t.card));
         if (selp) {
            this.t.rborder(ctx, lx, ry, colW, rh, 2.5F, this.t.lineStrong);
         }

         this.t.ring(ctx, lx + this.u(9.0F), ry + rh / 2.0F, this.u(3.0F), 1, selp ? this.t.text : this.t.text3);
         if (selp) {
            this.t.circle(ctx, lx + this.u(9.0F), ry + rh / 2.0F, this.u(1.4F), this.t.accentControls ? this.t.accent : this.t.text);
         }

         this.t.textC(ctx, this.t.ellipsize(name, colW - this.u(30.0F), md), lx + this.u(18.0F), ry, rh, selp ? this.t.text : this.t.text2, md);
         this.t.kebab(ctx, lx + colW - this.u(8.0F), ry + rh / 2.0F, this.t.text3);
         this.hits.add(new ModernGuiScreen.Hit(lx, ry, colW, rh, () -> selectedProfile = name, () -> selectedProfile = name, "prow"));
         ry += rh + gap;
      }

      ctx.disableScissor();
      int cyy = this.bodyTop + this.u(24.0F);
      int ch = this.wy + this.H - this.u(6.0F) - cyy;
      this.t.rrect(ctx, rxx, cyy, colW, ch, 3.0F, this.t.card);
      Profile sp = selectedProfile == null ? null : Profiles.get().get(selectedProfile);
      if (sp == null) {
         this.t.text(ctx, "Select a profile", rxx + this.u(10.0F), cyy + this.u(10.0F), this.t.text2, md);
         this.t.text(ctx, "Load applies it, Save overwrites it with your current setup.", rxx + this.u(10.0F), cyy + this.u(22.0F), this.t.text3, xs);
      } else {
         this.t.text(ctx, sp.name.get(), rxx + this.u(10.0F), cyy + this.u(9.0F), this.t.text, md);
         this.t.hline(ctx, rxx + this.u(8.0F), cyy + this.u(22.0F), colW - this.u(16.0F), this.t.line);
         int iy = cyy + this.u(28.0F);
         this.t.text(ctx, "SAVES", rxx + this.u(10.0F), iy, this.t.text3, xs);
         iy += this.u(9.0F);
         this.t
            .text(
               ctx,
               (sp.modules.get() ? "Modules" : "")
                  + (sp.modules.get() && sp.hud.get() ? ", " : "")
                  + (sp.hud.get() ? "HUD" : "")
                  + (!sp.modules.get() && !sp.hud.get() ? "Nothing selected" : ""),
               rxx + this.u(10.0F),
               iy,
               this.t.text2,
               sm
            );
         iy += this.u(14.0F);
         String[] labels = new String[]{"LOAD", "SAVE", "DELETE"};
         int bwid = (colW - this.u(20.0F) - this.u(8.0F)) / 3;
         int bh = this.u(16.0F);

         for (int i = 0; i < 3; i++) {
            int bx2 = rxx + this.u(10.0F) + i * (bwid + this.u(4.0F));
            boolean hovx = mx >= bx2 && mx < bx2 + bwid && my >= iy && my < iy + bh;
            boolean primary = i == 0;
            if (primary) {
               this.t.rrect(ctx, bx2, iy, bwid, bh, 3.0F, hovx ? ModernTheme.lift(this.t.text, -20) : this.t.text);
            } else {
               this.t.rrect(ctx, bx2, iy, bwid, bh, 3.0F, hovx ? this.t.cardHover : this.t.field);
               this.t.rborder(ctx, bx2, iy, bwid, bh, 3.0F, this.t.fieldBorder);
            }

            this.t.textC(ctx, labels[i], bx2 + (bwid - this.t.tw(labels[i], xxs)) / 2, iy, bh, primary ? this.t.onKnob : this.t.text, xxs);
            int idx = i;
            this.hits.add(new ModernGuiScreen.Hit(bx2, iy, bwid, bh, () -> {
               if (idx == 0) {
                  sp.load();
               } else if (idx == 1) {
                  sp.save();
               } else {
                  Profiles.get().remove(sp);
                  selectedProfile = null;
               }
            }, null, "pbtn"));
         }

         iy += bh + this.u(10.0F);
         this.t.text(ctx, "Load-on-join servers and what a profile saves are edited from its settings.", rxx + this.u(10.0F), iy, this.t.text3, xs);
      }
   }

   private void createProfile() {
      String name = this.profileName.trim();
      if (name.isEmpty()) {
         this.profileNameFocused = true;
      } else if (Profiles.get().get(name) != null) {
         selectedProfile = Profiles.get().get(name).name.get();
      } else {
         Profile p = new Profile();
         p.name.set(name);
         Profiles.get().add(p);
         selectedProfile = name;
         this.profileName = "";
         this.profileNameFocused = false;
      }
   }

   private void renderSettingsTab(DrawContext ctx, int mx, int my) {
      float lg = this.t.ts(17.0F);
      int x = this.wx + this.u(16.0F);
      this.t.text(ctx, "Settings", x, this.bodyTop + this.u(9.0F), this.t.text, lg);
      int sw = Math.min(this.u(236.0F), this.W - this.u(32.0F));
      int top = this.bodyTop + this.u(26.0F);
      int bot = this.wy + this.H - this.u(4.0F);
      this.guiContentH = this.guiSettings.layout(this.gui().settings, null, sw);
      int maxScroll = Math.max(0, this.guiContentH + this.u(8.0F) - (bot - top));
      this.guiScroll = Math.max(0, Math.min(this.guiScroll, maxScroll));
      int cy0 = top - this.guiScroll;
      ctx.enableScissor(x - this.u(2.0F), top, x + sw + this.u(4.0F), bot);
      this.guiSettings.render(ctx, x, cy0, sw, mx, my, top, bot);
      ctx.disableScissor();
      if (maxScroll > 0) {
         int trackH = bot - top;
         int total = this.guiContentH + this.u(8.0F);
         int barH = Math.max(this.u(10.0F), trackH * trackH / Math.max(1, total));
         int barY = top + (trackH - barH) * this.guiScroll / Math.max(1, maxScroll);
         this.t.rrect(ctx, x + sw + this.u(2.0F), barY, this.u(1.5F), barH, 1.0F, this.t.lineStrong);
      }

      this.hits.add(new ModernGuiScreen.Hit(x, top, sw, bot - top, null, null, new Object[]{"gui", x, cy0, sw, top, bot}));
      int gx = x + sw + this.u(16.0F);
      if (gx + this.u(120.0F) < this.wx + this.W) {
         float sm = this.t.ts(11.5F);
         float xs = this.t.ts(10.0F);
         this.t.text(ctx, "GUI style picks which GUI opens: Classic and Clean are the column GUIs, Modern is this one.", gx, top, this.t.text2, xs);
         int var18 = top + this.u(9.0F);
         this.t.text(ctx, "Modern layout switches between floating panels and this centred window.", gx, var18, this.t.text2, xs);
         var18 += this.u(9.0F);
         this.t.text(ctx, "Every Modern colour is under Modern Colours; accent-on-controls pushes the accent into the controls.", gx, var18, this.t.text2, xs);
         var18 += this.u(9.0F);
         this.t.text(ctx, "Shader sets an animated background; Modern blur blurs the game when the shader is off.", gx, var18, this.t.text2, xs);
      }
   }

   private ModernGuiScreen.Panel makePanel(ModernGuiScreen.PKind kind, String key, Category cat, int defX, int defY, int w, int headerH) {
      ModernGuiScreen.Panel p = new ModernGuiScreen.Panel();
      p.kind = kind;
      p.key = key;
      p.cat = cat;
      p.w = w;
      p.headerH = headerH;
      if (NewGuiLayout.has(key)) {
         p.x = NewGuiLayout.getX(key);
         p.y = NewGuiLayout.getY(key);
      } else {
         p.x = defX;
         p.y = defY;
         NewGuiLayout.set(key, p.x, p.y);
      }

      p.x = Math.max(this.u(10.0F) - w, Math.min(p.x, this.width - this.u(20.0F)));
      p.y = Math.max(this.u(4.0F), Math.min(p.y, this.height - headerH));
      p.contentTop = p.y + headerH;
      p.collapsed = NewGuiLayout.isCollapsed(key);
      return p;
   }

   private void finishPanel(ModernGuiScreen.Panel p, int contentH, int pad) {
      p.contentH = contentH;
      if (p.collapsed) {
         p.visH = 0;
         p.maxScroll = 0;
         p.h = p.headerH;
      } else {
         int cap = Math.max(this.u(30.0F), this.height - p.contentTop - this.u(10.0F));
         p.visH = Math.min(contentH + pad, cap);
         p.maxScroll = Math.max(0, contentH + pad - p.visH);
         p.h = p.headerH + p.visH;
      }
   }

   private boolean panelHidden(String key) {
      return hiddenPanels.getOrDefault(key, false);
   }

   private void buildPanels() {
      this.panelsThisFrame.clear();
      int rowH = this.u(17.0F);
      int gap = this.u(0.0F);
      ModernGuiScreen.Panel nav = this.makePanel(ModernGuiScreen.PKind.NAV, "modern:nav", null, this.u(20.0F), this.u(35.0F), this.u(115.0F), this.u(22.0F));
      List<Category> cats = new ArrayList<>();
      cats.add(FAVORITES);
      cats.addAll(this.categories());
      this.finishPanel(nav, this.u(23.0F) + cats.size() * this.u(19.0F), this.u(6.0F));
      this.panelsThisFrame.add(nav);
      int idx = 0;

      for (Category c : cats) {
         String key = "modern:cat:" + c.name;
         if (c == FAVORITES && !NewGuiLayout.has(key) && !hiddenPanels.containsKey(key)) {
            hiddenPanels.put(key, true);
         }

         if (this.panelHidden(key)) {
            idx++;
         } else {
            int defX = this.u(150.0F) + idx % 6 * (this.u(98.0F) + this.u(9.0F));
            int defY = this.u(45.0F) + idx / 6 * this.u(120.0F);
            ModernGuiScreen.Panel p = this.makePanel(ModernGuiScreen.PKind.CAT, key, c, defX, defY, this.u(98.0F), this.u(20.0F));
            this.finishPanel(p, this.modsOf(c).size() * rowH, this.u(6.0F));
            this.panelsThisFrame.add(p);
            idx++;
         }
      }

      ModernGuiScreen.Panel prof = this.makePanel(
         ModernGuiScreen.PKind.PROFILES, "modern:profiles", null, this.width - this.u(116.0F) - this.u(20.0F), this.u(45.0F), this.u(116.0F), this.u(20.0F)
      );
      int pn = Profiles.get().getAll().size();
      this.finishPanel(prof, this.u(24.0F) + Math.max(1, pn) * this.u(19.0F) + (selectedProfile != null ? this.u(18.0F) : 0), this.u(8.0F));
      this.panelsThisFrame.add(prof);
      if (settingsPanelOpen) {
         ModernGuiScreen.Panel sp = this.makePanel(
            ModernGuiScreen.PKind.SETTINGS, "modern:settings", null, this.u(20.0F), nav.y + nav.h + this.u(10.0F), this.u(130.0F), this.u(20.0F)
         );
         this.guiContentH = this.guiSettings.layout(this.gui().settings, null, sp.w - this.u(14.0F));
         this.finishPanel(sp, this.guiContentH, this.u(10.0F));
         this.panelsThisFrame.add(sp);
      }

      this.panelsThisFrame.sort((a, b) -> {
         int ra = a.kind == ModernGuiScreen.PKind.SETTINGS ? 1000000 : panelFront.indexOf(a.key);
         int rb = b.kind == ModernGuiScreen.PKind.SETTINGS ? 1000000 : panelFront.indexOf(b.key);
         return Integer.compare(ra, rb);
      });
      if (this.openModule != null) {
         ModernGuiScreen.Panel pop = this.makePanel(
            ModernGuiScreen.PKind.POPUP, "modern:popup", null, this.width / 2 - this.u(128.0F) / 2, this.u(60.0F), this.u(128.0F), this.u(22.0F)
         );
         this.drawerContentH = this.drawer.layout(this.openModule.settings, this.openModule, pop.w - this.u(14.0F));
         this.finishPanel(pop, this.drawerContentH, this.u(10.0F));
         this.panelsThisFrame.add(pop);
      }
   }

   private void bringToFront(String key) {
      panelFront.remove(key);
      panelFront.add(key);
   }

   private void renderPanels(DrawContext ctx, int mx, int my, float dt) {
      this.buildPanels();

      for (ModernGuiScreen.Panel p : this.panelsThisFrame) {
         switch (p.kind) {
            case NAV:
               this.renderNav(ctx, p, mx, my);
               break;
            case CAT:
               this.renderCatPanel(ctx, p, mx, my, dt);
               break;
            case PROFILES:
               this.renderProfilesPanel(ctx, p, mx, my);
               break;
            case SETTINGS:
               this.renderSettingsPanel(ctx, p, mx, my);
               break;
            case POPUP:
               this.renderPopup(ctx, p, mx, my);
         }
      }

      float xs = this.t.ts(10.0F);
      String legend = "LMB toggle  -  RMB or dots: settings  -  drag headers  -  gear: GUI settings";
      this.t.text(ctx, legend, this.width - this.t.tw(legend, xs) - this.u(10.0F), this.height - this.t.th(xs) - this.u(6.0F), this.t.text3, xs);
   }

   private void panelFrame(DrawContext ctx, ModernGuiScreen.Panel p) {
      this.t.rrect(ctx, p.x - 1, p.y + this.u(2.0F), p.w + 2, p.h + this.u(1.0F), 5.0F, this.t.shadow);
      this.t.rrect(ctx, p.x, p.y, p.w, p.h, 3.0F, this.t.win);
      this.t.rborder(ctx, p.x, p.y, p.w, p.h, 3.0F, this.t.winBorder);
      if (!p.collapsed) {
         this.t.hline(ctx, p.x + 1, p.contentTop, p.w - 2, this.t.line);
      }

      GuiTextMesh.pushOccluder(p.x, p.y, p.x + p.w, p.y + p.h);
   }

   private void addDragHit(ModernGuiScreen.Panel p, int fromX, int toX) {
      this.hits.add(new ModernGuiScreen.Hit(fromX, p.y, toX - fromX, p.headerH, () -> {
         this.dragKey = p.key;
         this.dragOffX = this.lastMx - p.x;
         this.dragOffY = this.lastMy - p.y;
         this.bringToFront(p.key);
      }, () -> {
         if (p.kind == ModernGuiScreen.PKind.CAT || p.kind == ModernGuiScreen.PKind.PROFILES) {
            NewGuiLayout.setCollapsed(p.key, !p.collapsed);
            NewGuiLayout.save();
         }
      }, "drag"));
   }

   private void renderNav(DrawContext ctx, ModernGuiScreen.Panel p, int mx, int my) {
      this.panelFrame(ctx, p);
      float lg = this.t.ts(17.0F);
      float md = this.t.ts(13.0F);
      float sm = this.t.ts(11.5F);
      this.t.textC(ctx, "REVIVE", p.x + this.u(9.0F), p.y, p.headerH, this.t.text, lg * 0.9F);
      int gearX = p.x + p.w - this.u(24.0F);
      int closeX = p.x + p.w - this.u(9.0F);
      int cy = p.y + p.headerH / 2;
      boolean gearHov = Math.abs(mx - gearX) < this.u(6.0F) && my >= p.y && my < p.contentTop;
      boolean closeHov = Math.abs(mx - closeX) < this.u(6.0F) && my >= p.y && my < p.contentTop;
      this.t.gear(ctx, gearX, cy, this.u(4.2F), !gearHov && !settingsPanelOpen ? this.t.text3 : this.t.text);
      this.t.xmark(ctx, closeX, cy, this.u(2.4F), closeHov ? this.t.text : this.t.text3);
      this.hits.add(new ModernGuiScreen.Hit(gearX - this.u(6.0F), p.y, this.u(12.0F), p.headerH, () -> settingsPanelOpen = !settingsPanelOpen, null, "gear"));
      this.hits.add(new ModernGuiScreen.Hit(closeX - this.u(6.0F), p.y, this.u(12.0F), p.headerH, this::close, null, "close"));
      this.addDragHit(p, p.x, gearX - this.u(6.0F));
      if (!p.collapsed) {
         int sy = p.contentTop + this.u(4.0F);
         int sh = this.u(15.0F);
         int sx = p.x + this.u(5.0F);
         int sw = p.w - this.u(10.0F);
         this.t.rrect(ctx, sx, sy, sw, sh, 2.5F, this.t.field);
         this.t.rborder(ctx, sx, sy, sw, sh, 2.5F, this.searchFocused ? this.t.lineStrong : this.t.fieldBorder);
         this.t.ring(ctx, sx + this.u(7.0F), sy + sh / 2.0F - this.u(0.5F), this.u(2.4F), 1, this.t.text3);
         String disp;
         int dcol;
         if (this.searchFocused) {
            boolean b = (System.currentTimeMillis() / 530L & 1L) == 0L;
            disp = this.search + (b ? "|" : "");
            dcol = this.t.text;
         } else if (this.search.isEmpty()) {
            disp = "Search modules...";
            dcol = this.t.text3;
         } else {
            disp = this.search;
            dcol = this.t.text;
         }

         ctx.enableScissor(sx + this.u(12.0F), sy, sx + sw - this.u(2.0F), sy + sh);
         this.t.textC(ctx, disp, sx + this.u(13.0F), sy, sh, dcol, sm);
         ctx.disableScissor();
         this.hits.add(new ModernGuiScreen.Hit(sx, sy, sw, sh, () -> {
            this.searchFocused = true;
            this.profileNameFocused = false;
         }, null, "search"));
         List<Category> cats = new ArrayList<>();
         cats.add(FAVORITES);
         cats.addAll(this.categories());
         int ry = sy + sh + this.u(4.0F);
         int rh = this.u(19.0F);

         for (Category c : cats) {
            String key = "modern:cat:" + c.name;
            boolean shown = !this.panelHidden(key);
            boolean hov = mx >= p.x && mx < p.x + p.w && my >= ry && my < ry + rh;
            if (hov) {
               this.t.rrect(ctx, p.x + this.u(4.0F), ry, p.w - this.u(8.0F), rh, 2.5F, ModernTheme.withA(16777215, 6));
            }

            int iconX = p.x + this.u(9.0F);
            int iconS = this.u(10.0F);
            if (c == FAVORITES) {
               this.t.star(ctx, iconX + iconS / 2.0F, ry + rh / 2.0F, this.u(4.2F), this.t.accent);
            } else {
               this.drawItemIcon(ctx, c.icon, iconX, ry + (rh - iconS) / 2, iconS);
            }

            int col = shown ? this.t.text : this.t.text3;
            this.t.textC(ctx, c.name, p.x + this.u(24.0F), ry, rh, hov ? this.t.text : col, md);
            this.t.chevron(ctx, p.x + p.w - this.u(9.0F), ry + rh / 2.0F, this.u(3.5F), false, ModernTheme.withA(col, 150));
            this.hits.add(new ModernGuiScreen.Hit(p.x, ry, p.w, rh, () -> {
               hiddenPanels.put(key, !this.panelHidden(key));
               this.bringToFront(key);
            }, null, "navcat"));
            ry += rh;
         }
      }
   }

   private void renderCatPanel(DrawContext ctx, ModernGuiScreen.Panel p, int mx, int my, float dt) {
      this.panelFrame(ctx, p);
      float md = this.t.ts(13.0F);
      float sm = this.t.ts(11.5F);
      int iconS = this.u(10.0F);
      if (p.cat == FAVORITES) {
         this.t.star(ctx, p.x + this.u(8.0F) + iconS / 2.0F, p.y + p.headerH / 2.0F, this.u(4.2F), this.t.accent);
      } else {
         this.drawItemIcon(ctx, p.cat.icon, p.x + this.u(8.0F), p.y + (p.headerH - iconS) / 2, iconS);
      }

      this.t.textC(ctx, p.cat.name, p.x + this.u(22.0F), p.y, p.headerH, this.t.text, md);
      int chevX = p.x + p.w - this.u(9.0F);
      boolean chevHov = Math.abs(mx - chevX) < this.u(6.0F) && my >= p.y && my < p.contentTop;
      this.t.chevron(ctx, chevX, p.y + p.headerH / 2.0F + (p.collapsed ? 0 : 0), this.u(4.0F), true, chevHov ? this.t.text : this.t.text3);
      this.hits.add(new ModernGuiScreen.Hit(chevX - this.u(6.0F), p.y, this.u(12.0F), p.headerH, () -> {
         NewGuiLayout.setCollapsed(p.key, !p.collapsed);
         NewGuiLayout.save();
      }, null, "collapse"));
      this.addDragHit(p, p.x, chevX - this.u(6.0F));
      if (!p.collapsed) {
         List<Module> mods = this.modsOf(p.cat);
         int rowH = this.u(17.0F);
         int scroll = Math.max(0, Math.min(scrolls.getOrDefault(p.key, 0), p.maxScroll));
         scrolls.put(p.key, scroll);
         int top = p.contentTop + 1;
         int bot = p.y + p.h - 1;
         this.hits.add(new ModernGuiScreen.Hit(p.x, p.contentTop, p.w, p.visH, () -> {}, () -> {}, "panelbody"));
         ctx.enableScissor(p.x + 1, top, p.x + p.w - 1, bot);
         int ry = p.contentTop + this.u(3.0F) - scroll;

         for (Module m : mods) {
            if (ry + rowH >= top && ry <= bot) {
               boolean hov = mx >= p.x && mx < p.x + p.w && my >= ry && my < ry + rowH && my >= top && my < bot;
               boolean open = m == this.openModule;
               if (hov || open) {
                  this.t.rrect(ctx, p.x + this.u(3.0F), ry, p.w - this.u(6.0F), rowH, 2.5F, ModernTheme.withA(16777215, open ? 14 : 7));
               }

               int kebX = p.x + p.w - this.u(8.0F);
               int col = m.isActive() ? this.t.text : this.t.text3;
               int nameMax = kebX - this.u(10.0F) - (p.x + this.u(8.0F));
               int bindW = 0;
               if (m.keybind.isSet()) {
                  bindW = this.t.tw(compactBind(m.keybind.toString()), this.t.ts(9.0F)) + this.u(10.0F);
               }

               String name = this.t.ellipsize(m.title, nameMax - bindW, md);
               this.t.textC(ctx, name, p.x + this.u(8.0F), ry, rowH, hov && !m.isActive() ? this.t.text2 : col, md);
               if (bindW > 0) {
                  this.t
                     .chip(
                        ctx,
                        compactBind(m.keybind.toString()),
                        p.x + this.u(8.0F) + this.t.tw(name, md) + this.u(4.0F),
                        ry + rowH / 2,
                        this.t.text3,
                        this.t.line
                     );
               }

               boolean kebHov = hov && mx >= kebX - this.u(6.0F);
               this.t.kebab(ctx, kebX, ry + rowH / 2.0F, kebHov ? this.t.text : this.t.text3);
               int rowY = ry;
               int hy = Math.max(ry, top);
               int hh = Math.min(ry + rowH, bot) - Math.max(ry, top);
               this.hits.add(new ModernGuiScreen.Hit(p.x, hy, p.w, hh, () -> this.toggle(m), () -> this.openPopupFor(m, p, rowY), "row"));
               this.hits
                  .add(
                     new ModernGuiScreen.Hit(
                        kebX - this.u(7.0F), hy, this.u(14.0F), hh, () -> this.openPopupFor(m, p, rowY), () -> this.openPopupFor(m, p, rowY), "keb"
                     )
                  );
            }

            ry += rowH;
         }

         ctx.disableScissor();
         if (p.maxScroll > 0) {
            int trackH = p.visH;
            int total = p.contentH + this.u(6.0F);
            int barH = Math.max(this.u(8.0F), trackH * trackH / Math.max(1, total));
            int barY = p.contentTop + (trackH - barH) * scroll / Math.max(1, p.maxScroll);
            this.t.rrect(ctx, p.x + p.w - this.u(2.5F), barY, this.u(1.5F), barH, 1.0F, this.t.lineStrong);
         }
      }
   }

   private void openPopupFor(Module m, ModernGuiScreen.Panel near, int rowY) {
      if (this.openModule != m) {
         this.drawer.reset();
         this.openModule = m;
         this.drawerScroll = 0;
         Modules.get().setModuleToBind(null);
         int px = near.x + near.w + this.u(6.0F);
         if (px + this.u(128.0F) > this.width - this.u(6.0F)) {
            px = near.x - this.u(128.0F) - this.u(6.0F);
         }

         int py = Math.max(this.u(6.0F), Math.min(rowY - this.u(8.0F), this.height - this.u(120.0F)));
         NewGuiLayout.set("modern:popup", Math.max(this.u(4.0F), px), py);
      }

      this.bringToFront("modern:popup");
   }

   private void renderPopup(DrawContext ctx, ModernGuiScreen.Panel p, int mx, int my) {
      Module m = this.openModule;
      if (m != null) {
         this.panelFrame(ctx, p);
         float md = this.t.ts(13.0F);
         float xxs = this.t.ts(9.0F);
         boolean resetHov = mx >= p.x + this.u(3.0F) && mx < p.x + this.u(15.0F) && my >= p.y && my < p.contentTop;
         this.t.reset(ctx, p.x + this.u(9.0F), p.y + p.headerH / 2.0F, this.u(3.6F), resetHov ? this.t.text : this.t.text3);
         int closeX = p.x + p.w - this.u(8.0F);
         int pillW = this.u(20.0F);
         int pillH = this.u(9.0F);
         int pillX = closeX - this.u(8.0F) - pillW;
         int starX = pillX - this.u(9.0F);
         boolean closeHov = Math.abs(mx - closeX) < this.u(6.0F) && my >= p.y && my < p.contentTop;
         this.t.xmark(ctx, closeX, p.y + p.headerH / 2.0F, this.u(2.3F), closeHov ? this.t.text : this.t.text3);
         boolean on = m.isActive();
         int pillY = p.y + (p.headerH - pillH) / 2;
         this.t.rrect(ctx, pillX, pillY, pillW, pillH, pillH / 2.0F, on ? this.t.onTrack : this.t.pillBg);
         String pl = on ? "ON" : "OFF";
         this.t.textC(ctx, pl, pillX + (pillW - this.t.tw(pl, xxs)) / 2, pillY, pillH, on ? this.t.onKnob : this.t.text2, xxs);
         boolean starHov = Math.abs(mx - starX) < this.u(6.0F) && my >= p.y && my < p.contentTop;
         this.t.star(ctx, starX, p.y + p.headerH / 2.0F, this.u(4.0F), m.favorite ? this.t.accent : (starHov ? this.t.text2 : this.t.text3));
         this.t.textC(ctx, this.t.ellipsize(m.title, starX - this.u(8.0F) - (p.x + this.u(18.0F)), md), p.x + this.u(18.0F), p.y, p.headerH, this.t.text, md);
         this.hits.add(new ModernGuiScreen.Hit(p.x + this.u(3.0F), p.y, this.u(12.0F), p.headerH, () -> {
            m.settings.reset();
            this.drawer.reset();
         }, null, "reset"));
         this.hits.add(new ModernGuiScreen.Hit(starX - this.u(6.0F), p.y, this.u(12.0F), p.headerH, () -> m.favorite = !m.favorite, null, "star"));
         this.hits.add(new ModernGuiScreen.Hit(pillX, p.y, pillW + this.u(2.0F), p.headerH, () -> this.toggle(m), null, "pill"));
         this.hits.add(new ModernGuiScreen.Hit(closeX - this.u(6.0F), p.y, this.u(12.0F), p.headerH, this::closeSettings, null, "close"));
         this.addDragHit(p, p.x + this.u(16.0F), starX - this.u(6.0F));
         if (!p.collapsed) {
            int cx = p.x + this.u(7.0F);
            int cw = p.w - this.u(14.0F);
            int top = p.contentTop + 1;
            int bot = p.y + p.h - 1;
            this.drawerScroll = Math.max(0, Math.min(this.drawerScroll, p.maxScroll));
            int cy0 = p.contentTop + this.u(5.0F) - this.drawerScroll;
            ctx.enableScissor(p.x + 1, top, p.x + p.w - 1, bot);
            this.drawer.render(ctx, cx, cy0, cw, mx, my, top, bot);
            ctx.disableScissor();
            if (p.maxScroll > 0) {
               int trackH = p.visH;
               int total = p.contentH + this.u(10.0F);
               int barH = Math.max(this.u(8.0F), trackH * trackH / Math.max(1, total));
               int barY = p.contentTop + (trackH - barH) * this.drawerScroll / Math.max(1, p.maxScroll);
               this.t.rrect(ctx, p.x + p.w - this.u(2.5F), barY, this.u(1.5F), barH, 1.0F, this.t.lineStrong);
            }

            this.hits.add(new ModernGuiScreen.Hit(p.x, p.contentTop, p.w, p.visH, null, null, new Object[]{"drawer", cx, cy0, cw, top, bot}));
         }
      }
   }

   private void renderSettingsPanel(DrawContext ctx, ModernGuiScreen.Panel p, int mx, int my) {
      this.panelFrame(ctx, p);
      float md = this.t.ts(13.0F);
      this.t.gear(ctx, p.x + this.u(9.0F), p.y + p.headerH / 2.0F, this.u(4.0F), this.t.text2);
      this.t.textC(ctx, "GUI settings", p.x + this.u(18.0F), p.y, p.headerH, this.t.text, md);
      int closeX = p.x + p.w - this.u(8.0F);
      boolean closeHov = Math.abs(mx - closeX) < this.u(6.0F) && my >= p.y && my < p.contentTop;
      this.t.xmark(ctx, closeX, p.y + p.headerH / 2.0F, this.u(2.3F), closeHov ? this.t.text : this.t.text3);
      this.hits.add(new ModernGuiScreen.Hit(closeX - this.u(6.0F), p.y, this.u(12.0F), p.headerH, () -> settingsPanelOpen = false, null, "close"));
      this.addDragHit(p, p.x, closeX - this.u(6.0F));
      if (!p.collapsed) {
         int cx = p.x + this.u(7.0F);
         int cw = p.w - this.u(14.0F);
         int top = p.contentTop + 1;
         int bot = p.y + p.h - 1;
         this.guiScroll = Math.max(0, Math.min(this.guiScroll, p.maxScroll));
         int cy0 = p.contentTop + this.u(5.0F) - this.guiScroll;
         ctx.enableScissor(p.x + 1, top, p.x + p.w - 1, bot);
         this.guiSettings.render(ctx, cx, cy0, cw, mx, my, top, bot);
         ctx.disableScissor();
         if (p.maxScroll > 0) {
            int trackH = p.visH;
            int total = p.contentH + this.u(10.0F);
            int barH = Math.max(this.u(8.0F), trackH * trackH / Math.max(1, total));
            int barY = p.contentTop + (trackH - barH) * this.guiScroll / Math.max(1, p.maxScroll);
            this.t.rrect(ctx, p.x + p.w - this.u(2.5F), barY, this.u(1.5F), barH, 1.0F, this.t.lineStrong);
         }

         this.hits.add(new ModernGuiScreen.Hit(p.x, p.contentTop, p.w, p.visH, null, null, new Object[]{"gui", cx, cy0, cw, top, bot}));
      }
   }

   private void renderProfilesPanel(DrawContext ctx, ModernGuiScreen.Panel p, int mx, int my) {
      this.panelFrame(ctx, p);
      float md = this.t.ts(13.0F);
      float sm = this.t.ts(11.5F);
      float xxs = this.t.ts(9.0F);
      float xs = this.t.ts(10.0F);
      this.t.rect(ctx, p.x + this.u(8.0F), p.y + p.headerH / 2 - this.u(2.0F), this.u(7.0F), 1, this.t.text2);
      this.t.rect(ctx, p.x + this.u(8.0F), p.y + p.headerH / 2, this.u(5.0F), 1, this.t.text2);
      this.t.rect(ctx, p.x + this.u(8.0F), p.y + p.headerH / 2 + this.u(2.0F), this.u(7.0F), 1, this.t.text2);
      this.t.textC(ctx, "Profiles", p.x + this.u(20.0F), p.y, p.headerH, this.t.text, md);
      int chevX = p.x + p.w - this.u(9.0F);
      this.t.chevron(ctx, chevX, p.y + p.headerH / 2.0F, this.u(4.0F), true, this.t.text3);
      this.hits.add(new ModernGuiScreen.Hit(chevX - this.u(6.0F), p.y, this.u(12.0F), p.headerH, () -> {
         NewGuiLayout.setCollapsed(p.key, !p.collapsed);
         NewGuiLayout.save();
      }, null, "collapse"));
      this.addDragHit(p, p.x, chevX - this.u(6.0F));
      if (!p.collapsed) {
         int top = p.contentTop + 1;
         int bot = p.y + p.h - 1;
         this.hits.add(new ModernGuiScreen.Hit(p.x, p.contentTop, p.w, p.visH, () -> {}, () -> {}, "panelbody"));
         ctx.enableScissor(p.x + 1, top, p.x + p.w - 1, bot);
         int fy = p.contentTop + this.u(4.0F);
         int fh = this.u(14.0F);
         int fx = p.x + this.u(5.0F);
         int bw = this.u(34.0F);
         int fw = p.w - this.u(10.0F) - bw - this.u(4.0F);
         this.t.rrect(ctx, fx, fy, fw, fh, 2.5F, this.t.field);
         this.t.rborder(ctx, fx, fy, fw, fh, 2.5F, this.profileNameFocused ? this.t.lineStrong : this.t.fieldBorder);
         String disp = this.profileNameFocused
            ? this.profileName + ((System.currentTimeMillis() / 530L & 1L) == 0L ? "|" : "")
            : (this.profileName.isEmpty() ? "Name..." : this.profileName);
         ctx.enableScissor(fx + this.u(2.0F), fy, fx + fw - this.u(2.0F), fy + fh);
         this.t.textC(ctx, disp, fx + this.u(4.0F), fy, fh, this.profileName.isEmpty() && !this.profileNameFocused ? this.t.text3 : this.t.text, xs);
         ctx.disableScissor();
         this.hits.add(new ModernGuiScreen.Hit(fx, fy, fw, fh, () -> {
            this.profileNameFocused = true;
            this.searchFocused = false;
         }, null, "pname"));
         int bx2 = fx + fw + this.u(4.0F);
         boolean bhov = mx >= bx2 && mx < bx2 + bw && my >= fy && my < fy + fh;
         this.t.rrect(ctx, bx2, fy, bw, fh, 2.5F, bhov ? ModernTheme.lift(this.t.text, -20) : this.t.text);
         this.t.textC(ctx, "+ NEW", bx2 + (bw - this.t.tw("+ NEW", xxs)) / 2, fy, fh, this.t.onKnob, xxs);
         this.hits.add(new ModernGuiScreen.Hit(bx2, fy, bw, fh, this::createProfile, null, "pcreate"));
         int ry = fy + fh + this.u(6.0F);
         int rh = this.u(17.0F);
         List<Profile> profiles = Profiles.get().getAll();
         if (profiles.isEmpty()) {
            this.t.text(ctx, "No profiles yet.", p.x + this.u(8.0F), ry + this.u(2.0F), this.t.text3, xs);
         }

         for (Profile pr : profiles) {
            String name = pr.name.get();
            boolean selp = name.equals(selectedProfile);
            boolean hov = mx >= p.x && mx < p.x + p.w && my >= ry && my < ry + rh;
            if (selp) {
               this.t.rrect(ctx, p.x + this.u(4.0F), ry, p.w - this.u(8.0F), rh, 2.5F, this.t.accentControls ? this.t.accent : this.t.text);
            } else if (hov) {
               this.t.rrect(ctx, p.x + this.u(4.0F), ry, p.w - this.u(8.0F), rh, 2.5F, ModernTheme.withA(16777215, 7));
            }

            this.t
               .textC(
                  ctx,
                  this.t.ellipsize(name, p.w - this.u(28.0F), sm),
                  p.x + this.u(9.0F),
                  ry,
                  rh,
                  selp ? (this.t.accentControls ? -1 : this.t.onKnob) : this.t.text2,
                  sm
               );
            this.t.kebab(ctx, p.x + p.w - this.u(9.0F), ry + rh / 2.0F, selp ? (this.t.accentControls ? -1 : this.t.onKnob) : this.t.text3);
            this.hits.add(new ModernGuiScreen.Hit(p.x, ry, p.w, rh, () -> selectedProfile = name, () -> selectedProfile = name, "prow"));
            ry += rh + this.u(2.0F);
         }

         Profile sp = selectedProfile == null ? null : Profiles.get().get(selectedProfile);
         if (sp != null) {
            String[] labels = new String[]{"LOAD", "SAVE", "DEL"};
            int bwid = (p.w - this.u(10.0F) - this.u(6.0F)) / 3;
            int bh = this.u(14.0F);
            int by = ry + this.u(2.0F);

            for (int i = 0; i < 3; i++) {
               int x2 = p.x + this.u(5.0F) + i * (bwid + this.u(3.0F));
               boolean hov = mx >= x2 && mx < x2 + bwid && my >= by && my < by + bh;
               this.t.rrect(ctx, x2, by, bwid, bh, 2.5F, hov ? this.t.cardHover : this.t.field);
               this.t.rborder(ctx, x2, by, bwid, bh, 2.5F, this.t.fieldBorder);
               this.t.textC(ctx, labels[i], x2 + (bwid - this.t.tw(labels[i], xxs)) / 2, by, bh, this.t.text, xxs);
               int idx = i;
               this.hits.add(new ModernGuiScreen.Hit(x2, by, bwid, bh, () -> {
                  if (idx == 0) {
                     sp.load();
                  } else if (idx == 1) {
                     sp.save();
                  } else {
                     Profiles.get().remove(sp);
                     selectedProfile = null;
                  }
               }, null, "pbtn"));
            }
         }

         ctx.disableScissor();
      }
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      int mx = (int)click.x();
      int my = (int)click.y();
      int btn = click.button();
      this.lastMx = mx;
      this.lastMy = my;
      if (this.openT < 0.6F && this.anim()) {
         return true;
      } else {
         boolean hitSearch = false;
         boolean hitPname = false;

         for (int i = this.hits.size() - 1; i >= 0; i--) {
            ModernGuiScreen.Hit h = this.hits.get(i);
            if (h.in(mx, my)) {
               if (h.tag instanceof Object[] arr) {
                  String kind = (String)arr[0];
                  int x = (Integer)arr[1];
                  int y0 = (Integer)arr[2];
                  int w = (Integer)arr[3];
                  int top = (Integer)arr[4];
                  int bot = (Integer)arr[5];
                  if (my >= top && my < bot) {
                     this.searchFocused = false;
                     this.profileNameFocused = false;
                     if (kind.equals("drawer")) {
                        if (!this.centeredNow) {
                           this.bringToFront("modern:popup");
                        }

                        this.drawer.click(x, y0, w, mx, my, btn);
                        this.guiSettings.commit();
                     } else {
                        this.guiSettings.click(x, y0, w, mx, my, btn);
                        this.drawer.commit();
                     }

                     return true;
                  }

                  return true;
               }

               if ("search".equals(h.tag)) {
                  hitSearch = true;
               }

               if ("pname".equals(h.tag)) {
                  hitPname = true;
               }

               if (!hitSearch) {
                  this.searchFocused = false;
               }

               if (!hitPname) {
                  this.profileNameFocused = false;
               }

               this.drawer.commit();
               this.guiSettings.commit();
               if (btn == 0 && h.onLeft != null) {
                  h.onLeft.run();
                  return true;
               }

               if (btn == 1 && h.onRight != null) {
                  h.onRight.run();
                  return true;
               }

               if (h.onLeft != null || h.onRight != null) {
                  return true;
               }
            }
         }

         this.searchFocused = false;
         this.profileNameFocused = false;
         this.drawer.commit();
         this.guiSettings.commit();
         if (!this.centeredNow) {
            if (this.openModule != null && btn == 0) {
               this.closeSettings();
            }

            return true;
         } else {
            boolean inWin = mx >= this.wx && mx < this.wx + this.W && my >= this.wy && my < this.wy + this.H;
            if (!inWin && this.openModule != null) {
               this.closeSettings();
               return true;
            } else {
               return true;
            }
         }
      }
   }

   public boolean mouseDragged(Click click, double offsetX, double offsetY) {
      int mx = (int)click.x();
      int my = (int)click.y();
      this.lastMx = mx;
      this.lastMy = my;
      if (click.button() == 0 && this.dragKey != null) {
         int nx = mx - this.dragOffX;
         int ny = my - this.dragOffY;
         nx = Math.max(this.u(10.0F) - this.u(98.0F), Math.min(nx, this.width - this.u(20.0F)));
         ny = Math.max(this.u(4.0F), Math.min(ny, this.height - this.u(20.0F)));
         NewGuiLayout.set(this.dragKey, nx, ny);
         return true;
      } else {
         if (click.button() == 0) {
            this.drawer.drag(mx, my);
            this.guiSettings.drag(mx, my);
         }

         return super.mouseDragged(click, offsetX, offsetY);
      }
   }

   public boolean mouseReleased(Click click) {
      if (click.button() == 0) {
         if (this.dragKey != null) {
            NewGuiLayout.save();
            this.dragKey = null;
         }

         this.drawer.release();
         this.guiSettings.release();
      }

      return super.mouseReleased(click);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int mx = (int)mouseX;
      int my = (int)mouseY;
      int step = (int)(verticalAmount * this.u(14.0F));
      if (!this.centeredNow) {
         for (int i = this.panelsThisFrame.size() - 1; i >= 0; i--) {
            ModernGuiScreen.Panel p = this.panelsThisFrame.get(i);
            if (p.in(mx, my)) {
               switch (p.kind) {
                  case CAT:
                     scrolls.put(p.key, Math.max(0, Math.min(scrolls.getOrDefault(p.key, 0) - step, p.maxScroll)));
                  case PROFILES:
                  default:
                     break;
                  case SETTINGS:
                     this.guiScroll = Math.max(0, Math.min(this.guiScroll - step, p.maxScroll));
                     break;
                  case POPUP:
                     this.drawerScroll = Math.max(0, Math.min(this.drawerScroll - step, p.maxScroll));
               }

               return true;
            }
         }

         return false;
      } else {
         if (tab == ModernGuiScreen.Tab.MODULES) {
            if (this.drawerT > 0.5F && mx >= this.drawerX) {
               this.drawerScroll = Math.max(0, this.drawerScroll - step);
               return true;
            }

            if (mx >= this.listX && mx < this.listX + this.listW && my >= this.listY && my < this.listY + this.listH) {
               scrolls.put("list", Math.max(0, scrolls.getOrDefault("list", 0) - step));
               return true;
            }
         } else {
            if (tab == ModernGuiScreen.Tab.SETTINGS || tab == ModernGuiScreen.Tab.HUD) {
               this.guiScroll = Math.max(0, this.guiScroll - step);
               return true;
            }

            if (tab == ModernGuiScreen.Tab.PROFILES) {
               scrolls.put("profiles", Math.max(0, scrolls.getOrDefault("profiles", 0) - step));
               return true;
            }
         }

         return false;
      }
   }

   public boolean keyPressed(KeyInput input) {
      int key = input.key();
      int mods = input.modifiers();
      if (this.drawer.isEditingText()) {
         return this.drawer.keyPressed(key, mods);
      } else if (this.guiSettings.isEditingText()) {
         return this.guiSettings.keyPressed(key, mods);
      } else if (this.searchFocused) {
         if (key == 256) {
            this.searchFocused = false;
            return true;
         } else if (key == 259 && !this.search.isEmpty()) {
            this.search = this.search.substring(0, this.search.length() - 1);
            scrolls.put("list", 0);
            return true;
         } else if (key != 257 && key != 335) {
            return true;
         } else {
            this.searchFocused = false;
            return true;
         }
      } else if (this.profileNameFocused) {
         if (key == 256) {
            this.profileNameFocused = false;
            return true;
         } else if (key == 259 && !this.profileName.isEmpty()) {
            this.profileName = this.profileName.substring(0, this.profileName.length() - 1);
            return true;
         } else if (key != 257 && key != 335) {
            return true;
         } else {
            this.createProfile();
            return true;
         }
      } else if (key == 256) {
         if (NewGuiBindCapture.get().isListeningForSetting()) {
            NewGuiBindCapture.get().cancelSettingListen();
            return true;
         } else if (Modules.get().isBinding()) {
            Modules.get().setModuleToBind(null);
            return true;
         } else if (this.openModule != null) {
            this.closeSettings();
            return true;
         } else {
            this.close();
            return true;
         }
      } else {
         return super.keyPressed(input);
      }
   }

   public boolean charTyped(CharInput input) {
      char chr = (char)input.codepoint();
      if (NewGuiBindCapture.get().isListeningForSetting() || Modules.get().isBinding()) {
         return true;
      } else if (this.drawer.isEditingText()) {
         return this.drawer.charTyped(chr);
      } else if (this.guiSettings.isEditingText()) {
         return this.guiSettings.charTyped(chr);
      } else if (this.searchFocused) {
         if (chr >= ' ') {
            this.search = this.search + chr;
            scrolls.put("list", 0);
         }

         return true;
      } else if (this.profileNameFocused) {
         if (chr >= ' ' && chr != '/' && chr != '\\') {
            this.profileName = this.profileName + chr;
         }

         return true;
      } else if (chr != '/' || this.centeredNow && tab != ModernGuiScreen.Tab.MODULES) {
         return super.charTyped(input);
      } else {
         this.searchFocused = true;
         return true;
      }
   }

   private static final class Hit {
      final int x;
      final int y;
      final int w;
      final int h;
      final Runnable onLeft;
      final Runnable onRight;
      final Object tag;

      Hit(int x, int y, int w, int h, Runnable onLeft, Runnable onRight, Object tag) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.onLeft = onLeft;
         this.onRight = onRight;
         this.tag = tag;
      }

      boolean in(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum PKind {
      NAV,
      CAT,
      PROFILES,
      SETTINGS,
      POPUP;
   }

   private static final class Panel {
      ModernGuiScreen.PKind kind;
      String key;
      Category cat;
      int x;
      int y;
      int w;
      int h;
      int headerH;
      int contentTop;
      int contentH;
      int visH;
      int maxScroll;
      boolean collapsed;

      boolean in(int mx, int my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static enum Tab {
      MODULES,
      HUD,
      PROFILES,
      SETTINGS;
   }
}
