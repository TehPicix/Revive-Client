package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.pingbypass.PbOldServer;

@Mixin({FireworkRocketItem.class})
public abstract class PbOldServerFireworkMixin {
   @Inject(
      method = {"method_7884"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void revive$oldFireworkOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
      if (context.getPlayer() != null && context.getPlayer().isGliding() && PbOldServer.emulates(context.getPlayer(), 770)) {
         cir.setReturnValue(ActionResult.SUCCESS);
      }
   }
}
