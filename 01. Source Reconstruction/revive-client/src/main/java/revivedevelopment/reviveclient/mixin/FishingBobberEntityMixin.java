package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.Velocity;

@Mixin({FishingBobberEntity.class})
public abstract class FishingBobberEntityMixin {
   @Inject(
      method = {"method_6954"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPullHookedEntity(Entity entity, CallbackInfo info) {
      if (entity == ReviveClient.mc.player) {
         Velocity velocity = Modules.get().get(Velocity.class);
         if (velocity != null && velocity.shouldCancelPulling()) {
            info.cancel();
         }
      }
   }
}
