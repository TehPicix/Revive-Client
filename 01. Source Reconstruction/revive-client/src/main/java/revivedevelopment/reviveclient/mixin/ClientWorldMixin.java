package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.RemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.EntityAddedEvent;
import revivedevelopment.reviveclient.events.entity.EntityRemovedEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;

@Mixin({ClientWorld.class})
public abstract class ClientWorldMixin implements IClientWorld {
   @Shadow
   public abstract Entity method_8469(int var1);

   @Inject(
      method = {"method_53875"},
      at = {@At("TAIL")}
   )
   private void revive$onAddEntity(Entity entity, CallbackInfo info) {
      if (entity != null) {
         ReviveClient.EVENT_BUS.post(EntityAddedEvent.get(entity));
      }
   }

   @Inject(
      method = {"method_2945"},
      at = {@At("HEAD")}
   )
   private void revive$onRemoveEntity(int entityId, RemovalReason removalReason, CallbackInfo info) {
      Entity entity = this.method_8469(entityId);
      if (entity != null) {
         ReviveClient.EVENT_BUS.post(EntityRemovedEvent.get(entity));
      }
   }

   @Override
   public int revive$getSequence() {
      PendingUpdateManager pum = ((ClientWorldAccessor)this).getPendingUpdateManager();
      return ((PendingUpdateManagerAccessor)pum).getSequence();
   }

   @Override
   public int revive$getAndIncrementSequence() {
      PendingUpdateManager pum = ((ClientWorldAccessor)this).getPendingUpdateManager();
      ((PendingUpdateManagerAccessor)pum).invokeIncrementSequence();
      return ((PendingUpdateManagerAccessor)pum).getSequence();
   }
}
