package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IPlayerInventory;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.BaritoneBridge;
import revivedevelopment.reviveclient.utils.compat.XaeroCompat;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.Rotations;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class TrailFollower extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Integer> maxTrailLength = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-trail-length")
            .description(
               "The number of trail points to keep for the average. Adjust to change how quickly the average will change. More does not necessarily equal better because if the list is too long it will contain chunks behind you."
            )
            .defaultValue(20)
            .sliderRange(1, 100)
            .build()
      );
   public final Setting<Integer> chunksBeforeStarting = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("chunks-before-starting")
            .description("Useful for afking looking for a trail. The amount of chunks before it gets detected as a trail.")
            .defaultValue(10)
            .sliderRange(1, 50)
            .build()
      );
   public final Setting<Integer> chunkConsiderationWindow = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("chunk-timeframe")
            .description("The amount of time in seconds that the chunks must be found in before starting.")
            .defaultValue(5)
            .sliderRange(1, 20)
            .build()
      );
   public final Setting<TrailFollower.TrailEndBehavior> trailEndBehavior = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("trail-end-behavior"))
                  .description("What to do when the trail ends."))
               .defaultValue(TrailFollower.TrailEndBehavior.DISABLE))
            .build()
      );
   public final Setting<Double> trailEndYaw = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("trail-end-yaw")
            .description("The direction to go after the trail is abandoned.")
            .defaultValue(0.0)
            .sliderRange(0.0, 359.9)
            .visible(() -> this.trailEndBehavior.get() == TrailFollower.TrailEndBehavior.FLY_TOWARDS_YAW)
            .build()
      );
   public final Setting<TrailFollower.OverworldFlightMode> overworldFlightMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("overworld-flight-mode"))
                  .description(
                     "Choose how TrailFollower flies in Overworld. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail."
                  ))
               .defaultValue(TrailFollower.OverworldFlightMode.PITCH40))
            .build()
      );
   public final Setting<TrailFollower.NetherPathMode> netherPathMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("nether-path-mode"))
                  .description(
                     "Choose how TrailFollower does baritone pathing in Nether. If other is selected then nothing will be automatically enabled, instead just your yaw will be changed to point towards the trail."
                  ))
               .defaultValue(TrailFollower.NetherPathMode.AVERAGE))
            .build()
      );
   public final Setting<Boolean> pitch40Firework = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-firework")
            .description("Uses a firework automatically if your velocity is too low.")
            .defaultValue(true)
            .visible(() -> this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40)
            .build()
      );
   public final Setting<Double> rotateScaling = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rotate-scaling")
            .description("Scaling of how fast the yaw changes. 1 = instant, 0 = doesn't change")
            .defaultValue(0.1)
            .sliderRange(0.0, 1.0)
            .build()
      );
   public final Setting<Boolean> oppositeDimension = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("opposite-dimension")
            .description("Follows trails from the opposite dimension (Requires that you've already loaded the other dimension with XP).")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> autoElytra = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-start-baritone-elytra")
            .description(
               "Automatically starts Baritone elytra when following in Baritone mode (runs the elytra command for you). In the Nether always; in the Overworld/End only with a Baritone that can fly there (the HunterBuddy Overworld build, or BaritoneElytraOverworld's unlock), where AUTO flight-method then picks Baritone mode too."
            )
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> elytraReplace = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("elytra-replace")
            .description("Automatically swaps in a fresh elytra from your inventory when the equipped one's durability gets too low.")
            .defaultValue(false)
            .build()
      );
   public final Setting<Integer> elytraReplaceDurability = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("replace-durability")
            .description("Remaining durability at which the equipped elytra is replaced.")
            .defaultValue(50)
            .range(1, (Integer)Items.ELYTRA.getComponents().get(DataComponentTypes.MAX_DAMAGE) - 1)
            .sliderRange(1, (Integer)Items.ELYTRA.getComponents().get(DataComponentTypes.MAX_DAMAGE) - 1)
            .visible(this.elytraReplace::get)
            .build()
      );
   private final SettingGroup sgAdvanced = this.settings.createGroup("Advanced");
   public final Setting<Double> pathDistance = this.sgAdvanced
      .add(
         new DoubleSetting.Builder()
            .name("path-distance")
            .description("The distance to add trail positions in the direction the player is facing. (Ignored when following overworld from nether)")
            .defaultValue(500.0)
            .sliderRange(100.0, 2000.0)
            .onChanged(value -> this.pathDistanceActual = value)
            .build()
      );
   public final Setting<TrailFollower.FollowMode> flightMethod = this.sgAdvanced
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("flight-method"))
                  .description("Decided how the goals will be used. Leave this on AUTO unless you want to use yaw lock in the nether for example."))
               .defaultValue(TrailFollower.FollowMode.AUTO))
            .build()
      );
   public final Setting<Double> startDirectionWeighting = this.sgAdvanced
      .add(
         new DoubleSetting.Builder()
            .name("start-direction-weight")
            .description(
               "The weighting of the direction the player is facing when starting the trail. 0 for no weighting (not recommended) 1 for max weighting (will take a bit for direction to change)"
            )
            .defaultValue(0.5)
            .min(0.0)
            .sliderMax(1.0)
            .build()
      );
   public final Setting<TrailFollower.DirectionWeighting> directionWeighting = this.sgAdvanced
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("direction-weighting"))
                  .description(
                     "How the chunks found should be weighted. Useful for path splits. Left will weight chunks to the left of the player higher, right will weigh chunks to the right higher, and none will be in the middle/random. "
                  ))
               .defaultValue(TrailFollower.DirectionWeighting.NONE))
            .build()
      );
   public final Setting<Integer> directionWeightingMultiplier = this.sgAdvanced
      .add(
         new IntSetting.Builder()
            .name("direction-weighting-multiplier")
            .description(
               "The multiplier for how much weight should be given to chunks in the direction specified. Values are capped to be in the range [2, maxTrailLength]."
            )
            .defaultValue(2)
            .min(2)
            .sliderMax(10)
            .visible(() -> this.directionWeighting.get() != TrailFollower.DirectionWeighting.NONE)
            .build()
      );
   public final Setting<Boolean> only112 = this.sgAdvanced
      .add(
         new BoolSetting.Builder().name("follow-only-1.12").description("Will only follow 1.12 chunks and will ignore other ones.").defaultValue(false).build()
      );
   public final Setting<Double> chunkFoundTimeout = this.sgAdvanced
      .add(
         new DoubleSetting.Builder()
            .name("chunk-found-timeout")
            .description("The amount of MS without a chunk found to trigger circling.")
            .defaultValue(5000.0)
            .min(1000.0)
            .sliderMax(10000.0)
            .build()
      );
   public final Setting<Double> circlingDegPerTick = this.sgAdvanced
      .add(
         new DoubleSetting.Builder()
            .name("Circling-degrees-per-tick")
            .description("The amount of degrees to change per tick while circling.")
            .defaultValue(2.0)
            .min(1.0)
            .sliderMax(20.0)
            .build()
      );
   public final Setting<Double> trailTimeout = this.sgAdvanced
      .add(
         new DoubleSetting.Builder()
            .name("trail-timeout")
            .description("The amount of MS without a chunk found to stop following the trail.")
            .defaultValue(30000.0)
            .min(10000.0)
            .sliderMax(60000.0)
            .build()
      );
   public final Setting<Double> maxTrailDeviation = this.sgAdvanced
      .add(
         new DoubleSetting.Builder()
            .name("max-trail-deviation")
            .description("Maximum allowed angle (in degrees) from the original trail direction. Helps avoid switching to intersecting trails.")
            .defaultValue(180.0)
            .min(1.0)
            .sliderMax(270.0)
            .build()
      );
   public final Setting<Integer> chunkCacheLength = this.sgAdvanced
      .add(
         new IntSetting.Builder()
            .name("chunk-cache-length")
            .description("The amount of chunks to keep in the cache. (Won't be applied until deactivating)")
            .defaultValue(100000)
            .sliderRange(0, 10000000)
            .build()
      );
   public final Setting<String> webhookLink = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("webhook-link")
            .description("Will send all updates to the webhook link. Leave blank to disable.")
            .defaultValue("")
            .build()
      );
   public final Setting<Integer> baritoneUpdateTicks = this.sgAdvanced
      .add(
         new IntSetting.Builder()
            .name("baritone-path-update-ticks")
            .description("The amount of ticks between updates to the baritone goal. Low values may cause high instability.")
            .defaultValue(100)
            .sliderRange(20, 600)
            .build()
      );
   public final Setting<Boolean> debug = this.sgAdvanced
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Draws debug lines while following a trail: red = current target direction, blue = last trail chunk found.")
            .defaultValue(false)
            .build()
      );
   private boolean oldAutoFireworkValue;
   private TrailFollower.FollowMode followMode;
   private boolean followingTrail = false;
   private ArrayDeque<Vec3d> trail = new ArrayDeque<>();
   private ArrayDeque<Vec3d> possibleTrail = new ArrayDeque<>();
   private long lastFoundTrailTime;
   private long lastFoundPossibleTrailTime;
   private double pathDistanceActual = this.pathDistance.get();
   private TrailFollower.SeenChunksCache seenChunksCache = new TrailFollower.SeenChunksCache();
   boolean started = false;
   private Object chunkListener;
   private double targetYaw;
   private int baritoneSetGoalTicks = 0;
   Vec3d posDebug;

   public TrailFollower() {
      super(Categories.Movement, "trail-follower", "Automatically follows trails in all dimensions.");
   }

   void resetTrail() {
      this.baritoneSetGoalTicks = 0;
      this.followingTrail = false;
      this.trail = new ArrayDeque<>();
      this.possibleTrail = new ArrayDeque<>();
      this.posDebug = null;
   }

   @Override
   public void onActivate() {
      if (!XaeroCompat.XAEROPLUS) {
         this.error("XaeroPlus (with Xaero's World Map) isn't installed - TrailFollower finds trails through it. Disabling TrailFollower", new Object[0]);
         this.toggle();
      } else {
         this.resetTrail();
         if (this.chunkListener == null) {
            this.chunkListener = XaeroCompat.newChunkListener(this::onChunkData);
         }

         XaeroCompat.register(this.chunkListener);
         if (this.started) {
            if (this.mc.player != null && this.mc.world != null) {
               RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
               if (this.oppositeDimension.get()) {
                  if (currentDimension.equals(World.END)) {
                     this.info("There is no opposite dimension to the end. Disabling TrailFollower", new Object[0]);
                     this.toggle();
                     return;
                  }

                  if (currentDimension.equals(World.NETHER)) {
                     this.info("Following overworld trails from the nether is not supported yet, sorry. Disabling TrailFollower", new Object[0]);
                     this.toggle();
                     return;
                  }
               }

               if (this.flightMethod.get() != TrailFollower.FollowMode.AUTO) {
                  this.followMode = this.flightMethod.get();
               } else if (!currentDimension.equals(World.NETHER)) {
                  if (this.autoElytra.get() && BaritoneBridge.canElytraFlyHere()) {
                     this.followMode = TrailFollower.FollowMode.BARITONE;
                     this.info("You are in the overworld or end and this Baritone can fly here, baritone mode will be used.", new Object[0]);
                  } else {
                     this.followMode = TrailFollower.FollowMode.YAWLOCK;
                     this.info("You are in the overworld or end, basic yaw mode will be used.", new Object[0]);
                  }
               } else {
                  if (!BaritoneBridge.isPresent()) {
                     this.info("Baritone is required to trail follow in the nether. Disabling TrailFollower", new Object[0]);
                     this.toggle();
                     return;
                  }

                  this.followMode = TrailFollower.FollowMode.BARITONE;
                  this.info("You are in the nether, baritone mode will be used.", new Object[0]);
               }

               if (this.followMode == TrailFollower.FollowMode.YAWLOCK && !this.mc.world.getRegistryKey().equals(World.NETHER)) {
                  if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40) {
                     Class<? extends Module> pitch40Util = Pitch40Util.class;
                     Module pitch40UtilModule = Modules.get().get(pitch40Util);
                     if (!pitch40UtilModule.isActive()) {
                        pitch40UtilModule.toggle();
                        if (this.pitch40Firework.get()) {
                           Setting<Boolean> setting = (Setting<Boolean>)pitch40UtilModule.settings.get("auto-firework");
                           this.info(
                              "Auto Firework enabled, if you want to change the velocity threshold or the firework cooldown check the settings under Pitch40Util.",
                              new Object[0]
                           );
                           this.oldAutoFireworkValue = setting.get();
                           setting.set(true);
                        }
                     }
                  } else if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.VANILLA) {
                     AFKVanillaFly afkVanillaFly = Modules.get().get(AFKVanillaFly.class);
                     if (!afkVanillaFly.isActive()) {
                        afkVanillaFly.toggle();
                     }
                  }
               }

               Vec3d offset = new Vec3d(Math.sin(-this.mc.player.getYaw() * Math.PI / 180.0), 0.0, Math.cos(-this.mc.player.getYaw() * Math.PI / 180.0))
                  .normalize()
                  .multiply(this.pathDistance.get());
               Vec3d targetPos = this.mc.player.getEntityPos().add(offset);

               for (int i = 0; i < this.maxTrailLength.get().intValue() * this.startDirectionWeighting.get(); i++) {
                  this.trail.add(targetPos);
               }

               this.targetYaw = this.getActualYaw(this.mc.player.getYaw());
            } else {
               this.toggle();
            }

            this.started = true;
         }
      }
   }

   @Override
   public void onDeactivate() {
      this.started = false;
      this.seenChunksCache = new TrailFollower.SeenChunksCache();
      XaeroCompat.unregister(this.chunkListener);
      this.trail.clear();
      if (this.followMode != null) {
         switch (this.followMode) {
            case BARITONE:
               BaritoneBridge.execute("cancel");
               break;
            case YAWLOCK:
               if (this.mc.world == null || this.mc.world.getRegistryKey().equals(World.NETHER)) {
                  return;
               }

               if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.VANILLA) {
                  AFKVanillaFly afkVanillaFly = Modules.get().get(AFKVanillaFly.class);
                  if (afkVanillaFly != null) {
                     afkVanillaFly.resetYLock();
                     if (afkVanillaFly.isActive()) {
                        afkVanillaFly.toggle();
                     }
                  }
               } else if (this.overworldFlightMode.get() == TrailFollower.OverworldFlightMode.PITCH40) {
                  Class<? extends Module> pitch40Util = Pitch40Util.class;
                  Module pitch40UtilModule = Modules.get().get(pitch40Util);
                  if (pitch40UtilModule.isActive()) {
                     pitch40UtilModule.toggle();
                  }

                  Setting<Boolean> setting = (Setting<Boolean>)pitch40UtilModule.settings.get("auto-firework");
                  setting.set(this.oldAutoFireworkValue);
               }
         }
      }
   }

   private void circle() {
      if (this.followMode != TrailFollower.FollowMode.BARITONE) {
         this.mc.player.setYaw(this.getActualYaw((float)(this.mc.player.getYaw() + this.circlingDegPerTick.get())));
         if (this.mc.player.age % 100 == 0) {
            this.log(
               "Circling to look for new chunks, abandoning trail in "
                  + (long)((this.trailTimeout.get() - (System.currentTimeMillis() - this.lastFoundTrailTime)) / 1000.0)
                  + " seconds."
            );
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (!this.started) {
         this.started = true;
         this.onActivate();
      }

      if (this.mc.player != null && this.mc.world != null) {
         if (this.elytraReplace.get()) {
            ItemStack chestStack = (ItemStack)((IPlayerInventory)this.mc.player.getInventory()).revive$getArmor().get(2);
            if (chestStack.getItem() == Items.ELYTRA && chestStack.getMaxDamage() - chestStack.getDamage() <= this.elytraReplaceDurability.get()) {
               FindItemResult elytra = InvUtils.find(
                  stack -> stack.getItem() == Items.ELYTRA && stack.getMaxDamage() - stack.getDamage() > this.elytraReplaceDurability.get()
               );
               if (elytra.found()) {
                  InvUtils.move().from(elytra.slot()).toArmor(2);
               }
            }
         }

         if (this.followingTrail && System.currentTimeMillis() - this.lastFoundTrailTime > this.trailTimeout.get()) {
            this.resetTrail();
            this.log("Trail timed out, stopping.");
            switch ((TrailFollower.TrailEndBehavior)this.trailEndBehavior.get()) {
               case DISABLE:
                  this.toggle();
                  break;
               case FLY_TOWARDS_YAW:
                  this.targetYaw = this.trailEndYaw.get();
                  break;
               case DISCONNECT:
                  this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[TrailFollower] Trail timed out.")));
            }
         }

         if (this.followingTrail && System.currentTimeMillis() - this.lastFoundTrailTime > this.chunkFoundTimeout.get()) {
            this.circle();
         } else if (this.followMode != null) {
            switch (this.followMode) {
               case BARITONE:
                  if (this.baritoneSetGoalTicks > 0) {
                     this.baritoneSetGoalTicks--;
                  } else if (this.baritoneSetGoalTicks == 0) {
                     this.baritoneSetGoalTicks = this.baritoneUpdateTicks.get();
                     if (this.mc.world.getRegistryKey().equals(World.NETHER)) {
                        if (!this.trail.isEmpty()) {
                           Vec3d baritoneTarget;
                           if (this.netherPathMode.get() == TrailFollower.NetherPathMode.AVERAGE) {
                              Vec3d averagePos = this.calculateAveragePosition(this.trail);
                              Vec3d directionVec = averagePos.subtract(this.mc.player.getEntityPos()).normalize();
                              Vec3d predictedPos = this.mc.player.getEntityPos().add(directionVec.multiply(10.0));
                              this.targetYaw = Rotations.getYaw(predictedPos);
                              baritoneTarget = HuntingUtils.positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                           } else {
                              Vec3d lastPos = this.trail.getLast();
                              baritoneTarget = lastPos;
                           }

                           BaritoneBridge.setGoalXZAndPath((int)baritoneTarget.x, (int)baritoneTarget.z);
                        }
                     } else {
                        Vec3d targetPos = HuntingUtils.positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, this.pathDistanceActual);
                        BaritoneBridge.setGoalXZAndPath((int)targetPos.x, (int)targetPos.z);
                        this.targetYaw = Rotations.getYaw(targetPos);
                     }

                     if (this.autoElytra.get() && !BaritoneBridge.elytraHasDestination()) {
                        BaritoneBridge.acceptElytraTerms();
                        BaritoneBridge.execute("elytra");
                     }
                  }
                  break;
               case YAWLOCK:
                  this.mc.player.setYaw(HuntingUtils.smoothRotation(this.getActualYaw(this.mc.player.getYaw()), this.targetYaw, this.rotateScaling.get()));
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.debug.get() && this.mc.player != null) {
         if (this.followingTrail) {
            Vec3d targetPos = HuntingUtils.positionInDirection(this.mc.player.getEntityPos(), this.targetYaw, 10.0);
            event.renderer
               .line(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), targetPos.x, targetPos.y, targetPos.z, new Color(255, 0, 0));
            if (this.posDebug != null) {
               event.renderer
                  .line(
                     this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), this.posDebug.x, targetPos.y, this.posDebug.z, new Color(0, 0, 255)
                  );
            }
         }
      }
   }

   private void onChunkData(WorldChunk chunk) {
      RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
      ChunkPos chunkPos = chunk.getPos();
      long chunkLong = chunkPos.toLong();
      if (!this.seenChunksCache.contains(chunkLong)) {
         ChunkPos chunkDelta = new ChunkPos(chunkPos.x - this.mc.player.getChunkPos().x, chunkPos.z - this.mc.player.getChunkPos().z);
         if (this.oppositeDimension.get()) {
            if (currentDimension.equals(World.OVERWORLD)) {
               chunkPos = new ChunkPos(this.mc.player.getChunkPos().x / 8 + chunkDelta.x, this.mc.player.getChunkPos().z / 8 + chunkDelta.z);
               currentDimension = World.NETHER;
            } else if (currentDimension.equals(World.NETHER)) {
               chunkPos = new ChunkPos(this.mc.player.getChunkPos().x * 8 + chunkDelta.x, this.mc.player.getChunkPos().z * 8 + chunkDelta.z);
               currentDimension = World.OVERWORLD;
            }
         }

         if (this.isValidChunk(chunkPos, currentDimension)) {
            this.seenChunksCache.add(chunkLong);
            Vec3d pos = chunk.getPos().getCenterAtY(0).toCenterPos();
            this.posDebug = pos;
            if (!this.followingTrail) {
               if (System.currentTimeMillis() - this.lastFoundPossibleTrailTime > this.chunkConsiderationWindow.get() * 1000) {
                  this.possibleTrail.clear();
               }

               this.possibleTrail.add(pos);
               this.lastFoundPossibleTrailTime = System.currentTimeMillis();
               if (this.possibleTrail.size() > this.chunksBeforeStarting.get()) {
                  this.log("Trail found, starting to follow.");
                  this.followingTrail = true;
                  this.lastFoundTrailTime = System.currentTimeMillis();
                  this.trail.addAll(this.possibleTrail);
                  this.possibleTrail.clear();
               }
            } else {
               double chunkAngle = Rotations.getYaw(pos);
               double angleDiff = HuntingUtils.angleDifference(this.targetYaw, chunkAngle);
               if (!this.followingTrail || !(Math.abs(angleDiff) > this.maxTrailDeviation.get())) {
                  this.lastFoundTrailTime = System.currentTimeMillis();

                  while (this.trail.size() >= this.maxTrailLength.get()) {
                     this.trail.pollFirst();
                  }

                  if (angleDiff > 0.0 && angleDiff < 90.0 && this.directionWeighting.get() == TrailFollower.DirectionWeighting.LEFT) {
                     for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                        this.trail.pollFirst();
                        this.trail.add(pos);
                     }

                     this.trail.add(pos);
                  } else if (angleDiff < 0.0 && angleDiff > -90.0 && this.directionWeighting.get() == TrailFollower.DirectionWeighting.RIGHT) {
                     for (int i = 0; i < this.directionWeightingMultiplier.get() - 1; i++) {
                        this.trail.pollFirst();
                        this.trail.add(pos);
                     }

                     this.trail.add(pos);
                  } else {
                     this.trail.add(pos);
                  }

                  if (!this.trail.isEmpty()) {
                     if (this.followMode == TrailFollower.FollowMode.YAWLOCK) {
                        Vec3d averagePos = this.calculateAveragePosition(this.trail);
                        Vec3d positionVec = averagePos.subtract(this.mc.player.getEntityPos()).normalize();
                        Vec3d targetPos = this.mc.player.getEntityPos().add(positionVec.multiply(10.0));
                        this.targetYaw = Rotations.getYaw(targetPos);
                     } else {
                        Vec3d lastTrailPoint = this.trail.getLast();
                        this.targetYaw = Rotations.getYaw(lastTrailPoint);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isValidChunk(ChunkPos chunkPos, RegistryKey<World> currentDimension) {
      boolean is119NewChunk = XaeroCompat.isNewChunk(chunkPos.x, chunkPos.z, currentDimension);
      boolean is112OldChunk = XaeroCompat.isOldChunk(chunkPos.x, chunkPos.z, currentDimension);
      boolean isHighlighted = is119NewChunk || XaeroCompat.isInverseNewChunk(chunkPos.x, chunkPos.z, currentDimension);
      return isHighlighted && (!is119NewChunk && !this.only112.get() || is112OldChunk);
   }

   private Vec3d calculateAveragePosition(ArrayDeque<Vec3d> positions) {
      double sumX = 0.0;
      double sumZ = 0.0;

      for (Vec3d pos : positions) {
         sumX += pos.x;
         sumZ += pos.z;
      }

      return new Vec3d(sumX / positions.size(), 0.0, sumZ / positions.size());
   }

   private float getActualYaw(float yaw) {
      return (yaw % 360.0F + 360.0F) % 360.0F;
   }

   private void log(String message) {
      this.info(message, new Object[0]);
      if (!this.webhookLink.get().isEmpty()) {
         HuntingUtils.sendWebhook(this.webhookLink.get(), "TrailFollower", message, null, this.mc.player.getGameProfile().name());
      }
   }

   public static enum DirectionWeighting {
      LEFT,
      NONE,
      RIGHT;
   }

   public static enum FollowMode {
      AUTO,
      BARITONE,
      YAWLOCK;
   }

   public static enum NetherPathMode {
      AVERAGE,
      OTHER;
   }

   public static enum OverworldFlightMode {
      VANILLA,
      PITCH40,
      OTHER;
   }

   private class SeenChunksCache extends LinkedHashMap<Long, Long> {
      private static final long EXPIRY_MS = 300000L;

      @Override
      protected boolean removeEldestEntry(Entry<Long, Long> eldest) {
         return this.size() > TrailFollower.this.chunkCacheLength.get();
      }

      boolean contains(long key) {
         Long written = this.get(key);
         if (written == null) {
            return false;
         } else if (System.currentTimeMillis() - written > 300000L) {
            this.remove(key);
            return false;
         } else {
            return true;
         }
      }

      void add(long key) {
         this.put(key, System.currentTimeMillis());
      }
   }

   public static enum TrailEndBehavior {
      DISABLE,
      FLY_TOWARDS_YAW,
      DISCONNECT;
   }
}
