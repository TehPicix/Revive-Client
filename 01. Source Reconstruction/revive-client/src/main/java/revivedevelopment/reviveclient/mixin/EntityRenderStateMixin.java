package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import revivedevelopment.reviveclient.mixininterface.IEntityRenderState;

@Mixin({EntityRenderState.class})
public abstract class EntityRenderStateMixin implements IEntityRenderState {
   @Unique
   private Entity entity;

   @Override
   public Entity revive$getEntity() {
      return this.entity;
   }

   @Override
   public void revive$setEntity(Entity entity) {
      this.entity = entity;
   }
}
