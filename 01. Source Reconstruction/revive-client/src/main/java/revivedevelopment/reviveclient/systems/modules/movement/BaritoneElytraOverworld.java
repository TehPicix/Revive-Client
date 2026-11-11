package revivedevelopment.reviveclient.systems.modules.movement;

import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.BaritoneMixinGuard;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.hunting.HuntingUtils;
import revivedevelopment.reviveclient.utils.BaritoneBridge;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class BaritoneElytraOverworld extends Module {
   private static volatile boolean unlocked;
   private static volatile int goalYValue = 100;
   private static volatile int landingRetryDropValue = 64;
   private static volatile boolean debugValue;
   private static volatile long lastClampNoticeMs;
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
   private final Setting<Boolean> predictTerrainInNether = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("predict-terrain-in-nether")
            .description(
               "What Baritone's elytraPredictTerrain is set to while you are in the Nether. Outside the Nether it is always forced off: prediction generates Nether terrain from the seed, which is nonsense in the Overworld and would fly you into the ground."
            )
            .defaultValue(true)
            .onChanged(v -> this.refresh(true))
            .build()
      );
   private final Setting<Integer> overworldGoalY = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("overworld-goal-y")
            .description(
               "Arrival height used for X/Z elytra goals outside the Nether (Baritone hardcodes 64, which is ground level in the Overworld); keep it above the terrain you expect around the destination. With a natively Overworld-capable Baritone the whole world height is available; with the hooked-in unlock the pathfinder can only model up to y=127, so anything higher is clamped to that."
            )
            .defaultValue(100)
            .range(1, 319)
            .sliderRange(32, 200)
            .onChanged(v -> goalYValue = v)
            .build()
      );
   private final Setting<Integer> landingRetryDrop = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("landing-retry-drop")
            .description(
               "How many blocks below its chosen landing spot Baritone may glide before it gives the spot up and picks another (Baritone's Nether value is 15). Overworld terrain beside a spot is often far lower than the spot itself, and touching down anywhere finishes the flight, so keep this generous."
            )
            .defaultValue(64)
            .range(15, 255)
            .sliderRange(15, 128)
            .onChanged(v -> landingRetryDropValue = v)
            .build()
      );
   private final SettingGroup sgAutoJump = this.settings.createGroup("Auto Jump");
   private final Setting<Boolean> autoJump = this.sgAutoJump
      .add(
         new BoolSetting.Builder()
            .name("auto-jump")
            .description(
               "Take off from wherever you stand when a Baritone elytra flight starts outside the Nether: jump, open the elytra mid-air and fire a rocket, then Baritone flies. Needs an elytra on and firework rockets in the hotbar (or in the inventory with a free hotbar slot). Baritone's own elytraAutoJump, which walks to a cliff instead, is switched off while this is in charge. With a natively Overworld-capable Baritone this instead turns on that build's own elytraAutoJump and elytraStandingTakeoff outside the Nether, which take off from flat ground themselves."
            )
            .defaultValue(false)
            .onChanged(v -> {
               this.applyAutoJump();
               this.resetTakeoff();
            })
            .build()
      );
   private final Setting<Integer> takeoffDelay = this.sgAutoJump
      .add(
         new IntSetting.Builder()
            .name("takeoff-delay")
            .description(
               "Ticks to stand still after Baritone asks for a glide before jumping, so its flight path is usually computed by the time you are airborne."
            )
            .defaultValue(10)
            .range(0, 100)
            .sliderRange(0, 60)
            .visible(this.autoJump::get)
            .build()
      );
   private final Setting<Double> takeoffPitch = this.sgAutoJump
      .add(
         new DoubleSetting.Builder()
            .name("takeoff-pitch")
            .description("Where you look when the takeoff rocket is fired. Negative is up; Baritone takes over the rotation as soon as it is flying.")
            .defaultValue(-35.0)
            .range(-89.0, 0.0)
            .sliderRange(-89.0, 0.0)
            .visible(this.autoJump::get)
            .build()
      );
   private final Setting<Integer> maxAttempts = this.sgAutoJump
      .add(
         new IntSetting.Builder()
            .name("max-attempts")
            .description("Give up (until the next flight is started) after this many takeoffs that ended back on the ground without a glide.")
            .defaultValue(3)
            .range(1, 10)
            .sliderRange(1, 10)
            .visible(this.autoJump::get)
            .build()
      );
   private final Setting<Integer> retryDelay = this.sgAutoJump
      .add(
         new IntSetting.Builder()
            .name("retry-delay")
            .description("Ticks to wait on the ground between takeoff attempts.")
            .defaultValue(40)
            .range(0, 200)
            .sliderRange(0, 100)
            .visible(this.autoJump::get)
            .build()
      );
   private final SettingGroup sgCrashGuard = this.settings.createGroup("Crash Guard");
   private final Setting<Boolean> crashGuard = this.sgCrashGuard
      .add(
         new BoolSetting.Builder()
            .name("crash-guard")
            .description(
               "EXPERIMENTAL, off by default - test it in open terrain before trusting it. While BARITONE is flying you (not a manual glide), watch the REAL loaded world ahead and steer over, under or around a block about to be hit. Baritone's own avoidance is blind above y=127 (its pathfinder only models y 0-127, so mountains and trees above that are invisible and it flies straight in); this reads the actual blocks instead and picks whichever escape - climb, dive or turn - opens a clear line, pulling straight up when nothing else is clear. It steers by overriding the flight's yaw/pitch, which fights Baritone's own steering, so it can be twitchy; it only powers a rocket toward a heading that is already clear, never into terrain. Off unless you turn it on. Only runs with the hooked-in unlock: a natively Overworld-capable Baritone models the full world height and has no such blind spot."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> crashLookahead = this.sgCrashGuard
      .add(
         new IntSetting.Builder()
            .name("lookahead")
            .description(
               "Ticks of clear space to keep ahead. Avoidance triggers when a block is within this many ticks of travel at your current speed. Higher = earlier, wider berth but more sensitive; lower = later, sharper turns."
            )
            .defaultValue(14)
            .range(4, 40)
            .sliderRange(4, 40)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Double> crashClimbPitch = this.sgCrashGuard
      .add(
         new DoubleSetting.Builder()
            .name("max-avoid-pitch")
            .description(
               "Steepest up/down tilt the guard may use to escape over or under an obstacle. Negative is up; the same magnitude is used downward. Turns (going around) are tried at the same time and preferred when they cost less of a course change."
            )
            .defaultValue(-45.0)
            .range(-89.0, -5.0)
            .sliderRange(-89.0, -5.0)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Boolean> crashRocket = this.sgCrashGuard
      .add(
         new BoolSetting.Builder()
            .name("emergency-rocket")
            .description(
               "Fire a rocket when pulling up, so the climb actually clears the obstacle instead of just stalling into it. Uses the durability saver like any other rocket."
            )
            .defaultValue(true)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description(
               "Say in chat whenever the unlock, elytraPredictTerrain or elytraAutoJump is switched, and when a flight is planned from y=127 because you are above it."
            )
            .defaultValue(false)
            .onChanged(v -> debugValue = v)
            .build()
      );
   private RegistryKey<World> lastDimension;
   private boolean hooksOk;
   private boolean nativeBuild;
   private Boolean savedAutoJump;
   private Boolean savedStandingTakeoff;
   private BaritoneElytraOverworld.Takeoff takeoff = BaritoneElytraOverworld.Takeoff.IDLE;
   private int takeoffTicks;
   private int failedAttempts;
   private boolean jumpHeld;
   private boolean warnedNoRockets;
   private int crashClimbUntilTick;
   private float crashSteerYaw;
   private float crashSteerPitch;
   private Vec3d crashSteerDir;
   private int crashRocketReadyTick;
   private int crashGuardTraceTick = Integer.MIN_VALUE;
   private static final int CRASH_CLIMB_HOLD_TICKS = 6;
   private static final int CRASH_ROCKET_COOLDOWN_TICKS = 15;
   private static final double CRASH_SIDE_OFFSET = 0.6;
   private static final int ACTIVATE_AFTER_AIR_TICKS = 3;
   private static final int LAUNCH_SETTLED_TICKS = 20;

   public BaritoneElytraOverworld() {
      super(
         Categories.Movement,
         "baritone-elytra-overworld",
         "Lets Baritone's elytra pathing work outside the Nether and auto-switches its terrain prediction per dimension (on in the Nether, off elsewhere)."
      );
      ElytraDurabilitySaver.get().register(this::isActive, this.saveDurability::get);
   }

   public static boolean isUnlocked() {
      return unlocked;
   }

   public static int goalY() {
      return goalYValue;
   }

   public static int landingRetryDrop() {
      return landingRetryDropValue;
   }

   public static int takeoffSearchY(int original) {
      MinecraftClient client = MinecraftClient.getInstance();
      return client.world != null ? client.world.getBottomY() : original;
   }

   public static boolean isLandingBlock(Block block) {
      BlockState state = block.getDefaultState();
      return !state.isAir() && state.getFluidState().isEmpty() && state.isOpaque();
   }

   public static void onPathStartClamped(int fromY, int toY) {
      if (debugValue) {
         long now = System.currentTimeMillis();
         if (now - lastClampNoticeMs >= 5000L) {
            lastClampNoticeMs = now;
            MinecraftClient.getInstance().execute(() -> {
               BaritoneElytraOverworld module = Modules.get().get(BaritoneElytraOverworld.class);
               if (module != null && module.isActive()) {
                  module.info("You are at y=%d, above what the pathfinder can model; planning from y=%d below you.", new Object[]{fromY, toY});
               }
            });
         }
      }
   }

   @Override
   public void onActivate() {
      goalYValue = this.overworldGoalY.get();
      landingRetryDropValue = this.landingRetryDrop.get();
      debugValue = this.debug.get();
      this.lastDimension = null;
      this.hooksOk = false;
      this.nativeBuild = false;
      this.savedAutoJump = null;
      this.savedStandingTakeoff = null;
      this.resetTakeoff();
      this.failedAttempts = 0;
      this.warnedNoRockets = false;
      this.crashClimbUntilTick = 0;
      this.crashRocketReadyTick = 0;
      this.crashGuardTraceTick = Integer.MIN_VALUE;
      this.crashSteerDir = null;
      if (!BaritoneBridge.isPresent()) {
         this.error("Baritone is not installed, so there is nothing to unlock. Terrain prediction switching is off too.", new Object[0]);
      } else {
         this.nativeBuild = BaritoneBridge.hasNativeOverworldElytra();
         if (this.nativeBuild) {
            if (this.debug.get()) {
               this.info(
                  "The installed Baritone flies the Overworld natively; only the Nether prediction preference, auto-jump and the arrival height are managed here.",
                  new Object[0]
               );
            }
         } else {
            List<String> failures = BaritoneMixinGuard.failures();
            this.hooksOk = failures.isEmpty();
            if (!this.hooksOk) {
               this.error(
                  "The installed Baritone build does not have the hook points this module needs, so elytra stays Nether-only (terrain prediction switching still works): %s",
                  new Object[]{String.join(" | ", failures)}
               );
            }
         }

         this.refresh(true);
      }
   }

   public boolean isNativeBuild() {
      return this.nativeBuild;
   }

   @Override
   public void onDeactivate() {
      unlocked = false;
      this.lastDimension = null;
      this.resetTakeoff();
      if (BaritoneBridge.isPresent()) {
         if (!this.nativeBuild && this.mc.world != null && this.mc.world.getRegistryKey() != World.NETHER && BaritoneBridge.elytraHasDestination()) {
            BaritoneBridge.cancelEverything();
            if (this.debug.get()) {
               this.info("Cancelled Baritone elytra: unlock disabled.", new Object[0]);
            }
         }

         if (BaritoneBridge.isElytraPredictTerrain() != this.predictTerrainInNether.get()) {
            BaritoneBridge.setElytraPredictTerrain(this.predictTerrainInNether.get());
            if (this.debug.get()) {
               this.info("elytraPredictTerrain restored to %s.", new Object[]{this.predictTerrainInNether.get()});
            }
         }

         this.applyAutoJump();
      }
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.lastDimension = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.world == null) {
         unlocked = false;
         this.lastDimension = null;
      } else {
         this.refresh(false);
      }
   }

   @EventHandler
   private void onPreTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && unlocked && this.autoJump.get() && BaritoneBridge.elytraWaitingForTakeoff()) {
         this.tickTakeoff();
      } else {
         if (this.takeoff != BaritoneElytraOverworld.Takeoff.IDLE || this.jumpHeld) {
            this.resetTakeoff();
         }

         if (!BaritoneBridge.elytraWaitingForTakeoff()) {
            this.failedAttempts = 0;
            this.warnedNoRockets = false;
         }
      }
   }

   @EventHandler(
      priority = -200
   )
   private void onCrashGuardTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.tickCrashGuard(this.mc.player.age);
      }
   }

   private void tickCrashGuard(int now) {
      if (this.crashGuard.get() && unlocked && this.isActive() && this.mc.player.isGliding()) {
         if (BaritoneBridge.isPresent() && BaritoneBridge.elytraHasDestination()) {
            if (this.takeoff == BaritoneElytraOverworld.Takeoff.IDLE) {
               Vec3d vel = this.mc.player.getVelocity();
               double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
               boolean holding = now <= this.crashClimbUntilTick;
               if (!(speed < 0.05) || holding) {
                  double dx;
                  double dz;
                  if (speed >= 0.05) {
                     dx = vel.x / speed;
                     dz = vel.z / speed;
                  } else {
                     Vec3d look = this.mc.player.getRotationVector();
                     double h = Math.sqrt(look.x * look.x + look.z * look.z);
                     if (h < 1.0E-4) {
                        return;
                     }

                     dx = look.x / h;
                     dz = look.z / h;
                  }

                  double reach = MathHelper.clamp(speed * this.crashLookahead.get().intValue(), 6.0, 64.0);
                  Vec3d ahead = new Vec3d(dx, -0.15, dz).normalize();
                  double forwardClear = this.clearance(ahead, dx, dz, reach);
                  boolean blockedFwd = forwardClear < reach - 0.5;
                  if (blockedFwd) {
                     double up = Math.toRadians(-this.crashClimbPitch.get());
                     double[] pitches = new double[]{up, up * 0.5, 0.0, -up * 0.5, -up};
                     double[] yaws = new double[]{
                        0.0,
                        Math.toRadians(25.0),
                        Math.toRadians(-25.0),
                        Math.toRadians(50.0),
                        Math.toRadians(-50.0),
                        Math.toRadians(75.0),
                        Math.toRadians(-75.0),
                        Math.toRadians(90.0),
                        Math.toRadians(-90.0)
                     };
                     Vec3d best = null;
                     double bestDev = Double.MAX_VALUE;
                     double bestClear = -1.0;
                     boolean bestIsClear = false;

                     for (double p : pitches) {
                        double cosP = Math.cos(p);
                        double sinP = Math.sin(p);

                        for (double a : yaws) {
                           double cosA = Math.cos(a);
                           double sinA = Math.sin(a);
                           double hx = dx * cosA - dz * sinA;
                           double hz = dx * sinA + dz * cosA;
                           Vec3d cand = new Vec3d(hx * cosP, sinP, hz * cosP).normalize();
                           double clear = this.clearance(cand, hx, hz, reach);
                           boolean isClear = clear >= reach - 0.5;
                           double dev = Math.abs(a) + Math.abs(p);
                           boolean better = bestIsClear
                              ? isClear && dev < bestDev
                              : isClear || clear > bestClear + 0.5 || Math.abs(clear - bestClear) <= 0.5 && dev < bestDev;
                           if (best == null || better) {
                              best = cand;
                              bestDev = dev;
                              bestClear = clear;
                              bestIsClear = isClear;
                           }
                        }
                     }

                     if (!bestIsClear) {
                        double sinUp = Math.sin(up);
                        double cosUp = Math.cos(up);
                        best = new Vec3d(dx * cosUp, sinUp, dz * cosUp).normalize();
                     }

                     double horizLen = Math.sqrt(best.x * best.x + best.z * best.z);
                     this.crashSteerYaw = (float)Math.toDegrees(Math.atan2(-best.x, best.z));
                     this.crashSteerPitch = (float)(-Math.toDegrees(Math.atan2(best.y, horizLen)));
                     this.crashSteerDir = best;
                     this.crashClimbUntilTick = now + 6;
                     if (this.debug.get() && (this.crashGuardTraceTick == Integer.MIN_VALUE || now - this.crashGuardTraceTick > 30)) {
                        this.crashGuardTraceTick = now;
                        this.info(
                           "Crash guard: block %.0f ahead - %s (%.0f deg turn, pitch %.0f).",
                           new Object[]{
                              forwardClear,
                              bestIsClear ? "steering clear" : "no clear line, climbing",
                              this.crashSteerYaw - (float)Math.toDegrees(Math.atan2(-dx, dz)),
                              this.crashSteerPitch
                           }
                        );
                     }

                     boolean imminent = forwardClear < Math.max(6.0, speed * 6.0);
                     if (this.crashRocket.get() && bestIsClear && imminent && now >= this.crashRocketReadyTick) {
                        this.crashRocketReadyTick = now + 15;
                        HuntingUtils.firework(this.mc, false);
                     }
                  }

                  if (now <= this.crashClimbUntilTick) {
                     this.mc.player.setYaw(this.crashSteerYaw);
                     this.mc.player.setPitch(this.crashSteerPitch);
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = -200
   )
   private void onCrashGuardVelocity(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.world != null && this.crashSteerDir != null) {
         if (this.crashGuard.get() && unlocked && this.isActive() && this.mc.player.isGliding()) {
            if (BaritoneBridge.isPresent() && BaritoneBridge.elytraHasDestination()) {
               if (this.mc.player.age <= this.crashClimbUntilTick) {
                  Vec3d v = this.mc.player.getVelocity();
                  double speed = v.length();
                  if (!(speed < 0.05)) {
                     this.mc.player.setVelocity(this.crashSteerDir.multiply(speed));
                  }
               }
            }
         }
      }
   }

   private double clearance(Vec3d dir, double hx, double hz, double reach) {
      Vec3d eye = this.mc.player.getEyePos();
      Vec3d body = eye.subtract(0.0, 1.2, 0.0);
      Vec3d perp = new Vec3d(-hz, 0.0, hx).multiply(0.6);
      double d = Math.min(reach, this.rayDist(eye, dir, reach));
      d = Math.min(d, this.rayDist(body, dir, reach));
      d = Math.min(d, this.rayDist(eye.add(perp), dir, reach));
      return Math.min(d, this.rayDist(eye.subtract(perp), dir, reach));
   }

   private double rayDist(Vec3d start, Vec3d dir, double reach) {
      Vec3d end = start.add(dir.multiply(reach));
      HitResult hit = this.mc.world.raycast(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, this.mc.player));
      return hit.getType() == Type.MISS ? reach : hit.getPos().distanceTo(start);
   }

   private void tickTakeoff() {
      boolean gliding = this.mc.player.isGliding();
      boolean onGround = this.mc.player.isOnGround();
      switch (this.takeoff) {
         case IDLE:
         case GAVE_UP:
            if (this.takeoff == BaritoneElytraOverworld.Takeoff.GAVE_UP || gliding) {
               return;
            }

            if (!onGround) {
               return;
            }

            if (!this.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
               return;
            }

            if (!this.ensureRocketInHotbar()) {
               return;
            }

            if (this.mc.player.isTouchingWater() || this.mc.player.isInLava() || this.mc.player.hasVehicle()) {
               return;
            }

            if (++this.takeoffTicks < this.takeoffDelay.get()) {
               return;
            }

            this.takeoff = BaritoneElytraOverworld.Takeoff.JUMPING;
            this.takeoffTicks = 0;
            if (this.debug.get()) {
               this.info("Auto-jump: taking off (attempt %d/%d).", new Object[]{this.failedAttempts + 1, this.maxAttempts.get()});
            }
            break;
         case JUMPING:
            if (onGround) {
               this.setJump(true);
               this.takeoffTicks = 0;
            } else {
               this.setJump(false);
               if (++this.takeoffTicks >= 3) {
                  this.takeoff = BaritoneElytraOverworld.Takeoff.OPENING;
                  this.takeoffTicks = 0;
               }
            }
            break;
         case OPENING:
            if (gliding) {
               this.setJump(false);
               this.mc.player.setPitch(this.takeoffPitch.get().floatValue());
               if (HuntingUtils.firework(this.mc, false) == -1) {
                  this.warning("Auto-jump: the elytra opened but no rocket could be fired.", new Object[0]);
               }

               this.takeoff = BaritoneElytraOverworld.Takeoff.LAUNCHED;
               this.takeoffTicks = 0;
            } else if (onGround) {
               this.attemptFailed("the elytra did not open before landing");
            } else {
               if (PbPredict.serverPushesOnly()) {
                  this.setJump(this.takeoffTicks % 2 == 0);
               } else if (this.takeoffTicks % 2 == 0) {
                  this.sendStartGliding();
               }

               this.takeoffTicks++;
            }
            break;
         case LAUNCHED:
            if (gliding) {
               if (++this.takeoffTicks >= 20) {
                  this.failedAttempts = 0;
                  this.takeoff = BaritoneElytraOverworld.Takeoff.IDLE;
                  this.takeoffTicks = 0;
                  if (this.debug.get()) {
                     this.info("Auto-jump: airborne, Baritone has the flight.", new Object[0]);
                  }
               }
            } else if (onGround) {
               this.attemptFailed("the glide stopped right after takeoff");
            }
      }
   }

   private void attemptFailed(String why) {
      this.setJump(false);
      this.failedAttempts++;
      if (this.failedAttempts >= this.maxAttempts.get()) {
         this.takeoff = BaritoneElytraOverworld.Takeoff.GAVE_UP;
         this.warning(
            "Auto-jump: %s, %d times in a row; giving up until the next flight is started. Try more open ground or start the flight again.",
            new Object[]{why, this.failedAttempts}
         );
      } else {
         this.takeoff = BaritoneElytraOverworld.Takeoff.IDLE;
         this.takeoffTicks = -this.retryDelay.get();
         if (this.debug.get()) {
            this.info("Auto-jump: %s, retrying in %d ticks.", new Object[]{why, this.retryDelay.get()});
         }
      }
   }

   private void resetTakeoff() {
      this.setJump(false);
      this.takeoff = BaritoneElytraOverworld.Takeoff.IDLE;
      this.takeoffTicks = 0;
   }

   private void setJump(boolean pressed) {
      if (this.jumpHeld != pressed) {
         this.jumpHeld = pressed;
         if (this.mc.options != null) {
            this.mc.options.jumpKey.setPressed(pressed);
         }
      }
   }

   private void sendStartGliding() {
      if (this.mc.getNetworkHandler() != null) {
         this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
      }
   }

   private boolean ensureRocketInHotbar() {
      if (InvUtils.findInHotbar(Items.FIREWORK_ROCKET).found()) {
         return true;
      } else {
         FindItemResult inInventory = InvUtils.find(Items.FIREWORK_ROCKET);
         if (inInventory.found()) {
            for (int slot = 0; slot < 9; slot++) {
               if (this.mc.player.getInventory().getStack(slot).isEmpty()) {
                  InvUtils.move().from(inInventory.slot()).to(slot);
                  return true;
               }
            }
         }

         if (!this.warnedNoRockets) {
            this.warnedNoRockets = true;
            this.warning(
               inInventory.found()
                  ? "Auto-jump: rockets are in your inventory but the hotbar is full; free a hotbar slot to take off."
                  : "Auto-jump: no firework rockets, so not taking off.",
               new Object[0]
            );
         }

         return false;
      }
   }

   private void refresh(boolean force) {
      if (this.isActive()) {
         RegistryKey<World> dimension = this.mc.world != null ? this.mc.world.getRegistryKey() : null;
         if (force || dimension != this.lastDimension) {
            this.lastDimension = dimension;
            boolean nether = dimension == World.NETHER;
            boolean nowUnlocked = this.hooksOk && dimension != null && !nether;
            if (nowUnlocked != unlocked && this.debug.get()) {
               this.info(nowUnlocked ? "Baritone elytra unlocked for this dimension." : "Baritone elytra back to Nether rules.", new Object[0]);
            }

            unlocked = nowUnlocked;
            if (BaritoneBridge.isPresent() && dimension != null) {
               if (nether || !this.nativeBuild) {
                  boolean predict = nether && this.predictTerrainInNether.get();
                  if (BaritoneBridge.isElytraPredictTerrain() != predict) {
                     BaritoneBridge.setElytraPredictTerrain(predict);
                     if (this.debug.get()) {
                        this.info("elytraPredictTerrain -> %s (%s).", new Object[]{predict, nether ? "Nether" : "not the Nether"});
                     }
                  }
               }

               this.applyAutoJump();
            }
         }
      }
   }

   private void applyAutoJump() {
      if (BaritoneBridge.isPresent()) {
         if (this.nativeBuild) {
            this.applyNativeAutoJump();
         } else if (unlocked && this.isActive() && this.autoJump.get()) {
            if (this.savedAutoJump == null) {
               this.savedAutoJump = BaritoneBridge.isElytraAutoJump();
            }

            if (BaritoneBridge.isElytraAutoJump()) {
               BaritoneBridge.setElytraAutoJump(false);
               if (this.debug.get()) {
                  this.info("elytraAutoJump -> false (the module's auto-jump takes off instead).", new Object[0]);
               }
            }
         } else if (this.savedAutoJump != null) {
            boolean restore = this.savedAutoJump;
            this.savedAutoJump = null;
            if (BaritoneBridge.isElytraAutoJump() != restore) {
               BaritoneBridge.setElytraAutoJump(restore);
               if (this.debug.get()) {
                  this.info("elytraAutoJump restored to %s.", new Object[]{restore});
               }
            }
         }
      }
   }

   private void applyNativeAutoJump() {
      boolean outsideNether = this.mc.world != null && this.mc.world.getRegistryKey() != World.NETHER;
      if (this.isActive() && this.autoJump.get() && outsideNether) {
         if (this.savedAutoJump == null) {
            this.savedAutoJump = BaritoneBridge.isElytraAutoJump();
         }

         if (!BaritoneBridge.isElytraAutoJump()) {
            BaritoneBridge.setElytraAutoJump(true);
            if (this.debug.get()) {
               this.info("elytraAutoJump -> true (this Baritone takes off by itself).", new Object[0]);
            }
         }

         if (this.savedStandingTakeoff == null) {
            this.savedStandingTakeoff = BaritoneBridge.getBooleanSetting("elytraStandingTakeoff", true);
         }

         if (!BaritoneBridge.getBooleanSetting("elytraStandingTakeoff", true)) {
            BaritoneBridge.setBooleanSetting("elytraStandingTakeoff", true);
            if (this.debug.get()) {
               this.info("elytraStandingTakeoff -> true (takeoff from flat ground).", new Object[0]);
            }
         }
      } else {
         if (this.savedAutoJump != null) {
            boolean restore = this.savedAutoJump;
            this.savedAutoJump = null;
            if (BaritoneBridge.isElytraAutoJump() != restore) {
               BaritoneBridge.setElytraAutoJump(restore);
               if (this.debug.get()) {
                  this.info("elytraAutoJump restored to %s.", new Object[]{restore});
               }
            }
         }

         if (this.savedStandingTakeoff != null) {
            boolean restore = this.savedStandingTakeoff;
            this.savedStandingTakeoff = null;
            if (BaritoneBridge.getBooleanSetting("elytraStandingTakeoff", restore) != restore) {
               BaritoneBridge.setBooleanSetting("elytraStandingTakeoff", restore);
               if (this.debug.get()) {
                  this.info("elytraStandingTakeoff restored to %s.", new Object[]{restore});
               }
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      if (this.mc.world == null) {
         return null;
      } else if (this.mc.world.getRegistryKey() == World.NETHER) {
         return "nether";
      } else if (this.nativeBuild) {
         return "native";
      } else if (!unlocked) {
         return "locked";
      } else {
         return switch (this.takeoff) {
            case IDLE -> "unlocked";
            case JUMPING, OPENING -> "taking off";
            case LAUNCHED -> "launched";
            case GAVE_UP -> "takeoff failed";
         };
      }
   }

   private static enum Takeoff {
      IDLE,
      JUMPING,
      OPENING,
      LAUNCHED,
      GAVE_UP;
   }
}
