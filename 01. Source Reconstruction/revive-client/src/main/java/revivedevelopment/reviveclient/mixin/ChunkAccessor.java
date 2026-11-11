package revivedevelopment.reviveclient.mixin;

import java.util.Map;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Chunk.class})
public interface ChunkAccessor {
   @Accessor("field_34543")
   Map<BlockPos, BlockEntity> getBlockEntities();
}
