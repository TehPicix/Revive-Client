package revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.pressable.WMinus;

public class WReviveMinus extends WMinus implements ReviveWidget {
   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      double pad = this.pad();
      double s = this.theme.scale(3.0);
      this.renderBackground(renderer, this, this.pressed, this.mouseOver);
      renderer.quad(this.x + pad, this.y + this.height / 2.0 - s / 2.0, this.width - pad * 2.0, s, this.theme().minusColor.get());
   }
}
