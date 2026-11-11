package revivedevelopment.reviveclient.events.entity.player;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import revivedevelopment.reviveclient.events.Cancellable;

public class StartBreakingBlockEvent extends Cancellable {
   private static final StartBreakingBlockEvent INSTANCE = new StartBreakingBlockEvent();
   public BlockPos blockPos;
   public Direction direction;

   public static StartBreakingBlockEvent get(BlockPos blockPos, Direction direction) {
      INSTANCE.setCancelled(false);
      INSTANCE.blockPos = blockPos;
      INSTANCE.direction = direction;
      return INSTANCE;
   }
}
