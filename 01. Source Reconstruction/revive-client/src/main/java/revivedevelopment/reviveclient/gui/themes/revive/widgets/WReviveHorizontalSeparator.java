package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveGuiTheme;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.WHorizontalSeparator;

public class WReviveHorizontalSeparator extends WHorizontalSeparator implements ReviveWidget {
   public WReviveHorizontalSeparator(String text) {
      super(text);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (this.text == null) {
         this.renderWithoutText(renderer);
      } else {
         this.renderWithText(renderer);
      }
   }

   private void renderWithoutText(GuiRenderer renderer) {
      ReviveGuiTheme theme = this.theme();
      double s = theme.scale(1.0);
      double w = this.width / 2.0;
      renderer.quad(this.x, this.y + s, w, s, theme.separatorEdges.get(), theme.separatorCenter.get());
      renderer.quad(this.x + w, this.y + s, w, s, theme.separatorCenter.get(), theme.separatorEdges.get());
   }

   private void renderWithText(GuiRenderer renderer) {
      ReviveGuiTheme theme = this.theme();
      double s = theme.scale(2.0);
      double h = theme.scale(1.0);
      double textStart = Math.round(this.width / 2.0 - this.textWidth / 2.0 - s);
      double textEnd = s + textStart + this.textWidth + s;
      double offsetY = Math.round(this.height / 2.0);
      renderer.quad(this.x, this.y + offsetY, textStart, h, theme.separatorEdges.get(), theme.separatorCenter.get());
      renderer.text(this.text, this.x + textStart + s, this.y, theme.separatorText.get(), false);
      renderer.quad(this.x + textEnd, this.y + offsetY, this.width - textEnd, h, theme.separatorCenter.get(), theme.separatorEdges.get());
   }
}
