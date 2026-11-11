package revivedevelopment.reviveclient.systems.modules.combat;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.GameMode;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.packets.PlaySoundPacketEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.PbFx;
import revivedevelopment.reviveclient.pingbypass.PbRenderable;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.BlockPlacementManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.systems.modules.render.LogoutSpots;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class AutoTrap extends Module implements PbRenderable {
   private static final double TERMINAL_VELOCITY = -3.92;
   private static final double DRAG_XZ_FLY = 0.99;
   private static final double DRAG_Y_FLY = 0.98;
   private static final double ALIGN_D = 1.5;
   private static final double ALIGN_E = 0.01;
   private static final double LOOK_PUSH = 0.1;
   private static final int BOOST_DURATION_TICKS = 40;
   private static final double ELYTRA_GRAVITY = -0.04;
   private static final int ELYTRA_MIN_LEAD_TICKS = 1;
   private static final int ELYTRA_MAX_LEAD_TICKS = 9;
   private static final int ELYTRA_MIN_BLOCK_BUDGET = 3;
   private static final int ELYTRA_MAX_BLOCK_BUDGET = 10;
   private static final Direction[] HORIZONTAL_DIRS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   private static final double HITBOX_EPSILON = 1.0E-4;
   private static final double MOVE_EPSILON = 1.0E-5;
   private static final double PLAYER_WIDTH = 0.6;
   private static final double STANDING_HEIGHT = 1.8;
   private static final double CROUCHING_HEIGHT = 1.5;
   private static final double LOW_PROFILE_HEIGHT = 0.6;
   private boolean buildInterceptNext = true;
   private Set<BlockPos> placePoses;
   private final Map<UUID, Integer> boostingTicks = new HashMap<>();
   private int trapClosingTicks = -1;
   private int lastElytraLeadTicks = 1;
   private int lastElytraBlockBudget = 3;
   private PlayerEntity target;
   private final List<BlockPos> posesToRender = new ArrayList<>();
   private final Map<BlockPos, Long> renderLastPlacedBlock = new HashMap<>();
   private final Map<BlockPos, Long> renderLastRemovedBlock = new HashMap<>();
   private final Set<BlockPos> lastRenderPoses = new HashSet<>();
   private final List<Vec3d> debugElytraPath = new ArrayList<>();
   private Box debugElytraBox = null;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(new BlockListSetting.Builder().name("whitelist").description("Blocks to trap with.").defaultValue(Blocks.OBSIDIAN).build());
   private final Setting<Boolean> pauseEat = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-eat").description("Pauses while eating.").defaultValue(true).build());
   private final Setting<Boolean> trapLogs = this.sgGeneral
      .add(new BoolSetting.Builder().name("trap-logs").description("Traps logout spots. (Requires LogoutSpots module)").defaultValue(false).build());
   private final Setting<Boolean> ignoreCrawled = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-crawled").description("Ignore crawling player.").defaultValue(false).build());
   private final Setting<Boolean> predictMovement = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("predict-movement")
            .description("Use target velocity to predict position and place blocks ahead.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> predictionTicks = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("prediction-ticks")
            .description("How many ticks ahead to predict for ground targets.")
            .defaultValue(2)
            .range(0, 5)
            .sliderRange(0, 5)
            .visible(this.predictMovement::get)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Render the trap blocks.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the shapes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<Double> fadeTime = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("fade-time")
            .description("Seconds a placed/removed block stays rendered before fading out.")
            .defaultValue(0.2)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("Side color of placed blocks.")
            .defaultValue(new SettingColor(170, 0, 255, 40))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("Line color of placed blocks.")
            .defaultValue(new SettingColor(170, 0, 255, 120))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
      );
   private final Setting<Boolean> debugPrediction = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("debug-prediction")
            .description("Render the sampled elytra prediction path and selected intercept box.")
            .defaultValue(false)
            .visible(this.render::get)
            .build()
      );
   private final Setting<Boolean> debug = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Log AutoTrap's per-tick decisions to chat so you can see why it does / doesn't place. Logs only when the status changes (no spam).")
            .defaultValue(false)
            .build()
      );
   private String lastDebugMsg = "";
   private String placeReason = "";

   public AutoTrap() {
      super(Categories.Combat, "auto-trap", "Traps people in a box to prevent them from moving.");
   }

   private void resetElytraTrap() {
      this.buildInterceptNext = true;
      this.trapClosingTicks = -1;
      this.lastElytraLeadTicks = 1;
      this.lastElytraBlockBudget = 3;
      this.clearDebugPrediction();
   }

   @Override
   public void onActivate() {
      this.target = null;
      this.posesToRender.clear();
      this.renderLastPlacedBlock.clear();
      this.renderLastRemovedBlock.clear();
      this.lastRenderPoses.clear();
      this.clearDebugPrediction();
      this.resetElytraTrap();
      this.lastDebugMsg = "";
      this.placeReason = "";
   }

   @Override
   public void onDeactivate() {
      this.target = null;
   }

   @Override
   public String getInfoString() {
      return this.target != null ? this.target.getName().getString() : null;
   }

   @EventHandler
   private void onPlaySound(PlaySoundPacketEvent event) {
      if (this.mc.world != null && !this.mc.world.getPlayers().isEmpty()) {
         PlaySoundS2CPacket packet = event.packet;
         if (packet.getSound() != null) {
            if (((SoundEvent)packet.getSound().value()).id().getPath().contains("firework_rocket.launch")) {
               Vec3d soundPos = new Vec3d(packet.getX(), packet.getY(), packet.getZ());

               for (PlayerEntity player : this.mc.world.getPlayers()) {
                  if (player.getEntityPos().squaredDistanceTo(soundPos) < 9.0) {
                     this.boostingTicks.put(player.getUuid(), 40);
                     break;
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.runAutoTrapTick();
   }

   private void runAutoTrapTick() {
      this.posesToRender.clear();
      if (this.mc.player != null && this.mc.world != null) {
         if (!this.boostingTicks.isEmpty()) {
            this.boostingTicks.entrySet().removeIf(e -> {
               int left = e.getValue() - 1;
               if (left <= 0) {
                  return true;
               } else {
                  e.setValue(left);
                  return false;
               }
            });
         }

         if (this.trapClosingTicks > 0) {
            this.trapClosingTicks--;
         }

         if (this.target == null || this.isBadTarget(this.target, 14.0) || this.isIgnoredCrawlingTarget(this.target)) {
            this.target = this.findTrapTarget();
            this.resetElytraTrap();
            if (this.target == null) {
               if (this.trapLogs.get()) {
                  this.target = this.findLogoutTarget();
               }

               if (this.target == null) {
                  this.dbg("no target in range (need an enemy/non-friend in survival within 14 blocks)");
                  this.updateFading(Collections.emptyList());
                  return;
               }
            }
         }

         if (this.pauseEat.get() && this.mc.player.isUsingItem()) {
            this.dbg("paused: you are using an item (Pause Eat is on)");
            this.updateFading(Collections.emptyList());
         } else {
            AutoTrap.PredictedTargetState predictedState = this.predictTargetState(this.target);
            this.placePoses = this.getPoseTrapPoses(predictedState);
            List<BlockPos> reachablePoses = new ArrayList<>();
            Vec3d eyePos = this.mc.player.getEyePos();

            for (BlockPos pos : this.placePoses) {
               Vec3d closest = clampClosestPoint(eyePos, new Box(pos));
               if (eyePos.distanceTo(closest) <= this.placeRange()) {
                  reachablePoses.add(pos);
               }
            }

            if (reachablePoses.isEmpty()) {
               this.dbg(
                  "target=%s pose=%s: %d trap spots computed but none within place range (%.1f). Move closer.",
                  this.targetName(),
                  predictedState.pose,
                  this.placePoses.size(),
                  this.placeRange()
               );
               this.updateFading(Collections.emptyList());
            } else {
               List<BlockPos> renderablePoses = reachablePoses.stream().filter(BlockPlacementManager::canPlaceOver).collect(Collectors.toList());
               this.sortTrapCandidates(renderablePoses, predictedState, eyePos);
               if (predictedState.pose == AutoTrap.TrapPose.FALL_FLYING) {
                  int maxStopBlocks = Math.min(this.lastElytraBlockBudget, renderablePoses.size());
                  if (maxStopBlocks > 0 && renderablePoses.size() > maxStopBlocks) {
                     renderablePoses = new ArrayList<>(renderablePoses.subList(0, maxStopBlocks));
                  }
               }

               this.posesToRender.addAll(renderablePoses);
               int candidateCount = renderablePoses.size();
               this.placeReason = "ok";
               List<BlockPos> placedBlocks = this.placeBlockBatch(renderablePoses);
               if (!placedBlocks.isEmpty()) {
                  this.posesToRender.removeAll(placedBlocks);
               }

               this.dbg(
                  "target=%s pose=%s | trapSpots=%d reachable=%d placeable=%d placed=%d%s",
                  this.targetName(),
                  predictedState.pose,
                  this.placePoses.size(),
                  reachablePoses.size(),
                  candidateCount,
                  placedBlocks.size(),
                  placedBlocks.isEmpty() ? " | reason: " + this.placeReason : ""
               );
               this.updateFading(this.posesToRender);
            }
         }
      }
   }

   private void updateFading(List<BlockPos> currentPoses) {
      long now = System.currentTimeMillis();
      double fadeMs = this.fadeTimeMs();
      Set<BlockPos> currentSet = new HashSet<>(currentPoses);

      for (BlockPos pos : currentSet) {
         this.renderLastPlacedBlock.put(pos, now);
      }

      for (BlockPos pos : this.lastRenderPoses) {
         if (!currentSet.contains(pos)) {
            this.renderLastRemovedBlock.put(pos, now);
         }
      }

      this.lastRenderPoses.clear();
      this.lastRenderPoses.addAll(currentSet);
      if (fadeMs > 0.0) {
         this.renderLastPlacedBlock.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
         this.renderLastRemovedBlock.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
      } else {
         this.renderLastPlacedBlock.entrySet().removeIf(e -> !currentSet.contains(e.getKey()));
         this.renderLastRemovedBlock.clear();
      }
   }

   private List<BlockPos> placeBlockBatch(List<BlockPos> placeablePoses) {
      placeablePoses.removeIf(this::isRebreakTarget);
      List<BlockPos> placedBlocks = new ArrayList<>();
      if (placeablePoses.isEmpty()) {
         this.placeReason = "all candidates are SilentMine rebreak targets";
         return placedBlocks;
      } else {
         Item blockItem = this.findAvailableBlockItem();
         if (blockItem == null) {
            this.placeReason = "no whitelisted block found in inventory (check Whitelist; need e.g. obsidian)";
            return placedBlocks;
         } else {
            BlockPlacementManager bpm = ReviveClient.BLOCK;
            placeablePoses.removeIf(bpm::isOnPlaceCooldown);
            if (placeablePoses.isEmpty()) {
               this.placeReason = "all candidates on per-block place cooldown";
               return placedBlocks;
            } else {
               int budget = bpm.getRemainingBudget();
               if (budget <= 0) {
                  this.placeReason = "block-interact packet budget exhausted (AntiCheat packet limits)";
                  return placedBlocks;
               } else {
                  if (placeablePoses.size() > budget) {
                     placeablePoses = new ArrayList<>(placeablePoses.subList(0, budget));
                  }

                  if (bpm.beginPlacement(placeablePoses, blockItem)) {
                     try {
                        for (BlockPos pos : placeablePoses) {
                           if (bpm.placeBlock(blockItem, pos)) {
                              placedBlocks.add(pos);
                           }
                        }
                     } finally {
                        bpm.endPlacement();
                     }

                     if (placedBlocks.isEmpty()) {
                        this.placeReason = "beginPlacement ok but every placeBlock() was rejected (range/checkPlacement/per-block cooldown)";
                     }
                  } else {
                     this.placeReason = "beginPlacement failed (swap to block failed, or packet budget / placement check)";
                  }

                  if (!placedBlocks.isEmpty() && this.target != null && this.target.isGliding() && this.buildInterceptNext) {
                     this.buildInterceptNext = false;
                     this.trapClosingTicks = Math.max(this.lastElytraLeadTicks - 1, 0);
                  }

                  return placedBlocks;
               }
            }
         }
      }
   }

   private Item findAvailableBlockItem() {
      for (Block block : this.blocks.get()) {
         if (block != Blocks.AIR) {
            Item item = block.asItem();
            if (item != Items.AIR && InvUtils.findInHotbar(item).found()) {
               return item;
            }
         }
      }

      for (Block blockx : this.blocks.get()) {
         if (blockx != Blocks.AIR) {
            Item item = blockx.asItem();
            if (item != Items.AIR && InvUtils.find(item).found()) {
               return item;
            }
         }
      }

      return null;
   }

   private AutoTrap.PredictedTargetState predictTargetState(PlayerEntity player) {
      AutoTrap.TrapPose pose = this.detectTrapPose(player);
      Vec3d startPos = player.getEntityPos();
      Vec3d pos = startPos;
      Vec3d velocity = player.getVelocity();
      Box box = this.getPoseBoxAt(player, pose, startPos);
      if (pose == AutoTrap.TrapPose.FALL_FLYING && this.predictMovement.get()) {
         return this.predictElytraTargetState(player, pose, startPos, box, velocity);
      } else {
         this.clearDebugPrediction();
         boolean onGround = player.isOnGround();
         int ticks = this.getPredictionTicksFor(pose);

         for (int i = 0; i < ticks && !(velocity.lengthSquared() <= 1.0000000000000002E-10); i++) {
            AutoTrap.CollisionMove collisionMove = this.moveWithCollision(player, box, velocity);
            box = collisionMove.box;
            pos = pos.add(collisionMove.movement);
            boolean grounded = collisionMove.collidedY && velocity.y < 0.0 || this.isBoxOnGround(player, box);
            velocity = this.nextVelocity(player, pose, velocity, collisionMove.movement, grounded);
         }

         Vec3d predictedTravel = pos.subtract(startPos);
         if (horizontalLengthSqr(predictedTravel) <= 1.0000000000000002E-10) {
            predictedTravel = player.getVelocity();
         }

         return new AutoTrap.PredictedTargetState(pose, startPos, pos, velocity, box, predictedTravel, ticks, 0.0, List.of(startPos, pos));
      }
   }

   private AutoTrap.TrapPose detectTrapPose(PlayerEntity player) {
      if (player.isGliding()) {
         return AutoTrap.TrapPose.FALL_FLYING;
      } else if (player.isSwimming()) {
         return AutoTrap.TrapPose.SWIMMING;
      } else if (player.isCrawling()) {
         return AutoTrap.TrapPose.CRAWLING;
      } else {
         return player.isSneaking() ? AutoTrap.TrapPose.CROUCHING : AutoTrap.TrapPose.STANDING;
      }
   }

   private int getPredictionTicksFor(AutoTrap.TrapPose pose) {
      if (!this.predictMovement.get()) {
         return 0;
      } else {
         return pose == AutoTrap.TrapPose.FALL_FLYING ? 0 : this.predictionTicks.get();
      }
   }

   private AutoTrap.PredictedTargetState predictElytraTargetState(
      PlayerEntity player, AutoTrap.TrapPose pose, Vec3d startPos, Box startBox, Vec3d startVelocity
   ) {
      List<AutoTrap.PredictedTargetState> samples = new ArrayList<>();
      List<Vec3d> path = new ArrayList<>();
      path.add(startPos);
      Vec3d pos = startPos;
      Vec3d velocity = startVelocity;
      Box box = startBox;
      int maxLeadTicks = this.getAdaptiveElytraMaxLead(player, startVelocity);
      AutoTrap.PredictedTargetState bestState = null;
      AutoTrap.ElytraSampleScore bestScore = null;

      for (int tick = 1; tick <= maxLeadTicks; tick++) {
         Vec3d desiredMove = velocity;
         if (velocity.lengthSquared() <= 1.0000000000000002E-10) {
            break;
         }

         AutoTrap.CollisionMove collisionMove = this.moveWithCollision(player, box, velocity);
         box = collisionMove.box;
         pos = pos.add(collisionMove.movement);
         velocity = this.nextVelocity(player, pose, velocity, collisionMove.movement, false);
         path.add(pos);
         Vec3d predictedTravel = pos.subtract(startPos);
         double uncertainty = this.getElytraUncertainty(player, desiredMove, velocity, tick);
         AutoTrap.PredictedTargetState sample = new AutoTrap.PredictedTargetState(
            pose, startPos, pos, velocity, box, predictedTravel, tick, uncertainty, List.copyOf(path)
         );
         samples.add(sample);
         AutoTrap.ElytraSampleScore sampleScore = this.scoreElytraPredictionSample(sample);
         if (bestState == null || sampleScore.score > bestScore.score) {
            bestState = sample;
            bestScore = sampleScore;
         }

         if (sampleScore.frontCount > 0 && sampleScore.placeableCount >= 3 && sampleScore.score >= 160.0) {
            bestState = sample;
            bestScore = sampleScore;
            break;
         }

         if (collisionMove.collidedX || collisionMove.collidedY || collisionMove.collidedZ) {
            break;
         }
      }

      if (bestState == null) {
         bestState = new AutoTrap.PredictedTargetState(
            pose,
            startPos,
            startPos,
            startVelocity,
            startBox,
            startVelocity,
            0,
            this.getElytraUncertainty(player, startVelocity, startVelocity, 0),
            List.of(startPos)
         );
         bestScore = this.scoreElytraPredictionSample(bestState);
      }

      this.lastElytraLeadTicks = Math.max(1, bestState.leadTicks);
      this.lastElytraBlockBudget = this.getAdaptiveElytraBlockBudget(bestState, bestScore);
      this.updateDebugPrediction(bestState);
      return bestState;
   }

   private int getAdaptiveElytraMaxLead(PlayerEntity player, Vec3d velocity) {
      double speed = velocity.length();
      double horizontalSpeed = Math.sqrt(horizontalLengthSqr(velocity));
      double lookAlignment = this.getHorizontalAlignment(velocity, player.getRotationVector());
      int lead = 4;
      if (speed > 0.7) {
         lead++;
      }

      if (speed > 1.15) {
         lead++;
      }

      if (horizontalSpeed > 1.5) {
         lead++;
      }

      if (this.boostingTicks.containsKey(player.getUuid())) {
         lead += 2;
      }

      if (lookAlignment < 0.35) {
         lead--;
      }

      return Math.max(1, Math.min(9, lead));
   }

   private double getElytraUncertainty(PlayerEntity player, Vec3d previousVelocity, Vec3d nextVelocity, int leadTicks) {
      double speed = previousVelocity.length();
      double lookAlignment = this.getHorizontalAlignment(previousVelocity, player.getRotationVector());
      double turnAmount = !(previousVelocity.lengthSquared() <= 1.0000000000000002E-10) && !(nextVelocity.lengthSquared() <= 1.0000000000000002E-10)
         ? 1.0 - Math.max(-1.0, Math.min(1.0, previousVelocity.normalize().dotProduct(nextVelocity.normalize())))
         : 0.0;
      double speedFactor = MathHelper.clamp(speed / 2.0, 0.0, 1.0);
      double turnFactor = MathHelper.clamp(turnAmount * 1.8, 0.0, 1.0);
      double steeringFactor = MathHelper.clamp(1.0 - Math.max(0.0, lookAlignment), 0.0, 1.0);
      double leadFactor = MathHelper.clamp(leadTicks / 9.0, 0.0, 1.0);
      return MathHelper.clamp(speedFactor * 0.35 + turnFactor * 0.25 + steeringFactor * 0.25 + leadFactor * 0.15, 0.0, 1.0);
   }

   private AutoTrap.ElytraSampleScore scoreElytraPredictionSample(AutoTrap.PredictedTargetState sample) {
      if (this.mc.player == null) {
         return new AutoTrap.ElytraSampleScore(Double.NEGATIVE_INFINITY, 0, 0, Double.POSITIVE_INFINITY);
      } else {
         Vec3d eye = this.mc.player.getEyePos();
         Vec3d forward = this.horizontalUnit(sample.predictedTravel);
         Vec3d center = boxCenter(sample.box);
         int placeable = 0;
         int front = 0;
         double bestReachSq = Double.POSITIVE_INFINITY;

         for (BlockPos pos : this.getPoseTrapPoses(sample)) {
            if (BlockPlacementManager.canPlaceOver(pos)) {
               Vec3d closest = clampClosestPoint(eye, new Box(pos));
               double reachSq = eye.squaredDistanceTo(closest);
               if (!(reachSq > this.placeRange() * this.placeRange())) {
                  placeable++;
                  bestReachSq = Math.min(bestReachSq, reachSq);
                  if (forward.lengthSquared() > 0.0) {
                     Vec3d toBlock = new Vec3d(Vec3d.ofCenter(pos).x - center.x, 0.0, Vec3d.ofCenter(pos).z - center.z);
                     double len = Math.sqrt(horizontalLengthSqr(toBlock));
                     if (len > 1.0E-5 && (toBlock.x * forward.x + toBlock.z * forward.z) / len > 0.35) {
                        front++;
                     }
                  }
               }
            }
         }

         if (placeable == 0) {
            return new AutoTrap.ElytraSampleScore(Double.NEGATIVE_INFINITY, 0, 0, bestReachSq);
         } else {
            double reach = Math.sqrt(bestReachSq);
            double pr = this.placeRange();
            double reachScore = MathHelper.clamp((pr - reach) / pr, 0.0, 1.0) * 220.0;
            double score = reachScore
               + Math.min(placeable, 10) * 18.0
               + front * 70.0
               - sample.leadTicks * (10.0 + sample.uncertainty * 22.0)
               - sample.uncertainty * 65.0;
            return new AutoTrap.ElytraSampleScore(score, placeable, front, bestReachSq);
         }
      }
   }

   private int getAdaptiveElytraBlockBudget(AutoTrap.PredictedTargetState state, AutoTrap.ElytraSampleScore score) {
      double speed = state.velocity.length();
      int desired = 3;
      if (score.frontCount <= 1) {
         desired++;
      }

      if (speed > 0.7) {
         desired++;
      }

      if (speed > 1.15) {
         desired++;
      }

      desired += (int)Math.round(state.uncertainty * 4.0);
      if (score.placeableCount > 0) {
         desired = Math.min(desired, score.placeableCount);
      }

      return Math.max(3, Math.min(10, desired));
   }

   private double getHorizontalAlignment(Vec3d velocity, Vec3d look) {
      double vLen = Math.sqrt(horizontalLengthSqr(velocity));
      double lLen = Math.sqrt(horizontalLengthSqr(look));
      return !(vLen <= 1.0E-5) && !(lLen <= 1.0E-5) ? (velocity.x * look.x + velocity.z * look.z) / (vLen * lLen) : 1.0;
   }

   private void updateDebugPrediction(AutoTrap.PredictedTargetState state) {
      this.debugElytraPath.clear();
      this.debugElytraPath.addAll(state.path);
      this.debugElytraBox = state.box;
   }

   private void clearDebugPrediction() {
      this.debugElytraPath.clear();
      this.debugElytraBox = null;
   }

   private Box getPoseBoxAt(PlayerEntity player, AutoTrap.TrapPose pose, Vec3d pos) {
      double width = Math.max(0.6, (double)player.getWidth());

      double height = switch (pose) {
         case STANDING -> 1.8;
         case CROUCHING -> 1.5;
         case CRAWLING, SWIMMING, FALL_FLYING -> 0.6;
      };
      return new Box(pos.x - width / 2.0, pos.y, pos.z - width / 2.0, pos.x + width / 2.0, pos.y + height, pos.z + width / 2.0);
   }

   private AutoTrap.CollisionMove moveWithCollision(PlayerEntity player, Box box, Vec3d desiredMove) {
      double dy = this.clipAxisMove(player, box, desiredMove.y, Axis.Y);
      Box movedBox = box.offset(0.0, dy, 0.0);
      double dx = this.clipAxisMove(player, movedBox, desiredMove.x, Axis.X);
      movedBox = movedBox.offset(dx, 0.0, 0.0);
      double dz = this.clipAxisMove(player, movedBox, desiredMove.z, Axis.Z);
      movedBox = movedBox.offset(0.0, 0.0, dz);
      return new AutoTrap.CollisionMove(
         new Vec3d(dx, dy, dz), movedBox, Math.abs(dx - desiredMove.x) > 1.0E-5, Math.abs(dy - desiredMove.y) > 1.0E-5, Math.abs(dz - desiredMove.z) > 1.0E-5
      );
   }

   private double clipAxisMove(PlayerEntity player, Box box, double desired, Axis axis) {
      if (Math.abs(desired) <= 1.0E-5) {
         return 0.0;
      } else if (this.canOccupy(player, this.moveBoxAlongAxis(box, axis, desired))) {
         return desired;
      } else {
         double best = 0.0;
         double low = 0.0;
         double high = 1.0;

         for (int i = 0; i < 12; i++) {
            double mid = (low + high) * 0.5;
            double candidate = desired * mid;
            if (this.canOccupy(player, this.moveBoxAlongAxis(box, axis, candidate))) {
               best = candidate;
               low = mid;
            } else {
               high = mid;
            }
         }

         return Math.abs(best) <= 1.0E-5 ? 0.0 : best;
      }
   }

   private Box moveBoxAlongAxis(Box box, Axis axis, double amount) {
      return switch (axis) {
         case X -> box.offset(amount, 0.0, 0.0);
         case Y -> box.offset(0.0, amount, 0.0);
         case Z -> box.offset(0.0, 0.0, amount);
         default -> throw new MatchException(null, null);
      };
   }

   private boolean canOccupy(PlayerEntity player, Box box) {
      return this.mc.world == null ? false : this.mc.world.isSpaceEmpty(player, box.expand(-1.0E-4, -1.0E-4, -1.0E-4));
   }

   private boolean isBoxOnGround(PlayerEntity player, Box box) {
      return !this.canOccupy(player, box.offset(0.0, -0.05, 0.0));
   }

   private Vec3d nextVelocity(PlayerEntity player, AutoTrap.TrapPose pose, Vec3d desiredMove, Vec3d actualMove, boolean onGround) {
      Vec3d clipped = new Vec3d(
         Math.abs(actualMove.x - desiredMove.x) > 1.0E-5 ? 0.0 : actualMove.x,
         Math.abs(actualMove.y - desiredMove.y) > 1.0E-5 ? 0.0 : actualMove.y,
         Math.abs(actualMove.z - desiredMove.z) > 1.0E-5 ? 0.0 : actualMove.z
      );
      if (pose == AutoTrap.TrapPose.FALL_FLYING) {
         return this.boostingTicks.containsKey(player.getUuid()) ? this.nextBoostedElytraVelocity(clipped) : this.nextElytraVelocity(player, clipped);
      } else if (player.isTouchingWater()) {
         return new Vec3d(clipped.x * 0.8, (clipped.y - 0.02) * 0.8, clipped.z * 0.8);
      } else if (player.isInLava()) {
         return new Vec3d(clipped.x * 0.5, (clipped.y - 0.02) * 0.5, clipped.z * 0.5);
      } else {
         double y = clipped.y;
         if (!onGround) {
            y -= 0.08;
            if (y < -3.92) {
               y = -3.92;
            }
         } else if (y < 0.0) {
            y = 0.0;
         }

         double horizontalDrag = onGround ? 0.6 : 0.91;
         return new Vec3d(clipped.x * horizontalDrag, y * 0.98, clipped.z * horizontalDrag);
      }
   }

   private Vec3d nextElytraVelocity(PlayerEntity player, Vec3d velocity) {
      float pitchRad = (float)Math.toRadians(player.getPitch());
      Vec3d look = player.getRotationVector();
      double cos = Math.cos(pitchRad);
      double horizSpeed = Math.hypot(velocity.x, velocity.z);
      double len = velocity.length();
      double liftFactor = cos * cos * Math.min(1.0, len / 0.4);
      double vy = velocity.y + (-0.04 + liftFactor * 0.06);
      if (velocity.y < 0.0 && horizSpeed > 0.0) {
         vy += -0.1 * velocity.y * liftFactor;
      }

      Vec3d lifted = new Vec3d(velocity.x, vy, velocity.z);
      Vec3d align = new Vec3d(
         look.x * 0.1 + (look.x * 1.5 - lifted.x) * 0.01, look.y * 0.1 + (look.y * 1.5 - lifted.y) * 0.01, look.z * 0.1 + (look.z * 1.5 - lifted.z) * 0.01
      );
      Vec3d aligned = lifted.add(align);
      return new Vec3d(aligned.x * 0.99, aligned.y * 0.98, aligned.z * 0.99);
   }

   private Vec3d nextBoostedElytraVelocity(Vec3d velocity) {
      double boostDrag = 0.991;
      double y = Math.max(velocity.y, -3.92);
      return new Vec3d(velocity.x * 0.991, y, velocity.z * 0.991);
   }

   private Set<BlockPos> getPoseTrapPoses(AutoTrap.PredictedTargetState predicted) {
      Set<BlockPos> poses = new LinkedHashSet<>();
      Box box = predicted.box.expand(-0.025, 0.0, -0.025);
      int minX = floorMin(box.minX);
      int maxX = floorMax(box.maxX);
      int minY = floorMin(box.minY);
      int maxY = floorMax(box.maxY);
      int minZ = floorMin(box.minZ);
      int maxZ = floorMax(box.maxZ);
      int wallMinY = this.firstTrapWallY(predicted.pose, minY, maxY);

      for (int y = wallMinY; y <= maxY; y++) {
         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               BlockPos occupied = new BlockPos(x, y, z);

               for (Direction dir : HORIZONTAL_DIRS) {
                  this.addTrapCandidate(poses, occupied.offset(dir), box);
               }
            }
         }
      }

      int ceilingY = maxY + 1;
      int floorY = minY - 1;

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            this.addTrapCandidate(poses, new BlockPos(x, ceilingY, z), box);
            this.addTrapCandidate(poses, new BlockPos(x, floorY, z), box);
         }
      }

      this.addPrimaryForwardCandidates(poses, predicted, box, minX, maxX, minY, maxY, minZ, maxZ);
      return poses;
   }

   private int firstTrapWallY(AutoTrap.TrapPose pose, int minY, int maxY) {
      return (pose == AutoTrap.TrapPose.STANDING || pose == AutoTrap.TrapPose.CROUCHING) && minY < maxY ? minY + 1 : minY;
   }

   private void addPrimaryForwardCandidates(
      Set<BlockPos> poses, AutoTrap.PredictedTargetState predicted, Box box, int minX, int maxX, int minY, int maxY, int minZ, int maxZ
   ) {
      Direction forward = this.primaryHorizontalDirection(predicted.predictedTravel);
      if (forward != null) {
         int wallMinY = this.firstTrapWallY(predicted.pose, minY, maxY);
         switch (forward) {
            case EAST:
               for (int y = wallMinY; y <= maxY; y++) {
                  for (int z = minZ; z <= maxZ; z++) {
                     this.addTrapCandidate(poses, new BlockPos(maxX + 1, y, z), box);
                  }
               }
               break;
            case WEST:
               for (int y = wallMinY; y <= maxY; y++) {
                  for (int z = minZ; z <= maxZ; z++) {
                     this.addTrapCandidate(poses, new BlockPos(minX - 1, y, z), box);
                  }
               }
               break;
            case SOUTH:
               for (int y = wallMinY; y <= maxY; y++) {
                  for (int x = minX; x <= maxX; x++) {
                     this.addTrapCandidate(poses, new BlockPos(x, y, maxZ + 1), box);
                  }
               }
               break;
            case NORTH:
               for (int y = wallMinY; y <= maxY; y++) {
                  for (int x = minX; x <= maxX; x++) {
                     this.addTrapCandidate(poses, new BlockPos(x, y, minZ - 1), box);
                  }
               }
         }
      }
   }

   private void addTrapCandidate(Set<BlockPos> poses, BlockPos pos, Box targetBox) {
      if (!new Box(pos).intersects(targetBox.expand(-1.0E-4, -1.0E-4, -1.0E-4))) {
         poses.add(pos.toImmutable());
      }
   }

   private void sortTrapCandidates(List<BlockPos> poses, AutoTrap.PredictedTargetState predicted, Vec3d eyePos) {
      Vec3d forward = this.horizontalUnit(predicted.predictedTravel);
      Vec3d center = boxCenter(predicted.box);
      poses.sort(
         Comparator.<BlockPos>comparingInt(pos -> BlockPlacementManager.getPlaceOnDirection(pos) != null ? 0 : 1)
            .thenComparing(Comparator.<BlockPos>comparingDouble(pos -> this.scoreTrapCandidate(pos, predicted, center, forward)).reversed())
            .thenComparingDouble(pos -> eyePos.squaredDistanceTo(Vec3d.ofCenter(pos)))
      );
   }

   private double scoreTrapCandidate(BlockPos pos, AutoTrap.PredictedTargetState predicted, Vec3d center, Vec3d forward) {
      double score = 0.0;
      Vec3d blockCenter = Vec3d.ofCenter(pos);
      if (forward.lengthSquared() > 0.0) {
         Vec3d toBlock = new Vec3d(blockCenter.x - center.x, 0.0, blockCenter.z - center.z);
         double len = Math.sqrt(horizontalLengthSqr(toBlock));
         if (len > 1.0E-5) {
            double dot = (toBlock.x * forward.x + toBlock.z * forward.z) / len;
            score += dot * 120.0;
            if (dot > 0.35) {
               score += 180.0;
            }

            if (dot < -0.25) {
               score -= 120.0;
            }
         }
      }

      if (blockCenter.y >= predicted.box.minY && blockCenter.y <= predicted.box.maxY) {
         score += 45.0;
      } else if (blockCenter.y > predicted.box.maxY) {
         score += 12.0;
      } else {
         score -= 8.0;
      }

      if (predicted.pose == AutoTrap.TrapPose.FALL_FLYING || predicted.pose == AutoTrap.TrapPose.SWIMMING || predicted.pose == AutoTrap.TrapPose.CRAWLING) {
         score += blockCenter.y >= predicted.box.minY && blockCenter.y <= predicted.box.maxY ? 35.0 : 0.0;
      }

      return score;
   }

   private Direction primaryHorizontalDirection(Vec3d movement) {
      if (horizontalLengthSqr(movement) <= 1.0000000000000002E-10) {
         return null;
      } else {
         return Math.abs(movement.x) >= Math.abs(movement.z)
            ? (movement.x >= 0.0 ? Direction.EAST : Direction.WEST)
            : (movement.z >= 0.0 ? Direction.SOUTH : Direction.NORTH);
      }
   }

   private Vec3d horizontalUnit(Vec3d movement) {
      double length = Math.sqrt(horizontalLengthSqr(movement));
      return length <= 1.0E-5 ? Vec3d.ZERO : new Vec3d(movement.x / length, 0.0, movement.z / length);
   }

   private static double horizontalLengthSqr(Vec3d vec) {
      return vec.x * vec.x + vec.z * vec.z;
   }

   private static Vec3d boxCenter(Box box) {
      return new Vec3d((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
   }

   private static int floorMin(double value) {
      return MathHelper.floor(value + 1.0E-4);
   }

   private static int floorMax(double value) {
      return MathHelper.floor(value - 1.0E-4);
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.render.get() && this.target != null) {
         this.draw(event);
      }
   }

   private void draw(Render3DEvent event) {
      long now = System.currentTimeMillis();
      double fadeMs = this.fadeTime.get() * 1000.0;
      boolean drawDebugPrediction = this.debugPrediction.get() && !this.debugElytraPath.isEmpty();
      if (!this.renderLastPlacedBlock.isEmpty() || !this.renderLastRemovedBlock.isEmpty() || drawDebugPrediction) {
         ShapeMode shape = this.shapeMode.get();
         Color fillColor = this.sideColor.get();
         Color lineCol = this.lineColor.get();

         for (Entry<BlockPos, Long> entry : this.renderLastPlacedBlock.entrySet()) {
            double elapsed = now - entry.getValue();
            if (!(fadeMs > 0.0) || !(elapsed > fadeMs)) {
               double timeCompletion = fadeMs > 0.0 ? elapsed / fadeMs : 0.0;
               double alpha = 1.0 - timeCompletion;
               this.drawBlock(event, entry.getKey(), scaleAlpha(fillColor, alpha), scaleAlpha(lineCol, alpha), shape);
            }
         }

         for (Entry<BlockPos, Long> entryx : this.renderLastRemovedBlock.entrySet()) {
            double elapsed = now - entryx.getValue();
            if (!(fadeMs > 0.0) || !(elapsed > fadeMs)) {
               double timeCompletion = fadeMs > 0.0 ? elapsed / fadeMs : 0.0;
               double alpha = 1.0 - timeCompletion;
               this.drawBlock(event, entryx.getKey(), scaleAlpha(fillColor, alpha), scaleAlpha(lineCol, alpha), shape);
            }
         }

         if (drawDebugPrediction) {
            this.drawDebugPrediction(event);
         }
      }
   }

   private void drawDebugPrediction(Render3DEvent event) {
      Color pathColor = new Color(0, 213, 255, 170);
      Color boxColor = new Color(255, 0, 128, 170);
      Color pointColor = new Color(0, 213, 255, 68);

      for (int i = 1; i < this.debugElytraPath.size(); i++) {
         Vec3d a = this.debugElytraPath.get(i - 1).add(0.0, 0.3, 0.0);
         Vec3d b = this.debugElytraPath.get(i).add(0.0, 0.3, 0.0);
         event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, pathColor);
      }

      for (Vec3d point : this.debugElytraPath) {
         Vec3d c = point.add(0.0, 0.3, 0.0);
         Box marker = new Box(c.x - 0.06, c.y - 0.06, c.z - 0.06, c.x + 0.06, c.y + 0.06, c.z + 0.06);
         event.renderer.box(marker, pointColor, pathColor, ShapeMode.Both, 0);
      }

      if (this.debugElytraBox != null) {
         event.renderer.box(this.debugElytraBox, new Color(0, 0, 0, 0), boxColor, ShapeMode.Lines, 0);
      }
   }

   @Override
   public JsonObject writeRemoteRender() {
      if (this.render.get() && this.target != null && (!this.renderLastPlacedBlock.isEmpty() || !this.renderLastRemovedBlock.isEmpty())) {
         long now = System.currentTimeMillis();
         long fadeMs = this.fadeTime.get() > 0.0 ? (long)(this.fadeTime.get() * 1000.0) : 2305843009213693951L;
         JsonObject state = new JsonObject();
         state.add("placed", PbFx.posTimes(this.renderLastPlacedBlock, now, fadeMs, 48));
         state.add("removed", PbFx.posTimes(this.renderLastRemovedBlock, now, fadeMs, 48));
         return state;
      } else {
         return null;
      }
   }

   @Override
   public void readRemoteRender(JsonObject state, PbFx.Clock clock) {
      PbFx.readPosTimes(state.get("placed"), clock, this.renderLastPlacedBlock);
      PbFx.readPosTimes(state.get("removed"), clock, this.renderLastRemovedBlock);
      this.clearDebugPrediction();
   }

   @Override
   public void drawRemote3D(Render3DEvent event) {
      if (this.render.get()) {
         this.draw(event);
      }
   }

   @Override
   public void clearRemote() {
      this.renderLastPlacedBlock.clear();
      this.renderLastRemovedBlock.clear();
   }

   private void drawBlock(Render3DEvent event, BlockPos pos, Color side, Color line, ShapeMode shape) {
      event.renderer.box(pos, side, line, shape, 0);
   }

   private boolean isIgnoredCrawlingTarget(PlayerEntity player) {
      return this.ignoreCrawled.get() && player != null && player.isCrawling() && !player.isGliding();
   }

   private boolean isBadTarget(PlayerEntity player, double range) {
      if (player != null && this.mc.player != null && this.mc.world != null) {
         if (player.isDead() || player.getHealth() <= 0.0F) {
            return true;
         } else {
            return this.mc.player.squaredDistanceTo(player) > range * range ? true : !player.isAlive();
         }
      } else {
         return true;
      }
   }

   private PlayerEntity findTrapTarget() {
      if (this.mc.player != null && this.mc.world != null) {
         List<PlayerEntity> candidates = new ArrayList<>();

         for (PlayerEntity player : this.mc.world.getPlayers()) {
            if (player != this.mc.player
               && !player.isDead()
               && !(player.getHealth() <= 0.0F)
               && !(this.mc.player.squaredDistanceTo(player) > 196.0)
               && Friends.get().shouldAttack(player)) {
               GameMode gameMode = EntityUtils.getGameMode(player);
               if ((gameMode == null || gameMode == GameMode.SURVIVAL) && !this.isIgnoredCrawlingTarget(player)) {
                  candidates.add(player);
               }
            }
         }

         if (candidates.isEmpty()) {
            return null;
         } else {
            candidates.sort(Comparator.comparingDouble(p -> this.mc.player.squaredDistanceTo(p)));
            return candidates.get(0);
         }
      } else {
         return null;
      }
   }

   private PlayerEntity findLogoutTarget() {
      if (this.mc.player != null && this.mc.world != null) {
         LogoutSpots module = Modules.get().get(LogoutSpots.class);
         if (module != null && module.isActive()) {
            Vec3d playerPos = this.mc.player.getEntityPos();
            double bestDistSq = 196.0;
            LogoutSpots.LogoutEntry bestEntry = null;

            for (LogoutSpots.LogoutEntry entry : module.getLogoutSpots().values()) {
               if (this.mc.world.getRegistryKey().equals(entry.dimension()) && entry.ghost() != null && entry.ghost().isAlive()) {
                  double distSq = playerPos.squaredDistanceTo(entry.pos());
                  if (distSq < bestDistSq) {
                     bestDistSq = distSq;
                     bestEntry = entry;
                  }
               }
            }

            return bestEntry != null ? bestEntry.ghost() : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private boolean isRebreakTarget(BlockPos pos) {
      SilentMine sm = SilentMine.get();
      if (sm != null && sm.isActive()) {
         BlockPos rb = sm.getRebreakBlockPos();
         BlockPos db = sm.getDelayedDestroyBlockPos();
         return rb != null && rb.equals(pos) || db != null && db.equals(pos);
      } else {
         return false;
      }
   }

   private double fadeTimeMs() {
      return this.fadeTime.get() * 1000.0;
   }

   private double placeRange() {
      return AntiCheatConfig.get().blockInteractRange.get();
   }

   private String targetName() {
      return this.target != null ? this.target.getName().getString() : "none";
   }

   private void dbg(String fmt, Object... args) {
      if (this.debug.get()) {
         String msg = String.format(Locale.US, fmt, args);
         if (!msg.equals(this.lastDebugMsg)) {
            this.lastDebugMsg = msg;
            this.info("%s", new Object[]{msg});
         }
      }
   }

   private static Color scaleAlpha(Color color, double t) {
      int a = MathHelper.clamp((int)Math.round(color.a * t), 0, 255);
      return new Color(color.r, color.g, color.b, a);
   }

   private static Vec3d clampClosestPoint(Vec3d eyePos, Box box) {
      double x = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
      double y = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
      double z = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
      return new Vec3d(x, y, z);
   }

   public static enum BottomMode {
      SINGLE,
      PLATFORM,
      FULL,
      NONE;
   }

   private record CollisionMove(Vec3d movement, Box box, boolean collidedX, boolean collidedY, boolean collidedZ) {
   }

   private record ElytraSampleScore(double score, int placeableCount, int frontCount, double bestReachSq) {
   }

   private record PredictedTargetState(
      AutoTrap.TrapPose pose, Vec3d startPos, Vec3d pos, Vec3d velocity, Box box, Vec3d predictedTravel, int leadTicks, double uncertainty, List<Vec3d> path
   ) {
   }

   public static enum RenderShape {
      BOTH,
      LINES,
      SIDES;
   }

   public static enum TopMode {
      FULL,
      TOP,
      FACE,
      NONE;
   }

   private static enum TrapPose {
      STANDING,
      CROUCHING,
      CRAWLING,
      SWIMMING,
      FALL_FLYING;
   }
}
