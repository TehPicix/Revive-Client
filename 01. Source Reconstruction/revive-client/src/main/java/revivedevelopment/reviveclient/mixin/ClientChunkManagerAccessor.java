package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientChunkManager.ClientChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientChunkManager.class})
public interface ClientChunkManagerAccessor {
   @Accessor("field_16246")
   ClientChunkMap getChunks();
}
