package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.mixininterface.IEntityRenderState;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.ESP;
import revivedevelopment.reviveclient.systems.modules.render.Nametags;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;

@Mixin({EntityRenderer.class})
public abstract class EntityRendererMixin {
   @Inject(
      method = {"method_62425"},
      at = {@At("RETURN")}
   )
   private void revive$attachEntity(Entity entity, float tickProgress, CallbackInfoReturnable<EntityRenderState> cir) {
      ((IEntityRenderState)cir.getReturnValue()).revive$setEntity(entity);
   }

   @Inject(
      method = {"method_62426"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderNametags(Entity entity, CallbackInfoReturnable<Text> cir) {
      if (Modules.get().get(NoRender.class).noNametags()) {
         cir.setReturnValue(null);
      } else if (entity instanceof PlayerEntity player) {
         Nametags nametags = Modules.get().get(Nametags.class);
         if (nametags.playerNametags() && (EntityUtils.getGameMode(player) != null || !nametags.excludeBots())) {
            cir.setReturnValue(null);
         }
      }
   }

   @Inject(
      method = {"method_3933"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderShouldRender(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
      NoRender noRender = Modules.get().get(NoRender.class);
      if (noRender.noEntity(entity)) {
         cir.setReturnValue(false);
      } else if (noRender.noFallingBlocks() && entity instanceof FallingBlockEntity) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"method_62354"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/class_10017;field_61821:I",
         shift = Shift.AFTER,
         opcode = 181
      )},
      require = 0
   )
   private void onUpdateOutlineColor(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
      ESP esp = Modules.get().get(ESP.class);
      if (esp.isGlow() && !esp.shouldSkip(entity)) {
         Color color = esp.getColor(entity);
         if (color == null) {
            return;
         }

         state.outlineColor = color.getPacked();
      }
   }
}
