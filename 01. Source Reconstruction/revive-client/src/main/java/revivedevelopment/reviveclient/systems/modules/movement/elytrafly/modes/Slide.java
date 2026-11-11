package revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightModes;

public class Slide extends ElytraFlightMode {
   boolean rubberbanded = false;
   int tickDelay = this.elytraFly.restartDelay.get();

   public Slide() {
      super(ElytraFlightModes.Slide);
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.mc.options.jumpKey.isPressed() && !this.mc.player.isGliding()) {
         this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
      }

      if (checkConditions(this.mc.player) && this.mc.player.isOnGround()) {
         double yaw = Math.toRadians(this.mc.player.getYaw());
         double speedFactor = Math.max(
            0.1,
            Math.min(
               1.0, (100.0 * this.elytraFly.slideAccel.get() / 20.0 - this.mc.player.getVelocity().length()) / (100.0 * this.elytraFly.slideAccel.get() / 20.0)
            )
         );
         Vec3d dir = new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
         this.mc.player.addVelocity(dir.multiply(this.elytraFly.slideMaxSpeed.get() / 2000.0 / speedFactor));
         if (this.rubberbanded && this.elytraFly.restart.get()) {
            if (this.tickDelay > 0) {
               this.tickDelay--;
            } else {
               this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
               this.rubberbanded = false;
               this.tickDelay = this.elytraFly.restartDelay.get();
            }
         }
      }
   }

   @Override
   public void onPreTick() {
      super.onPreTick();
   }

   @Override
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.rubberbanded = true;
         this.mc.player.stopGliding();
      }
   }

   @Override
   public void onPacketSend(PacketEvent.Send event) {
      if (event.packet instanceof ClientCommandC2SPacket
         && ((ClientCommandC2SPacket)event.packet).getMode().equals(Mode.START_FALL_FLYING)
         && !this.elytraFly.sprint.get()) {
         this.mc.player.setSprinting(true);
      }
   }

   public static boolean recastElytra(ClientPlayerEntity player) {
      if (checkConditions(player) && ignoreGround(player)) {
         player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
         return true;
      } else {
         return false;
      }
   }

   public static boolean checkConditions(ClientPlayerEntity player) {
      ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
      return !player.hasVehicle() && !player.isClimbing() && itemStack.isOf(Items.ELYTRA) && isElytraUsable(itemStack);
   }

   private static boolean isElytraUsable(ItemStack stack) {
      return stack.get(DataComponentTypes.DAMAGE) == null || stack.getMaxDamage() - (Integer)stack.get(DataComponentTypes.DAMAGE) > 1;
   }

   private static boolean ignoreGround(ClientPlayerEntity player) {
      if (!player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.LEVITATION)) {
         ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
         if (itemStack.isOf(Items.ELYTRA) && isElytraUsable(itemStack)) {
            player.startGliding();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public void onActivate() {
   }

   @Override
   public void onDeactivate() {
      this.rubberbanded = false;
   }
}
