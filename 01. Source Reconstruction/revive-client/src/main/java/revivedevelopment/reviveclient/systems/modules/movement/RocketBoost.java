package revivedevelopment.reviveclient.systems.modules.movement;

import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.FireworkRocketEntityAccessor;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.BaritoneBridge;

public class RocketBoost extends Module {
   private static final double MAX_PLAUSIBLE_MOVEMENT = 40.0;
   private static final double ANTI_TICK_SKIPPING = 0.05;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final Setting<Double> speed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description(
               "Ceiling on the resulting velocity, in blocks per tick (1 b/t is 72 km/h). A limiter, not a target - the ride is clamped back inside the window, so this can only make you slower, never illegal."
            )
            .defaultValue(9.5)
            .min(0.1)
            .max(20.0)
            .sliderRange(1.7, 10.0)
            .build()
      );
   private final Setting<Double> amount = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("amount")
            .description(
               "Size of the accepted box to ride, in the server's own units. 1.7 is the server's own constant and the fastest setting that never flags. Above it is a cliff (setbacks), not a slope; drop to 1.68 if you ever see one."
            )
            .defaultValue(1.7)
            .min(0.1)
            .max(2.0)
            .sliderRange(1.0, 1.7)
            .build()
      );
   private final Setting<Double> alignment = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("alignment")
            .description(
               "How far off your aim (degrees) the boost may fly to reach a faster point of the box. Saturated by ~15 degrees; speed only, never legality."
            )
            .defaultValue(15.0)
            .min(0.0)
            .max(90.0)
            .sliderRange(0.0, 45.0)
            .build()
      );
   private final Setting<Boolean> requireInput = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("require-input")
            .description("Only boost while holding a movement key. Off by default - plain gliding needs no key held.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Report once a second what the boost is doing, and why it is idle when it is.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> wallCheck = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("wall-check")
            .description("Throttle the declared speed when a block is coming up in the look direction.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> lookahead = this.sgSafety
      .add(
         new IntSetting.Builder()
            .name("lookahead")
            .description("Ticks of clear space to keep ahead. The speed is capped so obstacles stay at least this far away.")
            .defaultValue(10)
            .range(2, 40)
            .sliderRange(2, 40)
            .build()
      );
   private final Setting<Boolean> chunkCheck = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("chunk-check")
            .description("Throttle the speed when flying towards chunks that have not loaded yet.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> pauseInFluid = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("pause-in-fluid")
            .description("Fall back to vanilla gliding in water or lava, where the server predicts a different movement.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> baritoneSync = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("baritone-sync")
            .description(
               "Fly exactly where Baritone's elytra is aiming while it is flying you (pins alignment to 0), instead of swinging off its cleared line into terrain. Also counts Baritone's flight as travelling."
            )
            .defaultValue(true)
            .build()
      );
   private Vec3d prevPos = null;
   private Vec3d lastGlidePos = null;
   private volatile boolean repositioned = false;
   private boolean windowOpen = false;
   private int latchedRocketId = -1;
   private int latchGraceTicks = 0;
   private int suppressTicks = 0;
   private int travellingTicks = 0;
   private String state = "not gliding";
   private String limiter = "speed";
   private int appliedInWindow = 0;
   private int debugTicks = 0;
   private double appliedSpeed = 0.0;
   private double appliedOffAim = 0.0;
   private boolean syncedToBaritone = false;

   public RocketBoost() {
      super(
         Categories.Movement,
         "rocket-boost",
         "Declares the velocity the server will still accept while a firework is lit. It never throws fireworks itself - light them yourself or let a flight module do it."
      );
   }

   @Override
   public void onActivate() {
      this.prevPos = this.mc.player == null ? null : this.mc.player.getEntityPos();
      this.lastGlidePos = null;
      this.repositioned = false;
      this.windowOpen = false;
      this.latchedRocketId = -1;
      this.latchGraceTicks = 0;
      this.suppressTicks = 0;
      this.travellingTicks = 0;
      this.state = "not gliding";
      this.appliedInWindow = 0;
      this.debugTicks = 0;
   }

   @Override
   public void onDeactivate() {
      this.windowOpen = false;
      this.latchedRocketId = -1;
      this.latchGraceTicks = 0;
      this.prevPos = null;
      this.lastGlidePos = null;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.repositioned = true;
      }
   }

   public void onCorrected(Vec3d offset) {
      if (this.prevPos != null) {
         this.prevPos = this.prevPos.add(offset);
      }

      if (this.lastGlidePos != null) {
         this.lastGlidePos = this.lastGlidePos.add(offset);
      }
   }

   @EventHandler
   private void onTickPre(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (!this.mc.player.isGliding()) {
            this.lastGlidePos = null;
         }

         this.trackWindow();
         if (this.suppressTicks > 0) {
            this.suppressTicks--;
         }

         if (this.travellingTicks > 0) {
            this.travellingTicks--;
         }
      }
   }

   private void trackWindow() {
      if (!this.mc.player.isGliding()) {
         this.windowOpen = false;
         this.latchedRocketId = -1;
         this.latchGraceTicks = 0;
      } else {
         FireworkRocketEntity attached = this.findAttachedRocket();
         if (attached != null) {
            this.latchedRocketId = attached.getId();
            this.latchGraceTicks = 1;
            this.windowOpen = true;
         } else if (this.mc.world.getEntityById(this.latchedRocketId) instanceof FireworkRocketEntity firework && firework.isAlive()) {
            this.windowOpen = true;
         } else if (this.latchGraceTicks > 0) {
            this.latchGraceTicks--;
            this.windowOpen = true;
         } else {
            this.windowOpen = false;
            this.latchedRocketId = -1;
         }
      }
   }

   @EventHandler
   private void onTickPost(TickEvent.Post event) {
      if (this.mc.player == null) {
         this.prevPos = null;
      } else {
         Vec3d vanilla = this.mc.player.getVelocity();
         Vec3d boosted = this.glideVelocity(vanilla, vanilla);
         if (boosted != null) {
            this.mc.player.setVelocity(boosted);
         }

         Vec3d pos = this.mc.player.getEntityPos();
         Vec3d travelled = this.prevPos == null ? Vec3d.ZERO : pos.subtract(this.prevPos);
         if (travelled.length() > 40.0) {
            travelled = Vec3d.ZERO;
         }

         this.prevPos = pos;
         if (this.debug.get() && ++this.debugTicks >= 20) {
            double bpt = travelled.length();
            if (this.appliedInWindow > 0) {
               String past = this.amount.get() > 1.7 ? " - OUTSIDE the window, expect setbacks" : "";
               this.info(
                  "(highlight)%.0f(default) km/h / %.0f b/s (%.2f b/t) - boosting %d/%d ticks at %.2f, %.1f deg off aim, limited by %s%s%s",
                  new Object[]{
                     bpt * 72.0,
                     bpt * 20.0,
                     bpt,
                     this.appliedInWindow,
                     this.debugTicks,
                     this.appliedSpeed,
                     this.appliedOffAim,
                     this.limiter,
                     past,
                     this.syncedToBaritone ? " - straightened onto Baritone's line" : ""
                  }
               );
            } else {
               this.info("(highlight)%.0f(default) km/h / %.0f b/s (%.2f b/t) - idle: %s", new Object[]{bpt * 72.0, bpt * 20.0, bpt, this.state});
            }

            this.debugTicks = 0;
            this.appliedInWindow = 0;
         }
      }
   }

   public Vec3d glideVelocity(Vec3d oldVelocity, Vec3d vanilla) {
      if (this.mc.player != null && this.mc.world != null) {
         Vec3d pos = this.mc.player.getEntityPos();
         Vec3d start = this.lastGlidePos == null ? oldVelocity : pos.subtract(this.lastGlidePos);
         boolean desynced = this.lastGlidePos != null && (this.repositioned || start.length() > 40.0);
         this.repositioned = false;
         this.lastGlidePos = pos;
         if (desynced) {
            return this.note("resyncing after a teleport");
         } else if (!this.mc.player.isGliding()) {
            return this.note("not gliding");
         } else if (!this.allowed()) {
            return this.note("holding still - press a movement key");
         } else if (!this.windowOpen) {
            return this.note("no live rocket");
         } else if (!this.pauseInFluid.get() || !this.mc.player.isTouchingWater() && !this.mc.player.isInLava()) {
            float pitch = this.mc.player.getPitch();
            Vec3d look = dirFromRotation(pitch, this.mc.player.getYaw());
            if (look.lengthSquared() < 1.0E-9) {
               return this.note("no look direction");
            } else {
               Vec3d velocity = this.rideBox(start, look, pitch, this.throttledSpeed(look));
               if (velocity == null) {
                  return this.note("no room in the window");
               } else if (velocity.dotProduct(look) <= vanilla.dotProduct(look)) {
                  return this.note("vanilla is faster on this heading");
               } else {
                  this.state = "boosting";
                  this.syncedToBaritone = this.baritoneSync.get() && this.baritoneFlying();
                  this.appliedSpeed = velocity.length();
                  this.appliedOffAim = Math.toDegrees(Math.acos(MathHelper.clamp(velocity.dotProduct(look) / Math.max(this.appliedSpeed, 1.0E-9), -1.0, 1.0)));
                  this.appliedInWindow++;
                  return velocity;
               }
            }
         } else {
            return this.note("in fluid");
         }
      } else {
         return null;
      }
   }

   private Vec3d note(String why) {
      this.state = why;
      return null;
   }

   private Vec3d rideBox(Vec3d start, Vec3d look, float pitch, double cap) {
      Vec3d lastLook = dirFromRotation(ReviveClient.ROTATION.getServerPitch(), ReviveClient.ROTATION.getServerYaw());
      return rideWindow(start, look, lastLook, pitch, this.effectiveGravity(start.y), this.amount.get(), 0.05, this.effectiveAlignment(), cap);
   }

   public static Vec3d rideWindow(
      Vec3d start, Vec3d look, Vec3d lastLook, float pitch, double gravity, double threshold, double anti, double alignmentDeg, double cap
   ) {
      double[] box = axisBox(look, lastLook, threshold, anti);
      Vec3d predicted = predictGliding(start, look, pitch, gravity);
      double[] w = new double[]{
         predicted.x + Math.min(0.0, box[0] - start.x),
         predicted.y + Math.min(0.0, box[1] - start.y),
         predicted.z + Math.min(0.0, box[2] - start.z),
         predicted.x + Math.max(0.0, box[3] - start.x),
         predicted.y + Math.max(0.0, box[4] - start.y),
         predicted.z + Math.max(0.0, box[5] - start.z)
      };
      Vec3d heading = heading(look, w, alignmentDeg, anti);
      double reach = reach(heading, w, anti);
      if (reach <= 1.0E-9) {
         return null;
      } else {
         Vec3d ride = heading.multiply(Math.min(reach, cap));
         return new Vec3d(MathHelper.clamp(ride.x, w[0], w[3]), MathHelper.clamp(ride.y, w[1], w[4]), MathHelper.clamp(ride.z, w[2], w[5]));
      }
   }

   private static Vec3d heading(Vec3d look, double[] w, double alignmentDeg, double anti) {
      double slack = Math.toRadians(alignmentDeg);
      if (slack <= 1.0E-6) {
         return look;
      } else {
         Vec3d corner = new Vec3d(axisEdge(look.x, w[0], w[3], anti), axisEdge(look.y, w[1], w[4], anti), axisEdge(look.z, w[2], w[5], anti));
         double len = corner.length();
         if (len <= 1.0E-9) {
            return look;
         } else {
            Vec3d target = corner.multiply(1.0 / len);
            double angle = Math.acos(MathHelper.clamp(target.dotProduct(look), -1.0, 1.0));
            if (angle <= slack) {
               return target;
            } else if (angle >= 3.141591653589793) {
               return look;
            } else {
               double t = slack / angle;
               double sin = Math.sin(angle);
               return look.multiply(Math.sin((1.0 - t) * angle) / sin).add(target.multiply(Math.sin(t * angle) / sin));
            }
         }
      }
   }

   private static double reach(Vec3d dir, double[] w, double anti) {
      double reach = Double.MAX_VALUE;
      reach = Math.min(reach, axisReach(dir.x, w[0], w[3], anti));
      reach = Math.min(reach, axisReach(dir.y, w[1], w[4], anti));
      reach = Math.min(reach, axisReach(dir.z, w[2], w[5], anti));
      return reach == Double.MAX_VALUE ? 0.0 : Math.max(0.0, reach);
   }

   private static double axisReach(double dir, double lo, double hi, double anti) {
      if (dir > anti) {
         return hi <= 0.0 ? 0.0 : hi / dir;
      } else if (dir < -anti) {
         return lo >= 0.0 ? 0.0 : lo / dir;
      } else {
         return Double.MAX_VALUE;
      }
   }

   private static double axisEdge(double dir, double lo, double hi, double anti) {
      if (dir > anti) {
         return hi;
      } else {
         return dir < -anti ? lo : MathHelper.clamp(0.0, lo, hi);
      }
   }

   private static double[] axisBox(Vec3d look, Vec3d lastLook, double threshold, double a) {
      double minX = Math.min(-a, look.x) + Math.min(-a, lastLook.x);
      double minY = Math.min(-a, look.y) + Math.min(-a, lastLook.y);
      double minZ = Math.min(-a, look.z) + Math.min(-a, lastLook.z);
      double maxX = Math.max(a, look.x) + Math.max(a, lastLook.x);
      double maxY = Math.max(a, look.y) + Math.max(a, lastLook.y);
      double maxZ = Math.max(a, look.z) + Math.max(a, lastLook.z);
      return new double[]{
         Math.max(-threshold, minX * threshold),
         Math.max(-threshold, minY * threshold),
         Math.max(-threshold, minZ * threshold),
         Math.min(threshold, maxX * threshold),
         Math.min(threshold, maxY * threshold),
         Math.min(threshold, maxZ * threshold)
      };
   }

   public static Vec3d predictGliding(Vec3d old, Vec3d look, float pitchDeg, double gravity) {
      float pitchRad = pitchDeg * (float) (Math.PI / 180.0);
      double horizSqrt = Math.sqrt(look.x * look.x + look.z * look.z);
      double horizLen = old.horizontalLength();
      double vertCos = Math.cos(pitchRad);
      vertCos = vertCos * vertCos * Math.min(1.0, look.length() / 0.4);
      Vec3d v = old.add(0.0, gravity * (-1.0 + vertCos * 0.75), 0.0);
      if (v.y < 0.0 && horizSqrt > 0.0) {
         double d = v.y * -0.1 * vertCos;
         v = v.add(look.x * d / horizSqrt, d, look.z * d / horizSqrt);
      }

      if (pitchRad < 0.0F && horizSqrt > 0.0) {
         double d = horizLen * -MathHelper.sin(pitchRad) * 0.04;
         v = v.add(-look.x * d / horizSqrt, d * 3.2, -look.z * d / horizSqrt);
      }

      if (horizSqrt > 0.0) {
         v = v.add((look.x / horizSqrt * horizLen - v.x) * 0.1, 0.0, (look.z / horizSqrt * horizLen - v.z) * 0.1);
      }

      return v.multiply(0.99, 0.98, 0.99);
   }

   private double effectiveGravity(double velocityY) {
      double gravity = this.mc.player.getAttributeValue(EntityAttributes.GRAVITY);
      return velocityY <= 0.0 && this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING) ? Math.min(gravity, 0.01) : gravity;
   }

   private double throttledSpeed(Vec3d look) {
      double capped = this.speed.get();
      int ticks = this.lookahead.get();
      double reach = Math.max(4.0, capped * ticks);
      Vec3d eye = this.mc.player.getEyePos();
      this.limiter = "speed";
      if (this.wallCheck.get()) {
         Vec3d end = eye.add(look.multiply(reach));
         HitResult hit = this.mc.world.raycast(new RaycastContext(eye, end, ShapeType.COLLIDER, FluidHandling.NONE, this.mc.player));
         if (hit.getType() != Type.MISS) {
            double limit = hit.getPos().distanceTo(eye) / ticks;
            if (limit < capped) {
               capped = limit;
               this.limiter = "wall ahead";
            }
         }
      }

      if (this.chunkCheck.get()) {
         for (double d = 16.0; d <= reach; d += 16.0) {
            Vec3d at = eye.add(look.multiply(d));
            BlockPos atPos = BlockPos.ofFloored(at);
            if (!this.mc.world.getChunkManager().isChunkLoaded(atPos.getX() >> 4, atPos.getZ() >> 4)) {
               if (d / ticks < capped) {
                  capped = d / ticks;
                  this.limiter = "unloaded chunks";
               }
               break;
            }
         }
      }

      return capped;
   }

   private boolean allowed() {
      if (this.suppressTicks > 0) {
         return false;
      } else if (!this.requireInput.get()) {
         return true;
      } else if (this.travellingTicks > 0) {
         return true;
      } else {
         return this.baritoneSync.get() && this.baritoneFlying()
            ? true
            : this.mc.options.forwardKey.isPressed()
               || this.mc.options.backKey.isPressed()
               || this.mc.options.leftKey.isPressed()
               || this.mc.options.rightKey.isPressed()
               || this.mc.options.jumpKey.isPressed()
               || this.mc.options.sneakKey.isPressed();
      }
   }

   public void suppress() {
      this.suppressTicks = 3;
   }

   private boolean baritoneFlying() {
      return BaritoneBridge.isPresent() && BaritoneBridge.elytraHasDestination();
   }

   private double effectiveAlignment() {
      return this.baritoneSync.get() && this.baritoneFlying() ? 0.0 : this.alignment.get();
   }

   public double windowThreshold() {
      return this.amount.get();
   }

   public boolean hasWindow() {
      return this.isActive() && this.windowOpen;
   }

   private FireworkRocketEntity findAttachedRocket() {
      List<FireworkRocketEntity> nearby = this.mc
         .world
         .getEntitiesByClass(FireworkRocketEntity.class, this.mc.player.getBoundingBox().expand(3.0), f -> f.isAlive() && !f.wasShotAtAngle());
      FireworkRocketEntity best = null;
      int bestRemaining = Integer.MIN_VALUE;

      for (FireworkRocketEntity firework : nearby) {
         int remaining = this.remainingLife(firework);
         if (remaining > bestRemaining) {
            bestRemaining = remaining;
            best = firework;
         }
      }

      return best;
   }

   private int remainingLife(FireworkRocketEntity firework) {
      FireworkRocketEntityAccessor accessor = (FireworkRocketEntityAccessor)firework;
      return accessor.getLifeTime() - accessor.getLife();
   }

   private static Vec3d dirFromRotation(float pitchDeg, float yawDeg) {
      float p = pitchDeg * (float) (Math.PI / 180.0);
      float y = -yawDeg * (float) (Math.PI / 180.0);
      float cosY = MathHelper.cos(y);
      float sinY = MathHelper.sin(y);
      float cosP = MathHelper.cos(p);
      float sinP = MathHelper.sin(p);
      return new Vec3d(sinY * cosP, -sinP, cosY * cosP);
   }
}
