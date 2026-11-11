package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.FinishUsingItemEvent;
import revivedevelopment.reviveclient.events.entity.player.StoppedUsingItemEvent;
import revivedevelopment.reviveclient.events.game.ItemStackTooltipEvent;
import revivedevelopment.reviveclient.utils.Utils;

@Mixin({ItemStack.class})
public abstract class ItemStackMixin {
   @ModifyReturnValue(
      method = {"method_7950"},
      at = {@At("RETURN")}
   )
   private List<Text> onGetTooltip(List<Text> original) {
      if (Utils.canUpdate()) {
         ItemStackTooltipEvent event = (ItemStackTooltipEvent)ReviveClient.EVENT_BUS.post(new ItemStackTooltipEvent((ItemStack)(Object)this, original));
         return event.list();
      } else {
         return original;
      }
   }

   @Inject(
      method = {"method_7910"},
      at = {@At("HEAD")}
   )
   private void onFinishUsing(World world, LivingEntity user, CallbackInfoReturnable<ItemStack> info) {
      if (user == ReviveClient.mc.player) {
         ReviveClient.EVENT_BUS.post(FinishUsingItemEvent.get((ItemStack)(Object)this));
      }
   }

   @Inject(
      method = {"method_7930"},
      at = {@At("HEAD")}
   )
   private void onStoppedUsing(World world, LivingEntity user, int remainingUseTicks, CallbackInfo info) {
      if (user == ReviveClient.mc.player) {
         ReviveClient.EVENT_BUS.post(StoppedUsingItemEvent.get((ItemStack)(Object)this));
      }
   }
}
