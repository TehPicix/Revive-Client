package revivedevelopment.reviveclient.mixin;

import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.IBox;

@Mixin({Box.class})
public abstract class BoxMixin implements IBox {
   @Shadow
   @Final
   @Mutable
   public double field_1323;
   @Shadow
   @Final
   @Mutable
   public double field_1322;
   @Shadow
   @Final
   @Mutable
   public double field_1321;
   @Shadow
   @Final
   @Mutable
   public double field_1320;
   @Shadow
   @Final
   @Mutable
   public double field_1325;
   @Shadow
   @Final
   @Mutable
   public double field_1324;

   @Override
   public void revive$expand(double v) {
      this.field_1323 -= v;
      this.field_1322 -= v;
      this.field_1321 -= v;
      this.field_1320 += v;
      this.field_1325 += v;
      this.field_1324 += v;
   }

   @Override
   public void revive$set(double x1, double y1, double z1, double x2, double y2, double z2) {
      this.field_1323 = Math.min(x1, x2);
      this.field_1322 = Math.min(y1, y2);
      this.field_1321 = Math.min(z1, z2);
      this.field_1320 = Math.max(x1, x2);
      this.field_1325 = Math.max(y1, y2);
      this.field_1324 = Math.max(z1, z2);
   }
}
