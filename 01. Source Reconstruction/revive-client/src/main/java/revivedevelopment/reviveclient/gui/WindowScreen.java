package revivedevelopment.reviveclient.gui;

import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WWindow;

public abstract class WindowScreen extends WidgetScreen {
   protected final WWindow window;

   public WindowScreen(GuiTheme theme, WWidget icon, String title) {
      super(theme, title);
      this.window = super.add(theme.window(icon, title)).center().widget();
      this.window.view.scrollOnlyWhenMouseOver = false;
   }

   public WindowScreen(GuiTheme theme, String title) {
      this(theme, null, title);
   }

   @Override
   public <W extends WWidget> Cell<W> add(W widget) {
      return this.window.add(widget);
   }

   @Override
   public void clear() {
      this.window.clear();
   }
}
