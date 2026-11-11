package revivedevelopment.reviveclient.systems.modules.combat.autocrystal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.EntityAddedEvent;
import revivedevelopment.reviveclient.events.entity.PlayerDeathEvent;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.revive.SilentMineFinishedEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.pingbypass.PbFx;
import revivedevelopment.reviveclient.pingbypass.PbState;
import revivedevelopment.reviveclient.pingbypass.PbVia;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.managers.SwapManager;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.AutoCrystal;
import revivedevelopment.reviveclient.systems.modules.combat.AutoMine;
import revivedevelopment.reviveclient.systems.modules.combat.Offhand;
import revivedevelopment.reviveclient.systems.modules.combat.Surround;
import revivedevelopment.reviveclient.systems.modules.player.PacketLimiter;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.systems.modules.render.BreakIndicators;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.NametagUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

public class AutoCrystalFeature {
   private static final double DEFAULT_AIM_PRIORITY = 1200.0;
   private static final int OFFHAND_PACKET_RESERVE = 2;
   private static final int SURROUND_INTERACT_RESERVE = 2;
   private static final int ATTACK_PACKET_RESERVE = 2;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final double CEV_HEAD_RANGE = 5.0;
   private static final long DELAY_ENTRY_TTL_MS = 5000L;
   private static final float LASER_SCORE = 0.001F;
   private static final int QUEUED_PLACE_TTL_TICKS = 3;
   private static final double CRYSTAL_SCAN_RANGE_SQ = 144.0;
   private final ExecutorService calcExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "auto-crystal-calc");
      thread.setDaemon(true);
      return thread;
   });
   private Future<?> runningTask;
   private final AtomicReference<AutoCrystalFeature.CalcResult> asyncResult = new AtomicReference<>();
   private final Set<UUID> recentlyDeadPlayers = ConcurrentHashMap.newKeySet();
   private AutoCrystalFeature.PlaceTarget bestPlace;
   private AutoCrystalFeature.PlaceTarget lastPlaceTarget;
   private float lastTotalDamage;
   private long lastPlaceMs;
   private long lastBreakMs;
   private final Map<Long, Long> placeAttemptMs = new HashMap<>();
   private final Map<Integer, Long> breakAttemptMs = new ConcurrentHashMap<>();
   private final Counter cpsCounter = new Counter();
   private final Counter placeCounter = new Counter();
   private static final int NO_CRYSTAL_BACKOFF_TICKS = 10;
   private int placeBackoffTicks;
   private long clientTick;
   private long cacheEpoch;
   private long flushId;
   private long lastAttackFlushTick = Long.MIN_VALUE;
   private Vec3d lastAttackPos;
   private long lastAttackNs;
   private static final double OWN_DROP_BLAST_RANGE_SQ = 64.0;
   private int lastCrystalHotbarSlot = -1;
   private static final int RESTOCK_COOLDOWN_TICKS = 5;
   private int restockCooldown;
   private int borrowedHotbarSlot = -1;
   private int borrowedReturnInvSlot = -1;
   private ItemStack borrowedOriginalStack = ItemStack.EMPTY;
   private static final int BORROW_RESTORE_IDLE_TICKS = 20;
   private long lastCombatActivityTick = Long.MIN_VALUE;
   private final Map<String, AutoCrystalFeature.DbgState> dbgStates = new HashMap<>();
   private static final long DBG_REPEAT_MS = 5000L;
   private final Map<String, String> dbgSigs = new HashMap<>();
   private BlockPos passthroughPrimary;
   private String lastCalcDbg;
   private boolean aimHeld;
   private Vec3d placeDelayHold;
   private boolean aimOwned;
   private float aimYaw;
   private float aimPitch;
   private static final int STOP_STARVE_TICKS = 3;
   private long aimYieldTick = Long.MIN_VALUE;
   private boolean aimYielded;
   private int yieldDeferCount;
   private BlockPos yieldPos;
   private BlockPos futileYieldPos;
   private long futileYieldTick;
   private static final int FUTILE_YIELD_HOLD_TICKS = 20;
   private BlockPos lastPickPos;
   private long lastPickTick = Long.MIN_VALUE;
   private static final int EXPOSED_PICK_TICKS = 2;
   private AutoCrystalFeature.StagedPlace stagedPlace;
   private EndCrystalEntity pendingBreak;
   private float pendingBreakDamage;
   private int scanIgnoreCrystalId = Integer.MIN_VALUE;
   private AutoCrystalFeature.QueuedPlace queuedPlace;
   private static final int UNCONFIRMED_PLACE_LIMIT = 6;
   private static final int UNCONFIRMED_PLACE_BACKOFF_TICKS = 10;
   private static final int UNCONFIRMED_PLACE_SPOTS_MAX = 64;
   private final Map<Long, Integer> unconfirmedPlaces = new HashMap<>();
   private ClientWorld unconfirmedWorld;
   private final Map<Long, Long> spotBackoffUntil = new HashMap<>();
   private BlockPos asPos;
   private long asStopTick;
   private String asStop;
   private int asReplaceAgo;
   private long asPlaceTick;
   private int asPlaces;
   private long asSealTick = -1L;
   private long asOpenTick = -1L;
   private boolean asNoGlass;
   private int lastXItemLogged = Integer.MIN_VALUE;
   private static final double COMBO_LOOK_REACH = 4.5;
   private static final int COMBO_MAX_WAIT_TICKS = 10;
   private static final int COMBO_FALLBACK_TICKS = 20;
   private static final double SEAL_BLAST_RANGE_SQ = 16.0;
   private static final long SETBACK_HOLD_NS = 100000000L;
   private AutoCrystalFeature.ComboPlan comboPlan;
   private AutoCrystalFeature.StagedCombo stagedCombo;
   private static final double CRYSTAL_HURT_RANGE_SQ = 144.0;
   private BlockPos sealPlannedX;
   private AutoCrystalFeature.FollowUp followUp;
   private BlockPos comboWaitX;
   private int comboWaitTicks;
   private BlockPos comboFallbackX;
   private long comboFallbackUntil = Long.MIN_VALUE;
   private BlockPos comboLastX;
   private long comboLastTick = Long.MIN_VALUE;
   private BlockPos answerX;
   private AutoCrystalFeature.ComboKind answerKind;
   private long answerMs;
   private boolean answerSameFlush;
   private boolean openOrderSwitched;
   private int orderRefusals;
   private static final int ORDER_SWITCH_REFUSALS = 3;
   private BlockPos lateX;
   private AutoCrystalFeature.ComboKind lateKind;
   private long lateMs;
   private boolean lateSwitched;
   private static final long LATE_ANSWER_MS = 1000L;
   private long answerEchoMs;
   private final Map<Long, Long> stopSentTicks = new HashMap<>();
   private volatile long lastTeleportNs;
   private boolean comboStopping;
   private static final int BASE_PLACE_MIN_COOLDOWN = 3;
   private int basePlaceTimer;
   private BlockPos lastBasePlacePos;
   private static final int BASE_SCAN_IDLE_TICKS = 4;
   private long baseScanIdleUntilTick = Long.MIN_VALUE;
   private long baseScanIdlePos = Long.MIN_VALUE;
   private long enemiesTick = Long.MIN_VALUE;
   private List<LivingEntity> enemiesCache = List.of();
   private long ctxTick = Long.MIN_VALUE;
   private AutoCrystalSnapshot.Context ctxCache;
   private long ignoredCacheTick = Long.MIN_VALUE;
   private long ignoredCachePos = Long.MIN_VALUE;
   private Set<BlockPos> ignoredCache;
   private static final int TERRAIN_CACHE_TICKS = 5;
   private long terrainCacheTick = Long.MIN_VALUE;
   private long terrainCachePos = Long.MIN_VALUE;
   private Set<BlockPos> terrainCache;
   private long crystalsCacheTick = Long.MIN_VALUE;
   private List<EndCrystalEntity> crystalsCache = List.of();
   private final Map<BlockPos, double[]> trackedRates = new HashMap<>();
   private BlockPos renderPos;
   private Vec3d renderVec;
   private float renderDmg;
   private long renderLastSeenMs;
   private static final long SLIDE_HOLD_MS = 250L;
   private final Map<BlockPos, Long> placeRenders = new LinkedHashMap<BlockPos, Long>() {
      @Override
      protected boolean removeEldestEntry(Entry<BlockPos, Long> eldest) {
         return this.size() > 64;
      }
   };
   private final ArrayDeque<AutoCrystalFeature.BreakRender> breakRenders = new ArrayDeque<>();
   private BlockPos simpleRenderBase;
   private float simpleRenderDmg;
   private long simpleRenderMs;
   private volatile BlockPos[] debugSpots = new BlockPos[0];
   private static final int PROTOCOL_1_21_1 = 767;

   public void onEnable() {
      this.lastPlaceMs = 0L;
      this.lastBreakMs = 0L;
      this.placeBackoffTicks = 0;
      this.pendingBreak = null;
      this.aimHeld = false;
      this.aimOwned = false;
      this.stagedPlace = null;
      this.queuedPlace = null;
      this.asyncResult.set(null);
      this.bestPlace = null;
      this.placeAttemptMs.clear();
      this.breakAttemptMs.clear();
      this.aimYieldTick = Long.MIN_VALUE;
      this.aimYielded = false;
      this.yieldDeferCount = 0;
      this.yieldPos = null;
      this.futileYieldPos = null;
      this.lastPickPos = null;
      this.lastPickTick = Long.MIN_VALUE;
      this.renderPos = null;
      this.renderVec = null;
      this.renderDmg = 0.0F;
      this.renderLastSeenMs = 0L;
      this.placeRenders.clear();
      this.breakRenders.clear();
      this.simpleRenderBase = null;
      this.debugSpots = new BlockPos[0];
      this.lastTotalDamage = 0.0F;
      this.lastPlaceTarget = null;
      this.lastCrystalHotbarSlot = -1;
      this.restockCooldown = 0;
      this.clearBorrowRecord();
      this.dbgStates.clear();
      this.dbgSigs.clear();
      this.passthroughPrimary = null;
      this.lastCalcDbg = null;
      this.cpsCounter.reset();
      this.placeCounter.reset();
      this.lastCombatActivityTick = Long.MIN_VALUE;
      this.unconfirmedPlaces.clear();
      this.unconfirmedWorld = null;
      this.spotBackoffUntil.clear();
      this.lastAttackFlushTick = Long.MIN_VALUE;
      this.lastAttackPos = null;
      this.asPos = null;
      this.lastXItemLogged = Integer.MIN_VALUE;
      this.comboPlan = null;
      this.stagedCombo = null;
      this.sealPlannedX = null;
      this.followUp = null;
      this.comboWaitX = null;
      this.comboFallbackX = null;
      this.comboLastX = null;
      this.answerX = null;
      this.openOrderSwitched = false;
      this.orderRefusals = 0;
      this.lateX = null;
      this.answerEchoMs = 0L;
      this.stopSentTicks.clear();
      this.lastTeleportNs = 0L;
      this.basePlaceTimer = 0;
      this.lastBasePlacePos = null;
      this.baseScanIdleUntilTick = this.baseScanIdlePos = Long.MIN_VALUE;
      this.recentlyDeadPlayers.clear();
      this.trackedRates.clear();
      this.enemiesTick = this.ctxTick = this.ignoredCacheTick = this.crystalsCacheTick = this.terrainCacheTick = Long.MIN_VALUE;
      this.terrainCache = null;
      this.ignoredCache = null;
   }

   public void onDisable() {
      this.enemiesCache = List.of();
      this.crystalsCache = List.of();
      AutoCrystal cfg = AutoCrystal.get();
      if (cfg != null
         && this.borrowedHotbarSlot != -1
         && ReviveClient.mc.player != null
         && ReviveClient.mc.player.currentScreenHandler == ReviveClient.mc.player.playerScreenHandler
         && !ReviveClient.SWAP.isSilentSwapActive()
         && !ReviveClient.SWAP.isMultiTickSwapActive()
         && this.borrowedHotbarSlot != ReviveClient.mc.player.getInventory().getSelectedSlot()
         && ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
         this.restoreBorrowedSlotNow(cfg, "module disabled");
      }

      this.clearBorrowRecord();
      this.lastPlaceTarget = null;
      this.bestPlace = null;
      this.queuedPlace = null;
      this.pendingBreak = null;
      this.aimYieldTick = Long.MIN_VALUE;
      this.yieldPos = null;
      this.futileYieldPos = null;
      this.lastPickPos = null;
      this.stagedPlace = null;
      this.comboPlan = null;
      this.stagedCombo = null;
      this.followUp = null;
      this.lastTotalDamage = 0.0F;
      this.unconfirmedPlaces.clear();
      this.recentlyDeadPlayers.clear();
   }

   public String getInfoString() {
      int cps = this.cpsCounter.getPerSecond();
      AutoCrystal cfg = AutoCrystal.get();
      if (cfg != null && cfg.fakeCps.get()) {
         cps += 8;
      }

      return String.format(Locale.US, "%.2f, %d CPS", this.lastTotalDamage, cps);
   }

   public int getBreaksPerSecond() {
      return this.cpsCounter.getPerSecond();
   }

   public int getPlacesPerSecond() {
      return this.placeCounter.getPerSecond();
   }

   private void markCombatActivity() {
      this.lastCombatActivityTick = this.clientTick;
   }

   private void dbg(AutoCrystal cfg, String msg) {
      if (cfg != null && cfg.debug.get()) {
         cfg.featureInfo("ac", msg);
      }
   }

   private static boolean dbgOn(AutoCrystal cfg) {
      return cfg != null && cfg.debug.get();
   }

   private void dbgState(AutoCrystal cfg, String key, String msg) {
      if (cfg != null && cfg.debug.get()) {
         long now = System.currentTimeMillis();
         AutoCrystalFeature.DbgState st = this.dbgStates.get(key);
         if (st != null && st.msg.equals(msg)) {
            st.repeats++;
            if (now - st.printedMs >= 5000L) {
               cfg.featureInfo("ac", msg + " (still - x" + st.repeats + " more over " + (now - st.printedMs) / 1000L + "s)");
               st.printedMs = now;
               st.repeats = 0;
            }
         } else {
            if (this.dbgStates.size() > 256) {
               this.dbgStates.values().removeIf(s -> now - s.printedMs > 5000L);
            }

            this.dbgStates.put(key, new AutoCrystalFeature.DbgState(msg, now));
            cfg.featureInfo("ac", msg);
         }
      }
   }

   private void dbgClear(String key) {
      this.dbgStates.remove(key);
   }

   private void dbgOnChange(AutoCrystal cfg, String key, String sig, String msg) {
      if (cfg != null && cfg.debug.get()) {
         if (!sig.equals(this.dbgSigs.get(key))) {
            if (this.dbgSigs.size() > 64) {
               this.dbgSigs.clear();
            }

            this.dbgSigs.put(key, sig);
            cfg.featureInfo("ac", msg);
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   public void onPlayerDeath(PlayerDeathEvent.Death event) {
      PlayerEntity p = event.getPlayer();
      if (p != null && p != ReviveClient.mc.player) {
         this.recentlyDeadPlayers.add(p.getUuid());
         AutoCrystal cfg = AutoCrystal.get();
         if (cfg != null && cfg.setPlayerDead.get()) {
            p.setHealth(0.0F);
         }
      }
   }

   private boolean isMarkedDead(PlayerEntity p) {
      if (p == null) {
         return false;
      } else {
         UUID id = p.getUuid();
         if (!this.recentlyDeadPlayers.contains(id)) {
            return false;
         } else if (p.getHealth() > 0.0F && !p.isDead()) {
            this.recentlyDeadPlayers.remove(id);
            return false;
         } else {
            return true;
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   public void onTickStart(TickEvent.Pre event) {
      this.clientTick++;
      this.cacheEpoch++;
   }

   @EventHandler(
      priority = 200
   )
   public void onTickEnd(TickEvent.Post event) {
      this.cacheEpoch++;
   }

   @EventHandler(
      priority = 100
   )
   public void onTick(TickEvent.Pre event) {
      if (ReviveClient.mc.player != null && ReviveClient.mc.world != null && !ReviveClient.mc.player.isDead()) {
         AutoCrystal cfg = AutoCrystal.get();
         if (cfg != null) {
            if (this.restockCooldown > 0) {
               this.restockCooldown--;
            }

            if (this.basePlaceTimer > 0) {
               this.basePlaceTimer--;
            }

            if (this.placeBackoffTicks > 0) {
               this.placeBackoffTicks--;
            }

            this.update();
            long tick = this.clientTick;
            if (this.queuedPlace != null && tick > this.queuedPlace.expiresTick()) {
               this.queuedPlace = null;
            }

            this.stagedPlace = null;
            this.stagedCombo = null;
            this.comboPlan = null;
            this.sealPlannedX = null;
            this.checkComboAnswer(cfg);
            boolean using = ReviveClient.mc.player.isUsingItem();
            boolean canBreakNow = cfg.breakCrystals.get() && (!cfg.pauseEatBreak.get() || !using);
            boolean canPlaceNow = cfg.placeCrystals.get() && (!cfg.pauseEatPlace.get() || !using);
            if (this.enemies(cfg).isEmpty()) {
               this.asyncResult.set(null);
               this.bestPlace = null;
               this.queuedPlace = null;
               this.followUp = null;
            } else {
               if (canPlaceNow && this.placeBackoffTicks <= 0) {
                  this.comboPlan = this.planCombo(cfg);
               } else {
                  this.followUp = null;
               }

               this.bestPlace = null;
               AutoCrystalFeature.CalcResult calc = canPlaceNow ? this.asyncResult.get() : null;
               if (calc != null && calc.best() != null) {
                  BlockPos pos = calc.best().pos();
                  this.lastPickPos = pos;
                  this.lastPickTick = this.clientTick;
                  float live = this.isPlaceable(pos, cfg, true, true) && !this.baseBreaksFirst(pos, cfg) ? this.placeDamage(pos, cfg, null, null) : -1.0F;
                  if (live > 0.0F) {
                     this.bestPlace = new AutoCrystalFeature.PlaceTarget(pos, live);
                     this.lastTotalDamage = live;
                     this.markCombatActivity();
                     if (cfg.debug.get()) {
                        this.dbgBestPlace(cfg, pos, live);
                     }
                  } else if (dbgOn(cfg)) {
                     this.dbgState(cfg, "reval", "revalidate REJECT: worker best " + pos.toShortString() + " no longer placeable / damaging");
                  }
               }

               this.pendingBreak = null;
               if (canBreakNow) {
                  this.doBreak(cfg);
               } else if (cfg.breakCrystals.get()) {
                  this.dbgState(cfg, "break", "break paused: using item (pause-eat-break)");
               }

               if (canPlaceNow && (this.runningTask == null || this.runningTask.isDone())) {
                  AutoCrystalSnapshot snap = this.doSnapshot(tick, cfg, this.pendingBreak);
                  AutoCrystalSnapshot.DebugInfo info = new AutoCrystalSnapshot.DebugInfo(tick);
                  float rebreakThreshold = cfg.rebreakDmgThreshold.get().floatValue();
                  boolean debug = cfg.debug.get();
                  this.runningTask = this.calcExecutor.submit(() -> {
                     try {
                        this.asyncResult.set(findBestPlace(snap, info, rebreakThreshold));
                        if (debug) {
                           float ms = (float)(System.nanoTime() - info.startNs) / 1000000.0F;
                           ReviveClient.mc.execute(() -> {
                              String summary = info.summary();
                              if (!summary.equals(this.lastCalcDbg)) {
                                 this.lastCalcDbg = summary;
                                 cfg.featureInfo("ac-debug", info.buildMessage(ms));
                              }
                           });
                        }
                     } catch (Throwable var7x) {
                        this.asyncResult.set(null);
                     }
                  });
               }

               if (cfg.autoRestockHotbar.get() && !using) {
                  this.maybeRestockHotbar(cfg);
               }

               this.maybeRestoreBorrowedSlot(cfg);
               if (this.placeBackoffTicks <= 0 && !this.hasAnyCrystal()) {
                  this.placeBackoffTicks = 10;
                  if (dbgOn(cfg)) {
                     this.dbgState(cfg, "place", "place skip: no crystals anywhere - backing off 10t");
                  }
               }

               if (!canPlaceNow) {
                  if (cfg.placeCrystals.get()) {
                     this.dbgState(cfg, "place", "place paused: using item (pause-eat-place)");
                  }
               } else {
                  this.placeDelayHold = null;
                  boolean acted;
                  if (this.placeBackoffTicks > 0) {
                     acted = true;
                  } else if (this.tryCombo(cfg)) {
                     acted = true;
                  } else if (!this.placeSpeedOk(cfg)) {
                     acted = true;
                  } else {
                     acted = this.tryQueuedPlace(cfg)
                        || cfg.cevHead.get() && this.tryCevHead(cfg)
                        || this.tryPrimaryTopPlace(cfg)
                        || this.tryPlaceSilentMineRebreak(cfg)
                        || this.bestPlace != null && this.doPlace(this.bestPlace, cfg, false, "place", false)
                        || cfg.shorelineMode.get() && cfg.laser.get() && this.tryShorelinePlace(cfg)
                        || cfg.predictBlock.get() && this.tryPredictBlockPlace(cfg);
                     if (!acted && this.bestPlace == null) {
                        this.dbgState(cfg, "place", "place: no bestPlace this tick");
                     }
                  }

                  if (cfg.basePlace.get() && !acted && this.basePlaceTimer <= 0) {
                     this.tryBasePlace(cfg, calc);
                  }

                  if (this.placeDelayHold != null) {
                     this.aimAt(this.placeDelayHold);
                  }

                  this.placeDelayHold = null;
               }
            }
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   public void onPacket(PacketEvent.Receive event) {
      if (event.packet instanceof EntitiesDestroyS2CPacket packet) {
         IntListIterator var5 = packet.getEntityIds().iterator();

         while (var5.hasNext()) {
            int id = (Integer)var5.next();
            this.breakAttemptMs.remove(id);
         }
      }

      if (event.packet instanceof PlayerPositionLookS2CPacket) {
         this.lastTeleportNs = System.nanoTime();
      }
   }

   @EventHandler(
      priority = 200
   )
   public void onEntityAdded(EntityAddedEvent event) {
      if (event.entity instanceof EndCrystalEntity crystal) {
         if (ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
            AutoCrystal cfg = AutoCrystal.get();
            if (cfg != null) {
               long posKey = crystal.getBlockPos().asLong();
               this.placeAttemptMs.remove(posKey);
               this.unconfirmedPlaces.remove(posKey);
               this.spotBackoffUntil.remove(posKey);
               if (crystal.getBlockPos().equals(this.answerX) && this.answerKind != AutoCrystalFeature.ComboKind.SEAL) {
                  this.answerEchoMs = System.currentTimeMillis() - this.answerMs;
                  this.dbg(cfg, "spawn at " + this.answerX.toShortString() + " +" + this.answerEchoMs + "ms after combo " + this.answerKind);
                  if (this.answerSameFlush) {
                     this.orderRefusals = 0;
                  }

                  this.answerX = null;
               } else if (crystal.getBlockPos().equals(this.lateX) && this.lateKind != AutoCrystalFeature.ComboKind.SEAL) {
                  this.lateAnswer(cfg, "spawn");
               }

               if (crystal.getBlockPos().equals(this.asPos)) {
                  this.closeCycle(cfg, "crystal");
               }

               if (cfg.breakCrystals.get() && cfg.packetBreak.get()) {
                  if (!cfg.pauseEatBreak.get() || !ReviveClient.mc.player.isUsingItem()) {
                     if (this.breakSpeedOk(cfg)) {
                        if (!this.attackBudgetLow(cfg, "packet-break")) {
                           if (!this.isBreakDelayed(crystal.getId(), cfg)) {
                              if (this.isInBreakRangeNow(crystal, cfg)) {
                                 if (!this.enemies(cfg).isEmpty()) {
                                    if (this.waitsForPrimaryStop(crystal, cfg)) {
                                       this.dbgOnChange(
                                          cfg,
                                          "stop-hold",
                                          crystal.getId() + " pb",
                                          String.format(
                                             Locale.US,
                                             "packet-break hold: id=%d on primary %s - its STOP isn't out (ttb=%.0f)",
                                             crystal.getId(),
                                             crystal.getBlockPos().down().toShortString(),
                                             SilentMine.get().getPrimaryTicksToBreak()
                                          )
                                       );
                                    } else {
                                       String comboHold = this.comboHitHold(crystal);
                                       if (comboHold != null) {
                                          this.dbgOnChange(
                                             cfg,
                                             "combo-hit-hold",
                                             crystal.getId() + " pb " + comboHold,
                                             "packet-break hold: id=" + crystal.getId() + " - " + comboHold
                                          );
                                       } else {
                                          float dmg = this.breakDamage(crystal.getEntityPos(), cfg);
                                          if (!(dmg <= 0.0F) || cfg.laser.get() && this.isSelfSafe(crystal.getEntityPos(), cfg, true)) {
                                             if (cfg.rotateBreak.get()) {
                                                Vec3d point = this.breakAimPoint(crystal, cfg);
                                                if (point == null) {
                                                   return;
                                                }

                                                this.aimAt(point);
                                             }

                                             if (this.canBreak(crystal, cfg)) {
                                                this.dbgClear("packet-break");
                                                this.attackCrystal(crystal, cfg);
                                                this.dbg(
                                                   cfg, String.format(Locale.US, "packet-break ATTACK id=%d dmg=%.1f", crystal.getId(), Math.max(0.0F, dmg))
                                                );
                                                this.packetPlace(crystal, cfg);
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
               }
            }
         }
      }
   }

   @EventHandler
   public void onStopSent(SilentMineFinishedEvent.Sent event) {
      BlockPos pos = event.getBlockPos();
      if (pos != null) {
         if (this.stopSentTicks.size() > 32) {
            this.stopSentTicks.values().removeIf(t -> this.clientTick - t > 100L);
         }

         this.stopSentTicks.put(pos.asLong(), this.clientTick);
         if (pos.equals(this.futileYieldPos)) {
            this.futileYieldPos = null;
         }

         this.dbgSigs.remove("yield-kept");
         AutoCrystal cfg = AutoCrystal.get();
         if (!this.comboStopping && cfg != null && cfg.debug.get() && event.getIsRebreak()) {
            this.openCycle(cfg, pos);
            AutoMine autoMine = AutoMine.get();
            this.asNoGlass = autoMine != null && autoMine.isSealablePos(pos) && this.sealGlass(cfg, autoMine) == null;
         }
      }
   }

   private void openCycle(AutoCrystal cfg, BlockPos pos) {
      if (this.asPos != null) {
         this.closeCycle(cfg, !this.asPos.equals(pos) ? "moved" : (this.asPlaces > 0 ? "refused" : "enemy-won"));
      }

      SilentMine sm = SilentMine.get();
      this.asPos = pos.toImmutable();
      this.asStopTick = this.clientTick;
      this.asStop = sm != null ? sm.describeStop(pos) : null;
      this.asReplaceAgo = sm != null ? (int)sm.getRebreakTicksSinceReplace() : -1;
      this.asPlaces = 0;
      this.asPlaceTick = -1L;
      this.asSealTick = -1L;
      this.asOpenTick = -1L;
      this.asNoGlass = false;
   }

   private void cycleCombo(AutoCrystal cfg, BlockPos x, AutoCrystalFeature.ComboKind kind, boolean stop) {
      if (cfg.debug.get()) {
         if (stop && (kind == AutoCrystalFeature.ComboKind.SEAL || !x.equals(this.asPos))) {
            this.openCycle(cfg, x);
         }

         if (x.equals(this.asPos)) {
            if (stop && kind == AutoCrystalFeature.ComboKind.OPEN) {
               this.asOpenTick = this.clientTick;
            } else if (!stop && this.asSealTick < 0L) {
               this.asSealTick = this.clientTick;
            }
         }
      }
   }

   private void closeCycle(AutoCrystal cfg, String result) {
      if (this.asPos != null) {
         boolean crystal = "crystal".equals(result);
         this.dbg(
            cfg,
            "[AS] "
               + this.asPos.toShortString()
               + " cycle: replace@-"
               + this.asReplaceAgo
               + " stop@0("
               + (this.asStop != null ? this.asStop : "?")
               + ")"
               + (this.asSealTick >= 0L ? " seal@+" + (this.asSealTick - this.asStopTick) : "")
               + (this.asOpenTick >= 0L ? " open@+" + (this.asOpenTick - this.asStopTick) : "")
               + (this.asPlaces > 0 ? " place@+" + (this.asPlaceTick - this.asStopTick) + (this.asPlaces > 1 ? " x" + this.asPlaces : "") : " no-place")
               + (crystal ? " spawn@+" + (this.clientTick - this.asStopTick) : "")
               + " result="
               + (this.asNoGlass && !crystal ? "no-glass" : result)
               + (crystal && this.asPlaces == 0 ? " (none of our places: not ours?)" : "")
         );
         this.asPos = null;
      }
   }

   private void packetPlace(EndCrystalEntity crystal, AutoCrystal cfg) {
      if (cfg.packetPlace.get() && cfg.placeCrystals.get()) {
         if (!PbState.spawnHitsWait) {
            if (this.queuedPlace == null) {
               if (!cfg.pauseEatPlace.get() || !ReviveClient.mc.player.isUsingItem()) {
                  if (this.placeBackoffTicks <= 0) {
                     if (!this.sealFollowUp(crystal, cfg)) {
                        if (this.placeSpeedOk(cfg)) {
                           BlockPos spot = crystal.getBlockPos();
                           boolean planned = this.bestPlace != null && this.bestPlace.pos().equals(spot)
                              || this.lastPlaceTarget != null && this.lastPlaceTarget.pos().equals(spot);
                           if (planned && !this.isPlaceDelayed(spot, cfg) && !this.isSpotBackedOff(spot)) {
                              if (!onSentPrimary(spot)) {
                                 float live = this.isPlaceable(spot, cfg, true, true) ? this.placeDamage(spot, cfg, null, null) : -1.0F;
                                 if (!(live <= 0.0F)) {
                                    BlockPos base = spot.down();
                                    AutoCrystalFeature.PlaceAim aim = this.placeAim(base, cfg);
                                    if (aim != null) {
                                       if (cfg.rotatePlace.get()) {
                                          if (cfg.rotationTiming.get() == AutoCrystal.RotationTiming.Pre) {
                                             Box baseBox = new Box(base);
                                             if (!ReviveClient.ROTATION
                                                   .raytraceCheck(
                                                      ReviveClient.mc.player.getEyePos(),
                                                      ReviveClient.ROTATION.getServerYaw(),
                                                      ReviveClient.ROTATION.getServerPitch(),
                                                      baseBox
                                                   )
                                                || !this.outgoingLooksAt(baseBox)) {
                                                return;
                                             }
                                          } else {
                                             this.aimAt(aim.aimPoint());
                                          }
                                       }

                                       if (this.useItemOn(Items.END_CRYSTAL, aim.hit(), cfg, "packet-place")) {
                                          this.crystalPlaceSent(new AutoCrystalFeature.PlaceTarget(spot, live), cfg, "packet-place", false);
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
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
         AutoCrystal cfg = AutoCrystal.get();
         if (cfg != null) {
            switch ((AutoCrystal.RenderMode)cfg.renderMode.get()) {
               case FADE:
               case SLIDE:
                  this.renderFadeSlide(event, cfg);
                  break;
               case DelayDraw:
                  this.renderDelay(event, cfg);
                  break;
               case Simple:
                  if (this.simpleRenderBase != null && System.currentTimeMillis() - this.simpleRenderMs <= cfg.simpleDrawTime.get() * 1000.0) {
                     SettingColor c = cfg.simpleColor.get();
                     event.renderer.box(this.simpleRenderBase, c, c, cfg.shapeMode.get(), 0);
                  }
                  break;
               case Debug:
                  SettingColor c = cfg.simpleColor.get();

                  for (BlockPos spot : this.debugSpots) {
                     event.renderer.box(spot.down(), c, c, cfg.shapeMode.get(), 0);
                  }
               case NONE:
            }
         }
      }
   }

   private void renderFadeSlide(Render3DEvent event, AutoCrystal cfg) {
      this.updateRenderState(cfg);
      float alpha = this.renderAlpha(cfg);
      if (this.renderVec != null && !(alpha <= 0.0F)) {
         SettingColor base = cfg.renderColor.get();
         Color side = new Color(base.r, base.g, base.b, (int)(cfg.renderFillOpacity.get().intValue() * alpha));
         Color line = new Color(base.r, base.g, base.b, (int)(cfg.renderLineOpacity.get().intValue() * alpha));
         double x = this.renderVec.x;
         double y = this.renderVec.y;
         double z = this.renderVec.z;
         event.renderer.box(x, y, z, x + 1.0, y + 1.0, z + 1.0, side, line, cfg.shapeMode.get(), 0);
      }
   }

   private void renderDelay(Render3DEvent event, AutoCrystal cfg) {
      long now = System.currentTimeMillis();
      double placeFadeMs = cfg.placeDelayFadeTime.get() * 1000.0;
      SettingColor pc = cfg.placeDelayColor.get();
      Iterator<Entry<BlockPos, Long>> it = this.placeRenders.entrySet().iterator();

      while (it.hasNext()) {
         Entry<BlockPos, Long> e = it.next();
         long age = now - e.getValue();
         if (age > placeFadeMs) {
            it.remove();
         } else {
            double alpha = placeFadeMs <= 0.0 ? 0.0 : 1.0 - age / placeFadeMs;
            Color c = new Color(pc.r, pc.g, pc.b, (int)(pc.a * alpha));
            event.renderer.box(e.getKey(), c, c, cfg.placeDelayShapeMode.get(), 0);
         }
      }

      double breakFadeMs = cfg.breakDelayFadeTime.get() * 1000.0;
      SettingColor bc = cfg.breakDelayColor.get();
      this.breakRenders.removeIf(rx -> now - rx.timeMs() > breakFadeMs);

      for (AutoCrystalFeature.BreakRender r : this.breakRenders) {
         double t = breakFadeMs <= 0.0 ? 1.0 : (now - r.timeMs()) / breakFadeMs;
         Color c = new Color(bc.r, bc.g, bc.b, (int)(bc.a * Math.pow(1.0 - t, cfg.breakDelayFadeExponent.get())));
         event.renderer.box(r.box(), c, c, cfg.breakDelayShapeMode.get(), 0);
      }
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
         AutoCrystal cfg = AutoCrystal.get();
         if (cfg != null && cfg.renderDamage.get()) {
            AutoCrystal.RenderMode mode = cfg.renderMode.get();
            if (mode == AutoCrystal.RenderMode.FADE || mode == AutoCrystal.RenderMode.SLIDE) {
               if (this.renderVec != null && !(this.renderAlpha(cfg) <= 0.0F)) {
                  Vector3d screen = new Vector3d(this.renderVec.x + 0.5, this.renderVec.y + 0.5, this.renderVec.z + 0.5);
                  if (NametagUtils.to2D(screen, 1.0)) {
                     String text = String.format(Locale.US, "%.1f", Math.max(0.0F, this.renderDmg));
                     int textWidth = ReviveClient.mc.textRenderer.getWidth(text);
                     NametagUtils.begin(screen, event.drawContext);
                     event.drawContext.drawText(ReviveClient.mc.textRenderer, text, -textWidth / 2, -9 / 2, -1, true);
                     NametagUtils.end(event.drawContext);
                  }
               }
            }
         }
      }
   }

   public JsonObject writeRemoteRender(AutoCrystal cfg) {
      AutoCrystal.RenderMode mode = cfg.renderMode.get();
      if (mode == AutoCrystal.RenderMode.NONE) {
         return null;
      } else {
         long now = System.currentTimeMillis();
         JsonObject state = new JsonObject();

         long lastFor = switch (mode) {
            case FADE -> 50L + (long)(cfg.renderFadeTime.get() * 1000.0);
            case SLIDE -> 250L;
            default -> -1L;
            case Simple -> (long)(cfg.simpleDrawTime.get() * 1000.0);
         };
         if (this.simpleRenderBase != null && now - this.simpleRenderMs <= lastFor) {
            state.add("last", PbFx.pos(this.simpleRenderBase));
            state.addProperty("t", this.simpleRenderMs);
            state.addProperty("dmg", Math.round(this.simpleRenderDmg * 10.0F) / 10.0);
         }

         if (mode == AutoCrystal.RenderMode.DelayDraw) {
            JsonArray places = PbFx.posTimes(this.placeRenders, now, (long)(cfg.placeDelayFadeTime.get() * 1000.0), 32);
            if (!places.isEmpty()) {
               state.add("places", places);
            }

            long breakFadeMs = (long)(cfg.breakDelayFadeTime.get() * 1000.0);
            JsonArray breaks = new JsonArray();
            Iterator<AutoCrystalFeature.BreakRender> it = this.breakRenders.descendingIterator();

            while (it.hasNext() && breaks.size() < 32) {
               AutoCrystalFeature.BreakRender r = it.next();
               if (now - r.timeMs() <= breakFadeMs) {
                  JsonArray a = new JsonArray();
                  a.add(Math.round(r.box().minX * 16.0));
                  a.add(Math.round(r.box().minY * 16.0));
                  a.add(Math.round(r.box().minZ * 16.0));
                  a.add(Math.round(r.box().maxX * 16.0));
                  a.add(Math.round(r.box().maxY * 16.0));
                  a.add(Math.round(r.box().maxZ * 16.0));
                  a.add(r.timeMs());
                  breaks.add(a);
               }
            }

            if (!breaks.isEmpty()) {
               state.add("breaks", breaks);
            }
         }

         if (mode == AutoCrystal.RenderMode.Debug) {
            JsonArray spots = new JsonArray();
            List<BlockPos> nearest = new ArrayList<>(List.of(this.debugSpots));
            if (ReviveClient.mc.player != null) {
               nearest.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(ReviveClient.mc.player.getEyePos())));
            }

            for (BlockPos spot : nearest) {
               if (spots.size() >= 32) {
                  break;
               }

               spots.add(PbFx.pos(spot));
            }

            if (!spots.isEmpty()) {
               state.add("debug", spots);
            }
         }

         return state.isEmpty() ? null : state;
      }
   }

   public void readRemoteRender(JsonObject state, PbFx.Clock clock) {
      BlockPos last = PbFx.pos(state.get("last"));
      if (last != null) {
         long t = PbFx.time(state, "t", clock, System.currentTimeMillis());
         float dmg = (float)PbFx.num(state, "dmg", 0.0);
         this.renderPos = last;
         this.renderDmg = dmg;
         this.renderLastSeenMs = t;
         this.simpleRenderBase = last;
         this.simpleRenderMs = t;
      }

      PbFx.readPosTimes(state.get("places"), clock, this.placeRenders);
      this.breakRenders.clear();
      JsonElement breaks = state.get("breaks");
      if (breaks != null && breaks.isJsonArray()) {
         for (JsonElement e : breaks.getAsJsonArray()) {
            if (e.isJsonArray() && e.getAsJsonArray().size() >= 7) {
               JsonArray a = e.getAsJsonArray();
               Box box = new Box(
                  a.get(0).getAsLong() / 16.0,
                  a.get(1).getAsLong() / 16.0,
                  a.get(2).getAsLong() / 16.0,
                  a.get(3).getAsLong() / 16.0,
                  a.get(4).getAsLong() / 16.0,
                  a.get(5).getAsLong() / 16.0
               );
               this.breakRenders.addFirst(new AutoCrystalFeature.BreakRender(box, clock.toLocal(a.get(6).getAsLong())));
            }
         }
      }

      JsonElement debug = state.get("debug");
      List<BlockPos> spots = new ArrayList<>();
      if (debug != null && debug.isJsonArray()) {
         for (JsonElement ex : debug.getAsJsonArray()) {
            BlockPos pos = PbFx.pos(ex);
            if (pos != null) {
               spots.add(pos);
            }
         }
      }

      this.debugSpots = spots.toArray(new BlockPos[0]);
   }

   public void clearRemoteRender() {
      this.renderPos = null;
      this.renderVec = null;
      this.renderDmg = 0.0F;
      this.renderLastSeenMs = 0L;
      this.placeRenders.clear();
      this.breakRenders.clear();
      this.simpleRenderBase = null;
      this.debugSpots = new BlockPos[0];
   }

   private void updateRenderState(AutoCrystal cfg) {
      AutoCrystalFeature.PlaceTarget t = this.lastPlaceTarget;
      if (t != null) {
         this.renderPos = t.pos().down();
         this.renderDmg = t.totalDamage();
         this.renderLastSeenMs = System.currentTimeMillis();
      }

      if (this.renderPos != null) {
         Vec3d goal = new Vec3d(this.renderPos.getX(), this.renderPos.getY(), this.renderPos.getZ());
         int steps = cfg.renderSlideSteps.get();
         if (this.renderVec != null && cfg.renderMode.get() == AutoCrystal.RenderMode.SLIDE && steps > 0) {
            this.renderVec = this.renderVec.add(goal.subtract(this.renderVec).multiply(1.0 / steps));
         } else {
            this.renderVec = goal;
         }
      }
   }

   private float renderAlpha(AutoCrystal cfg) {
      if (this.renderPos == null) {
         return 0.0F;
      } else {
         long since = System.currentTimeMillis() - this.renderLastSeenMs;

         return switch ((AutoCrystal.RenderMode)cfg.renderMode.get()) {
            case FADE -> {
               double fadeMs = cfg.renderFadeTime.get() * 1000.0;
               yield since <= 50L ? 1.0F : (fadeMs <= 0.0 ? 0.0F : (float)MathHelper.clamp(1.0 - (since - 50L) / fadeMs, 0.0, 1.0));
            }
            case SLIDE -> since <= 250L ? 1.0F : 0.0F;
            default -> 0.0F;
         };
      }
   }

   private void doBreak(AutoCrystal cfg) {
      if (this.breakSpeedOk(cfg)) {
         if (!this.attackBudgetLow(cfg, "break")) {
            AutoCrystalFeature.BreakTarget target = this.bestCrystal(cfg);
            if (target != null) {
               EndCrystalEntity crystal = target.crystal();
               if (cfg.rotateBreak.get()) {
                  Vec3d point = this.breakAimPoint(crystal, cfg);
                  if (point == null) {
                     if (dbgOn(cfg)) {
                        this.dbgState(cfg, "break", "break skip: no look from your eye reaches crystal id=" + crystal.getId() + " within break-range");
                     }

                     return;
                  }

                  AutoCrystalFeature.ComboPlan combo = this.comboPlan;
                  if (this.comboUrgent(combo, cfg) && !point.equals(combo.aim())) {
                     if (dbgOn(cfg)) {
                        this.dbgState(
                           cfg,
                           "break",
                           "break skip: the glass seal's "
                              + combo.kind()
                              + " at "
                              + combo.x().toShortString()
                              + " needs this tick's look (its look doesn't reach crystal id="
                              + crystal.getId()
                              + ")"
                        );
                     }

                     return;
                  }

                  this.aimAt(point);
               }

               if (!this.canBreak(crystal, cfg)) {
                  if (dbgOn(cfg)) {
                     this.dbgState(
                        cfg,
                        "break",
                        this.aimOwned
                           ? "break skip: this packet already carries our aim at another crystal - next tick"
                           : "break skip: a higher-priority module holds this tick's rotation - next tick"
                     );
                  }
               } else {
                  this.dbgClear("break");
                  if (cfg.rotateBreak.get() && cfg.rotationTiming.get() != AutoCrystal.RotationTiming.Post) {
                     this.markCombatActivity();
                     this.pendingBreak = crystal;
                     this.pendingBreakDamage = target.totalDamage();
                  } else {
                     this.attackCrystal(crystal, cfg);
                     this.dbg(cfg, String.format(Locale.US, "break ATTACK id=%d dmg=%.1f", crystal.getId(), target.totalDamage()));
                  }
               }
            }
         }
      }
   }

   private Vec3d breakAimPoint(EndCrystalEntity crystal, AutoCrystal cfg) {
      Vec3d eye = ReviveClient.mc.player.getEyePos();
      Box box = crystal.getBoundingBox();
      double reach = cfg.breakRange.get();
      boolean standing = ReviveClient.mc.player.getVelocity().horizontalLengthSquared() < 1.0E-4;
      if (ReviveClient.ROTATION.silentLooksRideMotion() && this.waitsForPrimaryStop(crystal, cfg)) {
         double ttb = SilentMine.get().getPrimaryTicksToBreak();
         Vec3d top = comboAimPoint(crystal.getBlockPos());
         if (ttb > 0.0 && ttb <= 1.0 && aimReaches(eye, top, box, reach)) {
            this.dbgOnChange(
               cfg,
               "stop-aim",
               crystal.getId() + " top",
               "break aim: id="
                  + crystal.getId()
                  + " at the top of primary "
                  + crystal.getBlockPos().down().toShortString()
                  + " - its STOP rides this look, the hit follows it"
            );
            return top;
         }
      }

      Vec3d queued = this.queuedPlace != null ? this.sharedAimPoint(this.queuedPlace.pos().down(), eye, box, reach, cfg) : null;
      if (queued != null) {
         return queued;
      } else if (!cfg.rotatePlace.get()) {
         return this.breakAimOn(eye, box, reach);
      } else {
         Vec3d combo = this.comboAimFor(crystal, cfg);
         if (combo != null && aimReaches(eye, combo, box, reach)) {
            return combo;
         } else {
            boolean placing = cfg.placeCrystals.get() && (!cfg.pauseEatPlace.get() || !ReviveClient.mc.player.isUsingItem());
            AutoCrystalFeature.PlaceTarget topPlace = this.queuedPlace == null && placing ? this.primaryTopTarget(cfg) : null;
            AutoCrystalFeature.PlaceTarget rebreakPlace = this.queuedPlace == null && placing ? this.silentMineRebreakTarget(cfg) : null;
            boolean rebreakNow = rebreakPlace != null && this.placeFreeNow(rebreakPlace.pos(), cfg);
            BlockPos[] bases = new BlockPos[]{
               topPlace != null && this.placeFreeNow(topPlace.pos(), cfg) ? topPlace.pos().down() : null,
               rebreakNow ? rebreakPlace.pos().down() : null,
               this.bestPlace != null ? this.bestPlace.pos().down() : null,
               rebreakPlace != null && !rebreakNow ? rebreakPlace.pos().down() : null,
               standing ? crystal.getBlockPos().down() : null
            };

            for (BlockPos base : bases) {
               Vec3d point = this.sharedAimPoint(base, eye, box, reach, cfg);
               if (point != null) {
                  return point;
               }
            }

            return this.breakAimOn(eye, box, reach);
         }
      }
   }

   private boolean placeFreeNow(BlockPos spot, AutoCrystal cfg) {
      return !this.isPlaceDelayed(spot, cfg) && this.unconfirmedPlaces.getOrDefault(spot.asLong(), 0) < 6;
   }

   private Vec3d sharedAimPoint(BlockPos base, Vec3d eye, Box box, double reach, AutoCrystal cfg) {
      if (base == null) {
         return null;
      } else {
         AutoCrystalFeature.PlaceAim aim = this.placeAim(base, cfg);
         return aim != null && aimReaches(eye, aim.aimPoint(), box, reach) ? aim.aimPoint() : null;
      }
   }

   private Vec3d breakAimOn(Vec3d eye, Box box, double reach) {
      Vec3d center = box.getCenter();
      if (aimReaches(eye, center, box, reach)) {
         return center;
      } else {
         Vec3d closest = new Vec3d(
            MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ)
         );
         Vec3d half = closest.add(center.subtract(closest).multiply(0.5));
         if (aimReaches(eye, half, box, reach)) {
            return half;
         } else {
            Vec3d inset = aimPointOn(eye, box);
            if (aimReaches(eye, inset, box, reach)) {
               return inset;
            } else {
               Vec3d nudged = closest.add(center.subtract(closest).multiply(0.02));
               return aimReaches(eye, nudged, box, reach) ? nudged : null;
            }
         }
      }
   }

   private static boolean aimReaches(Vec3d eye, Vec3d point, Box box, double reach) {
      float[] rot = ReviveClient.ROTATION.getRotation(eye, point);
      return rayHits(eye, rot[0], rot[1], box, reach);
   }

   @EventHandler(
      priority = -100
   )
   public void onMovementSent(SendMovementPacketsEvent.Post event) {
      boolean ourLookWent = this.aimHeld
         && this.aimOwned
         && Math.abs(MathHelper.wrapDegrees(ReviveClient.ROTATION.getServerYaw() - this.aimYaw)) < 0.01F
         && Math.abs(ReviveClient.ROTATION.getServerPitch() - this.aimPitch) < 0.01F;
      this.aimHeld = false;
      this.aimYielded = false;
      this.flushId++;
      if (this.aimYielding() && this.yieldPos != null) {
         Long stop = this.stopSentTicks.get(this.yieldPos.asLong());
         if (stop == null || stop < this.aimYieldTick - 1L) {
            this.futileYieldPos = this.yieldPos;
            this.futileYieldTick = this.clientTick;
         }
      }

      this.aimYieldTick = this.starvationYield(ourLookWent) ? this.clientTick + 1L : Long.MIN_VALUE;
      EndCrystalEntity crystal = this.pendingBreak;
      if (crystal != null) {
         this.pendingBreak = null;
         AutoCrystal cfg = AutoCrystal.get();
         if (cfg != null && ReviveClient.mc.player != null && ReviveClient.mc.world != null && ReviveClient.mc.interactionManager != null) {
            if (!crystal.isAlive()) {
               if (dbgOn(cfg)) {
                  this.dbgState(cfg, "break-post", "break drop: crystal gone before the move packet");
               }
            } else if (!this.isInBreakRangeNow(crystal, cfg)) {
               if (dbgOn(cfg)) {
                  this.dbgState(cfg, "break-post", "break drop: out of range after this tick's movement");
               }
            } else if (this.waitsForPrimaryStop(crystal, cfg)) {
               if (dbgOn(cfg)) {
                  this.dbgState(
                     cfg,
                     "break-post",
                     "break drop: id=" + crystal.getId() + " on primary " + crystal.getBlockPos().down().toShortString() + " - its STOP was deferred this tick"
                  );
               }
            } else {
               String comboHold = this.comboHitHold(crystal);
               if (comboHold != null) {
                  if (dbgOn(cfg)) {
                     this.dbgState(cfg, "break-post", "break hold: id=" + crystal.getId() + " - " + comboHold);
                  }
               } else if (cfg.rotateBreak.get()
                  && !rayHits(
                     ReviveClient.mc.player.getEyePos(),
                     ReviveClient.ROTATION.getServerYaw(),
                     ReviveClient.ROTATION.getServerPitch(),
                     crystal.getBoundingBox(),
                     cfg.breakRange.get()
                  )) {
                  boolean oursSent = Math.abs(MathHelper.wrapDegrees(ReviveClient.ROTATION.getServerYaw() - this.aimYaw)) < 0.01F
                     && Math.abs(ReviveClient.ROTATION.getServerPitch() - this.aimPitch) < 0.01F;
                  if (dbgOn(cfg)) {
                     this.dbgState(
                        cfg,
                        "break-post",
                        oursSent
                           ? "break skip: our aim went out, but this tick's movement moved the eye off the crystal - next tick"
                           : "break skip: a higher-priority module took the move packet's rotation after our tick - next tick"
                     );
                  }
               } else if (!this.attackBudgetLow(cfg, "break-post")) {
                  this.dbgClear("break-post");
                  this.attackCrystal(crystal, cfg);
                  this.dbg(cfg, String.format(Locale.US, "break ATTACK id=%d dmg=%.1f", crystal.getId(), this.pendingBreakDamage));
               }
            }
         }
      }
   }

   private void attackCrystal(EndCrystalEntity crystal, AutoCrystal cfg) {
      if (ReviveClient.mc.player != null && ReviveClient.mc.interactionManager != null) {
         this.markCombatActivity();
         ReviveClient.mc.interactionManager.attackEntity(ReviveClient.mc.player, crystal);
         this.swing(cfg.breakSwingMode.get(), Hand.MAIN_HAND);
         this.lastAttackFlushTick = this.flushId;
         this.lastAttackPos = crystal.getEntityPos();
         this.lastAttackNs = System.nanoTime();
         long now = System.currentTimeMillis();
         this.lastBreakMs = now;
         this.breakAttemptMs.put(crystal.getId(), now);
         this.cpsCounter.updateCounter();
         this.breakRenders.addLast(new AutoCrystalFeature.BreakRender(crystal.getBoundingBox(), now));

         while (this.breakRenders.size() > 64) {
            this.breakRenders.pollFirst();
         }
      }
   }

   private AutoCrystalFeature.BreakTarget bestCrystal(AutoCrystal cfg) {
      int seen = 0;
      int delayed = 0;
      int range = 0;
      int rejected = 0;
      int held = 0;
      AutoCrystalFeature.BreakTarget best = null;
      SilentMine sm = SilentMine.get();
      double stopTicks = sm != null && sm.isActive() ? sm.getPrimaryTicksToBreak() : -1.0;
      boolean hitFollowsStop = cfg.rotateBreak.get() && cfg.rotationTiming.get() == AutoCrystal.RotationTiming.Pre && stopTicks > 0.0 && stopTicks <= 1.0;

      for (EndCrystalEntity crystal : this.endCrystals()) {
         if (crystal.isAlive()) {
            seen++;
            if (this.isBreakDelayed(crystal.getId(), cfg)) {
               delayed++;
            } else if (!this.isInBreakRangeNow(crystal, cfg)) {
               range++;
            } else if (!hitFollowsStop && this.waitsForPrimaryStop(crystal, cfg)) {
               held++;
               this.dbgOnChange(
                  cfg,
                  "stop-hold",
                  crystal.getId() + " tick",
                  String.format(
                     Locale.US,
                     "break hold: id=%d on primary %s - its STOP isn't out (ttb=%.0f)",
                     crystal.getId(),
                     crystal.getBlockPos().down().toShortString(),
                     stopTicks
                  )
               );
            } else {
               String comboHold = this.comboHitHold(crystal);
               if (comboHold != null) {
                  held++;
                  this.dbgOnChange(cfg, "combo-hit-hold", crystal.getId() + " " + comboHold, "break hold: id=" + crystal.getId() + " - " + comboHold);
               } else {
                  float totalDamage = this.breakDamage(crystal.getEntityPos(), cfg);
                  if (totalDamage <= 0.0F) {
                     if (!cfg.laser.get() || !this.isSelfSafe(crystal.getEntityPos(), cfg, true)) {
                        rejected++;
                        continue;
                     }

                     totalDamage = 0.001F;
                  }

                  if (best == null || totalDamage > best.totalDamage()) {
                     best = new AutoCrystalFeature.BreakTarget(crystal, totalDamage);
                  }
               }
            }
         }
      }

      if (best == null && dbgOn(cfg)) {
         this.dbgState(cfg, "break", String.format(Locale.US, "break none: seen=%d delayed=%d range=%d dmg=%d held=%d", seen, delayed, range, rejected, held));
      }

      return best;
   }

   private boolean waitsForPrimaryStop(EndCrystalEntity crystal, AutoCrystal cfg) {
      if (!cfg.antiSurround.get()) {
         return false;
      } else {
         SilentMine sm = SilentMine.get();
         BlockPos primary = sm != null && sm.isActive() ? sm.getDelayedDestroyPos() : null;
         if (primary != null && !sm.isPrimaryStopSent() && crystal.getBlockPos().down().equals(primary)) {
            double ticks = sm.getPrimaryTicksToBreak();
            return ticks >= 0.0 && ticks <= 2.0;
         } else {
            return false;
         }
      }
   }

   private static boolean onSentPrimary(BlockPos spot) {
      SilentMine sm = SilentMine.get();
      return sm != null && sm.isActive() && sm.isPrimaryStopSent() && spot.down().equals(sm.getDelayedDestroyPos());
   }

   private boolean breakSpeedOk(AutoCrystal cfg) {
      double limit = cfg.breakSpeedLimit.get();
      return limit <= 0.0 || (System.currentTimeMillis() - this.lastBreakMs) / 1000.0 > 1.0 / limit;
   }

   private boolean placeSpeedOk(AutoCrystal cfg) {
      double limit = cfg.placeSpeedLimit.get();
      return limit <= 0.0 || (System.currentTimeMillis() - this.lastPlaceMs) / 1000.0 > 1.0 / limit;
   }

   private boolean isBreakDelayed(int id, AutoCrystal cfg) {
      Long last = this.breakAttemptMs.get(id);
      return last != null && (System.currentTimeMillis() - last) / 1000.0 < cfg.breakDelay.get();
   }

   private boolean isPlaceDelayed(BlockPos spot, AutoCrystal cfg) {
      Long last = this.placeAttemptMs.get(spot.asLong());
      return last != null && (System.currentTimeMillis() - last) / 1000.0 < cfg.placeDelay.get();
   }

   private boolean canBreak(EndCrystalEntity crystal, AutoCrystal cfg) {
      if (ReviveClient.mc.player == null || crystal == null || !crystal.isAlive()) {
         return false;
      } else if (!cfg.rotateBreak.get()) {
         return true;
      } else {
         return cfg.rotationTiming.get() == AutoCrystal.RotationTiming.Post
            ? true
            : rayHits(
               ReviveClient.mc.player.getEyePos(),
               ReviveClient.ROTATION.getOutgoingYaw(),
               ReviveClient.ROTATION.getOutgoingPitch(),
               crystal.getBoundingBox(),
               cfg.breakRange.get()
            );
      }
   }

   private boolean doPlace(AutoCrystalFeature.PlaceTarget target, AutoCrystal cfg, boolean forceRotate, String label, boolean queued) {
      BlockPos spot = target.pos();
      BlockPos base = spot.down();
      if (this.isSpotBackedOff(spot)) {
         if (dbgOn(cfg)) {
            this.dbgState(cfg, "place:" + label, "place skip: " + spot.toShortString() + " backed off (places there never spawned a crystal)");
         }

         return false;
      } else if (this.isPlaceDelayed(spot, cfg)) {
         if ("place".equals(label)
            && cfg.packetBreak.get()
            && cfg.packetPlace.get()
            && cfg.rotatePlace.get()
            && !PbState.spawnHitsWait
            && cfg.rotationTiming.get() == AutoCrystal.RotationTiming.Pre) {
            AutoCrystalFeature.PlaceAim held = this.placeAim(base, cfg);
            if (held != null) {
               this.placeDelayHold = held.aimPoint();
            }
         }

         if (dbgOn(cfg)) {
            this.dbgState(cfg, "place:" + label, "place skip: place-delay at " + spot.toShortString());
         }

         return false;
      } else if (this.startSpotBackoff(spot, cfg)) {
         return false;
      } else {
         AutoCrystalFeature.PlaceAim aim = this.placeAim(base, cfg);
         if (aim == null) {
            if (dbgOn(cfg)) {
               this.dbgState(cfg, "place:" + label, "place skip: no visible face on " + base.toShortString());
            }

            return false;
         } else {
            if (forceRotate || cfg.rotatePlace.get()) {
               boolean owned = this.aimAt(aim.aimPoint());
               if (forceRotate || cfg.rotationTiming.get() == AutoCrystal.RotationTiming.Pre) {
                  if (!this.outgoingLooksAt(new Box(base))) {
                     if (dbgOn(cfg)) {
                        this.dbgState(
                           cfg,
                           "place:" + label,
                           (
                                 owned
                                    ? "place skip: this packet already carries our aim (break / packet-break) and it doesn't reach base "
                                    : "place skip: a higher-priority module holds this tick's rotation; it doesn't reach base "
                              )
                              + base.toShortString()
                              + " - next tick"
                        );
                     }

                     return false;
                  }

                  this.stagedPlace = new AutoCrystalFeature.StagedPlace(target, null, label, queued);
                  this.markCombatActivity();
                  return true;
               }
            }

            if (!this.useItemOn(Items.END_CRYSTAL, aim.hit(), cfg, "place")) {
               return false;
            } else {
               this.crystalPlaceSent(target, cfg, label, queued);
               return true;
            }
         }
      }
   }

   private boolean startSpotBackoff(BlockPos spot, AutoCrystal cfg) {
      long placeKey = spot.asLong();
      if (this.unconfirmedPlaces.getOrDefault(placeKey, 0) < 6) {
         return false;
      } else {
         this.spotBackoffUntil.put(placeKey, this.clientTick + 10L);
         this.unconfirmedPlaces.put(placeKey, 5);
         this.dbg(cfg, "spot backoff " + spot.toShortString() + " 10t: 6 places there never spawned a crystal");
         return true;
      }
   }

   @EventHandler(
      priority = -200
   )
   public void onMovementPre(SendMovementPacketsEvent.Pre event) {
      AutoCrystalFeature.StagedPlace staged = this.stagedPlace;
      this.stagedPlace = null;
      AutoCrystalFeature.StagedCombo combo = this.stagedCombo;
      this.stagedCombo = null;
      if (staged != null || combo != null) {
         AutoCrystal cfg = AutoCrystal.get();
         if (cfg != null && ReviveClient.mc.player != null && ReviveClient.mc.world != null && ReviveClient.mc.getNetworkHandler() != null) {
            Vec3d eye = ReviveClient.mc.player.getEyePos();
            float yaw = ReviveClient.mc.player.getYaw();
            float pitch = ReviveClient.mc.player.getPitch();
            boolean oursOnPacket = Math.abs(MathHelper.wrapDegrees(yaw - this.aimYaw)) < 0.01F && Math.abs(pitch - this.aimPitch) < 0.01F;
            String lostReason = oursOnPacket
               ? "this tick's movement moved the eye off it"
               : "a higher-priority module took the move packet's rotation after our tick";
            if (combo != null) {
               this.sendCombo(combo, cfg, yaw, pitch, oursOnPacket ? "the eye the server has changed since the tick (setback / pose)" : lostReason);
            } else if (staged.crystal() != null) {
               AutoCrystalFeature.PlaceTarget target = staged.crystal();
               BlockPos spot = target.pos();
               BlockPos base = spot.down();
               if (!this.isPlaceable(spot, cfg, true, false)) {
                  if (dbgOn(cfg)) {
                     this.dbgState(cfg, "place-pre", "place drop: " + spot.toShortString() + " not placeable from where this move packet puts you");
                  }
               } else {
                  AutoCrystalFeature.PlaceAim aim = this.placeAim(base, cfg);
                  if (aim != null && ReviveClient.ROTATION.raytraceCheck(eye, yaw, pitch, new Box(base))) {
                     this.dbgClear("place-pre");
                     if (this.useItemOn(Items.END_CRYSTAL, aim.hit(), cfg, "place")) {
                        this.crystalPlaceSent(target, cfg, staged.label(), staged.queued());
                     }
                  } else {
                     if (dbgOn(cfg)) {
                        this.dbgState(
                           cfg, "place-pre", "place drop: the move packet's look misses base " + base.toShortString() + " (" + lostReason + ") - next tick"
                        );
                     }
                  }
               }
            } else {
               AutoCrystalFeature.BasePlaceTarget target = staged.base();
               BlockHitResult hit = target.hit();
               BlockState baseState = ReviveClient.mc.world.getBlockState(target.base());
               if (!baseState.isAir() && !baseState.isReplaceable()) {
                  this.dbgClear("place-pre");
               } else if (!(eye.distanceTo(hit.getPos()) > cfg.placeRange.get())
                  && ReviveClient.ROTATION.raytraceCheck(eye, yaw, pitch, new Box(hit.getBlockPos()))) {
                  this.dbgClear("place-pre");
                  if (this.useItemOn(Items.OBSIDIAN, hit, cfg, "base-place")) {
                     this.basePlaceSent(target, cfg);
                  }
               } else {
                  if (dbgOn(cfg)) {
                     this.dbgState(
                        cfg,
                        "place-pre",
                        "base-place drop: the move packet's look misses the clicked face of "
                           + hit.getBlockPos().toShortString()
                           + " ("
                           + lostReason
                           + ") - next tick"
                     );
                  }
               }
            }
         }
      }
   }

   private void crystalPlaceSent(AutoCrystalFeature.PlaceTarget target, AutoCrystal cfg, String label, boolean queued) {
      this.onPlaceSent(target);
      if (target.pos().equals(this.lateX)) {
         this.lateX = null;
      }

      if (queued) {
         this.queuedPlace = null;
      }

      if (target.pos().equals(this.asPos) && this.asPlaces++ == 0) {
         this.asPlaceTick = this.clientTick;
      }

      this.dbgClear("place");
      this.dbgClear("place:" + label);
      if (cfg.debug.get()) {
         this.dbg(
            cfg,
            String.format(
               Locale.US, "%s SENT pos=%s dmg=%.1f%s", label, target.pos().toShortString(), target.totalDamage(), this.placeRelation(target.pos(), cfg)
            )
         );
      }
   }

   private String placeRelation(BlockPos spot, AutoCrystal cfg) {
      AutoMine autoMine = AutoMine.get();
      PlayerEntity target = autoMine != null ? autoMine.getTargetPlayer() : null;
      if (target == null) {
         double best = Double.MAX_VALUE;

         for (LivingEntity e : this.enemies(cfg)) {
            if (e instanceof PlayerEntity p && !(p.squaredDistanceTo(ReviveClient.mc.player) >= best)) {
               best = p.squaredDistanceTo(ReviveClient.mc.player);
               target = p;
            }
         }
      }

      if (target == null) {
         return "";
      } else {
         BlockPos feet = target.getBlockPos();
         int dy = spot.getY() - feet.getY();
         return " rel="
            + (spot.getX() - feet.getX())
            + ","
            + dy
            + ","
            + (spot.getZ() - feet.getZ())
            + " pose="
            + target.getPose()
            + (target.isCrawling() && dy >= 1 && !cfg.cevHead.get() ? " ABOVE-HEAD" : "");
      }
   }

   private void onPlaceSent(AutoCrystalFeature.PlaceTarget target) {
      this.placeCounter.updateCounter();
      this.lastPlaceTarget = target;
      this.lastTotalDamage = target.totalDamage();
      this.markCombatActivity();
      long now = System.currentTimeMillis();
      this.lastPlaceMs = now;
      long key = target.pos().asLong();
      this.placeAttemptMs.put(key, now);
      this.unconfirmedPlaces.merge(key, 1, Integer::sum);
      BlockPos base = target.pos().down();
      this.placeRenders.remove(base);
      this.placeRenders.put(base, now);
      this.simpleRenderBase = base;
      this.simpleRenderMs = now;
      this.simpleRenderDmg = target.totalDamage();
   }

   private boolean useItemOn(Item item, BlockHitResult hit, AutoCrystal cfg, String what) {
      return this.useItemOn(item, hit, cfg, what, null);
   }

   private boolean useItemOn(Item item, BlockHitResult hit, AutoCrystal cfg, String what, IntPredicate beforeUse) {
      if (ReviveClient.mc.player == null || ReviveClient.mc.getNetworkHandler() == null) {
         return false;
      } else if (!this.reserveInteractionBudget()) {
         if (dbgOn(cfg)) {
            this.dbgState(
               cfg, "use", what + " skip: BLOCK_INTERACT budget spent (remaining=" + ReviveClient.PACKET.remaining(PacketManager.Category.BLOCK_INTERACT) + ")"
            );
         }

         return false;
      } else if (!ReviveClient.mc.player.getOffHandStack().isOf(item)) {
         if (!ReviveClient.mc.player.getMainHandStack().isOf(item)) {
            AutoCrystal.CrystalSwapMode mode = cfg.swapMode.get();
            if (mode == AutoCrystal.CrystalSwapMode.None) {
               if (dbgOn(cfg)) {
                  this.dbgState(cfg, "use", what + " skip: swap-mode None and the item isn't in hand");
               }

               return false;
            } else {
               if (mode == AutoCrystal.CrystalSwapMode.OffhandSilent) {
                  FindItemResult hotbar = InvUtils.findInHotbar(item);
                  int selected = ReviveClient.mc.player.getInventory().getSelectedSlot();
                  boolean slotBusy = hotbar.found() && hotbar.slot() != selected && miningOwnsSelectedSlot();
                  if (hotbar.found() && !slotBusy) {
                     if (!this.canSendOffhandSwapPair()) {
                        if (dbgOn(cfg)) {
                           this.dbgState(cfg, "use", what + " skip: OFFHAND budget spent");
                        }

                        return false;
                     }

                     if (beforeUse != null && !beforeUse.test(-1)) {
                        return false;
                     }

                     this.placeOffhandSilent(hit, hotbar.slot(), cfg);
                     if (item == Items.END_CRYSTAL) {
                        this.lastCrystalHotbarSlot = hotbar.slot();
                     }

                     return true;
                  }
               }

               FindItemResult result = ReviveClient.SWAP.getSlot(item);
               if (!result.found()) {
                  if (dbgOn(cfg)) {
                     this.dbgState(cfg, "use", what + " skip: no " + item + " reachable under item-swap-mode " + ReviveClient.SWAP.getItemSwapMode());
                  }

                  return false;
               } else if (ReviveClient.SWAP.isSlotLocked(result.slot())) {
                  if (dbgOn(cfg)) {
                     this.dbgState(cfg, "use", what + " skip: source slot locked by another swap");
                  }

                  return false;
               } else if (!ReviveClient.SWAP.beginSwap(result, true)) {
                  if (dbgOn(cfg)) {
                     this.dbgState(cfg, "use", what + " skip: SwapManager refused the swap (mode/budget/held swap)");
                  }

                  return false;
               } else {
                  boolean containerSwap = ReviveClient.SWAP.isInstantSwapContainerBased();

                  try {
                     if (beforeUse != null && !beforeUse.test(ReviveClient.mc.player.getInventory().getSelectedSlot())) {
                        return false;
                     }

                     this.sendPlacePacket(hit, Hand.MAIN_HAND);
                     if (containerSwap && !ReviveClient.mc.player.getAbilities().creativeMode) {
                        ItemStack held = ReviveClient.mc.player.getMainHandStack();
                        if (!held.isEmpty()) {
                           held.decrement(1);
                        }
                     }

                     this.swing(cfg.placeSwingMode.get(), Hand.MAIN_HAND);
                  } finally {
                     ReviveClient.SWAP.endSwap(true);
                     if (containerSwap && ReviveClient.mc.player != null) {
                        ReviveClient.mc.player.playerScreenHandler.nextRevision();
                     }
                  }

                  if (item == Items.END_CRYSTAL && result.isHotbar()) {
                     this.lastCrystalHotbarSlot = result.slot();
                  }

                  return true;
               }
            }
         } else if (beforeUse != null && !beforeUse.test(ReviveClient.mc.player.getInventory().getSelectedSlot())) {
            return false;
         } else {
            this.sendPlacePacket(hit, Hand.MAIN_HAND);
            this.swing(cfg.placeSwingMode.get(), Hand.MAIN_HAND);
            if (item == Items.END_CRYSTAL) {
               this.lastCrystalHotbarSlot = ReviveClient.mc.player.getInventory().getSelectedSlot();
            }

            return true;
         }
      } else if (beforeUse != null && !beforeUse.test(-1)) {
         return false;
      } else {
         this.sendPlacePacket(hit, Hand.OFF_HAND);
         this.swing(cfg.placeSwingMode.get(), Hand.OFF_HAND);
         return true;
      }
   }

   private void placeOffhandSilent(BlockHitResult hit, int hotbarSlot, AutoCrystal cfg) {
      int selectedSlot = ReviveClient.mc.player.getInventory().getSelectedSlot();
      boolean spoofed = hotbarSlot != selectedSlot;
      if (spoofed) {
         this.sendCarriedItemPacket(hotbarSlot);
      }

      this.sendOffhandSwapPacket();

      try {
         this.sendPlacePacket(hit, Hand.OFF_HAND);
         this.swing(cfg.placeSwingMode.get(), Hand.OFF_HAND);
      } finally {
         this.sendOffhandSwapPacket();
         if (spoofed) {
            this.sendCarriedItemPacket(selectedSlot);
         }
      }
   }

   private static boolean miningOwnsSelectedSlot() {
      if (ReviveClient.SWAP.isMultiTickSwapActive()) {
         return true;
      } else {
         SilentMine sm = SilentMine.get();
         return sm != null && sm.isActive() && sm.hasDelayedDestroy();
      }
   }

   public boolean preplaceCrystal(BlockPos pos, boolean snap) {
      return this.preplaceCrystal(pos, snap, null);
   }

   public boolean preplaceCrystal(BlockPos pos, boolean snap, BlockPos pretendAir) {
      AutoCrystal cfg = AutoCrystal.get();
      if (cfg == null || !cfg.isRunningHere()) {
         return false;
      } else if (ReviveClient.mc.player == null || ReviveClient.mc.world == null || pos == null) {
         return false;
      } else if (!cfg.placeCrystals.get()) {
         return false;
      } else if (cfg.pauseEatPlace.get() && ReviveClient.mc.player.isUsingItem()) {
         return false;
      } else if (this.enemies(cfg).isEmpty()) {
         return false;
      } else if (!this.isPlaceable(pos, cfg, true, false)) {
         return false;
      } else {
         this.queuedPlace = new AutoCrystalFeature.QueuedPlace(
            pos.toImmutable(), snap, this.clientTick + 3L, pretendAir != null ? pretendAir.toImmutable() : null
         );
         return true;
      }
   }

   private boolean tryQueuedPlace(AutoCrystal cfg) {
      AutoCrystalFeature.QueuedPlace q = this.queuedPlace;
      if (q == null) {
         return false;
      } else {
         BlockPos spot = q.pos();
         if (!this.isSpotBackedOff(spot) && this.isPlaceable(spot, cfg, true, false)) {
            AutoCrystalSnapshot.Context ctx = this.context(cfg);
            Vec3d crystalPos = crystalPos(spot);
            if (ctx.rejectsSelf(ctx.selfDamage(crystalPos, ctx.world()), false)) {
               this.queuedPlace = null;
               this.dbg(cfg, "queued place DROP: self damage at " + spot.toShortString());
               return false;
            } else {
               float dmg = ctx.maxEnemyDamage(crystalPos, pretend(ctx.world(), null, q.pretendAir()));
               String pretendDbg = " pretendAir=" + (q.pretendAir() != null ? q.pretendAir().toShortString() : "-");
               if (dmg <= 0.0F) {
                  this.queuedPlace = null;
                  this.dbg(cfg, "queued place DROP: " + spot.toShortString() + pretendDbg + " dmg=0 (hurts no enemy)");
                  return false;
               } else {
                  this.dbgOnChange(
                     cfg, "queued", spot.asLong() + pretendDbg, String.format(Locale.US, "queued place %s%s dmg=%.1f", spot.toShortString(), pretendDbg, dmg)
                  );
                  this.markCombatActivity();
                  return this.doPlace(new AutoCrystalFeature.PlaceTarget(spot, dmg), cfg, q.snap(), "queued place", true);
               }
            }
         } else {
            return false;
         }
      }
   }

   private boolean tryPlaceSilentMineRebreak(AutoCrystal cfg) {
      AutoCrystalFeature.PlaceTarget target = this.silentMineRebreakTarget(cfg);
      if (target == null) {
         return false;
      } else {
         this.markCombatActivity();
         return this.doPlace(target, cfg, false, "rebreak place", false);
      }
   }

   private AutoCrystalFeature.PlaceTarget silentMineRebreakTarget(AutoCrystal cfg) {
      if (!cfg.antiSurround.get()) {
         return null;
      } else {
         AutoMine autoMine = AutoMine.get();
         if (autoMine != null && autoMine.hasValidTarget()) {
            if (!this.isValidAutoCrystalTarget(autoMine.getTargetPlayer(), cfg)) {
               return null;
            } else {
               SilentMine sm = SilentMine.get();
               if (sm != null && sm.isActive()) {
                  BlockPos rebreakPos = sm.getRebreakPos();
                  if (rebreakPos == null || this.isSpotBackedOff(rebreakPos)) {
                     return null;
                  } else if (sm.isOwnSeal(rebreakPos)) {
                     if (ReviveClient.mc.world.getBlockState(rebreakPos).isAir() && dbgOn(cfg)) {
                        this.dbgState(
                           cfg,
                           "rebreak-place",
                           "rebreak place skip: "
                              + rebreakPos.toShortString()
                              + " is our glass seal (reads our STOP's air) - only the combo puts a crystal there"
                        );
                     }

                     return null;
                  } else if (this.expectsDropSkip(cfg, rebreakPos)) {
                     return null;
                  } else if (!this.isPlaceable(rebreakPos, cfg, true, true)) {
                     return null;
                  } else {
                     float damage = this.placeDamage(rebreakPos, cfg, null, null);
                     if (damage <= 0.0F) {
                        return null;
                     } else if (this.bestPlace != null && this.bestPlace.totalDamage() - damage > cfg.rebreakDmgThreshold.get()) {
                        if (dbgOn(cfg)) {
                           this.dbgState(
                              cfg,
                              "rebreak-place",
                              "rebreak place skip: best "
                                 + this.bestPlace.pos().toShortString()
                                 + " beats "
                                 + rebreakPos.toShortString()
                                 + " by more than rebreak-dmg-threshold"
                           );
                        }

                        return null;
                     } else {
                        this.dbgClear("rebreak-place");
                        return new AutoCrystalFeature.PlaceTarget(rebreakPos.toImmutable(), damage);
                     }
                  }
               } else {
                  return null;
               }
            }
         } else {
            return null;
         }
      }
   }

   private boolean tryPrimaryTopPlace(AutoCrystal cfg) {
      AutoCrystalFeature.PlaceTarget target = this.primaryTopTarget(cfg);
      if (target == null) {
         return false;
      } else {
         this.markCombatActivity();
         return this.doPlace(target, cfg, false, "pre-STOP place", false);
      }
   }

   private AutoCrystalFeature.PlaceTarget primaryTopTarget(AutoCrystal cfg) {
      if (!cfg.antiSurround.get()) {
         return null;
      } else {
         SilentMine sm = SilentMine.get();
         AutoMine autoMine = AutoMine.get();
         if (sm != null && sm.isActive() && autoMine != null && autoMine.hasValidTarget()) {
            BlockPos primary = sm.getDelayedDestroyPos();
            if (primary != null && !sm.isPrimaryStopSent()) {
               double ticks = sm.getPrimaryTicksToBreak();
               if (primaryPlaceWindow(ticks, placeIsStaged(cfg)) && !(sm.getPrimaryProgress() < cfg.mineThreshold.get())) {
                  PlayerEntity target = autoMine.getTargetPlayer();
                  if (!this.isValidAutoCrystalTarget(target, cfg)) {
                     return null;
                  } else {
                     BlockPos spot = primary.up();
                     String head = String.format(Locale.US, "pre-STOP place %s (primary ttb=%.0f)", spot.toShortString(), ticks);
                     String skip = null;
                     float damage = -1.0F;
                     AutoCrystalFeature.ComboPlan combo = this.comboPlan;
                     if ((combo == null || combo.kind() != AutoCrystalFeature.ComboKind.SEAL || !primary.equals(combo.x())) && !sm.isOwnSeal(primary)) {
                        if (target.getBoundingBox().maxY <= primary.getY() + 1.0) {
                           skip = String.format(Locale.US, "target box top %.2f <= %d", target.getBoundingBox().maxY, primary.getY() + 1);
                        } else if (cfg.cevHead.get() || !primary.equals(target.getBlockPos().up()) && !this.enemyCrawlCeilings(cfg).contains(primary)) {
                           if (this.isSpotBackedOff(spot) || !this.isPlaceable(spot, cfg, true, true)) {
                              skip = "not placeable";
                           } else if ((damage = this.placeDamage(spot, cfg, null, primary)) <= 0.0F) {
                              skip = "below min-place with " + primary.toShortString() + " gone";
                           } else if (this.bestPlace != null && this.bestPlace.totalDamage() - damage > cfg.rebreakDmgThreshold.get()) {
                              skip = "best " + this.bestPlace.pos().toShortString() + " beats it by more than rebreak-dmg-threshold";
                           }
                        } else {
                           skip = "ceiling, cev-head off";
                        }
                     } else {
                        skip = "glass seal goes in, not air";
                     }

                     if (cfg.debug.get()) {
                        this.dbgOnChange(
                           cfg,
                           "pre-stop",
                           primary.asLong() + " " + skip,
                           skip != null ? head + " skip: " + skip : String.format(Locale.US, "%s -> place dmg=%.1f", head, damage)
                        );
                     }

                     return skip == null ? new AutoCrystalFeature.PlaceTarget(spot.toImmutable(), damage) : null;
                  }
               } else {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   private boolean expectsDropSkip(AutoCrystal cfg, BlockPos spot) {
      SilentMine sm = SilentMine.get();
      if (sm == null || !sm.isActive() || !sm.expectsDrop(spot)) {
         return false;
      } else if (cfg.ignoreItems.get() && this.ownDropBlownUp(spot)) {
         if (dbgOn(cfg)) {
            this.dbgState(cfg, "x-drop", spot.toShortString() + " expectsDrop, but our hit sent after the STOP blows the item up first: placing");
         }

         return false;
      } else {
         if (dbgOn(cfg)) {
            this.dbgState(cfg, "x-drop", spot.toShortString() + " skip: expectsDrop (our break's item is in it or on its way)");
         }

         return true;
      }
   }

   private boolean ownDropBlownUp(BlockPos spot) {
      SilentMine sm = SilentMine.get();
      if (sm != null
         && ReviveClient.mc.world != null
         && ReviveClient.mc.player != null
         && this.lastAttackPos != null
         && this.lastAttackNs > sm.stopSentNs(spot)) {
         Vec3d center = Vec3d.ofCenter(spot);
         return this.lastAttackPos.squaredDistanceTo(center) > 64.0
            ? false
            : ReviveClient.mc
                  .world
                  .raycast(new RaycastContext(center, this.lastAttackPos, ShapeType.COLLIDER, FluidHandling.NONE, ReviveClient.mc.player))
                  .getType()
               == Type.MISS;
      } else {
         return false;
      }
   }

   private boolean isValidAutoCrystalTarget(PlayerEntity target, AutoCrystal cfg) {
      if (ReviveClient.mc.player == null || target == null) {
         return false;
      } else if (!this.isEnemyTarget(target, cfg)) {
         return false;
      } else {
         double maxRange = cfg.targetRange.get();
         return ReviveClient.mc.player.squaredDistanceTo(target) <= maxRange * maxRange;
      }
   }

   private boolean isEnemyTarget(LivingEntity e, AutoCrystal cfg) {
      if (ReviveClient.mc.player != null && e != null && e != ReviveClient.mc.player) {
         if (!e.isAlive() || e.getHealth() <= 0.0F) {
            return false;
         } else if (e instanceof PlayerEntity target) {
            if (!cfg.targetPlayers.get()) {
               return false;
            } else if (cfg.ignoreFakePlayers.get() && target instanceof FakePlayerEntity) {
               return false;
            } else {
               if (cfg.survivalOnly.get()) {
                  GameMode mode = EntityUtils.getGameMode(target);
                  if (mode != null && mode != GameMode.SURVIVAL) {
                     return false;
                  }
               }

               if (this.isMarkedDead(target)) {
                  return false;
               } else {
                  return cfg.ignoreNakeds.get() && isNaked(target) ? false : Friends.get().shouldAttack(target);
               }
            }
         } else {
            return cfg.targetMobs.get() && e instanceof HostileEntity;
         }
      } else {
         return false;
      }
   }

   private List<LivingEntity> enemies(AutoCrystal cfg) {
      long tick = this.cacheEpoch;
      if (this.enemiesTick == tick) {
         return this.enemiesCache;
      } else {
         this.enemiesTick = tick;
         this.enemiesCache = this.collectEnemies(cfg, cfg.targetRange.get() * cfg.targetRange.get());
         return this.enemiesCache;
      }
   }

   private List<LivingEntity> collectEnemies(AutoCrystal cfg, double rangeSq) {
      List<LivingEntity> out = new ArrayList<>();
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
         if (cfg.targetPlayers.get()) {
            for (PlayerEntity p : ReviveClient.mc.world.getPlayers()) {
               if (!(p.squaredDistanceTo(ReviveClient.mc.player) > rangeSq) && this.isEnemyTarget(p, cfg)) {
                  out.add(p);
               }
            }
         }

         if (cfg.targetMobs.get()) {
            for (Entity e : ReviveClient.mc.world.getEntities()) {
               if (e instanceof HostileEntity mob && !(mob.squaredDistanceTo(ReviveClient.mc.player) > rangeSq) && this.isEnemyTarget(mob, cfg)) {
                  out.add(mob);
               }
            }
         }

         AutoCrystal.TargetMode mode = cfg.targetMode.get();
         int max = Math.max(1, cfg.maxTargets.get());
         if (mode != AutoCrystal.TargetMode.HighestDamage && out.size() > max) {
            Comparator<LivingEntity> order = mode == AutoCrystal.TargetMode.Closest
               ? Comparator.comparingDouble(ex -> ex.squaredDistanceTo(ReviveClient.mc.player))
               : Comparator.comparingDouble(ex -> ex.getHealth() + ex.getAbsorptionAmount());
            out.sort(order);
            return List.copyOf(out.subList(0, max));
         } else {
            return List.copyOf(out);
         }
      } else {
         return out;
      }
   }

   private void tryBasePlace(AutoCrystal cfg, AutoCrystalFeature.CalcResult calc) {
      if (!this.hasObsidianAvailable()) {
         if (dbgOn(cfg)) {
            this.dbgState(cfg, "base", "base-place skip: no obsidian in inventory");
         }
      } else if (this.bestPlace == null && calc != null && calc.readyBaseWaiting()) {
         if (dbgOn(cfg)) {
            this.dbgState(cfg, "base", "base-place skip: ready base already waiting nearby");
         }
      } else {
         long tick = this.clientTick;
         long posKey = ReviveClient.mc.player.getBlockPos().asLong();
         if (posKey != this.baseScanIdlePos || tick >= this.baseScanIdleUntilTick) {
            AutoCrystalFeature.BasePlaceTarget target = this.findBestBasePlace(cfg);
            if (target == null) {
               this.baseScanIdlePos = posKey;
               this.baseScanIdleUntilTick = tick + 4L;
            } else {
               this.baseScanIdlePos = Long.MIN_VALUE;
               float threshold = cfg.basePlaceThreshold.get().floatValue();
               if (this.bestPlace != null && target.damage() - this.bestPlace.totalDamage() < threshold) {
                  if (dbgOn(cfg)) {
                     this.dbgState(
                        cfg,
                        "base",
                        String.format(
                           Locale.US,
                           "base-place skip: ready spot better (base %.1f vs ready %.1f, need +%.1f)",
                           target.damage(),
                           this.bestPlace.totalDamage(),
                           threshold
                        )
                     );
                  }
               } else {
                  if (cfg.rotatePlace.get()) {
                     boolean owned = this.aimAt(target.hit().getPos());
                     if (cfg.rotationTiming.get() == AutoCrystal.RotationTiming.Pre) {
                        if (!this.outgoingLooksAt(new Box(target.hit().getBlockPos()))) {
                           if (dbgOn(cfg)) {
                              this.dbgState(
                                 cfg,
                                 "base",
                                 owned
                                    ? "base-place skip: this packet already carries our aim (break / packet-break) and it doesn't reach the clicked face - next tick"
                                    : "base-place skip: a higher-priority module holds this tick's rotation - next tick"
                              );
                           }

                           return;
                        }

                        this.stagedPlace = new AutoCrystalFeature.StagedPlace(null, target, "base-place", false);
                        this.markCombatActivity();
                        return;
                     }
                  }

                  if (this.useItemOn(Items.OBSIDIAN, target.hit(), cfg, "base-place")) {
                     this.basePlaceSent(target, cfg);
                  }
               }
            }
         }
      }
   }

   private void basePlaceSent(AutoCrystalFeature.BasePlaceTarget target, AutoCrystal cfg) {
      this.markCombatActivity();
      this.basePlaceTimer = 3;
      this.lastBasePlacePos = target.base();
      this.dbgClear("base");
      this.dbg(cfg, String.format(Locale.US, "base-place SENT obby=%s dmg=%.1f", target.base().toShortString(), target.damage()));
   }

   public boolean isOwnBasePlace(BlockPos pos) {
      if (pos == null || this.lastBasePlacePos == null || ReviveClient.mc.world == null) {
         return false;
      } else {
         return !pos.equals(this.lastBasePlacePos) ? false : ReviveClient.mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN);
      }
   }

   private boolean hasObsidianAvailable() {
      if (ReviveClient.mc.player == null) {
         return false;
      } else {
         return ReviveClient.mc.player.getOffHandStack().isOf(Items.OBSIDIAN) ? true : InvUtils.find(Items.OBSIDIAN).found();
      }
   }

   private AutoCrystalFeature.BasePlaceTarget findBestBasePlace(AutoCrystal cfg) {
      Vec3d eye = ReviveClient.mc.player.getEyePos();
      BlockPos playerPos = ReviveClient.mc.player.getBlockPos();
      double pr = cfg.placeRange.get();
      int r = (int)Math.ceil(pr) + 1;
      BlockPos bestBase = null;
      BlockHitResult bestHit = null;
      float bestDmg = 0.0F;
      Set<BlockPos> mineSlots = this.silentMineActiveSlots();
      int rejNoFace = 0;
      int rejEntity = 0;
      int rejDamage = 0;

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r + 1; y++) {
            for (int z = -r; z <= r; z++) {
               BlockPos basePos = playerPos.add(x, y, z);
               BlockState baseState = ReviveClient.mc.world.getBlockState(basePos);
               if (!baseState.isOf(Blocks.OBSIDIAN)
                  && !baseState.isOf(Blocks.BEDROCK)
                  && (baseState.isAir() || baseState.isReplaceable())
                  && !mineSlots.contains(basePos)) {
                  BlockPos crystalSpot = basePos.up();
                  if (this.isPlaceable(crystalSpot, cfg, false, false)) {
                     Box baseBox = new Box(basePos);
                     boolean occupied = false;

                     for (Entity e : ReviveClient.mc.world.getOtherEntities(null, baseBox)) {
                        if ((e instanceof LivingEntity || e instanceof EndCrystalEntity) && e.isAlive()) {
                           occupied = true;
                           break;
                        }
                     }

                     if (occupied) {
                        rejEntity++;
                     } else {
                        BlockHitResult hit = this.getObsidianPlaceHit(basePos, eye, pr);
                        if (hit == null) {
                           rejNoFace++;
                        } else {
                           float dmg = this.placeDamage(crystalSpot, cfg, basePos, null);
                           if (dmg <= 0.0F) {
                              rejDamage++;
                           } else if (bestBase == null || dmg > bestDmg) {
                              bestBase = basePos.toImmutable();
                              bestHit = hit;
                              bestDmg = dmg;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (bestBase == null) {
         if (dbgOn(cfg)) {
            this.dbgState(cfg, "base", String.format(Locale.US, "base-place scan: 0 valid (noFace=%d entity=%d dmgReject=%d)", rejNoFace, rejEntity, rejDamage));
         }

         return null;
      } else {
         return new AutoCrystalFeature.BasePlaceTarget(bestBase, bestHit, bestDmg);
      }
   }

   private Set<BlockPos> silentMineActiveSlots() {
      Set<BlockPos> slots = new HashSet<>();
      SilentMine sm = SilentMine.get();
      if (sm != null && sm.isActive()) {
         BlockPos delayed = sm.getDelayedDestroyPos();
         if (delayed != null) {
            slots.add(delayed.toImmutable());
         }

         BlockPos doubled = sm.getDoubleMinePos();
         if (doubled != null) {
            slots.add(doubled.toImmutable());
         }

         BlockPos rebreak = sm.getRebreakPos();
         if (rebreak != null) {
            slots.add(rebreak.toImmutable());
         }

         return slots;
      } else {
         return slots;
      }
   }

   private BlockHitResult getObsidianPlaceHit(BlockPos basePos, Vec3d eye, double placeRange) {
      BlockHitResult best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction d : Direction.values()) {
         BlockPos neighbor = basePos.offset(d);
         BlockState ns = ReviveClient.mc.world.getBlockState(neighbor);
         if (!ns.isAir() && !ns.isReplaceable() && !ns.getCollisionShape(ReviveClient.mc.world, neighbor).isEmpty()) {
            Direction face = d.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(neighbor).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            double dist = eye.distanceTo(hitVec);
            if (!(dist > placeRange) && !(dist >= bestDist)) {
               bestDist = dist;
               best = new BlockHitResult(hitVec, face, neighbor, false);
            }
         }
      }

      return best;
   }

   private boolean isPlaceable(BlockPos spot, AutoCrystal cfg, boolean baseMustExist, boolean needBreakReach) {
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
         SilentMine sm = SilentMine.get();
         if (sm != null && sm.isActive() && sm.isOwnSeal(spot)) {
            return false;
         } else {
            BlockPos base = spot.down();
            if (baseMustExist) {
               BlockState baseState = ReviveClient.mc.world.getBlockState(base);
               if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) {
                  return false;
               }
            }

            if (!isCrystalSpotFree(ReviveClient.mc.world.getBlockState(spot), cfg)) {
               return false;
            } else if (this.eyeDistTo(new Box(base)) > cfg.placeRange.get()) {
               return false;
            } else if (needBreakReach && !this.inBreakReach(spot, cfg)) {
               return false;
            } else {
               Box box = new Box(spot.getX(), spot.getY(), spot.getZ(), spot.getX() + 1.0, spot.getY() + 2.0, spot.getZ() + 1.0);
               Iterator var8 = ReviveClient.mc.world.getOtherEntities(null, box).iterator();

               while (true) {
                  if (!var8.hasNext()) {
                     return !this.expectsDropSkip(cfg, spot);
                  }

                  Entity e = (Entity)var8.next();
                  if (!e.isSpectator()
                     && (
                        !(e instanceof EndCrystalEntity crystal)
                           || !this.breakAttemptMs.containsKey(crystal.getId()) && crystal.getId() != this.scanIgnoreCrystalId
                     )) {
                     if (!(e instanceof ItemEntity item)) {
                        break;
                     }

                     boolean ownSpot = this.isOwnMiningSpot(spot);
                     if ((ownSpot || !cfg.ignoreItems.get() || item.age > 5 || this.lastAttackFlushTick != this.flushId)
                        && (!ownSpot || !cfg.ignoreItems.get() || item.age > 5 || !this.ownDropBlownUp(spot))) {
                        if (ownSpot && item.getId() != this.lastXItemLogged) {
                           this.lastXItemLogged = item.getId();
                           this.dbg(cfg, spot.toShortString() + " skip: item in 2-high box id=" + item.getId() + " age=" + item.age);
                        }
                        break;
                     }
                  }
               }

               return false;
            }
         }
      } else {
         return false;
      }
   }

   private boolean isOwnMiningSpot(BlockPos spot) {
      if (this.queuedPlace != null && this.queuedPlace.pos().equals(spot)) {
         return true;
      } else {
         SilentMine sm = SilentMine.get();
         return sm != null && sm.isActive() && sm.isOwnMiningPos(spot);
      }
   }

   private boolean inBreakReach(BlockPos spot, AutoCrystal cfg) {
      Box crystalBox = new Box(spot.getX() - 0.5, spot.getY(), spot.getZ() - 0.5, spot.getX() + 1.5, spot.getY() + 2.0, spot.getZ() + 1.5);
      return this.eyeDistTo(crystalBox) <= cfg.breakRange.get();
   }

   private static boolean isCrystalSpotFree(BlockState state, AutoCrystal cfg) {
      return !state.isAir() && !state.isOf(Blocks.FIRE) && !state.isOf(Blocks.SOUL_FIRE)
         ? cfg.ignoreTrapdoors.get() && state.getBlock() instanceof TrapdoorBlock && !state.isOf(Blocks.IRON_TRAPDOOR)
         : true;
   }

   private AutoCrystalSnapshot doSnapshot(long tickId, AutoCrystal cfg, EndCrystalEntity breaking) {
      AutoCrystalSnapshot.Context ctx = this.context(cfg);
      this.scanIgnoreCrystalId = breaking != null ? breaking.getId() : Integer.MIN_VALUE;
      BlockPos playerPos = ReviveClient.mc.player.getBlockPos();
      int r = (int)Math.ceil(cfg.placeRange.get()) + 1;
      Set<BlockPos> crawlCeilings = cfg.cevHead.get() ? Set.of() : this.enemyCrawlCeilings(cfg);
      List<BlockPos> candidates = new ArrayList<>();
      List<BlockPos> approach = new ArrayList<>();

      for (int x = -r; x <= r; x++) {
         for (int y = -r; y <= r + 2; y++) {
            for (int z = -r; z <= r; z++) {
               BlockPos spot = playerPos.add(x, y, z);
               if (this.isPlaceable(spot, cfg, true, false) && !this.baseBreaksFirst(spot, cfg)) {
                  if (!crawlCeilings.isEmpty() && crawlCeilings.contains(spot.down())) {
                     if (dbgOn(cfg)) {
                        this.dbgState(
                           cfg,
                           "ceil-spot " + spot.asLong(),
                           "spot skip " + spot.toShortString() + ": base is crawler ceiling " + spot.down().toShortString() + " (cev-head off)"
                        );
                     }
                  } else if (this.inBreakReach(spot, cfg)) {
                     candidates.add(spot);
                  } else {
                     approach.add(spot);
                  }
               }
            }
         }
      }

      this.scanIgnoreCrystalId = Integer.MIN_VALUE;
      BlockPos[] spots = candidates.toArray(new BlockPos[0]);
      this.debugSpots = spots;
      return new AutoCrystalSnapshot(tickId, ctx, spots, approach.toArray(new BlockPos[0]), this.preferredAntiFeetTrapPositions(cfg), this.backedOffSpots());
   }

   private boolean baseBreaksFirst(BlockPos spot, AutoCrystal cfg) {
      if (!placeIsStaged(cfg)) {
         return false;
      } else {
         this.context(cfg);
         if (this.passthroughPrimary != null && spot.down().equals(this.passthroughPrimary)) {
            if (dbgOn(cfg)) {
               this.dbgState(
                  cfg,
                  "base-breaks " + spot.asLong(),
                  "spot skip "
                     + spot.toShortString()
                     + ": base "
                     + this.passthroughPrimary.toShortString()
                     + " breaks first (its STOP goes out before this place)"
               );
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean placeIsStaged(AutoCrystal cfg) {
      return cfg.rotatePlace.get() && cfg.rotationTiming.get() == AutoCrystal.RotationTiming.Pre;
   }

   private Set<BlockPos> enemyCrawlCeilings(AutoCrystal cfg) {
      Set<BlockPos> out = null;

      for (LivingEntity e : this.enemies(cfg)) {
         if (e instanceof PlayerEntity p && p.isCrawling()) {
            if (out == null) {
               out = new HashSet<>();
            }

            out.add(p.getBlockPos().up());
         }
      }

      return out != null ? out : Set.of();
   }

   private Set<BlockPos> backedOffSpots() {
      if (this.spotBackoffUntil.isEmpty()) {
         return Set.of();
      } else {
         Set<BlockPos> out = new HashSet<>();

         for (Entry<Long, Long> e : this.spotBackoffUntil.entrySet()) {
            if (this.clientTick < e.getValue()) {
               out.add(BlockPos.fromLong(e.getKey()));
            }
         }

         return Set.copyOf(out);
      }
   }

   private boolean isSpotBackedOff(BlockPos spot) {
      Long until = this.spotBackoffUntil.get(spot.asLong());
      return until != null && this.clientTick < until;
   }

   private static AutoCrystalFeature.CalcResult findBestPlace(AutoCrystalSnapshot snap, AutoCrystalSnapshot.DebugInfo dbg, float rebreakThreshold) {
      AutoCrystalSnapshot.Context ctx = snap.ctx();
      Set<BlockPos> preferredPos = snap.preferredPos();
      Set<BlockPos> backedOff = snap.backedOffSpots();
      dbg.targets = ctx.enemies().size();
      dbg.candidatesTotal = snap.candidates().length;
      dbg.candidatesOutBreakRange = snap.approachCandidates().length;
      AutoCrystalFeature.PlaceTarget preferred = null;
      AutoCrystalFeature.PlaceTarget best = null;
      boolean backedOffWaiting = false;

      for (BlockPos spot : snap.candidates()) {
         float dmg = ctx.evaluate(crystalPos(spot), false, ctx.world(), dbg);
         if (!(dmg < 0.0F)) {
            if (backedOff.contains(spot)) {
               backedOffWaiting = true;
            } else {
               if (best == null || dmg > best.totalDamage()) {
                  best = new AutoCrystalFeature.PlaceTarget(spot, dmg);
               }

               if (preferredPos.contains(spot) && (preferred == null || dmg > preferred.totalDamage())) {
                  preferred = new AutoCrystalFeature.PlaceTarget(spot, dmg);
               }
            }
         }
      }

      if (preferred != null && (best == null || best.totalDamage() - preferred.totalDamage() <= rebreakThreshold)) {
         best = preferred;
      }

      boolean readyWaiting = best != null || backedOffWaiting;
      if (!readyWaiting) {
         for (BlockPos spotx : snap.approachCandidates()) {
            if (ctx.evaluate(crystalPos(spotx), false, ctx.world(), null) > 0.0F) {
               readyWaiting = true;
               break;
            }
         }
      }

      if (best != null) {
         dbg.bestFound = 1;
      }

      return new AutoCrystalFeature.CalcResult(best, readyWaiting);
   }

   private AutoCrystalSnapshot.Context context(AutoCrystal cfg) {
      long tick = this.cacheEpoch;
      if (this.ctxCache != null && this.ctxTick == tick) {
         return this.ctxCache;
      } else {
         boolean assumeBest = cfg.assumeBestArmor.get();
         List<AutoCrystalSnapshot.TargetData> enemyData = new ArrayList<>();

         for (LivingEntity e : this.enemies(cfg)) {
            enemyData.add(targetData(e, assumeBest, this.shouldFacePlace(e, cfg)));
         }

         List<Vec3d> chain = new ArrayList<>();

         for (EndCrystalEntity c : this.endCrystals()) {
            if (c.isAlive()) {
               chain.add(c.getEntityPos());
            }
         }

         SilentMine sm = SilentMine.get();
         Set<BlockPos> seals = sm != null && sm.isActive() ? sm.standingSealPositions() : Set.of();
         DamageUtil.BlockRaycastProvider world = DamageUtil.blockCheck(ReviveClient.mc.world);
         if (!seals.isEmpty()) {
            world = sealsSolid(world, seals);
         }

         this.ctxCache = new AutoCrystalSnapshot.Context(
            targetData(ReviveClient.mc.player, false, false),
            ReviveClient.mc.player.getHealth() + ReviveClient.mc.player.getAbsorptionAmount(),
            List.copyOf(enemyData),
            List.copyOf(chain),
            this.ignoredBlocks(cfg),
            world,
            cfg.minPlace.get(),
            cfg.maxPlace.get(),
            cfg.minBreak.get(),
            cfg.maxBreak.get(),
            cfg.noSelfPop.get()
         );
         this.ctxTick = tick;
         return this.ctxCache;
      }
   }

   private static AutoCrystalSnapshot.TargetData targetData(LivingEntity e, boolean assumeBest, boolean facePlace) {
      return new AutoCrystalSnapshot.TargetData(e.getId(), e.getEntityPos(), e.getBoundingBox(), DamageUtil.explosionProfile(e, assumeBest), facePlace);
   }

   private float placeDamage(BlockPos spot, AutoCrystal cfg, BlockPos pretendSolid, BlockPos pretendAir) {
      AutoCrystalSnapshot.Context ctx = this.context(cfg);
      return ctx.evaluate(crystalPos(spot), false, pretend(ctx.world(), pretendSolid, pretendAir), null);
   }

   private void dbgBestPlace(AutoCrystal cfg, BlockPos spot, float priced) {
      AutoCrystalSnapshot.Context ctx = this.context(cfg);
      AutoCrystalSnapshot.Context vanilla = ctx.withIgnored(Set.of());
      Vec3d pos = crystalPos(spot);
      float real = vanilla.evaluate(pos, false, ctx.world(), null);
      BlockPos primary = this.passthroughPrimary != null && ctx.ignored().contains(this.passthroughPrimary) ? this.passthroughPrimary : null;
      String tag = "";
      if (real < 0.0F) {
         if (primary != null && vanilla.evaluate(pos, false, pretend(ctx.world(), null, primary), null) >= 0.0F) {
            tag = " (pre-STOP " + primary.toShortString() + ")";
         } else {
            Set<BlockPos> terrain = new HashSet<>(ctx.ignored());
            if (primary != null) {
               terrain.remove(primary);
            }

            tag = !terrain.isEmpty() && ctx.withIgnored(terrain).evaluate(pos, false, ctx.world(), null) >= 0.0F ? " (ignore-terrain)" : " PHANTOM";
         }

         real = vanilla.maxEnemyDamage(pos, ctx.world());
      }

      this.dbgOnChange(
         cfg,
         "best",
         spot.asLong() + tag,
         String.format(
            Locale.US, "bestPlace %s priced=%.1f real=%.1f pt=[%s]%s", spot.toShortString(), priced, real, primary != null ? primary.toShortString() : "", tag
         )
      );
   }

   private float breakDamage(Vec3d pos, AutoCrystal cfg) {
      AutoCrystalSnapshot.Context ctx = this.context(cfg);
      return ctx.evaluate(pos, true, ctx.world(), null);
   }

   private boolean isSelfSafe(Vec3d crystalPos, AutoCrystal cfg, boolean breaking) {
      AutoCrystalSnapshot.Context ctx = this.context(cfg);
      return !ctx.rejectsSelf(ctx.selfDamage(crystalPos, ctx.world()), breaking);
   }

   private static DamageUtil.BlockRaycastProvider pretend(DamageUtil.BlockRaycastProvider provider, BlockPos solid, BlockPos air) {
      return solid == null && air == null ? provider : (c, p) -> {
         if (air != null && p.equals(air)) {
            return null;
         } else {
            return solid != null && p.equals(solid) ? VoxelShapes.fullCube().raycast(c.start(), c.end(), p) : provider.apply(c, p);
         }
      };
   }

   private static DamageUtil.BlockRaycastProvider sealsSolid(DamageUtil.BlockRaycastProvider provider, Set<BlockPos> seals) {
      return (c, p) -> seals.contains(p) ? VoxelShapes.fullCube().raycast(c.start(), c.end(), p) : provider.apply(c, p);
   }

   private static Vec3d crystalPos(BlockPos spot) {
      return new Vec3d(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
   }

   private Set<BlockPos> ignoredBlocks(AutoCrystal cfg) {
      long epoch = this.cacheEpoch;
      long posKey = ReviveClient.mc.player.getBlockPos().asLong();
      if (this.ignoredCache != null && this.ignoredCacheTick == epoch && this.ignoredCachePos == posKey) {
         return this.ignoredCache;
      } else {
         Set<BlockPos> ignored = cfg.ignoreTerrain.get() ? new HashSet<>(this.terrainPassthrough(cfg, this.clientTick, posKey)) : new HashSet<>();
         SilentMine sm = cfg.ignoreTerrain.get() ? SilentMine.get() : null;
         if (sm != null && sm.isActive()) {
            ignored.removeAll(sm.ownSealPositions());
         }

         this.addMinedPassthrough(ignored, cfg);
         this.ignoredCache = ignored;
         this.ignoredCacheTick = epoch;
         this.ignoredCachePos = posKey;
         return ignored;
      }
   }

   private Set<BlockPos> terrainPassthrough(AutoCrystal cfg, long tick, long posKey) {
      if (this.terrainCache != null && this.terrainCachePos == posKey && tick - this.terrainCacheTick < 5L && tick >= this.terrainCacheTick) {
         return this.terrainCache;
      } else {
         Set<BlockPos> terrain = new HashSet<>();
         int r = cfg.placeRange.get().intValue() + 2;
         BlockPos center = ReviveClient.mc.player.getBlockPos();

         for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos pos = center.add(x, y, z);
                  BlockState state = ReviveClient.mc.world.getBlockState(pos);
                  if (!state.isAir() && state.getBlock().getBlastResistance() < 600.0F) {
                     terrain.add(pos.toImmutable());
                  }
               }
            }
         }

         this.terrainCache = terrain;
         this.terrainCacheTick = tick;
         this.terrainCachePos = posKey;
         return terrain;
      }
   }

   private void addMinedPassthrough(Set<BlockPos> out, AutoCrystal cfg) {
      BlockPos passed = null;
      SilentMine sm = SilentMine.get();
      BlockPos primary = cfg.antiSurround.get() && sm != null && sm.isActive() ? sm.getDelayedDestroyPos() : null;
      double ticks = primary != null ? sm.getPrimaryTicksToBreak() : -1.0;
      boolean due = primary != null && ticks >= 0.0 && ticks <= 1.0 && sm.getPrimaryProgress() >= cfg.mineThreshold.get();
      boolean sent = due && sm.isPrimaryStopSent();
      boolean sealed = due && (primary.equals(this.sealPlannedX) || sm.isOwnSeal(primary));
      boolean priced = due && !sealed && (sent || ticks >= 1.0);
      if (due) {
         passed = primary.toImmutable();
      }

      if (priced) {
         out.add(passed);
      }

      if (cfg.debug.get() && primary != null) {
         String verdict = priced ? "in" : (sealed ? "due, not priced (glass seal goes in)" : (due ? "due, not priced (STOP deferred)" : "out"));
         this.dbgOnChange(
            cfg,
            "passthrough",
            primary.asLong() + " " + due + "/" + priced,
            String.format(
               Locale.US, "passthrough primary %s ttb=%.0f prog=%.2f sent=%s -> %s", primary.toShortString(), ticks, sm.getPrimaryProgress(), sent, verdict
            )
         );
      }

      this.passthroughPrimary = passed;
   }

   private Set<BlockPos> preferredAntiFeetTrapPositions(AutoCrystal cfg) {
      if (!cfg.antiSurround.get()) {
         return Set.of();
      } else {
         SilentMine sm = SilentMine.get();
         if (sm != null && sm.isActive()) {
            AutoMine autoMine = AutoMine.get();
            PlayerEntity target = autoMine != null ? autoMine.getTargetPlayer() : null;
            Set<BlockPos> preferred = new LinkedHashSet<>();
            this.addPreferredAboveMineBlock(preferred, sm.getDelayedDestroyPos(), sm.getPrimaryProgress(), target, cfg);
            this.addPreferredAboveMineBlock(preferred, sm.getDoubleMinePos(), sm.getDoubleMineProgress(), target, cfg);
            BlockPos rebreak = sm.getRebreakPos();
            if (rebreak != null && ReviveClient.mc.world.getBlockState(rebreak).isAir()) {
               preferred.add(rebreak.toImmutable());
               if (dbgOn(cfg)) {
                  this.dbgState(cfg, "pref-x", "preferred add X " + rebreak.toShortString() + " (air)");
               }
            }

            return Set.copyOf(preferred);
         } else {
            return Set.of();
         }
      }
   }

   private void addPreferredAboveMineBlock(Set<BlockPos> preferred, BlockPos minePos, double progress, PlayerEntity target, AutoCrystal cfg) {
      if (minePos != null) {
         if (!(progress <= 0.0) && !(progress >= 1.0)) {
            if (target != null && target.getBoundingBox().maxY <= minePos.getY() + 1.0) {
               if (dbgOn(cfg)) {
                  this.dbgState(
                     cfg,
                     "pref " + minePos.asLong(),
                     String.format(
                        Locale.US,
                        "preferred skip %s.up(): target box top %.2f <= %d",
                        minePos.toShortString(),
                        target.getBoundingBox().maxY,
                        minePos.getY() + 1
                     )
                  );
               }
            } else if (target != null && !cfg.cevHead.get() && minePos.equals(target.getBlockPos().up())) {
               if (dbgOn(cfg)) {
                  this.dbgState(cfg, "pref " + minePos.asLong(), "preferred skip ceiling " + minePos.toShortString() + ": cev-head off");
               }
            } else {
               preferred.add(minePos.up().toImmutable());
            }
         }
      }
   }

   private AutoCrystalFeature.ComboPlan planCombo(AutoCrystal cfg) {
      AutoCrystalFeature.FollowUp pending = this.followUp;
      this.followUp = null;
      if (!cfg.antiSurround.get()) {
         return null;
      } else {
         AutoMine autoMine = AutoMine.get();
         SilentMine sm = SilentMine.get();
         if (autoMine != null && sm != null && sm.isActive() && sm.isRunningHere() && autoMine.glassSealActive()) {
            if (this.isValidAutoCrystalTarget(autoMine.getTargetPlayer(), cfg) && this.hasAnyCrystal()) {
               if (System.nanoTime() - this.lastTeleportNs < 100000000L) {
                  BlockPos held = sealTarget(autoMine, sm);
                  if (held != null && this.glassSealOwns(held)) {
                     sm.claimStop(held);
                  }

                  if (dbgOn(cfg)) {
                     this.dbgState(
                        cfg, "combo", "combo wait: setback just now (the server drops places until it has our teleport confirm; a STOP would still land)"
                     );
                  }

                  return null;
               } else if (pending != null && pending.tick() == this.clientTick - 1L) {
                  return new AutoCrystalFeature.ComboPlan(AutoCrystalFeature.ComboKind.FILL, pending.x(), this.sealGlass(cfg, autoMine), 0, true);
               } else {
                  BlockPos x = sealTarget(autoMine, sm);
                  if (x == null) {
                     return null;
                  } else if (!ReviveClient.mc
                     .world
                     .getEntitiesByClass(EndCrystalEntity.class, new Box(x), c -> c.isAlive() && c.getBlockPos().equals(x))
                     .isEmpty()) {
                     return null;
                  } else if (!ReviveClient.mc.world.getBlockState(x).isAir()) {
                     SilentMine.StopKind kind = sm.stopKind(x);
                     int dueIn = sm.ticksUntilStopDue(x, -1);
                     if (kind == SilentMine.StopKind.OPEN) {
                        return dueIn != 0 && dueIn != 1 ? null : new AutoCrystalFeature.ComboPlan(AutoCrystalFeature.ComboKind.OPEN, x, null, dueIn, false);
                     } else if ((kind == SilentMine.StopKind.PRIMARY || kind == SilentMine.StopKind.REBREAK) && dueIn >= 0 && dueIn <= 1) {
                        Item glass = this.sealGlass(cfg, autoMine);
                        String skip = glass == null
                           ? "no glass (glass-source " + autoMine.glassSource() + ")"
                           : (
                              sm.isSealPaused(x)
                                 ? "the server refused the last seal there"
                                 : (
                                    !this.glassPlaceFree(x, null)
                                       ? "an entity in it would block the glass"
                                       : (
                                          x.equals(this.comboFallbackX) && this.clientTick < this.comboFallbackUntil
                                             ? "starved, SilentMine sends its own STOP for now"
                                             : null
                                       )
                                 )
                           );
                        if (skip != null) {
                           if (dbgOn(cfg)) {
                              this.dbgState(cfg, "combo", "combo SEAL " + x.toShortString() + " skipped: " + skip + " - inner / outer crystal as before");
                           }

                           return null;
                        } else {
                           if (dueIn == 0) {
                              if (x.equals(this.comboWaitX)) {
                                 this.comboWaitTicks++;
                              } else {
                                 this.comboWaitX = x.toImmutable();
                                 this.comboWaitTicks = 1;
                              }

                              if (this.comboWaitTicks > 10) {
                                 this.comboFallbackX = this.comboWaitX;
                                 this.comboFallbackUntil = this.clientTick + 20L;
                                 this.comboWaitX = null;
                                 this.dbg(
                                    cfg,
                                    "combo aborted "
                                       + x.toShortString()
                                       + ": SEAL due 10 ticks without going out (look not on the wire, or no budget for the STOP and its glass) - SilentMine's own STOP for 20 ticks"
                                 );
                                 return null;
                              }

                              this.sealPlannedX = x.toImmutable();
                           }

                           sm.claimStop(x);
                           return new AutoCrystalFeature.ComboPlan(AutoCrystalFeature.ComboKind.SEAL, x.toImmutable(), glass, dueIn, false);
                        }
                     } else {
                        return null;
                     }
                  } else if (!sm.isOwnSeal(x) && x.equals(sm.getRebreakPos()) && !this.recentOwnChange(x)) {
                     Item fillGlass = this.sealGlass(cfg, autoMine);
                     if (fillGlass == null) {
                        if (dbgOn(cfg)) {
                           this.dbgState(
                              cfg,
                              "combo",
                              "combo FILL "
                                 + x.toShortString()
                                 + " skipped: no glass (glass-source "
                                 + autoMine.glassSource()
                                 + ") - the crystal into it as before"
                           );
                        }

                        return null;
                     } else {
                        return new AutoCrystalFeature.ComboPlan(AutoCrystalFeature.ComboKind.FILL, x, fillGlass, 0, false);
                     }
                  } else {
                     return null;
                  }
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   private boolean tryCombo(AutoCrystal cfg) {
      AutoCrystalFeature.ComboPlan plan = this.comboPlan;
      AutoMine autoMine = AutoMine.get();
      if (plan != null && autoMine != null) {
         BlockPos x = plan.x();
         BlockPos base = x.down();
         String head = "combo " + plan.kind() + " " + x.toShortString();
         Item item = plan.glass();
         float damage = 0.0F;
         if (plan.kind() != AutoCrystalFeature.ComboKind.SEAL) {
            AutoCrystalFeature.CrystalGate gate = this.comboCrystalGate(x, cfg, null);
            boolean glassOk = this.fillGlassOk(plan, autoMine);
            if (gate.why() == null) {
               item = Items.END_CRYSTAL;
               damage = gate.damage();
            } else if (!glassOk) {
               this.dbgOnChange(
                  cfg,
                  "combo-hold",
                  x.asLong() + " " + plan.kind() + " " + gate.sig(),
                  head
                     + " hold: crystal can't go ("
                     + gate.why()
                     + ")"
                     + (
                        plan.kind() == AutoCrystalFeature.ComboKind.OPEN
                           ? " - the seal keeps the enemy out"
                           : (item == null ? ", no glass" : ", glass-reseal off")
                     )
               );
               return false;
            }
         }

         this.dbgOnChange(
            cfg,
            "combo-plan",
            x.asLong() + " " + plan.kind() + " " + plan.dueIn() + " " + item,
            head
               + " planned"
               + (plan.dueIn() > 0 ? " (pre-aim, STOP due next tick)" : "")
               + (plan.kind() == AutoCrystalFeature.ComboKind.FILL ? " -> " + (item == Items.END_CRYSTAL ? "crystal" : "glass") : "")
         );
         Vec3d p = plan.aim();
         if (cfg.rotatePlace.get()) {
            this.cancelAimYield(cfg, "the glass seal's " + plan.kind() + " at " + x.toShortString() + " needs this packet's look");
            this.aimAt(p);
         }

         if (plan.dueIn() > 0) {
            return false;
         } else {
            boolean lastHit = this.serverLooksAt(x) && this.serverLooksAt(base);
            boolean outHit = this.outgoingLooksAt(new Box(x)) && this.outgoingLooksAt(new Box(base));
            if (!placeIsStaged(cfg) || lastHit && outHit) {
               if (!this.reserveInteractionBudget()) {
                  if (dbgOn(cfg)) {
                     this.dbgState(cfg, "combo", head + " wait: BLOCK_INTERACT budget spent");
                  }

                  return false;
               } else {
                  int stopClicks = this.sealStopClicks(plan, autoMine);
                  if (!this.canUseItem(item, cfg, stopClicks)) {
                     if (dbgOn(cfg)) {
                        this.dbgState(
                           cfg,
                           "combo",
                           head
                              + " wait: "
                              + item
                              + " can't be used now (swap-mode / slot / container-click budget"
                              + (stopClicks > 0 ? ", behind the STOP's " + stopClicks + "-click tool swap" : "")
                              + ")"
                        );
                     }

                     return false;
                  } else {
                     this.stagedCombo = new AutoCrystalFeature.StagedCombo(plan, item, damage);
                     this.markCombatActivity();
                     return true;
                  }
               }
            } else {
               if (dbgOn(cfg)) {
                  this.dbgState(cfg, "combo", head + " wait: look not on the wire yet (lastLookHit=" + lastHit + " outHit=" + outHit + ")");
               }

               return false;
            }
         }
      } else {
         return false;
      }
   }

   private void sendCombo(AutoCrystalFeature.StagedCombo combo, AutoCrystal cfg, float yaw, float pitch, String lostReason) {
      AutoCrystalFeature.ComboPlan plan = combo.plan();
      BlockPos x = plan.x();
      BlockPos base = x.down();
      SilentMine sm = SilentMine.get();
      AutoMine autoMine = AutoMine.get();
      if (sm != null && sm.isActive() && autoMine != null) {
         String head = "combo " + plan.kind() + " " + x.toShortString();
         Vec3d eye = sm.serverEye();
         boolean lastHit = this.serverLooksAt(eye, x) && this.serverLooksAt(eye, base);
         boolean outX = ReviveClient.ROTATION.raytraceCheck(eye, yaw, pitch, new Box(x));
         boolean outBase = ReviveClient.ROTATION.raytraceCheck(eye, yaw, pitch, new Box(base));
         boolean outHit = outX && outBase;
         String looks = " lastLookHit=" + lastHit + " outHit=" + outX + "," + outBase;
         if (!placeIsStaged(cfg) || lastHit && outHit) {
            Vec3d p = plan.aim();
            BlockHitResult hit = new BlockHitResult(p, Direction.UP, base, false);
            boolean nextTick = autoMine.glassOpenNextTick() || this.openOrderSwitched;
            boolean crystal = combo.item() == Items.END_CRYSTAL;
            switch (plan.kind()) {
               case SEAL:
                  int stopClicks = this.sealStopClicks(plan, autoMine);
                  if (this.reserveInteractionBudget() && this.canUseItem(combo.item(), cfg, stopClicks)) {
                     if (!this.comboStop(sm, x, -1, eye, p)) {
                        this.dbg(cfg, head + " aborted: SilentMine sent no STOP (see its log)" + looks);
                        return;
                     }

                     this.comboChanged(x);
                     this.comboWaitX = null;
                     this.cycleCombo(cfg, x, AutoCrystalFeature.ComboKind.SEAL, true);
                     if (nextTick) {
                        this.followUp = new AutoCrystalFeature.FollowUp(x, this.clientTick);
                        this.dbg(cfg, "combo sent " + x.toShortString() + ": stop (glass next tick, glass-open-order NextTick)" + looks);
                        return;
                     }

                     if (!this.useItemOn(combo.item(), hit, cfg, "seal")) {
                        this.dbg(cfg, head + " aborted after its STOP: the glass didn't go (see the use skip)" + looks);
                        return;
                     }

                     this.sealSent(cfg, x, combo.item(), true, false, false);
                     this.dbg(cfg, "combo sent " + x.toShortString() + ": stop+glass" + looks);
                     break;
                  }

                  if (dbgOn(cfg)) {
                     this.dbgState(
                        cfg,
                        "combo",
                        head
                           + " wait: the glass can't follow the STOP now (BLOCK_INTERACT budget, swap state or ClickSlot room changed since the tick"
                           + (stopClicks > 0 ? ", behind the STOP's " + stopClicks + "-click tool swap" : "")
                           + ")"
                     );
                  }

                  return;
               case OPEN:
                  if (nextTick) {
                     if (!this.comboStop(sm, x, -1, eye, p)) {
                        this.dbg(cfg, head + " aborted: SilentMine sent no STOP (see its log)" + looks);
                        return;
                     }

                     this.comboChanged(x);
                     this.cycleCombo(cfg, x, AutoCrystalFeature.ComboKind.OPEN, true);
                     this.followUp = new AutoCrystalFeature.FollowUp(x, this.clientTick);
                     this.dbg(cfg, "combo sent " + x.toShortString() + ": stop (crystal next tick, glass-open-order NextTick)" + looks);
                     return;
                  }

                  if (!this.canUseItem(Items.END_CRYSTAL, cfg)) {
                     if (dbgOn(cfg)) {
                        this.dbgState(
                           cfg,
                           "combo",
                           head + " wait: the crystal can't follow the STOP now (swap state, ClickSlot room or Grim click-while-moving changed since the tick)"
                        );
                     }

                     return;
                  }

                  boolean[] stopped = new boolean[]{false};
                  boolean placed = this.useItemOn(Items.END_CRYSTAL, hit, cfg, "open", slot -> stopped[0] = this.comboStop(sm, x, slot, eye, p));
                  if (!stopped[0]) {
                     this.dbg(cfg, head + " aborted: no STOP (SilentMine refused it, or the crystal couldn't be taken in hand)" + looks);
                     return;
                  }

                  this.comboChanged(x);
                  this.cycleCombo(cfg, x, AutoCrystalFeature.ComboKind.OPEN, true);
                  if (!placed) {
                     this.dbg(cfg, head + " aborted after its STOP: the crystal didn't go" + looks);
                     return;
                  }

                  this.crystalPlaceSent(new AutoCrystalFeature.PlaceTarget(x, combo.damage()), cfg, "open", false);
                  this.expectAnswer(x, AutoCrystalFeature.ComboKind.OPEN, true);
                  this.dbg(cfg, "combo sent " + x.toShortString() + ": stop+crystal" + looks);
                  break;
               case FILL:
                  if (!this.useItemOn(combo.item(), hit, cfg, crystal ? "fill" : "fill glass")) {
                     this.dbg(cfg, head + " aborted: the " + (crystal ? "crystal" : "glass") + " didn't go" + looks);
                     return;
                  }

                  if (crystal) {
                     this.crystalPlaceSent(new AutoCrystalFeature.PlaceTarget(x, combo.damage()), cfg, "fill", false);
                     this.expectAnswer(x, AutoCrystalFeature.ComboKind.FILL, false);
                     this.comboChanged(x);
                  } else {
                     this.sealSent(cfg, x, combo.item(), false, plan.followUp(), false);
                  }

                  this.dbg(
                     cfg, "combo sent " + x.toShortString() + ": " + (crystal ? "crystal" : "glass") + (plan.followUp() ? " (NextTick follow-up)" : "") + looks
                  );
            }
         } else {
            this.dbg(cfg, head + " aborted: the move packet's look misses X or its base (" + lostReason + ")" + looks);
         }
      }
   }

   private int sealStopClicks(AutoCrystalFeature.ComboPlan plan, AutoMine autoMine) {
      if (plan.kind() == AutoCrystalFeature.ComboKind.SEAL && !autoMine.glassOpenNextTick() && !this.openOrderSwitched) {
         SilentMine sm = SilentMine.get();
         return sm != null && sm.isActive() ? sm.comboStopClicks(plan.x(), -1) : 0;
      } else {
         return 0;
      }
   }

   private boolean comboStop(SilentMine sm, BlockPos x, int heldSlot, Vec3d eye, Vec3d aim) {
      this.comboStopping = true;

      boolean var6;
      try {
         var6 = sm.comboStop(x, heldSlot, stopFace(eye, x, aim));
      } finally {
         this.comboStopping = false;
      }

      return var6;
   }

   private void sealSent(AutoCrystal cfg, BlockPos x, Item glass, boolean sameFlush, boolean afterStop, boolean behindHit) {
      SilentMine sm = SilentMine.get();
      if (sm != null && glass instanceof BlockItem blockItem) {
         sm.notePendingSeal(x, blockItem.getBlock(), sameFlush, afterStop, behindHit);
      }

      this.cycleCombo(cfg, x, AutoCrystalFeature.ComboKind.SEAL, false);
      this.expectAnswer(x, AutoCrystalFeature.ComboKind.SEAL, sameFlush);
      this.comboChanged(x);
      this.markCombatActivity();
   }

   private String comboHitHold(EndCrystalEntity crystal) {
      BlockPos x = this.answerX;
      if (x != null
         && this.answerKind != AutoCrystalFeature.ComboKind.SEAL
         && !crystal.getBlockPos().equals(x)
         && crystal.getEntityPos().squaredDistanceTo(Vec3d.ofBottomCenter(x)) <= 144.0) {
         return "our crystal into " + x.toShortString() + " is on its way (an explosion within 12 blocks removes it)";
      } else {
         SilentMine sm = SilentMine.get();
         if (sm == null) {
            return null;
         } else {
            for (BlockPos seal : sm.ownSealPositions()) {
               if (sm.sealPending(seal) && !crystal.getBlockPos().equals(seal) && this.explosionClears(crystal.getEntityPos(), seal)) {
                  return "its explosion would break our glass going into " + seal.toShortString() + " (not answered yet)";
               }
            }

            return null;
         }
      }
   }

   private void comboChanged(BlockPos x) {
      this.comboLastX = x.toImmutable();
      this.comboLastTick = this.clientTick;
   }

   private boolean sealFollowUp(EndCrystalEntity crystal, AutoCrystal cfg) {
      if (!cfg.antiSurround.get()) {
         return false;
      } else {
         AutoMine autoMine = AutoMine.get();
         SilentMine sm = SilentMine.get();
         if (autoMine != null && sm != null && sm.isActive() && sm.isRunningHere() && autoMine.glassSealActive()) {
            BlockPos x = sealTarget(autoMine, sm);
            if (x == null) {
               return false;
            } else {
               boolean inX = crystal.getBlockPos().equals(x);
               if (x.equals(sm.getRebreakPos()) || inX && primaryStopOut(x, sm)) {
                  if (inX || sm.sealConfirmed(x) && this.explosionClears(crystal.getEntityPos(), x)) {
                     if (inX && !sm.isOwnSeal(x) && this.sealGlass(cfg, autoMine) == null) {
                        return false;
                     } else {
                        Box baseBox = new Box(x.down());
                        if (!cfg.rotatePlace.get()
                           || cfg.rotationTiming.get() != AutoCrystal.RotationTiming.Pre
                           || this.serverLooksAt(x.down()) && this.outgoingLooksAt(baseBox)) {
                           String head = (inX ? "hit in X " : "hit by our seal ") + x.toShortString();
                           BlockHitResult hit = new BlockHitResult(comboAimPoint(x), Direction.UP, x.down(), false);
                           AutoCrystalFeature.CrystalGate gate = this.placeSpeedOk(cfg)
                              ? this.comboCrystalGate(x, cfg, crystal)
                              : AutoCrystalFeature.CrystalGate.blocked("place-speed-limit");
                           if (gate.why() != null) {
                              Item glass = autoMine.glassReseal() ? this.sealGlass(cfg, autoMine) : null;
                              if (glass == null || sm.isSealPaused(x) || !this.glassPlaceFree(x, crystal)) {
                                 this.dbgOnChange(
                                    cfg,
                                    "seal-follow",
                                    x.asLong() + " " + inX + " " + gate.sig() + " " + glass,
                                    head
                                       + ": nothing behind the hit (crystal: "
                                       + gate.why()
                                       + (glass == null ? ", no glass / glass-reseal off" : ", glass blocked")
                                       + ")"
                                 );
                                 return false;
                              } else if (!this.useItemOn(glass, hit, cfg, "reseal")) {
                                 return false;
                              } else {
                                 this.sealSent(cfg, x, glass, false, false, true);
                                 this.dbg(cfg, head + ": reseal behind the hit (crystal: " + gate.why() + ")");
                                 return true;
                              }
                           } else if (!this.useItemOn(Items.END_CRYSTAL, hit, cfg, "packet-place X")) {
                              return false;
                           } else {
                              this.crystalPlaceSent(new AutoCrystalFeature.PlaceTarget(x, gate.damage()), cfg, "packet-place X", false);
                              this.expectAnswer(x, AutoCrystalFeature.ComboKind.FILL, false);
                              this.comboChanged(x);
                              return true;
                           }
                        } else {
                           return false;
                        }
                     }
                  } else {
                     return false;
                  }
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      }
   }

   private AutoCrystalFeature.CrystalGate comboCrystalGate(BlockPos x, AutoCrystal cfg, EndCrystalEntity hit) {
      BlockState baseState = ReviveClient.mc.world.getBlockState(x.down());
      if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) {
         return AutoCrystalFeature.CrystalGate.blocked("no obsidian / bedrock base");
      } else if (!this.canCrystalAt(x)) {
         return AutoCrystalFeature.CrystalGate.blocked("out of reach");
      } else if (this.isSpotBackedOff(x) || this.startSpotBackoff(x, cfg)) {
         return AutoCrystalFeature.CrystalGate.blocked("backed off (places there never spawned a crystal)");
      } else if (this.isPlaceDelayed(x, cfg)) {
         return AutoCrystalFeature.CrystalGate.blocked("place-delay");
      } else {
         Box box = new Box(x.getX(), x.getY(), x.getZ(), x.getX() + 1.0, x.getY() + 2.0, x.getZ() + 1.0);

         for (Entity e : ReviveClient.mc.world.getOtherEntities(null, box)) {
            if (!e.isSpectator() && e != hit && (!(e instanceof EndCrystalEntity c) || !this.breakAttemptMs.containsKey(c.getId()))) {
               if (!(e instanceof ItemEntity item)) {
                  return AutoCrystalFeature.CrystalGate.blocked("entity in 2-high box: " + e.getName().getString());
               }

               if (hit == null || !cfg.ignoreItems.get() || !this.blownUpBy(item, hit)) {
                  return AutoCrystalFeature.CrystalGate.blocked("item " + item.getId(), "item in 2-high box id=" + item.getId() + " age=" + item.age);
               }
            }
         }

         SilentMine sm = SilentMine.get();
         if (hit == null && sm != null && sm.expectsDrop(x)) {
            return AutoCrystalFeature.CrystalGate.blocked("expectsDrop (our break's item is on its way)");
         } else {
            float damage = this.placeDamage(x, cfg, null, x);
            if (damage <= 0.0F) {
               return AutoCrystalFeature.CrystalGate.blocked("below min-place / self gates");
            } else {
               return this.bestPlace != null && !this.bestPlace.pos().equals(x) && this.bestPlace.totalDamage() - damage > cfg.rebreakDmgThreshold.get()
                  ? AutoCrystalFeature.CrystalGate.blocked("best " + this.bestPlace.pos().toShortString() + " beats it by more than rebreak-dmg-threshold")
                  : new AutoCrystalFeature.CrystalGate(damage, null, null);
            }
         }
      }
   }

   private boolean blownUpBy(ItemEntity item, EndCrystalEntity hit) {
      Vec3d at = item.getEntityPos();
      Vec3d center = hit.getEntityPos();
      return at.squaredDistanceTo(center) > 64.0
         ? false
         : ReviveClient.mc.world.raycast(new RaycastContext(at, center, ShapeType.COLLIDER, FluidHandling.NONE, ReviveClient.mc.player)).getType() == Type.MISS;
   }

   private boolean explosionClears(Vec3d center, BlockPos x) {
      Vec3d target = Vec3d.ofCenter(x);
      if (center.squaredDistanceTo(target) > 16.0) {
         return false;
      } else {
         BlockHitResult r = ReviveClient.mc.world.raycast(new RaycastContext(center, target, ShapeType.COLLIDER, FluidHandling.NONE, ReviveClient.mc.player));
         return r.getType() == Type.MISS || r.getBlockPos().equals(x);
      }
   }

   private boolean glassPlaceFree(BlockPos x, Entity hit) {
      for (Entity e : ReviveClient.mc.world.getOtherEntities(null, new Box(x))) {
         if (e != hit && !e.isRemoved() && e.intersectionChecked) {
            return false;
         }
      }

      return true;
   }

   private static BlockPos sealTarget(AutoMine autoMine, SilentMine sm) {
      BlockPos rebreak = sm.getRebreakPos();
      if (rebreak != null && autoMine.isSealablePos(rebreak)) {
         return rebreak;
      } else {
         BlockPos primary = sm.getDelayedDestroyPos();
         return primary != null && autoMine.isSealablePos(primary) ? primary : null;
      }
   }

   private static boolean primaryStopOut(BlockPos x, SilentMine sm) {
      return x.equals(sm.getDelayedDestroyPos()) && sm.isPrimaryStopSent();
   }

   private boolean recentOwnChange(BlockPos x) {
      int ping = Math.min(Math.max(PlayerUtils.getPing(), 0), 1000);
      long quietTicks = ping / 50 + 2;
      Long stop = this.stopSentTicks.get(x.asLong());
      if (stop != null && this.clientTick - stop <= quietTicks) {
         return true;
      } else if (x.equals(this.comboLastX) && this.clientTick - this.comboLastTick <= quietTicks) {
         return true;
      } else {
         Long placed = this.placeAttemptMs.get(x.asLong());
         return placed != null && System.currentTimeMillis() - placed <= ping + 100L;
      }
   }

   private boolean comboUrgent(AutoCrystalFeature.ComboPlan plan, AutoCrystal cfg) {
      if (plan != null && plan.dueIn() == 0 && cfg.rotatePlace.get()) {
         return plan.kind() == AutoCrystalFeature.ComboKind.SEAL
            ? true
            : plan.kind() == AutoCrystalFeature.ComboKind.FILL
               && plan.glass() != null
               && (this.fillGlassOk(plan, AutoMine.get()) || this.comboCrystalGate(plan.x(), cfg, null).why() == null);
      } else {
         return false;
      }
   }

   private boolean fillGlassOk(AutoCrystalFeature.ComboPlan plan, AutoMine autoMine) {
      SilentMine sm = SilentMine.get();
      return plan.kind() == AutoCrystalFeature.ComboKind.FILL
         && plan.glass() != null
         && autoMine != null
         && (plan.followUp() || autoMine.glassReseal())
         && this.glassPlaceFree(plan.x(), null)
         && sm != null
         && !sm.isSealPaused(plan.x());
   }

   private Vec3d comboAimFor(EndCrystalEntity crystal, AutoCrystal cfg) {
      AutoCrystalFeature.ComboPlan plan = this.comboPlan;
      if (plan != null) {
         if (plan.kind() == AutoCrystalFeature.ComboKind.OPEN && !crystal.getBlockPos().equals(plan.x())) {
            return this.comboCrystalGate(plan.x(), cfg, null).why() == null ? plan.aim() : null;
         } else {
            return plan.aim();
         }
      } else {
         AutoMine autoMine = AutoMine.get();
         SilentMine sm = SilentMine.get();
         if (cfg.antiSurround.get() && autoMine != null && sm != null && sm.isActive() && autoMine.glassSealActive()) {
            BlockPos x = sealTarget(autoMine, sm);
            return x == null
                  || !crystal.getBlockPos().equals(x)
                  || !x.equals(sm.getRebreakPos()) && !primaryStopOut(x, sm)
                  || !sm.isOwnSeal(x) && this.sealGlass(cfg, autoMine) == null
               ? null
               : comboAimPoint(x);
         } else {
            return null;
         }
      }
   }

   private static Vec3d comboAimPoint(BlockPos x) {
      return new Vec3d(x.getX() + 0.5, x.getY(), x.getZ() + 0.5);
   }

   private static Direction stopFace(Vec3d eye, BlockPos x, Vec3d aim) {
      Optional<Vec3d> clip = new Box(x).raycast(eye, eye.add(aim.subtract(eye).multiply(1.5)));
      if (clip.isEmpty()) {
         return Direction.UP;
      } else {
         Vec3d rel = clip.get().subtract(Vec3d.ofCenter(x));
         double ax = Math.abs(rel.x);
         double ay = Math.abs(rel.y);
         double az = Math.abs(rel.z);
         if (ay >= ax && ay >= az) {
            return rel.y > 0.0 ? Direction.UP : Direction.DOWN;
         } else if (ax >= az) {
            return rel.x > 0.0 ? Direction.EAST : Direction.WEST;
         } else {
            return rel.z > 0.0 ? Direction.SOUTH : Direction.NORTH;
         }
      }
   }

   private boolean serverLooksAt(BlockPos pos) {
      return this.serverLooksAt(ReviveClient.mc.player.getEyePos(), pos);
   }

   private boolean serverLooksAt(Vec3d eye, BlockPos pos) {
      return ReviveClient.ROTATION.raytraceCheck(eye, ReviveClient.ROTATION.getServerYaw(), ReviveClient.ROTATION.getServerPitch(), new Box(pos));
   }

   public boolean canCrystalAt(BlockPos pos) {
      AutoCrystal cfg = AutoCrystal.get();
      if (cfg != null && pos != null && ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
         Vec3d eye = ReviveClient.mc.player.getEyePos();
         return eye.y > pos.getY()
            && this.eyeDistTo(new Box(pos.down())) <= cfg.placeRange.get()
            && this.inBreakReach(pos, cfg)
            && eye.distanceTo(comboAimPoint(pos)) <= 4.5;
      } else {
         return false;
      }
   }

   public boolean glassSealOwns(BlockPos pos) {
      AutoCrystal cfg = AutoCrystal.get();
      AutoMine autoMine = AutoMine.get();
      SilentMine sm = SilentMine.get();
      if (cfg == null || autoMine == null || sm == null || pos == null || ReviveClient.mc.player == null) {
         return false;
      } else if (!sm.isActive() || !sm.isRunningHere() || !cfg.antiSurround.get() || !cfg.placeCrystals.get()) {
         return false;
      } else if (!autoMine.isSealablePos(pos)) {
         return false;
      } else if (sm.isOwnSeal(pos)) {
         return true;
      } else if (!pos.equals(sealTarget(autoMine, sm))) {
         return false;
      } else if (!ReviveClient.mc.world.getBlockState(pos).isAir() && !this.glassPlaceFree(pos, null)) {
         return false;
      } else if (this.placeBackoffTicks > 0 || !this.hasAnyCrystal() || !this.isValidAutoCrystalTarget(autoMine.getTargetPlayer(), cfg)) {
         return false;
      } else {
         return !sm.isSealPaused(pos) && (!pos.equals(this.comboFallbackX) || this.clientTick >= this.comboFallbackUntil)
            ? this.sealGlass(cfg, autoMine) != null
            : false;
      }
   }

   private static boolean isSealGlass(ItemStack stack) {
      return stack.getItem() instanceof BlockItem blockItem && (blockItem.getBlock() == Blocks.GLASS || blockItem.getBlock() instanceof StainedGlassBlock);
   }

   private Item sealGlass(AutoCrystal cfg, AutoMine autoMine) {
      FindItemResult found = InvUtils.findInHotbar(AutoCrystalFeature::isSealGlass);
      if (!found.found() && autoMine.glassSource() == AutoMine.GlassSource.Inventory) {
         found = InvUtils.find(AutoCrystalFeature::isSealGlass);
      }

      if (!found.found()) {
         return null;
      } else {
         ItemStack stack = found.slot() == 45 ? ReviveClient.mc.player.getOffHandStack() : ReviveClient.mc.player.getInventory().getStack(found.slot());
         Item item = stack.getItem();
         return this.canUseItem(item, cfg) ? item : null;
      }
   }

   private boolean canUseItem(Item item, AutoCrystal cfg) {
      return this.canUseItem(item, cfg, 0);
   }

   private boolean canUseItem(Item item, AutoCrystal cfg, int extraClicks) {
      if (item != null && ReviveClient.mc.player != null) {
         if (!ReviveClient.mc.player.getOffHandStack().isOf(item) && !ReviveClient.mc.player.getMainHandStack().isOf(item)) {
            AutoCrystal.CrystalSwapMode mode = cfg.swapMode.get();
            if (mode == AutoCrystal.CrystalSwapMode.None) {
               return false;
            } else if (mode == AutoCrystal.CrystalSwapMode.OffhandSilent && InvUtils.findInHotbar(item).found() && !miningOwnsSelectedSlot()) {
               return this.canSendOffhandSwapPair();
            } else {
               FindItemResult result = ReviveClient.SWAP.getSlot(item);
               if (result.found() && !ReviveClient.SWAP.isSlotLocked(result.slot()) && !ReviveClient.SWAP.isSilentSwapActive()) {
                  SwapManager.SwapMode swapMode = ReviveClient.SWAP.getItemSwapMode();
                  SilentMine sm = SilentMine.get();
                  boolean container = !result.isHotbar()
                     || swapMode == SwapManager.SwapMode.SilentSwap
                     || ReviveClient.SWAP.isMultiTickSwapActive()
                     || swapMode == SwapManager.SwapMode.Auto
                        && (
                           result.slot() != ReviveClient.mc.player.getInventory().getSelectedSlot()
                                 && sm != null
                                 && sm.isRunningHere()
                                 && !sm.isPacketMode()
                                 && sm.hasDelayedDestroy()
                              || ReviveClient.mc.player.isUsingItem() && ReviveClient.mc.player.getActiveHand() == Hand.MAIN_HAND
                        );
                  if (!container) {
                     return true;
                  } else if (ReviveClient.mc.player.currentScreenHandler != ReviveClient.mc.player.playerScreenHandler) {
                     return false;
                  } else if (clickWhileMovingDropped()) {
                     if (dbgOn(cfg)) {
                        this.dbgState(
                           cfg,
                           "combo-click",
                           "combo: "
                              + item
                              + " needs a container click, which Grim's MultiActionsC drops while sprinting / sneaking / holding a movement key - not used this tick"
                        );
                     }

                     return false;
                  } else {
                     return ReviveClient.PACKET.remaining(PacketManager.Category.CLICK_SLOT) >= 2 + extraClicks + (offhandModuleActive() ? 2 : 0);
                  }
               } else {
                  return false;
               }
            }
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean clickWhileMovingDropped() {
      if (ReviveClient.mc.player.isSprinting()) {
         return true;
      } else if (!nativeModernServer()) {
         return false;
      } else {
         PlayerInput in = ReviveClient.mc.player.input.playerInput;
         return ReviveClient.mc.player.isSneaking() || in.forward() || in.backward() || in.left() || in.right() || in.jump();
      }
   }

   private void expectAnswer(BlockPos x, AutoCrystalFeature.ComboKind kind, boolean sameFlush) {
      if (x.equals(this.lateX)) {
         this.lateX = null;
      }

      this.answerX = x.toImmutable();
      this.answerKind = kind;
      this.answerMs = System.currentTimeMillis();
      this.answerSameFlush = sameFlush;
   }

   private void checkComboAnswer(AutoCrystal cfg) {
      SilentMine sm = SilentMine.get();
      if (this.lateX != null) {
         if (System.currentTimeMillis() - this.lateMs > 1000L) {
            this.lateX = null;
         } else if (this.lateKind == AutoCrystalFeature.ComboKind.SEAL && sm != null && sm.sealConfirmed(this.lateX)) {
            this.lateAnswer(cfg, "glass");
         }
      }

      if (this.answerX != null) {
         long waited = System.currentTimeMillis() - this.answerMs;
         long window = Math.min(Math.max((long)Math.max(PlayerUtils.getPing(), 0), this.answerEchoMs), 1000L) + 100L;
         if (waited > window) {
            BlockPos x = this.answerX;
            if (this.answerKind == AutoCrystalFeature.ComboKind.SEAL) {
               if (sm != null && sm.isOwnSeal(x) && !sm.sealConfirmed(x) && waited <= 3L * window) {
                  return;
               }

               this.answerX = null;
               if (sm == null || !sm.isSealPaused(x)) {
                  if (this.answerSameFlush && sm != null && sm.isOwnSeal(x)) {
                     this.orderRefusals = 0;
                  }

                  return;
               }

               this.dbg(cfg, "no glass at " + x.toShortString() + " within rtt+100 (" + waited + "ms): refused");
               if (!ReviveClient.mc.world.getBlockState(x).isAir()) {
                  return;
               }
            } else {
               this.answerX = null;
               if (this.answerKind == AutoCrystalFeature.ComboKind.OPEN && sm != null && sm.sealConfirmed(x)) {
                  this.dbg(cfg, "no spawn at " + x.toShortString() + " within rtt+100 (" + waited + "ms): the open STOP was refused (glass still in)");
                  return;
               }

               this.dbg(cfg, "no spawn at " + x.toShortString() + " within rtt+100 (" + waited + "ms): refused");
            }

            if (this.answerSameFlush) {
               this.lateX = x;
               this.lateKind = this.answerKind;
               this.lateMs = System.currentTimeMillis();
               this.lateSwitched = false;
               AutoMine autoMine = AutoMine.get();
               if (!this.openOrderSwitched && nativeModernServer() && autoMine != null && !autoMine.glassOpenNextTick()) {
                  if (++this.orderRefusals < 3) {
                     this.dbg(cfg, "glass-open-order: place behind our STOP refused " + this.orderRefusals + "/3 in a row");
                  } else {
                     this.openOrderSwitched = true;
                     this.lateSwitched = true;
                     this.dbg(
                        cfg, "glass-open-order -> NextTick for this session: a 1.21.2+ server refused 3 places in a row behind our STOP (Grim's PacketOrderI)"
                     );
                  }
               }
            }
         }
      }
   }

   private void lateAnswer(AutoCrystal cfg, String what) {
      this.dbg(
         cfg,
         "late "
            + what
            + " at "
            + this.lateX.toShortString()
            + " ("
            + (System.currentTimeMillis() - this.lateMs)
            + "ms after the refused verdict): not refused"
            + (this.lateSwitched ? " - glass-open-order back to SameTick" : "")
      );
      this.orderRefusals = 0;
      if (this.lateSwitched) {
         this.openOrderSwitched = false;
      }

      this.lateX = null;
      this.lateSwitched = false;
   }

   private static boolean nativeModernServer() {
      if (ReviveClient.mc.getNetworkHandler() == null) {
         return false;
      } else {
         PbVia.Version version = PbVia.describe(PbVia.connectionTarget(ReviveClient.mc.getNetworkHandler().getConnection()));
         return version == null || !version.atMost(767);
      }
   }

   private double leadTicks(AutoCrystal cfg) {
      int ping = switch ((AutoCrystal.PingCompensation)cfg.pingCompensation.get()) {
         case Off -> 0;
         case Manual -> cfg.manualPing.get();
         case Auto -> {
            PlayerListEntry entry = ReviveClient.mc.getNetworkHandler() != null && ReviveClient.mc.player != null
               ? ReviveClient.mc.getNetworkHandler().getPlayerListEntry(ReviveClient.mc.player.getUuid())
               : null;
            yield entry != null ? entry.getLatency() : cfg.manualPing.get();
         }
      };
      return Math.max(0, ping) / 50.0;
   }

   private boolean othersMiningQualifies(BreakIndicators.TrackedBlock tracked, double lead) {
      return tracked.progress + this.progressPerTick(tracked) * lead >= breakPoint(tracked);
   }

   private static double breakPoint(BreakIndicators.TrackedBlock tracked) {
      BreakIndicators indicators = Modules.get().get(BreakIndicators.class);
      if (indicators != null && indicators.isActive()) {
         Setting<Double> completion = indicators.settings.get(tracked.isRebreak ? "rebreak-completion" : "full-completion", Double.class);
         double point = completion != null ? completion.get() : 1.0;
         return point > 0.0 ? Math.min(point, 1.0) : 1.0;
      } else {
         return 1.0;
      }
   }

   private double progressPerTick(BreakIndicators.TrackedBlock tracked) {
      BlockState state = ReviveClient.mc.world.getBlockState(tracked.pos);
      FindItemResult tool = InvUtils.findFastestToolHotbar(state);
      int slot = tool.found() ? tool.slot() : ReviveClient.mc.player.getInventory().getSelectedSlot();
      double delta = BlockUtils.getBreakDelta(slot, state);
      if (delta > 0.0 && Double.isFinite(delta)) {
         return delta;
      } else {
         long nowNs = System.nanoTime();
         double[] rate = this.trackedRates.get(tracked.pos);
         if (rate == null) {
            this.trackedRates.put(tracked.pos.toImmutable(), new double[]{nowNs, tracked.progress, System.currentTimeMillis()});
            return 0.0;
         } else {
            rate[2] = System.currentTimeMillis();
            double elapsedTicks = (nowNs - rate[0]) / 5.0E7;
            return elapsedTicks < 1.0 ? 0.0 : Math.max(0.0, (tracked.progress - rate[1]) / elapsedTicks);
         }
      }
   }

   private boolean tryCevHead(AutoCrystal cfg) {
      double lead = this.leadTicks(cfg);
      Map<BlockPos, BreakIndicators.TrackedBlock> tracked = trackedBlocks();
      AutoCrystalFeature.PlaceTarget best = null;
      BlockPos bestHead = null;

      for (LivingEntity e : this.enemies(cfg)) {
         if (e instanceof PlayerEntity && !(e.squaredDistanceTo(ReviveClient.mc.player) > 25.0)) {
            BlockPos head = e.getBlockPos().up();
            if (ReviveClient.mc.world.getBlockState(head).isOf(Blocks.OBSIDIAN)) {
               BreakIndicators.TrackedBlock t = tracked.get(head);
               boolean qualifies = t != null && this.othersMiningQualifies(t, lead) || this.ownMineQualifies(head, cfg);
               if (qualifies) {
                  BlockPos spot = head.up();
                  if (this.isPlaceable(spot, cfg, true, true)) {
                     float dmg = this.placeDamage(spot, cfg, null, head);
                     if (!(dmg <= 0.0F) && (best == null || dmg > best.totalDamage())) {
                        best = new AutoCrystalFeature.PlaceTarget(spot.toImmutable(), dmg);
                        bestHead = head;
                     }
                  }
               }
            }
         }
      }

      return best == null ? false : this.doPlace(best, cfg, false, "cev-head (head " + bestHead.toShortString() + ")", false);
   }

   private boolean ownMineQualifies(BlockPos pos, AutoCrystal cfg) {
      SilentMine sm = SilentMine.get();
      if (sm != null && sm.isActive()) {
         if (pos.equals(sm.getDelayedDestroyPos())) {
            double ticks = sm.getPrimaryTicksToBreak();
            boolean staged = placeIsStaged(cfg);
            boolean qualifies = primaryPlaceWindow(ticks, staged);
            if (cfg.debug.get() && ticks >= 0.0 && ticks <= 2.0) {
               this.dbgOnChange(
                  cfg,
                  "cev-own",
                  pos.asLong() + " " + qualifies,
                  String.format(
                     Locale.US,
                     "cev-head own primary %s ttb=%.0f %s -> %s",
                     pos.toShortString(),
                     ticks,
                     staged ? "staged (place the tick before the STOP's)" : "sent in-tick",
                     qualifies ? "place" : "wait"
                  )
               );
            }

            if (qualifies) {
               return true;
            }
         }

         return pos.equals(sm.getDoubleMinePos()) && sm.getDoubleMineProgress() >= 1.0
            ? true
            : pos.equals(sm.getRebreakPos()) && sm.getRebreakProgress() >= 1.0;
      } else {
         return false;
      }
   }

   private static boolean primaryPlaceWindow(double ticks, boolean staged) {
      return staged ? ticks > 1.0 && ticks <= 2.0 : ticks >= 0.0 && ticks <= 1.0;
   }

   private static Map<BlockPos, BreakIndicators.TrackedBlock> trackedBlocks() {
      BreakIndicators indicators = Modules.get().get(BreakIndicators.class);
      return indicators != null && indicators.isActive() ? indicators.getTracked() : Map.of();
   }

   private boolean tryShorelinePlace(AutoCrystal cfg) {
      AutoMine autoMine = AutoMine.get();
      if (autoMine != null && autoMine.isActive()) {
         PlayerEntity target = autoMine.getTargetPlayer();
         if (!this.isValidAutoCrystalTarget(target, cfg)) {
            return false;
         } else {
            BlockPos feet = target.getBlockPos();
            BlockPos self = ReviveClient.mc.player.getBlockPos();
            Direction[][] corners = new Direction[][]{
               {Direction.NORTH, Direction.EAST}, {Direction.NORTH, Direction.WEST}, {Direction.SOUTH, Direction.EAST}, {Direction.SOUTH, Direction.WEST}
            };

            for (Direction[] corner : corners) {
               if (autoMine.isTargetedPos(feet.offset(corner[0])) || autoMine.isTargetedPos(feet.offset(corner[1]))) {
                  BlockPos base = feet.offset(corner[0]).offset(corner[1]).down();
                  if (!base.equals(self) && !base.equals(self.down()) && !base.equals(self.down(2))) {
                     BlockPos spot = base.up();
                     if (this.isPlaceable(spot, cfg, true, true)) {
                        Vec3d crystalPos = crystalPos(spot);
                        if (this.isSelfSafe(crystalPos, cfg, false)) {
                           float dmg = Math.max(0.0F, this.placeDamage(spot, cfg, null, null));
                           if (this.doPlace(new AutoCrystalFeature.PlaceTarget(spot.toImmutable(), dmg), cfg, false, "shoreline", false)) {
                              return true;
                           }
                        }
                     }
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private boolean tryPredictBlockPlace(AutoCrystal cfg) {
      double lead = this.leadTicks(cfg);
      AutoCrystalFeature.PlaceTarget best = null;
      BlockPos bestBase = null;

      for (BreakIndicators.TrackedBlock tracked : trackedBlocks().values()) {
         if (this.othersMiningQualifies(tracked, lead)) {
            BlockPos base = tracked.pos;
            BlockPos spot = base.up();
            if (this.isPlaceable(spot, cfg, true, true)) {
               float dmg = this.placeDamage(spot, cfg, null, base);
               if (!(dmg <= 0.0F) && (best == null || dmg > best.totalDamage())) {
                  best = new AutoCrystalFeature.PlaceTarget(spot.toImmutable(), dmg);
                  bestBase = base;
               }
            }
         }
      }

      return best == null ? false : this.doPlace(best, cfg, false, "predict-block (mined " + bestBase.toShortString() + ")", false);
   }

   private void update() {
      this.lastPlaceTarget = null;
      this.lastTotalDamage = 0.0F;
      long now = System.currentTimeMillis();
      this.placeAttemptMs.values().removeIf(t -> now - t > 5000L);
      this.breakAttemptMs.values().removeIf(t -> now - t > 5000L);
      this.trackedRates.values().removeIf(r -> now - (long)r[2] > 2000L);
      if (ReviveClient.mc.world != this.unconfirmedWorld) {
         this.unconfirmedWorld = ReviveClient.mc.world;
         this.unconfirmedPlaces.clear();
         this.spotBackoffUntil.clear();
         this.stopSentTicks.clear();
         this.openOrderSwitched = false;
         this.orderRefusals = 0;
         this.lateX = null;
      }

      if (!this.spotBackoffUntil.isEmpty()) {
         this.spotBackoffUntil.values().removeIf(until -> this.clientTick >= until);
      }

      if (!this.unconfirmedPlaces.isEmpty()) {
         this.unconfirmedPlaces.keySet().removeIf(k -> !this.placeAttemptMs.containsKey(k) && !this.spotBackoffUntil.containsKey(k));
      }

      if (this.unconfirmedPlaces.size() > 64) {
         this.unconfirmedPlaces.values().removeIf(n -> n < 5);
      }
   }

   private boolean shouldFacePlace(LivingEntity target, AutoCrystal cfg) {
      if (target != null && cfg.facePlace.get()) {
         if (cfg.forceFacePlace.get().isPressed()) {
            return true;
         } else if (target.getHealth() <= cfg.facePlaceHealth.get().intValue()) {
            return true;
         } else {
            int threshold = cfg.facePlaceArmor.get();

            for (EquipmentSlot slot : ARMOR_SLOTS) {
               ItemStack stack = target.getEquippedStack(slot);
               if (stack.isEmpty()) {
                  if (cfg.facePlaceMissingArmor.get()) {
                     return true;
                  }
               } else if (DamageUtil.isBroken(stack, threshold)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isNaked(LivingEntity e) {
      for (EquipmentSlot slot : ARMOR_SLOTS) {
         if (!e.getEquippedStack(slot).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private boolean isInBreakRangeNow(EndCrystalEntity crystal, AutoCrystal cfg) {
      return ReviveClient.mc.player != null && crystal != null && crystal.isAlive() ? this.eyeDistTo(crystal.getBoundingBox()) <= cfg.breakRange.get() : false;
   }

   private double eyeDistTo(Box box) {
      if (ReviveClient.mc.player == null) {
         return Double.MAX_VALUE;
      } else {
         Vec3d eye = ReviveClient.mc.player.getEyePos();
         double dx = eye.x - MathHelper.clamp(eye.x, box.minX, box.maxX);
         double dy = eye.y - MathHelper.clamp(eye.y, box.minY, box.maxY);
         double dz = eye.z - MathHelper.clamp(eye.z, box.minZ, box.maxZ);
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
   }

   private static Vec3d aimPointOn(Vec3d eye, Box box) {
      double inset = 0.0625;
      double x = MathHelper.clamp(eye.x, Math.min(box.minX + 0.0625, box.maxX), Math.max(box.maxX - 0.0625, box.minX));
      double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
      double z = MathHelper.clamp(eye.z, Math.min(box.minZ + 0.0625, box.maxZ), Math.max(box.maxZ - 0.0625, box.minZ));
      return new Vec3d(x, y, z);
   }

   private static boolean rayHits(Vec3d eye, float yaw, float pitch, Box box, double reach) {
      if (box.contains(eye)) {
         return true;
      } else {
         Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(reach));
         return box.raycast(eye, end).isPresent();
      }
   }

   private boolean outgoingLooksAt(Box box) {
      return ReviveClient.ROTATION
         .raytraceCheck(ReviveClient.mc.player.getEyePos(), ReviveClient.ROTATION.getOutgoingYaw(), ReviveClient.ROTATION.getOutgoingPitch(), box);
   }

   private boolean aimAt(Vec3d target) {
      if (ReviveClient.mc.player == null) {
         return false;
      } else if (this.aimHeld) {
         return this.aimOwned;
      } else {
         this.aimHeld = true;
         float[] rot = ReviveClient.ROTATION.getRotation(ReviveClient.mc.player.getEyePos(), target);
         this.aimYaw = rot[0];
         this.aimPitch = rot[1];
         if (this.aimYielding()) {
            this.aimYielded = true;
            this.aimOwned = false;
            return false;
         } else {
            this.aimOwned = ReviveClient.ROTATION.requestMotionRotation("auto-crystal", rot[0], rot[1], aimPriority(), true);
            return this.aimOwned;
         }
      }
   }

   private boolean aimYielding() {
      return this.clientTick <= this.aimYieldTick;
   }

   private boolean starvationYield(boolean ourLookWent) {
      AutoCrystal cfg = AutoCrystal.get();
      SilentMine sm = SilentMine.get();
      if (cfg != null && sm != null && sm.isActive() && sm.isRunningHere() && ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
         int waited = sm.deferredStopTicks();
         if (waited < this.yieldDeferCount) {
            this.yieldDeferCount = 0;
         }

         if (ourLookWent && waited >= 3 && waited - this.yieldDeferCount >= 3) {
            AutoMine autoMine = AutoMine.get();
            PlayerEntity target = autoMine != null ? autoMine.getTargetPlayer() : null;
            if (target == null) {
               return false;
            } else {
               BlockPos at = sm.deferredStopPos();
               if (at != null && !this.glassSealOwns(at)) {
                  String where = at.toShortString();
                  if (at.equals(this.futileYieldPos) && this.clientTick - this.futileYieldTick < 20L) {
                     if (dbgOn(cfg)) {
                        this.dbgState(cfg, "yield", "aim kept: SilentMine's STOP at " + where + " waits, but the last yield for it went to another look");
                     }

                     return false;
                  } else {
                     String exposedBy = this.enemyExposedBy(cfg, at);
                     if (exposedBy != null) {
                        this.dbgOnChange(
                           cfg,
                           "yield-kept",
                           where,
                           "aim kept: SilentMine's STOP at "
                              + where
                              + " waits for the move packet's look (3+ ticks), but an enemy is exposed ("
                              + exposedBy
                              + ")"
                        );
                        return false;
                     } else {
                        this.dbgSigs.remove("yield-kept");
                        this.yieldDeferCount = waited;
                        this.yieldPos = at;
                        if (dbgOn(cfg)) {
                           this.dbgState(
                              cfg,
                              "yield",
                              "aim yield: SilentMine's STOP at "
                                 + where
                                 + " waits for the move packet's look (3+ ticks) and no enemy is exposed - the next packet's look is SilentMine's"
                           );
                        }

                        return true;
                     }
                  }
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void cancelAimYield(AutoCrystal cfg, String why) {
      if (this.aimYielding()) {
         this.aimYieldTick = Long.MIN_VALUE;
         if (this.aimYielded) {
            this.aimYielded = false;
            this.aimHeld = false;
         }

         this.dbg(cfg, "aim yield taken back: " + why);
      }
   }

   private String enemyExposedBy(AutoCrystal cfg, BlockPos waiting) {
      BlockPos pick = this.bestPlace != null
         ? this.bestPlace.pos()
         : (this.lastPickPos != null && this.clientTick - this.lastPickTick <= 2L ? this.lastPickPos : null);
      EndCrystalEntity hit = this.pendingBreak;
      if (pick == null && hit == null) {
         return null;
      } else {
         AutoCrystalSnapshot.Context ctx = this.context(cfg);
         Set<BlockPos> ignored = ctx.ignored();
         if (waiting != null && ignored.contains(waiting)) {
            ignored = new HashSet<>(ignored);
            ignored.remove(waiting);
         }

         for (AutoCrystalSnapshot.TargetData t : ctx.enemies()) {
            double min = t.facePlace() ? Math.min(cfg.minPlace.get(), 1.0) : cfg.minPlace.get();
            String what = pick != null && DamageUtil.crystalDamage(t.profile(), t.pos(), t.box(), crystalPos(pick), ctx.world(), ignored) >= min
               ? (this.bestPlace != null ? "bestPlace " : "the worker's pick ") + pick.toShortString()
               : (
                  hit != null && DamageUtil.crystalDamage(t.profile(), t.pos(), t.box(), hit.getEntityPos(), ctx.world(), ignored) >= min
                     ? "the crystal broken this tick at " + hit.getBlockPos().toShortString()
                     : null
               );
            if (what != null) {
               Entity e = ReviveClient.mc.world.getEntityById(t.id());
               return what + " deals " + (e != null ? e.getName().getString() : "id=" + t.id()) + " its minimum";
            }
         }

         return null;
      }
   }

   private static double aimPriority() {
      AntiCheatConfig config = AntiCheatConfig.get();
      return config != null ? config.crystalAuraPriority.get().weight : 1200.0;
   }

   private AutoCrystalFeature.PlaceAim placeAim(BlockPos base, AutoCrystal cfg) {
      Vec3d eye = ReviveClient.mc.player.getEyePos();
      if (!cfg.strictDirection.get() && !(eye.y <= base.getY() + 1.0)) {
         Vec3d top = Vec3d.ofCenter(base).add(0.0, 0.5, 0.0);
         return new AutoCrystalFeature.PlaceAim(new BlockHitResult(top, Direction.UP, base, true), Vec3d.ofCenter(base));
      } else {
         BlockHitResult hit = this.getStrictPlaceHitResult(base);
         return hit == null ? null : new AutoCrystalFeature.PlaceAim(hit, hit.getPos());
      }
   }

   private BlockHitResult getStrictPlaceHitResult(BlockPos base) {
      Vec3d eye = ReviveClient.mc.player.getEyePos();
      Vec3d center = Vec3d.ofCenter(base);
      Vec3d dir = center.subtract(eye).normalize();
      Vec3d end = eye.add(dir.multiply(eye.distanceTo(center) + 1.0));
      Optional<Vec3d> clip = new Box(base).raycast(eye, end);
      if (clip.isEmpty()) {
         return null;
      } else {
         Vec3d hitPos = clip.get();
         Vec3d relative = hitPos.subtract(center);
         double ax = Math.abs(relative.x);
         double ay = Math.abs(relative.y);
         double az = Math.abs(relative.z);
         Direction face;
         if (ax >= ay && ax >= az) {
            face = relative.x > 0.0 ? Direction.EAST : Direction.WEST;
         } else if (ay >= ax && ay >= az) {
            face = relative.y > 0.0 ? Direction.UP : Direction.DOWN;
         } else {
            face = relative.z > 0.0 ? Direction.SOUTH : Direction.NORTH;
         }

         return new BlockHitResult(hitPos, face, base, true);
      }
   }

   private void swing(AutoCrystal.SwingMode mode, Hand hand) {
      if (ReviveClient.mc.player != null && mode != null) {
         switch (mode) {
            case Client:
               ReviveClient.mc.player.swingHand(hand);
               break;
            case Packet:
               if (ReviveClient.mc.getNetworkHandler() != null) {
                  ReviveClient.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
               }
            case None:
         }
      }
   }

   private static boolean offhandModuleActive() {
      Offhand offhand = Modules.get().get(Offhand.class);
      return offhand != null && offhand.isActive();
   }

   private static boolean surroundModuleActive() {
      Surround surround = Modules.get().get(Surround.class);
      return surround != null && surround.isActive();
   }

   private void sendPlacePacket(BlockHitResult hit, Hand hand) {
      int seq = ((IClientWorld)ReviveClient.mc.world).revive$getAndIncrementSequence();
      ReviveClient.mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hit, seq));
   }

   private void sendCarriedItemPacket(int slot) {
      ReviveClient.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
   }

   private boolean canSendOffhandSwapPair() {
      return ReviveClient.PACKET.remaining(PacketManager.Category.OFFHAND) >= 2 + (offhandModuleActive() ? 2 : 0);
   }

   private void sendOffhandSwapPacket() {
      ReviveClient.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
   }

   private boolean reserveInteractionBudget() {
      int reserve = surroundModuleActive() ? 2 : 0;
      return ReviveClient.PACKET.remaining(PacketManager.Category.BLOCK_INTERACT) >= 1 + reserve;
   }

   private boolean attackBudgetLow(AutoCrystal cfg, String key) {
      PacketLimiter limiter = Modules.get().get(PacketLimiter.class);
      if (limiter != null && limiter.isActive()) {
         int reserve = Math.min(2, limiter.typeLimit(PlayerInteractEntityC2SPacket.class) - 1);
         int left = limiter.remaining(PlayerInteractEntityC2SPacket.class);
         if (left > reserve) {
            return false;
         } else {
            if (dbgOn(cfg)) {
               this.dbgState(cfg, key, "break skip: PacketLimiter's attack allowance is down to " + left + " (kept for other modules)");
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private void maybeRestockHotbar(AutoCrystal cfg) {
      if (ReviveClient.mc.player != null && ReviveClient.mc.interactionManager != null) {
         if (ReviveClient.mc.player.currentScreenHandler == ReviveClient.mc.player.playerScreenHandler) {
            if (!ReviveClient.SWAP.isSilentSwapActive()) {
               if (!ReviveClient.mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
                  FindItemResult hotbar = InvUtils.findInHotbar(Items.END_CRYSTAL);
                  if (hotbar.found()) {
                     this.lastCrystalHotbarSlot = hotbar.slot();
                  } else {
                     int sourceInvSlot = this.findCrystalInMainInventory();
                     if (sourceInvSlot != -1) {
                        if (!ReviveClient.SWAP.isSlotLocked(sourceInvSlot)) {
                           int targetHotbar = this.pickRestockHotbarSlot();
                           if (targetHotbar != -1) {
                              if (!ReviveClient.SWAP.isSlotLocked(targetHotbar)) {
                                 if (targetHotbar == ReviveClient.mc.player.getInventory().getSelectedSlot() && !this.canRestockSelectedSlot(targetHotbar)) {
                                    int alt = this.pickRestockHotbarSlotExcluding(targetHotbar);
                                    if (alt == -1) {
                                       return;
                                    }

                                    targetHotbar = alt;
                                 }

                                 if (this.restockCooldown <= 0) {
                                    if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                                       ItemStack displaced = ReviveClient.mc.player.getInventory().getStack(targetHotbar).copy();
                                       InvUtils.move().fromMain(sourceInvSlot - 9).toHotbar(targetHotbar);
                                       ReviveClient.mc.player.playerScreenHandler.nextRevision();
                                       this.restockCooldown = 5;
                                       this.lastCrystalHotbarSlot = targetHotbar;
                                       if (!displaced.isEmpty() && this.borrowedHotbarSlot == -1) {
                                          this.borrowedHotbarSlot = targetHotbar;
                                          this.borrowedReturnInvSlot = sourceInvSlot;
                                          this.borrowedOriginalStack = displaced;
                                          this.dbg(
                                             cfg,
                                             "restock borrowed hotbar "
                                                + targetHotbar
                                                + " (bumped "
                                                + displaced.getItem()
                                                + " to inv "
                                                + sourceInvSlot
                                                + " - will restore when idle)"
                                          );
                                       }

                                       this.dbg(cfg, "restock: crystals inv slot " + sourceInvSlot + " -> hotbar " + targetHotbar);
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
      }
   }

   private void maybeRestoreBorrowedSlot(AutoCrystal cfg) {
      if (this.borrowedHotbarSlot != -1 && ReviveClient.mc.player != null && ReviveClient.mc.interactionManager != null) {
         boolean idle = this.lastCombatActivityTick != Long.MIN_VALUE && this.clientTick - this.lastCombatActivityTick > 20L;
         boolean crystalsGone = !this.hasAnyCrystal();
         if (idle || crystalsGone) {
            if (ReviveClient.mc.player.currentScreenHandler == ReviveClient.mc.player.playerScreenHandler) {
               if (!ReviveClient.SWAP.isSilentSwapActive() && !ReviveClient.SWAP.isMultiTickSwapActive()) {
                  if (this.restockCooldown <= 0) {
                     if (ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
                        if (this.borrowedHotbarSlot != ReviveClient.mc.player.getInventory().getSelectedSlot()) {
                           if (!this.restoreBorrowedSlotNow(cfg, crystalsGone ? "out of crystals" : "idle 20t")) {
                              this.clearBorrowRecord();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean restoreBorrowedSlotNow(AutoCrystal cfg, String reason) {
      if (ReviveClient.mc.player == null) {
         return false;
      } else if (this.borrowedReturnInvSlot >= 9 && this.borrowedReturnInvSlot <= 35) {
         ItemStack returnSlot = ReviveClient.mc.player.getInventory().getStack(this.borrowedReturnInvSlot);
         if (!this.borrowedOriginalStack.isEmpty() && returnSlot.isOf(this.borrowedOriginalStack.getItem())) {
            InvUtils.move().fromMain(this.borrowedReturnInvSlot - 9).toHotbar(this.borrowedHotbarSlot);
            ReviveClient.mc.player.playerScreenHandler.nextRevision();
            this.restockCooldown = 5;
            this.dbg(cfg, "borrow restore: " + this.borrowedOriginalStack.getItem() + " -> hotbar " + this.borrowedHotbarSlot + " (" + reason + ")");
            if (this.borrowedHotbarSlot == this.lastCrystalHotbarSlot) {
               this.lastCrystalHotbarSlot = -1;
            }

            this.clearBorrowRecord();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void clearBorrowRecord() {
      this.borrowedHotbarSlot = -1;
      this.borrowedReturnInvSlot = -1;
      this.borrowedOriginalStack = ItemStack.EMPTY;
   }

   private boolean canRestockSelectedSlot(int slot) {
      if (slot != this.lastCrystalHotbarSlot) {
         return false;
      } else if (!ReviveClient.mc.player.getInventory().getStack(slot).isEmpty()) {
         return false;
      } else {
         return ReviveClient.mc.player.isUsingItem() ? false : !ReviveClient.SWAP.isMultiTickSwapActive();
      }
   }

   private int findCrystalInMainInventory() {
      if (ReviveClient.mc.player == null) {
         return -1;
      } else {
         for (int i = 9; i < 36; i++) {
            if (ReviveClient.mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
               return i;
            }
         }

         return -1;
      }
   }

   private boolean hasAnyCrystal() {
      if (ReviveClient.mc.player == null) {
         return false;
      } else if (ReviveClient.mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
         return true;
      } else {
         return InvUtils.findInHotbar(Items.END_CRYSTAL).found() ? true : this.findCrystalInMainInventory() != -1;
      }
   }

   private int pickRestockHotbarSlot() {
      if (ReviveClient.mc.player == null) {
         return -1;
      } else if (this.lastCrystalHotbarSlot >= 0
         && this.lastCrystalHotbarSlot <= 8
         && ReviveClient.mc.player.getInventory().getStack(this.lastCrystalHotbarSlot).isEmpty()) {
         return this.lastCrystalHotbarSlot;
      } else {
         int selected = ReviveClient.mc.player.getInventory().getSelectedSlot();

         for (int i = 0; i < 9; i++) {
            if (i != selected && ReviveClient.mc.player.getInventory().getStack(i).isEmpty()) {
               return i;
            }
         }

         if (this.lastCrystalHotbarSlot >= 0
            && this.lastCrystalHotbarSlot <= 8
            && this.lastCrystalHotbarSlot != selected
            && !this.isProtectedHotbarSlot(this.lastCrystalHotbarSlot)) {
            return this.lastCrystalHotbarSlot;
         } else {
            for (int ix = 0; ix < 9; ix++) {
               if (ix != selected && !this.isProtectedHotbarSlot(ix)) {
                  return ix;
               }
            }

            return -1;
         }
      }
   }

   private int pickRestockHotbarSlotExcluding(int exclude) {
      if (ReviveClient.mc.player == null) {
         return -1;
      } else {
         int selected = ReviveClient.mc.player.getInventory().getSelectedSlot();

         for (int i = 0; i < 9; i++) {
            if (i != exclude && i != selected && ReviveClient.mc.player.getInventory().getStack(i).isEmpty()) {
               return i;
            }
         }

         for (int ix = 0; ix < 9; ix++) {
            if (ix != exclude && ix != selected && !this.isProtectedHotbarSlot(ix)) {
               return ix;
            }
         }

         return -1;
      }
   }

   private boolean isProtectedHotbarSlot(int slot) {
      if (ReviveClient.mc.player == null) {
         return true;
      } else if (slot == ReviveClient.mc.player.getInventory().getSelectedSlot()) {
         return true;
      } else if (ReviveClient.SWAP.isSlotLocked(slot)) {
         return true;
      } else {
         ItemStack s = ReviveClient.mc.player.getInventory().getStack(slot);
         if (s.isEmpty()) {
            return false;
         } else {
            return s.isDamageable() ? true : s.isOf(Items.TOTEM_OF_UNDYING) || s.isOf(Items.OBSIDIAN) || s.isOf(Items.END_CRYSTAL);
         }
      }
   }

   private List<EndCrystalEntity> endCrystals() {
      long tick = this.cacheEpoch;
      if (this.crystalsCacheTick == tick) {
         return this.crystalsCache;
      } else {
         this.crystalsCacheTick = tick;
         List<EndCrystalEntity> out = new ArrayList<>();
         if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
            for (Entity e : ReviveClient.mc.world.getEntities()) {
               if (e instanceof EndCrystalEntity crystal && e.squaredDistanceTo(ReviveClient.mc.player) <= 144.0) {
                  out.add(crystal);
               }
            }
         }

         this.crystalsCache = out;
         return out;
      }
   }

   record BasePlaceTarget(BlockPos base, BlockHitResult hit, float damage) {
   }

   record BreakRender(Box box, long timeMs) {
   }

   record BreakTarget(EndCrystalEntity crystal, float totalDamage) {
   }

   record CalcResult(AutoCrystalFeature.PlaceTarget best, boolean readyBaseWaiting) {
   }

   static enum ComboKind {
      SEAL,
      OPEN,
      FILL;
   }

   record ComboPlan(AutoCrystalFeature.ComboKind kind, BlockPos x, Item glass, int dueIn, boolean followUp) {
      Vec3d aim() {
         return AutoCrystalFeature.comboAimPoint(this.x);
      }
   }

   private record CrystalGate(float damage, String why, String sig) {
      static AutoCrystalFeature.CrystalGate blocked(String why) {
         return new AutoCrystalFeature.CrystalGate(-1.0F, why, why);
      }

      static AutoCrystalFeature.CrystalGate blocked(String sig, String why) {
         return new AutoCrystalFeature.CrystalGate(-1.0F, why, sig);
      }
   }

   private static final class DbgState {
      final String msg;
      long printedMs;
      int repeats;

      DbgState(String msg, long printedMs) {
         this.msg = msg;
         this.printedMs = printedMs;
      }
   }

   record FollowUp(BlockPos x, long tick) {
   }

   record PlaceAim(BlockHitResult hit, Vec3d aimPoint) {
   }

   record PlaceTarget(BlockPos pos, float totalDamage) {
   }

   record QueuedPlace(BlockPos pos, boolean snap, long expiresTick, BlockPos pretendAir) {
   }

   record StagedCombo(AutoCrystalFeature.ComboPlan plan, Item item, float damage) {
   }

   record StagedPlace(AutoCrystalFeature.PlaceTarget crystal, AutoCrystalFeature.BasePlaceTarget base, String label, boolean queued) {
   }
}
