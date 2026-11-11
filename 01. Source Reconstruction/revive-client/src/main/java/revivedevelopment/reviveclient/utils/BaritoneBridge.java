package revivedevelopment.reviveclient.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.systems.modules.movement.BaritoneElytraOverworld;

public final class BaritoneBridge {
   private static final boolean PRESENT = detect();
   private static volatile Boolean nativeOverworld;

   private BaritoneBridge() {
   }

   private static boolean detect() {
      try {
         Class.forName("baritone.api.BaritoneAPI");
         return true;
      } catch (Throwable var1) {
         return false;
      }
   }

   public static boolean isPresent() {
      return PRESENT;
   }

   public static void execute(String command) {
      if (PRESENT) {
         BaritoneImpl.execute(command);
      }
   }

   public static String getPrefix() {
      if (!PRESENT) {
         return "#";
      } else {
         try {
            String p = BaritoneImpl.getPrefix();
            return p != null && !p.isEmpty() ? p : "#";
         } catch (Throwable var1) {
            return "#";
         }
      }
   }

   public static boolean isPrefixControlEnabled() {
      if (!PRESENT) {
         return false;
      } else {
         try {
            return BaritoneImpl.isPrefixControlEnabled();
         } catch (Throwable var1) {
            return true;
         }
      }
   }

   public static void setGoalXZAndPath(int x, int z) {
      if (PRESENT) {
         BaritoneImpl.setGoalXZAndPath(x, z);
      }
   }

   public static void setGoalBlockAndPath(int x, int y, int z) {
      if (PRESENT) {
         BaritoneImpl.setGoalBlockAndPath(x, y, z);
      }
   }

   public static void setGoalYLevelAndPath(int y) {
      if (PRESENT) {
         BaritoneImpl.setGoalYLevelAndPath(y);
      }
   }

   public static void clearGoal() {
      if (PRESENT) {
         BaritoneImpl.clearGoal();
      }
   }

   public static boolean hasGoal() {
      return PRESENT && BaritoneImpl.hasGoal();
   }

   public static boolean isPathing() {
      if (!PRESENT) {
         return false;
      } else {
         try {
            return BaritoneImpl.isPathing();
         } catch (Throwable var1) {
            return false;
         }
      }
   }

   public static void cancelEverything() {
      if (PRESENT) {
         try {
            BaritoneImpl.cancelEverything();
         } catch (Throwable var1) {
         }
      }
   }

   public static String pathingGoalString() {
      if (!PRESENT) {
         return null;
      } else {
         try {
            return BaritoneImpl.pathingGoalString();
         } catch (Throwable var1) {
            return null;
         }
      }
   }

   public static int getElytraSetbackDelay() {
      if (!PRESENT) {
         return -1;
      } else {
         try {
            return BaritoneImpl.getElytraSetbackDelay();
         } catch (Throwable var1) {
            return -1;
         }
      }
   }

   public static void setElytraSetbackDelay(int ticks) {
      if (PRESENT) {
         try {
            BaritoneImpl.setElytraSetbackDelay(ticks);
         } catch (Throwable var2) {
         }
      }
   }

   public static boolean elytraHasDestination() {
      if (!PRESENT) {
         return false;
      } else {
         try {
            return BaritoneImpl.elytraHasDestination();
         } catch (Throwable var1) {
            return false;
         }
      }
   }

   public static boolean elytraWaitingForTakeoff() {
      if (!PRESENT) {
         return false;
      } else {
         try {
            return BaritoneImpl.elytraWaitingForTakeoff();
         } catch (Throwable var1) {
            return false;
         }
      }
   }

   public static void elytraPathTo(int x, int z) {
      if (PRESENT) {
         try {
            Integer y = overworldArrivalY();
            if (y != null) {
               BaritoneImpl.elytraPathTo(x, y, z);
            } else {
               BaritoneImpl.elytraPathTo(x, z);
            }
         } catch (Throwable var3) {
         }
      }
   }

   public static void elytraPathTo(int x, int y, int z) {
      if (PRESENT) {
         try {
            BaritoneImpl.elytraPathTo(x, y, z);
         } catch (Throwable var4) {
         }
      }
   }

   private static Integer overworldArrivalY() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.world != null) {
         if (mc.world.getRegistryKey() == World.NETHER) {
            return null;
         } else if (!canElytraFlyHere()) {
            return null;
         } else {
            int y = BaritoneElytraOverworld.goalY();
            int bottom = mc.world.getBottomY();
            int top = mc.world.getBottomY() + mc.world.getHeight() - 1;
            return Math.max(bottom + 1, Math.min(top - 1, y));
         }
      } else {
         return null;
      }
   }

   public static boolean hasNativeOverworldElytra() {
      if (!PRESENT) {
         return false;
      } else {
         Boolean cached = nativeOverworld;
         if (cached != null) {
            return cached;
         } else {
            boolean result;
            try {
               result = BaritoneImpl.hasSetting("elytraOverworldFastMode") && BaritoneImpl.hasSetting("elytraStandingTakeoff");
            } catch (Throwable var3) {
               result = false;
            }

            nativeOverworld = result;
            return result;
         }
      }
   }

   public static boolean canElytraFlyHere() {
      if (!PRESENT) {
         return false;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null && mc.world != null) {
            return mc.world.getRegistryKey() == World.NETHER ? true : hasNativeOverworldElytra() || BaritoneElytraOverworld.isUnlocked();
         } else {
            return false;
         }
      }
   }

   public static boolean getBooleanSetting(String name, boolean fallback) {
      if (!PRESENT) {
         return fallback;
      } else {
         try {
            return BaritoneImpl.getBooleanSetting(name, fallback);
         } catch (Throwable var3) {
            return fallback;
         }
      }
   }

   public static boolean setBooleanSetting(String name, boolean value) {
      if (!PRESENT) {
         return false;
      } else {
         try {
            return BaritoneImpl.setBooleanSetting(name, value);
         } catch (Throwable var3) {
            return false;
         }
      }
   }

   public static void acceptElytraTerms() {
      if (PRESENT) {
         BaritoneImpl.acceptElytraTerms();
      }
   }

   public static boolean isElytraPredictTerrain() {
      if (!PRESENT) {
         return false;
      } else {
         try {
            return BaritoneImpl.isElytraPredictTerrain();
         } catch (Throwable var1) {
            return false;
         }
      }
   }

   public static void setElytraPredictTerrain(boolean value) {
      if (PRESENT) {
         try {
            BaritoneImpl.setElytraPredictTerrain(value);
         } catch (Throwable var2) {
         }
      }
   }

   public static boolean isElytraAutoJump() {
      if (!PRESENT) {
         return false;
      } else {
         try {
            return BaritoneImpl.isElytraAutoJump();
         } catch (Throwable var1) {
            return false;
         }
      }
   }

   public static void setElytraAutoJump(boolean value) {
      if (PRESENT) {
         try {
            BaritoneImpl.setElytraAutoJump(value);
         } catch (Throwable var2) {
         }
      }
   }
}
