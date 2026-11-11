package revivedevelopment.reviveclient.systems.modules.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.entity.player.StartBreakingBlockEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.revive.SilentMineFinishedEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.ClientPlayerInteractionManagerAccessor;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.pingbypass.PbFx;
import revivedevelopment.reviveclient.pingbypass.PbRenderable;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.managers.RotationManager;
import revivedevelopment.reviveclient.systems.managers.SwapManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.AutoCrystal;
import revivedevelopment.reviveclient.systems.modules.combat.AutoMine;
import revivedevelopment.reviveclient.systems.modules.movement.Sprint;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;
import revivedevelopment.reviveclient.utils.world.BlockUtils;
import revivedevelopment.reviveclient.utils.world.TickRate;

public class SilentMine extends Module implements PbRenderable {
   private static final long START_NANOS = System.nanoTime();
   private static final double STOP_PROGRESS = 1.1;
   private static final double RESYNC_RIPE_PROGRESS = 0.8;
   private static final int DIG_COOLDOWN_TICKS = 5;
   private static final int GRIM_BREAK_DELAY_TICKS = 6;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDoubleMine = this.settings.createGroup("Double Mine");
   private final SettingGroup sgRebreak = this.settings.createGroup("Rebreak");
   private final SettingGroup sgQueue = this.settings.createGroup("Queue");
   private final SettingGroup sgLegacy = this.settings.createGroup("Legacy");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("How far blocks may be to start mining them. Capped at the server's break reach (block-interaction range + 1.0, normally 5.5).")
            .defaultValue(5.14)
            .min(0.0)
            .sliderMax(5.5)
            .build()
      );
   private final Setting<Double> rangeSlack = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range-slack")
            .description(
               "How far a block already being mined may drift past range before its session is cancelled (stops the start/abort loop at the edge of range)."
            )
            .defaultValue(0.35)
            .min(0.0)
            .sliderMax(1.5)
            .build()
      );
   public final Setting<Boolean> pauseWhileEating = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-while-eating")
            .description(
               "Pause mining while using an item (eating, drinking, etc.). Whatever this says, AutoMine's surround rebreak waits while you use an item and AutoCrystal's pause-eat-place stops its places: no crystal or glass could follow it."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> rebreakWhileEating = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rebreak-while-eating")
            .description(
               "Still fire an armed rebreak while you use an item in the OFFHAND (its tool swap never touches the offhand, so the use goes on). A main-hand use still pauses it: the slot change would end the use. Grim's experimental MultiActionsB flags breaking while using."
            )
            .defaultValue(true)
            .visible(this.pauseWhileEating::get)
            .build()
      );
   public final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description("Aim at the block server-side on the ticks that send break packets (silent; Grim raycasts break packets against your look).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> preSwitchSinglebreak = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pre-switch-single-break")
            .description(
               "Switch to the tool a few ticks before a block the server finishes on its own timer (packet-mode double mine's parked block) completes, so the tool is surely in hand on that tick. Off: switch on the estimated completion tick. Legacy mode always holds the tool."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> singleBreakFailTicks = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("single-break-fail-ticks")
            .description("Ticks after the first STOP to wait for the server's verdict (air = broken, re-sync = refused) before restarting the session.")
            .defaultValue(20)
            .min(5)
            .sliderMax(50)
            .build()
      );
   public final Setting<Boolean> rebreakSetBlockBroken = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("set-rebreak-block-broken")
            .description("Set the rebreak block to air client-side on every rebreak attempt.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Double> speedPercentage = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed-percentage")
            .description(
               "Percentage of vanilla break time to send the STOP at (100% = full time; 70% = vanilla's 0.7 threshold). Also scales the break estimate shown. Below 100 is only accepted on 2b2t after the Grim bypass burst, which already uses 70%."
            )
            .defaultValue(100.0)
            .min(0.0)
            .sliderMax(100.0)
            .build()
      );
   public final Setting<Boolean> tpsScaling = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("scale-to-server-tps")
            .description("Pace the break estimate on the server's measured TPS (the server counts break time in its own ticks).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> instantFinish = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("instant-finish")
            .description("Break blocks that mine in a single tick with one aimed START (verified against the server's response).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> packetMode = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("packet-mode")
            .description(
               "Lambda PacketMine's tool handling: the tool is swapped in (hotbar) only on the tick the STOP goes out, so hotbar changes mid-mine never reset the break. Off = the legacy whole-session tool hold (see the Legacy group)."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Log SilentMine state transitions (and AutoMine's feed decisions) to chat.")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> miningSwing = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("mining-swing")
            .description("Swing each tick while mining, like a real player (Grim's MiningSwing).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> grimBypass = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("grim-bypass")
            .description(
               "Lambda's OldGrim bypass: a few ticks into a block, burst START_DESTROY_BLOCK at the block shifted Y+955. Lifts Grim's break delay so the STOP lands at vanilla's 0.7 and back-to-back breaks aren't throttled."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Integer> grimBypassBurst = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("grim-bypass-count")
            .description("How many START packets to send in the Y+955 burst (Lambda uses 22).")
            .defaultValue(22)
            .min(1)
            .sliderRange(1, 32)
            .visible(this.grimBypass::get)
            .build()
      );
   private final Setting<Boolean> doubleMine = this.sgDoubleMine
      .add(
         new BoolSetting.Builder()
            .name("double-mine")
            .description("Mine a second block alongside the primary (packet mode: park the first in the server's delayed slot and mine the second at once).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Keybind> doubleMineAbortKey = this.sgDoubleMine
      .add(
         new KeybindSetting.Builder()
            .name("abort")
            .description("Press to cancel the queued double-mine block.")
            .defaultValue(Keybind.none())
            .action(this::abortDoubleMine)
            .visible(this.doubleMine::get)
            .build()
      );
   private final Setting<Boolean> instantVisual = this.sgDoubleMine
      .add(
         new BoolSetting.Builder()
            .name("instant-visual")
            .description(
               "Remove the block on your screen the instant its STOP is sent (vanilla sequenced prediction). The block stays tracked until the server confirms the break; a refusal restarts it."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> rebreakEnabled = this.sgRebreak
      .add(new BoolSetting.Builder().name("rebreak").description("Automatically re-break the last block you mined.").defaultValue(true).build());
   private final Setting<SilentMine.RebreakMode> rebreakMode = this.sgRebreak
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                     .description("How rebreak attempts are driven."))
                  .defaultValue(SilentMine.RebreakMode.Auto))
               .visible(this.rebreakEnabled::get))
            .build()
      );
   private final Setting<Integer> rebreakSpeed = this.sgRebreak
      .add(
         new IntSetting.Builder()
            .name("speed")
            .description("Maximum rebreak attempts per second.")
            .defaultValue(10)
            .min(1)
            .max(20)
            .sliderRange(1, 20)
            .visible(() -> this.rebreakEnabled.get() && this.rebreakMode.get() != SilentMine.RebreakMode.Aggressive)
            .build()
      );
   private final Setting<Keybind> rebreakAbortKey = this.sgRebreak
      .add(
         new KeybindSetting.Builder()
            .name("abort")
            .description("Press to cancel the current rebreak block.")
            .defaultValue(Keybind.none())
            .action(this::abortRebreak)
            .visible(this.rebreakEnabled::get)
            .build()
      );
   private final Setting<Boolean> rebreakNoDrop = this.sgRebreak
      .add(
         new BoolSetting.Builder()
            .name("no-drop")
            .description(
               "Rebreak obsidian-tier blocks with a golden pickaxe once the dig session is old enough, so no item drops into the hole (an item blocks the crystal). Needs a golden pickaxe (Efficiency V recommended); falls back to the fastest tool."
            )
            .defaultValue(true)
            .visible(this.rebreakEnabled::get)
            .build()
      );
   private final Setting<Boolean> queueing = this.sgQueue
      .add(new BoolSetting.Builder().name("queue").description("Add blocks to a queue to be mined sequentially as slots free up.").defaultValue(false).build());
   private final Setting<SilentMine.BlockWhitelistMode> queueingMode = this.sgQueue
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                     .description("Whitelist/Blacklist the block list, or queue all blocks."))
                  .defaultValue(SilentMine.BlockWhitelistMode.Whitelist))
               .visible(this.queueing::get))
            .build()
      );
   private final Setting<List<Block>> queueingBlocks = this.sgQueue
      .add(
         new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to queue when mined.")
            .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)
            .visible(() -> this.queueing.get() && this.queueingMode.get() != SilentMine.BlockWhitelistMode.All)
            .build()
      );
   private final Setting<Keybind> queueClearKey = this.sgQueue
      .add(
         new KeybindSetting.Builder()
            .name("clear-queue")
            .description("Press to clear the current mining queue.")
            .defaultValue(Keybind.none())
            .action(this::clearQueue)
            .visible(this.queueing::get)
            .build()
      );
   private final Setting<Boolean> simultaneous = this.sgLegacy
      .add(
         new BoolSetting.Builder()
            .name("simultaneous")
            .description(
               "Legacy double mine / queue cascade via the server's delayed-destroy slot: park the primary with an early STOP at park-at (or right after the Y+955 burst) and mine the next block in the freed slot."
            )
            .defaultValue(false)
            .visible(() -> !this.packetMode.get() && (this.doubleMine.get() || this.queueing.get()))
            .build()
      );
   private final Setting<Double> parkAtProgress = this.sgLegacy
      .add(
         new DoubleSetting.Builder()
            .name("park-at")
            .description("Server progress at which the legacy primary is parked (must stay under 0.7). Ignored once the Y+955 burst is out.")
            .defaultValue(0.45)
            .min(0.1)
            .max(0.68)
            .sliderRange(0.1, 0.68)
            .visible(() -> !this.packetMode.get() && this.simultaneous.get())
            .build()
      );
   public final Setting<Boolean> hideSwitch = this.sgLegacy
      .add(
         new BoolSetting.Builder()
            .name("hide-switch")
            .description("Visually keep your original item in hand during the legacy tool hold (render-only).")
            .defaultValue(true)
            .visible(() -> !this.packetMode.get())
            .build()
      );
   private final Setting<Boolean> render = this.sgRender.add(new BoolSetting.Builder().name("render").defaultValue(true).build());
   private final Setting<SilentMine.RenderAnimation> renderAnimation = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("animation"))
                     .description("How the box animates as the block breaks."))
                  .defaultValue(SilentMine.RenderAnimation.Grow))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> startColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("start-color")
            .description("Color at the beginning of mining.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> endColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("end-color")
            .description("Color when the block is fully mined.")
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .visible(this.render::get)
            .build()
      );
   private final Setting<Integer> renderFillAlpha = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("fill-opacity")
            .description("Opacity of the sides-only box fill.")
            .defaultValue(40)
            .min(0)
            .max(255)
            .sliderMax(255)
            .visible(this.render::get)
            .build()
      );
   private final Setting<Boolean> rebreakRender = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("rebreak-render")
            .description("Render the rebreak (air-ready) target.")
            .defaultValue(true)
            .visible(this.render::get)
            .build()
      );
   private final Setting<Integer> rebreakFillAlpha = this.sgRender
      .add(
         new IntSetting.Builder()
            .name("rebreak-fill-opacity")
            .description("Opacity of the rebreak (sides-only) box fill.")
            .defaultValue(40)
            .min(0)
            .max(255)
            .sliderMax(255)
            .visible(() -> this.render.get() && this.rebreakRender.get())
            .build()
      );
   private final LinkedHashMap<BlockPos, SilentMine.QueuedBlock> queuedBlocks = new LinkedHashMap<>();
   private final LinkedHashMap<BlockPos, SilentMine.QueuedBlock> activeQueuedBlocks = new LinkedHashMap<>();
   private long rebreakWindowStartMs = -1L;
   private int rebreakPacketsInWindow = 0;
   private long lastInstantRebreakMs = -1L;
   private static final long INSTANT_REBREAK_INTERVAL_MS = 50L;
   private static final long INSTANT_REBREAK_EATING_EXTRA_MS = 75L;
   private static final int REBREAK_ACTION_RESERVE = 6;
   private SilentMine.SilentMineBlock rebreakBlock = null;
   private SilentMine.SilentMineBlock delayedDestroyBlock = null;
   private BlockPos lastDelayedDestroyBlockPos = null;
   private double currentGameTickCalculated = 0.0;
   private boolean needDelayedDestroySwapBack = false;
   private static final int REBREAK_MAX_UNANSWERED_STOPS = 5;
   private static final double REBREAK_KEEP_RANGE_EXTRA = 2.0;
   private static final int REBREAK_WAIT_LOG_TICKS = 20;
   private String lastRebreakWaitKey = null;
   private double lastRebreakWaitLogTick = -1.0E9;
   private int primaryRestarts = 0;
   private double lastPrimaryBreakTick = -1.0E9;
   private boolean lastBreakBypassed = false;
   private final ConcurrentHashMap<BlockPos, Double> pendingInstantFinish = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<BlockPos, Double> predictedBroken = new ConcurrentHashMap<>();
   private final HashMap<BlockPos, Double> deniedInstantFinish = new HashMap<>();
   private static final int RANGE_CANCEL_REPICK_TICKS = 10;
   private final HashMap<BlockPos, Double> rangeCancelledAt = new HashMap<>();
   private static final int INSTANT_FINISH_VERDICT_TICKS = 60;
   private static final int INSTANT_FINISH_DENY_TICKS = 400;
   private static final int MANUAL_HOTBAR_GRACE_TICKS = 40;
   private int manualHotbarGraceTicks = 0;
   private static final int QUICK_HOLD_BREAK_TICKS = 5;
   private static final int MAX_HOTBAR_GRACE_TICKS = 120;
   private double holdTakenTick = -1.0E9;
   private int quickHoldBreaks = 0;
   private static final int TOOL_MOVE_CONFIRM_TICKS = 3;
   private int toolMoveCandidateSlot = -1;
   private int toolMoveCandidateTicks = 0;
   private static final int INTERACT_RESEND_TICKS = 20;
   private final ConcurrentHashMap<BlockPos, Double> recentInteractResends = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<BlockPos, SilentMine.PlaceSound> recentPlaceSounds = new ConcurrentHashMap<>();
   private static final double NO_DROP_MIN_PROGRESS = 0.9;
   private static final int NO_DROP_VERDICT_TICKS = 20;
   private static final int NO_DROP_DENY_TICKS = 200;
   private volatile BlockPos lastStartSentPos = null;
   private volatile double lastStartSentTick = 0.0;
   private final ConcurrentHashMap<BlockPos, Double> noDropAttempts = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<BlockPos, Double> noDropDenied = new ConcurrentHashMap<>();
   private final HashMap<BlockPos, SilentMine.StopDrop> stopDrops = new HashMap<>();
   private static final int STOP_DROP_KEEP_TICKS = 100;
   private static final int STOP_DROP_MAX_TICKS = 1200;
   private static final int PARKED_DROP_FRESH_TICKS = 5;
   private final ConcurrentHashMap<BlockPos, SilentMine.Seal> ownSeals = new ConcurrentHashMap<>();
   public static final int SEAL_REFUSED_PAUSE_TICKS = 20;
   private final HashMap<BlockPos, Double> refusedSeals = new HashMap<>();
   private final ConcurrentHashMap<BlockPos, SilentMine.Seal> lateSeals = new ConcurrentHashMap<>();
   private BlockPos claimedStopPos = null;
   private final HashMap<String, Double> dbgLimitTicks = new HashMap<>();
   private static final int RESTART_DELAY_TICKS = 8;
   private static final int RESYNC_STOP_RETRIES = 2;
   private boolean primaryRestartPending = false;
   private double primaryRestartAtTick = 0.0;
   private boolean rebreakIsDoubleMine = false;
   private SilentMine.SilentMineBlock parkedBlock = null;
   private static final int MAX_PARK_FAILURES = 3;
   private static final int PARK_RETRY_COOLDOWN_TICKS = 200;
   private static final double CLICK_PRIORITY = 100.0;
   private int parkFailures = 0;
   private double parksDisabledUntilTick = -1.0;
   private static final int PARK_HOLD_LEAD_TICKS = 3;
   private static final int PARK_HOLD_MAX_TICKS = 20;
   private boolean parkHoldActive = false;
   private double parkHoldTakenTick = 0.0;
   private ItemStack capturedOriginal = ItemStack.EMPTY;
   private int capturedSlot = -1;
   private int totemPopTicks = 0;
   private boolean isBeingTotemPopped = false;
   private volatile long[] remoteOwned = new long[0];
   private Direction lastAimFace = null;
   private int deferredStopTicks = 0;
   private long lastStopDeferTick = Long.MIN_VALUE;
   private BlockPos deferredStopPos = null;
   private long tickCount = 0L;
   private boolean pendingRebreakAttempt;
   private float pendingRebreakYaw;
   private float pendingRebreakPitch;
   private Direction pendingRebreakFace;
   private static final double[] CORNER_FRACTIONS = new double[]{0.15, 0.85};
   private static final int FX_PRIMARY = 0;
   private static final int FX_PARKED = 1;
   private static final int FX_RESERVED = 2;
   private static final int FX_REBREAK = 3;
   private static final int FX_QUEUED = 4;
   private final List<double[]> remoteBlocks = new ArrayList<>();
   private double remoteBlocksTick;
   private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

   private static double gameTick() {
      return (System.nanoTime() - START_NANOS) / 5.0E7;
   }

   private double serverTicksPerWallTick() {
      if (!this.tpsScaling.get()) {
         return 1.0;
      } else {
         float tps = TickRate.INSTANCE.getTickRate();
         if (!(tps > 0.0F) || tps > 20.0F) {
            tps = 20.0F;
         }

         return tps / 20.0;
      }
   }

   private int restartDelayTicks() {
      return 8 + Math.min(this.primaryRestarts * 6, 40);
   }

   public SilentMine() {
      super(Categories.Player, "silent-mine", "Mine blocks without holding a pickaxe.");
      this.currentGameTickCalculated = gameTick();
   }

   public static SilentMine get() {
      return Modules.get().get(SilentMine.class);
   }

   public boolean isPacketMode() {
      return this.packetMode.get();
   }

   private void dbg(String msg) {
      if (this.debug.get()) {
         ChatUtils.infoPrefix("SilentMine", "%s", msg);
      }
   }

   private static String fmt(BlockPos pos) {
      return pos == null ? "null" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   private double speedMultiplier() {
      return Math.max(this.speedPercentage.get(), 1.0) / 100.0;
   }

   private static FindItemResult preferredTool(BlockState state) {
      FindItemResult hotbar = InvUtils.findFastestToolHotbar(state);
      return hotbar.found() ? hotbar : InvUtils.findFastestTool(state);
   }

   private boolean toolMoveConfirmed(int slot) {
      boolean holdUp = this.needDelayedDestroySwapBack && ReviveClient.SWAP.isYieldableHoldActive();
      if (!holdUp) {
         this.toolMoveCandidateSlot = -1;
         this.toolMoveCandidateTicks = 0;
         return true;
      } else {
         if (this.toolMoveCandidateSlot != slot) {
            this.toolMoveCandidateSlot = slot;
            this.toolMoveCandidateTicks = 1;
            this.dbg(
               "tool reads in slot " + slot + " while held in slot " + this.mc.player.getInventory().selectedSlot + " — waiting 3 ticks before following it"
            );
         } else {
            this.toolMoveCandidateTicks++;
         }

         return this.toolMoveCandidateTicks >= 3;
      }
   }

   private boolean isInteractResend(BlockPos pos) {
      Double clickedAt = this.recentInteractResends.get(pos);
      return clickedAt != null && this.currentGameTickCalculated - clickedAt <= 20.0;
   }

   private boolean placeSoundHeard(BlockPos pos, BlockState state, double sinceTick) {
      SilentMine.PlaceSound heard = this.recentPlaceSounds.get(pos);
      return heard != null && heard.tick() >= sinceTick && !state.isAir() && heard.id().equals(state.getSoundGroup().getPlaceSound().id());
   }

   private int elapsedTicks(SilentMine.SilentMineBlock block) {
      return (int)(this.currentGameTickCalculated - block.destroyProgressStart);
   }

   private void sendSwing() {
      if (this.miningSwing.get() && this.connectionOpen()) {
         this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
      }
   }

   @Override
   public void onDeactivate() {
      this.cancelBreaking("module disabled");
      this.clearQueue();
      this.pendingInstantFinish.clear();
      this.deniedInstantFinish.clear();
      this.noDropAttempts.clear();
      this.noDropDenied.clear();
      this.stopDrops.clear();
      this.dbgLimitTicks.clear();
      this.ownSeals.clear();
      this.refusedSeals.clear();
      this.lateSeals.clear();
      this.claimedStopPos = null;
      this.noteStopSent();
      this.lastStartSentPos = null;
      this.pendingRebreakAttempt = false;
      this.parkFailures = 0;
      this.parksDisabledUntilTick = -1.0;
   }

   private double serverStopPenalty() {
      double penalty = 1.0;
      if (!RotationManager.lastGround || !this.mc.player.isOnGround()) {
         penalty /= 5.0;
      }

      if (this.mc.player.isSubmergedIn(FluidTags.WATER)) {
         penalty *= BlockUtils.submergedMiningSpeed();
      }

      return penalty;
   }

   private String serverStopStateName() {
      boolean air = !RotationManager.lastGround || !this.mc.player.isOnGround();
      boolean water = this.mc.player.isSubmergedIn(FluidTags.WATER);
      if (air && water) {
         return "airborne under water";
      } else if (water) {
         return "under water";
      } else {
         return this.mc.player.isGliding() ? "gliding" : "airborne";
      }
   }

   private boolean isPaused() {
      return this.isBeingTotemPopped ? true : this.pauseWhileEating.get() && this.mc.player != null && this.mc.player.isUsingItem();
   }

   private boolean isRebreakPaused(BlockPos pos) {
      if (this.isBeingTotemPopped) {
         return true;
      } else if (this.mc.player == null || !this.mc.player.isUsingItem()) {
         return false;
      } else if (this.crystalPausedForUse(pos)) {
         return true;
      } else {
         return !this.pauseWhileEating.get() ? false : !this.rebreakWhileEating.get() || this.mc.player.getActiveHand() != Hand.OFF_HAND;
      }
   }

   private boolean crystalPausedForUse(BlockPos pos) {
      AutoMine autoMine = AutoMine.get();
      return autoMine != null && autoMine.crystalPausedForUse(pos);
   }

   private boolean sealStopPausedForUse(BlockPos pos) {
      if (this.mc.player != null && this.mc.player.isUsingItem() && this.crystalPausedForUse(pos)) {
         AutoCrystal autoCrystal = AutoCrystal.get();
         return autoCrystal != null && autoCrystal.glassSealOwns(pos);
      } else {
         return false;
      }
   }

   private String rebreakPauseReason() {
      if (this.isBeingTotemPopped) {
         return "the totem-pop pause";
      } else {
         String item = this.mc.player.getActiveItem().getName().getString();
         if (this.rebreakBlock != null && this.crystalPausedForUse(this.rebreakBlock.blockPos)) {
            return "AutoCrystal's pause-eat-place (" + item + " in use: no crystal or glass could follow this STOP)";
         } else {
            return this.mc.player.getActiveHand() == Hand.OFF_HAND
               ? "pause-while-eating (" + item + " in the offhand, rebreak-while-eating off)"
               : "pause-while-eating (" + item + " in the main hand: a tool swap would end the use)";
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onTickStart(TickEvent.Pre event) {
      this.claimedStopPos = null;
      this.tickCount++;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.currentGameTickCalculated = gameTick();
      this.pendingRebreakAttempt = false;
      if (!this.ownSeals.isEmpty() && this.mc.world != null) {
         this.tickSeals();
      }

      if (!this.refusedSeals.isEmpty()) {
         this.refusedSeals.values().removeIf(t -> this.currentGameTickCalculated - t > 20.0);
      }

      if (!this.lateSeals.isEmpty()) {
         this.lateSeals.keySet().removeIf(p -> !this.refusedSeals.containsKey(p));
      }

      if (!this.pendingInstantFinish.isEmpty()) {
         this.pendingInstantFinish.values().removeIf(t -> this.currentGameTickCalculated - t > 60.0);
      }

      if (!this.deniedInstantFinish.isEmpty()) {
         this.deniedInstantFinish.values().removeIf(t -> this.currentGameTickCalculated - t > 400.0);
      }

      if (!this.recentInteractResends.isEmpty()) {
         this.recentInteractResends.values().removeIf(t -> this.currentGameTickCalculated - t > 60.0);
      }

      if (!this.recentPlaceSounds.isEmpty()) {
         this.recentPlaceSounds.values().removeIf(s -> this.currentGameTickCalculated - s.tick() > 20.0);
      }

      if (!this.noDropAttempts.isEmpty()) {
         this.noDropAttempts.values().removeIf(t -> this.currentGameTickCalculated - t > 20.0);
      }

      if (!this.noDropDenied.isEmpty()) {
         this.noDropDenied.values().removeIf(t -> this.currentGameTickCalculated - t > 200.0);
      }

      if (!this.stopDrops.isEmpty() && this.mc.world != null && this.mc.player != null) {
         this.stopDrops.entrySet().removeIf(e -> this.dropSettled(e.getKey(), e.getValue()));
      }

      this.handleHoldBrokenExternally();
      if (this.manualHotbarGraceTicks > 0) {
         this.manualHotbarGraceTicks--;
      }

      if (this.mc.player != null && !this.needDelayedDestroySwapBack) {
         this.capturedOriginal = this.mc.player.getMainHandStack().copy();
         this.capturedSlot = this.mc.player.getInventory().selectedSlot;
      }

      if (this.mc.player != null && this.mc.player.isUsingItem() && this.mc.player.getActiveItem().getItem() == Items.TOTEM_OF_UNDYING) {
         this.isBeingTotemPopped = true;
         this.totemPopTicks = 0;
      } else {
         this.totemPopTicks++;
      }

      if (this.totemPopTicks > 30) {
         this.isBeingTotemPopped = false;
      }

      boolean paused = this.isPaused();
      this.lastDelayedDestroyBlockPos = this.hasDelayedDestroy() ? this.delayedDestroyBlock.blockPos : null;
      this.tickParkedBlock();
      this.updateParkToolHold(paused);
      if (this.hasDelayedDestroy() && this.shouldTreatAsBroken(this.delayedDestroyBlock)) {
         this.onPrimaryBroken();
      }

      if (this.hasRebreakBlock() && (this.mc.world.getBlockState(this.rebreakBlock.blockPos).isAir() || !BlockUtils.canBreak(this.rebreakBlock.blockPos))) {
         this.clearRebreak("went air before we mined it (never our session)");
      }

      if (this.canRebreakRebreakBlock() && !this.isSessionPos(this.rebreakBlock.blockPos)) {
         this.clearRebreak("not the session any more (newest START at " + fmt(this.lastStartSentPos) + ")");
      }

      if (this.canRebreakRebreakBlock() && this.rebreakFailureLimitReached() && (BlockUtils.canBreak(this.rebreakBlock.blockPos) || !this.instantVisual.get())) {
         this.noDropDenied.put(this.rebreakBlock.blockPos.toImmutable(), this.currentGameTickCalculated);
         this.clearRebreak(this.rebreakBlock.stopsSinceReplace + " STOPs since the last re-place and no server air — ignored, AutoMine may re-feed it");
      }

      if (!this.doubleMine.get() && this.hasRebreakBlock()) {
         this.clearRebreak("double-mine turned off");
      }

      if (!this.rebreakEnabled.get() && this.canRebreakRebreakBlock()) {
         this.clearRebreak("rebreak turned off");
      }

      this.rangeCancelledAt.values().removeIf(tick -> this.currentGameTickCalculated - tick > 10.0);
      if (this.hasDelayedDestroy() && !this.inCancelRange(this.delayedDestroyBlock.blockPos)) {
         this.dbg("primary " + fmt(this.delayedDestroyBlock.blockPos) + " out of range — cancelled");
         this.rangeCancelledAt.put(this.delayedDestroyBlock.blockPos.toImmutable(), this.currentGameTickCalculated);
         this.delayedDestroyBlock.cancelBreaking();
         this.delayedDestroyBlock = null;
         this.primaryRestarts = 0;
         this.primaryRestartPending = false;
      }

      if (this.hasRebreakBlock() && !this.inCancelRange(this.rebreakBlock.blockPos)) {
         this.rangeCancelledAt.put(this.rebreakBlock.blockPos.toImmutable(), this.currentGameTickCalculated);
         this.clearRebreak("out of range");
      }

      if (this.canRebreakRebreakBlock() && !this.inRebreakKeepRange(this.rebreakBlock.blockPos)) {
         this.clearRebreak("beyond range + range-slack + 2");
      }

      if (this.canRebreakRebreakBlock() && this.inBreakRange(this.rebreakBlock.blockPos)) {
         this.rebreakBlock.lastInRangeTick = this.currentGameTickCalculated;
      }

      if (this.hasDelayedDestroy() && !this.delayedDestroyBlock.started && !paused && !this.digCooldownActive()) {
         this.delayedDestroyBlock.startBreaking(true);
         this.dbg("primary " + fmt(this.delayedDestroyBlock.blockPos) + " session opened after dig cooldown");
      }

      this.tryPark(paused);
      if (this.hasDelayedDestroy() && !paused) {
         this.tickPrimary();
      }

      if (!this.hasDelayedDestroy() && this.rebreakBlock != null && this.rebreakIsDoubleMine && !this.rebreakBlock.beenAir && !this.rebreakBlock.started) {
         SilentMine.SilentMineBlock next = this.rebreakBlock;
         this.rebreakBlock = null;
         this.rebreakIsDoubleMine = false;
         this.delayedDestroyBlock = new SilentMine.SilentMineBlock(next.blockPos, next.direction, next.getPriority(), false);
         this.dbg("double-mine block " + fmt(next.blockPos) + " promoted to primary (primary slot freed early)");
      }

      if (this.canRebreakRebreakBlock() && BlockUtils.canBreak(this.rebreakBlock.blockPos)) {
         boolean tickPath = !this.useAggressiveRebreak() || this.deferMovingAim();
         if (this.isOwnSeal(this.rebreakBlock.blockPos)) {
            this.logRebreakWait("seal", "held: our glass seal, AutoCrystal opens it");
         } else if (this.isStopClaimed(this.rebreakBlock.blockPos)) {
            this.logRebreakWait("claim", "held: AutoCrystal's combo sends this STOP (glass behind it, same packet batch)");
         } else if (this.isRebreakPaused(this.rebreakBlock.blockPos)) {
            this.logRebreakWait("paused", "re-placed, held by " + this.rebreakPauseReason());
         } else if (!this.inBreakRange(this.rebreakBlock.blockPos)) {
            this.logRebreakWait("range", "re-placed but out of break range — attempt skipped (kept to range + range-slack + 2)");
         } else if (this.rebreakReady(true) && tickPath) {
            boolean sendAttempt = !this.playerActionBudgetLow() && this.rebreakBudgetLeft();
            if (sendAttempt) {
               if (!this.deferMovingAim()) {
                  if (this.aimStop(this.rebreakBlock.blockPos, !this.silentBudgetSpentThisTick())) {
                     if (this.lastAimFace != null) {
                        this.rebreakBlock.direction = this.lastAimFace;
                     }

                     this.executeRebreakAttempt("tick");
                  }
               } else {
                  this.stageMovingRebreak();
               }
            }
         }
      }

      this.tickFailRestart(paused);
      if (this.needDelayedDestroySwapBack && !this.hasDelayedDestroy() && this.parkedBlock == null) {
         ReviveClient.SWAP.endSwap(false);
         this.needDelayedDestroySwapBack = false;
      }

      if (!this.predictedBroken.isEmpty()) {
         this.predictedBroken.entrySet().removeIf(e -> this.currentGameTickCalculated - e.getValue() > 200.0);
      }

      this.processQueue();
   }

   private void handleHoldBrokenExternally() {
      if (ReviveClient.SWAP.consumeMultiTickHoldBroken()) {
         this.parkHoldActive = false;
         int grace = 40;
         if (this.needDelayedDestroySwapBack) {
            this.needDelayedDestroySwapBack = false;
            boolean evictedByOwnItemUse = this.mc.player != null && this.mc.player.isUsingItem();
            if (evictedByOwnItemUse) {
               this.quickHoldBreaks = 0;
               grace = 0;
               this.dbg("tool hold released — yielded to our own item use (eating)");
            } else {
               if (this.currentGameTickCalculated - this.holdTakenTick <= 5.0) {
                  this.quickHoldBreaks++;
                  if (this.quickHoldBreaks >= 2) {
                     grace = Math.min(40 << this.quickHoldBreaks - 1, 120);
                  }
               } else {
                  this.quickHoldBreaks = 0;
               }

               this.dbg(
                  "tool hold released — player took the hotbar (grace "
                     + grace
                     + " ticks"
                     + (this.quickHoldBreaks >= 2 ? ", kicked " + this.quickHoldBreaks + "x — backing off" : "")
                     + ")"
               );
            }
         }

         this.manualHotbarGraceTicks = grace;
      }
   }

   private void tickParkedBlock() {
      if (this.parkedBlock != null) {
         boolean replacedUnseen = this.parkedBlock.serverAirConfirmed
            || this.placeSoundHeard(
               this.parkedBlock.blockPos,
               this.mc.world.getBlockState(this.parkedBlock.blockPos),
               this.parkedBlock.destroyProgressStart + this.parkedBlock.lastStopElapsed
            );
         if (replacedUnseen || this.mc.world.getBlockState(this.parkedBlock.blockPos).isAir() || !BlockUtils.canBreak(this.parkedBlock.blockPos)) {
            BlockPos pos = this.parkedBlock.blockPos.toImmutable();
            Direction dir = this.parkedBlock.direction;
            double prio = this.parkedBlock.getPriority();
            boolean bypassed = this.parkedBlock.bypassedDelay;
            BlockState parkedState = this.parkedBlock.initialState != null ? this.parkedBlock.initialState : this.parkedBlock.effectiveState();
            FindItemResult parkTool = InvUtils.findFastestToolHotbar(parkedState);
            ItemStack breakItem = (this.parkHoldActive || this.needDelayedDestroySwapBack) && parkTool.found()
               ? this.mc.player.getInventory().getStack(parkTool.slot())
               : this.mc.player.getMainHandStack();
            boolean drops = this.breakDrops(breakItem, parkedState);
            this.noteStop(pos, drops, itemName(breakItem), 5);
            this.dbg(
               "parked "
                  + fmt(pos)
                  + " broken by server (delayed slot) at elapsed "
                  + this.elapsedTicks(this.parkedBlock)
                  + " ticks"
                  + (replacedUnseen && !this.mc.world.getBlockState(pos).isAir() ? " (already re-placed)" : "")
                  + (
                     this.hasDelayedDestroy()
                        ? "; second block at " + this.elapsedTicks(this.delayedDestroyBlock) + "/" + this.delayedDestroyBlock.heldTicksNeeded()
                        : ""
                  )
                  + "; drops="
                  + (drops ? "y" : "n")
                  + " ("
                  + itemName(breakItem)
                  + ")"
            );
            ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Post(pos, false));
            this.lastPrimaryBreakTick = this.currentGameTickCalculated;
            this.lastBreakBypassed = bypassed;
            this.parkedBlock = null;
            this.parkFailures = 0;
            if (this.rebreakEnabled.get() && this.rebreakBlock == null) {
               if (this.isSessionPos(pos)) {
                  this.armRebreak(pos, dir, prio, bypassed, "parked block broken, still the session");
               } else {
                  this.dbg("parked " + fmt(pos) + " not armed for rebreak — the session is on " + fmt(this.lastStartSentPos));
               }
            }
         } else if (!this.inCancelRange(this.parkedBlock.blockPos)) {
            this.dbg("parked " + fmt(this.parkedBlock.blockPos) + " out of range — dropped");
            this.parkedBlock.cancelBreaking();
            this.parkedBlock = null;
         } else if (this.elapsedTicks(this.parkedBlock) > this.parkedBlock.heldTicksNeeded() * 2 + this.singleBreakFailTicks.get()) {
            BlockPos pos = this.parkedBlock.blockPos.toImmutable();
            Direction dir = this.parkedBlock.direction;
            double prio = this.parkedBlock.getPriority();
            this.parkFailures++;
            boolean backOff = this.parkFailures >= 3;
            boolean keepSession = this.rebreakEnabled.get()
               && prio > 100.0
               && (this.hasDelayedDestroy() || this.canRebreakRebreakBlock() && this.isSessionPos(this.rebreakBlock.blockPos));
            this.dbg(
               "parked "
                  + fmt(pos)
                  + " NOT broken after "
                  + this.elapsedTicks(this.parkedBlock)
                  + " ticks — park refused #"
                  + this.parkFailures
                  + (backOff ? " (parks paused for 200 ticks)" : "")
                  + (keepSession ? "; dropped, the session stays on " + fmt(this.lastStartSentPos) : "; re-mining it")
            );
            if (backOff) {
               this.parksDisabledUntilTick = this.currentGameTickCalculated + 200.0;
               this.parkFailures = 0;
            }

            this.parkedBlock.cancelBreaking();
            this.parkedBlock = null;
            if (!keepSession) {
               this.requestBreak(pos, dir, prio);
            }
         } else {
            this.sendSwing();
         }
      }
   }

   private void onPrimaryBroken() {
      BlockPos brokenPos = this.delayedDestroyBlock.blockPos.toImmutable();
      Direction brokenDir = this.delayedDestroyBlock.direction;
      double brokenPriority = this.delayedDestroyBlock.getPriority();
      boolean brokenBypassed = this.delayedDestroyBlock.bypassedDelay;
      ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Post(brokenPos, false));
      this.lastBreakBypassed = this.delayedDestroyBlock.bypassedDelay;
      this.delayedDestroyBlock = null;
      this.primaryRestartPending = false;
      this.lastPrimaryBreakTick = this.currentGameTickCalculated;
      this.dbg("primary " + fmt(brokenPos) + " broken by server" + (this.primaryRestarts > 0 ? " (after " + this.primaryRestarts + " restart(s))" : ""));
      this.primaryRestarts = 0;
      if (this.hasRebreakBlock()) {
         SilentMine.SilentMineBlock next = this.rebreakBlock;
         this.rebreakBlock = null;
         this.rebreakIsDoubleMine = false;
         boolean stillSolid = !this.mc.world.getBlockState(next.blockPos).isAir();
         this.delayedDestroyBlock = new SilentMine.SilentMineBlock(next.blockPos, next.direction, next.getPriority(), false);
         if (!this.simultaneous.get()
            && this.instantVisual.get()
            && stillSolid
            && !this.noSilkStop(next.blockPos)
            && this.delayedDestroyBlock.initialState != null
            && !this.delayedDestroyBlock.initialState.isAir()) {
            this.delayedDestroyBlock.clientPredicted = true;
            this.mc.world.setBlockState(next.blockPos, Blocks.AIR.getDefaultState());
            this.predictedBroken.put(next.blockPos.toImmutable(), this.currentGameTickCalculated);
            this.dbg("double-mine second block " + fmt(next.blockPos) + " promoted and cleared on screen");
         } else {
            this.dbg("double-mine block " + fmt(next.blockPos) + " promoted to primary");
         }
      }

      if (this.rebreakEnabled.get() && (this.rebreakBlock == null || this.canRebreakRebreakBlock())) {
         if (this.isSessionPos(brokenPos)) {
            if (this.rebreakBlock != null) {
               this.clearRebreak("replaced by the broken primary " + fmt(brokenPos));
            }

            this.armRebreak(brokenPos, brokenDir, brokenPriority, brokenBypassed, "primary broken by our STOP, owns the session");
         } else {
            this.dbg("primary " + fmt(brokenPos) + " not armed for rebreak — the session is on " + fmt(this.lastStartSentPos));
         }
      }
   }

   private void armRebreak(BlockPos pos, Direction dir, double priority, boolean bypassed, String why) {
      this.rebreakBlock = new SilentMine.SilentMineBlock(pos, dir, priority, true);
      this.rebreakBlock.beenAir = true;
      this.rebreakBlock.bypassedDelay = bypassed;
      this.rebreakIsDoubleMine = false;
      this.dbg("rebreak " + fmt(pos) + " armed (" + why + ")");
   }

   private void clearRebreak(String reason) {
      if (this.rebreakBlock != null) {
         this.dbg((this.rebreakBlock.beenAir ? "rebreak " : "double-mine ") + fmt(this.rebreakBlock.blockPos) + " cleared: " + reason);
         this.rebreakBlock = null;
         this.rebreakIsDoubleMine = false;
      }
   }

   private boolean isSessionPos(BlockPos pos) {
      return pos != null && pos.equals(this.lastStartSentPos);
   }

   private void tryPark(boolean paused) {
      if (!paused && !(this.currentGameTickCalculated < this.parksDisabledUntilTick) && this.parkedBlock == null && this.hasDelayedDestroy()) {
         SilentMine.SilentMineBlock primary = this.delayedDestroyBlock;
         if (primary.started && !primary.clientPredicted && !(primary.serverProgress >= 0.7)) {
            if (!this.isOwnSeal(primary.blockPos) && !this.isStopClaimed(primary.blockPos)) {
               boolean reserveReady = this.rebreakBlock != null
                  && this.rebreakIsDoubleMine
                  && !this.rebreakBlock.beenAir
                  && this.inBreakRange(this.rebreakBlock.blockPos);
               if (this.packetMode.get()) {
                  if (this.doubleMine.get() && reserveReady && primary.timesSendBreakPacket == 0) {
                     if (!this.grimBypass.get() || primary.bypassedDelay) {
                        SilentMine.SilentMineBlock second = this.rebreakBlock;
                        this.parkAndOpen(primary, second.blockPos, second.direction, second.getPriority());
                        this.rebreakBlock = null;
                        this.rebreakIsDoubleMine = false;
                        this.dbg(
                           "packet-mode double mine: parked "
                              + fmt(primary.blockPos)
                              + " at elapsed "
                              + this.elapsedTicks(primary)
                              + " (finishes ~"
                              + this.parkFinishTicks(primary)
                              + " ticks from START); opened "
                              + fmt(second.blockPos)
                        );
                     }
                  }
               } else if (this.simultaneous.get()) {
                  if (primary.serverProgress >= this.parkAtProgress.get() || this.parkAfterBypass(primary)) {
                     if (reserveReady) {
                        SilentMine.SilentMineBlock second = this.rebreakBlock;
                        this.parkAndOpen(primary, second.blockPos, second.direction, second.getPriority());
                        this.rebreakBlock = null;
                        this.rebreakIsDoubleMine = false;
                        this.dbg(
                           "simultaneous: parked "
                              + fmt(primary.blockPos)
                              + " at progress "
                              + String.format("%.2f", primary.serverProgress)
                              + "; opened "
                              + fmt(second.blockPos)
                        );
                     } else if (this.queueing.get()
                        && (this.rebreakBlock == null || !this.rebreakIsDoubleMine)
                        && this.activeQueuedBlocks.containsKey(primary.blockPos)) {
                        SilentMine.QueuedBlock next = this.pollNextReadyQueuedBlock();
                        if (next == null) {
                           return;
                        }

                        this.parkAndOpen(primary, next.blockPos, next.direction, next.priority);
                        this.activeQueuedBlocks.put(next.blockPos, next);
                        this.dbg(
                           "simultaneous(queue): parked "
                              + fmt(primary.blockPos)
                              + "; opened "
                              + fmt(next.blockPos)
                              + " ("
                              + this.queuedBlocks.size()
                              + " still queued)"
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private void parkAndOpen(SilentMine.SilentMineBlock primary, BlockPos nextPos, Direction nextDir, double nextPriority) {
      primary.sendParkStop();
      primary.lastStopElapsed = this.elapsedTicks(primary);
      this.parkedBlock = primary;
      this.primaryRestartPending = false;
      this.primaryRestarts = 0;
      this.delayedDestroyBlock = new SilentMine.SilentMineBlock(nextPos, nextDir, nextPriority, false);
      this.delayedDestroyBlock.startBreaking(true);
   }

   private void tickPrimary() {
      SilentMine.SilentMineBlock b = this.delayedDestroyBlock;
      BlockState blockState = b.effectiveState();
      FindItemResult result = InvUtils.findFastestTool(blockState);
      if (!result.found() || this.mc.player.getInventory().selectedSlot == result.slot()) {
         this.toolMoveCandidateSlot = -1;
         this.toolMoveCandidateTicks = 0;
      }

      if (!this.packetMode.get()
         && result.found()
         && this.mc.player.getInventory().selectedSlot != result.slot()
         && this.manualHotbarGraceTicks == 0
         && this.toolMoveConfirmed(result.slot())
         && ReviveClient.SWAP.beginSwap(result, false, true)) {
         this.needDelayedDestroySwapBack = true;
         this.holdTakenTick = this.currentGameTickCalculated;
         this.dbg("primary " + fmt(b.blockPos) + " holding tool (slot " + result.slot() + "), first STOP at ~" + b.heldTicksNeeded() + " wall ticks");
      }

      if (!result.found() && b.ticksHeldPickaxe == 0) {
         this.dbg("primary " + fmt(b.blockPos) + " no tool found for " + blockState.getBlock().getName().getString() + " — mining bare-handed");
      }

      if (b.started && (this.packetMode.get() || !result.found() || this.mc.player.getInventory().selectedSlot == result.slot())) {
         b.ticksHeldPickaxe++;
         b.tickServerProgress();
         this.sendSwing();
         if (this.grimBypass.get()
            && !b.bypassedDelay
            && b.ticksHeldPickaxe > 6
            && ReviveClient.PACKET.canBurst(PacketManager.Category.GLOBAL, this.grimBypassBurst.get())) {
            b.sendDelayBypassBurst();
            this.dbg("grim-bypass: sent " + this.grimBypassBurst.get() + "x START @ Y+955 for " + fmt(b.blockPos));
         }

         int elapsed = this.elapsedTicks(b);
         double stopPenalty = this.serverStopPenalty();
         boolean retryDue = b.stopRetryDue;
         boolean confirmOnly = b.locallyCleared() && !retryDue;
         boolean claimed = this.isStopClaimed(b.blockPos);
         boolean usePaused = !claimed && this.sealStopPausedForUse(b.blockPos);
         if (!claimed && !usePaused && !this.isOwnSeal(b.blockPos) && !this.lateSeals.containsKey(b.blockPos)) {
            if (!(b.serverProgress < b.stopTargetProgress(stopPenalty))
               && !this.primaryRestartPending
               && (retryDue || b.timesSendBreakPacket < 4 && elapsed - b.lastStopElapsed >= 4)
               && (confirmOnly || this.aimStop(b.blockPos, true))) {
               FindItemResult stopTool = this.packetMode.get() ? this.stopTool(b.blockPos, b.effectiveState()) : null;
               ItemStack stopItem = this.stopItem(stopTool);
               boolean drops = this.breakDrops(stopItem, b.effectiveState());
               boolean stopSwap = stopTool != null && stopTool.found() && this.mc.player.getInventory().selectedSlot != stopTool.slot();
               if (stopSwap && !ReviveClient.SWAP.beginSwap(stopTool, true)) {
                  this.dbg("primary " + fmt(b.blockPos) + " STOP deferred — tool swap unavailable this tick");
               } else {
                  if (!confirmOnly && this.lastAimFace != null) {
                     b.direction = this.lastAimFace;
                  }

                  if (b.firstStopElapsed < 0) {
                     b.firstStopElapsed = elapsed;
                  }

                  b.lastStopElapsed = elapsed;
                  b.stopRetryDue = false;
                  this.noteStop(b.blockPos, drops, itemName(stopItem));

                  try {
                     b.tryBreak();
                  } finally {
                     if (stopSwap) {
                        ReviveClient.SWAP.endSwap(true);
                     }
                  }

                  this.noteStopSent();
                  this.dbg(
                     "primary "
                        + fmt(b.blockPos)
                        + (retryDue ? " STOP-only retry" : " STOP")
                        + " (server progress ~"
                        + String.format("%.2f", b.serverProgress)
                        + " at elapsed "
                        + elapsed
                        + " ticks"
                        + (stopSwap ? ", tool slot " + stopTool.slot() : "")
                        + (stopPenalty < 1.0 ? String.format(", %s at x%.2f speed", this.serverStopStateName(), stopPenalty) : "")
                        + ", drops="
                        + (drops ? "y" : "n")
                        + " ("
                        + itemName(stopItem)
                        + "))"
                  );
                  ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Sent(b.blockPos.toImmutable(), false));
               }
            }
         } else {
            if (b.locallyCleared() || b.serverProgress >= b.stopTargetProgress(stopPenalty)) {
               this.dbgLimited(
                  "hold " + b.blockPos.asLong(),
                  20.0,
                  "primary "
                     + fmt(b.blockPos)
                     + " STOP held: "
                     + (
                        claimed
                           ? "AutoCrystal's combo sends it (glass behind it, same packet batch)"
                           : (
                              usePaused
                                 ? "AutoCrystal's pause-eat-place ("
                                    + this.mc.player.getActiveItem().getName().getString()
                                    + " in use: no glass or crystal could follow it)"
                                 : "our glass seal, AutoCrystal opens it"
                           )
                     )
               );
            }
         }
      }
   }

   private void tickFailRestart(boolean paused) {
      if (this.hasDelayedDestroy() && this.delayedDestroyBlock.started && !paused) {
         SilentMine.SilentMineBlock b = this.delayedDestroyBlock;
         if (!this.isOwnSeal(b.blockPos) || this.sealConfirmed(b.blockPos)) {
            int elapsed = this.elapsedTicks(b);
            boolean rejectedRestartDue = this.primaryRestartPending && this.currentGameTickCalculated >= this.primaryRestartAtTick;
            boolean stopsTimedOut = b.firstStopElapsed >= 0 && elapsed - b.verdictBaseElapsed() > this.singleBreakFailTicks.get();
            boolean neverStopped = b.firstStopElapsed < 0
               && this.currentGameTickCalculated - b.timeoutBaseTick
                  > b.heldTicksNeeded() * 2 / this.serverStopPenalty() + this.singleBreakFailTicks.get().intValue();
            if (rejectedRestartDue || !this.primaryRestartPending && (stopsTimedOut || neverStopped)) {
               if (!neverStopped || this.primaryRestartPending || !this.sealStopPausedForUse(b.blockPos)) {
                  if (neverStopped && !this.primaryRestartPending && this.isOwnSeal(b.blockPos)) {
                     boolean headBehind = this.rebreakBlock != null
                        && this.rebreakIsDoubleMine
                        && !this.rebreakBlock.beenAir
                        && this.rebreakBlock.blockPos.equals(this.mc.player.getBlockPos().up());
                     AutoMine autoMine = AutoMine.get();
                     if (headBehind || autoMine == null || !autoMine.isSealablePos(b.blockPos)) {
                        this.dbg(
                           "primary "
                              + fmt(b.blockPos)
                              + " is our glass seal and "
                              + (headBehind ? "our head is reserved behind it" : "AutoCrystal's combo can't open it from here")
                              + " - cancelled, the glass stays"
                        );
                        b.cancelBreaking();
                        this.delayedDestroyBlock = null;
                        this.primaryRestarts = 0;
                        this.primaryRestartPending = false;
                        if (headBehind) {
                           SilentMine.SilentMineBlock next = this.rebreakBlock;
                           this.rebreakBlock = null;
                           this.rebreakIsDoubleMine = false;
                           this.delayedDestroyBlock = new SilentMine.SilentMineBlock(next.blockPos, next.direction, next.getPriority(), false);
                           this.dbg("double-mine block " + fmt(next.blockPos) + " promoted to primary (primary slot freed early)");
                        }
                     }
                  } else {
                     String reason = rejectedRestartDue ? "session rejected by server (re-sync)" : "NOT broken at elapsed " + elapsed + " ticks";
                     if (this.rotate.get() && this.primaryRestarts >= 1 && !b.locallyCleared() && this.visibleHit(this.serverEye(), b.blockPos) == null) {
                        this.dbg("primary " + fmt(b.blockPos) + " " + reason + " — no visible face from server eye, cancelled");
                        b.cancelBreaking();
                        this.delayedDestroyBlock = null;
                        this.primaryRestarts = 0;
                        this.primaryRestartPending = false;
                     } else {
                        this.primaryRestartPending = false;
                        this.primaryRestarts++;
                        this.dbg(
                           "primary "
                              + fmt(b.blockPos)
                              + " "
                              + reason
                              + " (server progress ~"
                              + String.format("%.2f", b.serverProgress)
                              + ") — fresh session (restart #"
                              + this.primaryRestarts
                              + ")"
                        );
                        b.startBreaking(true);
                     }
                  }
               }
            }
         }
      }
   }

   public void silentBreakBlock(BlockPos blockPos, Direction direction, double priority) {
      if (this.isRemote()) {
         PingBypass pingBypass = this.isActive() ? Modules.get().get(PingBypass.class) : null;
         if (pingBypass != null) {
            pingBypass.forwardMine(blockPos, direction, priority, true);
         }
      } else if (this.isRunningHere()
         && blockPos != null
         && this.connectionOpen()
         && !this.alreadyBreaking(blockPos)
         && BlockUtils.canBreak(blockPos, this.mc.world.getBlockState(blockPos))
         && this.inBreakRange(blockPos)
         && !this.recentlyRangeCancelled(blockPos)) {
         if (ReviveClient.PACKET.canSend(PacketManager.Category.GLOBAL)) {
            if (!this.digCooldownActive()) {
               BlockState instaState = this.mc.world.getBlockState(blockPos);
               if (!this.instantFinish.get() || !this.canInstantFinish(instaState) || this.isInstantFinishDenied(blockPos)) {
                  boolean isAntiSwimBlock = blockPos.equals(this.mc.player.getBlockPos().up());
                  if (!this.hasDelayedDestroy()) {
                     this.delayedDestroyBlock = new SilentMine.SilentMineBlock(blockPos, direction, priority, false);
                     this.primaryRestarts = 0;
                     this.primaryRestartPending = false;
                     this.dbg("primary start " + fmt(blockPos) + " (" + this.mc.world.getBlockState(blockPos).getBlock().getName().getString() + ")");
                     FindItemResult holdTool = InvUtils.findFastestTool(this.mc.world.getBlockState(blockPos));
                     if (!this.packetMode.get()
                        && holdTool.found()
                        && this.mc.player.getInventory().selectedSlot != holdTool.slot()
                        && this.manualHotbarGraceTicks == 0
                        && ReviveClient.SWAP.beginSwap(holdTool, false, true)) {
                        this.needDelayedDestroySwapBack = true;
                        this.holdTakenTick = this.currentGameTickCalculated;
                        this.dbg("primary " + fmt(blockPos) + " holding tool (slot " + holdTool.slot() + ") for the whole session");
                     }

                     this.delayedDestroyBlock.startBreaking(true);
                  }

                  if (!this.alreadyBreaking(blockPos)) {
                     if (this.rebreakBlock != null
                        && this.delayedDestroyBlock != null
                        && (priority >= this.rebreakBlock.priority || this.canRebreakRebreakBlock())
                        && this.delayedDestroyBlock.getBreakProgress() <= 0.8) {
                        this.clearRebreak("replaced by a request for " + fmt(blockPos) + " (priority " + (int)priority + ")");
                     }

                     if (this.doubleMine.get() && (this.rebreakBlock == null || isAntiSwimBlock)) {
                        this.rebreakBlock = new SilentMine.SilentMineBlock(blockPos, direction, priority, true);
                        this.rebreakIsDoubleMine = true;
                        this.dbg(
                           "double-mine armed "
                              + fmt(blockPos)
                              + " ("
                              + this.mc.world.getBlockState(blockPos).getBlock().getName().getString()
                              + ") — reserved"
                        );
                     }
                  }
               } else if (this.aimAt(blockPos)) {
                  if (this.lastAimFace != null) {
                     direction = this.lastAimFace;
                  }

                  this.dbg("instant-finish " + fmt(blockPos) + " (" + instaState.getBlock().getName().getString() + ")");
                  this.clearRebreak("instant-finish START at " + fmt(blockPos) + " restarts the session clock");
                  FindItemResult tool = preferredTool(instaState);
                  ItemStack toolItem = this.stopItem(tool);
                  boolean swapped = tool.found() && this.mc.player.getInventory().selectedSlot != tool.slot() && ReviveClient.SWAP.beginSwap(tool, true);
                  ItemStack instaItem = !swapped && this.mc.player.getInventory().selectedSlot != tool.slot() ? this.mc.player.getMainHandStack() : toolItem;
                  this.noteStop(blockPos, this.breakDrops(instaItem, instaState), itemName(instaItem));

                  try {
                     this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, blockPos, direction, this.getSeq()));
                  } finally {
                     if (swapped) {
                        ReviveClient.SWAP.endSwap(true);
                     }
                  }

                  this.mc.world.setBlockState(blockPos, Blocks.AIR.getDefaultState());
                  this.pendingInstantFinish.put(blockPos.toImmutable(), this.currentGameTickCalculated);
                  this.lastPrimaryBreakTick = this.currentGameTickCalculated;
                  this.lastBreakBypassed = false;
                  ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Post(blockPos.toImmutable(), false));
                  if (this.hasDelayedDestroy()) {
                     this.delayedDestroyBlock.started = false;
                     this.dbg("primary " + fmt(this.delayedDestroyBlock.blockPos) + " session stolen by instant-finish — reclaim after dig cooldown");
                  }
               }
            }
         }
      }
   }

   private boolean isReservedDoubleMine() {
      return this.rebreakBlock != null && this.rebreakIsDoubleMine && !this.rebreakBlock.beenAir && this.hasDelayedDestroy();
   }

   private boolean shouldTreatAsBroken(SilentMine.SilentMineBlock b) {
      if (b.serverAirConfirmed) {
         return true;
      } else if (!b.locallyCleared()) {
         return this.mc.world.getBlockState(b.blockPos).isAir() || !BlockUtils.canBreak(b.blockPos);
      } else if (b.started && b.timesSendBreakPacket != 0 && !this.primaryRestartPending) {
         return this.elapsedTicks(b) - b.verdictBaseElapsed() <= this.singleBreakFailTicks.get()
            ? false
            : b.clientPredicted || this.mc.world.getBlockState(b.blockPos).isAir() || !BlockUtils.canBreak(b.blockPos);
      } else {
         return false;
      }
   }

   @EventHandler
   public void onStartBreakingBlock(StartBreakingBlockEvent event) {
      event.cancel();
      this.handleClick(event.blockPos, event.direction);
   }

   public void handleClick(BlockPos pos, Direction dir) {
      this.requestBreak(pos, dir, 100.0);
   }

   public void requestBreak(BlockPos pos, Direction dir, double priority) {
      if (this.isRunningHere() && this.mc.world != null && pos != null) {
         if (!this.queueing.get() || !this.passesQueueFilter(this.mc.world.getBlockState(pos))) {
            this.silentBreakBlock(pos, dir, priority);
         } else if (!this.hasDelayedDestroy() && !this.digCooldownActive()) {
            this.silentBreakBlock(pos, dir, priority);
         } else {
            this.enqueueBreak(pos, dir, priority);
         }
      }
   }

   public boolean hasDelayedDestroy() {
      return this.delayedDestroyBlock != null;
   }

   public boolean shouldAcceptBulkRequest(BlockPos pos) {
      if (this.mc.world == null || pos == null) {
         return false;
      } else if (!this.hasDelayedDestroy()) {
         return true;
      } else {
         BlockState state = this.mc.world.getBlockState(pos);
         if (this.queueing.get() && this.passesQueueFilter(state)) {
            return true;
         } else {
            return this.hasRebreakBlock() ? false : !this.instantFinish.get() || !this.canInstantFinish(state) || this.isInstantFinishDenied(pos);
         }
      }
   }

   public boolean hasRebreakBlock() {
      return this.rebreakBlock != null && !this.rebreakBlock.beenAir;
   }

   public BlockPos getDelayedDestroyBlockPos() {
      return this.delayedDestroyBlock != null ? this.delayedDestroyBlock.blockPos : null;
   }

   public boolean isPrimaryStopSent() {
      return this.delayedDestroyBlock != null && this.delayedDestroyBlock.started && this.delayedDestroyBlock.timesSendBreakPacket > 0;
   }

   public boolean isParkDoubleMine() {
      return this.doubleMine.get() && this.parkedBlock == null && !(this.currentGameTickCalculated < this.parksDisabledUntilTick)
         ? this.packetMode.get() || this.simultaneous.get()
         : false;
   }

   private boolean parkAfterBypass(SilentMine.SilentMineBlock block) {
      return this.grimBypass.get() && block.bypassedDelay;
   }

   private int parkFinishTicks(SilentMine.SilentMineBlock block) {
      BlockState state = block.effectiveState();
      if (state.isAir()) {
         return 0;
      } else {
         FindItemResult tool = InvUtils.findFastestToolHotbar(state);
         int slot = tool.found() ? tool.slot() : this.mc.player.getInventory().selectedSlot;
         double delta = BlockUtils.getBreakDeltaPerTick(slot, state, true, false);
         return delta <= 0.0 ? 1000 : (int)Math.ceil(1.0 / (delta * this.serverTicksPerWallTick()));
      }
   }

   private void updateParkToolHold(boolean paused) {
      if (!this.packetMode.get() || this.parkedBlock == null) {
         this.releaseParkHold();
      } else if (this.parkHoldActive) {
         if (this.currentGameTickCalculated - this.parkHoldTakenTick > 20.0) {
            this.dbg("parked " + fmt(this.parkedBlock.blockPos) + " still solid after 20 ticks of tool hold — releasing");
            this.releaseParkHold();
         }
      } else if (!paused && !ReviveClient.SWAP.isMultiTickSwapActive()) {
         int lead = this.preSwitchSinglebreak.get() ? 3 : 0;
         if (this.elapsedTicks(this.parkedBlock) >= this.parkFinishTicks(this.parkedBlock) - lead) {
            FindItemResult tool = InvUtils.findFastestToolHotbar(this.parkedBlock.effectiveState());
            if (tool.found() && this.mc.player.getInventory().selectedSlot != tool.slot()) {
               if (ReviveClient.SWAP.beginSwap(tool, false, true)) {
                  this.parkHoldActive = true;
                  this.parkHoldTakenTick = this.currentGameTickCalculated;
                  this.dbg(
                     "tool in for parked "
                        + fmt(this.parkedBlock.blockPos)
                        + " at elapsed "
                        + this.elapsedTicks(this.parkedBlock)
                        + "/"
                        + this.parkFinishTicks(this.parkedBlock)
                        + " ticks"
                  );
               }
            }
         }
      }
   }

   private void releaseParkHold() {
      if (this.parkHoldActive) {
         this.parkHoldActive = false;
         if (ReviveClient.SWAP.isMultiTickSwapActive()) {
            ReviveClient.SWAP.endSwap(false);
         }
      }
   }

   public void cancelBreaking() {
      this.cancelBreaking("cancelBreaking()");
   }

   public void cancelBreaking(String reason) {
      this.releaseParkHold();
      this.clearRebreak(reason);
      if (this.delayedDestroyBlock != null) {
         this.delayedDestroyBlock.cancelBreaking();
         this.delayedDestroyBlock = null;
      }

      if (this.parkedBlock != null) {
         this.parkedBlock.cancelBreaking();
         this.parkedBlock = null;
      }

      this.primaryRestarts = 0;
      this.primaryRestartPending = false;
      if (this.needDelayedDestroySwapBack) {
         ReviveClient.SWAP.endSwap(false);
         this.needDelayedDestroySwapBack = false;
         this.dbg("cancel: released held tool swap");
      }
   }

   public void abortRebreak(BlockPos pos) {
      this.abortRebreak(pos, "abortRebreak (a module places there)");
   }

   public void abortRebreak(BlockPos pos, String reason) {
      if (this.rebreakBlock != null && this.rebreakBlock.blockPos.equals(pos)) {
         this.clearRebreak(reason);
      }
   }

   public BlockPos getLastDelayedDestroyBlockPos() {
      return this.lastDelayedDestroyBlockPos;
   }

   public BlockPos getRebreakBlockPos() {
      return this.rebreakBlock != null ? this.rebreakBlock.blockPos : null;
   }

   public boolean canRebreakRebreakBlock() {
      return this.rebreakBlock != null && this.rebreakBlock.beenAir;
   }

   public BlockPos getRebreakPos() {
      return this.canRebreakRebreakBlock() ? this.rebreakBlock.blockPos : null;
   }

   public BlockPos getDoubleMinePos() {
      return this.hasRebreakBlock() ? this.rebreakBlock.blockPos : null;
   }

   public double getRebreakTicksSinceReplace() {
      return this.canRebreakRebreakBlock() ? this.currentGameTickCalculated - this.rebreakBlock.lastReplaceTick : 0.0;
   }

   public double getRebreakTicksOutOfRange() {
      return this.canRebreakRebreakBlock() && !this.inBreakRange(this.rebreakBlock.blockPos)
         ? this.currentGameTickCalculated - this.rebreakBlock.lastInRangeTick
         : 0.0;
   }

   public BlockPos getDelayedDestroyPos() {
      return this.getDelayedDestroyBlockPos();
   }

   public BlockPos getParkedPos() {
      return this.parkedBlock != null ? this.parkedBlock.blockPos : null;
   }

   public boolean isOwnMiningPos(BlockPos pos) {
      if (pos == null) {
         return false;
      } else if (this.isRemote()) {
         long key = pos.asLong();

         for (long own : this.remoteOwned) {
            if (own == key) {
               return true;
            }
         }

         return false;
      } else {
         return pos.equals(this.getDelayedDestroyBlockPos())
            || pos.equals(this.getParkedPos())
            || pos.equals(this.getRebreakBlockPos())
            || pos.equals(this.lastDelayedDestroyBlockPos);
      }
   }

   public void setRemoteOwnedPositions(long[] positions) {
      this.remoteOwned = positions;
   }

   public long[] ownMiningPositions() {
      List<Long> out = new ArrayList<>(4);

      for (BlockPos pos : new BlockPos[]{this.getDelayedDestroyBlockPos(), this.getParkedPos(), this.getRebreakBlockPos(), this.lastDelayedDestroyBlockPos}) {
         if (pos != null && !out.contains(pos.asLong())) {
            out.add(pos.asLong());
         }
      }

      long[] arr = new long[out.size()];

      for (int i = 0; i < arr.length; i++) {
         arr[i] = out.get(i);
      }

      return arr;
   }

   public boolean isMining() {
      return this.delayedDestroyBlock != null || this.rebreakBlock != null || this.parkedBlock != null;
   }

   private boolean digCooldownActive() {
      return this.digCooldownRemainingTicks() > 0.0;
   }

   private double digCooldownRemainingTicks() {
      if (this.grimBypass.get() && this.lastBreakBypassed) {
         return 0.0;
      } else {
         int cooldown = this.grimBypass.get() ? 6 : 5;
         return Math.max(0.0, cooldown / this.serverTicksPerWallTick() - (this.currentGameTickCalculated - this.lastPrimaryBreakTick));
      }
   }

   private boolean isInstantFinishDenied(BlockPos pos) {
      Double deniedAt = this.deniedInstantFinish.get(pos);
      return deniedAt != null && this.currentGameTickCalculated - deniedAt <= 400.0;
   }

   public double getPrimaryProgress() {
      SilentMine.SilentMineBlock b = this.delayedDestroyBlock;
      if (b == null || this.mc.player == null || this.mc.world == null || !b.started) {
         return 0.0;
      } else if (b.timesSendBreakPacket > 0) {
         return 1.0;
      } else {
         double target = b.stopTargetProgress(this.serverStopPenalty());
         return target > 0.0 ? Math.min(b.serverProgress / target, 1.0) : 0.0;
      }
   }

   public double getDelayedDestroyProgress() {
      return this.getPrimaryProgress();
   }

   public double getPrimaryTicksToBreak() {
      SilentMine.SilentMineBlock b = this.delayedDestroyBlock;
      if (b == null || this.mc.player == null || this.mc.world == null) {
         return -1.0;
      } else if (b.started && b.timesSendBreakPacket > 0) {
         return 0.0;
      } else {
         double rate = b.accrualPerWallTick();
         if (rate <= 0.0) {
            return Double.MAX_VALUE;
         } else {
            double target = b.stopTargetProgress(this.serverStopPenalty());
            double ticks = Math.ceil(Math.max(0.0, target - (b.started ? b.serverProgress : 0.0)) / rate);
            if (!b.started) {
               ticks += Math.ceil(this.digCooldownRemainingTicks());
            }

            return ticks;
         }
      }
   }

   public double getDoubleMineProgress() {
      return this.hasRebreakBlock() && !this.isReservedDoubleMine() ? this.rebreakBlock.getBreakProgress() : 0.0;
   }

   public double getRebreakProgress() {
      return this.rebreakBlock != null ? this.rebreakBlock.getBreakProgress() : 0.0;
   }

   public double getParkedProgress() {
      if (this.parkedBlock != null && this.mc.player != null && this.mc.world != null) {
         int finish = this.parkFinishTicks(this.parkedBlock);
         return finish <= 0 ? 1.0 : Math.min((double)this.elapsedTicks(this.parkedBlock) / finish, 1.0);
      } else {
         return 0.0;
      }
   }

   public boolean inBreakRange(BlockPos blockPos) {
      double limit = Math.min(this.range.get(), this.serverBreakReach());
      return new Box(blockPos).squaredMagnitude(this.mc.player.getEyePos()) <= limit * limit;
   }

   private boolean inCancelRange(BlockPos blockPos) {
      double limit = Math.min(this.range.get() + this.rangeSlack.get(), this.serverBreakReach());
      return new Box(blockPos).squaredMagnitude(this.mc.player.getEyePos()) <= limit * limit;
   }

   private boolean inRebreakKeepRange(BlockPos blockPos) {
      double limit = this.range.get() + this.rangeSlack.get() + 2.0;
      return new Box(blockPos).squaredMagnitude(this.mc.player.getEyePos()) <= limit * limit;
   }

   private double serverBreakReach() {
      double base = this.mc.player != null ? this.mc.player.getBlockInteractionRange() : 4.5;
      return base + 1.0;
   }

   private boolean recentlyRangeCancelled(BlockPos blockPos) {
      Double tick = this.rangeCancelledAt.get(blockPos);
      return tick != null && this.currentGameTickCalculated - tick <= 10.0;
   }

   private boolean playerActionBudgetLow() {
      PacketLimiter limiter = Modules.get().get(PacketLimiter.class);
      return limiter != null && limiter.isActive() && limiter.remaining(PlayerActionC2SPacket.class) <= 6;
   }

   private boolean rebreakBudgetLeft() {
      long now = System.currentTimeMillis();
      if (this.rebreakWindowStartMs == -1L || now - this.rebreakWindowStartMs >= 1000L) {
         this.rebreakWindowStartMs = now;
         this.rebreakPacketsInWindow = 0;
      }

      return this.rebreakPacketsInWindow < this.rebreakSpeed.get();
   }

   private boolean aimAt(BlockPos blockPos) {
      this.lastAimFace = null;
      if (!this.rotate.get()) {
         return true;
      } else {
         Vec3d eye = this.serverEye();
         Vec3d point = this.aimPoint(eye, blockPos);
         float[] aim = ReviveClient.ROTATION.getRotation(eye, point);
         return ReviveClient.ROTATION.requestSilentRotation("silent-mine", 350.0, aim[0], aim[1]);
      }
   }

   private boolean aimStop(BlockPos blockPos, boolean request) {
      this.lastAimFace = null;
      if (!this.rotate.get()) {
         return true;
      } else {
         Box box = new Box(blockPos);
         boolean motionSlot = request && ReviveClient.ROTATION.silentLooksRideMotion();
         boolean ok;
         boolean rode;
         if (request) {
            Vec3d eye = this.serverEye();
            Vec3d point = this.aimPoint(eye, blockPos);
            float[] aim = ReviveClient.ROTATION.getRotation(eye, point);
            ok = ReviveClient.ROTATION.requestSilentRotation("silent-mine", 350.0, aim[0], aim[1], box);
            rode = ok && ReviveClient.ROTATION.lastSilentRequestRode();
         } else {
            ok = rode = ReviveClient.ROTATION.rideLookHits(box);
         }

         if (!ok) {
            String holder = request
               ? ReviveClient.ROTATION.describeLookHolder(motionSlot)
               : "silent budget spent (" + ReviveClient.ROTATION.describeLookHolder(false) + ")";
            this.noteStopDeferred(blockPos, holder, motionSlot);
            return false;
         } else if (!rode) {
            return true;
         } else {
            AutoCrystal autoCrystal = AutoCrystal.get();
            if (autoCrystal != null && autoCrystal.glassSealOwns(blockPos)) {
               this.dbgLimited(
                  "ride-seal " + blockPos.asLong(),
                  20.0,
                  "stop " + fmt(blockPos) + " doesn't ride the outgoing look: AutoCrystal's glass seal owns it (its combo sends this STOP with the glass)"
               );
               return false;
            } else {
               Vec3d eye = ReviveClient.ROTATION.getRideEye();
               Direction face = rayEntryFace(eye, ReviveClient.ROTATION.getRideYaw(), ReviveClient.ROTATION.getRidePitch(), blockPos);
               if (face != null) {
                  this.lastAimFace = face;
               } else {
                  this.aimPoint(eye, blockPos);
               }

               String owner = ReviveClient.ROTATION.describeLookHolder(true);
               this.dbgLimited(
                  "ride " + blockPos.asLong(),
                  20.0,
                  "stop " + fmt(blockPos) + " rides the outgoing look (" + ("none".equals(owner) ? "the player's own" : owner) + ") face=" + this.lastAimFace
               );
               return true;
            }
         }
      }
   }

   private static Direction rayEntryFace(Vec3d eye, float yaw, float pitch, BlockPos pos) {
      Box box = new Box(pos);
      Optional<Vec3d> clip = box.raycast(eye, eye.add(Vec3d.fromPolar(pitch, yaw).multiply(8.0)));
      if (clip.isEmpty()) {
         return null;
      } else {
         Vec3d rel = clip.get().subtract(box.getCenter());
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

   private void noteStopDeferred(BlockPos pos, String holder, boolean motionSlot) {
      AutoCrystal autoCrystal = motionSlot ? AutoCrystal.get() : null;
      boolean sealed = autoCrystal != null && autoCrystal.glassSealOwns(pos);
      boolean counted = motionSlot && !sealed;
      if (counted && this.lastStopDeferTick != this.tickCount) {
         this.deferredStopTicks = this.lastStopDeferTick == this.tickCount - 1L ? this.deferredStopTicks + 1 : 1;
         this.lastStopDeferTick = this.tickCount;
         this.deferredStopPos = pos.toImmutable();
      }

      this.dbgLimited(
         "defer " + pos.asLong(),
         20.0,
         "stop deferred "
            + fmt(pos)
            + ": aim refused holder="
            + (holder != null ? holder : "?")
            + (counted ? " n=" + this.deferredStopTicks : (sealed ? " (the glass seal owns it: not counted)" : " (silent channel)"))
      );
   }

   private void noteStopSent() {
      this.deferredStopTicks = 0;
      this.lastStopDeferTick = Long.MIN_VALUE;
      this.deferredStopPos = null;
   }

   public int deferredStopTicks() {
      return this.lastStopDeferTick == this.tickCount ? this.deferredStopTicks : 0;
   }

   public BlockPos deferredStopPos() {
      return this.deferredStopTicks() > 0 ? this.deferredStopPos : null;
   }

   private Vec3d aimPoint(Vec3d eye, BlockPos blockPos) {
      BlockHitResult hit = this.visibleHit(eye, blockPos);
      if (hit != null) {
         this.lastAimFace = hit.getSide();
         return hit.getPos();
      } else {
         Box box = this.getBlockBox(blockPos);
         Vec3d point = new Vec3d(
            Math.min(Math.max(eye.x, box.minX), box.maxX), Math.min(Math.max(eye.y, box.minY), box.maxY), Math.min(Math.max(eye.z, box.minZ), box.maxZ)
         );
         Vec3d d = eye.subtract(box.getCenter());
         double ax = Math.abs(d.x);
         double ay = Math.abs(d.y);
         double az = Math.abs(d.z);
         if (ax >= ay && ax >= az) {
            this.lastAimFace = d.x > 0.0 ? Direction.EAST : Direction.WEST;
         } else if (ay >= az) {
            this.lastAimFace = d.y > 0.0 ? Direction.UP : Direction.DOWN;
         } else {
            this.lastAimFace = d.z > 0.0 ? Direction.SOUTH : Direction.NORTH;
         }

         return point;
      }
   }

   private boolean deferMovingAim() {
      if (!this.rotate.get()) {
         return false;
      } else if (this.mc.player != null && !this.mc.player.hasVehicle()) {
         boolean keys = this.mc.player.input.playerInput.forward()
            || this.mc.player.input.playerInput.backward()
            || this.mc.player.input.playerInput.left()
            || this.mc.player.input.playerInput.right();
         boolean standingStill = this.mc.player.isOnGround() && !keys && this.mc.player.getVelocity().horizontalLengthSquared() < 9.0E-6;
         return !standingStill;
      } else {
         return false;
      }
   }

   private boolean silentBudgetSpentThisTick() {
      return ReviveClient.ROTATION.silentSendsThisTick() >= 2;
   }

   private void stageMovingRebreak() {
      Vec3d eye = this.mc.player.getEyePos();
      this.lastAimFace = null;
      Vec3d point = this.aimPoint(eye, this.rebreakBlock.blockPos);
      float[] aim = ReviveClient.ROTATION.getRotation(eye, point);
      Sprint sprint = Sprint.get();
      if (sprint == null || !sprint.isMotionSpoofActive()) {
         if (this.mc.player.isSprinting()) {
            Vec3d vel = this.mc.player.getVelocity();
            if (vel.horizontalLengthSquared() > 0.01) {
               float travelYaw = (float)Math.toDegrees(Math.atan2(vel.z, vel.x)) - 90.0F;
               if (Math.abs(MathHelper.wrapDegrees(aim[0] - travelYaw)) > 35.0F) {
                  return;
               }
            }
         }

         BlockPos pos = this.rebreakBlock.blockPos;
         if (!ReviveClient.ROTATION.requestMotionRotation("silent-mine", aim[0], aim[1], 350.0, true)) {
            String holder = ReviveClient.ROTATION.describeLookHolder(true);
            if (!ReviveClient.ROTATION.outgoingLookHits(new Box(pos))) {
               this.noteStopDeferred(pos, holder, true);
               return;
            }

            AutoCrystal autoCrystal = AutoCrystal.get();
            if (autoCrystal != null && autoCrystal.glassSealOwns(pos)) {
               this.dbgLimited(
                  "ride-seal " + pos.asLong(),
                  20.0,
                  "stop " + fmt(pos) + " doesn't ride the outgoing look: AutoCrystal's glass seal owns it (its combo sends this STOP with the glass)"
               );
               return;
            }
         }

         this.pendingRebreakYaw = aim[0];
         this.pendingRebreakPitch = aim[1];
         this.pendingRebreakFace = this.lastAimFace;
         this.pendingRebreakAttempt = true;
      }
   }

   @EventHandler
   private void onSendMovementPost(SendMovementPacketsEvent.Post event) {
      if (this.pendingRebreakAttempt) {
         this.pendingRebreakAttempt = false;
         if (this.mc.player != null && this.mc.world != null && this.mc.getNetworkHandler() != null) {
            if (this.canRebreakRebreakBlock() && !this.isRebreakPaused(this.rebreakBlock.blockPos) && this.inBreakRange(this.rebreakBlock.blockPos)) {
               if (!this.isOwnSeal(this.rebreakBlock.blockPos) && !this.isStopClaimed(this.rebreakBlock.blockPos)) {
                  BlockPos pos = this.rebreakBlock.blockPos;
                  Vec3d eye = this.mc.player.getEyePos();
                  float yaw = ReviveClient.ROTATION.getServerYaw();
                  float pitch = ReviveClient.ROTATION.getServerPitch();
                  boolean ours = Math.abs(MathHelper.wrapDegrees(yaw - this.pendingRebreakYaw)) <= 1.0F && Math.abs(pitch - this.pendingRebreakPitch) <= 1.0F;
                  if (this.rebreakReady(false)) {
                     if (!ours) {
                        if (!ReviveClient.ROTATION.raytraceCheck(eye, yaw, pitch, new Box(pos))) {
                           this.noteStopDeferred(pos, "the move packet's look (a later aim, or the eye moved off it)", true);
                           return;
                        }

                        AutoCrystal autoCrystal = AutoCrystal.get();
                        if (autoCrystal != null && autoCrystal.glassSealOwns(pos)) {
                           return;
                        }
                     }

                     Direction face = rayEntryFace(eye, yaw, pitch, pos);
                     if (face == null && ours) {
                        this.aimPoint(eye, pos);
                        face = this.lastAimFace;
                     }

                     if (face != null) {
                        this.rebreakBlock.direction = face;
                     } else if (this.pendingRebreakFace != null) {
                        this.rebreakBlock.direction = this.pendingRebreakFace;
                     }

                     if (!ours) {
                        this.dbgLimited("ride " + pos.asLong(), 20.0, "stop " + fmt(pos) + " rides the move packet's look face=" + this.rebreakBlock.direction);
                     }

                     this.executeRebreakAttempt("move");
                  }
               }
            }
         }
      }
   }

   private void executeRebreakAttempt(String path) {
      SilentMine.SilentMineBlock rb = this.rebreakBlock;
      BlockState blockState = this.mc.world.getBlockState(rb.blockPos);
      FindItemResult noDrop = this.noDropRebreakTool(blockState);
      FindItemResult result = noDrop != null ? noDrop : this.stopTool(rb.blockPos, blockState);
      ItemStack stopItem = this.stopItem(result);
      boolean drops = this.breakDrops(stopItem, blockState);
      double score = this.sessionStopScore(result.found() ? result.slot() : this.mc.player.getInventory().selectedSlot, blockState);
      boolean swap = result.found() && this.mc.player.getInventory().selectedSlot != result.slot();
      if (swap && !ReviveClient.SWAP.beginSwap(result, true)) {
         this.logRebreakWait("swap", "STOP deferred — tool swap unavailable this tick");
      } else {
         this.noteStop(rb.blockPos, drops, itemName(stopItem));
         this.logRebreakStop(rb.blockPos, itemName(stopItem), drops, score, path);
         ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Pre(rb.blockPos, true));

         try {
            rb.tryBreak();
         } finally {
            if (swap) {
               ReviveClient.SWAP.endSwap(true);
            }
         }

         rb.stopsSinceReplace++;
         rb.lastRebreakStopTick = this.currentGameTickCalculated;
         if (noDrop != null) {
            this.noteNoDropAttempt(rb.blockPos);
         }

         this.rebreakPacketsInWindow++;
         this.noteStopSent();
         if (this.rebreakSetBlockBroken.get()) {
            this.mc.world.setBlockState(rb.blockPos, Blocks.AIR.getDefaultState());
         }

         ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Sent(rb.blockPos.toImmutable(), true));
      }
   }

   private FindItemResult rebreakTool(BlockPos pos, BlockState state) {
      FindItemResult noDrop = this.noDropRebreakTool(state);
      return noDrop != null ? noDrop : this.stopTool(pos, state);
   }

   private FindItemResult stopTool(BlockPos pos, BlockState state) {
      FindItemResult tool = preferredTool(state);
      return !tool.found() && state != null && !state.isAir() && !state.isToolRequired() && this.noSilkStop(pos) ? this.noToolStopSlot(-1) : tool;
   }

   private boolean noSilkStop(BlockPos pos) {
      if (pos == null) {
         return false;
      } else if (!this.ownSeals.containsKey(pos) && !this.lateSeals.containsKey(pos)) {
         AutoCrystal autoCrystal = AutoCrystal.get();
         return autoCrystal != null && autoCrystal.glassSealOwns(pos);
      } else {
         return true;
      }
   }

   private FindItemResult noToolStopSlot(int preferredSlot) {
      int selected = this.mc.player.getInventory().selectedSlot;
      if (preferredSlot >= 0 && preferredSlot <= 8 && eligibleNoToolItem(this.mc.player.getInventory().getStack(preferredSlot))) {
         return new FindItemResult(preferredSlot, 1);
      } else if (eligibleNoToolItem(this.mc.player.getInventory().getStack(selected))) {
         return new FindItemResult(selected, 1);
      } else {
         for (int i = 0; i < 9; i++) {
            if (i != selected && eligibleNoToolItem(this.mc.player.getInventory().getStack(i))) {
               return new FindItemResult(i, 1);
            }
         }

         return new FindItemResult(-1, 0);
      }
   }

   public static boolean eligibleNoToolItem(ItemStack stack) {
      return stack != null && !Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH) && !stack.contains(DataComponentTypes.PIERCING_WEAPON);
   }

   private boolean rebreakFailureLimitReached() {
      return (this.instantVisual.get() || this.rebreakSetBlockBroken.get()) && this.rebreakBlock.stopsSinceReplace >= 5;
   }

   private boolean rebreakReady(boolean log) {
      return this.rebreakReady(log, this.currentGameTickCalculated);
   }

   private boolean rebreakReady(boolean log, double now) {
      SilentMine.SilentMineBlock rb = this.rebreakBlock;
      if (rb != null && rb.beenAir && this.isSessionPos(rb.blockPos) && BlockUtils.canBreak(rb.blockPos)) {
         BlockState state = this.mc.world.getBlockState(rb.blockPos);
         FindItemResult tool = this.rebreakTool(rb.blockPos, state);
         int slot = tool.found() ? tool.slot() : this.mc.player.getInventory().selectedSlot;
         double penalty = this.serverStopPenalty();
         double score = this.sessionStopScore(slot, state, now);
         double target = rb.rebreakStopTarget(BlockUtils.getBreakDeltaPerTick(slot, state, true, false) * penalty);
         if (score >= target) {
            return true;
         } else {
            if (log) {
               this.logRebreakWait(
                  "ready",
                  "re-placed, not ready: server would score "
                     + String.format("%.2f of %.2f", score, target)
                     + " (tool slot "
                     + slot
                     + (penalty < 1.0 ? String.format(", %s at x%.2f speed", this.serverStopStateName(), penalty) : ", on ground")
                     + ")"
               );
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private double sessionStopScore(int slot, BlockState state) {
      return this.sessionStopScore(slot, state, this.currentGameTickCalculated);
   }

   private double sessionStopScore(int slot, BlockState state, double now) {
      double delta = BlockUtils.getBreakDeltaPerTick(slot, state, true, false) * this.serverStopPenalty();
      double serverElapsed = (now - this.lastStartSentTick) * this.serverTicksPerWallTick();
      return delta * (serverElapsed + 1.0);
   }

   private void logRebreakWait(String key, String msg) {
      if (this.debug.get() && this.rebreakBlock != null) {
         if (!key.equals(this.lastRebreakWaitKey) || !(this.currentGameTickCalculated - this.lastRebreakWaitLogTick < 20.0)) {
            this.lastRebreakWaitKey = key;
            this.lastRebreakWaitLogTick = this.currentGameTickCalculated;
            this.dbg("rebreak " + fmt(this.rebreakBlock.blockPos) + " " + msg);
         }
      }
   }

   private FindItemResult noDropRebreakTool(BlockState state) {
      if (this.rebreakNoDrop.get() && this.canRebreakRebreakBlock() && this.mc.player != null && this.mc.world != null) {
         BlockPos pos = this.rebreakBlock.blockPos;
         if (state != null && !state.isAir() && state.isToolRequired()) {
            BlockPos sessionPos = this.lastStartSentPos;
            if (sessionPos != null && sessionPos.equals(pos)) {
               Double deniedAt = this.noDropDenied.get(pos);
               if (deniedAt != null && this.currentGameTickCalculated - deniedAt <= 200.0) {
                  return null;
               } else {
                  FindItemResult gold = InvUtils.find(Items.GOLDEN_PICKAXE);
                  if (!gold.found() || gold.slot() < 0 || gold.slot() > 35) {
                     return null;
                  } else if (this.mc.player.getInventory().getStack(gold.slot()).isSuitableFor(state)) {
                     return null;
                  } else if (ReviveClient.SWAP.getItemSwapMode() == SwapManager.SwapMode.None && !gold.isMainHand()) {
                     return null;
                  } else {
                     return this.sessionStopScore(gold.slot(), state) < 0.9 ? null : gold;
                  }
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

   private void noteNoDropAttempt(BlockPos pos) {
      if (pos != null) {
         boolean first = this.noDropAttempts.put(pos.toImmutable(), this.currentGameTickCalculated) == null;
         if (first) {
            this.dbg("rebreak " + fmt(pos) + " STOP with golden pickaxe (no drop)");
         }
      }
   }

   private ItemStack stopItem(FindItemResult tool) {
      return tool != null && tool.found() && tool.slot() >= 0 && tool.slot() <= 35
         ? this.mc.player.getInventory().getStack(tool.slot())
         : this.mc.player.getMainHandStack();
   }

   private static String itemName(ItemStack stack) {
      return stack != null && !stack.isEmpty() ? Registries.ITEM.getId(stack.getItem()).getPath() : "hand";
   }

   private boolean breakDrops(ItemStack item, BlockState state) {
      if (state == null || state.isAir() || this.mc.player.getAbilities().creativeMode) {
         return false;
      } else if (state.isToolRequired() && !item.isSuitableFor(state)) {
         return false;
      } else {
         return !state.isOf(Blocks.GLASS) && !(state.getBlock() instanceof StainedGlassBlock) ? true : Utils.hasEnchantment(item, Enchantments.SILK_TOUCH);
      }
   }

   private void noteStop(BlockPos pos, boolean drops, String tool) {
      this.noteStop(pos, drops, tool, -1);
   }

   private void noteStop(BlockPos pos, boolean drops, String tool, int freshAge) {
      Set<Integer> pre = Set.of();
      if (drops && this.mc.world != null) {
         List<ItemEntity> items = this.mc.world.getEntitiesByClass(ItemEntity.class, crystalBox(pos), ex -> !ex.isRemoved() && ex.age > freshAge);
         if (!items.isEmpty()) {
            pre = new HashSet<>();

            for (ItemEntity e : items) {
               pre.add(e.getId());
            }
         }
      }

      this.stopDrops.put(pos.toImmutable(), new SilentMine.StopDrop(drops, tool, gameTick(), pre));
   }

   public boolean expectsDrop(BlockPos pos) {
      if (pos != null && this.mc.world != null && this.mc.player != null) {
         SilentMine.StopDrop drop = this.stopDrops.get(pos);
         if (drop == null) {
            return false;
         } else if (this.dropSettled(pos, drop)) {
            this.stopDrops.remove(pos);
            return false;
         } else {
            return drop.drops;
         }
      } else {
         return false;
      }
   }

   public long stopSentNs(BlockPos pos) {
      SilentMine.StopDrop drop = pos != null ? this.stopDrops.get(pos) : null;
      return drop == null ? Long.MAX_VALUE : drop.sentNs;
   }

   public String describeStop(BlockPos pos) {
      SilentMine.StopDrop drop = pos != null ? this.stopDrops.get(pos) : null;
      return drop == null ? null : drop.tool + (drop.drops ? ",drop" : ",no-drop");
   }

   private boolean dropSettled(BlockPos pos, SilentMine.StopDrop drop) {
      double age = gameTick() - drop.tick;
      if (!drop.drops) {
         return age > 100.0;
      } else if (age > 1200.0) {
         return true;
      } else if (this.itemInCrystalBox(pos, drop)) {
         drop.itemSeen = true;
         return false;
      } else {
         String why = null;
         if (drop.itemSeen) {
            why = "the item left the crystal box";
         } else if (this.mc.world.getBlockState(pos).isAir()) {
            drop.airSeen = true;
         } else if (drop.airSeen) {
            why = "solid again (re-placed, or the STOP rolled back) before an item showed";
         }

         if (why == null && age > this.dropWaitTicks()) {
            why = "no item within " + (int)this.dropWaitTicks() + " ticks";
         }

         if (why == null) {
            return false;
         } else {
            this.dbg("drop at " + fmt(pos) + " settled: " + why);
            return true;
         }
      }
   }

   private boolean itemInCrystalBox(BlockPos pos, SilentMine.StopDrop drop) {
      return !this.mc.world.getEntitiesByClass(ItemEntity.class, crystalBox(pos), e -> !e.isRemoved() && !drop.preItems.contains(e.getId())).isEmpty();
   }

   private static Box crystalBox(BlockPos pos) {
      return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0);
   }

   private double dropWaitTicks() {
      return Math.min(Math.max(PlayerUtils.getPing(), 0), 1000) / 50.0 + 2.0;
   }

   public void claimStop(BlockPos pos) {
      this.claimedStopPos = pos != null ? pos.toImmutable() : null;
   }

   private boolean isStopClaimed(BlockPos pos) {
      return pos != null && pos.equals(this.claimedStopPos);
   }

   public void notePendingSeal(BlockPos pos, Block block, boolean sameFlush, boolean afterStop, boolean behindHit) {
      if (pos != null && block != null) {
         this.lateSeals.remove(pos);
         this.ownSeals.put(pos.toImmutable(), new SilentMine.Seal(block, gameTick(), sameFlush, afterStop, behindHit));
         this.dbg("seal pending " + fmt(pos) + " (" + block.getName().getString() + ")");
      }
   }

   public boolean isOwnSeal(BlockPos pos) {
      SilentMine.Seal s = pos != null ? this.ownSeals.get(pos) : null;
      if (s == null || this.mc.world == null) {
         return false;
      } else if (!s.confirmed) {
         return true;
      } else {
         BlockState state = this.mc.world.getBlockState(pos);
         return state.isOf(s.block) || state.isAir();
      }
   }

   public boolean sealConfirmed(BlockPos pos) {
      SilentMine.Seal s = pos != null ? this.ownSeals.get(pos) : null;
      return s != null && s.confirmed && this.mc.world != null && this.mc.world.getBlockState(pos).isOf(s.block);
   }

   public boolean sealPending(BlockPos pos) {
      SilentMine.Seal s = pos != null ? this.ownSeals.get(pos) : null;
      return s != null && !s.confirmed;
   }

   public boolean isSealPaused(BlockPos pos) {
      Double at = pos != null ? this.refusedSeals.get(pos) : null;
      return at != null && gameTick() - at <= 20.0;
   }

   public Set<BlockPos> ownSealPositions() {
      return this.ownSeals.isEmpty() ? Set.of() : Set.copyOf(this.ownSeals.keySet());
   }

   public Set<BlockPos> standingSealPositions() {
      if (this.ownSeals.isEmpty()) {
         return Set.of();
      } else {
         Set<BlockPos> standing = new HashSet<>();
         this.ownSeals.forEach((pos, s) -> {
            if (!s.opening) {
               standing.add(pos);
            }
         });
         return Set.copyOf(standing);
      }
   }

   private double sealWaitTicks() {
      return this.dropWaitTicks() + 2.0;
   }

   private void onSealState(BlockPos pos, BlockState state) {
      SilentMine.Seal s = this.ownSeals.get(pos);
      String name = state.getBlock().getName().getString();
      if (s == null) {
         SilentMine.Seal late = this.lateSeals.get(pos);
         if (late == null || !state.isOf(late.block)) {
            return;
         }

         this.lateSeals.remove(pos);
         this.refusedSeals.remove(pos);
         this.ownSeals.put(pos.toImmutable(), late);
         s = late;
         this.dbg("seal " + fmt(pos) + " answered late (" + name + ") - ours again, the pause lifted");
      }

      if (state.isOf(s.block)) {
         s.glassTick = gameTick();
         s.stopAirPending = false;
         if (s.opening) {
            s.opening = false;
            this.dbg("seal " + fmt(pos) + " still in after our open STOP (refused) - kept");
         }

         if (!s.confirmed) {
            s.confirmed = true;
            SilentMine.SilentMineBlock b = this.delayedDestroyBlock;
            if (b != null && b.blockPos.equals(pos) && b.started && b.timesSendBreakPacket > 0) {
               b.serverAirConfirmed = true;
               this.dbg("primary " + fmt(pos) + " broken+sealed (" + name + " confirmed)");
            } else {
               SilentMine.SilentMineBlock rb = this.rebreakBlock;
               if (rb != null && rb.beenAir && rb.blockPos.equals(pos)) {
                  rb.stopsSinceReplace = 0;
               }

               this.dbg("seal " + fmt(pos) + " confirmed (" + name + ")");
            }
         }
      } else {
         boolean gone = state.isAir() || state.getBlock() instanceof FluidBlock;
         if (!s.confirmed && gone && s.behindHit) {
            this.dbgLimited(
               "seal-air " + pos.asLong(),
               this.sealWaitTicks(),
               "seal " + fmt(pos) + " pending: air from the crystal we hit (its place / our open) - the glass or the wait answers it"
            );
         } else if (!s.confirmed && gone && s.stopAirPending) {
            s.stopAirPending = false;
            this.dbg("seal " + fmt(pos) + " pending: air from our STOP the tick before (the glass follows)");
         } else {
            this.ownSeals.remove(pos);
            if (s.opening && gone) {
               this.dbg("seal " + fmt(pos) + " opened (our STOP broke it, the crystal goes in behind it)");
            } else if (s.confirmed || !s.sameFlush && !gone) {
               if (!s.confirmed) {
                  this.dbg("seal " + fmt(pos) + " lost the race (" + name + " went in first) - REBREAK SEAL next");
               } else {
                  this.dbg("seal " + fmt(pos) + " lost (" + (gone ? "air" : "replaced by " + name) + ")");
               }
            } else {
               this.refusedSeals.put(pos.toImmutable(), gameTick());
               this.dbg("seal " + fmt(pos) + " refused (the server shows " + name + ") - no seal there for 20 ticks");
            }
         }
      }
   }

   private void tickSeals() {
      double now = gameTick();
      double wait = this.sealWaitTicks();
      this.ownSeals
         .entrySet()
         .removeIf(
            e -> {
               BlockPos pos = e.getKey();
               SilentMine.Seal s = e.getValue();
               BlockState state = this.mc.world.getBlockState(pos);
               if (!s.confirmed) {
                  if (now - s.sentTick <= wait) {
                     return false;
                  } else {
                     this.refusedSeals.put(pos.toImmutable(), now);
                     this.lateSeals.put(pos.toImmutable(), s);
                     this.dbg(
                        "seal " + fmt(pos) + " refused: no " + s.block.getName().getString() + " within " + (int)wait + " ticks - no seal there for 20 ticks"
                     );
                     return true;
                  }
               } else {
                  AutoMine autoMine = AutoMine.get();
                  if (autoMine != null && !autoMine.sealReleased(pos)) {
                     if (s.opening && now - s.openTick > wait) {
                        s.opening = false;
                        if (state.isOf(s.block)) {
                           return false;
                        }
                     }

                     if (!s.opening && !state.isOf(s.block)) {
                        if (state.isAir() && now - s.glassTick <= wait) {
                           return false;
                        } else {
                           this.dbg("seal " + fmt(pos) + " lost (" + (state.isAir() ? "air" : "reads " + state.getBlock().getName().getString()) + ")");
                           return true;
                        }
                     } else {
                        return false;
                     }
                  } else {
                     this.dbg(
                        "seal "
                           + fmt(pos)
                           + " released ("
                           + (autoMine != null && autoMine.isTargetFeetBlock(pos) ? "the target phased into it" : "no target, glass-seal or AutoCrystal off")
                           + "): mined as a normal block"
                     );
                     return true;
                  }
               }
            }
         );
   }

   public SilentMine.StopKind stopKind(BlockPos pos) {
      if (pos != null && this.mc.world != null && this.mc.player != null && this.isRunningHere() && this.isSessionPos(pos)) {
         boolean primary = this.delayedDestroyBlock != null && this.delayedDestroyBlock.blockPos.equals(pos);
         boolean rebreak = !primary && this.canRebreakRebreakBlock() && this.rebreakBlock.blockPos.equals(pos);
         if (!primary && !rebreak) {
            return SilentMine.StopKind.NONE;
         } else if (this.sealConfirmed(pos)) {
            return SilentMine.StopKind.OPEN;
         } else if (this.isOwnSeal(pos)) {
            return SilentMine.StopKind.NONE;
         } else if (primary) {
            return SilentMine.StopKind.PRIMARY;
         } else {
            return BlockUtils.canBreak(pos) ? SilentMine.StopKind.REBREAK : SilentMine.StopKind.NONE;
         }
      } else {
         return SilentMine.StopKind.NONE;
      }
   }

   public SilentMine.StopKind stopDue(BlockPos pos) {
      return this.ticksUntilStopDue(pos, -1) == 0 ? this.stopKind(pos) : SilentMine.StopKind.NONE;
   }

   public int ticksUntilStopDue(BlockPos pos, int slot) {
      SilentMine.StopKind kind = this.stopKind(pos);
      switch (kind) {
         case PRIMARY:
            SilentMine.SilentMineBlock b = this.delayedDestroyBlock;
            if (!this.isPaused() && b.started && !this.primaryRestartPending) {
               if (b.timesSendBreakPacket > 0 && !b.stopRetryDue) {
                  return -1;
               }

               double ttb = b.timesSendBreakPacket > 0
                  ? (b.serverProgress >= b.stopTargetProgress(this.serverStopPenalty()) ? 1 : -1)
                  : this.getPrimaryTicksToBreak();
               if (!(ttb < 0.0) && !(ttb >= 1000.0)) {
                  return (int)Math.max(0.0, ttb - 1.0);
               }

               return -1;
            }

            return -1;
         case REBREAK:
            if (!this.isRebreakPaused(pos) && this.inBreakRange(pos) && !this.playerActionBudgetLow() && !this.rebreakFailureLimitReached()) {
               return this.rebreakReady(false, gameTick()) && this.rebreakBudgetLeft() ? 0 : -1;
            } else {
               return -1;
            }
         case OPEN:
            if (!this.isRebreakPaused(pos) && this.inBreakRange(pos) && !this.playerActionBudgetLow()) {
               b = this.sessionBlock(pos);
               BlockState state = this.mc.world.getBlockState(pos);
               int s = slot >= 0 && slot <= 8 ? slot : this.noToolStopSlot(-1).slot();
               if (b != null && s >= 0) {
                  double delta = BlockUtils.getBreakDeltaPerTick(s, state, true, false) * this.serverStopPenalty();
                  if (delta <= 0.0) {
                     return -1;
                  }

                  double need = b.rebreakStopTarget(delta) / delta - 1.0;
                  double have = (gameTick() - this.lastStartSentTick) * this.serverTicksPerWallTick();
                  return (int)Math.max(0.0, Math.ceil((need - have) / this.serverTicksPerWallTick()));
               }

               return -1;
            }

            return -1;
         default:
            return -1;
      }
   }

   private SilentMine.SilentMineBlock sessionBlock(BlockPos pos) {
      if (this.delayedDestroyBlock != null && this.delayedDestroyBlock.blockPos.equals(pos)) {
         return this.delayedDestroyBlock;
      } else {
         return this.canRebreakRebreakBlock() && this.rebreakBlock.blockPos.equals(pos) ? this.rebreakBlock : null;
      }
   }

   public boolean comboStop(BlockPos pos, int heldSlot, Direction face) {
      if (pos != null && this.mc.player != null && this.mc.world != null && this.connectionOpen()) {
         SilentMine.StopKind kind = this.stopKind(pos);
         SilentMine.SilentMineBlock b = kind == SilentMine.StopKind.NONE ? null : this.sessionBlock(pos);
         String why = null;
         if (kind != SilentMine.StopKind.NONE && b != null) {
            if (kind == SilentMine.StopKind.PRIMARY) {
               if (this.isPaused()
                  || !b.started
                  || this.primaryRestartPending
                  || b.timesSendBreakPacket > 0 && !b.stopRetryDue
                  || b.serverProgress < b.stopTargetProgress(this.serverStopPenalty())) {
                  why = "the primary's STOP isn't due";
               }
            } else if (this.ticksUntilStopDue(pos, heldSlot) != 0) {
               why = kind + " not due";
            }
         } else {
            why = !this.isSessionPos(pos) ? "not the session (newest START at " + fmt(this.lastStartSentPos) + ")" : "no STOP of ours is due there";
         }

         if (why != null) {
            this.dbgLimited("combo-no " + pos.asLong(), 20.0, "combo STOP at " + fmt(pos) + " not sent: " + why);
            return false;
         } else {
            boolean primary = b == this.delayedDestroyBlock;
            BlockState state = primary ? b.effectiveState() : this.mc.world.getBlockState(pos);
            FindItemResult tool = this.comboStopTool(kind, pos, state, heldSlot);
            if (kind == SilentMine.StopKind.OPEN && !tool.found()) {
               this.dbgLimited(
                  "combo-no " + pos.asLong(), 20.0, "combo STOP at " + fmt(pos) + " not sent: no hotbar item without Silk Touch that isn't a spear"
               );
               return false;
            } else {
               int slot = tool.found() ? tool.slot() : this.mc.player.getInventory().selectedSlot;
               ItemStack stopItem = this.stopItem(tool);
               boolean drops = this.breakDrops(stopItem, state);
               double score = this.sessionStopScore(slot, state);
               boolean swap = tool.found() && this.mc.player.getInventory().selectedSlot != tool.slot();
               if (swap && !ReviveClient.SWAP.beginSwap(tool, true)) {
                  this.dbgLimited("combo-no " + pos.asLong(), 20.0, "combo STOP at " + fmt(pos) + " not sent: tool swap unavailable");
                  return false;
               } else {
                  int elapsed = this.elapsedTicks(b);
                  this.noteStop(pos, drops, itemName(stopItem));
                  if (face != null) {
                     b.direction = face;
                  }

                  try {
                     if (primary) {
                        if (b.firstStopElapsed < 0) {
                           b.firstStopElapsed = elapsed;
                        }

                        b.lastStopElapsed = elapsed;
                        b.stopRetryDue = false;
                     }

                     b.tryBreak();
                  } finally {
                     if (swap) {
                        ReviveClient.SWAP.endSwap(true);
                     }
                  }

                  if (!primary) {
                     b.stopsSinceReplace++;
                     b.lastRebreakStopTick = this.currentGameTickCalculated;
                     this.rebreakPacketsInWindow++;
                     if (this.rebreakSetBlockBroken.get()) {
                        this.mc.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                     }
                  }

                  this.noteStopSent();
                  if (kind == SilentMine.StopKind.OPEN) {
                     SilentMine.Seal s = this.ownSeals.get(pos);
                     if (s != null) {
                        s.opening = true;
                        s.openTick = gameTick();
                     }
                  }

                  if (primary) {
                     this.dbg(
                        "primary "
                           + fmt(pos)
                           + " combo STOP ("
                           + kind
                           + ", server progress ~"
                           + String.format("%.2f", b.serverProgress)
                           + " at elapsed "
                           + elapsed
                           + " ticks, drops="
                           + (drops ? "y" : "n")
                           + " ("
                           + itemName(stopItem)
                           + "), path=combo)"
                     );
                  } else {
                     this.logRebreakStop(pos, itemName(stopItem), drops, score, "combo " + kind);
                  }

                  ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Sent(pos.toImmutable(), !primary));
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   private FindItemResult comboStopTool(SilentMine.StopKind kind, BlockPos pos, BlockState state, int heldSlot) {
      if (kind == SilentMine.StopKind.OPEN && !preferredTool(state).found()) {
         return this.noToolStopSlot(heldSlot);
      } else {
         return kind == SilentMine.StopKind.REBREAK ? this.rebreakTool(pos, state) : this.stopTool(pos, state);
      }
   }

   public int comboStopClicks(BlockPos pos, int heldSlot) {
      SilentMine.StopKind kind = this.stopKind(pos);
      SilentMine.SilentMineBlock b = kind == SilentMine.StopKind.NONE ? null : this.sessionBlock(pos);
      if (b == null) {
         return 0;
      } else {
         BlockState state = b == this.delayedDestroyBlock ? b.effectiveState() : this.mc.world.getBlockState(pos);
         FindItemResult tool = this.comboStopTool(kind, pos, state, heldSlot);
         if (tool.found() && tool.slot() != this.mc.player.getInventory().selectedSlot) {
            SwapManager.SwapMode mode = ReviveClient.SWAP.getItemSwapMode();

            boolean container = switch (mode) {
               case SilentSwap -> true;
               case Auto -> !tool.isHotbar()
                  || ReviveClient.SWAP.isMultiTickSwapActive()
                  || !this.isPacketMode() && this.hasDelayedDestroy()
                  || this.mc.player.isUsingItem() && this.mc.player.getActiveHand() == Hand.MAIN_HAND;
               default -> false;
            };
            return container ? 2 : 0;
         } else {
            return 0;
         }
      }
   }

   private void logRebreakStop(BlockPos pos, String tool, boolean drops, double score, String path) {
      if (this.debug.get()) {
         this.dbgLimited(
            "stop " + pos.asLong(),
            1.0,
            "rebreak STOP "
               + fmt(pos)
               + " tool="
               + tool
               + " drops="
               + (drops ? "y" : "n")
               + " age="
               + (int)(gameTick() - this.lastStartSentTick)
               + " score="
               + String.format("%.2f", score)
               + " path="
               + path
         );
      }
   }

   private void dbgLimited(String key, double ticks, String msg) {
      if (this.debug.get()) {
         double now = gameTick();
         Double last = this.dbgLimitTicks.get(key);
         if (last == null || !(now - last < ticks)) {
            if (this.dbgLimitTicks.size() > 64) {
               this.dbgLimitTicks.clear();
            }

            this.dbgLimitTicks.put(key, now);
            this.dbg(msg);
         }
      }
   }

   public Vec3d serverEye() {
      RotationManager rot = ReviveClient.ROTATION;
      return rot.lastX == 0.0 && rot.lastY == 0.0 && rot.lastZ == 0.0
         ? this.mc.player.getEyePos()
         : new Vec3d(rot.lastX, rot.lastY + this.mc.player.getEyeHeight(this.mc.player.getPose()), rot.lastZ);
   }

   private BlockHitResult visibleHit(Vec3d eye, BlockPos pos) {
      Box box = this.getBlockBox(pos);
      if (box.contains(eye)) {
         return null;
      } else {
         for (Direction face : Direction.values()) {
            if (facesEye(face, box, eye)) {
               BlockHitResult hit = this.castTo(eye, facePoint(box, face, 0.5, 0.5), face, pos);
               if (hit != null) {
                  return hit;
               }
            }
         }

         BlockHitResult center = this.castTo(eye, box.getCenter(), null, pos);
         if (center != null) {
            return center;
         } else {
            for (Direction facex : Direction.values()) {
               if (facesEye(facex, box, eye)) {
                  for (double u : CORNER_FRACTIONS) {
                     for (double v : CORNER_FRACTIONS) {
                        BlockHitResult hit = this.castTo(eye, facePoint(box, facex, u, v), facex, pos);
                        if (hit != null) {
                           return hit;
                        }
                     }
                  }
               }
            }

            return null;
         }
      }
   }

   private static boolean facesEye(Direction face, Box box, Vec3d eye) {
      return switch (face) {
         case EAST -> eye.x > box.maxX;
         case WEST -> eye.x < box.minX;
         case UP -> eye.y > box.maxY;
         case DOWN -> eye.y < box.minY;
         case SOUTH -> eye.z > box.maxZ;
         case NORTH -> eye.z < box.minZ;
         default -> throw new MatchException(null, null);
      };
   }

   private static Vec3d facePoint(Box b, Direction face, double u, double v) {
      double f = face.getDirection() == AxisDirection.POSITIVE ? 1.0 : 0.0;

      return switch (face.getAxis()) {
         case X -> boxPoint(b, f, u, v);
         case Y -> boxPoint(b, u, f, v);
         case Z -> boxPoint(b, u, v, f);
         default -> throw new MatchException(null, null);
      };
   }

   private static Vec3d boxPoint(Box b, double fx, double fy, double fz) {
      return new Vec3d(b.minX + (b.maxX - b.minX) * fx, b.minY + (b.maxY - b.minY) * fy, b.minZ + (b.maxZ - b.minZ) * fz);
   }

   private BlockHitResult castTo(Vec3d eye, Vec3d point, Direction face, BlockPos pos) {
      Vec3d target = face == null ? point : point.subtract(face.getOffsetX() * 0.01, face.getOffsetY() * 0.01, face.getOffsetZ() * 0.01);
      BlockHitResult hit = this.mc.world.raycast(new RaycastContext(eye, target, ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
      return hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
   }

   public boolean alreadyBreaking(BlockPos blockPos) {
      return this.rebreakBlock != null && blockPos.equals(this.rebreakBlock.blockPos)
         || this.delayedDestroyBlock != null && blockPos.equals(this.delayedDestroyBlock.blockPos)
         || this.parkedBlock != null && blockPos.equals(this.parkedBlock.blockPos);
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      this.tickInstantRebreakRender();
      if (this.render.get() && this.mc.world != null && this.mc.player != null) {
         double tick = gameTick();
         if (this.canRebreakRebreakBlock() && this.rebreakRender.get()) {
            this.renderRebreakBlock(event, this.rebreakBlock.blockPos);
         }

         double primaryProg = this.delayedDestroyBlock != null ? this.delayedDestroyBlock.renderProgress(tick) : 0.0;
         if (this.delayedDestroyBlock != null) {
            this.renderMineBlock(event, this.delayedDestroyBlock.blockPos, primaryProg);
         }

         if (this.parkedBlock != null && !this.mc.world.getBlockState(this.parkedBlock.blockPos).isAir()) {
            int finish = this.parkFinishTicks(this.parkedBlock);
            double p = finish <= 0 ? 1.0 : (tick - this.parkedBlock.destroyProgressStart) / finish;
            this.renderMineBlock(event, this.parkedBlock.blockPos, Math.max(0.0, Math.min(p, 1.0)));
         }

         if (this.hasRebreakBlock()) {
            double own = Math.min(this.rebreakBlock.getBreakProgress(tick), 1.0);
            this.renderMineBlock(event, this.rebreakBlock.blockPos, this.delayedDestroyBlock != null ? Math.min(own, primaryProg) : own);
         }

         for (SilentMine.QueuedBlock q : this.queuedBlocks.values()) {
            if (!this.alreadyBreaking(q.blockPos) && !this.mc.world.getBlockState(q.blockPos).isAir()) {
               this.renderSides(event, q.blockPos, this.startColor.get(), this.renderFillAlpha.get());
            }
         }

         for (SilentMine.QueuedBlock qx : this.activeQueuedBlocks.values()) {
            if (!this.alreadyBreaking(qx.blockPos) && !this.mc.world.getBlockState(qx.blockPos).isAir()) {
               this.renderSides(event, qx.blockPos, this.startColor.get(), this.renderFillAlpha.get());
            }
         }
      }
   }

   private static JsonArray fxBlock(int kind, BlockPos pos, double progress, double perTick) {
      JsonArray a = PbFx.pos(pos);
      a.add(kind);
      a.add(Math.round(Math.max(0.0, Math.min(progress, 1.0)) * 1000.0) / 1000.0);
      a.add(Math.round(Math.max(0.0, perTick) * 10000.0) / 10000.0);
      return a;
   }

   @Override
   public JsonObject writeRemoteRender() {
      if (this.render.get() && this.mc.world != null && this.mc.player != null) {
         double tick = gameTick();
         JsonArray blocks = new JsonArray();
         if (this.canRebreakRebreakBlock() && this.rebreakRender.get()) {
            blocks.add(fxBlock(3, this.rebreakBlock.blockPos, 0.0, 0.0));
         }

         double primaryProg = this.delayedDestroyBlock != null ? this.delayedDestroyBlock.renderProgress(tick) : 0.0;
         if (this.delayedDestroyBlock != null) {
            blocks.add(fxBlock(0, this.delayedDestroyBlock.blockPos, primaryProg, 0.0));
         }

         if (this.parkedBlock != null && !this.mc.world.getBlockState(this.parkedBlock.blockPos).isAir()) {
            int finish = this.parkFinishTicks(this.parkedBlock);
            double p = finish <= 0 ? 1.0 : (tick - this.parkedBlock.destroyProgressStart) / finish;
            blocks.add(fxBlock(1, this.parkedBlock.blockPos, p, finish <= 0 ? 0.0 : 1.0 / finish));
         }

         if (this.hasRebreakBlock()) {
            double own = Math.min(this.rebreakBlock.getBreakProgress(tick), 1.0);
            double shown = this.delayedDestroyBlock != null ? Math.min(own, primaryProg) : own;
            double perTick = this.delayedDestroyBlock != null ? 0.0 : Math.min(this.rebreakBlock.getBreakProgress(tick + 1.0), 1.0) - own;
            blocks.add(fxBlock(2, this.rebreakBlock.blockPos, shown, perTick));
         }

         for (SilentMine.QueuedBlock q : this.queuedBlocks.values()) {
            if (blocks.size() >= 40) {
               break;
            }

            if (!this.alreadyBreaking(q.blockPos) && !this.mc.world.getBlockState(q.blockPos).isAir()) {
               blocks.add(fxBlock(4, q.blockPos, 0.0, 0.0));
            }
         }

         for (SilentMine.QueuedBlock q : this.activeQueuedBlocks.values()) {
            if (blocks.size() >= 40) {
               break;
            }

            if (!this.alreadyBreaking(q.blockPos) && !this.mc.world.getBlockState(q.blockPos).isAir()) {
               blocks.add(fxBlock(4, q.blockPos, 0.0, 0.0));
            }
         }

         if (blocks.isEmpty()) {
            return null;
         } else {
            JsonObject state = new JsonObject();
            state.add("b", blocks);
            return state;
         }
      } else {
         return null;
      }
   }

   @Override
   public void readRemoteRender(JsonObject state, PbFx.Clock clock) {
      this.remoteBlocks.clear();
      this.remoteBlocksTick = gameTick();
      JsonElement list = state.get("b");
      if (list != null && list.isJsonArray()) {
         for (JsonElement e : list.getAsJsonArray()) {
            if (e.isJsonArray() && e.getAsJsonArray().size() >= 6) {
               JsonArray a = e.getAsJsonArray();
               this.remoteBlocks
                  .add(
                     new double[]{
                        a.get(3).getAsInt(), a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(4).getAsDouble(), a.get(5).getAsDouble()
                     }
                  );
            }
         }
      }
   }

   @Override
   public void drawRemote3D(Render3DEvent event) {
      if (this.render.get() && this.mc.world != null && !this.remoteBlocks.isEmpty()) {
         double ticks = Math.max(0.0, Math.min(gameTick() - this.remoteBlocksTick, 20.0));

         for (double[] b : this.remoteBlocks) {
            BlockPos pos = new BlockPos((int)b[1], (int)b[2], (int)b[3]);
            switch ((int)b[0]) {
               case 3:
                  this.renderRebreakBlock(event, pos);
                  break;
               case 4:
                  if (!this.mc.world.getBlockState(pos).isAir()) {
                     this.renderSides(event, pos, this.startColor.get(), this.renderFillAlpha.get());
                  }
                  break;
               default:
                  this.renderMineBlock(event, pos, Math.max(0.0, Math.min(b[4] + b[5] * ticks, 1.0)));
            }
         }
      }
   }

   @Override
   public void clearRemote() {
      this.remoteBlocks.clear();
   }

   private static Color withA(Color c, int a) {
      return new Color(c.r, c.g, c.b, a);
   }

   private Box getBlockBox(BlockPos pos) {
      VoxelShape shape = this.mc.world.getBlockState(pos).getOutlineShape(this.mc.world, pos);
      return shape != null && !shape.isEmpty() ? shape.getBoundingBox().offset(pos.getX(), pos.getY(), pos.getZ()) : new Box(pos);
   }

   private void renderSides(Render3DEvent event, BlockPos pos, Color color, int alpha) {
      if (alpha > 0) {
         event.renderer.box(this.getBlockBox(pos), withA(color, alpha), TRANSPARENT, ShapeMode.Sides, 0);
      }
   }

   private void renderMineBlock(Render3DEvent event, BlockPos pos, double progress) {
      Box anim = this.applyAnimation(this.getBlockBox(pos), progress);
      Color color = lerpColor(this.startColor.get(), this.endColor.get(), progress);
      event.renderer.box(anim, withA(color, this.renderFillAlpha.get()), TRANSPARENT, ShapeMode.Sides, 0);
   }

   private void renderRebreakBlock(Render3DEvent event, BlockPos pos) {
      this.renderSides(event, pos, this.endColor.get(), this.rebreakFillAlpha.get());
   }

   private Box applyAnimation(Box box, double progress) {
      return switch ((SilentMine.RenderAnimation)this.renderAnimation.get()) {
         case None -> box;
         case Grow -> scaleBox(box, progress);
         case Shrink -> scaleBox(box, 1.0 - progress);
         case Smooth -> {
            double t = progress < 0.5 ? 4.0 * progress * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 3.0) / 2.0;
            yield scaleBox(box, t);
         }
      };
   }

   private static Box scaleBox(Box box, double scale) {
      if (scale >= 1.0) {
         return box;
      } else {
         if (scale <= 0.0) {
            scale = 0.001;
         }

         double cx = (box.minX + box.maxX) / 2.0;
         double cy = (box.minY + box.maxY) / 2.0;
         double cz = (box.minZ + box.maxZ) / 2.0;
         double hw = (box.maxX - box.minX) / 2.0 * scale;
         double hh = (box.maxY - box.minY) / 2.0 * scale;
         double hd = (box.maxZ - box.minZ) / 2.0 * scale;
         return new Box(cx - hw, cy - hh, cz - hd, cx + hw, cy + hh, cz + hd);
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof BlockUpdateS2CPacket packet) {
         if (this.onServerBlockState(packet.getPos().toImmutable(), packet.getState(), true)) {
            event.setCancelled(true);
         }
      } else if (event.packet instanceof PlaySoundS2CPacket packetx && packetx.getSound() != null) {
         BlockPos pos = BlockPos.ofFloored(packetx.getX(), packetx.getY(), packetx.getZ());
         SilentMine.SilentMineBlock rb = this.rebreakBlock;
         if ((pos.equals(this.getDelayedDestroyBlockPos()) || pos.equals(this.getParkedPos()) || rb != null && rb.beenAir && rb.blockPos.equals(pos))
            && packetx.getX() - pos.getX() == 0.5
            && packetx.getY() - pos.getY() == 0.5
            && packetx.getZ() - pos.getZ() == 0.5) {
            this.recentPlaceSounds
               .put(pos.toImmutable(), new SilentMine.PlaceSound(this.currentGameTickCalculated, ((SoundEvent)packetx.getSound().value()).id()));
         }
      } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packetxx) {
         packetxx.visitUpdates(
            (pos, state) -> {
               SilentMine.SilentMineBlock rbx = this.rebreakBlock;
               if (state.isAir()
                  || state.getBlock() instanceof FluidBlock
                  || rbx != null && rbx.beenAir && rbx.blockPos.equals(pos)
                  || this.ownSeals.containsKey(pos)
                  || this.lateSeals.containsKey(pos)) {
                  this.onServerBlockState(pos.toImmutable(), state, false);
               }
            }
         );
      }
   }

   private boolean onServerBlockState(BlockPos pos, BlockState state, boolean canSuppress) {
      boolean suppressed = false;
      if (this.predictedBroken.containsKey(pos)) {
         if (state.isAir()) {
            this.predictedBroken.remove(pos);
         } else if (canSuppress) {
            suppressed = true;
         }
      }

      boolean gone = state.isAir() || state.getBlock() instanceof FluidBlock;
      Double noDropAt = this.noDropAttempts.get(pos);
      if (noDropAt != null) {
         if (gone || this.placeSoundHeard(pos, state, noDropAt)) {
            this.noDropAttempts.remove(pos);
         } else if (!this.isInteractResend(pos)) {
            this.noDropAttempts.remove(pos);
            this.noDropDenied.put(pos, this.currentGameTickCalculated);
            String refusedBlock = state.getBlock().getName().getString();
            this.mc
               .execute(
                  () -> this.dbg("rebreak " + fmt(pos) + " golden-pickaxe STOP refused (re-synced " + refusedBlock + ") - fastest tool for the next 200 ticks")
               );
         }
      }

      if (this.pendingInstantFinish.containsKey(pos)) {
         String verdictBlock = state.getBlock().getName().getString();
         this.mc.execute(() -> {
            if (this.pendingInstantFinish.remove(pos) != null) {
               if (!gone) {
                  this.deniedInstantFinish.put(pos, this.currentGameTickCalculated);
                  this.dbg("instant-finish " + fmt(pos) + " REJECTED (server re-synced " + verdictBlock + ") — dug as a normal session next");
               }
            }
         });
      }

      if (gone) {
         this.mc.execute(() -> {
            if (this.delayedDestroyBlock != null && this.delayedDestroyBlock.blockPos.equals(pos)) {
               this.delayedDestroyBlock.serverAirConfirmed = true;
            }

            if (this.parkedBlock != null && this.parkedBlock.blockPos.equals(pos)) {
               this.parkedBlock.serverAirConfirmed = true;
            }
         });
      }

      SilentMine.Seal seal = this.ownSeals.get(pos);
      if (seal == null) {
         SilentMine.Seal late = this.lateSeals.get(pos);
         if (late != null && state.isOf(late.block)) {
            seal = late;
         }
      }

      if (seal != null) {
         this.mc.execute(() -> this.onSealState(pos, state));
      }

      boolean sealGlass = seal != null && state.isOf(seal.block);
      boolean isPrimary = pos.equals(this.getDelayedDestroyBlockPos());
      boolean tracked = isPrimary || this.rebreakBlock != null && pos.equals(this.rebreakBlock.blockPos);
      if (tracked && !sealGlass) {
         boolean interactResend = !gone && this.isInteractResend(pos);
         boolean armedRebreak = !isPrimary && this.rebreakBlock != null && this.rebreakBlock.beenAir;
         if (this.debug.get() && (!armedRebreak || !gone)) {
            String verdict;
            if (state.isAir()) {
               verdict = "air (break confirmed)";
            } else if (gone) {
               verdict = state.getBlock().getName().getString() + " (liquid flooded the hole — break confirmed)";
            } else if (interactResend) {
               verdict = state.getBlock().getName().getString()
                  + (
                     armedRebreak
                        ? " (re-placed or re-sent after our use-item-on — armed rebreak, STOP count kept without a place sound)"
                        : " (re-sent after our use-item-on — ignored without a place sound after our STOP)"
                  );
            } else if (armedRebreak) {
               verdict = state.getBlock().getName().getString() + " (re-placed or STOP refused — armed rebreak)";
            } else {
               verdict = state.getBlock().getName().getString() + " (server re-synced the block — break REJECTED" + (suppressed ? ", hidden" : "") + ")";
            }

            this.mc.execute(() -> this.dbg("server block update @ " + fmt(pos) + " -> " + verdict));
         }

         if (armedRebreak) {
            this.mc.execute(() -> {
               SilentMine.SilentMineBlock rb = this.rebreakBlock;
               if (rb != null && rb.beenAir && rb.blockPos.equals(pos)) {
                  boolean replaced = !gone && this.placeSoundHeard(pos, state, rb.lastRebreakStopTick);
                  if (!gone && !interactResend && rb.stopsSinceReplace > 0 && !replaced) {
                     this.dbg("rebreak " + fmt(pos) + " STOP refused (re-sync, no place sound) — " + rb.stopsSinceReplace + "/5");
                  } else {
                     if (!gone || rb.stopsSinceReplace > 0) {
                        rb.lastReplaceTick = this.currentGameTickCalculated;
                     }

                     if (!interactResend || replaced) {
                        if (gone && rb.stopsSinceReplace > 0) {
                           this.dbg("rebreak " + fmt(pos) + " broken by server (air confirmed after " + rb.stopsSinceReplace + " STOP(s))");
                        } else if (interactResend && rb.stopsSinceReplace > 0) {
                           this.dbg("rebreak " + fmt(pos) + " re-placed after our STOP (place sound heard) — STOP count reset");
                        }

                        rb.stopsSinceReplace = 0;
                     }
                  }
               }
            });
         }

         if (isPrimary && !gone) {
            boolean predictedResync = suppressed;
            this.mc
               .execute(
                  () -> {
                     SilentMine.SilentMineBlock b = this.delayedDestroyBlock;
                     if (b != null && b.blockPos.equals(pos)) {
                        if (b.started && !this.primaryRestartPending) {
                           if (!predictedResync || b.timesSendBreakPacket != 0) {
                              boolean replaced = b.timesSendBreakPacket > 0
                                 && this.isSessionPos(pos)
                                 && this.placeSoundHeard(pos, state, b.destroyProgressStart + b.lastStopElapsed);
                              if (!interactResend || replaced) {
                                 if (replaced) {
                                    b.serverAirConfirmed = true;
                                    this.dbg("primary " + fmt(pos) + " broken and re-placed before the server's update (place sound heard) — treated as broken");
                                 } else if (b.timesSendBreakPacket > 0 && b.resyncStopRetries < 2) {
                                    b.resyncStopRetries++;
                                    b.stopRetryDue = true;
                                    this.dbg(
                                       "primary " + fmt(pos) + " re-synced solid after our STOP — STOP-only retry " + b.resyncStopRetries + "/2 (session kept)"
                                    );
                                 } else if (b.timesSendBreakPacket > 0) {
                                    this.primaryRestartPending = true;
                                    this.primaryRestartAtTick = this.currentGameTickCalculated + this.restartDelayTicks();
                                    this.dbg(
                                       "primary "
                                          + fmt(pos)
                                          + " STOP refused (re-sync after 2 STOP-only retries) — restart in ~"
                                          + this.restartDelayTicks()
                                          + " ticks"
                                    );
                                 } else if (b.serverProgress >= 0.8) {
                                    double ripe = Math.max(1.1, b.stopTargetProgress(this.serverStopPenalty()));
                                    if (b.serverProgress < ripe) {
                                       b.serverProgress = ripe;
                                       this.dbg("primary " + fmt(pos) + " re-sync on a ripe session — sending the STOP now");
                                    }
                                 } else if (!b.earlyReopenDone) {
                                    b.earlyReopenDone = true;
                                    this.primaryRestartPending = true;
                                    this.primaryRestartAtTick = this.currentGameTickCalculated + this.restartDelayTicks();
                                    this.dbg("primary " + fmt(pos) + " opening START refused (re-sync) — re-open in ~" + this.restartDelayTicks() + " ticks");
                                 }
                              }
                           }
                        }
                     }
                  }
               );
         }

         return suppressed;
      } else {
         return suppressed;
      }
   }

   @EventHandler
   private void onPacket(PacketEvent.Send event) {
      if (event.packet instanceof UpdateSelectedSlotC2SPacket
         && !this.packetMode.get()
         && this.hasDelayedDestroy()
         && this.delayedDestroyBlock.serverProgress > 0.0) {
         this.delayedDestroyBlock.serverProgress = 0.0;
         this.delayedDestroyBlock.timeoutBaseTick = this.currentGameTickCalculated;
         this.delayedDestroyBlock.lastAccrualTick = this.currentGameTickCalculated;
         this.dbg("primary " + fmt(this.delayedDestroyBlock.blockPos) + " held slot changed — server progress estimate reset");
      }

      if (event.packet instanceof PlayerInteractBlockC2SPacket interact && interact.getBlockHitResult() != null) {
         BlockPos clicked = interact.getBlockHitResult().getBlockPos().toImmutable();
         this.recentInteractResends.put(clicked, this.currentGameTickCalculated);
         this.recentInteractResends.put(clicked.offset(interact.getBlockHitResult().getSide()).toImmutable(), this.currentGameTickCalculated);
      }

      if (event.packet instanceof PlayerActionC2SPacket packet) {
         if (packet.getAction() != Action.START_DESTROY_BLOCK || this.mc.world != null && this.mc.world.isOutOfHeightLimit(packet.getPos().getY())) {
            if (packet.getAction() == Action.ABORT_DESTROY_BLOCK && this.lastStartSentPos != null && !packet.getPos().equals(this.lastStartSentPos)) {
               this.lastStartSentPos = null;
            }
         } else {
            this.lastStartSentPos = packet.getPos().toImmutable();
            this.lastStartSentTick = gameTick();
         }

         if (this.debug.get()
            && (packet.getPos().equals(this.getDelayedDestroyBlockPos()) || this.rebreakBlock != null && packet.getPos().equals(this.rebreakBlock.blockPos))) {
            this.dbg("send " + packet.getAction() + " @ " + fmt(packet.getPos()) + " face=" + packet.getDirection());
         }
      }
   }

   private boolean canInstantFinish(BlockState state) {
      if (this.mc.player != null && this.mc.world != null && !state.isAir()) {
         FindItemResult tool = InvUtils.findFastestToolHotbar(state);
         int slot = tool.found() ? tool.slot() : this.mc.player.getInventory().selectedSlot;
         return BlockUtils.getBreakDeltaPerTick(slot, state, this.mc.player.isOnGround(), this.mc.player.isSubmergedIn(FluidTags.WATER)) >= 1.0;
      } else {
         return false;
      }
   }

   public boolean isHideSwitchActive() {
      return this.hideSwitch.get() && this.isActive() && this.mc.player != null && this.needDelayedDestroySwapBack;
   }

   public ItemStack getHideSwitchStack() {
      return this.isHideSwitchActive() ? this.capturedOriginal : null;
   }

   public int getVisualSelectedSlot() {
      if (this.isHideSwitchActive() && this.capturedSlot >= 0) {
         return this.capturedSlot;
      } else {
         return this.mc.player != null ? this.mc.player.getInventory().selectedSlot : 0;
      }
   }

   public ItemStack getVisualHotbarStack(int slot) {
      if (this.mc.player == null) {
         return ItemStack.EMPTY;
      } else {
         if (this.isHideSwitchActive() && slot == this.getVisualSelectedSlot() && this.capturedOriginal != null && !this.capturedOriginal.isEmpty()) {
            ItemStack live = this.mc.player.getInventory().getStack(slot);
            if (live.getItem() != this.capturedOriginal.getItem()) {
               return this.capturedOriginal;
            }
         }

         return this.mc.player.getInventory().getStack(slot);
      }
   }

   private int getSeq() {
      return ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
   }

   private boolean connectionOpen() {
      return this.mc.getNetworkHandler() != null && this.mc.getNetworkHandler().getConnection().isOpen();
   }

   private static Color lerpColor(Color a, Color b, double t) {
      t = Math.max(0.0, Math.min(1.0, t));
      return new Color(
         (int)Math.round(a.r + (b.r - a.r) * t),
         (int)Math.round(a.g + (b.g - a.g) * t),
         (int)Math.round(a.b + (b.b - a.b) * t),
         (int)Math.round(a.a + (b.a - a.a) * t)
      );
   }

   public boolean isQueued(BlockPos pos) {
      return pos != null && (this.queuedBlocks.containsKey(pos) || this.activeQueuedBlocks.containsKey(pos));
   }

   private boolean passesQueueFilter(BlockState state) {
      if (state != null && !state.isAir()) {
         SilentMine.BlockWhitelistMode mode = this.queueingMode.get();
         if (mode == SilentMine.BlockWhitelistMode.All) {
            return true;
         } else {
            List<Block> blocks = this.queueingBlocks.get();
            if (blocks != null && !blocks.isEmpty()) {
               boolean contains = blocks.contains(state.getBlock());
               return mode == SilentMine.BlockWhitelistMode.Whitelist ? contains : !contains;
            } else {
               return mode != SilentMine.BlockWhitelistMode.Whitelist;
            }
         }
      } else {
         return false;
      }
   }

   private void enqueueBreak(BlockPos blockPos, Direction direction, double priority) {
      if (this.mc.world != null && this.mc.player != null && blockPos != null) {
         if (BlockUtils.canBreak(blockPos, this.mc.world.getBlockState(blockPos))) {
            if (this.inBreakRange(blockPos)) {
               if (!this.recentlyRangeCancelled(blockPos)) {
                  BlockPos immutable = blockPos.toImmutable();
                  if (!this.alreadyBreaking(immutable) && !this.queuedBlocks.containsKey(immutable) && !this.activeQueuedBlocks.containsKey(immutable)) {
                     this.queuedBlocks.put(immutable, new SilentMine.QueuedBlock(immutable, direction, priority));
                  }
               }
            }
         }
      }
   }

   private void processQueue() {
      if (!this.queueing.get()) {
         if (!this.queuedBlocks.isEmpty() || !this.activeQueuedBlocks.isEmpty()) {
            this.clearQueue();
         }
      } else if (this.mc.world != null) {
         this.cleanupActiveQueuedBlocks();
         if (!this.queuedBlocks.isEmpty()) {
            if (!this.hasDelayedDestroy()) {
               if (!this.digCooldownActive()) {
                  Iterator<Entry<BlockPos, SilentMine.QueuedBlock>> it = this.queuedBlocks.entrySet().iterator();

                  while (it.hasNext()) {
                     SilentMine.QueuedBlock queued = it.next().getValue();
                     BlockState state = this.mc.world.getBlockState(queued.blockPos);
                     if (!state.isAir() && BlockUtils.canBreak(queued.blockPos, state) && this.inBreakRange(queued.blockPos)) {
                        if (!this.recentlyRangeCancelled(queued.blockPos)) {
                           it.remove();
                           this.activeQueuedBlocks.put(queued.blockPos, queued);
                           this.silentBreakBlock(queued.blockPos, queued.direction, queued.priority);
                           if (this.alreadyBreaking(queued.blockPos)) {
                              if (this.hasDelayedDestroy()) {
                                 break;
                              }
                           } else {
                              this.activeQueuedBlocks.remove(queued.blockPos);
                           }
                        }
                     } else {
                        it.remove();
                     }
                  }
               }
            }
         }
      }
   }

   private SilentMine.QueuedBlock pollNextReadyQueuedBlock() {
      if (this.mc.world == null) {
         return null;
      } else {
         Iterator<Entry<BlockPos, SilentMine.QueuedBlock>> it = this.queuedBlocks.entrySet().iterator();

         while (it.hasNext()) {
            SilentMine.QueuedBlock q = it.next().getValue();
            BlockState state = this.mc.world.getBlockState(q.blockPos);
            if (state.isAir() || !BlockUtils.canBreak(q.blockPos, state) || !this.inBreakRange(q.blockPos)) {
               it.remove();
            } else if (!this.recentlyRangeCancelled(q.blockPos)) {
               if (!this.alreadyBreaking(q.blockPos) && !this.activeQueuedBlocks.containsKey(q.blockPos)) {
                  it.remove();
                  return q;
               }

               it.remove();
            }
         }

         return null;
      }
   }

   private void cleanupActiveQueuedBlocks() {
      Iterator<Entry<BlockPos, SilentMine.QueuedBlock>> it = this.activeQueuedBlocks.entrySet().iterator();

      while (it.hasNext()) {
         Entry<BlockPos, SilentMine.QueuedBlock> entry = it.next();
         BlockPos pos = entry.getKey();
         SilentMine.QueuedBlock queued = entry.getValue();
         if (!this.alreadyBreaking(pos)) {
            it.remove();
            BlockState state = this.mc.world.getBlockState(pos);
            if (!state.isAir() && BlockUtils.canBreak(pos, state)) {
               this.queuedBlocks.put(pos, queued);
            }
         }
      }
   }

   private void clearQueue() {
      this.queuedBlocks.clear();
      this.activeQueuedBlocks.clear();
   }

   private void abortDoubleMine() {
      if (this.hasRebreakBlock()) {
         this.clearRebreak("double-mine abort key");
      }
   }

   private void abortRebreak() {
      if (this.canRebreakRebreakBlock()) {
         this.clearRebreak("rebreak abort key");
      }
   }

   private boolean useAggressiveRebreak() {
      SilentMine.RebreakMode mode = this.rebreakMode.get();
      if (mode == SilentMine.RebreakMode.Aggressive) {
         return true;
      } else {
         return mode == SilentMine.RebreakMode.Auto ? this.feetPhasedInBlock() : false;
      }
   }

   private boolean feetPhasedInBlock() {
      if (this.mc.player != null && this.mc.world != null) {
         Box box = this.mc.player.getBoundingBox().expand(-0.001);
         Box feet = new Box(box.minX, box.minY, box.minZ, box.maxX, Math.min(box.minY + 0.5, box.maxY), box.maxZ);
         BlockPos min = BlockPos.ofFloored(feet.minX, feet.minY, feet.minZ);
         BlockPos max = BlockPos.ofFloored(feet.maxX, feet.maxY, feet.maxZ);

         for (BlockPos pos : BlockPos.iterate(min, max)) {
            VoxelShape shape = this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos);
            if (!shape.isEmpty()) {
               for (Box shapeBox : shape.getBoundingBoxes()) {
                  if (shapeBox.offset(pos.getX(), pos.getY(), pos.getZ()).intersects(feet)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isEatingOrDrinking() {
      if (this.mc.player != null && this.mc.player.isUsingItem()) {
         ItemStack using = this.mc.player.getActiveItem();
         return using != null && !using.isEmpty() ? using.get(DataComponentTypes.FOOD) != null : false;
      } else {
         return false;
      }
   }

   private void tickInstantRebreakRender() {
      if (this.isActive() && this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         if (!this.isRebreakPaused(this.getRebreakBlockPos()) && this.useAggressiveRebreak()) {
            if (this.canRebreakRebreakBlock() && this.inBreakRange(this.rebreakBlock.blockPos)) {
               if (!this.isOwnSeal(this.rebreakBlock.blockPos) && !this.isStopClaimed(this.rebreakBlock.blockPos)) {
                  AutoCrystal autoCrystal = AutoCrystal.get();
                  if (autoCrystal == null || !autoCrystal.glassSealOwns(this.rebreakBlock.blockPos)) {
                     if (!this.rebreakFailureLimitReached()) {
                        if (!this.deferMovingAim()) {
                           if (!this.silentBudgetSpentThisTick()) {
                              if (!this.playerActionBudgetLow()) {
                                 long now = System.currentTimeMillis();
                                 long interval = 50L;
                                 if (this.isEatingOrDrinking()) {
                                    interval += 75L;
                                 }

                                 if (this.lastInstantRebreakMs == -1L || now - this.lastInstantRebreakMs >= interval) {
                                    if (this.rebreakReady(false)) {
                                       if (this.aimAt(this.rebreakBlock.blockPos)) {
                                          if (this.lastAimFace != null) {
                                             this.rebreakBlock.direction = this.lastAimFace;
                                          }

                                          this.lastInstantRebreakMs = now;
                                          SilentMine.SilentMineBlock rb = this.rebreakBlock;
                                          BlockState state = this.mc.world.getBlockState(rb.blockPos);
                                          FindItemResult noDrop = this.noDropRebreakTool(state);
                                          FindItemResult tool = noDrop != null ? noDrop : this.stopTool(rb.blockPos, state);
                                          ItemStack stopItem = this.stopItem(tool);
                                          boolean drops = this.breakDrops(stopItem, state);
                                          double score = this.sessionStopScore(tool.found() ? tool.slot() : this.mc.player.getInventory().selectedSlot, state);
                                          boolean didSwap = tool.found() && this.mc.player.getInventory().selectedSlot != tool.slot();
                                          if (didSwap && !ReviveClient.SWAP.beginSwap(tool, true)) {
                                             this.logRebreakWait("swap", "STOP deferred — tool swap unavailable this frame");
                                          } else {
                                             this.noteStop(rb.blockPos, drops, itemName(stopItem));
                                             this.logRebreakStop(rb.blockPos, itemName(stopItem), drops, score, "render");
                                             ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Pre(rb.blockPos, true));

                                             try {
                                                rb.tryBreak();
                                                rb.stopsSinceReplace++;
                                                rb.lastRebreakStopTick = this.currentGameTickCalculated;
                                                if (noDrop != null) {
                                                   this.noteNoDropAttempt(rb.blockPos);
                                                }

                                                if (this.rebreakSetBlockBroken.get()) {
                                                   this.mc.world.setBlockState(rb.blockPos, Blocks.AIR.getDefaultState());
                                                }
                                             } finally {
                                                if (didSwap) {
                                                   ReviveClient.SWAP.endSwap(true);
                                                }
                                             }

                                             this.noteStopSent();
                                             ReviveClient.EVENT_BUS.post(new SilentMineFinishedEvent.Sent(rb.blockPos.toImmutable(), true));
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

   public static enum BlockWhitelistMode {
      Whitelist,
      Blacklist,
      All;
   }

   private record PlaceSound(double tick, Identifier id) {
   }

   private static class QueuedBlock {
      final BlockPos blockPos;
      final Direction direction;
      final double priority;

      QueuedBlock(BlockPos blockPos, Direction direction, double priority) {
         this.blockPos = blockPos;
         this.direction = direction;
         this.priority = priority;
      }
   }

   public static enum RebreakMode {
      Auto,
      Instant,
      Aggressive;
   }

   public static enum RenderAnimation {
      None,
      Grow,
      Shrink,
      Smooth;
   }

   private static final class Seal {
      final Block block;
      final double sentTick;
      final boolean sameFlush;
      boolean stopAirPending;
      final boolean behindHit;
      boolean confirmed;
      double glassTick;
      boolean opening;
      double openTick;

      Seal(Block block, double sentTick, boolean sameFlush, boolean afterStop, boolean behindHit) {
         this.block = block;
         this.sentTick = sentTick;
         this.sameFlush = sameFlush;
         this.stopAirPending = afterStop;
         this.behindHit = behindHit;
         this.glassTick = sentTick;
      }
   }

   class SilentMineBlock {
      public BlockPos blockPos;
      public Direction direction;
      public boolean started = false;
      public int timesSendBreakPacket = 0;
      public int ticksHeldPickaxe = 0;
      public boolean beenAir = false;
      private double destroyProgressStart = 0.0;
      private double priority = 0.0;
      private boolean isRebreak;
      public BlockState initialState = null;
      public boolean clientPredicted = false;
      public boolean stopAirPredicted = false;
      public volatile boolean serverAirConfirmed = false;
      public boolean bypassedDelay = false;
      public int stopsSinceReplace = 0;
      public double lastRebreakStopTick = -1.0;
      public double lastReplaceTick = 0.0;
      public double lastInRangeTick = 0.0;
      public int resyncStopRetries = 0;
      public boolean stopRetryDue = false;
      public double serverProgress = 0.0;
      public int firstStopElapsed = -1;
      public int lastStopElapsed = -1000;
      public double timeoutBaseTick = 0.0;
      public boolean earlyReopenDone = false;
      private boolean tpsDippedThisSession = false;
      public double lastAccrualTick = -1.0;

      public SilentMineBlock(BlockPos blockPos, Direction direction, double priority, boolean isRebreak) {
         this.blockPos = blockPos;
         this.direction = direction;
         this.priority = priority;
         this.isRebreak = isRebreak;
         this.destroyProgressStart = SilentMine.this.currentGameTickCalculated;
         this.timeoutBaseTick = SilentMine.this.currentGameTickCalculated;
         this.lastReplaceTick = SilentMine.this.currentGameTickCalculated;
         this.lastInRangeTick = SilentMine.this.currentGameTickCalculated;
         this.initialState = SilentMine.this.mc.world != null ? SilentMine.this.mc.world.getBlockState(blockPos) : null;
      }

      boolean locallyCleared() {
         return this.clientPredicted || this.stopAirPredicted;
      }

      int verdictBaseElapsed() {
         return this.resyncStopRetries > 0 ? this.lastStopElapsed : this.firstStopElapsed;
      }

      private BlockState effectiveState() {
         BlockState live = SilentMine.this.mc.world.getBlockState(this.blockPos);
         return this.locallyCleared() && live.isAir() && this.initialState != null && !this.initialState.isAir() ? this.initialState : live;
      }

      double rebreakStopTarget(double delta) {
         double margin = SilentMine.this.grimBypass.get() && this.bypassedDelay ? delta : Math.max(0.04, delta * 1.5);
         return 0.7 + margin;
      }

      public void startBreaking(boolean isDelayedDestroy) {
         if (this.stopAirPredicted && SilentMine.this.mc.world.getBlockState(this.blockPos).isAir() && this.initialState != null && !this.initialState.isAir()) {
            SilentMine.this.mc.world.setBlockState(this.blockPos, this.initialState);
         }

         this.stopAirPredicted = false;
         this.serverAirConfirmed = false;
         this.ticksHeldPickaxe = 0;
         this.timesSendBreakPacket = 0;
         this.bypassedDelay = false;
         this.serverProgress = 0.0;
         this.firstStopElapsed = -1;
         this.lastStopElapsed = -1000;
         this.resyncStopRetries = 0;
         this.stopRetryDue = false;
         this.destroyProgressStart = SilentMine.this.currentGameTickCalculated;
         this.timeoutBaseTick = SilentMine.this.currentGameTickCalculated;
         this.lastAccrualTick = SilentMine.this.currentGameTickCalculated;
         if (isDelayedDestroy && SilentMine.this.canRebreakRebreakBlock()) {
            SilentMine.this.clearRebreak("new session at " + SilentMine.fmt(this.blockPos) + " (prio " + (int)this.priority + ")");
         }

         SilentMine.this.aimAt(this.blockPos);
         if (SilentMine.this.lastAimFace != null) {
            this.direction = SilentMine.this.lastAimFace;
         }

         if (SilentMine.this.connectionOpen()) {
            FindItemResult startTool = SilentMine.this.stopTool(this.blockPos, this.effectiveState());
            boolean flashed = startTool.found()
               && SilentMine.this.mc.player.getInventory().selectedSlot != startTool.slot()
               && ReviveClient.SWAP.beginSwap(startTool, true);

            try {
               SilentMine.this.mc
                  .getNetworkHandler()
                  .sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, this.blockPos, this.direction, SilentMine.this.getSeq()));
               if (SilentMine.this.grimBypass.get()) {
                  SilentMine.this.mc
                     .getNetworkHandler()
                     .sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, this.blockPos.up(955), this.direction, SilentMine.this.getSeq()));
               }

               SilentMine.this.sendSwing();
            } finally {
               if (flashed) {
                  ReviveClient.SWAP.endSwap(true);
               }
            }
         }

         this.started = true;
      }

      public void tryBreak() {
         if (SilentMine.this.connectionOpen()) {
            if (SilentMine.this.instantVisual.get() && SilentMine.this.mc.interactionManager != null && SilentMine.this.mc.world != null) {
               BlockPos p = this.blockPos;
               Direction d = this.direction;
               boolean wasSolid = !SilentMine.this.mc.world.getBlockState(p).isAir();
               ((ClientPlayerInteractionManagerAccessor)SilentMine.this.mc.interactionManager)
                  .invokeSendSequencedPacket(SilentMine.this.mc.world, sequence -> {
                     SilentMine.this.mc.world.setBlockState(p, Blocks.AIR.getDefaultState());
                     return new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, p, d, sequence);
                  });
               if (!this.isRebreak && wasSolid) {
                  this.stopAirPredicted = true;
               }
            } else {
               SilentMine.this.mc
                  .getNetworkHandler()
                  .sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction, SilentMine.this.getSeq()));
            }

            SilentMine.this.sendSwing();
            this.timesSendBreakPacket++;
         }
      }

      public void cancelBreaking() {
         if (SilentMine.this.connectionOpen() && this.started && this.blockPos.equals(SilentMine.this.lastStartSentPos)) {
            SilentMine.this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, this.blockPos, this.direction));
         }
      }

      public void sendParkStop() {
         if (SilentMine.this.connectionOpen()) {
            SilentMine.this.mc
               .getNetworkHandler()
               .sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, this.blockPos, this.direction, SilentMine.this.getSeq()));
            SilentMine.this.sendSwing();
         }
      }

      public void sendDelayBypassBurst() {
         if (SilentMine.this.connectionOpen()) {
            BlockPos bypassPos = this.blockPos.up(955);
            int n = SilentMine.this.grimBypassBurst.get();

            for (int i = 0; i < n; i++) {
               SilentMine.this.mc
                  .getNetworkHandler()
                  .sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, bypassPos, this.direction, SilentMine.this.getSeq()));
            }

            this.bypassedDelay = true;
         }
      }

      public double getBreakProgress() {
         return this.getBreakProgress(SilentMine.this.currentGameTickCalculated);
      }

      public double getBreakProgress(double gameTick) {
         BlockState state = this.effectiveState();
         FindItemResult bestTool = InvUtils.findFastestToolHotbar(state);
         int toolSlot = bestTool.found() ? bestTool.slot() : SilentMine.this.mc.player.getInventory().selectedSlot;
         Box boundingBox = SilentMine.this.mc.player.getBoundingBox();
         double playerFeetY = boundingBox.minY;
         Box groundBox = new Box(boundingBox.minX, playerFeetY - 0.2, boundingBox.minZ, boundingBox.maxX, playerFeetY, boundingBox.maxZ);
         boolean willBeOnGround = false;

         for (BlockPos pos : BlockUtils.iterate(groundBox)) {
            BlockState blockState = SilentMine.this.mc.world.getBlockState(pos);
            if (blockState.isSolidBlock(SilentMine.this.mc.world, pos)) {
               double blockTopY = pos.getY() + 1.0;
               double distanceToBlock = playerFeetY - blockTopY;
               if (distanceToBlock >= 0.0 && distanceToBlock < Math.abs(SilentMine.this.mc.player.getVelocity().y * 2.0)) {
                  willBeOnGround = true;
               }
            }
         }

         double delta = BlockUtils.getBreakDeltaPerTick(
            toolSlot, state, RotationManager.lastGround || willBeOnGround && !this.isRebreak, SilentMine.this.mc.player.isSubmergedIn(FluidTags.WATER)
         );
         return Math.min(delta / SilentMine.this.speedMultiplier() * (gameTick - this.destroyProgressStart), 1.0);
      }

      double renderProgress(double gameTick) {
         if (!this.started) {
            return 0.0;
         } else if (this.timesSendBreakPacket > 0) {
            return 1.0;
         } else {
            double target = this.stopTargetProgress(SilentMine.this.serverStopPenalty());
            if (target <= 0.0) {
               return 0.0;
            } else {
               double since = this.lastAccrualTick < 0.0 ? 0.0 : Math.max(0.0, Math.min(gameTick - this.lastAccrualTick, 1.0));
               return Math.max(0.0, Math.min((this.serverProgress + this.accrualPerWallTick() * since) / target, 1.0));
            }
         }
      }

      private double scoringDelta(BlockState state) {
         return state.isAir() ? 0.0 : BlockUtils.getBreakDeltaPerTick(this.scoringSlot(state), state, true, false);
      }

      double accrualPerWallTick() {
         return this.scoringDelta(this.effectiveState()) * SilentMine.this.serverTicksPerWallTick();
      }

      public void tickServerProgress() {
         double now = SilentMine.this.currentGameTickCalculated;
         double dt = this.lastAccrualTick < 0.0 ? 1.0 : Math.min(now - this.lastAccrualTick, 2.0);
         this.lastAccrualTick = now;
         if (!(dt <= 0.0)) {
            BlockState state = this.effectiveState();
            if (!state.isAir()) {
               float tps = TickRate.INSTANCE.getTickRate();
               if (tps > 0.0F && tps < 19.5F) {
                  this.tpsDippedThisSession = true;
               }

               double delta = this.scoringDelta(state);
               if (!(delta <= 0.0)) {
                  this.serverProgress = this.serverProgress + delta * SilentMine.this.serverTicksPerWallTick() * dt;
               }
            }
         }
      }

      public double stopTargetProgress() {
         return this.stopTargetProgress(1.0);
      }

      public double stopTargetProgress(double penalty) {
         double base = Math.max(0.7, Math.min(1.0, SilentMine.this.speedMultiplier()));
         boolean bypassed = SilentMine.this.grimBypass.get() && this.bypassedDelay;
         if (bypassed) {
            base = Math.min(base, 0.7);
         }

         double delta = this.scoringDelta(this.effectiveState());
         if (delta <= 0.0) {
            return (base + 0.10000000000000009) / penalty;
         } else {
            delta *= penalty;
            double margin = bypassed ? delta : Math.max(0.04, delta * 1.5);
            if (this.tpsDippedThisSession) {
               margin = Math.max(margin, 0.10000000000000009);
            }

            return (base + margin) / penalty;
         }
      }

      public int heldTicksNeeded() {
         BlockState state = this.effectiveState();
         if (state.isAir()) {
            return 0;
         } else {
            FindItemResult tool = InvUtils.findFastestToolHotbar(state);
            int toolSlot = tool.found() ? tool.slot() : SilentMine.this.mc.player.getInventory().selectedSlot;
            double delta = BlockUtils.getBreakDeltaPerTick(toolSlot, state, true, false);
            return delta <= 0.0 ? 1000 : (int)Math.ceil(1.1 / (delta * SilentMine.this.serverTicksPerWallTick())) + 1;
         }
      }

      public double getPriority() {
         return this.priority;
      }

      private int scoringSlot(BlockState state) {
         if (SilentMine.this.packetMode.get()) {
            FindItemResult tool = SilentMine.preferredTool(state);
            if (tool.found()) {
               return tool.slot();
            }
         }

         return SilentMine.this.mc.player.getInventory().selectedSlot;
      }
   }

   private static final class StopDrop {
      final boolean drops;
      final String tool;
      final double tick;
      final long sentNs = System.nanoTime();
      boolean airSeen;
      boolean itemSeen;
      final Set<Integer> preItems;

      StopDrop(boolean drops, String tool, double tick, Set<Integer> preItems) {
         this.drops = drops;
         this.tool = tool;
         this.tick = tick;
         this.preItems = preItems;
      }
   }

   public static enum StopKind {
      NONE,
      PRIMARY,
      REBREAK,
      OPEN;
   }
}
