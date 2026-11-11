package revivedevelopment.reviveclient.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({PlayerMoveC2SPacket.class})
public interface PlayerMoveC2SPacketAccessor {
   @Mutable
   @Accessor("field_12886")
   void setY(double var1);

   @Mutable
   @Accessor("field_29179")
   void setOnGround(boolean var1);
}
