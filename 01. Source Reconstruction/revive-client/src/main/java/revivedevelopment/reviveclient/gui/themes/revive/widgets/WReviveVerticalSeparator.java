package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveGuiTheme;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.WVerticalSeparator;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class WReviveVerticalSeparator extends WVerticalSeparator implements ReviveWidget {
   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      ReviveGuiTheme theme = this.theme();
      Color colorEdges = theme.separatorEdges.get();
      Color colorCenter = theme.separatorCenter.get();
      double s = theme.scale(1.0);
      double offsetX = Math.round(this.width / 2.0);
      renderer.quad(this.x + offsetX, this.y, s, this.height / 2.0, colorEdges, colorEdges, colorCenter, colorCenter);
      renderer.quad(this.x + offsetX, this.y + this.height / 2.0, s, this.height / 2.0, colorCenter, colorCenter, colorEdges, colorEdges);
   }
}
