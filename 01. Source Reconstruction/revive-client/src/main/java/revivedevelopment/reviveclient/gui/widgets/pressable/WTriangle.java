package revivedevelopment.reviveclient.gui.widgets.pressable;

public abstract class WTriangle extends WPressable {
   public double rotation;

   @Override
   protected void onCalculateSize() {
      double s = this.theme.textHeight();
      this.width = s;
      this.height = s;
   }
}
