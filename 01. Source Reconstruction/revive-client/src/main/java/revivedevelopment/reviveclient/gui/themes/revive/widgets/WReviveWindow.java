package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveGuiTheme;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WWindow;

public class WReviveWindow extends WWindow implements ReviveWidget {
   public WReviveWindow(WWidget icon, String title) {
      super(icon, title);
   }

   @Override
   protected WWindow.WHeader header(WWidget icon) {
      return new WReviveWindow.WReviveHeader(icon);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (this.expanded || this.animProgress > 0.0) {
         ReviveGuiTheme theme = this.theme();
         renderer.quad(this.x, this.y + this.header.height, this.width, this.height - this.header.height, theme.backgroundColor.get());
         renderer.quad(this.x, this.y + this.header.height, theme.scale(2.0), this.height - this.header.height, theme.accentColor.get());
      }
   }

   private class WReviveHeader extends WWindow.WHeader {
      public WReviveHeader(WWidget icon) {
         super(icon);
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         ReviveGuiTheme theme = WReviveWindow.this.theme();
         renderer.quad(this, theme.backgroundColor.get());
         renderer.quad(this.x, this.y + this.height - theme.scale(2.0), this.width, theme.scale(2.0), theme.accentColor.get());
      }
   }
}
