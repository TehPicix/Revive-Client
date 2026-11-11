package revivedevelopment.reviveclient.mixin;

import java.util.Optional;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.TooltipDataEvent;

@Mixin({Item.class})
public abstract class ItemMixin {
   @Inject(
      method = {"method_32346"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onTooltipData(ItemStack stack, CallbackInfoReturnable<Optional<TooltipData>> cir) {
      TooltipDataEvent event = (TooltipDataEvent)ReviveClient.EVENT_BUS.post(TooltipDataEvent.get(stack));
      if (event.tooltipData != null) {
         cir.setReturnValue(Optional.of(event.tooltipData));
      }
   }
}
