package revivedevelopment.reviveclient.commands.commands;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap.Type;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.commands.Command;
import revivedevelopment.reviveclient.utils.BaritoneBridge;

public class SurfaceCommand extends Command {
   public SurfaceCommand() {
      super("surface", "Paths to the surface with Baritone, from any depth underground or underwater.", "top");
   }

   @Override
   public void execute(String[] args) {
      if (ReviveClient.mc.player == null || ReviveClient.mc.world == null) {
         this.error("Not in a world.", new Object[0]);
      } else if (!BaritoneBridge.isPresent()) {
         this.error("Baritone is not installed - it does the actual pathing.", new Object[0]);
      } else {
         BlockPos feet = ReviveClient.mc.player.getBlockPos();
         int x = feet.getX();
         int z = feet.getZ();
         String description;
         if (ReviveClient.mc.world.getDimension().hasCeiling()) {
            BlockPos target = this.highestStandableBelow(x, z, feet.getY());
            if (target == null) {
               this.error("No open space above you in this column.", new Object[0]);
               return;
            }

            BaritoneBridge.setGoalBlockAndPath(target.getX(), target.getY(), target.getZ());
            description = "block " + target.getX() + ", " + target.getY() + ", " + target.getZ();
         } else {
            int top = ReviveClient.mc.world.getWorldChunk(feet).getHeightmap(Type.MOTION_BLOCKING).get(x & 15, z & 15);
            if (feet.getY() >= top) {
               this.info("Already at the surface.", new Object[0]);
               return;
            }

            if (!ReviveClient.mc.world.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
               BaritoneBridge.setGoalYLevelAndPath(top - 1);
               description = "y " + (top - 1);
            } else {
               BaritoneBridge.setGoalBlockAndPath(x, top, z);
               description = "block " + x + ", " + top + ", " + z;
            }
         }

         this.info("Pathing to the surface (%s).", new Object[]{description});
      }
   }

   private BlockPos highestStandableBelow(int x, int z, int aboveY) {
      int roof = ReviveClient.mc.world.getBottomY() + ReviveClient.mc.world.getDimension().logicalHeight();

      for (int y = roof - 2; y > aboveY; y--) {
         BlockPos pos = new BlockPos(x, y, z);
         BlockPos below = pos.down();
         if (ReviveClient.mc.world.getBlockState(pos).isAir()
            && ReviveClient.mc.world.getBlockState(pos.up()).isAir()
            && ReviveClient.mc.world.getBlockState(below).isSolidBlock(ReviveClient.mc.world, below)) {
            return pos;
         }
      }

      return null;
   }
}
