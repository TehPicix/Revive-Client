package revivedevelopment.reviveclient.systems.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.FreeLook;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.MaceUtils;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class MaceDive extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> height = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("height")
            .description("How high above the target to climb before diving. Lower = faster, more frequent smashes; higher = bigger single hits.")
            .defaultValue(12.0)
            .min(5.0)
            .sliderRange(5.0, 60.0)
            .build()
      );
   private final Setting<Double> angle = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("angle")
            .description(
               "Dive approach angle. 90 = straight down on top of them; lower = a slanted run-in (offsets the climb column to the side so the dive comes in at an angle)."
            )
            .defaultValue(90.0)
            .min(30.0)
            .sliderRange(30.0, 90.0)
            .build()
      );
   private final Setting<Double> attackRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("attack-range")
            .description("Distance to the target at which the mace hit is thrown. Vanilla reach is ~3.0; higher only registers on lenient/Grim servers.")
            .defaultValue(3.0)
            .min(2.5)
            .sliderRange(2.5, 6.0)
            .build()
      );
   private final Setting<Boolean> shieldBreak = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("shield-break")
            .description(
               "If the victim is blocking with a shield when the smash is due, hit them with an axe first (disables the shield) and then land the mace in the same tick."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> spoofFall = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("spoof-fall")
            .description(
               "Adds fake fall distance right before the hit (teleport-up/teleport-back position packets, like LiquidBounce MaceKill / TrollHack MaceSpoof). Only for plain vanilla/Paper servers. On 2b2t it made hits STOP popping totems (12 spoofed hits = 1 pop, 4 unspoofed = 4 pops) - leave it off there."
            )
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> spoofHeight = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("spoof-height")
            .description(
               "Extra fall blocks to fake. Up to 10 works on vanilla; more than 10 only on Paper/Spigot. Capped automatically by the clear air above you."
            )
            .defaultValue(10)
            .min(1)
            .max(170)
            .sliderRange(1, 60)
            .visible(() -> this.spoofFall.get())
            .build()
      );
   private final Setting<Double> detectionRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("target-range")
            .description("Max horizontal range to detect / keep a target.")
            .defaultValue(64.0)
            .min(10.0)
            .sliderRange(10.0, 128.0)
            .build()
      );
   private final Setting<Boolean> freelook = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("freelook")
            .description("Hides the steering rotation visually using FreeLook camera mode. Steering still works server-side.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> debug = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Log phase transitions / hits to chat and latest.log. Spammy — diagnostics only.")
            .defaultValue(false)
            .build()
      );
   private static final int MIN_FALL_TICKS = 3;
   private static final int ATTACK_DELAY_MS = 100;
   private static final double DENSITY_THRESHOLD = 7.0;
   private static final SettingColor RENDER_FILL = new SettingColor(255, 100, 0, 50);
   private static final SettingColor RENDER_LINE = new SettingColor(255, 100, 0, 150);
   private static final float CLIMB_PITCH = -45.0F;
   private static final float RECOVER_PITCH = -50.0F;
   private static final double YAW_HOLD_EPS = 2.0;
   private static final float[] YAW_STEPS = new float[]{0.0F, 25.0F, -25.0F, 50.0F, -50.0F, 80.0F, -80.0F, 120.0F, -120.0F, 180.0F};
   private static final float[] PITCH_STEPS = new float[]{0.0F, -25.0F, -50.0F, -75.0F};
   private static final double PROBE_DIST = 4.5;
   private static final int STUCK_LIMIT = 10;
   private static final int ROCKET_DELAY = 3;
   private static final int CHESTPLATE_SETTLE_TICKS = 4;
   private static final RegistryKey<Enchantment> DENSITY = Enchantments.DENSITY;
   private static final RegistryKey<Enchantment> BREACH = Enchantments.BREACH;
   private MaceDive.Phase phase = MaceDive.Phase.IDLE;
   private PlayerEntity target;
   private int ticks;
   private int rocketCooldown;
   private int diveCount;
   private int lastMacePullTick = -1000;
   private boolean warnedNoMace = false;
   private boolean hitThisDive = false;
   private int chestplateTicks = 0;
   private int fallTicks = 0;
   private boolean elytraPreSwapped = false;
   private double smashEntryY = Double.NaN;
   private double confirmDrop = Double.NaN;
   private float recoverYaw = 0.0F;
   private Vec3d targetVel = Vec3d.ZERO;
   private Vec3d lastTargetPos = null;
   private Vec3d approachDir = new Vec3d(1.0, 0.0, 0.0);
   private float aimYaw;
   private float aimPitch;
   private long lastHitMs = 0L;
   private int savedSlot = -1;
   private double lastClimbY = 0.0;
   private int climbStallTicks = 0;
   private Vec3d lastPos = null;
   private int stuckTicks = 0;
   private boolean holdingJump;
   private int avoidTicks = 0;
   private float avoidYaw = 0.0F;
   private float avoidPitch = -45.0F;
   private boolean freelookWasActive = false;
   private FreeLook.Mode freelookPrevMode = null;

   public MaceDive() {
      super(Categories.Combat, "mace-dive", "All-in-one mace dive: climb, glide-dive, drop the elytra above them, smash, climb out, repeat.");
      ElytraDurabilitySaver.get().registerVeto(this::isActive);
   }

   @Override
   public void onActivate() {
      this.reset();
      if (this.freelook.get()) {
         FreeLook fl = Modules.get().get(FreeLook.class);
         this.freelookWasActive = fl.isActive();
         this.freelookPrevMode = fl.mode.get();
         fl.cameraYaw = this.mc.player.getYaw();
         fl.cameraPitch = this.mc.player.getPitch();
         fl.mode.set(FreeLook.Mode.Camera);
         if (!fl.isActive()) {
            fl.toggle();
         }
      }
   }

   @Override
   public void onDeactivate() {
      this.reset();
      if (this.freelook.get()) {
         FreeLook fl = Modules.get().get(FreeLook.class);
         if (!this.freelookWasActive && fl.isActive()) {
            fl.toggle();
         } else if (this.freelookWasActive && this.freelookPrevMode != null) {
            fl.mode.set(this.freelookPrevMode);
         }
      }
   }

   private void reset() {
      this.restoreSlot();
      this.warnedNoMace = false;
      this.phase = MaceDive.Phase.IDLE;
      this.target = null;
      this.ticks = 0;
      this.rocketCooldown = 0;
      this.stuckTicks = 0;
      this.lastPos = null;
      this.holdingJump = false;
      if (this.mc.options != null) {
         this.mc.options.jumpKey.setPressed(false);
      }
   }

   @Override
   public String getInfoString() {
      return this.target != null ? EntityUtils.getName(this.target) + " [" + this.phase.name() + "] x" + this.diveCount : this.phase.name();
   }

   private void dbg(String fmt, Object... args) {
      if (this.debug.get()) {
         this.info(fmt, args);

         try {
            ReviveClient.LOG.info("[MaceDive] " + String.format(fmt, args));
         } catch (Exception var4) {
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.holdingJump) {
         this.holdingJump = false;
         if (this.mc.options != null) {
            this.mc.options.jumpKey.setPressed(Input.isPressed(this.mc.options.jumpKey));
         }
      }

      ClientPlayerEntity player = this.mc.player;
      if (player == null || this.mc.world == null || !player.isAlive() || player.isSpectator()) {
         this.reset();
      } else if (!player.isUsingItem()) {
         this.ticks++;
         if (this.rocketCooldown > 0) {
            this.rocketCooldown--;
         }

         if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            this.chestplateTicks++;
         } else {
            this.chestplateTicks = 0;
         }

         if (!player.isOnGround() && player.getVelocity().y < 0.0) {
            this.fallTicks++;
         } else {
            this.fallTicks = 0;
         }

         if (this.phase != MaceDive.Phase.IDLE && this.phase != MaceDive.Phase.CLIMB && this.phase != MaceDive.Phase.RECOVER) {
            this.mc.options.jumpKey.setPressed(false);
         }

         boolean hadTarget = this.target != null;
         if (!this.validateTarget(player)) {
            this.target = this.pickTarget(player);
         }

         if (this.target == null) {
            if (hadTarget) {
               this.dbg("TARGET lost -> IDLE");
            }

            this.restoreSlot();
            this.phase = MaceDive.Phase.IDLE;
            this.lastTargetPos = null;
         } else {
            Vec3d tp = this.target.getEntityPos();
            this.targetVel = this.lastTargetPos != null ? tp.subtract(this.lastTargetPos) : Vec3d.ZERO;
            this.lastTargetPos = tp;
            if (!this.hasMaceAnywhere()) {
               if (!this.warnedNoMace) {
                  this.warning("No mace in your inventory - not diving. Hotbar: [%s]", new Object[]{this.hotbarNames()});
                  this.dbg("no mace anywhere -> holding off (phase=%s)", this.phase);
                  this.warnedNoMace = true;
               }

               if (this.phase != MaceDive.Phase.DIVE && this.phase != MaceDive.Phase.SMASH) {
                  if (this.phase == MaceDive.Phase.IDLE || player.isOnGround()) {
                     this.phase = MaceDive.Phase.IDLE;
                     this.restoreSlot();
                     return;
                  }
               } else {
                  this.enterPhase(MaceDive.Phase.RECOVER);
               }
            } else if (this.warnedNoMace) {
               this.warnedNoMace = false;
               this.info("Mace found - diving.", new Object[0]);
            }

            this.holdMace();
            this.updateStuck(player);
            if (this.stuckTicks > 10) {
               this.unstick(player);
               this.tryMaceHit(player);
            } else if (player.isOnGround() && this.phase != MaceDive.Phase.DIVE && this.phase != MaceDive.Phase.SMASH && this.phase != MaceDive.Phase.CLIMB) {
               this.takeoff(player);
               this.tryMaceHit(player);
            } else {
               switch (this.phase) {
                  case IDLE:
                     this.tickIdle(player);
                     break;
                  case CLIMB:
                     this.tickClimb(player);
                     break;
                  case DIVE:
                     this.tickDive(player);
                     break;
                  case SMASH:
                     this.tickSmash(player);
                     break;
                  case RECOVER:
                     this.tickRecover(player);
               }

               this.tryMaceHit(player);
            }
         }
      }
   }

   private void tryMaceHit(ClientPlayerEntity player) {
      PlayerEntity victim = this.closestAttackable(player);
      if (victim != null) {
         if (!player.isOnGround() && this.fallTicks >= 3) {
            boolean serverNotFlying = !this.isGliding(player);
            boolean fallbackSettled = !player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && this.chestplateTicks >= 4;
            if (!serverNotFlying && !fallbackSettled) {
               this.dbg("no hit: waiting for chest swap (gliding=%s chestTicks=%d)", this.isGliding(player), this.chestplateTicks);
            } else if (System.currentTimeMillis() - this.lastHitMs >= 100L) {
               if (player.fallDistance < 1.5 && !this.spoofFall.get()) {
                  this.dbg("no hit: fallDistance %.1f too small", player.fallDistance);
               } else if (this.findBestMaceSlot(player.fallDistance) == -1) {
                  this.dbg("no hit: no mace in hotbar [%s]", this.hotbarNames());
               } else if (!this.isMace(player.getMainHandStack())) {
                  this.dbg("no hit: mace not in hand yet (holding)");
               } else {
                  float[] aim = this.attackAim(player, victim);
                  if (aim == null) {
                     this.dbg("no hit: aim ray misses %s", EntityUtils.getName(victim));
                  } else {
                     boolean wasSprinting = player.isSprinting() && !player.isGliding();
                     if (wasSprinting) {
                        this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player, Mode.STOP_SPRINTING));
                     }

                     if (!this.isGliding(player)) {
                        this.setRotation(player, aim[0], aim[1]);
                     }

                     this.syncLook(player, aim[0], aim[1]);
                     boolean shieldBroken = this.shieldBreak.get() && isShielding(victim) && this.breakShield(player, victim);
                     int spoofed = this.spoofFall.get() ? this.spoofFallDistance(player) : 0;
                     this.mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(victim, player.isSneaking()));
                     player.swingHand(Hand.MAIN_HAND);
                     if (wasSprinting && !player.isGliding()) {
                        this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player, Mode.START_SPRINTING));
                     }

                     this.hitThisDive = true;
                     this.lastHitMs = System.currentTimeMillis();
                     this.dbg(
                        "MACE HIT %s | fall=%.1f spoof=+%d shield=%s aim=%.1f/%.1f mainhand=%s",
                        EntityUtils.getName(victim),
                        player.fallDistance,
                        spoofed,
                        shieldBroken,
                        aim[0],
                        aim[1],
                        this.mc.player.getMainHandStack().getItem()
                     );
                     if (!ReviveClient.SWAP.isSilentSwapActive()) {
                        PlayerUtils.silentSwapEquipElytra();
                     }
                  }
               }
            }
         } else {
            this.dbg("no hit: not a falling smash (ground=%s fallTicks=%d)", player.isOnGround(), this.fallTicks);
         }
      }
   }

   private PlayerEntity closestAttackable(ClientPlayerEntity player) {
      PlayerEntity best = null;
      double bestDist = this.attackRange.get();
      Vec3d eyes = player.getEyePos();

      for (Entity e : this.mc.world.getEntities()) {
         if (e != player && e instanceof PlayerEntity p && p.isAlive() && !p.isSpectator() && !p.isRemoved() && !Friends.get().isFriend(p)) {
            Box b = p.getBoundingBox();
            Vec3d cp = new Vec3d(MathHelper.clamp(eyes.x, b.minX, b.maxX), MathHelper.clamp(eyes.y, b.minY, b.maxY), MathHelper.clamp(eyes.z, b.minZ, b.maxZ));
            double d = cp.distanceTo(eyes);
            if (d <= bestDist) {
               best = p;
               bestDist = d;
            }
         }
      }

      return best;
   }

   private void holdMace() {
      if (!this.isMace(this.mc.player.getMainHandStack())) {
         int slot = this.findBestMaceSlot(this.mc.player.fallDistance);
         if (slot == -1) {
            this.pullMaceToHotbar();
         } else {
            if (this.savedSlot == -1) {
               this.savedSlot = this.mc.player.getInventory().selectedSlot;
            }

            this.switchSlot(slot);
         }
      }
   }

   private void pullMaceToHotbar() {
      int now = this.mc.player.age;
      if (this.phase != MaceDive.Phase.SMASH && now - this.lastMacePullTick >= 10) {
         FindItemResult inv = InvUtils.find(this::isMace, 9, 35);
         if (!inv.found()) {
            if (now - this.lastMacePullTick >= 100) {
               this.lastMacePullTick = now;
               this.dbg("no mace anywhere in the inventory");
            }
         } else {
            int dest = -1;

            for (int i = 0; i < 9 && dest == -1; i++) {
               if (this.mc.player.getInventory().getStack(i).isEmpty()) {
                  dest = i;
               }
            }

            for (int ix = 0; ix < 9 && dest == -1; ix++) {
               if (!diveNeeds(this.mc.player.getInventory().getStack(ix)) && ix != this.mc.player.getInventory().selectedSlot) {
                  dest = ix;
               }
            }

            if (dest != -1) {
               this.lastMacePullTick = now;
               this.dbg("mace in inventory slot %d -> hotbar %d", inv.slot(), dest);
               InvUtils.move().from(inv.slot()).toHotbar(dest);
            }
         }
      }
   }

   private static boolean diveNeeds(ItemStack s) {
      return s.isOf(Items.ELYTRA)
         || s.isOf(Items.FIREWORK_ROCKET)
         || s.isOf(Items.TOTEM_OF_UNDYING)
         || s.isOf(Items.NETHERITE_CHESTPLATE)
         || s.isOf(Items.DIAMOND_CHESTPLATE)
         || s.isOf(Items.ENCHANTED_GOLDEN_APPLE)
         || s.isOf(Items.GOLDEN_APPLE);
   }

   private void restoreSlot() {
      if (this.savedSlot != -1) {
         if (this.mc.player != null) {
            this.switchSlot(this.savedSlot);
         }

         this.savedSlot = -1;
      }
   }

   private void syncLook(ClientPlayerEntity player, float yaw, float pitch) {
      if (this.mc.getNetworkHandler() != null) {
         this.mc.getNetworkHandler().sendPacket(new LookAndOnGround(yaw, pitch, player.isOnGround(), player.horizontalCollision));
      }
   }

   private float[] attackAim(ClientPlayerEntity player, PlayerEntity victim) {
      Vec3d eyes = player.getEyePos().add(player.getVelocity());
      Box box = victim.getBoundingBox();
      Vec3d edge = new Vec3d(
         MathHelper.clamp(eyes.x, box.minX, box.maxX), MathHelper.clamp(eyes.y, box.minY, box.maxY), MathHelper.clamp(eyes.z, box.minZ, box.maxZ)
      );
      float[] a = this.snapGcd(player.getYaw(), player.getPitch(), yawTo(eyes, edge), pitchTo(eyes, edge));
      if (this.rayHits(eyes, a[0], a[1], box)) {
         return a;
      } else {
         Vec3d center = box.getCenter();
         a = this.snapGcd(player.getYaw(), player.getPitch(), yawTo(eyes, center), pitchTo(eyes, center));
         return this.rayHits(eyes, a[0], a[1], box) ? a : null;
      }
   }

   private static boolean isShielding(PlayerEntity p) {
      return p.isUsingItem() && p.getActiveItem().isOf(Items.SHIELD);
   }

   private boolean rayHits(Vec3d eyes, float yaw, float pitch, Box box) {
      Box b = box.expand(0.1);
      if (b.contains(eyes)) {
         return true;
      } else {
         Vec3d end = eyes.add(Vec3d.fromPolar(pitch, yaw).multiply(this.attackRange.get() + 0.5));
         return b.raycast(eyes, end).isPresent();
      }
   }

   private boolean breakShield(ClientPlayerEntity player, PlayerEntity victim) {
      int axe = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack s = player.getInventory().getStack(i);
         if (s.getItem() instanceof AxeItem && !this.isMace(s)) {
            axe = i;
            break;
         }
      }

      if (axe != -1 && !ReviveClient.SWAP.isSlotLocked(axe)) {
         int maceSlot = player.getInventory().selectedSlot;
         this.switchSlot(axe);
         this.mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(victim, player.isSneaking()));
         player.swingHand(Hand.MAIN_HAND);
         this.switchSlot(maceSlot);
         this.dbg("SHIELD: axe hit on %s before the mace", EntityUtils.getName(victim));
         return true;
      } else {
         return false;
      }
   }

   private int spoofFallDistance(ClientPlayerEntity player) {
      if (this.mc.getNetworkHandler() != null && this.mc.world != null && !player.hasVehicle()) {
         int h = this.clearHeightAbove(player, this.spoofHeight.get());
         if (h <= 0) {
            return 0;
         } else {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            boolean col = player.horizontalCollision;
            int packets = (int)Math.ceil(h / 10.0);
            if (packets > 20) {
               packets = 1;
            }

            for (int i = 0; i < packets - 1; i++) {
               this.mc.getNetworkHandler().sendPacket(new OnGroundOnly(false, col));
            }

            this.mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, y + h, z, false, col));
            this.mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, y, z, false, col));
            this.mc.getNetworkHandler().sendPacket(new PositionAndOnGround(x, y, z, false, col));
            return h;
         }
      } else {
         return 0;
      }
   }

   private int clearHeightAbove(ClientPlayerEntity player, int max) {
      Box box = player.getBoundingBox();
      int top = this.mc.world.getBottomY() + this.mc.world.getHeight();
      BlockPos feet = player.getBlockPos();

      for (int i = max; i >= 1; i--) {
         if (!(box.maxY + i >= top)
            && this.mc.world.isSpaceEmpty(player, box.stretch(0.0, i, 0.0))
            && this.mc.world.getFluidState(feet.up(i)).isEmpty()
            && this.mc.world.getFluidState(feet.up(i + 1)).isEmpty()) {
            return i;
         }
      }

      return 0;
   }

   private float mouseGcd() {
      double f = (Double)this.mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
      return (float)(f * f * f * 8.0) * 0.15F;
   }

   private float[] snapGcd(float curYaw, float curPitch, float yaw, float pitch) {
      float g = this.mouseGcd();
      if (!(g > 0.0F)) {
         return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
      } else {
         float dYaw = Math.round(MathHelper.wrapDegrees(yaw - curYaw) / g) * g;
         int steps = Math.round((MathHelper.clamp(pitch, -90.0F, 90.0F) - curPitch) / g);
         steps = Math.min(steps, (int)Math.floor((90.0F - curPitch) / g));
         steps = Math.max(steps, (int)Math.ceil((-90.0F - curPitch) / g));
         return new float[]{MathHelper.wrapDegrees(curYaw + dYaw), MathHelper.clamp(curPitch + steps * g, -90.0F, 90.0F)};
      }
   }

   private void switchSlot(int slot) {
      this.mc.player.getInventory().selectedSlot = slot;
      if (this.mc.getNetworkHandler() != null) {
         this.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
      }
   }

   private void tickIdle(ClientPlayerEntity player) {
      this.setRotation(player, player.getYaw(), -45.0F);
      boolean gliding = this.startGliding(player);
      if (gliding && this.rocketCooldown <= 0 && this.fireRocket(player)) {
         this.rocketCooldown = 3;
      }

      if (!player.isOnGround() && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         this.lockApproach(player);
         this.enterPhase(MaceDive.Phase.CLIMB);
      }
   }

   private void tickClimb(ClientPlayerEntity player) {
      Vec3d climb = this.climbColumn();
      float[] aim = this.clearAim(player, this.yawTo(player, climb), -45.0F);
      this.setRotation(player, aim[0], aim[1]);
      this.startGliding(player);
      double above = player.getY() - this.target.getY();
      if (player.getY() - this.lastClimbY > 0.25) {
         this.climbStallTicks = 0;
      } else {
         this.climbStallTicks++;
      }

      this.lastClimbY = player.getY();
      boolean canSmash = above >= 4.0;
      boolean blocked = this.climbStallTicks > 10 || this.ceilingClose(player);
      if (this.isGliding(player) && (above >= this.height.get() || blocked && canSmash)) {
         if (this.isMace(player.getMainHandStack())) {
            this.dbg("CLIMB -> DIVE (above=%.1f%s)", above, blocked ? " blocked" : "");
            this.enterPhase(MaceDive.Phase.DIVE);
            return;
         }

         this.dbg("CLIMB: at height but no mace in hand yet - holding");
      }

      if (this.ticks > 100) {
         this.dbg("CLIMB stuck -> IDLE");
         this.target = null;
         this.phase = MaceDive.Phase.IDLE;
      } else {
         if (this.isGliding(player) && this.rocketCooldown <= 0 && this.fireRocket(player)) {
            this.rocketCooldown = 3;
         }
      }
   }

   private boolean ceilingClose(ClientPlayerEntity player) {
      if (this.mc.world == null) {
         return false;
      } else {
         BlockPos pos = player.getBlockPos();

         for (int up = 2; up <= 3; up++) {
            if (!this.mc.world.getBlockState(pos.up(up)).isReplaceable()) {
               return true;
            }
         }

         return false;
      }
   }

   private float[] clearAim(ClientPlayerEntity player, float desiredYaw, float desiredPitch) {
      if (this.avoidTicks > 0) {
         this.avoidTicks--;
         return new float[]{this.avoidYaw, this.avoidPitch};
      } else if (!this.blockAheadYaw(player, desiredYaw, desiredPitch, 4.5)) {
         return new float[]{desiredYaw, desiredPitch};
      } else {
         for (float dp : PITCH_STEPS) {
            float p = MathHelper.clamp(desiredPitch + dp, -88.0F, 45.0F);

            for (float dy : YAW_STEPS) {
               if (!this.blockAheadYaw(player, desiredYaw + dy, p, 4.5)) {
                  this.avoidYaw = desiredYaw + dy;
                  this.avoidPitch = p;
                  this.avoidTicks = 3;
                  this.dbg("AVOID: steer yaw %+.0f pitch %.0f around block", dy, p);
                  return new float[]{this.avoidYaw, this.avoidPitch};
               }
            }
         }

         this.avoidYaw = desiredYaw;
         this.avoidPitch = -88.0F;
         this.avoidTicks = 3;
         this.dbg("AVOID: boxed in, straight up");
         return new float[]{this.avoidYaw, this.avoidPitch};
      }
   }

   private void takeoff(ClientPlayerEntity player) {
      Vec3d climb = this.climbColumn();
      float[] aim = this.clearAim(player, this.yawTo(player, climb), -55.0F);
      this.setRotation(player, aim[0], aim[1]);
      this.rocketCooldown = 0;
      boolean gliding = this.startGliding(player);
      if (gliding && this.fireRocket(player)) {
         this.rocketCooldown = 3;
      }

      double dxt = player.getX() - this.target.getX();
      double dzt = player.getZ() - this.target.getZ();
      this.dbg("TAKEOFF y=%.1f distTarget=%.1f gliding=%s", player.getY(), Math.sqrt(dxt * dxt + dzt * dzt), gliding);
      this.phase = MaceDive.Phase.CLIMB;
      this.climbStallTicks = 0;
      this.lastClimbY = player.getY();
   }

   private void updateStuck(ClientPlayerEntity player) {
      Vec3d pos = player.getEntityPos();
      if (!player.isOnGround() && this.lastPos != null) {
         if (pos.distanceTo(this.lastPos) < 0.12) {
            this.stuckTicks++;
         } else {
            this.stuckTicks = 0;
         }

         this.lastPos = pos;
      } else {
         this.stuckTicks = 0;
         this.lastPos = pos;
      }
   }

   private void unstick(ClientPlayerEntity player) {
      float free = this.findFreeYaw(player);
      this.dbg("UNSTICK: pinned %d ticks at y=%.1f -> yaw %.0f", this.stuckTicks, player.getY(), free);
      this.avoidYaw = free;
      this.avoidPitch = -35.0F;
      this.avoidTicks = 12;
      this.setRotation(player, free, -35.0F);
      this.startGliding(player);
      if (this.fireRocket(player)) {
         this.rocketCooldown = 3;
      }

      if (this.phase == MaceDive.Phase.DIVE || this.phase == MaceDive.Phase.SMASH) {
         this.phase = MaceDive.Phase.CLIMB;
         this.ticks = 0;
         this.climbStallTicks = 0;
         this.lastClimbY = player.getY();
      }

      this.stuckTicks = 0;
      this.lastPos = null;
   }

   private float findFreeYaw(ClientPlayerEntity player) {
      float base = player.getYaw();

      for (float off : new float[]{180.0F, 90.0F, -90.0F, 135.0F, -135.0F, 45.0F, -45.0F}) {
         if (!this.blockAheadYaw(player, base + off, -35.0F, 4.0)) {
            return base + off;
         }
      }

      return base + 180.0F;
   }

   private boolean blockAheadYaw(ClientPlayerEntity player, float yaw, float pitch, double dist) {
      if (this.mc.world == null) {
         return false;
      } else {
         double y = Math.toRadians(yaw + 90.0);
         double p = Math.toRadians(pitch);
         Vec3d dir = new Vec3d(Math.cos(y) * Math.cos(p), -Math.sin(p), Math.sin(y) * Math.cos(p));
         Vec3d eyes = player.getEyePos();
         Vec3d end = eyes.add(dir.multiply(dist));
         BlockHitResult hit = this.mc.world.raycast(new RaycastContext(eyes, end, ShapeType.COLLIDER, FluidHandling.NONE, player));
         return hit.getType() == Type.BLOCK;
      }
   }

   private void tickDive(ClientPlayerEntity player) {
      Vec3d center = this.leadCenter(player);
      double aboveTarget = player.getY() - this.target.getY();
      double dx = player.getX() - center.x;
      double dz = player.getZ() - center.z;
      double horiz = Math.sqrt(dx * dx + dz * dz);
      this.setRotation(player, this.yawTo(player, center), this.pitchTo(player, center));
      this.startGliding(player);
      double vy = Math.abs(player.getVelocity().y);
      double autoSwapAbove = this.attackRange.get() + vy * 5.0 + 2.5;
      if (!Double.isNaN(this.confirmDrop)) {
         double adaptive = this.attackRange.get() + this.confirmDrop * 1.3 + vy * 1.5 + 1.5;
         autoSwapAbove = MathHelper.clamp(adaptive, 6.0, autoSwapAbove);
      }

      boolean aligned = horiz <= this.attackRange.get() + 0.5;
      if ((!aligned || !(aboveTarget <= autoSwapAbove) || !(aboveTarget >= 1.0)) && !(aboveTarget <= 2.5)) {
         if (player.getY() < this.target.getY() - 1.0) {
            this.dbg("DIVE -> RECOVER (below target)");
            this.enterPhase(MaceDive.Phase.RECOVER);
         }
      } else if (!this.isMace(player.getMainHandStack())) {
         this.dbg("DIVE -> RECOVER (no mace in hand at swap point)");
         this.enterPhase(MaceDive.Phase.RECOVER);
      } else {
         this.dbg("DIVE -> SMASH (above=%.1f horiz=%.1f swapAbove=%.1f vy=%.2f)", aboveTarget, horiz, autoSwapAbove, vy);
         this.enterPhase(MaceDive.Phase.SMASH);
      }
   }

   private void tickSmash(ClientPlayerEntity player) {
      boolean belowTarget = player.getY() < this.target.getY() - 1.0;
      boolean atBounce = player.getY() <= this.bounceFloorY(player);
      double vy = Math.abs(player.getVelocity().y);
      Vec3d tc = this.targetCenter();
      double hdx = player.getX() - tc.x;
      double hdz = player.getZ() - tc.z;
      boolean offTarget = Math.sqrt(hdx * hdx + hdz * hdz) > this.attackRange.get() + 1.0;
      boolean groundClose = !this.hitThisDive && offTarget && this.groundWithin(player, vy * 3.0 + 1.5);
      if (!this.hitThisDive && !belowTarget && !player.isOnGround() && !atBounce && !groundClose && this.ticks <= 14) {
         Vec3d center = this.targetCenter();
         double dist = this.closestPoint(player).distanceTo(player.getEyePos());
         float pitch = this.pitchTo(player, center);
         boolean inBand = dist <= this.attackRange.get() + 1.0;
         if (inBand) {
            pitch = MathHelper.clamp(pitch, -10.0F, 22.0F);
         }

         this.setRotation(player, this.yawTo(player, center), pitch);
         if (!this.elytraPreSwapped) {
            if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
               if (!ReviveClient.SWAP.isSilentSwapActive()) {
                  PlayerUtils.silentSwapEquipChestplate();
               }
            } else if (!this.isGliding(player) && this.chestplateTicks >= 1) {
               if (!ReviveClient.SWAP.isSilentSwapActive()) {
                  PlayerUtils.silentSwapEquipElytra();
               }

               this.elytraPreSwapped = true;
               if (!Double.isNaN(this.smashEntryY)) {
                  double drop = Math.max(0.0, this.smashEntryY - player.getY());
                  this.confirmDrop = Double.isNaN(this.confirmDrop) ? drop : this.confirmDrop * 0.6 + drop * 0.4;
                  this.dbg("CONFIRM not-gliding after %.1f blocks (ema %.1f, chestTicks=%d)", drop, this.confirmDrop, this.chestplateTicks);
               }
            }
         }
      } else {
         this.dbg(
            "SMASH -> RECOVER (hit=%s below=%s ground=%s bounce=%s groundClose=%s y=%.1f chestTicks=%d)",
            this.hitThisDive,
            belowTarget,
            player.isOnGround(),
            atBounce,
            groundClose,
            player.getY(),
            this.chestplateTicks
         );
         this.enterPhase(MaceDive.Phase.RECOVER);
      }
   }

   private void tickRecover(ClientPlayerEntity player) {
      if (this.ticks <= 1) {
         double dx = player.getX() - this.target.getX();
         double dz = player.getZ() - this.target.getZ();
         this.recoverYaw = dx * dx + dz * dz < 1.0 ? player.getYaw() : this.yawTo(player, this.climbColumn());
      }

      float[] aim = this.clearAim(player, this.recoverYaw, -50.0F);
      this.setRotation(player, aim[0], aim[1]);
      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         if (!ReviveClient.SWAP.isSilentSwapActive()) {
            PlayerUtils.silentSwapEquipElytra();
         }

         if (!player.isOnGround()) {
            return;
         }
      }

      boolean flying = this.startGliding(player);
      if (flying && this.rocketCooldown <= 0 && player.getPitch() < -25.0F && this.fireRocket(player)) {
         this.rocketCooldown = 3;
      }

      if (this.isGliding(player) && player.getVelocity().y > 0.0) {
         this.dbg("RECOVER -> CLIMB");
         this.lockApproach(player);
         this.enterPhase(MaceDive.Phase.CLIMB);
      } else if (this.ticks > 60) {
         this.dbg("RECOVER timeout -> IDLE");
         this.enterPhase(MaceDive.Phase.IDLE);
      }
   }

   private int findBestMaceSlot(double fallDist) {
      boolean useDensity = fallDist >= 7.0;
      int pref = this.findMaceSlot(useDensity ? DENSITY : BREACH);
      if (pref != -1) {
         return pref;
      } else {
         int other = this.findMaceSlot(useDensity ? BREACH : DENSITY);
         if (other != -1) {
            return other;
         } else {
            for (int i = 0; i < 9; i++) {
               if (this.isMace(this.mc.player.getInventory().getStack(i))) {
                  return i;
               }
            }

            return -1;
         }
      }
   }

   private int findMaceSlot(RegistryKey<Enchantment> key) {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getStack(i);
         if (this.isMace(stack) && Utils.getEnchantmentLevel(stack, key) > 0) {
            return i;
         }
      }

      return -1;
   }

   private boolean hasMaceAnywhere() {
      for (int i = 0; i < 9; i++) {
         if (this.isMace(this.mc.player.getInventory().getStack(i))) {
            return true;
         }
      }

      return InvUtils.find(this::isMace, 9, 35).found();
   }

   private boolean groundWithin(ClientPlayerEntity player, double dist) {
      if (this.mc.world == null) {
         return false;
      } else {
         Vec3d feet = player.getEntityPos();
         BlockHitResult hit = this.mc.world.raycast(new RaycastContext(feet, feet.add(0.0, -dist, 0.0), ShapeType.COLLIDER, FluidHandling.NONE, player));
         return hit.getType() == Type.BLOCK;
      }
   }

   private String hotbarNames() {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < 9; i++) {
         ItemStack st = this.mc.player.getInventory().getStack(i);
         if (i > 0) {
            sb.append(", ");
         }

         sb.append(i).append('=').append(st.isEmpty() ? "-" : Registries.ITEM.getId(st.getItem()).getPath());
         if (st.isOf(Items.NETHERITE_AXE) || st.isOf(Items.MACE)) {
            sb.append(" {").append(MaceUtils.describe(st)).append('}');
         }
      }

      return sb.toString();
   }

   private boolean isMace(ItemStack stack) {
      return MaceUtils.isMace(stack);
   }

   private void lockApproach(ClientPlayerEntity player) {
      double dx = player.getX() - this.target.getX();
      double dz = player.getZ() - this.target.getZ();
      double h = Math.sqrt(dx * dx + dz * dz);
      this.approachDir = h < 0.5 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(dx / h, 0.0, dz / h);
   }

   private Vec3d climbColumn() {
      double a = Math.toRadians(this.angle.get());
      double offset = this.angle.get() >= 89.0 ? 0.0 : this.height.get() / Math.tan(a);
      return new Vec3d(
         this.target.getX() + this.approachDir.x * offset, this.target.getY() + this.height.get(), this.target.getZ() + this.approachDir.z * offset
      );
   }

   private Vec3d targetCenter() {
      return this.target.getEntityPos().add(0.0, this.target.getHeight() * 0.5, 0.0);
   }

   private Vec3d leadCenter(ClientPlayerEntity player) {
      Vec3d c = this.targetCenter();
      double above = Math.max(0.0, player.getY() - this.target.getY());
      double fallTicks = Math.min(8.0, above / 1.6);
      double lx = MathHelper.clamp(this.targetVel.x * fallTicks, -4.0, 4.0);
      double lz = MathHelper.clamp(this.targetVel.z * fallTicks, -4.0, 4.0);
      return c.add(lx, 0.0, lz);
   }

   private Vec3d closestPoint(ClientPlayerEntity player) {
      Box b = this.target.getBoundingBox();
      Vec3d e = player.getEyePos();
      return new Vec3d(MathHelper.clamp(e.x, b.minX, b.maxX), MathHelper.clamp(e.y, b.minY, b.maxY), MathHelper.clamp(e.z, b.minZ, b.maxZ));
   }

   private double coverTopY() {
      if (this.mc.world == null) {
         return Double.NaN;
      } else {
         BlockPos feet = this.target.getBlockPos();

         for (int up = 1; up <= 4; up++) {
            BlockPos pos = feet.up(up);
            if (!this.mc.world.getBlockState(pos).isReplaceable()) {
               return pos.getY() + 1.0;
            }
         }

         return Double.NaN;
      }
   }

   private double bounceFloorY(ClientPlayerEntity player) {
      double cover = this.coverTopY();
      return Double.isNaN(cover) ? this.target.getY() + 1.5 : cover + 1.1;
   }

   private void setRotation(ClientPlayerEntity player, float yaw, float pitch) {
      float[] s = this.snapGcd(player.getYaw(), player.getPitch(), yaw, pitch);
      this.aimYaw = MathHelper.wrapDegrees(s[0]);
      this.aimPitch = s[1];
      player.setYaw(this.aimYaw);
      player.setPitch(this.aimPitch);
      player.setHeadYaw(this.aimYaw);
   }

   private float yawTo(ClientPlayerEntity player, Vec3d point) {
      double dx = point.x - player.getX();
      double dz = point.z - player.getZ();
      return dx * dx + dz * dz < 4.0 ? player.getYaw() : (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
   }

   private float pitchTo(ClientPlayerEntity player, Vec3d point) {
      double dx = point.x - player.getX();
      double dy = point.y - player.getEyeY();
      double dz = point.z - player.getZ();
      return (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
   }

   private static float yawTo(Vec3d from, Vec3d to) {
      return (float)(Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
   }

   private static float pitchTo(Vec3d from, Vec3d to) {
      double dx = to.x - from.x;
      double dy = to.y - from.y;
      double dz = to.z - from.z;
      return (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
   }

   private boolean isGliding(ClientPlayerEntity player) {
      return player.getPose() == EntityPose.GLIDING;
   }

   private boolean startGliding(ClientPlayerEntity player) {
      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         if (!ReviveClient.SWAP.isSilentSwapActive()) {
            PlayerUtils.silentSwapEquipElytra();
         }

         return false;
      } else if (this.isGliding(player)) {
         this.mc.options.jumpKey.setPressed(false);
         return true;
      } else if (player.isOnGround()) {
         this.mc.options.jumpKey.setPressed(true);
         this.holdingJump = true;
         return false;
      } else {
         this.mc.options.jumpKey.setPressed(false);
         if (!(player.getVelocity().y <= 0.25) || this.mc.getNetworkHandler() == null) {
            return this.isGliding(player);
         } else if (PbPredict.serverPushesOnly()) {
            if (!player.input.playerInput.jump()) {
               this.mc.options.jumpKey.setPressed(true);
               this.holdingJump = true;
            }

            return false;
         } else {
            this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
            return true;
         }
      }
   }

   private boolean fireRocket(ClientPlayerEntity player) {
      if (this.mc.interactionManager != null && this.mc.getNetworkHandler() != null) {
         if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, this.nextSequence(), this.aimYaw, this.aimPitch));
            return true;
         } else {
            int fw = -1;

            for (int i = 0; i < 9; i++) {
               if (player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                  fw = i;
                  break;
               }
            }

            if (fw != -1 && !ReviveClient.SWAP.isSlotLocked(fw)) {
               int maceSlot = player.getInventory().selectedSlot;
               if (fw != maceSlot) {
                  this.switchSlot(fw);
               }

               this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, this.nextSequence(), this.aimYaw, this.aimPitch));
               if (fw != maceSlot) {
                  this.switchSlot(maceSlot);
               }

               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private int nextSequence() {
      return ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
   }

   private PlayerEntity pickTarget(ClientPlayerEntity player) {
      PlayerEntity best = null;
      double bestSq = Double.MAX_VALUE;

      for (Entity entity : this.mc.world.getEntities()) {
         if (entity != player && entity instanceof PlayerEntity p && p.isAlive() && !p.isSpectator() && !p.isRemoved() && !Friends.get().isFriend(p)) {
            double dx = p.getX() - player.getX();
            double dz = p.getZ() - player.getZ();
            double horizSq = dx * dx + dz * dz;
            if (horizSq <= this.detectionRange.get() * this.detectionRange.get() && horizSq < bestSq) {
               best = p;
               bestSq = horizSq;
            }
         }
      }

      return best;
   }

   private boolean validateTarget(ClientPlayerEntity player) {
      if (this.target != null && this.target.isAlive() && !this.target.isRemoved()) {
         double dx = this.target.getX() - player.getX();
         double dz = this.target.getZ() - player.getZ();
         double r = this.detectionRange.get() * 1.5;
         return dx * dx + dz * dz <= r * r;
      } else {
         return false;
      }
   }

   private void enterPhase(MaceDive.Phase next) {
      if (next != this.phase) {
         this.dbg(">>> %s -> %s", this.phase, next);
      }

      if (next == MaceDive.Phase.SMASH && this.phase != MaceDive.Phase.SMASH) {
         this.diveCount++;
      }

      if (next == MaceDive.Phase.DIVE) {
         this.hitThisDive = false;
         this.elytraPreSwapped = false;
         this.smashEntryY = Double.NaN;
      }

      if (next == MaceDive.Phase.SMASH && this.mc.player != null) {
         this.smashEntryY = this.mc.player.getY();
      }

      this.phase = next;
      this.ticks = 0;
      if (next == MaceDive.Phase.CLIMB || next == MaceDive.Phase.RECOVER) {
         this.rocketCooldown = 0;
         this.avoidTicks = 0;
      }

      if (next == MaceDive.Phase.CLIMB) {
         this.climbStallTicks = 0;
         this.lastClimbY = this.mc.player != null ? this.mc.player.getY() : 0.0;
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.target != null && this.target.isAlive()) {
            double x = MathHelper.lerp(event.tickDelta, this.target.lastRenderX, this.target.getX()) - this.target.getX();
            double y = MathHelper.lerp(event.tickDelta, this.target.lastRenderY, this.target.getY()) - this.target.getY();
            double z = MathHelper.lerp(event.tickDelta, this.target.lastRenderZ, this.target.getZ()) - this.target.getZ();
            Box box = this.target.getBoundingBox();
            event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, RENDER_FILL, RENDER_LINE, ShapeMode.Both, 0);
            event.renderer
               .line(
                  this.mc.player.getX(),
                  this.mc.player.getY() + 1.0,
                  this.mc.player.getZ(),
                  this.target.getX(),
                  this.target.getY() + this.target.getHeight() / 2.0,
                  this.target.getZ(),
                  this.phase != MaceDive.Phase.DIVE && this.phase != MaceDive.Phase.SMASH ? RENDER_FILL : RENDER_LINE
               );
         }
      }
   }

   private static enum Phase {
      IDLE,
      CLIMB,
      DIVE,
      SMASH,
      RECOVER;
   }
}
