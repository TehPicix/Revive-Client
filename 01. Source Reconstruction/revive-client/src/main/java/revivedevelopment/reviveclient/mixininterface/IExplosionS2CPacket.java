package revivedevelopment.reviveclient.mixininterface;

import java.util.Optional;
import net.minecraft.util.math.Vec3d;

public interface IExplosionS2CPacket {
   void revive$setVelocityX(float var1);

   void revive$setVelocityY(float var1);

   void revive$setVelocityZ(float var1);

   Optional<Vec3d> revive$getKnockback();
}
