package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LoginHelloS2CPacket.class})
public interface LoginHelloS2CPacketAccessor {
   @Accessor("field_13211")
   byte[] revive$getPublicKey();
}
