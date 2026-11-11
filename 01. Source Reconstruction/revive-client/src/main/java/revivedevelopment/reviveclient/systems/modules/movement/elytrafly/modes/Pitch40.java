package revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes;

import revivedevelopment.reviveclient.events.entity.player.PlayerMoveEvent;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.modules.hunting.HuntingUtils;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightModes;

public class Pitch40 extends ElytraFlightMode {
   private boolean pitchingDown = true;
   private int pitch;
   private boolean climbingToBounds;
   private int fireworkCooldown;

   public Pitch40() {
      super(ElytraFlightModes.Pitch40);
   }

   @Override
   public void onActivate() {
      this.climbingToBounds = false;
      if (this.mc.player.getY() < this.elytraFly.pitch40Upper()) {
         if (this.elytraFly.pitch40RocketToBounds.get()) {
            this.climbingToBounds = true;
            this.fireworkCooldown = 0;
            this.pitch = (int)this.mc.player.getPitch();
            return;
         }

         this.elytraFly.error("Player must be above upper bounds!", new Object[0]);
         this.elytraFly.toggle();
      }

      this.pitch = 40;
   }

   @Override
   public void onDeactivate() {
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.climbingToBounds) {
         if (this.mc.player.getY() < this.elytraFly.pitch40Upper()) {
            if (this.mc.player.getPitch() > -40.0F) {
               this.pitch = (int)(this.pitch - this.elytraFly.pitch40rotationSpeed.get());
               if (this.pitch < -40) {
                  this.pitch = -40;
               }
            }

            this.mc.player.setPitch(this.pitch);
            if (this.fireworkCooldown > 0) {
               this.fireworkCooldown--;
            } else if (this.mc.player.isGliding() && !PbPredict.serverPushesOnly() && HuntingUtils.firework(this.mc, false) >= 0) {
               this.fireworkCooldown = this.elytraFly.pitch40RocketFireworkDelay.get();
            }

            return;
         }

         this.climbingToBounds = false;
         this.pitchingDown = true;
      }

      if (this.pitchingDown && this.mc.player.getY() <= this.elytraFly.pitch40Lower()) {
         this.pitchingDown = false;
      } else if (!this.pitchingDown && this.mc.player.getY() >= this.elytraFly.pitch40Upper()) {
         this.pitchingDown = true;
      }

      if (!this.pitchingDown && this.mc.player.getPitch() > -40.0F) {
         this.pitch = (int)(this.pitch - this.elytraFly.pitch40rotationSpeed.get());
         if (this.pitch < -40) {
            this.pitch = -40;
         }
      } else if (this.pitchingDown && this.mc.player.getPitch() < 40.0F) {
         this.pitch = (int)(this.pitch + this.elytraFly.pitch40rotationSpeed.get());
         if (this.pitch > 40) {
            this.pitch = 40;
         }
      }

      this.mc.player.setPitch(this.pitch);
   }

   @Override
   public void autoTakeoff() {
   }

   @Override
   public void handleHorizontalSpeed(PlayerMoveEvent event) {
      this.velX = event.movement.x;
      this.velZ = event.movement.z;
   }

   @Override
   public void handleVerticalSpeed(PlayerMoveEvent event) {
   }

   @Override
   public void handleFallMultiplier() {
   }

   @Override
   public void handleAutopilot() {
   }

   @Override
   public String getHudString() {
      return this.climbingToBounds ? super.getHudString() + " (climbing)" : super.getHudString();
   }
}
