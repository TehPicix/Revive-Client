package revivedevelopment.reviveclient.mixin;

import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.InteractTypeHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({PlayerInteractEntityC2SPacket.class})
public interface PlayerInteractEntityC2SPacketAccessor {
   @Accessor("field_12870")
   int revive$getEntityId();

   @Accessor("field_12871")
   InteractTypeHandler revive$getType();
}
