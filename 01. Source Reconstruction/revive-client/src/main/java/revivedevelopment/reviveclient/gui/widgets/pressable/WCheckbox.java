package revivedevelopment.reviveclient.gui.widgets.pressable;

public abstract class WCheckbox extends WPressable {
   public boolean checked;

   public WCheckbox(boolean checked) {
      this.checked = checked;
   }

   @Override
   protected void onCalculateSize() {
      double pad = this.pad();
      double s = this.theme.textHeight();
      this.width = pad + s + pad;
      this.height = pad + s + pad;
   }

   @Override
   protected void onPressed(int button) {
      this.checked = !this.checked;
   }
}
