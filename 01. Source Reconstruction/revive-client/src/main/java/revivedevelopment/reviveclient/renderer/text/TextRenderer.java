package revivedevelopment.reviveclient.renderer.text;

import revivedevelopment.reviveclient.renderer.Fonts;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.utils.render.color.Color;

public interface TextRenderer {
   static TextRenderer get() {
      Fonts.ensureLoaded();
      return (TextRenderer)(Config.get().customFont.get() ? Fonts.RENDERER : VanillaTextRenderer.INSTANCE);
   }

   void setAlpha(double var1);

   void begin(double var1, boolean var3, boolean var4);

   default void begin(double scale) {
      this.begin(scale, false, false);
   }

   default void begin() {
      this.begin(1.0, false, false);
   }

   default void beginBig() {
      this.begin(1.0, false, true);
   }

   double getWidth(String var1, int var2, boolean var3);

   default double getWidth(String text, boolean shadow) {
      return this.getWidth(text, text.length(), shadow);
   }

   default double getWidth(String text) {
      return this.getWidth(text, text.length(), false);
   }

   double getHeight(boolean var1);

   default double getHeight() {
      return this.getHeight(false);
   }

   double render(String var1, double var2, double var4, Color var6, boolean var7);

   default double render(String text, double x, double y, Color color) {
      return this.render(text, x, y, color, false);
   }

   boolean isBuilding();

   void end();
}
