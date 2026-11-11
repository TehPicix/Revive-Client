package revivedevelopment.reviveclient.mixin;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({EntityVelocityUpdateS2CPacket.class})
public interface EntityVelocityUpdateS2CPacketAccessor {
   @Mutable
   @Accessor("field_61887")
   void revive$setVelocity(Vec3d var1);
}
