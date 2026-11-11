package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientPlayerEntity.class})
public interface ClientPlayerEntityAccessor {
   @Accessor("field_3923")
   void setTicksSinceLastPositionPacketSent(int var1);
}
