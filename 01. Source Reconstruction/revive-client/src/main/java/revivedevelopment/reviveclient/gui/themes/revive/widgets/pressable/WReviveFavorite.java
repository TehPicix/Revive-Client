package revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable;

import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.pressable.WFavorite;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class WReviveFavorite extends WFavorite implements ReviveWidget {
   public WReviveFavorite(boolean checked) {
      super(checked);
   }

   @Override
   protected Color getColor() {
      return this.theme().favoriteColor.get();
   }
}
