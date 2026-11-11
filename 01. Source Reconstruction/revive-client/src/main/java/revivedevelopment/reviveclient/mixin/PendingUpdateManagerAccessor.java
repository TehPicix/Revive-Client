package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.network.PendingUpdateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({PendingUpdateManager.class})
public interface PendingUpdateManagerAccessor {
   @Accessor("field_37954")
   int getSequence();

   @Invoker("method_41937")
   PendingUpdateManager invokeIncrementSequence();
}
