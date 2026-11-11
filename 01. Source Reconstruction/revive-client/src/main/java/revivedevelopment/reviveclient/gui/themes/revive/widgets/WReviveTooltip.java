package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.WTooltip;

public class WReviveTooltip extends WTooltip implements ReviveWidget {
   public WReviveTooltip(String text) {
      super(text);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      renderer.quad(this, this.theme().backgroundColor.get());
      renderer.quad(this.x, this.y, this.width, this.theme().scale(1.0), this.theme().accentColor.get());
   }
}
