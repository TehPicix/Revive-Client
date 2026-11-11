package revivedevelopment.reviveclient.gui.widgets;

public class WVerticalSeparator extends WWidget {
   @Override
   protected void onCalculateSize() {
      this.width = this.theme.scale(3.0);
      this.height = 1.0;
   }
}
