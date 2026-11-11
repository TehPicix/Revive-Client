package revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveGuiTheme;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.pressable.WPlus;

public class WRevivePlus extends WPlus implements ReviveWidget {
   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      ReviveGuiTheme theme = this.theme();
      double pad = this.pad();
      double s = theme.scale(3.0);
      this.renderBackground(renderer, this, this.pressed, this.mouseOver);
      renderer.quad(this.x + pad, this.y + this.height / 2.0 - s / 2.0, this.width - pad * 2.0, s, theme.plusColor.get());
      renderer.quad(this.x + this.width / 2.0 - s / 2.0, this.y + pad, s, this.height - pad * 2.0, theme.plusColor.get());
   }
}
