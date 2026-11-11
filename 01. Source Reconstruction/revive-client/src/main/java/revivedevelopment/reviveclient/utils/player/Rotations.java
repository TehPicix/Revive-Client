package revivedevelopment.reviveclient.utils.player;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.managers.RotationManager;
import revivedevelopment.reviveclient.utils.PreInit;
import revivedevelopment.reviveclient.utils.entity.Target;
import revivedevelopment.reviveclient.utils.misc.Pool;

public class Rotations {
   private static final Pool<Rotations.Rotation> rotationPool = new Pool<>(Rotations.Rotation::new);
   private static final List<Rotations.Rotation> rotations = new ArrayList<>();
   public static float serverYaw;
   public static float serverPitch;
   public static int rotationTimer;
   private static float preYaw;
   private static float prePitch;
   private static int i = 0;
   private static Rotations.Rotation lastRotation;
   private static int lastRotationTimer;
   private static boolean sentLastRotation;
   public static boolean rotating = false;

   private Rotations() {
   }

   @PreInit
   public static void init() {
      ReviveClient.EVENT_BUS.subscribe(Rotations.class);
   }

   public static void rotate(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
      if (PbPredict.serverPushesOnly() && ReviveClient.ROTATION != null) {
         ReviveClient.ROTATION.requestMotionRotation("rotations", (float)yaw, (float)pitch, RotationManager.RotationPriority.High.weight + priority, true);
      }

      Rotations.Rotation rotation = rotationPool.get();
      rotation.set(yaw, pitch, priority, clientSide, callback);
      int i = 0;

      while (i < rotations.size() && priority <= rotations.get(i).priority) {
         i++;
      }

      rotations.add(i, rotation);
   }

   public static void rotate(double yaw, double pitch, int priority, Runnable callback) {
      rotate(yaw, pitch, priority, false, callback);
   }

   public static void rotate(double yaw, double pitch, Runnable callback) {
      rotate(yaw, pitch, 0, callback);
   }

   public static void rotate(double yaw, double pitch, int priority) {
      rotate(yaw, pitch, priority, null);
   }

   public static void rotate(double yaw, double pitch) {
      rotate(yaw, pitch, 0, null);
   }

   private static void resetLastRotation() {
      if (lastRotation != null) {
         rotationPool.free(lastRotation);
         lastRotation = null;
         lastRotationTimer = 0;
      }
   }

   @EventHandler
   private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
      if (ReviveClient.mc.getCameraEntity() == ReviveClient.mc.player) {
         sentLastRotation = false;
         if (!rotations.isEmpty()) {
            rotating = true;
            resetLastRotation();
            Rotations.Rotation rotation = rotations.get(i);
            setupMovementPacketRotation(rotation);
            if (rotations.size() > 1) {
               rotationPool.free(rotation);
            }

            i++;
         } else if (lastRotation != null) {
            if (lastRotationTimer >= Config.get().rotationHoldTicks.get()) {
               resetLastRotation();
               rotating = false;
            } else {
               setupMovementPacketRotation(lastRotation);
               sentLastRotation = true;
               lastRotationTimer++;
            }
         }
      }
   }

   private static void setupMovementPacketRotation(Rotations.Rotation rotation) {
      setClientRotation(rotation);
      setCamRotation(rotation.yaw, rotation.pitch);
   }

   private static void setClientRotation(Rotations.Rotation rotation) {
      if (!PbPredict.serverPushesOnly()) {
         preYaw = ReviveClient.mc.player.getYaw();
         prePitch = ReviveClient.mc.player.getPitch();
         ReviveClient.mc.player.setYaw((float)rotation.yaw);
         ReviveClient.mc.player.setPitch((float)rotation.pitch);
      }
   }

   @EventHandler
   private static void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
      if (!rotations.isEmpty()) {
         if (ReviveClient.mc.getCameraEntity() == ReviveClient.mc.player) {
            rotations.get(i - 1).runCallback();
            if (rotations.size() == 1) {
               lastRotation = rotations.get(i - 1);
            }

            resetPreRotation();
         }

         for (; i < rotations.size(); i++) {
            Rotations.Rotation rotation = rotations.get(i);
            setCamRotation(rotation.yaw, rotation.pitch);
            if (rotation.clientSide) {
               setClientRotation(rotation);
            }

            rotation.sendPacket();
            if (rotation.clientSide) {
               resetPreRotation();
            }

            if (i == rotations.size() - 1) {
               lastRotation = rotation;
            } else {
               rotationPool.free(rotation);
            }
         }

         rotations.clear();
         i = 0;
      } else if (sentLastRotation) {
         resetPreRotation();
      }
   }

   private static void resetPreRotation() {
      if (!PbPredict.serverPushesOnly()) {
         ReviveClient.mc.player.setYaw(preYaw);
         ReviveClient.mc.player.setPitch(prePitch);
      }
   }

   @EventHandler
   private static void onTick(TickEvent.Pre event) {
      rotationTimer++;
   }

   public static double getYaw(Entity entity) {
      return ReviveClient.mc.player.getYaw()
         + MathHelper.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(entity.getZ() - ReviveClient.mc.player.getZ(), entity.getX() - ReviveClient.mc.player.getX()))
               - 90.0F
               - ReviveClient.mc.player.getYaw()
         );
   }

   public static double getYaw(Vec3d pos) {
      return ReviveClient.mc.player.getYaw()
         + MathHelper.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(pos.getZ() - ReviveClient.mc.player.getZ(), pos.getX() - ReviveClient.mc.player.getX()))
               - 90.0F
               - ReviveClient.mc.player.getYaw()
         );
   }

   public static double getPitch(Vec3d pos) {
      double diffX = pos.getX() - ReviveClient.mc.player.getX();
      double diffY = pos.getY() - (ReviveClient.mc.player.getY() + ReviveClient.mc.player.getEyeHeight(ReviveClient.mc.player.getPose()));
      double diffZ = pos.getZ() - ReviveClient.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return ReviveClient.mc.player.getPitch()
         + MathHelper.wrapDegrees((float)(-Math.toDegrees(Math.atan2(diffY, diffXZ))) - ReviveClient.mc.player.getPitch());
   }

   public static double getPitch(Entity entity, Target target) {
      double y;
      if (target == Target.Head) {
         y = entity.getEyeY();
      } else if (target == Target.Body) {
         y = entity.getY() + entity.getHeight() / 2.0F;
      } else {
         y = entity.getY();
      }

      double diffX = entity.getX() - ReviveClient.mc.player.getX();
      double diffY = y - (ReviveClient.mc.player.getY() + ReviveClient.mc.player.getEyeHeight(ReviveClient.mc.player.getPose()));
      double diffZ = entity.getZ() - ReviveClient.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return ReviveClient.mc.player.getPitch()
         + MathHelper.wrapDegrees((float)(-Math.toDegrees(Math.atan2(diffY, diffXZ))) - ReviveClient.mc.player.getPitch());
   }

   public static double getPitch(Entity entity) {
      return getPitch(entity, Target.Body);
   }

   public static double getYaw(BlockPos pos) {
      return ReviveClient.mc.player.getYaw()
         + MathHelper.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(pos.getZ() + 0.5 - ReviveClient.mc.player.getZ(), pos.getX() + 0.5 - ReviveClient.mc.player.getX()))
               - 90.0F
               - ReviveClient.mc.player.getYaw()
         );
   }

   public static double getPitch(BlockPos pos) {
      double diffX = pos.getX() + 0.5 - ReviveClient.mc.player.getX();
      double diffY = pos.getY() + 0.5 - (ReviveClient.mc.player.getY() + ReviveClient.mc.player.getEyeHeight(ReviveClient.mc.player.getPose()));
      double diffZ = pos.getZ() + 0.5 - ReviveClient.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return ReviveClient.mc.player.getPitch()
         + MathHelper.wrapDegrees((float)(-Math.toDegrees(Math.atan2(diffY, diffXZ))) - ReviveClient.mc.player.getPitch());
   }

   public static void setCamRotation(double yaw, double pitch) {
      serverYaw = (float)yaw;
      serverPitch = (float)pitch;
      rotationTimer = 0;
   }

   private static class Rotation {
      public double yaw;
      public double pitch;
      public int priority;
      public boolean clientSide;
      public Runnable callback;

      public void set(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
         this.yaw = yaw;
         this.pitch = pitch;
         this.priority = priority;
         this.clientSide = clientSide;
         this.callback = callback;
      }

      public void sendPacket() {
         ReviveClient.mc
            .getNetworkHandler()
            .sendPacket(
               new LookAndOnGround((float)this.yaw, (float)this.pitch, ReviveClient.mc.player.isOnGround(), ReviveClient.mc.player.horizontalCollision)
            );
         this.runCallback();
      }

      public void runCallback() {
         if (this.callback != null) {
            this.callback.run();
         }
      }
   }
}
