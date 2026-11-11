package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WView;

public class WReviveView extends WView implements ReviveWidget {
   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (this.canScroll && this.hasScrollBar) {
         renderer.quad(
            this.handleX(), this.handleY(), this.handleWidth(), this.handleHeight(), this.theme().scrollbarColor.get(this.handlePressed, this.handleMouseOver)
         );
      }
   }
}
