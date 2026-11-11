package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LoginKeyC2SPacket.class})
public interface LoginKeyC2SPacketAccessor {
   @Accessor("field_13274")
   byte[] revive$getEncryptedSecretKey();
}
