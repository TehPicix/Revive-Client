package revivedevelopment.reviveclient.utils.render;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.SubmitEntitiesEvent;
import revivedevelopment.reviveclient.mixininterface.IEntityRenderState;

public final class SkinEntityRenderer {
   private SkinEntityRenderer() {
   }

   public static void render(SubmitEntitiesEvent event, Entity entity, double scale) {
      EntityRenderer<Entity, EntityRenderState> renderer = (EntityRenderer<Entity, EntityRenderState>)(EntityRenderer<?, ?>)ReviveClient.mc.getEntityRenderDispatcher().getRenderer(entity);
      EntityRenderState state = renderer.getAndUpdateRenderState(entity, 1.0F);
      ((IEntityRenderState)state).revive$setEntity(entity);
      state.displayName = null;
      if (state instanceof PlayerEntityRenderState playerState) {
         playerState.playerName = null;
      }

      state.outlineColor = 0;
      Vec3d camera = event.cameraState.pos;
      Vec3d offset = renderer.getPositionOffset(state);
      event.matrices.push();

      try {
         event.matrices.translate(state.x - camera.x + offset.x, state.y - camera.y + offset.y, state.z - camera.z + offset.z);
         if (scale != 1.0) {
            event.matrices.scale((float)scale, (float)scale, (float)scale);
         }

         renderer.render(state, event.matrices, event.queue, event.cameraState);
      } finally {
         event.matrices.pop();
      }
   }
}
