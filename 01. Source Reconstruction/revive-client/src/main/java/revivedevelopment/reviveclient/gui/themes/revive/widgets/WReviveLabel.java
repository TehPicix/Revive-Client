package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.WLabel;

public class WReviveLabel extends WLabel implements ReviveWidget {
   public WReviveLabel(String text, boolean title) {
      super(text, title);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (!this.text.isEmpty()) {
         renderer.text(
            this.text,
            this.x,
            this.y,
            this.color != null ? this.color : (this.title ? this.theme().titleTextColor.get() : this.theme().textColor.get()),
            this.title
         );
      }
   }
}
