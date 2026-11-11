package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import revivedevelopment.reviveclient.mixininterface.ICamera;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.FreeLook;
import revivedevelopment.reviveclient.systems.modules.render.Freecam;

@Mixin({Camera.class})
public abstract class CameraMixin implements ICamera {
   @Shadow
   private boolean field_18719;
   @Shadow
   private float field_18721;
   @Shadow
   private float field_18722;

   @Shadow
   protected abstract void method_19325(float var1, float var2);

   @Shadow
   protected abstract void method_19327(double var1, double var3, double var5);

   @Shadow
   protected abstract void method_19324(float var1, float var2, float var3);

   @Shadow
   protected abstract float method_19318(float var1);

   @Inject(
      method = {"method_19321"},
      at = {@At("TAIL")}
   )
   private void onUpdateTail(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
      if (Modules.get().isActive(Freecam.class)) {
         this.field_18719 = true;
      }
   }

   @ModifyArgs(
      method = {"method_19321"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4184;method_19327(DDD)V"
      )
   )
   private void onUpdateSetPosArgs(Args args, @Local(argsOnly = true) float tickDelta) {
      Freecam freecam = Modules.get().get(Freecam.class);
      if (freecam.isActive()) {
         args.set(0, freecam.getX(tickDelta));
         args.set(1, freecam.getY(tickDelta));
         args.set(2, freecam.getZ(tickDelta));
      }
   }

   @ModifyArgs(
      method = {"method_19321"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4184;method_19325(FF)V"
      )
   )
   private void onUpdateSetRotationArgs(Args args, @Local(argsOnly = true) float tickDelta) {
      Freecam freecam = Modules.get().get(Freecam.class);
      if (freecam.isActive()) {
         args.set(0, (float)freecam.getYaw(tickDelta));
         args.set(1, (float)freecam.getPitch(tickDelta));
      }
   }

   @Inject(
      method = {"method_19321"},
      at = {@At("TAIL")}
   )
   private void onUpdateFreeLook(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
      if (!Modules.get().isActive(Freecam.class)) {
         FreeLook freeLook = Modules.get().get(FreeLook.class);
         if (freeLook != null && freeLook.playerMode()) {
            double x = MathHelper.lerp(tickProgress, focusedEntity.lastX, focusedEntity.getX());
            double y = MathHelper.lerp(tickProgress, focusedEntity.lastY, focusedEntity.getY())
               + MathHelper.lerp(tickProgress, this.field_18722, this.field_18721);
            double z = MathHelper.lerp(tickProgress, focusedEntity.lastZ, focusedEntity.getZ());
            this.method_19327(x, y, z);
            this.method_19325(freeLook.cameraYaw, freeLook.cameraPitch);
            float baseDist = 4.0F;
            if (focusedEntity instanceof LivingEntity living) {
               baseDist *= (float)living.getAttributeValue(EntityAttributes.SCALE);
            }

            float totalDist = Math.max(0.0F, baseDist + freeLook.extraDistance);
            this.method_19324(-(freeLook.noClip.get() ? totalDist : this.method_19318(totalDist)), 0.0F, 0.0F);
         }
      }
   }

   @Override
   public void revive$setRot(double yaw, double pitch) {
      this.method_19325((float)yaw, (float)MathHelper.clamp(pitch, -90.0, 90.0));
   }
}
