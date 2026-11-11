package revivedevelopment.reviveclient.gui.renderer.operations;

import revivedevelopment.reviveclient.gui.renderer.GuiRenderOperation;
import revivedevelopment.reviveclient.renderer.text.TextRenderer;

public class TextOperation extends GuiRenderOperation<TextOperation> {
   private String text;
   private TextRenderer renderer;
   public boolean title;

   public TextOperation set(String text, TextRenderer renderer, boolean title) {
      this.text = text;
      this.renderer = renderer;
      this.title = title;
      return this;
   }

   @Override
   protected void onRun() {
      this.renderer.render(this.text, this.x, this.y, this.color);
   }
}
