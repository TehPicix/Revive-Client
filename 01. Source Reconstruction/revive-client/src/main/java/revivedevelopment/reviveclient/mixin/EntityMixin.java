package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.PlayerMoveEvent;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.Velocity;
import revivedevelopment.reviveclient.systems.modules.movement.NoSlow;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.systems.modules.render.FreeLook;
import revivedevelopment.reviveclient.systems.modules.render.Freecam;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;

@Mixin({Entity.class})
public abstract class EntityMixin {
   @ModifyArgs(
      method = {"method_5697"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1297;method_5762(DDD)V"
      )
   )
   private void onPushAwayFrom(Args args, Entity entity) {
      if (entity instanceof FakePlayerEntity player && player.doNotPush) {
         args.set(0, 0.0);
         args.set(2, 0.0);
      }

      if ((Object)this == ReviveClient.mc.player) {
         Velocity velocity = Modules.get().get(Velocity.class);
         if (velocity != null && velocity.shouldCancelEntityPush()) {
            args.set(0, 0.0);
            args.set(2, 0.0);
         }
      }
   }

   @ModifyExpressionValue(
      method = {"method_5692"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_3610;method_15758(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;)Lnet/minecraft/class_243;"
      )}
   )
   private Vec3d onUpdateMovementInFluidVelocity(Vec3d original) {
      if ((Object)this != ReviveClient.mc.player) {
         return original;
      } else {
         Velocity velocity = Modules.get().get(Velocity.class);
         return velocity != null && velocity.shouldCancelLiquids() ? Vec3d.ZERO : original;
      }
   }

   @Inject(
      method = {"method_5784"},
      at = {@At("HEAD")}
   )
   private void onMove(MovementType type, Vec3d movement, CallbackInfo info) {
      if ((Object)this == ReviveClient.mc.player) {
         ReviveClient.EVENT_BUS.post(PlayerMoveEvent.get(type, movement));
      }
   }

   @Inject(
      method = {"method_5756"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsInvisibleTo(PlayerEntity player, CallbackInfoReturnable<Boolean> info) {
      if (player == null) {
         info.setReturnValue(false);
      }
   }

   @ModifyReturnValue(
      method = {"method_5756"},
      at = {@At("RETURN")}
   )
   private boolean noRenderInvisibility(boolean original) {
      return Modules.get().get(NoRender.class).noInvisibility() ? false : original;
   }

   @Inject(
      method = {"method_5851"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderGlowing(CallbackInfoReturnable<Boolean> info) {
      if (Modules.get().get(NoRender.class).noGlowing()) {
         info.setReturnValue(false);
      }
   }

   @Inject(
      method = {"method_18376"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void getPoseHook(CallbackInfoReturnable<EntityPose> info) {
      if ((Object)this == ReviveClient.mc.player) {
         if (Modules.get().get(ElytraFly.class).canPacketEfly()) {
            info.setReturnValue(EntityPose.GLIDING);
         } else {
            if (ElytraDurabilitySaver.get().pinsGlidePose()) {
               info.setReturnValue(EntityPose.GLIDING);
            }
         }
      }
   }

   @Inject(
      method = {"method_5674"},
      at = {@At("HEAD")}
   )
   private void onTrackedDataSetHook(TrackedData<?> data, CallbackInfo ci) {
      if ((Object)this == ReviveClient.mc.player && data == EntityAccessor.revive$getFlags()) {
         ElytraDurabilitySaver.get().onFlagsTracked();
      }
   }

   @ModifyReturnValue(
      method = {"method_18376"},
      at = {@At("RETURN")}
   )
   private EntityPose modifyGetPose(EntityPose original) {
      if ((Object)this != ReviveClient.mc.player) {
         return original;
      } else {
         return original == EntityPose.CROUCHING
               && !ReviveClient.mc.player.isSneaking()
               && ((PlayerEntityAccessor)ReviveClient.mc.player).revive$canChangeIntoPose(EntityPose.STANDING)
            ? EntityPose.STANDING
            : original;
      }
   }

   @Inject(
      method = {"method_5872"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
      if ((Object)this == ReviveClient.mc.player) {
         Freecam freecam = Modules.get().get(Freecam.class);
         if (freecam.isActive()) {
            freecam.changeLookDirection(cursorDeltaX * 0.15, cursorDeltaY * 0.15);
            ci.cancel();
         } else {
            FreeLook freeLook = Modules.get().get(FreeLook.class);
            if (freeLook != null && freeLook.playerMode()) {
               freeLook.applyMouseDelta(cursorDeltaX * 0.15, cursorDeltaY * 0.15);
               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"method_5844"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void revive$noSlowWeb(BlockState state, Vec3d multiplier, CallbackInfo ci) {
      if ((Object)this == ReviveClient.mc.player && NoSlow.cancelWebSlow(state)) {
         ((Entity)(Object)this).onLanding();
         ci.cancel();
      }
   }
}
