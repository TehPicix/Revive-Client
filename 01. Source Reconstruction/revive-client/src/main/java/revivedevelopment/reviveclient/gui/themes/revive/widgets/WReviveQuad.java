package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.widgets.WQuad;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class WReviveQuad extends WQuad {
   public WReviveQuad(Color color) {
      super(color);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      renderer.quad(this.x, this.y, this.width, this.height, this.color);
   }
}
