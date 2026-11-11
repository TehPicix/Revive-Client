package revivedevelopment.reviveclient.systems.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.PbState;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.hunting.HuntingUtils;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class ElytraRecast extends Module {
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
   private final SettingGroup sgWallSafety = this.settings.createGroup("Wall Safety");
   private final SettingGroup sgNether = this.settings.createGroup("Nether");
   private final SettingGroup sgOverworld = this.settings.createGroup("Overworld/End");
   private final Setting<Boolean> disableIfNoRockets = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("disable-if-no-rockets")
            .description("Automatically disable ElytraRecast when no firework rockets are available.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> debugMessages = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug-messages")
            .description("Show debug messages in chat for state changes and recovery events.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> activationDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("activation-delay")
            .description("Ticks to wait between jump and elytra activation attempts.")
            .defaultValue(3)
            .range(1, 20)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Integer> rocketDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("rocket-delay")
            .description("Minimum ticks between rocket usages during ascent.")
            .defaultValue(15)
            .range(5, 60)
            .sliderRange(5, 60)
            .build()
      );
   private final Setting<Boolean> smartRockets = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("smart-rockets")
            .description("Only fire a rocket when the previous boost has run out and climb speed drops, instead of on a fixed timer.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> minClimbSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-climb-speed")
            .description("Fire the next rocket only when vertical speed drops below this during ascent.")
            .defaultValue(0.5)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .visible(this.smartRockets::get)
            .build()
      );
   private final Setting<Double> rotationSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rotation-speed")
            .description("Degrees per tick to adjust pitch when ascending. Lower = smoother & more realistic.")
            .defaultValue(3.0)
            .range(0.5, 15.0)
            .sliderRange(0.5, 15.0)
            .build()
      );
   private final Setting<Double> targetPitch = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-pitch")
            .description("Target pitch angle when ascending. Negative = looking up.")
            .defaultValue(-70.0)
            .range(-89.0, 0.0)
            .sliderRange(-89.0, 0.0)
            .build()
      );
   private final Setting<Integer> wallClearance = this.sgWallSafety
      .add(
         new IntSetting.Builder()
            .name("wall-clearance")
            .description("Blocks of clear space needed in the look direction before firing a rocket. Prevents boosting into walls.")
            .defaultValue(20)
            .range(5, 50)
            .sliderRange(5, 50)
            .build()
      );
   private final Setting<ElytraRecast.WallHitAction> wallHitAction = this.sgWallSafety
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("on-wall-hit"))
                  .description("What to do when you take fly-into-wall damage."))
               .defaultValue(ElytraRecast.WallHitAction.Pause))
            .build()
      );
   private final Setting<Integer> pauseDuration = this.sgWallSafety
      .add(
         new IntSetting.Builder()
            .name("pause-duration")
            .description("Ticks to pause recovery after taking wall damage.")
            .defaultValue(20)
            .range(0, 200)
            .sliderRange(0, 200)
            .visible(() -> this.wallHitAction.get() == ElytraRecast.WallHitAction.Pause)
            .build()
      );
   private final Setting<Integer> maxWallHits = this.sgWallSafety
      .add(
         new IntSetting.Builder()
            .name("max-wall-hits")
            .description("Disable after this many wall impacts without 30 seconds of damage-free flight.")
            .defaultValue(3)
            .range(1, 10)
            .sliderRange(1, 10)
            .visible(() -> this.wallHitAction.get() == ElytraRecast.WallHitAction.Pause)
            .build()
      );
   private final Setting<Integer> netherFallDelay = this.sgNether
      .add(
         new IntSetting.Builder()
            .name("fall-delay")
            .description("Ticks to wait after stopping flight before triggering recovery in Nether.")
            .defaultValue(5)
            .range(1, 40)
            .sliderRange(1, 40)
            .build()
      );
   private final Setting<Boolean> usePitch40Bounds = this.sgOverworld
      .add(
         new BoolSetting.Builder()
            .name("use-pitch40-bounds")
            .description(
               "Derive altitudes from ElytraFly's Pitch40 bounds: recover below its lower bound, ascend back above its upper bound. Falls back to the settings below when Pitch40 is inactive."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> overworldTargetAltitude = this.sgOverworld
      .add(
         new IntSetting.Builder()
            .name("target-altitude")
            .description("Target Y level to ascend to in Overworld/End. Ignored if use-pitch40-bounds is enabled.")
            .defaultValue(360)
            .sliderRange(50, 400)
            .visible(() -> !this.usePitch40Bounds.get())
            .build()
      );
   private final Setting<Integer> overworldMinAltitude = this.sgOverworld
      .add(
         new IntSetting.Builder()
            .name("min-altitude")
            .description("Trigger recovery if Y drops below this in Overworld/End. Ignored if use-pitch40-bounds is enabled.")
            .defaultValue(310)
            .sliderRange(10, 400)
            .visible(() -> !this.usePitch40Bounds.get())
            .build()
      );
   private ElytraRecast.State state;
   private int tickCounter;
   private int rocketTickCounter;
   private boolean usedActivationRocket;
   private int notGlidingTicks;
   private boolean netherRocketUsed;
   private ElytraFly elytraFly;
   private int pauseTicks;
   private int wallHits;
   private int ticksSinceWallHit;
   private volatile boolean pendingWallHit;
   private static final int WALL_HIT_RESET_TICKS = 600;

   public ElytraRecast() {
      super(Categories.Movement, "elytra-recast", "Flight recovery fallback. Monitors and recovers when you stop flying or drop too low.");
      ElytraDurabilitySaver.get().register(this::isActive, this.saveDurability::get);
   }

   @Override
   public void onActivate() {
      if (this.mc.player != null) {
         if (!this.hasElytraEquipped()) {
            this.error("No elytra equipped!", new Object[0]);
            this.toggle();
         } else {
            this.tickCounter = 0;
            this.rocketTickCounter = 0;
            this.usedActivationRocket = false;
            this.notGlidingTicks = 0;
            this.netherRocketUsed = false;
            this.elytraFly = Modules.get().get(ElytraFly.class);
            this.pauseTicks = 0;
            this.wallHits = 0;
            this.ticksSinceWallHit = 0;
            this.pendingWallHit = false;
            this.state = ElytraRecast.State.MONITORING;
            if (this.debugMessages.get()) {
               this.info("Monitoring flight...", new Object[0]);
            }
         }
      }
   }

   @Override
   public void onDeactivate() {
      this.state = null;
      if (this.mc.options != null && !steeredByPlayer()) {
         this.mc.options.jumpKey.setPressed(false);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.state != null && this.hasElytraEquipped()) {
         if (this.pendingWallHit) {
            this.pendingWallHit = false;
            this.handleWallHit();
            if (!this.isActive()) {
               return;
            }
         }

         this.ticksSinceWallHit++;
         if (this.wallHits > 0 && this.ticksSinceWallHit > 600) {
            this.wallHits = 0;
         }

         this.tickCounter++;
         this.rocketTickCounter++;
         switch (this.state) {
            case MONITORING:
               this.handleMonitoring();
               break;
            case JUMPING:
               this.handleJumping();
               break;
            case ACTIVATING:
               this.handleActivating();
               break;
            case ASCENDING:
               this.handleAscending();
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (event.packet instanceof EntityDamageS2CPacket packet && packet.entityId() == this.mc.player.getId()) {
            DamageSource source = packet.createDamageSource(this.mc.world);
            if (source != null && source.isOf(DamageTypes.FLY_INTO_WALL)) {
               this.pendingWallHit = true;
            }
         }
      }
   }

   private void handleWallHit() {
      this.wallHits++;
      this.ticksSinceWallHit = 0;
      if (this.wallHitAction.get() == ElytraRecast.WallHitAction.Disable) {
         this.error("Flew into a wall! Disabling.", new Object[0]);
         this.toggle();
      } else if (this.wallHits >= this.maxWallHits.get()) {
         this.error("Flew into a wall " + this.wallHits + " times! Disabling.", new Object[0]);
         this.toggle();
      } else {
         this.warning(
            "Flew into a wall (" + this.wallHits + "/" + this.maxWallHits.get() + ")! Pausing recovery for " + this.pauseDuration.get() + " ticks.",
            new Object[0]
         );
         this.state = ElytraRecast.State.MONITORING;
         this.pauseTicks = this.pauseDuration.get();
         this.tickCounter = 0;
         this.notGlidingTicks = 0;
         this.usedActivationRocket = false;
         this.netherRocketUsed = false;
      }
   }

   private void handleMonitoring() {
      if (this.pauseTicks > 0) {
         this.pauseTicks--;
         this.notGlidingTicks = 0;
      } else {
         boolean currentlyGliding = this.mc.player.isGliding();
         double currentY = this.mc.player.getY();
         if (this.isInNether()) {
            if (!currentlyGliding) {
               this.notGlidingTicks++;
               if (this.notGlidingTicks >= this.netherFallDelay.get()) {
                  if (this.debugMessages.get()) {
                     this.info("Nether: Flight stopped for " + this.notGlidingTicks + " ticks! Recovering...", new Object[0]);
                  }

                  this.triggerRecovery();
                  this.notGlidingTicks = 0;
               }
            } else {
               this.notGlidingTicks = 0;
            }
         } else {
            double minAlt = this.getEffectiveMinAltitude();
            if (currentY < minAlt) {
               if (!currentlyGliding) {
                  if (this.debugMessages.get()) {
                     this.info("Below min altitude (Y=" + (int)currentY + " < " + (int)minAlt + ") and not flying! Recovering...", new Object[0]);
                  }

                  this.triggerRecovery();
                  return;
               }

               if (this.debugMessages.get()) {
                  this.info("Altitude too low (Y=" + (int)currentY + " < " + (int)minAlt + ")! Ascending...", new Object[0]);
               }

               this.triggerRecovery();
            }
         }
      }
   }

   private boolean pitch40Active() {
      return this.elytraFly != null && this.elytraFly.isActive() && this.elytraFly.flightMode.get() == ElytraFlightModes.Pitch40;
   }

   private double getEffectiveMinAltitude() {
      return this.usePitch40Bounds.get() && this.pitch40Active() ? this.elytraFly.pitch40Lower() - 10.0 : this.overworldMinAltitude.get().intValue();
   }

   private double getEffectiveTargetAltitude() {
      return this.usePitch40Bounds.get() && this.pitch40Active()
         ? Math.max(this.elytraFly.pitch40Upper(), this.elytraFly.pitch40Lower() + 40.0) + 2.0
         : this.overworldTargetAltitude.get().intValue();
   }

   private void triggerRecovery() {
      if (!this.hasRockets()) {
         if (this.disableIfNoRockets.get()) {
            this.error("No firework rockets available! Disabling ElytraRecast.", new Object[0]);
            this.toggle();
            return;
         }

         this.warning("No firework rockets available! Recovery may fail.", new Object[0]);
      }

      this.tickCounter = 0;
      this.rocketTickCounter = this.rocketDelay.get();
      this.usedActivationRocket = false;
      this.netherRocketUsed = false;
      if (this.mc.player.isGliding()) {
         this.state = ElytraRecast.State.ASCENDING;
      } else if (this.mc.player.isOnGround()) {
         this.state = ElytraRecast.State.JUMPING;
      } else {
         this.state = ElytraRecast.State.ACTIVATING;
      }
   }

   private void handleJumping() {
      if (this.mc.player.isOnGround()) {
         if (!steeredByPlayer()) {
            this.mc.options.jumpKey.setPressed(true);
         }

         this.tickCounter = 0;
      } else {
         if (!steeredByPlayer()) {
            this.mc.options.jumpKey.setPressed(false);
         }

         if (this.tickCounter >= this.activationDelay.get()) {
            this.state = ElytraRecast.State.ACTIVATING;
            this.tickCounter = 0;
         }
      }
   }

   private void handleActivating() {
      if (this.mc.player.isGliding()) {
         this.state = ElytraRecast.State.ASCENDING;
         this.rocketTickCounter = this.rocketDelay.get();
         this.tickCounter = 0;
      } else if (this.mc.player.isOnGround()) {
         this.state = ElytraRecast.State.JUMPING;
         this.tickCounter = 0;
         this.usedActivationRocket = false;
      } else {
         if (this.tickCounter % 2 == 0) {
            this.sendElytraPacket();
         }

         if (this.tickCounter > 15 && !this.usedActivationRocket) {
            if (!this.hasRocketClearance()) {
               this.adjustPitchUp();
            } else if (this.useRocket()) {
               this.usedActivationRocket = true;
            } else if (this.disableIfNoRockets.get()) {
               this.error("No rockets for activation! Disabling.", new Object[0]);
               this.toggle();
               return;
            }
         }

         if (this.tickCounter > 40) {
            if (!this.hasRockets() && this.disableIfNoRockets.get()) {
               this.error("No rockets and activation failed! Disabling.", new Object[0]);
               this.toggle();
               return;
            }

            this.state = ElytraRecast.State.JUMPING;
            this.tickCounter = 0;
            this.usedActivationRocket = false;
         }
      }
   }

   private void handleAscending() {
      if (!this.mc.player.isGliding()) {
         if (!this.hasRockets() && this.disableIfNoRockets.get()) {
            this.error("Lost flight and no rockets! Disabling.", new Object[0]);
            this.toggle();
         } else {
            this.state = ElytraRecast.State.ACTIVATING;
            this.tickCounter = 0;
            this.usedActivationRocket = false;
         }
      } else {
         if (this.isInNether()) {
            this.adjustPitchUp();
            if (!this.netherRocketUsed && this.hasRocketClearance()) {
               if (this.useRocket()) {
                  this.netherRocketUsed = true;
               } else if (this.disableIfNoRockets.get()) {
                  this.error("No rockets for Nether recovery! Disabling.", new Object[0]);
                  this.toggle();
                  return;
               }
            }

            if (this.netherRocketUsed ? this.tickCounter > 10 : this.tickCounter > 40) {
               if (this.debugMessages.get()) {
                  this.info(
                     this.netherRocketUsed
                        ? "Nether recovery complete - baritone takes over."
                        : "Nether recovery: no clear path for a rocket, handing back to monitoring.",
                     new Object[0]
                  );
               }

               this.state = ElytraRecast.State.MONITORING;
               this.tickCounter = 0;
               this.netherRocketUsed = false;
            }
         } else {
            double currentY = this.mc.player.getY();
            double target = this.getEffectiveTargetAltitude();
            if (currentY >= target) {
               if (this.debugMessages.get()) {
                  this.info("Target altitude Y=" + (int)target + " reached! Resuming monitoring.", new Object[0]);
               }

               this.state = ElytraRecast.State.MONITORING;
               this.tickCounter = 0;
               return;
            }

            this.adjustPitchUp();
            if (this.rocketTickCounter >= this.rocketDelay.get() && this.shouldBoost()) {
               if (this.useRocket()) {
                  this.rocketTickCounter = 0;
               } else if (this.disableIfNoRockets.get()) {
                  this.error("Out of rockets during ascent! Disabling.", new Object[0]);
                  this.toggle();
               }
            }
         }
      }
   }

   private boolean shouldBoost() {
      return !this.hasRocketClearance() ? false : !this.smartRockets.get() || !this.isBoosted() && this.mc.player.getVelocity().y < this.minClimbSpeed.get();
   }

   private boolean hasRocketClearance() {
      Vec3d eye = this.mc.player.getEyePos();
      Vec3d end = eye.add(this.mc.player.getRotationVector().multiply(this.wallClearance.get().intValue()));
      return this.mc.world.raycast(new RaycastContext(eye, end, ShapeType.COLLIDER, FluidHandling.NONE, this.mc.player)).getType() == Type.MISS;
   }

   private boolean isBoosted() {
      return !this.mc
         .world
         .getEntitiesByClass(FireworkRocketEntity.class, this.mc.player.getBoundingBox().expand(2.0), rocket -> !rocket.wasShotAtAngle())
         .isEmpty();
   }

   private void adjustPitchUp() {
      if (!steeredByPlayer()) {
         float currentPitch = this.mc.player.getPitch();
         float target = this.targetPitch.get().floatValue();
         float speed = this.rotationSpeed.get().floatValue();
         float diff = target - currentPitch;
         if (!(Math.abs(diff) < 0.5F)) {
            float adjustment;
            if (Math.abs(diff) <= speed) {
               adjustment = diff;
            } else {
               adjustment = diff > 0.0F ? speed : -speed;
            }

            float newPitch = Math.max(-89.0F, Math.min(89.0F, currentPitch + adjustment));
            this.mc.player.setPitch(newPitch);
         }
      }
   }

   private void sendElytraPacket() {
      if (!actsOnVps()) {
         if (this.mc.player != null && this.mc.getNetworkHandler() != null) {
            this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
         }
      }
   }

   private boolean hasRockets() {
      return InvUtils.findInHotbar(Items.FIREWORK_ROCKET).found() || InvUtils.find(Items.FIREWORK_ROCKET).found();
   }

   private boolean useRocket() {
      if (actsOnVps()) {
         return this.hasRockets();
      } else {
         FindItemResult hotbar = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
         if (!hotbar.found()) {
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (!inv.found()) {
               return false;
            }

            int hotbarSlot = this.findEmptyHotbarSlot();
            if (hotbarSlot != -1) {
               InvUtils.move().from(inv.slot()).to(hotbarSlot);
            }
         }

         HuntingUtils.firework(this.mc, false);
         return true;
      }
   }

   private int findEmptyHotbarSlot() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   private static boolean steeredByPlayer() {
      return PbState.lookOnlyInTicks;
   }

   private static boolean actsOnVps() {
      return PbPredict.serverPushesOnly();
   }

   private boolean hasElytraEquipped() {
      return this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
   }

   private boolean isInNether() {
      return this.mc.world != null && this.mc.world.getRegistryKey() == World.NETHER;
   }

   @Override
   public String getInfoString() {
      if (this.state == null) {
         return null;
      } else if (this.mc.player == null) {
         return this.state.name();
      } else {
         double minAlt = this.isInNether() ? 0.0 : this.getEffectiveMinAltitude();

         return switch (this.state) {
            case MONITORING -> this.pauseTicks > 0
               ? String.format("PAUSED %d", this.pauseTicks)
               : (this.isInNether() ? String.format("Y=%.0f", this.mc.player.getY()) : String.format("Y=%.0f (min:%.0f)", this.mc.player.getY(), minAlt));
            case JUMPING -> "JUMP";
            case ACTIVATING -> "ACTIVATE";
            case ASCENDING -> this.isInNether()
               ? String.format("^%.0f", this.mc.player.getY())
               : String.format("^%.0f/%.0f", this.mc.player.getY(), this.getEffectiveTargetAltitude());
         };
      }
   }

   private static enum State {
      MONITORING,
      JUMPING,
      ACTIVATING,
      ASCENDING;
   }

   public static enum WallHitAction {
      Pause,
      Disable;
   }
}
