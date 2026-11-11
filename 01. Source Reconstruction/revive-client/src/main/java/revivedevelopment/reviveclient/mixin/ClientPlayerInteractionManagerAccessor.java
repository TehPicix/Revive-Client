package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ClientPlayerInteractionManager.class})
public interface ClientPlayerInteractionManagerAccessor {
   @Accessor("field_3715")
   float getBreakingProgress();

   @Accessor("field_3715")
   void setCurrentBreakingProgress(float var1);

   @Accessor("field_3714")
   BlockPos getCurrentBreakingBlockPos();

   @Invoker("method_41931")
   void invokeSendSequencedPacket(ClientWorld var1, SequencedPacketCreator var2);
}
