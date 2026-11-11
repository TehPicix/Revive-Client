package revivedevelopment.reviveclient.utils;

import baritone.api.BaritoneAPI;
import baritone.api.Settings.Setting;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.process.IElytraProcess;
import java.util.Locale;

final class BaritoneImpl {
   private BaritoneImpl() {
   }

   static void execute(String command) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(command);
   }

   static String getPrefix() {
      return (String)BaritoneAPI.getSettings().prefix.value;
   }

   static boolean isPrefixControlEnabled() {
      return (Boolean)BaritoneAPI.getSettings().prefixControl.value;
   }

   static void setGoalXZAndPath(int x, int z) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
   }

   static void setGoalBlockAndPath(int x, int y, int z) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z));
   }

   static void setGoalYLevelAndPath(int y) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalYLevel(y));
   }

   static void clearGoal() {
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
   }

   static boolean hasGoal() {
      return BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal() != null;
   }

   static boolean isPathing() {
      return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
   }

   static void cancelEverything() {
      BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
   }

   static String pathingGoalString() {
      Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
      return goal != null ? goal.toString() : null;
   }

   static int getElytraSetbackDelay() {
      return (Integer)BaritoneAPI.getSettings().elytraFireworkSetbackUseDelay.value;
   }

   static void setElytraSetbackDelay(int ticks) {
      BaritoneAPI.getSettings().elytraFireworkSetbackUseDelay.value = ticks;
   }

   static boolean elytraHasDestination() {
      return BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() != null;
   }

   static boolean elytraWaitingForTakeoff() {
      IElytraProcess process = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess();
      return process.currentDestination() != null && !process.isSafeToCancel();
   }

   static void elytraPathTo(int x, int z) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new GoalXZ(x, z));
   }

   static void elytraPathTo(int x, int y, int z) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new GoalBlock(x, y, z));
   }

   static boolean hasSetting(String name) {
      return BaritoneAPI.getSettings().byLowerName.containsKey(name.toLowerCase(Locale.ROOT));
   }

   static boolean getBooleanSetting(String name, boolean fallback) {
      Setting<?> setting = (Setting<?>)BaritoneAPI.getSettings().byLowerName.get(name.toLowerCase(Locale.ROOT));
      return setting != null && setting.value instanceof Boolean value ? value : fallback;
   }

   static boolean setBooleanSetting(String name, boolean value) {
      Setting setting = (Setting)BaritoneAPI.getSettings().byLowerName.get(name.toLowerCase(Locale.ROOT));
      if (setting != null && setting.value instanceof Boolean) {
         setting.value = value;
         return true;
      } else {
         return false;
      }
   }

   static void acceptElytraTerms() {
      BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
   }

   static boolean isElytraPredictTerrain() {
      return (Boolean)BaritoneAPI.getSettings().elytraPredictTerrain.value;
   }

   static void setElytraPredictTerrain(boolean value) {
      BaritoneAPI.getSettings().elytraPredictTerrain.value = value;
   }

   static boolean isElytraAutoJump() {
      return (Boolean)BaritoneAPI.getSettings().elytraAutoJump.value;
   }

   static void setElytraAutoJump(boolean value) {
      BaritoneAPI.getSettings().elytraAutoJump.value = value;
   }
}
