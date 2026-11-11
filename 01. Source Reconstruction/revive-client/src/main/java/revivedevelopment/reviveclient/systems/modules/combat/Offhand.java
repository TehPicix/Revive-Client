package revivedevelopment.reviveclient.systems.modules.combat;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShovelItem;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientPlayerInteractionManager;
import revivedevelopment.reviveclient.pingbypass.PbVia;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;
import revivedevelopment.reviveclient.utils.entity.EnchantUtil;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.SlotUtils;

public class Offhand extends Module {
   private static final int HOTBAR_SIZE = 9;
   private static final int INV_SIZE = 36;
   private static final int OFFHAND_SWAP_BUTTON = 40;
   private static final int OFFHAND_CONTAINER = 45;
   private static final int SILENT_SWAP_GRACE_TICKS = 6;
   private static final int GAP_MAIN_HAND_GRACE_TICKS = 2;
   private final SettingGroup sgMainHand = this.settings.createGroup("Main Hand");
   private final Setting<Boolean> mainHand = this.sgMainHand
      .add(
         new BoolSetting.Builder()
            .name("main-hand")
            .description(
               "Also keep a totem selected in your hotbar (held in the main hand) at low health. With the totem slot selected, right-click eats a gapple from the offhand (the main-hand totem stays and is rechambered even mid-bite)."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> stockSlot = this.sgMainHand
      .add(
         new BoolSetting.Builder()
            .name("stock-slot")
            .description(
               "Always keep a totem stocked in the main-hand slot (from spare inventory totems) without changing your selected slot, so scrolling to it is an instant main-hand totem. Ported from the old main-hand-totem."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> totemGapple = this.sgMainHand
      .add(
         new BoolSetting.Builder()
            .name("main-hand-totem-gapple")
            .description(
               "Totem in the main hand, golden apple in the offhand (like sword-gapple, but the main hand keeps you totem-protected while you eat). Falls back to an offhand totem when no main-hand totem is available, on predicted lethal damage or below the fallback health."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Offhand.TotemGappleMode> totemGappleMode = this.sgMainHand
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("totem-gapple-mode"))
                     .description(
                        "Hold: holding a totem + right-click moves a gapple to the offhand. Holding: whenever you hold a totem the offhand keeps a gapple. Always: force-selects the main-hand totem slot and keeps a gapple in the offhand."
                     ))
                  .defaultValue(Offhand.TotemGappleMode.Hold))
               .visible(this.totemGapple::get))
            .build()
      );
   private final Setting<Double> totemGappleHealth = this.sgMainHand
      .add(
         new DoubleSetting.Builder()
            .name("totem-gapple-fallback-health")
            .description(
               "At or below this health (incl. absorption) the offhand goes back to a totem even while main-hand-totem-gapple is active. 0 = only lethal prediction / missing totem fall back."
            )
            .defaultValue(0.0)
            .min(0.0)
            .range(0.0, 36.0)
            .sliderRange(0.0, 20.0)
            .visible(this.totemGapple::get)
            .build()
      );
   private final Setting<Integer> mainhandSlot = this.sgMainHand
      .add(
         new IntSetting.Builder()
            .name("slot")
            .description("Hotbar slot (1-9) to keep the main-hand totem in.")
            .defaultValue(3)
            .range(1, 9)
            .sliderRange(1, 9)
            .visible(() -> this.mainHand.get() || this.stockSlot.get() || this.totemGapple.get())
            .build()
      );
   private final Setting<Integer> mainhandHealth = this.sgMainHand
      .add(
         new IntSetting.Builder()
            .name("health")
            .description("Health threshold to force a totem into the main hand as well.")
            .defaultValue(6)
            .range(0, 20)
            .sliderRange(0, 20)
            .visible(this.mainHand::get)
            .build()
      );
   private final SettingGroup sgSafety = this.settings.createGroup("Safety");
   private final Setting<Boolean> safety = this.sgSafety
      .add(new BoolSetting.Builder().name("safety").description("Master toggle for lethal-damage prediction.").defaultValue(true).build());
   private final Setting<Boolean> lethalPrediction = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("predict-lethal")
            .description("Force a totem when predicted incoming damage would kill you.")
            .defaultValue(true)
            .visible(this.safety::get)
            .build()
      );
   private final Setting<Boolean> lethalSum = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("sum-damage")
            .description("Sum all nearby crystal damages instead of the single max (for 2b2t/Folia double-pops).")
            .defaultValue(true)
            .visible(() -> this.safety.get() && this.lethalPrediction.get())
            .build()
      );
   private final Setting<Boolean> lethalIncludeFall = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("include-fall")
            .description("Consider fall damage in lethal predictions.")
            .defaultValue(true)
            .visible(() -> this.safety.get() && this.lethalPrediction.get())
            .build()
      );
   private final Setting<Double> lethalBuffer = this.sgSafety
      .add(
         new DoubleSetting.Builder()
            .name("buffer")
            .description("Extra hearts of padding when comparing predicted damage to your health.")
            .defaultValue(0.5)
            .min(0.0)
            .range(0.0, 4.0)
            .sliderRange(0.0, 4.0)
            .visible(() -> this.safety.get() && this.lethalPrediction.get())
            .build()
      );
   private final Setting<Boolean> antiGhost = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("anti-ghost")
            .description(
               "On a totem pop, consume the popped totem client-side immediately (main hand first, like vanilla) instead of waiting for the server's slot update, so the rechamber click predicts the real server state and no ghost totem is left in hand."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> predictiveRender = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("predict-on-render")
            .description("Run the lethal projection every render frame (reacts faster than the 20Hz tick).")
            .defaultValue(true)
            .visible(() -> this.safety.get() && this.lethalPrediction.get())
            .build()
      );
   private final Setting<Boolean> nettyFastPop = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("netty-fast-pop")
            .description(
               "On a totem pop, fire the offhand re-swap straight from the network thread (a frame faster than the main-thread path). Auto-disables for 30s if the server rejects the raw clicks. Ignored when ViaVersion/ViaFabricPlus is installed - the raw click cannot be remapped to an older protocol and would be lost."
            )
            .defaultValue(false)
            .visible(this.safety::get)
            .build()
      );
   private final Setting<Boolean> strictLethalGap = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("strict-lethal-gap")
            .description(
               "When lethal damage is predicted, swap an offhand gapple back to a totem even if the main hand holds a totem (stops the bite). Safer against same-tick double pops, but you can't eat through predicted lethal hits."
            )
            .defaultValue(false)
            .visible(() -> this.safety.get() && this.lethalPrediction.get())
            .build()
      );
   private final Setting<Boolean> lethalMainhand = this.sgSafety
      .add(
         new BoolSetting.Builder()
            .name("mainhand-on-lethal")
            .description("Also place a totem in your main hand when lethal damage is predicted.")
            .defaultValue(true)
            .visible(() -> this.safety.get() && this.lethalPrediction.get() && this.mainHand.get())
            .build()
      );
   private final SettingGroup sgEating = this.settings.createGroup("Eating");
   private final Setting<Boolean> eatingGroup = this.sgEating
      .add(new BoolSetting.Builder().name("eating").description("Swap to a food item when right-clicking.").defaultValue(false).build());
   private final Setting<List<Item>> foodWhitelist = this.sgEating
      .add(
         new ItemListSetting.Builder()
            .name("foods")
            .description("Food items allowed for the eat swap.")
            .defaultValue(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.GOLDEN_CARROT, Items.COOKED_MUTTON, Items.BAKED_POTATO)
            .filter(item -> item.getComponents().contains(DataComponentTypes.FOOD))
            .visible(this.eatingGroup::get)
            .build()
      );
   private final Setting<Offhand.HandSide> foodHand = this.sgEating
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("hand"))
                     .description("Hand to use for eating food."))
                  .defaultValue(Offhand.HandSide.RIGHT))
               .visible(this.eatingGroup::get))
            .build()
      );
   private final Setting<Integer> gappleHealthThreshold = this.sgEating
      .add(
         new IntSetting.Builder()
            .name("gapple-health")
            .description("Health threshold to switch from food to a gapple.")
            .defaultValue(12)
            .range(0, 20)
            .sliderRange(0, 20)
            .visible(this.eatingGroup::get)
            .build()
      );
   private final SettingGroup sgGap = this.settings.createGroup("Gapping");
   private final Setting<Boolean> gapGroup = this.sgGap
      .add(new BoolSetting.Builder().name("gapping").description("Eat a gapple in the offhand while holding a tool (right-click).").defaultValue(false).build());
   private final Setting<Boolean> swordGapple = this.sgGap
      .add(
         new BoolSetting.Builder()
            .name("sword-gapple")
            .description(
               "Holding a sword + right-click moves a golden apple into the offhand so you can eat without switching off the sword (independent of 'gapping')."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Offhand.HandSide> gapHand = this.sgGap
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("hand"))
                     .description("Hand to hold the gapple in while gapping."))
                  .defaultValue(Offhand.HandSide.RIGHT))
               .visible(this.gapGroup::get))
            .build()
      );
   private final Setting<Double> gapMinHealth = this.sgGap
      .add(
         new DoubleSetting.Builder()
            .name("min-health")
            .description("Minimum health required before gapping can start.")
            .defaultValue(0.0)
            .min(0.0)
            .range(0.0, 20.0)
            .sliderRange(0.0, 20.0)
            .visible(() -> this.gapGroup.get() && this.gapHand.get() == Offhand.HandSide.LEFT)
            .build()
      );
   private final Setting<Boolean> gapOnlyPhased = this.sgGap
      .add(
         new BoolSetting.Builder()
            .name("only-phased")
            .description("Only gap when phased inside a block.")
            .defaultValue(false)
            .visible(() -> this.gapGroup.get() && this.gapHand.get() == Offhand.HandSide.LEFT)
            .build()
      );
   private final Setting<Boolean> gapBedrockOnly = this.sgGap
      .add(
         new BoolSetting.Builder()
            .name("bedrock-only")
            .description("Require phasing specifically inside bedrock before gapping.")
            .defaultValue(false)
            .visible(() -> this.gapGroup.get() && this.gapOnlyPhased.get() && this.gapHand.get() == Offhand.HandSide.LEFT)
            .build()
      );
   private final SettingGroup sgCrystal = this.settings.createGroup("Crystal");
   private final Setting<Boolean> crystalGroup = this.sgCrystal
      .add(
         new BoolSetting.Builder()
            .name("crystal-offhand")
            .description("Keep an end crystal in your offhand; swap to a totem when low health.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> crystalHealthThreshold = this.sgCrystal
      .add(
         new IntSetting.Builder()
            .name("health")
            .description("Health threshold to swap from the crystal back to a totem.")
            .defaultValue(8)
            .range(1, 20)
            .sliderRange(1, 20)
            .visible(this.crystalGroup::get)
            .build()
      );
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final Setting<Boolean> debug = this.sgDebug
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description(
               "Log to chat: lethal prediction flips, gap sessions, the offhand gapple going in, out or blocked (and why), lethal hits covered by the main hand vs. the offhand, and every inventory click with the sprint state the server holds."
            )
            .defaultValue(false)
            .build()
      );
   private static final Set<Block> AXE_STRIPPABLE_BLOCKS = Set.of(
      Blocks.OAK_WOOD,
      Blocks.OAK_LOG,
      Blocks.DARK_OAK_WOOD,
      Blocks.DARK_OAK_LOG,
      Blocks.ACACIA_WOOD,
      Blocks.ACACIA_LOG,
      Blocks.CHERRY_WOOD,
      Blocks.CHERRY_LOG,
      Blocks.BIRCH_WOOD,
      Blocks.BIRCH_LOG,
      Blocks.JUNGLE_WOOD,
      Blocks.JUNGLE_LOG,
      Blocks.SPRUCE_WOOD,
      Blocks.SPRUCE_LOG,
      Blocks.WARPED_STEM,
      Blocks.WARPED_HYPHAE,
      Blocks.CRIMSON_STEM,
      Blocks.CRIMSON_HYPHAE,
      Blocks.MANGROVE_WOOD,
      Blocks.MANGROVE_LOG,
      Blocks.BAMBOO_BLOCK
   );
   private static final Set<Block> SHOVEL_PATH_BLOCKS = Set.of(
      Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MYCELIUM, Blocks.ROOTED_DIRT
   );
   private final Deque<Offhand.OffhandSwapRecord> offhandSwapHistory = new ArrayDeque<>();
   private boolean suppressOffhandTotem;
   private int pendingRestoreSlot = -1;
   private int priorityMainhandTicks;
   private int pendingMainhandRechamberDelay;
   private int pendingHotbarBackfillSlot = -1;
   private int pendingHotbarBackfillDelay;
   private int pendingHotbarBackfillAttempts;
   private int inventoryCloseGraceTicks;
   private boolean wasInventoryOpen;
   private int silentSwapGraceTicks;
   private boolean lastTickMainHandTotem;
   private int lastTickSelectedSlot = -1;
   private ItemStack lastMainHandStack = ItemStack.EMPTY;
   private ItemStack lastOffHandStack = ItemStack.EMPTY;
   private int pendingPopRechamberTicks;
   private volatile int latestStateId = 0;
   private volatile int snapshotContainerId = 0;
   private volatile int snapshotTotemContainerSlot = -1;
   private volatile int snapshotTotemInvSlot = -1;
   private volatile int rawPopInvSlot = -1;
   private volatile boolean snapshotInventoryMenuOpen = false;
   private volatile boolean snapshotOffhandTotem = false;
   private volatile long lastRawSendMs = 0L;
   private volatile long rawRejectionWindowStartMs = 0L;
   private volatile int rawRejectionStreak = 0;
   private volatile long rawDisabledUntilMs = 0L;
   private final boolean[] managedHotbarTotem = new boolean[9];
   private final boolean[] lastHotbarTotem = new boolean[9];
   private int previousRightHandSlot = -1;
   private int skipFoodSwapTicks;
   private boolean suppressFoodSwapUntilRelease;
   private boolean waitingForUseKeyRelease;
   private int emptyHandSuppressionTicks;
   private boolean overrideSlotLockForFood;
   private boolean gapSession;
   private World gapSessionWorld;
   private int gapCoverFromSlot = -1;
   private int gapCoverSlot = -1;
   private boolean gapRipped;
   private int gapOffMainTicks;
   private boolean releasedThisTick;
   private boolean actedThisTick;
   private boolean replayedThisTick;
   private boolean coveredBeforeReplay;
   private volatile boolean serverSprinting;
   private boolean lastLethal;
   private boolean printedLethal;
   private long lastLethalDbgMs;
   private int mutedLethalFlips;
   private float lastCrystalDamage;
   private float lastMeleeDamage;
   private float lastFallDamage;
   private int lastCrystalCount;
   private String lastGapDbg;
   private int tickCounter;
   private int bedCacheTick = -1;
   private float cachedBedDamage;
   private static final boolean VIA_PRESENT = FabricLoader.getInstance().isModLoaded("viaversion")
      || FabricLoader.getInstance().isModLoaded("viabackwards")
      || FabricLoader.getInstance().isModLoaded("viafabric");
   private static final boolean VFP_PRESENT = FabricLoader.getInstance().isModLoaded("viafabricplus");

   public Offhand() {
      super(Categories.Combat, "offhand", "Ensures you always have a totem in your hand.");
   }

   @Override
   public void onActivate() {
      this.serverSprinting = this.mc.player != null && this.mc.player.isSprinting();
   }

   @Override
   public void onDeactivate() {
      this.suppressOffhandTotem = false;
      this.pendingRestoreSlot = -1;
      this.priorityMainhandTicks = 0;
      this.pendingMainhandRechamberDelay = 0;
      this.pendingHotbarBackfillSlot = -1;
      this.pendingHotbarBackfillDelay = 0;
      this.pendingHotbarBackfillAttempts = 0;
      this.offhandSwapHistory.clear();

      for (int i = 0; i < this.managedHotbarTotem.length; i++) {
         this.managedHotbarTotem[i] = false;
      }

      this.previousRightHandSlot = -1;
      this.skipFoodSwapTicks = 0;
      this.suppressFoodSwapUntilRelease = false;
      this.waitingForUseKeyRelease = false;
      this.emptyHandSuppressionTicks = 0;
      this.overrideSlotLockForFood = false;
      this.pendingPopRechamberTicks = 0;
      this.rawPopInvSlot = -1;
      this.bedCacheTick = -1;
      this.lastMainHandStack = ItemStack.EMPTY;
      this.lastOffHandStack = ItemStack.EMPTY;
      this.gapSession = false;
      this.gapSessionWorld = null;
      this.gapCoverFromSlot = -1;
      this.gapRipped = false;
      this.releasedThisTick = false;
      this.actedThisTick = false;
      this.replayedThisTick = false;
      this.coveredBeforeReplay = false;
      this.lastLethal = false;
      this.printedLethal = false;
      this.mutedLethalFlips = 0;
      this.lastGapDbg = null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.endGapSession("left the game");
      this.gapCoverFromSlot = -1;
      this.releasedThisTick = false;
      this.actedThisTick = false;
      this.replayedThisTick = false;
      this.coveredBeforeReplay = false;
      this.serverSprinting = false;
   }

   @EventHandler(
      priority = 200
   )
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.tickCounter++;
         this.overrideSlotLockForFood = false;
         boolean useKeyPressed = this.isUseKeyPressed();
         boolean skipFoodSwap = this.skipFoodSwapTicks > 0 || this.suppressFoodSwapUntilRelease;
         if (this.skipFoodSwapTicks > 0) {
            this.skipFoodSwapTicks--;
         }

         boolean inventoryOpen = this.isInventoryScreenOpen();
         if (inventoryOpen) {
            this.wasInventoryOpen = true;
         } else if (this.wasInventoryOpen) {
            this.inventoryCloseGraceTicks = Math.max(this.inventoryCloseGraceTicks, 8);
            this.wasInventoryOpen = false;
         }

         if (this.inventoryCloseGraceTicks > 0 && !inventoryOpen) {
            this.inventoryCloseGraceTicks--;
         }

         boolean interactionLocked = inventoryOpen || this.inventoryCloseGraceTicks > 0;
         if (ReviveClient.SWAP.isSilentSwapActive()) {
            this.silentSwapGraceTicks = 6;
         } else if (this.silentSwapGraceTicks > 0) {
            this.silentSwapGraceTicks--;
         }

         try {
            if (ReviveClient.SWAP.isSilentSwapActive()) {
               if (this.waitingForUseKeyRelease && !useKeyPressed) {
                  this.releaseFoodSwapSuppression();
               }

               return;
            }

            this.updateGapSession(useKeyPressed);
            if (this.pendingPopRechamberTicks > 0) {
               this.pendingPopRechamberTicks--;
               if (this.mc.player.getInventory().getStack(this.mc.player.getInventory().selectedSlot).isEmpty()
                  || !this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                  this.ensureMainHandRechamberIfNeeded();
               }
            }

            if (this.pendingRestoreSlot != -1) {
               this.mc.player.getInventory().selectedSlot = this.pendingRestoreSlot;
               this.pendingRestoreSlot = -1;
            }

            if (this.waitingForUseKeyRelease && !useKeyPressed) {
               this.releaseFoodSwapSuppression();
               skipFoodSwap = this.skipFoodSwapTicks > 0;
            } else if (this.suppressFoodSwapUntilRelease && (!useKeyPressed || !this.mc.player.isUsingItem())) {
               this.suppressFoodSwapUntilRelease = false;
               this.waitingForUseKeyRelease = false;
               skipFoodSwap = this.skipFoodSwapTicks > 0;
            }

            if (this.suppressFoodSwapUntilRelease) {
               if (this.mc.player.getMainHandStack().isEmpty()) {
                  if (++this.emptyHandSuppressionTicks > 5) {
                     this.releaseFoodSwapSuppression();
                     skipFoodSwap = this.skipFoodSwapTicks > 0;
                  }
               } else {
                  this.emptyHandSuppressionTicks = 0;
               }
            } else {
               this.emptyHandSuppressionTicks = 0;
            }

            boolean usingMainHandItem = this.mc.player.isUsingItem() && this.mc.player.getActiveHand() == Hand.MAIN_HAND;
            boolean swordGapHeld = this.swordGapple.get() && this.isSword(this.mc.player.getMainHandStack());
            boolean usingOffHandGap = this.mc.player.isUsingItem() && this.mc.player.getActiveHand() == Hand.OFF_HAND;
            boolean suppressRechamber = useKeyPressed && !usingOffHandGap && (this.gapGroup.get() || this.eatingGroup.get() || swordGapHeld)
               || usingMainHandItem;
            if (!interactionLocked) {
               if (this.pendingHotbarBackfillSlot != -1) {
                  if (this.pendingHotbarBackfillDelay > 0) {
                     this.pendingHotbarBackfillDelay--;
                  }

                  if (this.pendingHotbarBackfillDelay == 0) {
                     boolean filled = this.forceBackfillHotbarTotem(this.pendingHotbarBackfillSlot, true);
                     if (!filled && ++this.pendingHotbarBackfillAttempts <= 12) {
                        this.pendingHotbarBackfillDelay = 1;
                     } else {
                        this.pendingHotbarBackfillSlot = -1;
                        this.pendingHotbarBackfillDelay = 0;
                        this.pendingHotbarBackfillAttempts = 0;
                     }
                  }
               }

               this.detectAndBackfillLostHotbarTotems();
            }

            if (!suppressRechamber && !interactionLocked && this.priorityMainhandTicks > 0) {
               this.applyMainHand(Items.TOTEM_OF_UNDYING, true);
               this.priorityMainhandTicks--;
            }

            if (this.pendingMainhandRechamberDelay > 0 && !suppressRechamber && !interactionLocked) {
               this.pendingMainhandRechamberDelay--;
               if (this.pendingMainhandRechamberDelay == 0) {
                  this.restockSelectedSlotWithTotem(true);
               }
            }

            if (!suppressRechamber && !interactionLocked) {
               this.ensureMainHandRechamberIfNeeded();
            }

            if (this.stockSlot.get() && !suppressRechamber && !interactionLocked) {
               this.stockMainHandSlot();
            }

            if (this.suppressOffhandTotem && !this.gapSession && (!useKeyPressed || !this.mc.player.isUsingItem())) {
               this.suppressOffhandTotem = false;
            }

            if (this.previousRightHandSlot != -1 && !useKeyPressed) {
               int restoreSlot = this.resolvePostEatRestoreSlot(this.previousRightHandSlot);
               if (!this.releasedThisTick && !this.replayedThisTick) {
                  this.mc.player.getInventory().selectedSlot = restoreSlot;
               }

               this.pendingRestoreSlot = restoreSlot;
               this.previousRightHandSlot = -1;
            }

            if (this.coverHeld()) {
               this.gapCoverSlot = this.mc.player.getInventory().selectedSlot;
            }

            boolean lethalRisk = this.safety.get() && this.lethalPrediction.get() && this.isLethalDamageIncoming();
            this.dbgLethal(lethalRisk);
            boolean offhandGap = this.offhandGapProtected();
            boolean keepOffhandGap = false;
            if (lethalRisk && offhandGap && !this.strictLethalGap.get()) {
               if ((!this.coveredBeforeReplay || !this.actedThisTick || usingOffHandGap) && !this.coverGapWithMainTotem()) {
                  if (this.countTotems() == 0) {
                     keepOffhandGap = true;
                     this.dbgGap("lethal predicted: no totem anywhere, the gapple stays");
                  }
               } else {
                  keepOffhandGap = true;
                  this.dbgGap("lethal predicted: the main-hand totem covers the gapple");
               }
            }

            if (lethalRisk && !keepOffhandGap) {
               if (offhandGap) {
                  this.dbgGap(
                     this.strictLethalGap.get()
                        ? "lethal predicted (strict-lethal-gap): offhand totem instead"
                        : "lethal predicted, no main-hand totem to cover the gapple: offhand totem instead"
                  );
               }

               this.suppressOffhandTotem = false;
               this.ensureTotemInOffhand();
               if (offhandGap && !this.hasGappleInOffhand()) {
                  if (!this.strictLethalGap.get()) {
                     this.gapRipped = true;
                  }

                  this.endGapSession("lethal fallback");
               }

               if (this.mainHand.get() && this.lethalMainhand.get()) {
                  this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
                  this.priorityMainhandTicks = Math.max(this.priorityMainhandTicks, 4);
               }
            }

            this.restoreGapCoverSlot(lethalRisk);
            if (this.tryHandleTotemGapple(useKeyPressed, lethalRisk, interactionLocked)) {
               return;
            }

            if (this.tryHandleFoodSwap(skipFoodSwap, useKeyPressed, lethalRisk)) {
               return;
            }

            if (this.handleGap(useKeyPressed, lethalRisk, false, "use key")) {
               return;
            }

            if (this.isGapping()) {
               if (!this.gapSession && this.mc.player.getActiveHand() != Hand.OFF_HAND) {
                  this.ensureTotemInOffhand();
               } else {
                  this.suppressOffhandTotem = true;
               }

               return;
            }

            label761: {
               boolean usingOffHandItem = this.mc.player.isUsingItem() && this.mc.player.getActiveHand() == Hand.OFF_HAND;
               if (this.crystalGroup.get() && !usingOffHandItem) {
                  double crystalHp = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
                  if (!(crystalHp <= this.crystalHealthThreshold.get().intValue()) && !lethalRisk) {
                     if (this.hasGappleInOffhand()) {
                        this.dbgGap("gapple out: crystal-offhand");
                     }

                     if (!this.mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) {
                        this.applyOffHand(Items.END_CRYSTAL);
                     }
                     break label761;
                  }

                  if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                     this.applyOffHand(Items.TOTEM_OF_UNDYING);
                  }
               }

               if (!this.suppressOffhandTotem && !usingOffHandItem) {
                  if (this.hasGappleInOffhand()) {
                     this.dbgGap("gapple out: no gap session or eat holds it");
                  }

                  this.ensureTotemInOffhand();
               }

               if (!this.mainHand.get()) {
                  return;
               }

               double currentHp = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
               if (currentHp > this.mainhandHealth.get().intValue()) {
                  return;
               }

               this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
               return;
            }

            if (this.mainHand.get()) {
               double mainHp = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
               if (mainHp <= this.mainhandHealth.get().intValue()) {
                  this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
               }

               return;
            }
         } finally {
            this.overrideSlotLockForFood = false;
            this.replayedThisTick = false;
            this.coveredBeforeReplay = false;
            this.cacheHandStacks();
            this.snapshotHotbarTotems();
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onReceivePacket(PacketEvent.Receive event) {
      if (this.nettyFastPop.get() && event.packet instanceof ScreenHandlerSlotUpdateS2CPacket setSlot && setSlot.getSyncId() == 0 && setSlot.getSlot() == 45) {
         long sinceSend = System.currentTimeMillis() - this.lastRawSendMs;
         if (sinceSend > 0L && sinceSend < 200L && !setSlot.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
            this.noteRawRejection();
         }
      } else if (event.packet instanceof EntityStatusS2CPacket packet) {
         if (packet.getStatus() == 35) {
            this.tryRawFastPop(packet);
            this.mc.execute(() -> {
               if (this.isActive()) {
                  this.handleTotemPop(packet);
               }
            });
         }
      }
   }

   private void tryRawFastPop(EntityStatusS2CPacket packet) {
      if (this.nettyFastPop.get() && !this.viaTranslating()) {
         long now = System.currentTimeMillis();
         if (now >= this.rawDisabledUntilMs) {
            if (now - this.lastRawSendMs >= 50L) {
               if (this.snapshotInventoryMenuOpen && this.snapshotOffhandTotem) {
                  int containerSlot = this.snapshotTotemContainerSlot;
                  if (containerSlot >= 0) {
                     if (this.mc.world != null && this.mc.player != null) {
                        Entity ent;
                        try {
                           ent = packet.getEntity(this.mc.world);
                        } catch (Throwable var10) {
                           return;
                        }

                        if (ent == this.mc.player) {
                           if (ReviveClient.PACKET.canSend(PacketManager.Category.OFFHAND)) {
                              ClientPlayNetworkHandler conn = this.mc.getNetworkHandler();
                              if (conn != null) {
                                 Int2ObjectMap<ItemStackHash> changed = new Int2ObjectOpenHashMap();
                                 ClickSlotC2SPacket clickPkt = new ClickSlotC2SPacket(
                                    this.snapshotContainerId,
                                    this.latestStateId,
                                    (short)containerSlot,
                                    (byte)40,
                                    SlotActionType.SWAP,
                                    changed,
                                    ItemStackHash.EMPTY
                                 );
                                 this.lastRawSendMs = now;
                                 this.rawPopInvSlot = this.snapshotTotemInvSlot;
                                 conn.sendPacket(clickPkt);
                                 if (this.debug.get()) {
                                    String grim = this.grimClickState();
                                    this.mc.execute(() -> this.dbg("raw pop click SWAP slot " + containerSlot + " button 40, " + grim));
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
   private void onPacketSent(PacketEvent.Sent event) {
      if (event.packet instanceof ClientTickEndC2SPacket) {
         this.releasedThisTick = false;
         this.actedThisTick = false;
      } else if (event.packet instanceof PlayerActionC2SPacket action) {
         switch (action.getAction()) {
            case RELEASE_USE_ITEM:
               this.releasedThisTick = this.actedThisTick = true;
               break;
            case STAB:
               this.actedThisTick = true;
         }
      } else if (event.packet instanceof PlayerInteractEntityC2SPacket
         || event.packet instanceof PlayerInteractBlockC2SPacket
         || event.packet instanceof PlayerInteractItemC2SPacket) {
         this.actedThisTick = true;
      }

      if (event.packet instanceof ClientCommandC2SPacket command) {
         if (command.getMode() == Mode.START_SPRINTING) {
            this.serverSprinting = true;
         } else if (command.getMode() == Mode.STOP_SPRINTING) {
            this.serverSprinting = false;
         }
      }
   }

   private boolean viaTranslating() {
      if (VIA_PRESENT) {
         return true;
      } else if (!VFP_PRESENT) {
         return false;
      } else if (!PbVia.connectionVersionIsServers) {
         return true;
      } else {
         ClientPlayNetworkHandler handler = this.mc.getNetworkHandler();
         return handler == null || !PbVia.available() || PbVia.isTranslating(handler.getConnection());
      }
   }

   private void noteRawRejection() {
      long now = System.currentTimeMillis();
      if (now - this.rawRejectionWindowStartMs > 10000L) {
         this.rawRejectionWindowStartMs = now;
         this.rawRejectionStreak = 0;
      }

      if (++this.rawRejectionStreak >= 3) {
         this.rawDisabledUntilMs = now + 30000L;
         this.rawRejectionStreak = 0;
      }
   }

   private void handleTotemPop(EntityStatusS2CPacket packet) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.mc.player.equals(packet.getEntity(this.mc.world))) {
            boolean swapBusy = ReviveClient.SWAP.isSilentSwapActive();
            boolean hadMainHandTotem = !this.lastMainHandStack.isEmpty() && this.lastMainHandStack.isOf(Items.TOTEM_OF_UNDYING);
            boolean hadOffHandTotem = !this.lastOffHandStack.isEmpty() && this.lastOffHandStack.isOf(Items.TOTEM_OF_UNDYING);
            boolean usingItem = this.mc.player.isUsingItem();
            Hand usedHand = usingItem ? this.mc.player.getActiveHand() : null;
            boolean usingOffhand = usingItem && usedHand == Hand.OFF_HAND;
            boolean usingMainhand = usingItem && usedHand == Hand.MAIN_HAND;
            if (this.antiGhost.get()) {
               this.consumePoppedTotemClientSide(swapBusy);
            }

            boolean rawHandled = this.mirrorRawFastPop();
            boolean mainHandNeedsRechamber = hadMainHandTotem && !this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
            if (!usingOffhand && !this.gapSession && !rawHandled) {
               if (hadOffHandTotem) {
                  this.forceRechamberOffhandOnPop();
               } else if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                  if (swapBusy) {
                     this.forceRechamberOffhandOnPop();
                  } else {
                     this.ensureOffhandTotem(true, true);
                  }
               }
            }

            if (mainHandNeedsRechamber && !this.isInventoryScreenOpen() && !usingMainhand && !swapBusy) {
               this.forceRechamberMainHandOnPop();
               this.priorityMainhandTicks = Math.max(this.priorityMainhandTicks, 10);
            } else if (mainHandNeedsRechamber && (usingMainhand || swapBusy)) {
               this.priorityMainhandTicks = Math.max(this.priorityMainhandTicks, 10);
               this.pendingPopRechamberTicks = Math.max(this.pendingPopRechamberTicks, 10);
            }

            if (!swapBusy) {
               this.cacheHandStacks();
            }
         }
      }
   }

   private void consumePoppedTotemClientSide(boolean swapBusy) {
      ItemStack main = this.mc.player.getMainHandStack();
      ItemStack off = this.mc.player.getOffHandStack();
      if (!swapBusy && main.isOf(Items.TOTEM_OF_UNDYING)) {
         main.decrement(1);
      } else if (off.isOf(Items.TOTEM_OF_UNDYING)) {
         off.decrement(1);
      }
   }

   private boolean mirrorRawFastPop() {
      int rawSlot = this.rawPopInvSlot;
      this.rawPopInvSlot = -1;
      if (rawSlot >= 0 && rawSlot < 36) {
         if (System.currentTimeMillis() - this.lastRawSendMs > 500L) {
            return false;
         } else {
            PlayerInventory inv = this.mc.player.getInventory();
            ItemStack source = inv.getStack(rawSlot);
            if (!source.isOf(Items.TOTEM_OF_UNDYING)) {
               return false;
            } else {
               ItemStack offhandNow = this.mc.player.getOffHandStack().copy();
               this.mc.player.equipStack(EquipmentSlot.OFFHAND, source.copy());
               inv.setStack(rawSlot, offhandNow);
               if (isHotbar(rawSlot)) {
                  this.markManagedHotbarSlot(rawSlot);
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   private void forceRechamberOffhandOnPop() {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         PlayerScreenHandler h = this.handler();
         if (h != null) {
            if (!h.getCursorStack().isEmpty()) {
               this.ensureOffhandTotem(true, true);
            } else {
               int totemSlot = this.find(Items.TOTEM_OF_UNDYING, true);
               if (totemSlot != -1) {
                  int handlerSlot = SlotUtils.indexToId(totemSlot);
                  if (handlerSlot != -1) {
                     this.click(handlerSlot, 40, SlotActionType.SWAP);
                     if (isHotbar(totemSlot)) {
                        this.markManagedHotbarSlot(totemSlot);
                     }
                  }
               }
            }
         }
      }
   }

   private void forceRechamberMainHandOnPop() {
      if (this.mc.player != null && this.mc.interactionManager != null && this.mc.getNetworkHandler() != null) {
         PlayerScreenHandler h = this.handler();
         if (h != null && h.getCursorStack().isEmpty()) {
            int hotbarTotemSlot = -1;

            for (int i = 0; i < 9; i++) {
               if (this.mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                  hotbarTotemSlot = i;
                  break;
               }
            }

            if (hotbarTotemSlot == -1) {
               int invSlot = this.findInMainInventory(Items.TOTEM_OF_UNDYING, true);
               if (invSlot != -1) {
                  int invSlotContainer = SlotUtils.indexToId(invSlot);
                  if (invSlotContainer != -1) {
                     int currentSelected = this.mc.player.getInventory().selectedSlot;
                     this.click(invSlotContainer, currentSelected, SlotActionType.SWAP);
                     this.markManagedHotbarSlot(currentSelected);
                  }
               }
            } else {
               this.mc.player.getInventory().selectedSlot = hotbarTotemSlot;
               if (PbVia.connectionVersionIsServers || !this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                  ((IClientPlayerInteractionManager)this.mc.interactionManager).revive$syncSelected();
               }

               this.markManagedHotbarSlot(hotbarTotemSlot);
            }
         }
      }
   }

   private boolean inSilentSwapGrace() {
      return this.silentSwapGraceTicks > 0 || ReviveClient.SWAP.isSilentSwapActive();
   }

   private void ensureTotemInOffhand() {
      if (this.mc.player != null) {
         if (!this.inSilentSwapGrace()) {
            if (this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
               this.finalizeOffhandSwapHistory();
            } else if (!this.offhandSwapHistory.isEmpty()) {
               this.restoreOffhandSwaps();
            } else {
               this.applyOffHand(Items.TOTEM_OF_UNDYING);
            }
         }
      }
   }

   private void finalizeOffhandSwapHistory() {
      if (!this.offhandSwapHistory.isEmpty() && this.mc.player != null && this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         this.offhandSwapHistory.clear();
      }
   }

   private void restoreOffhandSwaps() {
      if (this.mc.player == null) {
         this.offhandSwapHistory.clear();
      } else {
         while (!this.offhandSwapHistory.isEmpty()) {
            if (this.mc.player.getOffHandStack().isEmpty()) {
               this.offhandSwapHistory.pop();
            } else {
               Offhand.OffhandSwapRecord record = this.offhandSwapHistory.pop();
               if (record.slot >= 0 && record.slot < 36) {
                  if (this.isSlotSwapLocked(record.slot)) {
                     this.offhandSwapHistory.push(record);
                     break;
                  }

                  this.swapOffhandIntoSlot(record.slot);
               }
            }
         }

         if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            this.applyOffHand(Items.TOTEM_OF_UNDYING);
         }

         this.offhandSwapHistory.clear();
      }
   }

   private void applyOffHand(Item item) {
      if (this.mc.player.getOffHandStack().getItem() != item) {
         int itemSlot = this.find(item);
         if (itemSlot != -1) {
            this.applyOffHand(itemSlot);
         }
      }
   }

   private void applyOffHand(int itemSlot) {
      if (itemSlot >= 0) {
         if (!this.isSlotSwapLocked(itemSlot)) {
            if (!this.hasCleanOffhandContext()) {
               this.cleanupCursor(itemSlot);
               if (!this.hasCleanOffhandContext()) {
                  return;
               }
            }

            ItemStack offHandStack = this.mc.player.getOffHandStack();
            ItemStack itemStack = this.mc.player.getInventory().getStack(itemSlot);
            if (offHandStack.getItem() != itemStack.getItem()) {
               boolean sourcedFromHotbar = isHotbar(itemSlot);
               if (this.swapSlotIntoOffhand(itemSlot, itemStack.getItem(), false)) {
                  if (sourcedFromHotbar) {
                     this.markManagedHotbarSlot(itemSlot);
                     this.restockHotbarSlotIfEmpty(itemSlot, itemStack.getItem(), false);
                     if (this.mc.player.getInventory().selectedSlot == itemSlot
                        && this.mc.player.getInventory().getStack(itemSlot).isEmpty()
                        && !this.isInventoryScreenOpen()) {
                        this.restockSelectedSlotWithTotem(true);
                        this.pendingMainhandRechamberDelay = Math.max(this.pendingMainhandRechamberDelay, 1);
                        this.priorityMainhandTicks = Math.max(this.priorityMainhandTicks, 4);
                     }
                  }
               } else {
                  this.cleanupCursor(itemSlot);
                  if (sourcedFromHotbar) {
                     this.markManagedHotbarSlot(itemSlot);
                     this.restockHotbarSlotIfEmpty(itemSlot, itemStack.getItem(), false);
                  }
               }
            }
         }
      }
   }

   private void applyMainHand(Item item, boolean bypassLocks) {
      if (!this.isInventoryScreenOpen()) {
         if (this.mc.player.getMainHandStack().getItem() != item) {
            for (int i = 0; i < 9; i++) {
               if (this.mc.player.getInventory().getStack(i).getItem() == item) {
                  this.applyMainHand(i, bypassLocks);
                  return;
               }
            }

            int itemSlot = this.find(item);
            if (itemSlot != -1) {
               this.applyMainHand(itemSlot, bypassLocks);
            }
         }
      }
   }

   private void applyMainHand(int itemSlot, boolean bypassLocks) {
      if (!this.isInventoryScreenOpen()) {
         if (itemSlot >= 0) {
            if (bypassLocks || !this.isSlotSwapLocked(itemSlot)) {
               ItemStack sourceStack = this.mc.player.getInventory().getStack(itemSlot);
               if (!sourceStack.isEmpty()) {
                  if (this.mc.player.getMainHandStack().getItem() != sourceStack.getItem() || this.mc.player.getInventory().selectedSlot != itemSlot) {
                     if (itemSlot < 9) {
                        this.mc.player.getInventory().selectedSlot = itemSlot;
                     } else {
                        int targetSlot = this.mainhandSlot.get() - 1;
                        ItemStack targetStack = this.mc.player.getInventory().getStack(targetSlot);
                        if (targetStack.getItem() == sourceStack.getItem()) {
                           this.mc.player.getInventory().selectedSlot = targetSlot;
                        } else {
                           if (!targetStack.isEmpty() || targetStack.isOf(Items.END_CRYSTAL)) {
                              for (int i = 0; i < 9; i++) {
                                 ItemStack candidate = this.mc.player.getInventory().getStack(i);
                                 if (candidate.isEmpty() && !candidate.isOf(Items.END_CRYSTAL) && (bypassLocks || !this.isSlotSwapLocked(i))) {
                                    targetSlot = i;
                                    break;
                                 }
                              }
                           }

                           if (!this.mc.player.getInventory().getStack(targetSlot).isOf(Items.END_CRYSTAL)) {
                              if (bypassLocks || !this.isSlotSwapLocked(targetSlot)) {
                                 if (this.swapSlotIntoHotbar(itemSlot, targetSlot, sourceStack.getItem(), bypassLocks)) {
                                    this.mc.player.getInventory().selectedSlot = targetSlot;
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

   private PlayerScreenHandler handler() {
      return this.mc.player.currentScreenHandler instanceof PlayerScreenHandler h ? h : null;
   }

   private boolean hasCleanOffhandContext() {
      PlayerScreenHandler h = this.handler();
      return h != null && h.getCursorStack().isEmpty();
   }

   private boolean canUseSwapPackets() {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         PlayerScreenHandler h = this.handler();
         if (h == null || !h.getCursorStack().isEmpty()) {
            return false;
         } else {
            return ReviveClient.SWAP.isSilentSwapActive() ? false : ReviveClient.PACKET.canSendCritical(PacketManager.Category.CLICK_SLOT);
         }
      } else {
         return false;
      }
   }

   private void click(int slotId, int button, SlotActionType action) {
      if (this.debug.get()) {
         this.dbg("click " + action + " slot " + slotId + " button " + button + ", " + this.grimClickState());
      }

      this.mc.interactionManager.clickSlot(this.mc.player.currentScreenHandler.syncId, slotId, button, action, this.mc.player);
   }

   private String grimClickState() {
      return "server sprint " + (this.serverSprinting ? "ON" : "off");
   }

   private boolean swapSlotIntoOffhand(int itemSlot, Item expectedItem, boolean bypassLocks) {
      if (!this.canUseSwapPackets()) {
         return false;
      } else if (itemSlot < 0 || itemSlot >= 36) {
         return false;
      } else if (!bypassLocks && this.isSlotSwapLocked(itemSlot)) {
         return false;
      } else {
         PlayerScreenHandler h = this.handler();
         if (h == null) {
            return false;
         } else {
            int handlerSlot = SlotUtils.indexToId(itemSlot);
            if (handlerSlot == -1) {
               return false;
            } else {
               this.click(handlerSlot, 40, SlotActionType.SWAP);
               if (this.mc.player.getOffHandStack().isOf(expectedItem) && h.getCursorStack().isEmpty()) {
                  return true;
               } else {
                  if (!h.getCursorStack().isEmpty()) {
                     this.cleanupCursor(itemSlot);
                     if (!h.getCursorStack().isEmpty()) {
                        int empty = this.findFirstEmptyMainInventorySlot(true);
                        if (empty == -1) {
                           return false;
                        }

                        int emptyId = SlotUtils.indexToId(empty);
                        if (emptyId == -1 || emptyId >= h.slots.size()) {
                           return false;
                        }

                        this.click(emptyId, 0, SlotActionType.PICKUP);
                        if (!h.getCursorStack().isEmpty()) {
                           return false;
                        }
                     }
                  }

                  this.click(handlerSlot, 0, SlotActionType.PICKUP);
                  this.click(45, 0, SlotActionType.PICKUP);
                  this.click(handlerSlot, 0, SlotActionType.PICKUP);
                  return this.mc.player.getOffHandStack().isOf(expectedItem) && h.getCursorStack().isEmpty();
               }
            }
         }
      }
   }

   private boolean swapOffhandIntoSlot(int targetSlot) {
      if (!this.canUseSwapPackets()) {
         return false;
      } else if (targetSlot >= 0 && targetSlot < 36) {
         int handlerSlot = SlotUtils.indexToId(targetSlot);
         if (handlerSlot == -1) {
            return false;
         } else {
            this.click(handlerSlot, 40, SlotActionType.SWAP);
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean swapSlotIntoHotbar(int sourceSlot, int hotbarSlot, Item expectedItem, boolean bypassLocks) {
      if (!this.canUseSwapPackets()) {
         return false;
      } else if (!isHotbar(hotbarSlot)) {
         return false;
      } else if (sourceSlot < 0 || sourceSlot >= 36) {
         return false;
      } else if (!bypassLocks && this.isSlotSwapLocked(hotbarSlot)) {
         return false;
      } else {
         int handlerSlot = SlotUtils.indexToId(sourceSlot);
         if (handlerSlot == -1) {
            return false;
         } else {
            this.click(handlerSlot, hotbarSlot, SlotActionType.SWAP);
            return expectedItem == null ? true : this.mc.player.getInventory().getStack(hotbarSlot).isOf(expectedItem);
         }
      }
   }

   private boolean moveTotemIntoHotbarPreservingHeldItem(int totemSlot, int hotbarSlot, boolean bypassLocks) {
      PlayerScreenHandler h = this.handler();
      if (h == null || !h.getCursorStack().isEmpty()) {
         return false;
      } else if (!isHotbar(hotbarSlot)) {
         return false;
      } else if (!bypassLocks && this.isSlotSwapLocked(hotbarSlot)) {
         return false;
      } else {
         int emptySlot = this.findFirstEmptyMainInventorySlot(bypassLocks);
         if (emptySlot == -1) {
            return false;
         } else {
            int totemId = SlotUtils.indexToId(totemSlot);
            int targetId = SlotUtils.indexToId(hotbarSlot);
            int emptyId = SlotUtils.indexToId(emptySlot);
            if (totemId != -1 && targetId != -1 && emptyId != -1) {
               this.click(totemId, 0, SlotActionType.PICKUP);
               this.click(targetId, 0, SlotActionType.PICKUP);
               if (!h.getCursorStack().isEmpty()) {
                  this.click(emptyId, 0, SlotActionType.PICKUP);
               }

               return this.mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING) && h.getCursorStack().isEmpty();
            } else {
               return false;
            }
         }
      }
   }

   private void cleanupCursor(int preferredSlot) {
      PlayerScreenHandler h = this.handler();
      if (h != null) {
         ItemStack cursor = h.getCursorStack();
         if (!cursor.isEmpty()) {
            int slotId = SlotUtils.indexToId(preferredSlot);
            if (slotId == -1) {
               slotId = SlotUtils.indexToId(this.mc.player.getInventory().selectedSlot);
            }

            if (slotId != -1 && slotId < h.slots.size()) {
               Slot target = h.getSlot(slotId);
               if (target.hasStack()) {
                  int empty = this.findFirstEmptyMainInventorySlot(true);
                  if (empty == -1) {
                     return;
                  }

                  int emptyId = SlotUtils.indexToId(empty);
                  if (emptyId == -1 || emptyId >= h.slots.size()) {
                     return;
                  }

                  if (h.getSlot(emptyId).hasStack()) {
                     return;
                  }

                  slotId = emptyId;
               }

               this.click(slotId, 0, SlotActionType.PICKUP);
            }
         }
      }
   }

   private int find(Item item) {
      return this.find(item, false);
   }

   private int find(Item item, boolean bypassLocks) {
      int selected = this.mc.player.getInventory().selectedSlot;

      for (int i = 0; i < 9; i++) {
         if (i != selected && (bypassLocks || !this.isSlotSwapLocked(i)) && this.mc.player.getInventory().getStack(i).getItem() == item) {
            return i;
         }
      }

      int mainInvSlot = this.findInMainInventory(item, bypassLocks);
      if (mainInvSlot != -1) {
         return mainInvSlot;
      } else {
         return this.mc.player.getMainHandStack().getItem() == item && !this.isSlotSwapLocked(selected) ? selected : -1;
      }
   }

   private int findInMainInventory(Item item, boolean bypassLocks) {
      for (int i = 9; i < 36; i++) {
         if ((bypassLocks || !this.isSlotSwapLocked(i)) && this.mc.player.getInventory().getStack(i).getItem() == item) {
            return i;
         }
      }

      return -1;
   }

   private int findFirstEmptyMainInventorySlot(boolean bypassLocks) {
      for (int i = 9; i < 36; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty() && (bypassLocks || !this.isSlotSwapLocked(i))) {
            return i;
         }
      }

      return -1;
   }

   private void markManagedHotbarSlot(int slot) {
      if (isHotbar(slot)) {
         this.managedHotbarTotem[slot] = true;
      }
   }

   private void detectAndBackfillLostHotbarTotems() {
      for (int i = 0; i < 9; i++) {
         if (this.managedHotbarTotem[i]) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.END_CRYSTAL)) {
               this.managedHotbarTotem[i] = false;
               this.lastHotbarTotem[i] = false;
            } else {
               boolean hasTotemNow = stack.isOf(Items.TOTEM_OF_UNDYING);
               if (this.lastHotbarTotem[i] && !hasTotemNow && !this.forceBackfillHotbarTotem(i, true)) {
                  this.pendingHotbarBackfillSlot = i;
                  this.pendingHotbarBackfillDelay = 1;
                  this.pendingHotbarBackfillAttempts = 0;
               }
            }
         }
      }
   }

   private void snapshotHotbarTotems() {
      for (int i = 0; i < 9; i++) {
         this.lastHotbarTotem[i] = this.managedHotbarTotem[i] && this.mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING);
      }
   }

   private boolean restockSelectedSlotWithTotem(boolean bypassLocks) {
      if (this.isInventoryScreenOpen()) {
         return false;
      } else {
         int targetSlot = this.mc.player.getInventory().selectedSlot;
         if (targetSlot >= 0 && targetSlot < 9) {
            if (this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
               return true;
            } else if (this.mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
               return false;
            } else if (!bypassLocks && this.isSlotSwapLocked(targetSlot)) {
               return false;
            } else {
               int totemSlot = this.find(Items.TOTEM_OF_UNDYING);
               if (totemSlot == -1) {
                  return false;
               } else if (totemSlot == targetSlot) {
                  return true;
               } else if (!isHotbar(totemSlot) || !bypassLocks && this.isSlotSwapLocked(totemSlot)) {
                  if (this.moveTotemIntoHotbarPreservingHeldItem(totemSlot, targetSlot, bypassLocks)) {
                     this.mc.player.getInventory().selectedSlot = targetSlot;
                     return true;
                  } else {
                     return false;
                  }
               } else {
                  this.mc.player.getInventory().selectedSlot = totemSlot;
                  return true;
               }
            }
         } else {
            return false;
         }
      }
   }

   private void restockHotbarSlotIfEmpty(int hotbarSlot, Item item, boolean bypassLocks) {
      if (isHotbar(hotbarSlot)) {
         if (this.mc.player.getOffHandStack().isOf(item)) {
            if (this.mc.player.getInventory().getStack(hotbarSlot).isEmpty()) {
               if (bypassLocks || !this.isSlotSwapLocked(hotbarSlot)) {
                  int refillSlot = this.findInMainInventory(item, bypassLocks);
                  if (refillSlot != -1) {
                     this.swapSlotIntoHotbar(refillSlot, hotbarSlot, item, bypassLocks);
                  }
               }
            }
         }
      }
   }

   private boolean forceBackfillHotbarTotem(int hotbarSlot, boolean bypassLocks) {
      if (!isHotbar(hotbarSlot)) {
         return false;
      } else if (!bypassLocks && this.isSlotSwapLocked(hotbarSlot)) {
         return false;
      } else {
         ItemStack current = this.mc.player.getInventory().getStack(hotbarSlot);
         if (current.isOf(Items.TOTEM_OF_UNDYING)) {
            return true;
         } else if (current.isOf(Items.END_CRYSTAL)) {
            this.managedHotbarTotem[hotbarSlot] = false;
            return false;
         } else {
            int refillSlot = this.findInMainInventory(Items.TOTEM_OF_UNDYING, bypassLocks);
            if (refillSlot == -1) {
               return false;
            } else {
               this.markManagedHotbarSlot(hotbarSlot);
               if (this.swapSlotIntoHotbar(refillSlot, hotbarSlot, Items.TOTEM_OF_UNDYING, bypassLocks)) {
                  return true;
               } else {
                  PlayerScreenHandler h = this.handler();
                  if (h == null) {
                     return false;
                  } else {
                     int hotbarId = SlotUtils.indexToId(hotbarSlot);
                     int sourceId = SlotUtils.indexToId(refillSlot);
                     if (sourceId != -1 && hotbarId != -1) {
                        ItemStack cursorStack = h.getCursorStack();
                        if (!cursorStack.isEmpty() && cursorStack.isOf(Items.TOTEM_OF_UNDYING)) {
                           this.click(hotbarId, 0, SlotActionType.PICKUP);
                           if (!h.getCursorStack().isEmpty()) {
                              int emptySlot = this.findFirstEmptyMainInventorySlot(bypassLocks);
                              int emptyId = SlotUtils.indexToId(emptySlot);
                              if (emptySlot != -1 && emptyId != -1) {
                                 this.click(emptyId, 0, SlotActionType.PICKUP);
                              }
                           }

                           return this.mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING) && h.getCursorStack().isEmpty();
                        } else if (cursorStack.isEmpty()) {
                           this.click(sourceId, 0, SlotActionType.PICKUP);
                           this.click(hotbarId, 0, SlotActionType.PICKUP);
                           if (!h.getCursorStack().isEmpty()) {
                              this.click(sourceId, 0, SlotActionType.PICKUP);
                           }

                           if (this.mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING) && h.getCursorStack().isEmpty()) {
                              return true;
                           } else if (this.placeMainInvTotemIntoHotbarAggressive(refillSlot, hotbarSlot)) {
                              return true;
                           } else {
                              this.pendingHotbarBackfillSlot = hotbarSlot;
                              this.pendingHotbarBackfillDelay = 1;
                              this.pendingHotbarBackfillAttempts = 0;
                              return false;
                           }
                        } else {
                           this.click(sourceId, 0, SlotActionType.PICKUP);
                           cursorStack = h.getCursorStack();
                           if (!cursorStack.isEmpty() && cursorStack.isOf(Items.TOTEM_OF_UNDYING)) {
                              this.click(hotbarId, 0, SlotActionType.PICKUP);
                              if (!h.getCursorStack().isEmpty()) {
                                 this.click(sourceId, 0, SlotActionType.PICKUP);
                              }

                              return this.mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING) && h.getCursorStack().isEmpty();
                           } else {
                              this.pendingHotbarBackfillSlot = hotbarSlot;
                              this.pendingHotbarBackfillDelay = 1;
                              this.pendingHotbarBackfillAttempts = 0;
                              return false;
                           }
                        }
                     } else {
                        return false;
                     }
                  }
               }
            }
         }
      }
   }

   private boolean placeMainInvTotemIntoHotbarAggressive(int refillSlot, int hotbarSlot) {
      PlayerScreenHandler h = this.handler();
      if (h == null) {
         return false;
      } else {
         int sourceId = SlotUtils.indexToId(refillSlot);
         int hotbarId = SlotUtils.indexToId(hotbarSlot);
         if (sourceId != -1 && hotbarId != -1) {
            ItemStack cursor = h.getCursorStack();
            if (!cursor.isEmpty() && !cursor.isOf(Items.TOTEM_OF_UNDYING)) {
               int empty = this.findFirstEmptyMainInventorySlot(true);
               int emptyId = SlotUtils.indexToId(empty);
               if (empty != -1 && emptyId != -1) {
                  this.click(emptyId, 0, SlotActionType.PICKUP);
               }
            }

            if (!h.getCursorStack().isEmpty() && !h.getCursorStack().isOf(Items.TOTEM_OF_UNDYING)) {
               this.click(sourceId, 0, SlotActionType.PICKUP);
            }

            if (!h.getCursorStack().isOf(Items.TOTEM_OF_UNDYING)) {
               this.click(sourceId, 0, SlotActionType.PICKUP);
            }

            this.click(hotbarId, 0, SlotActionType.PICKUP);
            if (!h.getCursorStack().isEmpty()) {
               this.click(sourceId, 0, SlotActionType.PICKUP);
               if (!h.getCursorStack().isEmpty()) {
                  int empty = this.findFirstEmptyMainInventorySlot(true);
                  int emptyId = SlotUtils.indexToId(empty);
                  if (empty != -1 && emptyId != -1) {
                     this.click(emptyId, 0, SlotActionType.PICKUP);
                  }
               }
            }

            return this.mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING) && h.getCursorStack().isEmpty();
         } else {
            return false;
         }
      }
   }

   private void ensureMainHandRechamberIfNeeded() {
      if (this.lastTickMainHandTotem) {
         if (this.lastTickSelectedSlot != -1) {
            if (this.mc.player.getInventory().selectedSlot == this.lastTickSelectedSlot) {
               if (!this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                  this.priorityMainhandTicks = Math.max(this.priorityMainhandTicks, 6);
                  this.pendingMainhandRechamberDelay = Math.max(this.pendingMainhandRechamberDelay, 1);
               }
            }
         }
      }
   }

   private void cacheHandStacks() {
      if (!ReviveClient.SWAP.isSilentSwapActive()) {
         this.lastMainHandStack = this.mc.player.getMainHandStack().copy();
         this.lastOffHandStack = this.mc.player.getOffHandStack().copy();
         this.lastTickMainHandTotem = this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
         this.lastTickSelectedSlot = this.mc.player.getInventory().selectedSlot;
         this.snapshotOffhandTotem = this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
         boolean invMenu = this.mc.player.currentScreenHandler instanceof PlayerScreenHandler;
         this.snapshotInventoryMenuOpen = invMenu;
         this.snapshotContainerId = this.mc.player.currentScreenHandler != null ? this.mc.player.currentScreenHandler.syncId : 0;
         this.latestStateId = this.mc.player.currentScreenHandler != null ? this.mc.player.currentScreenHandler.getRevision() : 0;
         int sourceInvSlot = invMenu ? this.findTotemSourceInvSlot() : -1;
         this.snapshotTotemInvSlot = sourceInvSlot;
         this.snapshotTotemContainerSlot = sourceInvSlot == -1 ? -1 : SlotUtils.indexToId(sourceInvSlot);
      }
   }

   private int findTotemSourceInvSlot() {
      if (this.mc.player == null) {
         return -1;
      } else {
         PlayerInventory inv = this.mc.player.getInventory();
         int sel = inv.selectedSlot;

         for (int i = 0; i < 9; i++) {
            if (i != sel && inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
               return i;
            }
         }

         for (int ix = 9; ix < 36; ix++) {
            if (inv.getStack(ix).isOf(Items.TOTEM_OF_UNDYING)) {
               return ix;
            }
         }

         return -1;
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null && this.mc.interactionManager != null) {
         if (this.safety.get() && this.lethalPrediction.get() && this.predictiveRender.get()) {
            if (!this.suppressOffhandTotem && !this.offhandGapProtected()) {
               if (!this.isInventoryScreenOpen()) {
                  if (this.canUseSwapPackets()) {
                     if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || this.mainHand.get() && this.lethalMainhand.get()) {
                        if (this.isLethalDamageIncoming()) {
                           if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                              this.ensureOffhandTotem(true, true);
                           }

                           if (this.mainHand.get() && this.lethalMainhand.get() && !this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                              this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
                              this.priorityMainhandTicks = Math.max(this.priorityMainhandTicks, 4);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean ensureOffhandTotem(boolean bypassLocks, boolean minimizeMoves) {
      if (this.mc.player == null) {
         return false;
      } else if (this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         return true;
      } else {
         int totemSlot = this.find(Items.TOTEM_OF_UNDYING, bypassLocks);
         if (totemSlot == -1) {
            return false;
         } else {
            int selectedSlot = this.mc.player.getInventory().selectedSlot;
            ItemStack mainhandBefore = this.mc.player.getMainHandStack().copy();
            ItemStack sourceBefore = this.mc.player.getInventory().getStack(totemSlot).copy();
            if (!this.swapSlotIntoOffhand(totemSlot, Items.TOTEM_OF_UNDYING, bypassLocks)) {
               return false;
            } else {
               if (isHotbar(totemSlot)) {
                  this.markManagedHotbarSlot(totemSlot);
               }

               this.restoreMainhandIfDisplaced(totemSlot, sourceBefore, selectedSlot, mainhandBefore);
               this.cleanupDonorIfMainhandParked(totemSlot, selectedSlot, mainhandBefore);
               if (isHotbar(totemSlot)) {
                  if (!this.forceBackfillHotbarTotem(totemSlot, true)) {
                     this.pendingHotbarBackfillSlot = totemSlot;
                     this.pendingHotbarBackfillDelay = 1;
                     this.pendingHotbarBackfillAttempts = 0;
                  }

                  if (!this.isInventoryScreenOpen()) {
                     this.pendingMainhandRechamberDelay = Math.max(this.pendingMainhandRechamberDelay, 1);
                     this.priorityMainhandTicks = Math.max(this.priorityMainhandTicks, 6);
                     if (selectedSlot == totemSlot && this.mc.player.getInventory().getStack(selectedSlot).isEmpty()) {
                        this.restockSelectedSlotWithTotem(true);
                     }
                  }
               }

               return true;
            }
         }
      }
   }

   private void restoreMainhandIfDisplaced(int sourceSlot, ItemStack sourceBefore, int mainhandSlotIdx, ItemStack mainhandBefore) {
      PlayerScreenHandler h = this.handler();
      if (h != null && !mainhandBefore.isEmpty()) {
         ItemStack sourceNow = this.mc.player.getInventory().getStack(sourceSlot);
         ItemStack mainNow = this.mc.player.getInventory().getStack(mainhandSlotIdx);
         boolean mainhandLost = !ItemStack.areItemsEqual(mainNow, mainhandBefore);
         boolean mainhandParkedInSource = ItemStack.areItemsEqual(sourceNow, mainhandBefore) && !ItemStack.areItemsEqual(sourceBefore, mainhandBefore);
         if (mainhandParkedInSource || mainhandLost) {
            int srcId = SlotUtils.indexToId(sourceSlot);
            int mainId = SlotUtils.indexToId(mainhandSlotIdx);
            if (srcId != -1 && mainId != -1) {
               this.click(srcId, 0, SlotActionType.PICKUP);
               this.click(mainId, 0, SlotActionType.PICKUP);
               if (!h.getCursorStack().isEmpty()) {
                  this.click(srcId, 0, SlotActionType.PICKUP);
               }
            }
         }
      }
   }

   private void cleanupDonorIfMainhandParked(int donorSlot, int mainhandSlotIdx, ItemStack mainhandBefore) {
      PlayerScreenHandler h = this.handler();
      if (h != null && !mainhandBefore.isEmpty()) {
         ItemStack donor = this.mc.player.getInventory().getStack(donorSlot);
         ItemStack mainNow = this.mc.player.getInventory().getStack(mainhandSlotIdx);
         if (!donor.isEmpty() && ItemStack.areItemsEqual(donor, mainhandBefore)) {
            if (ItemStack.areItemsEqual(mainNow, mainhandBefore)) {
               int emptySlot = this.findFirstEmptyMainInventorySlot(false);
               if (emptySlot == -1) {
                  return;
               }

               int donorId = SlotUtils.indexToId(donorSlot);
               int emptyId = SlotUtils.indexToId(emptySlot);
               if (donorId == -1 || emptyId == -1) {
                  return;
               }

               this.click(donorId, 0, SlotActionType.PICKUP);
               this.click(emptyId, 0, SlotActionType.PICKUP);
               if (!h.getCursorStack().isEmpty()) {
                  this.click(donorId, 0, SlotActionType.PICKUP);
               }
            }
         }
      }
   }

   private boolean isLethalDamageIncoming() {
      if (this.mc.player == null) {
         return false;
      } else {
         double predicted = this.estimatePossibleDamage();
         if (predicted <= 0.0) {
            return false;
         } else {
            double currentHp = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
            return predicted + this.lethalBuffer.get() >= currentHp;
         }
      }
   }

   private double estimatePossibleDamage() {
      if (this.mc.player != null && this.mc.world != null) {
         float maxDamage = 0.0F;
         Vec3d playerPos = this.mc.player.getEntityPos();
         Box playerBox = this.mc.player.getBoundingBox();
         boolean sum = this.lethalSum.get();
         float crystalAccum = 0.0F;
         int crystals = 0;

         for (EndCrystalEntity crystal : this.mc.world.getEntitiesByClass(EndCrystalEntity.class, this.mc.player.getBoundingBox().expand(12.0), e -> true)) {
            float crystalDmg = DamageUtil.crystalDamage(this.mc.player, playerPos, playerBox, crystal.getEntityPos(), DamageUtil.BLOCK_CHECK, false);
            crystals++;
            if (sum) {
               crystalAccum += crystalDmg;
            } else if (crystalDmg > maxDamage) {
               maxDamage = crystalDmg;
            }
         }

         if (sum && crystalAccum > maxDamage) {
            maxDamage = crystalAccum;
         }

         this.lastCrystalDamage = maxDamage;
         this.lastCrystalCount = crystals;
         this.lastMeleeDamage = 0.0F;
         this.lastFallDamage = 0.0F;
         if (this.bedCacheTick != this.tickCounter) {
            this.bedCacheTick = this.tickCounter;
            this.cachedBedDamage = this.estimateBedDamage(playerPos, playerBox);
         }

         if (this.cachedBedDamage > maxDamage) {
            maxDamage = this.cachedBedDamage;
         }

         for (PlayerEntity player : this.mc.world.getEntitiesByClass(PlayerEntity.class, this.mc.player.getBoundingBox().expand(5.0), e -> true)) {
            if (player != this.mc.player && !Friends.get().isFriend(player)) {
               float meleeDmg = this.estimateAttackDamage(player, this.mc.player);
               this.lastMeleeDamage = Math.max(this.lastMeleeDamage, meleeDmg);
               if (meleeDmg > maxDamage) {
                  maxDamage = meleeDmg;
               }
            }
         }

         if (this.lethalIncludeFall.get()) {
            float fallDmg = this.estimateFallDamage();
            this.lastFallDamage = fallDmg;
            if (fallDmg > maxDamage) {
               maxDamage = fallDmg;
            }
         }

         return maxDamage;
      } else {
         return 0.0;
      }
   }

   private float estimateBedDamage(Vec3d playerPos, Box playerBox) {
      if (this.mc.world.getRegistryKey() != World.NETHER && this.mc.world.getRegistryKey() != World.END) {
         return 0.0F;
      } else {
         float maxDamage = 0.0F;
         BlockPos playerBlockPos = this.mc.player.getBlockPos();
         int scanRange = 10;
         Mutable bp = new Mutable();

         for (int x = -scanRange; x <= scanRange; x++) {
            for (int y = -scanRange; y <= scanRange; y++) {
               for (int z = -scanRange; z <= scanRange; z++) {
                  bp.set(playerBlockPos.getX() + x, playerBlockPos.getY() + y, playerBlockPos.getZ() + z);
                  if (this.mc.world.getBlockState(bp).getBlock() instanceof BedBlock) {
                     float bedDmg = DamageUtil.bedDamage(this.mc.player, playerPos, playerBox, Vec3d.ofCenter(bp), DamageUtil.BLOCK_CHECK, false);
                     if (bedDmg > maxDamage) {
                        maxDamage = bedDmg;
                     }
                  }
               }
            }
         }

         return maxDamage;
      }
   }

   private float estimateAttackDamage(PlayerEntity attacker, Entity target) {
      if (this.mc.world == null) {
         return 0.0F;
      } else {
         float baseDamage = (float)attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
         ItemStack weapon = attacker.getMainHandStack();
         int sharpness = EnchantUtil.getLevel(weapon, Enchantments.SHARPNESS);
         float enchantBonus = sharpness > 0 ? 1.0F + 0.5F * (sharpness - 1) : 0.0F;
         float charge = attacker.getAttackCooldownProgress(0.5F);
         baseDamage *= 0.2F + charge * charge * 0.8F;
         enchantBonus *= charge;
         if (charge > 0.9F
            && attacker.fallDistance > 0.0
            && !attacker.isOnGround()
            && !attacker.isClimbing()
            && !attacker.isTouchingWater()
            && !attacker.hasStatusEffect(StatusEffects.BLINDNESS)
            && !attacker.hasVehicle()) {
            baseDamage *= 1.5F;
         }

         float totalDamage = baseDamage + enchantBonus;
         return DamageUtil.applyReductions(totalDamage, target, this.mc.world.getDamageSources().playerAttack(attacker), false);
      }
   }

   private float estimateFallDamage() {
      if (this.mc.player == null || this.mc.world == null) {
         return 0.0F;
      } else if (this.mc.player.getAbilities().flying) {
         return 0.0F;
      } else if (this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING) || this.mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
         return 0.0F;
      } else if (this.mc.player.fallDistance <= 3.0) {
         return 0.0F;
      } else {
         Vec3d start = this.mc.player.getEntityPos();
         Vec3d end = new Vec3d(this.mc.player.getX(), this.mc.world.getBottomY(), this.mc.player.getZ());
         BlockHitResult hit = this.mc.world.raycast(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, this.mc.player));
         if (hit.getType() == Type.MISS) {
            return 0.0F;
         } else {
            int surfaceY = hit.getBlockPos().getY();
            if (this.isAboveWater(this.mc.player)) {
               return 0.0F;
            } else {
               int fallHeight = (int)(this.mc.player.getY() - surfaceY + this.mc.player.fallDistance - 3.0);
               StatusEffectInstance jumpBoost = this.mc.player.getStatusEffect(StatusEffects.JUMP_BOOST);
               if (jumpBoost != null) {
                  fallHeight -= jumpBoost.getAmplifier() + 1;
               }

               return fallHeight <= 0 ? 0.0F : DamageUtil.applyReductions(fallHeight, this.mc.player, this.mc.world.getDamageSources().fall(), false);
            }
         }
      }
   }

   private boolean isAboveWater(Entity entity) {
      if (this.mc.world == null) {
         return false;
      } else {
         Mutable pos = new Mutable(MathHelper.floor(entity.getX()), MathHelper.floor(entity.getY()), MathHelper.floor(entity.getZ()));

         for (int i = 0; i < 8; i++) {
            BlockState state = this.mc.world.getBlockState(pos);
            boolean liquid = !state.getFluidState().isEmpty();
            if (!state.isAir() && !liquid) {
               return false;
            }

            if (liquid) {
               return true;
            }

            pos.move(Direction.DOWN);
         }

         return false;
      }
   }

   private boolean tryHandleFoodSwap(boolean skipFoodSwap, boolean useKeyPressed, boolean lethalRisk) {
      if (!skipFoodSwap && this.eatingGroup.get() && useKeyPressed && !this.willInteract()) {
         this.overrideSlotLockForFood = true;
         Offhand.HandSide eatHand = this.foodHand.get();
         if (!this.foodMainHandReady(eatHand)) {
            return false;
         } else {
            boolean usingGapple = this.foodWantsGapple(lethalRisk);
            boolean satisfiedByOffhand = false;
            if (eatHand == Offhand.HandSide.LEFT) {
               satisfiedByOffhand = usingGapple ? this.hasGappleInOffhand() : this.hasWhitelistedFoodInOffhand();
            }

            boolean lockingOffhand = eatHand == Offhand.HandSide.LEFT
               && this.mc.player.isUsingItem()
               && this.mc.player.getActiveHand() == Hand.OFF_HAND
               && satisfiedByOffhand;
            if (lockingOffhand) {
               if (!this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                  this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
                  if (!this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                     return false;
                  }
               }

               this.suppressOffhandTotem = true;
               return true;
            } else if (this.offhandGapProtected()) {
               return false;
            } else {
               int targetSlot = -1;
               if (!satisfiedByOffhand) {
                  targetSlot = usingGapple ? this.findGappleSlot() : this.findBestFoodSlot();
                  if (targetSlot == -1) {
                     return false;
                  }

                  if (this.isSlotSwapLocked(targetSlot)) {
                     return true;
                  }
               }

               if (eatHand == Offhand.HandSide.RIGHT) {
                  if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                     this.applyOffHand(Items.TOTEM_OF_UNDYING);
                     if (!this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                        return false;
                     }
                  }

                  if (this.previousRightHandSlot == -1) {
                     this.previousRightHandSlot = this.mc.player.getInventory().selectedSlot;
                  }

                  this.applyMainHand(targetSlot, false);
               } else {
                  if (!this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                     this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
                     if (!this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                        return false;
                     }
                  }

                  if (!satisfiedByOffhand) {
                     Item food = this.mc.player.getInventory().getStack(targetSlot).getItem();
                     this.applyOffHand(targetSlot);
                     if (this.mc.player.getOffHandStack().isOf(food)) {
                        this.rememberOffhandSwap(targetSlot, food);
                     }
                  }

                  if (this.countTotems() == 1) {
                     this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
                  }

                  this.suppressOffhandTotem = !this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   private boolean foodMainHandReady(Offhand.HandSide eatHand) {
      ItemStack mainHandStack = this.mc.player.getMainHandStack();
      return mainHandStack.isEmpty()
         || mainHandStack.isOf(Items.TOTEM_OF_UNDYING)
         || this.isToolItem(mainHandStack)
         || eatHand == Offhand.HandSide.RIGHT && this.previousRightHandSlot != -1;
   }

   private boolean foodWantsGapple(boolean lethalRisk) {
      boolean hungerFull = this.mc.player.getHungerManager().getFoodLevel() >= 20;
      double currentHp = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
      return lethalRisk || currentHp <= this.gappleHealthThreshold.get().intValue() || hungerFull;
   }

   private boolean foodSwapTakesUse(boolean lethalRisk) {
      if (this.skipFoodSwapTicks <= 0 && !this.suppressFoodSwapUntilRelease && this.eatingGroup.get() && !this.willInteract()) {
         Offhand.HandSide eatHand = this.foodHand.get();
         if (this.foodMainHandReady(eatHand) && !this.offhandGapProtected()) {
            this.overrideSlotLockForFood = true;
            ItemStack totemHand = eatHand == Offhand.HandSide.LEFT ? this.mc.player.getMainHandStack() : this.mc.player.getOffHandStack();
            if (!totemHand.isOf(Items.TOTEM_OF_UNDYING) && this.find(Items.TOTEM_OF_UNDYING) == -1) {
               return false;
            } else {
               boolean usingGapple = this.foodWantsGapple(lethalRisk);
               return eatHand != Offhand.HandSide.LEFT || (usingGapple ? !this.hasGappleInOffhand() : !this.hasWhitelistedFoodInOffhand())
                  ? (usingGapple ? this.findGappleSlot() : this.findBestFoodSlot()) != -1
                  : true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int resolvePostEatRestoreSlot(int previousSlot) {
      if (this.mc.player != null && this.mainHand.get()) {
         return !this.mc.player.getInventory().getStack(previousSlot).isEmpty() ? previousSlot : MathHelper.clamp(this.mainhandSlot.get() - 1, 0, 8);
      } else {
         return previousSlot;
      }
   }

   private void releaseFoodSwapSuppression() {
      this.suppressFoodSwapUntilRelease = false;
      this.waitingForUseKeyRelease = false;
      this.skipFoodSwapTicks = 0;
      this.emptyHandSuppressionTicks = 0;
   }

   public boolean isGapping() {
      return this.gapSession || this.isEatingGap();
   }

   private boolean isEatingGap() {
      if (this.mc.player == null || this.mc.options == null) {
         return false;
      } else if (this.mc.options.useKey.isPressed() && this.mc.player.isUsingItem()) {
         ItemStack main = this.mc.player.getMainHandStack();
         if (this.mc.player.getActiveHand() != Hand.OFF_HAND) {
            return this.gapHand.get() == Offhand.HandSide.LEFT ? false : this.isGapple(main);
         } else if (!this.isGapple(this.mc.player.getOffHandStack())) {
            return false;
         } else if (this.gapHand.get() == Offhand.HandSide.LEFT && this.isToolItem(main)) {
            return true;
         } else if (this.swordGapple.get() && this.isSword(main)) {
            return true;
         } else {
            boolean offhandGapFeature = this.totemGapple.get()
               || this.mainHand.get()
               || this.swordGapple.get()
               || this.gapGroup.get() && this.gapHand.get() == Offhand.HandSide.LEFT;
            return offhandGapFeature && main.isOf(Items.TOTEM_OF_UNDYING);
         }
      } else {
         return false;
      }
   }

   private boolean handleGap(boolean useKeyPressed, boolean lethalRisk, boolean leftOnly, String trigger) {
      if (!useKeyPressed) {
         return false;
      } else {
         boolean behindCover = this.coverHeld();
         ItemStack gapMainStack = behindCover ? this.mc.player.getInventory().getStack(this.gapCoverFromSlot) : this.mc.player.getMainHandStack();
         boolean swordGap = this.swordGapple.get() && this.isSword(gapMainStack);
         boolean toolGap = this.gapGroup.get() && this.isToolItem(gapMainStack);
         if (!swordGap && !toolGap) {
            return false;
         } else {
            Offhand.HandSide activeGapHand = swordGap ? Offhand.HandSide.LEFT : this.gapHand.get();
            boolean gapLeftHand = activeGapHand == Offhand.HandSide.LEFT;
            if ((leftOnly || behindCover) && !gapLeftHand) {
               return false;
            } else {
               boolean applyGapFilters = toolGap && this.gapHand.get() == Offhand.HandSide.LEFT;
               double gapHealth = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
               if (gapLeftHand
                  ? !this.gapRipped && (!lethalRisk || !this.strictLethalGap.get())
                  : !lethalRisk || !this.mainHand.get() || !this.lethalMainhand.get()) {
                  if (this.willInteract()) {
                     this.dbgBlocked("the block under the crosshair takes the use key");
                     return false;
                  } else if (applyGapFilters && gapHealth < this.gapMinHealth.get()) {
                     this.dbgBlocked("below min-health");
                     return false;
                  } else {
                     if (applyGapFilters && this.gapOnlyPhased.get()) {
                        BlockPos phasedBlock = this.getPhasedSolidBlock();
                        boolean phased = phasedBlock != null || this.isPlayerPhased();
                        if (!phased) {
                           this.handleGapFailure();
                           return true;
                        }

                        if (phasedBlock != null) {
                           if (this.gapBedrockOnly.get() && !this.mc.world.getBlockState(phasedBlock).isOf(Blocks.BEDROCK)) {
                              this.handleGapFailure();
                              return true;
                           }
                        } else if (this.gapBedrockOnly.get()) {
                           this.handleGapFailure();
                           return true;
                        }
                     }

                     this.overrideSlotLockForFood = true;
                     int gapSlot = this.findGappleSlot();
                     if (activeGapHand == Offhand.HandSide.RIGHT) {
                        if (gapSlot != -1 && !this.offhandGapProtected()) {
                           if (this.previousRightHandSlot == -1) {
                              this.previousRightHandSlot = this.mc.player.getInventory().selectedSlot;
                           }

                           this.applyOffHand(Items.TOTEM_OF_UNDYING);
                           this.applyMainHand(gapSlot, false);
                           return true;
                        } else {
                           return false;
                        }
                     } else {
                        return gapSlot == -1 && !this.hasGappleInOffhand() ? false : this.startLeftGap(gapSlot, lethalRisk, trigger);
                     }
                  }
               } else {
                  if (gapLeftHand) {
                     this.dbgBlocked(this.gapRipped ? "a fallback pulled it, release the use key to retry" : "lethal predicted (strict-lethal-gap)");
                  }

                  return false;
               }
            }
         }
      }
   }

   private boolean startLeftGap(int gapSlot, boolean lethalRisk, String trigger) {
      if (!this.hasGappleInOffhand()) {
         boolean offhandTotemCovers = isHotbar(gapSlot) && !this.isInventoryScreenOpen() && this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
         if (lethalRisk && !this.coverGapWithMainTotem() && !offhandTotemCovers && this.countTotems() > 0) {
            this.dbgBlocked("lethal predicted and no main-hand totem could be selected");
            return false;
         }

         if (!this.isGapple(this.mc.player.getInventory().getStack(gapSlot))) {
            gapSlot = this.findGappleSlot();
            if (gapSlot == -1) {
               return false;
            }
         }

         if (this.isSlotSwapLocked(gapSlot)) {
            this.dbgBlocked("slot " + gapSlot + " is locked by a swap");
            return true;
         }

         Item gapItem = this.mc.player.getInventory().getStack(gapSlot).getItem();
         if (!this.swapSlotIntoOffhand(gapSlot, gapItem, false)) {
            this.dbgBlocked("the click was refused (click budget or cursor)");
            return true;
         }

         this.rememberOffhandSwap(gapSlot, gapItem);
         this.dbg("gapple in from slot " + gapSlot + " (" + trigger + (lethalRisk ? ", lethal predicted" : "") + ")");
         if (lethalRisk || this.countTotems() == 1) {
            this.coverGapWithMainTotem();
         }
      }

      this.startGapSession(trigger);
      return true;
   }

   public void beforeReplayedUse() {
      if (this.isActive() && this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         if (this.isUseKeyPressed()) {
            if (!ReviveClient.SWAP.isSilentSwapActive() && !this.isInventoryScreenOpen()) {
               boolean lethalRisk = this.safety.get() && this.lethalPrediction.get() && this.isLethalDamageIncoming();

               try {
                  if ((!this.gapSession || !this.hasGappleInOffhand()) && !this.foodSwapTakesUse(lethalRisk)) {
                     this.handleGap(true, lethalRisk, true, "replayed use");
                  }

                  if (lethalRisk && this.offhandGapProtected() && !this.strictLethalGap.get() && !this.coveredBeforeReplay) {
                     this.coverGapWithMainTotem();
                  }
               } finally {
                  this.overrideSlotLockForFood = false;
                  this.snapshotOffhandTotem = this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
               }
            }
         }
      }
   }

   public void beforeReplayedTick() {
      if (this.isActive() && this.mc.player != null && this.mc.world != null) {
         this.coveredBeforeReplay = false;
         this.actedThisTick = false;
         if (!ReviveClient.SWAP.isSilentSwapActive()) {
            if (this.pendingRestoreSlot != -1) {
               this.mc.player.getInventory().selectedSlot = this.pendingRestoreSlot;
               this.pendingRestoreSlot = -1;
            }

            if (this.gapCoverFromSlot != -1 && !this.gapSession) {
               this.restoreGapCoverSlot(this.safety.get() && this.lethalPrediction.get() && this.isLethalDamageIncoming());
            }

            if (this.offhandGapProtected() && !this.strictLethalGap.get() && this.safety.get() && this.lethalPrediction.get() && this.isLethalDamageIncoming()) {
               this.coveredBeforeReplay = this.coverGapWithMainTotem();
            }
         }

         this.replayedThisTick = true;
      }
   }

   private boolean coverGapWithMainTotem() {
      if (this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         return true;
      } else if (!this.mainHand.get() && !this.stockSlot.get() && !this.totemGapple.get() && this.countTotems() > 1 && !this.hotbarTakesTotem()) {
         return false;
      } else {
         int before = this.mc.player.getInventory().selectedSlot;
         this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
         if (!this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return false;
         } else {
            int now = this.mc.player.getInventory().selectedSlot;
            if (this.gapCoverFromSlot == -1 && now != before) {
               this.gapCoverFromSlot = before;
            }

            this.gapCoverSlot = now;
            this.dbg("main-hand totem (slot " + now + ") covers the offhand gapple");
            return true;
         }
      }
   }

   private boolean hotbarTakesTotem() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.isOf(Items.TOTEM_OF_UNDYING) || stack.isEmpty() && !this.isSlotSwapLocked(i)) {
            return true;
         }
      }

      return false;
   }

   private boolean coverHeld() {
      if (this.gapCoverFromSlot == -1) {
         return false;
      } else {
         int selected = this.mc.player.getInventory().selectedSlot;
         return selected == this.gapCoverSlot || selected != this.gapCoverFromSlot && this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
      }
   }

   private void restoreGapCoverSlot(boolean lethalRisk) {
      if (this.gapCoverFromSlot != -1 && !this.gapSession) {
         if (this.coverHeld() && !this.mc.player.getInventory().getStack(this.gapCoverFromSlot).isEmpty()) {
            double hp = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
            if (!lethalRisk
               && this.priorityMainhandTicks <= 0
               && !this.totemGapple.get()
               && (!this.mainHand.get() || !(hp <= this.mainhandHealth.get().intValue()))) {
               if (!this.hasGappleInOffhand()
                  && (!this.mc.player.getOffHandStack().isEmpty() || this.countTotems() <= 0)
                  && !this.releasedThisTick
                  && !this.replayedThisTick) {
                  this.dbg("gap over: main hand back to slot " + this.gapCoverFromSlot);
                  this.mc.player.getInventory().selectedSlot = this.gapCoverFromSlot;
                  this.gapCoverFromSlot = -1;
               }
            }
         } else {
            this.gapCoverFromSlot = -1;
         }
      }
   }

   private boolean offhandGapProtected() {
      return this.hasGappleInOffhand() && (this.gapSession || this.mc.player.isUsingItem() && this.mc.player.getActiveHand() == Hand.OFF_HAND);
   }

   private void startGapSession(String trigger) {
      this.suppressOffhandTotem = true;
      if (!this.gapSession) {
         this.gapSession = true;
         this.gapSessionWorld = this.mc.world;
         this.gapOffMainTicks = 0;
         this.lastGapDbg = null;
         this.dbg("gap session start (" + trigger + ")");
      }
   }

   private void endGapSession(String reason) {
      if (this.gapSession) {
         this.gapSession = false;
         this.gapSessionWorld = null;
         this.suppressOffhandTotem = false;
         this.dbg("gap session end: " + reason);
      }
   }

   private void updateGapSession(boolean useKeyPressed) {
      if (!useKeyPressed) {
         this.gapRipped = false;
      }

      if (this.gapSession) {
         boolean usingOffhand = this.mc.player.isUsingItem() && this.mc.player.getActiveHand() == Hand.OFF_HAND;
         this.gapOffMainTicks = this.gapMainHandHeld() ? 0 : this.gapOffMainTicks + 1;
         if (this.mc.player.isDead() || this.mc.world != this.gapSessionWorld) {
            this.endGapSession(this.mc.player.isDead() ? "died" : "world changed");
            this.gapCoverFromSlot = -1;
         } else if (!usingOffhand && !this.hasGappleInOffhand()) {
            this.endGapSession("no gapple in the offhand");
         } else if (!usingOffhand && !useKeyPressed) {
            this.endGapSession("use key released");
         } else if (this.gapOffMainTicks > 2) {
            this.endGapSession("main hand left the sword or tool");
         } else {
            this.suppressOffhandTotem = true;
         }
      }

      if (!useKeyPressed && !this.gapSession && !this.hasGappleInOffhand()) {
         this.lastGapDbg = null;
      }
   }

   private boolean gapMainHandHeld() {
      ItemStack main = this.mc.player.getMainHandStack();
      if (main.isOf(Items.TOTEM_OF_UNDYING) || this.coverHeld()) {
         return true;
      } else {
         return this.swordGapple.get() && this.isSword(main)
            ? true
            : this.gapGroup.get() && this.gapHand.get() == Offhand.HandSide.LEFT && this.isToolItem(main);
      }
   }

   private boolean tryHandleTotemGapple(boolean useKeyPressed, boolean lethalRisk, boolean interactionLocked) {
      boolean viaMainHand = !this.totemGapple.get() && this.mainHand.get();
      if (!this.totemGapple.get() && !viaMainHand) {
         return false;
      } else {
         ItemStack main = this.mc.player.getMainHandStack();
         boolean mainTotem = main.isOf(Items.TOTEM_OF_UNDYING);
         boolean eatingOffhandGapple = this.mc.player.isUsingItem() && this.mc.player.getActiveHand() == Hand.OFF_HAND && this.hasGappleInOffhand();
         Offhand.TotemGappleMode mode = viaMainHand ? Offhand.TotemGappleMode.Hold : this.totemGappleMode.get();

         boolean triggered = switch (mode) {
            case Hold -> mainTotem && useKeyPressed && !this.willInteract();
            case Holding -> mainTotem;
            case Always -> true;
         };
         if (triggered || eatingOffhandGapple && mainTotem && useKeyPressed) {
            if (!lethalRisk || mainTotem && !this.strictLethalGap.get()) {
               double hp = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
               if (!viaMainHand && this.totemGappleHealth.get() > 0.0 && hp <= this.totemGappleHealth.get()) {
                  if (!this.hasGappleInOffhand() && this.offhandSwapHistory.isEmpty()) {
                     return false;
                  } else {
                     if (this.hasGappleInOffhand()) {
                        this.dbgGap("gapple out: at or below totem-gapple-fallback-health");
                        this.gapRipped = true;
                     }

                     this.endGapSession("totem-gapple fallback health");
                     this.suppressOffhandTotem = false;
                     this.ensureTotemInOffhand();
                     return false;
                  }
               } else if (!interactionLocked) {
                  if (!mainTotem) {
                     if (mode != Offhand.TotemGappleMode.Always) {
                        return false;
                     }

                     this.applyMainHand(Items.TOTEM_OF_UNDYING, false);
                     if (!this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                        return false;
                     }

                     this.markManagedHotbarSlot(this.mc.player.getInventory().selectedSlot);
                  }

                  if (this.hasGappleInOffhand()) {
                     this.suppressOffhandTotem = true;
                     return true;
                  } else if (this.mc.player.isUsingItem() && this.mc.player.getActiveHand() == Hand.OFF_HAND) {
                     return true;
                  } else {
                     int gapSlot = this.findGappleSlot();
                     if (gapSlot == -1 || gapSlot == this.mc.player.getInventory().selectedSlot) {
                        return false;
                     } else if (this.isSlotSwapLocked(gapSlot)) {
                        return false;
                     } else {
                        Item gapItem = this.mc.player.getInventory().getStack(gapSlot).getItem();
                        if (!this.swapSlotIntoOffhand(gapSlot, gapItem, false)) {
                           return this.hasGappleInOffhand();
                        } else {
                           this.rememberOffhandSwap(gapSlot, gapItem);
                           this.suppressOffhandTotem = true;
                           return true;
                        }
                     }
                  }
               } else {
                  return this.hasGappleInOffhand() && mainTotem;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private void stockMainHandSlot() {
      int slot = MathHelper.clamp(this.mainhandSlot.get() - 1, 0, 8);
      ItemStack current = this.mc.player.getInventory().getStack(slot);
      if (current.isOf(Items.TOTEM_OF_UNDYING)) {
         this.markManagedHotbarSlot(slot);
      } else if (!current.isOf(Items.END_CRYSTAL)) {
         if (this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            if (!this.isSlotSwapLocked(slot)) {
               int spare = this.findInMainInventory(Items.TOTEM_OF_UNDYING, false);
               if (spare != -1) {
                  if (this.swapSlotIntoHotbar(spare, slot, Items.TOTEM_OF_UNDYING, false)) {
                     this.markManagedHotbarSlot(slot);
                  }
               }
            }
         }
      }
   }

   private void handleGapFailure() {
      if (this.mc.player != null && this.isEatingGap()) {
         if (this.mc.player.isUsingItem()) {
            this.mc.player.stopUsingItem();
         }

         if (this.gapHand.get() == Offhand.HandSide.RIGHT) {
            if (this.previousRightHandSlot != -1) {
               this.mc.player.getInventory().selectedSlot = this.previousRightHandSlot;
               this.pendingRestoreSlot = this.previousRightHandSlot;
               this.previousRightHandSlot = -1;
            }
         } else {
            this.dbg("gapple out: gap filter failed mid-eat");
            this.endGapSession("gap filter failed");
            this.applyOffHand(Items.TOTEM_OF_UNDYING);
         }

         this.suppressOffhandTotem = false;
      }
   }

   private boolean hasGappleInOffhand() {
      return this.mc.player != null && this.isGapple(this.mc.player.getOffHandStack());
   }

   private boolean isGapple(ItemStack stack) {
      return stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.GOLDEN_APPLE);
   }

   private boolean hasWhitelistedFoodInOffhand() {
      if (this.mc.player == null) {
         return false;
      } else {
         ItemStack offhand = this.mc.player.getOffHandStack();
         return !offhand.isEmpty() && this.foodWhitelist.get().contains(offhand.getItem());
      }
   }

   private int findGappleSlot() {
      if (this.mc.player == null) {
         return -1;
      } else {
         int selected = this.mc.player.getInventory().selectedSlot;
         if (selected >= 0 && selected < 9) {
            ItemStack sel = this.mc.player.getInventory().getStack(selected);
            if (this.isGapple(sel) && !this.isSlotSwapLocked(selected)) {
               return selected;
            }
         }

         int slot = this.find(Items.ENCHANTED_GOLDEN_APPLE);
         if (slot == -1) {
            slot = this.find(Items.GOLDEN_APPLE);
         }

         return slot;
      }
   }

   private int findBestFoodSlot() {
      if (this.mc.player == null) {
         return -1;
      } else {
         PlayerInventory inventory = this.mc.player.getInventory();
         int bestSlot = -1;
         double bestSaturation = -1.0;
         int bestNutrition = -1;

         for (int i = 0; i < 36; i++) {
            if (!this.isSlotSwapLocked(i)) {
               ItemStack stack = inventory.getStack(i);
               if (!stack.isEmpty() && this.foodWhitelist.get().contains(stack.getItem())) {
                  FoodComponent food = (FoodComponent)stack.get(DataComponentTypes.FOOD);
                  if (food != null) {
                     double saturation = food.saturation();
                     int nutrition = food.nutrition();
                     if (saturation > bestSaturation || saturation == bestSaturation && nutrition > bestNutrition) {
                        bestSaturation = saturation;
                        bestNutrition = nutrition;
                        bestSlot = i;
                     }
                  }
               }
            }
         }

         return bestSlot;
      }
   }

   private boolean isSword(ItemStack stack) {
      return !stack.isEmpty() && stack.isIn(ItemTags.SWORDS);
   }

   private boolean isToolItem(ItemStack stack) {
      return !stack.isEmpty() && stack.contains(DataComponentTypes.TOOL);
   }

   private void rememberOffhandSwap(int slot, Item item) {
      if (this.mc.player != null) {
         if (slot >= 0 && slot < 36) {
            if (item != Items.TOTEM_OF_UNDYING) {
               if (!this.isSlotSwapLocked(slot)) {
                  this.offhandSwapHistory.push(new Offhand.OffhandSwapRecord(slot));
               }
            }
         }
      }
   }

   private int countTotems() {
      int count = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
            count += stack.getCount();
         }
      }

      ItemStack offhand = this.mc.player.getOffHandStack();
      if (offhand.isOf(Items.TOTEM_OF_UNDYING)) {
         count += offhand.getCount();
      }

      return count;
   }

   private boolean willInteract() {
      if (this.mc.player == null || this.mc.world == null) {
         return false;
      } else if (this.mc.player.isSneaking()) {
         return false;
      } else {
         HitResult hit = this.mc.crosshairTarget;
         if (hit == null || hit.getType() == Type.MISS) {
            hit = this.mc.player.raycast(5.0, 0.0F, false);
         }

         if (hit != null && hit.getType() == Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult)hit;
            BlockState blockState = this.mc.world.getBlockState(blockHit.getBlockPos());
            Block block = blockState.getBlock();
            if (!(block instanceof AbstractChestBlock)
               && !(block instanceof AbstractFurnaceBlock)
               && !(block instanceof AnvilBlock)
               && !(block instanceof BarrelBlock)
               && !(block instanceof DispenserBlock)
               && !(block instanceof DoorBlock)
               && !(block instanceof DropperBlock)
               && !(block instanceof EnderChestBlock)
               && !(block instanceof HopperBlock)
               && !(block instanceof ShulkerBoxBlock)
               && !(block instanceof SmithingTableBlock)
               && !(block instanceof TrapdoorBlock)
               && !(block instanceof TrappedChestBlock)) {
               ItemStack mainHandStack = this.mc.player.getMainHandStack();
               if (mainHandStack.isEmpty()) {
                  return false;
               } else {
                  Item heldItem = mainHandStack.getItem();
                  return heldItem instanceof AxeItem && AXE_STRIPPABLE_BLOCKS.contains(block)
                     ? true
                     : heldItem instanceof ShovelItem
                        && blockHit.getSide() != Direction.DOWN
                        && SHOVEL_PATH_BLOCKS.contains(block)
                        && this.mc.world.getBlockState(blockHit.getBlockPos().up()).isAir();
               }
            } else {
               return true;
            }
         } else {
            return false;
         }
      }
   }

   private boolean isPlayerPhased() {
      return this.getPhasedSolidBlock() != null;
   }

   private BlockPos getPhasedSolidBlock() {
      if (this.mc.player != null && this.mc.world != null) {
         Box bb = this.mc.player.getBoundingBox();

         for (BlockPos pos : BlockPos.iterate(
            MathHelper.floor(bb.minX),
            MathHelper.floor(bb.minY),
            MathHelper.floor(bb.minZ),
            MathHelper.floor(bb.maxX),
            MathHelper.floor(bb.maxY),
            MathHelper.floor(bb.maxZ)
         )) {
            BlockState state = this.mc.world.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.getCollisionShape(this.mc.world, pos).isEmpty()) {
               return pos.toImmutable();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void dbg(String msg) {
      if (this.debug.get()) {
         ChatUtils.infoPrefix("Offhand", "%s", msg);
      }
   }

   private void dbgGap(String line) {
      if (this.debug.get() && !line.equals(this.lastGapDbg)) {
         this.lastGapDbg = line;
         ChatUtils.infoPrefix("Offhand", "%s", line);
      }
   }

   private void dbgBlocked(String reason) {
      if (!this.hasGappleInOffhand()) {
         this.dbgGap("gapple blocked: " + reason);
      }
   }

   private void dbgLethal(boolean lethal) {
      if (lethal != this.lastLethal) {
         this.lastLethal = lethal;
         if (this.debug.get()) {
            this.mutedLethalFlips++;
         }
      }

      if (this.debug.get() && lethal != this.printedLethal) {
         long now = System.currentTimeMillis();
         if (now - this.lastLethalDbgMs >= 500L) {
            this.lastLethalDbgMs = now;
            this.printedLethal = lethal;
            int muted = this.mutedLethalFlips - 1;
            double hp = this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount();
            this.dbg(
               String.format(
                  Locale.US,
                  "lethal %s: crystals %.1f (%d in 12 blocks), bed %.1f, melee %.1f, fall %.1f, buffer %.1f vs hp %.1f%s",
                  lethal ? "ON" : "OFF",
                  this.lastCrystalDamage,
                  this.lastCrystalCount,
                  this.cachedBedDamage,
                  this.lastMeleeDamage,
                  this.lastFallDamage,
                  this.lethalBuffer.get(),
                  hp,
                  muted > 0 ? " (" + muted + " flips muted)" : ""
               )
            );
            this.mutedLethalFlips = 0;
         }
      }
   }

   private boolean isUseKeyPressed() {
      return this.mc.options != null && this.mc.options.useKey.isPressed();
   }

   private boolean isInventoryScreenOpen() {
      return this.mc.currentScreen instanceof HandledScreen;
   }

   private boolean isSlotSwapLocked(int slot) {
      if (slot < 0) {
         return false;
      } else {
         boolean locked = ReviveClient.SWAP.isSlotLocked(slot);
         if (this.overrideSlotLockForFood) {
            locked = false;
         }

         return locked && this.shouldOverrideSlotLock() ? false : locked;
      }
   }

   private boolean shouldOverrideSlotLock() {
      boolean mainHandHasTotem = this.mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
      boolean offHandHasTotem = this.mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
      return !mainHandHasTotem && !offHandHasTotem;
   }

   private static boolean isHotbar(int slot) {
      return slot >= 0 && slot < 9;
   }

   public static enum HandSide {
      LEFT,
      RIGHT;
   }

   private static final class OffhandSwapRecord {
      final int slot;

      private OffhandSwapRecord(int slot) {
         this.slot = slot;
      }
   }

   public static enum TotemGappleMode {
      Hold,
      Holding,
      Always;
   }
}
