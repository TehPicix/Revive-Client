package revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.renderer.packer.GuiTexture;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveGuiTheme;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;

public class WReviveButton extends WButton implements ReviveWidget {
   public WReviveButton(String text, GuiTexture texture) {
      super(text, texture);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      ReviveGuiTheme theme = this.theme();
      double pad = this.pad();
      this.renderBackground(renderer, this, this.pressed, this.mouseOver);
      if (this.text != null) {
         renderer.text(this.text, this.x + this.width / 2.0 - this.textWidth / 2.0, this.y + pad, theme.textColor.get(), false);
      } else {
         double ts = theme.textHeight();
         renderer.quad(this.x + this.width / 2.0 - ts / 2.0, this.y + pad, ts, ts, this.texture, theme.textColor.get());
      }
   }
}
