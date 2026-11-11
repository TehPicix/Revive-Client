package revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import revivedevelopment.reviveclient.events.entity.player.PlayerMoveEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.ChunkDataEvent;
import revivedevelopment.reviveclient.mixin.LivingEntityAccessor;
import revivedevelopment.reviveclient.mixininterface.IVec3d;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.hunting.HuntingUtils;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import revivedevelopment.reviveclient.systems.modules.player.Rotation;
import revivedevelopment.reviveclient.systems.modules.player.Timer;
import revivedevelopment.reviveclient.utils.BaritoneBridge;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class Bounce extends ElytraFlightMode {
   private double prevFov;
   private Vec3d lastBoostPos;
   private static final double MAX_DISTANCE = 80.0;
   private BlockPos tempPath = null;
   private boolean waitingForChunksToLoad = false;
   private boolean paused = false;
   private BlockPos portalTrap = null;
   private Vec3d lastUnstuckPos;
   private int stuckTimer = 0;
   private double obstaclePasserYaw = 0.0;
   private boolean obstaclePasserYawComputed = false;
   private static final float TUNNEL_RISE_PITCH = 89.0F;
   private static final double LOW_CEILING_HEADROOM = 0.5;
   private static final double ROOF_SIM_HEADROOM = 0.2;
   private static final int ROOF_SIM_REPORT_TICKS = 100;
   private double roofSimFloorY = Double.NaN;
   private int roofSimHops;
   private int roofSimSetbacks;
   private int roofSimTicks;
   private double roofSimSpeedSum;

   public Bounce() {
      super(ElytraFlightModes.Bounce);
   }

   @Override
   public void onTick() {
      super.onTick();
   }

   @Override
   public void onPreTick() {
      super.onPreTick();
      if (this.mc.player != null) {
         if (checkConditions(this.mc.player)) {
            this.roofSimTick();
            if (this.prevFov != 0.0 && !this.elytraFly.sprint.get()) {
               this.mc.options.getFovEffectScale().setValue(0.0);
            }

            if (this.elytraFly.highwayObstaclePasser.get()) {
               if (this.handleObstaclePasser()) {
                  this.release(this.mc.options.forwardKey);
                  return;
               }
            } else if (this.paused || this.tempPath != null) {
               this.tempPath = null;
               BaritoneBridge.clearGoal();
               this.paused = false;
            }

            boolean usingItem = this.mc.player.isUsingItem();
            if (usingItem) {
               this.mc.player.setSprinting(false);
            } else if (this.elytraFly.sprint.get()) {
               this.mc.player.setSprinting(true);
            } else if (this.mc.player.isGliding()) {
               this.mc.player.setSprinting(this.mc.player.isOnGround());
            } else {
               this.mc.player.setSprinting(true);
            }

            this.mc.player.setYaw(this.getYawDirection());
            this.mc.player.setPitch(this.bouncePitch());
            this.setPressed(this.mc.options.forwardKey, true);
            if (this.elytraFly.motionYBoost.get()) {
               if (!usingItem) {
                  this.mc.player.setSprinting(true);
               }

               if (this.mc.player.isOnGround() && Utils.getPlayerSpeed().multiply(1.0, 0.0, 1.0).length() < this.elytraFly.speed.get()) {
                  ((LivingEntityAccessor)this.mc.player).revive$jump();
                  this.mc.player.setPitch(this.bouncePitch());
               }

               if (!this.mc.player.isTouchingWater()) {
                  this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
                  this.mc.player.startGliding();
               }
            } else if (this.mc.player.isOnGround()) {
               if (this.elytraFly.autoJump.get() || this.mc.options.jumpKey.isPressed()) {
                  this.roofSimFloorY = this.mc.player.getY();
                  ((LivingEntityAccessor)this.mc.player).revive$jump();
                  this.mc.player.setPitch(this.bouncePitch());
                  if (!usingItem) {
                     this.mc.player.setSprinting(true);
                  }
               }
            } else {
               if (!this.mc.player.isGliding() && !this.mc.player.isTouchingWater()) {
                  this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
                  this.mc.player.startGliding();
               }
            }
         }
      }
   }

   private float bouncePitch() {
      if (!this.elytraFly.tunnel.get()) {
         return this.elytraFly.pitch.get().floatValue();
      } else if (this.headroomAbove(1.0) < 0.5) {
         return 89.0F;
      } else if (this.mc.player.getVelocity().y > 0.0) {
         return 89.0F;
      } else if (this.mc.player.isOnGround()) {
         return this.elytraFly.tunnelGlidePitch.get().floatValue();
      } else {
         double height = this.heightAboveFloor(3.0);
         return height > this.elytraFly.tunnelHeight.get() ? 89.0F : this.elytraFly.tunnelGlidePitch.get().floatValue();
      }
   }

   public static boolean underLowRoof() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client.player != null && client.world != null ? headroomAbove(client, 1.0) < 0.5 : false;
   }

   private double headroomAbove(double limit) {
      return headroomAbove(this.mc, limit);
   }

   private static double headroomAbove(MinecraftClient mc, double limit) {
      Box box = mc.player.getBoundingBox();
      double ceiling = Double.POSITIVE_INFINITY;
      double[][] corners = new double[][]{{box.minX, box.minZ}, {box.maxX, box.minZ}, {box.minX, box.maxZ}, {box.maxX, box.maxZ}};
      int bottom = (int)Math.floor(box.maxY);
      int top = (int)Math.floor(box.maxY + limit);

      for (double[] c : corners) {
         int x = (int)Math.floor(c[0]);
         int z = (int)Math.floor(c[1]);

         for (int y = bottom; y <= top; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            VoxelShape shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos);
            if (!shape.isEmpty()) {
               double shapeBottom = y + shape.getMin(Axis.Y);
               if (shapeBottom >= box.maxY - 1.0E-4) {
                  ceiling = Math.min(ceiling, shapeBottom);
                  break;
               }
            }
         }
      }

      return ceiling == Double.POSITIVE_INFINITY ? limit : Math.max(0.0, ceiling - box.maxY);
   }

   private double heightAboveFloor(double limit) {
      Box box = this.mc.player.getBoundingBox();
      double floor = Double.NEGATIVE_INFINITY;
      double[][] corners = new double[][]{{box.minX, box.minZ}, {box.maxX, box.minZ}, {box.minX, box.maxZ}, {box.maxX, box.maxZ}};
      int top = (int)Math.floor(box.minY);
      int bottom = (int)Math.floor(box.minY - limit);

      for (double[] c : corners) {
         int x = (int)Math.floor(c[0]);
         int z = (int)Math.floor(c[1]);

         for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            VoxelShape shape = this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos);
            if (!shape.isEmpty()) {
               double shapeTop = y + shape.getMax(Axis.Y);
               if (shapeTop <= box.minY + 1.0E-4) {
                  floor = Math.max(floor, shapeTop);
                  break;
               }
            }
         }
      }

      return floor == Double.NEGATIVE_INFINITY ? limit : Math.max(0.0, box.minY - floor);
   }

   private boolean roofSimActive() {
      return this.elytraFly.tunnel.get() && this.elytraFly.simulateRoof.get() && !this.paused && !underLowRoof();
   }

   private void roofSim(PlayerMoveEvent event) {
      if (this.mc.player != null && event.type == MovementType.SELF && this.roofSimActive()) {
         if (!Double.isNaN(this.roofSimFloorY) && !this.mc.player.isOnGround()) {
            double maxFeetY = this.roofSimFloorY + 0.2;
            double y = this.mc.player.getY();
            double ny = y + event.movement.y;
            if (ny > maxFeetY && event.movement.y > 0.0) {
               ((IVec3d)event.movement).revive$setY(Math.max(0.0, maxFeetY - y));
               Vec3d v = this.mc.player.getVelocity();
               this.mc.player.setVelocity(v.x, 0.0, v.z);
               this.roofSimHops++;
            }
         }
      }
   }

   private void roofSimTick() {
      if (!this.roofSimActive()) {
         if (this.roofSimTicks > 0) {
            this.roofSimReport("ended");
         }

         this.roofSimTicks = 0;
         this.roofSimHops = 0;
         this.roofSimSetbacks = 0;
         this.roofSimSpeedSum = 0.0;
      } else {
         this.roofSimTicks++;
         this.roofSimSpeedSum = this.roofSimSpeedSum + this.mc.player.getVelocity().horizontalLength();
         if (this.roofSimTicks % 100 == 0) {
            this.roofSimReport("last 100 ticks");
         }
      }
   }

   private void roofSimReport(String span) {
      int ticks = this.roofSimTicks % 100 == 0 ? 100 : this.roofSimTicks % 100;
      double mean = ticks > 0 ? this.roofSimSpeedSum / ticks : 0.0;
      ChatUtils.infoPrefix(
         "ElytraFly",
         "roof-sim (%s): %d hops capped at the fake roof, %d server setbacks, mean speed %.2f b/t (%.0f bps)",
         span,
         this.roofSimHops,
         this.roofSimSetbacks,
         mean,
         mean * 20.0
      );
      this.roofSimHops = 0;
      this.roofSimSetbacks = 0;
      this.roofSimSpeedSum = 0.0;
   }

   @Override
   public void onPacketReceive(PacketEvent.Receive event) {
      super.onPacketReceive(event);
      if (event.packet instanceof PlayerPositionLookS2CPacket && this.roofSimActive()) {
         this.roofSimSetbacks++;
      }
   }

   @Override
   public void handleMotionYBoost(PlayerMoveEvent event) {
      this.roofSim(event);
      if (this.mc.player != null && event.type == MovementType.SELF && !this.paused && this.elytraFly.motionYBoost.get()) {
         if (!this.elytraFly.onlyWhileColliding.get() || this.mc.player.horizontalCollision) {
            if (!this.mc.player.isUsingItem()) {
               this.mc.player.setSprinting(true);
            }

            if (this.lastBoostPos != null) {
               double speedBps = this.mc.player.getEntityPos().subtract(this.lastBoostPos).multiply(20.0, 0.0, 20.0).length();
               Timer timer = Modules.get().get(Timer.class);
               if (timer.isActive() && (!PbPredict.serverPushesOnly() || timer.isRemote())) {
                  speedBps *= timer.getMultiplier();
               }

               if (this.mc.player.isOnGround() && speedBps < this.elytraFly.speed.get()) {
                  if (speedBps > 20.0 || this.elytraFly.tunnelBounce.get()) {
                     ((IVec3d)event.movement).revive$setY(0.0);
                  }

                  this.mc.player.setVelocity(this.mc.player.getVelocity().x, 0.0, this.mc.player.getVelocity().z);
               }
            }

            this.lastBoostPos = this.mc.player.getEntityPos();
         }
      }
   }

   private boolean handleObstaclePasser() {
      if (this.tempPath != null && this.mc.player.getBlockPos().getSquaredDistance(this.tempPath) < 500.0) {
         this.tempPath = null;
         BaritoneBridge.clearGoal();
      } else if (this.tempPath != null) {
         BaritoneBridge.setGoalBlockAndPath(this.tempPath.getX(), this.tempPath.getY(), this.tempPath.getZ());
         return true;
      }

      if (BaritoneBridge.hasGoal()) {
         return true;
      } else {
         if (this.lastUnstuckPos == null) {
            this.lastUnstuckPos = this.mc.player.getEntityPos();
         }

         if (this.mc.player.squaredDistanceTo(this.lastUnstuckPos) < 25.0) {
            this.stuckTimer++;
         } else {
            this.stuckTimer = 0;
            this.lastUnstuckPos = this.mc.player.getEntityPos();
         }

         this.ensureObstaclePasserYaw();
         if (this.mc.player.getEntityPos().length() > 100.0
            && (
               this.mc.player.getY() < this.elytraFly.targetY.get().intValue()
                  || this.mc.player.getY() > this.elytraFly.targetY.get() + 2
                  || this.mc.player.horizontalCollision && !this.mc.player.collidedSoftly
                  || this.portalTrap != null
                     && this.portalTrap.getSquaredDistance(this.mc.player.getBlockPos())
                        < this.elytraFly.portalAvoidDistance.get() * this.elytraFly.portalAvoidDistance.get()
                  || this.waitingForChunksToLoad
                  || this.stuckTimer > 50
            )) {
            this.waitingForChunksToLoad = false;
            this.paused = true;
            BlockPos goal = this.mc.player.getBlockPos();
            double currDistance = this.elytraFly.obstacleDistance.get();
            if (this.portalTrap != null) {
               currDistance += this.mc.player.getEntityPos().distanceTo(this.portalTrap.toCenterPos());
               this.portalTrap = null;
               this.elytraFly.info("Pathing around portal.", new Object[0]);
            }

            BlockPos start = this.getStartPos();

            while (!(currDistance > 80.0)) {
               Vec3d unitYawVec = HuntingUtils.yawToDirection(this.obstaclePasserYaw);
               Vec3d travelVec = this.mc.player.getEntityPos().subtract(start.toCenterPos());
               double parallelCurrPosDot = travelVec.multiply(1.0, 0.0, 1.0).dotProduct(unitYawVec);
               Vec3d parallelCurrPosComponent = unitYawVec.multiply(parallelCurrPosDot);
               Vec3d pos = start.toCenterPos().add(parallelCurrPosComponent);
               pos = HuntingUtils.positionInDirection(pos, this.obstaclePasserYaw, currDistance);
               goal = new BlockPos((int)Math.floor(pos.x), this.elytraFly.targetY.get(), (int)Math.floor(pos.z));
               currDistance++;
               if (this.mc.world.getBlockState(goal).getBlock() == Blocks.VOID_AIR) {
                  this.waitingForChunksToLoad = true;
                  return true;
               }

               if (this.mc.world.getBlockState(goal.down()).isSolidBlock(this.mc.world, goal.down())
                  && this.mc.world.getBlockState(goal).getBlock() != Blocks.NETHER_PORTAL
                  && this.mc.world.getBlockState(goal).isAir()) {
                  BaritoneBridge.setGoalBlockAndPath(goal.getX(), goal.getY(), goal.getZ());
                  return true;
               }
            }

            this.tempPath = goal;
            BaritoneBridge.setGoalBlockAndPath(goal.getX(), goal.getY(), goal.getZ());
            return true;
         } else {
            this.paused = false;
            return false;
         }
      }
   }

   @Override
   public void onChunkData(ChunkDataEvent event) {
      if (this.elytraFly.avoidPortalTraps.get() && this.elytraFly.highwayObstaclePasser.get()) {
         if (this.mc.player != null && this.mc.world != null) {
            this.ensureObstaclePasserYaw();
            ChunkPos pos = event.chunk().getPos();
            BlockPos centerPos = pos.getCenterAtY(this.elytraFly.targetY.get());
            Vec3d moveDir = HuntingUtils.yawToDirection(this.obstaclePasserYaw);
            double distanceToHighway = HuntingUtils.distancePointToDirection(Vec3d.of(centerPos), moveDir, this.mc.player.getEntityPos());
            if (!(distanceToHighway > 21.0)) {
               for (int x = 0; x < 16; x++) {
                  for (int z = 0; z < 16; z++) {
                     for (int y = this.elytraFly.targetY.get(); y < this.elytraFly.targetY.get() + 3; y++) {
                        BlockPos position = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);
                        if (!(
                              HuntingUtils.distancePointToDirection(Vec3d.of(position), moveDir, this.mc.player.getEntityPos())
                                 > this.elytraFly.portalScanWidth.get().intValue()
                           )
                           && this.mc.world.getBlockState(position).getBlock().equals(Blocks.NETHER_PORTAL)) {
                           BlockPos posBehind = new BlockPos(
                              (int)Math.floor(position.getX() + moveDir.x), position.getY(), (int)Math.floor(position.getZ() + moveDir.z)
                           );
                           if ((
                                 this.mc.world.getBlockState(posBehind).isSolidBlock(this.mc.world, posBehind)
                                    || this.mc.world.getBlockState(posBehind).getBlock() == Blocks.NETHER_PORTAL
                              )
                              && (
                                 this.portalTrap == null
                                    || this.portalTrap.getSquaredDistance(posBehind) > 100.0
                                       && this.mc.player.getBlockPos().getSquaredDistance(posBehind)
                                          < this.mc.player.getBlockPos().getSquaredDistance(this.portalTrap)
                              )) {
                              this.portalTrap = posBehind;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private BlockPos getStartPos() {
      return this.elytraFly.useCustomStartPos.get() ? this.elytraFly.startPos.get() : new BlockPos(0, 0, 0);
   }

   private void ensureObstaclePasserYaw() {
      if (!this.obstaclePasserYawComputed && this.mc.player != null) {
         if (this.elytraFly.yawLockMode.get() == Rotation.LockMode.Simple) {
            this.obstaclePasserYaw = this.elytraFly.yaw.get();
            this.obstaclePasserYawComputed = true;
         } else if (!(this.mc.player.getEntityPos().multiply(1.0, 0.0, 1.0).length() < 100.0)) {
            BlockPos start = this.getStartPos();
            if (this.mc.player.getBlockPos().getSquaredDistance(start) < 10000.0) {
               this.obstaclePasserYaw = HuntingUtils.angleOnAxis(this.mc.player.getYaw());
            } else {
               BlockPos directionVec = this.mc.player.getBlockPos().subtract(start);
               double angle = Math.toDegrees(Math.atan2(-directionVec.getX(), directionVec.getZ()));
               double angleNormalized = HuntingUtils.angleOnAxis(angle);
               if (!this.elytraFly.awayFromStartPos.get()) {
                  angleNormalized += 180.0;
               }

               this.obstaclePasserYaw = angleNormalized;
            }

            this.obstaclePasserYawComputed = true;
         }
      }
   }

   private void unpress() {
      this.release(this.mc.options.forwardKey);
      if (this.elytraFly.autoJump.get()) {
         this.release(this.mc.options.jumpKey);
      }
   }

   private void setPressed(KeyBinding key, boolean pressed) {
      key.setPressed(pressed);
   }

   private void release(KeyBinding key) {
      key.setPressed(Input.isPressed(key));
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
      return !player.getAbilities().flying && !player.hasVehicle() && !player.isClimbing() && itemStack.isOf(Items.ELYTRA) && isElytraUsable(itemStack);
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

   private float getYawDirection() {
      if (this.elytraFly.highwayObstaclePasser.get() && this.obstaclePasserYawComputed) {
         return (float)this.obstaclePasserYaw;
      } else {
         return switch ((Rotation.LockMode)this.elytraFly.yawLockMode.get()) {
            case None -> this.mc.player.getYaw();
            case Smart -> Math.round((this.mc.player.getYaw() + 1.0F) / 45.0F) * 45.0F;
            case Simple -> this.elytraFly.yaw.get().floatValue();
         };
      }
   }

   @Override
   public void onCorrected(Vec3d offset) {
      if (this.lastBoostPos != null) {
         this.lastBoostPos = this.lastBoostPos.add(offset);
      }

      if (this.lastUnstuckPos != null) {
         this.lastUnstuckPos = this.lastUnstuckPos.add(offset);
      }

      if (!Double.isNaN(this.roofSimFloorY)) {
         this.roofSimFloorY = this.roofSimFloorY + offset.y;
      }
   }

   @Override
   public void onActivate() {
      super.onActivate();
      this.prevFov = (Double)this.mc.options.getFovEffectScale().getValue();
      this.lastBoostPos = this.mc.player != null ? this.mc.player.getEntityPos() : null;
      this.lastUnstuckPos = this.mc.player != null ? this.mc.player.getEntityPos() : null;
      this.stuckTimer = 0;
      this.tempPath = null;
      this.portalTrap = null;
      this.paused = false;
      this.waitingForChunksToLoad = false;
      this.obstaclePasserYawComputed = false;
      if (this.mc.player != null && this.elytraFly.highwayObstaclePasser.get() && this.mc.player.getEntityPos().multiply(1.0, 0.0, 1.0).length() >= 100.0) {
         if (!BaritoneBridge.elytraHasDestination()) {
            BaritoneBridge.clearGoal();
         }

         this.ensureObstaclePasserYaw();
      }
   }

   @Override
   public void onDeactivate() {
      super.onDeactivate();
      this.unpress();
      if (this.prevFov != 0.0 && !this.elytraFly.sprint.get()) {
         this.mc.options.getFovEffectScale().setValue(this.prevFov);
      }

      if (this.elytraFly.highwayObstaclePasser.get() && !BaritoneBridge.elytraHasDestination()) {
         BaritoneBridge.clearGoal();
      }

      this.paused = false;
      this.tempPath = null;
   }
}
