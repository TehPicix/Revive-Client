package revivedevelopment.reviveclient.utils.world;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.AirBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IPlayerInventory;
import revivedevelopment.reviveclient.utils.PreInit;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.Rotations;

public class BlockUtils {
   public static boolean breaking;
   private static boolean breakingThisTick;
   private static final ThreadLocal<Mutable> EXPOSED_POS = ThreadLocal.withInitial(Mutable::new);

   private BlockUtils() {
   }

   @PreInit
   public static void init() {
      ReviveClient.EVENT_BUS.subscribe(BlockUtils.class);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, int rotationPriority) {
      return place(blockPos, findItemResult, rotationPriority, true);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, boolean rotate, int rotationPriority) {
      return place(blockPos, findItemResult, rotate, rotationPriority, true);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, boolean rotate, int rotationPriority, boolean checkEntities) {
      return place(blockPos, findItemResult, rotate, rotationPriority, true, checkEntities);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, int rotationPriority, boolean checkEntities) {
      return place(blockPos, findItemResult, true, rotationPriority, true, checkEntities);
   }

   public static boolean place(BlockPos blockPos, FindItemResult findItemResult, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities) {
      return place(blockPos, findItemResult, rotate, rotationPriority, swingHand, checkEntities, true);
   }

   public static boolean place(
      BlockPos blockPos, FindItemResult findItemResult, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities, boolean swapBack
   ) {
      if (findItemResult.isOffhand()) {
         return place(blockPos, Hand.OFF_HAND, ReviveClient.mc.player.getInventory().selectedSlot, rotate, rotationPriority, swingHand, checkEntities, swapBack);
      } else {
         return findItemResult.isHotbar()
            ? place(blockPos, Hand.MAIN_HAND, findItemResult.slot(), rotate, rotationPriority, swingHand, checkEntities, swapBack)
            : false;
      }
   }

   public static boolean place(
      BlockPos blockPos, Hand hand, int slot, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities, boolean swapBack
   ) {
      return place(blockPos, hand, slot, null, rotate, rotationPriority, swingHand, checkEntities, swapBack);
   }

   public static boolean place(
      BlockPos blockPos,
      FindItemResult findItemResult,
      Direction side,
      boolean rotate,
      int rotationPriority,
      boolean swingHand,
      boolean checkEntities,
      boolean swapBack
   ) {
      if (findItemResult.isOffhand()) {
         return place(
            blockPos, Hand.OFF_HAND, ReviveClient.mc.player.getInventory().selectedSlot, side, rotate, rotationPriority, swingHand, checkEntities, swapBack
         );
      } else {
         return findItemResult.isHotbar()
            ? place(blockPos, Hand.MAIN_HAND, findItemResult.slot(), side, rotate, rotationPriority, swingHand, checkEntities, swapBack)
            : false;
      }
   }

   public static boolean place(
      BlockPos blockPos, Hand hand, int slot, Direction side, boolean rotate, int rotationPriority, boolean swingHand, boolean checkEntities, boolean swapBack
   ) {
      if (slot >= 0 && slot <= 8) {
         Block toPlace = Blocks.OBSIDIAN;
         ItemStack i = hand == Hand.MAIN_HAND ? ReviveClient.mc.player.getInventory().getStack(slot) : ReviveClient.mc.player.getInventory().getStack(45);
         if (i.getItem() instanceof BlockItem blockItem) {
            toPlace = blockItem.getBlock();
         }

         if (!canPlaceBlock(blockPos, checkEntities, toPlace)) {
            return false;
         } else {
            Vec3d hitPos = Vec3d.ofCenter(blockPos);
            if (side == null) {
               side = getPlaceSide(blockPos);
            }

            BlockPos neighbour;
            if (side == null) {
               side = Direction.UP;
               neighbour = blockPos;
            } else {
               neighbour = blockPos.offset(side);
               hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            }

            BlockHitResult bhr = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
            if (rotate) {
               Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), rotationPriority, () -> {
                  InvUtils.swap(slot, swapBack);
                  interact(bhr, hand, swingHand);
                  if (swapBack) {
                     InvUtils.swapBack();
                  }
               });
            } else {
               InvUtils.swap(slot, swapBack);
               interact(bhr, hand, swingHand);
               if (swapBack) {
                  InvUtils.swapBack();
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static void interact(BlockHitResult blockHitResult, Hand hand, boolean swing) {
      boolean wasSneaking = ReviveClient.mc.player.isSneaking();
      ReviveClient.mc.player.setSneaking(false);
      ActionResult result = ReviveClient.mc.interactionManager.interactBlock(ReviveClient.mc.player, hand, blockHitResult);
      if (result.isAccepted()) {
         if (swing) {
            ReviveClient.mc.player.swingHand(hand);
         } else {
            ReviveClient.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
         }
      }

      ReviveClient.mc.player.setSneaking(wasSneaking);
   }

   public static boolean canPlaceBlock(BlockPos blockPos, boolean checkEntities, Block block) {
      if (blockPos == null) {
         return false;
      } else if (!World.isValid(blockPos)) {
         return false;
      } else {
         return !ReviveClient.mc.world.getBlockState(blockPos).isReplaceable()
            ? false
            : !checkEntities || ReviveClient.mc.world.canPlace(block.getDefaultState(), blockPos, ShapeContext.absent());
      }
   }

   public static boolean canPlace(BlockPos blockPos, boolean checkEntities) {
      return canPlaceBlock(blockPos, checkEntities, Blocks.OBSIDIAN);
   }

   public static boolean canPlace(BlockPos blockPos) {
      return canPlace(blockPos, true);
   }

   public static Direction getPlaceSide(BlockPos blockPos) {
      Vec3d lookVec = blockPos.toCenterPos().subtract(ReviveClient.mc.player.getEyePos());
      double bestRelevancy = -Double.MAX_VALUE;
      Direction bestSide = null;

      for (Direction side : Direction.values()) {
         BlockPos neighbor = blockPos.offset(side);
         BlockState state = ReviveClient.mc.world.getBlockState(neighbor);
         if (!state.isReplaceable() && !isClickable(state.getBlock()) && state.getFluidState().isEmpty()) {
            double relevancy = side.getAxis().choose(lookVec.getX(), lookVec.getY(), lookVec.getZ()) * side.getDirection().offset();
            if (relevancy > bestRelevancy) {
               bestRelevancy = relevancy;
               bestSide = side;
            }
         }
      }

      return bestSide;
   }

   public static Direction getClosestPlaceSide(BlockPos blockPos) {
      return getClosestPlaceSide(blockPos, ReviveClient.mc.player.getEyePos());
   }

   public static Direction getClosestPlaceSide(BlockPos blockPos, Vec3d pos) {
      Direction closestSide = null;
      double closestDistance = Double.MAX_VALUE;

      for (Direction side : Direction.values()) {
         BlockPos neighbor = blockPos.offset(side);
         BlockState state = ReviveClient.mc.world.getBlockState(neighbor);
         if (!state.isReplaceable() && !isClickable(state.getBlock()) && state.getFluidState().isEmpty()) {
            double distance = pos.squaredDistanceTo(neighbor.getX(), neighbor.getY(), neighbor.getZ());
            if (distance < closestDistance) {
               closestDistance = distance;
               closestSide = side;
            }
         }
      }

      return closestSide;
   }

   @EventHandler(
      priority = 300
   )
   private static void onTickPre(TickEvent.Pre event) {
      breakingThisTick = false;
   }

   @EventHandler(
      priority = -300
   )
   private static void onTickPost(TickEvent.Post event) {
      if (!breakingThisTick && breaking) {
         breaking = false;
         if (ReviveClient.mc.interactionManager != null) {
            ReviveClient.mc.interactionManager.cancelBlockBreaking();
         }
      }
   }

   public static boolean breakBlock(BlockPos blockPos, boolean swing) {
      if (!canBreak(blockPos, ReviveClient.mc.world.getBlockState(blockPos))) {
         return false;
      } else {
         BlockPos pos = blockPos instanceof Mutable ? new BlockPos(blockPos) : blockPos;
         if (ReviveClient.mc.interactionManager.isBreakingBlock()) {
            ReviveClient.mc.interactionManager.updateBlockBreakingProgress(pos, getDirection(blockPos));
         } else {
            ReviveClient.mc.interactionManager.attackBlock(pos, getDirection(blockPos));
         }

         if (swing) {
            ReviveClient.mc.player.swingHand(Hand.MAIN_HAND);
         } else {
            ReviveClient.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         }

         breaking = true;
         breakingThisTick = true;
         return true;
      }
   }

   public static boolean canBreak(BlockPos blockPos, BlockState state) {
      return !ReviveClient.mc.player.isCreative() && state.getHardness(ReviveClient.mc.world, blockPos) < 0.0F
         ? false
         : state.getOutlineShape(ReviveClient.mc.world, blockPos) != VoxelShapes.empty();
   }

   public static boolean canBreak(BlockPos blockPos) {
      return canBreak(blockPos, ReviveClient.mc.world.getBlockState(blockPos));
   }

   public static boolean canInstaBreak(BlockPos blockPos, float breakSpeed) {
      return ReviveClient.mc.player.isCreative() || calcBlockBreakingDelta2(blockPos, breakSpeed) >= 1.0F;
   }

   public static boolean canInstaBreak(BlockPos blockPos) {
      BlockState state = ReviveClient.mc.world.getBlockState(blockPos);
      return canInstaBreak(blockPos, ReviveClient.mc.player.getBlockBreakingSpeed(state));
   }

   public static float calcBlockBreakingDelta2(BlockPos blockPos, float breakSpeed) {
      BlockState state = ReviveClient.mc.world.getBlockState(blockPos);
      float f = state.getHardness(ReviveClient.mc.world, blockPos);
      if (f == -1.0F) {
         return 0.0F;
      } else {
         int i = ReviveClient.mc.player.canHarvest(state) ? 30 : 100;
         return breakSpeed / f / i;
      }
   }

   public static boolean isClickable(Block block) {
      return block instanceof CraftingTableBlock
         || block instanceof AnvilBlock
         || block instanceof LoomBlock
         || block instanceof CartographyTableBlock
         || block instanceof GrindstoneBlock
         || block instanceof StonecutterBlock
         || block instanceof ButtonBlock
         || block instanceof AbstractPressurePlateBlock
         || block instanceof BlockWithEntity
         || block instanceof BedBlock
         || block instanceof FenceGateBlock
         || block instanceof DoorBlock
         || block instanceof NoteBlock
         || block instanceof TrapdoorBlock;
   }

   public static BlockUtils.MobSpawn isValidMobSpawn(BlockPos blockPos, boolean newMobSpawnLightLevel) {
      return isValidMobSpawn(blockPos, ReviveClient.mc.world.getBlockState(blockPos), newMobSpawnLightLevel ? 0 : 7);
   }

   public static BlockUtils.MobSpawn isValidMobSpawn(BlockPos blockPos, BlockState blockState, int spawnLightLimit) {
      if (!(blockState.getBlock() instanceof AirBlock)) {
         return BlockUtils.MobSpawn.Never;
      } else {
         BlockPos down = blockPos.down();
         BlockState downState = ReviveClient.mc.world.getBlockState(down);
         if (downState.getBlock() == Blocks.BEDROCK) {
            return BlockUtils.MobSpawn.Never;
         } else {
            if (!topSurface(downState)) {
               if (downState.getCollisionShape(ReviveClient.mc.world, down) != VoxelShapes.fullCube()) {
                  return BlockUtils.MobSpawn.Never;
               }

               if (downState.isTransparent()) {
                  return BlockUtils.MobSpawn.Never;
               }
            }

            if (ReviveClient.mc.world.getLightLevel(LightType.BLOCK, blockPos) > spawnLightLimit) {
               return BlockUtils.MobSpawn.Never;
            } else {
               return ReviveClient.mc.world.getLightLevel(LightType.SKY, blockPos) > spawnLightLimit
                  ? BlockUtils.MobSpawn.Potential
                  : BlockUtils.MobSpawn.Always;
            }
         }
      }
   }

   public static boolean topSurface(BlockState blockState) {
      return blockState.getBlock() instanceof SlabBlock && blockState.get(SlabBlock.TYPE) == SlabType.TOP
         ? true
         : blockState.getBlock() instanceof StairsBlock && blockState.get(StairsBlock.HALF) == BlockHalf.TOP;
   }

   public static Direction getDirection(BlockPos pos) {
      Vec3d eyesPos = new Vec3d(
         ReviveClient.mc.player.getX(),
         ReviveClient.mc.player.getY() + ReviveClient.mc.player.getEyeHeight(ReviveClient.mc.player.getPose()),
         ReviveClient.mc.player.getZ()
      );
      if (pos.getY() > eyesPos.y) {
         return ReviveClient.mc.world.getBlockState(pos.add(0, -1, 0)).isReplaceable()
            ? Direction.DOWN
            : ReviveClient.mc.player.getHorizontalFacing().getOpposite();
      } else {
         return !ReviveClient.mc.world.getBlockState(pos.add(0, 1, 0)).isReplaceable()
            ? ReviveClient.mc.player.getHorizontalFacing().getOpposite()
            : Direction.UP;
      }
   }

   public static boolean isExposed(BlockPos blockPos) {
      for (Direction direction : Direction.values()) {
         if (!ReviveClient.mc.world.getBlockState(EXPOSED_POS.get().set(blockPos, direction)).isOpaque()) {
            return true;
         }
      }

      return false;
   }

   public static double getBreakDelta(int slot, BlockState state) {
      float hardness = state.getHardness(null, null);
      if (hardness == -1.0F) {
         return 0.0;
      } else {
         ItemStack stack = (ItemStack)((IPlayerInventory)ReviveClient.mc.player.getInventory()).revive$getMain().get(slot);
         return getBlockBreakingSpeed(slot, state, ReviveClient.mc.player.isOnGround())
            / hardness
            / (state.isToolRequired() && !(stack.getMiningSpeedMultiplier(state) > 1.0F) ? 100 : 30);
      }
   }

   public static double getBreakDeltaPerTick(int slot, BlockState state, boolean isOnGround, boolean submerged) {
      if (state != null && !state.isAir()) {
         float hardness = state.getHardness(null, null);
         if (hardness < 0.0F) {
            return 0.0;
         } else if (hardness == 0.0F) {
            return 1.0;
         } else {
            boolean canHarvest = !state.isToolRequired() || ReviveClient.mc.player.getInventory().getStack(slot).isSuitableFor(state);
            return getBlockBreakingSpeed(slot, state, isOnGround, submerged) / hardness / (canHarvest ? 30 : 100);
         }
      } else {
         return 0.0;
      }
   }

   public static double getBreakDelta(double breakingSpeed, BlockState state) {
      float hardness = state.getHardness(null, null);
      return hardness == -1.0F ? 0.0 : breakingSpeed / hardness / 30.0;
   }

   public static double getBlockBreakingSpeed(int slot, BlockState block, boolean isOnGround) {
      return getBlockBreakingSpeed(slot, block, isOnGround, ReviveClient.mc.player.isSubmergedIn(FluidTags.WATER));
   }

   public static double getBlockBreakingSpeed(int slot, BlockState block, boolean isOnGround, boolean submerged) {
      double speed = ((ItemStack)((IPlayerInventory)ReviveClient.mc.player.getInventory()).revive$getMain().get(slot)).getMiningSpeedMultiplier(block);
      if (speed > 1.0) {
         ItemStack tool = ReviveClient.mc.player.getInventory().getStack(slot);
         int efficiency = Utils.getEnchantmentLevel(tool, Enchantments.EFFICIENCY);
         if (efficiency > 0 && !tool.isEmpty()) {
            speed += efficiency * efficiency + 1;
         }
      }

      if (StatusEffectUtil.hasHaste(ReviveClient.mc.player)) {
         speed *= 1.0F + (StatusEffectUtil.getHasteAmplifier(ReviveClient.mc.player) + 1) * 0.2F;
      }

      if (ReviveClient.mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
         float k = switch (ReviveClient.mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
         speed *= k;
      }

      if (submerged) {
         speed *= submergedMiningSpeed();
      }

      if (!isOnGround) {
         speed /= 5.0;
      }

      return speed;
   }

   public static double submergedMiningSpeed() {
      return ReviveClient.mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
   }

   public static Iterable<BlockPos> iterate(Box box) {
      return BlockPos.iterate(
         MathHelper.floor(box.minX),
         MathHelper.floor(box.minY),
         MathHelper.floor(box.minZ),
         MathHelper.floor(box.maxX),
         MathHelper.floor(box.maxY),
         MathHelper.floor(box.maxZ)
      );
   }

   public static enum MobSpawn {
      Never,
      Potential,
      Always;
   }
}
