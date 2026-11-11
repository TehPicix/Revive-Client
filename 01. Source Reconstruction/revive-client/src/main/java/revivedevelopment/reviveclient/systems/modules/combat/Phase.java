package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.MultiTask;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Phase extends Module {
   private static final double X_LOW = Math.min(Math.PI / 13, 0.758339026646939);
   private static final double X_HIGH = Math.max(Math.PI / 13, 0.758339026646939);
   private static final double Z_LOW = Math.min(Math.PI / 4, 0.21460183660255172);
   private static final double Z_HIGH = Math.max(Math.PI / 4, 0.21460183660255172);
   private static final double MAX_NUDGE = 0.2;
   private static final double CORNER_NUDGE = 0.3;
   private static final double DEBUG_BOX_SIZE = 0.08;
   private static final float ANTI_VOID_PITCH = 75.0F;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Keybind> phaseBind = this.sgGeneral
      .add(new KeybindSetting.Builder().name("phase-bind").description("Press to throw a single phase pearl.").defaultValue(Keybind.none()).build());
   private final Setting<Boolean> antiVoid = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("anti-void")
            .description("On the lowest block layer, aim straight down and one block higher so you don't phase into the void.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> corner = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("corner")
            .description("Always land in the corner of the block, so you end up inside the walls on both sides (and the diagonal) instead of just one.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> placeBlock = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("burrow")
            .description("Place a single obsidian block in your phase direction (the cell you clip into) before throwing. This is what keeps you in the wall.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> crawlPhase = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("crawl-phase")
            .description(
               "Allow throwing while crawling (manual, hold and rephase). The throw is aimed from the crawl eye height so the pearl lands where a standing throw would. Off = old PearlPhase behaviour: never phase in the crawl pose."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Phase.RotateMode> rotateMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("rotate-mode"))
                  .description(
                     "Movement: the throw rotation rides your normal move packet, throw once the server holds it. Instant: snap and throw on the same tick (on ground only). DelayedInstant: like Movement, then re-snap right before the throw. DelayedInstantWebOnly: like Movement, re-snap only while in cobwebs. Silent: capped silent look packet plus a one-tick warmup."
                  ))
               .defaultValue(Phase.RotateMode.DelayedInstantWebOnly))
            .build()
      );
   private final Setting<Boolean> antiPearlFail = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("anti-pearl-fail")
            .description("Simulate the pearl first: hit an end crystal or item frame in its path before throwing, and break scaffolding you're standing in.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> antiPearlFailStrict = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("anti-pearl-fail-strict")
            .description("Don't throw until nothing is in the pearl's path (and the scaffolding is actually gone) instead of throwing right after the hit.")
            .defaultValue(false)
            .visible(this.antiPearlFail::get)
            .build()
      );
   private final Setting<Integer> scaffoldBreakDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("scaffold-break-delay")
            .description("Ticks to wait after breaking the scaffolding you're standing in before throwing.")
            .defaultValue(1)
            .min(0)
            .max(20)
            .sliderMin(0)
            .sliderMax(10)
            .visible(this.antiPearlFail::get)
            .build()
      );
   private final Setting<Boolean> hold = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hold")
            .description(
               "The old PearlPhase 'rephase': while the bind is held, throw whenever you're able to phase - not already in a wall, not crawling (unless crawl-phase), pearl off cooldown. Sneak to stop."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> rephase = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rephase")
            .description(
               "When a block you're phased into gets broken (not by you) and you're no longer in any block, re-throw (in your WASD / look direction) as soon as you're able to phase. If the break leaves you crawling it waits until you can stand, unless crawl-phase is on. Sneak or walk away to cancel."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> rephasePrime = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rephase-prime")
            .description(
               "When a block you're phased into is close to breaking (server break stage 6/10+ from another player's dig), pre-hold the throw rotation through the rotate-mode path, so the rephase / hold pearl goes out on the very tick the block breaks."
            )
            .defaultValue(true)
            .visible(() -> this.rephase.get() || this.hold.get())
            .build()
      );
   private final Setting<Integer> rephaseTimeout = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("rephase-timeout")
            .description(
               "Ticks a rephase stays pending after the block breaks (e.g. waiting out the pearl cooldown) before giving up. Ticks spent waiting out a crawl don't count."
            )
            .defaultValue(20)
            .range(1, 60)
            .sliderRange(1, 40)
            .visible(this.rephase::get)
            .build()
      );
   private final Setting<Boolean> chatFeedback = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat-feedback").description("Print a message when a phase pearl is thrown.").defaultValue(false).build());
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Print why Phase is waiting, backing off or giving up the shared rotation (deduped, once a second at most).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> debugRender = this.sgRender
      .add(new BoolSetting.Builder().name("debug-render").description("Render the computed pearl target.").defaultValue(false).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("debug-shape-mode"))
                     .description("How the debug target is rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.debugRender::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("debug-side-color")
            .description("Side color of the debug target.")
            .defaultValue(new SettingColor(0, 200, 255, 30))
            .visible(() -> this.debugRender.get() && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("debug-line-color")
            .description("Line color of the debug target.")
            .defaultValue(new SettingColor(0, 200, 255, 255))
            .visible(() -> this.debugRender.get() && this.shapeMode.get().lines())
            .build()
      );
   private static final double PRIME_PRIORITY = 800.0;
   private static final int MAX_THROW_HOLD_TICKS = 10;
   private static final int MAX_ARM_TICKS = 40;
   private static final int MAX_PRIME_TICKS = 40;
   private static final int PRIME_BACKOFF_TICKS = 20;
   private static final int HOLD_BACKOFF_TICKS = 20;
   private static final int HOLD_REFUSED_BACKOFF_TICKS = 5;
   private static final double AIM_DISTANCE = 2.0;
   private static final double AIM_BOX_SIZE = 0.05;
   private static final int MOTION_STALL_TICKS = 3;
   private static final double PEARL_SPEED = 1.5;
   private static final double PEARL_GRAVITY = 0.03;
   private static final double PEARL_DRAG = 0.99;
   private static final double PEARL_ENTITY_MARGIN = 0.3;
   private static final int PEARL_SIM_TICKS = 40;
   private static final int WARMUP_TICKS = 1;
   private static final String ROTATION_ID = "phase";
   private static final double THROW_PRIORITY = 1300.0;
   private static final double PEARL_SPAWN_DROP = 0.1;
   private static final double PHASE_EPSILON = 0.01;
   private static final double REPHASE_CANCEL_DISTANCE = 1.0;
   private boolean active;
   private Phase.ArmSource activeSource;
   private boolean bindReleased = true;
   private int rotationTicksHeld;
   private int armTicks;
   private int throwHoldTicks;
   private int holdBackoff;
   private int primeBackoff;
   private String lastDebug;
   private long lastDebugMs;
   private static final long MINE_SEEN_TIMEOUT_MS = 3000L;
   private final List<BlockPos> insideBlocks = new ArrayList<>();
   private final Map<BlockPos, Phase.MineInfo> minedBlocks = new ConcurrentHashMap<>();
   private static final int PRIME_STAGE = 5;
   private boolean rephasePending;
   private int rephasePendingTicks;
   private int primedTicks;
   private Vec3d rephaseAnchor;
   private int motionStallTicks;
   private boolean snappedThisTick;
   private int scaffoldBreakTimer;
   private boolean scaffoldCleared;
   private final Set<Integer> attackedBlockers = new HashSet<>();

   public Phase() {
      super(Categories.Combat, "phase", "Phases into walls using pearls.");
   }

   @Override
   public void onDeactivate() {
      this.deactivate();
      this.bindReleased = true;
      this.clearRephase();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.holdBackoff > 0) {
            this.holdBackoff--;
         }

         if (this.primeBackoff > 0) {
            this.primeBackoff--;
         }

         this.updateArm();
         this.updateRephase();
         if (this.active) {
            if (!this.hasPearl()) {
               this.dbg("dropped the arm: no pearl");
               this.deactivate();
            } else if (this.mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
               this.dbg("dropped the arm: pearl on cooldown");
               this.deactivate();
            } else {
               EntityPose pose = this.predictPose(Collections.emptyList());
               if (this.crawlBlocked(pose)) {
                  boolean fromRephase = this.activeSource == Phase.ArmSource.REPHASE;
                  this.dbg("dropped the arm: crawling (crawl-phase is off)");
                  this.deactivate();
                  if (fromRephase) {
                     this.repend(false);
                  } else if (this.chatFeedback.get()) {
                     this.warning("Can't phase while crawling (crawl-phase is off).", new Object[0]);
                  }
               } else if (++this.armTicks > 40) {
                  this.giveUp("armed for 40 ticks without a throw");
               } else {
                  String refusal = this.throwRefusal();
                  if (refusal != null) {
                     this.dbg("waiting without the rotation: " + refusal);
                  } else {
                     float yaw = this.movementYaw();
                     Vec3d target = this.phaseTarget(yaw);
                     float[] rotation = this.throwRotation(target, pose);
                     float throwYaw = rotation[0];
                     float throwPitch = rotation[1];
                     Vec3d eye = this.poseEye(pose);
                     Phase.RotateMode mode = this.rotateMode.get();
                     if (this.scaffoldBreakTimer > 0) {
                        this.scaffoldBreakTimer--;
                        if (this.scaffoldBreakTimer == 0) {
                           if (this.throwHoldCapped(mode)) {
                              return;
                           }

                           this.warmThrowRotation(mode, throwYaw, throwPitch);
                        }
                     } else {
                        boolean strict = this.antiPearlFail.get() && this.antiPearlFailStrict.get();
                        Entity blocker = this.antiPearlFail.get() ? this.pearlBlocker(throwYaw, throwPitch, pose) : null;
                        boolean attackable = blocker instanceof EndCrystalEntity || blocker instanceof ItemFrameEntity;
                        if (blocker != null && !attackable && strict) {
                           this.dbg("waiting without the rotation: " + blocker.getName().getString() + " is in the pearl's path (strict)");
                        } else if (!this.throwHoldCapped(mode)) {
                           this.snappedThisTick = false;
                           if (this.holdThrowRotation(mode, throwYaw, throwPitch, eye)) {
                              if (!this.antiPearlFail.get() || !this.inScaffolding() || this.scaffoldCleared && !strict) {
                                 boolean attackInline = false;
                                 if (attackable) {
                                    if (ReviveClient.ROTATION.raytraceCheck(eye, throwYaw, throwPitch, blocker.getBoundingBox())) {
                                       attackInline = true;
                                    } else {
                                       float[] aim = ReviveClient.ROTATION.getRotation(eye, boxCenter(blocker.getBoundingBox()));
                                       if (mode == Phase.RotateMode.Silent || ReviveClient.ROTATION.silentLooksRideMotion()) {
                                          if (ReviveClient.ROTATION.requestSilentRotation("phase", 1300.0, aim[0], aim[1])) {
                                             this.attackBlocker(blocker, strict);
                                          }

                                          this.rotationTicksHeld = 0;
                                          return;
                                       }

                                       ReviveClient.ROTATION.snapAt(aim[0], aim[1], true);
                                       this.attackBlocker(blocker, strict);
                                       if (strict) {
                                          return;
                                       }
                                    }
                                 }

                                 boolean var10000;
                                 label168: {
                                    label167: {
                                       if (!this.snappedThisTick) {
                                          switch (mode) {
                                             case Instant:
                                             case DelayedInstant:
                                                break label167;
                                             case DelayedInstantWebOnly:
                                                if (this.inWebs()) {
                                                   break label167;
                                                }
                                          }
                                       }

                                       var10000 = false;
                                       break label168;
                                    }

                                    var10000 = true;
                                 }

                                 boolean snap = var10000;
                                 if (attackInline && strict) {
                                    if (snap || !this.serverLookingAt(throwYaw, throwPitch, eye)) {
                                       ReviveClient.ROTATION.snapAt(throwYaw, throwPitch, true);
                                    }

                                    this.attackBlocker(blocker, true);
                                 } else {
                                    if (this.placeBlock.get()) {
                                       this.placePhaseBlock(yaw);
                                    }

                                    if (snap || !this.serverLookingAt(throwYaw, throwPitch, eye)) {
                                       ReviveClient.ROTATION.snapAt(throwYaw, throwPitch, true);
                                    }

                                    if (attackInline) {
                                       this.attackBlocker(blocker, false);
                                    }

                                    boolean thrown = this.throwPearl(throwYaw, throwPitch);
                                    if (thrown && this.chatFeedback.get()) {
                                       this.info(this.activeSource == Phase.ArmSource.REPHASE ? "Rephased." : "Threw pearl.", new Object[0]);
                                    }

                                    if (!thrown) {
                                       this.dbg("throw refused by the " + MultiTask.phaseMode() + " pearl swap");
                                    }

                                    Phase.ArmSource source = this.activeSource;
                                    this.deactivate();
                                    if (!thrown && source == Phase.ArmSource.REPHASE && --this.rephasePendingTicks > 0) {
                                       this.repend(false);
                                    }

                                    if (!thrown && source == Phase.ArmSource.HOLD) {
                                       this.holdBackoff = 5;
                                    }
                                 }
                              } else {
                                 this.breakScaffold();
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private String throwRefusal() {
      if (this.mc.currentScreen instanceof ChatScreen) {
         return "chat open";
      } else if (this.mc.player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
         return null;
      } else {
         switch (MultiTask.phaseMode()) {
            case Normal:
               if (this.mc.player.isUsingItem()) {
                  return "using an item (Normal phase mode won't throw mid-use)";
               }
               break;
            case Offhand:
               if (!ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                  return "click-slot packet budget spent";
               }
         }

         return null;
      }
   }

   private boolean throwHoldCapped(Phase.RotateMode mode) {
      if (mode == Phase.RotateMode.Instant) {
         return false;
      } else if (this.throwHoldTicks < 10) {
         return false;
      } else {
         this.giveUp("held the throw rotation 10 ticks without throwing");
         return true;
      }
   }

   private void warmThrowRotation(Phase.RotateMode mode, float yaw, float pitch) {
      switch (mode) {
         case Instant:
            return;
         case Silent:
            if (ReviveClient.ROTATION.requestSilentRotation("phase", 1300.0, yaw, pitch)) {
               this.rotationTicksHeld = Math.min(1, this.rotationTicksHeld + 1);
            }
            break;
         default:
            ReviveClient.ROTATION.requestMotionRotation(yaw, pitch, 1300.0);
      }

      this.throwHoldTicks++;
   }

   private void giveUp(String reason) {
      Phase.ArmSource source = this.activeSource;
      this.deactivate();
      this.dbg("gave up: " + reason);
      if (source == Phase.ArmSource.HOLD) {
         this.holdBackoff = 20;
      } else if (source == Phase.ArmSource.REPHASE) {
         this.cancelPending();
      } else if (source == Phase.ArmSource.MANUAL && this.chatFeedback.get()) {
         this.warning("Phase gave up: " + reason + ".", new Object[0]);
      }
   }

   private void dbg(String msg) {
      if (this.debug.get()) {
         long now = System.currentTimeMillis();
         if (!msg.equals(this.lastDebug) || now - this.lastDebugMs >= 1000L) {
            this.lastDebug = msg;
            this.lastDebugMs = now;
            this.info("%s", new Object[]{msg});
         }
      }
   }

   private void updateArm() {
      if (!this.phaseBind.get().isPressed()) {
         this.bindReleased = true;
      } else if (this.bindReleased && !(this.mc.currentScreen instanceof ChatScreen)) {
         this.bindReleased = false;
         if (this.crawlBlocked(this.predictPose(Collections.emptyList()))) {
            if (this.chatFeedback.get()) {
               this.warning("Can't phase while crawling (crawl-phase is off).", new Object[0]);
            }

            return;
         }

         this.arm(Phase.ArmSource.MANUAL);
      } else if (this.hold.get() && !this.active && this.canAutoArm()) {
         boolean warm = this.primedTicks >= 1;
         this.arm(Phase.ArmSource.HOLD);
         if (warm) {
            this.rotationTicksHeld = 1;
         }
      }
   }

   private void arm(Phase.ArmSource source) {
      this.active = true;
      this.activeSource = source;
      this.rotationTicksHeld = 0;
      this.armTicks = 0;
      this.throwHoldTicks = 0;
      this.motionStallTicks = 0;
      this.scaffoldBreakTimer = 0;
      this.scaffoldCleared = false;
      this.attackedBlockers.clear();
   }

   private boolean holdThrowRotation(Phase.RotateMode mode, float yaw, float pitch, Vec3d eye) {
      if (mode != Phase.RotateMode.Instant) {
         this.throwHoldTicks++;
      }

      switch (mode) {
         case Instant:
            return this.mc.player.isOnGround();
         case Silent:
            if (!ReviveClient.ROTATION.requestSilentRotation("phase", 1300.0, yaw, pitch)) {
               return false;
            } else {
               if (this.rotationTicksHeld < 1) {
                  this.rotationTicksHeld++;
                  return false;
               }

               return true;
            }
         default:
            boolean held = this.serverLookingAt(yaw, pitch, eye);
            ReviveClient.ROTATION.requestMotionRotation(yaw, pitch, 1300.0);
            if (held) {
               this.motionStallTicks = 0;
               return true;
            } else if (++this.motionStallTicks >= 3) {
               this.dbg("server didn't take the throw aim for 3 ticks - snapping it");
               ReviveClient.ROTATION.snapAt(yaw, pitch, true);
               this.snappedThisTick = true;
               this.motionStallTicks = 0;
               return true;
            } else {
               return false;
            }
      }
   }

   private boolean serverLookingAt(float yaw, float pitch, Vec3d eye) {
      Vec3d dir = rotationVector(yaw, pitch);
      Vec3d point = eye.add(dir.x * 2.0, dir.y * 2.0, dir.z * 2.0);
      double h = 0.025;
      Box box = new Box(point.x - h, point.y - h, point.z - h, point.x + h, point.y + h, point.z + h);
      return ReviveClient.ROTATION.raytraceCheck(eye, ReviveClient.ROTATION.getServerYaw(), ReviveClient.ROTATION.getServerPitch(), box);
   }

   private static Vec3d rotationVector(float yaw, float pitch) {
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      return new Vec3d(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
   }

   private Vec3d poseEye(EntityPose pose) {
      return new Vec3d(this.mc.player.getX(), this.mc.player.getY() + this.mc.player.getEyeHeight(pose), this.mc.player.getZ());
   }

   private static Vec3d boxCenter(Box box) {
      return new Vec3d((box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0, (box.minZ + box.maxZ) / 2.0);
   }

   private Entity pearlBlocker(float yaw, float pitch, EntityPose pose) {
      Vec3d dir = rotationVector(yaw, pitch);
      Vec3d playerVel = this.mc.player.getVelocity();
      Vec3d velocity = dir.multiply(1.5).add(playerVel.x, this.mc.player.isOnGround() ? 0.0 : playerVel.y, playerVel.z);
      Vec3d pos = this.poseEye(pose).subtract(0.0, 0.1, 0.0);

      for (int i = 0; i < 40; i++) {
         Vec3d next = pos.add(velocity);
         HitResult blockHit = this.mc.world.raycast(new RaycastContext(pos, next, ShapeType.COLLIDER, FluidHandling.NONE, this.mc.player));
         boolean landed = blockHit.getType() != Type.MISS;
         Vec3d end = landed ? blockHit.getPos() : next;
         Box sweep = new Box(pos.x, pos.y, pos.z, end.x, end.y, end.z).expand(1.0);
         Entity closest = null;
         double closestDist = Double.MAX_VALUE;

         for (Entity entity : this.mc.world.getOtherEntities(this.mc.player, sweep, Phase::pearlCanHit)) {
            if (!this.attackedBlockers.contains(entity.getId())) {
               Optional<Vec3d> hit = entity.getBoundingBox().expand(0.3).raycast(pos, end);
               if (!hit.isEmpty()) {
                  double dist = pos.squaredDistanceTo(hit.get());
                  if (dist < closestDist) {
                     closestDist = dist;
                     closest = entity;
                  }
               }
            }
         }

         if (closest != null) {
            return closest;
         }

         if (landed || next.y < this.mc.world.getBottomY()) {
            return null;
         }

         pos = next;
         velocity = velocity.multiply(0.99).subtract(0.0, 0.03, 0.0);
      }

      return null;
   }

   private static boolean pearlCanHit(Entity e) {
      return e.isAlive() && !e.isSpectator() ? e instanceof LivingEntity || e instanceof EndCrystalEntity || e instanceof ItemFrameEntity : false;
   }

   private void attackBlocker(Entity entity, boolean strict) {
      this.mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, this.mc.player.isSneaking()));
      this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
      if (!strict) {
         this.attackedBlockers.add(entity.getId());
      }
   }

   private boolean inScaffolding() {
      return this.mc.world.getBlockState(this.mc.player.getBlockPos()).isOf(Blocks.SCAFFOLDING);
   }

   private void breakScaffold() {
      BlockPos pos = this.mc.player.getBlockPos().toImmutable();
      SilentMine silentMine = SilentMine.get();
      if (silentMine != null && silentMine.isActive()) {
         silentMine.silentBreakBlock(pos, Direction.UP, 100.0);
      } else {
         if (!ReviveClient.PACKET.canSend(PacketManager.Category.GLOBAL)) {
            return;
         }

         this.mc
            .getNetworkHandler()
            .sendPacket(
               new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP, ((IClientWorld)this.mc.world).revive$getAndIncrementSequence())
            );
         this.mc
            .getNetworkHandler()
            .sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP, ((IClientWorld)this.mc.world).revive$getAndIncrementSequence()));
      }

      this.scaffoldCleared = true;
      this.scaffoldBreakTimer = this.scaffoldBreakDelay.get();
   }

   private boolean inWebs() {
      Box bb = this.mc.player.getBoundingBox();
      int minX = MathHelper.floor(bb.minX + 1.0E-5);
      int maxX = MathHelper.floor(bb.maxX - 1.0E-5);
      int minY = MathHelper.floor(bb.minY + 1.0E-5);
      int maxY = MathHelper.floor(bb.maxY - 1.0E-5);
      int minZ = MathHelper.floor(bb.minZ + 1.0E-5);
      int maxZ = MathHelper.floor(bb.maxZ - 1.0E-5);
      Mutable pos = new Mutable();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               if (this.mc.world.getBlockState(pos.set(x, y, z)).isOf(Blocks.COBWEB)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private void updateRephase() {
      boolean holdWatching = this.hold.get() && this.phaseBind.get().isPressed();
      if (!this.rephase.get() && !holdWatching) {
         if (this.rephasePending || this.primedTicks > 0 || !this.insideBlocks.isEmpty()) {
            this.clearRephase();
         }
      } else {
         Box bb = this.mc.player.getBoundingBox();
         if (this.rephase.get() && !this.rephasePending) {
            for (BlockPos pos : this.insideBlocks) {
               if (!this.isSolid(pos) && bb.intersects(new Box(pos)) && !this.isOwnMine(pos)) {
                  this.repend(true);
                  break;
               }
            }
         }

         this.insideBlocks.clear();
         this.collectInsideBlocks(bb, this.insideBlocks);
         if (this.rephasePending) {
            if (this.mc.options.sneakKey.isPressed() || this.movedFromAnchor()) {
               this.cancelPending();
            } else if (this.isPhased(Collections.emptyList())) {
               this.cancelPending();
            } else {
               EntityPose pose = this.predictPose(Collections.emptyList());
               if (this.crawlBlocked(pose)) {
                  this.primedTicks = 0;
                  this.dbg("rephase pending: waiting out the crawl, rotation left to combat");
               } else if (!this.active && this.canRephaseNow()) {
                  boolean warm = this.primedTicks >= 1;
                  this.arm(Phase.ArmSource.REPHASE);
                  if (warm) {
                     this.rotationTicksHeld = 1;
                  }

                  this.rephasePending = false;
                  this.rephaseAnchor = null;
                  this.primedTicks = 0;
               } else if (--this.rephasePendingTicks <= 0) {
                  this.dbg("rephase timed out");
                  this.cancelPending();
               } else {
                  this.primedTicks = 0;
                  if (!this.active) {
                     this.dbg("rephase pending: " + this.rephaseBlockReason() + ", rotation left to combat");
                  }
               }
            }
         } else {
            boolean primed = false;
            if (!this.active && this.rephasePrime.get() && this.primeBackoff == 0 && this.canRephaseNow() && this.throwRefusal() == null) {
               List<BlockPos> mined = this.minedInsideBlocks();
               if (!mined.isEmpty() && !this.isPhased(mined)) {
                  EntityPose after = this.predictPose(mined);
                  if (!this.crawlBlocked(after)) {
                     primed = this.holdPrimeRotation(after);
                  }
               }
            }

            if (!primed) {
               this.primedTicks = 0;
            }
         }
      }
   }

   private boolean holdPrimeRotation(EntityPose pose) {
      if (this.primedTicks >= 40) {
         this.dbg("primed 40 ticks without a break - backing off 20 ticks");
         this.primeBackoff = 20;
         return false;
      } else {
         float[] rotation = this.throwRotation(this.phaseTarget(this.movementYaw()), pose);
         if (this.rotateMode.get() == Phase.RotateMode.Silent) {
            if (ReviveClient.ROTATION.requestSilentRotation("phase", 800.0, rotation[0], rotation[1])) {
               this.primedTicks++;
            } else {
               this.primedTicks = 0;
            }
         } else {
            ReviveClient.ROTATION.requestMotionRotation(rotation[0], rotation[1], 800.0);
            this.primedTicks++;
         }

         return true;
      }
   }

   private void repend(boolean freshTimeout) {
      this.rephasePending = true;
      if (freshTimeout || this.rephasePendingTicks <= 0) {
         this.rephasePendingTicks = this.rephaseTimeout.get();
      }

      if (this.rephaseAnchor == null) {
         this.rephaseAnchor = new Vec3d(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
      }
   }

   private void cancelPending() {
      this.rephasePending = false;
      this.rephasePendingTicks = 0;
      this.rephaseAnchor = null;
      this.primedTicks = 0;
   }

   private boolean movedFromAnchor() {
      if (this.rephaseAnchor == null) {
         return false;
      } else {
         double dx = this.mc.player.getX() - this.rephaseAnchor.x;
         double dz = this.mc.player.getZ() - this.rephaseAnchor.z;
         return dx * dx + dz * dz > 1.0 || Math.abs(this.mc.player.getY() - this.rephaseAnchor.y) > 1.0;
      }
   }

   private boolean canAutoArm() {
      if (this.holdBackoff > 0) {
         return false;
      } else if (this.mc.currentScreen instanceof ChatScreen) {
         return false;
      } else if (this.mc.options.sneakKey.isPressed()) {
         return false;
      } else if (this.crawlBlocked(this.predictPose(Collections.emptyList()))) {
         return false;
      } else if (this.isPhased(Collections.emptyList())) {
         return false;
      } else if (!this.hasPearl()) {
         return false;
      } else {
         return this.mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack()) ? false : this.throwRefusal() == null;
      }
   }

   private boolean isOwnMine(BlockPos pos) {
      SilentMine silentMine = SilentMine.get();
      return silentMine != null && silentMine.isActive() ? silentMine.isOwnMiningPos(pos) : false;
   }

   private boolean canRephaseNow() {
      return this.rephaseBlockReason() == null;
   }

   private String rephaseBlockReason() {
      if (this.mc.currentScreen instanceof ChatScreen) {
         return "chat open";
      } else if (!this.hasPearl()) {
         return "no pearl";
      } else {
         return this.mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack()) ? "pearl on cooldown" : null;
      }
   }

   private List<BlockPos> minedInsideBlocks() {
      List<BlockPos> out = new ArrayList<>();
      if (!this.insideBlocks.isEmpty() && !this.minedBlocks.isEmpty()) {
         long now = System.currentTimeMillis();
         this.minedBlocks.values().removeIf(infox -> now - infox.seenMs() > 3000L);

         for (BlockPos pos : this.insideBlocks) {
            Phase.MineInfo info = this.minedBlocks.get(pos);
            if (info != null && info.stage() >= 5) {
               out.add(pos);
            }
         }

         return out;
      } else {
         return out;
      }
   }

   private void collectInsideBlocks(Box bb, List<BlockPos> out) {
      int minX = MathHelper.floor(bb.minX + 0.01);
      int maxX = MathHelper.floor(bb.maxX - 0.01);
      int minY = MathHelper.floor(bb.minY + 0.01);
      int maxY = MathHelper.floor(bb.maxY - 0.01);
      int minZ = MathHelper.floor(bb.minZ + 0.01);
      int maxZ = MathHelper.floor(bb.maxZ - 0.01);

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (this.isSolid(pos)) {
                  out.add(pos);
               }
            }
         }
      }
   }

   private boolean isSolid(BlockPos pos) {
      return this.mc.world.getBlockState(pos).isSolidBlock(this.mc.world, pos);
   }

   private void clearRephase() {
      this.insideBlocks.clear();
      this.minedBlocks.clear();
      this.cancelPending();
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if ((this.rephase.get() || this.hold.get()) && event.packet instanceof BlockBreakingProgressS2CPacket packet) {
         if (this.mc.player == null || packet.getEntityId() != this.mc.player.getId()) {
            BlockPos pos = packet.getPos().toImmutable();
            int stage = packet.getProgress();
            if (stage >= 0 && stage <= 9) {
               this.minedBlocks.put(pos, new Phase.MineInfo(System.currentTimeMillis(), stage));
            } else {
               this.minedBlocks.remove(pos);
            }
         }
      }
   }

   private boolean isPhased(Collection<BlockPos> treatAsAir) {
      Box bb = this.mc.player.getBoundingBox();
      double feetY = this.mc.player.getY();
      int minX = MathHelper.floor(bb.minX + 0.01);
      int maxX = MathHelper.floor(bb.maxX - 0.01);
      int minZ = MathHelper.floor(bb.minZ + 0.01);
      int maxZ = MathHelper.floor(bb.maxZ - 0.01);
      int minY = MathHelper.floor(feetY + 0.001);
      int maxY = MathHelper.floor(feetY + 0.1);
      Mutable pos = new Mutable();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               pos.set(x, y, z);
               if (!treatAsAir.contains(pos) && this.mc.world.getBlockState(pos).isSolidBlock(this.mc.world, pos)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean crawlBlocked(EntityPose pose) {
      return !this.crawlPhase.get() && pose == EntityPose.SWIMMING && !this.mc.player.isTouchingWater();
   }

   private EntityPose predictPose(Collection<BlockPos> treatAsAir) {
      EntityPose current = this.mc.player.getPose();
      if (this.mc.player.hasVehicle() || this.mc.player.isGliding() || this.mc.player.isTouchingWater()) {
         return current;
      } else if (!this.poseFits(EntityPose.SWIMMING, treatAsAir)) {
         return current;
      } else {
         EntityPose expected = this.mc.player.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING;
         if (this.poseFits(expected, treatAsAir)) {
            return expected;
         } else {
            return this.poseFits(EntityPose.CROUCHING, treatAsAir) ? EntityPose.CROUCHING : EntityPose.SWIMMING;
         }
      }
   }

   private boolean poseFits(EntityPose pose, Collection<BlockPos> treatAsAir) {
      Box bb = this.mc.player.getBoundingBox();
      double half = (bb.maxX - bb.minX) / 2.0;
      double x = this.mc.player.getX();
      double y = this.mc.player.getY();
      double z = this.mc.player.getZ();
      double e = 1.0E-7;
      Box box = new Box(x - half + e, y + e, z - half + e, x + half - e, y + poseHeight(pose) - e, z + half - e);
      VoxelShape boxShape = VoxelShapes.cuboid(box);
      int minX = MathHelper.floor(box.minX);
      int minY = MathHelper.floor(box.minY);
      int minZ = MathHelper.floor(box.minZ);
      int maxX = MathHelper.floor(box.maxX);
      int maxY = MathHelper.floor(box.maxY);
      int maxZ = MathHelper.floor(box.maxZ);
      Mutable pos = new Mutable();

      for (int bx = minX; bx <= maxX; bx++) {
         for (int by = minY; by <= maxY; by++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
               pos.set(bx, by, bz);
               if (!treatAsAir.contains(pos)) {
                  BlockState state = this.mc.world.getBlockState(pos);
                  if (!state.isAir()) {
                     VoxelShape shape = state.getCollisionShape(this.mc.world, pos);
                     if (!shape.isEmpty() && VoxelShapes.matchesAnywhere(boxShape, shape.offset(bx, by, bz), BooleanBiFunction.AND)) {
                        return false;
                     }
                  }
               }
            }
         }
      }

      return true;
   }

   private static double poseHeight(EntityPose pose) {
      return switch (pose) {
         case CROUCHING -> 1.5;
         case SWIMMING, GLIDING, SPIN_ATTACK -> 0.6;
         case SLEEPING -> 0.2;
         default -> 1.8;
      };
   }

   private float[] throwRotation(Vec3d target, EntityPose pose) {
      double x = this.mc.player.getX();
      double y = this.mc.player.getY();
      double z = this.mc.player.getZ();
      double eye = this.mc.player.getEyeHeight(pose);
      double standEye = this.mc.player.getEyeHeight(EntityPose.STANDING);
      double k = (standEye - 0.1) / (standEye + (y - target.y));
      double landDx = (target.x - x) * k;
      double landDz = (target.z - z) * k;
      Vec3d eyePos = new Vec3d(x, y + eye, z);
      Vec3d aim = new Vec3d(x + landDx, y + 0.1, z + landDz);
      float[] rotation = ReviveClient.ROTATION.getRotation(eyePos, aim);
      if (this.antiVoidActive()) {
         rotation[1] = 75.0F;
      }

      return rotation;
   }

   private void deactivate() {
      this.active = false;
      this.activeSource = null;
      this.rotationTicksHeld = 0;
      this.armTicks = 0;
      this.throwHoldTicks = 0;
      ReviveClient.ROTATION.release("phase");
   }

   private boolean throwPearl(float yaw, float pitch) {
      if (this.mc.player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
         this.sendInteract(Hand.OFF_HAND, yaw, pitch);
         return true;
      } else {
         return switch (MultiTask.phaseMode()) {
            case Normal -> this.throwFromMainHand(yaw, pitch, true);
            case Offhand -> this.throwFromOffhand(yaw, pitch);
            case AltSilent -> this.throwFromMainHand(yaw, pitch, false);
         };
      }
   }

   private boolean throwFromMainHand(float yaw, float pitch, boolean guardUsingItem) {
      if (guardUsingItem && this.mc.player.isUsingItem()) {
         return false;
      } else if (this.mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)) {
         this.sendInteract(Hand.MAIN_HAND, yaw, pitch);
         return true;
      } else if (!ReviveClient.SWAP.beginSwap(Items.ENDER_PEARL, true)) {
         return false;
      } else {
         try {
            this.sendInteract(Hand.MAIN_HAND, yaw, pitch);
         } finally {
            ReviveClient.SWAP.endSwap(true);
         }

         return true;
      }
   }

   private boolean throwFromOffhand(float yaw, float pitch) {
      FindItemResult pearl = this.findPearl();
      if (!pearl.found()) {
         return false;
      } else if (!ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
         return false;
      } else {
         if (pearl.isHotbar()) {
            this.clickSwap(45, pearl.slot());
            this.sendInteract(Hand.OFF_HAND, yaw, pitch);
            this.clickSwap(45, pearl.slot());
         } else {
            int selected = this.mc.player.getInventory().selectedSlot;
            this.clickSwap(pearl.slot(), selected);
            this.clickSwap(45, selected);
            this.sendInteract(Hand.OFF_HAND, yaw, pitch);
            this.clickSwap(45, selected);
            this.clickSwap(pearl.slot(), selected);
         }

         return true;
      }
   }

   private void clickSwap(int slotId, int hotbarButton) {
      this.mc.interactionManager.clickSlot(this.mc.player.playerScreenHandler.syncId, slotId, hotbarButton, SlotActionType.SWAP, this.mc.player);
   }

   private void sendInteract(Hand hand, float yaw, float pitch) {
      int sequence = ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
      this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, sequence, yaw, pitch));
   }

   private void placePhaseBlock(float yaw) {
      BlockPos pos = this.mc.player.getBlockPos().add(this.directionOffset(yaw)).toImmutable();
      if (ReviveClient.BLOCK.beginPlacement(pos, this.mc.world.getBlockState(pos), Items.OBSIDIAN)) {
         try {
            ReviveClient.BLOCK.placeBlock(Items.OBSIDIAN, pos);
         } finally {
            ReviveClient.BLOCK.endPlacement();
         }
      }
   }

   private float movementYaw() {
      Input input = this.mc.player.input;
      if (input == null) {
         return this.mc.player.getYaw();
      } else {
         PlayerInput pi = input.playerInput;
         float forwardBack = (pi.forward() ? 1.0F : 0.0F) - (pi.backward() ? 1.0F : 0.0F);
         float leftRight = (pi.left() ? 1.0F : 0.0F) - (pi.right() ? 1.0F : 0.0F);
         float yaw = this.mc.player.getYaw();
         if (forwardBack > 0.0F) {
            if (leftRight > 0.0F) {
               yaw -= 45.0F;
            } else if (leftRight < 0.0F) {
               yaw += 45.0F;
            }
         } else if (forwardBack < 0.0F) {
            yaw += 180.0F;
            if (leftRight > 0.0F) {
               yaw += 45.0F;
            } else if (leftRight < 0.0F) {
               yaw -= 45.0F;
            }
         } else if (leftRight > 0.0F) {
            yaw -= 90.0F;
         } else if (leftRight < 0.0F) {
            yaw += 90.0F;
         }

         return MathHelper.wrapDegrees(yaw);
      }
   }

   private Vec3d phaseTarget(float yaw) {
      double x = this.mc.player.getX();
      double y = this.mc.player.getY();
      double z = this.mc.player.getZ();
      double floorX = Math.floor(x);
      double floorZ = Math.floor(z);
      double fracX = x - floorX;
      double fracZ = z - floorZ;
      BlockPos dir = this.directionOffset(yaw);

      double targetX = floorX + switch (Integer.signum(dir.getX())) {
         case -1 -> X_LOW;
         case 1 -> X_HIGH;
         default -> closer(fracX, X_LOW, X_HIGH);
      };

      double targetZ = floorZ + switch (Integer.signum(dir.getZ())) {
         case -1 -> Z_LOW;
         case 1 -> Z_HIGH;
         default -> closer(fracZ, Z_LOW, Z_HIGH);
      };
      double nudge = this.corner.get() ? 0.3 : 0.2;
      double nudgedX = x + MathHelper.clamp(targetX - x, -nudge, nudge);
      double nudgedZ = z + MathHelper.clamp(targetZ - z, -nudge, nudge);
      double targetY = y - 0.5;
      if (this.antiVoidActive()) {
         targetY++;
      }

      return new Vec3d(nudgedX, targetY, nudgedZ);
   }

   private static double closer(double value, double a, double b) {
      return Math.abs(value - a) <= Math.abs(value - b) ? a : b;
   }

   private BlockPos directionOffset(float yaw) {
      int octant = MathHelper.floor(MathHelper.wrapDegrees(yaw) * 8.0F / 360.0F + 0.5) & 7;

      return switch (octant) {
         case 0 -> new BlockPos(0, 0, 1);
         case 1 -> new BlockPos(-1, 0, 1);
         case 2 -> new BlockPos(-1, 0, 0);
         case 3 -> new BlockPos(-1, 0, -1);
         case 4 -> new BlockPos(0, 0, -1);
         case 5 -> new BlockPos(1, 0, -1);
         case 6 -> new BlockPos(1, 0, 0);
         case 7 -> new BlockPos(1, 0, 1);
         default -> BlockPos.ORIGIN;
      };
   }

   private boolean antiVoidActive() {
      return this.antiVoid.get() && this.mc.player.getBlockY() <= this.mc.world.getBottomY() + 1;
   }

   private boolean hasPearl() {
      return this.mc.player.getOffHandStack().isOf(Items.ENDER_PEARL) || this.mc.player.getMainHandStack().isOf(Items.ENDER_PEARL) || this.findPearl().found();
   }

   private FindItemResult findPearl() {
      return InvUtils.find(stack -> stack.isOf(Items.ENDER_PEARL), 0, 35);
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.debugRender.get() && this.mc.player != null && this.mc.world != null) {
         Vec3d target = this.phaseTarget(this.movementYaw());
         Box box = new Box(target.x - 0.08, target.y - 0.08, target.z - 0.08, target.x + 0.08, target.y + 0.08, target.z + 0.08);
         event.renderer.box(box, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
      }
   }

   private static enum ArmSource {
      MANUAL,
      HOLD,
      REPHASE;
   }

   private record MineInfo(long seenMs, int stage) {
   }

   public static enum RotateMode {
      Movement,
      Instant,
      DelayedInstant,
      DelayedInstantWebOnly,
      Silent;
   }
}
