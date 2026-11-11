package revivedevelopment.reviveclient.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.BlockUpdateEvent;

@Mixin({WorldChunk.class})
public abstract class WorldChunkMixin {
   @Shadow
   @Final
   World field_12858;

   @Inject(
      method = {"method_12010"},
      at = {@At("TAIL")}
   )
   private void onSetBlockState(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> info) {
      if (this.field_12858.isClient()) {
         ReviveClient.EVENT_BUS.post(BlockUpdateEvent.get(pos, (BlockState)info.getReturnValue(), state));
      }
   }
}
