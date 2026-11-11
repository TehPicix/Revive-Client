package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.managers.BlockPlacementManager;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.SortPriority;
import revivedevelopment.reviveclient.utils.player.FindItemResult;

public class ChineseAura extends Module {
   private static final String ROTATION_ID = "chinese-aura";
   private static final double ROTATION_PRIORITY = 900.0;
   private static final double TRAPDOOR_TOP_BOTTOM_Y = 0.8125;
   private static final Set<Item> ALLOWED_FEET_ITEMS = Set.of(Items.LADDER, Items.VINE, Items.ITEM_FRAME, Items.COBWEB, Items.SCAFFOLDING);
   private static final Set<Item> TRAPDOOR_ITEMS = Set.of(
      Items.OAK_TRAPDOOR,
      Items.SPRUCE_TRAPDOOR,
      Items.BIRCH_TRAPDOOR,
      Items.JUNGLE_TRAPDOOR,
      Items.ACACIA_TRAPDOOR,
      Items.DARK_OAK_TRAPDOOR,
      Items.MANGROVE_TRAPDOOR,
      Items.CHERRY_TRAPDOOR,
      Items.PALE_OAK_TRAPDOOR,
      Items.BAMBOO_TRAPDOOR,
      Items.CRIMSON_TRAPDOOR,
      Items.WARPED_TRAPDOOR
   );
   private static final Set<Item> ALLOWED_HEAD_ITEMS_BASE = Set.of(Items.LADDER, Items.VINE, Items.COBWEB);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgItems = this.settings.createGroup("Items");
   private final Setting<Boolean> pauseEat = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-eat").description("Pauses while eating.").defaultValue(true).build());
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("The maximum distance to target players.")
            .defaultValue(5.0)
            .range(0.0, 6.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target-priority"))
                  .description("How to pick the target among players in range."))
               .defaultValue(SortPriority.ClosestAngle))
            .build()
      );
   private final Setting<Boolean> ignoreNakeds = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-nakeds").description("Ignores players with no armor.").defaultValue(true).build());
   private final Setting<Double> placeDelay = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("place-delay")
            .description("How many seconds to wait before placing on the same spot again.")
            .defaultValue(0.2)
            .min(0.0)
            .sliderMax(2.0)
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("swing")
            .description("Sends a hand swing after each face placement / trapdoor toggle, like vanilla does.")
            .defaultValue(true)
            .build()
      );
   private final Setting<List<Item>> feetItems = this.sgItems
      .add(
         new ItemListSetting.Builder()
            .name("feet-items")
            .description("Items to place on the enemy's feet. Tried in list order; the first one that can be placed is used.")
            .defaultValue(Items.COBWEB)
            .filter(ALLOWED_FEET_ITEMS::contains)
            .build()
      );
   private final Setting<List<Item>> headItems = this.sgItems
      .add(
         new ItemListSetting.Builder()
            .name("head-items")
            .description("Items to place on the enemy's head. Tried in list order; the first one that can be placed is used.")
            .filter(ChineseAura::isAllowedHeadItem)
            .build()
      );
   private PlayerEntity target = null;
   private final Map<BlockPos, Long> timeOfLastPlace = new HashMap<>();

   public ChineseAura() {
      super(Categories.Combat, "chinese-aura", "Places whatever you want on your enemies. Extremely chinese.");
   }

   @Override
   public void onActivate() {
      this.target = null;
      this.timeOfLastPlace.clear();
   }

   @Override
   public void onDeactivate() {
      this.target = null;
      this.timeOfLastPlace.clear();
   }

   @Override
   public String getInfoString() {
      return this.target != null ? this.target.getName().getString() : null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.mc.getNetworkHandler() != null) {
         long now = System.currentTimeMillis();
         long expire = (long)(this.placeDelay.get() * 1000.0) + 1000L;
         this.timeOfLastPlace.values().removeIf(t -> now - t > expire);
         if (ReviveClient.TARGET.isBadTarget(this.target, this.range.get())) {
            this.target = ReviveClient.TARGET.getPlayerTarget(this.range.get(), this.priority.get());
            if (ReviveClient.TARGET.isBadTarget(this.target, this.range.get())) {
               this.target = null;
               return;
            }
         }

         if (!this.ignoreNakeds.get() || !this.isNaked(this.target)) {
            if (!this.pauseEat.get() || !this.mc.player.isUsingItem()) {
               BlockPos lowerPos = this.target.getBlockPos();
               BlockPos upperPos = lowerPos.up();

               for (Item item : this.headItems.get()) {
                  if (isAllowedHeadItem(item) && this.placeHead(item, lowerPos, upperPos, now)) {
                     break;
                  }
               }

               for (Item itemx : this.feetItems.get()) {
                  if (ALLOWED_FEET_ITEMS.contains(itemx) && this.placeFeet(itemx, lowerPos, now)) {
                     break;
                  }
               }
            }
         }
      }
   }

   private boolean placeHead(Item item, BlockPos lowerPos, BlockPos upperPos, long now) {
      if (TRAPDOOR_ITEMS.contains(item)) {
         if (!this.cooldownOver(upperPos, now)) {
            return false;
         } else if (this.placeTrapdoor(item, upperPos)) {
            this.timeOfLastPlace.put(upperPos, now);
            return true;
         } else {
            return false;
         }
      } else if (item == Items.COBWEB) {
         if (!this.cooldownOver(upperPos, now)) {
            return false;
         } else if (this.placeViaManager(Items.COBWEB, upperPos)) {
            this.timeOfLastPlace.put(upperPos, now);
            return true;
         } else {
            return false;
         }
      } else if (!this.cooldownOver(lowerPos, now)) {
         return false;
      } else {
         boolean placed = false;
         if (item == Items.VINE || item == Items.LADDER) {
            placed = this.placeOnWall(item, lowerPos);
         }

         if (placed) {
            this.timeOfLastPlace.put(lowerPos, now);
         }

         return placed;
      }
   }

   private boolean placeFeet(Item item, BlockPos feetPos, long now) {
      if (!this.cooldownOver(feetPos, now)) {
         return false;
      } else {
         boolean placed;
         if (item == Items.ITEM_FRAME) {
            placed = this.placeItemFrame(feetPos);
         } else if (item == Items.VINE || item == Items.LADDER) {
            placed = this.placeOnWall(item, feetPos);
         } else if (item != Items.COBWEB && item != Items.SCAFFOLDING) {
            placed = false;
         } else {
            placed = this.placeViaManager(item, feetPos);
         }

         if (placed) {
            this.timeOfLastPlace.put(feetPos, now);
         }

         return placed;
      }
   }

   private boolean placeViaManager(Item item, BlockPos pos) {
      BlockPlacementManager bpm = ReviveClient.BLOCK;
      if (!BlockPlacementManager.canPlaceOver(pos)) {
         return false;
      } else if (!ReviveClient.SWAP.getSlot(item).found()) {
         return false;
      } else if (bpm.isOnPlaceCooldown(pos)) {
         return false;
      } else if (!bpm.beginPlacement(List.of(pos), item)) {
         return false;
      } else {
         boolean var4;
         try {
            var4 = bpm.placeBlock(item, pos);
         } finally {
            bpm.endPlacement();
         }

         return var4;
      }
   }

   private boolean placeOnWall(Item item, BlockPos pos) {
      if (!this.mc.world.getBlockState(pos).isAir()) {
         return false;
      } else {
         Vec3d eye = this.mc.player.getEyePos();
         BlockPos bestSupport = null;
         Direction bestFace = null;
         double bestDist = Double.MAX_VALUE;

         for (Direction dir : Direction.HORIZONTAL) {
            BlockPos supportPos = pos.offset(dir);
            if (this.mc.world.getBlockState(supportPos).isSolidBlock(this.mc.world, supportPos)) {
               double dist = new Box(supportPos).squaredMagnitude(eye);
               if (dist < bestDist) {
                  bestDist = dist;
                  bestSupport = supportPos;
                  bestFace = dir.getOpposite();
               }
            }
         }

         if (bestSupport == null) {
            return false;
         } else {
            Vec3d hitPos = bestSupport.toCenterPos().add(bestFace.getOffsetX() * 0.5, 0.0, bestFace.getOffsetZ() * 0.5);
            return this.interact(item, bestSupport, bestFace, hitPos);
         }
      }
   }

   private boolean placeItemFrame(BlockPos feetPos) {
      if (!this.mc.world.getBlockState(feetPos).isAir()) {
         return false;
      } else if (!this.isTargetInSingleCell()) {
         return false;
      } else {
         BlockPos floorPos = feetPos.down();
         if (!this.mc.world.getBlockState(floorPos).isSolidBlock(this.mc.world, floorPos)) {
            return false;
         } else {
            Box frameBox = new Box(feetPos.getX(), feetPos.getY(), feetPos.getZ(), feetPos.getX() + 1.0, feetPos.getY() + 0.2, feetPos.getZ() + 1.0);
            if (!this.mc.world.getEntitiesByClass(ItemFrameEntity.class, frameBox, e -> true).isEmpty()) {
               return false;
            } else {
               Vec3d hitPos = floorPos.toCenterPos().add(0.0, 0.5, 0.0);
               return this.interact(Items.ITEM_FRAME, floorPos, Direction.UP, hitPos);
            }
         }
      }
   }

   private boolean placeTrapdoor(Item item, BlockPos upperPos) {
      BlockPos supportPos = upperPos.up();
      BlockState upperState = this.mc.world.getBlockState(upperPos);
      if (upperState.getBlock() instanceof TrapdoorBlock) {
         if (!upperState.contains(Properties.OPEN)) {
            return false;
         } else {
            boolean open = (Boolean)upperState.get(Properties.OPEN);
            return this.isTargetCrawling() == open ? false : this.toggleTrapdoor(upperPos, open);
         }
      } else if (!upperState.isReplaceable()) {
         return false;
      } else if (!this.mc.world.getBlockState(supportPos).isSolidBlock(this.mc.world, supportPos)) {
         return false;
      } else {
         Vec3d hitPos = supportPos.toCenterPos().add(0.0, -0.5, 0.0);
         return this.interact(item, supportPos, Direction.DOWN, hitPos);
      }
   }

   private boolean toggleTrapdoor(BlockPos pos, boolean open) {
      if (this.mc.player.isSneaking()) {
         return false;
      } else {
         double y = open ? 0.5 : 0.8125;
         Vec3d hitPos = new Vec3d(pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5);
         if (!this.preInteract(pos, hitPos)) {
            return false;
         } else {
            this.sendInteract(Hand.MAIN_HAND, new BlockHitResult(hitPos, Direction.DOWN, pos, false));
            return true;
         }
      }
   }

   private boolean interact(Item item, BlockPos clickedPos, Direction face, Vec3d hitPos) {
      FindItemResult result = ReviveClient.SWAP.getSlot(item);
      if (!result.found()) {
         return false;
      } else if (!this.preInteract(clickedPos, hitPos)) {
         return false;
      } else if (!ReviveClient.SWAP.beginSwap(result, true)) {
         return false;
      } else {
         try {
            this.sendInteract(Hand.MAIN_HAND, new BlockHitResult(hitPos, face, clickedPos, false));
         } finally {
            ReviveClient.SWAP.endSwap(true);
         }

         return true;
      }
   }

   private boolean preInteract(BlockPos clickedPos, Vec3d hitPos) {
      if (!ReviveClient.PACKET.canSend(PacketManager.Category.BLOCK_INTERACT)) {
         return false;
      } else {
         AntiCheatConfig ac = AntiCheatConfig.get();
         double maxRange = ac.blockInteractRange.get();
         if (new Box(clickedPos).squaredMagnitude(this.mc.player.getEyePos()) > maxRange * maxRange) {
            return false;
         } else if (ac.blockRotatePlace.get()) {
            float[] angle = ReviveClient.ROTATION.getRotation(hitPos);
            return ReviveClient.ROTATION.requestSilentRotation("chinese-aura", 900.0, angle[0], angle[1]);
         } else {
            return true;
         }
      }
   }

   private void sendInteract(Hand hand, BlockHitResult hitResult) {
      int sequence = ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
      this.mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hitResult, sequence));
      if (this.swing.get()) {
         this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
      }
   }

   private boolean cooldownOver(BlockPos pos, long now) {
      Long last = this.timeOfLastPlace.get(pos);
      return last == null || (now - last) / 1000.0 > this.placeDelay.get();
   }

   private static boolean isAllowedHeadItem(Item item) {
      return ALLOWED_HEAD_ITEMS_BASE.contains(item) || TRAPDOOR_ITEMS.contains(item);
   }

   private boolean isNaked(PlayerEntity player) {
      return player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
         && player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
         && player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
         && player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
   }

   private boolean isTargetCrawling() {
      if (this.target == null) {
         return false;
      } else {
         Box box = this.target.getBoundingBox();
         return box.maxY - box.minY < 1.5;
      }
   }

   private boolean isTargetInSingleCell() {
      Box box = this.target.getBoundingBox();
      int minX = MathHelper.floor(box.minX + 0.01);
      int maxX = MathHelper.floor(box.maxX - 0.01);
      int minZ = MathHelper.floor(box.minZ + 0.01);
      int maxZ = MathHelper.floor(box.maxZ - 0.01);
      return minX == maxX && minZ == maxZ;
   }
}
