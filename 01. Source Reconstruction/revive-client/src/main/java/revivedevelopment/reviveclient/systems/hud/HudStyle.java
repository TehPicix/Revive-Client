package revivedevelopment.reviveclient.systems.hud;

import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.utils.render.color.Color;

public final class HudStyle {
   public static final int RADIUS = 2;
   public static final int ACCENT_BAR = 1;
   public static final Color PRIMARY = new Color(255, 105, 190);
   public static final Color SECONDARY = new Color(255, 105, 190);
   public static final Color ACCENT = new Color(255, 105, 190);
   public static final Color ACCENT_LIGHT = new Color(255, 140, 210);
   public static final Color BORDER = new Color(255, 105, 190, 220);
   public static final Color BORDER_LIGHT = new Color(255, 140, 210, 220);
   public static final Color TEXT_PRIMARY = new Color(240, 240, 250);
   public static final Color TEXT_SECONDARY = new Color(140, 140, 165);
   public static final Color TEXT_VALUE = new Color(170, 170, 200);
   public static final Color BG_PANEL = new Color(24, 24, 32, 200);
   public static final Color BG_MODULE = new Color(30, 30, 40, 205);
   public static final Color BG_MODULE_HOVER = new Color(38, 38, 50, 215);
   public static final Color BG_HEADER = new Color(28, 28, 38, 215);
   public static final Color ENABLED = new Color(90, 255, 130);
   public static final Color DISABLED = new Color(255, 70, 70);

   private HudStyle() {
   }

   public static void update() {
      FontManager fm = FontManager.get();
      int primary = fm.getPrimaryColor();
      int secondary = fm.getSecondaryColor();
      int hudPrimary = fm.getHudTextPrimary();
      int hudSecondary = fm.getHudTextSecondary();
      set(PRIMARY, primary, 255);
      set(ACCENT, primary, 255);
      set(ACCENT_LIGHT, primary, 255);
      set(SECONDARY, secondary, 255);
      set(BORDER, secondary, 220);
      set(BORDER_LIGHT, secondary, 220);
      set(TEXT_PRIMARY, hudPrimary, hudPrimary >>> 24 & 0xFF);
      set(TEXT_SECONDARY, hudSecondary, hudSecondary >>> 24 & 0xFF);
      set(TEXT_VALUE, hudSecondary, hudSecondary >>> 24 & 0xFF);
   }

   private static void set(Color c, int argb, int a) {
      c.r = argb >> 16 & 0xFF;
      c.g = argb >> 8 & 0xFF;
      c.b = argb & 0xFF;
      c.a = a;
   }

   public static Color withAlpha(Color base, double factor) {
      return new Color(base.r, base.g, base.b, (int)Math.round(base.a * Math.max(0.0, Math.min(1.0, factor))));
   }

   public static Color alpha(Color base, int a) {
      return new Color(base.r, base.g, base.b, Math.max(0, Math.min(255, a)));
   }
}
