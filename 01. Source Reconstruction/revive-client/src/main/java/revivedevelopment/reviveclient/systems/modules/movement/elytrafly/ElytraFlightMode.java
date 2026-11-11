package revivedevelopment.reviveclient.systems.modules.movement.elytrafly;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.entity.player.PlayerMoveEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.ChunkDataEvent;
import revivedevelopment.reviveclient.mixin.LivingEntityAccessor;
import revivedevelopment.reviveclient.mixininterface.IPlayerInventory;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;

public class ElytraFlightMode {
   protected final MinecraftClient mc = MinecraftClient.getInstance();
   protected final ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
   private final ElytraFlightModes type;
   protected boolean lastJumpPressed;
   protected boolean incrementJumpTimer;
   protected boolean lastForwardPressed;
   protected int jumpTimer;
   protected double velX;
   protected double velY;
   protected double velZ;
   protected double ticksLeft;
   protected Vec3d forward;
   protected Vec3d right;
   protected double acceleration;

   public ElytraFlightMode(ElytraFlightModes type) {
      this.type = type;
   }

   public void onTick() {
      if (!PbPredict.serverPushesOnly()) {
         if (this.elytraFly.autoReplenish.get()) {
            FindItemResult fireworks = InvUtils.find(Items.FIREWORK_ROCKET);
            if (fireworks.found() && !fireworks.isHotbar()) {
               InvUtils.move().from(fireworks.slot()).toHotbar(this.elytraFly.replenishSlot.get() - 1);
            }
         }

         if (this.elytraFly.replace.get()) {
            ItemStack chestStack = (ItemStack)((IPlayerInventory)this.mc.player.getInventory()).revive$getArmor().get(2);
            if (chestStack.getItem() == Items.ELYTRA && chestStack.getMaxDamage() - chestStack.getDamage() <= this.elytraFly.replaceDurability.get()) {
               FindItemResult elytra = InvUtils.find(
                  stack -> stack.getMaxDamage() - stack.getDamage() > this.elytraFly.replaceDurability.get() && stack.getItem() == Items.ELYTRA
               );
               if (elytra.found()) {
                  InvUtils.move().from(elytra.slot()).toArmor(2);
               }
            }
         }
      }
   }

   public void onPreTick() {
   }

   public void onPacketSend(PacketEvent.Send event) {
   }

   public void onPacketReceive(PacketEvent.Receive event) {
   }

   public void onPlayerMove() {
   }

   public void handleMotionYBoost(PlayerMoveEvent event) {
   }

   public void onChunkData(ChunkDataEvent event) {
   }

   public void onActivate() {
      this.lastJumpPressed = false;
      this.jumpTimer = 0;
      this.ticksLeft = 0.0;
      this.acceleration = 0.0;
   }

   public void onDeactivate() {
   }

   public void onCorrected(Vec3d offset) {
   }

   public void autoTakeoff() {
      if (this.incrementJumpTimer) {
         this.jumpTimer++;
      }

      boolean jumpPressed = this.mc.options.jumpKey.isPressed();
      if (this.elytraFly.autoTakeOff.get() && jumpPressed) {
         if (!this.lastJumpPressed && !this.mc.player.isGliding()) {
            this.jumpTimer = 0;
            this.incrementJumpTimer = true;
         }

         if (this.jumpTimer >= 8) {
            this.jumpTimer = 0;
            this.incrementJumpTimer = false;
            this.mc.player.setJumping(false);
            this.mc.player.setSprinting(true);
            ((LivingEntityAccessor)this.mc.player).revive$jump();
            this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
         }
      }

      this.lastJumpPressed = jumpPressed;
   }

   public void handleAutopilot() {
      if (this.mc.player.isGliding()) {
         if (this.elytraFly.autoPilot.get() && this.mc.player.getY() > this.elytraFly.autoPilotMinimumHeight.get()) {
            this.mc.options.forwardKey.setPressed(true);
            this.lastForwardPressed = true;
         }

         if (this.elytraFly.useFireworks.get() && !PbPredict.serverPushesOnly()) {
            if (this.ticksLeft <= 0.0) {
               ElytraDurabilitySaver saver = ElytraDurabilitySaver.get();
               if (!saver.serverGlideSteady()) {
                  saver.holdForRocket();
                  return;
               }

               this.ticksLeft = this.elytraFly.autoPilotFireworkDelay.get() * 20.0;
               FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
               if (!itemResult.found()) {
                  return;
               }

               if (itemResult.isOffhand()) {
                  this.mc.interactionManager.interactItem(this.mc.player, Hand.OFF_HAND);
                  this.mc.player.swingHand(Hand.OFF_HAND);
               } else {
                  InvUtils.swap(itemResult.slot(), true);
                  this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
                  InvUtils.swapBack();
               }

               saver.rocketUsed();
            }

            this.ticksLeft--;
         }
      }
   }

   public void handleHorizontalSpeed(PlayerMoveEvent event) {
      boolean a = false;
      boolean b = false;
      if (this.mc.options.forwardKey.isPressed()) {
         this.velX = this.velX + this.forward.x * this.getSpeed() * 10.0;
         this.velZ = this.velZ + this.forward.z * this.getSpeed() * 10.0;
         a = true;
      } else if (this.mc.options.backKey.isPressed()) {
         this.velX = this.velX - this.forward.x * this.getSpeed() * 10.0;
         this.velZ = this.velZ - this.forward.z * this.getSpeed() * 10.0;
         a = true;
      }

      if (this.mc.options.rightKey.isPressed()) {
         this.velX = this.velX + this.right.x * this.getSpeed() * 10.0;
         this.velZ = this.velZ + this.right.z * this.getSpeed() * 10.0;
         b = true;
      } else if (this.mc.options.leftKey.isPressed()) {
         this.velX = this.velX - this.right.x * this.getSpeed() * 10.0;
         this.velZ = this.velZ - this.right.z * this.getSpeed() * 10.0;
         b = true;
      }

      if (a && b) {
         double diagonal = 1.0 / Math.sqrt(2.0);
         this.velX *= diagonal;
         this.velZ *= diagonal;
      }
   }

   public void handleVerticalSpeed(PlayerMoveEvent event) {
      if (this.mc.options.jumpKey.isPressed()) {
         this.velY = this.velY + 0.5 * this.elytraFly.verticalSpeed.get();
      } else if (this.mc.options.sneakKey.isPressed()) {
         this.velY = this.velY - 0.5 * this.elytraFly.verticalSpeed.get();
      }
   }

   public void handleFallMultiplier() {
      if (this.velY < 0.0) {
         this.velY = this.velY * this.elytraFly.fallMultiplier.get();
      } else if (this.velY > 0.0) {
         this.velY = 0.0;
      }
   }

   public void handleAcceleration() {
      if (this.elytraFly.acceleration.get()) {
         if (!PlayerUtils.isMoving()) {
            this.acceleration = 0.0;
         }

         this.acceleration = Math.min(
            this.acceleration + this.elytraFly.accelerationMin.get() + this.elytraFly.accelerationStep.get() * 0.1, this.elytraFly.horizontalSpeed.get()
         );
      } else {
         this.acceleration = 0.0;
      }
   }

   public void zeroAcceleration() {
      this.acceleration = 0.0;
   }

   protected double getSpeed() {
      return this.elytraFly.acceleration.get() ? this.acceleration : this.elytraFly.horizontalSpeed.get();
   }

   public String getHudString() {
      return this.type.name();
   }
}
