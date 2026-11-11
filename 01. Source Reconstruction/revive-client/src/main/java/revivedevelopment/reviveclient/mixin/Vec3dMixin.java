package revivedevelopment.reviveclient.mixin;

import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.IVec3d;

@Mixin({Vec3d.class})
public abstract class Vec3dMixin implements IVec3d {
   @Shadow
   @Final
   @Mutable
   public double field_1352;
   @Shadow
   @Final
   @Mutable
   public double field_1351;
   @Shadow
   @Final
   @Mutable
   public double field_1350;

   @Override
   public void revive$set(double x, double y, double z) {
      this.field_1352 = x;
      this.field_1351 = y;
      this.field_1350 = z;
   }

   @Override
   public void revive$setXZ(double x, double z) {
      this.field_1352 = x;
      this.field_1350 = z;
   }

   @Override
   public void revive$setY(double y) {
      this.field_1351 = y;
   }
}
