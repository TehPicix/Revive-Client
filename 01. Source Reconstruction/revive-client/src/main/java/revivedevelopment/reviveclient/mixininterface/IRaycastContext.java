package revivedevelopment.reviveclient.mixininterface;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public interface IRaycastContext {
   void revive$set(Vec3d var1, Vec3d var2, ShapeType var3, FluidHandling var4, Entity var5);
}
