package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.Velocity;
import revivedevelopment.reviveclient.systems.modules.movement.Sprint;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin extends Entity {
   @Unique
   private Vec3d revive$preMeleeKBVel;
   @Unique
   private Vec3d revive$preJumpVel;

   public LivingEntityMixin(EntityType<?> type, World world) {
      super(type, world);
   }

   @Inject(
      method = {"method_6005"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onTakeKnockback(double strength, double x, double z, CallbackInfo info) {
      this.revive$preMeleeKBVel = null;
      if ((Object)this == ReviveClient.mc.player) {
         Velocity v = Modules.get().get(Velocity.class);
         if (v != null && v.isActive() && v.shouldCancelLivingEntityKnockback()) {
            switch (v.getMode()) {
               case Grim:
                  this.revive$preMeleeKBVel = ReviveClient.mc.player.getVelocity();
                  break;
               case GrimV3:
                  if (v.isPlayerPhased() || v.isCollidingWithEntities()) {
                     info.cancel();
                  }
                  break;
               default:
                  if (v.isFullCancel()) {
                     info.cancel();
                  } else {
                     this.revive$preMeleeKBVel = ReviveClient.mc.player.getVelocity();
                  }
            }
         }
      }
   }

   @Inject(
      method = {"method_6005"},
      at = {@At("TAIL")}
   )
   private void afterTakeKnockback(double strength, double x, double z, CallbackInfo info) {
      Vec3d pre = this.revive$preMeleeKBVel;
      this.revive$preMeleeKBVel = null;
      if (pre != null && (Object)this == ReviveClient.mc.player) {
         Velocity v = Modules.get().get(Velocity.class);
         if (v != null) {
            Vec3d post = this.getVelocity();
            double hMul = v.getHorizontalMultiplier();
            double vMul = v.getVerticalMultiplier();
            this.setVelocity(pre.x + (post.x - pre.x) * hMul, pre.y + (post.y - pre.y) * vMul, pre.z + (post.z - pre.z) * hMul);
         }
      }
   }

   @Inject(
      method = {"method_6043"},
      at = {@At("HEAD")}
   )
   private void beforeJump(CallbackInfo info) {
      this.revive$preJumpVel = this.getVelocity();
   }

   @Inject(
      method = {"method_6043"},
      at = {@At("TAIL")}
   )
   private void afterJump(CallbackInfo info) {
      Vec3d pre = this.revive$preJumpVel;
      this.revive$preJumpVel = null;
      if (pre != null && (Object)this == ReviveClient.mc.player && this.isSprinting()) {
         if (!this.getVelocity().equals(pre)) {
            Sprint sprint = Modules.get().get(Sprint.class);
            if (sprint != null && sprint.isMotionSpoofActive() && sprint.hasDesiredRotation()) {
               float real = this.getYaw() * (float) (Math.PI / 180.0);
               float want = sprint.getDesiredYaw() * (float) (Math.PI / 180.0);
               this.setVelocity(
                  this.getVelocity().add((MathHelper.sin(real) - MathHelper.sin(want)) * 0.2F, 0.0, (MathHelper.cos(want) - MathHelper.cos(real)) * 0.2F)
               );
            }
         }
      }
   }

   @Inject(
      method = {"method_6037"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderEatParticles(ItemStack stack, int count, CallbackInfo info) {
      if (Modules.get().get(NoRender.class).noEatParticles() && stack.getComponents().contains(DataComponentTypes.FOOD)) {
         info.cancel();
      }
   }

   @ModifyReturnValue(
      method = {"method_6128"},
      at = {@At("RETURN")}
   )
   private boolean isGlidingHook(boolean original) {
      if ((Object)this != ReviveClient.mc.player) {
         return original;
      } else {
         return Modules.get().get(ElytraFly.class).canPacketEfly() ? true : original;
      }
   }
}
