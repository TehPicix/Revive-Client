package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.WMultiLabel;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class WReviveMultiLabel extends WMultiLabel implements ReviveWidget {
   public WReviveMultiLabel(String text, boolean title, double maxWidth) {
      super(text, title, maxWidth);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      double h = this.theme.textHeight(this.title);
      Color defaultColor = this.theme().textColor.get();

      for (int i = 0; i < this.lines.size(); i++) {
         renderer.text(this.lines.get(i), this.x, this.y + h * i, this.color != null ? this.color : defaultColor, false);
      }
   }
}
