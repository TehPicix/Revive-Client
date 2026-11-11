package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class Timer extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAuto = this.settings.createGroup("Auto Timer");
   private final Setting<Double> multiplier = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("multiplier")
            .description("The timer multiplier amount.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderMin(0.1)
            .sliderMax(10.0)
            .build()
      );
   private final Setting<Boolean> autoTimer = this.sgAuto
      .add(new BoolSetting.Builder().name("auto-timer").description("Automatically adjust timer speed based on chunk loading.").defaultValue(false).build());
   private final Setting<Boolean> onlyWhenFlying = this.sgAuto
      .add(
         new BoolSetting.Builder()
            .name("only-when-flying")
            .description("Only activates and adjusts itself when the player is flying with an elytra.")
            .defaultValue(true)
            .visible(this.autoTimer::get)
            .build()
      );
   private final Setting<Double> minThreshold = this.sgAuto
      .add(
         new DoubleSetting.Builder()
            .name("min-threshold")
            .description("Minimum value the timer is allowed to set itself to.")
            .defaultValue(0.4)
            .sliderRange(0.1, 1.0)
            .decimalPlaces(2)
            .visible(this.autoTimer::get)
            .build()
      );
   private final Setting<Double> maxThreshold = this.sgAuto
      .add(
         new DoubleSetting.Builder()
            .name("max-threshold")
            .description("Maximum value the timer is allowed to set itself to (default = 1.0).")
            .defaultValue(1.0)
            .sliderRange(0.1, 1.0)
            .decimalPlaces(2)
            .visible(this.autoTimer::get)
            .build()
      );
   private final Setting<Integer> scanRadius = this.sgAuto
      .add(
         new IntSetting.Builder()
            .name("scan-radius")
            .description("Radius in chunks to scan.")
            .defaultValue(4)
            .sliderRange(1, 10)
            .visible(this.autoTimer::get)
            .build()
      );
   private final Setting<Integer> unloadedThreshold = this.sgAuto
      .add(
         new IntSetting.Builder()
            .name("unloaded-threshold")
            .description("Amount of unloaded chunks before the timer is allowed to adjust itself.")
            .defaultValue(5)
            .sliderRange(1, 20)
            .visible(this.autoTimer::get)
            .build()
      );
   private final Setting<Double> adjustmentFactor = this.sgAuto
      .add(
         new DoubleSetting.Builder()
            .name("adjustment-factor")
            .description("The factor by which the timer is allowed to adjust itself.")
            .defaultValue(0.1)
            .sliderRange(0.1, 1.0)
            .decimalPlaces(2)
            .visible(this.autoTimer::get)
            .build()
      );
   public static final double OFF = 1.0;
   private double override = 1.0;
   private double currentThreshold;

   public Timer() {
      super(Categories.Player, "timer", "Changes the speed of everything in your game.");
   }

   @Override
   public void onActivate() {
      this.currentThreshold = this.multiplier.get();
   }

   public double getMultiplier() {
      return this.override != 1.0 ? this.override : (this.isActive() ? this.multiplier.get() : 1.0);
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.autoTimer.get()) {
         if (this.mc.player.isGliding() || !this.onlyWhenFlying.get()) {
            int unloadedChunks = this.countUnloadedChunks();
            double severity = Math.min(1.0, unloadedChunks / (this.unloadedThreshold.get().intValue() * 2.0));
            double targetSpeed = unloadedChunks > this.unloadedThreshold.get()
               ? this.minThreshold.get() + (this.maxThreshold.get() - this.minThreshold.get()) * (1.0 - severity)
               : this.maxThreshold.get();
            double difference = targetSpeed - this.currentThreshold;
            if (Math.abs(difference) > 0.01) {
               this.currentThreshold = this.currentThreshold + difference * this.adjustmentFactor.get();
               this.multiplier.set(this.currentThreshold);
            }
         }
      }
   }

   private int countUnloadedChunks() {
      ChunkPos playerPos = this.mc.player.getChunkPos();
      int radius = this.scanRadius.get();
      int unloadedCount = 0;

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            if (!this.mc.world.getChunkManager().isChunkLoaded(playerPos.x + x, playerPos.z + z)) {
               if (++unloadedCount > this.unloadedThreshold.get()) {
                  return unloadedCount;
               }
            }
         }
      }

      return unloadedCount;
   }
}
