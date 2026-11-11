package revivedevelopment.reviveclient.systems.managers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.data.DataTracker.SerializedEntry;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.PlayerTickMovementEvent;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.packets.ContainerSlotUpdateEvent;
import revivedevelopment.reviveclient.events.packets.EntityAttributesEvent;
import revivedevelopment.reviveclient.events.packets.InventoryEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.packets.SetPlayerInventoryEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.EntityAccessor;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.ElytraSaver;
import revivedevelopment.reviveclient.systems.modules.movement.RocketBoost;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes.Bounce;
import revivedevelopment.reviveclient.utils.BaritoneBridge;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.world.TickRate;

public final class ElytraDurabilitySaver {
   private static ElytraDurabilitySaver instance;
   private static final int MAX_CLOSED_TICKS = 70;
   private static final int MIN_CLOSED_TICKS = 6;
   private static final int MIN_OPEN_TICKS = 2;
   private static final double START_CLEARANCE = 3.0;
   private static final double PROBE_DEPTH = 16.0;
   private static final double SINK_MARGIN = 0.75;
   private static final double TERRAIN_RESERVE = 1.5;
   private static final double NUDGE_DRAG_GUESS = 0.1;
   private static final double REJECTION_DISTANCE = 4.0;
   private static final int BACKOFF_CYCLES = 5;
   private static final int ACK_TIMEOUT_TICKS = 10;
   private static final int OWNED_STOP_WINDOW_TICKS = 30;
   private static final int MIN_REOPEN_DELAY_TICKS = 2;
   private static final int MAX_REOPEN_DELAY_TICKS = 8;
   private static final int ROCKET_HOLD_TICKS = 6;
   private static final int ROCKET_FLASH_OFF_TICKS = 10;
   private volatile int burnUntilTick = Integer.MIN_VALUE;
   private static final int BURN_FROM_START_TICKS = 30;
   private static final int BURN_TAIL_TICKS = 8;
   private final Set<Integer> trackedRockets = new HashSet<>();
   private ElytraDurabilitySaver.Mode lastMode;
   private int hopAirborneTicks;
   private int hopSuppressGlideTick = Integer.MIN_VALUE;
   private int hopGroundTicks;
   private static final int HOP_GROUND_TIMEOUT_TICKS = 40;
   private int sprintGuardUntilTick = Integer.MIN_VALUE;
   private static final int SPRINT_GUARD_TICKS = 20;
   private static final int HOP_GROUND_GLIDE_TICKS = 8;
   private static final int HOP_CLEAR_AIRBORNE_TICK = 2;
   private boolean hopCommandThisHop;
   private int lastHopEngageTick = Integer.MIN_VALUE;
   private static final int HOP_QUICK_FAIL_TICKS = 40;
   private static final int HOP_FAIL_COOLDOWN_TICKS = 60;
   private static final int REOPEN_ACK_TIMEOUT_TICKS = 12;
   private static final int MAX_REOPENS_PER_CYCLE = 3;
   private static final int CLICK_SLOT_HEADROOM = 4;
   private static final int VIEW_GUARD_AFTER_RESTORE_TICKS = 15;
   private static final double EQUIP_SOUND_RADIUS = 24.0;
   private static final int CHEST_CONTAINER_SLOT = 6;
   private static final int HOTBAR_CONTAINER_SLOT_0 = 36;
   private static final int GLIDING_FLAG_INDEX = 7;
   private final List<ElytraDurabilitySaver.Requester> requesters = new ArrayList<>();
   private final List<BooleanSupplier> vetoes_ = new ArrayList<>();
   private ElytraDurabilitySaver.Phase phase = ElytraDurabilitySaver.Phase.IDLE;
   private int glideTicks;
   private int burnGateBlockedSince = Integer.MIN_VALUE;
   private int phaseTicks;
   private int holdUntilTick = Integer.MIN_VALUE;
   private int closedUntilTick = Integer.MIN_VALUE;
   private int closedSinceTick = Integer.MIN_VALUE;
   private final AtomicInteger teleportsPending = new AtomicInteger();
   private volatile double teleportMaxPending;
   private volatile double teleportDyPending;
   private volatile double teleportHorizPending;
   private int nudgesClosed;
   private int nudgesOpen;
   private double nudgeMax;
   private double nudgeDy;
   private double nudgeHoriz;
   private double windowStartY;
   private double windowStartSpeed;
   private double nudgeDragPerTick = 0.1;
   private int flashedTick = Integer.MIN_VALUE;
   private int notGlidingTicks;
   private int flashes;
   private int startsConverted;
   private int flashesSkipped;
   private int folds;
   private int stalls;
   private boolean flashDue;
   private boolean swapOutPending;
   private boolean wrapJumpOwed;
   private int rocketsWrapped;
   private PlayerInteractItemC2SPacket pendingWrapUse;
   private int pendingWrapUntilTick = Integer.MIN_VALUE;
   private static final int WRAP_DEFER_TICKS = 8;
   private PlayerInteractItemC2SPacket burnUseAfterResume;
   private int burnUseUntilTick = Integer.MIN_VALUE;
   private volatile boolean foreignClickPending;
   private int lastForeignClickTick = Integer.MIN_VALUE;
   private static final int FOREIGN_CLICK_QUIET_TICKS = 8;
   private boolean ownClicking;
   private int foreignClicks;
   private static final int BARITONE_SETBACK_DELAY_ENGAGED = 3;
   private int baritoneSetbackDelaySaved = -1;
   private static final int BURN_USE_MAX_WAIT_TICKS = 24;
   private int wrapGraceUntilTick = Integer.MIN_VALUE;
   private static final int WRAP_GRACE_TICKS = 60;
   private int engageDeadline = Integer.MIN_VALUE;
   private int resumeDeadline = Integer.MIN_VALUE;
   private String disengagePendingWhy;
   private boolean serverGliding;
   private boolean awaitingClear;
   private static final int FLASH_MIN_SPACING = 8;
   private static final int FLASH_STALL_TICKS = 45;
   private static final int FLASH_MAX_STALLS = 3;
   private static final int ENGAGE_TIMEOUT_TICKS = 40;
   private static final int RESUME_TIMEOUT_TICKS = 20;
   private boolean ownInput;
   private boolean wantJump;
   private PlayerInput serverInput;
   private boolean inputSentThisTick;
   private boolean inputResyncOwed;
   private boolean serverSprinting;
   private boolean clientSprintSent;
   private boolean sendingOwnPacket;
   private int flashResyncsLogged;
   private int clearAppliedTick = Integer.MIN_VALUE;
   private final ConcurrentLinkedQueue<Integer> heldPongs = new ConcurrentLinkedQueue<>();
   private volatile boolean holdPongs;
   private int pongsHeldSince = Integer.MIN_VALUE;
   private int pongsHeldMax;
   private boolean gapCut;
   private final ConcurrentHashMap<Long, Integer> heldChunks = new ConcurrentHashMap<>();
   private int chunkCuts;
   private final ConcurrentLinkedQueue<String> trace = new ConcurrentLinkedQueue<>();
   private final AtomicInteger traceSize = new AtomicInteger();
   private static final int TRACE_MAX = 60;
   private int startsRefused;
   private int refusedLogged;
   private static final int FLASH_MAX_REFUSALS = 4;
   private static final int FLASH_REFUSAL_WINDOW_TICKS = 8;
   private static final int PONG_HOLD_GRACE_TICKS = 20;
   private static final int PONG_TELEPORT_MARK = Integer.MIN_VALUE;
   private final Set<Integer> clearPingIds = ConcurrentHashMap.newKeySet();
   private volatile int lastHeldPingId = Integer.MIN_VALUE;
   private int flashUnexpectedClearsLogged;
   private int flashSetbacks;
   private int flashSetbackWindowStart;
   private int flashFullSyncs;
   private static final int FLASH_MAX_FULL_SYNCS = 6;
   private int flashCooldownUntil = Integer.MIN_VALUE;
   private String flashCooldownWhy = "";
   private int idleSinceTick = Integer.MIN_VALUE;
   private int lastIdleReportTick = Integer.MIN_VALUE;
   private int lastLateGateReportTick = Integer.MIN_VALUE;
   private static final int FLASH_IDLE_FIRST_REPORT_TICKS = 40;
   private static final int FLASH_IDLE_REPORT_TICKS = 100;
   private int unrequestedGlideTicks;
   private int unrequestedNotGlidingTicks;
   private int lastIdleGlideTick = Integer.MIN_VALUE;
   private static final int FLASH_MAX_SETBACKS = 20;
   private static final int FLASH_SETBACK_WINDOW = 60;
   private static final int FLASH_COOLDOWN_TICKS = 200;
   private volatile long lastFlashNanos;
   private volatile long lastServerTeleportNanos;
   private static final long SETBACK_VELOCITY_WINDOW_NANOS = 150000000L;
   private int velocitiesKept;
   private int setbackVelocitiesPassed;
   private int burnVelocitiesPassed;
   private boolean burnTraceDumped;
   private boolean applyingServerTracker;
   private boolean lastLocalGlideTraced;
   private boolean sprintBeforeServerWrite;
   private volatile double lastSetbackAfterFlashMs = -1.0;
   private int flashSetbacksLogged;
   private static final int FLASH_SETBACKS_LOGGED = 5;
   private static final int FLASH_ENGAGE_GLIDE_TICKS = 20;
   private static final int FLASH_ENGAGE_GLIDE_TICKS_AFTER_BURN = 6;
   private static final int FLASH_FORCE_ENGAGE_AFTER_BURN_BLOCKED_TICKS = 24;
   private static final int FLASH_ENGAGE_AFTER_BURN_WINDOW_TICKS = 40;
   private static final double FLASH_MIN_CLEARANCE = 4.0;
   private static final int LANDED_FLASH_OFF_TICKS = 60;
   private int recentNudges;
   private int recentNudgeWindowStart = Integer.MIN_VALUE;
   private static final int FLASH_NUDGE_WINDOW_TICKS = 40;
   private static final int FLASH_MAX_IDLE_NUDGES = 12;
   private int lastFlashReportTick = Integer.MIN_VALUE;
   private double reportSpeedSum;
   private double reportSpeedMax;
   private int reportTicks;
   private int reportSprintTicks;
   private int reportGlideTicks;
   private int reportGroundTicks;
   private boolean flashing;
   private static final int FLASH_GROUND_HOLD_TICKS = 40;
   private static final int FLASH_REPORT_TICKS = 100;
   private Vec3d lastTickVelocity;
   private int velocityKeptHook;
   private int velocityKeptTick;
   private int positionKeptHook;
   private final AtomicInteger velocityPacketsPending = new AtomicInteger();
   private int velocityDropped;
   private int velocityPassed;
   private static final double KNOCKBACK_MIN_Y = 0.05;
   private int backoffCyclesLeft;
   private boolean joinHintShown;
   private int lastDamage = -1;
   private int hotbarSlot = -1;
   private ItemStack plateStack = ItemStack.EMPTY;
   private ItemStack elytraStack = ItemStack.EMPTY;
   private int ownedUntilTick = Integer.MIN_VALUE;
   private int viewGuardUntilTick = Integer.MIN_VALUE;
   private int reopens;
   private int reopenDueTick = Integer.MIN_VALUE;
   private boolean reopenAcked = true;
   private int ackDeadlineTick = Integer.MIN_VALUE;
   private boolean settingOwnFlag;
   private boolean wasGliding;
   private boolean pinGlidePose;
   private boolean noPlateWarned;
   private int cycles;
   private boolean lastSyncChestElytra;

   public static ElytraDurabilitySaver get() {
      if (instance == null) {
         instance = new ElytraDurabilitySaver();
      }

      return instance;
   }

   private boolean hopFlight() {
      if (mode() == ElytraDurabilitySaver.Mode.Flash && AntiCheatConfig.get().elytraSaverBounceHops.get()) {
         ElytraFly fly = Modules.get().get(ElytraFly.class);
         return fly != null && fly.isActive() && fly.flightMode.get() == ElytraFlightModes.Bounce && !fly.motionYBoost.get() && this.isRequested();
      } else {
         return false;
      }
   }

   private boolean hopAwaitingCommand() {
      return this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT
         && !this.hopCommandThisHop
         && this.hopAirborneTicks >= 2
         && !ReviveClient.mc.player.isOnGround()
         && this.hopFlight();
   }

   private boolean hopGroundGlide() {
      return this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT
         && ReviveClient.mc.player.isOnGround()
         && !ReviveClient.mc.player.isTouchingWater()
         && this.hopGroundTicks <= 8
         && this.hopFlight();
   }

   private boolean wrapRockets() {
      return mode() == ElytraDurabilitySaver.Mode.Flash && AntiCheatConfig.get().elytraSaverWrapRockets.get();
   }

   private boolean rocketWrapReady(int now) {
      return this.wrapRockets()
         && this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT
         && !this.hopFlight()
         && (this.flashDue || !this.serverGliding && !this.awaitingClear)
         && !this.swapOutPending
         && !this.serverJump()
         && !this.inputSentThisTick
         && (this.flashedTick == Integer.MIN_VALUE || now - this.flashedTick >= 8)
         && this.foreignClickQuiet(now)
         && localCanGlide()
         && ReviveClient.mc.player.isGliding()
         && this.hotbarSlot >= 0
         && ReviveClient.mc.interactionManager != null
         && playerInventoryOnly()
         && ReviveClient.mc.player.playerScreenHandler.getCursorStack().isEmpty();
   }

   public void rocketUsed() {
      if (ReviveClient.mc.player != null && mode() == ElytraDurabilitySaver.Mode.Flash) {
         if (!this.wrapRockets() || this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
            int now = ReviveClient.mc.player.age;
            this.flashCooldownUntil = Math.max(this.flashCooldownUntil, now + 10);
            this.flashCooldownWhy = "a rocket";
            this.burnUntilTick = Math.max(this.burnUntilTick, now + 30);
            this.trace("rocket used");
         }
      }
   }

   private boolean foreignClickQuiet(int now) {
      if (this.foreignClickPending) {
         this.foreignClickPending = false;
         this.lastForeignClickTick = now;
         this.foreignClicks++;
      }

      return this.lastForeignClickTick == Integer.MIN_VALUE || now - this.lastForeignClickTick >= 8;
   }

   private void ownClickSlot(int syncId, int slot, int button, SlotActionType action) {
      this.ownClicking = true;

      try {
         ReviveClient.mc.interactionManager.clickSlot(syncId, slot, button, action, ReviveClient.mc.player);
      } finally {
         this.ownClicking = false;
      }
   }

   private void manageBaritoneSetbackDelay() {
      boolean want = this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT
         && mode() == ElytraDurabilitySaver.Mode.Flash
         && BaritoneBridge.elytraHasDestination();
      if (want && this.baritoneSetbackDelaySaved < 0) {
         int cur = BaritoneBridge.getElytraSetbackDelay();
         if (cur < 0) {
            return;
         }

         this.baritoneSetbackDelaySaved = cur;
         BaritoneBridge.setElytraSetbackDelay(Math.min(cur, 3));
      } else if (!want && this.baritoneSetbackDelaySaved >= 0) {
         BaritoneBridge.setElytraSetbackDelay(this.baritoneSetbackDelaySaved);
         this.baritoneSetbackDelaySaved = -1;
      }
   }

   private boolean burnUseCanGo() {
      return this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.resumeDeadline == Integer.MIN_VALUE && this.disengagePendingWhy == null;
   }

   private void flushBurnUse(int now, boolean force) {
      if (this.burnUseAfterResume != null) {
         if (force || this.burnUseCanGo() || now > this.burnUseUntilTick) {
            PlayerInteractItemC2SPacket use = this.burnUseAfterResume;
            this.burnUseAfterResume = null;
            this.burnUseUntilTick = Integer.MIN_VALUE;
            if (ReviveClient.mc.getNetworkHandler() != null) {
               this.burnUntilTick = Math.max(this.burnUntilTick, now + 30);
               this.sendOwn(use);
               this.trace("> held rocket sent after the resume command");
            }
         }
      }
   }

   private ElytraDurabilitySaver() {
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   public void register(BooleanSupplier active, BooleanSupplier wants) {
      this.requesters.add(new ElytraDurabilitySaver.Requester(active, wants));
   }

   public void registerVeto(BooleanSupplier vetoes) {
      this.vetoes_.add(vetoes);
   }

   public boolean isRequested() {
      if (!ElytraSaver.allowed()) {
         return false;
      } else if (PbPredict.serverPushesOnly()) {
         return false;
      } else if (this.bounceUnderRoof()) {
         return false;
      } else if (AntiCheatConfig.get().elytraSaverSkipBaritone.get() && BaritoneBridge.elytraHasDestination()) {
         return false;
      } else {
         for (BooleanSupplier v : this.vetoes_) {
            if (v.getAsBoolean()) {
               return false;
            }
         }

         for (ElytraDurabilitySaver.Requester r : this.requesters) {
            if (r.active.getAsBoolean() && r.wants.getAsBoolean()) {
               return true;
            }
         }

         return AntiCheatConfig.get().elytraSaverAllFlights.get();
      }
   }

   private boolean bounceUnderRoof() {
      if (ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
         ElytraFly fly = Modules.get().get(ElytraFly.class);
         return fly != null && fly.isActive() && fly.flightMode.get() == ElytraFlightModes.Bounce && Bounce.underLowRoof();
      } else {
         return false;
      }
   }

   public boolean isSwappedOut() {
      return this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT;
   }

   public boolean pinsGlidePose() {
      return this.pinGlidePose;
   }

   public boolean keepsVelocityThroughSetbacks() {
      return ReviveClient.mc.player != null && ReviveClient.mc.player.isGliding() && this.isRequested() ? mode() == ElytraDurabilitySaver.Mode.Windows : false;
   }

   private static ElytraDurabilitySaver.Mode mode() {
      return AntiCheatConfig.get().elytraSaverMode.get();
   }

   public boolean isFlashing() {
      return this.flashing;
   }

   public boolean keepsPositionThroughSetbacks() {
      return this.keepsVelocityThroughSetbacks() && AntiCheatConfig.get().elytraSaverSetbacks.get() == ElytraDurabilitySaver.Setbacks.KeepVelocityAndPosition;
   }

   public static double rejectionDistance() {
      return 4.0;
   }

   public boolean serverGlideSteady() {
      if (!this.isRequested() && this.phase == ElytraDurabilitySaver.Phase.IDLE && this.resumeDeadline == Integer.MIN_VALUE) {
         return true;
      } else if (mode() == ElytraDurabilitySaver.Mode.Flash) {
         return this.rocketWrapReady(ReviveClient.mc.player.age)
            ? true
            : this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.resumeDeadline == Integer.MIN_VALUE && this.serverGliding;
      } else {
         return this.phase == ElytraDurabilitySaver.Phase.IDLE && this.reopenDueTick == Integer.MIN_VALUE && this.reopenAcked;
      }
   }

   private void onModeChanged(int now, ElytraDurabilitySaver.Mode from, ElytraDurabilitySaver.Mode to) {
      boolean busy = this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT
         || this.resumeDeadline != Integer.MIN_VALUE
         || this.ownInput
         || this.holdPongs
         || !this.reopenAcked
         || this.reopenDueTick != Integer.MIN_VALUE;
      if (busy) {
         dbg("mode changed from " + from + " to " + to + " mid-flight - elytra back in the chest slot server-side, starting over in " + to + " mode");
         if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
            if (this.swapOutPending) {
               this.swapOutPending = false;
               this.restoreServerElytra(now, false);
            } else {
               this.restoreServerElytra(now, true);
            }
         }

         boolean glideOn = ReviveClient.mc.player.isGliding() && localCanGlide() && !ReviveClient.mc.player.getAbilities().flying;
         this.resumeDeadline = Integer.MIN_VALUE;
         this.flashDue = false;
         this.awaitingClear = false;
         this.engageDeadline = Integer.MIN_VALUE;
         this.disengagePendingWhy = null;
         this.swapOutPending = false;
         this.reset();
         this.stopHoldingPongs();
         if (this.ownInput) {
            this.releaseOwnership();
         }

         if (glideOn && !this.serverGliding && !this.inputSentThisTick) {
            this.sendOwn(
               new ClientCommandC2SPacket(ReviveClient.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING)
            );
            this.setLocalGlide(true);
         }
      }
   }

   public void holdForRocket() {
      if (ReviveClient.mc.player != null) {
         if (mode() == ElytraDurabilitySaver.Mode.Flash) {
            if (!this.wrapRockets() || this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
               int now = ReviveClient.mc.player.age;
               this.flashCooldownUntil = Math.max(this.flashCooldownUntil, now + 10);
               this.flashCooldownWhy = "a rocket";
               if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.disengagePendingWhy == null) {
                  this.flashDisengage(now, "a rocket is wanted - it only boosts a server-side glide");
               }
            }
         } else {
            int now = ReviveClient.mc.player.age;
            this.holdUntilTick = now + 6;
            if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.closedUntilTick != Integer.MIN_VALUE) {
               this.closedUntilTick = Math.min(this.closedUntilTick, now + 2);
            }

            if (this.reopenDueTick != Integer.MIN_VALUE) {
               this.reopenDueTick = Math.min(this.reopenDueTick, now + 2);
            }
         }
      }
   }

   private int closedTicks(double clearance) {
      int t = Math.max(6, Math.min(70, AntiCheatConfig.get().elytraSaverClosedTicks.get()));
      if (this.backoffCyclesLeft > 0) {
         t = Math.max(6, t / 2);
      }

      return Math.max(6, Math.min(t, this.maxClosedTicksFor(clearance)));
   }

   private int maxClosedTicksFor(double clearance) {
      double budget = clearance - 1.5;
      if (budget <= 0.0) {
         return 6;
      } else {
         double bySink = budget >= 3.9 ? Double.MAX_VALUE : Math.log(1.0 - budget / 3.92) / Math.log(0.98);
         double byDrag = budget / Math.max(0.01, this.nudgeDragPerTick);
         return (int)Math.floor(Math.min(70.0, Math.min(bySink, byDrag)));
      }
   }

   private double terrainReserve(int closedFor) {
      return Math.max(serverSinkPerTick(closedFor + 2) + 0.75, 1.5 + 4.0 * this.nudgeDragPerTick);
   }

   private int openTicks() {
      return Math.max(2, AntiCheatConfig.get().elytraSaverOpenTicks.get());
   }

   @EventHandler(
      priority = 200
   )
   private void onTick(TickEvent.Pre event) {
      if (ReviveClient.mc.player != null
         && ReviveClient.mc.world != null
         && ReviveClient.mc.getNetworkHandler() != null
         && ReviveClient.mc.interactionManager != null) {
         int now = ReviveClient.mc.player.age;
         this.inputSentThisTick = false;
         this.foreignClickQuiet(now);
         this.manageBaritoneSetbackDelay();
         ElytraDurabilitySaver.Mode modeNow = mode();
         if (this.lastMode != null && modeNow != this.lastMode) {
            this.onModeChanged(now, this.lastMode, modeNow);
         }

         this.lastMode = modeNow;
         if (modeNow == ElytraDurabilitySaver.Mode.Flash) {
            if (ReviveClient.mc.player.isGliding()) {
               for (FireworkRocketEntity r : ReviveClient.mc
                  .world
                  .getEntitiesByClass(FireworkRocketEntity.class, ReviveClient.mc.player.getBoundingBox().expand(2.0), e -> e.isAlive())) {
                  this.trackedRockets.add(r.getId());
               }
            }

            if (!this.trackedRockets.isEmpty()) {
               this.trackedRockets.removeIf(id -> {
                  Entity r = ReviveClient.mc.world.getEntityById(id);
                  return !(r instanceof FireworkRocketEntity) || !r.isAlive();
               });
               if (!this.trackedRockets.isEmpty()) {
                  this.burnUntilTick = Math.max(this.burnUntilTick, now + 8);
                  if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
                     this.wrapGraceUntilTick = Math.max(this.wrapGraceUntilTick, now + 60);
                  }
               } else {
                  this.burnUntilTick = Math.min(this.burnUntilTick, now + 8);
                  this.trace("last rocket gone - burn tail running");
               }
            }
         } else {
            this.trackedRockets.clear();
         }

         if (this.holdPongs
            && (mode() != ElytraDurabilitySaver.Mode.Flash || this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.resumeDeadline == Integer.MIN_VALUE)
            )
          {
            this.stopHoldingPongs();
         }

         if (!this.holdPongs && !this.heldPongs.isEmpty()) {
            this.releasePongs();
         }

         this.enforceLocalView(now);
         int teleports = this.teleportsPending.getAndSet(0);
         int velocityPackets = this.velocityPacketsPending.getAndSet(0);
         if ((teleports > 0 || velocityPackets > 0) && this.lastTickVelocity != null && this.keepsVelocityThroughSetbacks()) {
            Vec3d cur = ReviveClient.mc.player.getVelocity();
            if (cur.squaredDistanceTo(this.lastTickVelocity) > 1.0E-6) {
               ReviveClient.mc.player.setVelocity(this.lastTickVelocity);
               this.velocityKeptTick++;
            }
         }

         if (teleports > 0) {
            double moved = this.teleportMaxPending;
            double dy = this.teleportDyPending;
            double horiz = this.teleportHorizPending;
            this.teleportMaxPending = 0.0;
            this.teleportDyPending = 0.0;
            this.teleportHorizPending = 0.0;
            if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
               this.nudgesClosed += teleports;
               this.nudgeDy += dy;
               this.nudgeHoriz += horiz;
               if (mode() == ElytraDurabilitySaver.Mode.Flash) {
                  this.flashSetbacks += teleports;
                  boolean refused = moved < 0.05 && this.flashedTick != Integer.MIN_VALUE && now - this.flashedTick <= 8;
                  if (refused && now > this.wrapGraceUntilTick) {
                     this.startsRefused++;
                  }

                  if (this.flashSetbacksLogged < 5 || refused && this.refusedLogged < 3) {
                     this.flashSetbacksLogged++;
                     if (refused) {
                        this.refusedLogged++;
                     }

                     dbg(
                        String.format(
                           "flash: server setback (%.1f blocks, %.1f down) %.0f ms after the last glide command; %d in the last %d ticks; %s, clear %s, %d pong(s) held%s%s",
                           moved,
                           -dy,
                           this.lastSetbackAfterFlashMs,
                           this.flashSetbacks,
                           now - this.flashSetbackWindowStart,
                           this.swapOutPending ? "elytra in" : "chestplate on",
                           this.flashDue ? "applied " + (now - this.clearAppliedTick) + " tick(s) ago" : (this.awaitingClear ? "on its way" : "not expected"),
                           this.heldPongs.size(),
                           this.inHeldChunk() ? ", in a chunk sent during the hold" : "",
                           refused ? " - zero-distance resync (a setback re-sent because its velocity was not applied, or a refused glide command)" : ""
                        )
                     );
                     if (this.flashSetbacksLogged == 1) {
                        this.dumpTrace("first setback of this engagement");
                     }
                  }
               }
            } else {
               this.nudgesOpen += teleports;
               if (this.nudgeWindowAge(now) > 40) {
                  this.recentNudges = 0;
                  this.recentNudgeWindowStart = now;
               }

               this.recentNudges += teleports;
               if (mode() == ElytraDurabilitySaver.Mode.Flash
                  && !this.burnTraceDumped
                  && now <= this.burnUntilTick
                  && this.isRequested()
                  && ReviveClient.mc.player.isGliding()) {
                  this.burnTraceDumped = true;
                  dbg(
                     String.format(
                        "flash: server position packet (%.1f blocks, %.1f down) during a rocket burn with the saver idle; %d nudge(s) so far this idle stretch%s",
                        moved,
                        -dy,
                        this.nudgesOpen,
                        velocityShapers()
                     )
                  );
                  this.dumpTrace("first setback inside a rocket burn, saver idle");
               }
            }

            if (moved > this.nudgeMax) {
               this.nudgeMax = moved;
            }

            if (moved >= 4.0 && this.isRequested() && now <= this.ownedUntilTick) {
               this.backoffCyclesLeft = 5;
               dbg(
                  "server moved us "
                     + String.format("%.1f", moved)
                     + " blocks during a cycle (phase "
                     + this.phase
                     + ", y "
                     + String.format("%.1f", ReviveClient.mc.player.getY())
                     + ", clearance "
                     + String.format("%.1f", groundClearance())
                     + ") - closed window halved for 5 cycles"
               );
               if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.closedUntilTick != Integer.MIN_VALUE) {
                  this.closedUntilTick = Math.min(this.closedUntilTick, now + 2);
               }
            }
         }

         boolean glidingNow = ReviveClient.mc.player.isGliding();
         boolean stoppedThisTick = this.wasGliding && !glidingNow;
         this.wasGliding = glidingNow;
         if (mode() == ElytraDurabilitySaver.Mode.Flash) {
            this.flashTick(now, glidingNow);
         } else if (!this.isRequested()) {
            if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
               this.restoreServerElytra(now);
            }

            this.reset();
         } else if (stoppedThisTick) {
            this.handleGlideStop(now);
         } else {
            if (this.pinGlidePose && !localCanGlide()) {
               this.pinGlidePose = false;
            }

            if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.closedUntilTick != Integer.MIN_VALUE) {
               if (now < this.closedUntilTick) {
                  int closedFor = now - this.closedSinceTick;
                  double sink = this.terrainReserve(closedFor);
                  double clearance = groundClearance();
                  if (!(clearance < sink) || now < this.closedSinceTick + 2) {
                     return;
                  }

                  dbg("terrain " + String.format("%.1f", clearance) + " blocks below - wings re-opened early after " + closedFor + " ticks");
               }

               this.closedUntilTick = Integer.MIN_VALUE;
               if (!localCanGlide()) {
                  this.giveUpGlide("can't glide locally when the wings were due to open: " + cannotGlideReason());
               } else {
                  this.restoreServerElytra(now);
                  this.sendReopen(now);
                  dbg("wings re-opened");
               }
            } else if (this.reopenDueTick != Integer.MIN_VALUE) {
               if (now >= this.reopenDueTick) {
                  this.reopenDueTick = Integer.MIN_VALUE;
                  if (!localCanGlide()) {
                     this.giveUpGlide("can't glide locally when the re-open was due: " + cannotGlideReason());
                  } else {
                     this.sendReopen(now);
                     dbg("wings re-opened (counter restarted)");
                  }
               }
            } else if (!this.reopenAcked) {
               if (now > this.ackDeadlineTick) {
                  if (this.reopens >= 3) {
                     this.giveUpGlide("server never confirmed the re-open");
                  } else if (!localCanGlide()) {
                     this.giveUpGlide("can't glide locally while waiting for the re-open: " + cannotGlideReason());
                  } else {
                     this.sendReopen(now);
                     dbg("no confirmation from the server - re-open sent again (" + this.reopens + ")");
                  }
               }
            } else if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
               this.phaseTicks++;
               if (this.phaseTicks >= 10) {
                  dbg("no glide-stop from the server within 10 ticks - elytra back, re-opening to resync");
                  this.restoreServerElytra(now);
                  this.scheduleReopen(now);
               }
            } else if (glidingNow && localCanGlide() && playerInventoryOnly()) {
               if (groundClearance() < 3.0) {
                  this.glideTicks = 0;
               } else {
                  this.glideTicks++;
                  if (this.glideTicks >= this.openTicks()) {
                     if (now > this.holdUntilTick) {
                        int plate = findHotbarChestplate();
                        if (plate == -1) {
                           if (!this.noPlateWarned) {
                              dbg("no chestplate in the hotbar - elytra durability is NOT being saved");
                              this.noPlateWarned = true;
                           }
                        } else {
                           this.noPlateWarned = false;
                           if (ReviveClient.PACKET.remaining(PacketManager.Category.CLICK_SLOT) >= 4) {
                              this.swapOut(plate, now);
                           }
                        }
                     }
                  }
               }
            } else {
               this.glideTicks = 0;
               if (!this.joinHintShown
                  && !glidingNow
                  && !ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                  && findHotbarElytra() >= 0
                  && isChestplate(ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
                  this.joinHintShown = true;
                  ChatUtils.infoPrefix(
                     "ElytraSaver",
                     "%s",
                     "a chestplate is worn and the elytra is in the hotbar (a cycle cut off by a disconnect?) - right-click the elytra to put it back on"
                  );
               }
            }
         }
      } else {
         this.hardReset();
      }
   }

   private void handleGlideStop(int now) {
      boolean ours = now <= this.ownedUntilTick;
      if (ours && this.reopens >= 3) {
         this.giveUpGlide("server keeps stopping the glide right after each re-open");
      } else if (ours && localCanGlide()) {
         this.setLocalGlide(true);
         this.pinGlidePose = true;
         if (ReviveClient.mc.player.getPose() != EntityPose.GLIDING) {
            ReviveClient.mc.player.setPose(EntityPose.GLIDING);
         }

         this.wasGliding = true;
         if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
            double clearance = groundClearance();
            int closed = now <= this.holdUntilTick ? 2 : this.closedTicks(clearance);
            this.closedUntilTick = now + closed;
            this.closedSinceTick = now;
            this.ownedUntilTick = Math.max(this.ownedUntilTick, this.closedUntilTick + 12 + 30);
            dbg("wings closed server-side for " + closed + " ticks" + (clearance < 16.0 ? String.format(" (terrain %.1f below)", clearance) : ""));
         } else {
            this.scheduleReopen(now);
         }
      } else {
         this.wasGliding = false;
         this.pinGlidePose = false;
         if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
            this.restoreServerElytra(now);
         }

         dbg("glide stopped" + (ours ? " (can't glide locally: " + cannotGlideReason() + " - not overriding)" : " (not ours - not overriding)"));
         this.reset();
      }
   }

   public void serverTrackerApply(boolean applying) {
      this.applyingServerTracker = applying;
      if (applying && ReviveClient.mc.player != null) {
         this.sprintBeforeServerWrite = ReviveClient.mc.player.isSprinting();
      }
   }

   public void onFlagsTracked() {
      if (!this.settingOwnFlag && ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
         if (!this.applyingServerTracker) {
            boolean localGlide = ReviveClient.mc.player.isGliding();
            if (localGlide != this.lastLocalGlideTraced) {
               this.lastLocalGlideTraced = localGlide;
               if (this.tracing()) {
                  this.trace("~ local flags glide=" + (localGlide ? 1 : 0));
               }
            }
         } else {
            int now = ReviveClient.mc.player.age;
            boolean clearExpected = this.awaitingClear;
            this.serverGliding = ReviveClient.mc.player.isGliding();
            if (this.tracing()) {
               this.trace("< flags glide=" + (this.serverGliding ? 1 : 0) + " sprint=" + (ReviveClient.mc.player.isSprinting() ? 1 : 0));
            }

            if (!this.serverGliding) {
               this.awaitingClear = false;
            }

            if (this.serverGliding
               && mode() == ElytraDurabilitySaver.Mode.Flash
               && this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT
               && this.resumeDeadline == Integer.MIN_VALUE) {
               this.pinGlidePose = false;
            }

            if ((this.ownInput || now <= this.sprintGuardUntilTick) && ReviveClient.mc.player.isSprinting() != this.sprintBeforeServerWrite) {
               this.settingOwnFlag = true;

               try {
                  ReviveClient.mc.player.setSprinting(this.sprintBeforeServerWrite);
               } finally {
                  this.settingOwnFlag = false;
               }
            }

            if (mode() != ElytraDurabilitySaver.Mode.Flash) {
               if (ReviveClient.mc.player.isGliding()) {
                  if (!this.reopenAcked) {
                     this.reopenAcked = true;
                     this.ackDeadlineTick = Integer.MIN_VALUE;
                     this.glideTicks = 0;
                  }
               } else if (this.isRequested() && this.wasGliding) {
                  if (now <= this.ownedUntilTick) {
                     this.handleGlideStop(now);
                  }
               }
            } else {
               if (!ReviveClient.mc.player.isGliding() && (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT || this.resumeDeadline != Integer.MIN_VALUE)) {
                  if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT
                     && !clearExpected
                     && !this.swapOutPending
                     && this.flashUnexpectedClearsLogged < 3
                     && !this.hopFlight()) {
                     this.flashUnexpectedClearsLogged++;
                     dbg(
                        "flash: server flag-clear with no swap-out pending (anticheat resync?) "
                           + (this.flashedTick == Integer.MIN_VALUE ? -1 : now - this.flashedTick)
                           + " tick(s) after the last glide command"
                     );
                  }

                  if (!this.flashDue) {
                     this.clearAppliedTick = now;
                  }

                  this.flashDue = true;
                  this.trace("clear applied -> command due");
                  if ((localCanGlide() || this.hopGroundGlide()) && this.notGlidingTicks == 0 && !this.hopAwaitingCommand()) {
                     this.setLocalGlide(true);
                  }
               }
            }
         }
      }
   }

   private void setLocalGlide(boolean gliding) {
      this.settingOwnFlag = true;

      try {
         ((EntityAccessor)ReviveClient.mc.player).revive$setFlag(7, gliding);
      } finally {
         this.settingOwnFlag = false;
      }
   }

   private void giveUpGlide(String why) {
      if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
         this.restoreServerElytra(ReviveClient.mc.player.age);
      }

      this.setLocalGlide(false);
      this.pinGlidePose = false;
      this.wasGliding = false;
      dbg("giving this glide up: " + why);
      this.reset();
   }

   private void scheduleReopen(int now) {
      this.reopenDueTick = now + this.reopenDelayTicks();
      this.ownedUntilTick = Math.max(this.ownedUntilTick, this.reopenDueTick + 12 + 30);
   }

   private int reopenDelayTicks() {
      float tps = TickRate.INSTANCE.getTickRate();
      if (tps <= 0.0F || tps > 20.0F) {
         tps = 20.0F;
      }

      double tickMs = 1000.0 / tps;
      int ticks = (int)Math.ceil((tickMs + 30.0) / 50.0);
      return Math.max(2, Math.min(8, ticks));
   }

   private void flashTick(int now, boolean gliding) {
      boolean engaged = this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT;
      if (gliding && !localCanGlide() && (engaged || this.resumeDeadline != Integer.MIN_VALUE) && !this.hopGroundGlide()) {
         this.setLocalGlide(false);
         this.pinGlidePose = false;
         gliding = false;
      }

      if (this.pinGlidePose && !gliding && !engaged && this.resumeDeadline == Integer.MIN_VALUE) {
         this.pinGlidePose = false;
      }

      if (this.disengagePendingWhy != null) {
         String why = this.disengagePendingWhy;
         this.disengagePendingWhy = null;
         if (engaged) {
            this.flashDisengage(now, why);
            return;
         }
      }

      if (!engaged && this.resumeDeadline != Integer.MIN_VALUE) {
         if (gliding && localCanGlide() && !ReviveClient.mc.player.getAbilities().flying) {
            if (now <= this.resumeDeadline) {
               this.setLocalGlide(true);
               this.pinGlidePose = true;
               return;
            }

            this.resumeDeadline = Integer.MIN_VALUE;
            if (!this.serverJump() && !this.inputSentThisTick) {
               dbg("no server flag-clear within 20 ticks - sending the resume glide command anyway");
               this.flashDue = true;
               this.flash(now, false);
               this.flushBurnUse(now, true);
            } else {
               dbg("no server flag-clear to answer with the resume glide command - leaving the glide to the flight module");
            }
         } else {
            this.resumeDeadline = Integer.MIN_VALUE;
            this.pinGlidePose = false;
         }

         this.flashDue = false;
         this.stopHoldingPongs();
      }

      if (this.isRequested()
         && !ReviveClient.mc.player.isTouchingWater()
         && !ReviveClient.mc.player.hasVehicle()
         && !ReviveClient.mc.player.getAbilities().flying) {
         if (!engaged) {
            this.unrequestedGlideTicks = 0;
            if (this.holdPongs) {
               this.stopHoldingPongs();
            }

            if (this.hopFlight()) {
               this.hopGroundTicks = 0;
               if (gliding) {
                  int plate = findHotbarChestplate();
                  String hold = now < this.flashCooldownUntil
                     ? "off for " + (this.flashCooldownUntil - now) + " more ticks after " + this.flashCooldownWhy
                     : (
                        !playerInventoryOnly()
                           ? "a container is open"
                           : (plate < 0 ? "no chestplate in the hotbar" : (!this.swapOutLayoutOk(plate) ? "the client copy is not in the elytra layout" : null))
                     );
                  this.idleReport(now, "bounce hops: engaging at the next take-off" + (hold != null ? " (" + hold + ")" : ""));
               }

               return;
            }

            if (this.ownInput && !this.wantJump) {
               this.releaseOwnership();
            }

            if (!gliding) {
               this.glideTicks = 0;
               this.burnGateBlockedSince = Integer.MIN_VALUE;
               if (this.lastIdleGlideTick == Integer.MIN_VALUE || now - this.lastIdleGlideTick > 40) {
                  this.idleSinceTick = Integer.MIN_VALUE;
                  this.burnTraceDumped = false;
               }

               return;
            }

            this.lastIdleGlideTick = now;
            if (!localCanGlide()) {
               this.glideTicks = 0;
               this.idleReport(now, "can't glide locally: " + cannotGlideReason());
               return;
            }

            if (!playerInventoryOnly()) {
               this.glideTicks = 0;
               this.idleReport(now, "a container is open");
               return;
            }

            if (now < this.flashCooldownUntil) {
               this.glideTicks = 0;
               this.burnGateBlockedSince = Integer.MIN_VALUE;
               this.idleReport(now, "off for " + (this.flashCooldownUntil - now) + " more ticks after " + this.flashCooldownWhy + velocityShapers());
               return;
            }

            boolean burnForced = false;
            if (now <= this.burnUntilTick) {
               if (this.burnGateBlockedSince == Integer.MIN_VALUE) {
                  this.burnGateBlockedSince = now;
               }

               if (this.ownInput || now - this.burnGateBlockedSince < 24) {
                  this.glideTicks = 0;
                  this.idleReport(now, "a rocket burn is under way");
                  return;
               }

               if (this.tracing()) {
                  this.trace("burn gate held " + (now - this.burnGateBlockedSince) + " ready ticks - forcing the engage to break the fast-rocket livelock");
               }

               burnForced = true;
            } else {
               this.burnGateBlockedSince = Integer.MIN_VALUE;
            }

            if (this.ownInput) {
               this.glideTicks = 0;
               this.idleReport(now, "the server input is still owned");
               return;
            }

            int engageNeed = this.burnUntilTick != Integer.MIN_VALUE && now - this.burnUntilTick <= 40 ? 6 : 20;
            if (!burnForced && ++this.glideTicks < engageNeed) {
               this.idleReport(now, "steady glide for " + this.glideTicks + " of " + engageNeed + " ticks; " + this.nudgeRate(now));
               return;
            }

            double clearance = groundClearance();
            if (clearance < 4.0) {
               this.glideTicks = 0;
               this.lateGateReport(now, String.format("terrain %.1f blocks below", clearance));
               return;
            }

            if (this.nudgeWindowAge(now) <= 40 && this.recentNudges >= 12) {
               this.glideTicks = 0;
               this.lateGateReport(now, "the anticheat " + this.nudgeRate(now));
               return;
            }

            int slot = findHotbarChestplate();
            if (slot < 0) {
               if (!this.noPlateWarned) {
                  this.noPlateWarned = true;
                  ChatUtils.warningPrefix("ElytraSaver", "%s", "no chestplate in the hotbar - flying on the elytra's durability");
               }

               this.idleReport(now, "no chestplate in the hotbar");
               return;
            }

            if (this.inputSentThisTick) {
               this.idleReport(now, "an input packet already went out this tick");
               return;
            }

            if (!this.swapOutLayoutOk(slot)) {
               this.idleReport(now, "the client copy is not in the elytra layout");
               return;
            }

            if (!this.foreignClickQuiet(now)) {
               this.idleReport(now, "another module/Baritone just clicked the inventory");
               return;
            }

            this.noPlateWarned = false;
            this.idleSinceTick = Integer.MIN_VALUE;
            this.takeOwnership();
            this.swapOut(slot, now);
            this.notGlidingTicks = 0;
            this.swapOutPending = false;
            this.burnGateBlockedSince = Integer.MIN_VALUE;
            this.flashedTick = Integer.MIN_VALUE;
            this.flashResyncsLogged = 0;
            if (this.serverGliding) {
               this.flashDue = false;
               this.awaitingClear = true;
               this.engageDeadline = now + 40;
            } else {
               this.flashDue = true;
               this.awaitingClear = false;
               this.engageDeadline = Integer.MIN_VALUE;
            }

            this.resetEngagementStats(now);
            if (this.tracing()) {
               this.trace("engage (server " + (this.serverGliding ? "gliding" : "not gliding") + ")");
            }

            dbg(
               "flash mode: chestplate stays on server-side; the elytra is clicked in and straight back out with each glide command, "
                  + glideGapTicks()
                  + " ticks apart - never worn for a server tick"
            );
            engaged = true;
         }

         if (!this.elytraStack.isEmpty() && this.hotbarSlot >= 0) {
            int liveDamage = this.liveElytraDamage();
            if (liveDamage > this.elytraStack.getDamage()) {
               ItemStack chest = ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST);
               if (chest.isOf(Items.ELYTRA)) {
                  this.elytraStack = chest.copy();
               }
            }

            if (this.lastDamage >= 0 && liveDamage > this.lastDamage) {
               dbg(
                  String.format(
                     "elytra wear %d -> %d while engaged: elytra %s, %d ms / %d tick(s) after the last glide command",
                     this.lastDamage,
                     liveDamage,
                     this.swapOutPending ? "in the chest slot" : "back in the hotbar",
                     this.lastFlashNanos == 0L ? -1L : (System.nanoTime() - this.lastFlashNanos) / 1000000L,
                     this.flashedTick == Integer.MIN_VALUE ? -1 : now - this.flashedTick
                  )
               );
            }

            if (liveDamage >= 0) {
               this.lastDamage = liveDamage;
            }

            if (this.engageDeadline != Integer.MIN_VALUE) {
               if (!this.flashDue && this.flashedTick == Integer.MIN_VALUE) {
                  if (now > this.engageDeadline) {
                     this.flashCooldownUntil = now + 200;
                     this.flashCooldownWhy = "a bail-out";
                     this.abandonSwap(now, "the swap-out click was not accepted (no server flag-clear in 40 ticks)");
                     return;
                  }
               } else {
                  this.engageDeadline = Integer.MIN_VALUE;
               }
            }

            if (now - this.flashSetbackWindowStart > 60) {
               this.flashSetbacks = 0;
               this.flashFullSyncs = 0;
               this.flashSetbackWindowStart = now;
            }

            if (this.flashFullSyncs >= 6) {
               this.flashCooldownUntil = now + 200;
               this.flashCooldownWhy = "a bail-out";
               this.flashDisengage(now, this.flashFullSyncs + " full inventory resyncs in 60 ticks - flying on the elytra for 10 s");
            } else if (this.hopFlight() && this.flashSetbacks > 0) {
               boolean quick = this.lastHopEngageTick != Integer.MIN_VALUE && now - this.lastHopEngageTick < 40;
               if (quick) {
                  this.flashCooldownUntil = now + 60;
                  this.flashCooldownWhy = "a bail-out";
               }

               this.dumpTrace("hop setback");
               this.flashDisengage(
                  now, "a server setback mid-hop - elytra back, " + (quick ? "flying on the elytra for 3 s" : "re-engaging at the next take-off")
               );
            } else if (this.flashSetbacks >= 20 && !this.hopFlight()) {
               this.flashCooldownUntil = now + 200;
               this.flashCooldownWhy = "a bail-out";
               this.dumpTrace("setback bail-out");
               this.flashDisengage(now, this.flashSetbacks + " server setbacks in 60 ticks - flying on the elytra for 10 s");
            } else if (this.startsRefused >= 4 && !this.hopFlight()) {
               this.flashCooldownUntil = now + 100;
               this.flashCooldownWhy = "a bail-out";
               this.dumpTrace("commands refused");
               this.flashDisengage(now, this.startsRefused + " zero-distance resyncs right after glide commands - flying on the elytra for 5 s");
            } else {
               if (!gliding || !localCanGlide() && !this.hopGroundGlide()) {
                  this.pinGlidePose = false;
                  this.notGlidingTicks++;
                  this.stopHoldingPongs();
                  if (this.hopFlight()) {
                     if (ReviveClient.mc.player.isOnGround()) {
                        this.hopGroundTicks++;
                     }

                     if (this.hopGroundTicks > 40 || ReviveClient.mc.player.isTouchingWater()) {
                        this.flashDisengage(now, "bounce ended");
                     }

                     return;
                  }

                  if (ReviveClient.mc.player.isOnGround()
                     || ReviveClient.mc.player.isTouchingWater()
                     || ReviveClient.mc.player.isInLava()
                     || ReviveClient.mc.player.hasVehicle()) {
                     this.flashCooldownUntil = Math.max(this.flashCooldownUntil, now + 60);
                     this.flashCooldownWhy = "a landing";
                     this.flashDisengage(now, "landed");
                     return;
                  }

                  if (this.notGlidingTicks > 40) {
                     this.flashDisengage(now, "not gliding for 40 ticks");
                     return;
                  }
               } else {
                  this.notGlidingTicks = 0;
                  if (this.hopFlight()) {
                     if (ReviveClient.mc.player.isOnGround()) {
                        this.hopGroundTicks++;
                     } else {
                        this.hopGroundTicks = 0;
                     }

                     if (this.hopGroundTicks > 40) {
                        this.flashDisengage(now, "bounce ended");
                        return;
                     }
                  } else {
                     this.hopGroundTicks = 0;
                  }

                  this.setLocalGlide(true);
                  this.pinGlidePose = !ReviveClient.mc.player.isOnGround();
                  this.holdPongs = true;
                  if (this.heldPongs.isEmpty()) {
                     this.pongsHeldSince = Integer.MIN_VALUE;
                  } else if (this.pongsHeldSince == Integer.MIN_VALUE) {
                     this.pongsHeldSince = now;
                  } else if (now - this.pongsHeldSince > glideGapTicks() + 20) {
                     this.releasePongs();
                  }

                  if (!this.flashDue && !this.swapOutPending && this.flashedTick != Integer.MIN_VALUE && now - this.flashedTick > 45) {
                     this.stalls++;
                     if (this.stalls > 3) {
                        this.flashCooldownUntil = now + 200;
                        this.flashCooldownWhy = "a bail-out";
                        this.flashDisengage(now, "no server answer to " + this.stalls + " glide commands in a row - flying on the elytra for 10 s");
                        return;
                     }

                     dbg("flash: no server flag-clear for " + (now - this.flashedTick) + " ticks after the glide command - repeating it");
                     this.flashedTick = now - 8;
                     this.flashDue = true;
                  }
               }

               double hSpeed = ReviveClient.mc.player.getVelocity().horizontalLength();
               this.reportSpeedSum += hSpeed;
               if (hSpeed > this.reportSpeedMax) {
                  this.reportSpeedMax = hSpeed;
               }

               this.reportTicks++;
               if (ReviveClient.mc.player.isSprinting()) {
                  this.reportSprintTicks++;
               }

               if (gliding) {
                  this.reportGlideTicks++;
               }

               if (ReviveClient.mc.player.isOnGround()) {
                  this.reportGroundTicks++;
               }

               if (now - this.lastFlashReportTick >= 100) {
                  this.lastFlashReportTick = now;
                  String motion = this.reportTicks > 0
                     ? String.format(
                        "; over the last %d ticks: speed peak %.2f, mean %.2f b/t (%.1f km/h), sprinting %d, gliding %d, on ground %d",
                        this.reportTicks,
                        this.reportSpeedMax,
                        this.reportSpeedSum / this.reportTicks,
                        this.reportSpeedSum / this.reportTicks * 72.0,
                        this.reportSprintTicks,
                        this.reportGlideTicks,
                        this.reportGroundTicks
                     )
                     : "";
                  dbg("flash: " + this.flashStats() + String.format("; y %.1f, speed %.2f b/t", ReviveClient.mc.player.getY(), hSpeed) + motion);
                  this.reportSpeedSum = 0.0;
                  this.reportSpeedMax = 0.0;
                  this.reportTicks = 0;
                  this.reportSprintTicks = 0;
                  this.reportGlideTicks = 0;
                  this.reportGroundTicks = 0;
                  this.folds = 0;
                  this.flashes = 0;
                  this.foreignClicks = 0;
                  this.startsConverted = 0;
                  this.flashesSkipped = 0;
                  this.chunkCuts = 0;
                  this.velocitiesKept = 0;
                  this.nudgesClosed = 0;
                  this.nudgeMax = 0.0;
               }
            }
         } else {
            if (this.lastSyncChestElytra) {
               this.abandonSwap(now, "lost track of the slots, and the server's last sync showed the elytra worn");
            } else {
               this.flashDisengage(now, "lost track of the slots");
            }
         }
      } else if (engaged) {
         this.flashDisengage(
            now,
            this.bounceUnderRoof()
               ? "a tunnel roof - three-tick hops leave no room for the swap, and the elytra loses nothing there"
               : (!this.isRequested() ? "no flight module wants it" : "water / vehicle / creative flight")
         );
      } else {
         if (!this.isRequested() && gliding && localCanGlide()) {
            this.unrequestedNotGlidingTicks = 0;
            if (this.unrequestedGlideTicks < 20 && ++this.unrequestedGlideTicks == 20) {
               dbg(
                  this.bounceUnderRoof()
                     ? "flash: bounce under a tunnel roof - staying out (three-tick hops leave no room for the swap; the glide counter resets on every landing there, so no durability is lost)"
                     : "flash: gliding, but no elytra module with save-durability on is active and elytra-saver-all-flights (anti-cheat config) is off - staying out of this flight, no swaps"
               );
            }
         } else if (++this.unrequestedNotGlidingTicks >= 100) {
            this.unrequestedGlideTicks = 0;
         }

         this.idleSinceTick = Integer.MIN_VALUE;
         this.reset();
         if (this.holdPongs) {
            this.stopHoldingPongs();
         }

         if (this.ownInput) {
            this.releaseOwnership();
         }
      }
   }

   private String flashStats() {
      return String.format(
         "%d cycles, %d module glide commands converted, %d deferred, %d stalls, %d gaps cut for a fresh chunk, %d server echoes folded (%d full resyncs), %d foreign inventory clicks waited out, pongs held up to %d, %d server setback(s) (largest %.1f), %d velocity packets kept from the Velocity module, elytra damage %d/%d",
         this.flashes,
         this.startsConverted,
         this.flashesSkipped,
         this.stalls,
         this.chunkCuts,
         this.folds,
         this.flashFullSyncs,
         this.foreignClicks,
         this.pongsHeldMax,
         this.nudgesClosed,
         this.nudgeMax,
         this.velocitiesKept,
         this.liveElytraDamage(),
         this.elytraStack.getMaxDamage()
      );
   }

   private static int glideGapTicks() {
      return AntiCheatConfig.get().elytraSaverGlideGap.get();
   }

   private void releasePongs() {
      if (!this.heldPongs.isEmpty()) {
         int n = 0;
         boolean clearOut = false;

         Integer id;
         while ((id = this.heldPongs.poll()) != null) {
            if (id != Integer.MIN_VALUE) {
               n++;
               if (this.clearPingIds.contains(id)) {
                  clearOut = true;
               }

               ClientPlayNetworkHandler handler = ReviveClient.mc.getNetworkHandler();
               if (handler != null) {
                  handler.sendPacket(new CommonPongC2SPacket(id));
               }
            }
         }

         if (this.tracing()) {
            this.trace("> " + n + " pong(s)" + (clearOut ? " incl. the clear's" : ""));
         }

         this.clearPingIds.clear();
         if (n > this.pongsHeldMax) {
            this.pongsHeldMax = n;
         }

         this.pongsHeldSince = Integer.MIN_VALUE;
         int last = this.lastHeldPingId;
         this.heldChunks.values().removeIf(p -> p != last);
      }
   }

   private void stopHoldingPongs() {
      this.holdPongs = false;
      this.releasePongs();
      this.heldChunks.clear();
   }

   private boolean inHeldChunk() {
      return !this.heldChunks.isEmpty()
         && this.heldChunks
            .containsKey(ChunkPos.toLong(MathHelper.floor(ReviveClient.mc.player.getX()) >> 4, MathHelper.floor(ReviveClient.mc.player.getZ()) >> 4));
   }

   public void onServerPositionApplying() {
      if (this.heldPongs.contains(Integer.MIN_VALUE)) {
         boolean clearOut = false;
         int n = 0;

         Integer id;
         while ((id = this.heldPongs.poll()) != null && id != Integer.MIN_VALUE) {
            n++;
            if (this.clearPingIds.remove(id)) {
               clearOut = true;
            }

            ClientPlayNetworkHandler handler = ReviveClient.mc.getNetworkHandler();
            if (handler != null) {
               handler.sendPacket(new CommonPongC2SPacket(id));
            }
         }

         if (this.tracing()) {
            this.trace("> teleport flush: " + n + " pong(s)" + (clearOut ? " incl. the clear's" : ""));
         }

         if (clearOut) {
            this.gapCut = true;
         }
      }
   }

   private int liveElytraDamage() {
      if (ReviveClient.mc.player != null) {
         ItemStack chest = ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST);
         if (chest.isOf(Items.ELYTRA)) {
            return chest.getDamage();
         }
      }

      return this.elytraStack.isEmpty() ? -1 : this.elytraStack.getDamage();
   }

   @EventHandler(
      priority = -200
   )
   private void onSendMovementPackets(SendMovementPacketsEvent.Pre event) {
      if (ReviveClient.mc.player != null && ReviveClient.mc.getNetworkHandler() != null) {
         this.flushBurnUse(ReviveClient.mc.player.age, mode() != ElytraDurabilitySaver.Mode.Flash || !ReviveClient.mc.player.isGliding());
         if (mode() == ElytraDurabilitySaver.Mode.Flash) {
            if (this.inputResyncOwed && !this.ownInput && !this.inputSentThisTick) {
               this.inputResyncOwed = false;
               PlayerInput real = ReviveClient.mc.player.input != null ? ReviveClient.mc.player.input.playerInput : PlayerInput.DEFAULT;
               if (!real.equals(this.serverInput == null ? PlayerInput.DEFAULT : this.serverInput)) {
                  this.sendOwn(new PlayerInputC2SPacket(real));
               }
            }

            if (this.pendingWrapUse != null) {
               int t = ReviveClient.mc.player.age;
               if (this.rocketWrapReady(t)) {
                  PlayerInteractItemC2SPacket held = this.pendingWrapUse;
                  this.pendingWrapUse = null;
                  this.pendingWrapUntilTick = Integer.MIN_VALUE;
                  this.flashRocketWrap(t, held);
                  return;
               }

               if (t > this.pendingWrapUntilTick || this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT || !ReviveClient.mc.player.isGliding()) {
                  PlayerInteractItemC2SPacket held = this.pendingWrapUse;
                  this.pendingWrapUse = null;
                  this.pendingWrapUntilTick = Integer.MIN_VALUE;
                  if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.disengagePendingWhy == null) {
                     this.flashDisengage(t, "a held rocket that never found a cycle - flying its real burn");
                  }

                  this.burnUntilTick = Math.max(this.burnUntilTick, t + 30);
                  if (this.burnUseCanGo() || this.burnUseAfterResume != null) {
                     this.sendOwn(held);
                     return;
                  }

                  this.burnUseAfterResume = held;
                  this.burnUseUntilTick = t + 24;
               }
            }

            if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT || this.resumeDeadline != Integer.MIN_VALUE || this.swapOutPending || this.wantJump) {
               int now = ReviveClient.mc.player.age;
               if (this.wantJump && this.flashedTick != now && !this.inputSentThisTick) {
                  this.sendOwnInput(false);
               }

               if (this.wrapJumpOwed) {
                  this.wrapJumpOwed = false;
                  if (this.ownInput && !this.inputSentThisTick) {
                     this.sendOwnInput(true);
                  }
               }

               if (this.swapOutPending
                  && this.flashedTick != now
                  && !this.serverJump()
                  && this.hotbarSlot >= 0
                  && ReviveClient.mc.interactionManager != null
                  && playerInventoryOnly()) {
                  this.swapOutClick();
                  this.swapOutPending = false;
               }

               if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT && !this.gapCut && this.inHeldChunk()) {
                  this.chunkCuts++;
                  this.gapCut = true;
                  this.trace("gap cut: chunk sent during the hold");
               }

               boolean gapOver = this.gapCut || this.clearAppliedTick == Integer.MIN_VALUE || now - this.clearAppliedTick >= glideGapTicks();
               boolean commandAllowed = this.flashDue
                  && !this.swapOutPending
                  && !this.serverJump()
                  && !this.inputSentThisTick
                  && (this.flashedTick == Integer.MIN_VALUE || now - this.flashedTick >= 8)
                  && localCanGlide()
                  && (!this.hopFlight() || this.hopAirborneTicks >= 2);
               if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
                  if (!ReviveClient.mc.player.isGliding()) {
                     return;
                  }

                  if (commandAllowed && gapOver && this.foreignClickQuiet(now)) {
                     this.flash(now, true);
                  } else {
                     if (this.gapCut && !this.heldPongs.isEmpty()) {
                        this.releasePongs();
                     }

                     if (this.flashDue && gapOver) {
                        this.flashesSkipped++;
                     }
                  }
               } else if (this.resumeDeadline != Integer.MIN_VALUE && commandAllowed) {
                  this.trace("resume command");
                  this.flash(now, false);
                  this.resumeDeadline = Integer.MIN_VALUE;
                  dbg("flight resumed on the elytra");
                  this.flushBurnUse(now, true);
               }
            }
         }
      }
   }

   private void flash(int now, boolean clicks) {
      if (ReviveClient.mc.getNetworkHandler() != null
         && (
            !clicks
               || this.hotbarSlot >= 0
                  && ReviveClient.mc.interactionManager != null
                  && playerInventoryOnly()
                  && ReviveClient.mc.player.playerScreenHandler.getCursorStack().isEmpty()
         )) {
         this.releasePongs();
         this.gapCut = false;
         this.flashing = true;

         try {
            if (clicks) {
               this.applyTrueLayout();
               this.flashClick(
                  ReviveClient.mc.player.playerScreenHandler.syncId,
                  36 + this.hotbarSlot,
                  AntiCheatConfig.get().elytraSaverClick.get() == ElytraDurabilitySaver.Click.Pickup
               );
               this.applyViewLayout();
               this.swapOutPending = true;
            }

            this.sendOwn(
               new ClientCommandC2SPacket(ReviveClient.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING)
            );
            this.lastFlashNanos = System.nanoTime();
            if (clicks) {
               this.swapOutClick();
               this.swapOutPending = false;
               this.flashing = true;
            }

            this.sendOwnInput(true);
         } finally {
            this.flashing = false;
         }

         this.flashedTick = now;
         this.flashDue = false;
         this.flashes++;
         if (localCanGlide()) {
            this.notGlidingTicks = 0;
            this.setLocalGlide(true);
         }
      } else {
         this.flashesSkipped++;
      }
   }

   private void swapOutClick() {
      this.flashing = true;

      try {
         this.flashClick(
            ReviveClient.mc.player.playerScreenHandler.syncId,
            36 + this.hotbarSlot,
            AntiCheatConfig.get().elytraSaverClick.get() == ElytraDurabilitySaver.Click.Pickup
         );
         this.applyViewLayout();
      } finally {
         this.flashing = false;
      }

      this.awaitingClear = true;
   }

   private void flashClick(int syncId, int hotbarContainerSlot, boolean pickup) {
      if (pickup) {
         this.ownClickSlot(syncId, hotbarContainerSlot, 0, SlotActionType.PICKUP);
         this.ownClickSlot(syncId, 6, 0, SlotActionType.PICKUP);
         this.ownClickSlot(syncId, hotbarContainerSlot, 0, SlotActionType.PICKUP);
      } else {
         this.ownClickSlot(syncId, 6, this.hotbarSlot, SlotActionType.SWAP);
      }

      if (this.tracing()) {
         this.trace(
            "> click "
               + (pickup ? "pickup x3" : "swap")
               + " -> chest="
               + itemName(ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST))
               + " rev="
               + ReviveClient.mc.player.playerScreenHandler.getRevision()
         );
      }
   }

   private static String itemName(ItemStack stack) {
      return stack.isEmpty() ? "empty" : stack.getItem().getName().getString();
   }

   private void applyTrueLayout() {
      ReviveClient.mc.player.equipStack(EquipmentSlot.CHEST, this.plateStack.copy());
      ReviveClient.mc.player.getInventory().setStack(this.hotbarSlot, this.elytraStack.copy());
   }

   private void applyViewLayout() {
      ReviveClient.mc.player.equipStack(EquipmentSlot.CHEST, this.elytraStack.copy());
      ReviveClient.mc.player.getInventory().setStack(this.hotbarSlot, this.plateStack.copy());
   }

   private int nudgeWindowAge(int now) {
      return this.recentNudgeWindowStart == Integer.MIN_VALUE ? Integer.MAX_VALUE : now - this.recentNudgeWindowStart;
   }

   private String nudgeRate(int now) {
      return this.nudgeWindowAge(now) <= 40
         ? "nudged this flight " + this.recentNudges + " time(s) in the last " + Math.max(1, this.nudgeWindowAge(now)) + " ticks (limit 12 in 40)"
         : "has not nudged this flight for 40 ticks";
   }

   private void lateGateReport(int now, String why) {
      if (this.lastLateGateReportTick == Integer.MIN_VALUE || now - this.lastLateGateReportTick >= 100) {
         this.lastLateGateReportTick = now;
         dbg("flash: not engaged - " + why + " (a full 20-tick glide thrown away)");
      }
   }

   private void resetEngagementStats(int now) {
      this.stalls = 0;
      this.flashSetbacks = 0;
      this.flashSetbacksLogged = 0;
      this.flashUnexpectedClearsLogged = 0;
      this.flashFullSyncs = 0;
      this.flashSetbackWindowStart = now;
      this.flashes = 0;
      this.foreignClicks = 0;
      this.startsConverted = 0;
      this.flashesSkipped = 0;
      this.chunkCuts = 0;
      this.velocitiesKept = 0;
      this.gapCut = false;
      this.startsRefused = 0;
      this.refusedLogged = 0;
      this.lastFlashReportTick = now;
   }

   private boolean hopStart(PacketEvent.Send event) {
      if (!ReviveClient.mc.player.isGliding() && !ReviveClient.mc.player.isOnGround() && !ReviveClient.mc.player.isTouchingWater()) {
         int now = ReviveClient.mc.player.age;
         boolean engaged = this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT;
         if (this.hopAirborneTicks < 2) {
            event.setCancelled(true);
            this.hopSuppressGlideTick = now;
            this.trace("x module START (hop: first airborne tick, swallowed)");
            return true;
         } else if (engaged || this.isRequested() && now >= this.flashCooldownUntil && playerInventoryOnly() && this.swapOutLayoutOk(findHotbarChestplate())) {
            if (!this.ownInput) {
               this.takeOwnership();
            }

            event.setCancelled(true);
            this.hopCommandThisHop = true;
            if (engaged) {
               this.startsConverted++;
               this.flash(now, true);
               this.holdPongs = true;
               this.trace("> module START taken over (hop)");
               return true;
            } else {
               int slot = findHotbarChestplate();
               this.releasePongs();
               this.flashing = true;

               try {
                  this.sendOwn(
                     new ClientCommandC2SPacket(ReviveClient.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING)
                  );
                  this.lastFlashNanos = System.nanoTime();
                  this.swapOut(slot, now);
                  this.sendOwnInput(true);
               } finally {
                  this.flashing = false;
               }

               this.notGlidingTicks = 0;
               this.hopGroundTicks = 0;
               this.swapOutPending = false;
               this.awaitingClear = true;
               this.flashDue = false;
               this.engageDeadline = Integer.MIN_VALUE;
               this.flashedTick = now;
               this.flashResyncsLogged = 0;
               this.resetEngagementStats(now);
               this.lastHopEngageTick = now;
               this.flashes = 1;
               this.holdPongs = true;
               this.idleSinceTick = Integer.MIN_VALUE;
               this.trace("engage (hop)");
               dbg(
                  "flash mode (bounce hops): chestplate stays on server-side across the hops; each take-off command carries the elytra in and straight back out - never worn for a server tick"
               );
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @EventHandler
   private void onPlayerTickMovement(PlayerTickMovementEvent event) {
      if (ReviveClient.mc.player != null && this.hopSuppressGlideTick != Integer.MIN_VALUE && ReviveClient.mc.player.age - this.hopSuppressGlideTick <= 1) {
         this.hopSuppressGlideTick = Integer.MIN_VALUE;
         if (ReviveClient.mc.player.isGliding()) {
            this.setLocalGlide(false);
            this.pinGlidePose = false;
            this.trace("hop: client glide undone for the swallowed command");
         }
      }
   }

   @EventHandler(
      priority = -200
   )
   private void onMovementPacketsSent(SendMovementPacketsEvent.Post event) {
      if (ReviveClient.mc.player != null && ReviveClient.mc.getNetworkHandler() != null) {
         boolean ground = ReviveClient.mc.player.isOnGround();
         this.hopAirborneTicks = ground ? 0 : this.hopAirborneTicks + 1;
         if (this.hopFlight()) {
            int now = ReviveClient.mc.player.age;
            boolean engaged = this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT;
            if (engaged || this.isRequested() && now >= this.flashCooldownUntil && findHotbarChestplate() >= 0) {
               if (!ground && !ReviveClient.mc.player.isTouchingWater()) {
                  if (engaged && !this.hopCommandThisHop && ReviveClient.mc.player.isGliding() && this.hopAirborneTicks == 2) {
                     this.setLocalGlide(false);
                     this.pinGlidePose = false;
                     this.trace("hop: ground-carried glide cleared for the take-off command");
                  }

                  if (!ReviveClient.mc.player.isGliding() && this.hopAirborneTicks >= 2 && !this.ownInput && !this.inputSentThisTick) {
                     this.takeOwnership();
                  }
               } else {
                  this.hopCommandThisHop = false;
                  if (engaged && ReviveClient.mc.player.isTouchingWater()) {
                     this.releasePongs();
                     if (ReviveClient.mc.player.isGliding()) {
                        this.setLocalGlide(false);
                     }

                     this.pinGlidePose = false;
                  }

                  if (this.ownInput) {
                     this.releaseOwnership();
                  }
               }
            }
         }
      }
   }

   private void idleReport(int now, String why) {
      if (this.idleSinceTick == Integer.MIN_VALUE) {
         this.idleSinceTick = now;
         this.lastIdleReportTick = Integer.MIN_VALUE;
      }

      if (now - this.idleSinceTick >= 40) {
         if (this.lastIdleReportTick == Integer.MIN_VALUE || now - this.lastIdleReportTick >= 100) {
            this.lastIdleReportTick = now;
            dbg("flash: not engaged - " + why);
         }
      }
   }

   private void flashDisengage(int now, String why) {
      if (this.tracing()) {
         this.trace("disengage: " + why);
      }

      if (!this.ownInput && this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
         this.takeOwnership();
      }

      if (this.serverJump()) {
         if (this.inputSentThisTick) {
            this.disengagePendingWhy = why;
            return;
         }

         this.sendOwnInput(false);
      }

      boolean clearSeen = this.flashDue || !this.serverGliding && !this.awaitingClear;
      if (this.swapOutPending) {
         this.swapOutPending = false;
         dbg("flash mode off (" + why + ") - elytra already in the chest slot server-side; " + this.flashStats());
         this.restoreServerElytra(now, false);
         this.reset();
         this.stopHoldingPongs();
      } else {
         dbg("flash mode off (" + why + ") - elytra back in the chest slot server-side; " + this.flashStats());
         this.restoreServerElytra(now, true);
         this.reset();
         if (localCanGlide() && !ReviveClient.mc.player.getAbilities().flying) {
            this.resumeDeadline = now + 20;
            this.flashDue = clearSeen;
            this.setLocalGlide(true);
            this.pinGlidePose = true;
         } else {
            this.stopHoldingPongs();
            if (this.ownInput) {
               this.releaseOwnership();
            }
         }
      }
   }

   private void abandonSwap(int now, String why) {
      dbg("flash mode off (" + why + ") - the elytra never left the chest slot server-side");
      this.hotbarSlot = -1;
      this.viewGuardUntilTick = Integer.MIN_VALUE;
      this.reset();
      this.stopHoldingPongs();
      if (this.ownInput && !this.wantJump) {
         this.releaseOwnership();
      }
   }

   private static PlayerInput ownedInput(boolean jump) {
      return new PlayerInput(false, false, false, false, jump, false, false);
   }

   private boolean serverJump() {
      return this.serverInput != null && this.serverInput.jump();
   }

   private void takeOwnership() {
      this.ownInput = true;
      this.wantJump = false;
      PlayerInput want = ownedInput(false);
      if (!want.equals(this.serverInput == null ? PlayerInput.DEFAULT : this.serverInput)) {
         this.sendOwnInput(false);
      }

      this.sendOwn(new ClientCommandC2SPacket(ReviveClient.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.STOP_SPRINTING));
   }

   private void releaseOwnership() {
      this.ownInput = false;
      this.wantJump = false;
      if (ReviveClient.mc.player != null && ReviveClient.mc.getNetworkHandler() != null) {
         this.sprintGuardUntilTick = ReviveClient.mc.player.age + 20;
         PlayerInput real = ReviveClient.mc.player.input != null ? ReviveClient.mc.player.input.playerInput : PlayerInput.DEFAULT;
         if (!real.equals(this.serverInput == null ? PlayerInput.DEFAULT : this.serverInput)) {
            if (this.inputSentThisTick) {
               this.inputResyncOwed = true;
            } else {
               this.sendOwn(new PlayerInputC2SPacket(real));
            }
         }

         boolean sprinting = ReviveClient.mc.player.isSprinting();
         this.clientSprintSent = sprinting;
         if (sprinting != this.serverSprinting) {
            this.sendOwn(
               new ClientCommandC2SPacket(
                  ReviveClient.mc.player,
                  sprinting
                     ? net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_SPRINTING
                     : net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.STOP_SPRINTING
               )
            );
         }
      }
   }

   private void sendOwnInput(boolean jump) {
      this.wantJump = jump;
      this.sendOwn(new PlayerInputC2SPacket(ownedInput(jump)));
   }

   private void sendOwn(Packet<?> packet) {
      if (this.tracing()) {
         if (packet instanceof ClientCommandC2SPacket c) {
            this.trace("> " + c.getMode());
         } else if (packet instanceof PlayerInputC2SPacket p) {
            this.trace("> input jump=" + p.input().jump());
         }
      }

      this.sendingOwnPacket = true;

      try {
         ReviveClient.mc.getNetworkHandler().sendPacket(packet);
      } finally {
         this.sendingOwnPacket = false;
      }
   }

   public boolean ownsServerInput() {
      return this.ownInput;
   }

   public boolean isFlightVelocity(EntityVelocityUpdateS2CPacket packet) {
      if (mode() != ElytraDurabilitySaver.Mode.Flash || ReviveClient.mc.player == null || packet.getEntityId() != ReviveClient.mc.player.getId()) {
         return false;
      } else if (this.hopFlight()) {
         return false;
      } else if (this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.resumeDeadline == Integer.MIN_VALUE) {
         return false;
      } else {
         this.velocitiesKept++;
         if (this.tracing()) {
            this.trace("< velocity kept (" + (System.nanoTime() - this.lastServerTeleportNanos <= 150000000L ? "setback" : "boost") + ")");
         }

         return true;
      }
   }

   public boolean passesVelocityAsSent(EntityVelocityUpdateS2CPacket packet) {
      if (mode() != ElytraDurabilitySaver.Mode.Flash || ReviveClient.mc.player == null || packet.getEntityId() != ReviveClient.mc.player.getId()) {
         return false;
      } else if (!ReviveClient.mc.player.isGliding()) {
         return false;
      } else if (!this.isRequested() && this.phase != ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.resumeDeadline == Integer.MIN_VALUE) {
         return false;
      } else {
         boolean setback = System.nanoTime() - this.lastServerTeleportNanos <= 150000000L;
         if (setback) {
            this.setbackVelocitiesPassed++;
         } else {
            this.burnVelocitiesPassed++;
         }

         Vec3d v = packet.getVelocity();
         Vec3d c = ReviveClient.mc.player.getVelocity();
         if (this.tracing()) {
            this.trace(
               String.format("< velocity as sent (%s) %.2f,%.2f,%.2f vs client %.2f,%.2f,%.2f", setback ? "setback" : "flight", v.x, v.y, v.z, c.x, c.y, c.z)
            );
         }

         return true;
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onPacketSend(PacketEvent.Send event) {
      if (event.packet instanceof ClickSlotC2SPacket && !this.ownClicking && !this.sendingOwnPacket) {
         this.foreignClickPending = true;
         if (this.tracing()) {
            this.trace("> foreign click (another module/Baritone) - our clicks wait 8 ticks");
         }
      }

      if (!this.sendingOwnPacket && ReviveClient.mc.player != null) {
         if (event.packet instanceof ClientCommandC2SPacket packet) {
            switch (packet.getMode()) {
               case START_SPRINTING:
               case STOP_SPRINTING:
                  this.clientSprintSent = packet.getMode() == net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_SPRINTING;
                  if (this.ownInput) {
                     event.setCancelled(true);
                     if (this.tracing()) {
                        this.trace("x client " + packet.getMode() + " held");
                     }
                  } else if (this.tracing()) {
                     this.trace("> client " + packet.getMode());
                  }
                  break;
               case START_FALL_FLYING:
                  if (mode() != ElytraDurabilitySaver.Mode.Flash) {
                     return;
                  }

                  if (this.hopFlight() && this.hopStart(event)) {
                     return;
                  }

                  if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
                     event.setCancelled(true);
                     this.trace("x module START dropped");
                     this.startsConverted++;
                     if (!ReviveClient.mc.player.isGliding()) {
                        this.flashDue = true;
                     }
                  } else if (this.resumeDeadline != Integer.MIN_VALUE) {
                     this.resumeDeadline = Integer.MIN_VALUE;
                     this.flashDue = false;
                     this.stopHoldingPongs();
                     this.trace("> module START (resume taken over)");
                  } else {
                     this.trace("> module START");
                  }
            }
         } else if (event.packet instanceof PlayerInputC2SPacket packet) {
            if (!this.ownInput) {
               if (this.tracing()) {
                  this.trace("> client input " + inputKeys(packet.input()));
               }
            } else {
               PlayerInput want = ownedInput(this.wantJump);
               if (!packet.input().equals(want)) {
                  event.setCancelled(true);
                  if (this.tracing()) {
                     this.trace("x client input " + inputKeys(packet.input()) + " held");
                  }

                  if (!this.inputSentThisTick && !want.equals(this.serverInput == null ? PlayerInput.DEFAULT : this.serverInput)) {
                     this.sendOwnInput(this.wantJump);
                  }
               }
            }
         } else {
            if (event.packet instanceof PlayerInteractItemC2SPacket use
               && mode() == ElytraDurabilitySaver.Mode.Flash
               && ReviveClient.mc.player.isGliding()
               && (use.getHand() == Hand.MAIN_HAND ? ReviveClient.mc.player.getMainHandStack() : ReviveClient.mc.player.getOffHandStack())
                  .isOf(Items.FIREWORK_ROCKET)) {
               int now = ReviveClient.mc.player.age;
               if (this.rocketWrapReady(now)) {
                  event.setCancelled(true);
                  this.flashRocketWrap(now, use);
                  return;
               }

               if (this.serverGlideSteady()) {
                  return;
               }

               if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.pendingWrapUse == null && this.wrapRockets()) {
                  event.setCancelled(true);
                  this.pendingWrapUse = use;
                  this.pendingWrapUntilTick = now + 8;
                  this.trace("rocket held to wrap on the next cycle");
                  return;
               }

               if (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT && this.disengagePendingWhy == null) {
                  this.flashDisengage(now, "a rocket between cycles - flying its real burn instead");
               }

               this.burnUntilTick = Math.max(this.burnUntilTick, now + 30);
               if (!this.burnUseCanGo() && this.burnUseAfterResume == null) {
                  event.setCancelled(true);
                  this.burnUseAfterResume = use;
                  this.burnUseUntilTick = now + 24;
                  this.trace("rocket by hand: held until the glide is resumed server-side");
                  return;
               }

               this.trace("rocket by hand: server already gliding, sent as is");
            }
         }
      }
   }

   private void flashRocketWrap(int now, PlayerInteractItemC2SPacket use) {
      this.releasePongs();
      this.gapCut = false;
      this.flashing = true;

      try {
         this.applyTrueLayout();
         this.flashClick(
            ReviveClient.mc.player.playerScreenHandler.syncId,
            36 + this.hotbarSlot,
            AntiCheatConfig.get().elytraSaverClick.get() == ElytraDurabilitySaver.Click.Pickup
         );
         this.applyViewLayout();
         this.swapOutPending = true;
         this.sendOwn(new ClientCommandC2SPacket(ReviveClient.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
         this.lastFlashNanos = System.nanoTime();
         this.sendOwn(use);
         this.swapOutClick();
         this.swapOutPending = false;
      } finally {
         this.flashing = false;
      }

      this.wrapJumpOwed = true;
      this.wrapGraceUntilTick = now + 60;
      this.flashedTick = now;
      this.flashDue = false;
      this.flashes++;
      this.rocketsWrapped++;
      this.trace("> rocket riding the cycle");
      if (this.rocketsWrapped == 1) {
         dbg("rocket fired inside the swap: elytra in and straight back around it - no server burn, no worn server tick, the client flies the boost");
      }

      if (localCanGlide()) {
         this.notGlidingTicks = 0;
         this.setLocalGlide(true);
      }
   }

   private static String inputKeys(PlayerInput in) {
      return (in.forward() ? "W" : "")
         + (in.backward() ? "S" : "")
         + (in.left() ? "A" : "")
         + (in.right() ? "D" : "")
         + (in.jump() ? "J" : "")
         + (in.sneak() ? "Sn" : "")
         + (in.sprint() ? "Sp" : "");
   }

   @EventHandler(
      priority = -200
   )
   private void onPacketSent(PacketEvent.Send event) {
      if (event.packet instanceof PlayerInputC2SPacket packet) {
         this.serverInput = packet.input();
         this.inputSentThisTick = true;
      } else if (event.packet instanceof ClientCommandC2SPacket packet) {
         if (packet.getMode() == net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_SPRINTING) {
            this.serverSprinting = true;
         } else if (packet.getMode() == net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
            this.serverSprinting = false;
         }
      }
   }

   private boolean swapOutLayoutOk(int slot) {
      return slot >= 0
         && ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
         && isChestplate(ReviveClient.mc.player.getInventory().getStack(slot));
   }

   private void swapOut(int slot, int now) {
      ItemStack elytra = ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).copy();
      ItemStack plate = ReviveClient.mc.player.getInventory().getStack(slot).copy();
      if (this.backoffCyclesLeft > 0) {
         this.backoffCyclesLeft--;
      }

      int damage = elytra.getDamage();
      if (this.lastDamage >= 0 && damage > this.lastDamage) {
         dbg("elytra lost durability (" + this.lastDamage + " -> " + damage + ")");
      }

      this.lastDamage = damage;
      this.ownClickSlot(ReviveClient.mc.player.playerScreenHandler.syncId, 6, slot, SlotActionType.SWAP);
      if (this.tracing()) {
         this.trace("> click engage (chestplate on) rev=" + ReviveClient.mc.player.playerScreenHandler.getRevision());
      }

      ReviveClient.mc.player.equipStack(EquipmentSlot.CHEST, elytra);
      ReviveClient.mc.player.getInventory().setStack(slot, plate);
      this.hotbarSlot = slot;
      this.plateStack = plate;
      this.elytraStack = elytra;
      this.phase = ElytraDurabilitySaver.Phase.SWAPPED_OUT;
      this.phaseTicks = 0;
      this.glideTicks = 0;
      this.reopens = 0;
      this.ownedUntilTick = now + 10 + 30;
      this.viewGuardUntilTick = Integer.MAX_VALUE;
      this.cycles++;
      this.windowStartY = ReviveClient.mc.player.getY();
      this.windowStartSpeed = ReviveClient.mc.player.getVelocity().horizontalLength();
      dbg(
         "cycle "
            + this.cycles
            + ": chestplate (hotbar "
            + slot
            + ") into the chest slot server-side (elytra damage "
            + elytra.getDamage()
            + "/"
            + elytra.getMaxDamage()
            + ")"
            + (this.nudgesOpen > 0 ? " - " + this.nudgesOpen + " server nudge(s) while open" : "")
      );
      this.nudgesOpen = 0;
   }

   private void restoreServerElytra(int now) {
      this.restoreServerElytra(now, true);
   }

   private void restoreServerElytra(int now, boolean click) {
      if (click && this.hotbarSlot >= 0 && ReviveClient.mc.player != null && ReviveClient.mc.interactionManager != null) {
         this.enforceLocalView(now);
         ItemStack elytra = ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).copy();
         ItemStack plate = ReviveClient.mc.player.getInventory().getStack(this.hotbarSlot).copy();
         ReviveClient.mc.player.equipStack(EquipmentSlot.CHEST, plate.copy());
         ReviveClient.mc.player.getInventory().setStack(this.hotbarSlot, elytra.copy());
         this.ownClickSlot(ReviveClient.mc.player.playerScreenHandler.syncId, 6, this.hotbarSlot, SlotActionType.SWAP);
         if (this.tracing()) {
            this.trace("> click restore (elytra back) rev=" + ReviveClient.mc.player.playerScreenHandler.getRevision());
         }

         ReviveClient.mc.player.equipStack(EquipmentSlot.CHEST, elytra);
         ReviveClient.mc.player.getInventory().setStack(this.hotbarSlot, plate);
      }

      this.phase = ElytraDurabilitySaver.Phase.IDLE;
      this.phaseTicks = 0;
      this.glideTicks = 0;
      this.ownedUntilTick = Math.max(this.ownedUntilTick, now + 30);
      this.viewGuardUntilTick = now + 15;
      if (this.closedSinceTick != Integer.MIN_VALUE && now - this.closedSinceTick >= 8 && this.nudgesClosed > 0) {
         double measured = Math.max(0.0, -this.nudgeDy) / (now - this.closedSinceTick);
         this.nudgeDragPerTick = 0.7 * this.nudgeDragPerTick + 0.3 * measured;
      }

      String closedFor = this.closedSinceTick != Integer.MIN_VALUE ? " after " + (now - this.closedSinceTick) + " closed ticks" : "";
      String nudges = this.nudgesClosed > 0
         ? String.format(
            ", %d server nudge(s), largest %.1f, together %.1f down and %.1f sideways", this.nudgesClosed, this.nudgeMax, -this.nudgeDy, this.nudgeHoriz
         )
         : ", no server nudges";
      String cost = ReviveClient.mc.player != null
         ? String.format(
            "; client y %.1f -> %.1f, speed %.2f -> %.2f b/t",
            this.windowStartY,
            ReviveClient.mc.player.getY(),
            this.windowStartSpeed,
            ReviveClient.mc.player.getVelocity().horizontalLength()
         )
         : "";
      String kept = String.format(
         "; velocity: %d position-packet restores by hook, %d by tick, %d velocity packets dropped, %d passed, %d setback and %d flight velocities applied as sent%s",
         this.velocityKeptHook,
         this.velocityKeptTick,
         this.velocityDropped,
         this.velocityPassed,
         this.setbackVelocitiesPassed,
         this.burnVelocitiesPassed,
         this.positionKeptHook > 0 ? ", position kept " + this.positionKeptHook + " times" : ""
      );
      dbg("elytra back in the chest slot server-side" + closedFor + nudges + cost + kept);
      this.setbackVelocitiesPassed = 0;
      this.burnVelocitiesPassed = 0;
      this.velocityKeptHook = 0;
      this.velocityKeptTick = 0;
      this.positionKeptHook = 0;
      this.velocityDropped = 0;
      this.velocityPassed = 0;
      this.nudgesClosed = 0;
      this.nudgeMax = 0.0;
      this.nudgeDy = 0.0;
      this.nudgeHoriz = 0.0;
   }

   private void sendReopen(int now) {
      ReviveClient.mc
         .getNetworkHandler()
         .sendPacket(new ClientCommandC2SPacket(ReviveClient.mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
      this.reopens++;
      this.reopenAcked = false;
      this.ackDeadlineTick = now + 12;
      this.glideTicks = 0;
      this.ownedUntilTick = Math.max(this.ownedUntilTick, now + 12 + 30);
   }

   private void reset() {
      this.phase = ElytraDurabilitySaver.Phase.IDLE;
      this.glideTicks = 0;
      this.phaseTicks = 0;
      this.wrapJumpOwed = false;
      this.rocketsWrapped = 0;
      this.ownedUntilTick = Integer.MIN_VALUE;
      this.reopens = 0;
      this.lastDamage = -1;
      this.pinGlidePose = false;
      this.reopenDueTick = Integer.MIN_VALUE;
      this.reopenAcked = true;
      this.ackDeadlineTick = Integer.MIN_VALUE;
      this.closedUntilTick = Integer.MIN_VALUE;
   }

   private boolean guardActive(int now) {
      return this.hotbarSlot >= 0 && (this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT || now <= this.viewGuardUntilTick);
   }

   private void enforceLocalView(int now, String why) {
      if (ReviveClient.mc.player != null && this.guardActive(now)) {
         ItemStack chest = ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST);
         ItemStack bar = ReviveClient.mc.player.getInventory().getStack(this.hotbarSlot);
         boolean chestIsElytra = chest.isOf(Items.ELYTRA);
         boolean barIsElytra = bar.isOf(Items.ELYTRA);
         if (!chestIsElytra || barIsElytra) {
            ItemStack elytra;
            ItemStack plate;
            if (!chestIsElytra && barIsElytra) {
               elytra = bar.copy();
               plate = chest.copy();
            } else if (chestIsElytra) {
               elytra = chest.copy();
               plate = this.plateStack.copy();
            } else {
               if (this.elytraStack.isEmpty()) {
                  return;
               }

               elytra = this.elytraStack.copy();
               plate = isChestplate(chest) ? chest.copy() : this.plateStack.copy();
            }

            ReviveClient.mc.player.equipStack(EquipmentSlot.CHEST, elytra);
            ReviveClient.mc.player.getInventory().setStack(this.hotbarSlot, plate);
            if (!plate.isEmpty()) {
               this.plateStack = plate;
            }

            this.elytraStack = elytra;
            if (mode() == ElytraDurabilitySaver.Mode.Flash) {
               this.folds++;
            } else {
               dbg("server " + why + " showed the swap - client view put back to the elytra");
            }
         }
      }
   }

   private void enforceLocalView(int now) {
      this.enforceLocalView(now, "sync");
   }

   private static boolean isChestplate(ItemStack s) {
      if (!s.isEmpty() && !s.isOf(Items.ELYTRA)) {
         EquippableComponent eq = (EquippableComponent)s.get(DataComponentTypes.EQUIPPABLE);
         return eq != null && eq.slot() == EquipmentSlot.CHEST;
      } else {
         return false;
      }
   }

   @EventHandler
   private void onInventorySync(InventoryEvent event) {
      if (ReviveClient.mc.player != null) {
         if (event.packet != null && event.packet.contents().size() > 6) {
            this.lastSyncChestElytra = ((ItemStack)event.packet.contents().get(6)).isOf(Items.ELYTRA);
         }

         if (mode() == ElytraDurabilitySaver.Mode.Flash && this.phase == ElytraDurabilitySaver.Phase.SWAPPED_OUT) {
            this.flashFullSyncs++;
            if (this.flashResyncsLogged < 3 && event.packet != null && this.hotbarSlot >= 0) {
               this.flashResyncsLogged++;
               List<ItemStack> contents = event.packet.contents();
               int hotbar = 36 + this.hotbarSlot;
               String chest = contents.size() > 6 ? contents.get(6).getItem().getName().getString() : "?";
               String hot = contents.size() > hotbar ? contents.get(hotbar).getItem().getName().getString() : "?";
               int now = ReviveClient.mc.player.age;
               dbg(
                  String.format(
                     "flash: full inventory resync (revision %d): server chest slot = %s, hotbar %d = %s; expected %s; %d tick(s) after the last glide command",
                     event.packet.revision(),
                     chest,
                     this.hotbarSlot,
                     hot,
                     this.swapOutPending ? "elytra worn" : "chestplate worn",
                     this.flashedTick == Integer.MIN_VALUE ? -1 : now - this.flashedTick
                  )
               );
            }
         }

         if (event.packet != null) {
            List<ItemStack> contents = event.packet.contents();
            int hotbar = 36 + Math.max(0, this.hotbarSlot);
            if (this.tracing()) {
               this.trace(
                  "< full sync rev="
                     + event.packet.revision()
                     + " chest="
                     + (contents.size() > 6 ? itemName(contents.get(6)) : "?")
                     + " hotbar="
                     + (contents.size() > hotbar ? itemName(contents.get(hotbar)) : "?")
               );
            }
         }

         this.enforceLocalView(ReviveClient.mc.player.age, "full inventory sync");
      }
   }

   @EventHandler
   private void onSlotUpdate(ContainerSlotUpdateEvent event) {
      if (ReviveClient.mc.player != null && event.packet != null && event.packet.getSyncId() == 0) {
         int slot = event.packet.getSlot();
         if (this.tracing()) {
            this.trace("< slot " + slot + "=" + itemName(event.packet.getStack()) + " rev=" + event.packet.getRevision());
         }

         if (slot == 6 || slot == 36 + this.hotbarSlot) {
            this.enforceLocalView(ReviveClient.mc.player.age, "slot update (" + slot + ")");
         }
      }
   }

   @EventHandler
   private void onPlayerInventorySet(SetPlayerInventoryEvent event) {
      if (ReviveClient.mc.player != null) {
         if (event.packet != null && this.tracing()) {
            this.trace("< " + event.packet);
         }

         this.enforceLocalView(ReviveClient.mc.player.age, "player-inventory set");
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (ReviveClient.mc.player != null) {
         if (event.packet instanceof CommonPingS2CPacket ping) {
            if (this.holdPongs) {
               this.heldPongs.add(ping.getParameter());
               this.lastHeldPingId = ping.getParameter();
               event.setCancelled(true);
            }
         } else if (event.packet instanceof ChunkDataS2CPacket chunk) {
            if (this.holdPongs) {
               this.heldChunks.put(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()), this.lastHeldPingId);
            }
         } else if (event.packet instanceof EntitySpawnS2CPacket spawn) {
            if (mode() == ElytraDurabilitySaver.Mode.Flash
               && spawn.getEntityType() == EntityType.FIREWORK_ROCKET
               && ReviveClient.mc.player != null
               && ReviveClient.mc.player.squaredDistanceTo(spawn.getX(), spawn.getY(), spawn.getZ()) < 25.0) {
               this.burnUntilTick = Math.max(this.burnUntilTick, ReviveClient.mc.player.age + 30);
               this.trace("< rocket spawned at us");
            }
         } else if (!(event.packet instanceof EntityTrackerUpdateS2CPacket tracker)) {
            if (event.packet instanceof EntityVelocityUpdateS2CPacket vel) {
               if (vel.getEntityId() == ReviveClient.mc.player.getId() && this.keepsVelocityThroughSetbacks()) {
                  this.velocityPacketsPending.incrementAndGet();
                  if (vel.getVelocity().y > 0.05) {
                     this.velocityPassed++;
                  } else {
                     this.velocityDropped++;
                     event.setCancelled(true);
                  }
               }
            } else if (event.packet instanceof PlaySoundS2CPacket packet && packet.getSound() != null) {
               if (this.guardActive(ReviveClient.mc.player.age)) {
                  String id = ((SoundEvent)packet.getSound().value()).id().getPath();
                  if (id.startsWith("item.armor.equip")) {
                     double dx = packet.getX() - ReviveClient.mc.player.getX();
                     double dy = packet.getY() - ReviveClient.mc.player.getY();
                     double dz = packet.getZ() - ReviveClient.mc.player.getZ();
                     if (!(dx * dx + dy * dy + dz * dz > 576.0)) {
                        event.setCancelled(true);
                     }
                  }
               }
            }
         } else if (this.holdPongs && tracker.id() == ReviveClient.mc.player.getId()) {
            int pingx = this.lastHeldPingId;
            if (pingx != Integer.MIN_VALUE) {
               for (SerializedEntry<?> entry : tracker.trackedValues()) {
                  if (entry.id() == 0 && entry.value() instanceof Byte flags && (flags & 128) == 0) {
                     this.clearPingIds.add(pingx);
                     break;
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onAttributes(EntityAttributesEvent event) {
      if (ReviveClient.mc.player != null && event.packet != null && event.packet.getEntityId() == ReviveClient.mc.player.getId()) {
         if (this.guardActive(ReviveClient.mc.player.age) && !this.plateStack.isEmpty()) {
            AttributeModifiersComponent mods = (AttributeModifiersComponent)this.plateStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (mods != null) {
               mods.applyModifiers(EquipmentSlot.CHEST, (attribute, modifier) -> {
                  EntityAttributeInstance inst = ReviveClient.mc.player.getAttributeInstance(attribute);
                  if (inst != null && inst.hasModifier(modifier.id())) {
                     inst.removeModifier(modifier.id());
                  }
               });
            }
         }
      }
   }

   private static boolean playerInventoryOnly() {
      return ReviveClient.mc.player.currentScreenHandler == ReviveClient.mc.player.playerScreenHandler;
   }

   @EventHandler(
      priority = -200
   )
   private void onTickPost(TickEvent.Post event) {
      this.lastTickVelocity = ReviveClient.mc.player != null && this.keepsVelocityThroughSetbacks() ? ReviveClient.mc.player.getVelocity() : null;
   }

   public void noteVelocityKeptByHook() {
      this.velocityKeptHook++;
   }

   public void notePositionKeptByHook() {
      this.positionKeptHook++;
   }

   @EventHandler
   private void onTeleport(PacketEvent.Receive event) {
      if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
         if (this.holdPongs) {
            this.heldPongs.add(Integer.MIN_VALUE);
         }

         double moved = 0.0;

         try {
            ClientPlayerEntity player = ReviveClient.mc.player;
            if (player != null) {
               Vec3d pos = packet.change().position();
               Set<PositionFlag> flags = packet.relatives();
               double dx = flags.contains(PositionFlag.X) ? pos.x : pos.x - player.getX();
               double dy = flags.contains(PositionFlag.Y) ? pos.y : pos.y - player.getY();
               double dz = flags.contains(PositionFlag.Z) ? pos.z : pos.z - player.getZ();
               moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
               this.teleportDyPending += dy;
               this.teleportHorizPending = this.teleportHorizPending + Math.sqrt(dx * dx + dz * dz);
            }
         } catch (Throwable var14) {
         }

         if (moved > this.teleportMaxPending) {
            this.teleportMaxPending = moved;
         }

         this.lastServerTeleportNanos = System.nanoTime();
         if (this.lastFlashNanos != 0L) {
            this.lastSetbackAfterFlashMs = (System.nanoTime() - this.lastFlashNanos) / 1000000.0;
         }

         if (this.tracing()) {
            this.trace(String.format("< teleport %.2f", moved));
         }

         this.teleportsPending.incrementAndGet();
      }
   }

   private static double serverSinkPerTick(int closedTicks) {
      return 3.92 * (1.0 - Math.pow(0.98, Math.max(0, closedTicks)));
   }

   private static double groundClearance() {
      Box box = ReviveClient.mc.player.getBoundingBox();
      double feet = box.minY;
      double best = 16.0;
      Vec3d v = ReviveClient.mc.player.getVelocity();
      double[][] columns = new double[][]{
         {box.minX + 0.01, box.minZ + 0.01},
         {box.maxX - 0.01, box.minZ + 0.01},
         {box.minX + 0.01, box.maxZ - 0.01},
         {box.maxX - 0.01, box.maxZ - 0.01},
         {(box.minX + box.maxX) / 2.0, (box.minZ + box.maxZ) / 2.0},
         {box.minX + 0.01 + v.x, box.minZ + 0.01 + v.z},
         {box.maxX - 0.01 + v.x, box.minZ + 0.01 + v.z},
         {box.minX + 0.01 + v.x, box.maxZ - 0.01 + v.z},
         {box.maxX - 0.01 + v.x, box.maxZ - 0.01 + v.z}
      };
      Mutable pos = new Mutable();

      for (double[] c : columns) {
         int bx = (int)Math.floor(c[0]);
         int bz = (int)Math.floor(c[1]);

         for (int by = (int)Math.floor(feet); by >= (int)Math.floor(feet - 16.0); by--) {
            pos.set(bx, by, bz);
            VoxelShape shape = ReviveClient.mc.world.getBlockState(pos).getCollisionShape(ReviveClient.mc.world, pos);
            if (!shape.isEmpty()) {
               double top = by + shape.getMax(Axis.Y);
               double d = feet - top;
               if (d < 0.0) {
                  d = 0.0;
               }

               if (d < best) {
                  best = d;
               }
               break;
            }
         }
      }

      return best;
   }

   private static int findHotbarElytra() {
      for (int i = 0; i < 9; i++) {
         if (ReviveClient.mc.player.getInventory().getStack(i).isOf(Items.ELYTRA)) {
            return i;
         }
      }

      return -1;
   }

   private void hardReset() {
      this.reset();
      if (this.baritoneSetbackDelaySaved >= 0) {
         BaritoneBridge.setElytraSetbackDelay(this.baritoneSetbackDelaySaved);
         this.baritoneSetbackDelaySaved = -1;
      }

      this.foreignClickPending = false;
      this.lastForeignClickTick = Integer.MIN_VALUE;
      this.hotbarSlot = -1;
      this.plateStack = ItemStack.EMPTY;
      this.elytraStack = ItemStack.EMPTY;
      this.viewGuardUntilTick = Integer.MIN_VALUE;
      this.holdUntilTick = Integer.MIN_VALUE;
      this.closedSinceTick = Integer.MIN_VALUE;
      this.teleportsPending.set(0);
      this.teleportMaxPending = 0.0;
      this.teleportDyPending = 0.0;
      this.teleportHorizPending = 0.0;
      this.nudgesClosed = 0;
      this.nudgesOpen = 0;
      this.nudgeMax = 0.0;
      this.nudgeDy = 0.0;
      this.nudgeHoriz = 0.0;
      this.nudgeDragPerTick = 0.1;
      this.flashedTick = Integer.MIN_VALUE;
      this.notGlidingTicks = 0;
      this.flashes = 0;
      this.foreignClicks = 0;
      this.startsConverted = 0;
      this.flashesSkipped = 0;
      this.folds = 0;
      this.stalls = 0;
      this.lastFlashReportTick = Integer.MIN_VALUE;
      this.flashing = false;
      this.flashDue = false;
      this.swapOutPending = false;
      this.engageDeadline = Integer.MIN_VALUE;
      this.disengagePendingWhy = null;
      this.clearAppliedTick = Integer.MIN_VALUE;
      this.stopHoldingPongs();
      this.gapCut = false;
      this.chunkCuts = 0;
      this.velocitiesKept = 0;
      this.burnUntilTick = Integer.MIN_VALUE;
      this.trackedRockets.clear();
      this.burnTraceDumped = false;
      this.lastMode = null;
      this.idleSinceTick = Integer.MIN_VALUE;
      this.lastIdleReportTick = Integer.MIN_VALUE;
      this.lastLateGateReportTick = Integer.MIN_VALUE;
      this.lastIdleGlideTick = Integer.MIN_VALUE;
      this.unrequestedGlideTicks = 0;
      this.hopAirborneTicks = 0;
      this.hopSuppressGlideTick = Integer.MIN_VALUE;
      this.hopGroundTicks = 0;
      this.hopCommandThisHop = false;
      this.lastHopEngageTick = Integer.MIN_VALUE;
      this.sprintGuardUntilTick = Integer.MIN_VALUE;
      this.recentNudges = 0;
      this.recentNudgeWindowStart = Integer.MIN_VALUE;
      this.startsRefused = 0;
      this.refusedLogged = 0;
      this.trace.clear();
      this.traceSize.set(0);
      this.flashSetbacks = 0;
      this.flashSetbacksLogged = 0;
      this.flashResyncsLogged = 0;
      this.flashUnexpectedClearsLogged = 0;
      this.flashFullSyncs = 0;
      this.flashCooldownUntil = Integer.MIN_VALUE;
      this.lastFlashNanos = 0L;
      this.lastSetbackAfterFlashMs = -1.0;
      this.lastTickVelocity = null;
      this.velocityKeptHook = 0;
      this.velocityKeptTick = 0;
      this.positionKeptHook = 0;
      this.velocityPacketsPending.set(0);
      this.velocityDropped = 0;
      this.velocityPassed = 0;
      this.backoffCyclesLeft = 0;
      this.joinHintShown = false;
      this.resumeDeadline = Integer.MIN_VALUE;
      this.ownInput = false;
      this.wantJump = false;
      this.serverInput = null;
      this.inputSentThisTick = false;
      this.inputResyncOwed = false;
      this.wrapJumpOwed = false;
      this.rocketsWrapped = 0;
      this.pendingWrapUse = null;
      this.pendingWrapUntilTick = Integer.MIN_VALUE;
      this.burnUseAfterResume = null;
      this.burnUseUntilTick = Integer.MIN_VALUE;
      this.wrapGraceUntilTick = Integer.MIN_VALUE;
      this.serverSprinting = false;
      this.clientSprintSent = false;
      this.sendingOwnPacket = false;
      this.serverGliding = false;
      this.awaitingClear = false;
      this.heldPongs.clear();
      this.clearPingIds.clear();
      this.lastHeldPingId = Integer.MIN_VALUE;
      this.pongsHeldMax = 0;
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.hardReset();
   }

   private static String cannotGlideReason() {
      if (ReviveClient.mc.player.isOnGround()) {
         return "on ground (y " + String.format("%.1f", ReviveClient.mc.player.getY()) + ")";
      } else if (ReviveClient.mc.player.isTouchingWater()) {
         return "in water";
      } else if (ReviveClient.mc.player.isInLava()) {
         return "in lava";
      } else if (ReviveClient.mc.player.hasVehicle()) {
         return "in a vehicle";
      } else if (ReviveClient.mc.player.isClimbing()) {
         return "climbing";
      } else {
         ItemStack chest = ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST);
         if (!chest.isOf(Items.ELYTRA)) {
            return "chest slot holds " + chest.getItem().getName().getString() + " locally";
         } else {
            return chest.getMaxDamage() - chest.getDamage() <= 1 ? "elytra is about to break" : "unknown";
         }
      }
   }

   private static boolean localCanGlide() {
      if (!ReviveClient.mc.player.isOnGround()
         && !ReviveClient.mc.player.isTouchingWater()
         && !ReviveClient.mc.player.isInLava()
         && !ReviveClient.mc.player.hasVehicle()
         && !ReviveClient.mc.player.isClimbing()) {
         ItemStack chest = ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST);
         return chest.isOf(Items.ELYTRA) && chest.getMaxDamage() - chest.getDamage() > 1;
      } else {
         return false;
      }
   }

   private static int findHotbarChestplate() {
      for (int i = 0; i < 9; i++) {
         ItemStack s = ReviveClient.mc.player.getInventory().getStack(i);
         if (!s.isEmpty() && !s.isOf(Items.ELYTRA)) {
            EquippableComponent eq = (EquippableComponent)s.get(DataComponentTypes.EQUIPPABLE);
            if (eq != null && eq.slot() == EquipmentSlot.CHEST) {
               return i;
            }
         }
      }

      return -1;
   }

   private static void dbg(String msg) {
      if (AntiCheatConfig.get().swapDebug.get()) {
         ChatUtils.infoPrefix("ElytraSaver", "%s", msg);
      }
   }

   private boolean tracing() {
      return mode() == ElytraDurabilitySaver.Mode.Flash && AntiCheatConfig.get().swapDebug.get();
   }

   private void trace(String msg) {
      if (this.tracing()) {
         ClientPlayerEntity player = ReviveClient.mc.player;
         this.trace.add((player == null ? "?" : Integer.toString(player.age)) + " " + msg);
         if (this.traceSize.incrementAndGet() > 60) {
            this.trace.poll();
            this.traceSize.decrementAndGet();
         }
      }
   }

   private static String velocityShapers() {
      RocketBoost boost = Modules.get().get(RocketBoost.class);
      return boost != null && boost.isActive()
         ? String.format(
            "; RocketBoost is on (window %.2f%s) and sets the burn velocity itself", boost.windowThreshold(), boost.hasWindow() ? ", riding a rocket now" : ""
         )
         : "";
   }

   private void dumpTrace(String why) {
      if (AntiCheatConfig.get().swapDebug.get()) {
         dbg("flash trace (" + why + "): " + String.join(" | ", this.trace));
      }
   }

   public static enum Click {
      Pickup,
      Swap;
   }

   public static enum Mode {
      Flash,
      Windows;

      @Override
      public String toString() {
         return this == Flash ? "Flash (2b2t chestplate fake-fly)" : "Windows";
      }
   }

   private static enum Phase {
      IDLE,
      SWAPPED_OUT;
   }

   private record Requester(BooleanSupplier active, BooleanSupplier wants) {
   }

   public static enum Setbacks {
      KeepVelocity,
      KeepVelocityAndPosition;

      @Override
      public String toString() {
         return this == KeepVelocity ? "Keep velocity" : "Keep velocity and position";
      }
   }
}
