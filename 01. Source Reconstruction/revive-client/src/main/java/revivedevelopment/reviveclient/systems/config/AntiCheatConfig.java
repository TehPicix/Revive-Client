package revivedevelopment.reviveclient.systems.config;

import meteordevelopment.orbit.EventHandler;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.managers.RotationManager;
import revivedevelopment.reviveclient.systems.managers.SwapManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;

public class AntiCheatConfig extends Module {
   private final SettingGroup sgRotations = this.settings.createGroup("Rotations");
   private final SettingGroup sgBlockPlacement = this.settings.createGroup("Block Placement");
   private final SettingGroup sgSwap = this.settings.createGroup("Swap");
   private final SettingGroup sgMultiTask = this.settings.createGroup("Multi Task");
   private final SettingGroup sgNetwork = this.settings.createGroup("Network");
   private final SettingGroup sgPacketLimits = this.settings.createGroup("Packet Limits");
   private final SettingGroup sgPacketLimiter = this.settings.createGroup("Packet Limiter");
   private final SettingGroup sgRotationPriority = this.settings.createGroup("Rotation Priority");
   private final SettingGroup sgLatency = this.settings.createGroup("Latency");
   private final SettingGroup sgPrediction = this.settings.createGroup("Prediction");
   public final Setting<Boolean> tickSync = this.sgRotations
      .add(new BoolSetting.Builder().name("tick-sync").description("Rotate every tick.").defaultValue(true).build());
   public final Setting<Boolean> grimSync = this.sgRotations
      .add(
         new BoolSetting.Builder()
            .name("grim-sync")
            .description("Send full movement packet every tick.")
            .defaultValue(false)
            .visible(() -> this.tickSync.get())
            .build()
      );
   public final Setting<Boolean> grimRotation = this.sgRotations
      .add(
         new BoolSetting.Builder()
            .name("grim-rotation")
            .description("Send full movement packet when look changes.")
            .defaultValue(true)
            .visible(() -> this.tickSync.get())
            .build()
      );
   public final Setting<Boolean> grimSnapRotation = this.sgRotations
      .add(new BoolSetting.Builder().name("grim-snap-rotation").description("Send full movement packet when snapping rotation.").defaultValue(true).build());
   public final Setting<Boolean> gcdRotation = this.sgRotations
      .add(
         new BoolSetting.Builder()
            .name("gcd-rotation")
            .description(
               "Snap silent look-packet rotations to a per-session mouse GCD (with dot-jitter) so aim deltas look like discrete mouse movement to Grim's aim-GCD check. Off = the default direct snap with micro-jitter."
            )
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> rotationSmooth = this.sgRotations
      .add(new BoolSetting.Builder().name("smooth").description("Ease rotations toward the target instead of snapping instantly.").defaultValue(false).build());
   public final Setting<Double> smoothYawStep = this.sgRotations
      .add(
         new DoubleSetting.Builder()
            .name("smooth-yaw-step")
            .description("Max yaw change per tick when smoothing.")
            .defaultValue(22.0)
            .min(1.0)
            .sliderRange(1.0, 180.0)
            .visible(() -> this.rotationSmooth.get())
            .build()
      );
   public final Setting<Double> smoothPitchStep = this.sgRotations
      .add(
         new DoubleSetting.Builder()
            .name("smooth-pitch-step")
            .description("Max pitch change per tick when smoothing.")
            .defaultValue(10.0)
            .min(1.0)
            .sliderRange(1.0, 90.0)
            .visible(() -> this.rotationSmooth.get())
            .build()
      );
   public final Setting<Boolean> blockRotatePlace = this.sgBlockPlacement
      .add(new BoolSetting.Builder().name("block-rotate-place").description("Rotate to place blocks.").defaultValue(false).build());
   public final Setting<Boolean> blockPlaceAirPlace = this.sgBlockPlacement
      .add(
         new BoolSetting.Builder()
            .name("grim-air-place")
            .description("Allow air placing blocks using offhand swap exploit (for Grim servers).")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> mainHandAirPlace = this.sgBlockPlacement
      .add(
         new BoolSetting.Builder()
            .name("main-hand-air-place")
            .description("Allow air placing blocks using vanilla interaction (works on most servers).")
            .defaultValue(true)
            .visible(() -> !this.blockPlaceAirPlace.get())
            .build()
      );
   public final Setting<Boolean> forceAirPlace = this.sgBlockPlacement
      .add(
         new BoolSetting.Builder()
            .name("force-air-place")
            .description("Only air place blocks.")
            .defaultValue(false)
            .visible(() -> this.blockPlaceAirPlace.get() || this.mainHandAirPlace.get())
            .build()
      );
   public final Setting<Double> blockPlacePerBlockCooldown = this.sgBlockPlacement
      .add(
         new DoubleSetting.Builder()
            .name("block-place-cooldown")
            .description("Cooldown before retrying same position.")
            .defaultValue(0.05)
            .min(0.0)
            .sliderMax(0.3)
            .build()
      );
   public final Setting<Boolean> swapAntiScreenClose = this.sgSwap
      .add(new BoolSetting.Builder().name("anti-screen-close").description("Pause swapping when screens are open.").defaultValue(true).build());
   public final Setting<SwapManager.SwapMode> swapMode = this.sgSwap
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("item-swap-mode")).description("How to swap items."))
               .defaultValue(SwapManager.SwapMode.Auto))
            .build()
      );
   public final Setting<Boolean> grimSilentSwapFix = this.sgSwap
      .add(
         new BoolSetting.Builder()
            .name("grim-silent-swap-fix")
            .description("Cancel the server's desync slot-update so silent swaps don't snap back (Grim).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> swapDebug = this.sgSwap
      .add(new BoolSetting.Builder().name("swap-debug").description("Log SwapManager begin/end swaps to chat (diagnostics).").defaultValue(false).build());
   public final Setting<ElytraDurabilitySaver.Mode> elytraSaverMode = this.sgSwap
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("elytra-saver-mode"))
                  .description(
                     "Flash (default): the chestplate stays on server-side for the whole flight. Each time the server clears the glide, the elytra is clicked into the chest slot, the glide command goes out with a jump input, and about 300 ms later the chestplate is clicked back - the elytra is never worn server-side for more than a handful of consecutive glide ticks, so it never wears (durability ticks on the 20th). Shaped around Grim's rules: one command per clear, no clicks while keys/jump/sprint are known down (the server is told 'no keys, not sprinting' while it runs), clicks spaced so its five-tick slot re-checks find client and server in step. Windows: the older closed/open window cycling (partial saving, setbacks on 2b2t)."
                  ))
               .defaultValue(ElytraDurabilitySaver.Mode.Flash))
            .build()
      );
   public final Setting<ElytraDurabilitySaver.Click> elytraSaverClick = this.sgSwap
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("elytra-saver-click"))
                     .description(
                        "Flash mode: Swap uses one hotbar-swap click each way (2 clicks a cycle); Pickup uses three pick-up clicks each way like the reference implementation (6 clicks a cycle)."
                     ))
                  .defaultValue(ElytraDurabilitySaver.Click.Swap))
               .visible(() -> this.elytraSaverMode.get() == ElytraDurabilitySaver.Mode.Flash))
            .build()
      );
   public final Setting<Integer> elytraSaverGlideGap;
   public final Setting<Boolean> elytraSaverBounceHops = this.sgSwap
      .add(
         new BoolSetting.Builder()
            .name("elytra-saver-bounce-hops")
            .description(
               "Flash mode with ElytraFly's Bounce (motion-y-boost off): the chestplate stays on server-side across the hops, and each take-off's glide command carries the elytra in and straight back out, so the glides never wear it. The keys and sprint are handed to the saver after the second airborne tick of each hop and handed back the moment you land, so the jump and the ground run are predicted with your real input. The anticheat may log one experimental duplicate-input flag per hop. Off: Bounce is left alone."
            )
            .defaultValue(true)
            .visible(() -> this.elytraSaverMode.get() == ElytraDurabilitySaver.Mode.Flash)
            .build()
      );
   public final Setting<Boolean> elytraSaverAllFlights;
   public final Setting<Boolean> elytraSaverSkipBaritone;
   public final Setting<Boolean> elytraSaverWrapRockets = this.sgSwap
      .add(
         new BoolSetting.Builder()
            .name("elytra-saver-wrap-rockets")
            .description(
               "BepHax-style rockets: instead of handing the elytra back for a whole burn (a real server-side boost, ~1 durability per rocket), each rocket is fired INSIDE a flash cycle - elytra clicked in, glide command, the rocket use right behind it (consumed and spawned while the server glides for that instant), chestplate back with the next movement packet. The elytra is worn server-side for at most a tick or two per rocket (~0.05 durability), the held pongs keep the anticheat counting the glide, and the live rocket opens its firework uncertainty window, so the burn speed comes from the client (vanilla's own boost prediction, or RocketBoost riding the window - turn RocketBoost on for full speed). The server never boosts: without RocketBoost the climb of a wrapped rocket is the client's prediction only, and a rocket can only be wrapped while a cycle is due (a request between cycles waits a moment). Off: the saver disengages for every burn and the rocket boosts server-side at full durability cost."
            )
            .defaultValue(true)
            .visible(() -> this.elytraSaverMode.get() == ElytraDurabilitySaver.Mode.Flash)
            .build()
      );
   public final Setting<ElytraDurabilitySaver.Setbacks> elytraSaverSetbacks;
   public final Setting<Integer> elytraSaverClosedTicks;
   public final Setting<Integer> elytraSaverOpenTicks;
   public final Setting<MultiTask.PhaseMode> phaseMode;
   public final Setting<MultiTask.PearlMode> pearlMode;
   public final Setting<MultiTask.KillAuraMode> killAuraMode;
   public final Setting<AntiCheatConfig.PacketPreset> globalPreset;
   public final Setting<Integer> globalLimit;
   public final Setting<Double> globalTiming;
   public final Setting<AntiCheatConfig.PacketPreset> offhandPreset;
   public final Setting<Integer> offhandLimit;
   public final Setting<Double> offhandTiming;
   public final Setting<AntiCheatConfig.PacketPreset> clickSlotPreset;
   public final Setting<Integer> clickSlotLimit;
   public final Setting<Double> clickSlotTiming;
   public final Setting<Double> blockInteractRange;
   public final Setting<Integer> blockInteractLimit;
   public final Setting<Integer> blockInteractTimeframe;
   public final Setting<Boolean> packetLimiter;
   public final Setting<Integer> limiterMaxPps;
   public final Setting<Integer> limiterCombatPps;
   public final Setting<Double> limiterWindow;
   public final Setting<Boolean> limiterAuto;
   public final Setting<Double> limiterPressure;
   public final Setting<Boolean> limiterDebug;
   public final Setting<RotationManager.RotationPriority> killAuraPriority;
   public final Setting<RotationManager.RotationPriority> crystalAuraPriority;
   public final Setting<RotationManager.RotationPriority> anchorAuraPriority;
   public final Setting<RotationManager.RotationPriority> surroundPriority;
   public final Setting<RotationManager.RotationPriority> sprintPriority;
   public final Setting<RotationManager.RotationPriority> speedPriority;
   public final Setting<RotationManager.RotationPriority> pearlPriority;
   public final Setting<RotationManager.RotationPriority> autoExpPriority;
   public final Setting<RotationManager.RotationPriority> elytraPriority;
   public final Setting<AntiCheatConfig.LatencyMode> latencyMode;
   public final Setting<Integer> fastLatency;
   public final Setting<Boolean> predictionEnabled;
   public final Setting<Integer> predictionTicks;
   public final Setting<Integer> elytraTicks;
   public final Setting<Boolean> groundRaycast;
   private volatile int sentThisSecond;
   private volatile int recvThisSecond;
   private volatile int sentLastSecond;
   private volatile int recvLastSecond;
   private long windowStartMs;

   public AntiCheatConfig() {
      super(Categories.Gui, "anti-cheat", "Shared anti-cheat / bypass settings (packets, rotations, swap, placement).");
      this.elytraSaverAllFlights = this.sgSwap
         .add(
            new BoolSetting.Builder()
               .name("elytra-saver-all-flights")
               .description(
                  "Cover every glide, not only flights driven by a module with its save-durability setting on: flying by hand, Baritone, rocket travel with middle-click. Rockets still cost their burn (a boost only exists while the elytra is worn and gliding server-side); everything between burns is saved. Middle-click rockets (MiddleClickExtra) wait out the saver's swap automatically; a rocket right-clicked straight from the hand while the chestplate is on server-side is wasted, and the saver stands down so the next one boosts. Off: only the flight modules' own save-durability settings bring the saver in."
               )
               .defaultValue(true)
               .build()
         );
      this.elytraSaverSkipBaritone = this.sgSwap
         .add(
            new BoolSetting.Builder()
               .name("elytra-saver-skip-baritone")
               .description(
                  "Stand the saver down while Baritone's elytra process is flying (overworld or nether). Baritone steers from its own simulation of every rocket and glide tick; the swap cycles, wrapped rockets (no server-side boost) and delayed rockets put the server's copy out of step with it, which shows as rubberbanding. On: Baritone flights wear the elytra normally but fly clean. Off: the saver covers them too."
               )
               .defaultValue(true)
               .build()
         );
      this.elytraSaverGlideGap = this.sgSwap
         .add(
            new IntSetting.Builder()
               .name("elytra-saver-glide-gap")
               .description(
                  "Flash mode: ticks the chestplate stays on server-side after each glide cycle before the next glide command. The chestplate goes back in the same tick as each command, so the server never ticks with the elytra on and nothing wears at all; the anticheat keeps predicting a glide through the gap (its transaction pongs are held until the next command). Longer gaps mean fewer clicks. A gap ends early when you fly into a chunk the server sent during it (the anticheat would reject every move there until it is acknowledged). Longer gaps delay the anticheat's view of world changes around you by that much; keep it short in combat. While flash mode is engaged (or resuming the glide after it), the Velocity module leaves your own velocity packets alone: the anticheat re-sends its setbacks until their velocity is applied as sent. In any glide the saver covers, your own velocity packets bypass the Velocity module and apply as vanilla would: the server sends one on every tick of a rocket burn and one behind each anticheat setback, and each one the Velocity module cancels (its Grim mode cancels one every 100 ms) buys a setback - a visible jolt every few ticks through a rocket climb (the rocket flutter), and a setback whose velocity is not taken is re-sent every tick at no violation cost. Flash mode never engages while the anticheat is nudging a flight hard (a dozen position packets in two seconds: Bounce, or a running setback chain), so Bounce is left alone."
               )
               .defaultValue(20)
               .min(0)
               .max(60)
               .sliderRange(0, 60)
               .visible(() -> this.elytraSaverMode.get() == ElytraDurabilitySaver.Mode.Flash)
               .build()
         );
      this.elytraSaverSetbacks = this.sgSwap
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("elytra-saver-setbacks"))
                        .description(
                           "While the chestplate is on, 2b2t sends a position packet every tick that drags you toward a falling player's path and carries that player's velocity. Keep velocity: your glide speed is kept, the small position nudge is accepted (the flight jitters, altitude erodes). Keep velocity and position: the nudge is confirmed but not applied, so the client flies its own path; a correction of 4 blocks or more is still applied."
                        ))
                     .defaultValue(ElytraDurabilitySaver.Setbacks.KeepVelocity))
                  .visible(() -> this.elytraSaverMode.get() == ElytraDurabilitySaver.Mode.Windows))
               .build()
         );
      this.elytraSaverClosedTicks = this.sgSwap
         .add(
            new IntSetting.Builder()
               .name("elytra-saver-closed-ticks")
               .description(
                  "Elytra durability saver (the save-durability setting on the flight modules): how many ticks the server sees a chestplate worn and the wings closed per cycle. Durability is only used while the wings are open server-side, so longer is cheaper; vanilla kicks after 80 airborne ticks without a glide, so 70 is the ceiling."
               )
               .defaultValue(50)
               .min(10)
               .max(70)
               .sliderRange(10, 70)
               .visible(() -> this.elytraSaverMode.get() == ElytraDurabilitySaver.Mode.Windows)
               .build()
         );
      this.elytraSaverOpenTicks = this.sgSwap
         .add(
            new IntSetting.Builder()
               .name("elytra-saver-open-ticks")
               .description(
                  "Elytra durability saver: ticks the wings stay open server-side per cycle once the server confirmed the re-open. A few ticks are enough to reset the server's flight check; rockets open the wings on demand regardless."
               )
               .defaultValue(4)
               .min(2)
               .max(20)
               .sliderRange(2, 20)
               .visible(() -> this.elytraSaverMode.get() == ElytraDurabilitySaver.Mode.Windows)
               .build()
         );
      this.phaseMode = this.sgMultiTask
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("phase-mode"))
                     .description("How the Phase module brings a pearl into a hand before throwing."))
                  .defaultValue(MultiTask.PhaseMode.Normal))
               .build()
         );
      this.pearlMode = this.sgMultiTask
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("pearl-mode"))
                     .description("Shared swap mode for pearl-throwing features."))
                  .defaultValue(MultiTask.PearlMode.AltSilent))
               .build()
         );
      this.killAuraMode = this.sgMultiTask
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("kill-aura-mode"))
                     .description("Shared swap mode for aura / combat item use."))
                  .defaultValue(MultiTask.KillAuraMode.None))
               .build()
         );
      this.globalPreset = this.sgPacketLimits
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("global-preset"))
                     .description("All outgoing packets budget."))
                  .defaultValue(AntiCheatConfig.PacketPreset.Grim))
               .build()
         );
      this.globalLimit = this.sgPacketLimits
         .add(
            new IntSetting.Builder()
               .name("global-limit")
               .description("Max outgoing packets per timing window.")
               .defaultValue(1000)
               .min(1)
               .sliderRange(1, 5000)
               .visible(() -> this.globalPreset.get() == AntiCheatConfig.PacketPreset.Custom)
               .build()
         );
      this.globalTiming = this.sgPacketLimits
         .add(
            new DoubleSetting.Builder()
               .name("global-timing")
               .description("Global window length (seconds).")
               .defaultValue(1.0)
               .min(0.1)
               .sliderRange(0.1, 10.0)
               .visible(() -> this.globalPreset.get() == AntiCheatConfig.PacketPreset.Custom)
               .build()
         );
      this.offhandPreset = this.sgPacketLimits
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("offhand-preset"))
                     .description("Offhand-swap packets budget."))
                  .defaultValue(AntiCheatConfig.PacketPreset.Grim))
               .build()
         );
      this.offhandLimit = this.sgPacketLimits
         .add(
            new IntSetting.Builder()
               .name("offhand-limit")
               .description("Max offhand swaps per timing window.")
               .defaultValue(16)
               .min(1)
               .sliderRange(1, 1000)
               .visible(() -> this.offhandPreset.get() == AntiCheatConfig.PacketPreset.Custom)
               .build()
         );
      this.offhandTiming = this.sgPacketLimits
         .add(
            new DoubleSetting.Builder()
               .name("offhand-timing")
               .description("Offhand window length (seconds).")
               .defaultValue(1.0)
               .min(0.05)
               .sliderRange(0.05, 10.0)
               .visible(() -> this.offhandPreset.get() == AntiCheatConfig.PacketPreset.Custom)
               .build()
         );
      this.clickSlotPreset = this.sgPacketLimits
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("click-slot-preset"))
                     .description("Inventory click-slot packets budget."))
                  .defaultValue(AntiCheatConfig.PacketPreset.Grim))
               .build()
         );
      this.clickSlotLimit = this.sgPacketLimits
         .add(
            new IntSetting.Builder()
               .name("click-slot-limit")
               .description("Max click-slot packets per timing window.")
               .defaultValue(8)
               .min(1)
               .sliderRange(1, 1000)
               .visible(() -> this.clickSlotPreset.get() == AntiCheatConfig.PacketPreset.Custom)
               .build()
         );
      this.clickSlotTiming = this.sgPacketLimits
         .add(
            new DoubleSetting.Builder()
               .name("click-slot-timing")
               .description("Click-slot window length (seconds).")
               .defaultValue(1.0)
               .min(0.1)
               .sliderRange(0.1, 10.0)
               .visible(() -> this.clickSlotPreset.get() == AntiCheatConfig.PacketPreset.Custom)
               .build()
         );
      this.blockInteractRange = this.sgPacketLimits
         .add(
            new DoubleSetting.Builder()
               .name("block-interact-range")
               .description("Max block interaction range.")
               .defaultValue(4.5)
               .min(0.5)
               .sliderRange(0.5, 10.0)
               .build()
         );
      this.blockInteractLimit = this.sgPacketLimits
         .add(
            new IntSetting.Builder()
               .name("block-interact-limit")
               .description("Max block interactions per timeframe.")
               .defaultValue(9)
               .min(1)
               .sliderRange(1, 100)
               .build()
         );
      this.blockInteractTimeframe = this.sgPacketLimits
         .add(
            new IntSetting.Builder()
               .name("block-interact-timeframe")
               .description("Block interaction window (milliseconds).")
               .defaultValue(310)
               .min(50)
               .sliderRange(50, 1000)
               .build()
         );
      this.packetLimiter = this.sgPacketLimiter
         .add(
            new BoolSetting.Builder()
               .name("packet-limiter")
               .description(
                  "Measure every outgoing packet and defer module packets (crystal place/break, block placement, inventory clicks, extra look packets) when the total send rate nears the cap, so the server's packet limiter never kicks you. Vanilla movement / keep-alive / chat / teleport-confirm packets are never blocked."
               )
               .defaultValue(true)
               .build()
         );
      this.limiterMaxPps = this.sgPacketLimiter
         .add(
            new IntSetting.Builder()
               .name("limiter-max-pps")
               .description(
                  "Highest average outgoing packets/second (ALL packets, vanilla included) up to which LOW-priority module traffic runs: mining (SilentMine STARTs and its Y+955 burst), XP bottles, Phase, low-priority look packets. Paper's default kick limit is 500/s averaged over 7 s; lower this if the server kicks earlier."
               )
               .defaultValue(250)
               .min(20)
               .sliderRange(40, 500)
               .visible(() -> this.packetLimiter.get())
               .build()
         );
      this.limiterCombatPps = this.sgPacketLimiter
         .add(
            new IntSetting.Builder()
               .name("limiter-combat-pps")
               .description(
                  "Higher ceiling for COMBAT traffic (crystal place/break and block placement, inventory clicks, combat aim packets): it keeps running between limiter-max-pps and this rate after mining is already deferred, so a busy fight never starves it. Totem swaps are only refused at the server's 500/s kick line. Never effectively lower than limiter-max-pps."
               )
               .defaultValue(400)
               .min(20)
               .sliderRange(100, 490)
               .visible(() -> this.packetLimiter.get())
               .build()
         );
      this.limiterWindow = this.sgPacketLimiter
         .add(
            new DoubleSetting.Builder()
               .name("limiter-window")
               .description("Sliding window (seconds) the average is measured over. Paper averages over 7 s.")
               .defaultValue(7.0)
               .min(1.0)
               .max(10.0)
               .sliderRange(1.0, 10.0)
               .visible(() -> this.packetLimiter.get())
               .build()
         );
      this.limiterAuto = this.sgPacketLimiter
         .add(
            new BoolSetting.Builder()
               .name("limiter-auto")
               .description(
                  "Adaptive: learn the average rate of non-module (vanilla) traffic, keep a second of it in reserve, and once the window is filling up spread module packets over the ticks so they only use what is left of the cap."
               )
               .defaultValue(true)
               .visible(() -> this.packetLimiter.get())
               .build()
         );
      this.limiterPressure = this.sgPacketLimiter
         .add(
            new DoubleSetting.Builder()
               .name("limiter-pressure")
               .description("Fraction of the window cap at which module packets start being metered per tick (auto mode).")
               .defaultValue(0.75)
               .min(0.3)
               .max(1.0)
               .sliderRange(0.3, 1.0)
               .visible(() -> this.packetLimiter.get() && this.limiterAuto.get())
               .build()
         );
      this.limiterDebug = this.sgPacketLimiter
         .add(
            new BoolSetting.Builder()
               .name("limiter-debug")
               .description(
                  "Print the measured packet rates to chat when the limiter starts / stops throttling, with the top packet types of the last 5 s (module-classified types marked *)."
               )
               .defaultValue(false)
               .visible(() -> this.packetLimiter.get())
               .build()
         );
      this.killAuraPriority = this.addPriority("kill-aura-priority", RotationManager.RotationPriority.Highest);
      this.crystalAuraPriority = this.sgRotationPriority
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("crystal-aura-priority"))
                     .description(
                        "Priority of AutoCrystal's aim on the move packet. Highest = above SwordAura / SilentMine, below Phase's throw and Sprint's yaw spoof; VeryHigh or lower lets SwordAura's hit aim win the packet."
                     ))
                  .defaultValue(RotationManager.RotationPriority.Highest))
               .build()
         );
      this.anchorAuraPriority = this.addPriority("anchor-aura-priority", RotationManager.RotationPriority.Highest);
      this.surroundPriority = this.addPriority("surround-priority", RotationManager.RotationPriority.Highest);
      this.sprintPriority = this.addPriority("sprint-priority", RotationManager.RotationPriority.VeryHigh);
      this.speedPriority = this.addPriority("speed-priority", RotationManager.RotationPriority.Lowest);
      this.pearlPriority = this.addPriority("pearl-priority", RotationManager.RotationPriority.High);
      this.autoExpPriority = this.addPriority("auto-exp-priority", RotationManager.RotationPriority.Normal);
      this.elytraPriority = this.addPriority("elytra-priority", RotationManager.RotationPriority.Normal);
      this.latencyMode = this.sgLatency
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("latency-mode"))
                     .description("QueryPing uses the target's real ping; Fast uses a fixed value."))
                  .defaultValue(AntiCheatConfig.LatencyMode.QueryPing))
               .build()
         );
      this.fastLatency = this.sgLatency
         .add(
            new IntSetting.Builder()
               .name("fast-latency")
               .description("Latency (ms) used in Fast mode.")
               .defaultValue(3000)
               .min(250)
               .sliderRange(250, 10000)
               .visible(() -> this.latencyMode.get() == AntiCheatConfig.LatencyMode.Fast)
               .build()
         );
      this.predictionEnabled = this.sgPrediction
         .add(new BoolSetting.Builder().name("prediction").description("Extrapolate targets forward by their velocity.").defaultValue(false).build());
      this.predictionTicks = this.sgPrediction
         .add(
            new IntSetting.Builder()
               .name("ticks")
               .description("Ticks to predict ahead.")
               .defaultValue(3)
               .min(0)
               .sliderRange(0, 20)
               .visible(() -> this.predictionEnabled.get())
               .build()
         );
      this.elytraTicks = this.sgPrediction
         .add(
            new IntSetting.Builder()
               .name("elytra-ticks")
               .description("Ticks to predict ahead for gliding targets.")
               .defaultValue(3)
               .min(0)
               .sliderRange(0, 20)
               .visible(() -> this.predictionEnabled.get())
               .build()
         );
      this.groundRaycast = this.sgPrediction
         .add(
            new BoolSetting.Builder()
               .name("ground-raycast")
               .description("Clamp predicted position to the ground.")
               .defaultValue(true)
               .visible(() -> this.predictionEnabled.get())
               .build()
         );
      this.sentThisSecond = 0;
      this.recvThisSecond = 0;
      this.sentLastSecond = 0;
      this.recvLastSecond = 0;
      this.windowStartMs = 0L;
   }

   private Setting<RotationManager.RotationPriority> addPriority(String name, RotationManager.RotationPriority def) {
      return this.sgRotationPriority
         .add(
            ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name(name))
                     .description("Rotation priority for this feature."))
                  .defaultValue(def))
               .build()
         );
   }

   public static AntiCheatConfig get() {
      return Modules.get().get(AntiCheatConfig.class);
   }

   @EventHandler
   private void onSend(PacketEvent.Send event) {
      this.sentThisSecond++;
   }

   @EventHandler
   private void onReceive(PacketEvent.Receive event) {
      this.recvThisSecond++;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      long now = System.currentTimeMillis();
      if (this.windowStartMs == 0L) {
         this.windowStartMs = now;
      }

      if (now - this.windowStartMs >= 1000L) {
         this.windowStartMs = now;
         this.sentLastSecond = this.sentThisSecond;
         this.recvLastSecond = this.recvThisSecond;
         this.sentThisSecond = 0;
         this.recvThisSecond = 0;
      }
   }

   public int getSentPps() {
      return this.sentLastSecond;
   }

   public int getRecvPps() {
      return this.recvLastSecond;
   }

   public static enum LatencyMode {
      QueryPing,
      Fast;
   }

   public static enum PacketPreset {
      Grim,
      Custom;
   }
}
