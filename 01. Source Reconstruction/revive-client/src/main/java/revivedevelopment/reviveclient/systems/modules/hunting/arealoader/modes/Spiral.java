package revivedevelopment.reviveclient.systems.modules.hunting.arealoader.modes;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.systems.modules.hunting.HuntingUtils;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.AreaLoader;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.AreaLoaderMode;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.AreaLoaderModes;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.Rotations;

public class Spiral extends AreaLoaderMode {
   private Spiral.PathingDataSpiral pd;
   private boolean goingToStart = true;
   private long startTime;
   private BlockPos nextCornerTarget = null;
   private boolean recovering = false;
   private BlockPos recoveryTarget = null;
   private BlockPos lastTickPos = null;
   private String lastTickDimension = null;
   private boolean teleportPaused = false;
   private int teleportStableTicks = 0;
   private double teleportJumpDistance = 0.0;
   private float supposedYaw = Float.NaN;
   private double lastOffTrackDistance = 0.0;
   private int overshootGrowTicks = 0;
   private int headingDriftTicks = 0;

   public Spiral(AreaLoader searchArea) {
      super(searchArea, AreaLoaderModes.Spiral);
   }

   @Override
   public void onActivate() {
      this.startTime = System.nanoTime();
      this.goingToStart = true;
      this.nextCornerTarget = null;
      this.recovering = false;
      this.recoveryTarget = null;
      this.lastTickPos = null;
      this.lastTickDimension = null;
      this.teleportPaused = false;
      this.teleportStableTicks = 0;
      this.teleportJumpDistance = 0.0;
      this.resetDriftDetectors();
      File file = this.getJsonFile(this.saveFileName());
      if (file != null && file.exists()) {
         try {
            this.debugInfo("Loading save file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
            FileReader reader = new FileReader(file);
            this.pd = (Spiral.PathingDataSpiral)GSON.fromJson(reader, Spiral.PathingDataSpiral.class);
            reader.close();
            boolean corrupted = false;
            String corruptionReason = "";
            if (this.pd == null) {
               corrupted = true;
               corruptionReason = "pd is null";
            } else if (this.pd.spiralOrigin == null && this.pd.initialPos == null) {
               corrupted = true;
               corruptionReason = "both spiralOrigin and initialPos are null";
            } else if (this.pd.currPos == null) {
               corrupted = true;
               corruptionReason = "currPos is null";
            } else if (this.pd.spiralOrigin != null && this.pd.spiralWidth == 0 && this.pd.spiralHeight == 0) {
               double distFromOrigin = dist3D(this.pd.currPos, this.pd.spiralOrigin);
               if (distFromOrigin > 1000.0) {
                  corrupted = true;
                  corruptionReason = String.format("spiralWidth/Height are 0 but currPos is %.0f blocks from origin - save may have been reset", distFromOrigin);
               }
            }

            if (corrupted) {
               ChatUtils.error("SAVE FILE APPEARS CORRUPTED: " + corruptionReason);
               ChatUtils.error("NOT starting fresh to protect your progress. Please fix the save file manually.");
               ChatUtils.error("Save file location: " + file.getAbsolutePath());
               ChatUtils.error("Disabling module to prevent data loss.");
               this.pd = null;
               this.disable();
               return;
            }

            if (this.pd.spiralOrigin == null) {
               this.pd.spiralOrigin = this.pd.initialPos;
               this.debugInfo("Loaded legacy save - set origin to: " + this.pd.spiralOrigin.toShortString());
            }

            this.debugInfo("Loaded saved path successfully. Origin: " + this.pd.spiralOrigin.toShortString() + ", Current: " + this.pd.currPos.toShortString());
            this.debugInfo(
               "Spiral state: width=%d, height=%d, yaw=%.1f, mainPath=%b",
               new Object[]{this.pd.spiralWidth, this.pd.spiralHeight, this.pd.yawDirection, this.pd.mainPath}
            );
         } catch (Exception var7) {
            ChatUtils.error("Failed to load saved path: " + var7.getMessage());
            ChatUtils.error("NOT starting fresh to protect your progress. Please check the save file.");
            ChatUtils.error("Save file location: " + file.getAbsolutePath());
            var7.printStackTrace();
            this.pd = null;
            this.disable();
            return;
         }
      } else {
         if (file == null) {
            this.debugInfo("Error: Cannot create save file path. Check save-name setting.");
         } else {
            this.debugInfo("Spiral started from origin (no save file found at: " + file.getAbsolutePath() + ")");
         }

         this.pd = new Spiral.PathingDataSpiral(this.mc.player.getBlockPos(), this.mc.player.getBlockPos(), -90.0F, true, 0, 0);
         this.goingToStart = false;
      }

      this.initializeFlightModes();
   }

   @Override
   public void onDeactivate() {
      super.onDeactivate();
      super.saveToJson(this.goingToStart, this.pd);
   }

   @Override
   public void resetState() {
      this.pd = null;
      this.goingToStart = true;
      this.nextCornerTarget = null;
      this.recovering = false;
      this.recoveryTarget = null;
      this.lastTickPos = null;
      this.lastTickDimension = null;
      this.teleportPaused = false;
      this.teleportStableTicks = 0;
      this.teleportJumpDistance = 0.0;
      this.resetDriftDetectors();
      this.debugInfo("Spiral state reset. Next activation will start fresh.");
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.mc.player != null && this.mc.world != null && this.pd != null) {
         BlockPos currentPos = this.mc.player.getBlockPos();
         String currentTickDim = this.mc.world.getRegistryKey().getValue().toString();
         if (!currentTickDim.equals(this.lastTickDimension)) {
            this.lastTickDimension = currentTickDim;
            this.lastTickPos = null;
         }

         double tickDistance = this.lastTickPos != null ? dist3D(this.lastTickPos, currentPos) : 0.0;
         if (this.lastTickPos != null && !this.goingToStart && !this.recovering && !this.teleportPaused && tickDistance > 100.0) {
            this.debugInfo("TELEPORT DETECTED! Moved %.0f blocks in one tick. Pausing spiral to protect state.", new Object[]{tickDistance});
            this.debugInfo(
               "Last safe position: X=%d Z=%d. Current: X=%d Z=%d",
               new Object[]{this.lastTickPos.getX(), this.lastTickPos.getZ(), currentPos.getX(), currentPos.getZ()}
            );
            this.pd.currPos = this.lastTickPos;
            super.saveToJsonExact(this.pd);
            this.teleportPaused = true;
            this.teleportJumpDistance = tickDistance;
            this.teleportStableTicks = 0;
            HuntingUtils.setPressed(this.mc.options.forwardKey, false);
            this.mc.player.setVelocity(0.0, 0.0, 0.0);
            ChatUtils.info("Spiral PAUSED due to teleportation. State saved at last safe position.");
            if (tickDistance < 5000.0) {
               ChatUtils.info("Waiting for the position to stabilize, then auto-resuming from the saved position.");
            } else {
               ChatUtils.info("Jump too large to auto-resume. Disable and re-enable the module to resume from saved position.");
            }
         } else {
            this.lastTickPos = currentPos;
            if (this.teleportPaused) {
               HuntingUtils.setPressed(this.mc.options.forwardKey, false);
               if (tickDistance < 2.0) {
                  this.teleportStableTicks++;
               } else {
                  this.teleportStableTicks = 0;
               }

               if (this.teleportJumpDistance < 5000.0 && this.teleportStableTicks >= 100) {
                  this.teleportPaused = false;
                  this.goingToStart = true;
                  this.nextCornerTarget = null;
                  this.resetDriftDetectors();
                  ChatUtils.info(
                     "Position stable again. Auto-resuming spiral: navigating back to saved position X=%d Z=%d.",
                     this.pd.currPos.getX(),
                     this.pd.currPos.getZ()
                  );
               }
            } else {
               if (System.nanoTime() - this.startTime > 6.0E11) {
                  this.startTime = System.nanoTime();
                  super.saveToJson(this.goingToStart, this.pd);
               }

               if (System.nanoTime() < this.paused) {
                  HuntingUtils.setPressed(this.mc.options.forwardKey, false);
               } else if (this.usingBaritoneElytra()) {
                  this.onTickBaritone();
               } else {
                  this.onTickOverworld();
               }
            }
         }
      }
   }

   private void onTickOverworld() {
      if (!this.usingBaritoneElytra()) {
         if (this.goingToStart) {
            this.updateGoalWaypoint(this.pd.currPos);
            if (this.distToXZ(this.pd.currPos.getX(), this.pd.currPos.getZ()) < 5.0) {
               this.goingToStart = false;
               this.nextCornerTarget = null;
               this.mc.player.setVelocity(0.0, 0.0, 0.0);
               this.resetDriftDetectors();
            } else {
               this.mc.player.setYaw((float)Rotations.getYaw(this.pd.currPos));
               HuntingUtils.setPressed(this.mc.options.forwardKey, true);
            }
         } else if (this.recovering) {
            if (this.recoveryTarget == null) {
               this.recovering = false;
            } else if (this.distToXZ(this.recoveryTarget.getX(), this.recoveryTarget.getZ()) < 20.0) {
               this.debugInfo("Recovery complete. Resuming spiral at X=%d Z=%d", new Object[]{this.recoveryTarget.getX(), this.recoveryTarget.getZ()});
               this.recovering = false;
               this.recoveryTarget = null;
               this.nextCornerTarget = null;
               this.mc.player.setVelocity(0.0, 0.0, 0.0);
               this.resetDriftDetectors();
            } else {
               this.updateGoalWaypoint(this.recoveryTarget);
               this.mc.player.setYaw((float)Rotations.getYaw(this.recoveryTarget));
               HuntingUtils.setPressed(this.mc.options.forwardKey, true);
            }
         } else {
            int blockGap = 16 * this.searchArea.rowGap.get();
            if (this.nextCornerTarget == null) {
               this.nextCornerTarget = this.calculateExactCornerTarget(blockGap);
            }

            this.updateGoalWaypoint(this.nextCornerTarget);
            BlockPos expectedPos = this.calculateExpectedPosition(blockGap);
            double offTrackDistance = this.getOffTrackDistance(expectedPos);
            if (offTrackDistance > this.searchArea.spiralMaxOffTrackDistance.get().intValue()) {
               this.debugInfo(
                  "Off-track by %.0f blocks! Expected near X=%d Z=%d. Saving state and initiating recovery...",
                  new Object[]{offTrackDistance, expectedPos.getX(), expectedPos.getZ()}
               );
               this.startRecovery(expectedPos);
            } else if (this.hasReachedCorner(this.nextCornerTarget)) {
               BlockPos snappedCorner = this.nextCornerTarget;
               this.pd.yawDirection = this.normalizeYaw(this.pd.yawDirection + 90.0F);
               this.pd.initialPos = new BlockPos(snappedCorner.getX(), this.pd.initialPos.getY(), snappedCorner.getZ());
               if (this.pd.mainPath) {
                  this.pd.spiralWidth += blockGap;
                  this.pd.mainPath = false;
               } else {
                  this.pd.spiralHeight += blockGap;
                  this.pd.mainPath = true;
               }

               this.mc.player.setVelocity(0.0, 0.0, 0.0);
               this.mc.player.setYaw(this.pd.yawDirection);
               this.resetDriftDetectors();
               this.supposedYaw = this.pd.yawDirection;
               this.pd.currPos = snappedCorner;
               this.nextCornerTarget = null;
               super.saveToJsonExact(this.pd);
               this.debugInfo(
                  "Spiral: Turned corner at X=%d Z=%d (yaw=%.1f, mainPath=%b, width=%d, height=%d)",
                  new Object[]{snappedCorner.getX(), snappedCorner.getZ(), this.pd.yawDirection, this.pd.mainPath, this.pd.spiralWidth, this.pd.spiralHeight}
               );
            } else {
               HuntingUtils.setPressed(this.mc.options.forwardKey, true);
               float yawToTarget = (float)Rotations.getYaw(this.nextCornerTarget);
               this.mc.player.setYaw(yawToTarget);
               this.supposedYaw = this.normalizeYaw(yawToTarget);
               this.checkTurnDrift(offTrackDistance, expectedPos);
            }
         }
      }
   }

   private BlockPos calculateExactCornerTarget(int blockGap) {
      float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
      int targetX;
      int targetZ;
      if (this.pd.mainPath) {
         targetZ = this.pd.initialPos.getZ();
         if (normalizedYaw >= 225.0F && normalizedYaw < 315.0F) {
            targetX = this.pd.initialPos.getX() + blockGap + this.pd.spiralWidth;
         } else {
            targetX = this.pd.initialPos.getX() - (blockGap + this.pd.spiralWidth);
         }
      } else {
         targetX = this.pd.initialPos.getX();
         if (!(normalizedYaw >= 315.0F) && !(normalizedYaw < 45.0F)) {
            targetZ = this.pd.initialPos.getZ() - (blockGap + this.pd.spiralHeight);
         } else {
            targetZ = this.pd.initialPos.getZ() + blockGap + this.pd.spiralHeight;
         }
      }

      return new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
   }

   private boolean hasReachedCorner(BlockPos corner) {
      double distToCorner = this.distToXZ(corner.getX(), corner.getZ());
      return distToCorner < this.searchArea.spiralCornerReachDistance.get().intValue() ? true : this.hasPassedCorner(corner);
   }

   private boolean hasPassedCorner(BlockPos corner) {
      if (this.pd.mainPath) {
         boolean goingPositive = corner.getX() >= this.pd.initialPos.getX();
         return goingPositive ? this.mc.player.getX() >= corner.getX() : this.mc.player.getX() <= corner.getX();
      } else {
         boolean goingPositive = corner.getZ() >= this.pd.initialPos.getZ();
         return goingPositive ? this.mc.player.getZ() >= corner.getZ() : this.mc.player.getZ() <= corner.getZ();
      }
   }

   private BlockPos calculateExpectedPosition(int blockGap) {
      float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
      int playerX = this.mc.player.getBlockX();
      int playerZ = this.mc.player.getBlockZ();
      if (this.pd.mainPath) {
         int expectedZ = this.pd.initialPos.getZ();
         int targetX;
         if (normalizedYaw >= 225.0F && normalizedYaw < 315.0F) {
            targetX = this.pd.initialPos.getX() + blockGap + this.pd.spiralWidth;
         } else {
            targetX = this.pd.initialPos.getX() - (blockGap + this.pd.spiralWidth);
         }

         int expectedX = this.clamp(playerX, Math.min(this.pd.initialPos.getX(), targetX), Math.max(this.pd.initialPos.getX(), targetX));
         return new BlockPos(expectedX, this.mc.player.getBlockY(), expectedZ);
      } else {
         int expectedX = this.pd.initialPos.getX();
         int targetZ;
         if (!(normalizedYaw >= 315.0F) && !(normalizedYaw < 45.0F)) {
            targetZ = this.pd.initialPos.getZ() - (blockGap + this.pd.spiralHeight);
         } else {
            targetZ = this.pd.initialPos.getZ() + blockGap + this.pd.spiralHeight;
         }

         int expectedZ = this.clamp(playerZ, Math.min(this.pd.initialPos.getZ(), targetZ), Math.max(this.pd.initialPos.getZ(), targetZ));
         return new BlockPos(expectedX, this.mc.player.getBlockY(), expectedZ);
      }
   }

   private double getOffTrackDistance(BlockPos expectedPos) {
      return this.pd.mainPath ? Math.abs(this.mc.player.getZ() - expectedPos.getZ()) : Math.abs(this.mc.player.getX() - expectedPos.getX());
   }

   private void resetDriftDetectors() {
      this.supposedYaw = Float.NaN;
      this.lastOffTrackDistance = 0.0;
      this.overshootGrowTicks = 0;
      this.headingDriftTicks = 0;
   }

   private void startRecovery(BlockPos target) {
      this.pd.currPos = target;
      super.saveToJsonExact(this.pd);
      ChatUtils.info("Saved recovery point at X=%d Z=%d. Navigating back to resume spiral.", target.getX(), target.getZ());
      this.recovering = true;
      this.recoveryTarget = target;
      this.nextCornerTarget = null;
      this.mc.player.setVelocity(0.0, 0.0, 0.0);
      this.resetDriftDetectors();
   }

   private void checkTurnDrift(double offTrackDistance, BlockPos expectedPos) {
      double overshootTrip = Math.max(60, this.searchArea.spiralCornerReachDistance.get() * 3);
      if (offTrackDistance > overshootTrip && offTrackDistance > this.lastOffTrackDistance + 0.25) {
         this.overshootGrowTicks++;
      } else {
         this.overshootGrowTicks = 0;
      }

      this.lastOffTrackDistance = offTrackDistance;
      Vec3d vel = this.mc.player.getVelocity();
      double horizontalSpeedSq = vel.x * vel.x + vel.z * vel.z;
      if (!Float.isNaN(this.supposedYaw) && horizontalSpeedSq > 0.04) {
         float motionYaw = this.normalizeYaw((float)Math.toDegrees(Math.atan2(-vel.x, vel.z)));
         if (this.yawDistance(motionYaw, this.supposedYaw) > 45.0F) {
            this.headingDriftTicks++;
         } else {
            this.headingDriftTicks = 0;
         }
      } else {
         this.headingDriftTicks = 0;
      }

      if (this.overshootGrowTicks >= 20) {
         this.debugInfo(
            "Overdrift detected: off-track %.0f blocks and growing (supposed yaw %.0f). Recovering to the missed corner.",
            new Object[]{offTrackDistance, this.supposedYaw}
         );
         this.startRecovery(expectedPos);
      } else if (this.headingDriftTicks >= 60) {
         this.debugInfo(
            "Heading drift detected: motion is >%.0f° off the supposed yaw %.0f. Recovering to the spiral path.", new Object[]{45.0F, this.supposedYaw}
         );
         this.startRecovery(expectedPos);
      }
   }

   private float yawDistance(float a, float b) {
      float d = Math.abs(this.normalizeYaw(a) - this.normalizeYaw(b)) % 360.0F;
      return d > 180.0F ? 360.0F - d : d;
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double distanceXZ(int x1, int z1, int x2, int z2) {
      double dx = x2 - x1;
      double dz = z2 - z1;
      return Math.sqrt(dx * dx + dz * dz);
   }

   private void onTickBaritone() {
      if (this.usingBaritoneElytra()) {
         int blockGap = 16 * this.searchArea.rowGap.get();
         int reachDist = this.searchArea.netherWaypointReachDistance.get();
         if (this.goingToStart) {
            double distToSaved = this.distToXZ(this.pd.currPos.getX(), this.pd.currPos.getZ());
            if (distToSaved < reachDist) {
               this.goingToStart = false;
               this.nextCornerTarget = null;
               this.debugInfo(
                  "Reached saved position. Resuming spiral at yaw=%.1f, mainPath=%b, width=%d, height=%d",
                  new Object[]{this.pd.yawDirection, this.pd.mainPath, this.pd.spiralWidth, this.pd.spiralHeight}
               );
            } else if (this.needsNewGoal()) {
               this.setBaritoneGoal(this.pd.currPos);
            }
         } else if (this.nextCornerTarget == null) {
            this.calculateNextCorner(blockGap);
            if (this.nextCornerTarget != null) {
               this.setBaritoneGoal(this.nextCornerTarget);
            }
         } else {
            double distToCorner = this.distToXZ(this.nextCornerTarget.getX(), this.nextCornerTarget.getZ());
            if (distToCorner < reachDist || this.hasPassedCorner(this.nextCornerTarget)) {
               BlockPos snappedCorner = this.nextCornerTarget;
               this.debugInfo(
                  "Spiral: Reached corner at X=%d Z=%d, yaw was %.1f", new Object[]{snappedCorner.getX(), snappedCorner.getZ(), this.pd.yawDirection}
               );
               this.pd.yawDirection = this.normalizeYaw(this.pd.yawDirection + 90.0F);
               this.pd.initialPos = new BlockPos(snappedCorner.getX(), this.pd.initialPos.getY(), snappedCorner.getZ());
               if (this.pd.mainPath) {
                  this.pd.spiralWidth += blockGap;
                  this.pd.mainPath = false;
               } else {
                  this.pd.spiralHeight += blockGap;
                  this.pd.mainPath = true;
               }

               this.pd.currPos = snappedCorner;
               this.nextCornerTarget = null;
               super.saveToJsonExact(this.pd);
               this.debugInfo(
                  "Spiral: Turned to yaw=%.1f, mainPath=%b, width=%d, height=%d (saved)",
                  new Object[]{this.pd.yawDirection, this.pd.mainPath, this.pd.spiralWidth, this.pd.spiralHeight}
               );
            } else if (this.needsNewGoal()) {
               this.debugInfo("Spiral: Baritone needs new goal, resetting to corner target");
               this.setBaritoneGoal(this.nextCornerTarget);
            }
         }
      }
   }

   private float normalizeYaw(float yaw) {
      yaw %= 360.0F;
      if (yaw < 0.0F) {
         yaw += 360.0F;
      }

      return yaw;
   }

   private void calculateNextCorner(int blockGap) {
      float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
      int targetX;
      int targetZ;
      if (this.pd.mainPath) {
         int xDirection;
         if (normalizedYaw >= 225.0F && normalizedYaw < 315.0F) {
            xDirection = 1;
         } else if (normalizedYaw >= 45.0F && normalizedYaw < 135.0F) {
            xDirection = -1;
         } else {
            xDirection = 1;
         }

         targetX = this.pd.initialPos.getX() + xDirection * (blockGap + this.pd.spiralWidth);
         targetZ = this.pd.initialPos.getZ();
      } else {
         int zDirection;
         if (normalizedYaw >= 315.0F || normalizedYaw < 45.0F) {
            zDirection = 1;
         } else if (normalizedYaw >= 135.0F && normalizedYaw < 225.0F) {
            zDirection = -1;
         } else {
            zDirection = 1;
         }

         targetX = this.pd.initialPos.getX();
         targetZ = this.pd.initialPos.getZ() + zDirection * (blockGap + this.pd.spiralHeight);
      }

      this.nextCornerTarget = new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
      this.updateGoalWaypoint(this.nextCornerTarget);
      this.debugInfo("Spiral: Next corner target at X=%d, Z=%d (yaw=%.1f, mainPath=%b)", new Object[]{targetX, targetZ, normalizedYaw, this.pd.mainPath});
   }

   private List<Spiral.SpiralLegInfo> generateSpiralLegs(int originX, int originZ, int blockGap, int maxLegs) {
      List<Spiral.SpiralLegInfo> legs = new ArrayList<>();
      int x = originX;
      int z = originZ;
      float yaw = 270.0F;
      boolean mainPath = true;
      int spiralWidth = 0;
      int spiralHeight = 0;

      for (int legNum = 0; legNum < maxLegs; legNum++) {
         float normYaw = this.normalizeYaw(yaw);
         int endX;
         int endZ;
         if (mainPath) {
            endZ = z;
            if (normYaw >= 225.0F && normYaw < 315.0F) {
               endX = x + blockGap + spiralWidth;
            } else {
               endX = x - (blockGap + spiralWidth);
            }
         } else {
            endX = x;
            if (!(normYaw >= 315.0F) && !(normYaw < 45.0F)) {
               endZ = z - (blockGap + spiralHeight);
            } else {
               endZ = z + blockGap + spiralHeight;
            }
         }

         legs.add(new Spiral.SpiralLegInfo(x, z, endX, endZ, spiralWidth, spiralHeight, yaw, mainPath, legNum));
         x = endX;
         z = endZ;
         yaw = this.normalizeYaw(yaw + 90.0F);
         if (mainPath) {
            spiralWidth += blockGap;
            mainPath = false;
         } else {
            spiralHeight += blockGap;
            mainPath = true;
         }
      }

      return legs;
   }

   private Spiral.SpiralLegInfo findClosestLeg(int originX, int originZ, int targetX, int targetZ, int blockGap) {
      int dxFromOrigin = Math.abs(targetX - originX);
      int dzFromOrigin = Math.abs(targetZ - originZ);
      int maxCoordDist = Math.max(dxFromOrigin, dzFromOrigin);
      int estimatedLegs = (maxCoordDist / blockGap + 1) * 4 + 500;
      estimatedLegs = Math.max(500, Math.min(estimatedLegs, 500000));
      this.debugInfo("Search params: origin=(%d,%d) target=(%d,%d) gap=%d", new Object[]{originX, originZ, targetX, targetZ, blockGap});
      this.debugInfo("Generating %d spiral legs (max coord dist = %d)", new Object[]{estimatedLegs, maxCoordDist});
      List<Spiral.SpiralLegInfo> legs = this.generateSpiralLegs(originX, originZ, blockGap, estimatedLegs);
      double tolerance = blockGap * 1.5;
      List<Spiral.SpiralLegInfo> candidates = new ArrayList<>();

      for (Spiral.SpiralLegInfo leg : legs) {
         double dist = leg.distanceToLeg(targetX, targetZ);
         if (dist <= tolerance) {
            candidates.add(leg);
         }
      }

      this.debugInfo("Found %d candidate legs within tolerance %.0f blocks", new Object[]{candidates.size(), tolerance});
      if (candidates.isEmpty()) {
         Spiral.SpiralLegInfo closest = null;
         double closestDist = Double.MAX_VALUE;

         for (Spiral.SpiralLegInfo legx : legs) {
            double dist = legx.distanceToLeg(targetX, targetZ);
            if (dist < closestDist) {
               closestDist = dist;
               closest = legx;
            }
         }

         this.debugInfo(
            "No candidates within tolerance, using closest leg #%d at distance %.0f", new Object[]{closest != null ? closest.legNumber : -1, closestDist}
         );
         return closest;
      } else {
         List<Spiral.SpiralLegInfo> notPastEnd = new ArrayList<>();

         for (Spiral.SpiralLegInfo legxx : candidates) {
            int[] proj = legxx.projectOntoLeg(targetX, targetZ);
            double legLength = distanceXZ(legxx.startX, legxx.startZ, legxx.endX, legxx.endZ);
            double projDist = distanceXZ(legxx.startX, legxx.startZ, proj[0], proj[1]);
            if (projDist < legLength * 0.95) {
               notPastEnd.add(legxx);
            }
         }

         List<Spiral.SpiralLegInfo> pool = notPastEnd.isEmpty() ? candidates : notPastEnd;
         Spiral.SpiralLegInfo best = null;

         for (Spiral.SpiralLegInfo legxxx : pool) {
            if (best == null || legxxx.legNumber > best.legNumber) {
               best = legxxx;
            }
         }

         if (best != null) {
            double distToBest = best.distanceToLeg(targetX, targetZ);
            double closestPossible = Double.MAX_VALUE;

            for (Spiral.SpiralLegInfo legxxxx : legs) {
               double dist = legxxxx.distanceToLeg(targetX, targetZ);
               if (dist < closestPossible) {
                  closestPossible = dist;
               }
            }

            if (distToBest > closestPossible * 2.0 + 50.0) {
               for (Spiral.SpiralLegInfo legxxxxx : legs) {
                  if (legxxxxx.distanceToLeg(targetX, targetZ) <= closestPossible + 10.0) {
                     this.debugInfo(
                        "Selected Leg #%d (closest): (%d,%d)->(%d,%d) dist=%.0f",
                        new Object[]{
                           legxxxxx.legNumber, legxxxxx.startX, legxxxxx.startZ, legxxxxx.endX, legxxxxx.endZ, legxxxxx.distanceToLeg(targetX, targetZ)
                        }
                     );
                     return legxxxxx;
                  }
               }
            }
         }

         if (best != null) {
            this.debugInfo(
               "Selected Leg #%d (best candidate): (%d,%d)->(%d,%d) dist=%.0f",
               new Object[]{best.legNumber, best.startX, best.startZ, best.endX, best.endZ, best.distanceToLeg(targetX, targetZ)}
            );
         }

         return best;
      }
   }

   public boolean validateManualCoordinates(int originX, int originZ, int targetX, int targetZ, int blockGap, boolean apply) {
      this.debugInfo("Finding spiral leg for position (%d, %d) with origin (%d, %d), gap=%d blocks", new Object[]{targetX, targetZ, originX, originZ, blockGap});
      Spiral.SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
      if (closestLeg == null) {
         ChatUtils.error("Could not calculate spiral path. Check your settings.");
         return false;
      } else {
         double distanceToLeg = closestLeg.distanceToLeg(targetX, targetZ);
         int[] projected = closestLeg.projectOntoLeg(targetX, targetZ);
         int expectedEndX;
         int expectedEndZ;
         if (closestLeg.mainPath) {
            expectedEndZ = closestLeg.startZ;
            float normYaw = this.normalizeYaw(closestLeg.yaw);
            if (normYaw >= 225.0F && normYaw < 315.0F) {
               expectedEndX = closestLeg.startX + blockGap + closestLeg.spiralWidth;
            } else {
               expectedEndX = closestLeg.startX - (blockGap + closestLeg.spiralWidth);
            }
         } else {
            expectedEndX = closestLeg.startX;
            float normYaw = this.normalizeYaw(closestLeg.yaw);
            if (!(normYaw >= 315.0F) && !(normYaw < 45.0F)) {
               expectedEndZ = closestLeg.startZ - (blockGap + closestLeg.spiralHeight);
            } else {
               expectedEndZ = closestLeg.startZ + blockGap + closestLeg.spiralHeight;
            }
         }

         this.debugInfo("Found Leg #%d:", new Object[]{closestLeg.legNumber});
         this.debugInfo("  Start: (%d, %d)", new Object[]{closestLeg.startX, closestLeg.startZ});
         this.debugInfo("  End:   (%d, %d)", new Object[]{closestLeg.endX, closestLeg.endZ});
         this.debugInfo("  Next corner will be at: (%d, %d)", new Object[]{expectedEndX, expectedEndZ});
         this.debugInfo("  Direction: yaw=%.0f (%s), mainPath=%b", new Object[]{closestLeg.yaw, this.getDirectionName(closestLeg.yaw), closestLeg.mainPath});
         this.debugInfo("  Spiral state: width=%d, height=%d", new Object[]{closestLeg.spiralWidth, closestLeg.spiralHeight});
         this.debugInfo("  Distance from path: %.0f blocks", new Object[]{distanceToLeg});
         if (distanceToLeg > blockGap / 2) {
            this.debugInfo("  Projected position onto leg: (%d, %d)", new Object[]{projected[0], projected[1]});
         }

         if (apply) {
            if (distanceToLeg > blockGap * 2) {
               ChatUtils.error("Position is too far from spiral path (%.0f blocks). Max tolerance: %d blocks.", distanceToLeg, blockGap * 2);
               ChatUtils.error("Make sure you entered the correct origin coordinates where the spiral started.");
               return false;
            } else {
               int resumeX = distanceToLeg < 50.0 ? targetX : projected[0];
               int resumeZ = distanceToLeg < 50.0 ? targetZ : projected[1];
               Spiral.PathingDataSpiral newPd = new Spiral.PathingDataSpiral(
                  new BlockPos(closestLeg.startX, 64, closestLeg.startZ),
                  new BlockPos(resumeX, 64, resumeZ),
                  closestLeg.yaw,
                  closestLeg.mainPath,
                  closestLeg.spiralWidth,
                  closestLeg.spiralHeight
               );
               newPd.spiralOrigin = new BlockPos(originX, 64, originZ);
               File file = this.getJsonFile(this.saveFileName());
               if (file == null) {
                  ChatUtils.error("Failed to get save file path.");
                  return false;
               } else {
                  try {
                     if (!file.getParentFile().exists()) {
                        file.getParentFile().mkdirs();
                     }

                     Writer writer = new FileWriter(file);
                     GSON.toJson(newPd, writer);
                     writer.flush();
                     writer.close();
                     this.debugInfo("SAVED spiral recovery state:");
                     this.debugInfo("  Origin: (%d, %d)", new Object[]{originX, originZ});
                     this.debugInfo("  Resume from: (%d, %d)", new Object[]{resumeX, resumeZ});
                     this.debugInfo("Enable the module to resume the spiral.");
                     return true;
                  } catch (Exception var18) {
                     ChatUtils.error("Failed to save: " + var18.getMessage());
                     return false;
                  }
               }
            }
         } else {
            return distanceToLeg < blockGap / 2;
         }
      }
   }

   private String getDirectionName(float yaw) {
      float norm = this.normalizeYaw(yaw);
      if (norm >= 315.0F || norm < 45.0F) {
         return "South (+Z)";
      } else if (norm >= 45.0F && norm < 135.0F) {
         return "West (-X)";
      } else {
         return norm >= 135.0F && norm < 225.0F ? "North (-Z)" : "East (+X)";
      }
   }

   public int[] snapToNearestCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
      this.debugInfo("Snapping position (%d, %d) to spiral path...", new Object[]{targetX, targetZ});
      Spiral.SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
      if (closestLeg == null) {
         ChatUtils.error("Could not calculate spiral path.");
         return null;
      } else {
         int[] projected = closestLeg.projectOntoLeg(targetX, targetZ);
         double distance = closestLeg.distanceToLeg(targetX, targetZ);
         this.debugInfo("Snapped to Leg #%d at position (%d, %d)", new Object[]{closestLeg.legNumber, projected[0], projected[1]});
         this.debugInfo("  Was %.0f blocks off the spiral path", new Object[]{distance});
         return projected;
      }
   }

   public int[] snapToNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
      Spiral.SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
      if (closestLeg == null) {
         ChatUtils.error("Could not calculate spiral path.");
         return null;
      } else {
         int nextCornerX = closestLeg.endX;
         int nextCornerZ = closestLeg.endZ;
         this.debugInfo(
            "Found Leg #%d: (%d, %d) -> (%d, %d)", new Object[]{closestLeg.legNumber, closestLeg.startX, closestLeg.startZ, closestLeg.endX, closestLeg.endZ}
         );
         this.debugInfo("Next corner: (%d, %d) going %s", new Object[]{nextCornerX, nextCornerZ, this.getDirectionName(closestLeg.yaw)});
         return new int[]{nextCornerX, nextCornerZ};
      }
   }

   public boolean applyFromNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
      Spiral.SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
      if (closestLeg == null) {
         ChatUtils.error("Could not find your position on the spiral.");
         return false;
      } else {
         int cornerX = closestLeg.endX;
         int cornerZ = closestLeg.endZ;
         float newYaw = this.normalizeYaw(closestLeg.yaw + 90.0F);
         boolean newMainPath;
         int newWidth;
         int newHeight;
         if (closestLeg.mainPath) {
            newWidth = closestLeg.spiralWidth + blockGap;
            newHeight = closestLeg.spiralHeight;
            newMainPath = false;
         } else {
            newWidth = closestLeg.spiralWidth;
            newHeight = closestLeg.spiralHeight + blockGap;
            newMainPath = true;
         }

         Spiral.PathingDataSpiral newPd = new Spiral.PathingDataSpiral(
            new BlockPos(cornerX, 64, cornerZ), new BlockPos(cornerX, 64, cornerZ), newYaw, newMainPath, newWidth, newHeight
         );
         newPd.spiralOrigin = new BlockPos(originX, 64, originZ);
         File file = this.getJsonFile(this.saveFileName());
         if (file == null) {
            ChatUtils.error("Failed to get save file path. Check that you're in a world.");
            return false;
         } else {
            try {
               if (!file.getParentFile().exists()) {
                  file.getParentFile().mkdirs();
               }

               Writer writer = new FileWriter(file);
               GSON.toJson(newPd, writer);
               writer.flush();
               writer.close();
               this.debugInfo("Go to (%d, %d) and enable the module.", new Object[]{cornerX, cornerZ});
               return true;
            } catch (Exception var16) {
               ChatUtils.error("Failed to save: " + var16.getMessage());
               return false;
            }
         }
      }
   }

   public static class PathingDataSpiral extends AreaLoaderMode.PathingData {
      public BlockPos spiralOrigin;
      public int spiralWidth = 0;
      public int spiralHeight = 0;

      public PathingDataSpiral(BlockPos initialPos, BlockPos currPos, float yawDirection, boolean mainPath, int spiralWidth, int spiralHeight) {
         this.spiralOrigin = initialPos;
         this.initialPos = initialPos;
         this.currPos = currPos;
         this.yawDirection = yawDirection;
         this.mainPath = mainPath;
         this.spiralWidth = spiralWidth;
         this.spiralHeight = spiralHeight;
      }
   }

   public static class SpiralLegInfo {
      public int startX;
      public int startZ;
      public int endX;
      public int endZ;
      public int spiralWidth;
      public int spiralHeight;
      public float yaw;
      public boolean mainPath;
      public int legNumber;

      SpiralLegInfo(int sx, int sz, int ex, int ez, int w, int h, float y, boolean mp, int num) {
         this.startX = sx;
         this.startZ = sz;
         this.endX = ex;
         this.endZ = ez;
         this.spiralWidth = w;
         this.spiralHeight = h;
         this.yaw = y;
         this.mainPath = mp;
         this.legNumber = num;
      }

      double distanceToLeg(int px, int pz) {
         double dx = this.endX - this.startX;
         double dz = this.endZ - this.startZ;
         double legLengthSq = dx * dx + dz * dz;
         if (legLengthSq == 0.0) {
            return Math.sqrt((px - this.startX) * (px - this.startX) + (pz - this.startZ) * (pz - this.startZ));
         } else {
            double t = Math.max(0.0, Math.min(1.0, ((px - this.startX) * dx + (pz - this.startZ) * dz) / legLengthSq));
            double projX = this.startX + t * dx;
            double projZ = this.startZ + t * dz;
            return Math.sqrt((px - projX) * (px - projX) + (pz - projZ) * (pz - projZ));
         }
      }

      int[] projectOntoLeg(int px, int pz) {
         double dx = this.endX - this.startX;
         double dz = this.endZ - this.startZ;
         double legLengthSq = dx * dx + dz * dz;
         if (legLengthSq == 0.0) {
            return new int[]{this.startX, this.startZ};
         } else {
            double t = ((px - this.startX) * dx + (pz - this.startZ) * dz) / legLengthSq;
            t = Math.max(0.0, Math.min(1.0, t));
            int projX = (int)Math.round(this.startX + t * dx);
            int projZ = (int)Math.round(this.startZ + t * dz);
            return new int[]{projX, projZ};
         }
      }
   }
}
