package revivedevelopment.reviveclient.systems.modules.hunting;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class Pitch40Util extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Boolean> autoBoundAdjust = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-adjust-bounds")
            .description(
               "Re-anchors the pitch40 bounds when a climb tops out well off the configured band, or when a reconnect leaves you below it (recovery from lag/reconnect falls). Stable in-band climbs never move the bounds. Turn off to keep your bounds fully manual."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Double> boundGap = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("bound-gap")
            .description("The gap between the upper and lower bounds. Used when reconnecting, or when at max height if Auto Adjust Bounds is enabled.")
            .defaultValue(60.0)
            .sliderRange(50.0, 100.0)
            .build()
      );
   public final Setting<Boolean> autoFirework = this.sgGeneral
      .add(new BoolSetting.Builder().name("auto-firework").description("Uses a firework automatically if your velocity is too low.").defaultValue(true).build());
   public final Setting<Double> velocityThreshold = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("velocity-threshold")
            .description("Velocity must be below this value when going up for firework to activate.")
            .defaultValue(-0.05)
            .sliderRange(-0.5, 1.0)
            .visible(this.autoFirework::get)
            .build()
      );
   public final Setting<Integer> fireworkCooldownTicks = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("cooldown-ticks")
            .description("Cooldown after using a firework in ticks.")
            .defaultValue(10)
            .sliderRange(0, 100)
            .visible(this.autoFirework::get)
            .build()
      );
   private ElytraFlightModes oldValue;
   int fireworkCooldown = 0;
   boolean goingUp = true;
   int elytraSwapSlot = -1;

   public Pitch40Util() {
      super(
         Categories.Hunting,
         "pitch40-util",
         "Makes sure pitch 40 stays on when reconnecting to 2b2t, and sets your bounds as you reach highest point each climb."
      );
   }

   private ElytraFly elytraFly() {
      return Modules.get().get(ElytraFly.class);
   }

   private Setting<ElytraFlightModes> elytraFlyMode() {
      return (Setting<ElytraFlightModes>)this.elytraFly().settings.get("mode");
   }

   @Override
   public void onActivate() {
      this.oldValue = this.elytraFlyMode().get();
      this.elytraFlyMode().set(ElytraFlightModes.Pitch40);
   }

   @Override
   public void onDeactivate() {
      ElytraFly elytraFly = this.elytraFly();
      elytraFly.pitch40UpperOverride = null;
      elytraFly.pitch40LowerOverride = null;
      if (elytraFly.isActive()) {
         elytraFly.toggle();
      }

      this.elytraFlyMode().set(this.oldValue);
   }

   private void resetBounds() {
      ElytraFly elytraFly = this.elytraFly();
      elytraFly.pitch40UpperOverride = this.mc.player.getY() - 5.0;
      elytraFly.pitch40LowerOverride = this.mc.player.getY() - 5.0 - this.boundGap.get();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      ElytraFly elytraFly = this.elytraFly();
      if (elytraFly.isActive()) {
         if (this.fireworkCooldown > 0) {
            this.fireworkCooldown--;
         }

         if (this.elytraSwapSlot != -1) {
            InvUtils.swap(this.elytraSwapSlot, true);
            this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
            this.elytraSwapSlot = -1;
         }

         if (this.autoBoundAdjust.get() && this.mc.player.getY() <= elytraFly.pitch40Lower() - 10.0) {
            this.resetBounds();
            return;
         }

         if (this.mc.player.getPitch() == -40.0F) {
            this.goingUp = true;
            if (this.autoFirework.get()
               && this.mc.player.getVelocity().y < this.velocityThreshold.get()
               && this.mc.player.getY() < elytraFly.pitch40Upper()
               && this.fireworkCooldown == 0) {
               int launchStatus = HuntingUtils.firework(this.mc, false);
               if (launchStatus >= 0) {
                  this.fireworkCooldown = this.fireworkCooldownTicks.get();
                  if (launchStatus != 200) {
                     this.elytraSwapSlot = launchStatus;
                  }
               }
            }
         } else if (this.autoBoundAdjust.get() && this.goingUp && this.mc.player.getVelocity().y <= 0.0) {
            this.goingUp = false;
            if (Math.abs(this.mc.player.getY() - 5.0 - elytraFly.pitch40Upper()) > 10.0) {
               this.resetBounds();
            }
         }
      } else if (!this.mc.player.getAbilities().allowFlying) {
         elytraFly.toggle();
         if (this.autoBoundAdjust.get() && this.mc.player.getY() <= elytraFly.pitch40Lower()) {
            this.resetBounds();
         }
      }
   }
}
