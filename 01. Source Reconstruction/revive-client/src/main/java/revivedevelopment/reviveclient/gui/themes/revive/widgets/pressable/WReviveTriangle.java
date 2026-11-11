package revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.pressable.WTriangle;

public class WReviveTriangle extends WTriangle implements ReviveWidget {
   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      renderer.rotatedQuad(this.x, this.y, this.width, this.height, this.rotation, GuiRenderer.TRIANGLE, this.theme().accentColor.get());
   }
}
