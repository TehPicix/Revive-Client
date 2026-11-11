package revivedevelopment.reviveclient.utils.tooltip;

import net.minecraft.client.gui.tooltip.OrderedTextTooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public class TextTooltipComponent extends OrderedTextTooltipComponent implements ReviveTooltipData {
   public TextTooltipComponent(OrderedText text) {
      super(text);
   }

   public TextTooltipComponent(Text text) {
      this(text.asOrderedText());
   }

   @Override
   public TooltipComponent getComponent() {
      return this;
   }
}
