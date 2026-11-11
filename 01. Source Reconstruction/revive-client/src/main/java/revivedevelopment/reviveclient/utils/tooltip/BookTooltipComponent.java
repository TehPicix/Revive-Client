package revivedevelopment.reviveclient.utils.tooltip;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

public class BookTooltipComponent implements TooltipComponent, ReviveTooltipData {
   private static final Identifier TEXTURE_BOOK_BACKGROUND = Identifier.of("textures/gui/book.png");
   private final Text page;

   public BookTooltipComponent(Text page) {
      this.page = page;
   }

   @Override
   public TooltipComponent getComponent() {
      return this;
   }

   public int getHeight(TextRenderer textRenderer) {
      return 134;
   }

   public int getWidth(TextRenderer textRenderer) {
      return 112;
   }

   public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext context) {
      context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE_BOOK_BACKGROUND, x - 10, y, 0.0F, 0.0F, 128, 128, 179, 179);
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.translate(x + 16, y + 12);
      matrices.scale(0.7F, 0.7F);
      int offset = 0;

      for (OrderedText line : textRenderer.wrapLines(this.page, 112)) {
         context.drawText(textRenderer, line, 0, offset, -16777216, false);
         offset += 8;
      }

      matrices.popMatrix();
   }
}
