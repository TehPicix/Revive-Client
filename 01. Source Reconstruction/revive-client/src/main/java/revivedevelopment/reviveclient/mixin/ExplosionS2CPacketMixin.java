package revivedevelopment.reviveclient.mixin;

import java.util.Optional;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.IExplosionS2CPacket;

@Mixin({ExplosionS2CPacket.class})
public abstract class ExplosionS2CPacketMixin implements IExplosionS2CPacket {
   @Shadow
   @Final
   @Mutable
   private Optional<Vec3d> comp_2884;

   @Override
   public void revive$setVelocityX(float velocity) {
      if (this.comp_2884.isPresent()) {
         Vec3d kb = this.comp_2884.get();
         this.comp_2884 = Optional.of(new Vec3d(velocity, kb.y, kb.z));
      } else {
         this.comp_2884 = Optional.of(new Vec3d(velocity, 0.0, 0.0));
      }
   }

   @Override
   public void revive$setVelocityY(float velocity) {
      if (this.comp_2884.isPresent()) {
         Vec3d kb = this.comp_2884.get();
         this.comp_2884 = Optional.of(new Vec3d(kb.x, velocity, kb.z));
      } else {
         this.comp_2884 = Optional.of(new Vec3d(0.0, velocity, 0.0));
      }
   }

   @Override
   public void revive$setVelocityZ(float velocity) {
      if (this.comp_2884.isPresent()) {
         Vec3d kb = this.comp_2884.get();
         this.comp_2884 = Optional.of(new Vec3d(kb.x, kb.y, velocity));
      } else {
         this.comp_2884 = Optional.of(new Vec3d(0.0, 0.0, velocity));
      }
   }

   @Override
   public Optional<Vec3d> revive$getKnockback() {
      return this.comp_2884;
   }
}
