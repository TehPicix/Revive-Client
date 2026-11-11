package revivedevelopment.reviveclient.systems.managers;

import java.util.Random;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.PlayerTickMovementEvent;
import revivedevelopment.reviveclient.events.entity.player.RotateEvent;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.pingbypass.PbState;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;

public class RotationManager {
   public float nextYaw;
   public float nextPitch;
   public float rotationYaw = 0.0F;
   public float rotationPitch = 0.0F;
   public float lastYaw = 0.0F;
   public float lastPitch = 0.0F;
   private static float renderPitch;
   private static float renderYawOffset;
   private static float prevPitch;
   private static float prevRenderYawOffset;
   private static float prevRotationYawHead;
   private static float rotationYawHead;
   public static boolean sendDisablerPacket = false;
   public static float lastActualYaw = 0.0F;
   public static boolean silentSnap = false;
   private float serverYaw = 0.0F;
   private float serverPitch = 0.0F;
   private boolean hasMotionRotation;
   private float motionYaw;
   private float motionPitch;
   private double motionPriority;
   private String motionHolderId;
   private boolean motionMoveFix;
   private boolean moveFixPhysicsApplied;
   private float moveFixCameraYaw;
   private boolean moveFixYawLocked;
   private int moveFixYawLockAge = Integer.MIN_VALUE;
   private static final float MOVE_FIX_LOCK_TOLERANCE = 0.01F;
   private boolean motionApplied;
   private float savedYaw;
   private float savedPitch;
   private int lastSilentSendTick = Integer.MIN_VALUE;
   private double lastSilentSendPriority = Double.NEGATIVE_INFINITY;
   private String lastSilentSendId = null;
   private static final int SILENT_SENDS_PER_TICK = 2;
   private int silentSendCountTick = Integer.MIN_VALUE;
   private int silentSendCount = 0;
   private float lastSilentTargetYaw = Float.NaN;
   private float lastSilentTargetPitch = Float.NaN;
   private boolean lastRequestRode;
   private final Random silentJitter = new Random();
   private static final float SILENT_SKIP_TOLERANCE = 0.05F;
   private static final float SILENT_JITTER = 0.001F;
   private int lastSilentTick = Integer.MIN_VALUE;
   private int ticksExisted;
   public static Vec3d targetVec = null;
   public static boolean lastGround;
   public double lastX = 0.0;
   public double lastY = 0.0;
   public double lastZ = 0.0;
   private boolean shouldFulfilRequest = false;
   private static final RotationManager.RotationRequest request = new RotationManager.RotationRequest();
   private boolean rotationLocked = false;
   private long lastRotationTime = 0L;
   private static final long ROTATION_COOLDOWN = 15L;
   private final float rotationQuantum;
   private static final double COMBAT_LOOK_PRIORITY = RotationManager.RotationPriority.VeryHigh.weight;

   public RotationManager() {
      ReviveClient.EVENT_BUS.subscribe(this);
      float sens = 0.35F + this.silentJitter.nextFloat() * 0.3F;
      float f = sens * 0.6F + 0.2F;
      this.rotationQuantum = f * f * f * 8.0F * 0.15F;
   }

   private float quantizeFrom(float lastSent, float target, boolean wrap) {
      float delta = wrap ? MathHelper.wrapDegrees(target - lastSent) : target - lastSent;
      int dots = Math.round(delta / this.rotationQuantum);
      if (dots == 0 && Math.abs(delta) > 1.0E-4F) {
         dots = delta > 0.0F ? 1 : -1;
      } else if (Math.abs(dots) >= 2) {
         dots += this.silentJitter.nextInt(3) - 1;
      }

      return lastSent + dots * this.rotationQuantum;
   }

   public void requestRotation(Vec3d target, RotationManager.RotationPriority priority) {
      this.requestRotation(target, priority.weight);
   }

   public void requestRotation(Vec3d target, RotationManager.RotationPriority priority, Runnable callback) {
      this.requestRotation(target, priority.weight, callback);
   }

   public void requestRotation(float yaw, float pitch, RotationManager.RotationPriority priority) {
      this.requestRotation(yaw, pitch, priority.weight);
   }

   public void requestRotation(float yaw, float pitch, RotationManager.RotationPriority priority, Runnable callback) {
      this.requestRotation(yaw, pitch, priority.weight, callback);
   }

   private static float stepToward(float current, float target, float maxStep) {
      float diff = MathHelper.wrapDegrees(target - current);
      return Math.abs(diff) <= maxStep ? target : MathHelper.wrapDegrees(current + Math.copySign(maxStep, diff));
   }

   public void snapAt(Vec3d target) {
      this.snapAt(target, false);
   }

   public void snapAt(Vec3d target, boolean silent) {
      float[] angle = this.getRotation(target);
      this.snapAt(angle[0], angle[1], silent);
   }

   public void snapAt(float yaw, float pitch) {
      this.snapAt(yaw, pitch, false);
   }

   public void snapAt(float yaw, float pitch, boolean silent) {
      if (this.silentLooksRideMotion()) {
         this.requestMotionRotation("snap", yaw, pitch, RotationManager.RotationPriority.Critical.weight, true);
      } else {
         silentSnap = silent;
         if (AntiCheatConfig.get().grimSnapRotation.get()) {
            ReviveClient.mc
               .getNetworkHandler()
               .sendPacket(new Full(this.lastX, this.lastY, this.lastZ, yaw, pitch, lastGround, ReviveClient.mc.player.horizontalCollision));
         } else {
            ReviveClient.mc.getNetworkHandler().sendPacket(new LookAndOnGround(yaw, pitch, lastGround, ReviveClient.mc.player.horizontalCollision));
         }

         silentSnap = false;
      }
   }

   public float getServerYaw() {
      return this.serverYaw;
   }

   public float getServerPitch() {
      return this.serverPitch;
   }

   public boolean silentRotationActiveWithin(int ticks) {
      if (ReviveClient.mc.player != null && this.lastSilentTick != Integer.MIN_VALUE) {
         int age = ReviveClient.mc.player.age;
         return age >= this.lastSilentTick && age - this.lastSilentTick <= ticks;
      } else {
         return false;
      }
   }

   public int silentSendsThisTick() {
      return ReviveClient.mc.player != null && this.silentSendCountTick == ReviveClient.mc.player.age ? this.silentSendCount : 0;
   }

   public boolean canSendSilentRotation(String id, double priority) {
      if (ReviveClient.mc.player == null || ReviveClient.mc.getNetworkHandler() == null) {
         return false;
      } else if (this.silentLooksRideMotion()) {
         return !silentLookRateCapped(priority) && (!this.hasMotionRotation || priority >= this.motionPriority);
      } else {
         return silentLookRateCapped(priority) ? false : this.silentSendAllowed(id, priority, ReviveClient.mc.player.age);
      }
   }

   public boolean canSendSilentRotation(double priority) {
      return this.canSendSilentRotation(null, priority);
   }

   public boolean silentLooksRideMotion() {
      return PbState.lookOnlyInTicks || PbPredict.serverPushesOnly();
   }

   public boolean outgoingLookHits(Box box) {
      return ReviveClient.mc.player != null
         && box != null
         && this.raytraceCheck(ReviveClient.mc.player.getEyePos(), this.getOutgoingYaw(), this.getOutgoingPitch(), box);
   }

   public float getRideYaw() {
      return this.silentLooksRideMotion() ? this.getOutgoingYaw() : this.serverYaw;
   }

   public float getRidePitch() {
      return this.silentLooksRideMotion() ? this.getOutgoingPitch() : this.serverPitch;
   }

   public Vec3d getRideEye() {
      return !this.silentLooksRideMotion() && (this.lastX != 0.0 || this.lastY != 0.0 || this.lastZ != 0.0)
         ? new Vec3d(this.lastX, this.lastY + ReviveClient.mc.player.getEyeHeight(ReviveClient.mc.player.getPose()), this.lastZ)
         : ReviveClient.mc.player.getEyePos();
   }

   public boolean rideLookHits(Box box) {
      return ReviveClient.mc.player != null && box != null && this.raytraceCheck(this.getRideEye(), this.getRideYaw(), this.getRidePitch(), box);
   }

   public String describeLookHolder(boolean motionSlot) {
      if (motionSlot) {
         return !this.hasMotionRotation
            ? "none"
            : (this.motionHolderId != null ? this.motionHolderId : "motion")
               + "@"
               + (int)this.motionPriority
               + (this.moveFixYawLocked() ? " (move-fix: the movement ran on it)" : "");
      } else {
         return ReviveClient.mc.player != null && this.lastSilentSendTick == ReviveClient.mc.player.age
            ? (this.lastSilentSendId != null ? this.lastSilentSendId : "?") + "@" + (int)this.lastSilentSendPriority
            : "packet-limiter";
      }
   }

   private static boolean silentLookRateCapped(double priority) {
      PacketManager packets = ReviveClient.PACKET;
      return packets != null && packets.isRateCapped(priority >= COMBAT_LOOK_PRIORITY);
   }

   private boolean silentSendAllowed(String id, double priority, int tick) {
      if (this.lastSilentSendTick != tick) {
         return true;
      } else if (id != null && id.equals(this.lastSilentSendId)) {
         return true;
      } else {
         return priority > this.lastSilentSendPriority ? true : this.silentSendsThisTick() < 2;
      }
   }

   public boolean requestSilentRotation(String id, double priority, float yaw, float pitch, Box target) {
      this.lastRequestRode = false;
      if (this.requestSilentRotation(id, priority, yaw, pitch)) {
         return true;
      } else {
         this.lastRequestRode = this.rideLookHits(target);
         return this.lastRequestRode;
      }
   }

   public boolean lastSilentRequestRode() {
      return this.lastRequestRode;
   }

   public boolean requestSilentRotation(String id, double priority, float yaw, float pitch) {
      if (ReviveClient.mc.player != null && ReviveClient.mc.getNetworkHandler() != null) {
         pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
         if (this.silentLooksRideMotion()) {
            return this.requestMotionRotation(id, yaw, pitch, priority, true);
         } else {
            int tick = ReviveClient.mc.player.age;
            if (!this.silentSendAllowed(id, priority, tick)) {
               return Math.abs(MathHelper.wrapDegrees(yaw - this.serverYaw)) < 0.05F && Math.abs(pitch - this.serverPitch) < 0.05F;
            } else {
               if (!Float.isNaN(this.lastSilentTargetYaw)) {
                  float dTY = MathHelper.wrapDegrees(yaw - this.lastSilentTargetYaw);
                  float dTP = pitch - this.lastSilentTargetPitch;
                  if (Math.abs(dTY) < 0.05F && Math.abs(dTP) < 0.05F) {
                     float dSY = MathHelper.wrapDegrees(yaw - this.serverYaw);
                     float dSP = pitch - this.serverPitch;
                     if (Math.abs(dSY) < 0.05F && Math.abs(dSP) < 0.05F) {
                        return true;
                     }
                  }
               }

               if (silentLookRateCapped(priority)) {
                  return Math.abs(MathHelper.wrapDegrees(yaw - this.serverYaw)) < 0.05F && Math.abs(pitch - this.serverPitch) < 0.05F;
               } else {
                  float sendYaw;
                  float sendPitch;
                  if (AntiCheatConfig.get().gcdRotation.get()) {
                     sendYaw = this.quantizeFrom(this.serverYaw, yaw, true);
                     sendPitch = MathHelper.clamp(this.quantizeFrom(this.serverPitch, pitch, false), -90.0F, 90.0F);
                  } else {
                     float jitterY = (this.silentJitter.nextFloat() - 0.5F) * 2.0F * 0.001F;
                     float jitterP = (this.silentJitter.nextFloat() - 0.5F) * 2.0F * 0.001F;
                     sendYaw = yaw + jitterY;
                     sendPitch = MathHelper.clamp(pitch + jitterP, -90.0F, 90.0F);
                  }

                  this.lastSilentTargetYaw = yaw;
                  this.lastSilentTargetPitch = pitch;
                  this.lastSilentSendPriority = this.lastSilentSendTick == tick ? Math.max(this.lastSilentSendPriority, priority) : priority;
                  this.lastSilentSendTick = tick;
                  this.lastSilentSendId = id;
                  if (this.silentSendCountTick != tick) {
                     this.silentSendCountTick = tick;
                     this.silentSendCount = 0;
                  }

                  this.silentSendCount++;
                  silentSnap = true;
                  boolean moving = ReviveClient.mc.player.getX() != this.lastX
                     || ReviveClient.mc.player.getY() != this.lastY
                     || ReviveClient.mc.player.getZ() != this.lastZ;
                  if (moving) {
                     ReviveClient.mc
                        .getNetworkHandler()
                        .sendPacket(new LookAndOnGround(sendYaw, sendPitch, lastGround, ReviveClient.mc.player.horizontalCollision));
                  } else {
                     ReviveClient.mc
                        .getNetworkHandler()
                        .sendPacket(new Full(this.lastX, this.lastY, this.lastZ, sendYaw, sendPitch, lastGround, ReviveClient.mc.player.horizontalCollision));
                  }

                  silentSnap = false;
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   public void requestMotionRotation(float yaw, float pitch) {
      this.requestMotionRotation(yaw, pitch, 100.0);
   }

   public boolean requestMotionRotation(float yaw, float pitch, double priority) {
      return this.requestMotionRotation(yaw, pitch, priority, false);
   }

   public boolean requestMotionRotation(float yaw, float pitch, double priority, boolean moveFix) {
      return this.requestMotionRotation(null, yaw, pitch, priority, moveFix);
   }

   public boolean requestMotionRotation(String id, float yaw, float pitch, double priority, boolean moveFix) {
      if (ReviveClient.mc.player == null) {
         return false;
      } else if (this.hasMotionRotation && priority < this.motionPriority) {
         return false;
      } else {
         boolean locked = this.moveFixYawLocked();
         if (locked && Math.abs(MathHelper.wrapDegrees(yaw - this.motionYaw)) > 0.01F) {
            return false;
         } else {
            this.motionYaw = yaw;
            this.motionPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
            this.motionPriority = priority;
            this.motionMoveFix = moveFix || locked;
            this.motionHolderId = id;
            this.hasMotionRotation = true;
            return true;
         }
      }
   }

   public boolean moveFixActive() {
      return this.hasMotionRotation && this.motionMoveFix;
   }

   public boolean hasMotionRotation() {
      return this.hasMotionRotation;
   }

   public double getMotionPriority() {
      return this.motionPriority;
   }

   private boolean moveFixYawLocked() {
      return this.moveFixYawLocked && ReviveClient.mc.player != null && this.moveFixYawLockAge == ReviveClient.mc.player.age;
   }

   public float getMotionYaw() {
      return this.motionYaw;
   }

   @EventHandler(
      priority = -200
   )
   public void onPlayerTickMovement(PlayerTickMovementEvent event) {
      if (ReviveClient.mc.player != null && !this.moveFixPhysicsApplied && this.moveFixActive()) {
         if (!ReviveClient.mc.player.hasVehicle() && !ReviveClient.mc.player.isGliding() && !ReviveClient.mc.player.isSwimming()) {
            this.moveFixCameraYaw = ReviveClient.mc.player.getYaw();
            ReviveClient.mc.player.setYaw(this.motionYaw);
            this.moveFixPhysicsApplied = true;
            this.moveFixYawLocked = true;
            this.moveFixYawLockAge = ReviveClient.mc.player.age;
         }
      }
   }

   public void onTickMovementEnd() {
      if (this.moveFixPhysicsApplied) {
         this.moveFixPhysicsApplied = false;
         if (ReviveClient.mc.player != null) {
            ReviveClient.mc.player.setYaw(this.moveFixCameraYaw);
         }
      }
   }

   public boolean moveFixPhysicsApplied() {
      return this.moveFixPhysicsApplied;
   }

   public float getMoveFixCameraYaw() {
      if (this.moveFixPhysicsApplied) {
         return this.moveFixCameraYaw;
      } else {
         return ReviveClient.mc.player != null ? ReviveClient.mc.player.getYaw() : 0.0F;
      }
   }

   public float getOutgoingYaw() {
      if (this.hasMotionRotation) {
         return this.motionYaw;
      } else {
         return ReviveClient.mc.player != null ? ReviveClient.mc.player.getYaw() : this.serverYaw;
      }
   }

   public float getOutgoingPitch() {
      if (this.hasMotionRotation) {
         return this.motionPitch;
      } else {
         return ReviveClient.mc.player != null ? ReviveClient.mc.player.getPitch() : this.serverPitch;
      }
   }

   public void requestMotionRotation(float yaw, float pitch, boolean ensureDelivery) {
      this.requestMotionRotation(yaw, pitch);
   }

   public void release(String id) {
   }

   public void requestRotation(Vec3d target, double priority) {
      if (!this.isRotationLocked() || !(priority < 900.0)) {
         float[] angle = this.getRotation(target);
         this.requestRotation(angle[0], angle[1], priority, null);
      }
   }

   public void requestRotation(Vec3d target, double priority, Runnable callback) {
      if (!this.isRotationLocked() || !(priority < 900.0)) {
         float[] angle = this.getRotation(target);
         this.requestRotation(angle[0], angle[1], priority, callback);
      }
   }

   public void requestRotation(float yaw, float pitch, double priority) {
      if (!this.isRotationLocked() || !(priority < 900.0)) {
         this.requestRotation(yaw, pitch, priority, null);
      }
   }

   public void requestRotation(float yaw, float pitch, double priority, Runnable callback) {
      if (!(request.priority > priority) || request.fulfilled || !(priority < 900.0)) {
         boolean smallRotationChange = Math.abs(MathHelper.wrapDegrees(yaw - request.yaw)) < 1.0F
            && Math.abs(MathHelper.wrapDegrees(pitch - request.pitch)) < 1.0F;
         if (!(request.priority >= priority) || request.fulfilled || !smallRotationChange) {
            request.fulfilled = false;
            request.yaw = yaw;
            request.pitch = pitch;
            request.priority = priority;
            request.callback = callback;
            request.timestamp = System.currentTimeMillis();
            this.rotationLocked = true;
            this.lastRotationTime = System.currentTimeMillis();
         }
      }
   }

   private boolean isRotationLocked() {
      long currentTime = System.currentTimeMillis();
      if (this.rotationLocked && currentTime - this.lastRotationTime > 15L) {
         this.rotationLocked = false;
      }

      return this.rotationLocked;
   }

   public float[] getRotation(Vec3d eyesPos, Vec3d vec) {
      double diffX = vec.x - eyesPos.x;
      double diffY = vec.y - eyesPos.y;
      double diffZ = vec.z - eyesPos.z;
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      float yaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(diffY, diffXZ)));
      return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch)};
   }

   public float[] getRotation(Vec3d vec) {
      Vec3d eyesPos = ReviveClient.mc.player.getEyePos();
      return this.getRotation(eyesPos, vec);
   }

   public boolean lookingAt(Box box) {
      return this.lookingAt(this.lastYaw, this.lastPitch, box);
   }

   public boolean lookingAt(float yaw, float pitch, Box box) {
      return this.raytraceCheck(ReviveClient.mc.player.getEyePos(), yaw, pitch, box);
   }

   @EventHandler(
      priority = -200
   )
   public void onLastRotation(RotateEvent event) {
      this.shouldFulfilRequest = false;
      if (request != null && !request.fulfilled && request.priority > 0.0) {
         if (AntiCheatConfig.get().rotationSmooth.get()) {
            float yawStep = AntiCheatConfig.get().smoothYawStep.get().floatValue();
            float pitchStep = AntiCheatConfig.get().smoothPitchStep.get().floatValue();
            float ny = stepToward(event.getYaw(), request.yaw, yawStep);
            float np = stepToward(event.getPitch(), request.pitch, pitchStep);
            event.setYaw(ny);
            event.setPitch(np);
            if (Math.abs(MathHelper.wrapDegrees(ny - request.yaw)) < 0.1F && Math.abs(MathHelper.wrapDegrees(np - request.pitch)) < 0.1F) {
               this.shouldFulfilRequest = true;
            }
         } else {
            event.setYaw(request.yaw);
            event.setPitch(request.pitch);
            this.shouldFulfilRequest = true;
         }
      }
   }

   public boolean isHighPriorityRotationActive(double threshold) {
      if (this.rotationLocked && request != null && !request.fulfilled && request.priority >= threshold) {
         long age = System.currentTimeMillis() - request.timestamp;
         if (age < 500L) {
            return true;
         }
      }

      return false;
   }

   @EventHandler(
      priority = -999
   )
   public void onPacketSend(PacketEvent.Send event) {
      if (ReviveClient.mc.player != null && !event.isCancelled()) {
         if (event.packet instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesLook()) {
               this.serverYaw = packet.getYaw(this.serverYaw);
               this.serverPitch = packet.getPitch(this.serverPitch);
               if (silentSnap) {
                  this.lastSilentTick = ReviveClient.mc.player.age;
               } else {
                  this.lastYaw = packet.getYaw(this.lastYaw);
                  if (sendDisablerPacket) {
                     sendDisablerPacket = false;
                     this.lastYaw = lastActualYaw;
                  }

                  this.lastPitch = packet.getPitch(this.lastPitch);
                  this.setRenderRotation(this.lastYaw, this.lastPitch, false);
               }
            }

            if (packet.changesPosition()) {
               this.lastX = packet.getX(this.lastX);
               this.lastY = packet.getY(this.lastY);
               this.lastZ = packet.getZ(this.lastZ);
            }

            lastGround = packet.isOnGround();
         }
      }
   }

   @EventHandler(
      priority = 100
   )
   public void onReceivePacket(PacketEvent.Receive event) {
      if (ReviveClient.mc.player != null) {
         if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            EntityPosition change = packet.change();
            if (packet.relatives().contains(PositionFlag.Y_ROT)) {
               this.lastYaw = this.lastYaw + change.yaw();
            } else {
               this.lastYaw = change.yaw();
            }

            if (packet.relatives().contains(PositionFlag.X_ROT)) {
               this.lastPitch = this.lastPitch + change.pitch();
            } else {
               this.lastPitch = change.pitch();
            }

            if (packet.relatives().contains(PositionFlag.X)) {
               this.lastX = this.lastX + change.position().x;
            } else {
               this.lastX = change.position().x;
            }

            if (packet.relatives().contains(PositionFlag.Y)) {
               this.lastY = this.lastY + change.position().y;
            } else {
               this.lastY = change.position().y;
            }

            if (packet.relatives().contains(PositionFlag.Z)) {
               this.lastZ = this.lastZ + change.position().z;
            } else {
               this.lastZ = change.position().z;
            }

            this.setRenderRotation(this.lastYaw, this.lastPitch, true);
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   public void onSendMovementPre(SendMovementPacketsEvent.Pre event) {
      if (ReviveClient.mc.player != null && this.hasMotionRotation && !this.motionApplied) {
         this.savedYaw = ReviveClient.mc.player.getYaw();
         this.savedPitch = ReviveClient.mc.player.getPitch();
         ReviveClient.mc.player.setYaw(this.motionYaw);
         ReviveClient.mc.player.setPitch(this.motionPitch);
         this.motionApplied = true;
         silentSnap = true;
      }
   }

   @EventHandler(
      priority = 200
   )
   public void onSendMovementPost(SendMovementPacketsEvent.Post event) {
      this.moveFixYawLocked = false;
      if (this.motionApplied) {
         this.motionApplied = false;
         silentSnap = false;
         this.hasMotionRotation = false;
         if (ReviveClient.mc.player != null) {
            ReviveClient.mc.player.setYaw(this.savedYaw);
            ReviveClient.mc.player.setPitch(this.savedPitch);
         }
      }
   }

   @EventHandler
   public void onUpdateWalkingPost(SendMovementPacketsEvent.Post event) {
      this.setRenderRotation(this.lastYaw, this.lastPitch, false);
      this.lastYaw = ReviveClient.mc.player.getYaw();
      this.lastPitch = ReviveClient.mc.player.getPitch();
      this.lastX = ReviveClient.mc.player.getX();
      this.lastY = ReviveClient.mc.player.getY();
      this.lastZ = ReviveClient.mc.player.getZ();
      lastGround = ReviveClient.mc.player.isOnGround();
   }

   @EventHandler
   public void onMovementPacket(SendMovementPacketsEvent.Rotation event) {
      if (AntiCheatConfig.get().tickSync.get()) {
         if (this.shouldFulfilRequest && !request.fulfilled) {
            request.fulfilled = true;
            this.shouldFulfilRequest = false;
         }

         RotateEvent rotateEvent = new RotateEvent(event.yaw, event.pitch);
         ReviveClient.EVENT_BUS.post(rotateEvent);
         event.yaw = rotateEvent.getYaw();
         event.pitch = rotateEvent.getPitch();
         if (AntiCheatConfig.get().grimSync.get()) {
            event.forceFull = true;
         }

         if (AntiCheatConfig.get().grimRotation.get()) {
            event.forceFullOnRotate = true;
         }
      }
   }

   public boolean raytraceCheck(Vec3d pos, double yaw, double pitch, Box box) {
      Vec3d vec = new Vec3d(
         Math.cos(Math.toRadians(yaw + 90.0)) * Math.abs(Math.cos(Math.toRadians(pitch))),
         -Math.sin(Math.toRadians(pitch)),
         Math.sin(Math.toRadians(yaw + 90.0)) * Math.abs(Math.cos(Math.toRadians(pitch)))
      );
      double rayX = pos.x;
      double rayY = pos.y;
      double rayZ = pos.z;
      double dirX = vec.x;
      double dirY = vec.y;
      double dirZ = vec.z;
      double minX = box.minX;
      double minY = box.minY;
      double minZ = box.minZ;
      double maxX = box.maxX;
      double maxY = box.maxY;
      double maxZ = box.maxZ;
      double invDirX = dirX != 0.0 ? 1.0 / dirX : 1.0E10;
      double invDirY = dirY != 0.0 ? 1.0 / dirY : 1.0E10;
      double invDirZ = dirZ != 0.0 ? 1.0 / dirZ : 1.0E10;
      double tMinX = (minX - rayX) * invDirX;
      double tMaxX = (maxX - rayX) * invDirX;
      if (tMinX > tMaxX) {
         double temp = tMinX;
         tMinX = tMaxX;
         tMaxX = temp;
      }

      double tMinY = (minY - rayY) * invDirY;
      double tMaxY = (maxY - rayY) * invDirY;
      if (tMinY > tMaxY) {
         double temp = tMinY;
         tMinY = tMaxY;
         tMaxY = temp;
      }

      double tMinZ = (minZ - rayZ) * invDirZ;
      double tMaxZ = (maxZ - rayZ) * invDirZ;
      if (tMinZ > tMaxZ) {
         double temp = tMinZ;
         tMinZ = tMaxZ;
         tMaxZ = temp;
      }

      double tMin = Math.max(Math.max(tMinX, tMinY), tMinZ);
      double tMax = Math.min(Math.min(tMaxX, tMaxY), tMaxZ);
      return tMax >= 0.0 && tMin <= tMax;
   }

   public void setRenderRotation(float yaw, float pitch, boolean force) {
      if (ReviveClient.mc.player != null && (ReviveClient.mc.player.age != this.ticksExisted || force)) {
         this.ticksExisted = ReviveClient.mc.player.age;
         prevPitch = renderPitch;
         prevRenderYawOffset = renderYawOffset;
         renderYawOffset = this.getRenderYawOffset(yaw, prevRenderYawOffset);
         prevRotationYawHead = rotationYawHead;
         rotationYawHead = yaw;
         renderPitch = pitch;
      }
   }

   public static float getRenderPitch() {
      return renderPitch;
   }

   public static float getRotationYawHead() {
      return rotationYawHead;
   }

   public static float getRenderYawOffset() {
      return renderYawOffset;
   }

   public static float getPrevPitch() {
      return prevPitch;
   }

   public static float getPrevRotationYawHead() {
      return prevRotationYawHead;
   }

   public static float getPrevRenderYawOffset() {
      return prevRenderYawOffset;
   }

   private float getRenderYawOffset(float yaw, float offsetIn) {
      float result = offsetIn;
      double xDif = ReviveClient.mc.player.getX() - ReviveClient.mc.player.lastX;
      double zDif = ReviveClient.mc.player.getZ() - ReviveClient.mc.player.lastZ;
      if (xDif * xDif + zDif * zDif > 0.0025000002F) {
         float offset = (float)MathHelper.atan2(zDif, xDif) * (180.0F / (float)Math.PI) - 90.0F;
         float wrap = MathHelper.abs(MathHelper.wrapDegrees(yaw) - offset);
         if (95.0F < wrap && wrap < 265.0F) {
            result = offset - 180.0F;
         } else {
            result = offset;
         }
      }

      if (ReviveClient.mc.player.handSwingProgress > 0.0F) {
         result = yaw;
      }

      result = offsetIn + MathHelper.wrapDegrees(result - offsetIn) * 0.3F;
      float offset = MathHelper.wrapDegrees(yaw - result);
      if (offset < -75.0F) {
         offset = -75.0F;
      } else if (offset >= 75.0F) {
         offset = 75.0F;
      }

      result = yaw - offset;
      if (offset * offset > 2500.0F) {
         result += offset * 0.2F;
      }

      return result;
   }

   public static enum RotationPriority {
      Lowest(50.0),
      Low(150.0),
      Normal(300.0),
      High(600.0),
      VeryHigh(900.0),
      Highest(1200.0),
      Critical(2000.0);

      public final double weight;

      private RotationPriority(double weight) {
         this.weight = weight;
      }
   }

   public static class RotationRequest {
      public double priority;
      public float yaw;
      public float pitch;
      public boolean fulfilled = false;
      public Runnable callback = null;
      public long timestamp = 0L;
   }
}
