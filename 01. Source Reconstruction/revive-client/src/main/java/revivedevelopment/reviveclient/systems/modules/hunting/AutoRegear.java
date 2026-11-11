package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.ContainerComponentAccessor;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.ItemSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.hunting.arealoader.AreaLoader;
import revivedevelopment.reviveclient.systems.modules.movement.ElytraRecast;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.systems.modules.player.AutoEat;
import revivedevelopment.reviveclient.utils.BaritoneBridge;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.Rotations;

public class AutoRegear extends Module {
   private final SettingGroup sgTriggers = this.settings.createGroup("Triggers");
   private final SettingGroup sgPlatform = this.settings.createGroup("Platform");
   private final SettingGroup sgHotbar = this.settings.createGroup("Hotbar Slots");
   private final SettingGroup sgRestock = this.settings.createGroup("Restock");
   private final SettingGroup sgItems = this.settings.createGroup("Items");
   private final SettingGroup sgTrash = this.settings.createGroup("Trash Dump");
   private final SettingGroup sgDelays = this.settings.createGroup("Delays");
   private final Setting<Integer> minRockets = this.sgTriggers
      .add(
         new IntSetting.Builder()
            .name("min-rockets")
            .description("Minimum rockets in inventory to trigger regear.")
            .defaultValue(64)
            .min(0)
            .sliderMax(256)
            .build()
      );
   private final Setting<Integer> minElytras = this.sgTriggers
      .add(
         new IntSetting.Builder().name("min-elytras").description("Minimum number of valid elytras to trigger regear.").defaultValue(2).min(1).max(10).build()
      );
   private final Setting<Integer> goalElytras = this.sgTriggers
      .add(new IntSetting.Builder().name("goal-elytras").description("Target number of valid elytras after regearing.").defaultValue(6).min(2).max(20).build());
   private final Setting<Integer> elytraDurabilityThreshold = this.sgTriggers
      .add(
         new IntSetting.Builder()
            .name("elytra-durability-%")
            .description("Durability percentage threshold for a valid elytra.")
            .defaultValue(30)
            .min(1)
            .max(99)
            .build()
      );
   private final Setting<Boolean> debugMessages = this.sgTriggers
      .add(new BoolSetting.Builder().name("debug-messages").description("Show verbose debug messages in chat.").defaultValue(false).build());
   private final Setting<Boolean> createWalls = this.sgPlatform
      .add(new BoolSetting.Builder().name("create-walls").description("Create protective walls around the platform.").defaultValue(true).build());
   private final Setting<Boolean> encapsule = this.sgPlatform
      .add(
         new BoolSetting.Builder()
            .name("encapsule")
            .description("Fully enclose with 2-level walls and a roof.")
            .defaultValue(true)
            .visible(this.createWalls::get)
            .build()
      );
   private final Setting<Integer> targetYOffset = this.sgPlatform
      .add(
         new IntSetting.Builder()
            .name("target-y-offset")
            .description("In Overworld/End: blocks below build limit to start platform creation. Ignored in Nether.")
            .defaultValue(10)
            .min(5)
            .max(50)
            .sliderRange(5, 30)
            .build()
      );
   private final Setting<Integer> eChestHotbarSlot = this.sgHotbar
      .add(
         new IntSetting.Builder()
            .name("ender-chest-slot")
            .description("Hotbar slot for the ender chest (0-8).")
            .defaultValue(7)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Integer> shulkerHotbarSlot = this.sgHotbar
      .add(
         new IntSetting.Builder()
            .name("shulker-slot")
            .description("Hotbar slot for shulker boxes (0-8).")
            .defaultValue(6)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Integer> obsidianHotbarSlot = this.sgHotbar
      .add(
         new IntSetting.Builder()
            .name("obsidian-slot")
            .description("Hotbar slot for obsidian/blocks (0-8).")
            .defaultValue(5)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Integer> rocketHotbarSlot = this.sgHotbar
      .add(
         new IntSetting.Builder()
            .name("rocket-slot")
            .description("Hotbar slot for firework rockets (0-8).")
            .defaultValue(4)
            .range(0, 8)
            .sliderRange(0, 8)
            .build()
      );
   private final Setting<Boolean> autoReEnable = this.sgRestock
      .add(
         new BoolSetting.Builder()
            .name("auto-re-enable-flight")
            .description("Automatically re-enable flight modules after restocking.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> swapToChestplate = this.sgRestock
      .add(new BoolSetting.Builder().name("swap-to-chestplate").description("Swap to a chestplate before dropping down.").defaultValue(true).build());
   private final Setting<Boolean> restockTotems = this.sgItems
      .add(new BoolSetting.Builder().name("restock-totems").description("Restock totems of undying from shulkers during regear.").defaultValue(true).build());
   private final Setting<Integer> goalTotems = this.sgItems
      .add(
         new IntSetting.Builder()
            .name("goal-totems")
            .description("Target number of totems after regearing.")
            .defaultValue(4)
            .min(0)
            .sliderMax(36)
            .visible(this.restockTotems::get)
            .build()
      );
   private final Setting<Boolean> totemsTriggerRegear = this.sgItems
      .add(
         new BoolSetting.Builder()
            .name("totems-trigger-regear")
            .description("Low totems also trigger a regear.")
            .defaultValue(false)
            .visible(this.restockTotems::get)
            .build()
      );
   private final Setting<Integer> minTotems = this.sgItems
      .add(
         new IntSetting.Builder()
            .name("min-totems")
            .description("Minimum totems in inventory to trigger regear.")
            .defaultValue(1)
            .min(0)
            .sliderMax(36)
            .visible(() -> this.restockTotems.get() && this.totemsTriggerRegear.get())
            .build()
      );
   private final Setting<Boolean> restockFood = this.sgItems
      .add(new BoolSetting.Builder().name("restock-food").description("Restock food from shulkers during regear.").defaultValue(true).build());
   private final Setting<Item> foodItem = this.sgItems
      .add(
         new ItemSetting.Builder()
            .name("food-item")
            .description("The food item to restock.")
            .defaultValue(Items.ENCHANTED_GOLDEN_APPLE)
            .filter(item -> item.getComponents().contains(DataComponentTypes.FOOD))
            .visible(this.restockFood::get)
            .build()
      );
   private final Setting<Integer> goalFood = this.sgItems
      .add(
         new IntSetting.Builder()
            .name("goal-food")
            .description("Target number of food items after regearing.")
            .defaultValue(32)
            .min(0)
            .sliderMax(128)
            .visible(this.restockFood::get)
            .build()
      );
   private final Setting<Boolean> foodTriggerRegear = this.sgItems
      .add(
         new BoolSetting.Builder()
            .name("food-trigger-regear")
            .description("Low food also triggers a regear.")
            .defaultValue(false)
            .visible(this.restockFood::get)
            .build()
      );
   private final Setting<Integer> minFood = this.sgItems
      .add(
         new IntSetting.Builder()
            .name("min-food")
            .description("Minimum food items in inventory to trigger regear.")
            .defaultValue(8)
            .min(0)
            .sliderMax(128)
            .visible(() -> this.restockFood.get() && this.foodTriggerRegear.get())
            .build()
      );
   private final Setting<Integer> placeDelay = this.sgDelays
      .add(
         new IntSetting.Builder()
            .name("place-delay")
            .description("Delay between GrimAirPlace block placements (ticks).")
            .defaultValue(5)
            .min(0)
            .max(100)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Integer> clickDelay = this.sgDelays
      .add(new IntSetting.Builder().name("click-delay").description("Delay between inventory clicks (ticks).").defaultValue(3).min(0).max(10).build());
   private final Setting<Integer> containerOpenDelay = this.sgDelays
      .add(
         new IntSetting.Builder().name("container-open-delay").description("Delay after opening a container (ticks).").defaultValue(10).min(5).max(30).build()
      );
   private final Setting<Integer> breakDelay = this.sgDelays
      .add(new IntSetting.Builder().name("break-delay").description("Delay after breaking blocks (ticks).").defaultValue(8).min(0).max(20).build());
   private final Setting<Integer> startupDelay = this.sgDelays
      .add(
         new IntSetting.Builder()
            .name("startup-delay")
            .description("Ticks to wait in survival mode before checking inventory. Prevents false triggers from queue.")
            .defaultValue(100)
            .min(0)
            .max(600)
            .sliderRange(20, 200)
            .build()
      );
   private final Setting<Boolean> dumpTrash = this.sgTrash
      .add(
         new BoolSetting.Builder()
            .name("dump-trash")
            .description(
               "Throw non-essential inventory items off the platform before building walls. Never dumps shulkers, ender chests, gear, enchanted items, totems, rockets, food, obsidian or netherite loot."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<List<Item>> keepItems = this.sgTrash
      .add(new ItemListSetting.Builder().name("keep-items").description("Extra items that are never dumped.").visible(this.dumpTrash::get).build());
   private AutoRegear.RegearState state = AutoRegear.RegearState.IDLE;
   private AutoRegear.RegearState stateBeforeEating = null;
   private int timer = 0;
   private BlockPos platformCenter = null;
   private final List<BlockPos> placedBlocks = new ArrayList<>();
   private final List<BlockPos> pendingBlocks = new ArrayList<>();
   private int currentBlockIndex = 0;
   private BlockPos shulkerPlacePos = null;
   private BlockPos echestPos = null;
   private final List<AutoRegear.RestockCategory> categoriesToProcess = new ArrayList<>();
   private int categoryIndex = 0;
   private int leftoverSweepAttempts = 0;
   private int sweepBaselineShulkers = Integer.MAX_VALUE;
   private int lastSweepShulkerCount = Integer.MAX_VALUE;
   private int transferSlotIndex = 0;
   private int transferStep = 0;
   private int pendingBrokenElytraSlot = -1;
   private ItemStack savedChestplate = ItemStack.EMPTY;
   private final Set<String> processedShulkers = new HashSet<>();
   private int placementAttempts = 0;
   private int shulkerEnderSlot = -1;
   private int ecAlignTicks = 0;
   private int ecOpenFailures = 0;
   private int returnOpenAttempts = 0;
   private int stateTickCounter = 0;
   private AutoRegear.RegearState lastState = AutoRegear.RegearState.IDLE;
   private final Map<String, Module> flightModules = new HashMap<>();
   private final Set<String> disabledModules = new HashSet<>();
   private int scaffoldWaitTicks = 0;
   private float savedYaw = 0.0F;
   private float savedPitch = 0.0F;
   private boolean hadBaritoneGoal = false;
   private String savedBaritoneCommand = null;
   private int startupDelayTicks = 0;
   private boolean startupComplete = false;
   private AutoEat autoEatModule = null;
   private int wallLayer = 0;
   private BlockPos currentClearingPos = null;
   private int clearingProgress = 0;
   private int cleanupBlockIndex = 0;
   private int cleanupBlockAttempts = 0;
   private final List<BlockPos> cleanupBlocks = new ArrayList<>();
   private boolean cleanupInitialized = false;
   private int reEnableStage = 0;

   public AutoRegear() {
      super(Categories.Hunting, "auto-regear", "Automatically builds a platform and restocks rockets/elytras/totems/food from an ender chest.");
   }

   @Override
   public void onActivate() {
      this.state = AutoRegear.RegearState.IDLE;
      this.stateBeforeEating = null;
      this.timer = 0;
      this.platformCenter = null;
      this.placedBlocks.clear();
      this.pendingBlocks.clear();
      this.currentBlockIndex = 0;
      this.disabledModules.clear();
      this.shulkerPlacePos = null;
      this.echestPos = null;
      this.categoriesToProcess.clear();
      this.categoryIndex = 0;
      this.leftoverSweepAttempts = 0;
      this.sweepBaselineShulkers = Integer.MAX_VALUE;
      this.lastSweepShulkerCount = Integer.MAX_VALUE;
      this.transferSlotIndex = 0;
      this.transferStep = 0;
      this.pendingBrokenElytraSlot = -1;
      this.savedChestplate = ItemStack.EMPTY;
      this.placementAttempts = 0;
      this.shulkerEnderSlot = -1;
      this.ecAlignTicks = 0;
      this.ecOpenFailures = 0;
      this.returnOpenAttempts = 0;
      this.processedShulkers.clear();
      this.stateTickCounter = 0;
      this.lastState = AutoRegear.RegearState.IDLE;
      this.scaffoldWaitTicks = 0;
      this.savedYaw = 0.0F;
      this.currentClearingPos = null;
      this.clearingProgress = 0;
      this.savedPitch = 0.0F;
      this.hadBaritoneGoal = false;
      this.savedBaritoneCommand = null;
      this.reEnableStage = 0;
      this.cleanupBlockIndex = 0;
      this.cleanupBlockAttempts = 0;
      this.cleanupBlocks.clear();
      this.cleanupInitialized = false;
      this.startupDelayTicks = 0;
      this.startupComplete = false;
      this.autoEatModule = Modules.get().get(AutoEat.class);
      this.flightModules.clear();
      this.flightModules.put("AreaLoader", Modules.get().get(AreaLoader.class));
      this.flightModules.put("ElytraFly", Modules.get().get(ElytraFly.class));
      this.flightModules.put("TrailFollower", Modules.get().get(TrailFollower.class));
      this.flightModules.put("ElytraRecast", Modules.get().get(ElytraRecast.class));
      if (this.debugMessages.get()) {
         this.info("AutoRegear activated - monitoring inventory.", new Object[0]);
      }
   }

   @Override
   public void onDeactivate() {
      for (String name : new ArrayList<>(this.disabledModules)) {
         Module module = this.flightModules.get(name);
         if (module != null && !module.isActive()) {
            module.toggle();
         }
      }

      this.disabledModules.clear();
      if (!this.savedChestplate.isEmpty() && this.swapToChestplate.get()) {
         this.restoreChestplate();
      }

      this.releaseAllKeys();
      BaritoneBridge.cancelEverything();
      this.placedBlocks.clear();
      this.pendingBlocks.clear();
      this.wallLayer = 0;
      this.cleanupBlocks.clear();
      this.cleanupBlockIndex = 0;
      this.cleanupInitialized = false;
   }

   private void releaseAllKeys() {
      this.mc.options.jumpKey.setPressed(false);
      this.mc.options.useKey.setPressed(false);
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.backKey.setPressed(false);
      this.mc.options.leftKey.setPressed(false);
      this.mc.options.rightKey.setPressed(false);
      this.mc.options.sneakKey.setPressed(false);
   }

   private boolean isAutoEating() {
      return this.autoEatModule != null && this.autoEatModule.isActive() && this.autoEatModule.isEating();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.isAutoEating() && this.state != AutoRegear.RegearState.IDLE) {
            if (this.stateBeforeEating == null) {
               this.stateBeforeEating = this.state;
               if (this.debugMessages.get()) {
                  this.info("AutoEat is eating - pausing AutoRegear operations.", new Object[0]);
               }
            }
         } else {
            if (this.stateBeforeEating != null) {
               if (this.debugMessages.get()) {
                  this.info("AutoEat finished eating - resuming AutoRegear.", new Object[0]);
               }

               this.stateBeforeEating = null;
            }

            if (this.state != this.lastState) {
               this.lastState = this.state;
               this.stateTickCounter = 0;
               this.ecAlignTicks = 0;
            } else {
               this.stateTickCounter++;
            }

            if (this.stateTickCounter > 600) {
               switch (this.state) {
                  case IDLE:
                  case OPENING_ECHEST:
                  case TAKING_SHULKER:
                  case OPENING_SHULKER:
                  case TRANSFERRING_ITEMS:
                  case OPENING_ECHEST_RETURN:
                  case RETURNING_SHULKER:
                  case COMPLETE:
                     break;
                  case SWAP_TO_CHESTPLATE:
                  case DISABLING_MODULES:
                  case CREATING_INITIAL_PLATFORM:
                  case DUMPING_TRASH:
                  case CREATING_WALLS:
                  case CLEARING_ECHEST_AREA:
                  case ROTATING_FOR_ECHEST:
                  case PLACING_ECHEST:
                  case WAIT_ECHEST_PLACE:
                  case WAIT_SHULKER_TAKEN:
                  case ROTATING_FOR_SHULKER:
                  case PLACING_SHULKER:
                  case WAIT_SHULKER_PLACE:
                  case BREAKING_SHULKER:
                  case WAIT_SHULKER_BREAK:
                  case WAIT_SHULKER_PICKUP:
                  case CHECK_NEXT_SHULKER:
                  case RESTORING_ELYTRA:
                  case CLEANUP:
                  case TAKING_OFF:
                  default:
                     if (this.debugMessages.get()) {
                        this.error("Unexpected state timeout in " + this.state + " after 30 seconds. Force completing...", new Object[0]);
                     }

                     this.categoryIndex = this.categoriesToProcess.size();
                     this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
                     this.stateTickCounter = 0;
                     this.timer = 0;
                     return;
                  case DROPPING:
                  case CENTERING_ON_PLATFORM:
                  case POSITIONING_FOR_SHULKER:
                     if (this.debugMessages.get()) {
                        this.warning("State timeout in " + this.state + " after 30 seconds. Attempting to continue...", new Object[0]);
                     }

                     BaritoneBridge.cancelEverything();
                     this.mc.options.forwardKey.setPressed(false);
                     this.mc.options.backKey.setPressed(false);
                     this.mc.options.leftKey.setPressed(false);
                     this.mc.options.rightKey.setPressed(false);
                     this.mc.options.sneakKey.setPressed(false);
                     if (this.state == AutoRegear.RegearState.DROPPING) {
                        this.platformCenter = this.mc.player.getBlockPos().down();
                        this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                     } else if (this.state == AutoRegear.RegearState.CENTERING_ON_PLATFORM) {
                        this.state = AutoRegear.RegearState.CREATING_INITIAL_PLATFORM;
                     } else {
                        this.state = AutoRegear.RegearState.ROTATING_FOR_SHULKER;
                     }

                     this.stateTickCounter = 0;
                     this.timer = 5;
                     return;
               }
            }

            if (this.timer > 0) {
               this.timer--;
            } else {
               switch (this.state) {
                  case IDLE:
                     this.handleIdleState();
                     break;
                  case SWAP_TO_CHESTPLATE:
                     this.handleSwapToChestplate();
                     break;
                  case DISABLING_MODULES:
                     this.handleDisablingModules();
                     break;
                  case DROPPING:
                     this.handleDroppingState();
                     break;
                  case CENTERING_ON_PLATFORM:
                     this.handleCenteringOnPlatform();
                     break;
                  case CREATING_INITIAL_PLATFORM:
                     this.handleCreatingInitialPlatform();
                     break;
                  case DUMPING_TRASH:
                     this.handleDumpingTrash();
                     break;
                  case CREATING_WALLS:
                     this.handleCreatingWalls();
                     break;
                  case CLEARING_ECHEST_AREA:
                     this.handleClearingEchestArea();
                     break;
                  case ROTATING_FOR_ECHEST:
                     this.handleRotatingForEchest();
                     break;
                  case PLACING_ECHEST:
                     this.handlePlacingEchest();
                     break;
                  case WAIT_ECHEST_PLACE:
                     this.handleWaitEchestPlace();
                     break;
                  case OPENING_ECHEST:
                     this.handleOpeningEchest();
                     break;
                  case TAKING_SHULKER:
                     this.handleTakingShulker();
                     break;
                  case WAIT_SHULKER_TAKEN:
                     this.handleWaitShulkerTaken();
                     break;
                  case POSITIONING_FOR_SHULKER:
                     this.handlePositioningForShulker();
                     break;
                  case ROTATING_FOR_SHULKER:
                     this.handleRotatingForShulker();
                     break;
                  case PLACING_SHULKER:
                     this.handlePlacingShulker();
                     break;
                  case WAIT_SHULKER_PLACE:
                     this.handleWaitShulkerPlace();
                     break;
                  case OPENING_SHULKER:
                     this.handleOpeningShulker();
                     break;
                  case TRANSFERRING_ITEMS:
                     this.handleTransferringItems();
                     break;
                  case BREAKING_SHULKER:
                     this.handleBreakingShulker();
                     break;
                  case WAIT_SHULKER_BREAK:
                     this.handleWaitShulkerBreak();
                     break;
                  case WAIT_SHULKER_PICKUP:
                     this.handleWaitShulkerPickup();
                     break;
                  case OPENING_ECHEST_RETURN:
                     this.handleOpeningEchestReturn();
                     break;
                  case RETURNING_SHULKER:
                     this.handleReturningShulker();
                     break;
                  case CHECK_NEXT_SHULKER:
                     this.handleCheckNextShulker();
                     break;
                  case RESTORING_ELYTRA:
                     this.handleRestoringElytra();
                     break;
                  case CLEANUP:
                     this.handleCleanup();
                     break;
                  case TAKING_OFF:
                     this.handleTakingOff();
                     break;
                  case COMPLETE:
                     this.handleComplete();
               }
            }
         }
      }
   }

   private void handleIdleState() {
      if (!this.startupComplete) {
         if (this.mc.interactionManager != null && this.mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL) {
            this.startupDelayTicks++;
            if (this.startupDelayTicks >= this.startupDelay.get()) {
               this.startupComplete = true;
               if (this.debugMessages.get()) {
                  this.info("Startup delay complete - now monitoring inventory.", new Object[0]);
               }
            }
         } else {
            this.startupDelayTicks = 0;
         }
      } else if (this.shouldTriggerRegear()) {
         this.leftoverSweepAttempts = 0;
         this.categoriesToProcess.clear();
         this.categoryIndex = 0;
         this.categoriesToProcess.add(AutoRegear.RestockCategory.ELYTRA);
         if (this.restockTotems.get() && this.countCategory(AutoRegear.RestockCategory.TOTEMS) < this.goalTotems.get()) {
            this.categoriesToProcess.add(AutoRegear.RestockCategory.TOTEMS);
         }

         if (this.restockFood.get() && this.countCategory(AutoRegear.RestockCategory.FOOD) < this.goalFood.get()) {
            this.categoriesToProcess.add(AutoRegear.RestockCategory.FOOD);
         }

         this.categoriesToProcess.add(AutoRegear.RestockCategory.ROCKETS);
         this.skipSatisfiedCategories();
         if (this.currentCategory() == null) {
            if (this.debugMessages.get() && this.stateTickCounter % 200 == 0) {
               this.warning("Supplies low but nothing can be restocked (inventory full?), waiting.", new Object[0]);
            }
         } else {
            if (this.debugMessages.get()) {
               this.info("Low on supplies - initiating auto-regear sequence.", new Object[0]);
            }

            this.processedShulkers.clear();
            this.sweepBaselineShulkers = this.countShulkerBoxes();
            this.lastSweepShulkerCount = Integer.MAX_VALUE;
            this.savedYaw = this.mc.player.getYaw();
            this.savedPitch = this.mc.player.getPitch();
            if (BaritoneBridge.isPathing()) {
               String goalString = BaritoneBridge.pathingGoalString();
               ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
               boolean wearingElytra = chestItem.getItem() == Items.ELYTRA;
               if (goalString != null && goalString.contains("Elytra") || wearingElytra && !this.mc.player.isOnGround()) {
                  this.hadBaritoneGoal = true;
                  this.savedBaritoneCommand = "elytra";
                  if (this.debugMessages.get()) {
                     this.info("Saved baritone elytra state.", new Object[0]);
                  }
               }

               BaritoneBridge.cancelEverything();
            }

            BlockPos beneath = this.mc.player.getBlockPos().down();
            BlockState blockState = this.mc.world.getBlockState(beneath);
            boolean isNether = this.mc.world.getRegistryKey() == World.NETHER;
            int playerY = (int)Math.floor(this.mc.player.getY());
            if (!blockState.isReplaceable() && blockState.isSolidBlock(this.mc.world, beneath)) {
               if (this.debugMessages.get()) {
                  this.info("Already on solid ground, skipping drop phase.", new Object[0]);
               }

               this.platformCenter = beneath;
               this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
               this.disableAllFlightModules();
               this.timer = 5;
            } else if (!isNether) {
               int buildLimit = this.mc.world.getHeight() + this.mc.world.getBottomY();
               int minHeightForDrop = buildLimit - this.targetYOffset.get() - 50;
               if (playerY < minHeightForDrop) {
                  if (this.debugMessages.get()) {
                     this.warning(
                        "Player at Y=" + playerY + " is too low for build limit scaffold (min: " + minHeightForDrop + "). Looking for ground...", new Object[0]
                     );
                  }

                  for (int y = playerY; y > playerY - 20 && y > this.mc.world.getBottomY(); y--) {
                     BlockPos checkPos = new BlockPos(this.mc.player.getBlockPos().getX(), y, this.mc.player.getBlockPos().getZ());
                     BlockState checkState = this.mc.world.getBlockState(checkPos);
                     if (!checkState.isReplaceable() && checkState.isSolidBlock(this.mc.world, checkPos)) {
                        this.platformCenter = checkPos;
                        if (this.debugMessages.get()) {
                           this.info("Found solid ground at Y=" + y + ", using as platform.", new Object[0]);
                        }

                        this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                        this.disableAllFlightModules();
                        this.timer = 5;
                        return;
                     }
                  }

                  if (this.debugMessages.get()) {
                     this.info("No nearby ground found, will scaffold at current position.", new Object[0]);
                  }

                  this.disableAllFlightModules();
                  this.state = this.swapToChestplate.get() ? AutoRegear.RegearState.SWAP_TO_CHESTPLATE : AutoRegear.RegearState.DISABLING_MODULES;
               } else {
                  this.state = this.swapToChestplate.get() ? AutoRegear.RegearState.SWAP_TO_CHESTPLATE : AutoRegear.RegearState.DISABLING_MODULES;
               }
            } else {
               this.state = this.swapToChestplate.get() ? AutoRegear.RegearState.SWAP_TO_CHESTPLATE : AutoRegear.RegearState.DISABLING_MODULES;
            }
         }
      }
   }

   private void handleSwapToChestplate() {
      ItemStack chestStack = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
      if (chestStack.getItem() == Items.ELYTRA) {
         this.savedChestplate = chestStack.copy();
         FindItemResult chestplate = InvUtils.find(
            itemStack -> {
               Item item = itemStack.getItem();
               return item == Items.NETHERITE_CHESTPLATE
                  || item == Items.DIAMOND_CHESTPLATE
                  || item == Items.IRON_CHESTPLATE
                  || item == Items.GOLDEN_CHESTPLATE
                  || item == Items.CHAINMAIL_CHESTPLATE
                  || item == Items.LEATHER_CHESTPLATE;
            }
         );
         if (chestplate.found()) {
            InvUtils.move().from(chestplate.slot()).toArmor(2);
            if (this.debugMessages.get()) {
               this.info("Swapped to chestplate for safe landing.", new Object[0]);
            }

            this.timer = this.clickDelay.get();
         } else if (this.debugMessages.get()) {
            this.warning("No chestplate found, proceeding without swap.", new Object[0]);
         }
      }

      this.state = AutoRegear.RegearState.DISABLING_MODULES;
   }

   private void handleDisablingModules() {
      this.disableAllFlightModules();
      this.state = AutoRegear.RegearState.DROPPING;
      if (this.debugMessages.get()) {
         this.info("Flight modules disabled - dropping to platform level.", new Object[0]);
      }
   }

   private void handleDroppingState() {
      BlockPos beneath = this.mc.player.getBlockPos().down();
      BlockState blockState = this.mc.world.getBlockState(beneath);
      boolean isNether = this.mc.world.getRegistryKey() == World.NETHER;
      if (!blockState.isReplaceable() && blockState.isSolidBlock(this.mc.world, beneath)) {
         this.platformCenter = beneath;
         if (this.debugMessages.get()) {
            this.info("Detected solid ground at " + this.platformCenter.toShortString() + " - centering.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
         this.timer = 5;
      } else if (this.stateTickCounter > 400) {
         if (this.debugMessages.get()) {
            this.warning("Falling timeout - forcing platform creation.", new Object[0]);
         }

         this.platformCenter = this.mc.player.getBlockPos();
         this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
         this.timer = 5;
      } else {
         int playerY = (int)Math.floor(this.mc.player.getY());
         double yVelocity = this.mc.player.getVelocity().y;
         int buildLimit = 0;
         int activationHeight;
         if (isNether) {
            activationHeight = Integer.MAX_VALUE;
         } else {
            buildLimit = this.mc.world.getHeight() + this.mc.world.getBottomY();
            activationHeight = buildLimit - this.targetYOffset.get();
            if (playerY > activationHeight && this.stateTickCounter % 20 == 0 && this.debugMessages.get()) {
               this.info("Falling to platform level... Y=" + playerY + " (target: " + activationHeight + ")", new Object[0]);
            }
         }

         boolean canActivate;
         if (isNether) {
            canActivate = this.hasSpaceBelow() && yVelocity < -0.05;
         } else {
            int minHeightForScaffold = buildLimit - this.targetYOffset.get() - 50;
            boolean playerIsInValidRange = playerY >= minHeightForScaffold;
            boolean playerIsAtOrBelowActivation = playerY <= activationHeight;
            if (!playerIsInValidRange) {
               if (this.stateTickCounter % 40 == 0 && this.debugMessages.get()) {
                  this.info(
                     "Player at Y=" + playerY + " is below valid scaffold range (min: " + minHeightForScaffold + "). Waiting for ground...", new Object[0]
                  );
               }

               canActivate = false;
            } else {
               canActivate = playerIsAtOrBelowActivation && this.hasSpaceBelow() && yVelocity < -0.05;
            }
         }

         if (canActivate) {
            BlockPos catchPos = this.mc.player.getBlockPos().down();
            if (!this.mc.world.getBlockState(catchPos).isReplaceable()) {
               this.platformCenter = catchPos;
               if (this.debugMessages.get()) {
                  this.info("Fall-catch block placed at " + this.platformCenter.toShortString(), new Object[0]);
               }

               this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
               this.timer = 10;
            } else if (this.placeBlockGrim(catchPos)) {
               this.scaffoldWaitTicks++;
               this.timer = 2;
            } else if (++this.scaffoldWaitTicks > 60) {
               if (this.debugMessages.get()) {
                  this.warning("Fall-catch timeout.", new Object[0]);
               }

               this.platformCenter = this.mc.player.getBlockPos().down();
               this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
               this.timer = 10;
            }
         }
      }
   }

   private boolean hasSpaceBelow() {
      BlockPos playerPos = this.mc.player.getBlockPos();
      return this.mc.world.getBlockState(playerPos.down()).isReplaceable() && this.mc.world.getBlockState(playerPos.down(2)).isReplaceable();
   }

   private void handleCenteringOnPlatform() {
      if (this.stateTickCounter == 1 && this.debugMessages.get()) {
         this.info("Initiating centering on platform at " + this.platformCenter.toShortString(), new Object[0]);
      }

      Vec3d centerTarget = Vec3d.ofCenter(this.platformCenter);
      Vec3d playerPos = this.mc.player.getEntityPos();
      double deltaX = centerTarget.x - playerPos.x;
      double deltaZ = centerTarget.z - playerPos.z;
      double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      if (distance < 0.2) {
         BaritoneBridge.cancelEverything();
         BaritoneBridge.execute("cancel");
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         if (this.debugMessages.get()) {
            this.info("Successfully centered (distance: " + String.format("%.3f", distance) + ")", new Object[0]);
         }

         this.state = AutoRegear.RegearState.CREATING_INITIAL_PLATFORM;
         this.timer = 10;
      } else if (this.stateTickCounter <= 5) {
         BaritoneBridge.execute("cancel");
         BaritoneBridge.execute("goto " + this.platformCenter.getX() + " " + (this.platformCenter.getY() + 1) + " " + this.platformCenter.getZ());
         if (this.debugMessages.get()) {
            this.info("Baritone goto center - distance: " + String.format("%.2f", distance), new Object[0]);
         }
      } else {
         boolean baritoneActive = BaritoneBridge.isPathing();
         if (!baritoneActive && this.stateTickCounter > 20) {
            if (this.debugMessages.get()) {
               this.info("Baritone not active, using manual movement.", new Object[0]);
            }

            float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float playerYaw = this.mc.player.getYaw();
            float yawDiff = MathHelper.wrapDegrees(targetYaw - playerYaw);
            if (Math.abs(yawDiff) > 5.0F) {
               this.mc.player.setYaw(playerYaw + Math.signum(yawDiff) * 5.0F);
            }

            if (Math.abs(yawDiff) < 30.0F) {
               if (distance > 0.5) {
                  this.mc.options.forwardKey.setPressed(true);
               } else if (distance > 0.2) {
                  this.mc.options.forwardKey.setPressed(this.stateTickCounter % 3 == 0);
               }
            }

            this.mc.options.sneakKey.setPressed(distance < 1.0);
         }

         if (this.stateTickCounter > 200) {
            if (this.debugMessages.get()) {
               this.warning("Centering timeout, proceeding anyway.", new Object[0]);
            }

            BaritoneBridge.execute("cancel");
            this.mc.options.forwardKey.setPressed(false);
            this.mc.options.backKey.setPressed(false);
            this.mc.options.leftKey.setPressed(false);
            this.mc.options.rightKey.setPressed(false);
            this.mc.options.sneakKey.setPressed(false);
            this.state = AutoRegear.RegearState.CREATING_INITIAL_PLATFORM;
            this.timer = 10;
         }
      }
   }

   private void handleCreatingInitialPlatform() {
      if (this.stateTickCounter == 1) {
         if (this.debugMessages.get()) {
            this.info("Starting 2x2 platform construction at " + this.platformCenter.toShortString(), new Object[0]);
         }

         this.pendingBlocks.clear();
         this.placedBlocks.clear();
         this.currentBlockIndex = 0;
      }

      if (this.pendingBlocks.isEmpty() && this.timer == 0) {
         BlockPos[] platformBlocks = this.platformFloor();

         for (BlockPos pos : platformBlocks) {
            BlockState state = this.mc.world.getBlockState(pos);
            if (!state.isReplaceable() && !state.isAir()) {
               this.placedBlocks.add(pos.toImmutable());
            } else {
               this.pendingBlocks.add(pos.toImmutable());
            }
         }

         this.currentBlockIndex = 0;
         if (this.pendingBlocks.isEmpty()) {
            if (this.debugMessages.get()) {
               this.info("2x2 platform already complete.", new Object[0]);
            }

            this.state = this.nextStateAfterPlatform();
            this.timer = this.placeDelay.get();
         } else {
            if (this.debugMessages.get()) {
               this.info("Need to place " + this.pendingBlocks.size() + " blocks for 2x2 platform.", new Object[0]);
            }

            this.timer = this.placeDelay.get();
         }
      } else if (this.timer <= 0) {
         if (this.currentBlockIndex < this.pendingBlocks.size()) {
            BlockPos posx = this.pendingBlocks.get(this.currentBlockIndex);
            BlockState currentState = this.mc.world.getBlockState(posx);
            if (!currentState.isReplaceable() && !currentState.isAir()) {
               if (!this.placedBlocks.contains(posx.toImmutable())) {
                  this.placedBlocks.add(posx.toImmutable());
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.timer = Math.max(this.placeDelay.get() / 4, 2);
               return;
            }

            if (this.placementAttempts >= 10) {
               if (this.debugMessages.get()) {
                  this.warning("Failed to place block after 10 attempts at " + posx.toShortString() + ", skipping.", new Object[0]);
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.timer = this.placeDelay.get();
               return;
            }

            if (this.placeBlockGrim(posx)) {
               this.placementAttempts++;
               this.timer = this.placeDelay.get();
            } else {
               if (this.debugMessages.get()) {
                  this.warning("Failed to send placement packet, retrying...", new Object[0]);
               }

               this.placementAttempts++;
               this.timer = Math.max(this.placeDelay.get() / 2, 5);
            }
         } else {
            if (this.debugMessages.get()) {
               this.info("Verifying 2x2 platform completion...", new Object[0]);
            }

            boolean allPlaced = true;

            for (BlockPos posxx : this.platformFloor()) {
               if (this.mc.world.getBlockState(posxx).isReplaceable() || this.mc.world.getBlockState(posxx).isAir()) {
                  if (this.debugMessages.get()) {
                     this.warning("Missing block at " + posxx.toShortString(), new Object[0]);
                  }

                  allPlaced = false;
               }
            }

            if (allPlaced) {
               if (this.debugMessages.get()) {
                  this.info("2x2 platform verified complete.", new Object[0]);
               }

               this.pendingBlocks.clear();
               this.currentBlockIndex = 0;
               Vec3d centerTarget = Vec3d.ofCenter(this.platformCenter);
               Vec3d playerPos = this.mc.player.getEntityPos();
               double distance = Math.sqrt(Math.pow(centerTarget.x - playerPos.x, 2.0) + Math.pow(centerTarget.z - playerPos.z, 2.0));
               if (distance > 0.5 && this.createWalls.get()) {
                  if (this.debugMessages.get()) {
                     this.info("Re-centering before walls (distance: " + String.format("%.2f", distance) + ")", new Object[0]);
                  }

                  this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
                  this.timer = 0;
               } else {
                  this.state = this.nextStateAfterPlatform();
                  this.timer = this.placeDelay.get();
               }
            } else {
               if (this.debugMessages.get()) {
                  this.warning("Platform incomplete, restarting...", new Object[0]);
               }

               this.pendingBlocks.clear();
               this.currentBlockIndex = 0;
               this.timer = this.placeDelay.get();
            }
         }
      }
   }

   private BlockPos[] platformFloor() {
      return new BlockPos[]{this.platformCenter, this.platformCenter.add(1, 0, 0), this.platformCenter.add(0, 0, 1), this.platformCenter.add(1, 0, 1)};
   }

   private void handleCreatingWalls() {
      if (this.stateTickCounter == 1) {
         Vec3d centerTarget = Vec3d.ofCenter(this.platformCenter);
         Vec3d playerPos = this.mc.player.getEntityPos();
         double distance = Math.sqrt(Math.pow(centerTarget.x - playerPos.x, 2.0) + Math.pow(centerTarget.z - playerPos.z, 2.0));
         if (distance > 0.5) {
            if (this.debugMessages.get()) {
               this.warning("Not centered for wall construction, re-centering.", new Object[0]);
            }

            this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
            this.timer = 0;
            return;
         }

         if (this.debugMessages.get()) {
            this.info("Starting wall construction" + (this.encapsule.get() ? " (Encapsule mode)" : ""), new Object[0]);
         }

         this.pendingBlocks.clear();
         this.currentBlockIndex = 0;
         this.wallLayer = 0;
      }

      if (this.pendingBlocks.isEmpty() && this.timer == 0) {
         List<BlockPos> layerPositions = new ArrayList<>();
         BlockPos[] wallPositions = this.wallRing();
         if (!this.encapsule.get()) {
            for (BlockPos wallPos : wallPositions) {
               BlockState wallState = this.mc.world.getBlockState(wallPos);
               if (wallState.isReplaceable() || wallState.isAir()) {
                  this.pendingBlocks.add(wallPos.toImmutable());
               }
            }

            if (this.pendingBlocks.isEmpty()) {
               if (this.debugMessages.get()) {
                  this.info("Walls already complete.", new Object[0]);
               }

               this.state = AutoRegear.RegearState.CLEARING_ECHEST_AREA;
               this.timer = this.placeDelay.get();
               return;
            }
         } else {
            if (this.wallLayer == 0) {
               for (BlockPos wallPosx : wallPositions) {
                  BlockState wallState = this.mc.world.getBlockState(wallPosx);
                  if (wallState.isReplaceable() || wallState.isAir()) {
                     layerPositions.add(wallPosx.toImmutable());
                  }
               }
            } else if (this.wallLayer == 1) {
               for (BlockPos baseWall : wallPositions) {
                  BlockPos upperWall = baseWall.up();
                  BlockState wallState = this.mc.world.getBlockState(upperWall);
                  if (wallState.isReplaceable() || wallState.isAir()) {
                     layerPositions.add(upperWall.toImmutable());
                  }
               }
            } else {
               if (this.wallLayer != 2) {
                  if (this.debugMessages.get()) {
                     this.info("Encapsule construction complete.", new Object[0]);
                  }

                  this.state = AutoRegear.RegearState.CLEARING_ECHEST_AREA;
                  this.timer = this.placeDelay.get();
                  return;
               }

               for (BlockPos roofPos : this.roofPositions()) {
                  BlockState roofState = this.mc.world.getBlockState(roofPos);
                  if (roofState.isReplaceable() || roofState.isAir()) {
                     layerPositions.add(roofPos.toImmutable());
                  }
               }
            }

            if (layerPositions.isEmpty()) {
               if (this.debugMessages.get()) {
                  this.info("Layer " + (this.wallLayer + 1) + " already complete.", new Object[0]);
               }

               this.wallLayer++;
               this.timer = this.placeDelay.get();
               return;
            }

            this.pendingBlocks.addAll(layerPositions);
            if (this.debugMessages.get()) {
               this.info("Building layer " + (this.wallLayer + 1) + " with " + this.pendingBlocks.size() + " blocks.", new Object[0]);
            }
         }

         this.currentBlockIndex = 0;
         this.timer = this.placeDelay.get();
      } else if (this.timer <= 0) {
         if (this.currentBlockIndex < this.pendingBlocks.size()) {
            BlockPos pos = this.pendingBlocks.get(this.currentBlockIndex);
            BlockState currentState = this.mc.world.getBlockState(pos);
            if (!currentState.isReplaceable() && !currentState.isAir()) {
               if (!this.placedBlocks.contains(pos.toImmutable())) {
                  this.placedBlocks.add(pos.toImmutable());
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.timer = Math.max(this.placeDelay.get() / 3, 3);
               return;
            }

            if (this.placementAttempts >= 10) {
               if (this.debugMessages.get()) {
                  this.warning("Failed to place wall block after 10 attempts at " + pos.toShortString() + ", skipping.", new Object[0]);
               }

               this.currentBlockIndex++;
               this.placementAttempts = 0;
               this.timer = this.placeDelay.get();
               return;
            }

            if (this.placeBlockGrim(pos)) {
               this.placementAttempts++;
               this.timer = this.placeDelay.get();
            } else {
               if (this.debugMessages.get()) {
                  this.warning("Failed block placement packet, retrying...", new Object[0]);
               }

               this.placementAttempts++;
               this.timer = Math.max(this.placeDelay.get() / 2, 5);
            }
         } else {
            this.placementAttempts = 0;
            if (this.encapsule.get() && this.wallLayer < 2) {
               if (this.debugMessages.get()) {
                  this.info("Layer " + (this.wallLayer + 1) + " complete, moving to next layer.", new Object[0]);
               }

               this.wallLayer++;
               this.pendingBlocks.clear();
               this.currentBlockIndex = 0;
               this.timer = this.placeDelay.get();
            } else {
               if (this.debugMessages.get()) {
                  this.info(this.encapsule.get() ? "Encapsule construction complete." : "Wall construction complete.", new Object[0]);
               }

               this.pendingBlocks.clear();
               this.currentBlockIndex = 0;
               this.state = AutoRegear.RegearState.CLEARING_ECHEST_AREA;
               this.timer = this.placeDelay.get();
            }
         }
      }
   }

   private BlockPos[] wallRing() {
      return new BlockPos[]{
         this.platformCenter.add(-1, 1, 0),
         this.platformCenter.add(-1, 1, 1),
         this.platformCenter.add(0, 1, -1),
         this.platformCenter.add(1, 1, -1),
         this.platformCenter.add(2, 1, 0),
         this.platformCenter.add(2, 1, 1),
         this.platformCenter.add(0, 1, 2),
         this.platformCenter.add(1, 1, 2)
      };
   }

   private BlockPos[] roofPositions() {
      return new BlockPos[]{
         this.platformCenter.add(0, 3, 0), this.platformCenter.add(1, 3, 0), this.platformCenter.add(0, 3, 1), this.platformCenter.add(1, 3, 1)
      };
   }

   private void handleClearingEchestArea() {
      BlockPos[] clearPositions = new BlockPos[]{
         this.platformCenter.add(0, 1, 0), this.platformCenter.add(1, 1, 0), this.platformCenter.add(0, 1, 1), this.platformCenter.add(1, 1, 1)
      };
      if (this.currentClearingPos != null) {
         BlockState blockState = this.mc.world.getBlockState(this.currentClearingPos);
         if (!blockState.isAir() && !blockState.isReplaceable() && blockState.getBlock() != Blocks.ENDER_CHEST) {
            Vec3d targetVec = Vec3d.ofCenter(this.currentClearingPos);
            float[] rotations = getRotationsTo(this.mc.player.getEyePos(), targetVec);
            this.mc.player.setYaw(rotations[0]);
            this.mc.player.setPitch(rotations[1]);
            if (this.clearingProgress == 0) {
               this.mc.interactionManager.attackBlock(this.currentClearingPos, Direction.UP);
               this.mc.player.swingHand(Hand.MAIN_HAND);
               this.clearingProgress++;
            } else {
               this.mc.interactionManager.updateBlockBreakingProgress(this.currentClearingPos, Direction.UP);
               this.mc.player.swingHand(Hand.MAIN_HAND);
               this.clearingProgress++;
            }

            if (this.clearingProgress > 600) {
               if (this.debugMessages.get()) {
                  this.warning("Block clearing timeout after 30 seconds at " + this.currentClearingPos.toShortString(), new Object[0]);
               }

               this.currentClearingPos = null;
               this.clearingProgress = 0;
               this.timer = 2;
            }

            this.timer = 0;
         } else {
            if (this.debugMessages.get()) {
               this.info("Block cleared at " + this.currentClearingPos.toShortString(), new Object[0]);
            }

            this.currentClearingPos = null;
            this.clearingProgress = 0;
            this.timer = 2;
         }
      } else {
         for (BlockPos pos : clearPositions) {
            BlockState blockState = this.mc.world.getBlockState(pos);
            if (!blockState.isAir() && !blockState.isReplaceable() && blockState.getBlock() != Blocks.ENDER_CHEST) {
               this.currentClearingPos = pos;
               this.clearingProgress = 0;
               if (this.debugMessages.get()) {
                  this.info("Clearing " + blockState.getBlock().getName().getString() + " at " + pos.toShortString(), new Object[0]);
               }

               return;
            }
         }

         if (this.debugMessages.get()) {
            this.info("2x2 area cleared for ender chest placement.", new Object[0]);
         }

         this.currentClearingPos = null;
         this.clearingProgress = 0;
         this.state = AutoRegear.RegearState.ROTATING_FOR_ECHEST;
         this.timer = this.placeDelay.get();
      }
   }

   private void handleRotatingForEchest() {
      this.echestPos = this.platformCenter.add(1, 1, 1);
      if (this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
         if (this.debugMessages.get()) {
            this.info("Ender chest already present.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.OPENING_ECHEST;
         this.timer = this.containerOpenDelay.get();
      } else {
         if (this.stateTickCounter == 1) {
            Vec3d targetCenter = Vec3d.ofCenter(this.platformCenter);
            Vec3d playerPos = this.mc.player.getEntityPos();
            double distance = Math.sqrt(Math.pow(targetCenter.x - playerPos.x, 2.0) + Math.pow(targetCenter.z - playerPos.z, 2.0));
            if (distance > 0.5) {
               if (this.debugMessages.get()) {
                  this.warning("Player drifted from center before placing enderchest, re-centering.", new Object[0]);
               }

               this.state = AutoRegear.RegearState.CENTERING_ON_PLATFORM;
               this.timer = 0;
               return;
            }
         }

         if (this.stateTickCounter > 40) {
            if (this.debugMessages.get()) {
               this.info("Rotation timeout, placing anyway.", new Object[0]);
            }

            this.state = AutoRegear.RegearState.PLACING_ECHEST;
            this.timer = 2;
         } else {
            BlockPos groundPos = this.echestPos.down();
            Vec3d groundCenter = Vec3d.of(groundPos).add(0.5, 0.5, 0.5);
            Vec3d eyePos = this.mc.player.getEyePos();
            Vec3d lookVec = groundCenter.subtract(eyePos);
            double horizontalDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
            float targetYaw = (float)Math.toDegrees(Math.atan2(-lookVec.x, lookVec.z));
            float targetPitch = (float)Math.toDegrees(Math.atan2(-lookVec.y, horizontalDist));
            float yawDiff = Math.abs(MathHelper.wrapDegrees(this.mc.player.getYaw() - targetYaw));
            float pitchDiff = Math.abs(this.mc.player.getPitch() - targetPitch);
            if (yawDiff < 5.0F && pitchDiff < 5.0F) {
               if (this.debugMessages.get()) {
                  this.info("Rotation complete for ender chest.", new Object[0]);
               }

               this.state = AutoRegear.RegearState.PLACING_ECHEST;
               this.timer = 2;
            } else {
               Rotations.rotate(targetYaw, targetPitch, 50, () -> {});
            }
         }
      }
   }

   private void handlePlacingEchest() {
      if (this.placementAttempts > 15) {
         if (this.debugMessages.get()) {
            this.error("Failed to place ender chest after 15 attempts.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.IDLE;
         this.placementAttempts = 0;
      } else {
         this.mc.options.sneakKey.setPressed(false);
         int targetHotbarSlot = this.eChestHotbarSlot.get();
         ItemStack currentStack = this.mc.player.getInventory().getStack(targetHotbarSlot);
         if (currentStack.getItem() != Items.ENDER_CHEST) {
            int eChestSlot = InvUtils.find(Items.ENDER_CHEST).slot();
            if (eChestSlot == -1) {
               if (this.debugMessages.get()) {
                  this.error("No ender chest found in inventory!", new Object[0]);
               }

               this.state = AutoRegear.RegearState.IDLE;
            } else {
               InvUtils.move().from(eChestSlot).to(targetHotbarSlot);
               if (this.debugMessages.get()) {
                  this.info("Moving ender chest to hotbar slot " + targetHotbarSlot, new Object[0]);
               }

               this.timer = this.clickDelay.get();
            }
         } else {
            if (this.mc.player.getInventory().selectedSlot != targetHotbarSlot) {
               this.mc.player.getInventory().selectedSlot = targetHotbarSlot;
            }

            BlockPos groundPos = this.echestPos.down();
            if (!this.mc.world.getBlockState(groundPos).isSolidBlock(this.mc.world, groundPos)) {
               if (this.debugMessages.get()) {
                  this.error("No solid ground for ender chest at " + this.echestPos, new Object[0]);
               }

               this.state = AutoRegear.RegearState.IDLE;
            } else {
               BlockHitResult placeHit = new BlockHitResult(Vec3d.of(groundPos).add(0.5, 1.0, 0.5), Direction.UP, groundPos, false);
               this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, placeHit);
               if (this.debugMessages.get()) {
                  this.info("Placing ender chest.", new Object[0]);
               }

               this.state = AutoRegear.RegearState.WAIT_ECHEST_PLACE;
               this.timer = this.containerOpenDelay.get();
               this.placementAttempts++;
            }
         }
      }
   }

   private void handleWaitEchestPlace() {
      if (this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
         if (this.debugMessages.get()) {
            this.info("Ender chest placed successfully.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.OPENING_ECHEST;
         this.timer = this.containerOpenDelay.get();
         this.placementAttempts = 0;
      } else if (this.placementAttempts > 15) {
         if (this.debugMessages.get()) {
            this.error("Ender chest failed to place after 15 attempts.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.IDLE;
         this.placementAttempts = 0;
      } else {
         if (this.debugMessages.get()) {
            this.warning("Ender chest not detected, retrying placement...", new Object[0]);
         }

         this.state = AutoRegear.RegearState.PLACING_ECHEST;
         this.timer = this.placeDelay.get();
      }
   }

   private void handleOpeningEchest() {
      if (!this.walkToEchestStand()) {
         if (this.mc.world.getBlockState(this.echestPos).getBlock() != Blocks.ENDER_CHEST) {
            if (this.debugMessages.get()) {
               this.warning("Ender chest missing, re-placing.", new Object[0]);
            }

            this.state = AutoRegear.RegearState.ROTATING_FOR_ECHEST;
            this.timer = this.placeDelay.get();
         } else {
            BlockHitResult hit = this.blockHit(this.echestPos, this.mc.player.getEyePos());
            if (hit != null && this.lookAtReal(hit.getPos())) {
               if (this.ecAlignTicks++ >= 1) {
                  this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hit);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
                  this.state = AutoRegear.RegearState.TAKING_SHULKER;
                  this.transferStep = 0;
                  this.timer = this.containerOpenDelay.get();
                  if (this.debugMessages.get()) {
                     this.info("Opening ender chest.", new Object[0]);
                  }
               }
            } else {
               this.ecAlignTicks = 0;
            }
         }
      }
   }

   private boolean walkToEchestStand() {
      BlockPos standPos = this.echestPos.add(-1, -1, 0);
      Vec3d targetCenter = Vec3d.ofCenter(standPos.up());
      double distance = this.mc.player.getEntityPos().distanceTo(targetCenter);
      if (distance > 0.3 && this.stateTickCounter < 40) {
         if (this.stateTickCounter == 1) {
            BlockPos goal = standPos.up();
            BaritoneBridge.setGoalBlockAndPath(goal.getX(), goal.getY(), goal.getZ());
            if (this.debugMessages.get()) {
               this.info("Walking to ender chest.", new Object[0]);
            }
         }

         this.ecAlignTicks = 0;
         return true;
      } else {
         if (BaritoneBridge.isPathing()) {
            BaritoneBridge.cancelEverything();
         }

         this.mc.options.sneakKey.setPressed(false);
         return false;
      }
   }

   private void handleTakingShulker() {
      if (this.mc.currentScreen instanceof GenericContainerScreen screen) {
         this.ecOpenFailures = 0;
         GenericContainerScreenHandler handler = (GenericContainerScreenHandler)screen.getScreenHandler();
         AutoRegear.RestockCategory category = this.currentCategory();
         if (category == null) {
            this.mc.player.closeHandledScreen();
            this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
            this.timer = 0;
            return;
         }

         String targetType = category.name().toLowerCase();
         int syncId = handler.syncId;
         if (this.transferStep == 0) {
            this.shulkerEnderSlot = -1;

            for (int slot = 0; slot < handler.getRows() * 9; slot++) {
               ItemStack stack = handler.getSlot(slot).getStack();
               if (this.isShulkerBox(stack.getItem()) && this.shulkerHasCategory(stack, category)) {
                  String shulkerId = targetType + "_slot_" + slot;
                  if (!this.processedShulkers.contains(shulkerId)) {
                     this.shulkerEnderSlot = slot;
                     this.processedShulkers.add(shulkerId);
                     if (this.debugMessages.get()) {
                        this.info("Found " + targetType + " shulker in ender chest slot " + slot, new Object[0]);
                     }
                     break;
                  }

                  if (this.debugMessages.get()) {
                     this.info("Skipping already processed " + targetType + " shulker at slot " + slot, new Object[0]);
                  }
               }
            }

            if (this.shulkerEnderSlot == -1) {
               if (this.debugMessages.get()) {
                  this.warning("No more unprocessed " + targetType + " shulkers found in ender chest.", new Object[0]);
               }

               this.mc.player.closeHandledScreen();
               this.categoryIndex++;
               this.skipSatisfiedCategories();
               this.state = this.currentCategory() != null ? AutoRegear.RegearState.OPENING_ECHEST : AutoRegear.RegearState.RESTORING_ELYTRA;
               this.timer = this.containerOpenDelay.get();
               return;
            }

            this.mc.interactionManager.clickSlot(syncId, this.shulkerEnderSlot, this.shulkerHotbarSlot.get(), SlotActionType.SWAP, this.mc.player);
            this.timer = this.clickDelay.get() * 2;
            this.transferStep = 1;
         } else if (this.transferStep == 1) {
            ItemStack hotbarStack = this.mc.player.getInventory().getStack(this.shulkerHotbarSlot.get());
            if (!this.isShulkerBox(hotbarStack.getItem())) {
               if (this.debugMessages.get()) {
                  this.warning("Shulker swap failed, retrying...", new Object[0]);
               }

               this.transferStep = 0;
               this.timer = this.clickDelay.get();
               return;
            }

            if (this.debugMessages.get()) {
               this.info("Took " + targetType + " shulker from ender chest.", new Object[0]);
            }

            this.mc.player.closeHandledScreen();
            this.state = AutoRegear.RegearState.WAIT_SHULKER_TAKEN;
            this.timer = this.clickDelay.get();
         }
      } else if (this.stateTickCounter > 40) {
         this.ecOpenFailures++;
         if (this.ecOpenFailures >= 5) {
            if (this.debugMessages.get()) {
               this.error("Ender chest won't open after " + this.ecOpenFailures + " attempts, finishing up.", new Object[0]);
            }

            this.ecOpenFailures = 0;
            this.categoryIndex = this.categoriesToProcess.size();
            this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
            this.timer = 0;
            return;
         }

         if (this.debugMessages.get()) {
            this.error("Failed to open ender chest, retrying (" + this.ecOpenFailures + "/5)", new Object[0]);
         }

         this.state = AutoRegear.RegearState.OPENING_ECHEST;
         this.timer = this.containerOpenDelay.get();
      }
   }

   private void handleWaitShulkerTaken() {
      if (this.mc.currentScreen != null) {
         this.mc.player.closeHandledScreen();
      }

      ItemStack hotbarStack = this.mc.player.getInventory().getStack(this.shulkerHotbarSlot.get());
      if (!this.isShulkerBox(hotbarStack.getItem())) {
         if (this.debugMessages.get()) {
            this.error("Shulker not in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
         }

         this.state = AutoRegear.RegearState.OPENING_ECHEST;
         this.timer = this.containerOpenDelay.get();
      } else {
         String targetType = this.currentCategory() != null ? this.currentCategory().name().toLowerCase() : "unknown";
         if (this.debugMessages.get()) {
            this.info(targetType + " shulker confirmed in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
         }

         this.state = AutoRegear.RegearState.POSITIONING_FOR_SHULKER;
         this.timer = 0;
         this.placementAttempts = 0;
      }
   }

   private void handlePositioningForShulker() {
      if (this.stateTickCounter == 1 && this.debugMessages.get()) {
         this.info("Positioning for shulker placement.", new Object[0]);
      }

      Vec3d targetCenter = Vec3d.ofCenter(this.platformCenter);
      Vec3d playerPos = this.mc.player.getEntityPos();
      double deltaX = targetCenter.x - playerPos.x;
      double deltaZ = targetCenter.z - playerPos.z;
      double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      if (horizontalDistance < 0.2) {
         BaritoneBridge.cancelEverything();
         BaritoneBridge.execute("cancel");
         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         if (this.debugMessages.get()) {
            this.info("Centered for shulker (distance: " + String.format("%.3f", horizontalDistance) + ")", new Object[0]);
         }

         this.state = AutoRegear.RegearState.ROTATING_FOR_SHULKER;
         this.timer = 10;
      } else if (this.stateTickCounter <= 5) {
         BaritoneBridge.execute("cancel");
         BaritoneBridge.execute("goto " + this.platformCenter.getX() + " " + (this.platformCenter.getY() + 1) + " " + this.platformCenter.getZ());
         if (this.debugMessages.get()) {
            this.info("Baritone goto for shulker - distance: " + String.format("%.2f", horizontalDistance), new Object[0]);
         }
      } else {
         boolean baritoneActive = BaritoneBridge.isPathing();
         if (!baritoneActive && this.stateTickCounter > 20) {
            if (this.debugMessages.get()) {
               this.info("Using manual movement for shulker positioning.", new Object[0]);
            }

            float targetYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float playerYaw = this.mc.player.getYaw();
            float yawDiff = MathHelper.wrapDegrees(targetYaw - playerYaw);
            if (Math.abs(yawDiff) > 5.0F) {
               this.mc.player.setYaw(playerYaw + Math.signum(yawDiff) * 5.0F);
            }

            if (Math.abs(yawDiff) < 30.0F) {
               if (horizontalDistance > 0.5) {
                  this.mc.options.forwardKey.setPressed(true);
               } else if (horizontalDistance > 0.2) {
                  this.mc.options.forwardKey.setPressed(this.stateTickCounter % 3 == 0);
               }
            }

            this.mc.options.sneakKey.setPressed(horizontalDistance < 1.0);
         }

         if (this.stateTickCounter > 150) {
            if (this.debugMessages.get()) {
               this.warning("Shulker positioning timeout.", new Object[0]);
            }

            BaritoneBridge.execute("cancel");
            this.mc.options.forwardKey.setPressed(false);
            this.mc.options.sneakKey.setPressed(false);
            this.state = AutoRegear.RegearState.ROTATING_FOR_SHULKER;
            this.timer = 10;
         }
      }
   }

   private void handleRotatingForShulker() {
      this.shulkerPlacePos = this.platformCenter.add(1, 1, 0);
      BlockState shulkerPosState = this.mc.world.getBlockState(this.shulkerPlacePos);
      if (!shulkerPosState.isAir() && !shulkerPosState.isReplaceable() && !(shulkerPosState.getBlock() instanceof ShulkerBoxBlock)) {
         if (this.debugMessages.get()) {
            this.info("Block at shulker position needs to be cleared: " + shulkerPosState.getBlock().getName().getString(), new Object[0]);
         }

         Vec3d blockCenter = Vec3d.ofCenter(this.shulkerPlacePos);
         float[] rotations = getRotationsTo(this.mc.player.getEyePos(), blockCenter);
         this.mc.player.setYaw(rotations[0]);
         this.mc.player.setPitch(rotations[1]);
         if (this.stateTickCounter == 1) {
            this.mc.interactionManager.attackBlock(this.shulkerPlacePos, Direction.UP);
            this.mc.player.swingHand(Hand.MAIN_HAND);
         } else {
            this.mc.interactionManager.updateBlockBreakingProgress(this.shulkerPlacePos, Direction.UP);
            this.mc.player.swingHand(Hand.MAIN_HAND);
         }

         if (this.stateTickCounter > 300 && this.debugMessages.get()) {
            this.warning("Block clearing timeout at shulker position.", new Object[0]);
         }
      } else if (this.mc.world.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock) {
         if (this.debugMessages.get()) {
            this.info("Shulker already present.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.OPENING_SHULKER;
         this.timer = this.containerOpenDelay.get();
      } else if (this.stateTickCounter > 40) {
         if (this.debugMessages.get()) {
            this.info("Rotation timeout, placing anyway.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.PLACING_SHULKER;
         this.timer = 2;
      } else {
         BlockPos groundPos = this.shulkerPlacePos.down();
         Vec3d groundCenter = Vec3d.of(groundPos).add(0.5, 1.0, 0.5);
         Vec3d eyePos = this.mc.player.getEyePos();
         Vec3d lookVec = groundCenter.subtract(eyePos);
         double horizontalDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
         float targetYaw = (float)Math.toDegrees(Math.atan2(-lookVec.x, lookVec.z));
         float targetPitch = (float)Math.toDegrees(Math.atan2(-lookVec.y, horizontalDist));
         float yawDiff = Math.abs(MathHelper.wrapDegrees(this.mc.player.getYaw() - targetYaw));
         float pitchDiff = Math.abs(this.mc.player.getPitch() - targetPitch);
         this.mc.player.setYaw(targetYaw);
         this.mc.player.setPitch(targetPitch);
         if (yawDiff < 5.0F && pitchDiff < 5.0F) {
            if (this.debugMessages.get()) {
               this.info("Rotation complete for shulker.", new Object[0]);
            }

            this.state = AutoRegear.RegearState.PLACING_SHULKER;
            this.timer = 2;
         } else {
            Rotations.rotate(targetYaw, targetPitch, 50, () -> {});
         }
      }
   }

   private void handlePlacingShulker() {
      if (this.placementAttempts > 15) {
         if (this.debugMessages.get()) {
            this.error("Failed to place shulker after 15 attempts.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
         this.placementAttempts = 0;
      } else {
         this.mc.options.sneakKey.setPressed(false);
         ItemStack shulkerStack = this.mc.player.getInventory().getStack(this.shulkerHotbarSlot.get());
         if (!this.isShulkerBox(shulkerStack.getItem())) {
            if (this.debugMessages.get()) {
               this.error("No shulker in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
            }

            this.state = AutoRegear.RegearState.OPENING_ECHEST;
            this.placementAttempts = 0;
         } else {
            if (this.mc.player.getInventory().selectedSlot != this.shulkerHotbarSlot.get()) {
               this.mc.player.getInventory().selectedSlot = this.shulkerHotbarSlot.get();
            }

            BlockPos groundPos = this.shulkerPlacePos.down();
            BlockState shulkerPosState = this.mc.world.getBlockState(this.shulkerPlacePos);
            if (!shulkerPosState.isAir() && !shulkerPosState.isReplaceable()) {
               if (this.debugMessages.get()) {
                  this.warning("Shulker position blocked, going back to rotation/clearing.", new Object[0]);
               }

               this.state = AutoRegear.RegearState.ROTATING_FOR_SHULKER;
               this.timer = 2;
            } else if (!this.mc.world.getBlockState(groundPos).isSolidBlock(this.mc.world, groundPos)) {
               if (this.debugMessages.get()) {
                  this.error("No solid ground for shulker at " + this.shulkerPlacePos, new Object[0]);
               }

               this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
               this.placementAttempts = 0;
            } else {
               Vec3d groundCenter = Vec3d.of(groundPos).add(0.5, 1.0, 0.5);
               Vec3d eyePos = this.mc.player.getEyePos();
               Vec3d lookVec = groundCenter.subtract(eyePos);
               double horizontalDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
               float targetYaw = (float)Math.toDegrees(Math.atan2(-lookVec.x, lookVec.z));
               float targetPitch = (float)Math.toDegrees(Math.atan2(-lookVec.y, horizontalDist));
               this.mc.player.setYaw(targetYaw);
               this.mc.player.setPitch(targetPitch);
               BlockHitResult placeHit = new BlockHitResult(Vec3d.of(groundPos).add(0.5, 1.0, 0.5), Direction.UP, groundPos, false);
               this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, placeHit);
               if (this.debugMessages.get()) {
                  this.info("Placing shulker.", new Object[0]);
               }

               this.state = AutoRegear.RegearState.WAIT_SHULKER_PLACE;
               this.timer = this.placeDelay.get();
               this.placementAttempts++;
            }
         }
      }
   }

   private void handleWaitShulkerPlace() {
      if (this.mc.world.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock) {
         if (this.debugMessages.get()) {
            this.info("Shulker placed successfully.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.OPENING_SHULKER;
         this.timer = this.containerOpenDelay.get();
         this.placementAttempts = 0;
      } else if (this.placementAttempts > 15) {
         if (this.debugMessages.get()) {
            this.error("Shulker failed to place after 15 attempts.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
         this.placementAttempts = 0;
      } else {
         if (this.debugMessages.get()) {
            this.warning("Shulker not detected, retrying placement...", new Object[0]);
         }

         this.state = AutoRegear.RegearState.PLACING_SHULKER;
         this.timer = this.placeDelay.get();
      }
   }

   private void handleOpeningShulker() {
      this.mc.options.sneakKey.setPressed(false);
      Vec3d shulkerCenter = Vec3d.ofCenter(this.shulkerPlacePos);
      Vec3d eyePos = this.mc.player.getEyePos();
      Vec3d rotationVec = shulkerCenter.subtract(eyePos);
      double horizontalDist = Math.sqrt(rotationVec.x * rotationVec.x + rotationVec.z * rotationVec.z);
      float targetYaw = (float)Math.toDegrees(Math.atan2(-rotationVec.x, rotationVec.z));
      float targetPitch = (float)Math.toDegrees(Math.atan2(-rotationVec.y, horizontalDist));
      Rotations.rotate(targetYaw, targetPitch, 50, () -> {
         BlockHitResult hitResult = new BlockHitResult(shulkerCenter, Direction.UP, this.shulkerPlacePos, false);
         this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hitResult);
      });
      this.state = AutoRegear.RegearState.TRANSFERRING_ITEMS;
      this.transferSlotIndex = 0;
      this.transferStep = 0;
      this.timer = this.containerOpenDelay.get();
      if (this.debugMessages.get()) {
         this.info("Opening shulker.", new Object[0]);
      }
   }

   private void handleTransferringItems() {
      if (!(this.mc.currentScreen instanceof HandledScreen)) {
         if (this.stateTickCounter > 40) {
            if (this.debugMessages.get()) {
               this.error("Failed to open shulker - screen didn't appear.", new Object[0]);
            }

            this.state = AutoRegear.RegearState.OPENING_SHULKER;
            this.timer = this.containerOpenDelay.get();
         }
      } else {
         int syncId = this.mc.player.currentScreenHandler.syncId;
         AutoRegear.RestockCategory category = this.currentCategory();
         if (category == null) {
            this.mc.player.closeHandledScreen();
            this.state = AutoRegear.RegearState.BREAKING_SHULKER;
            this.timer = this.breakDelay.get();
            this.transferStep = 0;
         } else {
            String itemType = category.name().toLowerCase();
            if (this.transferStep == 0 && this.isCategorySatisfied(category)) {
               if (this.debugMessages.get()) {
                  this.info("Restock goal reached for " + itemType + ", stopping transfer.", new Object[0]);
               }

               this.mc.player.closeHandledScreen();
               this.state = AutoRegear.RegearState.BREAKING_SHULKER;
               this.timer = this.breakDelay.get();
               this.transferStep = 0;
            } else if (this.transferStep == 1) {
               this.mc.interactionManager.clickSlot(syncId, this.pendingBrokenElytraSlot, 0, SlotActionType.PICKUP, this.mc.player);
               if (this.debugMessages.get()) {
                  this.info("Swapping with broken elytra in slot " + this.pendingBrokenElytraSlot, new Object[0]);
               }

               this.timer = this.clickDelay.get();
               this.transferStep = 2;
            } else if (this.transferStep == 2) {
               this.mc.interactionManager.clickSlot(syncId, this.transferSlotIndex, 0, SlotActionType.PICKUP, this.mc.player);
               if (this.debugMessages.get()) {
                  this.info("Placed broken elytra back into shulker, gained 1 valid elytra.", new Object[0]);
               }

               this.timer = this.clickDelay.get();
               this.transferStep = 0;
               this.pendingBrokenElytraSlot = -1;
               this.transferSlotIndex++;
            } else if (this.transferSlotIndex < 27) {
               Slot slot = this.mc.player.currentScreenHandler.getSlot(this.transferSlotIndex);
               ItemStack stack = slot.getStack();
               if (!stack.isEmpty()) {
                  Item item = stack.getItem();
                  if (category == AutoRegear.RestockCategory.ELYTRA && item == Items.ELYTRA) {
                     if (this.isValidElytra(stack)) {
                        int brokenElytraSlot = this.findBrokenElytraInInventory();
                        if (brokenElytraSlot != -1) {
                           this.pendingBrokenElytraSlot = brokenElytraSlot;
                           this.mc.interactionManager.clickSlot(syncId, this.transferSlotIndex, 0, SlotActionType.PICKUP, this.mc.player);
                           if (this.debugMessages.get()) {
                              this.info("Picking up good elytra from slot " + this.transferSlotIndex, new Object[0]);
                           }

                           this.timer = this.clickDelay.get();
                           this.transferStep = 1;
                           return;
                        }

                        if (!this.hasSpaceForQuickMove(stack)) {
                           if (this.debugMessages.get()) {
                              this.info(
                                 "Skipping elytra in shulker slot " + this.transferSlotIndex + " - keeping a slot free for shulker pickup.", new Object[0]
                              );
                           }

                           this.transferStep = 0;
                           this.transferSlotIndex++;
                           return;
                        }

                        this.mc.interactionManager.clickSlot(syncId, this.transferSlotIndex, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                        if (this.debugMessages.get()) {
                           this.info(
                              "Transferred good elytra from shulker slot "
                                 + this.transferSlotIndex
                                 + " (now have "
                                 + this.countValidElytras()
                                 + "/"
                                 + this.goalElytras.get()
                                 + ")",
                              new Object[0]
                           );
                        }

                        this.timer = this.clickDelay.get();
                        this.transferStep = 0;
                        this.transferSlotIndex++;
                        return;
                     }
                  } else if (category != AutoRegear.RestockCategory.ELYTRA && this.matchesCategory(category, stack)) {
                     if (!this.hasSpaceForQuickMove(stack)) {
                        if (this.debugMessages.get()) {
                           this.info(
                              "Skipping " + itemType + " in shulker slot " + this.transferSlotIndex + " - keeping a slot free for shulker pickup.",
                              new Object[0]
                           );
                        }

                        this.transferStep = 0;
                        this.transferSlotIndex++;
                        return;
                     }

                     this.mc.interactionManager.clickSlot(syncId, this.transferSlotIndex, 0, SlotActionType.QUICK_MOVE, this.mc.player);
                     if (this.debugMessages.get()) {
                        this.info("Transferred " + itemType + " from shulker slot " + this.transferSlotIndex, new Object[0]);
                     }

                     this.timer = this.clickDelay.get();
                     this.transferStep = 0;
                     this.transferSlotIndex++;
                     return;
                  }
               }

               this.transferSlotIndex++;
               this.transferStep = 0;
            } else {
               this.mc.player.closeHandledScreen();
               if (this.debugMessages.get()) {
                  this.info("Finished transferring " + itemType + " from shulker.", new Object[0]);
               }

               this.state = AutoRegear.RegearState.BREAKING_SHULKER;
               this.timer = this.breakDelay.get();
               this.transferStep = 0;
            }
         }
      }
   }

   private int findBrokenElytraInInventory() {
      if (this.mc.player.currentScreenHandler == null) {
         return -1;
      } else {
         for (int playerSlot = 27; playerSlot < this.mc.player.currentScreenHandler.slots.size(); playerSlot++) {
            Slot slot = this.mc.player.currentScreenHandler.getSlot(playerSlot);
            ItemStack stack = slot.getStack();
            if (stack.getItem() == Items.ELYTRA) {
               int maxDurability = stack.getMaxDamage();
               int currentDurability = maxDurability - stack.getDamage();
               double percent = (double)currentDurability / maxDurability * 100.0;
               if (percent < this.elytraDurabilityThreshold.get().intValue()) {
                  return playerSlot;
               }
            }
         }

         return -1;
      }
   }

   private void handleBreakingShulker() {
      if (this.mc.world.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock) {
         this.mc.interactionManager.attackBlock(this.shulkerPlacePos, Direction.UP);
         this.mc.player.swingHand(Hand.MAIN_HAND);
         if (this.debugMessages.get()) {
            this.info("Breaking shulker manually.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.WAIT_SHULKER_BREAK;
         this.timer = this.breakDelay.get();
         this.placementAttempts = 0;
      } else {
         if (this.debugMessages.get()) {
            this.info("Shulker already broken.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
         this.timer = 0;
      }
   }

   private void handleWaitShulkerBreak() {
      if (!(this.mc.world.getBlockState(this.shulkerPlacePos).getBlock() instanceof ShulkerBoxBlock)) {
         if (this.debugMessages.get()) {
            this.info("Shulker broken - immediately walking to pickup.", new Object[0]);
         }

         BaritoneBridge.cancelEverything();
         BaritoneBridge.clearGoal();
         this.state = AutoRegear.RegearState.WAIT_SHULKER_PICKUP;
         this.timer = 0;
         this.placementAttempts = 0;
      } else {
         this.mc.interactionManager.updateBlockBreakingProgress(this.shulkerPlacePos, Direction.UP);
         this.mc.player.swingHand(Hand.MAIN_HAND);
         if (this.placementAttempts > 150) {
            if (this.debugMessages.get()) {
               this.warning("Failed to break shulker after extended attempts, skipping return.", new Object[0]);
            }

            this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
            this.placementAttempts = 0;
         } else {
            this.placementAttempts++;
            this.timer = 0;
         }
      }
   }

   private void handleWaitShulkerPickup() {
      ItemStack hotbarStack = this.mc.player.getInventory().getStack(this.shulkerHotbarSlot.get());
      if (this.isShulkerBox(hotbarStack.getItem())) {
         if (this.debugMessages.get()) {
            this.info("Shulker picked up in hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
         }

         if (BaritoneBridge.isPathing()) {
            BaritoneBridge.cancelEverything();
         }

         this.state = AutoRegear.RegearState.OPENING_ECHEST_RETURN;
         this.timer = this.containerOpenDelay.get();
         this.placementAttempts = 0;
      } else {
         for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (this.isShulkerBox(stack.getItem())) {
               if (this.debugMessages.get()) {
                  this.info("Shulker found in slot " + i + ", moving to hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
               }

               InvUtils.move().from(i).to(this.shulkerHotbarSlot.get());
               this.timer = this.clickDelay.get() * 2;
               if (BaritoneBridge.isPathing()) {
                  BaritoneBridge.cancelEverything();
               }

               return;
            }
         }

         if (this.stateTickCounter == 1 && this.shulkerPlacePos != null) {
            BaritoneBridge.setGoalBlockAndPath(this.shulkerPlacePos.getX(), this.shulkerPlacePos.getY(), this.shulkerPlacePos.getZ());
            if (this.debugMessages.get()) {
               this.info("Walking to shulker item at " + this.shulkerPlacePos.toShortString(), new Object[0]);
            }
         } else if (this.stateTickCounter > 20 && this.stateTickCounter % 40 == 0 && this.shulkerPlacePos != null && !BaritoneBridge.isPathing()) {
            BaritoneBridge.setGoalBlockAndPath(this.shulkerPlacePos.getX(), this.shulkerPlacePos.getY(), this.shulkerPlacePos.getZ());
            if (this.debugMessages.get()) {
               this.info("Re-navigating to shulker.", new Object[0]);
            }
         }

         if (this.stateTickCounter > 40 && this.stateTickCounter % 20 == 0 && this.countEmptySlots() == 0) {
            for (int ix = 0; ix < 36; ix++) {
               ItemStack stack = this.mc.player.getInventory().getStack(ix);
               if (stack.getItem() == Items.FIREWORK_ROCKET && stack.getCount() >= 32) {
                  InvUtils.drop().slot(ix);
                  if (this.debugMessages.get()) {
                     this.info("Dropped rockets from slot " + ix + " to make room for shulker.", new Object[0]);
                  }

                  this.timer = 10;
                  return;
               }
            }

            for (int ixx = 0; ixx < 36; ixx++) {
               ItemStack stack = this.mc.player.getInventory().getStack(ixx);
               if (!stack.isEmpty()
                  && !this.isShulkerBox(stack.getItem())
                  && stack.getItem() != Items.ELYTRA
                  && stack.getItem() != Items.ENDER_CHEST
                  && stack.getItem() != Items.OBSIDIAN
                  && stack.getItem() != Items.TOTEM_OF_UNDYING
                  && !stack.getComponents().contains(DataComponentTypes.FOOD)) {
                  InvUtils.drop().slot(ixx);
                  if (this.debugMessages.get()) {
                     this.info("Dropped " + stack.getItem() + " to make room for shulker.", new Object[0]);
                  }

                  this.timer = 10;
                  return;
               }
            }
         }

         if (this.stateTickCounter > 120) {
            if (this.debugMessages.get()) {
               this.warning("Shulker pickup timeout (120 ticks) - shulker not found, moving to next.", new Object[0]);
            }

            if (BaritoneBridge.isPathing()) {
               BaritoneBridge.cancelEverything();
            }

            this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
            this.timer = 0;
         }
      }
   }

   private void handleOpeningEchestReturn() {
      if (!this.walkToEchestStand()) {
         if (this.mc.world.getBlockState(this.echestPos).getBlock() != Blocks.ENDER_CHEST) {
            if (this.debugMessages.get()) {
               this.warning("Ender chest missing, cannot return shulker.", new Object[0]);
            }

            this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
            this.timer = 0;
         } else {
            BlockHitResult hit = this.blockHit(this.echestPos, this.mc.player.getEyePos());
            if (hit != null && this.lookAtReal(hit.getPos())) {
               if (this.ecAlignTicks++ >= 1) {
                  this.mc.interactionManager.interactBlock(this.mc.player, Hand.MAIN_HAND, hit);
                  this.mc.player.swingHand(Hand.MAIN_HAND);
                  this.state = AutoRegear.RegearState.RETURNING_SHULKER;
                  this.transferStep = 0;
                  this.timer = this.containerOpenDelay.get();
                  if (this.debugMessages.get()) {
                     this.info("Opening ender chest to return shulker.", new Object[0]);
                  }
               }
            } else {
               this.ecAlignTicks = 0;
            }
         }
      }
   }

   private void handleReturningShulker() {
      if (this.mc.currentScreen instanceof GenericContainerScreen screen) {
         this.returnOpenAttempts = 0;
         GenericContainerScreenHandler handler = (GenericContainerScreenHandler)screen.getScreenHandler();
         int syncId = handler.syncId;
         if (this.transferStep == 0) {
            ItemStack hotbarStack = this.mc.player.getInventory().getStack(this.shulkerHotbarSlot.get());
            if (!this.isShulkerBox(hotbarStack.getItem())) {
               for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
                  ItemStack stack = this.mc.player.getInventory().getStack(i);
                  if (this.isShulkerBox(stack.getItem())) {
                     if (this.debugMessages.get()) {
                        this.info("Shulker found in slot " + i + ", moving to hotbar slot " + this.shulkerHotbarSlot.get(), new Object[0]);
                     }

                     InvUtils.move().from(i).to(this.shulkerHotbarSlot.get());
                     this.timer = this.clickDelay.get() * 2;
                     return;
                  }
               }

               if (this.debugMessages.get()) {
                  this.warning("No shulker in inventory to return.", new Object[0]);
               }

               this.mc.player.closeHandledScreen();
               this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
               this.timer = 0;
               return;
            }

            int playerInvStartSlot = handler.getRows() * 9;
            int shulkerSlotInScreen = playerInvStartSlot + this.shulkerHotbarSlot.get() + 27;
            this.mc.interactionManager.clickSlot(syncId, shulkerSlotInScreen, 0, SlotActionType.QUICK_MOVE, this.mc.player);
            if (this.debugMessages.get()) {
               this.info("Returning shulker to ender chest via shift-click.", new Object[0]);
            }

            this.timer = this.clickDelay.get() * 2;
            this.transferStep = 1;
         } else if (this.transferStep == 1) {
            ItemStack hotbarStackx = this.mc.player.getInventory().getStack(this.shulkerHotbarSlot.get());
            if (this.isShulkerBox(hotbarStackx.getItem())) {
               if (this.stateTickCounter > 60) {
                  if (this.debugMessages.get()) {
                     this.warning("Could not return shulker to ender chest (may be full), proceeding anyway.", new Object[0]);
                  }

                  this.mc.player.closeHandledScreen();
                  this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
                  this.timer = this.clickDelay.get();
                  return;
               }

               this.transferStep = 0;
               this.timer = this.clickDelay.get();
               return;
            }

            if (this.debugMessages.get()) {
               this.info("Shulker successfully returned to ender chest.", new Object[0]);
            }

            this.mc.player.closeHandledScreen();
            this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
            this.timer = this.clickDelay.get();
         }
      } else if (this.stateTickCounter > 40) {
         this.returnOpenAttempts++;
         if (this.returnOpenAttempts >= 3) {
            if (this.debugMessages.get()) {
               this.error("Failed to open ender chest for shulker return, moving on.", new Object[0]);
            }

            this.returnOpenAttempts = 0;
            this.state = AutoRegear.RegearState.CHECK_NEXT_SHULKER;
            this.timer = 0;
         } else {
            if (this.debugMessages.get()) {
               this.warning("Ender chest didn't open for return, retrying (" + this.returnOpenAttempts + "/3)", new Object[0]);
            }

            this.state = AutoRegear.RegearState.OPENING_ECHEST_RETURN;
            this.timer = this.containerOpenDelay.get();
         }
      }
   }

   private void handleCheckNextShulker() {
      this.skipSatisfiedCategories();
      if (this.currentCategory() != null) {
         if (this.debugMessages.get()) {
            this.info("Continuing restock, current category: " + this.currentCategory().name().toLowerCase(), new Object[0]);
         }

         this.state = AutoRegear.RegearState.OPENING_ECHEST;
         this.timer = this.containerOpenDelay.get();
      } else {
         if (this.debugMessages.get()) {
            this.info("All restock categories complete.", new Object[0]);
         }

         this.state = AutoRegear.RegearState.RESTORING_ELYTRA;
         this.timer = 0;
      }
   }

   private AutoRegear.RegearState nextStateAfterPlatform() {
      if (this.dumpTrash.get() && this.findDumpableSlot() != -1) {
         return AutoRegear.RegearState.DUMPING_TRASH;
      } else {
         return this.createWalls.get() ? AutoRegear.RegearState.CREATING_WALLS : AutoRegear.RegearState.CLEARING_ECHEST_AREA;
      }
   }

   private void handleDumpingTrash() {
      int slot = this.findDumpableSlot();
      if (slot == -1) {
         if (this.debugMessages.get()) {
            this.info("Trash dump complete.", new Object[0]);
         }

         this.state = this.createWalls.get() ? AutoRegear.RegearState.CREATING_WALLS : AutoRegear.RegearState.CLEARING_ECHEST_AREA;
         this.timer = this.placeDelay.get();
      } else if (this.turnTowardsReal(this.mc.player.getYaw(), 0.0F)) {
         ItemStack stack = this.mc.player.getInventory().getStack(slot);
         if (this.debugMessages.get()) {
            this.info("Dumping " + stack.getName().getString() + " x" + stack.getCount(), new Object[0]);
         }

         InvUtils.drop().slot(slot);
         this.timer = Math.max(this.clickDelay.get(), 2);
      }
   }

   private int findDumpableSlot() {
      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (!stack.isEmpty() && this.isDumpable(stack)) {
            return i;
         }
      }

      return -1;
   }

   private boolean isDumpable(ItemStack stack) {
      Item item = stack.getItem();
      if (this.isShulkerBox(item)) {
         return false;
      } else if (item == Items.ENDER_CHEST || item == Items.ELYTRA || item == Items.TOTEM_OF_UNDYING || item == Items.FIREWORK_ROCKET || item == Items.OBSIDIAN
         )
       {
         return false;
      } else if (stack.contains(DataComponentTypes.FOOD)) {
         return false;
      } else if (stack.contains(DataComponentTypes.EQUIPPABLE) || stack.contains(DataComponentTypes.TOOL) || stack.isIn(ItemTags.SWORDS)) {
         return false;
      } else if (!stack.hasEnchantments() && !stack.contains(DataComponentTypes.STORED_ENCHANTMENTS)) {
         if (stack.getRarity() != Rarity.COMMON) {
            return false;
         } else {
            return item != Items.NETHERITE_INGOT && item != Items.NETHERITE_SCRAP && item != Items.ANCIENT_DEBRIS && item != Items.NETHERITE_BLOCK
               ? !this.keepItems.get().contains(item)
               : false;
         }
      } else {
         return false;
      }
   }

   private Vec3d blockAimPoint(BlockPos pos, Vec3d eye) {
      BlockState blockState = this.mc.world.getBlockState(pos);
      if (blockState.isAir()) {
         return null;
      } else {
         VoxelShape shape = blockState.getOutlineShape(this.mc.world, pos);
         Box box = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox().offset(pos.getX(), pos.getY(), pos.getZ());
         return new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ));
      }
   }

   private Direction nearestFace(BlockPos pos, Vec3d point) {
      double dx = point.x - (pos.getX() + 0.5);
      double dy = point.y - (pos.getY() + 0.5);
      double dz = point.z - (pos.getZ() + 0.5);
      double ax = Math.abs(dx);
      double ay = Math.abs(dy);
      double az = Math.abs(dz);
      if (ax >= ay && ax >= az) {
         return dx > 0.0 ? Direction.EAST : Direction.WEST;
      } else if (az >= ay) {
         return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
      } else {
         return dy > 0.0 ? Direction.UP : Direction.DOWN;
      }
   }

   private BlockHitResult blockHit(BlockPos pos, Vec3d eye) {
      Vec3d aim = this.blockAimPoint(pos, eye);
      if (aim == null) {
         return null;
      } else {
         BlockHitResult clip = this.mc.world.raycast(new RaycastContext(eye, aim, ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
         return clip.getType() == Type.BLOCK && clip.getBlockPos().equals(pos) ? clip : new BlockHitResult(aim, this.nearestFace(pos, aim), pos, false);
      }
   }

   private boolean lookAtReal(Vec3d point) {
      float[] rot = getRotationsTo(this.mc.player.getEyePos(), point);
      return this.turnTowardsReal(rot[0], rot[1]);
   }

   private boolean turnTowardsReal(float targetYaw, float targetPitch) {
      float maxStep = 45.0F;
      float curYaw = this.mc.player.getYaw();
      float curPitch = this.mc.player.getPitch();
      float dYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
      float dPitch = targetPitch - curPitch;
      float newYaw = curYaw + MathHelper.clamp(dYaw, -maxStep, maxStep);
      float newPitch = MathHelper.clamp(curPitch + MathHelper.clamp(dPitch, -maxStep, maxStep), -90.0F, 90.0F);
      this.mc.player.setYaw(newYaw);
      this.mc.player.setPitch(newPitch);
      this.mc.player.setHeadYaw(newYaw);
      return Math.abs(dYaw) <= 3.0F && Math.abs(dPitch) <= 3.0F;
   }

   private void handleRestoringElytra() {
      if (this.echestPos != null && this.mc.world.getBlockState(this.echestPos).getBlock() == Blocks.ENDER_CHEST) {
         int shulkerCount = this.countShulkerBoxes();
         if (shulkerCount > this.sweepBaselineShulkers) {
            if (shulkerCount < this.lastSweepShulkerCount) {
               this.leftoverSweepAttempts = 0;
            }

            this.lastSweepShulkerCount = shulkerCount;
            if (this.leftoverSweepAttempts < 3) {
               this.leftoverSweepAttempts++;
               if (this.debugMessages.get()) {
                  this.warning("Leftover shulker in inventory, returning to ender chest (attempt " + this.leftoverSweepAttempts + "/3)", new Object[0]);
               }

               this.state = AutoRegear.RegearState.OPENING_ECHEST_RETURN;
               this.timer = this.containerOpenDelay.get();
               return;
            }

            if (this.debugMessages.get()) {
               this.warning("Could not return leftover shulker to ender chest, keeping it in inventory.", new Object[0]);
            }
         }
      }

      if (this.swapToChestplate.get() && !this.savedChestplate.isEmpty()) {
         this.restoreChestplate();
         if (this.debugMessages.get()) {
            this.info("Restored elytra.", new Object[0]);
         }
      }

      this.state = AutoRegear.RegearState.CLEANUP;
      this.timer = 5;
      this.cleanupBlockIndex = 0;
   }

   private void handleCleanup() {
      if (!this.cleanupInitialized) {
         this.cleanupInitialized = true;
         if (this.debugMessages.get()) {
            this.info("Starting cleanup of all blocks above floor for flight clearance.", new Object[0]);
         }

         this.cleanupBlocks.clear();
         this.cleanupBlockAttempts = 0;
         if (this.platformCenter != null) {
            Set<BlockPos> floorBlocks = new HashSet<>();

            for (BlockPos pos : this.platformFloor()) {
               floorBlocks.add(pos.toImmutable());
            }

            for (BlockPos pos : this.placedBlocks) {
               if (!floorBlocks.contains(pos.toImmutable())) {
                  BlockState blockState = this.mc.world.getBlockState(pos);
                  if (!blockState.isAir() && !blockState.isReplaceable()) {
                     this.cleanupBlocks.add(pos.toImmutable());
                  }
               }
            }

            BlockPos[] wallPositionsY1 = new BlockPos[]{
               this.platformCenter.add(-1, 1, 0),
               this.platformCenter.add(-1, 1, 1),
               this.platformCenter.add(0, 1, -1),
               this.platformCenter.add(1, 1, -1),
               this.platformCenter.add(2, 1, 0),
               this.platformCenter.add(2, 1, 1),
               this.platformCenter.add(0, 1, 2),
               this.platformCenter.add(1, 1, 2),
               this.platformCenter.add(0, 1, 0),
               this.platformCenter.add(1, 1, 0),
               this.platformCenter.add(0, 1, 1),
               this.platformCenter.add(1, 1, 1)
            };

            for (BlockPos posx : wallPositionsY1) {
               BlockPos immutablePos = posx.toImmutable();
               if (!this.cleanupBlocks.contains(immutablePos)) {
                  BlockState blockState = this.mc.world.getBlockState(posx);
                  if (!blockState.isAir() && !blockState.isReplaceable()) {
                     this.cleanupBlocks.add(immutablePos);
                  }
               }
            }

            for (BlockPos basePos : wallPositionsY1) {
               BlockPos upperWall = basePos.up();
               BlockPos immutablePos = upperWall.toImmutable();
               if (!this.cleanupBlocks.contains(immutablePos)) {
                  BlockState blockState = this.mc.world.getBlockState(upperWall);
                  if (!blockState.isAir() && !blockState.isReplaceable()) {
                     this.cleanupBlocks.add(immutablePos);
                  }
               }
            }

            for (BlockPos posxx : this.roofPositions()) {
               BlockPos immutablePos = posxx.toImmutable();
               if (!this.cleanupBlocks.contains(immutablePos)) {
                  BlockState blockState = this.mc.world.getBlockState(posxx);
                  if (!blockState.isAir() && !blockState.isReplaceable()) {
                     this.cleanupBlocks.add(immutablePos);
                  }
               }
            }
         }

         if (this.cleanupBlocks.isEmpty()) {
            if (this.debugMessages.get()) {
               this.info("No blocks to clean up.", new Object[0]);
            }

            this.cleanupInitialized = false;
            this.state = AutoRegear.RegearState.TAKING_OFF;
            this.timer = 5;
            return;
         }

         if (this.debugMessages.get()) {
            this.info("Found " + this.cleanupBlocks.size() + " blocks to clean up for flight clearance.", new Object[0]);
         }

         this.cleanupBlockIndex = 0;
      }

      if (this.timer <= 0) {
         if (this.cleanupBlockIndex < this.cleanupBlocks.size()) {
            BlockPos posxxx = this.cleanupBlocks.get(this.cleanupBlockIndex);
            BlockState blockState = this.mc.world.getBlockState(posxxx);
            if (blockState.isAir()) {
               if (this.debugMessages.get()) {
                  this.info("Block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size() + " already broken.", new Object[0]);
               }

               this.cleanupBlockIndex++;
               this.cleanupBlockAttempts = 0;
               this.timer = 1;
               return;
            }

            if (this.cleanupBlockAttempts > 150) {
               if (this.debugMessages.get()) {
                  this.warning("Cleanup timeout on block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size() + ", skipping.", new Object[0]);
               }

               this.cleanupBlockIndex++;
               this.cleanupBlockAttempts = 0;
               this.timer = 2;
               return;
            }

            FindItemResult pickaxe = InvUtils.find(itemStack -> itemStack.isIn(ItemTags.PICKAXES));
            if (pickaxe.found() && pickaxe.isHotbar() && this.mc.player.getInventory().selectedSlot != pickaxe.slot()) {
               this.mc.player.getInventory().selectedSlot = pickaxe.slot();
            }

            Vec3d playerPos = this.mc.player.getEntityPos();
            Vec3d blockCenter = Vec3d.ofCenter(posxxx);
            double dx = playerPos.x - blockCenter.x;
            double dy = playerPos.y + this.mc.player.getEyeHeight(this.mc.player.getPose()) - blockCenter.y;
            double dz = playerPos.z - blockCenter.z;
            Direction breakDirection;
            if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) {
               breakDirection = dx > 0.0 ? Direction.EAST : Direction.WEST;
            } else if (Math.abs(dz) > Math.abs(dy)) {
               breakDirection = dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
            } else {
               breakDirection = dy > 0.0 ? Direction.UP : Direction.DOWN;
            }

            float[] rotations = getRotationsTo(this.mc.player.getEyePos(), blockCenter);
            Direction finalBreakDirection = breakDirection;
            boolean first = this.cleanupBlockAttempts == 0;
            Rotations.rotate(rotations[0], rotations[1], 50, () -> {
               if (first) {
                  this.mc.interactionManager.attackBlock(posxxx, finalBreakDirection);
               } else {
                  this.mc.interactionManager.updateBlockBreakingProgress(posxxx, finalBreakDirection);
               }

               this.mc.player.swingHand(Hand.MAIN_HAND);
            });
            if (first && this.debugMessages.get()) {
               this.info("Started breaking block " + (this.cleanupBlockIndex + 1) + "/" + this.cleanupBlocks.size(), new Object[0]);
            }

            this.cleanupBlockAttempts++;
            this.timer = 0;
         } else {
            if (this.debugMessages.get()) {
               this.info("Cleanup complete, removed " + this.cleanupBlocks.size() + " blocks.", new Object[0]);
            }

            this.cleanupBlocks.clear();
            this.cleanupBlockAttempts = 0;
            this.cleanupBlockIndex = 0;
            this.cleanupInitialized = false;
            this.state = AutoRegear.RegearState.TAKING_OFF;
            this.timer = 5;
         }
      }
   }

   private void handleTakingOff() {
      if (this.stateTickCounter == 1) {
         if (this.debugMessages.get()) {
            this.info("Preparing for takeoff.", new Object[0]);
         }

         ItemStack chestItem = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
         if (chestItem.getItem() != Items.ELYTRA) {
            if (this.swapToChestplate.get() && !this.savedChestplate.isEmpty()) {
               this.restoreChestplate();
               if (this.debugMessages.get()) {
                  this.info("Restored elytra for takeoff.", new Object[0]);
               }
            } else if (this.debugMessages.get()) {
               this.warning("No elytra equipped!", new Object[0]);
            }
         }

         this.mc.player.setYaw(this.savedYaw);
         this.mc.player.setPitch(this.savedPitch);
         if (this.debugMessages.get()) {
            this.info("Restored rotation for takeoff.", new Object[0]);
         }
      }

      if (this.stateTickCounter >= 5 && this.stateTickCounter <= 15) {
         this.mc.options.jumpKey.setPressed(true);
      }

      if (this.stateTickCounter == 16) {
         this.mc.options.jumpKey.setPressed(false);
      }

      if (this.stateTickCounter == 20) {
         this.mc.options.jumpKey.setPressed(true);
         if (this.debugMessages.get()) {
            this.info("Activating elytra.", new Object[0]);
         }
      }

      if (this.stateTickCounter == 21) {
         this.mc.options.jumpKey.setPressed(false);
         ItemStack chest = this.mc.player.getEquippedStack(EquipmentSlot.CHEST);
         if (chest.getItem() == Items.ELYTRA && this.debugMessages.get()) {
            this.info("Elytra activated.", new Object[0]);
         }
      }

      if (this.stateTickCounter == 25) {
         FindItemResult rockets = InvUtils.find(Items.FIREWORK_ROCKET);
         if (rockets.found()) {
            if (this.mc.player.getInventory().selectedSlot != rockets.slot()) {
               this.mc.player.getInventory().selectedSlot = rockets.slot();
            }

            this.mc.options.useKey.setPressed(true);
         }
      }

      if (this.stateTickCounter == 26) {
         this.mc.options.useKey.setPressed(false);
         if (this.debugMessages.get()) {
            this.info("Takeoff complete.", new Object[0]);
         }
      }

      if (this.stateTickCounter > 30) {
         this.state = AutoRegear.RegearState.COMPLETE;
      }
   }

   private void handleComplete() {
      if (this.stateTickCounter == 1) {
         if (this.debugMessages.get()) {
            this.info("Auto-regear complete! Rockets: " + this.countRockets() + " Elytras: " + this.countValidElytras(), new Object[0]);
         }

         this.mc.options.forwardKey.setPressed(false);
         this.mc.options.backKey.setPressed(false);
         this.mc.options.leftKey.setPressed(false);
         this.mc.options.rightKey.setPressed(false);
         this.mc.options.sneakKey.setPressed(false);
         this.mc.player.setYaw(this.savedYaw);
         this.mc.player.setPitch(this.savedPitch);
         this.ensureRocketsInHotbar();
      }

      if (this.autoReEnable.get()) {
         if (this.reEnableStage == 0 && this.stateTickCounter >= 5) {
            this.reEnableFlightModules();
            return;
         }

         if (this.reEnableStage == 1 && this.timer == 0) {
            this.reEnableFlightModules();
            this.timer = 5;
            return;
         }

         if (this.reEnableStage == 2 && this.timer == 0) {
            if (this.hadBaritoneGoal && this.savedBaritoneCommand != null) {
               BaritoneBridge.execute(this.savedBaritoneCommand);
               if (this.debugMessages.get()) {
                  this.info("Restored baritone elytra mode.", new Object[0]);
               }
            }

            this.reEnableStage = 3;
            this.timer = 5;
            return;
         }

         if (this.reEnableStage < 3) {
            return;
         }
      }

      if (!this.autoReEnable.get() && this.stateTickCounter == 10 && this.hadBaritoneGoal && this.savedBaritoneCommand != null) {
         BaritoneBridge.execute(this.savedBaritoneCommand);
         if (this.debugMessages.get()) {
            this.info("Restored baritone elytra mode.", new Object[0]);
         }
      }

      if (this.stateTickCounter > 15) {
         this.state = AutoRegear.RegearState.IDLE;
         this.placedBlocks.clear();
         this.pendingBlocks.clear();
         this.platformCenter = null;
         this.categoriesToProcess.clear();
         this.categoryIndex = 0;
         this.leftoverSweepAttempts = 0;
         this.sweepBaselineShulkers = Integer.MAX_VALUE;
         this.lastSweepShulkerCount = Integer.MAX_VALUE;
         this.timer = 0;
         this.shulkerEnderSlot = -1;
         this.transferStep = 0;
         this.transferSlotIndex = 0;
         this.placementAttempts = 0;
         this.ecAlignTicks = 0;
         this.ecOpenFailures = 0;
         this.returnOpenAttempts = 0;
         this.stateTickCounter = 0;
         this.hadBaritoneGoal = false;
         this.savedBaritoneCommand = null;
         this.reEnableStage = 0;
         this.processedShulkers.clear();
         this.cleanupBlockIndex = 0;
         this.cleanupBlockAttempts = 0;
      }
   }

   private boolean shouldTriggerRegear() {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         if (this.mc.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL) {
            return false;
         } else {
            int rockets = this.countRockets();
            int validElytras = this.countValidElytras();
            return rockets < this.minRockets.get()
               || validElytras < Math.min(this.minElytras.get(), this.goalElytras.get())
               || this.restockTotems.get()
                  && this.totemsTriggerRegear.get()
                  && this.countCategory(AutoRegear.RestockCategory.TOTEMS) < Math.min(this.minTotems.get(), this.goalTotems.get())
               || this.restockFood.get()
                  && this.foodTriggerRegear.get()
                  && this.countCategory(AutoRegear.RestockCategory.FOOD) < Math.min(this.minFood.get(), this.goalFood.get());
         }
      } else {
         return false;
      }
   }

   private int countRockets() {
      if (this.mc.player == null) {
         return 0;
      } else {
         int count = 0;

         for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
               count += stack.getCount();
            }
         }

         return count;
      }
   }

   private int countValidElytras() {
      if (this.mc.player == null) {
         return 0;
      } else {
         int count = 0;

         for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            if (this.isValidElytra(this.mc.player.getInventory().getStack(i))) {
               count++;
            }
         }

         return count;
      }
   }

   private boolean isValidElytra(ItemStack stack) {
      if (stack.getItem() != Items.ELYTRA) {
         return false;
      } else {
         int maxDurability = stack.getMaxDamage();
         int currentDurability = maxDurability - stack.getDamage();
         double percent = (double)currentDurability / maxDurability * 100.0;
         return percent >= this.elytraDurabilityThreshold.get().intValue();
      }
   }

   private AutoRegear.RestockCategory currentCategory() {
      return this.categoryIndex < this.categoriesToProcess.size() ? this.categoriesToProcess.get(this.categoryIndex) : null;
   }

   private boolean matchesCategory(AutoRegear.RestockCategory category, ItemStack stack) {
      Item item = stack.getItem();

      return switch (category) {
         case ELYTRA -> this.isValidElytra(stack);
         case TOTEMS -> item == Items.TOTEM_OF_UNDYING;
         case FOOD -> item == this.foodItem.get();
         case ROCKETS -> item == Items.FIREWORK_ROCKET;
      };
   }

   private boolean shulkerHasCategory(ItemStack shulker, AutoRegear.RestockCategory category) {
      ContainerComponent container = (ContainerComponent)shulker.get(DataComponentTypes.CONTAINER);
      if (container == null) {
         return false;
      } else {
         for (ItemStack content : ((ContainerComponentAccessor)(Object)container).getStacks()) {
            if (!content.isEmpty() && this.matchesCategory(category, content)) {
               return true;
            }
         }

         return false;
      }
   }

   private int countCategory(AutoRegear.RestockCategory category) {
      if (category == AutoRegear.RestockCategory.ELYTRA) {
         return this.countValidElytras();
      } else if (this.mc.player == null) {
         return 0;
      } else {
         int count = 0;

         for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (this.matchesCategory(category, stack)) {
               count += stack.getCount();
            }
         }

         return count;
      }
   }

   private int countEmptySlots() {
      int emptySlots = 0;

      for (int i = 0; i < 36; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            emptySlots++;
         }
      }

      return emptySlots;
   }

   private boolean isCategorySatisfied(AutoRegear.RestockCategory category) {
      return switch (category) {
         case ELYTRA -> this.countValidElytras() >= this.goalElytras.get() || this.countEmptySlots() <= 1 && !this.hasBrokenElytra();
         case TOTEMS -> this.countCategory(category) >= this.goalTotems.get() || !this.canAcceptMore(category);
         case FOOD -> this.countCategory(category) >= this.goalFood.get() || !this.canAcceptMore(category);
         case ROCKETS -> !this.canAcceptMore(category);
      };
   }

   private boolean canAcceptMore(AutoRegear.RestockCategory category) {
      if (this.countEmptySlots() > 1) {
         return true;
      } else {
         for (int i = 0; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getCount() < stack.getMaxCount() && this.matchesCategory(category, stack)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean hasSpaceForQuickMove(ItemStack stack) {
      if (this.countEmptySlots() > 1) {
         return true;
      } else {
         int partialCapacity = 0;

         for (int i = 0; i < 36; i++) {
            ItemStack inv = this.mc.player.getInventory().getStack(i);
            if (!inv.isEmpty() && inv.getCount() < inv.getMaxCount() && ItemStack.areItemsAndComponentsEqual(inv, stack)) {
               partialCapacity += inv.getMaxCount() - inv.getCount();
            }
         }

         return stack.getCount() <= partialCapacity;
      }
   }

   private void skipSatisfiedCategories() {
      while (this.currentCategory() != null && this.isCategorySatisfied(this.currentCategory())) {
         this.categoryIndex++;
      }
   }

   private boolean hasBrokenElytra() {
      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.ELYTRA && !this.isValidElytra(stack)) {
            return true;
         }
      }

      return false;
   }

   private int countShulkerBoxes() {
      int count = 0;

      for (int i = 0; i < 36; i++) {
         if (this.isShulkerBox(this.mc.player.getInventory().getStack(i).getItem())) {
            count++;
         }
      }

      return count;
   }

   private void ensureRocketsInHotbar() {
      if (this.mc.player != null) {
         int targetSlot = this.rocketHotbarSlot.get();
         ItemStack hotbarStack = this.mc.player.getInventory().getStack(targetSlot);
         if (hotbarStack.getItem() == Items.FIREWORK_ROCKET) {
            if (this.debugMessages.get()) {
               this.info("Rockets already in hotbar slot " + targetSlot, new Object[0]);
            }
         } else {
            int bestRocketSlot = -1;
            int bestRocketCount = 0;

            for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
               if (i != targetSlot && i < 36) {
                  ItemStack stack = this.mc.player.getInventory().getStack(i);
                  if (stack.getItem() == Items.FIREWORK_ROCKET && stack.getCount() > bestRocketCount) {
                     bestRocketSlot = i;
                     bestRocketCount = stack.getCount();
                  }
               }
            }

            if (bestRocketSlot != -1) {
               InvUtils.move().from(bestRocketSlot).to(targetSlot);
               if (this.debugMessages.get()) {
                  this.info("Moved rockets from slot " + bestRocketSlot + " to hotbar slot " + targetSlot, new Object[0]);
               }
            } else if (this.debugMessages.get()) {
               this.warning("No rockets found in inventory to move to hotbar slot " + targetSlot, new Object[0]);
            }
         }
      }
   }

   private void disableAllFlightModules() {
      for (Entry<String, Module> entry : this.flightModules.entrySet()) {
         Module module = entry.getValue();
         if (module != null && module.isActive()) {
            module.toggle();
            this.disabledModules.add(entry.getKey());
            if (this.debugMessages.get()) {
               this.info("Disabled " + entry.getKey(), new Object[0]);
            }
         }
      }
   }

   private void reEnableFlightModules() {
      if (this.reEnableStage == 0) {
         if (this.disabledModules.contains("TrailFollower")) {
            Module module = this.flightModules.get("TrailFollower");
            if (module != null && !module.isActive()) {
               module.toggle();
               if (this.debugMessages.get()) {
                  this.info("Re-enabled TrailFollower (priority)", new Object[0]);
               }
            }
         }

         this.disabledModules.remove("TrailFollower");
         this.reEnableStage = 1;
         this.timer = 5;
      } else {
         for (String moduleName : new ArrayList<>(this.disabledModules)) {
            Module module = this.flightModules.get(moduleName);
            if (module != null && !module.isActive()) {
               module.toggle();
               if (this.debugMessages.get()) {
                  this.info("Re-enabled " + moduleName, new Object[0]);
               }
            }
         }

         this.disabledModules.clear();
         this.reEnableStage = 2;
      }
   }

   private void restoreChestplate() {
      if (!this.savedChestplate.isEmpty()) {
         for (int i = 0; i < this.mc.player.getInventory().size(); i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ELYTRA) {
               InvUtils.move().from(i).toArmor(2);
               this.savedChestplate = ItemStack.EMPTY;
               return;
            }
         }
      }
   }

   private boolean placeBlockGrim(BlockPos pos) {
      int targetHotbarSlot = this.obsidianHotbarSlot.get();
      ItemStack currentStack = this.mc.player.getInventory().getStack(targetHotbarSlot);
      if (!(currentStack.getItem() instanceof BlockItem) || currentStack.isEmpty()) {
         FindItemResult blocks = InvUtils.find(itemStack -> {
            if (itemStack.getItem() instanceof BlockItem blockItem) {
               if (itemStack.getItem() == Items.ENDER_CHEST) {
                  return false;
               } else {
                  Block block = blockItem.getBlock();
                  return block.getDefaultState().isSolidBlock(this.mc.world, pos);
               }
            } else {
               return false;
            }
         });
         if (!blocks.found()) {
            if (this.debugMessages.get()) {
               this.error("No solid blocks found in inventory!", new Object[0]);
            }

            return false;
         }

         if (blocks.slot() != targetHotbarSlot) {
            InvUtils.move().from(blocks.slot()).to(targetHotbarSlot);
            this.timer = 1;
            return false;
         }
      }

      if (this.mc.player.getInventory().selectedSlot != targetHotbarSlot) {
         this.mc.player.getInventory().selectedSlot = targetHotbarSlot;
      }

      Vec3d targetVec = Vec3d.ofCenter(pos);
      float[] rotations = getRotationsTo(this.mc.player.getEyePos(), targetVec);
      Rotations.rotate(rotations[0], rotations[1], 50, () -> {
         BlockHitResult hitResult = new BlockHitResult(targetVec, Direction.UP, pos, false);
         int seq = ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
         this.mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hitResult, seq));
         this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
         this.mc.player.swingHand(Hand.MAIN_HAND);
      });
      return true;
   }

   private boolean isShulkerBox(Item item) {
      return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
   }

   private static float[] getRotationsTo(Vec3d src, Vec3d dest) {
      double diffX = dest.x - src.x;
      double diffY = dest.y - src.y;
      double diffZ = dest.z - src.z;
      double yaw = Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0;
      double pitch = -Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ)));
      return new float[]{MathHelper.wrapDegrees((float)yaw), MathHelper.wrapDegrees((float)pitch)};
   }

   @Override
   public String getInfoString() {
      return this.countRockets() + "R/" + this.countValidElytras() + "E - " + this.state.name();
   }

   private static enum RegearState {
      IDLE,
      SWAP_TO_CHESTPLATE,
      DISABLING_MODULES,
      DROPPING,
      CENTERING_ON_PLATFORM,
      CREATING_INITIAL_PLATFORM,
      DUMPING_TRASH,
      CREATING_WALLS,
      CLEARING_ECHEST_AREA,
      ROTATING_FOR_ECHEST,
      PLACING_ECHEST,
      WAIT_ECHEST_PLACE,
      OPENING_ECHEST,
      TAKING_SHULKER,
      WAIT_SHULKER_TAKEN,
      POSITIONING_FOR_SHULKER,
      ROTATING_FOR_SHULKER,
      PLACING_SHULKER,
      WAIT_SHULKER_PLACE,
      OPENING_SHULKER,
      TRANSFERRING_ITEMS,
      BREAKING_SHULKER,
      WAIT_SHULKER_BREAK,
      WAIT_SHULKER_PICKUP,
      OPENING_ECHEST_RETURN,
      RETURNING_SHULKER,
      CHECK_NEXT_SHULKER,
      RESTORING_ELYTRA,
      CLEANUP,
      TAKING_OFF,
      COMPLETE;
   }

   private static enum RestockCategory {
      ELYTRA,
      TOTEMS,
      FOOD,
      ROCKETS;
   }
}
