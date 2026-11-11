package revivedevelopment.reviveclient.systems.modules.hunting.arealoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.hunting.HuntingUtils;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.utils.BaritoneBridge;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public abstract class AreaLoaderMode {
   protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   protected final AreaLoader searchArea;
   protected final MinecraftClient mc;
   private final AreaLoaderModes type;
   protected long paused = 0L;
   protected BlockPos currentGoalWaypoint = null;
   protected AreaLoaderMode.CurrentDimension currentDimension = AreaLoaderMode.CurrentDimension.UNKNOWN;
   private String lastKnownDimensionSuffix = null;
   protected boolean isInNether = false;
   protected boolean flightModesInitialized = false;
   private boolean elytraFlyDriven = false;
   private boolean weEnabledElytraFly = false;
   private ElytraFlightModes prevFlightMode = null;
   private boolean prevRocketToBounds = false;
   private boolean rocketToBoundsModified = false;
   private AreaLoader.OverworldFlightMode enabledOverworldMode = null;
   protected BlockPos currentBaritoneGoal = null;
   protected boolean baritoneElytraStarted = false;
   private long lastGoalSetTime = 0L;
   private BlockPos lastPlayerPos = null;
   private long lastMovementCheckTime = 0L;
   private int stuckCounter = 0;

   public AreaLoaderMode(AreaLoader searchArea, AreaLoaderModes type) {
      this.searchArea = searchArea;
      this.mc = MinecraftClient.getInstance();
      this.type = type;
   }

   protected String saveFileName() {
      return this.type.fileName();
   }

   protected static double dist2D(double x1, double z1, double x2, double z2) {
      double dx = x1 - x2;
      double dz = z1 - z2;
      return Math.sqrt(dx * dx + dz * dz);
   }

   protected static double dist3D(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dy = a.getY() - b.getY();
      double dz = a.getZ() - b.getZ();
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   protected double distToXZ(double x, double z) {
      return this.mc.player == null ? Double.MAX_VALUE : dist2D(this.mc.player.getX(), this.mc.player.getZ(), x, z);
   }

   protected void debugInfo(String message) {
      if (this.searchArea.debugMode.get()) {
         ChatUtils.info(message);
      }
   }

   protected void debugInfo(String format, Object... args) {
      if (this.searchArea.debugMode.get()) {
         ChatUtils.info(format, args);
      }
   }

   protected void updateGoalWaypoint(BlockPos target) {
      if (this.searchArea.showGoalWaypoint.get() && target != null) {
         if (this.currentGoalWaypoint == null || this.currentGoalWaypoint.getX() != target.getX() || this.currentGoalWaypoint.getZ() != target.getZ()) {
            this.clearGoalWaypoint();
            this.currentGoalWaypoint = target;
            AreaLoaderWaypoints.add(target);
         }
      } else {
         this.clearGoalWaypoint();
      }
   }

   protected void clearGoalWaypoint() {
      if (this.currentGoalWaypoint != null) {
         AreaLoaderWaypoints.remove(this.currentGoalWaypoint.getX(), this.currentGoalWaypoint.getZ());
         this.currentGoalWaypoint = null;
      }
   }

   protected boolean isNether() {
      return this.mc.world != null && this.mc.world.getRegistryKey().equals(World.NETHER);
   }

   protected boolean isEnd() {
      return this.mc.world != null && this.mc.world.getRegistryKey().equals(World.END);
   }

   protected boolean isSurvival() {
      return this.mc.player != null && this.mc.interactionManager != null && this.mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL;
   }

   protected AreaLoaderMode.CurrentDimension getDimension() {
      if (this.mc.world == null) {
         return AreaLoaderMode.CurrentDimension.UNKNOWN;
      } else if (this.isNether()) {
         return AreaLoaderMode.CurrentDimension.NETHER;
      } else {
         return this.isEnd() ? AreaLoaderMode.CurrentDimension.END : AreaLoaderMode.CurrentDimension.OVERWORLD;
      }
   }

   protected void checkDimensionChange() {
      if (this.isWorldReady()) {
         AreaLoaderMode.CurrentDimension newDimension = this.getDimension();
         if (this.flightModesInitialized && newDimension != this.currentDimension) {
            this.debugInfo("AreaLoader: Dimension change detected (was %s, now %s). Switching flight modes...", this.currentDimension, newDimension);
            this.cleanupFlightModes();
            this.flightModesInitialized = false;
            this.currentDimension = newDimension;
            this.isInNether = newDimension == AreaLoaderMode.CurrentDimension.NETHER;
            this.initializeFlightModes();
         }

         if (!this.flightModesInitialized) {
            this.initializeFlightModes();
         }
      }
   }

   private boolean isWorldReady() {
      if (this.mc.world != null && this.mc.player != null) {
         try {
            RegistryKey<World> key = this.mc.world.getRegistryKey();
            return key != null && key.getValue() != null;
         } catch (Exception var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   protected void initializeFlightModes() {
      if (!this.flightModesInitialized) {
         if (!this.isWorldReady()) {
            this.debugInfo("AreaLoader: World not ready, deferring flight mode initialization.");
         } else {
            this.currentDimension = this.getDimension();
            this.isInNether = this.currentDimension == AreaLoaderMode.CurrentDimension.NETHER;
            if (!this.isSurvival()) {
               this.debugInfo("AreaLoader: Not in survival mode, deferring flight mode initialization.");
            } else {
               this.flightModesInitialized = true;
               this.enabledOverworldMode = null;
               this.debugInfo(
                  "AreaLoader: Initializing flight modes. currentDimension=%s, dimension=%s",
                  this.currentDimension,
                  this.mc.world.getRegistryKey().getValue().toString()
               );
               if (this.currentDimension == AreaLoaderMode.CurrentDimension.NETHER) {
                  this.debugInfo("AreaLoader: In Nether, skipping overworld flight modules.");
                  if (this.searchArea.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA && !BaritoneBridge.isPresent()) {
                     this.debugInfo("AreaLoader: Baritone is required for Nether pathing. Disabling.");
                     this.disable();
                  }
               } else {
                  this.enabledOverworldMode = this.searchArea.overworldFlightMode.get();
                  if (this.enabledOverworldMode == AreaLoader.OverworldFlightMode.BARITONE_ELYTRA) {
                     if (!BaritoneBridge.isPresent()) {
                        ChatUtils.error("AreaLoader: BARITONE_ELYTRA needs Baritone installed. Disabling.");
                        this.disable();
                     } else if (!BaritoneBridge.canElytraFlyHere()) {
                        ChatUtils.error(
                           "AreaLoader: this Baritone only flies in the Nether. Install the HunterBuddy Overworld build, or enable BaritoneElytraOverworld on a hookable build, to use BARITONE_ELYTRA here. Disabling."
                        );
                        this.disable();
                     } else {
                        this.debugInfo(
                           "AreaLoader: Baritone elytra waypoints in %s (%s).",
                           this.currentDimension,
                           BaritoneBridge.hasNativeOverworldElytra() ? "native Overworld build" : "client unlock"
                        );
                     }
                  } else {
                     ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
                     if (elytraFly != null) {
                        if (this.enabledOverworldMode == AreaLoader.OverworldFlightMode.PITCH40) {
                           this.elytraFlyDriven = true;
                           this.prevFlightMode = elytraFly.flightMode.get();
                           if (this.prevFlightMode != ElytraFlightModes.Pitch40) {
                              elytraFly.flightMode.set(ElytraFlightModes.Pitch40);
                           }

                           if (this.searchArea.pitch40AutoFirework.get()) {
                              this.prevRocketToBounds = elytraFly.pitch40RocketToBounds.get();
                              elytraFly.pitch40RocketToBounds.set(true);
                              this.rocketToBoundsModified = true;
                           }

                           if (!elytraFly.isActive()) {
                              elytraFly.toggle();
                              this.weEnabledElytraFly = true;
                           }

                           this.debugInfo("AreaLoader: ElytraFly (Pitch40) enabled.");
                        } else if (this.enabledOverworldMode == AreaLoader.OverworldFlightMode.ROCKETS) {
                           this.elytraFlyDriven = true;
                           if (!elytraFly.isActive()) {
                              elytraFly.toggle();
                              this.weEnabledElytraFly = true;
                           }

                           this.debugInfo("AreaLoader: ElytraFly enabled (rockets/manual mode).");
                        }
                     }
                  }
               }
            }
         }
      }
   }

   protected void cleanupFlightModes() {
      if (this.baritoneElytraStarted) {
         try {
            BaritoneBridge.execute("cancel");
            this.debugInfo("AreaLoader: Baritone cancelled.");
         } catch (Throwable var2) {
         }
      }

      this.cleanupOverworldModules();
      this.flightModesInitialized = false;
      this.baritoneElytraStarted = false;
      this.currentBaritoneGoal = null;
      this.lastGoalSetTime = 0L;
      this.lastPlayerPos = null;
      this.lastMovementCheckTime = 0L;
      this.stuckCounter = 0;
      this.enabledOverworldMode = null;
   }

   private void cleanupOverworldModules() {
      ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
      if (elytraFly != null && this.elytraFlyDriven) {
         if (this.rocketToBoundsModified) {
            elytraFly.pitch40RocketToBounds.set(this.prevRocketToBounds);
            this.rocketToBoundsModified = false;
         }

         if (this.weEnabledElytraFly && elytraFly.isActive()) {
            elytraFly.toggle();
            this.debugInfo("AreaLoader: ElytraFly disabled.");
         }

         if (this.prevFlightMode != null) {
            elytraFly.flightMode.set(this.prevFlightMode);
         }
      }

      this.weEnabledElytraFly = false;
      this.elytraFlyDriven = false;
      this.prevFlightMode = null;
   }

   protected boolean usingBaritoneElytra() {
      return this.isInNether
         ? this.searchArea.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA
         : this.searchArea.overworldUsesBaritone() && BaritoneBridge.canElytraFlyHere();
   }

   protected void setBaritoneGoal(BlockPos goal) {
      if (this.usingBaritoneElytra() && goal != null) {
         if (!BaritoneBridge.isPresent()) {
            this.debugInfo("Baritone with elytra support required for elytra pathfinding");
         } else {
            long now = System.currentTimeMillis();
            if (!goal.equals(this.currentBaritoneGoal) || now - this.lastGoalSetTime >= 2000L) {
               this.currentBaritoneGoal = goal;
               this.lastGoalSetTime = now;
               this.stuckCounter = 0;

               try {
                  BaritoneBridge.acceptElytraTerms();
                  BaritoneBridge.elytraPathTo(goal.getX(), goal.getZ());
                  this.baritoneElytraStarted = true;
                  this.debugInfo("Baritone goal set to X=%d Z=%d", goal.getX(), goal.getZ());
               } catch (Exception var5) {
                  this.debugInfo("Failed to set Baritone goal: " + var5.getMessage());
               }
            }
         }
      }
   }

   protected boolean hasReachedBaritoneGoal() {
      if (this.currentBaritoneGoal != null && this.mc.player != null) {
         double distance = this.distToXZ(this.currentBaritoneGoal.getX(), this.currentBaritoneGoal.getZ());
         return distance < this.searchArea.netherWaypointReachDistance.get().intValue();
      } else {
         return false;
      }
   }

   protected boolean isBaritoneIdle() {
      return !BaritoneBridge.isPresent() ? true : !BaritoneBridge.elytraHasDestination();
   }

   protected boolean isStuck() {
      if (this.mc.player != null && this.currentBaritoneGoal != null) {
         long now = System.currentTimeMillis();
         BlockPos currentPos = this.mc.player.getBlockPos();
         if (now - this.lastMovementCheckTime < 1000L) {
            return false;
         } else {
            this.lastMovementCheckTime = now;
            if (this.lastPlayerPos != null) {
               double movedDistance = dist3D(currentPos, this.lastPlayerPos);
               if (movedDistance < 5.0) {
                  this.stuckCounter++;
                  if (this.stuckCounter >= 10) {
                     this.debugInfo("Baritone appears stuck (no progress for %d checks)", this.stuckCounter);
                     return true;
                  }
               } else {
                  this.stuckCounter = 0;
               }
            }

            this.lastPlayerPos = currentPos;
            return false;
         }
      } else {
         return false;
      }
   }

   protected boolean needsNewGoal() {
      if (this.currentBaritoneGoal == null) {
         return true;
      } else if (this.hasReachedBaritoneGoal()) {
         return true;
      } else {
         long now = System.currentTimeMillis();
         if (now - this.lastGoalSetTime < 2000L) {
            return false;
         } else if (this.isBaritoneIdle()) {
            this.debugInfo("Baritone idle, needs new goal");
            return true;
         } else if (this.isStuck()) {
            this.stuckCounter = 0;
            return true;
         } else {
            return false;
         }
      }
   }

   public void onTick() {
      this.checkDimensionChange();
   }

   public void onActivate() {
      if (this.mc.world != null) {
         this.getDimensionSuffix();
      }
   }

   public void onDeactivate() {
      HuntingUtils.setPressed(this.mc.options.forwardKey, false);
      this.cleanupFlightModes();
      this.clearGoalWaypoint();
   }

   public abstract void resetState();

   public void disable() {
      this.mc.execute(() -> {
         if (this.searchArea.isActive()) {
            this.searchArea.toggle();
         }
      });
   }

   protected String getDimensionSuffix() {
      if (this.mc.world == null) {
         return this.lastKnownDimensionSuffix;
      } else {
         try {
            RegistryKey<World> key = this.mc.world.getRegistryKey();
            String suffix;
            if (key.equals(World.NETHER)) {
               suffix = "_nether";
            } else if (key.equals(World.END)) {
               suffix = "_end";
            } else {
               suffix = "_overworld";
            }

            this.lastKnownDimensionSuffix = suffix;
            return suffix;
         } catch (Exception var3) {
            return this.lastKnownDimensionSuffix;
         }
      }
   }

   protected File getJsonFile(String fileName) {
      try {
         String saveName = this.searchArea.saveLocation.get();
         if (saveName == null || saveName.trim().isEmpty()) {
            saveName = "default";
         }

         File baseDir = new File(ReviveClient.FOLDER, "arealoader");
         File saveDir = new File(baseDir, saveName);
         String dimensionSuffix = this.getDimensionSuffix();
         if (dimensionSuffix == null) {
            this.debugInfo("Cannot determine dimension for save file - skipping save to prevent corruption");
            return null;
         } else {
            return new File(saveDir, fileName + dimensionSuffix + ".json");
         }
      } catch (Exception var6) {
         var6.printStackTrace();
         return null;
      }
   }

   protected File getJsonFileForDimension(String fileName, String dimensionSuffix) {
      try {
         String saveName = this.searchArea.saveLocation.get();
         if (saveName == null || saveName.trim().isEmpty()) {
            saveName = "default";
         }

         File baseDir = new File(ReviveClient.FOLDER, "arealoader");
         File saveDir = new File(baseDir, saveName);
         return new File(saveDir, fileName + dimensionSuffix + ".json");
      } catch (Exception var6) {
         var6.printStackTrace();
         return null;
      }
   }

   protected void saveToJson(boolean goingToStart, AreaLoaderMode.PathingData pd) {
      if (pd != null) {
         if (!goingToStart && this.mc.player != null && this.mc.world != null) {
            pd.currPos = this.mc.player.getBlockPos();
         }

         this.saveToJsonExact(pd);
      }
   }

   protected void saveToJsonExact(AreaLoaderMode.PathingData pd) {
      if (pd != null) {
         File file = this.getJsonFile(this.type.fileName());
         if (file == null) {
            this.debugInfo("Skipping save: cannot determine correct dimension file (likely in queue/disconnected)");
         } else {
            try {
               if (!file.getParentFile().exists()) {
                  file.getParentFile().mkdirs();
               }

               Writer writer = new FileWriter(file);
               GSON.toJson(pd, writer);
               writer.flush();
               writer.close();
               if (!file.exists() || file.length() <= 0L) {
                  ChatUtils.info("Warning: Save file was not created or is empty: " + file.getAbsolutePath());
               } else if (System.currentTimeMillis() % 30000L < 1000L) {
                  ChatUtils.info("Progress saved to: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
               }
            } catch (IOException var4) {
               this.debugInfo("Failed to save progress to file: " + var4.getMessage());
               var4.printStackTrace();
            }
         }
      }
   }

   public void clear() {
      File file = this.getJsonFile(this.type.fileName());
      if (file != null && file.exists()) {
         if (file.delete()) {
            this.debugInfo("Cleared saved " + this.type + " progress from: " + file.getAbsolutePath());
            this.resetState();
         } else {
            this.debugInfo("Failed to delete " + this.type + " save file: " + file.getAbsolutePath());
         }
      } else if (file != null) {
         this.debugInfo("No saved " + this.type + " progress to clear (file doesn't exist: " + file.getAbsolutePath() + ")");
         this.resetState();
      } else {
         this.debugInfo("No saved " + this.type + " progress to clear (invalid save path)");
      }
   }

   public void clearAll() {
      this.debugInfo("Clearing all AreaLoader saved progress (all dimensions)...");
      int deletedCount = 0;
      String[] dimensionSuffixes = new String[]{"_overworld", "_nether", "_end", ""};

      for (AreaLoaderModes mode : AreaLoaderModes.values()) {
         for (String suffix : dimensionSuffixes) {
            File file = this.getJsonFileForDimension(mode.fileName(), suffix);
            if (file != null && file.exists()) {
               if (file.delete()) {
                  deletedCount++;
                  this.debugInfo("Deleted " + mode.fileName() + suffix + ".json");
               } else {
                  this.debugInfo("Failed to delete " + mode.fileName() + suffix + ".json");
               }
            }
         }
      }

      this.lastKnownDimensionSuffix = null;
      this.resetState();
      if (deletedCount == 0) {
         this.debugInfo("No save files found to clear, but in-memory state has been reset.");
      } else {
         this.debugInfo("Successfully deleted " + deletedCount + " save file(s) and reset state. Ready to start fresh!");
      }
   }

   @Override
   public String toString() {
      return this.type.toString();
   }

   protected static enum CurrentDimension {
      OVERWORLD,
      NETHER,
      END,
      UNKNOWN;
   }

   protected static class PathingData {
      public BlockPos initialPos;
      public BlockPos currPos;
      public float yawDirection;
      public boolean mainPath;
   }
}
