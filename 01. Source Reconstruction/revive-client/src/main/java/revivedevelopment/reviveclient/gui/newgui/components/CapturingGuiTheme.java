package revivedevelopment.reviveclient.gui.newgui.components;

import revivedevelopment.reviveclient.gui.themes.revive.ReviveGuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.Settings;

public class CapturingGuiTheme extends ReviveGuiTheme {
   public Settings captured;

   @Override
   public WWidget settings(Settings settings) {
      if (this.captured == null) {
         this.captured = settings;
      }

      return super.settings(settings);
   }

   @Override
   public WWidget settings(Settings settings, String filter) {
      if (this.captured == null) {
         this.captured = settings;
      }

      return super.settings(settings, filter);
   }
}
