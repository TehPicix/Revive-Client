package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.ClipAtLedgeEvent;

@Mixin({PlayerEntity.class})
public abstract class PlayerEntityMixin extends LivingEntity {
   @Shadow
   public abstract PlayerAbilities method_31549();

   protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
      super(entityType, world);
   }

   @Inject(
      method = {"method_21825"},
      at = {@At("HEAD")},
      cancellable = true
   )
   protected void clipAtLedge(CallbackInfoReturnable<Boolean> info) {
      if (this.getEntityWorld().isClient()) {
         ClipAtLedgeEvent event = (ClipAtLedgeEvent)ReviveClient.EVENT_BUS.post(ClipAtLedgeEvent.get());
         if (event.isSet()) {
            info.setReturnValue(event.isClip());
         }
      }
   }

   @Inject(
      method = {"method_7325"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsSpectator(CallbackInfoReturnable<Boolean> info) {
      if (ReviveClient.mc.getNetworkHandler() == null) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"method_68878"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsCreative(CallbackInfoReturnable<Boolean> info) {
      if (ReviveClient.mc.getNetworkHandler() == null) {
         info.setReturnValue(false);
      }
   }
}
