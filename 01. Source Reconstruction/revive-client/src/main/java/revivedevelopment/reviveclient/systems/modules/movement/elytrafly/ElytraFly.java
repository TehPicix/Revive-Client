package revivedevelopment.reviveclient.systems.modules.movement.elytrafly;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.PlayerMoveEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.ChunkDataEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IVec3d;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.emotes.EmoteAutoPlay;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes.Bounce;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes.Packet;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes.Pitch40;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes.Slide;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.modes.Vanilla;
import revivedevelopment.reviveclient.systems.modules.player.ChestSwap;
import revivedevelopment.reviveclient.systems.modules.player.Rotation;
import revivedevelopment.reviveclient.systems.modules.render.Freecam;
import revivedevelopment.reviveclient.utils.misc.input.Input;

public class ElytraFly extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Boolean> saveDurability = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("save-durability")
            .description(
               "Skip elytra durability while gliding: right before each durability tick a hotbar chestplate is swapped into the chest slot server-side, then the elytra is swapped back and the wings re-opened. The client keeps gliding the whole time. Needs a chestplate in the hotbar."
            )
            .defaultValue(false)
            .build()
      );
   private final SettingGroup sgInventory = this.settings.createGroup("Inventory");
   private final SettingGroup sgAutopilot = this.settings.createGroup("Autopilot");
   private final SettingGroup sgObstaclePasser = this.settings.createGroup("Obstacle Passer");
   public final Setting<ElytraFlightModes> flightMode = this.sgGeneral
      .add(
         (((((new EnumSetting.Builder<ElytraFlightModes>().name("mode"))
                        .description("The mode of flying."))
                     .defaultValue(ElytraFlightModes.Vanilla))
                  .onModuleActivated(flightModesSetting -> this.onModeChanged((ElytraFlightModes)flightModesSetting.get())))
               .onChanged(this::onModeChanged))
            .build()
      );
   public final Setting<Boolean> autoTakeOff = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-take-off")
            .description("Automatically takes off when you hold jump without needing to double jump.")
            .defaultValue(false)
            .visible(
               () -> this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Double> fallMultiplier = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fall-multiplier")
            .description("Controls how fast will you go down naturally.")
            .defaultValue(0.01)
            .min(0.0)
            .visible(
               () -> this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Double> horizontalSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("horizontal-speed")
            .description("How fast you go forward and backward.")
            .defaultValue(1.0)
            .min(0.0)
            .visible(
               () -> this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Double> verticalSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("vertical-speed")
            .description("How fast you go up and down.")
            .defaultValue(1.0)
            .min(0.0)
            .visible(
               () -> this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Boolean> acceleration = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("acceleration")
            .defaultValue(false)
            .visible(
               () -> this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Double> accelerationStep = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("acceleration-step")
            .min(0.1)
            .max(5.0)
            .defaultValue(1.0)
            .visible(
               () -> this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.acceleration.get()
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Double> accelerationMin = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("acceleration-start")
            .min(0.1)
            .defaultValue(0.0)
            .visible(
               () -> this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.acceleration.get()
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Boolean> stopInWater = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("stop-in-water")
            .description("Stops flying in water.")
            .defaultValue(true)
            .visible(() -> this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide)
            .build()
      );
   public final Setting<Boolean> dontGoIntoUnloadedChunks = this.sgGeneral
      .add(new BoolSetting.Builder().name("no-unloaded-chunks").description("Stops you from going into unloaded chunks.").defaultValue(true).build());
   public final Setting<Boolean> autoHover = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-hover")
            .description("Automatically hover .3 blocks off ground when holding shift.")
            .defaultValue(false)
            .visible(() -> this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide)
            .build()
      );
   public final Setting<Boolean> noCrash = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("no-crash")
            .description("Stops you from going into walls.")
            .defaultValue(false)
            .visible(() -> this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide)
            .build()
      );
   public final Setting<Integer> crashLookAhead = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("crash-look-ahead")
            .description("Distance to look ahead when flying.")
            .defaultValue(5)
            .range(1, 15)
            .sliderMin(1)
            .visible(() -> this.noCrash.get() && this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide)
            .build()
      );
   private final Setting<Boolean> instaDrop = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("insta-drop")
            .description("Makes you drop out of flight instantly.")
            .defaultValue(false)
            .visible(() -> this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide)
            .build()
      );
   public final Setting<Double> pitch40lowerBounds = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("pitch40-lower-bounds")
            .description("The bottom height boundary for pitch40.")
            .defaultValue(80.0)
            .min(-128.0)
            .sliderMax(360.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Pitch40)
            .build()
      );
   public final Setting<Double> pitch40upperBounds = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("pitch40-upper-bounds")
            .description("The upper height boundary for pitch40.")
            .defaultValue(120.0)
            .min(-128.0)
            .sliderMax(360.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Pitch40)
            .build()
      );
   public final Setting<Double> pitch40rotationSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("pitch40-rotate-speed")
            .description("The speed for pitch rotation (degrees per tick)")
            .defaultValue(4.0)
            .min(1.0)
            .sliderMax(6.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Pitch40)
            .build()
      );
   public final Setting<Boolean> pitch40RocketToBounds = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pitch40-rocket-to-bounds")
            .description("When activated below the upper bounds, uses fireworks to climb up to it instead of turning off.")
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Pitch40)
            .build()
      );
   public final Setting<Integer> pitch40RocketFireworkDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("pitch40-rocket-firework-delay")
            .description("Delay in ticks between fireworks while rocketing up to the upper bounds. Lower means more fireworks used.")
            .defaultValue(20)
            .min(0)
            .sliderRange(0, 100)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Pitch40 && this.pitch40RocketToBounds.get())
            .build()
      );
   public Double pitch40UpperOverride = null;
   public Double pitch40LowerOverride = null;
   public final Setting<Boolean> autoTwerk = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-twerk")
            .description(
               "Plays the twerk emote on your player while you bounce, instead of the gliding pose. Everyone running Revive with the emotes module sees it."
            )
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
      );
   public final Setting<Boolean> autoJump = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-jump")
            .description("Automatically jumps for you.")
            .defaultValue(true)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
      );
   public final Setting<Rotation.LockMode> yawLockMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("yaw-lock"))
                     .description("Whether to enable yaw lock or not"))
                  .defaultValue(Rotation.LockMode.Smart))
               .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce))
            .build()
      );
   public final Setting<Double> pitch = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("pitch")
            .description("The pitch angle to look at when using the bounce mode.")
            .defaultValue(85.0)
            .range(0.0, 90.0)
            .sliderRange(0.0, 90.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
      );
   public final Setting<Boolean> tunnel = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("tunnel")
            .description(
               "Emulates the short hops of a 1x2 tunnel bounce as far as the anticheat allows: full glide gravity (looking straight down) while rising and until you are back within tunnel-height of the floor, then tunnel-glide-pitch for the last stretch to turn the drop into speed; under a real tunnel roof it stays straight down, where the fastest drop wins. The server runs the same physics from the same pitch, so it costs no setbacks; a real ceiling still beats it, because it kills the jump in one tick where full gravity needs five. Camera follows the pitch - use Free Look to keep your own view."
            )
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
      );
   public final Setting<Boolean> simulateRoof = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("simulate-roof")
            .description(
               "EXPERIMENT. On open ground, stops each jump 0.2 blocks up as a 2-high tunnel roof would, so the hops run at the tunnel's three-tick rhythm. The server has no such roof: its anticheat predicts the jump from its own world and is expected to set you back on every hop. Kept so the result can be read from the log - a report every 5 seconds counts capped hops, server setbacks and the speed that actually resulted. Off under a real roof."
            )
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.tunnel.get())
            .build()
      );
   public final Setting<Double> tunnelHeight = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("tunnel-height")
            .description("How far above the floor the drop switches from straight down to tunnel-glide-pitch. Simulated best around 0.5.")
            .defaultValue(0.5)
            .range(0.1, 2.0)
            .sliderRange(0.1, 2.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.tunnel.get())
            .build()
      );
   public final Setting<Double> tunnelGlidePitch = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("tunnel-glide-pitch")
            .description(
               "Pitch for the last stretch of each drop in tunnel mode. Shallower turns more of the fall into speed but lands later; simulated best around 30."
            )
            .defaultValue(30.0)
            .range(0.0, 89.0)
            .sliderRange(0.0, 89.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.tunnel.get())
            .build()
      );
   public final Setting<Double> yaw = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("yaw")
            .description("The yaw angle to look at when using simple rotation lock in bounce mode. Also used as the highway direction for the obstacle passer.")
            .defaultValue(0.0)
            .range(0.0, 360.0)
            .sliderRange(0.0, 360.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.yawLockMode.get() == Rotation.LockMode.Simple)
            .build()
      );
   public final Setting<Boolean> restart = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("restart")
            .description("Restarts flying with the elytra when rubberbanding.")
            .defaultValue(true)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
      );
   public final Setting<Integer> restartDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("restart-delay")
            .description("How many ticks to wait before restarting the elytra again after rubberbanding.")
            .defaultValue(7)
            .min(0)
            .sliderRange(0, 20)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.restart.get())
            .build()
      );
   public final Setting<Boolean> sprint = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("sprint")
            .description("Sprints all the time. If turned off, it will only sprint when the player is touching the ground.")
            .defaultValue(true)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
      );
   public final Setting<Boolean> motionYBoost = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("motion-y-boost")
            .description("Greatly increases speed by cancelling Y momentum.")
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
      );
   public final Setting<Boolean> onlyWhileColliding = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-while-colliding")
            .description("Only enables motion y boost if colliding with a wall.")
            .defaultValue(true)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.motionYBoost.get())
            .build()
      );
   public final Setting<Boolean> tunnelBounce = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("tunnel-bounce")
            .description("Allows you to bounce in 1x2 tunnels. This should not be on if you are not in a tunnel.")
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.motionYBoost.get())
            .build()
      );
   public final Setting<Double> speed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description("The speed in blocks per second to keep you at.")
            .defaultValue(100.0)
            .sliderRange(20.0, 250.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.motionYBoost.get())
            .build()
      );
   public final Setting<Double> slideAccel = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("slide-accel-speed")
            .description("The acceleration of slide.")
            .defaultValue(7.0)
            .min(0.0)
            .sliderMax(5.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Slide)
            .build()
      );
   public final Setting<Double> slideMaxSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("slide-max-speed")
            .description("The maximum speed of slide")
            .defaultValue(7.0)
            .min(0.0)
            .sliderMax(200.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Slide)
            .build()
      );
   public final Setting<Boolean> replace = this.sgInventory
      .add(new BoolSetting.Builder().name("elytra-replace").description("Replaces broken elytra with a new elytra.").defaultValue(false).build());
   public final Setting<Integer> replaceDurability = this.sgInventory
      .add(
         new IntSetting.Builder()
            .name("replace-durability")
            .description("The durability threshold your elytra will be replaced at.")
            .defaultValue(2)
            .range(1, (Integer)Items.ELYTRA.getComponents().get(DataComponentTypes.MAX_DAMAGE) - 1)
            .sliderRange(1, (Integer)Items.ELYTRA.getComponents().get(DataComponentTypes.MAX_DAMAGE) - 1)
            .visible(this.replace::get)
            .build()
      );
   public final Setting<ElytraFly.ChestSwapMode> chestSwap = this.sgInventory
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("chest-swap"))
                  .description("Enables ChestSwap when toggling this module."))
               .defaultValue(ElytraFly.ChestSwapMode.Never))
            .build()
      );
   public final Setting<Boolean> autoReplenish = this.sgInventory
      .add(new BoolSetting.Builder().name("replenish-fireworks").description("Moves fireworks into a selected hotbar slot.").defaultValue(false).build());
   public final Setting<Integer> replenishSlot = this.sgInventory
      .add(
         new IntSetting.Builder()
            .name("replenish-slot")
            .description("The slot auto move moves fireworks to.")
            .defaultValue(9)
            .range(1, 9)
            .sliderRange(1, 9)
            .visible(this.autoReplenish::get)
            .build()
      );
   public final Setting<Boolean> autoPilot = this.sgAutopilot
      .add(
         new BoolSetting.Builder()
            .name("auto-pilot")
            .description("Moves forward while elytra flying.")
            .defaultValue(false)
            .visible(
               () -> this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Boolean> useFireworks = this.sgAutopilot
      .add(
         new BoolSetting.Builder()
            .name("use-fireworks")
            .description("Uses firework rockets every second of your choice.")
            .defaultValue(false)
            .visible(
               () -> this.autoPilot.get()
                  && this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Double> autoPilotFireworkDelay = this.sgAutopilot
      .add(
         new DoubleSetting.Builder()
            .name("firework-delay")
            .description("The delay in seconds in between using fireworks if \"Use Fireworks\" is enabled.")
            .min(1.0)
            .defaultValue(8.0)
            .sliderMax(20.0)
            .visible(
               () -> this.useFireworks.get()
                  && this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Double> autoPilotMinimumHeight = this.sgAutopilot
      .add(
         new DoubleSetting.Builder()
            .name("minimum-height")
            .description("The minimum height for autopilot.")
            .defaultValue(120.0)
            .min(-128.0)
            .sliderMax(260.0)
            .visible(
               () -> this.autoPilot.get()
                  && this.flightMode.get() != ElytraFlightModes.Pitch40
                  && this.flightMode.get() != ElytraFlightModes.Bounce
                  && this.flightMode.get() != ElytraFlightModes.Slide
            )
            .build()
      );
   public final Setting<Boolean> highwayObstaclePasser = this.sgObstaclePasser
      .add(
         new BoolSetting.Builder()
            .name("highway-obstacle-passer")
            .description("Uses baritone to pass obstacles.")
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce)
            .build()
      );
   public final Setting<Boolean> useCustomStartPos = this.sgObstaclePasser
      .add(
         new BoolSetting.Builder()
            .name("use-custom-start-position")
            .description(
               "Enable and set this ONLY if you are on a ringroad or don't want to be locked to a highway. Otherwise (0, 0) is the start position and will be automatically used."
            )
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.highwayObstaclePasser.get())
            .build()
      );
   public final Setting<BlockPos> startPos = this.sgObstaclePasser
      .add(
         new BlockPosSetting.Builder()
            .name("start-position")
            .description("The start position to use when using a custom start position.")
            .defaultValue(new BlockPos(0, 0, 0))
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.highwayObstaclePasser.get() && this.useCustomStartPos.get())
            .build()
      );
   public final Setting<Boolean> awayFromStartPos = this.sgObstaclePasser
      .add(
         new BoolSetting.Builder()
            .name("away-from-start-position")
            .description("If true, will go away from the start position instead of towards it. The start pos is (0,0) if it is not set to a custom start pos.")
            .defaultValue(true)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.highwayObstaclePasser.get())
            .build()
      );
   public final Setting<Double> obstacleDistance = this.sgObstaclePasser
      .add(
         new DoubleSetting.Builder()
            .name("distance")
            .description("The distance to set the baritone goal for path realignment.")
            .defaultValue(10.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.highwayObstaclePasser.get())
            .build()
      );
   public final Setting<Integer> targetY = this.sgObstaclePasser
      .add(
         new IntSetting.Builder()
            .name("y-level")
            .description("The Y level to bounce at. This must be correct or bounce will not start properly.")
            .defaultValue(120)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.highwayObstaclePasser.get())
            .build()
      );
   public final Setting<Boolean> avoidPortalTraps = this.sgObstaclePasser
      .add(
         new BoolSetting.Builder()
            .name("avoid-portal-traps")
            .description("Will attempt to detect portal traps on chunk load and avoid them.")
            .defaultValue(false)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.highwayObstaclePasser.get())
            .build()
      );
   public final Setting<Double> portalAvoidDistance = this.sgObstaclePasser
      .add(
         new DoubleSetting.Builder()
            .name("portal-avoid-distance")
            .description("The distance to a portal trap where the obstacle passer will takeover and go around it.")
            .defaultValue(20.0)
            .min(0.0)
            .sliderMax(50.0)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.highwayObstaclePasser.get() && this.avoidPortalTraps.get())
            .build()
      );
   public final Setting<Integer> portalScanWidth = this.sgObstaclePasser
      .add(
         new IntSetting.Builder()
            .name("portal-scan-width")
            .description("The width on the axis of the highway that will be scanned for portal traps.")
            .defaultValue(5)
            .min(3)
            .sliderMax(10)
            .visible(() -> this.flightMode.get() == ElytraFlightModes.Bounce && this.highwayObstaclePasser.get() && this.avoidPortalTraps.get())
            .build()
      );
   private ElytraFlightMode currentMode = new Vanilla();
   private static final int BOUNCE_GRACE_TICKS = 15;
   private int ticksSinceGlide = Integer.MAX_VALUE;
   private final ElytraFly.StaticGroundListener staticGroundListener = new ElytraFly.StaticGroundListener();
   private final ElytraFly.StaticInstaDropListener staticInstadropListener = new ElytraFly.StaticInstaDropListener();

   public double pitch40Upper() {
      Double o = this.pitch40UpperOverride;
      return o != null ? o : this.pitch40upperBounds.get();
   }

   public double pitch40Lower() {
      Double o = this.pitch40LowerOverride;
      return o != null ? o : this.pitch40lowerBounds.get();
   }

   public ElytraFly() {
      super(Categories.Movement, "elytra-fly", "Gives you more control over your elytra.");
      ElytraDurabilitySaver.get().register(this::isActive, this.saveDurability::get);
   }

   public void onCorrected(Vec3d offset) {
      if (this.isActive()) {
         this.currentMode.onCorrected(offset);
      }
   }

   @Override
   public void onActivate() {
      this.currentMode.onActivate();
      if ((this.chestSwap.get() == ElytraFly.ChestSwapMode.Always || this.chestSwap.get() == ElytraFly.ChestSwapMode.WaitForGround)
         && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA
         && this.isActive()
         && !PbPredict.serverPushesOnly()) {
         Modules.get().get(ChestSwap.class).swap();
      }
   }

   @Override
   public void onDeactivate() {
      this.pitch40UpperOverride = null;
      this.pitch40LowerOverride = null;
      if (this.autoPilot.get()) {
         this.mc.options.forwardKey.setPressed(Input.isPressed(this.mc.options.forwardKey));
      }

      if (!PbPredict.serverPushesOnly()) {
         if (this.chestSwap.get() == ElytraFly.ChestSwapMode.Always && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            Modules.get().get(ChestSwap.class).swap();
         } else if (this.chestSwap.get() == ElytraFly.ChestSwapMode.WaitForGround) {
            this.enableGroundListener();
         }
      }

      if (this.mc.player.isGliding() && this.instaDrop.get()) {
         this.enableInstaDropListener();
      }

      this.currentMode.onDeactivate();
      this.ticksSinceGlide = Integer.MAX_VALUE;
      EmoteAutoPlay.drive(false, "twerk");
   }

   @EventHandler
   private void onPlayerMove(PlayerMoveEvent event) {
      if (this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
         this.currentMode.handleMotionYBoost(event);
         this.currentMode.autoTakeoff();
         if (this.mc.player.isGliding()) {
            if (this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide) {
               this.currentMode.velX = 0.0;
               this.currentMode.velY = event.movement.y;
               this.currentMode.velZ = 0.0;
               this.currentMode.forward = Vec3d.fromPolar(0.0F, this.mc.player.getYaw()).multiply(0.1);
               this.currentMode.right = Vec3d.fromPolar(0.0F, this.mc.player.getYaw() + 90.0F).multiply(0.1);
               if (this.mc.player.isTouchingWater() && this.stopInWater.get()) {
                  this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
                  return;
               }

               this.currentMode.handleFallMultiplier();
               this.currentMode.handleAutopilot();
               this.currentMode.handleAcceleration();
               this.currentMode.handleHorizontalSpeed(event);
               this.currentMode.handleVerticalSpeed(event);
            }

            int chunkX = (int)((this.mc.player.getX() + this.currentMode.velX) / 16.0);
            int chunkZ = (int)((this.mc.player.getZ() + this.currentMode.velZ) / 16.0);
            if (this.dontGoIntoUnloadedChunks.get()) {
               if (this.mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                  if (this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide) {
                     ((IVec3d)event.movement).revive$set(this.currentMode.velX, this.currentMode.velY, this.currentMode.velZ);
                  }
               } else {
                  this.currentMode.zeroAcceleration();
                  ((IVec3d)event.movement).revive$set(0.0, this.currentMode.velY, 0.0);
               }
            } else if (this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide) {
               ((IVec3d)event.movement).revive$set(this.currentMode.velX, this.currentMode.velY, this.currentMode.velZ);
            }

            if (this.flightMode.get() != ElytraFlightModes.Bounce && this.flightMode.get() != ElytraFlightModes.Slide) {
               this.currentMode.onPlayerMove();
            }
         } else if (this.currentMode.lastForwardPressed
            && this.flightMode.get() != ElytraFlightModes.Bounce
            && this.flightMode.get() != ElytraFlightModes.Slide) {
            this.mc.options.forwardKey.setPressed(false);
            this.currentMode.lastForwardPressed = false;
         }

         if (this.noCrash.get()
            && this.mc.player.isGliding()
            && this.flightMode.get() != ElytraFlightModes.Bounce
            && this.flightMode.get() != ElytraFlightModes.Slide) {
            Vec3d lookAheadPos = this.mc.player.getEntityPos().add(this.mc.player.getVelocity().normalize().multiply(this.crashLookAhead.get().intValue()));
            RaycastContext raycastContext = new RaycastContext(
               this.mc.player.getEntityPos(),
               new Vec3d(lookAheadPos.getX(), this.mc.player.getY(), lookAheadPos.getZ()),
               ShapeType.COLLIDER,
               FluidHandling.NONE,
               this.mc.player
            );
            BlockHitResult hitResult = this.mc.world.raycast(raycastContext);
            if (hitResult != null && hitResult.getType() == Type.BLOCK) {
               ((IVec3d)event.movement).revive$set(0.0, this.currentMode.velY, 0.0);
            }
         }

         if (this.autoHover.get()
            && this.mc.player.isSneaking()
            && !Modules.get().get(Freecam.class).isActive()
            && this.mc.player.isGliding()
            && this.flightMode.get() != ElytraFlightModes.Bounce
            && this.flightMode.get() != ElytraFlightModes.Slide) {
            BlockState underState = this.mc.world.getBlockState(this.mc.player.getBlockPos().down());
            Block under = underState.getBlock();
            BlockState under2State = this.mc.world.getBlockState(this.mc.player.getBlockPos().down().down());
            Block under2 = under2State.getBlock();
            boolean underCollidable = under.collidable || !underState.getFluidState().isEmpty();
            boolean under2Collidable = under2.collidable || !under2State.getFluidState().isEmpty();
            if (!underCollidable && under2Collidable) {
               ((IVec3d)event.movement).revive$set(event.movement.x, -0.1F, event.movement.z);
               this.mc.player.setPitch(MathHelper.clamp(this.mc.player.getPitch(0.0F), -50.0F, 20.0F));
            }

            if (underCollidable) {
               ((IVec3d)event.movement).revive$set(event.movement.x, -0.03F, event.movement.z);
               this.mc.player.setPitch(MathHelper.clamp(this.mc.player.getPitch(0.0F), -50.0F, 20.0F));
               if (this.mc.player.getEntityPos().y <= this.mc.player.getBlockPos().down().getY() + 1.34F) {
                  ((IVec3d)event.movement).revive$set(event.movement.x, 0.0, event.movement.z);
                  this.mc.player.setSneaking(false);
               }
            }
         }
      }
   }

   public boolean canPacketEfly() {
      return this.isActive()
         && this.flightMode.get() == ElytraFlightModes.Packet
         && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
         && !this.mc.player.isOnGround();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      this.currentMode.onTick();
      this.tickAutoTwerk();
   }

   public boolean isBouncing() {
      if (!this.isActive() || this.flightMode.get() != ElytraFlightModes.Bounce || this.mc.player == null) {
         return false;
      } else {
         return this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA
            ? false
            : this.mc.player.isGliding() || this.ticksSinceGlide <= 15;
      }
   }

   private void tickAutoTwerk() {
      if (this.mc.player != null) {
         if (this.mc.player.isGliding()) {
            this.ticksSinceGlide = 0;
         } else if (this.ticksSinceGlide < Integer.MAX_VALUE) {
            this.ticksSinceGlide++;
         }

         EmoteAutoPlay.drive(this.autoTwerk.get() && this.isBouncing(), "twerk");
      }
   }

   @EventHandler
   private void onPreTick(TickEvent.Pre event) {
      this.currentMode.onPreTick();
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      this.currentMode.onPacketSend(event);
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      this.currentMode.onPacketReceive(event);
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      this.currentMode.onChunkData(event);
   }

   private void onModeChanged(ElytraFlightModes mode) {
      switch (mode) {
         case Vanilla:
            this.currentMode = new Vanilla();
            break;
         case Packet:
            this.currentMode = new Packet();
            break;
         case Pitch40:
            this.currentMode = new Pitch40();
            this.autoPilot.set(false);
            break;
         case Bounce:
            this.currentMode = new Bounce();
            break;
         case Slide:
            this.currentMode = new Slide();
      }
   }

   protected void enableGroundListener() {
      ReviveClient.EVENT_BUS.unsubscribe(this.staticGroundListener);
      ReviveClient.EVENT_BUS.subscribe(this.staticGroundListener);
   }

   protected void disableGroundListener() {
      ReviveClient.EVENT_BUS.unsubscribe(this.staticGroundListener);
   }

   protected void enableInstaDropListener() {
      ReviveClient.EVENT_BUS.unsubscribe(this.staticInstadropListener);
      ReviveClient.EVENT_BUS.subscribe(this.staticInstadropListener);
   }

   protected void disableInstaDropListener() {
      ReviveClient.EVENT_BUS.unsubscribe(this.staticInstadropListener);
   }

   @Override
   public String getInfoString() {
      return this.currentMode.getHudString();
   }

   public static enum AutoPilotMode {
      Vanilla,
      Pitch40;
   }

   public static enum ChestSwapMode {
      Always,
      Never,
      WaitForGround;
   }

   private class StaticGroundListener {
      @EventHandler
      private void chestSwapGroundListener(PlayerMoveEvent event) {
         if (ElytraFly.this.mc.player != null
            && ElytraFly.this.mc.player.isOnGround()
            && ElytraFly.this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            if (!PbPredict.serverPushesOnly()) {
               Modules.get().get(ChestSwap.class).swap();
            }

            ElytraFly.this.disableGroundListener();
         }
      }
   }

   private class StaticInstaDropListener {
      @EventHandler
      private void onInstadropTick(TickEvent.Post event) {
         if (ElytraFly.this.mc.player != null && ElytraFly.this.mc.player.isGliding()) {
            ElytraFly.this.mc.player.setVelocity(0.0, 0.0, 0.0);
            ElytraFly.this.mc.player.networkHandler.sendPacket(new OnGroundOnly(true, ElytraFly.this.mc.player.horizontalCollision));
         } else {
            ElytraFly.this.disableInstaDropListener();
         }
      }
   }
}
