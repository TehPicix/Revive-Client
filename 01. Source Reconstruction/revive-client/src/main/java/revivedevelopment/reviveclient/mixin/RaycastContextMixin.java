package revivedevelopment.reviveclient.mixin;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.IRaycastContext;

@Mixin({RaycastContext.class})
public abstract class RaycastContextMixin implements IRaycastContext {
   @Shadow
   @Final
   @Mutable
   private Vec3d field_17553;
   @Shadow
   @Final
   @Mutable
   private Vec3d field_17554;
   @Shadow
   @Final
   @Mutable
   private ShapeType field_17555;
   @Shadow
   @Final
   @Mutable
   private FluidHandling field_17556;
   @Shadow
   @Final
   @Mutable
   private ShapeContext field_17557;

   @Override
   public void revive$set(Vec3d start, Vec3d end, ShapeType shapeType, FluidHandling fluidHandling, Entity entity) {
      this.field_17553 = start;
      this.field_17554 = end;
      this.field_17555 = shapeType;
      this.field_17556 = fluidHandling;
      this.field_17557 = ShapeContext.of(entity);
   }
}
