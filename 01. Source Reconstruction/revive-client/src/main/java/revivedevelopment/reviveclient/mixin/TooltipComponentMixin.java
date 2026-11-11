package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.utils.tooltip.ReviveTooltipData;

@Mixin({TooltipComponent.class})
public interface TooltipComponentMixin {
   @Inject(
      method = {"method_32663"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void shortcutReviveTooltipData(TooltipData tooltipData, CallbackInfoReturnable<TooltipComponent> cir) {
      if (tooltipData instanceof ReviveTooltipData) {
         cir.setReturnValue(null);
      }
   }
}
