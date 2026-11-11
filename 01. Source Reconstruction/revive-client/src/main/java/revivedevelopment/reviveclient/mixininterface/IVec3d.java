package revivedevelopment.reviveclient.mixininterface;

import net.minecraft.util.math.Vec3i;
import org.joml.Vector3d;

public interface IVec3d {
   void revive$set(double var1, double var3, double var5);

   default void revive$set(Vec3i vec) {
      this.revive$set(vec.getX(), vec.getY(), vec.getZ());
   }

   default void revive$set(Vector3d vec) {
      this.revive$set(vec.x, vec.y, vec.z);
   }

   void revive$setXZ(double var1, double var3);

   void revive$setY(double var1);
}
