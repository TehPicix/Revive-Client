package revivedevelopment.reviveclient.pingbypass.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.UnknownCustomPayload;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.state.NetworkState;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.mixin.ClientConnectionAccessor;
import revivedevelopment.reviveclient.mixin.LivingEntityAccessor;
import revivedevelopment.reviveclient.mixin.pingbypass.PbEncoderAccessor;
import revivedevelopment.reviveclient.pingbypass.PbDrive;
import revivedevelopment.reviveclient.pingbypass.PbFakes;
import revivedevelopment.reviveclient.systems.managers.RotationManager;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.RocketBoost;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.systems.modules.player.ElytraControl;

public final class PbPredict {
   private static final PbPredict BUS = new PbPredict();
   private static volatile boolean subscribed;
   private static final int RING = 256;
   private static final double EPS = 1.0E-4;
   private static final double SNAP = 10.0;
   private static final double BLOCKED = 0.5;
   private static final double SPRINT_SPOOF_PRIORITY = 2000.0;
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static volatile ClientConnection connection;
   private static NetworkState<?> idState;
   private static int stateFrameId = -1;
   private static int seq;
   private static final PbPredict.Pred[] ring = new PbPredict.Pred[256];
   private static int lastSeq = -1;
   private static int teleports;
   private static int tpWorld = -1;
   private static int rotations;
   private static int rotWorld = -1;
   private static int sentKeys;
   private static int slotPackets;
   private static int tickActions;
   private static int tickSplit = -1;
   private static final Object COUNTS = new Object();
   private static int offThread;
   private static float aimYaw = Float.NaN;
   private static float aimPitch;
   private static boolean aimMoveFix;
   private static int useSeq = -1;
   private static int vpsNotUsing;
   private static volatile int corrections;
   private static volatile double lastError;

   private PbPredict() {
   }

   static void begin(ClientConnection routed) {
      if (!subscribed) {
         subscribed = true;
         ReviveClient.EVENT_BUS.subscribe(BUS);
      }

      connection = routed;
      mc.execute(PbPredict::reset);
   }

   public static boolean isDriven(ClientConnection c) {
      return c != null && c == connection && PbRouting.isRouted(c);
   }

   public static boolean serverPushesOnly() {
      ClientPlayNetworkHandler handler = mc.getNetworkHandler();
      return handler != null && isDriven(handler.getConnection());
   }

   public static void onLeftPlay() {
      tickActions = 0;
      tickSplit = -1;
      synchronized (COUNTS) {
         offThread = 0;
      }
   }

   public static void onSlotPacket() {
      if (serverPushesOnly()) {
         slotPackets++;
      }
   }

   public static int corrections() {
      return corrections;
   }

   public static double lastError() {
      return lastError;
   }

   private static void reset() {
      seq = 0;
      lastSeq = -1;
      clearRing();
      teleports = 0;
      tpWorld = -1;
      rotations = 0;
      rotWorld = -1;
      sentKeys = 0;
      slotPackets = 0;
      tickActions = 0;
      tickSplit = -1;
      synchronized (COUNTS) {
         offThread = 0;
      }

      aimYaw = Float.NaN;
      useSeq = -1;
      vpsNotUsing = 0;
   }

   public static void onSend(ClientConnection c, Packet<?> packet) {
      if (c == connection) {
         if (!mc.isOnThread()) {
            if (mc.world != null && PbDrive.isPlayed(packet) && !(packet instanceof PlayerInputC2SPacket) && !(packet instanceof PlayerMoveC2SPacket)) {
               synchronized (COUNTS) {
                  offThread++;
               }
            }
         } else if (mc.world != null) {
            if (packet instanceof PlayerInputC2SPacket input) {
               sentKeys = keys(input.input()) | 128;
               if (tickSplit < 0) {
                  tickSplit = tickActions;
               }
            } else if (packet instanceof PlayerMoveC2SPacket) {
               if (tickSplit < 0 && PbRouting.isVanillaMove()) {
                  tickSplit = tickActions;
               }
            } else {
               if (PbDrive.isPlayed(packet)) {
                  tickActions++;
               }
            }
         }
      }
   }

   @EventHandler(
      priority = -200
   )
   private void onSendMovementPre(SendMovementPacketsEvent.Pre event) {
      if (serverPushesOnly()) {
         RotationManager rotation = ReviveClient.ROTATION;
         aimYaw = Float.NaN;
         if (rotation != null && rotation.hasMotionRotation()) {
            if (!(rotation.getMotionPriority() >= 2000.0) || rotation.moveFixActive()) {
               aimYaw = rotation.getMotionYaw();
               aimPitch = rotation.getOutgoingPitch();
               aimMoveFix = rotation.moveFixActive();
            }
         }
      }
   }

   public static void beforeTickEnd() {
      ClientPlayNetworkHandler handler = mc.getNetworkHandler();
      ClientPlayerEntity player = mc.player;
      int extra;
      synchronized (COUNTS) {
         extra = offThread;
         offThread = 0;
      }

      int actions = tickActions + extra;
      int split = (tickSplit < 0 ? tickActions : tickSplit) + extra;
      float yawAim = aimYaw;
      float pitchAim = aimPitch;
      boolean moveFix = aimMoveFix;
      tickActions = 0;
      tickSplit = -1;
      aimYaw = Float.NaN;
      if (handler != null && player != null && mc.world != null) {
         ClientConnection c = handler.getConnection();
         if (isDriven(c)) {
            Channel channel = ((ClientConnectionAccessor)c).getChannel();
            ChannelHandlerContext encoder = channel == null ? null : channel.pipeline().context("encoder");
            NetworkState<?> state = encoder != null && encoder.handler() instanceof PbEncoderAccessor a ? a.revive$pbEncoderState() : null;
            if (state != null && state.id() == NetworkPhase.PLAY) {
               PbDrive.Input input = input(player, seq, yawAim, pitchAim, moveFix, Math.min(split, actions));

               ByteBuf frame;
               try {
                  frame = PbDrive.inputFrame(state, input);
               } catch (RuntimeException var21) {
                  ReviveClient.LOG.error("[PingBypass] could not make the input frame", var21);
                  return;
               }

               encoder.writeAndFlush(frame);
               if (!player.isUsingItem()) {
                  useSeq = -1;
               } else if (useSeq < 0 || player.getItemUseTime() <= 1) {
                  useSeq = seq;
                  vpsNotUsing = 0;
               }

               Vec3d pos = player.getEntityPos();
               Vec3d vel = player.getVelocity();
               double fh;
               double fv;
               if (player.isGliding()) {
                  fh = 0.99;
                  fv = 0.98;
               } else if (player.isInLava()) {
                  fh = 0.5;
                  fv = 0.5;
               } else if (player.isTouchingWater()) {
                  fh = 0.8;
                  fv = 0.8;
               } else {
                  float slip = player.isOnGround() ? mc.world.getBlockState(player.getVelocityAffectingPos()).getBlock().getSlipperiness() : 1.0F;
                  fh = slip * 0.91;
                  fv = 0.98;
               }

               ring[seq & 0xFF] = new PbPredict.Pred(
                  seq,
                  input.world(),
                  new double[]{pos.x, pos.y, pos.z, vel.x, vel.y, vel.z},
                  fh,
                  fv,
                  player.isSprinting(),
                  player.isGliding(),
                  player.isOnGround(),
                  ((LivingEntityAccessor)player).getJumpCooldown(),
                  elytraFrozen()
               );
               lastSeq = seq++;
            }
         }
      }
   }

   private static int keys(PlayerInput input) {
      int keys = 0;
      if (input.forward()) {
         keys |= 1;
      }

      if (input.backward()) {
         keys |= 2;
      }

      if (input.left()) {
         keys |= 4;
      }

      if (input.right()) {
         keys |= 8;
      }

      if (input.jump()) {
         keys |= 16;
      }

      if (input.sneak()) {
         keys |= 32;
      }

      if (input.sprint()) {
         keys |= 64;
      }

      return keys;
   }

   private static PbDrive.Input input(ClientPlayerEntity player, int seq, float aimYaw, float aimPitch, boolean aimMoveFix, int split) {
      GameOptions o = mc.options;
      int keys = 0;
      if (!(player.input instanceof KeyboardInput)) {
         keys = keys(player.input.playerInput) & 47;
      } else {
         if (o.forwardKey.isPressed()) {
            keys |= 1;
         }

         if (o.backKey.isPressed()) {
            keys |= 2;
         }

         if (o.leftKey.isPressed()) {
            keys |= 4;
         }

         if (o.rightKey.isPressed()) {
            keys |= 8;
         }
      }

      if (o.jumpKey.isPressed() || player.input.playerInput.jump()) {
         keys |= 16;
      }

      if (o.sneakKey.isPressed()) {
         keys |= 32;
      }

      if (o.sprintKey.isPressed() || player.isSprinting()) {
         keys |= 64;
      }

      if (o.useKey.isPressed()) {
         keys |= 128;
      }

      Screen screen = mc.currentScreen;
      int open = screen == null
         ? 0
         : (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeInventoryScreen) ? (screen instanceof HandledScreen ? 2 : 3) : 1);
      Vec3d pos = player.getEntityPos();
      Vec3d vel = player.getVelocity();
      return new PbDrive.Input(
         seq,
         PbFakes.worldCount(),
         rotationsNow(),
         keys,
         sentKeys,
         player.getYaw(),
         player.getPitch(),
         aimYaw,
         aimPitch,
         aimMoveFix,
         split,
         slotPackets,
         open,
         pos.x,
         pos.y,
         pos.z,
         vel.x,
         vel.y,
         vel.z
      );
   }

   public static boolean onFrame(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out, NetworkState<?> state) {
      ClientConnection c = connection;
      if (c == null || state == null || state.id() != NetworkPhase.PLAY) {
         return false;
      } else if (ctx.channel() != ((ClientConnectionAccessor)c).getChannel()) {
         return false;
      } else {
         if (state != idState) {
            stateFrameId = PbDrive.packetId(state, new CustomPayloadS2CPacket(new UnknownCustomPayload(PbDrive.STATE)));
            idState = state;
         }

         if (stateFrameId >= 0 && PbDrive.isFrame(buf, stateFrameId, PbDrive.STATE)) {
            try {
               out.add(new CustomPayloadS2CPacket(new PbDrive.StatePayload(PbDrive.readState(buf))));
            } catch (RuntimeException var6) {
               ReviveClient.LOG.warn("[PingBypass] dropped a malformed state frame from the VPS: {}", var6.toString());
            }

            buf.skipBytes(buf.readableBytes());
            return true;
         } else {
            return false;
         }
      }
   }

   public static void onTeleported() {
      if (serverPushesOnly()) {
         teleportsNow();
         teleports++;
         onRotated();
         clearRing();
      }
   }

   public static void onRotated() {
      if (serverPushesOnly()) {
         rotationsNow();
         rotations++;
      }
   }

   private static int teleportsNow() {
      int w = PbFakes.worldCount();
      if (w != tpWorld) {
         tpWorld = w;
         teleports = 0;
      }

      return teleports;
   }

   private static int rotationsNow() {
      int w = PbFakes.worldCount();
      if (w != rotWorld) {
         rotWorld = w;
         rotations = 0;
      }

      return rotations;
   }

   public static void onState(PbDrive.State s) {
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null && serverPushesOnly()) {
         checkUse(player, s);
         if (s.has(32)) {
            if (s.world() == PbFakes.worldCount() && s.teleports() == teleportsNow() && s.seq() >= 0 && s.seq() <= lastSeq) {
               PbPredict.Pred p = ring[s.seq() & 0xFF];
               if (p != null && p.seq == s.seq() && p.world == s.world()) {
                  double[] e = new double[]{s.x() - p.v[0], s.y() - p.v[1], s.z() - p.v[2], s.vx() - p.v[3], s.vy() - p.v[4], s.vz() - p.v[5]};
                  if (frozenAt(s.seq() + 1)) {
                     e[3] = 0.0;
                     e[4] = 0.0;
                     e[5] = 0.0;
                  }

                  dropThrough(s.seq());
                  if (!player.hasVehicle()) {
                     boolean sprinting = s.has(2);
                     if (sprinting != p.sprinting && player.isSprinting() == p.sprinting) {
                        player.setSprinting(sprinting);
                     }

                     boolean gliding = s.has(8);
                     if (gliding != p.gliding && player.isGliding() == p.gliding) {
                        if (gliding) {
                           player.startGliding();
                        } else {
                           player.stopGliding();
                        }
                     }

                     int cooldownDiff = s.jumpCooldown() - p.jumpCooldown;
                     if (cooldownDiff != 0) {
                        LivingEntityAccessor jumper = (LivingEntityAccessor)player;
                        jumper.setJumpCooldown(Math.max(0, jumper.getJumpCooldown() + cooldownDiff));

                        for (PbPredict.Pred q : ring) {
                           if (q != null && q.seq > s.seq()) {
                              q.jumpCooldown = Math.max(0, q.jumpCooldown + cooldownDiff);
                           }
                        }
                     }

                     double error = Math.max(Math.abs(e[0]), Math.max(Math.abs(e[1]), Math.abs(e[2])));
                     double velError = Math.max(Math.abs(e[3]), Math.max(Math.abs(e[4]), Math.abs(e[5])));
                     if (!(error < 1.0E-4) || !(velError < 1.0E-4)) {
                        lastError = error;
                        corrections++;
                        Vec3d atSeq = new Vec3d(e[0], e[1], e[2]);
                        boolean snap = error > 10.0;
                        int ticks = Math.max(0, lastSeq - s.seq());
                        double[][] carried = new double[ticks][];
                        boolean ballistic = !s.has(1)
                           && !s.has(8)
                           && p.fv == 0.98
                           && !p.gliding
                           && !player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                           && !player.hasStatusEffect(StatusEffects.LEVITATION);
                        double by = s.y();
                        double bvy = s.vy();
                        double gravity = player.getFinalGravity();
                        double fh = p.fh;
                        double fv = p.fv;

                        for (int i = s.seq() + 1; i <= lastSeq; i++) {
                           PbPredict.Pred qx = ring[i & 0xFF];
                           boolean stored = qx != null && qx.seq == i;
                           if (stored && qx.frozen) {
                              e[3] = 0.0;
                              e[4] = 0.0;
                              e[5] = 0.0;
                           }

                           e[0] += e[3];
                           e[1] += e[4];
                           e[2] += e[5];
                           e[3] *= fh;
                           e[4] *= fv;
                           e[5] *= fh;
                           if (ballistic) {
                              by += bvy;
                              bvy = (bvy - gravity) * 0.98F;
                              if (stored) {
                                 if (qx.onGround && by <= qx.v[1]) {
                                    by = qx.v[1];
                                    bvy = qx.v[4];
                                 }

                                 e[1] = by - qx.v[1];
                                 e[4] = bvy - qx.v[4];
                              }
                           }

                           carried[i - s.seq() - 1] = (double[])e.clone();
                           if (stored) {
                              fh = qx.fh;
                              fv = qx.fv;
                           }
                        }

                        Vec3d pos = player.getEntityPos();
                        Vec3d vel = player.getVelocity();
                        Box box = player.getBoundingBox();
                        Vec3d shift = new Vec3d(e[0], e[1], e[2]);
                        Vec3d free = Entity.adjustMovementForCollisions(player, shift, box, mc.world, List.of());
                        double cut = free.distanceTo(shift);
                        if (cut <= 1.0E-4) {
                           if (snap) {
                              clearRing();
                           } else {
                              for (int i = s.seq() + 1; i <= lastSeq; i++) {
                                 PbPredict.Pred qxx = ring[i & 0xFF];
                                 if (qxx != null && qxx.seq == i) {
                                    for (int k = 0; k < 6; k++) {
                                       qxx.v[k] = qxx.v[k] + carried[i - s.seq() - 1][k];
                                    }
                                 }
                              }
                           }

                           shift = free;
                           player.setPosition(pos.x + free.x, pos.y + free.y, pos.z + free.z);
                           player.setVelocity(vel.x + e[3], vel.y + e[4], vel.z + e[5]);
                        } else {
                           clearRing();
                           if (cut <= 0.5) {
                              shift = free;
                              player.setPosition(pos.x + free.x, pos.y + free.y, pos.z + free.z);
                              player.setVelocity(
                                 free.x != e[0] ? vel.x : vel.x + e[3], free.y != e[1] ? vel.y : vel.y + e[4], free.z != e[2] ? vel.z : vel.z + e[5]
                              );
                           } else {
                              Vec3d baseFree = Entity.adjustMovementForCollisions(player, atSeq, box, mc.world, List.of());
                              if (baseFree.distanceTo(atSeq) <= 0.5) {
                                 shift = baseFree;
                              } else if (mc.world.isSpaceEmpty(player, box.offset(atSeq))) {
                                 shift = atSeq;
                              } else {
                                 shift = null;
                              }

                              if (shift != null) {
                                 player.setPosition(pos.x + shift.x, pos.y + shift.y, pos.z + shift.z);
                              } else {
                                 player.setPosition(s.x(), s.y(), s.z());
                                 player.setVelocity(s.vx(), s.vy(), s.vz());
                                 shift = new Vec3d(s.x() - pos.x, s.y() - pos.y, s.z() - pos.z);
                              }
                           }
                        }

                        settle(player);
                        Modules modules = Modules.get();
                        if (modules != null) {
                           RocketBoost boost = modules.get(RocketBoost.class);
                           if (boost != null) {
                              boost.onCorrected(shift);
                           }

                           ElytraFly fly = modules.get(ElytraFly.class);
                           if (fly != null) {
                              fly.onCorrected(shift);
                           }

                           ElytraControl control = modules.get(ElytraControl.class);
                           if (control != null) {
                              control.onCorrected(shift);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean elytraFrozen() {
      Modules modules = Modules.get();
      ElytraControl control = modules == null ? null : modules.get(ElytraControl.class);
      return control != null && control.isFrozen();
   }

   private static boolean frozenAt(int i) {
      if (i > lastSeq) {
         return elytraFrozen();
      } else {
         PbPredict.Pred q = ring[i & 0xFF];
         return q != null && q.seq == i && q.frozen;
      }
   }

   private static void checkUse(ClientPlayerEntity player, PbDrive.State s) {
      if (useSeq >= 0 && s.seq() >= useSeq && s.world() == PbFakes.worldCount()) {
         if (s.has(16) || !player.isUsingItem()) {
            vpsNotUsing = 0;
         } else if (++vpsNotUsing >= 2) {
            vpsNotUsing = 0;
            useSeq = -1;
            player.clearActiveItem();
         }
      }
   }

   private static void settle(ClientPlayerEntity player) {
      player.horizontalCollision = false;
      player.setOnGround(player.getVelocity().y <= 0.0 && !mc.world.isSpaceEmpty(player, player.getBoundingBox().offset(0.0, -0.001, 0.0)));
   }

   private static void dropThrough(int through) {
      for (int i = 0; i < 256; i++) {
         if (ring[i] != null && ring[i].seq <= through) {
            ring[i] = null;
         }
      }
   }

   private static void clearRing() {
      for (int i = 0; i < 256; i++) {
         ring[i] = null;
      }
   }

   @EventHandler(
      priority = 210
   )
   private void onReceive(PacketEvent.Receive event) {
      if (event.packet instanceof EntityVelocityUpdateS2CPacket velocity && isDriven(event.connection)) {
         ClientPlayerEntity player = mc.player;
         if (player != null && velocity.getEntityId() == player.getId()) {
            event.cancel();
         }
      }
   }

   private static final class Pred {
      final int seq;
      final int world;
      final double[] v;
      final double fh;
      final double fv;
      final boolean sprinting;
      final boolean gliding;
      final boolean onGround;
      final boolean frozen;
      int jumpCooldown;

      Pred(int seq, int world, double[] v, double fh, double fv, boolean sprinting, boolean gliding, boolean onGround, int jumpCooldown, boolean frozen) {
         this.seq = seq;
         this.world = world;
         this.v = v;
         this.fh = fh;
         this.fv = fv;
         this.sprinting = sprinting;
         this.gliding = gliding;
         this.onGround = onGround;
         this.jumpCooldown = jumpCooldown;
         this.frozen = frozen;
      }
   }
}
