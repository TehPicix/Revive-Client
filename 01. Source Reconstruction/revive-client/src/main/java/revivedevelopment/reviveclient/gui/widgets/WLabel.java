package revivedevelopment.reviveclient.gui.widgets;

import revivedevelopment.reviveclient.gui.widgets.pressable.WPressable;
import revivedevelopment.reviveclient.utils.render.color.Color;

public abstract class WLabel extends WPressable {
   public Color color;
   protected String text;
   protected boolean title;

   public WLabel(String text, boolean title) {
      this.text = text;
      this.title = title;
   }

   @Override
   protected void onCalculateSize() {
      this.width = this.theme.textWidth(this.text, this.text.length(), this.title);
      this.height = this.theme.textHeight(this.title);
   }

   @Override
   public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
      return this.action != null ? super.onMouseClicked(mouseX, mouseY, button, used) : false;
   }

   @Override
   public boolean onMouseReleased(double mouseX, double mouseY, int button) {
      return this.action != null ? super.onMouseReleased(mouseX, mouseY, button) : false;
   }

   public void set(String text) {
      if (Math.round(this.theme.textWidth(text, text.length(), this.title)) != this.width) {
         this.invalidate();
      }

      this.text = text;
   }

   public String get() {
      return this.text;
   }

   public WLabel color(Color color) {
      this.color = color;
      return this;
   }
}
