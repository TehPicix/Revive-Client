package revivedevelopment.reviveclient.systems.modules.movement;

import java.util.HashSet;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.PlayerInteractEntityC2SPacketAccessor;
import revivedevelopment.reviveclient.mixininterface.IHorseBaseEntity;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class EntityControl extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> forceMount = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("force-mount")
            .description(
               "Cancel the interact-at packet on horses so the plain mount interaction goes through — lets you ride entities that normally aren't rideable."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("How close a horse must be for its saddled flag to be spoofed.")
            .defaultValue(8.0)
            .min(1.0)
            .sliderMax(16.0)
            .build()
      );
   private final Setting<Boolean> control = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("control")
            .description(
               "Drive the entity you're riding with WASD in the direction you look, at the speed below — works on any vehicle, and overrides a mount's normal speed so you travel fast."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description(
               "Horizontal drive speed in blocks per tick (0.5 ≈ 10 b/s, 1.0 ≈ 20 b/s). Higher is faster but more likely to trip Grim's speed checks and setback you."
            )
            .defaultValue(0.5)
            .min(0.05)
            .sliderMax(3.0)
            .visible(this.control::get)
            .build()
      );
   private final Setting<Boolean> fly = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("fly")
            .description(
               "Give the ridden entity vertical control — jump rises, sneak descends, idle hovers. Blatant on ground mounts; high setback/flag risk on anticheat servers."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> flySpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fly-speed")
            .description("Vertical speed in blocks per tick while flying.")
            .defaultValue(0.5)
            .min(0.05)
            .sliderMax(3.0)
            .visible(this.fly::get)
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description(
               "Log the interact packets sent to horses (type, saddled state, whether force-mount cancelled it) and the number of horses spoofed in range."
            )
            .defaultValue(false)
            .build()
      );
   private final Set<AbstractHorseEntity> spoofed = new HashSet<>();
   private String lastDebug;
   private long lastDebugMs;

   public EntityControl() {
      super(Categories.Movement, "entity-control", "Control mountable entities (force-mount horses).");
   }

   @Override
   public void onDeactivate() {
      for (AbstractHorseEntity horse : this.spoofed) {
         if (horse != null) {
            ((IHorseBaseEntity)horse).setSaddled(false);
         }
      }

      this.spoofed.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.spoofed.removeIf(horsex -> horsex.isRemoved() || horsex.getEntityWorld() != this.mc.world);
         double rangeSq = this.range.get() * this.range.get();
         int spoofedInRange = 0;

         for (Entity entity : this.mc.world.getEntities()) {
            if (entity instanceof AbstractHorseEntity horse && !(horse.squaredDistanceTo(this.mc.player) > rangeSq)) {
               if (!horse.hasSaddleEquipped()) {
                  this.spoofed.add(horse);
               }

               ((IHorseBaseEntity)horse).setSaddled(true);
               spoofedInRange++;
            }
         }

         if (spoofedInRange > 0) {
            this.dbg("spoofing " + spoofedInRange + " horse(s) in range");
         }

         this.driveVehicle();
      }
   }

   private void driveVehicle() {
      if (this.control.get() || this.fly.get()) {
         if (this.mc.player != null && this.mc.player.hasVehicle()) {
            Entity vehicle = this.mc.player.getVehicle();
            if (vehicle != null) {
               PlayerInput input = this.mc.player.input.playerInput;
               boolean f = input.forward();
               boolean b = input.backward();
               boolean l = input.left();
               boolean r = input.right();
               boolean j = input.jump();
               boolean s = input.sneak();
               Vec3d vel = vehicle.getVelocity();
               double velX = vel.x;
               double velY = vel.y;
               double velZ = vel.z;
               boolean changed = false;
               if (this.control.get() && (f || b || l || r)) {
                  double forward = (f ? 1.0 : 0.0) - (b ? 1.0 : 0.0);
                  double strafe = (l ? 1.0 : 0.0) - (r ? 1.0 : 0.0);
                  if (forward != 0.0 && strafe != 0.0) {
                     forward *= 0.7071;
                     strafe *= 0.7071;
                  }

                  double yawRad = Math.toRadians(this.mc.player.getYaw());
                  double sin = MathHelper.sin((float)yawRad);
                  double cos = MathHelper.cos((float)yawRad);
                  double spd = this.speed.get();
                  velX = (strafe * cos - forward * sin) * spd;
                  velZ = (forward * cos + strafe * sin) * spd;
                  changed = true;
               }

               if (this.fly.get() && (j || s)) {
                  velY = j ? this.flySpeed.get() : -this.flySpeed.get();
                  changed = true;
               }

               if (!changed) {
                  if (this.debug.get()) {
                     this.dbg("drive: riding " + vehicle.getType().getName().getString() + ", idle (not overriding)");
                  }
               } else {
                  vehicle.setVelocity(velX, velY, velZ);
                  Vec3d after = vehicle.getVelocity();
                  if (this.debug.get()) {
                     this.dbg(
                        String.format(
                           "drive %s keys[f%d b%d l%d r%d j%d s%d] set=(%.2f,%.2f,%.2f) readback=(%.2f,%.2f,%.2f)",
                           vehicle.getType().getName().getString(),
                           f ? 1 : 0,
                           b ? 1 : 0,
                           l ? 1 : 0,
                           r ? 1 : 0,
                           j ? 1 : 0,
                           s ? 1 : 0,
                           velX,
                           velY,
                           velZ,
                           after.x,
                           after.y,
                           after.z
                        )
                     );
                  }
               }
            }
         } else {
            this.dbg("drive: not riding a vehicle");
         }
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (event.packet instanceof PlayerInteractEntityC2SPacket packet) {
            PlayerInteractEntityC2SPacketAccessor var8 = (PlayerInteractEntityC2SPacketAccessor)packet;
            if (this.mc.world.getEntityById(var8.revive$getEntityId()) instanceof AbstractHorseEntity horse) {
               String type = var8.revive$getType().getType().name();
               boolean cancel = this.forceMount.get() && "INTERACT_AT".equals(type);
               if (cancel) {
                  event.setCancelled(true);
               }

               this.dbg(
                  "interact "
                     + type
                     + " -> horse #"
                     + var8.revive$getEntityId()
                     + " (saddled="
                     + horse.hasSaddleEquipped()
                     + ")"
                     + (cancel ? " [force-mount: CANCELLED]" : "")
               );
            }
         }
      }
   }

   private void dbg(String msg) {
      if (this.debug.get()) {
         long now = System.currentTimeMillis();
         if (!msg.equals(this.lastDebug) || now - this.lastDebugMs >= 1000L) {
            this.lastDebug = msg;
            this.lastDebugMs = now;
            ChatUtils.infoPrefix("EntityControl", "%s", msg);
         }
      }
   }
}
