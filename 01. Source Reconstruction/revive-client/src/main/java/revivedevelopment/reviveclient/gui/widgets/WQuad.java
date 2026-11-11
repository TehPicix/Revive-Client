package revivedevelopment.reviveclient.gui.widgets;

import revivedevelopment.reviveclient.utils.render.color.Color;

public abstract class WQuad extends WWidget {
   public Color color;

   public WQuad(Color color) {
      this.color = color;
   }

   @Override
   protected void onCalculateSize() {
      double s = this.theme.scale(32.0);
      this.width = s;
      this.height = s;
   }
}
