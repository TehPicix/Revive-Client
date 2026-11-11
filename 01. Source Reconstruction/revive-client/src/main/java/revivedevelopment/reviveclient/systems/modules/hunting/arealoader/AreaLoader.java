package revivedevelopment.reviveclient.systems.modules.hunting.arealoader;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WVerticalList;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.modes.Rectangle;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.modes.Spiral;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.modes.SpiralCircle;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.modes.ZigZag;

public class AreaLoader extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgFlight = this.settings.createGroup("Flight");
   private final SettingGroup sgNether = this.settings.createGroup("Nether");
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   public final Setting<AreaLoaderModes> chunkLoadMode = this.sgGeneral
      .add(
         (((((new EnumSetting.Builder<AreaLoaderModes>().name("mode"))
                        .description("The pattern chunks are loaded in."))
                     .defaultValue(AreaLoaderModes.Spiral))
                  .onModuleActivated(s -> this.rebuildMode((AreaLoaderModes)s.get())))
               .onChanged(this::onModeChanged))
            .build()
      );
   public final Setting<BlockPos> startPos = this.sgGeneral
      .add(
         new BlockPosSetting.Builder()
            .name("start-position")
            .description("The coordinates to start the rectangle at. Y is ignored.")
            .defaultValue(new BlockPos(0, 0, 0))
            .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.Rectangle)
            .build()
      );
   public final Setting<BlockPos> targetPos = this.sgGeneral
      .add(
         new BlockPosSetting.Builder()
            .name("end-position")
            .description("The coordinates to end the rectangle at. Y is ignored.")
            .defaultValue(new BlockPos(0, 0, 0))
            .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.Rectangle)
            .build()
      );
   public final Setting<Integer> rowGap = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("path-gap")
            .description("The number of chunks to space between each path.")
            .defaultValue(25)
            .min(1)
            .sliderRange(1, 100)
            .build()
      );
   public final Setting<Integer> zigzagLegLength = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("zigzag-leg-length")
            .description("How far to travel in blocks before turning (one leg of the zigzag).")
            .defaultValue(5000)
            .min(100)
            .sliderRange(500, 20000)
            .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.ZigZag)
            .build()
      );
   public final Setting<Integer> zigzagRowGap = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("zigzag-row-gap")
            .description("How far to shift sideways in blocks between legs.")
            .defaultValue(192)
            .min(16)
            .sliderRange(16, 512)
            .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.ZigZag)
            .build()
      );
   public final Setting<String> saveLocation = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("save-name")
            .description("The folder name used to save progress. Leave blank to use 'default'.")
            .defaultValue("world")
            .build()
      );
   public final Setting<Boolean> disconnectOnCompletion = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("disconnect-on-completion")
            .description("Whether to disconnect after the rectangle path is complete.")
            .defaultValue(false)
            .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.Rectangle)
            .build()
      );
   public final Setting<Double> boatFlySpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("boat-fly-speed")
            .description("Speed in blocks per second, used only for the completion-time estimate.")
            .defaultValue(30.0)
            .min(1.0)
            .sliderRange(1.0, 100.0)
            .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.Rectangle)
            .build()
      );
   public final Setting<AreaLoader.OverworldFlightMode> overworldFlightMode = this.sgFlight
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("overworld-flight-mode"))
                  .description(
                     "How AreaLoader flies in Overworld/End. PITCH40 drives ElytraFly's Pitch40 mode, ROCKETS just enables ElytraFly, BARITONE_ELYTRA flies the same Baritone waypoints as in the Nether (needs a Baritone that can fly the Overworld: the HunterBuddy Overworld build, or BaritoneElytraOverworld's unlock on a hookable build), OTHER does nothing (manual control)."
                  ))
               .defaultValue(AreaLoader.OverworldFlightMode.PITCH40))
            .build()
      );
   public final Setting<Boolean> pitch40AutoFirework = this.sgFlight
      .add(
         new BoolSetting.Builder()
            .name("pitch40-auto-firework")
            .description("Enable ElytraFly's pitch40 rocket-to-bounds (auto-firework) while driving Pitch40.")
            .defaultValue(false)
            .visible(() -> this.overworldFlightMode.get() == AreaLoader.OverworldFlightMode.PITCH40)
            .build()
      );
   public final Setting<Integer> spiralCornerReachDistance = this.sgFlight
      .add(
         new IntSetting.Builder()
            .name("corner-reach-distance")
            .description("How close to a spiral corner (Circle: waypoint) before turning. It also turns once you pass it, so this is mainly a fallback.")
            .defaultValue(20)
            .min(1)
            .sliderRange(1, 100)
            .visible(this::isSpiral)
            .build()
      );
   public final Setting<Integer> spiralMaxOffTrackDistance = this.sgFlight
      .add(
         new IntSetting.Builder()
            .name("max-off-track-distance")
            .description("How far off the spiral path before saving state and navigating back to recover.")
            .defaultValue(500)
            .min(20)
            .sliderRange(50, 2000)
            .visible(this::isSpiral)
            .build()
      );
   public final Setting<Integer> circleSegmentLength = this.sgFlight
      .add(
         new IntSetting.Builder()
            .name("circle-segment-length")
            .description("Blocks flown along the arc between heading updates. Smaller is a rounder circle at the cost of more frequent turns.")
            .defaultValue(100)
            .min(10)
            .sliderRange(10, 500)
            .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.SpiralCircle)
            .build()
      );
   public final Setting<Integer> circleTurnStep = this.sgFlight
      .add(
         new IntSetting.Builder()
            .name("circle-turn-step")
            .description("Maximum degrees the heading turns per segment. Caps how sharp the turns get on the tight inner loops.")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 45)
            .visible(() -> this.chunkLoadMode.get() == AreaLoaderModes.SpiralCircle)
            .build()
      );
   public final Setting<AreaLoader.NetherPathMode> netherPathMode = this.sgNether
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("nether-path-mode"))
                  .description("How AreaLoader navigates in the Nether. BARITONE_ELYTRA uses Baritone elytra pathing, OTHER does nothing (manual control)."))
               .defaultValue(AreaLoader.NetherPathMode.BARITONE_ELYTRA))
            .build()
      );
   public final Setting<Integer> netherWaypointDistance = this.sgNether
      .add(
         new IntSetting.Builder()
            .name("nether-waypoint-distance")
            .description(
               "Distance in blocks between Baritone waypoints (Nether, and Overworld/End in BARITONE_ELYTRA flight mode). Smaller = more accurate but slower."
            )
            .defaultValue(200)
            .min(50)
            .sliderRange(50, 500)
            .visible(() -> this.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA || this.overworldUsesBaritone())
            .build()
      );
   public final Setting<Integer> netherWaypointReachDistance = this.sgNether
      .add(
         new IntSetting.Builder()
            .name("nether-reach-distance")
            .description("How close to a Baritone waypoint before considering it reached and moving to the next.")
            .defaultValue(100)
            .min(10)
            .sliderRange(10, 100)
            .visible(() -> this.netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA || this.overworldUsesBaritone())
            .build()
      );
   public final Setting<Boolean> debugMode = this.sgDebug
      .add(
         new BoolSetting.Builder()
            .name("debug-messages")
            .description("Show debug messages in chat (file saves, dimension changes, corner reached, etc.)")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> showGoalWaypoint = this.sgDebug
      .add(
         new BoolSetting.Builder()
            .name("show-goal-waypoint")
            .description("Create a temporary Xaero waypoint at the current goal position.")
            .defaultValue(false)
            .build()
      );
   private AreaLoaderMode currentMode = new Rectangle(this);

   public AreaLoader() {
      super(
         Categories.Hunting,
         "area-loader",
         "Auto-flies a rectangle, spiral, or zigzag to load chunks from you. Useful with Stash Finder or other map-saving mods."
      );
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      WVerticalList list = theme.verticalList();
      WButton clear = list.add(theme.button("Clear Currently Selected")).widget();
      clear.action = () -> {
         boolean wasActive = this.isActive();
         if (wasActive) {
            this.toggle();
         }

         this.currentMode.clear();
         if (wasActive) {
            this.info("Module was disabled. Activate it again to start fresh.", new Object[0]);
         }
      };
      WButton clearAll = list.add(theme.button("Clear All")).widget();
      clearAll.action = () -> {
         boolean wasActive = this.isActive();
         if (wasActive) {
            this.toggle();
         }

         this.currentMode.clearAll();
         if (wasActive) {
            this.info("Module was disabled. Activate it again to start fresh with any mode.", new Object[0]);
         }
      };
      list.add(theme.horizontalSeparator()).expandX();
      WButton recoveryBtn = list.add(theme.button("Recovery Configuration")).widget();
      recoveryBtn.action = () -> this.mc.setScreen(new AreaRecoveryScreen(theme, this));
      return list;
   }

   @Override
   public void onActivate() {
      this.currentMode.onActivate();
   }

   @Override
   public void onDeactivate() {
      this.currentMode.onDeactivate();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null
         && this.mc.world != null
         && this.mc.interactionManager != null
         && this.mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL) {
         this.currentMode.onTick();
      }
   }

   private boolean isSpiral() {
      return this.chunkLoadMode.get() == AreaLoaderModes.Spiral || this.chunkLoadMode.get() == AreaLoaderModes.SpiralCircle;
   }

   private void rebuildMode(AreaLoaderModes mode) {
      switch (mode) {
         case Rectangle:
            this.currentMode = new Rectangle(this);
            break;
         case Spiral:
            this.currentMode = new Spiral(this);
            break;
         case SpiralCircle:
            this.currentMode = new SpiralCircle(this);
            break;
         case ZigZag:
            this.currentMode = new ZigZag(this);
      }
   }

   private void onModeChanged(AreaLoaderModes mode) {
      boolean wasActive = this.isActive() && this.mc.player != null && this.mc.world != null;
      if (wasActive) {
         this.currentMode.onDeactivate();
      }

      this.rebuildMode(mode);
      if (wasActive) {
         this.currentMode.onActivate();
      }
   }

   public boolean overworldUsesBaritone() {
      return this.overworldFlightMode.get() == AreaLoader.OverworldFlightMode.BARITONE_ELYTRA;
   }

   public static enum NetherPathMode {
      BARITONE_ELYTRA,
      OTHER;
   }

   public static enum OverworldFlightMode {
      ROCKETS,
      PITCH40,
      BARITONE_ELYTRA,
      OTHER;
   }
}
