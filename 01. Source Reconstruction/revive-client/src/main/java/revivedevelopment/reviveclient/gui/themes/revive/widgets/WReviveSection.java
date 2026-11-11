package revivedevelopment.reviveclient.gui.themes.revive.widgets;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WSection;
import revivedevelopment.reviveclient.gui.widgets.pressable.WTriangle;

public class WReviveSection extends WSection {
   public WReviveSection(String title, boolean expanded, WWidget headerWidget) {
      super(title, expanded, headerWidget);
   }

   @Override
   protected WSection.WHeader createHeader() {
      return new WReviveSection.WReviveHeader(this.title);
   }

   protected static class WHeaderTriangle extends WTriangle implements ReviveWidget {
      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         renderer.rotatedQuad(this.x, this.y, this.width, this.height, this.rotation, GuiRenderer.TRIANGLE, this.theme().accentColor.get());
      }
   }

   protected class WReviveHeader extends WSection.WHeader {
      private WTriangle triangle;

      public WReviveHeader(String title) {
         super(title);
      }

      @Override
      public void init() {
         this.add(this.theme.horizontalSeparator(this.title)).expandX();
         if (WReviveSection.this.headerWidget != null) {
            this.add(WReviveSection.this.headerWidget);
         }

         this.triangle = new WReviveSection.WHeaderTriangle();
         this.triangle.theme = this.theme;
         this.triangle.action = () -> this.onClick();
         this.add(this.triangle);
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         this.triangle.rotation = (1.0 - WReviveSection.this.animProgress) * -90.0;
      }
   }
}
