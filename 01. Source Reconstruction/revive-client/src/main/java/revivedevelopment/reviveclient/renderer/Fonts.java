package revivedevelopment.reviveclient.renderer;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.revive.CustomFontChangedEvent;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.renderer.text.CustomTextRenderer;
import revivedevelopment.reviveclient.renderer.text.FontFace;
import revivedevelopment.reviveclient.renderer.text.FontFamily;
import revivedevelopment.reviveclient.renderer.text.FontInfo;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.utils.PreInit;
import revivedevelopment.reviveclient.utils.render.FontUtils;

public class Fonts {
   public static final String[] BUILTIN_FONTS = new String[]{"JetBrains Mono", "Comfortaa", "Tw Cen MT", "Pixelation"};
   public static String DEFAULT_FONT_FAMILY;
   public static FontFace DEFAULT_FONT;
   public static final List<FontFamily> FONT_FAMILIES = new ArrayList<>();
   public static CustomTextRenderer RENDERER;
   private static FontFace pendingFont;
   private static boolean loaded;

   private Fonts() {
   }

   @PreInit
   public static void refresh() {
      FONT_FAMILIES.clear();

      for (String builtinFont : BUILTIN_FONTS) {
         FontUtils.loadBuiltin(FONT_FAMILIES, builtinFont);
      }

      for (String fontPath : FontUtils.getSearchPaths()) {
         FontUtils.loadSystem(FONT_FAMILIES, new File(fontPath));
      }

      FONT_FAMILIES.sort(Comparator.comparing(FontFamily::getName));
      ReviveClient.LOG.info("Found {} font families.", FONT_FAMILIES.size());
      DEFAULT_FONT_FAMILY = FontUtils.getBuiltinFontInfo(BUILTIN_FONTS[1]).family();
      DEFAULT_FONT = getFamily(DEFAULT_FONT_FAMILY).get(FontInfo.Type.Regular);
      Config config = Config.get();
      pendingFont = config != null ? config.font.get() : DEFAULT_FONT;
   }

   public static void ensureLoaded() {
      if (!loaded && pendingFont != null) {
         loaded = true;
         load(pendingFont);
      }
   }

   public static void load(FontFace fontFace) {
      if (!loaded) {
         pendingFont = fontFace;
      } else {
         if (RENDERER != null) {
            if (RENDERER.fontFace.equals(fontFace)) {
               return;
            }

            RENDERER.destroy();
         }

         try {
            RENDERER = new CustomTextRenderer(fontFace);
            ReviveClient.EVENT_BUS.post(CustomFontChangedEvent.get());
         } catch (Exception var2) {
            if (fontFace.equals(DEFAULT_FONT)) {
               throw new RuntimeException("Failed to load default font: " + fontFace, var2);
            }

            ReviveClient.LOG.error("Failed to load font: {}", fontFace, var2);
            load(DEFAULT_FONT);
         }

         if (ReviveClient.mc.currentScreen instanceof WidgetScreen && Config.get().customFont.get()) {
            ((WidgetScreen)ReviveClient.mc.currentScreen).invalidate();
         }
      }
   }

   public static FontFamily getFamily(String name) {
      for (FontFamily fontFamily : FONT_FAMILIES) {
         if (fontFamily.getName().equalsIgnoreCase(name)) {
            return fontFamily;
         }
      }

      return null;
   }
}
