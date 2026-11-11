package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.SortPriority;
import revivedevelopment.reviveclient.utils.entity.TargetUtils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.MaceUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class AntiMace extends Module {
   private static final double TARGET_RANGE = 14.0;
   private static final double GRAVITY = -0.08;
   private static final double TERMINAL_VELOCITY = -3.92;
   private static final double DRAG = 0.98;
   private static final double DRAG_XZ_FLY = 0.99;
   private static final double ALIGN_D = 1.5;
   private static final double ALIGN_E = 0.01;
   private static final double LOOK_PUSH = 0.1;
   private static final double ELYTRA_GRAVITY = -0.04;
   private static final double BOOST_DRAG = 0.991;
   private static final int BOOST_DURATION_TICKS = 40;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPrediction = this.settings.createGroup("Prediction");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<AntiMace.Material> material = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("material"))
                  .description("Material to counter the mace with."))
               .defaultValue(AntiMace.Material.Obsidian))
            .build()
      );
   private final Setting<Integer> aboveOffset = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("above-offset")
            .description("How many blocks above your head to place the pattern so mace dive/aura cannot reach you.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 6)
            .build()
      );
   private final Setting<Double> placeRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("place-range")
            .description("Activate once the attacker's predicted position is this close to your eyes.")
            .defaultValue(4.5)
            .min(1.0)
            .sliderMax(8.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target-priority"))
                  .description("How to pick between several players diving at you."))
               .defaultValue(SortPriority.ClosestAngle))
            .build()
      );
   private final Setting<Boolean> onlyAbove = this.sgGeneral
      .add(new BoolSetting.Builder().name("only-above").description("Only target players above you.").defaultValue(true).build());
   private final Setting<Boolean> requireMaceInHand = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("require-holding-mace")
            .description("Require the attacker to hold a mace, wind charge or netherite axe (turn off against silent-swap mace users).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> pauseEat = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-eat").description("Pause while using an item (e.g. eating).").defaultValue(true).build());
   private final Setting<Boolean> angleDetection = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("angle-detection")
            .description(
               "Shape the obsidian pattern to the attacker's dive angle: a cap for vertical dives, a wedge toward the attacker for angled dives / mace aura."
            )
            .defaultValue(true)
            .visible(() -> this.material.get() == AntiMace.Material.Obsidian)
            .build()
      );
   private final Setting<Double> steepAngleThreshold = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("steep-angle-threshold")
            .description("Pitch (degrees) above which the attack is treated as a vertical dive. Below it, a wedge is built toward the attacker.")
            .defaultValue(60.0)
            .range(20.0, 89.0)
            .sliderRange(20.0, 89.0)
            .visible(() -> this.material.get() == AntiMace.Material.Obsidian && this.angleDetection.get())
            .build()
      );
   private final Setting<Boolean> autoAirPlace = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-air-place")
            .description("Order placements so blocks next to existing supports go first; the seed block is air-placed when needed and the rest chain off it.")
            .defaultValue(true)
            .visible(() -> this.material.get() == AntiMace.Material.Obsidian)
            .build()
      );
   private final Setting<Boolean> predictionEnabled = this.sgPrediction
      .add(
         new BoolSetting.Builder()
            .name("prediction")
            .description("Predict the attacker's position with vanilla-like fall / elytra physics.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> fallPredictionTicks = this.sgPrediction
      .add(
         new IntSetting.Builder()
            .name("fall-prediction-ticks")
            .description("Ticks to simulate ahead while the attacker is falling.")
            .defaultValue(7)
            .min(0)
            .sliderMax(20)
            .visible(this.predictionEnabled::get)
            .build()
      );
   private final Setting<Integer> elytraPredictionTicks = this.sgPrediction
      .add(
         new IntSetting.Builder()
            .name("elytra-prediction-ticks")
            .description("Ticks to simulate ahead while the attacker is elytra flying.")
            .defaultValue(7)
            .min(0)
            .sliderMax(20)
            .visible(this.predictionEnabled::get)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("render")
            .description("Render the predicted attacker position and the blocks placed this tick.")
            .defaultValue(true)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the shapes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> predictColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("predict-color")
            .description("Color of the predicted attacker point.")
            .defaultValue(new SettingColor(255, 140, 0, 35))
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> placementColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("placement-color")
            .description("Color of the placed blocks.")
            .defaultValue(new SettingColor(0, 200, 255, 35))
            .visible(this.render::get)
            .build()
      );
   private PlayerEntity target;
   private Vec3d lastPredictedCenter;
   private double lastPredictedDistance;
   private final List<BlockPos> lastPlacedThisTick = new ArrayList<>();
   private final Map<UUID, Vec3d> lastPos = new HashMap<>();
   private final Map<UUID, Vec3d> estVel = new HashMap<>();
   private final Map<UUID, Long> lastSeenTick = new HashMap<>();
   private final Map<UUID, Integer> boostingTicks = new ConcurrentHashMap<>();

   public AntiMace() {
      super(Categories.Combat, "anti-mace", "Places a shield above you to block a falling / elytra mace attacker.");
   }

   @Override
   public void onActivate() {
      this.reset();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.reset();
   }

   private void reset() {
      this.target = null;
      this.lastPredictedCenter = null;
      this.lastPredictedDistance = 0.0;
      this.lastPlacedThisTick.clear();
      this.lastPos.clear();
      this.estVel.clear();
      this.lastSeenTick.clear();
      this.boostingTicks.clear();
   }

   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      if (event.packet instanceof PlaySoundS2CPacket packet && packet.getSound() != null) {
         if (((SoundEvent)packet.getSound().value()).id().getPath().contains("mace.smash")) {
            if (this.mc.world != null) {
               Vec3d soundPos = new Vec3d(packet.getX(), packet.getY(), packet.getZ());

               for (PlayerEntity player : this.mc.world.getPlayers()) {
                  if (player != this.mc.player && new Vec3d(player.getX(), player.getY(), player.getZ()).squaredDistanceTo(soundPos) < 9.0) {
                     this.boostingTicks.put(player.getUuid(), 40);
                     break;
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onTickPre(TickEvent.Pre event) {
      if (!this.boostingTicks.isEmpty()) {
         this.boostingTicks.entrySet().removeIf(entry -> {
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
               return true;
            } else {
               entry.setValue(ticks);
               return false;
            }
         });
      }
   }

   @EventHandler
   private void onTickPost(TickEvent.Post event) {
      this.lastPredictedCenter = null;
      this.lastPlacedThisTick.clear();
      this.lastPredictedDistance = 0.0;
      if (this.mc.world != null && this.mc.player != null) {
         this.trackMotion();
         if (!this.pauseEat.get() || !this.mc.player.isUsingItem()) {
            if (this.target == null || TargetUtils.isBadTarget(this.target, 14.0) || !this.isAttacking(this.target)) {
               this.target = (PlayerEntity)TargetUtils.get(e -> e instanceof PlayerEntity p && this.isCandidate(p), this.priority.get());
               if (this.target == null) {
                  return;
               }
            }

            UUID id = this.target.getUuid();
            Vec3d currPos = new Vec3d(this.target.getX(), this.target.getY(), this.target.getZ());
            Vec3d vel = this.estVel.getOrDefault(id, new Vec3d(0.0, 0.0, 0.0));
            Vec3d future;
            if (!this.predictionEnabled.get()) {
               future = currPos;
            } else if (this.target.isGliding()) {
               future = this.boostingTicks.containsKey(id)
                  ? this.simulateBoostedElytra(currPos, vel, this.elytraPredictionTicks.get())
                  : this.simulateElytra(currPos, vel, this.target, this.elytraPredictionTicks.get());
            } else {
               future = this.simulateFall(currPos, vel, this.fallPredictionTicks.get());
            }

            Vec3d eyes = this.mc.player.getEyePos();
            this.lastPredictedDistance = eyes.distanceTo(future);
            this.lastPredictedCenter = Vec3d.ofCenter(BlockPos.ofFloored(future));
            if (!(this.lastPredictedDistance > this.placeRange.get())) {
               BlockPos centerPos = this.mc.player.getBlockPos().up(this.aboveOffset.get());
               List<BlockPos> pattern = this.material.get() == AntiMace.Material.Obsidian && this.angleDetection.get()
                  ? this.buildAttackAnglePattern(centerPos, future)
                  : this.crossPattern(centerPos);
               Block desired = this.material.get() == AntiMace.Material.Obsidian ? Blocks.OBSIDIAN : Blocks.COBWEB;
               pattern.removeIf(posx -> this.mc.world.getBlockState(posx).getBlock() == desired);
               if (!pattern.isEmpty()) {
                  if (this.material.get() == AntiMace.Material.Obsidian && this.autoAirPlace.get()) {
                     pattern = this.orderForCascade(pattern);
                  } else {
                     pattern.sort(Comparator.comparingInt(Vec3i::getY).reversed().thenComparingDouble(p -> eyes.squaredDistanceTo(Vec3d.ofCenter(p))));
                  }

                  Item useItem = this.findUseItem();
                  if (useItem != null) {
                     if (ReviveClient.BLOCK.beginPlacement(pattern, useItem)) {
                        try {
                           for (BlockPos pos : pattern) {
                              if (ReviveClient.BLOCK.placeBlock(useItem, pos)) {
                                 this.lastPlacedThisTick.add(pos);
                              }
                           }
                        } finally {
                           ReviveClient.BLOCK.endPlacement();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void trackMotion() {
      long now = this.mc.world.getTime();

      for (PlayerEntity player : this.mc.world.getPlayers()) {
         if (player != this.mc.player) {
            UUID id = player.getUuid();
            Vec3d curr = new Vec3d(player.getX(), player.getY(), player.getZ());
            Vec3d prev = this.lastPos.get(id);
            Long prevTick = this.lastSeenTick.get(id);
            if (prev != null && prevTick != null) {
               long dt = Math.max(1L, now - prevTick);
               this.estVel.put(id, curr.subtract(prev).multiply(1.0 / dt));
            }

            this.lastPos.put(id, curr);
            this.lastSeenTick.put(id, now);
         }
      }

      if (this.lastSeenTick.size() > 64) {
         this.lastSeenTick.entrySet().removeIf(e -> {
            if (Math.abs(now - e.getValue()) <= 200L) {
               return false;
            } else {
               this.lastPos.remove(e.getKey());
               this.estVel.remove(e.getKey());
               return true;
            }
         });
      }
   }

   private boolean isCandidate(PlayerEntity player) {
      if (player == this.mc.player || player.isDead() || player.getHealth() <= 0.0F) {
         return false;
      } else if (!Friends.get().shouldAttack(player)) {
         return false;
      } else {
         return TargetUtils.isBadTarget(player, 14.0) ? false : this.isAttacking(player);
      }
   }

   private boolean isAttacking(PlayerEntity player) {
      if (!player.isGliding() && player.isOnGround()) {
         return false;
      } else {
         return this.onlyAbove.get() && player.getY() <= this.mc.player.getY() + 1.0 ? false : !this.requireMaceInHand.get() || this.isHoldingMace(player);
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.render.get()) {
         if (this.lastPredictedCenter != null) {
            Vec3d c = this.lastPredictedCenter;
            Box box = new Box(c.x - 0.05, c.y - 0.05, c.z - 0.05, c.x + 0.05, c.y + 0.05, c.z + 0.05);
            event.renderer.box(box, this.predictColor.get(), this.predictColor.get(), ShapeMode.Both, 0);
         }

         for (BlockPos pos : this.lastPlacedThisTick) {
            event.renderer.box(pos, this.placementColor.get(), this.placementColor.get(), this.shapeMode.get(), 0);
         }
      }
   }

   @Override
   public String getInfoString() {
      return this.target == null ? null : String.format("%s [%.1f]", EntityUtils.getName(this.target), this.lastPredictedDistance);
   }

   private boolean isHoldingMace(PlayerEntity player) {
      return this.isMaceWeapon(player.getMainHandStack()) || this.isMaceWeapon(player.getOffHandStack());
   }

   private boolean isMaceWeapon(ItemStack stack) {
      return stack != null && !stack.isEmpty() ? MaceUtils.isMace(stack) || stack.isOf(Items.WIND_CHARGE) : false;
   }

   private Item findUseItem() {
      Item item = this.material.get() == AntiMace.Material.Obsidian ? Items.OBSIDIAN : Items.COBWEB;
      FindItemResult result = InvUtils.findInHotbar(item);
      return result.found() ? item : null;
   }

   private Vec3d simulateFall(Vec3d pos, Vec3d vel, int ticks) {
      Vec3d p = pos;
      Vec3d v = vel;

      for (int i = 0; i < ticks; i++) {
         p = p.add(v);
         double vy = Math.max((v.y + -0.08) * 0.98, -3.92);
         v = new Vec3d(v.x * 0.98, vy, v.z * 0.98);
      }

      return p;
   }

   private Vec3d simulateElytra(Vec3d pos, Vec3d vel, PlayerEntity player, int ticks) {
      Vec3d p = pos;
      Vec3d v = vel;
      double cos = Math.cos(Math.toRadians(player.getPitch()));
      Vec3d look = player.getRotationVector();

      for (int i = 0; i < ticks; i++) {
         p = p.add(v);
         double horizSpeed = Math.hypot(v.x, v.z);
         double lift = cos * cos * Math.min(1.0, v.length() / 0.4);
         double vy = v.y + -0.04 + lift * 0.06;
         if (v.y < 0.0 && horizSpeed > 0.0) {
            vy += -0.1 * v.y * lift;
         }

         Vec3d afterLift = new Vec3d(v.x, vy, v.z);
         Vec3d aligned = afterLift.add(
            look.x * 0.1 + (look.x * 1.5 - afterLift.x) * 0.01,
            look.y * 0.1 + (look.y * 1.5 - afterLift.y) * 0.01,
            look.z * 0.1 + (look.z * 1.5 - afterLift.z) * 0.01
         );
         v = new Vec3d(aligned.x * 0.99, aligned.y * 0.98, aligned.z * 0.99);
      }

      return p;
   }

   private Vec3d simulateBoostedElytra(Vec3d pos, Vec3d vel, int ticks) {
      Vec3d p = pos;
      Vec3d v = vel;

      for (int i = 0; i < ticks; i++) {
         p = p.add(v);
         v = new Vec3d(v.x * 0.991, Math.max(v.y, -3.92), v.z * 0.991);
      }

      return p;
   }

   private List<BlockPos> crossPattern(BlockPos center) {
      List<BlockPos> p = new ArrayList<>(7);
      p.add(center);
      p.add(center.up());
      p.add(center.down());
      p.add(center.north());
      p.add(center.south());
      p.add(center.east());
      p.add(center.west());
      return p;
   }

   private List<BlockPos> buildAttackAnglePattern(BlockPos center, Vec3d attacker) {
      Vec3d playerCenter = new Vec3d(this.mc.player.getX(), this.mc.player.getY() + this.mc.player.getHeight() / 2.0, this.mc.player.getZ());
      Vec3d toAttacker = attacker.subtract(playerCenter);
      double horizLen = Math.hypot(toAttacker.x, toAttacker.z);
      double pitchDeg = Math.toDegrees(Math.atan2(Math.abs(toAttacker.y), Math.max(horizLen, 1.0E-4)));
      List<BlockPos> p = new ArrayList<>(7);
      p.add(center);
      p.add(center.up());
      if (pitchDeg >= this.steepAngleThreshold.get()) {
         p.add(center.north());
         p.add(center.south());
         p.add(center.east());
         p.add(center.west());
      } else {
         Direction primary = this.dominantHorizontal(toAttacker.x, toAttacker.z);
         Direction secondary = this.secondaryHorizontal(toAttacker.x, toAttacker.z, primary);
         BlockPos side = center.offset(primary);
         p.add(side);
         p.add(side.up());
         if (secondary != null && secondary != primary) {
            p.add(center.offset(secondary));
         }
      }

      return p;
   }

   private Direction dominantHorizontal(double dx, double dz) {
      if (Math.abs(dx) >= Math.abs(dz)) {
         return dx >= 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   private Direction secondaryHorizontal(double dx, double dz, Direction primary) {
      double ax = Math.abs(dx);
      double az = Math.abs(dz);
      if (Math.min(ax, az) / Math.max(Math.max(ax, az), 1.0E-4) < 0.45) {
         return null;
      } else if (primary != Direction.EAST && primary != Direction.WEST) {
         return dx >= 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   private List<BlockPos> orderForCascade(List<BlockPos> pattern) {
      List<BlockPos> remaining = new ArrayList<>(pattern);
      List<BlockPos> ordered = new ArrayList<>(pattern.size());
      Set<BlockPos> planned = new HashSet<>();

      while (!remaining.isEmpty()) {
         BlockPos best = remaining.get(0);
         int bestScore = -1;

         for (BlockPos pos : remaining) {
            int score = this.supportScore(pos, planned);
            if (score > bestScore) {
               bestScore = score;
               best = pos;
            }
         }

         ordered.add(best);
         planned.add(best);
         remaining.remove(best);
      }

      return ordered;
   }

   private int supportScore(BlockPos pos, Set<BlockPos> planned) {
      int score = 0;

      for (Direction d : Direction.values()) {
         BlockPos n = pos.offset(d);
         if (!this.mc.world.getBlockState(n).isAir()) {
            score += 3;
         } else if (planned.contains(n)) {
            score += 2;
         }
      }

      return score;
   }

   public static enum Material {
      Obsidian,
      Cobweb;
   }
}
