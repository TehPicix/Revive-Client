package revivedevelopment.reviveclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.world.GameMode;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.revive.SilentMineFinishedEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.systems.modules.render.BreakIndicators;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.SortPriority;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.render.NametagUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

public class AutoMine extends Module {
   private static final double INVALID_SCORE = -1000.0;
   private static final int MAX_TARGET_CANDIDATES = 5;
   private static final double CRAWL_HEAD_SCORE = 60.0;
   private static final double REPOSITION_MAX_PROGRESS = 0.5;
   private static final int DAMAGE_SYNC_RADIUS = 3;
   private static final int STUCK_PRIMARY_TICKS = 100;
   private static final int REBREAK_AIR_HOLD_TICKS = 60;
   private static final int REBREAK_RANGE_HOLD_TICKS = 20;
   private static final int HEAD_YIELD_MAX_TICKS = 20;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgAntiSurround = this.settings.createGroup("Anti Surround");
   private final SettingGroup sgSync = this.settings.createGroup("Break Sync");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<AutoMine.Meta> meta = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("meta"))
                  .description(
                     "Old = gware block picking, one block at a time. Crawl = adds phase block modes, mine-under, vertical extends, the crawling target's ceiling (only with AutoCrystal's cev-head on; otherwise the target is kept crawling) and automatic double-mine (side block first, then the block they're phased in)."
                  ))
               .defaultValue(AutoMine.Meta.Crawl))
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("Maximum distance to an enemy for it to be targeted. Blocks are limited by SilentMine's own break range.")
            .defaultValue(5.5)
            .min(0.0)
            .max(12.0)
            .sliderMax(12.0)
            .build()
      );
   private final Setting<Boolean> repositionIfNotAdjacent = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("reposition-if-not-adjacent")
            .description("Cancels the block being mined when it is no longer next to (or inside) the target, unless it is already half broken.")
            .defaultValue(true)
            .build()
      );
   private final Setting<AutoMine.BlockMode> blockMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("block-mode"))
                     .description(
                        "Crawl scoring extras. Hitbox = mine the blocks the target is phased in, DamageSync = favour blocks that open the best crystal, Air = favour blocks touching air. A phased-in block above the target's feet is only mined with AutoCrystal's cev-head on (otherwise the target is kept crawling)."
                     ))
                  .defaultValue(AutoMine.BlockMode.DamageSync))
               .visible(this::isCrawl))
            .build()
      );
   private final Setting<AutoMine.ExtendBreakMode> extendBreakMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("extend-break-mode"))
                  .description(
                     "Which outside surround geometry to mine. Vertical only works in the Crawl meta (Old treats it as None), and skips the target's own head block unless AutoCrystal's cev-head is on."
                  ))
               .defaultValue(AutoMine.ExtendBreakMode.Long))
            .build()
      );
   private final Setting<Boolean> mineUnder = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("mine-under")
            .description("Also mine below the target's surround and favour feet blocks with an air pocket under them.")
            .defaultValue(false)
            .visible(this::isCrawl)
            .build()
      );
   private final Setting<AutoMine.AntiSwimMode> antiSwim = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("anti-swim-mode"))
                  .description(
                     "Mines your own obsidian head block so enemies can't force you into the swim/crawl pose. Always = whenever there is one, OnMine = when BreakIndicators sees an enemy mining your feet block, OnMineAndSwim = OnMine plus whenever you are already crawling. Any mode also clears it while you're submerged."
                  ))
               .defaultValue(AutoMine.AntiSwimMode.None))
            .build()
      );
   private final Setting<AutoMine.AntiSwimPriority> antiSwimPriority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("anti-swim-priority"))
                  .description(
                     "Rebreak = your own head block waits while mining it would take the session of the surround rebreak you crystal the target through (at most 1 s while you crawl or are submerged, never while an enemy mines your feet block). Head = it goes at once."
                  ))
               .defaultValue(AutoMine.AntiSwimPriority.Rebreak))
            .build()
      );
   private final Setting<SortPriority> targetPriority = this.sgTargets
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target-priority"))
                  .description(
                     "Which enemy to mine around when several are in range. The best-ranked enemy with at least one minable block wins; the focused enemy is kept until it dies, leaves range or has nothing left to mine."
                  ))
               .defaultValue(SortPriority.LowestDistance))
            .build()
      );
   private final Setting<Boolean> ignoreNakeds = this.sgTargets
      .add(new BoolSetting.Builder().name("ignore-nakeds").description("Ignore players wearing no armor.").defaultValue(true).build());
   private final Setting<Boolean> survivalOnly = this.sgTargets
      .add(new BoolSetting.Builder().name("survival-only").description("Only target players in survival mode.").defaultValue(true).build());
   private final Setting<Boolean> ignoreFakePlayers = this.sgTargets
      .add(
         new BoolSetting.Builder()
            .name("ignore-fake-players")
            .description("Skip client-side fake players (a fake player spawned on yourself would make AutoMine mine your own surround).")
            .defaultValue(true)
            .build()
      );
   private final Setting<AutoMine.AntiSurroundMode> antiSurroundMode = this.sgAntiSurround
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("anti-surround-mode"))
                  .description(
                     "When a target's surround block breaks, blocks the re-place with a crystal (needs AutoCrystal with anti-surround on). Inner = crystal in the broken block, Outer = next to it with the crystal hitbox covering it, Auto = both."
                  ))
               .defaultValue(AutoMine.AntiSurroundMode.Auto))
            .build()
      );
   private final Setting<Boolean> antiSurroundInnerSnap = this.sgAntiSurround
      .add(
         new BoolSetting.Builder()
            .name("anti-surround-inner-snap")
            .description("Instantly snaps the rotation for the inner place.")
            .defaultValue(true)
            .visible(this::innerAntiSurround)
            .build()
      );
   private final Setting<Boolean> antiSurroundOuterSnap = this.sgAntiSurround
      .add(
         new BoolSetting.Builder()
            .name("anti-surround-outer-snap")
            .description("Instantly snaps the rotation for the outer place.")
            .defaultValue(true)
            .visible(this::outerAntiSurround)
            .build()
      );
   private final Setting<Double> antiSurroundOuterCooldown = this.sgAntiSurround
      .add(
         new DoubleSetting.Builder()
            .name("anti-surround-outer-cooldown")
            .description("Seconds to wait between outer anti-surround crystal places.")
            .defaultValue(0.1)
            .min(0.0)
            .sliderMax(1.0)
            .visible(this::outerAntiSurround)
            .build()
      );
   private final Setting<Boolean> glassSeal = this.sgAntiSurround
      .add(
         new BoolSetting.Builder()
            .name("glass-seal")
            .description(
               "Puts glass (or stained glass) into the target's surround block in the same packet batch as the STOP that breaks it: the server handles both before any packet of the enemy's, so they never see the hole and can't re-place it. Later the glass is broken with the crystal held and the crystal goes in behind it in the same batch: glass drops nothing, so the crystal isn't refused over our own obsidian drop. The rebreak is only given up when the target phases into a new block. Needs AutoCrystal with anti-surround; without glass the inner / outer crystals work as before."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<AutoMine.GlassSource> glassSource = this.sgAntiSurround
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("glass-source"))
                     .description(
                        "Where the seal's glass comes from. Hotbar = hands and hotbar (a slot select). Inventory = also the main inventory, through AutoCrystal's swap-mode and the item-swap-mode (container clicks, about 8 a second; Grim drops those while you sprint, sneak or hold a movement key, so then only glass in the hotbar seals)."
                     ))
                  .defaultValue(AutoMine.GlassSource.Hotbar))
               .visible(this.glassSeal::get))
            .build()
      );
   private final Setting<AutoMine.GlassOpenOrder> glassOpenOrder = this.sgAntiSurround
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("glass-open-order"))
                     .description(
                        "SameTick = the STOP and the glass / crystal behind it in one packet batch. NextTick = the STOP, then the glass / crystal on the next tick (Grim's PacketOrderI order; the enemy can re-place in between). Switches to NextTick by itself for the session when a 1.21.2+ server joined natively refuses the glass / crystal placed right behind its STOP three times in a row."
                     ))
                  .defaultValue(AutoMine.GlassOpenOrder.SameTick))
               .visible(this.glassSeal::get))
            .build()
      );
   private final Setting<Boolean> glassReseal = this.sgAntiSurround
      .add(
         new BoolSetting.Builder()
            .name("glass-reseal")
            .description(
               "Seals the block again with glass when the next crystal can't go in: behind our hit on the crystal in it, and when an explosion or the enemy cleared the glass."
            )
            .defaultValue(true)
            .visible(this.glassSeal::get)
            .build()
      );
   private final Setting<Boolean> breakIndicatorsSync = this.sgSync
      .add(
         new BoolSetting.Builder()
            .name("break-indicators-sync")
            .description(
               "Penalizes blocks another player is already mining (tracked by BreakIndicators, which must be enabled) so you mine a different one. With a teammate this leads to quad mine."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> breakIndicatorsSyncOnlyFriends = this.sgSync
      .add(
         new BoolSetting.Builder()
            .name("break-indicators-sync-only-friends")
            .description("Only penalize blocks friends are mining.")
            .defaultValue(false)
            .visible(this.breakIndicatorsSync::get)
            .build()
      );
   private final Setting<Double> breakIndicatorSyncPenalty = this.sgSync
      .add(
         new DoubleSetting.Builder()
            .name("break-indicators-sync-penalty")
            .description("Score penalty for a block being mined by someone else.")
            .defaultValue(8.5)
            .min(0.0)
            .sliderMax(25.0)
            .visible(this.breakIndicatorsSync::get)
            .build()
      );
   private final Setting<Boolean> renderDebugScores = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("render-debug-scores")
            .description("Renders every scored candidate block (brighter = better) with its score.")
            .defaultValue(false)
            .build()
      );
   private PlayerEntity targetPlayer;
   private AutoMine.CityBlock target1;
   private AutoMine.CityBlock target2;
   private BlockPos ignorePos;
   private BlockPos lastFedPrimary;
   private BlockPos lastFedDoubleMine;
   private BlockPos heldPrimaryPos;
   private int heldPrimaryTicks;
   private BlockPos sealOwnedPos;
   private long sealOwnedMs;
   private boolean headYield;
   private BlockPos headYieldPos;
   private int headYieldTicks;
   private PlayerEntity lostTarget;
   private final Set<BlockPos> lostTargetBlocks = new HashSet<>();
   private final Set<BlockPos> fedBlocks = new HashSet<>();
   private BlockPos deferLoggedPos;
   private final Set<String> deferLoggedWhy = new HashSet<>();
   private BlockPos soloLoggedPos;
   private BlockPos preemptedRebreakPos;
   private BlockPos preemptedForPrimary;
   private boolean extendPairQueued;
   private long lastOuterPlaceTime;
   private final Set<String> loggedOnce = new HashSet<>();
   private final Map<String, String> loggedState = new HashMap<>();
   private BlockPos dsLastSpot;
   private int dsSkippedInBox;
   private int dsSkippedOnMine;
   private String lastAntiSurroundDbg;
   private long lastAntiSurroundDbgMs;
   private Map<BlockPos, BreakIndicators.TrackedBlock> trackedSnapshot;
   private final Map<PlayerEntity, AutoMine.Candidates> candidateCache = new HashMap<>();
   private final List<AutoMine.CityBlock> debugScores = new ArrayList<>();
   private double debugBestScore;
   private final List<Entity> targetScratch = new ArrayList<>();

   public AutoMine() {
      super(Categories.Combat, "auto-mine", "Manages which blocks SilentMine should mine.");
   }

   public static AutoMine get() {
      return Modules.get().get(AutoMine.class);
   }

   private SilentMine silentMine() {
      return Modules.get().get(SilentMine.class);
   }

   private boolean isCrawl() {
      return this.meta.get() == AutoMine.Meta.Crawl;
   }

   private boolean innerAntiSurround() {
      return this.antiSurroundMode.get() == AutoMine.AntiSurroundMode.Auto || this.antiSurroundMode.get() == AutoMine.AntiSurroundMode.Inner;
   }

   private boolean outerAntiSurround() {
      return this.antiSurroundMode.get() == AutoMine.AntiSurroundMode.Auto || this.antiSurroundMode.get() == AutoMine.AntiSurroundMode.Outer;
   }

   private AutoMine.BlockMode activeBlockMode() {
      return this.isCrawl() ? this.blockMode.get() : AutoMine.BlockMode.Normal;
   }

   private AutoMine.ExtendBreakMode activeExtendMode() {
      AutoMine.ExtendBreakMode mode = this.extendBreakMode.get();
      return mode == AutoMine.ExtendBreakMode.Vertical && !this.isCrawl() ? AutoMine.ExtendBreakMode.None : mode;
   }

   private boolean activeMineUnder() {
      return this.isCrawl() && this.mineUnder.get();
   }

   public PlayerEntity getTargetPlayer() {
      return this.hasValidTarget() ? this.targetPlayer : null;
   }

   public boolean hasValidTarget() {
      return this.isActive() && this.isValidTarget(this.targetPlayer);
   }

   public boolean glassSealActive() {
      return this.glassSeal.get() && this.hasValidTarget();
   }

   public AutoMine.GlassSource glassSource() {
      return this.glassSource.get();
   }

   public boolean glassOpenNextTick() {
      return this.glassOpenOrder.get() == AutoMine.GlassOpenOrder.NextTick;
   }

   public boolean glassReseal() {
      return this.glassReseal.get();
   }

   public boolean isSealablePos(BlockPos pos) {
      if (pos != null && this.mc.world != null && this.glassSealActive() && this.isAroundTargetFeet(pos) && this.hasCrystalBaseBelow(pos)) {
         AutoCrystal autoCrystal = AutoCrystal.get();
         return autoCrystal != null && autoCrystal.canCrystalAt(pos);
      } else {
         return false;
      }
   }

   public boolean sealReleased(BlockPos pos) {
      AutoCrystal autoCrystal = AutoCrystal.get();
      return this.isActive()
            && this.glassSeal.get()
            && autoCrystal != null
            && autoCrystal.isRunningHere()
            && autoCrystal.antiSurround.get()
            && autoCrystal.placeCrystals.get()
         ? this.targetPlayer == null && this.lostTarget == null || this.isTargetFeetBlock(pos)
         : true;
   }

   public boolean crystalPausedForUse(BlockPos pos) {
      AutoCrystal autoCrystal = AutoCrystal.get();
      return pos != null
         && this.hasValidTarget()
         && this.fedBlocks.contains(pos)
         && !pos.equals(this.mc.player.getBlockPos().up())
         && (this.isAroundTargetFeet(pos) || this.isUnderAirborneTarget(pos))
         && autoCrystal != null
         && autoCrystal.isRunningHere()
         && autoCrystal.antiSurround.get()
         && autoCrystal.placeCrystals.get()
         && autoCrystal.pauseEatPlace.get();
   }

   public boolean isTargetFeetBlock(BlockPos pos) {
      return this.targetPlayer != null && pos != null && this.candidates(this.targetPlayer).feet.contains(pos);
   }

   @Override
   public void onActivate() {
      this.resetState();
   }

   @Override
   public void onDeactivate() {
      this.resetState();
   }

   private void resetState() {
      this.targetPlayer = null;
      this.rememberLostTarget(null, null);
      this.fedBlocks.clear();
      this.clearTargetState();
      this.lastFedDoubleMine = null;
      this.preemptedRebreakPos = null;
      this.preemptedForPrimary = null;
      this.lastOuterPlaceTime = 0L;
      this.trackedSnapshot = null;
      this.candidateCache.clear();
      this.debugBestScore = 0.0;
      this.loggedOnce.clear();
      this.loggedState.clear();
   }

   private void clearTargetState() {
      this.target1 = null;
      this.target2 = null;
      this.ignorePos = null;
      this.lastFedPrimary = null;
      this.heldPrimaryPos = null;
      this.heldPrimaryTicks = 0;
      this.sealOwnedPos = null;
      this.headYield = false;
      this.headYieldPos = null;
      this.headYieldTicks = 0;
      this.deferLoggedPos = null;
      this.deferLoggedWhy.clear();
      this.soloLoggedPos = null;
      this.debugScores.clear();
   }

   private void dbg(SilentMine silentMine, String msg) {
      if (silentMine != null && silentMine.debug.get()) {
         ChatUtils.infoPrefix("AutoMine", "%s", msg);
      }
   }

   private void dbgOnce(String key, String msg) {
      SilentMine silentMine = this.silentMine();
      if (silentMine != null && silentMine.debug.get()) {
         if (this.loggedOnce.size() > 64) {
            this.loggedOnce.clear();
         }

         if (this.loggedOnce.add(key)) {
            this.dbg(silentMine, msg);
         }
      }
   }

   private boolean silentMineDebug() {
      SilentMine silentMine = this.silentMine();
      return silentMine != null && silentMine.debug.get();
   }

   private void dbgChanged(String key, String sig, String msg) {
      SilentMine silentMine = this.silentMine();
      if (silentMine != null && silentMine.debug.get()) {
         if (!sig.equals(this.loggedState.get(key))) {
            if (this.loggedState.size() > 64) {
               this.loggedState.clear();
            }

            this.loggedState.put(key, sig);
            this.dbg(silentMine, msg);
         }
      }
   }

   private void noteDefer(SilentMine silentMine, BlockPos pos, String why) {
      if (pos != null) {
         if (!pos.equals(this.deferLoggedPos)) {
            this.deferLoggedPos = pos;
            this.deferLoggedWhy.clear();
         }

         if (this.deferLoggedWhy.add(why)) {
            this.dbg(silentMine, "leaving SilentMine alone for " + pos.toShortString() + ": " + why);
         }
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onTick(TickEvent.Pre event) {
      if (this.mc.world != null && this.mc.player != null) {
         this.candidateCache.clear();
         SilentMine silentMine = this.silentMine();
         if (silentMine != null && silentMine.isRunningHere()) {
            this.trackedSnapshot = this.snapshotBreakIndicators();
            this.fedBlocks.removeIf(pos -> !silentMine.isOwnMiningPos(pos));
            this.updateHeadYield(silentMine);
            boolean prioHead = this.updateAntiSwim(silentMine);
            PlayerEntity lastTarget = this.targetPlayer;
            if (lastTarget != null && !this.isValidTarget(lastTarget)) {
               this.loggedOnce.clear();
               this.loggedState.clear();
            }

            this.targetPlayer = this.findTarget();
            if (lastTarget != null) {
               this.releaseTargetRebreak(silentMine, lastTarget);
               this.rememberLostTarget(silentMine, this.targetPlayer == null ? lastTarget : null);
            } else if (this.lostTarget != null) {
               this.lostTargetBlocks.removeIf(pos -> !silentMine.isOwnMiningPos(pos));
               if (this.lostTargetBlocks.contains(silentMine.getRebreakPos())) {
                  this.releaseTargetRebreak(silentMine, this.lostTarget);
               }

               if (this.targetPlayer != null || this.lostTargetBlocks.isEmpty()) {
                  this.rememberLostTarget(silentMine, null);
               }
            }

            if (this.targetPlayer == null) {
               this.clearTargetState();
            } else {
               this.dbgTarget(silentMine);
               if (!prioHead) {
                  if (this.repositionIfNotAdjacent.get()) {
                     this.updateReposition(silentMine);
                  }

                  if (!this.isCrawl() && this.hasOpeningAroundTarget(silentMine)) {
                     BlockPos rebreakPos = silentMine.getRebreakPos();
                     this.noteDefer(silentMine, rebreakPos != null ? rebreakPos : silentMine.getDoubleMinePos(), "the target already has an opening next to it");
                     if (this.isSelfSubmerged()
                        && this.selfHeadBreakable()
                        && rebreakPos != null
                        && !silentMine.alreadyBreaking(this.mc.player.getBlockPos().up())
                        && !this.headWaits()) {
                        this.sendToSilentMine(silentMine, this.mc.player.getBlockPos().up(), 20.0);
                     }
                  } else if (!this.updateSubmergedHead(silentMine)) {
                     this.updateMining(silentMine);
                  }
               }
            }
         } else {
            this.targetPlayer = null;
            this.rememberLostTarget(null, null);
            this.fedBlocks.clear();
            this.clearTargetState();
            this.lastFedDoubleMine = null;
            this.preemptedRebreakPos = null;
            this.preemptedForPrimary = null;
         }
      }
   }

   private void releaseTargetRebreak(SilentMine silentMine, PlayerEntity lostTarget) {
      BlockPos rebreakPos = silentMine.getRebreakPos();
      if (rebreakPos != null && !rebreakPos.equals(this.mc.player.getBlockPos().up())) {
         if (this.fedBlocks.contains(rebreakPos)) {
            if (this.targetPlayer != lostTarget || lostTarget.isOnGround() || lostTarget instanceof FakePlayerEntity) {
               if (this.targetPlayer != null
                  ? !this.isNearTarget(this.targetPlayer, rebreakPos)
                  : !lostTarget.isAlive() || !this.isNearTarget(lostTarget, rebreakPos)) {
                  String why = this.targetPlayer == lostTarget
                     ? "moved away from it"
                     : (
                        this.targetPlayer != null
                           ? "replaced by " + this.targetPlayer.getName().getString()
                           : (lostTarget.isAlive() ? "lost (moved away from it)" : "dead or gone")
                     );
                  silentMine.abortRebreak(rebreakPos, "AutoMine target " + lostTarget.getName().getString() + " " + why);
               }
            }
         }
      }
   }

   private void dbgTarget(SilentMine silentMine) {
      if (silentMine.debug.get()) {
         Box box = this.targetPlayer.getBoundingBox();
         StringBuilder phased = new StringBuilder();

         for (BlockPos pos : BlockUtils.iterate(box.expand(-0.01))) {
            if (!this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos).isEmpty()) {
               phased.append(phased.length() > 0 ? " " : "").append(pos.toShortString());
            }
         }

         String sig = this.targetPlayer.getId()
            + " "
            + this.targetPlayer.getPose()
            + String.format(Locale.US, " %.1f ", box.maxY - box.minY)
            + this.targetPlayer.getBlockPos().asLong()
            + " "
            + phased;
         this.dbgChanged(
            "target",
            sig,
            String.format(
               Locale.US,
               "target %s pose=%s box=[%.2f..%.2f] feet=%s phased=[%s]",
               this.targetPlayer.getName().getString(),
               this.targetPlayer.getPose(),
               box.minY,
               box.maxY,
               this.targetPlayer.getBlockPos().toShortString(),
               phased
            )
         );
      }
   }

   private void rememberLostTarget(SilentMine silentMine, PlayerEntity target) {
      this.lostTarget = null;
      this.lostTargetBlocks.clear();
      if (target != null) {
         for (BlockPos pos : new BlockPos[]{silentMine.getDelayedDestroyBlockPos(), silentMine.getParkedPos(), silentMine.getRebreakBlockPos()}) {
            if (pos != null) {
               this.lostTargetBlocks.add(pos.toImmutable());
            }
         }

         if (!this.lostTargetBlocks.isEmpty()) {
            this.lostTarget = target;
         }
      }
   }

   private boolean selfHeadBreakable() {
      BlockPos selfHeadPos = this.mc.player.getBlockPos().up();
      BlockState selfHeadBlock = this.mc.world.getBlockState(selfHeadPos);
      return (selfHeadBlock.isOf(Blocks.OBSIDIAN) || selfHeadBlock.isOf(Blocks.CRYING_OBSIDIAN)) && BlockUtils.canBreak(selfHeadPos, selfHeadBlock);
   }

   private boolean isSelfSubmerged() {
      return this.mc.player.isSubmergedInWater() || this.mc.player.isInLava();
   }

   private boolean updateAntiSwim(SilentMine silentMine) {
      BlockPos selfHeadPos = this.mc.player.getBlockPos().up();
      BlockState selfHeadState = this.mc.world.getBlockState(selfHeadPos);
      if (silentMine.getDelayedDestroyBlockPos() != null
         && this.mc.world.getBlockState(this.mc.player.getBlockPos()).isAir()
         && (selfHeadState.isOf(Blocks.OBSIDIAN) || selfHeadState.isOf(Blocks.CRYING_OBSIDIAN))
         && selfHeadPos.equals(silentMine.getRebreakPos())) {
         this.debugScores.clear();
         return true;
      } else {
         AutoMine.AntiSwimMode mode = this.antiSwim.get();
         if (mode != AutoMine.AntiSwimMode.None && this.selfHeadBreakable()) {
            double priority;
            if (mode == AutoMine.AntiSwimMode.OnMineAndSwim && this.mc.player.isCrawling()) {
               priority = 30.0;
            } else if (mode != AutoMine.AntiSwimMode.Always && this.isBeingMinedByOther(this.mc.player.getBlockPos())) {
               priority = 20.0;
            } else {
               if (mode != AutoMine.AntiSwimMode.Always) {
                  return false;
               }

               priority = 10.0;
            }

            if (!silentMine.alreadyBreaking(selfHeadPos) && !this.isRebreaking(silentMine, selfHeadPos)) {
               if (this.headWaits()) {
                  return this.headYieldTicks > 0;
               }

               this.sendToSilentMine(silentMine, selfHeadPos, priority);
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private void updateHeadYield(SilentMine silentMine) {
      BlockPos held = null;
      if (this.antiSwimPriority.get() == AutoMine.AntiSwimPriority.Rebreak && this.isValidTarget(this.targetPlayer) && this.selfHeadBreakable()) {
         BlockPos selfHead = this.mc.player.getBlockPos().up();

         for (BlockPos pos : new BlockPos[]{silentMine.getRebreakPos(), silentMine.getDelayedDestroyBlockPos()}) {
            if (pos != null && !pos.equals(selfHead) && (this.isAroundTargetFeet(pos) || this.isUnderAirborneTarget(pos))) {
               held = pos.toImmutable();
               break;
            }
         }
      }

      boolean urgent = this.mc.player.isInSwimmingPose() || this.isSelfSubmerged();
      this.headYieldTicks = held != null && urgent ? this.headYieldTicks + 1 : 0;
      this.headYieldPos = held;
      this.headYield = held != null && this.headYieldTicks <= 20 && !this.isBeingMinedByOther(this.mc.player.getBlockPos());
   }

   private boolean headWaits() {
      BlockPos head = this.mc.player.getBlockPos().up();
      if (this.headYield) {
         this.dbgChanged(
            "head-yield",
            "wait " + head.asLong() + " " + this.headYieldPos.asLong(),
            "head "
               + head.toShortString()
               + " deferred: surround rebreak "
               + this.headYieldPos.toShortString()
               + " held (our head would take its session)"
               + (this.headYieldTicks > 0 ? "; we crawl / are submerged: at most 20 ticks" : "")
         );
      } else if (this.headYieldPos != null) {
         boolean minedUnder = this.headYieldTicks <= 20;
         this.dbgChanged(
            "head-yield",
            "go " + head.asLong() + " " + this.headYieldPos.asLong() + " " + minedUnder,
            "head "
               + head.toShortString()
               + (minedUnder ? " goes now (another player mines our feet block)" : " goes after 20 ticks crawling / submerged")
               + ": it takes the session from surround rebreak "
               + this.headYieldPos.toShortString()
         );
      }

      return this.headYield;
   }

   private boolean updateSubmergedHead(SilentMine silentMine) {
      if (this.antiSwim.get() == AutoMine.AntiSwimMode.None || !this.isSelfSubmerged() || !this.selfHeadBreakable()) {
         return false;
      } else if (silentMine.getDoubleMinePos() != null) {
         return false;
      } else {
         BlockPos selfHeadPos = this.mc.player.getBlockPos().up();
         if (silentMine.alreadyBreaking(selfHeadPos)) {
            return this.isRebreaking(silentMine, selfHeadPos);
         } else if (this.headWaits()) {
            return true;
         } else {
            this.sendToSilentMine(silentMine, selfHeadPos, 20.0);
            return this.isRebreaking(silentMine, selfHeadPos);
         }
      }
   }

   private boolean isBeingMinedByOther(BlockPos pos) {
      if (this.trackedSnapshot == null) {
         return false;
      } else {
         BreakIndicators.TrackedBlock tracked = this.trackedSnapshot.get(pos);
         return tracked != null && tracked.player != this.mc.player;
      }
   }

   private void updateReposition(SilentMine silentMine) {
      BlockPos primary = silentMine.getDelayedDestroyBlockPos();
      if (primary != null && primary.equals(this.lastFedPrimary)) {
         if (!this.isNearTarget(this.targetPlayer, primary)) {
            if (!(silentMine.getDelayedDestroyProgress() >= 0.5)) {
               silentMine.cancelBreaking("AutoMine reposition: primary " + primary.toShortString() + " no longer next to the target");
               this.target1 = null;
               this.target2 = null;
               this.lastFedPrimary = null;
               this.heldPrimaryPos = null;
               this.heldPrimaryTicks = 0;
            }
         }
      }
   }

   private boolean isNearTarget(PlayerEntity player, BlockPos pos) {
      if (player.getBoundingBox().expand(-0.01).intersects(new Box(pos))) {
         return true;
      } else {
         boolean extend = this.activeExtendMode() != AutoMine.ExtendBreakMode.None;

         for (BlockPos feet : this.candidates(player).feet) {
            int dy = pos.getY() - feet.getY();
            int h = Math.abs(pos.getX() - feet.getX()) + Math.abs(pos.getZ() - feet.getZ());
            if (h == 0 && dy >= -1 && dy <= 2) {
               return true;
            }

            if (h == 1 && dy >= -1 && dy <= 1) {
               return true;
            }

            if (h == 2 && extend && dy >= -1 && dy <= 1) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean hasOpeningAroundTarget(SilentMine silentMine) {
      if (this.targetPlayer == null) {
         return false;
      } else if (this.candidates(this.targetPlayer).feet.size() > 1) {
         return false;
      } else if (this.isPlayerAtBlockCorner(this.targetPlayer)) {
         return false;
      } else {
         BlockPos doubleMinePos = silentMine.getDoubleMinePos();
         if (silentMine.getRebreakPos() == null && doubleMinePos == null) {
            return false;
         } else {
            Box box = this.targetPlayer.getBoundingBox().expand(-0.01, -0.1, -0.01);
            Box bodyBox = new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY + 1.6, box.maxZ);

            for (BlockPos pos : BlockUtils.iterate(bodyBox)) {
               BlockState state = this.mc.world.getBlockState(pos);
               if (!state.isAir() && !state.isOf(Blocks.WATER) && !state.isOf(Blocks.LAVA)) {
                  return false;
               }
            }

            BlockPos feet = this.targetPlayer.getBlockPos();

            for (Direction dir : Type.HORIZONTAL) {
               BlockPos surroundPos = feet.offset(dir);
               if (this.mc.world.getBlockState(surroundPos).isAir() || surroundPos.equals(doubleMinePos)) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private boolean isPlayerAtBlockCorner(PlayerEntity player) {
      double xFraction = Math.abs(player.getX() - Math.floor(player.getX()));
      double zFraction = Math.abs(player.getZ() - Math.floor(player.getZ()));
      return (xFraction < 0.3 || xFraction > 0.7) && (zFraction < 0.3 || zFraction > 0.7);
   }

   @EventHandler
   private void onSilentMineFinishedPre(SilentMineFinishedEvent.Pre event) {
      this.handleAntiSurround(event.getBlockPos());
   }

   @EventHandler
   private void onSilentMineFinishedPost(SilentMineFinishedEvent.Post event) {
      this.handleAntiSurround(event.getBlockPos());
   }

   private void handleAntiSurround(BlockPos brokenPos) {
      if (brokenPos != null && this.mc.world != null && this.mc.player != null) {
         if (this.outerAntiSurround() || this.innerAntiSurround()) {
            if (this.isValidTarget(this.targetPlayer) && this.isTargetSurroundPos(brokenPos)) {
               AutoCrystal autoCrystal = AutoCrystal.get();
               if (autoCrystal != null && autoCrystal.isRunningHere() && autoCrystal.antiSurround.get()) {
                  SilentMine silentMine = this.silentMine();
                  boolean dropped = silentMine != null && silentMine.expectsDrop(brokenPos);
                  if (autoCrystal.glassSealOwns(brokenPos)) {
                     if (silentMine != null && silentMine.debug.get()) {
                        this.dbgAntiSurround(
                           silentMine,
                           "anti-surround "
                              + brokenPos.toShortString()
                              + ": dropped="
                              + (dropped ? "y" : "n")
                              + " -> combo (glass seal owns it: no inner / outer crystal)"
                        );
                     }
                  } else {
                     boolean outer = this.outerAntiSurround() && this.tryOuterAntiSurround(autoCrystal, brokenPos);
                     boolean inner = this.innerAntiSurround()
                        && !dropped
                        && autoCrystal.preplaceCrystal(brokenPos.toImmutable(), this.antiSurroundInnerSnap.get(), brokenPos);
                     if (silentMine != null && silentMine.debug.get()) {
                        this.dbgAntiSurround(
                           silentMine,
                           "anti-surround "
                              + brokenPos.toShortString()
                              + ": dropped="
                              + (dropped ? "y" : "n")
                              + " -> "
                              + (inner ? (outer ? "inner (replaces the queued outer)" : "inner") : (outer ? "outer" : "none queued"))
                              + (dropped && this.innerAntiSurround() ? ", inner skipped: our drop is in the hole" : "")
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private void dbgAntiSurround(SilentMine silentMine, String line) {
      long now = System.currentTimeMillis();
      if (!line.equals(this.lastAntiSurroundDbg) || now - this.lastAntiSurroundDbgMs >= 1000L) {
         this.lastAntiSurroundDbg = line;
         this.lastAntiSurroundDbgMs = now;
         this.dbg(silentMine, line);
      }
   }

   private boolean isTargetSurroundPos(BlockPos pos) {
      if (this.targetPlayer != null && pos != null) {
         BlockPos feet = this.targetPlayer.getBlockPos();

         for (Direction dir : Type.HORIZONTAL) {
            if (feet.offset(dir).equals(pos)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean tryOuterAntiSurround(AutoCrystal autoCrystal, BlockPos surroundPos) {
      long now = System.currentTimeMillis();
      if (now - this.lastOuterPlaceTime <= (long)(this.antiSurroundOuterCooldown.get() * 1000.0)) {
         return false;
      } else {
         Vec3d center = Vec3d.ofCenter(surroundPos);
         Box checkBox = new Box(center.x - 1.25, center.y - 1.5, center.z - 1.25, center.x + 1.25, center.y + 1.5, center.z + 1.25);
         Box surroundHitbox = new Box(surroundPos);

         for (BlockPos blockPos : BlockUtils.iterate(checkBox)) {
            if (!blockPos.equals(surroundPos) && this.mc.world.getBlockState(blockPos).isAir()) {
               BlockState downState = this.mc.world.getBlockState(blockPos.down());
               if (downState.isOf(Blocks.OBSIDIAN) || downState.isOf(Blocks.BEDROCK)) {
                  Box crystalPlaceBox = new Box(
                     blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos.getX() + 1, blockPos.getY() + 2, blockPos.getZ() + 1
                  );
                  if (!EntityUtils.intersectsWithEntity(crystalPlaceBox, entity -> !entity.isSpectator())) {
                     double cx = blockPos.getX() + 0.5;
                     double cy = blockPos.getY();
                     double cz = blockPos.getZ() + 0.5;
                     Box crystalHitbox = new Box(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0);
                     if (crystalHitbox.intersects(surroundHitbox)
                        && autoCrystal.preplaceCrystal(blockPos.toImmutable(), this.antiSurroundOuterSnap.get(), surroundPos)) {
                        this.lastOuterPlaceTime = now;
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.renderDebugScores.get() && this.targetPlayer != null && !this.debugScores.isEmpty()) {
         double best = this.debugBestScore;

         for (AutoMine.CityBlock block : this.debugScores) {
            double alpha = best > 0.0 ? MathHelper.clamp(block.score / best, 0.0, 1.0) / 4.0 : 0.05;
            Color side = new Color(255, 0, 0, (int)(255.0 * alpha));
            event.renderer.box(block.blockPos, side, Color.WHITE, ShapeMode.Sides, 0);
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.renderDebugScores.get() && this.targetPlayer != null && !this.debugScores.isEmpty()) {
         if (this.mc.world != null && this.mc.player != null) {
            for (AutoMine.CityBlock block : this.debugScores) {
               Vector3d screen = new Vector3d(block.blockPos.getX() + 0.5, block.blockPos.getY() + 0.5, block.blockPos.getZ() + 0.5);
               if (NametagUtils.to2D(screen, 1.25)) {
                  String text = String.format(Locale.US, "%.1f", block.score);
                  int width = this.mc.textRenderer.getWidth(text);
                  NametagUtils.begin(screen, event.drawContext);
                  event.drawContext.drawText(this.mc.textRenderer, text, -width / 2, -9 / 2, -1, true);
                  NametagUtils.end(event.drawContext);
               }
            }
         }
      }
   }

   private void updateMining(SilentMine silentMine) {
      List<AutoMine.CityBlock> blocks = this.getScoreRankedBlocks(this.targetPlayer);
      this.target1 = blocks.size() > 0 ? blocks.get(0) : null;
      this.target2 = blocks.size() > 1 ? blocks.get(1) : null;
      if (!blocks.isEmpty()) {
         BlockPos rebreakPos = silentMine.getRebreakPos();
         BlockPos parkedPos = silentMine.getParkedPos();
         boolean isTargetingFeetBlock = this.target1 != null && !this.target1.blockPos.equals(parkedPos) && this.preemptsRebreak(this.target1, rebreakPos)
            || this.target2 != null && !this.target2.blockPos.equals(parkedPos) && this.preemptsRebreak(this.target2, rebreakPos);
         if (!isTargetingFeetBlock && rebreakPos != null) {
            boolean topTarget = this.target1 != null && this.target1.blockPos.equals(rebreakPos)
               || this.target2 != null && this.target2.blockPos.equals(rebreakPos);
            boolean aroundFeet = this.isAroundTargetFeet(rebreakPos);
            boolean surround = (aroundFeet || this.isUnderAirborneTarget(rebreakPos)) && silentMine.getRebreakTicksOutOfRange() <= 20.0;
            boolean airPastHold = surround && !this.canBreak(rebreakPos) && silentMine.getRebreakTicksSinceReplace() > 60.0;
            boolean sealOwned = surround && this.glassSealOwns(rebreakPos);
            long now = System.currentTimeMillis();
            if (sealOwned) {
               this.sealOwnedPos = rebreakPos.toImmutable();
               this.sealOwnedMs = now;
            }

            boolean sealLapse = !sealOwned && surround && rebreakPos.equals(this.sealOwnedPos) && now - this.sealOwnedMs <= 1000L;
            boolean airTooLong = airPastHold && !sealOwned && !sealLapse;
            if (topTarget || surround && !airTooLong) {
               this.noteDefer(
                  silentMine,
                  rebreakPos,
                  topTarget
                     ? "armed rebreak is a top target"
                     : (
                        !aroundFeet
                           ? "armed rebreak under the airborne target"
                           : (
                              airPastHold && sealLapse
                                 ? "armed rebreak in the target's surround, the glass seal's (out of crystal reach / no glass at hand for under 20 ticks)"
                                 : (
                                    this.inBreakRange(rebreakPos)
                                       ? "armed rebreak in the target's surround"
                                       : "armed rebreak in the target's surround, briefly out of break range"
                                 )
                           )
                     )
               );
               return;
            }

            if (airTooLong && rebreakPos.equals(this.deferLoggedPos)) {
               this.dbg(
                  silentMine,
                  "armed rebreak "
                     + rebreakPos.toShortString()
                     + " still air 60 ticks after its last re-place: opening the next block (its START ends the rebreak)"
               );
            }
         }

         this.deferLoggedPos = null;
         List<BlockPos> queue = this.buildMiningQueue(blocks);
         if (!queue.isEmpty()) {
            if (this.isCrawl()) {
               this.orderCrawlPair(queue);
            }

            if (queue.size() >= 2
               && !this.extendPairQueued
               && silentMine.isParkDoubleMine()
               && !this.isInsideTarget(queue.get(0))
               && !queue.get(0).equals(this.getCrawlCeiling(this.targetPlayer))) {
               BlockPos first = queue.get(0);
               queue.set(0, queue.get(1));
               queue.set(1, first);
            }

            BlockPos delayedPos = silentMine.getDelayedDestroyBlockPos();
            if (delayedPos != null) {
               boolean sealHold = silentMine.isOwnSeal(delayedPos) && this.isSealablePos(delayedPos);
               AutoMine.CityBlock seizer = !sealHold || silentMine.isPrimaryStopSent() || parkedPos != null
                  ? null
                  : (
                     this.target1 != null && this.preemptsRebreak(this.target1, delayedPos)
                        ? this.target1
                        : (this.target2 != null && this.preemptsRebreak(this.target2, delayedPos) ? this.target2 : null)
                  );
               if (seizer != null) {
                  silentMine.cancelBreaking(
                     "AutoMine: "
                        + seizer.type.name()
                        + " pick "
                        + seizer.blockPos.toShortString()
                        + " takes the session from our glass seal "
                        + delayedPos.toShortString()
                        + " (the glass stays)"
                  );
                  this.heldPrimaryPos = null;
                  this.heldPrimaryTicks = 0;
                  this.sendToSilentMine(silentMine, seizer.blockPos, 200.0);
                  if (seizer.blockPos.equals(silentMine.getDelayedDestroyBlockPos())) {
                     this.lastFedPrimary = seizer.blockPos;
                     this.lastFedDoubleMine = null;
                  }

                  return;
               }

               this.heldPrimaryTicks = delayedPos.equals(this.heldPrimaryPos) && !sealHold ? this.heldPrimaryTicks + 1 : 0;
               this.heldPrimaryPos = delayedPos;
               if (this.heldPrimaryTicks > 100) {
                  silentMine.cancelBreaking("AutoMine: primary " + delayedPos.toShortString() + " stuck for 100 ticks");
                  this.heldPrimaryPos = null;
                  this.heldPrimaryTicks = 0;
                  return;
               }
            } else {
               this.heldPrimaryPos = null;
               this.heldPrimaryTicks = 0;
               BlockPos primary = queue.get(0);
               if (!this.isRebreaking(silentMine, primary)) {
                  BlockPos preempted = rebreakPos != null
                        && this.isInsideTarget(primary)
                        && this.fedBlocks.contains(rebreakPos)
                        && this.isAroundTargetFeet(rebreakPos)
                     ? rebreakPos
                     : null;
                  this.sendToSilentMine(silentMine, primary, 200.0);
                  if (primary.equals(silentMine.getDelayedDestroyBlockPos())) {
                     this.dbgQueue(silentMine, queue);
                     if (rebreakPos != null && silentMine.getRebreakPos() == null) {
                        AutoMine.CityBlock pick = this.rankedPick(primary);
                        this.dbg(
                           silentMine,
                           "session "
                              + rebreakPos.toShortString()
                              + " -> "
                              + primary.toShortString()
                              + " by AutoMine prio=200 type="
                              + (pick != null ? pick.type.name() : "unranked")
                        );
                     }

                     this.lastFedPrimary = primary;
                     this.lastFedDoubleMine = null;
                     this.soloLoggedPos = null;
                     this.preemptedRebreakPos = preempted;
                     this.preemptedForPrimary = primary;
                     if (preempted != null) {
                        this.dbg(
                           silentMine,
                           "feet block "
                              + primary.toShortString()
                              + " ends rebreak "
                              + preempted.toShortString()
                              + ": no double-mine while it is air (re-placed, the park opens it again)"
                        );
                     }
                  }
               }
            }

            this.feedDoubleMine(silentMine, queue);
         }
      }
   }

   private void feedDoubleMine(SilentMine silentMine, List<BlockPos> queue) {
      if (silentMine.getRebreakPos() == null && silentMine.getDoubleMinePos() == null) {
         BlockPos primary = silentMine.getDelayedDestroyBlockPos();
         if (primary != null) {
            if (!primary.equals(this.lastFedDoubleMine) && silentMine.getParkedPos() == null && !silentMine.isPrimaryStopSent()) {
               List<BlockPos> order = queue;
               boolean park = silentMine.isParkDoubleMine();
               if (park && queue.size() >= 2 && !this.preemptsRebreak(this.rankedPick(queue.get(0)), primary)) {
                  if (queue.get(1).equals(primary)) {
                     if (!primary.equals(this.soloLoggedPos)) {
                        this.soloLoggedPos = primary;
                        this.dbg(
                           silentMine,
                           "no double-mine behind primary "
                              + primary.toShortString()
                              + ": it is the pick that should own the session (a park would open "
                              + queue.get(0).toShortString()
                              + " instead)"
                        );
                     }

                     return;
                  }

                  order = new ArrayList<>(queue);
                  order.add(0, order.remove(1));
               }

               if (!primary.equals(this.preemptedForPrimary)
                  || this.preemptedRebreakPos == null
                  || this.canBreak(this.preemptedRebreakPos)
                  || !this.isAroundTargetFeet(this.preemptedRebreakPos)) {
                  for (BlockPos pos : order) {
                     if (!pos.equals(primary) && !silentMine.alreadyBreaking(pos)) {
                        this.sendToSilentMine(silentMine, pos, 190.0);
                        if (pos.equals(silentMine.getDoubleMinePos())) {
                           this.lastFedDoubleMine = pos;
                           this.dbg(
                              silentMine,
                              "reserved double-mine "
                                 + pos.toShortString()
                                 + " behind primary "
                                 + primary.toShortString()
                                 + (park ? " (opened at the park once the burst is out: it owns the session)" : " (opened once the primary breaks)")
                           );
                        }

                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private void dbgQueue(SilentMine silentMine, List<BlockPos> queue) {
      if (silentMine.debug.get()) {
         StringBuilder line = new StringBuilder("queue [");

         for (int i = 0; i < queue.size() && i < 2; i++) {
            AutoMine.CityBlock pick = this.rankedPick(queue.get(i));
            line.append(i > 0 ? " " : "").append(queue.get(i).toShortString()).append('(').append(pick != null ? pick.type.name() : "inner").append(')');
         }

         line.append(']');
         if (queue.size() >= 2) {
            line.append(
               silentMine.isParkDoubleMine()
                  ? " -> park " + queue.get(0).toShortString() + ", open " + queue.get(1).toShortString()
                  : " -> " + queue.get(0).toShortString() + " first, then " + queue.get(1).toShortString()
            );
         }

         this.dbgChanged("queue", line.toString(), line.toString());
      }
   }

   private AutoMine.CityBlock rankedPick(BlockPos pos) {
      if (this.target1 != null && this.target1.blockPos.equals(pos)) {
         return this.target1;
      } else {
         return this.target2 != null && this.target2.blockPos.equals(pos) ? this.target2 : null;
      }
   }

   private boolean preemptsRebreak(AutoMine.CityBlock block, BlockPos rebreakPos) {
      if (block == null) {
         return false;
      } else if (block.isFeetBlock) {
         return true;
      } else if (block.blockPos.equals(rebreakPos)) {
         return false;
      } else if (block.type == AutoMine.CandidateType.PHASE && !this.isFutureCeiling(block.blockPos)) {
         return true;
      } else if (block.type != AutoMine.CandidateType.CRAWL_HEAD && block.type != AutoMine.CandidateType.PHASE) {
         return false;
      } else if (rebreakPos != null && this.isAroundTargetFeet(rebreakPos)) {
         this.dbgOnce(
            "ceiling-hold " + block.blockPos.asLong() + " " + rebreakPos.asLong(),
            "ceiling "
               + block.blockPos.toShortString()
               + " not pre-empting surround "
               + rebreakPos.toShortString()
               + " (it keeps the session; the ceiling goes in as a parked block)"
         );
         return false;
      } else {
         return true;
      }
   }

   private boolean isFutureCeiling(BlockPos pos) {
      return this.targetPlayer != null && pos.getY() > MathHelper.floor(this.targetPlayer.getY()) && !this.candidates(this.targetPlayer).inBedrock;
   }

   private boolean isAroundTargetFeet(BlockPos pos) {
      if (this.targetPlayer != null && pos != null) {
         List<BlockPos> feet = this.candidates(this.targetPlayer).feet;
         if (feet.contains(pos)) {
            return false;
         } else {
            for (BlockPos feetPos : feet) {
               for (Direction dir : Type.HORIZONTAL) {
                  if (feetPos.offset(dir).equals(pos)) {
                     return true;
                  }
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isUnderAirborneTarget(BlockPos pos) {
      if (this.targetPlayer != null && pos != null && !this.targetPlayer.isOnGround() && !(this.targetPlayer instanceof FakePlayerEntity)) {
         for (BlockPos feetPos : this.candidates(this.targetPlayer).feet) {
            int dy = feetPos.getY() - pos.getY();
            int h = Math.abs(pos.getX() - feetPos.getX()) + Math.abs(pos.getZ() - feetPos.getZ());
            if (h == 1 && dy >= 1 && dy <= 3) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void orderCrawlPair(List<BlockPos> queue) {
      if (queue.size() >= 2 && this.targetPlayer != null) {
         if (this.isInsideTarget(queue.get(0)) && !this.isInsideTarget(queue.get(1))) {
            BlockPos first = queue.get(0);
            queue.set(0, queue.get(1));
            queue.set(1, first);
         }
      }
   }

   private boolean isInsideTarget(BlockPos pos) {
      return this.isBlockInFeet(pos, this.targetPlayer) || this.targetPlayer.getBoundingBox().expand(-0.01).intersects(new Box(pos));
   }

   private List<BlockPos> buildMiningQueue(List<AutoMine.CityBlock> blocks) {
      List<BlockPos> queue = new ArrayList<>(3);
      Set<BlockPos> seen = new HashSet<>();
      this.extendPairQueued = false;
      AutoMine.CityBlock outerExtend = null;

      for (AutoMine.CityBlock block : blocks) {
         if (block.type == AutoMine.CandidateType.EXTEND) {
            outerExtend = block;
            break;
         }
      }

      AutoMine.CityBlock top = blocks.get(0);
      if (this.isCrawl() && outerExtend != null && outerExtend != top && (top.isFeetBlock || top.type == AutoMine.CandidateType.PHASE)) {
         BlockPos inner = this.getInnerBlockForExtend(outerExtend.blockPos, this.targetPlayer.getBlockPos());
         outerExtend = null;
         if (inner != null && this.isBreakCandidateValid(inner) && !inner.equals(top.blockPos)) {
            this.dbgOnce(
               "extend-suppressed " + top.blockPos.asLong() + " " + inner.asLong(),
               "extend pair suppressed: top "
                  + top.blockPos.toShortString()
                  + " "
                  + top.type
                  + " -> ["
                  + top.blockPos.toShortString()
                  + ", inner "
                  + inner.toShortString()
                  + "]"
            );
            queue.add(top.blockPos);
            queue.add(inner);
            queue.removeIf(pos -> !this.isBreakCandidateValid(pos));
            return queue;
         }
      }

      if (outerExtend != null) {
         BlockPos inner = this.getInnerBlockForExtend(outerExtend.blockPos, this.targetPlayer.getBlockPos());
         if (inner != null && this.isBreakCandidateValid(inner)) {
            queue.add(outerExtend.blockPos);
            seen.add(outerExtend.blockPos);
            if (seen.add(inner)) {
               queue.add(inner);
            }

            queue.removeIf(pos -> !this.isBreakCandidateValid(pos));
            this.extendPairQueued = true;
            return queue;
         }
      }

      for (AutoMine.CityBlock blockx : blocks) {
         if (seen.add(blockx.blockPos)) {
            queue.add(blockx.blockPos);
         }
      }

      queue.removeIf(pos -> !this.isBreakCandidateValid(pos));
      return queue;
   }

   private void sendToSilentMine(SilentMine silentMine, BlockPos pos, double priority) {
      if (silentMine != null && pos != null) {
         silentMine.silentBreakBlock(pos, Direction.UP, priority);
         if (silentMine.isOwnMiningPos(pos)) {
            this.fedBlocks.add(pos.toImmutable());
         }
      }
   }

   private boolean isRebreaking(SilentMine silentMine, BlockPos pos) {
      return pos.equals(silentMine.getRebreakPos()) || pos.equals(silentMine.getDoubleMinePos());
   }

   private boolean isBreakCandidateValid(BlockPos pos) {
      if (pos == null || this.mc.world == null || this.mc.player == null) {
         return false;
      } else if (!this.inBreakRange(pos)) {
         return false;
      } else {
         return this.isOwnCrystalBase(pos) ? false : this.canBreak(pos);
      }
   }

   private boolean isOwnCrystalBase(BlockPos pos) {
      AutoCrystal autoCrystal = AutoCrystal.get();
      return autoCrystal != null && autoCrystal.isOwnBasePlace(pos);
   }

   private AutoMine.Candidates candidates(PlayerEntity target) {
      return this.candidateCache.computeIfAbsent(target, this::buildCandidates);
   }

   private AutoMine.Candidates buildCandidates(PlayerEntity target) {
      List<BlockPos> feet = this.getInsideBlocks(this.feetBox(target), MathHelper.floor(target.getY()));
      boolean inBedrock = false;

      for (BlockPos pos : feet) {
         if (this.mc.world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
            inBedrock = true;
            break;
         }
      }

      Set<AutoMine.Candidate> raw = new LinkedHashSet<>();
      if (this.isCrawl() && this.activeBlockMode() != AutoMine.BlockMode.Normal) {
         this.addPhaseCandidates(target, raw, inBedrock);
      }

      if (inBedrock) {
         this.addBedrockCandidates(target, feet, raw);
      } else {
         this.addNormalCandidates(target, feet, raw);
      }

      if (this.isCrawl()) {
         this.addCrawlHeadCandidate(target, raw);
      }

      return new AutoMine.Candidates(raw, inBedrock, feet);
   }

   private void addNormalCandidates(PlayerEntity target, List<BlockPos> inside, Set<AutoMine.Candidate> candidates) {
      boolean mineUnderActive = this.activeMineUnder();

      for (BlockPos pos : inside) {
         candidates.add(new AutoMine.Candidate(pos, AutoMine.CandidateType.FEET));
      }

      for (BlockPos pos : inside) {
         for (Direction dir : Type.HORIZONTAL) {
            candidates.add(new AutoMine.Candidate(pos.offset(dir), AutoMine.CandidateType.SURROUND));
            if (mineUnderActive) {
               candidates.add(new AutoMine.Candidate(pos.offset(dir).down(), AutoMine.CandidateType.BELOW_SURROUND));
            }
         }
      }

      BlockPos feetPos = target.getBlockPos();
      candidates.add(new AutoMine.Candidate(feetPos, AutoMine.CandidateType.FEET));
      if (inside.size() <= 1) {
         AutoMine.ExtendBreakMode mode = this.activeExtendMode();
         if (mode != AutoMine.ExtendBreakMode.None) {
            for (Direction dirx : Type.HORIZONTAL) {
               switch (mode) {
                  case Long:
                     candidates.add(new AutoMine.Candidate(feetPos.offset(dirx, 2), AutoMine.CandidateType.EXTEND));
                     break;
                  case Corner:
                     candidates.add(new AutoMine.Candidate(feetPos.offset(dirx).offset(dirx.rotateYClockwise()), AutoMine.CandidateType.EXTEND));
                     candidates.add(new AutoMine.Candidate(feetPos.offset(dirx).offset(dirx.rotateYCounterclockwise()), AutoMine.CandidateType.EXTEND));
                     break;
                  case Vertical:
                     this.addVerticalExtendCandidates(candidates, feetPos, dirx);
               }
            }
         }
      }
   }

   private void addVerticalExtendCandidates(Set<AutoMine.Candidate> candidates, BlockPos feetPos, Direction dir) {
      List<BlockPos> basePositions = new ArrayList<>(2);
      if (!this.mc.world.getBlockState(feetPos).isAir()) {
         basePositions.add(feetPos);
      } else {
         BlockPos surroundPos = feetPos.offset(dir);
         if (!this.mc.world.getBlockState(surroundPos).isAir()) {
            basePositions.add(surroundPos);
         }

         BlockPos surroundBelow = surroundPos.down();
         if (!this.mc.world.getBlockState(surroundBelow).isAir()) {
            basePositions.add(surroundBelow);
         }
      }

      if (basePositions.isEmpty()) {
         candidates.add(new AutoMine.Candidate(feetPos.offset(dir, 2), AutoMine.CandidateType.EXTEND));
      } else {
         for (BlockPos base : basePositions) {
            if (!base.equals(feetPos) || this.cevHeadRunning()) {
               candidates.add(new AutoMine.Candidate(base.up(), AutoMine.CandidateType.EXTEND));
            } else if (this.canBreak(base.up())) {
               this.dbgOnce(
                  "vertical-head-skip " + base.up().asLong(), "vertical extend skipped: " + base.up().toShortString() + " is the target's head (cev-head off)"
               );
            }

            candidates.add(new AutoMine.Candidate(base, AutoMine.CandidateType.EXTEND));
            if (base.getY() >= feetPos.getY()) {
               candidates.add(new AutoMine.Candidate(base.down(), AutoMine.CandidateType.EXTEND));
            }
         }
      }
   }

   private void addBedrockCandidates(PlayerEntity target, List<BlockPos> inside, Set<AutoMine.Candidate> candidates) {
      if (this.activeBlockMode() == AutoMine.BlockMode.Hitbox) {
         Box feetBox = this.feetBox(target);
         int feetY = MathHelper.floor(target.getY());
         Box bodyBox = new Box(feetBox.minX, feetY + 1.0, feetBox.minZ, feetBox.maxX, feetY + 1.1, feetBox.maxZ);

         for (BlockPos pos : BlockUtils.iterate(bodyBox)) {
            if (this.canBreak(pos)) {
               candidates.add(new AutoMine.Candidate(pos.toImmutable(), AutoMine.CandidateType.PHASE));
            }
         }
      }

      boolean canFallDown = true;
      boolean canBeHitUp = true;

      for (BlockPos posx : inside) {
         if (this.mc.world.getBlockState(posx.down()).isOf(Blocks.BEDROCK)) {
            canFallDown = false;
         }

         if (this.mc.world.getBlockState(posx.up(2)).isOf(Blocks.BEDROCK)) {
            canBeHitUp = false;
         }
      }

      for (BlockPos posx : inside) {
         if (canFallDown) {
            candidates.add(new AutoMine.Candidate(posx.down(), AutoMine.CandidateType.BELOW));
         }

         if (canBeHitUp) {
            candidates.add(new AutoMine.Candidate(posx.up(2), AutoMine.CandidateType.HEAD));
         }

         candidates.add(new AutoMine.Candidate(posx.up(), AutoMine.CandidateType.FACE_PLACE));
         candidates.add(new AutoMine.Candidate(posx, AutoMine.CandidateType.SURROUND));

         for (Direction dir : Type.HORIZONTAL) {
            candidates.add(new AutoMine.Candidate(posx.up().offset(dir), AutoMine.CandidateType.FACE_PLACE));
            candidates.add(new AutoMine.Candidate(posx.offset(dir), AutoMine.CandidateType.SURROUND));
         }
      }
   }

   private void addPhaseCandidates(PlayerEntity target, Set<AutoMine.Candidate> candidates, boolean inBedrock) {
      Box box = target.getBoundingBox().expand(-0.01, -0.01, -0.01);
      int feetY = MathHelper.floor(target.getY());
      boolean headLayer = inBedrock || this.cevHeadRunning();

      for (BlockPos pos : BlockUtils.iterate(box)) {
         if (pos.getY() <= feetY || headLayer) {
            candidates.add(new AutoMine.Candidate(pos.toImmutable(), AutoMine.CandidateType.PHASE));
         } else if (this.canBreak(pos)) {
            this.dbgOnce("phase-head-skip " + pos.asLong(), "phased head block " + pos.toShortString() + " skipped: cev-head off (target kept crawling)");
         }
      }
   }

   private boolean cevHeadRunning() {
      AutoCrystal autoCrystal = AutoCrystal.get();
      return autoCrystal != null && autoCrystal.isRunningHere() && autoCrystal.cevHead.get();
   }

   private void addCrawlHeadCandidate(PlayerEntity target, Set<AutoMine.Candidate> candidates) {
      BlockPos ceiling = this.getCrawlCeiling(target);
      if (ceiling != null) {
         if (!this.cevHeadRunning()) {
            this.dbgOnce("ceiling-skip " + ceiling.asLong(), "ceiling " + ceiling.toShortString() + " skipped: cev-head off (target kept crawling)");
         } else {
            BlockState state = this.mc.world.getBlockState(ceiling);
            SilentMine silentMine = this.silentMine();
            boolean rebreak = silentMine != null && ceiling.equals(silentMine.getRebreakPos());
            if (rebreak || state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN)) {
               candidates.add(new AutoMine.Candidate(ceiling, AutoMine.CandidateType.CRAWL_HEAD));
            }
         }
      }
   }

   private BlockPos getCrawlCeiling(PlayerEntity target) {
      return target != null && target.isCrawling() ? target.getBlockPos().up() : null;
   }

   private List<AutoMine.CityBlock> getScoreRankedBlocks(PlayerEntity target) {
      AutoMine.Candidates candidates = this.candidates(target);
      boolean inBedrock = candidates.inBedrock;
      this.ignorePos = null;
      boolean recordDebug = this.renderDebugScores.get();
      this.debugScores.clear();
      this.debugBestScore = 0.0;
      List<AutoMine.CityBlock> scored = new ArrayList<>(candidates.raw.size());
      AutoMine.CityBlock first = null;

      for (AutoMine.Candidate candidate : candidates.raw) {
         AutoMine.CityBlock block = this.scoreCandidate(target, candidate, inBedrock);
         if (block != null) {
            scored.add(block);
            if (recordDebug) {
               this.recordDebugScore(block);
            }

            if (this.isBetter(block, first)) {
               first = block;
            }
         }
      }

      List<AutoMine.CityBlock> result = new ArrayList<>(2);
      if (first == null) {
         return result;
      } else {
         result.add(first);
         this.ignorePos = first.blockPos;
         AutoMine.CityBlock second = null;

         for (AutoMine.CityBlock block : scored) {
            if (!block.blockPos.equals(first.blockPos)) {
               AutoMine.CityBlock candidatex = block;
               if (!inBedrock && block.type == AutoMine.CandidateType.SURROUND) {
                  candidatex = this.scoreCandidate(target, new AutoMine.Candidate(block.blockPos, block.type), false);
                  if (candidatex == null) {
                     continue;
                  }
               }

               if (this.isBetter(candidatex, second)) {
                  second = candidatex;
               }
            }
         }

         if (second != null) {
            result.add(second);
         }

         return result;
      }
   }

   private boolean isBetter(AutoMine.CityBlock block, AutoMine.CityBlock best) {
      return best != null && !(block.score > best.score)
         ? block.score == best.score
            && this.mc.player.squaredDistanceTo(Vec3d.ofCenter(block.blockPos)) < this.mc.player.squaredDistanceTo(Vec3d.ofCenter(best.blockPos))
         : true;
   }

   private void recordDebugScore(AutoMine.CityBlock scored) {
      if (scored.score > this.debugBestScore) {
         this.debugBestScore = scored.score;
      }

      for (int i = 0; i < this.debugScores.size(); i++) {
         if (this.debugScores.get(i).blockPos.equals(scored.blockPos)) {
            if (scored.score > this.debugScores.get(i).score) {
               this.debugScores.set(i, scored);
            }

            return;
         }
      }

      this.debugScores.add(scored);
   }

   private Map<BlockPos, BreakIndicators.TrackedBlock> snapshotBreakIndicators() {
      AutoMine.AntiSwimMode swim = this.antiSwim.get();
      boolean needed = this.breakIndicatorsSync.get() || swim != AutoMine.AntiSwimMode.None;
      if (!needed) {
         return null;
      } else {
         BreakIndicators indicators = Modules.get().get(BreakIndicators.class);
         return indicators != null && indicators.isActive() ? indicators.getTracked() : null;
      }
   }

   private double getScorePenaltyForSync(BlockPos pos) {
      if (this.breakIndicatorsSync.get() && this.trackedSnapshot != null && !this.trackedSnapshot.isEmpty()) {
         BreakIndicators.TrackedBlock tracked = this.trackedSnapshot.get(pos);
         if (tracked != null && tracked.player != this.mc.player) {
            return !this.breakIndicatorsSyncOnlyFriends.get() || tracked.player != null && Friends.get().isFriend(tracked.player)
               ? this.breakIndicatorSyncPenalty.get()
               : 0.0;
         } else {
            return 0.0;
         }
      } else {
         return 0.0;
      }
   }

   private AutoMine.CityBlock scoreCandidate(PlayerEntity target, AutoMine.Candidate candidate, boolean inBedrock) {
      BlockPos pos = candidate.pos;
      if (!this.inBreakRange(pos)) {
         return null;
      } else {
         BlockState state = this.mc.world.getBlockState(pos);
         boolean goodRebreak = this.isGoodRebreak(pos, target, inBedrock);
         if (!goodRebreak && !this.canBreak(pos)) {
            return null;
         } else if (this.isSelfTrapRisk(pos)) {
            return null;
         } else if (this.isOwnCrystalBase(pos)) {
            return null;
         } else {
            boolean ownSeal = this.isOwnSeal(pos);
            if (ownSeal && !this.isSealablePos(pos)) {
               return null;
            } else {
               boolean crystalReach = goodRebreak || !this.isAroundTargetFeet(pos) || !this.crystalOutOfReach(pos);
               if (!crystalReach) {
                  this.dbgOnce(
                     "reach " + pos.asLong(),
                     "surround "
                        + pos.toShortString()
                        + " out of AutoCrystal's crystal reach: no anti-surround / DamageSync bonus (ranked after the ones it can crystal into)"
                  );
               }

               double score = this.scoreBlockMode(target, pos, candidate.type, crystalReach);
               if (score == -1000.0) {
                  return null;
               } else {
                  double typeScore = inBedrock
                     ? this.scoreBedrockCandidate(target, pos, candidate.type)
                     : this.scoreNormalCandidate(target, pos, candidate.type, state, crystalReach);
                  if (typeScore == -1000.0) {
                     return null;
                  } else {
                     score += typeScore;
                     if (!goodRebreak && !ownSeal) {
                        score -= this.getScorePenaltyForSync(pos);
                     } else {
                        score += 40.0;
                     }

                     double distance = Math.max(target.getEntityPos().distanceTo(Vec3d.ofCenter(pos)), 0.1);
                     score += 10.0 / distance;
                     AutoMine.CityBlock block = new AutoMine.CityBlock();
                     block.blockPos = pos.toImmutable();
                     block.score = score;
                     block.isFeetBlock = this.isBlockInFeet(pos, target);
                     block.type = candidate.type;
                     return block;
                  }
               }
            }
         }
      }
   }

   private double scoreBlockMode(PlayerEntity target, BlockPos pos, AutoMine.CandidateType type, boolean crystalReach) {
      AutoMine.BlockMode mode = this.activeBlockMode();
      if (mode == AutoMine.BlockMode.Normal) {
         return 0.0;
      } else {
         double score = 0.0;
         if (mode == AutoMine.BlockMode.Air) {
            int airCount = this.countAdjacentAir(pos);
            if (airCount <= 0) {
               return -1000.0;
            }

            score += 30.0 * airCount;
         }

         if (mode == AutoMine.BlockMode.DamageSync && crystalReach) {
            double ds = this.simulateBestCrystalDamage(target, pos);
            score += 8.0 * ds;
            if (this.renderDebugScores.get() && this.silentMineDebug()) {
               String sig = String.format(
                  Locale.US,
                  "%.1f %s %d %d %s",
                  ds,
                  this.dsLastSpot != null ? this.dsLastSpot.toShortString() : "-",
                  this.dsSkippedInBox,
                  this.dsSkippedOnMine,
                  this.ignorePos != null ? this.ignorePos.toShortString() : "-"
               );
               this.dbgChanged(
                  "ds " + pos.asLong() + " " + type + (this.ignorePos != null ? " pair" : ""),
                  sig,
                  String.format(
                     Locale.US,
                     "DS %s type=%s ds=%.2f spot=%s skipped(in-box=%d, on-mine=%d) pairIgnore=%s",
                     pos.toShortString(),
                     type,
                     ds,
                     this.dsLastSpot != null ? this.dsLastSpot.toShortString() : "-",
                     this.dsSkippedInBox,
                     this.dsSkippedOnMine,
                     this.ignorePos != null ? this.ignorePos.toShortString() : "-"
                  )
               );
            }
         }

         if (this.hasCrystalBaseBelow(pos)) {
            score += 15.0;
         }

         if (type == AutoMine.CandidateType.PHASE) {
            score += 150.0;
         }

         return score;
      }
   }

   private double scoreNormalCandidate(PlayerEntity target, BlockPos pos, AutoMine.CandidateType type, BlockState state, boolean crystalReach) {
      if (type == AutoMine.CandidateType.FEET && pos.equals(target.getBlockPos())) {
         double score;
         if (this.mc.world.getBlockState(pos.up()).isOf(Blocks.OBSIDIAN)) {
            score = 100.0;
         } else {
            if (state.isOf(Blocks.COBWEB)) {
               return -1000.0;
            }

            score = 50.0;
         }

         if (this.activeMineUnder() && this.hasAirPocketBelow(pos, 2)) {
            score += 120.0;
         }

         return score;
      } else {
         return switch (type) {
            case SURROUND -> crystalReach && this.isAntiSurroundMiningPos(pos, target) ? 28.0 : 3.0;
            case EXTEND -> this.extendBreakMode.get() == AutoMine.ExtendBreakMode.Long ? 30.0 : 20.0;
            default -> 0.0;
            case BELOW_SURROUND -> this.hasCrystalBaseBelow(pos) ? 45.0 : 35.0;
            case CRAWL_HEAD -> 60.0;
         };
      }
   }

   private double scoreBedrockCandidate(PlayerEntity target, BlockPos pos, AutoMine.CandidateType type) {
      if (type == AutoMine.CandidateType.CRAWL_HEAD) {
         return 60.0;
      } else {
         double score = 0.0;
         BlockPos feet = target.getBlockPos();
         if (pos.getY() == feet.getY() + 2 || pos.getY() == feet.getY() - 1) {
            score += 10.0;
         }

         if (this.isSingleFeetBlock(target) && !this.mc.world.getBlockState(feet.up()).isOf(Blocks.BEDROCK)) {
            if (pos.equals(feet.up())) {
               score += 20.0;
            } else {
               for (Direction dir : Type.HORIZONTAL) {
                  if (feet.up().offset(dir).equals(pos)) {
                     score += 7.5;
                     break;
                  }
               }
            }
         }

         return score;
      }
   }

   private boolean isGoodRebreak(BlockPos pos, PlayerEntity target, boolean inBedrock) {
      SilentMine silentMine = this.silentMine();
      if (silentMine == null || !silentMine.isRunningHere()) {
         return false;
      } else if (!pos.equals(silentMine.getRebreakPos())) {
         return false;
      } else if (this.isCrawl() && pos.equals(this.getCrawlCeiling(target))) {
         return true;
      } else {
         BlockPos feet = target.getBlockPos();
         if (inBedrock) {
            if (!this.isSingleFeetBlock(target)) {
               return false;
            } else if (pos.equals(feet.up(2))) {
               return true;
            } else if (!this.mc.world.getBlockState(feet.up()).isAir()) {
               return false;
            } else {
               for (Direction dir : Type.HORIZONTAL) {
                  if (feet.up().offset(dir).equals(pos)) {
                     return true;
                  }
               }

               return false;
            }
         } else if (!pos.equals(feet) && !this.isBlockInFeet(pos, target)) {
            for (Direction dirx : Type.HORIZONTAL) {
               BlockPos surround = feet.offset(dirx);
               if (surround.equals(pos) && this.hasCrystalBaseBelow(surround)) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   private boolean glassSealOwns(BlockPos pos) {
      AutoCrystal autoCrystal = AutoCrystal.get();
      return autoCrystal != null && autoCrystal.glassSealOwns(pos);
   }

   private boolean crystalOutOfReach(BlockPos pos) {
      AutoCrystal autoCrystal = AutoCrystal.get();
      return autoCrystal != null
         && autoCrystal.isRunningHere()
         && autoCrystal.antiSurround.get()
         && autoCrystal.placeCrystals.get()
         && !autoCrystal.canCrystalAt(pos);
   }

   private boolean isOwnSeal(BlockPos pos) {
      SilentMine silentMine = this.silentMine();
      return silentMine != null && silentMine.isRunningHere() && silentMine.isOwnSeal(pos);
   }

   private BlockPos getInnerBlockForExtend(BlockPos extendPos, BlockPos targetFeet) {
      if (extendPos.getY() != targetFeet.getY()) {
         return null;
      } else {
         int dx = extendPos.getX() - targetFeet.getX();
         int dz = extendPos.getZ() - targetFeet.getZ();
         int adx = Math.abs(dx);
         int adz = Math.abs(dz);
         if (adx == 2 && adz == 0) {
            return targetFeet.add(Integer.signum(dx), 0, 0);
         } else if (adz == 2 && adx == 0) {
            return targetFeet.add(0, 0, Integer.signum(dz));
         } else {
            if (adx == 1 && adz == 1) {
               BlockPos a = targetFeet.add(Integer.signum(dx), 0, 0);
               BlockPos b = targetFeet.add(0, 0, Integer.signum(dz));
               boolean aValid = this.isBreakCandidateValid(a);
               boolean bValid = this.isBreakCandidateValid(b);
               if (aValid && bValid) {
                  return this.mc.player.squaredDistanceTo(Vec3d.ofCenter(a)) <= this.mc.player.squaredDistanceTo(Vec3d.ofCenter(b)) ? a : b;
               }

               if (aValid) {
                  return a;
               }

               if (bValid) {
                  return b;
               }
            }

            return null;
         }
      }
   }

   private PlayerEntity findTarget() {
      if (this.isValidTarget(this.targetPlayer) && this.hasMinableCandidate(this.targetPlayer)) {
         return this.targetPlayer;
      } else {
         ReviveClient.TARGET
            .getList(this.targetScratch, entityx -> entityx instanceof PlayerEntity playerx && this.isValidTarget(playerx), this.targetPriority.get(), 5);
         PlayerEntity first = null;

         for (Entity entity : this.targetScratch) {
            if (entity instanceof PlayerEntity player) {
               if (first == null) {
                  first = player;
               }

               if (this.hasMinableCandidate(player)) {
                  this.targetScratch.clear();
                  return player;
               }
            }
         }

         this.targetScratch.clear();
         return first;
      }
   }

   private boolean hasMinableCandidate(PlayerEntity target) {
      SilentMine silentMine = this.silentMine();
      BlockPos rebreakPos = silentMine != null && silentMine.isRunningHere() ? silentMine.getRebreakPos() : null;

      for (AutoMine.Candidate candidate : this.candidates(target).raw) {
         BlockPos pos = candidate.pos;
         if (this.inBreakRange(pos) && !this.isSelfTrapRisk(pos) && !this.isOwnCrystalBase(pos) && (pos.equals(rebreakPos) || this.canBreak(pos))) {
            return true;
         }
      }

      return false;
   }

   private boolean isValidTarget(PlayerEntity player) {
      if (this.mc.player == null || this.mc.world == null || player == null) {
         return false;
      } else if (player == this.mc.player) {
         return false;
      } else if (player.isRemoved() || player.isDead() || player.getHealth() <= 0.0F) {
         return false;
      } else if (!Friends.get().shouldAttack(player)) {
         return false;
      } else {
         boolean fake = player instanceof FakePlayerEntity;
         if (fake && this.ignoreFakePlayers.get()) {
            return false;
         } else {
            if (this.survivalOnly.get() && !fake) {
               GameMode gameMode = EntityUtils.getGameMode(player);
               if (gameMode != null && gameMode != GameMode.SURVIVAL) {
                  return false;
               }
            }

            if (this.ignoreNakeds.get() && this.isNaked(player)) {
               return false;
            } else {
               double maxRange = this.range.get();
               return this.mc.player.squaredDistanceTo(player) <= maxRange * maxRange;
            }
         }
      }
   }

   private boolean isNaked(PlayerEntity player) {
      return player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
         && player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
         && player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
         && player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
   }

   private Box feetBox(PlayerEntity player) {
      Box box = player.getBoundingBox().expand(-0.01, -0.1, -0.01);
      double feetY = player.getY();
      return new Box(box.minX, feetY, box.minZ, box.maxX, feetY + 0.1, box.maxZ);
   }

   private List<BlockPos> getInsideBlocks(Box box, int y) {
      List<BlockPos> inside = new ArrayList<>();

      for (int x = MathHelper.floor(box.minX); x < MathHelper.ceil(box.maxX); x++) {
         for (int z = MathHelper.floor(box.minZ); z < MathHelper.ceil(box.maxZ); z++) {
            inside.add(new BlockPos(x, y, z));
         }
      }

      return inside;
   }

   private boolean isSingleFeetBlock(PlayerEntity target) {
      return this.candidates(target).feet.size() == 1;
   }

   private boolean isBlockInFeet(BlockPos pos, PlayerEntity target) {
      return pos != null && target != null && this.candidates(target).feet.contains(pos);
   }

   private boolean isAntiSurroundMiningPos(BlockPos pos, PlayerEntity target) {
      BlockPos feetPos = target.getBlockPos();

      for (Direction dir : Type.HORIZONTAL) {
         if (feetPos.offset(dir).equals(pos)) {
            if (this.isOpenCrystalSpot(feetPos.offset(dir, 2))) {
               return true;
            }

            if (this.isOpenCrystalSpot(feetPos.offset(dir).offset(dir.rotateYClockwise()))) {
               return true;
            }

            if (this.isOpenCrystalSpot(feetPos.offset(dir).offset(dir.rotateYCounterclockwise()))) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isOpenCrystalSpot(BlockPos pos) {
      boolean air = pos.equals(this.ignorePos) || this.mc.world.getBlockState(pos).isAir();
      return air && this.hasCrystalBaseBelow(pos);
   }

   private boolean hasCrystalBaseBelow(BlockPos pos) {
      BlockState below = this.mc.world.getBlockState(pos.down());
      return below.isOf(Blocks.OBSIDIAN) || below.isOf(Blocks.BEDROCK);
   }

   private int countAdjacentAir(BlockPos pos) {
      int count = 0;

      for (Direction dir : Direction.values()) {
         if (this.mc.world.getBlockState(pos.offset(dir)).isAir()) {
            count++;
         }
      }

      return count;
   }

   private boolean hasAirPocketBelow(BlockPos pos, int depth) {
      for (int i = 1; i <= depth; i++) {
         if (!this.mc.world.getBlockState(pos.down(i)).isAir()) {
            return false;
         }
      }

      return true;
   }

   private boolean isSelfTrapRisk(BlockPos pos) {
      if (!pos.equals(this.mc.player.getBlockPos())) {
         return false;
      } else {
         BlockState selfHead = this.mc.world.getBlockState(this.mc.player.getBlockPos().up());
         return selfHead.isOf(Blocks.OBSIDIAN) || selfHead.isOf(Blocks.BEDROCK);
      }
   }

   private boolean inBreakRange(BlockPos pos) {
      SilentMine silentMine = this.silentMine();
      return pos != null && silentMine != null && silentMine.inBreakRange(pos);
   }

   private boolean canBreak(BlockPos pos) {
      return pos != null && BlockUtils.canBreak(pos, this.mc.world.getBlockState(pos));
   }

   public boolean isTargetedPos(BlockPos pos) {
      return pos != null && (this.target1 != null && pos.equals(this.target1.blockPos) || this.target2 != null && pos.equals(this.target2.blockPos));
   }

   @Override
   public String getInfoString() {
      return this.targetPlayer != null ? this.targetPlayer.getName().getString() : null;
   }

   private double simulateBestCrystalDamage(PlayerEntity target, BlockPos mineBlock) {
      Set<BlockPos> ignored = new HashSet<>();
      ignored.add(mineBlock.toImmutable());
      if (this.ignorePos != null) {
         ignored.add(this.ignorePos.toImmutable());
      }

      Box targetBox = target.getBoundingBox();
      Box selfBox = this.mc.player.getBoundingBox();
      boolean cev = this.cevHeadRunning();
      this.dsLastSpot = null;
      this.dsSkippedInBox = 0;
      this.dsSkippedOnMine = 0;
      SilentMine silentMine = this.silentMine();
      if (silentMine != null && silentMine.isRunningHere()) {
         BlockPos delayed = silentMine.getDelayedDestroyBlockPos();
         if (delayed != null) {
            ignored.add(delayed.toImmutable());
         }

         BlockPos doubled = silentMine.getDoubleMinePos();
         if (doubled != null) {
            ignored.add(doubled.toImmutable());
         }

         BlockPos rebreak = silentMine.getRebreakPos();
         if (rebreak != null) {
            ignored.add(rebreak.toImmutable());
         }
      }

      AutoCrystal autoCrystal = AutoCrystal.get();
      double maxSelfDamage = autoCrystal != null ? autoCrystal.maxPlace.get() : 12.0;
      boolean noSelfPop = autoCrystal == null || autoCrystal.noSelfPop.get();
      double selfHealth = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
      double bestDamage = 0.0;

      for (int x = -3; x <= 3; x++) {
         for (int y = -1; y <= 2; y++) {
            for (int z = -3; z <= 3; z++) {
               BlockPos placePos = mineBlock.add(x, y, z);
               BlockState baseState = this.mc.world.getBlockState(placePos.down());
               if (baseState.isOf(Blocks.OBSIDIAN) || baseState.isOf(Blocks.BEDROCK)) {
                  BlockState placeState = this.mc.world.getBlockState(placePos);
                  if (placeState.isAir() || placeState.isOf(Blocks.FIRE) || placeState.isOf(Blocks.SOUL_FIRE) || placePos.equals(mineBlock)) {
                     if (!cev && placePos.down().equals(mineBlock)) {
                        this.dsSkippedOnMine++;
                     } else {
                        Box crystalBox = new Box(
                           placePos.getX(), placePos.getY(), placePos.getZ(), placePos.getX() + 1.0, placePos.getY() + 2.0, placePos.getZ() + 1.0
                        );
                        if (!crystalBox.intersects(targetBox) && !crystalBox.intersects(selfBox)) {
                           Vec3d crystalPos = new Vec3d(placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5);
                           float damage = DamageUtil.crystalDamage(
                              target, target.getEntityPos(), target.getBoundingBox(), crystalPos, DamageUtil.BLOCK_CHECK, false, ignored
                           );
                           if (!(damage <= 1.0F) && !(damage <= bestDamage)) {
                              float selfDamage = DamageUtil.crystalDamage(
                                 this.mc.player,
                                 this.mc.player.getEntityPos(),
                                 this.mc.player.getBoundingBox(),
                                 crystalPos,
                                 DamageUtil.BLOCK_CHECK,
                                 false,
                                 ignored
                              );
                              if (!(selfDamage > maxSelfDamage) && !(selfDamage >= selfHealth) && (!noSelfPop || !(selfDamage + 1.5 >= selfHealth))) {
                                 bestDamage = damage;
                                 this.dsLastSpot = placePos;
                              }
                           }
                        } else {
                           this.dsSkippedInBox++;
                        }
                     }
                  }
               }
            }
         }
      }

      return bestDamage;
   }

   public static enum AntiSurroundMode {
      None,
      Inner,
      Outer,
      Auto;
   }

   public static enum AntiSwimMode {
      None,
      Always,
      OnMine,
      OnMineAndSwim;
   }

   public static enum AntiSwimPriority {
      Rebreak,
      Head;
   }

   public static enum BlockMode {
      Normal,
      Hitbox,
      DamageSync,
      Air;
   }

   private record Candidate(BlockPos pos, AutoMine.CandidateType type) {
   }

   private static enum CandidateType {
      FEET,
      SURROUND,
      EXTEND,
      FACE_PLACE,
      HEAD,
      BELOW,
      BELOW_SURROUND,
      PHASE,
      CRAWL_HEAD;
   }

   private record Candidates(Set<AutoMine.Candidate> raw, boolean inBedrock, List<BlockPos> feet) {
   }

   private static class CityBlock {
      private BlockPos blockPos;
      private double score;
      private boolean isFeetBlock;
      private AutoMine.CandidateType type;
   }

   public static enum ExtendBreakMode {
      None,
      Long,
      Corner,
      Vertical;
   }

   public static enum GlassOpenOrder {
      SameTick,
      NextTick;
   }

   public static enum GlassSource {
      Hotbar,
      Inventory;
   }

   public static enum Meta {
      Old,
      Crawl;
   }
}
