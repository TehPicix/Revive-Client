package revivedevelopment.reviveclient.events.entity.player;

import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import revivedevelopment.reviveclient.events.Cancellable;

public class InteractBlockEvent extends Cancellable {
   private static final InteractBlockEvent INSTANCE = new InteractBlockEvent();
   public Hand hand;
   public BlockHitResult result;

   public static InteractBlockEvent get(Hand hand, BlockHitResult result) {
      INSTANCE.setCancelled(false);
      INSTANCE.hand = hand;
      INSTANCE.result = result;
      return INSTANCE;
   }
}
