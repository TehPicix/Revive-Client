package revivedevelopment.reviveclient.gui.themes.revive.widgets.input;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveGuiTheme;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.input.WDropdown;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class WReviveDropdown<T> extends WDropdown<T> implements ReviveWidget {
   public WReviveDropdown(T[] values, T value) {
      super(values, value);
   }

   @Override
   protected WDropdown.WDropdownRoot createRootWidget() {
      return new WReviveDropdown.WRoot();
   }

   @Override
   protected WDropdown<T>.WDropdownValue createValueWidget() {
      return new WReviveDropdown.WValue();
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      ReviveGuiTheme theme = this.theme();
      double pad = this.pad();
      double s = theme.textHeight();
      this.renderBackground(renderer, this, this.pressed, this.mouseOver);
      String text = this.get().toString();
      double w = theme.textWidth(text);
      renderer.text(text, this.x + pad + this.maxValueWidth / 2.0 - w / 2.0, this.y + pad, theme.textColor.get(), false);
      renderer.rotatedQuad(this.x + pad + this.maxValueWidth + pad, this.y + pad, s, s, 0.0, GuiRenderer.TRIANGLE, theme.accentColor.get());
   }

   private static class WRoot extends WDropdown.WDropdownRoot implements ReviveWidget {
      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         ReviveGuiTheme theme = this.theme();
         double s = theme.scale(1.0);
         Color c = theme.outlineColor.get();
         renderer.quad(this.x, this.y + this.height - s, this.width, s, c);
         renderer.quad(this.x, this.y, s, this.height - s, c);
         renderer.quad(this.x + this.width - s, this.y, s, this.height - s, c);
      }
   }

   private class WValue extends WDropdown<T>.WDropdownValue implements ReviveWidget {
      @Override
      protected void onCalculateSize() {
         double pad = this.pad();
         this.width = pad + this.theme.textWidth(this.value.toString()) + pad;
         this.height = pad + this.theme.textHeight() + pad;
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         ReviveGuiTheme theme = this.theme();
         Color color = theme.backgroundColor.get(this.pressed, this.mouseOver, true);
         int preA = color.a;
         color.a = color.a + color.a / 2;
         color.validate();
         renderer.quad(this, color);
         color.a = preA;
         String text = this.value.toString();
         renderer.text(text, this.x + this.width / 2.0 - theme.textWidth(text) / 2.0, this.y + this.pad(), theme.textColor.get(), false);
      }
   }
}
