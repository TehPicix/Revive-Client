package revivedevelopment.reviveclient.utils.tooltip;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class ContainerTooltipComponent implements TooltipComponent, ReviveTooltipData {
   private static final Identifier TEXTURE_CONTAINER_BACKGROUND = ReviveClient.identifier("textures/container.png");
   private final ItemStack[] items;
   private final Color color;

   public ContainerTooltipComponent(ItemStack[] items, Color color) {
      this.items = items;
      this.color = color;
   }

   @Override
   public TooltipComponent getComponent() {
      return this;
   }

   public int getHeight(TextRenderer textRenderer) {
      return 67;
   }

   public int getWidth(TextRenderer textRenderer) {
      return 176;
   }

   public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext context) {
      context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE_CONTAINER_BACKGROUND, x, y, 0.0F, 0.0F, 176, 67, 176, 67, this.color.getPacked());
      int row = 0;
      int i = 0;

      for (ItemStack itemStack : this.items) {
         RenderUtils.drawItem(context, itemStack, x + 8 + i * 18, y + 7 + row * 18, 1.0F, true, null, false);
         if (++i >= 9) {
            i = 0;
            row++;
         }
      }
   }
}
