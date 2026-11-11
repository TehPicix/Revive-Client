package revivedevelopment.reviveclient.systems.modules.hunting.arealoader.modes;

import java.io.File;
import java.io.FileReader;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.systems.modules.hunting.HuntingUtils;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.AreaLoader;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.AreaLoaderMode;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.AreaLoaderModes;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.Rotations;

public class Rectangle extends AreaLoaderMode {
   private Rectangle.PathingDataRectangle pd;
   private boolean goingToStart = true;
   private long startTime;
   private BlockPos nextWaypointTarget = null;
   private boolean recovering = false;
   private BlockPos recoveryTarget = null;
   private BlockPos lastKnownGoodPosition = null;
   private int offTrackCounter = 0;
   private long recoveryCompletedTime = 0L;

   public Rectangle(AreaLoader searchArea) {
      super(searchArea, AreaLoaderModes.Rectangle);
   }

   @Override
   public void onActivate() {
      this.startTime = System.nanoTime();
      this.goingToStart = true;
      this.nextWaypointTarget = null;
      this.recovering = false;
      this.recoveryTarget = null;
      this.lastKnownGoodPosition = null;
      this.offTrackCounter = 0;
      this.recoveryCompletedTime = 0L;
      File file = this.getJsonFile(this.saveFileName());
      if (file != null && file.exists()) {
         try {
            this.debugInfo("Loading save file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
            FileReader reader = new FileReader(file);
            this.pd = (Rectangle.PathingDataRectangle)GSON.fromJson(reader, Rectangle.PathingDataRectangle.class);
            reader.close();
            this.debugInfo("Loaded saved Rectangle successfully. Current position: " + this.pd.currPos.toShortString());
            this.printRectangleEstimate();
         } catch (Exception var7) {
            this.debugInfo("Failed to load saved Rectangle data. Starting fresh.");
            var7.printStackTrace();
            BlockPos playerPos = this.mc.player.getBlockPos();
            BlockPos startPos = this.searchArea.startPos.get();
            BlockPos targetPos = this.searchArea.targetPos.get();
            if (startPos.equals(new BlockPos(0, 0, 0))) {
               startPos = playerPos;
            }

            if (targetPos.equals(new BlockPos(0, 0, 0))) {
               targetPos = startPos.add(10000, 0, 10000);
            }

            float initialYaw = startPos.getX() < targetPos.getX() ? -90.0F : 90.0F;
            this.pd = new Rectangle.PathingDataRectangle(startPos, targetPos, playerPos, initialYaw, true, playerPos.getZ());
            this.goingToStart = false;
         }
      } else {
         if (file == null) {
            this.debugInfo("Error: Cannot create save file path. Check save-name setting.");
         }

         BlockPos playerPosx = this.mc.player.getBlockPos();
         BlockPos startPosx = this.searchArea.startPos.get();
         BlockPos targetPosx = this.searchArea.targetPos.get();
         if (startPosx.equals(new BlockPos(0, 0, 0))) {
            startPosx = playerPosx;
            this.debugInfo("Start position not configured, using player position: " + playerPosx.toShortString());
         }

         if (targetPosx.equals(new BlockPos(0, 0, 0))) {
            targetPosx = startPosx.add(10000, 0, 10000);
            this.debugInfo("End position not configured, using default offset: " + targetPosx.toShortString());
         }

         float initialYaw = startPosx.getX() < targetPosx.getX() ? -90.0F : 90.0F;
         this.pd = new Rectangle.PathingDataRectangle(startPosx, targetPosx, playerPosx, initialYaw, true, playerPosx.getZ());
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
      this.nextWaypointTarget = null;
      this.recovering = false;
      this.recoveryTarget = null;
      this.lastKnownGoodPosition = null;
      this.offTrackCounter = 0;
      this.debugInfo("Rectangle state reset. Next activation will start fresh.");
   }

   private void printRectangleEstimate() {
      double speedBPS = this.searchArea.boatFlySpeed.get();
      double rowDistance = Math.abs(this.pd.initialPos.getX() - this.pd.targetPos.getX());
      int blockGap = 16 * this.searchArea.rowGap.get();
      int remainingZDistance = Math.abs(this.pd.currPos.getZ() - this.pd.targetPos.getZ());
      int rowCount = remainingZDistance / blockGap;
      double totalBlocks = rowCount * (rowDistance + blockGap);
      long totalSeconds = (long)(totalBlocks / speedBPS);
      long hours = totalSeconds / 3600L;
      long minutes = totalSeconds % 3600L / 60L;
      long seconds = totalSeconds % 60L;
      this.debugInfo(
         "Completion will take an estimated %02d hours %02d minutes %02d seconds at a speed of %.2f and a gap of %d chunks between paths.",
         new Object[]{hours, minutes, seconds, speedBPS, this.searchArea.rowGap.get()}
      );
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.mc.player != null && this.mc.world != null) {
         if (System.nanoTime() - this.startTime > 6.0E11) {
            this.startTime = System.nanoTime();
            super.saveToJson(this.goingToStart, this.pd);
         }

         if (this.usingBaritoneElytra()) {
            this.onTickBaritone();
         } else {
            this.onTickOverworld();
         }
      }
   }

   private void onTickOverworld() {
      if (this.goingToStart) {
         this.updateGoalWaypoint(this.pd.currPos);
         if (this.distToXZ(this.pd.currPos.getX(), this.pd.currPos.getZ()) < 5.0) {
            this.goingToStart = false;
            this.mc.player.setVelocity(0.0, 0.0, 0.0);
         } else {
            this.mc.player.setYaw((float)Rotations.getYaw(this.pd.currPos));
            HuntingUtils.setPressed(this.mc.options.forwardKey, true);
         }
      } else {
         HuntingUtils.setPressed(this.mc.options.forwardKey, true);
         this.mc.player.setYaw(this.pd.yawDirection);
         if (this.distToXZ(this.pd.targetPos.getX(), this.pd.targetPos.getZ()) < 20.0) {
            this.onComplete();
         } else if (this.pd.mainPath
            && (
               this.pd.yawDirection == -90.0F && this.mc.player.getX() >= Math.max(this.pd.initialPos.getX(), this.pd.targetPos.getX())
                  || this.pd.yawDirection == 90.0F && this.mc.player.getX() <= Math.min(this.pd.initialPos.getX(), this.pd.targetPos.getX())
            )) {
            this.pd.yawDirection = this.mc.player.getZ() < this.pd.targetPos.getZ() ? 0.0F : 180.0F;
            this.pd.mainPath = false;
            this.mc.player.setVelocity(0.0, 0.0, 0.0);
         } else if (!this.pd.mainPath && Math.abs(this.mc.player.getZ() - this.pd.lastCompleteRowZ) >= 16 * this.searchArea.rowGap.get()) {
            this.pd.lastCompleteRowZ = (int)this.mc.player.getZ();
            this.pd.yawDirection = this.pd.initialPos.getX() > this.mc.player.getX() ? -90.0F : 90.0F;
            this.pd.mainPath = true;
            this.mc.player.setVelocity(0.0, 0.0, 0.0);
         }
      }
   }

   private void onTickBaritone() {
      if (!this.usingBaritoneElytra()) {
         this.mc.player.setYaw(this.pd.yawDirection);
      } else {
         int blockGap = 16 * this.searchArea.rowGap.get();
         int reachDist = this.searchArea.netherWaypointReachDistance.get();
         if (this.recovering && this.recoveryTarget != null) {
            double distToRecovery = this.distToXZ(this.recoveryTarget.getX(), this.recoveryTarget.getZ());
            if (distToRecovery < reachDist) {
               this.debugInfo("Rectangle: Recovery complete. Resuming from X=%d Z=%d", new Object[]{this.recoveryTarget.getX(), this.recoveryTarget.getZ()});
               this.recovering = false;
               this.recoveryTarget = null;
               this.offTrackCounter = 0;
               this.recoveryCompletedTime = System.currentTimeMillis();
               this.lastKnownGoodPosition = this.mc.player.getBlockPos();
               this.nextWaypointTarget = null;
            } else if (this.needsNewGoal()) {
               this.setBaritoneGoal(this.recoveryTarget);
            }
         } else if (this.goingToStart) {
            double distToSaved = this.distToXZ(this.pd.currPos.getX(), this.pd.currPos.getZ());
            if (distToSaved < reachDist) {
               this.goingToStart = false;
               this.lastKnownGoodPosition = this.pd.currPos;
               this.nextWaypointTarget = null;
               this.debugInfo(
                  "Rectangle: Reached saved position. Resuming at X=%d Z=%d, yaw=%.1f, mainPath=%b",
                  new Object[]{this.pd.currPos.getX(), this.pd.currPos.getZ(), this.pd.yawDirection, this.pd.mainPath}
               );
            } else if (this.needsNewGoal()) {
               this.setBaritoneGoal(this.pd.currPos);
            }
         } else {
            double distToTarget = this.distToXZ(this.pd.targetPos.getX(), this.pd.targetPos.getZ());
            if (distToTarget < reachDist) {
               this.onComplete();
            } else if (this.nextWaypointTarget == null) {
               this.calculateNextWaypoint();
               if (this.nextWaypointTarget != null) {
                  this.debugInfo(
                     "Rectangle: New waypoint at X=%d Z=%d (mainPath=%b, yaw=%.1f)",
                     new Object[]{this.nextWaypointTarget.getX(), this.nextWaypointTarget.getZ(), this.pd.mainPath, this.pd.yawDirection}
                  );
                  this.setBaritoneGoal(this.nextWaypointTarget);
               }
            } else {
               long timeSinceRecovery = System.currentTimeMillis() - this.recoveryCompletedTime;
               if (this.recoveryCompletedTime <= 0L || timeSinceRecovery >= 5000L) {
                  double offTrackDist = this.getRectangleOffTrackDistance();
                  if (offTrackDist > 200.0) {
                     this.offTrackCounter++;
                     if (this.offTrackCounter >= 10) {
                        this.debugInfo("Rectangle: Off-track by %.0f blocks. Saving state and initiating recovery...", new Object[]{offTrackDist});
                        BlockPos currentPos = this.mc.player.getBlockPos();
                        double distToLastGood = this.lastKnownGoodPosition != null ? dist3D(currentPos, this.lastKnownGoodPosition) : Double.MAX_VALUE;
                        if (this.lastKnownGoodPosition != null && distToLastGood > reachDist * 2) {
                           this.pd.currPos = this.lastKnownGoodPosition;
                           super.saveToJsonExact(this.pd);
                           this.recoveryTarget = this.lastKnownGoodPosition;
                           ChatUtils.info(
                              "Rectangle: Saved recovery point at X=%d Z=%d. Navigating back.",
                              this.lastKnownGoodPosition.getX(),
                              this.lastKnownGoodPosition.getZ()
                           );
                        } else {
                           this.recoveryTarget = this.pd.currPos;
                           super.saveToJsonExact(this.pd);
                           ChatUtils.info("Rectangle: Recovering to last saved position X=%d Z=%d", this.pd.currPos.getX(), this.pd.currPos.getZ());
                        }

                        this.recovering = true;
                        this.nextWaypointTarget = null;
                        this.offTrackCounter = 0;
                        this.setBaritoneGoal(this.recoveryTarget);
                        return;
                     }
                  } else {
                     this.offTrackCounter = 0;
                     this.lastKnownGoodPosition = this.mc.player.getBlockPos();
                  }

                  double distToWaypoint = this.distToXZ(this.nextWaypointTarget.getX(), this.nextWaypointTarget.getZ());
                  if (distToWaypoint < reachDist) {
                     this.debugInfo("Rectangle: Reached waypoint at X=%d Z=%d", new Object[]{this.nextWaypointTarget.getX(), this.nextWaypointTarget.getZ()});
                     if (this.pd.mainPath) {
                        int rowEndX = this.pd.yawDirection == -90.0F
                           ? Math.max(this.pd.initialPos.getX(), this.pd.targetPos.getX())
                           : Math.min(this.pd.initialPos.getX(), this.pd.targetPos.getX());
                        boolean reachedRowEnd = Math.abs(this.nextWaypointTarget.getX() - rowEndX) < reachDist;
                        if (reachedRowEnd) {
                           this.pd.yawDirection = this.nextWaypointTarget.getZ() < this.pd.targetPos.getZ() ? 0.0F : 180.0F;
                           this.pd.mainPath = false;
                           this.debugInfo("Rectangle: Reached row end, turning to yaw=%.1f", new Object[]{this.pd.yawDirection});
                        }
                     } else {
                        int targetRowZ = this.pd.lastCompleteRowZ + (this.pd.yawDirection == 0.0F ? blockGap : -blockGap);
                        boolean reachedNextRow = Math.abs(this.nextWaypointTarget.getZ() - targetRowZ) < reachDist;
                        if (reachedNextRow) {
                           this.pd.lastCompleteRowZ = this.nextWaypointTarget.getZ();
                           this.pd.yawDirection = this.pd.initialPos.getX() > this.nextWaypointTarget.getX() ? -90.0F : 90.0F;
                           this.pd.mainPath = true;
                           this.debugInfo(
                              "Rectangle: Reached next row at Z=%d, turning to yaw=%.1f", new Object[]{this.pd.lastCompleteRowZ, this.pd.yawDirection}
                           );
                        }
                     }

                     this.pd.currPos = this.nextWaypointTarget;
                     this.lastKnownGoodPosition = this.nextWaypointTarget;
                     super.saveToJsonExact(this.pd);
                     this.nextWaypointTarget = null;
                     this.calculateNextWaypoint();
                     if (this.nextWaypointTarget != null) {
                        this.debugInfo("Rectangle: Next waypoint at X=%d Z=%d", new Object[]{this.nextWaypointTarget.getX(), this.nextWaypointTarget.getZ()});
                        this.setBaritoneGoal(this.nextWaypointTarget);
                     }
                  } else if (this.needsNewGoal()) {
                     this.debugInfo("Rectangle: Baritone needs new goal, resetting");
                     this.setBaritoneGoal(this.nextWaypointTarget);
                  }
               } else if (this.needsNewGoal()) {
                  this.setBaritoneGoal(this.nextWaypointTarget);
               }
            }
         }
      }
   }

   private void calculateNextWaypoint() {
      int blockGap = 16 * this.searchArea.rowGap.get();
      BlockPos currentRef = this.pd.currPos != null ? this.pd.currPos : this.mc.player.getBlockPos();
      int targetX;
      int targetZ;
      if (this.pd.mainPath) {
         if (this.pd.yawDirection == -90.0F) {
            targetX = Math.max(this.pd.initialPos.getX(), this.pd.targetPos.getX());
         } else {
            targetX = Math.min(this.pd.initialPos.getX(), this.pd.targetPos.getX());
         }

         targetZ = this.pd.lastCompleteRowZ;
      } else {
         targetX = currentRef.getX();
         if (this.pd.yawDirection == 0.0F) {
            targetZ = this.pd.lastCompleteRowZ + blockGap;
         } else {
            targetZ = this.pd.lastCompleteRowZ - blockGap;
         }
      }

      this.nextWaypointTarget = new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
      this.updateGoalWaypoint(this.nextWaypointTarget);
   }

   private void onComplete() {
      HuntingUtils.setPressed(this.mc.options.forwardKey, false);
      this.searchArea.toggle();
      if (this.searchArea.disconnectOnCompletion.get() && this.mc.player != null && this.mc.player.networkHandler != null) {
         this.mc.player.networkHandler.getConnection().disconnect(Text.literal("[AreaLoader] Path is complete"));
      }
   }

   private double getRectangleOffTrackDistance() {
      if (this.pd == null) {
         return 0.0;
      } else {
         BlockPos currentPos = this.mc.player.getBlockPos();
         if (this.pd.mainPath) {
            return Math.abs(currentPos.getZ() - this.pd.lastCompleteRowZ);
         } else {
            return this.nextWaypointTarget != null ? Math.abs(currentPos.getX() - this.nextWaypointTarget.getX()) : 0.0;
         }
      }
   }

   public static class PathingDataRectangle extends AreaLoaderMode.PathingData {
      public BlockPos targetPos;
      public int lastCompleteRowZ;

      public PathingDataRectangle(BlockPos initialPos, BlockPos targetPos, BlockPos currPos, float yawDirection, boolean mainPath, int lastCompleteRowZ) {
         this.initialPos = initialPos;
         this.targetPos = targetPos;
         this.currPos = currPos;
         this.yawDirection = yawDirection;
         this.mainPath = mainPath;
         this.lastCompleteRowZ = lastCompleteRowZ;
      }
   }
}
