package revivedevelopment.reviveclient.gui.themes.revive;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.utils.BaseWidget;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.utils.render.color.Color;

public interface ReviveWidget extends BaseWidget {
   default ReviveGuiTheme theme() {
      return (ReviveGuiTheme)this.getTheme();
   }

   default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
      ReviveGuiTheme theme = this.theme();
      double s = theme.scale(1.0);
      renderer.quad(widget.x + s, widget.y + s, widget.width - s * 2.0, widget.height - s * 2.0, theme.backgroundColor.get(pressed, mouseOver));
      Color outline = theme.outlineColor.get(pressed, mouseOver);
      renderer.quad(widget.x, widget.y, widget.width, s, outline);
      renderer.quad(widget.x, widget.y + widget.height - s, widget.width, s, outline);
      renderer.quad(widget.x, widget.y + s, s, widget.height - s * 2.0, outline);
      renderer.quad(widget.x + widget.width - s, widget.y + s, s, widget.height - s * 2.0, outline);
   }
}
