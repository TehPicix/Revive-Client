package revivedevelopment.reviveclient.gui.widgets;

import net.minecraft.item.ItemStack;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;

public class WItem extends WWidget {
   protected ItemStack itemStack;

   public WItem(ItemStack itemStack) {
      this.itemStack = itemStack;
   }

   @Override
   protected void onCalculateSize() {
      double s = this.theme.scale(32.0);
      this.width = s;
      this.height = s;
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (!this.itemStack.isEmpty()) {
         renderer.post(() -> {
            double s = this.theme.scale(2.0);
            renderer.item(this.itemStack, (int)this.x, (int)this.y, (float)s, true);
         });
      }
   }

   public void set(ItemStack itemStack) {
      this.itemStack = itemStack;
   }
}
