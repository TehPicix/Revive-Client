package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.world.GameMode;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.BlockPlacementManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

public class PistonCrystal extends Module {
   private static final String ROTATION_ID = "piston-crystal";
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> targetRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("Maximum range to search for targets.")
            .defaultValue(10.0)
            .min(1.0)
            .sliderMax(15.0)
            .build()
      );
   private final Setting<Double> placeRange = this.sgGeneral
      .add(new DoubleSetting.Builder().name("place-range").description("Maximum block placement range.").defaultValue(4.5).min(1.0).sliderMax(6.0).build());
   private final Setting<Double> minDamage = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-damage")
            .description("Minimum crystal damage to the target required.")
            .defaultValue(6.0)
            .min(0.0)
            .sliderMax(36.0)
            .build()
      );
   private final Setting<Double> maxSelfDamage = this.sgGeneral
      .add(new DoubleSetting.Builder().name("max-self-damage").description("Maximum self damage allowed.").defaultValue(8.0).min(0.0).sliderMax(36.0).build());
   private final Setting<Double> placeDelay = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("place-delay")
            .description("Delay between setup attempts (seconds).")
            .defaultValue(0.5)
            .min(0.0)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<Double> breakDelay = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("break-delay")
            .description("Delay before attacking the pushed crystal (seconds).")
            .defaultValue(0.1)
            .min(0.0)
            .sliderMax(2.0)
            .build()
      );
   private final Setting<Boolean> antiSuicide = this.sgGeneral
      .add(new BoolSetting.Builder().name("anti-suicide").description("Don't place if the crystal would kill you.").defaultValue(true).build());
   private final Setting<Boolean> pauseEat = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-eat").description("Pause while eating / using items.").defaultValue(true).build());
   private final Setting<Boolean> swing = this.sgGeneral
      .add(new BoolSetting.Builder().name("swing").description("Show the swing animation when placing / attacking.").defaultValue(true).build());
   private final Setting<PistonCrystal.PistonType> pistonType = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("piston-type"))
                  .description("Which piston type to use."))
               .defaultValue(PistonCrystal.PistonType.Both))
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(new BoolSetting.Builder().name("debug").description("Log the state machine (phase changes, why a cycle aborted).").defaultValue(false).build());
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Render the piston / crystal / redstone placement indicators.").defaultValue(true).build());
   private PlayerEntity target;
   private PistonCrystal.Setup activeSetup;
   private PistonCrystal.Phase phase = PistonCrystal.Phase.IDLE;
   private long lastPlaceMs;
   private long phaseStartMs;
   private EndCrystalEntity trackedCrystal;
   private String lastDebug;

   public PistonCrystal() {
      super(Categories.Combat, "piston-crystal", "Pushes end crystals into players using pistons.");
   }

   public static PistonCrystal get() {
      return Modules.get().get(PistonCrystal.class);
   }

   @Override
   public void onActivate() {
      this.reset();
   }

   @Override
   public void onDeactivate() {
      this.reset();
   }

   private void reset() {
      this.target = null;
      this.activeSetup = null;
      this.phase = PistonCrystal.Phase.IDLE;
      this.phaseStartMs = 0L;
      this.trackedCrystal = null;
      ReviveClient.ROTATION.release("piston-crystal");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.world != null && this.mc.player != null) {
         if (!this.pauseEat.get() || !this.mc.player.isUsingItem()) {
            switch (this.phase) {
               case IDLE:
                  this.tickIdle();
                  break;
               case PLACE_PISTON:
                  this.tickPlacePiston();
                  break;
               case PLACE_CRYSTAL:
                  this.tickPlaceCrystal();
                  break;
               case PLACE_REDSTONE:
                  this.tickPlaceRedstone();
                  break;
               case WAIT_PUSH:
                  this.tickWaitPush();
                  break;
               case BREAK_CRYSTAL:
                  this.tickBreakCrystal();
            }
         }
      }
   }

   private void tickIdle() {
      if (this.placeCooldownReady()) {
         this.target = this.findTarget();
         if (this.target != null) {
            PistonCrystal.Setup setup = this.findBestSetup(this.target);
            if (setup != null) {
               this.activeSetup = setup;
               this.toPhase(PistonCrystal.Phase.PLACE_PISTON);
            }
         }
      }
   }

   private void tickPlacePiston() {
      if (this.target != null && this.activeSetup != null && !this.isSetupInvalid()) {
         BlockState current = this.mc.world.getBlockState(this.activeSetup.pistonPos);
         if (this.isPiston(current)) {
            ReviveClient.ROTATION.release("piston-crystal");
            this.toPhase(PistonCrystal.Phase.PLACE_CRYSTAL);
         } else if (!BlockPlacementManager.canPlaceOver(this.activeSetup.pistonPos)) {
            this.reset();
         } else {
            FindItemResult normal = InvUtils.findInHotbar(Items.PISTON);
            FindItemResult sticky = InvUtils.findInHotbar(Items.STICKY_PISTON);
            boolean useNormal = this.pistonType.get() != PistonCrystal.PistonType.Sticky && normal.found();
            boolean useSticky = this.pistonType.get() != PistonCrystal.PistonType.Normal && sticky.found();
            if (!useNormal && !useSticky) {
               this.debug("no piston in hotbar");
               this.reset();
            } else {
               Item item = useNormal ? Items.PISTON : Items.STICKY_PISTON;
               if (this.placePiston(this.activeSetup.pistonPos, item, this.activeSetup.pistonFacing)) {
                  ReviveClient.ROTATION.release("piston-crystal");
                  this.toPhase(PistonCrystal.Phase.PLACE_CRYSTAL);
               } else {
                  this.reset();
               }
            }
         }
      } else {
         this.reset();
      }
   }

   private void tickPlaceCrystal() {
      if (this.target == null || this.activeSetup == null || this.isSetupInvalid()) {
         this.reset();
      } else if (!this.isCrystalBaseBlock(this.mc.world.getBlockState(this.activeSetup.crystalBase))) {
         this.reset();
      } else {
         BlockPos crystalPos = this.activeSetup.crystalPos;
         if (this.mc.world.getBlockState(crystalPos).isAir() && this.mc.world.getBlockState(crystalPos.up()).isAir()) {
            Box crystalBox = new Box(
               crystalPos.getX(), crystalPos.getY(), crystalPos.getZ(), crystalPos.getX() + 1, crystalPos.getY() + 2, crystalPos.getZ() + 1
            );
            if (!this.mc.world.getEntitiesByClass(EndCrystalEntity.class, crystalBox, e -> true).isEmpty()) {
               this.toPhase(PistonCrystal.Phase.PLACE_REDSTONE);
            } else {
               FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
               if (!crystal.found()) {
                  this.debug("no end crystal in hotbar");
                  this.reset();
               } else {
                  if (this.placeCrystalOnBase(this.activeSetup.crystalBase, crystal.slot())) {
                     this.toPhase(PistonCrystal.Phase.PLACE_REDSTONE);
                  } else {
                     this.reset();
                  }
               }
            }
         } else {
            this.reset();
         }
      }
   }

   private void tickPlaceRedstone() {
      if (this.target != null && this.activeSetup != null) {
         BlockState pistonState = this.mc.world.getBlockState(this.activeSetup.pistonPos);
         if (!this.isPiston(pistonState)) {
            this.reset();
         } else if (pistonState.contains(Properties.EXTENDED) && (Boolean)pistonState.get(Properties.EXTENDED)) {
            this.toPhase(PistonCrystal.Phase.WAIT_PUSH);
         } else if (this.isRedstoneSource(this.mc.world.getBlockState(this.activeSetup.redstonePos))) {
            this.toPhase(PistonCrystal.Phase.WAIT_PUSH);
         } else if (!BlockPlacementManager.canPlaceOver(this.activeSetup.redstonePos)) {
            this.reset();
         } else {
            FindItemResult torch = InvUtils.findInHotbar(Items.REDSTONE_TORCH);
            FindItemResult block = InvUtils.findInHotbar(Items.REDSTONE_BLOCK);
            if (!torch.found() && !block.found()) {
               this.debug("no redstone source in hotbar");
               this.reset();
            } else {
               Item item = torch.found() ? Items.REDSTONE_TORCH : Items.REDSTONE_BLOCK;
               if (this.placeBlockManaged(this.activeSetup.redstonePos, item)) {
                  this.toPhase(PistonCrystal.Phase.WAIT_PUSH);
               } else {
                  this.reset();
               }
            }
         }
      } else {
         this.reset();
      }
   }

   private void tickWaitPush() {
      if (this.activeSetup == null) {
         this.reset();
      } else {
         BlockPos pushedPos = this.activeSetup.crystalPos.offset(this.activeSetup.pistonFacing);
         Box searchBox = new Box(pushedPos).expand(1.0);
         List<EndCrystalEntity> pushed = this.mc.world.getEntitiesByClass(EndCrystalEntity.class, searchBox, e -> true);
         if (!pushed.isEmpty()) {
            this.trackedCrystal = pushed.get(0);
            this.toPhase(PistonCrystal.Phase.BREAK_CRYSTAL);
         } else {
            Box origBox = new Box(this.activeSetup.crystalPos).expand(0.5);
            if (!this.mc.world.getEntitiesByClass(EndCrystalEntity.class, origBox, e -> true).isEmpty()) {
               if (System.currentTimeMillis() - this.phaseStartMs > 2000L) {
                  this.reset();
               }
            } else {
               if (System.currentTimeMillis() - this.phaseStartMs > 1000L) {
                  this.reset();
               }
            }
         }
      }
   }

   private void tickBreakCrystal() {
      if (this.trackedCrystal != null && !this.trackedCrystal.isRemoved()) {
         if (System.currentTimeMillis() - this.phaseStartMs >= (long)(this.breakDelay.get() * 1000.0)) {
            this.attackCrystal(this.trackedCrystal);
            this.lastPlaceMs = System.currentTimeMillis();
            this.reset();
         }
      } else {
         this.lastPlaceMs = System.currentTimeMillis();
         this.reset();
      }
   }

   private PistonCrystal.Setup findBestSetup(PlayerEntity target) {
      Vec3d eyes = this.mc.player.getEyePos();
      BlockPos targetFeet = target.getBlockPos();
      PistonCrystal.Setup best = null;
      double bestDamage = 0.0;

      for (Direction pushDir : Type.HORIZONTAL) {
         for (BlockPos crystalBase : this.getCandidateCrystalBases(target, targetFeet, pushDir)) {
            BlockPos crystalPos = crystalBase.up();
            BlockPos pistonPos = crystalPos.offset(pushDir.getOpposite());
            BlockPos pushedCrystalPos = crystalPos.offset(pushDir);
            Vec3d explosionPos = Vec3d.ofBottomCenter(pushedCrystalPos).add(0.0, 1.0, 0.0);
            if (!this.outOfPlaceRange(eyes, pistonPos) && !this.outOfPlaceRange(eyes, crystalBase)) {
               BlockPos redstonePos = this.findRedstonePos(pistonPos, pushDir);
               if (redstonePos != null
                  && !this.outOfPlaceRange(eyes, redstonePos)
                  && this.canUsePistonPos(pistonPos)
                  && this.canUseCrystalBase(crystalBase, crystalPos)
                  && this.canUseRedstonePos(redstonePos)
                  && this.mc.world.getBlockState(pushedCrystalPos).isAir()
                  && this.mc.world.getBlockState(pushedCrystalPos.up()).isAir()
                  && !pistonPos.equals(crystalBase)
                  && !redstonePos.equals(crystalPos)
                  && !redstonePos.equals(pushedCrystalPos)) {
                  float enemyDmg = this.crystalDamage(target, explosionPos);
                  if (!(enemyDmg < this.minDamage.get())) {
                     float selfDmg = this.crystalDamage(this.mc.player, explosionPos);
                     if (!(selfDmg > this.maxSelfDamage.get())
                        && (!this.antiSuicide.get() || !(selfDmg >= this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount()))
                        && enemyDmg > bestDamage) {
                        bestDamage = enemyDmg;
                        best = new PistonCrystal.Setup(pistonPos, pushDir, crystalBase, crystalPos, redstonePos, explosionPos);
                     }
                  }
               }
            }
         }
      }

      PistonCrystal.Setup vertical = this.findVerticalSetup(target, eyes, targetFeet);
      if (vertical != null && this.crystalDamage(target, vertical.explosionPos) > bestDamage) {
         best = vertical;
      }

      return best;
   }

   private PistonCrystal.Setup findVerticalSetup(PlayerEntity target, Vec3d eyes, BlockPos targetFeet) {
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            BlockPos crystalBase = targetFeet.add(dx, -1, dz);
            BlockPos crystalPos = crystalBase.up();
            BlockPos pistonPos = crystalBase.down();
            if (pistonPos.getY() >= this.mc.world.getBottomY() && !this.outOfPlaceRange(eyes, pistonPos)) {
               BlockPos pushedCrystalPos = crystalPos.up();
               Vec3d explosionPos = Vec3d.ofBottomCenter(pushedCrystalPos).add(0.0, 1.0, 0.0);
               BlockPos redstonePos = this.findRedstonePos(pistonPos, Direction.UP);
               if (redstonePos != null
                  && !this.outOfPlaceRange(eyes, redstonePos)
                  && this.canUsePistonPos(pistonPos)
                  && this.canUseCrystalBase(crystalBase, crystalPos)
                  && this.canUseRedstonePos(redstonePos)
                  && this.mc.world.getBlockState(pushedCrystalPos).isAir()
                  && this.mc.world.getBlockState(pushedCrystalPos.up()).isAir()) {
                  float enemyDmg = this.crystalDamage(target, explosionPos);
                  if (!(enemyDmg < this.minDamage.get())) {
                     float selfDmg = this.crystalDamage(this.mc.player, explosionPos);
                     if (!(selfDmg > this.maxSelfDamage.get())
                        && (!this.antiSuicide.get() || !(selfDmg >= this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount()))) {
                        return new PistonCrystal.Setup(pistonPos, Direction.UP, crystalBase, crystalPos, redstonePos, explosionPos);
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private List<BlockPos> getCandidateCrystalBases(PlayerEntity target, BlockPos targetFeet, Direction pushDir) {
      List<BlockPos> bases = new ArrayList<>();

      for (int dx = -3; dx <= 3; dx++) {
         for (int dy = -1; dy <= 2; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
               BlockPos base = targetFeet.add(dx, dy, dz);
               BlockPos pushedPos = base.up().offset(pushDir);
               if (target.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(pushedPos)) < 12.0) {
                  bases.add(base);
               }
            }
         }
      }

      return bases;
   }

   private BlockPos findRedstonePos(BlockPos pistonPos, Direction pistonFacing) {
      BlockPos behind = pistonPos.offset(pistonFacing.getOpposite());
      if (this.canUseRedstonePos(behind) && !behind.equals(pistonPos.offset(pistonFacing))) {
         return behind;
      } else {
         for (Direction dir : Direction.values()) {
            if (dir != pistonFacing) {
               BlockPos side = pistonPos.offset(dir);
               if (this.canUseRedstonePos(side)) {
                  return side;
               }
            }
         }

         return null;
      }
   }

   private boolean outOfPlaceRange(Vec3d eyes, BlockPos pos) {
      return eyes.squaredDistanceTo(Vec3d.ofCenter(pos)) > this.placeRange.get() * this.placeRange.get();
   }

   private boolean canUsePistonPos(BlockPos pos) {
      return this.isPiston(this.mc.world.getBlockState(pos)) || BlockPlacementManager.canPlaceOver(pos);
   }

   private boolean canUseCrystalBase(BlockPos base, BlockPos crystalPos) {
      if (!this.isCrystalBaseBlock(this.mc.world.getBlockState(base))) {
         return false;
      } else if (this.mc.world.getBlockState(crystalPos).isAir() && this.mc.world.getBlockState(crystalPos.up()).isAir()) {
         Box box = new Box(crystalPos.getX(), crystalPos.getY(), crystalPos.getZ(), crystalPos.getX() + 1, crystalPos.getY() + 2, crystalPos.getZ() + 1);
         return this.mc.world.getEntitiesByClass(Entity.class, box, e -> !(e instanceof PlayerEntity) && e.isAlive()).isEmpty();
      } else {
         return false;
      }
   }

   private boolean canUseRedstonePos(BlockPos pos) {
      return this.isRedstoneSource(this.mc.world.getBlockState(pos)) || BlockPlacementManager.canPlaceOver(pos);
   }

   private boolean isPiston(BlockState state) {
      return state.isOf(Blocks.PISTON) || state.isOf(Blocks.STICKY_PISTON);
   }

   private boolean isCrystalBaseBlock(BlockState state) {
      return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
   }

   private boolean isRedstoneSource(BlockState state) {
      return state.isOf(Blocks.REDSTONE_TORCH) || state.isOf(Blocks.REDSTONE_WALL_TORCH) || state.isOf(Blocks.REDSTONE_BLOCK);
   }

   private boolean isSetupInvalid() {
      if (this.target == null || this.target.isRemoved() || this.target.isDead()) {
         return true;
      } else if (this.activeSetup == null) {
         return true;
      } else {
         double r = this.placeRange.get() + 1.0;
         return this.mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(this.activeSetup.pistonPos)) > r * r;
      }
   }

   private boolean placePiston(BlockPos pos, Item item, Direction pushDir) {
      Direction dir = BlockUtils.getPlaceSide(pos);
      if (dir == null) {
         return false;
      } else {
         BlockPos neighbour = pos.offset(dir);
         Vec3d hitPos = pos.toCenterPos().add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
         Direction look = pushDir.getOpposite();
         float yaw = look.getAxis().isHorizontal() ? (float)Math.toDegrees(Math.atan2(-look.getOffsetX(), look.getOffsetZ())) : this.mc.player.getYaw();
         float pitch = look == Direction.UP ? -90.0F : (look == Direction.DOWN ? 90.0F : 0.0F);
         ReviveClient.ROTATION.requestSilentRotation("piston-crystal", 9.0, yaw, pitch);
         FindItemResult tool = InvUtils.findInHotbar(item);
         if (!tool.found()) {
            return false;
         } else {
            boolean swapped = tool.isHotbar() && this.mc.player.getInventory().selectedSlot != tool.slot() && ReviveClient.SWAP.beginSwap(tool, true);

            try {
               this.mc
                  .getNetworkHandler()
                  .sendPacket(
                     new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(hitPos, dir.getOpposite(), neighbour, false), this.nextSequence())
                  );
               if (this.swing.get()) {
                  this.mc.player.swingHand(Hand.MAIN_HAND);
               }
            } finally {
               if (swapped) {
                  ReviveClient.SWAP.endSwap(true);
               }
            }

            this.debug("placed piston " + pos.toShortString() + " facing " + pushDir);
            return true;
         }
      }
   }

   private boolean placeBlockManaged(BlockPos pos, Item item) {
      if (!ReviveClient.BLOCK.beginPlacement(List.of(pos), item)) {
         return false;
      } else {
         boolean var3;
         try {
            var3 = ReviveClient.BLOCK.placeBlock(item, pos);
         } finally {
            ReviveClient.BLOCK.endPlacement();
         }

         return var3;
      }
   }

   private boolean placeCrystalOnBase(BlockPos base, int crystalSlot) {
      if (this.mc.player != null && this.mc.getNetworkHandler() != null) {
         int currentSlot = this.mc.player.getInventory().selectedSlot;
         boolean needSwap = currentSlot != crystalSlot;
         if (needSwap) {
            this.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(crystalSlot));
         }

         Vec3d hit = Vec3d.ofCenter(base).add(0.0, 0.5, 0.0);
         BlockHitResult hitResult = new BlockHitResult(hit, Direction.UP, base, false);
         this.mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, this.nextSequence()));
         if (this.swing.get()) {
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         }

         if (needSwap) {
            this.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
         }

         this.debug("placed crystal on " + base.toShortString());
         return true;
      } else {
         return false;
      }
   }

   private void attackCrystal(EndCrystalEntity crystal) {
      if (this.mc.player != null && this.mc.getNetworkHandler() != null) {
         this.mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, this.mc.player.isSneaking()));
         if (this.swing.get()) {
            this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         }

         this.debug("attacked pushed crystal");
      }
   }

   private float crystalDamage(PlayerEntity target, Vec3d explosionPos) {
      return DamageUtil.crystalDamage(target, target.getEntityPos(), target.getBoundingBox(), explosionPos, DamageUtil.BLOCK_CHECK, false);
   }

   private PlayerEntity findTarget() {
      double rangeSq = this.targetRange.get() * this.targetRange.get();
      PlayerEntity best = null;
      double bestDist = Double.MAX_VALUE;

      for (PlayerEntity player : this.mc.world.getPlayers()) {
         if (player != this.mc.player && !player.isDead() && !player.isRemoved() && Friends.get().shouldAttack(player)) {
            GameMode mode = EntityUtils.getGameMode(player);
            if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
               double dist = this.mc.player.squaredDistanceTo(player);
               if (!(dist > rangeSq) && dist < bestDist) {
                  bestDist = dist;
                  best = player;
               }
            }
         }
      }

      return best;
   }

   private boolean placeCooldownReady() {
      return System.currentTimeMillis() - this.lastPlaceMs >= (long)(this.placeDelay.get() * 1000.0);
   }

   private int nextSequence() {
      return ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
   }

   private void toPhase(PistonCrystal.Phase next) {
      this.phase = next;
      this.phaseStartMs = System.currentTimeMillis();
      this.debug("-> " + next);
   }

   private void debug(String msg) {
      if (this.debug.get() && !msg.equals(this.lastDebug)) {
         this.lastDebug = msg;
         ChatUtils.infoPrefix("PistonCrystal", "%s", msg);
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.render.get() && this.activeSetup != null) {
         this.drawBox(event, this.activeSetup.pistonPos, new Color(160, 0, 225, 40), new Color(160, 0, 225, 120));
         this.drawBox(event, this.activeSetup.crystalPos, new Color(0, 255, 0, 40), new Color(0, 255, 0, 120));
         this.drawBox(event, this.activeSetup.redstonePos, new Color(255, 0, 0, 40), new Color(255, 0, 0, 120));
      }
   }

   private void drawBox(Render3DEvent event, BlockPos pos, Color side, Color line) {
      event.renderer.box(new Box(pos), side, line, ShapeMode.Both, 0);
   }

   private static enum Phase {
      IDLE,
      PLACE_PISTON,
      PLACE_CRYSTAL,
      PLACE_REDSTONE,
      WAIT_PUSH,
      BREAK_CRYSTAL;
   }

   public static enum PistonType {
      Normal,
      Sticky,
      Both;
   }

   private record Setup(BlockPos pistonPos, Direction pistonFacing, BlockPos crystalBase, BlockPos crystalPos, BlockPos redstonePos, Vec3d explosionPos) {
   }
}
