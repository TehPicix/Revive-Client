package revivedevelopment.reviveclient.systems.modules.movement;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.Rotations;

public class BoatFly extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPhase = this.settings.createGroup("Phase");
   private final SettingGroup sgRemount = this.settings.createGroup("Remount");
   private final SettingGroup sgGhost = this.settings.createGroup("Ghost Lane (experimental)");
   private final Setting<BoatFly.VerticalMode> verticalMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("vertical-mode"))
                  .description(
                     "How the boat handles up/down. Level: never touch vertical — the boat rides at its natural altitude on vanilla gravity and only WASD drives it, which is legal motion Grim accepts, so you actually travel instead of rubber-banding (the reliable default). Creep: jump/sneak add a tiny capped vertical (creep-speed) meant to slip under Grim's vehicle uncertainty for a slow climb — experimental, may still eject. Full: the old unrestricted vertical-speed/fall-speed — blatant, Grim setbacks it on sight, kept for non-anticheat servers."
                  ))
               .defaultValue(BoatFly.VerticalMode.Level))
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description(
               "Horizontal drive speed in blocks per tick (0.5 ≈ 10 b/s, 1.0 ≈ 20 b/s). Open-air only — while the boat touches blocks the phase-speed clamp takes over. Grim also caps boat speed, so back this off if level travel starts rubber-banding. Negative values are allowed for experimenting (reverses direction)."
            )
            .defaultValue(0.5)
            .min(-5.0)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<Double> creepSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("creep-speed")
            .description(
               "Per-tick vertical in Creep mode — jump rises, sneak descends. Kept tiny to stay inside Grim's per-tick vehicle uncertainty; larger values just trip the setback. Expect a slow climb at best, and some ejects regardless. Negatives allowed for experimenting (inverts jump/sneak)."
            )
            .defaultValue(0.04)
            .min(-0.1)
            .max(0.1)
            .sliderRange(-0.1, 0.1)
            .visible(() -> this.verticalMode.get() == BoatFly.VerticalMode.Creep)
            .build()
      );
   private final Setting<Double> verticalSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("vertical-speed")
            .description(
               "Full mode only. Vertical speed in blocks per tick — jump rises, sneak descends. Ascent is what Grim ejects for most reliably; smaller values plus auto-remount climb more smoothly than big ones. Negatives allowed for experimenting."
            )
            .defaultValue(0.3)
            .min(-3.0)
            .sliderMax(3.0)
            .visible(() -> this.verticalMode.get() == BoatFly.VerticalMode.Full)
            .build()
      );
   private final Setting<Double> fallSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fall-speed")
            .description(
               "Full mode only. Forced downward drift in blocks per tick while idle. 0 does NOT override an idle boat at all — it coasts on vanilla gravity, which matches the server's re-simulation. Any value above 0 forces a constant descent that ignores gravity's acceleration and diverges from the server within a few ticks (the eject-prone hover/drift behaviour). Level and Creep always coast when idle regardless of this."
            )
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(1.0)
            .visible(() -> this.verticalMode.get() == BoatFly.VerticalMode.Full)
            .build()
      );
   private final Setting<Double> acceleration = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("acceleration")
            .description(
               "Most the velocity may change per tick, so speed ramps up and down instead of snapping. Vanilla boats accelerate gently; instant full speed from rest is an obvious tell."
            )
            .defaultValue(0.12)
            .min(0.02)
            .sliderMax(1.0)
            .build()
      );
   private final Setting<Boolean> faceLook = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("face-look")
            .description("Point the boat the way you're looking so it visually travels bow-first.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> suppressDismount = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("no-sneak-dismount")
            .description(
               "Strip the sneak bit from outgoing input packets while driving, so sneak descends instead of making the server dismount you. Disable the module to get out of the boat."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> phase = this.sgPhase
      .add(
         new BoolSetting.Builder()
            .name("phase")
            .description(
               "No-clip the boat through blocks. Speed through solids is capped by phase-speed — that cap is what keeps the server accepting the movement."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> phaseSpeed = this.sgPhase
      .add(
         new DoubleSetting.Builder()
            .name("phase-speed")
            .description(
               "Per-tick movement cap while the boat's path intersects blocks — applied even with phase off, since an embedded boat has the same acceptance problem. The server allows at most a 0.0625-block disagreement per packet; 0.05 leaves margin. Negatives allowed for experimenting (reverses the clamped move)."
            )
            .defaultValue(0.05)
            .min(-0.062)
            .max(0.062)
            .sliderRange(-0.062, 0.062)
            .build()
      );
   private final Setting<Boolean> phaseOnlyMoving = this.sgPhase
      .add(
         new BoolSetting.Builder()
            .name("only-while-moving")
            .description("Only no-clip while a movement key is held, so an idle boat rests on solid ground instead of sinking through it.")
            .defaultValue(true)
            .visible(this.phase::get)
            .build()
      );
   private final Setting<Boolean> ghostLane = this.sgGhost
      .add(
         new BoolSetting.Builder()
            .name("ghost-lane")
            .description(
               "EXPERIMENT. Spawn client-side boats beneath you as a moving 'lane'. IMPORTANT: these boats exist ONLY on your client — the server never hears about them, so Grim can't see them and (per its architecture) shouldn't grant the boat-uncertainty speed the real embedded boats gave you. This is here so you can confirm that on your live server rather than take my word for it. Watch for immediate setback when you push speed over the ghost lane."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> ghostCount = this.sgGhost
      .add(
         new IntSetting.Builder()
            .name("ghost-count")
            .description("How many client-side boats to lay in the lane, spread forward from under you.")
            .defaultValue(3)
            .min(1)
            .sliderMax(12)
            .visible(this.ghostLane::get)
            .build()
      );
   private final Setting<Double> ghostSpacing = this.sgGhost
      .add(
         new DoubleSetting.Builder()
            .name("ghost-spacing")
            .description("Blocks between each ghost boat along your look direction.")
            .defaultValue(1.0)
            .min(0.25)
            .sliderMax(4.0)
            .visible(this.ghostLane::get)
            .build()
      );
   private final Setting<Double> ghostDrop = this.sgGhost
      .add(
         new DoubleSetting.Builder()
            .name("ghost-drop")
            .description("How far below you to place the ghost lane.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderMax(4.0)
            .visible(this.ghostLane::get)
            .build()
      );
   private final Setting<Boolean> autoRemount = this.sgRemount
      .add(
         new BoolSetting.Builder()
            .name("auto-remount")
            .description("When the server ejects you from the boat (Grim's vehicle setback), get back into it automatically while it's in reach.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> remountRotate = this.sgRemount
      .add(
         new BoolSetting.Builder()
            .name("remount-rotate")
            .description(
               "Send a server-side rotation at the boat with the remount interact. Grim cancels interacts on entities you aren't looking at, so without this the remount silently does nothing. Your camera doesn't move."
            )
            .defaultValue(true)
            .visible(this.autoRemount::get)
            .build()
      );
   private final Setting<Integer> remountDelay = this.sgRemount
      .add(
         new IntSetting.Builder()
            .name("remount-delay")
            .description(
               "Ticks to wait after an eject before remounting (and between retries). Too fast and the interact races the eject the server just processed."
            )
            .defaultValue(4)
            .min(1)
            .sliderMax(20)
            .visible(this.autoRemount::get)
            .build()
      );
   private final Setting<Boolean> resyncMove = this.sgRemount
      .add(
         new BoolSetting.Builder()
            .name("resync-move")
            .description(
               "Before sending the remount interact, nudge the player toward the boat for a few ticks. Grim rejects the interact until the player has sent fresh position packets from where it set you back to — the same reason a manual remount only works once you move after being kicked. A still player sends almost no position packets, so the auto-remount never resyncs; this replays that movement automatically."
            )
            .defaultValue(true)
            .visible(this.autoRemount::get)
            .build()
      );
   private final Setting<Integer> resyncTicks = this.sgRemount
      .add(
         new IntSetting.Builder()
            .name("resync-ticks")
            .description(
               "How many ticks to nudge toward the boat before sending the remount interact. Two or three is enough to resync; more also helps close distance if the eject threw you back."
            )
            .defaultValue(3)
            .min(1)
            .sliderMax(20)
            .visible(() -> this.autoRemount.get() && this.resyncMove.get())
            .build()
      );
   private final Setting<Double> resyncSpeed = this.sgRemount
      .add(
         new DoubleSetting.Builder()
            .name("resync-speed")
            .description(
               "Horizontal speed of the resync nudge in blocks per tick. Keep it near normal walking pace (≈0.12) so the movement itself doesn't read as impossible."
            )
            .defaultValue(0.12)
            .min(0.02)
            .sliderMax(0.3)
            .visible(() -> this.autoRemount.get() && this.resyncMove.get())
            .build()
      );
   private final Setting<Integer> resyncPause = this.sgRemount
      .add(
         new IntSetting.Builder()
            .name("resync-pause")
            .description(
               "Ticks to coast on vanilla physics after every (re)mount and after a server vehicle correction, before driving again. Fighting a setback with the next impossible move is what turns one violation into a kick loop."
            )
            .defaultValue(10)
            .min(0)
            .sliderMax(40)
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Log the velocity set on the boat, phase clamping, resync pauses, stripped sneak bits and remount attempts.")
            .defaultValue(false)
            .build()
      );
   private static final double REMOUNT_RANGE = 4.0;
   private AbstractBoatEntity phasedBoat;
   private AbstractBoatEntity lastBoat;
   private int remountCooldown;
   private int remountResyncTicks;
   private volatile int pauseTicks;
   private boolean wasDriving;
   private boolean sendingOwnInput;
   private final List<AbstractBoatEntity> ghostBoats = new ArrayList<>();
   private int ghostIdCounter = -30000;
   private String lastDebug;
   private long lastDebugMs;

   public BoatFly() {
      super(Categories.Movement, "boat-fly", "Fly around in a boat, with optional phase through blocks.");
   }

   @Override
   public void onActivate() {
      this.lastBoat = null;
      this.remountCooldown = 0;
      this.remountResyncTicks = 0;
      this.pauseTicks = 0;
      this.wasDriving = false;
   }

   @Override
   public void onDeactivate() {
      this.clearPhase();
      this.clearGhostLane();
      this.lastBoat = null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.updateGhostLane();
         if (this.mc.player.getVehicle() instanceof AbstractBoatEntity boat) {
            if (boat.getFirstPassenger() != this.mc.player) {
               this.clearPhase();
               this.wasDriving = false;
               this.dbg("riding as passenger, not driving");
            } else {
               this.lastBoat = boat;
               this.remountCooldown = Math.max(this.remountCooldown, this.remountDelay.get());
               if (!this.wasDriving) {
                  this.wasDriving = true;
                  this.pauseTicks = Math.max(this.pauseTicks, this.resyncPause.get());
               }

               if (this.pauseTicks > 0) {
                  this.pauseTicks--;
                  this.clearPhase();
                  this.dbg("resync pause, " + this.pauseTicks + " ticks left");
               } else {
                  PlayerInput input = this.mc.player.input.playerInput;
                  boolean f = input.forward();
                  boolean b = input.backward();
                  boolean l = input.left();
                  boolean r = input.right();
                  boolean j = input.jump();
                  boolean s = input.sneak();
                  boolean horiz = f || b || l || r;
                  boolean anyMove = horiz || j || s;
                  boolean embedded = !this.mc.world.isSpaceEmpty(boat, boat.getBoundingBox());
                  boolean phasing = this.phase.get() && (!this.phaseOnlyMoving.get() || anyMove) || embedded;
                  if (phasing) {
                     boat.noClip = true;
                     if (this.phasedBoat != boat) {
                        this.clearPhase();
                        this.phasedBoat = boat;
                     }
                  } else {
                     this.clearPhase();
                  }

                  Vec3d current = boat.getVelocity();
                  double velX = current.x;
                  double velZ = current.z;
                  if (horiz) {
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
                  }

                  boolean overrideY = false;
                  double velY = current.y;
                  BoatFly.VerticalMode mode = this.verticalMode.get();
                  if (embedded && !s) {
                     velY = 0.1;
                     overrideY = true;
                  } else {
                     switch (mode) {
                        case Level:
                        default:
                           break;
                        case Creep:
                           if (j) {
                              velY = this.creepSpeed.get();
                              overrideY = true;
                           } else if (s) {
                              velY = -this.creepSpeed.get();
                              overrideY = true;
                           }
                           break;
                        case Full:
                           if (j) {
                              velY = this.verticalSpeed.get();
                              overrideY = true;
                           } else if (s) {
                              velY = -this.verticalSpeed.get();
                              overrideY = true;
                           } else if (this.fallSpeed.get() > 0.0) {
                              velY = -this.fallSpeed.get();
                              overrideY = true;
                           }
                     }
                  }

                  if (phasing && !overrideY) {
                     velY = 0.0;
                     overrideY = true;
                  }

                  if (!horiz && !overrideY) {
                     this.dbg("idle, coasting on vanilla physics");
                  } else {
                     double dX = velX - current.x;
                     double dY = velY - current.y;
                     double dZ = velZ - current.z;
                     double change = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
                     double accel = this.acceleration.get();
                     if (change > accel) {
                        double k = accel / change;
                        velX = current.x + dX * k;
                        velY = current.y + dY * k;
                        velZ = current.z + dZ * k;
                     }

                     boolean clamped = false;
                     boolean pathBlocked = phasing && !this.mc.world.isSpaceEmpty(boat, boat.getBoundingBox().stretch(velX, velY, velZ));
                     if (embedded || pathBlocked) {
                        double len = Math.sqrt(velX * velX + velY * velY + velZ * velZ);
                        double cap = this.phaseSpeed.get();
                        if (len > cap) {
                           double scale = cap / len;
                           velX *= scale;
                           velY *= scale;
                           velZ *= scale;
                           clamped = true;
                        }
                     }

                     boat.setVelocity(velX, velY, velZ);
                     if (this.faceLook.get()) {
                        boat.setYaw(this.mc.player.getYaw());
                     }

                     if (this.debug.get()) {
                        this.dbg(
                           String.format(
                              "drive[%s] keys[f%d b%d l%d r%d j%d s%d] vel=(%.3f,%.3f,%.3f)%s%s%s",
                              mode.name().toLowerCase(),
                              f ? 1 : 0,
                              b ? 1 : 0,
                              l ? 1 : 0,
                              r ? 1 : 0,
                              j ? 1 : 0,
                              s ? 1 : 0,
                              velX,
                              velY,
                              velZ,
                              overrideY ? "" : " [y-coast]",
                              phasing ? " [phase]" : "",
                              clamped ? " [clamped]" : ""
                           )
                        );
                     }
                  }
               }
            }
         } else {
            this.clearPhase();
            if (this.wasDriving) {
               this.remountResyncTicks = this.resyncMove.get() ? this.resyncTicks.get() : 0;
               this.remountCooldown = 0;
            }

            this.wasDriving = false;
            this.attemptRemount();
         }
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (!this.sendingOwnInput && this.suppressDismount.get()) {
         if (event.packet instanceof PlayerInputC2SPacket packet) {
            if (this.mc.player != null && this.mc.player.getVehicle() instanceof AbstractBoatEntity boat) {
               if (boat.getFirstPassenger() == this.mc.player) {
                  PlayerInput in = packet.input();
                  if (in.sneak()) {
                     event.setCancelled(true);
                     PlayerInput stripped = new PlayerInput(in.forward(), in.backward(), in.left(), in.right(), in.jump(), false, in.sprint());
                     this.sendingOwnInput = true;

                     try {
                        this.mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(stripped));
                     } finally {
                        this.sendingOwnInput = false;
                     }

                     this.dbg("stripped sneak from input packet");
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      if (event.packet instanceof VehicleMoveS2CPacket) {
         if (this.mc.player != null && this.mc.player.getVehicle() instanceof AbstractBoatEntity boat) {
            if (boat.getFirstPassenger() == this.mc.player) {
               this.pauseTicks = Math.max(this.pauseTicks, this.resyncPause.get());
               this.dbg("server corrected the boat — coasting");
            }
         }
      }
   }

   private void attemptRemount() {
      if (this.autoRemount.get() && this.lastBoat != null && this.mc.interactionManager != null) {
         if (this.lastBoat.isRemoved()) {
            this.lastBoat = null;
            this.remountResyncTicks = 0;
         } else if (this.mc.player.squaredDistanceTo(this.lastBoat) > 16.0) {
            if (this.remountResyncTicks > 0) {
               this.remountResyncTicks--;
               this.nudgeTowardBoat();
               this.dbg("remount: boat out of reach, nudging closer (" + this.remountResyncTicks + " left)");
            } else {
               this.dbg("remount: boat out of reach");
            }
         } else if (this.remountResyncTicks > 0) {
            this.remountResyncTicks--;
            this.nudgeTowardBoat();
            this.dbg("remount: resync nudge, " + this.remountResyncTicks + " ticks left");
         } else if (this.remountCooldown > 0) {
            this.remountCooldown--;
         } else {
            this.remountCooldown = this.remountDelay.get();
            if (!PbPredict.serverPushesOnly()) {
               AbstractBoatEntity boat = this.lastBoat;
               if (this.remountRotate.get()) {
                  Rotations.rotate(Rotations.getYaw(boat), Rotations.getPitch(boat), 50, () -> this.mount(boat));
               } else {
                  this.mount(boat);
               }

               this.dbg("remount: interacting with boat" + (this.remountRotate.get() ? " (rotating)" : ""));
            }
         }
      }
   }

   private void nudgeTowardBoat() {
      if (this.mc.player != null && this.lastBoat != null) {
         double dx = this.lastBoat.getX() - this.mc.player.getX();
         double dz = this.lastBoat.getZ() - this.mc.player.getZ();
         double horiz = Math.sqrt(dx * dx + dz * dz);
         double nudge = this.resyncSpeed.get();
         double vx;
         double vz;
         if (horiz > 1.0E-4) {
            vx = dx / horiz * nudge;
            vz = dz / horiz * nudge;
         } else {
            double yawRad = Math.toRadians(this.mc.player.getYaw());
            vx = -MathHelper.sin((float)yawRad) * nudge;
            vz = MathHelper.cos((float)yawRad) * nudge;
         }

         Vec3d v = this.mc.player.getVelocity();
         this.mc.player.setVelocity(vx, v.y, vz);
      }
   }

   private void mount(AbstractBoatEntity boat) {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         if (!boat.isRemoved() && !this.mc.player.hasVehicle()) {
            this.mc.interactionManager.interactEntity(this.mc.player, boat, Hand.MAIN_HAND);
            this.mc.player.swingHand(Hand.MAIN_HAND);
         }
      }
   }

   private void clearPhase() {
      if (this.phasedBoat != null) {
         this.phasedBoat.noClip = false;
         this.phasedBoat = null;
      }
   }

   private void updateGhostLane() {
      if (this.ghostLane.get() && this.mc.player != null && this.mc.world != null) {
         AbstractBoatEntity template = this.lastBoat != null ? this.lastBoat : (this.mc.player.getVehicle() instanceof AbstractBoatEntity b ? b : null);
         if (template == null) {
            this.clearGhostLane();
            this.dbg("ghost lane: sit in a boat once so I can clone its type");
         } else {
            int count = this.ghostCount.get();

            while (this.ghostBoats.size() < count) {
               AbstractBoatEntity g = this.spawnGhostBoat(template);
               if (g == null) {
                  this.dbg("ghost lane: spawn failed, see previous line");
                  break;
               }

               this.ghostBoats.add(g);
            }

            while (this.ghostBoats.size() > count) {
               this.removeGhost(this.ghostBoats.remove(this.ghostBoats.size() - 1));
            }

            double yawRad = Math.toRadians(this.mc.player.getYaw());
            double fx = -MathHelper.sin((float)yawRad);
            double fz = MathHelper.cos((float)yawRad);
            double px = this.mc.player.getX();
            double py = this.mc.player.getY() - this.ghostDrop.get();
            double pz = this.mc.player.getZ();
            double spacing = this.ghostSpacing.get();

            for (int i = 0; i < this.ghostBoats.size(); i++) {
               AbstractBoatEntity g = this.ghostBoats.get(i);
               if (g == null || g.isRemoved()) {
                  g = this.spawnGhostBoat(template);
                  if (g == null) {
                     continue;
                  }

                  this.ghostBoats.set(i, g);
               }

               double off = i * spacing;
               g.setVelocity(0.0, 0.0, 0.0);
               g.refreshPositionAndAngles(px + fx * off, py, pz + fz * off, this.mc.player.getYaw(), 0.0F);
            }

            this.dbg("ghost lane: " + this.ghostBoats.size() + " client-only boats under you (Grim can't see these)");
         }
      } else {
         this.clearGhostLane();
      }
   }

   private AbstractBoatEntity spawnGhostBoat(AbstractBoatEntity template) {
      try {
         Class<?> cls = template.getClass();
         Constructor<?> ctor = null;

         for (Constructor<?> c : cls.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length >= 2 && p[0].isAssignableFrom(EntityType.class) && p[1].isAssignableFrom(World.class)) {
               ctor = c;
               break;
            }
         }

         if (ctor == null) {
            this.dbg("ghost boat: no (EntityType, World, ...) constructor on " + cls.getSimpleName());
            return null;
         } else {
            Class<?>[] p = ctor.getParameterTypes();
            Object[] args = new Object[p.length];
            args[0] = template.getType();
            args[1] = this.mc.world;

            for (int i = 2; i < p.length; i++) {
               args[i] = Supplier.class.isAssignableFrom(p[i]) ? (Supplier<?>)() -> null : null;
            }

            AbstractBoatEntity g = (AbstractBoatEntity)ctor.newInstance(args);
            g.setId(this.ghostIdCounter--);
            g.noClip = true;
            this.mc.world.addEntity(g);
            return g;
         }
      } catch (Throwable var9) {
         this.dbg("ghost boat spawn failed on " + template.getClass().getSimpleName() + " (" + var9.getClass().getSimpleName() + "): " + var9.getMessage());
         return null;
      }
   }

   private void removeGhost(AbstractBoatEntity g) {
      if (g != null) {
         if (this.mc.world != null && !g.isRemoved()) {
            this.mc.world.removeEntity(g.getId(), RemovalReason.DISCARDED);
         }

         g.setRemoved(RemovalReason.DISCARDED);
      }
   }

   private void clearGhostLane() {
      if (!this.ghostBoats.isEmpty()) {
         for (AbstractBoatEntity g : this.ghostBoats) {
            this.removeGhost(g);
         }

         this.ghostBoats.clear();
      }
   }

   @Override
   public String getInfoString() {
      String v = this.verticalMode.get().name().toLowerCase();
      return this.phase.get() ? v + "+phase" : v;
   }

   private void dbg(String msg) {
      if (this.debug.get()) {
         long now = System.currentTimeMillis();
         if (!msg.equals(this.lastDebug) || now - this.lastDebugMs >= 1000L) {
            this.lastDebug = msg;
            this.lastDebugMs = now;
            ChatUtils.infoPrefix("BoatFly", "%s", msg);
         }
      }
   }

   public static enum VerticalMode {
      Level,
      Creep,
      Full;
   }
}
