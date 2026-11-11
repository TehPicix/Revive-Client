package revivedevelopment.reviveclient.systems.managers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

public class BlockPlacementManager {
   private static final String ROTATION_ID = "block-place";
   private static final double SILENT_ROTATION_PRIORITY = 1200.0;
   private final AntiCheatConfig antiCheatConfig = AntiCheatConfig.get();
   private final Map<BlockPos, Long> placeCooldowns = new ConcurrentHashMap<>();
   private boolean locked = false;

   public BlockPlacementManager() {
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   public boolean beginPlacement(BlockPos position, BlockState state, Item item) {
      if (!ReviveClient.PACKET.canSend(PacketManager.Category.BLOCK_INTERACT)) {
         return false;
      } else if (this.locked) {
         return false;
      } else if (!this.checkPlacement(item, position, state)) {
         return false;
      } else if (!ReviveClient.SWAP.beginSwap(item, true)) {
         return false;
      } else {
         this.locked = true;
         return true;
      }
   }

   public boolean beginPlacement(List<BlockPos> positions, Item item) {
      if (!ReviveClient.PACKET.canSend(PacketManager.Category.BLOCK_INTERACT)) {
         return false;
      } else if (this.locked) {
         return false;
      } else if (positions.stream().filter(x -> this.checkPlacement(item, x)).findAny().isEmpty()) {
         return false;
      } else if (!ReviveClient.SWAP.beginSwap(item, true)) {
         return false;
      } else {
         this.locked = true;
         return true;
      }
   }

   public boolean placeBlock(Item item, BlockPos blockPos) {
      return this.placeBlock(item, blockPos, ReviveClient.mc.world.getBlockState(blockPos));
   }

   public boolean placeBlock(Item item, BlockPos blockPos, BlockState state) {
      long currentTime = System.currentTimeMillis();
      if (ReviveClient.mc.player != null) {
         double maxRange = this.antiCheatConfig.blockInteractRange.get();
         if (new Box(blockPos).squaredMagnitude(ReviveClient.mc.player.getEyePos()) > maxRange * maxRange) {
            return false;
         }
      }

      if (!this.checkPlacement(item, blockPos, state)) {
         return false;
      } else {
         Direction dir = BlockUtils.getPlaceSide(blockPos);
         Vec3d hitPos = blockPos.toCenterPos();
         Direction airPlaceDir = Direction.UP;
         BlockPos neighbour;
         if (dir == null) {
            double closestDist = Double.MAX_VALUE;

            for (int range = 1; range <= 3; range++) {
               for (Direction searchDir : Direction.values()) {
                  BlockPos candidate = blockPos.offset(searchDir, range);
                  if (!ReviveClient.mc.world.getBlockState(candidate).isAir()) {
                     double dist = candidate.getSquaredDistance(ReviveClient.mc.player.getEyePos());
                     if (dist < closestDist) {
                        closestDist = dist;
                        if (range == 1) {
                           dir = searchDir;
                        }

                        airPlaceDir = searchDir;
                     }
                  }
               }

               if (dir != null) {
                  break;
               }
            }

            if (dir != null) {
               neighbour = blockPos.offset(dir);
               hitPos = hitPos.add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
            } else {
               neighbour = blockPos;
            }
         } else {
            neighbour = blockPos.offset(dir);
            hitPos = hitPos.add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
         }

         if (this.antiCheatConfig.blockRotatePlace.get()) {
            float[] angle = ReviveClient.ROTATION.getRotation(hitPos);
            if (!ReviveClient.ROTATION.requestSilentRotation("block-place", 1200.0, angle[0], angle[1])) {
               return false;
            }
         }

         Long lastPlaceTime = this.placeCooldowns.get(blockPos);
         if (lastPlaceTime != null && currentTime - lastPlaceTime < this.antiCheatConfig.blockPlacePerBlockCooldown.get() * 1000.0) {
            return false;
         } else if (!ReviveClient.PACKET.canSend(PacketManager.Category.BLOCK_INTERACT)) {
            return false;
         } else {
            if (this.placeCooldowns.size() > 128) {
               double cooldownMs = this.antiCheatConfig.blockPlacePerBlockCooldown.get() * 1000.0;
               this.placeCooldowns.values().removeIf(t -> currentTime - t >= cooldownMs);
            }

            this.placeCooldowns.put(blockPos.toImmutable(), currentTime);
            boolean shouldAirPlace = dir == null || this.antiCheatConfig.forceAirPlace.get();
            boolean useGrimAirPlace = this.antiCheatConfig.blockPlaceAirPlace.get() && shouldAirPlace;
            boolean useMainHandAirPlace = !useGrimAirPlace && this.antiCheatConfig.mainHandAirPlace.get() && shouldAirPlace;
            if (useGrimAirPlace) {
               SilentMine silentMine = SilentMine.get();
               boolean slotSwitchUnsafe = ReviveClient.SWAP.isMultiTickSwapActive()
                  || ReviveClient.mc.player.isUsingItem() && ReviveClient.mc.player.getActiveHand() == Hand.MAIN_HAND
                  || silentMine != null && silentMine.isRunningHere() && silentMine.hasDelayedDestroy();
               int origSlot = ReviveClient.mc.player.getInventory().selectedSlot;
               FindItemResult obsidianResult = InvUtils.findInHotbar(item);
               boolean switchedSlot = false;
               if (!slotSwitchUnsafe && obsidianResult.found() && obsidianResult.isHotbar() && obsidianResult.slot() != origSlot) {
                  ReviveClient.mc.player.getInventory().selectedSlot = obsidianResult.slot();
                  ReviveClient.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(obsidianResult.slot()));
                  switchedSlot = true;
               }

               ReviveClient.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
               ReviveClient.mc
                  .getNetworkHandler()
                  .sendPacket(
                     new PlayerInteractBlockC2SPacket(
                        Hand.OFF_HAND, new BlockHitResult(hitPos, dir == null ? airPlaceDir : dir.getOpposite(), neighbour, false), getSequence()
                     )
                  );
               ReviveClient.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
               if (switchedSlot) {
                  ReviveClient.mc.player.getInventory().selectedSlot = origSlot;
                  ReviveClient.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(origSlot));
               }
            } else if (useMainHandAirPlace) {
               ReviveClient.mc
                  .getNetworkHandler()
                  .sendPacket(
                     new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND, new BlockHitResult(hitPos, dir == null ? airPlaceDir : dir.getOpposite(), neighbour, false), getSequence()
                     )
                  );
            } else {
               ReviveClient.mc
                  .getNetworkHandler()
                  .sendPacket(
                     new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND, new BlockHitResult(hitPos, dir == null ? airPlaceDir : dir.getOpposite(), neighbour, false), getSequence()
                     )
                  );
            }

            return true;
         }
      }
   }

   public boolean checkPlacement(Item item, BlockPos blockPos) {
      return this.checkPlacement(item, blockPos, ReviveClient.mc.world.getBlockState(blockPos));
   }

   public boolean checkPlacement(Item item, BlockPos blockPos, BlockState state) {
      if (ReviveClient.mc.player != null) {
         double maxRange = this.antiCheatConfig.blockInteractRange.get();
         if (new Box(blockPos).squaredMagnitude(ReviveClient.mc.player.getEyePos()) > maxRange * maxRange) {
            return false;
         }
      }

      if (this.isOnPlaceCooldown(blockPos)) {
         return false;
      } else if (!this.antiCheatConfig.blockPlaceAirPlace.get() && !this.antiCheatConfig.mainHandAirPlace.get() && getPlaceOnDirection(blockPos) == null) {
         return false;
      } else if (!state.isReplaceable()) {
         return false;
      } else {
         return !World.isValid(blockPos)
            ? false
            : ReviveClient.mc.world.canPlace(Block.getBlockFromItem(item).getDefaultState(), blockPos, ShapeContext.absent());
      }
   }

   public void endPlacement() {
      if (this.locked) {
         this.locked = false;
         ReviveClient.SWAP.endSwap(true);
      }
   }

   public void forceResetPlaceCooldown(BlockPos blockPos) {
      this.placeCooldowns.remove(blockPos);
   }

   public static boolean canPlaceOver(BlockPos pos) {
      if (pos == null || ReviveClient.mc.world == null) {
         return false;
      } else {
         return !World.isValid(pos) ? false : ReviveClient.mc.world.getBlockState(pos).isReplaceable();
      }
   }

   public boolean isOnPlaceCooldown(BlockPos pos) {
      Long last = this.placeCooldowns.get(pos);
      return last == null ? false : System.currentTimeMillis() - last < this.antiCheatConfig.blockPlacePerBlockCooldown.get() * 1000.0;
   }

   public int getRemainingBudget() {
      return ReviveClient.PACKET.remaining(PacketManager.Category.BLOCK_INTERACT);
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof BlockUpdateS2CPacket packet && !packet.getState().isAir()) {
         this.placeCooldowns.remove(packet.getPos());
      }
   }

   public static Direction getPlaceOnDirection(BlockPos pos) {
      if (pos == null) {
         return null;
      } else {
         Direction best = null;
         if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
            double cDist = -1.0;

            for (Direction dir : Direction.values()) {
               if (!ReviveClient.mc.world.getBlockState(pos.offset(dir)).isAir()) {
                  double dist = getDistanceForDir(pos, dir);
                  if (dist >= 0.0 && (cDist < 0.0 || dist < cDist)) {
                     best = dir;
                     cDist = dist;
                  }
               }
            }
         }

         return best;
      }
   }

   private static double getDistanceForDir(BlockPos pos, Direction dir) {
      if (ReviveClient.mc.player == null) {
         return 0.0;
      } else {
         Vec3d vec = new Vec3d(pos.getX() + dir.getOffsetX() / 2.0F, pos.getY() + dir.getOffsetY() / 2.0F, pos.getZ() + dir.getOffsetZ() / 2.0F);
         Vec3d dist = ReviveClient.mc.player.getEyePos().add(-vec.x, -vec.y, -vec.z);
         return dist.lengthSquared();
      }
   }

   private static int getSequence() {
      return ReviveClient.mc.world == null ? 0 : ((IClientWorld)ReviveClient.mc.world).revive$getAndIncrementSequence();
   }
}
