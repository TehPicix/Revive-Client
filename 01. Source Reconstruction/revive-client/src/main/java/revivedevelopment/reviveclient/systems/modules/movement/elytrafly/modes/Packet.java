package revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.mixininterface.IPlayerInventory;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightModes;

public class Packet extends ElytraFlightMode {
   private final Vec3d vec3d = new Vec3d(0.0, 0.0, 0.0);

   public Packet() {
      super(ElytraFlightModes.Packet);
   }

   @Override
   public void onDeactivate() {
      this.mc.player.getAbilities().flying = false;
      this.mc.player.getAbilities().allowFlying = false;
   }

   @Override
   public void onTick() {
      super.onTick();
      if (((ItemStack)((IPlayerInventory)this.mc.player.getInventory()).revive$getArmor().get(2)).getItem() == Items.ELYTRA
         && !(this.mc.player.fallDistance <= 0.2)
         && !this.mc.options.sneakKey.isPressed()) {
         if (this.mc.options.forwardKey.isPressed()) {
            this.vec3d.add(0.0, 0.0, this.elytraFly.horizontalSpeed.get());
            this.vec3d.rotateY(-((float)Math.toRadians(this.mc.player.getYaw())));
         } else if (this.mc.options.backKey.isPressed()) {
            this.vec3d.add(0.0, 0.0, this.elytraFly.horizontalSpeed.get());
            this.vec3d.rotateY((float)Math.toRadians(this.mc.player.getYaw()));
         }

         if (this.mc.options.jumpKey.isPressed()) {
            this.vec3d.add(0.0, this.elytraFly.verticalSpeed.get(), 0.0);
         } else if (!this.mc.options.jumpKey.isPressed()) {
            this.vec3d.add(0.0, -this.elytraFly.verticalSpeed.get(), 0.0);
         }

         this.mc.player.setVelocity(this.vec3d);
         this.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
         this.mc.player.networkHandler.sendPacket(new OnGroundOnly(true, this.mc.player.horizontalCollision));
      }
   }

   @Override
   public void onPacketSend(PacketEvent.Send event) {
      if (event.packet instanceof PlayerMoveC2SPacket) {
         this.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
      }
   }

   @Override
   public void onPlayerMove() {
      this.mc.player.getAbilities().flying = true;
      this.mc.player.getAbilities().setFlySpeed(this.elytraFly.horizontalSpeed.get().floatValue() / 20.0F);
   }
}
