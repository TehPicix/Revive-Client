package revivedevelopment.reviveclient.gui.widgets.pressable;

import revivedevelopment.reviveclient.gui.renderer.packer.GuiTexture;

public abstract class WButton extends WPressable {
   protected String text;
   protected double textWidth;
   protected GuiTexture texture;

   public WButton(String text, GuiTexture texture) {
      this.text = text;
      this.texture = texture;
   }

   @Override
   protected void onCalculateSize() {
      double pad = this.pad();
      if (this.text != null) {
         this.textWidth = this.theme.textWidth(this.text);
         this.width = pad + this.textWidth + pad;
         this.height = pad + this.theme.textHeight() + pad;
      } else {
         double s = this.theme.textHeight();
         this.width = pad + s + pad;
         this.height = pad + s + pad;
      }
   }

   public void set(String text) {
      if (this.text == null || Math.round(this.theme.textWidth(text)) != this.textWidth) {
         this.invalidate();
      }

      this.text = text;
   }

   public String getText() {
      return this.text;
   }
}
