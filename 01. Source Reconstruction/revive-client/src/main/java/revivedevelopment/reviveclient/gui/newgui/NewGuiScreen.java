package revivedevelopment.reviveclient.gui.newgui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.newgui.components.NewGuiBindCapture;
import revivedevelopment.reviveclient.gui.newgui.components.SettingGroupRenderer;
import revivedevelopment.reviveclient.gui.newgui.modern.ModernGuiScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewCollectionListScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewEntityTypeScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewStringListScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewVectorEditScreen;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnchantmentListSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.ModuleListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.SoundEventListSetting;
import revivedevelopment.reviveclient.settings.StatusEffectListSetting;
import revivedevelopment.reviveclient.settings.StorageBlockListSetting;
import revivedevelopment.reviveclient.settings.StringListSetting;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.settings.Vector3dSetting;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.modules.Category;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;
import revivedevelopment.reviveclient.utils.misc.ISerializable;
import revivedevelopment.reviveclient.utils.misc.Names;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class NewGuiScreen extends Screen {
   private static final float SC_LOGO = 1.0F;
   private static final float SC_STAT = 0.85F;
   private static final float SC_CNT = 0.75F;
   private static final float SC_POP_LBL = 0.8F;
   private static final float SC_POP_VAL = 0.85F;
   private static final int HEADER_H = 20;
   private static final int BODY_TOP = 21;
   private static final int COL_HH = 20;
   private static final int MOD_H = 10;
   private static final int POPUP_W = 280;
   private static final int SEARCH_W = 120;
   private static final int TXBOX_W = 45;
   private static final int STRBOX_W = 150;
   private static final int C_WHITE = -1;
   private int C_BG;
   private int C_HDR_BG;
   private int C_HDR_SEP;
   private int C_ACCENT;
   private int C_ACCENT_L;
   private int C_COL_SEP;
   private int C_COL_HBG;
   private int C_COL_HTXT;
   private int C_HOV_BG;
   private int C_HOV_TXT;
   private int C_ON_BG;
   private int C_ON_TXT;
   private int C_OFF_TXT;
   private int C_DIM_TXT;
   private int C_SRCH_BRD;
   private int C_ROW_ALT;
   private int C_GLOW_MID;
   private int C_GLOW_FAR;
   private int C_POPUP_BG;
   private int C_POPUP_BRD;
   private int C_POPUP_INS;
   private int C_TOG_ON;
   private int C_TOG_OFF;
   private int C_SLD_TRK;
   private int C_SLD_FILL;
   private int C_SLD_THM;
   private int C_SCRL_TRK;
   private int C_SCRL_BAR;
   private int C_KB_BG;
   private int C_TXBOX_BG;
   private int C_TIP_BG;
   private int C_TIP_BRD;
   private int C_TIP_TXT;
   private String searchText = "";
   private boolean searchFocused = false;
   private boolean clean;
   private static final int CLEAN_ARROW_ZONE = 16;
   private String hoveredDesc = null;
   private Module popupModule = null;
   private int popupX;
   private int popupY;
   private int popupHeight;
   private int popupContentH;
   private int popupScrollY = 0;
   private final List<NewGuiScreen.PopupItem> popupItems = new ArrayList<>();
   private Integer popupPosX = null;
   private Integer popupPosY = null;
   private boolean draggingPopup = false;
   private int dragPopupOffX;
   private int dragPopupOffY;
   private int lastMouseX;
   private int lastMouseY;
   private Setting<?> editingSetting = null;
   private String editText = "";
   private final Map<Category, Integer> colScrolls = new LinkedHashMap<>();
   private int panelW = 116;
   private Category draggingPanelCat = null;
   private int dragPanelOffX;
   private int dragPanelOffY;
   private Category resizingPanelCat = null;
   private int resizeGrabOff;
   private static final int RESIZE_BAND = 4;
   private final List<String> panelFront = new ArrayList<>();
   private static boolean layoutLoaded;
   private Setting<?> sliderSetting = null;
   private int sliderTX;
   private int sliderTW;
   private static final int CP_SV_H = 44;
   private static final int CP_BAR_H = 7;
   private static final int CP_GAP = 2;
   private ColorSetting openColor = null;
   private float cpHue;
   private float cpSat;
   private float cpBri;
   private int colorDrag = 0;
   private int cpX;
   private int cpW;
   private int cpSvY;
   private int cpHueY;
   private int cpAlphaY;

   private void refreshTheme() {
      FontManager fm = FontManager.get();
      int sec = fm.getSecondaryColor();
      int pri = fm.getPrimaryColor();
      int txt = fm.getTextColor();
      int txt2 = fm.getTextSecondary();
      int lite = lighten(sec, 70);
      int dark = mix(pri, 0, 0.82F);
      this.clean = Modules.get().get(Gui.class).style.get() == Gui.GuiStyle.Clean;
      if (this.clean) {
         this.refreshCleanTheme(sec, pri, txt, txt2, lite);
      } else {
         this.C_BG = withA(dark, 140);
         this.C_HDR_BG = withA(mix(pri, 0, 0.88F), 130);
         this.C_COL_HBG = withA(0, 60);
         this.C_POPUP_BG = withA(mix(pri, 0, 0.78F), 240);
         this.C_TXBOX_BG = withA(0, 102);
         this.C_TOG_OFF = withA(mix(pri, 0, 0.6F), 255);
         this.C_SLD_TRK = withA(mix(pri, 0, 0.6F), 255);
         this.C_ACCENT = opaque(sec);
         this.C_ACCENT_L = lite;
         this.C_HOV_BG = withA(sec, 28);
         this.C_HOV_TXT = lite;
         this.C_ON_BG = withA(sec, 30);
         this.C_ROW_ALT = withA(sec, 12);
         this.C_GLOW_MID = withA(sec, 102);
         this.C_GLOW_FAR = withA(sec, 38);
         this.C_POPUP_INS = withA(sec, 22);
         this.C_TOG_ON = opaque(sec);
         this.C_SLD_FILL = opaque(sec);
         this.C_SLD_THM = lite;
         this.C_SCRL_TRK = withA(sec, 14);
         this.C_SCRL_BAR = withA(sec, 110);
         this.C_KB_BG = withA(sec, 80);
         this.C_COL_HTXT = lite;
         this.C_HDR_SEP = withA(sec, 110);
         this.C_COL_SEP = withA(pri, 140);
         this.C_SRCH_BRD = withA(pri, 200);
         this.C_POPUP_BRD = opaque(pri);
         this.C_ON_TXT = txt;
         this.C_OFF_TXT = txt2;
         this.C_DIM_TXT = withA(txt2, 150);
         this.C_TIP_BG = withA(0, 235);
         this.C_TIP_BRD = opaque(sec);
         this.C_TIP_TXT = -1;
         this.applyColorOverrides();
      }
   }

   private void refreshCleanTheme(int sec, int pri, int txt, int txt2, int lite) {
      int dark = mix(pri, 0, 0.92F);
      this.C_BG = withA(dark, 228);
      this.C_HDR_BG = withA(mix(pri, 0, 0.9F), 215);
      this.C_COL_HBG = withA(0, 70);
      this.C_POPUP_BG = withA(mix(pri, 0, 0.9F), 246);
      this.C_TXBOX_BG = withA(0, 90);
      this.C_TOG_OFF = withA(16777215, 26);
      this.C_SLD_TRK = withA(16777215, 26);
      this.C_ACCENT = opaque(sec);
      this.C_ACCENT_L = lite;
      this.C_HOV_BG = withA(16777215, 12);
      this.C_HOV_TXT = txt;
      this.C_ON_BG = withA(sec, 34);
      this.C_ROW_ALT = withA(sec, 0);
      this.C_GLOW_MID = withA(sec, 90);
      this.C_GLOW_FAR = withA(sec, 30);
      this.C_POPUP_INS = withA(sec, 12);
      this.C_TOG_ON = opaque(sec);
      this.C_SLD_FILL = opaque(sec);
      this.C_SLD_THM = lite;
      this.C_SCRL_TRK = withA(sec, 10);
      this.C_SCRL_BAR = withA(sec, 60);
      this.C_KB_BG = withA(sec, 40);
      this.C_COL_HTXT = txt;
      this.C_HDR_SEP = withA(sec, 60);
      this.C_COL_SEP = withA(sec, 42);
      this.C_SRCH_BRD = withA(sec, 45);
      this.C_POPUP_BRD = withA(sec, 70);
      this.C_ON_TXT = txt;
      this.C_OFF_TXT = txt2;
      this.C_DIM_TXT = withA(txt2, 120);
      this.C_TIP_BG = withA(mix(pri, 0, 0.93F), 245);
      this.C_TIP_BRD = withA(sec, 70);
      this.C_TIP_TXT = txt2;
      this.applyColorOverrides();
   }

   private static int withA(int argb, int a) {
      return (a & 0xFF) << 24 | argb & 16777215;
   }

   private static int packColor(SettingColor c) {
      return (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
   }

   private static int scaleAlpha(int color, int pct) {
      if (pct >= 100) {
         return color;
      } else {
         int a = (color >>> 24 & 0xFF) * Math.max(0, pct) / 100;
         return color & 16777215 | a << 24;
      }
   }

   private void applyColorOverrides() {
      Gui g = this.gui();
      if (g.ovPanel.get()) {
         this.C_BG = packColor(g.colPanel.get());
      }

      if (g.ovHeader.get()) {
         this.C_COL_HBG = packColor(g.colHeader.get());
      }

      if (g.ovHover.get()) {
         this.C_HOV_BG = packColor(g.colHover.get());
      }

      if (g.ovActive.get()) {
         this.C_ON_BG = packColor(g.colActive.get());
      }

      if (g.ovStripe.get()) {
         this.C_ROW_ALT = packColor(g.colStripe.get());
      }

      if (g.ovBorder.get()) {
         this.C_COL_SEP = packColor(g.colBorder.get());
      }

      if (g.ovGlow.get()) {
         int gl = packColor(g.colGlow.get());
         int a = gl >>> 24 & 0xFF;
         this.C_GLOW_MID = gl;
         this.C_GLOW_FAR = withA(gl, a * 38 / 102);
      }

      if (g.ovScroll.get()) {
         this.C_SCRL_BAR = packColor(g.colScroll.get());
      }

      if (g.ovPopup.get()) {
         this.C_POPUP_BG = packColor(g.colPopup.get());
      }

      if (g.ovTooltip.get()) {
         this.C_TIP_BG = packColor(g.colTooltip.get());
      }
   }

   private static int opaque(int argb) {
      return 0xFF000000 | argb & 16777215;
   }

   private static int lighten(int argb, int amt) {
      int r = Math.min(255, (argb >> 16 & 0xFF) + amt);
      int g = Math.min(255, (argb >> 8 & 0xFF) + amt);
      int b = Math.min(255, (argb & 0xFF) + amt);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int mix(int a, int b, float t) {
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

   private int colHH() {
      int h = this.gui().headerHeight.get();
      return h > 0 ? h : (this.clean ? 14 : 20);
   }

   private int modH() {
      int h = this.gui().rowHeight.get();
      return h > 0 ? h : (this.clean ? 13 : 10);
   }

   private int cr() {
      return this.gui().cornerRadius.get();
   }

   private int scaleA(int color) {
      int pct = this.gui().panelOpacity.get();
      if (pct >= 100) {
         return color;
      } else {
         int a = (color >>> 24 & 0xFF) * pct / 100;
         return color & 16777215 | a << 24;
      }
   }

   private boolean striping() {
      Gui.Striping s = this.gui().rowStriping.get();
      return s == Gui.Striping.On || s == Gui.Striping.Auto && !this.clean;
   }

   private Gui gui() {
      return Modules.get().get(Gui.class);
   }

   private int popupW() {
      return this.clean ? 150 : 280;
   }

   private int strBoxW() {
      return this.clean ? 80 : 150;
   }

   private float popLbl() {
      return this.clean ? 0.5F : 0.8F;
   }

   private float popVal() {
      return this.clean ? 0.55F : 0.85F;
   }

   private float popTitleSc() {
      return this.clean ? 0.7F : 1.0F;
   }

   private float popDescSc() {
      return this.clean ? 0.55F : 1.0F;
   }

   private int headerH() {
      return this.clean ? 14 : 20;
   }

   private int bodyTop() {
      return this.headerH() + 1;
   }

   private int searchW() {
      return this.clean ? 80 : 120;
   }

   private float scLogo() {
      return this.clean ? 0.65F : 1.0F;
   }

   private float scStat() {
      return this.clean ? 0.5F : 0.85F;
   }

   public NewGuiScreen() {
      super(Text.literal("REVIVE"));
   }

   protected void init() {
      FontManager.get().auditFonts();
      super.init();
      NewGuiBindCapture.ensureSubscribed();

      for (Category cat : Modules.loopCategories()) {
         this.colScrolls.putIfAbsent(cat, 0);
      }

      if (!layoutLoaded) {
         NewGuiLayout.load();
         layoutLoaded = true;
      }
   }

   private int tH() {
      return 9;
   }

   private int tW(String s) {
      return FontManager.get().menuTextWidth(s);
   }

   private int stH(float sc) {
      return (int)(this.tH() * sc);
   }

   private int stW(String s, float sc) {
      return Math.round(this.tW(s) * sc);
   }

   private void txtS(DrawContext ctx, String s, int x, int y, int col, float sc) {
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.translate(x, y);
      m.scale(sc, sc);
      FontManager.get().drawStyled(ctx, s, 0, 0, col);
      m.popMatrix();
   }

   private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
      ctx.fill(x, y, x + w, y + 1, color);
      ctx.fill(x, y + h - 1, x + w, y + h, color);
      ctx.fill(x, y, x + 1, y + h, color);
      ctx.fill(x + w - 1, y, x + w, y + h, color);
   }

   private void txt(DrawContext ctx, String s, int x, int y, int col) {
      FontManager.get().drawStyled(ctx, s, x, y, col);
   }

   private void box(DrawContext ctx, int x, int y, int w, int h, int col) {
      ctx.fill(x, y, x + w, y + h, col);
   }

   private void drawTri(DrawContext ctx, int x, int y, int size, boolean right, int color) {
      float s = this.client.getWindow().getScaleFactor();
      Matrix3x2fStack m = ctx.getMatrices();
      m.pushMatrix();
      m.scale(1.0F / s, 1.0F / s);
      int X = Math.round(x * s);
      int Y = Math.round(y * s);
      int S = Math.max(3, Math.round(size * s));
      if (right) {
         for (int i = 0; i <= S / 2; i++) {
            ctx.fill(X + i, Y + i, X + i + 1, Y + S - i, color);
         }
      } else {
         for (int i = 0; i <= S / 2; i++) {
            ctx.fill(X + i, Y + i, X + S - i, Y + i + 1, color);
         }
      }

      m.popMatrix();
   }

   private static int cornerInset(int r, int i) {
      double dy = r - i - 0.5;
      return r - (int)Math.round(Math.sqrt((double)r * r - dy * dy));
   }

   private void rbox(DrawContext ctx, int x, int y, int w, int h, int rTop, int rBottom, int color) {
      if (this.clean && (rTop > 0 || rBottom > 0)) {
         float s = this.client.getWindow().getScaleFactor();
         Matrix3x2fStack m = ctx.getMatrices();
         m.pushMatrix();
         m.scale(1.0F / s, 1.0F / s);
         int X = Math.round(x * s);
         int Y = Math.round(y * s);
         int W = Math.round(w * s);
         int H = Math.round(h * s);
         int rt = Math.max(0, Math.min(Math.round(rTop * s), Math.min(W / 2, H)));
         int rb = Math.max(0, Math.min(Math.round(rBottom * s), Math.min(W / 2, H - rt)));
         ctx.fill(X, Y + rt, X + W, Y + H - rb, color);

         for (int i = 0; i < rt; i++) {
            int in = cornerInset(rt, i);
            ctx.fill(X + in, Y + i, X + W - in, Y + i + 1, color);
         }

         for (int i = 0; i < rb; i++) {
            int in = cornerInset(rb, i);
            ctx.fill(X + in, Y + H - i - 1, X + W - in, Y + H - i, color);
         }

         m.popMatrix();
      } else {
         this.box(ctx, x, y, w, h, color);
      }
   }

   private void rborder(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
      if (this.clean && r > 0) {
         float s = this.client.getWindow().getScaleFactor();
         Matrix3x2fStack m = ctx.getMatrices();
         m.pushMatrix();
         m.scale(1.0F / s, 1.0F / s);
         int X = Math.round(x * s);
         int Y = Math.round(y * s);
         int W = Math.round(w * s);
         int H = Math.round(h * s);
         int R = Math.max(1, Math.min(Math.round(r * s), Math.min(W, H) / 2));
         ctx.fill(X + R, Y, X + W - R, Y + 1, color);
         ctx.fill(X + R, Y + H - 1, X + W - R, Y + H, color);
         ctx.fill(X, Y + R, X + 1, Y + H - R, color);
         ctx.fill(X + W - 1, Y + R, X + W, Y + H - R, color);

         for (int i = 0; i < R; i++) {
            int a = cornerInset(R, i);
            int prev = i == 0 ? R : cornerInset(R, i - 1);
            int x2 = Math.max(a + 1, prev);
            ctx.fill(X + a, Y + i, X + x2, Y + i + 1, color);
            ctx.fill(X + W - x2, Y + i, X + W - a, Y + i + 1, color);
            ctx.fill(X + a, Y + H - i - 1, X + x2, Y + H - i, color);
            ctx.fill(X + W - x2, Y + H - i - 1, X + W - a, Y + H - i, color);
         }

         m.popMatrix();
      } else {
         drawBorder(ctx, x, y, w, h, color);
      }
   }

   private void hline(DrawContext ctx, int x1, int x2, int y, int col) {
      ctx.fill(x1, y, x2, y + 1, col);
   }

   private List<Category> visibleCats() {
      List<Category> out = new ArrayList<>();

      for (Category cat : Modules.loopCategories()) {
         if (!this.filteredMods(cat).isEmpty()) {
            out.add(cat);
         }
      }

      return out;
   }

   private List<Module> filteredMods(Category cat) {
      List<Module> all = Modules.get().getGroup(cat);
      if (this.searchText.isEmpty()) {
         return all;
      } else {
         String q = this.searchText.toLowerCase();
         List<Module> out = new ArrayList<>();

         for (Module m : all) {
            if (m.title.toLowerCase().contains(q) || m.name.toLowerCase().contains(q)) {
               out.add(m);
            }
         }

         return out;
      }
   }

   private boolean shaderActive() {
      return Modules.get().get(Gui.class).shader.get() != GuiShaderBackground.Mode.Off;
   }

   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (!this.shaderActive()) {
         super.renderBackground(context, mouseX, mouseY, deltaTicks);
      }
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (Modules.get().get(Gui.class).style.get() == Gui.GuiStyle.Modern) {
         this.client.setScreen(new ModernGuiScreen());
      } else {
         this.lastMouseX = mouseX;
         this.lastMouseY = mouseY;
         this.hoveredDesc = null;
         this.refreshTheme();
         Gui gui = Modules.get().get(Gui.class);
         GuiShaderBackground.Mode shaderMode = gui.shader.get();
         boolean shaderOn = shaderMode != GuiShaderBackground.Mode.Off;
         if (shaderOn) {
            int sc1 = gui.bgGradient.get() ? packColor(gui.bgGradientTop.get()) & 16777215 : FontManager.get().getPrimaryColor() & 16777215;
            int sc2 = gui.bgGradient.get() ? packColor(gui.bgGradientBottom.get()) & 16777215 : FontManager.get().getSecondaryColor() & 16777215;
            GuiShaderBackground.renderFullscreen(shaderMode.ordinal(), gui.shaderOpacity.get().floatValue(), sc1, sc2);
         }

         super.render(context, mouseX, mouseY, deltaTicks);
         if (shaderOn) {
            this.box(context, 0, 0, this.width, this.height, withA(0, 70));
         } else if (gui.bgGradient.get()) {
            int go = gui.bgGradientOpacity.get();
            context.fillGradient(
               0, 0, this.width, this.height, scaleAlpha(packColor(gui.bgGradientTop.get()), go), scaleAlpha(packColor(gui.bgGradientBottom.get()), go)
            );
         } else {
            this.box(context, 0, 0, this.width, this.height, this.C_BG);
         }

         this.renderHeader(context, mouseX, mouseY, true, true);
         this.hline(context, 0, this.width, this.headerH(), this.C_HDR_SEP);
         this.renderBody(context, mouseX, mouseY, true, true);
         if (this.clean) {
            this.drawLegend(context);
         }

         if (this.popupModule != null) {
            this.renderPopup(context, mouseX, mouseY);
         } else {
            this.drawModuleTooltip(context, mouseX, mouseY);
         }
      }
   }

   private void drawLegend(DrawContext ctx) {
      String[][] items = new String[][]{{"LMB", "toggle"}, {"RMB", "settings"}, {"DRAG", "move tab"}, {"EDGE", "resize"}, {"", "minimize"}};
      float sc = 0.55F;
      int pad = 4;
      int gap = 8;
      int kpad = 2;
      int triS = 4;
      int h = this.stH(sc) + pad * 2 + 2;
      int w = pad;

      for (String[] it : items) {
         w += this.chipW(it[0], sc, kpad, triS) + 2 + 3 + this.stW(it[1], sc) + gap;
      }

      w += pad - gap;
      int x = 6;
      int y = this.height - h - 6;
      this.rbox(ctx, x, y, w, h, 1, 1, this.C_BG);
      this.rborder(ctx, x, y, w, h, 1, this.C_COL_SEP);
      int cx = x + pad;
      int ty = y + pad + 1;

      for (String[] it : items) {
         int kw = this.chipW(it[0], sc, kpad, triS);
         this.rborder(ctx, cx, ty - 1, kw + 2, this.stH(sc) + 2, 1, withA(this.C_ACCENT, 45));
         if (it[0].isEmpty()) {
            this.drawTri(ctx, cx + kpad + 1, ty + (this.stH(sc) - triS / 2) / 2, triS, false, this.C_DIM_TXT);
         } else {
            this.txtS(ctx, it[0], cx + kpad + 1, ty, this.C_DIM_TXT, sc);
         }

         cx += kw + 2 + 3;
         this.txtS(ctx, it[1], cx, ty, this.C_OFF_TXT, sc);
         cx += this.stW(it[1], sc) + gap;
      }
   }

   private int chipW(String key, float sc, int kpad, int triS) {
      return (key.isEmpty() ? triS : this.stW(key, sc)) + kpad * 2;
   }

   private void drawModuleTooltip(DrawContext ctx, int mx, int my) {
      String desc = this.hoveredDesc;
      if (desc != null && !desc.isEmpty()) {
         float tsc = this.clean ? 0.55F : 1.0F;
         int maxW = (int)(Math.min(220, this.width - 16) / tsc);
         List<String> lines = this.wrapText(desc, maxW);
         if (!lines.isEmpty()) {
            int pad = 4;
            int lineH = this.stH(tsc) + 2;
            int boxW = 0;

            for (String l : lines) {
               boxW = Math.max(boxW, Math.round(this.tW(l) * tsc));
            }

            boxW += pad * 2;
            int boxH = lines.size() * lineH - 2 + pad * 2;
            int bx = mx + 12;
            int by = my + 8;
            if (bx + boxW > this.width - 2) {
               bx = mx - boxW - 8;
            }

            if (bx < 2) {
               bx = 2;
            }

            if (by + boxH > this.height - 2) {
               by = this.height - boxH - 2;
            }

            if (by < 2) {
               by = 2;
            }

            this.rbox(ctx, bx, by, boxW, boxH, 1, 1, this.C_TIP_BG);
            this.rborder(ctx, bx, by, boxW, boxH, 1, this.C_TIP_BRD);
            GuiTextMesh.pushOccluder(bx, by, bx + boxW, by + boxH);
            int ty = by + pad;

            for (String l : lines) {
               this.txtS(ctx, l, bx + pad, ty, this.C_TIP_TXT, tsc);
               ty += lineH;
            }
         }
      }
   }

   private List<String> wrapText(String text, int maxW) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : text.split(" ")) {
         String candidate = line.isEmpty() ? word : line + " " + word;
         if (this.tW(candidate) > maxW && !line.isEmpty()) {
            lines.add(line.toString());
            line = new StringBuilder(word);
         } else {
            line = new StringBuilder(candidate);
         }
      }

      if (!line.isEmpty()) {
         lines.add(line.toString());
      }

      return lines;
   }

   private void renderHeader(DrawContext ctx, int mx, int my, boolean fills, boolean content) {
      if (fills) {
         this.box(ctx, 0, 0, this.width, this.headerH(), this.C_HDR_BG);
      }

      if (content) {
         int cy = (this.headerH() - this.stH(this.scLogo())) / 2;
         int sy = (this.headerH() - this.stH(this.scStat())) / 2;
         this.txtS(ctx, "REVIVE", 10, cy, this.C_ACCENT, this.scLogo());
         if (this.clean) {
            this.txtS(ctx, "1.21.11", 12 + this.stW("REVIVE", this.scLogo()) + 4, (this.headerH() - this.stH(0.45F)) / 2 + 1, this.C_DIM_TXT, 0.45F);
         }

         int active = Modules.get().getActive().size();
         int total = Modules.get().getAll().size();
         String aNum = String.valueOf(active);
         String aLbl = " active";
         String gap = this.clean ? " · " : "   ";
         String tNum = String.valueOf(total);
         String tLbl = " total";
         int blockW = this.stW(aNum, this.scStat())
            + this.stW(aLbl, this.scStat())
            + this.stW(gap, this.scStat())
            + this.stW(tNum, this.scStat())
            + this.stW(tLbl, this.scStat());
         int bx = (this.width - blockW) / 2;
         this.txtS(ctx, aNum, bx, sy, this.C_ACCENT, this.scStat());
         bx += this.stW(aNum, this.scStat());
         this.txtS(ctx, aLbl, bx, sy, this.C_DIM_TXT, this.scStat());
         bx += this.stW(aLbl, this.scStat());
         this.txtS(ctx, gap, bx, sy, this.C_DIM_TXT, this.scStat());
         bx += this.stW(gap, this.scStat());
         this.txtS(ctx, tNum, bx, sy, this.C_ACCENT_L, this.scStat());
         bx += this.stW(tNum, this.scStat());
         this.txtS(ctx, tLbl, bx, sy, this.C_DIM_TXT, this.scStat());
         int sbH = this.headerH() - 6;
         int sx = this.width - this.searchW() - 6;
         int sy2 = (this.headerH() - sbH) / 2;
         this.rborder(ctx, sx, sy2, this.searchW(), sbH, 1, this.searchFocused ? this.C_ACCENT : this.C_SRCH_BRD);
         float ssc = this.clean ? 0.5F : 1.0F;
         String display;
         int dCol;
         if (this.searchFocused) {
            long b = System.currentTimeMillis() / 530L & 1L;
            display = this.searchText + (b == 0L ? "|" : " ");
            dCol = -1;
         } else if (this.searchText.isEmpty()) {
            display = this.clean ? "Search modules..." : "search...";
            dCol = this.C_DIM_TXT;
         } else {
            display = this.searchText;
            dCol = -1;
         }

         ctx.enableScissor(sx + 2, sy2, sx + this.searchW() - 2, sy2 + sbH);
         this.txtS(ctx, display, sx + 3, sy2 + (sbH - this.stH(ssc)) / 2, dCol, ssc);
         ctx.disableScissor();
      }
   }

   private List<NewGuiScreen.Panel> panels() {
      List<Category> cats = this.visibleCats();
      List<NewGuiScreen.Panel> out = new ArrayList<>(cats.size());
      int n = Math.max(1, cats.size());
      int gap = 6;
      int pwSet = this.gui().panelWidth.get();
      if (pwSet > 0) {
         this.panelW = Math.max(50, Math.min(pwSet, this.width - gap * 2));
      } else {
         this.panelW = this.clean
            ? Math.max(72, Math.min(94, (this.width - gap * (n + 1)) / n))
            : Math.max(60, Math.min(160, (this.width - gap * (n + 1)) / n));
      }

      int dx = gap;
      int dy = this.bodyTop() + 6;
      int maxNameW = 1;

      for (Category cat : cats) {
         maxNameW = Math.max(maxNameW, this.tW(cat.name.toUpperCase()));

         for (Module m : this.filteredMods(cat)) {
            maxNameW = Math.max(maxNameW, this.tW(m.title));
         }
      }

      float sc = this.clean ? clamp((float)(this.panelW - 14) / maxNameW, 0.45F, 0.62F) : clamp((float)(this.panelW - 14) / maxNameW, 0.6F, 0.85F);
      sc = clamp(sc * (float)this.gui().textSize.get().doubleValue(), 0.3F, 1.6F);

      for (Category cat : cats) {
         int py;
         int px;
         if (NewGuiLayout.has(cat.name)) {
            px = NewGuiLayout.getX(cat.name);
            py = NewGuiLayout.getY(cat.name);
         } else {
            if (dx + this.panelW > this.width - 2 && dx > gap) {
               dx = gap;
               dy += 230;
            }

            px = dx;
            py = dy;
            NewGuiLayout.set(cat.name, dx, dy);
            dx += this.panelW + gap;
         }

         px = Math.max(20 - this.panelW, Math.min(px, this.width - 20));
         py = Math.max(this.bodyTop() + 2, Math.min(py, this.height - this.colHH()));
         NewGuiScreen.Panel p = new NewGuiScreen.Panel();
         p.cat = cat;
         p.mods = this.filteredMods(cat);
         p.sc = sc;
         p.x = px;
         p.y = py;
         p.w = this.panelW;
         p.contentTop = py + this.colHH();
         p.totalH = p.mods.size() * this.modH();
         p.collapsed = NewGuiLayout.isCollapsed(cat.name);
         if (p.collapsed) {
            p.visH = 0;
            p.maxScroll = 0;
            p.h = this.colHH();
         } else {
            int cap = Math.max(this.modH() * 3, this.height - p.contentTop - 8);
            int desired = NewGuiLayout.hasHeight(cat.name) ? NewGuiLayout.getHeight(cat.name) : p.totalH;
            p.visH = Math.min(desired, Math.min(p.totalH, cap));
            p.visH = Math.max(p.visH, Math.min(this.modH() * 3, p.totalH));
            p.maxScroll = Math.max(0, p.totalH - p.visH);
            p.h = this.colHH() + p.visH;
         }

         out.add(p);
      }

      out.sort(Comparator.comparingInt(pp -> this.panelFront.indexOf(pp.cat.name)));
      return out;
   }

   private void bringToFront(Category cat) {
      this.panelFront.remove(cat.name);
      this.panelFront.add(cat.name);
   }

   private NewGuiScreen.Panel panelAt(List<NewGuiScreen.Panel> ps, int mx, int my) {
      for (int i = ps.size() - 1; i >= 0; i--) {
         NewGuiScreen.Panel p = ps.get(i);
         if (mx >= p.x && mx < p.x + p.w && my >= p.y && my < p.y + p.h) {
            return p;
         }
      }

      return null;
   }

   private void renderBody(DrawContext ctx, int mx, int my, boolean fills, boolean content) {
      List<NewGuiScreen.Panel> ps = this.panels();
      NewGuiScreen.Panel top = this.panelAt(ps, mx, my);

      for (NewGuiScreen.Panel p : ps) {
         this.renderPanel(ctx, p, mx, my, p == top, fills, content);
      }
   }

   private void renderPanel(DrawContext ctx, NewGuiScreen.Panel p, int mx, int my, boolean topHovered, boolean fills, boolean content) {
      List<Module> mods = p.mods;
      int bottom = p.y + p.h;
      int scroll = Math.max(0, Math.min(this.colScrolls.getOrDefault(p.cat, 0), p.maxScroll));
      this.colScrolls.put(p.cat, scroll);
      int sbW = p.maxScroll > 0 ? 3 : 0;
      int clipB = this.clean ? bottom - 2 : bottom;
      if (fills) {
         this.rbox(ctx, p.x, p.y, p.w, p.h, this.cr(), this.cr(), this.scaleA(this.C_BG));
         this.rbox(ctx, p.x, p.y, p.w, this.colHH(), this.cr(), p.collapsed ? this.cr() : 0, this.scaleA(this.C_COL_HBG));
         GuiTextMesh.pushOccluder(p.x, p.y, p.x + p.w, p.y + p.h);
         if (!p.collapsed) {
            ctx.enableScissor(p.x, p.contentTop, p.x + p.w - sbW, clipB);
            int ry = p.contentTop - scroll;

            for (int ri = 0; ri < mods.size(); ri++) {
               Module mod = mods.get(ri);
               if (ry + this.modH() > p.contentTop && ry < bottom) {
                  if (ri % 2 == 1 && this.striping()) {
                     this.box(ctx, p.x, ry, p.w, this.modH(), this.gui().ovStripe.get() ? this.C_ROW_ALT : withA(FontManager.get().getSecondaryColor(), 12));
                  }

                  if (mod.isActive()) {
                     this.box(ctx, p.x, ry, p.w, this.modH(), this.C_ON_BG);
                  }
               }

               ry += this.modH();
            }

            ctx.disableScissor();
         }
      }

      if (content) {
         int active = 0;

         for (Module m : mods) {
            if (m.isActive()) {
               active++;
            }
         }

         this.rborder(ctx, p.x, p.y, p.w, p.h, this.cr(), this.C_COL_SEP);
         boolean headerHov = topHovered && mx >= p.x && mx < p.x + p.w && my >= p.y && my < p.y + this.colHH();
         if (this.clean) {
            int nameY = p.y + (this.colHH() - this.stH(p.sc)) / 2;
            float cs = p.sc * 0.9F;
            int rowY = p.y + (this.colHH() - this.stH(cs)) / 2;
            boolean arrowHov = headerHov && mx >= p.x + p.w - 16;
            int triS = 4;
            int arrowX = p.x + p.w - triS - 6;
            String cnt = active + "/" + mods.size();
            int cntX = arrowX - 5 - this.stW(cnt, cs);
            ctx.enableScissor(p.x + 6, p.y, cntX - 3, p.y + this.colHH());
            this.txtS(ctx, p.cat.name.toUpperCase(), p.x + 6, nameY, this.C_COL_HTXT, p.sc);
            ctx.disableScissor();
            this.txtS(ctx, cnt, cntX, rowY, this.C_DIM_TXT, cs);
            int triCol = arrowHov ? this.C_ACCENT_L : this.C_DIM_TXT;
            if (p.collapsed) {
               this.drawTri(ctx, arrowX + 1, p.y + (this.colHH() - triS) / 2, triS, true, triCol);
            } else {
               this.drawTri(ctx, arrowX, p.y + (this.colHH() - triS / 2) / 2, triS, false, triCol);
            }

            if (!p.collapsed) {
               this.hline(ctx, p.x + 1, p.x + p.w - 1, p.contentTop - 1, this.C_HDR_SEP);
            }
         } else {
            int nameYx = p.y + 3;
            ctx.enableScissor(p.x + 4, p.y, p.x + p.w - 3, p.y + this.colHH());
            this.txtS(ctx, p.cat.name.toUpperCase(), p.x + 4, nameYx, this.C_COL_HTXT, p.sc);
            ctx.disableScissor();
            this.txtS(ctx, active + "/" + mods.size(), p.x + 4, nameYx + this.stH(p.sc) + 2, this.C_DIM_TXT, 0.75F);
            if (headerHov) {
               String arrow = p.collapsed ? "▸" : "▾";
               this.txtS(ctx, arrow, p.x + p.w - this.stW(arrow, 0.75F) - 4, nameYx + this.stH(p.sc) + 2, this.C_DIM_TXT, 0.75F);
            }
         }

         if (!p.collapsed) {
            ctx.enableScissor(p.x, p.contentTop, p.x + p.w - sbW, clipB);
            int ry = p.contentTop - scroll;

            for (int ri = 0; ri < mods.size(); ri++) {
               Module mod = mods.get(ri);
               if (ry + this.modH() > p.contentTop && ry < bottom) {
                  boolean hov = topHovered && mx >= p.x && mx < p.x + p.w && my >= Math.max(ry, p.contentTop) && my < Math.min(ry + this.modH(), bottom);
                  boolean on = mod.isActive();
                  if (hov) {
                     this.box(ctx, p.x, ry, p.w, this.modH(), this.C_HOV_BG);
                     if (mod.description != null && !mod.description.isEmpty()) {
                        this.hoveredDesc = mod.description;
                     }
                  }

                  Gui.AccentBar ab = this.gui().accentBar.get();
                  if (ab == Gui.AccentBar.Auto) {
                     ab = this.clean ? Gui.AccentBar.Rail : Gui.AccentBar.Glow;
                  }

                  if (on) {
                     switch (ab) {
                        case Rail:
                           this.box(ctx, p.x, ry + 1, 2, this.modH() - 2, this.C_ACCENT);
                           this.box(ctx, p.x + 2, ry + 1, 1, this.modH() - 2, this.C_GLOW_MID);
                           break;
                        case Glow:
                           this.box(ctx, p.x, ry, 2, this.modH(), this.C_ACCENT);
                           this.box(ctx, p.x + 2, ry, 2, this.modH(), this.C_GLOW_MID);
                           this.box(ctx, p.x + 4, ry, 2, this.modH(), this.C_GLOW_FAR);
                     }
                  } else {
                     switch (ab) {
                        case Rail:
                           this.box(ctx, p.x, ry + 1, 1, this.modH() - 2, withA(this.C_DIM_TXT, 70));
                           break;
                        case Glow:
                           this.box(ctx, p.x, ry, 2, this.modH(), this.C_DIM_TXT);
                     }
                  }

                  int tc = hov ? this.C_HOV_TXT : (on ? this.C_ON_TXT : this.C_OFF_TXT);
                  int titleY = ry + (this.modH() - this.stH(p.sc)) / 2;
                  if (this.clean && mod.keybind.isSet()) {
                     String kb = compactBind(mod.keybind.toString());
                     float ks = p.sc * 0.85F;
                     int kw = this.stW(kb, ks);
                     int kh = this.stH(ks) + 2;
                     int kx = p.x + p.w - kw - 6;
                     int ky = ry + (this.modH() - kh) / 2;
                     ctx.enableScissor(p.x, p.contentTop, kx - 5, clipB);
                     this.txtS(ctx, mod.title, p.x + 7, titleY, tc, p.sc);
                     ctx.disableScissor();
                     this.rborder(ctx, kx - 2, ky, kw + 4, kh, 1, withA(this.C_ACCENT, on ? 110 : 45));
                     this.txtS(ctx, kb, kx, ky + 1, on ? this.C_ACCENT_L : this.C_DIM_TXT, ks);
                  } else {
                     this.txtS(ctx, mod.title, p.x + 7, titleY, tc, p.sc);
                  }
               }

               ry += this.modH();
            }

            ctx.disableScissor();
         }

         if (p.maxScroll > 0 && p.visH > 0) {
            int sbX = p.x + p.w - 2;
            int tH2 = Math.max(6, p.visH * p.visH / Math.max(1, p.totalH));
            int tY = p.contentTop + (int)((long)(p.visH - tH2) * scroll / p.maxScroll);
            this.box(ctx, sbX, p.contentTop, 2, p.visH, this.C_SCRL_TRK);
            this.box(ctx, sbX, tY, 2, tH2, this.C_SCRL_BAR);
         }

         if (!p.collapsed) {
            boolean resizingThis = this.resizingPanelCat == p.cat;
            boolean resizeHov = topHovered && mx >= p.x && mx < p.x + p.w && my >= bottom - 4 && my < bottom;
            if (resizingThis || resizeHov) {
               int gw = Math.min(14, p.w / 3);
               this.box(ctx, p.x + (p.w - gw) / 2, bottom - 2, gw, 1, resizingThis ? this.C_ACCENT : withA(this.C_ACCENT, 160));
            }
         }
      }
   }

   private List<String> popupDescLines() {
      return this.popupModule != null && this.popupModule.description != null && !this.popupModule.description.isEmpty()
         ? this.wrapText(this.popupModule.description, (int)((this.popupW() - 12) / this.popDescSc()))
         : Collections.emptyList();
   }

   private int popupHeaderH() {
      int descLines = this.popupDescLines().size();
      int lineH = this.stH(this.popDescSc()) + 1;
      int descH = descLines > 0 ? descLines * lineH + 3 : 0;
      int statusH = this.clean ? this.stH(this.popVal()) + 4 : this.tH();
      return 4 + this.stH(this.popTitleSc()) + 2 + statusH + descH + 6;
   }

   private void renderPopup(DrawContext ctx, int mx, int my) {
      this.popupContentH = this.buildPopupItems(this.popupModule, 0, 0, true);
      int maxPH = (int)(this.height * 0.7F);
      this.popupHeight = Math.min(this.popupContentH, maxPH);
      this.popupScrollY = Math.max(0, Math.min(this.popupScrollY, Math.max(0, this.popupContentH - this.popupHeight)));
      int px;
      int py;
      if (this.popupPosX != null && this.popupPosY != null) {
         px = Math.max(0, Math.min(this.popupPosX, this.width - this.popupW()));
         py = Math.max(0, Math.min(this.popupPosY, this.height - this.popupHeight));
      } else {
         px = Math.max(0, this.width / 2 - this.popupW() / 2);
         py = Math.max(0, this.height / 2 - this.popupHeight / 2);
      }

      this.popupX = px;
      this.popupY = py;
      this.popupItems.clear();
      this.buildPopupItems(this.popupModule, px, 0, false);
      this.rbox(ctx, px, py, this.popupW(), this.popupHeight, 2, 2, this.C_POPUP_BG);
      this.rborder(ctx, px, py, this.popupW(), this.popupHeight, 2, this.C_POPUP_BRD);
      GuiTextMesh.pushOccluder(px, py, px + this.popupW(), py + this.popupHeight);
      if (!this.clean) {
         this.hline(ctx, px + 1, px + this.popupW() - 1, py + 1, this.C_POPUP_INS);
         this.hline(ctx, px + 1, px + this.popupW() - 1, py + this.popupHeight - 2, this.C_POPUP_INS);
         ctx.fill(px + 1, py + 1, px + 2, py + this.popupHeight - 1, this.C_POPUP_INS);
         ctx.fill(px + this.popupW() - 2, py + 1, px + this.popupW() - 1, py + this.popupHeight - 1, this.C_POPUP_INS);
      }

      boolean on = this.popupModule.isActive();
      this.txtS(ctx, this.popupModule.title, px + 6, py + 4, this.C_ON_TXT, this.popTitleSc());
      int statusY = py + 4 + this.stH(this.popTitleSc()) + 2;
      if (this.clean) {
         String st = on ? "ENABLED" : "DISABLED";
         int sw = this.stW(st, this.popVal());
         if (on) {
            this.rbox(ctx, px + 5, statusY - 1, sw + 6, this.stH(this.popVal()) + 3, 1, 1, this.C_ACCENT);
            this.txtS(ctx, st, px + 8, statusY, withA(0, 235), this.popVal());
         } else {
            this.rborder(ctx, px + 5, statusY - 1, sw + 6, this.stH(this.popVal()) + 3, 1, withA(this.C_ACCENT, 45));
            this.txtS(ctx, st, px + 8, statusY, this.C_OFF_TXT, this.popVal());
         }
      } else {
         this.txt(ctx, on ? "ENABLED" : "DISABLED", px + 6, statusY, on ? this.C_ACCENT : this.C_OFF_TXT);
      }

      int descY = statusY + (this.clean ? this.stH(this.popVal()) + 4 : this.tH()) + 3;

      for (String l : this.popupDescLines()) {
         this.txtS(ctx, l, px + 6, descY, this.C_DIM_TXT, this.popDescSc());
         descY += this.stH(this.popDescSc()) + 1;
      }

      int catTop = py + this.popupHeaderH();
      ctx.enableScissor(px, catTop, px + this.popupW(), py + this.popupHeight);

      for (NewGuiScreen.PopupItem item : this.popupItems) {
         int sy = item.y + py - this.popupScrollY;
         if (sy + item.h > catTop && sy < py + this.popupHeight) {
            if (item.isModuleKeybind) {
               this.renderModuleKeybindRow(ctx, px, sy);
            } else if (item.setting == null) {
               this.hline(ctx, px + 4, px + this.popupW() - 4, sy + 1, this.C_COL_SEP);
               this.txtS(ctx, item.groupName.toUpperCase(), px + 6, sy + 3, this.C_DIM_TXT, this.popLbl());
            } else {
               this.renderSettingItem(ctx, item, px, sy);
            }
         }
      }

      ctx.disableScissor();
      int scrollH = this.popupHeight - this.popupHeaderH();
      int maxScroll = Math.max(0, this.popupContentH - this.popupHeight);
      if (maxScroll > 0 && scrollH > 0) {
         int tH2 = Math.max(6, scrollH * scrollH / Math.max(1, this.popupContentH - this.popupHeaderH()));
         int tY = catTop + (int)((long)(scrollH - tH2) * this.popupScrollY / maxScroll);
         this.box(ctx, px + this.popupW() - 2, catTop, 2, scrollH, this.C_SCRL_TRK);
         this.box(ctx, px + this.popupW() - 2, tY, 2, tH2, this.C_SCRL_BAR);
      }
   }

   private int buildPopupItems(Module module, int px, int py, boolean dryRun) {
      int sy = py + this.popupHeaderH();

      for (SettingGroup group : module.settings.groups) {
         boolean anyVisible = false;

         for (Setting<?> s : group) {
            if (s.isVisible()) {
               anyVisible = true;
               break;
            }
         }

         if (anyVisible) {
            if (!group.name.equals("General")) {
               if (!dryRun) {
                  NewGuiScreen.PopupItem sep = new NewGuiScreen.PopupItem();
                  sep.groupName = group.name;
                  sep.y = sy;
                  sep.h = 12;
                  this.popupItems.add(sep);
               }

               sy += 12;
            }

            for (Setting<?> sx : group) {
               if (sx.isVisible()) {
                  int h = this.settingRowH(sx);
                  if (!dryRun) {
                     NewGuiScreen.PopupItem item = new NewGuiScreen.PopupItem();
                     item.setting = sx;
                     item.y = sy;
                     item.h = h;
                     item.hasSlider = this.hasSlider(sx);
                     if (item.hasSlider) {
                        item.tx = px + 5;
                        item.tw = this.popupW() - 10;
                     }

                     this.popupItems.add(item);
                  }

                  sy += h;
               }
            }
         }
      }

      int kbH = this.stH(this.popLbl()) + 6;
      if (!dryRun) {
         NewGuiScreen.PopupItem sep = new NewGuiScreen.PopupItem();
         sep.groupName = "Keybind";
         sep.y = sy;
         sep.h = 12;
         this.popupItems.add(sep);
      }

      sy += 12;
      if (!dryRun) {
         NewGuiScreen.PopupItem kb = new NewGuiScreen.PopupItem();
         kb.isModuleKeybind = true;
         kb.y = sy;
         kb.h = kbH;
         this.popupItems.add(kb);
      }

      sy += kbH;
      return sy - py + 4;
   }

   private boolean hasSlider(Setting<?> s) {
      return s instanceof IntSetting is && !is.noSlider || s instanceof DoubleSetting ds && !ds.noSlider;
   }

   private int settingRowH(Setting<?> s) {
      int lh = this.stH(this.popLbl());
      int txH = this.stH(this.popVal()) + 4;
      if (s instanceof IntSetting is && !is.noSlider) {
         return txH + 2 + 5 + 3;
      } else if (s instanceof DoubleSetting ds && !ds.noSlider) {
         return txH + 2 + 5 + 3;
      } else {
         return s instanceof ColorSetting cs ? lh + 6 + (this.openColor == cs ? this.colorPickerExtraH() : 0) : lh + 6;
      }
   }

   private int colorPickerExtraH() {
      return 66;
   }

   private void renderModuleKeybindRow(DrawContext ctx, int px, int sy) {
      boolean listening = Modules.get().isBinding();
      String kv = listening ? "[Press key]" : this.popupModule.keybind.toString();
      int kw = this.stW(kv, this.popVal());
      int kx = px + this.popupW() - kw - 12;
      this.txtS(ctx, "Bind", px + 5, sy + 2, this.C_OFF_TXT, this.popLbl());
      this.box(ctx, kx - 3, sy + 1, kw + 6, this.stH(this.popVal()) + 2, this.C_KB_BG);
      this.txtS(ctx, kv, kx, sy + 2, this.C_ACCENT_L, this.popVal());
   }

   private void renderSettingItem(DrawContext ctx, NewGuiScreen.PopupItem item, int px, int sy) {
      Setting<?> s = item.setting;
      int x5 = px + 5;
      int pw = this.popupW() - 10;
      if (s instanceof BoolSetting bs) {
         boolean val = bs.get();
         this.txtS(ctx, bs.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
         if (this.clean) {
            int tw = 14;
            int th = 7;
            int tx = px + this.popupW() - tw - 8;
            int ty = sy + 2 + (this.stH(this.popLbl()) - th) / 2;
            this.rbox(ctx, tx, ty, tw, th, 3, 3, val ? this.C_TOG_ON : this.C_TOG_OFF);
            int kx = val ? tx + tw - 6 : tx + 1;
            this.rbox(ctx, kx, ty + 1, 5, th - 2, 2, 2, val ? withA(0, 215) : withA(16777215, 160));
         } else {
            String tog = val ? "ON" : "OFF";
            int tw = this.stW(tog, this.popVal());
            int tx = px + this.popupW() - tw - 12;
            this.box(ctx, tx - 3, sy + 1, tw + 6, this.stH(this.popVal()) + 2, val ? this.C_TOG_ON : this.C_TOG_OFF);
            this.txtS(ctx, tog, tx, sy + 2, -1, this.popVal());
         }
      } else if (s instanceof IntSetting is) {
         this.renderSliderRow(
            ctx,
            s,
            px,
            sy,
            x5,
            pw,
            String.valueOf(is.get()),
            is.noSlider ? null : clamp01((float)(is.get() - is.sliderMin) / Math.max(1, is.sliderMax - is.sliderMin))
         );
      } else if (s instanceof DoubleSetting ds) {
         int dp = Math.max(1, Math.min(4, ds.decimalPlaces));
         this.renderSliderRow(
            ctx,
            s,
            px,
            sy,
            x5,
            pw,
            String.format("%." + dp + "f", ds.get()),
            ds.noSlider ? null : clamp01((float)((ds.get() - ds.sliderMin) / Math.max(1.0E-9, ds.sliderMax - ds.sliderMin)))
         );
      } else if (s instanceof EnumSetting<?> es) {
         this.txtS(ctx, es.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
         if (this.clean) {
            String v = es.get().toString();
            int triS = 3;
            int vw = this.stW(v, this.popVal());
            int vx = px + this.popupW() - vw - triS - 3 - 5;
            this.rborder(ctx, vx - 3, sy + 1, vw + triS + 3 + 6, this.stH(this.popVal()) + 2, 1, withA(this.C_ACCENT, 45));
            this.txtS(ctx, v, vx, sy + 2, this.C_ACCENT_L, this.popVal());
            this.drawTri(ctx, vx + vw + 2, sy + 2 + (this.stH(this.popVal()) - triS / 2) / 2, triS, false, this.C_ACCENT_L);
         } else {
            String v = es.get().toString() + " ▾";
            this.txtS(ctx, v, px + this.popupW() - this.stW(v, this.popVal()) - 5, sy + 2, this.C_ACCENT_L, this.popVal());
         }
      } else if (s instanceof KeybindSetting ks) {
         boolean listening = NewGuiBindCapture.get().getListeningSetting() == ks;
         String kv = listening ? "[Press key]" : ks.get().toString();
         int kw = this.stW(kv, this.popVal());
         int kx = px + this.popupW() - kw - 12;
         this.txtS(ctx, ks.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
         this.box(ctx, kx - 3, sy + 1, kw + 6, this.stH(this.popVal()) + 2, this.C_KB_BG);
         this.txtS(ctx, kv, kx, sy + 2, this.C_ACCENT_L, this.popVal());
      } else if (s instanceof ColorSetting csr) {
         SettingColor c = csr.get();
         this.txtS(ctx, csr.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
         int labelH = this.stH(this.popLbl()) + 6;
         int swW = 16;
         int swH = Math.max(6, this.stH(this.popLbl()));
         int swX = px + this.popupW() - swW - 6;
         int swY = sy + (labelH - swH) / 2;
         this.drawColorSwatch(ctx, swX, swY, swW, swH, c);
         if (this.openColor == csr) {
            this.renderColorPicker(ctx, csr, px, sy + labelH);
         }
      } else if (s instanceof StringSetting ss) {
         this.txtS(ctx, ss.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
         int boxX = px + this.popupW() - this.strBoxW() - 5;
         int boxY = sy + 1;
         int boxH = this.stH(this.popVal()) + 4;
         boolean editing = this.editingSetting == ss;
         String raw = editing ? this.editText : ss.get();
         this.box(ctx, boxX, boxY, this.strBoxW(), boxH, this.C_TXBOX_BG);
         drawBorder(ctx, boxX, boxY, this.strBoxW(), boxH, editing ? this.C_ACCENT_L : this.C_POPUP_BRD);
         String shown = editing ? raw + "|" : (raw.isEmpty() ? "click to edit" : raw);
         int innerR = boxX + this.strBoxW() - 3;
         int textX = Math.min(boxX + 3, innerR - this.stW(shown, this.popVal()));
         ctx.enableScissor(boxX + 1, boxY, boxX + this.strBoxW() - 1, boxY + boxH);
         this.txtS(
            ctx, shown, textX, boxY + (boxH - this.stH(this.popVal())) / 2, editing ? -1 : (raw.isEmpty() ? this.C_DIM_TXT : this.C_ACCENT_L), this.popVal()
         );
         ctx.disableScissor();
      } else if (s instanceof BlockPosSetting bps) {
         this.txtS(ctx, bps.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
         this.drawNavValue(ctx, bps.get().getX() + ", " + bps.get().getY() + ", " + bps.get().getZ(), px, sy);
      } else if (s instanceof Vector3dSetting v3s) {
         this.txtS(ctx, v3s.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
         this.drawNavValue(ctx, String.format("%.1f, %.1f, %.1f", v3s.get().x, v3s.get().y, v3s.get().z), px, sy);
      } else {
         String summary = this.listSummary(s);
         if (summary != null) {
            this.txtS(ctx, s.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
            this.drawNavValue(ctx, summary, px, sy);
         } else {
            this.txtS(ctx, s.title, x5, sy + 2, this.C_DIM_TXT, this.popLbl());
         }
      }
   }

   private void drawNavValue(DrawContext ctx, String v, int px, int sy) {
      if (this.clean) {
         int triS = 3;
         int vw = this.stW(v, this.popVal());
         int vx = px + this.popupW() - vw - triS - 2 - 5;
         this.txtS(ctx, v, vx, sy + 2, this.C_ACCENT_L, this.popVal());
         this.drawTri(ctx, vx + vw + 2, sy + 2 + (this.stH(this.popVal()) - triS) / 2, triS, true, this.C_ACCENT_L);
      } else {
         String s2 = v + " ▸";
         this.txtS(ctx, s2, px + this.popupW() - this.stW(s2, this.popVal()) - 5, sy + 2, this.C_ACCENT_L, this.popVal());
      }
   }

   private String listSummary(Setting<?> s) {
      if (s instanceof EntityTypeListSetting e) {
         return e.get().size() + " entities";
      } else if (s instanceof BlockListSetting b) {
         return b.get().size() + " blocks";
      } else if (s instanceof ItemListSetting i) {
         return i.get().size() + " items";
      } else if (s instanceof EnchantmentListSetting e) {
         return e.get().size() + " enchants";
      } else if (s instanceof StatusEffectListSetting e) {
         return e.get().size() + " effects";
      } else if (s instanceof SoundEventListSetting e) {
         return e.get().size() + " sounds";
      } else if (s instanceof ModuleListSetting m) {
         return m.get().size() + " modules";
      } else if (s instanceof StorageBlockListSetting sb) {
         return sb.get().size() + " storages";
      } else {
         return s instanceof StringListSetting sl ? sl.get().size() + " entries" : null;
      }
   }

   private boolean openListSelector(Setting<?> s) {
      if (s instanceof EntityTypeListSetting etls) {
         NewEntityTypeScreen screen = new NewEntityTypeScreen(etls);
         screen.parent = this;
         this.client.setScreen(screen);
         return true;
      } else if (s instanceof BlockListSetting bls) {
         List<Block> all = new ArrayList<>();

         for (Block b : Registries.BLOCK) {
            all.add(b);
         }

         NewCollectionListScreen<Block> screen = new NewCollectionListScreen<>(
            "Select Blocks — " + bls.name, bls, bls.get(), all, Names::get, b -> b.asItem().getDefaultStack(), null
         );
         screen.parent = this;
         this.client.setScreen(screen);
         return true;
      } else if (s instanceof ItemListSetting ils) {
         List<Item> all = new ArrayList<>();

         for (Item it : Registries.ITEM) {
            all.add(it);
         }

         NewCollectionListScreen<Item> screen = new NewCollectionListScreen<>(
            "Select Items — " + ils.name, ils, ils.get(), all, Names::get, Item::getDefaultStack, null
         );
         screen.parent = this;
         this.client.setScreen(screen);
         return true;
      } else if (s instanceof StatusEffectListSetting sels) {
         List<StatusEffect> all = new ArrayList<>();

         for (StatusEffect e : Registries.STATUS_EFFECT) {
            all.add(e);
         }

         NewCollectionListScreen<StatusEffect> screen = new NewCollectionListScreen<>(
            "Select Effects — " + sels.name, sels, sels.get(), all, Names::get, SettingGroupRenderer::statusEffectIcon, null
         );
         screen.parent = this;
         this.client.setScreen(screen);
         return true;
      } else if (s instanceof SoundEventListSetting sevls) {
         List<SoundEvent> all = new ArrayList<>();

         for (SoundEvent e : Registries.SOUND_EVENT) {
            all.add(e);
         }

         NewCollectionListScreen<SoundEvent> screen = new NewCollectionListScreen<>(
            "Select Sounds — " + sevls.name, sevls, sevls.get(), all, e -> e.id().getPath(), null, null
         );
         screen.parent = this;
         this.client.setScreen(screen);
         return true;
      } else if (s instanceof EnchantmentListSetting els) {
         List<RegistryKey<Enchantment>> all = new ArrayList<>();
         Registry<Enchantment> reg = null;
         if (this.client.world != null) {
            reg = (Registry<Enchantment>)this.client.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
         }

         if (reg == null && this.client.getNetworkHandler() != null) {
            reg = (Registry<Enchantment>)this.client.getNetworkHandler().getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
         }

         if (reg != null) {
            reg.streamKeys().forEach(all::add);
         } else {
            all.addAll(els.get());
         }

         NewCollectionListScreen<RegistryKey<Enchantment>> screen = new NewCollectionListScreen<>(
            "Select Enchantments — " + els.name, els, els.get(), all, Names::get
         );
         screen.parent = this;
         this.client.setScreen(screen);
         return true;
      } else if (s instanceof ModuleListSetting mls) {
         NewCollectionListScreen<Module> screen = new NewCollectionListScreen<>(
            "Select Modules — " + mls.name, mls, mls.get(), Modules.get().getAll(), m -> m.title
         );
         screen.parent = this;
         this.client.setScreen(screen);
         return true;
      } else if (!(s instanceof StorageBlockListSetting sbls)) {
         if (s instanceof StringListSetting sls) {
            NewStringListScreen screen = new NewStringListScreen(sls);
            screen.parent = this;
            this.client.setScreen(screen);
            return true;
         } else {
            return false;
         }
      } else {
         List<BlockEntityType<?>> allx = new ArrayList<>();

         for (BlockEntityType<?> t : StorageBlockListSetting.REGISTRY) {
            allx.add(t);
         }

         NewCollectionListScreen<BlockEntityType<?>> screen = new NewCollectionListScreen<>(
            "Select Storage Blocks — " + sbls.name, sbls, sbls.get(), allx, SettingGroupRenderer::storageBlockName, tx -> {
               Item item = SettingGroupRenderer.storageBlockItem(tx);
               return item == null ? null : item.getDefaultStack();
            }, null
         );
         screen.parent = this;
         this.client.setScreen(screen);
         return true;
      }
   }

   private void drawColorSwatch(DrawContext ctx, int x, int y, int w, int h, SettingColor c) {
      this.drawCheckerboard(ctx, x, y, w, h);
      int argb = (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
      ctx.fill(x, y, x + w, y + h, argb);
      drawBorder(ctx, x, y, w, h, this.C_POPUP_BRD);
   }

   private void renderColorPicker(DrawContext ctx, ColorSetting cs, int px, int top) {
      SettingColor c = cs.get();
      int x = px + 5;
      int w = this.popupW() - 10;
      int svY = top;
      int svH = 44;
      int hueY = top + svH + 2;
      int aY = hueY + 7 + 2;
      this.cpX = x;
      this.cpW = w;
      this.cpSvY = top;
      this.cpHueY = hueY;
      this.cpAlphaY = aY;
      int pure = Color.HSBtoRGB(this.cpHue, 1.0F, 1.0F);
      int hr = pure >> 16 & 0xFF;
      int hg = pure >> 8 & 0xFF;
      int hb = pure & 0xFF;

      for (int i = 0; i < w; i++) {
         float sat = (float)i / Math.max(1, w - 1);
         int r = (int)((1.0F - sat) * 255.0F + sat * hr);
         int g = (int)((1.0F - sat) * 255.0F + sat * hg);
         int b = (int)((1.0F - sat) * 255.0F + sat * hb);
         ctx.fill(x + i, svY, x + i + 1, svY + svH, 0xFF000000 | r << 16 | g << 8 | b);
      }

      for (int j = 0; j < svH; j++) {
         int a = (int)((float)j / Math.max(1, svH - 1) * 255.0F);
         ctx.fill(x, svY + j, x + w, svY + j + 1, a << 24);
      }

      drawBorder(ctx, x, svY, w, svH, this.C_POPUP_BRD);
      this.crosshair(ctx, x + (int)(this.cpSat * (w - 1)), svY + (int)((1.0F - this.cpBri) * (svH - 1)));

      for (int i = 0; i < w; i++) {
         int rgb = Color.HSBtoRGB((float)i / Math.max(1, w - 1), 1.0F, 1.0F);
         ctx.fill(x + i, hueY, x + i + 1, hueY + 7, 0xFF000000 | rgb & 16777215);
      }

      drawBorder(ctx, x, hueY, w, 7, this.C_POPUP_BRD);
      this.barMarker(ctx, x + (int)(this.cpHue * (w - 1)), hueY, 7);
      this.drawCheckerboard(ctx, x, aY, w, 7);

      for (int i = 0; i < w; i++) {
         int a = (int)((float)i / Math.max(1, w - 1) * 255.0F);
         ctx.fill(x + i, aY, x + i + 1, aY + 7, a << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF);
      }

      drawBorder(ctx, x, aY, w, 7, this.C_POPUP_BRD);
      this.barMarker(ctx, x + (int)((c.a & 0xFF) / 255.0F * (w - 1)), aY, 7);
   }

   private void drawCheckerboard(DrawContext ctx, int x, int y, int w, int h) {
      int sq = 3;
      int light = -3355444;
      int dark = -8355712;
      ctx.fill(x, y, x + w, y + h, light);

      for (int i = 0; i < w; i += sq) {
         for (int j = 0; j < h; j += sq) {
            if ((i / sq + j / sq & 1) != 0) {
               ctx.fill(x + i, y + j, Math.min(x + i + sq, x + w), Math.min(y + j + sq, y + h), dark);
            }
         }
      }
   }

   private void crosshair(DrawContext ctx, int cx, int cy) {
      ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, -16777216);
      ctx.fill(cx, cy, cx + 1, cy + 1, -1);
   }

   private void barMarker(DrawContext ctx, int cx, int y, int h) {
      ctx.fill(cx - 1, y - 1, cx + 2, y + h + 1, -16777216);
      ctx.fill(cx, y, cx + 1, y + h, -1);
   }

   private void initColorHSB(ColorSetting cs) {
      SettingColor c = cs.get();
      float[] hsb = Color.RGBtoHSB(c.r & 0xFF, c.g & 0xFF, c.b & 0xFF, null);
      this.cpHue = hsb[0];
      this.cpSat = hsb[1];
      this.cpBri = hsb[2];
   }

   private void applyColorDrag(ColorSetting cs, int mx, int my) {
      int a = cs.get().a & 0xFF;
      if (this.colorDrag == 1) {
         this.cpSat = clamp01((float)(mx - this.cpX) / Math.max(1, this.cpW - 1));
         this.cpBri = clamp01(1.0F - (float)(my - this.cpSvY) / Math.max(1, 43));
      } else if (this.colorDrag == 2) {
         this.cpHue = clamp01((float)(mx - this.cpX) / Math.max(1, this.cpW - 1));
      } else if (this.colorDrag == 3) {
         a = (int)(clamp01((float)(mx - this.cpX) / Math.max(1, this.cpW - 1)) * 255.0F);
      }

      int rgb = Color.HSBtoRGB(this.cpHue, this.cpSat, this.cpBri);
      cs.set(new SettingColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, a));
   }

   private void renderSliderRow(DrawContext ctx, Setting<?> s, int px, int sy, int x5, int pw, String rawVal, Float fracOrNull) {
      this.txtS(ctx, s.title, x5, sy + 2, this.C_OFF_TXT, this.popLbl());
      int txbX = px + this.popupW() - 45 - 5;
      int txbY = sy + 1;
      int txbH = this.stH(this.popVal()) + 4;
      boolean editing = this.editingSetting == s;
      String displayVal = editing ? this.editText + "|" : rawVal;
      if (this.clean && !editing) {
         this.txtS(
            ctx, rawVal, px + this.popupW() - this.stW(rawVal, this.popVal()) - 5, txbY + (txbH - this.stH(this.popVal())) / 2, this.C_ACCENT_L, this.popVal()
         );
      } else {
         this.box(ctx, txbX, txbY, 45, txbH, this.C_TXBOX_BG);
         drawBorder(ctx, txbX, txbY, 45, txbH, this.C_POPUP_BRD);
         int textX = txbX + 45 - this.stW(displayVal, this.popVal()) - 3;
         ctx.enableScissor(txbX + 1, txbY, txbX + 45 - 1, txbY + txbH);
         this.txtS(ctx, displayVal, Math.max(txbX + 2, textX), txbY + (txbH - this.stH(this.popVal())) / 2, editing ? -1 : this.C_ACCENT_L, this.popVal());
         ctx.disableScissor();
      }

      if (fracOrNull != null) {
         float frac = fracOrNull;
         int ty = sy + this.stH(this.popVal()) + 4 + 2;
         this.box(ctx, x5, ty, pw, 3, this.C_SLD_TRK);
         this.box(ctx, x5, ty, (int)(pw * frac), 3, this.C_SLD_FILL);
         if (this.clean) {
            this.rbox(ctx, x5 + (int)(pw * frac) - 2, ty - 2, 5, 7, 2, 2, this.C_SLD_THM);
         } else {
            this.box(ctx, x5 + (int)(pw * frac) - 1, ty - 1, 3, 5, this.C_SLD_THM);
         }
      }
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double dmx = click.x();
      double dmy = click.y();
      int btn = click.button();
      int mx = (int)dmx;
      int my = (int)dmy;
      if (this.popupModule != null) {
         if (this.inPopup(mx, my)) {
            return this.handlePopupClick(mx, my, btn);
         } else {
            this.commitSliderEdit();
            this.popupModule = null;
            this.sliderSetting = null;
            this.openColor = null;
            this.colorDrag = 0;
            this.draggingPopup = false;
            NewGuiBindCapture.get().cancelSettingListen();
            Modules.get().setModuleToBind(null);
            return true;
         }
      } else {
         int sbH = this.headerH() - 6;
         int sbX = this.width - this.searchW() - 6;
         int sbY = (this.headerH() - sbH) / 2;
         if (mx >= sbX && mx < sbX + this.searchW() && my >= sbY && my < sbY + sbH) {
            this.searchFocused = true;
            return true;
         } else if (my < this.headerH()) {
            this.searchFocused = false;
            return true;
         } else {
            this.searchFocused = false;
            List<NewGuiScreen.Panel> ps = this.panels();

            for (int i = ps.size() - 1; i >= 0; i--) {
               NewGuiScreen.Panel p = ps.get(i);
               if (mx >= p.x && mx < p.x + p.w && my >= p.y && my < p.y + p.h) {
                  this.bringToFront(p.cat);
                  if (my < p.y + this.colHH()) {
                     if (btn == 0) {
                        if (this.clean && mx >= p.x + p.w - 16) {
                           NewGuiLayout.setCollapsed(p.cat.name, !p.collapsed);
                           NewGuiLayout.save();
                           return true;
                        }

                        this.draggingPanelCat = p.cat;
                        this.dragPanelOffX = mx - p.x;
                        this.dragPanelOffY = my - p.y;
                     } else if (btn == 1) {
                        NewGuiLayout.setCollapsed(p.cat.name, !p.collapsed);
                        NewGuiLayout.save();
                     }

                     return true;
                  }

                  if (btn == 0 && !p.collapsed && my >= p.y + p.h - 4) {
                     this.resizingPanelCat = p.cat;
                     this.resizeGrabOff = p.y + p.h - my;
                     return true;
                  }

                  int scroll = this.colScrolls.getOrDefault(p.cat, 0);
                  int ry = p.contentTop - scroll;

                  for (Module mod : p.mods) {
                     if (my >= ry && my < ry + this.modH() && my >= p.contentTop) {
                        if (btn == 0) {
                           mod.toggle();
                        } else if (btn == 1) {
                           this.popupModule = mod;
                           this.popupScrollY = 0;
                           this.popupPosX = null;
                           this.popupPosY = null;
                           this.draggingPopup = false;
                           this.popupItems.clear();
                           this.sliderSetting = null;
                           this.openColor = null;
                           this.colorDrag = 0;
                           this.editingSetting = null;
                           this.editText = "";
                           NewGuiBindCapture.get().cancelSettingListen();
                           Modules.get().setModuleToBind(null);
                        }

                        return true;
                     }

                     ry += this.modH();
                  }

                  return true;
               }
            }

            return super.mouseClicked(click, doubled);
         }
      }
   }

   private boolean inPopup(int mx, int my) {
      return mx >= this.popupX && mx < this.popupX + this.popupW() && my >= this.popupY && my < this.popupY + this.popupHeight;
   }

   private boolean handlePopupClick(int mx, int my, int btn) {
      int titleBarBottom = this.popupY + 4 + this.stH(this.popTitleSc()) + 2;
      if (btn == 0 && my < titleBarBottom) {
         this.draggingPopup = true;
         this.dragPopupOffX = mx - this.popupX;
         this.dragPopupOffY = my - this.popupY;
         if (this.popupPosX == null || this.popupPosY == null) {
            this.popupPosX = this.popupX;
            this.popupPosY = this.popupY;
         }

         return true;
      } else if (btn == 0 && !this.popupItems.isEmpty()) {
         for (NewGuiScreen.PopupItem item : this.popupItems) {
            int screenY = item.y + this.popupY - this.popupScrollY;
            if (my >= screenY && my < screenY + item.h) {
               if (item.isModuleKeybind) {
                  this.commitSliderEdit();
                  if (Modules.get().isBinding()) {
                     Modules.get().setModuleToBind(null);
                  } else {
                     Modules.get().setModuleToBind(this.popupModule);
                  }

                  return true;
               }

               if (item.setting != null) {
                  Setting<?> s = item.setting;
                  if (s instanceof BoolSetting bs) {
                     this.commitSliderEdit();
                     bs.set(!bs.get());
                     return true;
                  }

                  if (s instanceof EnumSetting<?> es) {
                     this.commitSliderEdit();
                     this.cycleEnum(es);
                     return true;
                  }

                  if (s instanceof KeybindSetting ks) {
                     this.commitSliderEdit();
                     if (NewGuiBindCapture.get().getListeningSetting() == ks) {
                        NewGuiBindCapture.get().cancelSettingListen();
                     } else {
                        NewGuiBindCapture.get().startListeningForSetting(ks);
                     }

                     return true;
                  }

                  if (s instanceof ColorSetting cs) {
                     this.commitSliderEdit();
                     int labelH = this.stH(this.popLbl()) + 6;
                     if (my < screenY + labelH) {
                        if (this.openColor == cs) {
                           this.openColor = null;
                        } else {
                           this.openColor = cs;
                           this.initColorHSB(cs);
                        }

                        this.colorDrag = 0;
                        return true;
                     }

                     if (this.openColor == cs) {
                        if (my >= this.cpSvY && my < this.cpSvY + 44) {
                           this.colorDrag = 1;
                           this.applyColorDrag(cs, mx, my);
                           return true;
                        }

                        if (my >= this.cpHueY && my < this.cpHueY + 7) {
                           this.colorDrag = 2;
                           this.applyColorDrag(cs, mx, my);
                           return true;
                        }

                        if (my >= this.cpAlphaY && my < this.cpAlphaY + 7) {
                           this.colorDrag = 3;
                           this.applyColorDrag(cs, mx, my);
                           return true;
                        }
                     }

                     return true;
                  }

                  if (s instanceof BlockPosSetting || s instanceof Vector3dSetting) {
                     this.commitSliderEdit();
                     NewVectorEditScreen screen = s instanceof BlockPosSetting bps ? new NewVectorEditScreen(bps) : new NewVectorEditScreen((Vector3dSetting)s);
                     screen.parent = this;
                     this.client.setScreen(screen);
                     return true;
                  }

                  if (s instanceof StringSetting ss) {
                     this.commitSliderEdit();
                     this.editingSetting = ss;
                     this.editText = ss.get();
                     this.sliderSetting = null;
                     return true;
                  }

                  if (this.listSummary(s) != null) {
                     this.commitSliderEdit();
                     if (this.openListSelector(s)) {
                        return true;
                     }
                  }

                  if (item.hasSlider) {
                     int txbX = this.popupX + this.popupW() - 45 - 5;
                     int txbY = screenY + 1;
                     int txbH = this.stH(this.popVal()) + 4;
                     if (mx >= txbX && mx < txbX + 45 && my >= txbY && my < txbY + txbH) {
                        this.commitSliderEdit();
                        this.editingSetting = s;
                        if (s instanceof IntSetting is2) {
                           this.editText = String.valueOf(is2.get());
                        } else if (s instanceof DoubleSetting ds2) {
                           int dp = Math.max(1, Math.min(4, ds2.decimalPlaces));
                           this.editText = String.format("%." + dp + "f", ds2.get());
                        }

                        this.sliderSetting = null;
                        return true;
                     }

                     int trackY = screenY + this.stH(this.popVal()) + 4 + 2;
                     if (my >= trackY) {
                        this.commitSliderEdit();
                        this.sliderSetting = s;
                        this.sliderTX = item.tx;
                        this.sliderTW = item.tw;
                        this.applySlider(mx);
                        return true;
                     }

                     this.commitSliderEdit();
                     return true;
                  }
               }
            }
         }

         return true;
      } else {
         return true;
      }
   }

   public boolean mouseDragged(Click click, double offsetX, double offsetY) {
      double dmx = click.x();
      int btn = click.button();
      if (btn == 0 && this.sliderSetting != null) {
         this.applySlider((int)dmx);
         return true;
      } else if (btn == 0 && this.colorDrag != 0 && this.openColor != null) {
         this.applyColorDrag(this.openColor, (int)dmx, (int)click.y());
         return true;
      } else if (btn == 0 && this.draggingPopup) {
         this.popupPosX = Math.max(0, Math.min((int)dmx - this.dragPopupOffX, this.width - this.popupW()));
         this.popupPosY = Math.max(0, Math.min((int)click.y() - this.dragPopupOffY, this.height - this.popupHeight));
         return true;
      } else if (btn == 0 && this.draggingPanelCat != null) {
         int nx = (int)dmx - this.dragPanelOffX;
         int ny = (int)click.y() - this.dragPanelOffY;
         nx = Math.max(20 - this.panelW, Math.min(nx, this.width - 20));
         ny = Math.max(this.bodyTop() + 2, Math.min(ny, this.height - this.colHH()));
         NewGuiLayout.set(this.draggingPanelCat.name, nx, ny);
         return true;
      } else if (btn == 0 && this.resizingPanelCat != null) {
         int py = NewGuiLayout.getY(this.resizingPanelCat.name);
         py = Math.max(this.bodyTop() + 2, Math.min(py, this.height - this.colHH()));
         int contentTop = py + this.colHH();
         int visH = (int)click.y() + this.resizeGrabOff - contentTop;
         visH = Math.max(this.modH() * 3, Math.min(visH, this.height - contentTop - 2));
         NewGuiLayout.setHeight(this.resizingPanelCat.name, visH);
         return true;
      } else {
         return super.mouseDragged(click, offsetX, offsetY);
      }
   }

   public boolean mouseReleased(Click click) {
      int btn = click.button();
      if (btn == 0) {
         this.sliderSetting = null;
         this.colorDrag = 0;
         this.draggingPopup = false;
      }

      if (btn == 0 && this.draggingPanelCat != null) {
         NewGuiLayout.save();
         this.draggingPanelCat = null;
      }

      if (btn == 0 && this.resizingPanelCat != null) {
         NewGuiLayout.save();
         this.resizingPanelCat = null;
      }

      return super.mouseReleased(click);
   }

   private void applySlider(int mx) {
      if (this.sliderSetting != null) {
         float frac = clamp01((float)(mx - this.sliderTX) / Math.max(1, this.sliderTW));
         if (this.sliderSetting instanceof IntSetting is) {
            int v = is.sliderMin + Math.round(frac * (is.sliderMax - is.sliderMin));
            is.set(Math.max(is.min, Math.min(is.max, v)));
         } else if (this.sliderSetting instanceof DoubleSetting ds) {
            double v = ds.sliderMin + frac * (ds.sliderMax - ds.sliderMin);
            v = Math.max(ds.min, Math.min(ds.max, v));
            double f = Math.pow(10.0, Math.max(0, ds.decimalPlaces));
            ds.set(Math.round(v * f) / f);
         }
      }
   }

   private void pasteIntoEdit() {
      if (this.editingSetting != null) {
         String clip = this.client.keyboard.getClipboard();
         if (clip != null && !clip.isEmpty()) {
            clip = clip.replace("\n", "").replace("\r", "");
            if (this.editingSetting instanceof StringSetting) {
               this.editText = this.editText + clip;
            } else {
               StringBuilder sb = new StringBuilder(this.editText);

               for (int i = 0; i < clip.length(); i++) {
                  char c = clip.charAt(i);
                  if (Character.isDigit(c) || c == '.' || c == '-') {
                     sb.append(c);
                  }
               }

               this.editText = sb.toString();
            }
         }
      }
   }

   private void commitSliderEdit() {
      if (this.editingSetting != null) {
         try {
            if (this.editingSetting instanceof IntSetting is) {
               int v = Integer.parseInt(this.editText.trim());
               is.set(Math.max(is.min, Math.min(is.max, v)));
            } else if (this.editingSetting instanceof DoubleSetting ds) {
               double v = Double.parseDouble(this.editText.trim());
               v = Math.max(ds.min, Math.min(ds.max, v));
               double f = Math.pow(10.0, Math.max(0, ds.decimalPlaces));
               ds.set(Math.round(v * f) / f);
            } else if (this.editingSetting instanceof StringSetting ss) {
               ss.set(this.editText);
            }
         } catch (NumberFormatException var8) {
         }

         this.editingSetting = null;
         this.editText = "";
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int mx = (int)mouseX;
      int my = (int)mouseY;
      if (this.popupModule != null && this.inPopup(mx, my) && my >= this.popupY + this.popupHeaderH()) {
         int maxScroll = Math.max(0, this.popupContentH - this.popupHeight);
         this.popupScrollY = Math.max(0, Math.min(this.popupScrollY - (int)(verticalAmount * 12.0), maxScroll));
         return true;
      } else {
         List<NewGuiScreen.Panel> ps = this.panels();
         NewGuiScreen.Panel p = this.panelAt(ps, mx, my);
         if (p != null) {
            int scroll = this.colScrolls.getOrDefault(p.cat, 0) - (int)(verticalAmount * 14.0);
            this.colScrolls.put(p.cat, Math.max(0, Math.min(scroll, p.maxScroll)));
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int mods = input.modifiers();
      if (this.editingSetting != null) {
         boolean ctrlMod = (mods & 2) != 0 || (mods & 8) != 0;
         if (keyCode == 257 || keyCode == 335) {
            this.commitSliderEdit();
            return true;
         } else if (keyCode == 256) {
            this.editingSetting = null;
            this.editText = "";
            return true;
         } else if (keyCode == 259 && !this.editText.isEmpty()) {
            this.editText = this.editText.substring(0, this.editText.length() - 1);
            return true;
         } else if (ctrlMod && keyCode == 86) {
            this.pasteIntoEdit();
            return true;
         } else if (ctrlMod && keyCode == 67) {
            this.client.keyboard.setClipboard(this.editText);
            return true;
         } else if (ctrlMod && keyCode == 65) {
            if (this.editingSetting instanceof StringSetting) {
               this.editText = "";
            }

            return true;
         } else {
            return true;
         }
      } else if (this.searchFocused) {
         if (keyCode == 256) {
            this.searchFocused = false;
            return true;
         } else if (keyCode == 259 && !this.searchText.isEmpty()) {
            this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
            return true;
         } else {
            return true;
         }
      } else if (keyCode == 256) {
         if (NewGuiBindCapture.get().isListeningForSetting()) {
            NewGuiBindCapture.get().cancelSettingListen();
            return true;
         } else if (Modules.get().isBinding()) {
            Modules.get().setModuleToBind(null);
            return true;
         } else if (this.popupModule != null) {
            this.commitSliderEdit();
            this.popupModule = null;
            this.openColor = null;
            this.colorDrag = 0;
            return true;
         } else {
            this.close();
            return true;
         }
      } else {
         boolean ctrl = (mods & 2) != 0 || (mods & 8) != 0;
         if (ctrl && (keyCode == 67 || keyCode == 86)) {
            Module target = this.popupModule != null ? this.popupModule : this.moduleAt(this.lastMouseX, this.lastMouseY);
            if (keyCode == 67) {
               if (target != null) {
                  this.copyModuleConfig(target);
               } else {
                  this.copyAllModuleConfigs();
               }
            } else if (target != null) {
               this.pasteModuleConfig(target);
            } else {
               this.pasteAllModuleConfigs();
            }

            return true;
         } else {
            return super.keyPressed(input);
         }
      }
   }

   private static String compactBind(String kb) {
      if (kb.startsWith("Mouse ")) {
         return "M" + kb.substring(6);
      } else {
         switch (kb) {
            case "Apostrophe":
               return "'";
            case "Comma":
               return ",";
            case "Period":
               return ".";
            case "Semicolon":
               return ";";
            case "Grave Accent":
               return "`";
            case "Left Bracket":
               return "[";
            case "Right Bracket":
               return "]";
            case "Backslash":
               return "\\";
            case "Slash":
               return "/";
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

   private Module moduleAt(int mx, int my) {
      if (my < this.headerH()) {
         return null;
      } else {
         NewGuiScreen.Panel p = this.panelAt(this.panels(), mx, my);
         if (p != null && my >= p.contentTop) {
            int scroll = this.colScrolls.getOrDefault(p.cat, 0);
            int ry = p.contentTop - scroll;

            for (Module mod : p.mods) {
               if (my >= ry && my < ry + this.modH()) {
                  return mod;
               }

               ry += this.modH();
            }

            return null;
         } else {
            return null;
         }
      }
   }

   private boolean copyModuleConfig(Module module) {
      NbtCompound tag = new NbtCompound();
      tag.putString("name", module.name);
      NbtCompound settingsTag = module.settings.toTag();
      if (!settingsTag.isEmpty()) {
         tag.put("settings", settingsTag);
      }

      return NbtUtils.toClipboard(tag);
   }

   private boolean pasteModuleConfig(Module module) {
      NbtCompound tag = NbtUtils.fromClipboard();
      if (tag == null) {
         return false;
      } else if (!tag.getString("name", "").equals(module.name)) {
         return false;
      } else {
         Optional<NbtCompound> settings = tag.getCompound("settings");
         if (settings.isPresent()) {
            module.settings.fromTag(settings.get());
         } else {
            module.settings.reset();
         }

         return true;
      }
   }

   private boolean copyAllModuleConfigs() {
      NbtCompound tag = Modules.get().toTag();
      putSystem(tag, "hud", Hud.get());
      putSystem(tag, "config", Config.get());
      boolean ok = NbtUtils.toClipboard(tag);
      if (ok) {
         ChatUtils.info("Copied full config (modules, HUD, client settings) to clipboard.");
      } else {
         ChatUtils.error("Failed to copy config to clipboard.");
      }

      return ok;
   }

   private boolean pasteAllModuleConfigs() {
      NbtCompound tag = NbtUtils.fromClipboard();
      if (tag != null && tag.contains("modules")) {
         Modules.get().fromTag(tag);
         tag.getCompound("hud").ifPresent(t -> Hud.get().fromTag(t));
         tag.getCompound("config").ifPresent(t -> Config.get().fromTag(t));
         Gui gui = Modules.get().get(Gui.class);
         if (gui != null) {
            gui.applyToFontManager();
         }

         Modules.get().save();
         Hud.get().save();
         Config.get().save();
         ChatUtils.info("Pasted config from clipboard.");
         return true;
      } else {
         ChatUtils.error("Clipboard doesn't contain a config.");
         return false;
      }
   }

   private static void putSystem(NbtCompound tag, String key, ISerializable<?> system) {
      if (system != null) {
         try {
            NbtCompound systemTag = system.toTag();
            if (systemTag != null) {
               tag.put(key, systemTag);
            }
         } catch (Throwable var4) {
            ReviveClient.LOG.error("Failed to serialize '{}' for config copy", key, var4);
         }
      }
   }

   public boolean charTyped(CharInput input) {
      char chr = (char)input.codepoint();
      int mods = input.modifiers();
      if (NewGuiBindCapture.get().isListeningForSetting()) {
         return true;
      } else if (this.editingSetting == null) {
         if (this.searchFocused) {
            this.searchText = this.searchText + chr;
            return true;
         } else {
            return super.charTyped(input);
         }
      } else {
         if (this.editingSetting instanceof StringSetting) {
            if (chr >= ' ') {
               this.editText = this.editText + chr;
            }
         } else if (Character.isDigit(chr) || chr == '.' || chr == '-') {
            this.editText = this.editText + chr;
         }

         return true;
      }
   }

   public boolean shouldPause() {
      return false;
   }

   public void close() {
      this.commitSliderEdit();
      SettingGroupRenderer.commitStringEdit();
      NewGuiBindCapture.get().cancelSettingListen();
      this.sliderSetting = null;
      this.popupModule = null;
      this.openColor = null;
      this.colorDrag = 0;

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

      super.close();
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
   }

   private static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   private void cycleEnum(EnumSetting<?> es) {
      Enum<?> cur = es.get();
      Enum<?>[] vals = (Enum<?>[])cur.getDeclaringClass().getEnumConstants();
      ((EnumSetting<Enum<?>>)es).set(vals[(cur.ordinal() + 1) % vals.length]);
   }

   private static final class Panel {
      Category cat;
      List<Module> mods;
      float sc;
      int x;
      int y;
      int w;
      int h;
      int contentTop;
      int totalH;
      int visH;
      int maxScroll;
      boolean collapsed;
   }

   private static final class PopupItem {
      Setting<?> setting;
      String groupName;
      boolean isModuleKeybind;
      int y;
      int h;
      int tx;
      int tw;
      boolean hasSlider;
   }
}
