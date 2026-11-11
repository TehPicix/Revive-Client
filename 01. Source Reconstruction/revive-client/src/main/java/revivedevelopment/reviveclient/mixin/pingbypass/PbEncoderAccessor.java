package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.state.NetworkState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({EncoderHandler.class})
public interface PbEncoderAccessor {
   @Accessor("field_48537")
   NetworkState<?> revive$pbEncoderState();
}
