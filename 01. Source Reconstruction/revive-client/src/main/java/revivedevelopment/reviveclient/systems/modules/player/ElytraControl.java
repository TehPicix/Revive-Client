package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.PlayerTickMovementEvent;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
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
import revivedevelopment.reviveclient.systems.modules.render.Freecam;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.SlotUtils;

public class ElytraControl extends Module {
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
   private final Setting<ElytraControl.RotationMode> rotation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("rotation"))
                  .description(
                     "Motion: server-side rotation spoof (camera stays on your mouse; server drives the glide). Physics: set the real rotation around travel (no rubberband, but the flip wobble shows as drift)."
                  ))
               .defaultValue(ElytraControl.RotationMode.Motion))
            .build()
      );
   private final Setting<ElytraControl.ControlMode> controlMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("control-mode"))
                  .description("Snap steers instantly; Smooth eases the glide rotation."))
               .defaultValue(ElytraControl.ControlMode.Snap))
            .build()
      );
   private final Setting<ElytraControl.IdleMode> idleMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("idle-mode"))
                  .description(
                     "Hover: yaw-flip to cancel forward thrust (slight wobble). Stop: also zero horizontal velocity for a harder stop. Freeze: a stop-motion-style blink - pin you in place and drop outgoing move packets (cleanest park, but it's a blink exploit AC can flag)."
                  ))
               .defaultValue(ElytraControl.IdleMode.Hover))
            .build()
      );
   private final Setting<Integer> freezeResync = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("freeze-resync")
            .description(
               "While Freeze holds, let one keepalive position packet (reporting the pinned spot) through this often (ms) so a long hold doesn't trip a no-movement timeout. Lower = safer for long holds."
            )
            .defaultValue(750)
            .min(100)
            .max(5000)
            .sliderRange(100, 5000)
            .visible(() -> this.idleMode.get() == ElytraControl.IdleMode.Freeze)
            .build()
      );
   private final Setting<Double> hoverPitch = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("hover-pitch")
            .description("Pitch used while hovering (no movement keys). Near 0 keeps you level.")
            .defaultValue(-1.1)
            .min(-10.0)
            .max(10.0)
            .sliderRange(-10.0, 10.0)
            .build()
      );
   private final Setting<Boolean> freecamSuppress = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("freecam-suppress")
            .description("Don't steer the elytra from movement keys while Freecam is active.")
            .defaultValue(true)
            .build()
      );
   private final Setting<ElytraControl.RocketMode> rocketMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("rockets"))
                  .description(
                     "Fire fireworks while flying (only when a movement key is held). Automatic keeps one rocket always active; Static fires on a fixed delay."
                  ))
               .defaultValue(ElytraControl.RocketMode.Off))
            .build()
      );
   private final Setting<Integer> rocketDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("rocket-delay")
            .description("Fixed delay between rockets (ms).")
            .defaultValue(2250)
            .min(100)
            .max(10000)
            .sliderRange(100, 10000)
            .visible(() -> this.rocketMode.get() == ElytraControl.RocketMode.Static)
            .build()
      );
   private final Setting<Integer> retryDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("retry-delay")
            .description("Cooldown after firing before another rocket can go (ms).")
            .defaultValue(200)
            .min(50)
            .max(5000)
            .sliderRange(50, 5000)
            .visible(() -> this.rocketMode.get() != ElytraControl.RocketMode.Off)
            .build()
      );
   private final Setting<Boolean> offhand = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("offhand")
            .description("Use rockets from the offhand (swap in if needed).")
            .defaultValue(true)
            .visible(() -> this.rocketMode.get() != ElytraControl.RocketMode.Off)
            .build()
      );
   private long lastRocket = 0L;
   private long rocketSuppressUntil = 0L;
   private boolean applied;
   private float savedYaw;
   private float savedPitch;
   private float flightYaw;
   private float flightPitch;
   private boolean freezeActive;
   private Vec3d freezeAnchor;
   private long lastFreezePacket;
   private boolean smoothPrimed;
   private float smoothYaw;
   private float smoothPitch;
   private int idleTickPhase;

   public ElytraControl() {
      super(Categories.Movement, "elytra-control", "Steer your glide with movement keys and hover (cancel forward drift) when idle.");
      ElytraDurabilitySaver.get().register(this::isActive, this.saveDurability::get);
   }

   @Override
   public void onDeactivate() {
      if (this.applied) {
         this.restore();
      }

      this.unfreeze();
      this.smoothPrimed = false;
      this.idleTickPhase = 0;
      this.lastRocket = 0L;
      this.rocketSuppressUntil = 0L;
   }

   private void unfreeze() {
      this.freezeActive = false;
      this.freezeAnchor = null;
   }

   public boolean isFrozen() {
      return this.isActive() && this.freezeActive && this.freezeAnchor != null;
   }

   public void onCorrected(Vec3d offset) {
      if (this.freezeAnchor != null) {
         this.freezeAnchor = this.freezeAnchor.add(offset);
      }
   }

   @EventHandler
   private void onTickMovement(PlayerTickMovementEvent event) {
      if (this.applied) {
         this.restore();
      }

      ClientPlayerEntity player = this.mc.player;
      if (player == null || this.mc.world == null) {
         this.unfreeze();
      } else if (player.isGliding() && !player.isTouchingWater() && !player.isInLava()) {
         boolean suppress = this.freecamSuppress.get() && this.isFreecamActive();
         boolean pressJump = !suppress && this.mc.options.jumpKey.isPressed();
         boolean pressSneak = !suppress && this.mc.options.sneakKey.isPressed();
         boolean pressW = !suppress && this.mc.options.forwardKey.isPressed();
         boolean pressA = !suppress && this.mc.options.leftKey.isPressed();
         boolean pressS = !suppress && this.mc.options.backKey.isPressed();
         boolean pressD = !suppress && this.mc.options.rightKey.isPressed();
         boolean anyKey = pressJump || pressSneak || pressW || pressA || pressS || pressD;
         float yawBase = player.getYaw();
         float pitchBase = player.getPitch();
         float dirX = 0.0F;
         float dirZ = 0.0F;
         if (pressW) {
            dirZ++;
         }

         if (pressS) {
            dirZ--;
         }

         if (pressA) {
            dirX--;
         }

         if (pressD) {
            dirX++;
         }

         boolean hasHorizontal = dirX != 0.0F || dirZ != 0.0F;
         float glideYaw;
         float glidePitchVal;
         if (!anyKey) {
            if (this.idleMode.get() == ElytraControl.IdleMode.Freeze) {
               if (!this.freezeActive) {
                  this.freezeActive = true;
                  this.freezeAnchor = player.getEntityPos();
                  this.lastFreezePacket = System.currentTimeMillis();
               }

               if (this.applied) {
                  this.restore();
               }

               this.idleTickPhase = 0;
               this.smoothPrimed = false;
               return;
            }

            this.unfreeze();
            boolean lookBack = this.idleTickPhase++ % 2 == 1;
            glideYaw = lookBack ? MathHelper.wrapDegrees(yawBase + 180.0F) : yawBase;
            glidePitchVal = this.hoverPitch.get().floatValue();
            this.smoothPrimed = false;
            if (this.idleMode.get() == ElytraControl.IdleMode.Stop) {
               Vec3d v = player.getVelocity();
               player.setVelocity(0.0, v.y, 0.0);
            }
         } else {
            this.unfreeze();
            this.idleTickPhase = 0;
            glidePitchVal = pressJump ? (hasHorizontal ? -45.0F : -90.0F) : (pressSneak ? (hasHorizontal ? 45.0F : 90.0F) : pitchBase);
            glideYaw = yawBase;
            if (hasHorizontal) {
               double angle = Math.toDegrees(Math.atan2(dirX, dirZ));
               glideYaw = MathHelper.wrapDegrees(yawBase + (float)angle);
            }

            if (this.controlMode.get() == ElytraControl.ControlMode.Smooth) {
               if (!this.smoothPrimed) {
                  this.smoothYaw = yawBase;
                  this.smoothPitch = pitchBase;
                  this.smoothPrimed = true;
               }

               this.smoothYaw = MathHelper.wrapDegrees(this.smoothYaw + MathHelper.wrapDegrees(glideYaw - this.smoothYaw) * 0.35F);
               this.smoothPitch = MathHelper.clamp(this.smoothPitch + (glidePitchVal - this.smoothPitch) * 0.35F, -90.0F, 90.0F);
               glideYaw = this.smoothYaw;
               glidePitchVal = this.smoothPitch;
            } else {
               this.smoothPrimed = false;
               this.smoothYaw = glideYaw;
               this.smoothPitch = glidePitchVal;
            }
         }

         this.flightYaw = glideYaw;
         this.flightPitch = MathHelper.clamp(glidePitchVal, -90.0F, 90.0F);
         if (this.rotation.get() == ElytraControl.RotationMode.Motion) {
            ReviveClient.ROTATION.requestMotionRotation(this.flightYaw, this.flightPitch);
            this.applied = false;
         } else {
            this.savedYaw = yawBase;
            this.savedPitch = pitchBase;
            player.setYaw(this.flightYaw);
            player.setPitch(this.flightPitch);
            this.applied = true;
         }

         if (anyKey && this.rocketMode.get() != ElytraControl.RocketMode.Off && !PbPredict.serverPushesOnly() && this.shouldRocket(player)) {
            this.fireRocket(player);
         }
      } else {
         this.unfreeze();
         this.smoothPrimed = false;
         this.idleTickPhase = 0;
      }
   }

   @EventHandler
   private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
      if (this.freezeActive && this.freezeAnchor != null && this.mc.player != null) {
         this.mc.player.setPosition(this.freezeAnchor.x, this.freezeAnchor.y, this.freezeAnchor.z);
         this.mc.player.setVelocity(0.0, 0.0, 0.0);
      }
   }

   @EventHandler
   private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
      if (this.applied) {
         this.restore();
      }
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (this.freezeActive && event.packet instanceof PlayerMoveC2SPacket && !PbPredict.serverPushesOnly()) {
         long now = System.currentTimeMillis();
         if (now - this.lastFreezePacket >= this.freezeResync.get().intValue()) {
            this.lastFreezePacket = now;
         } else {
            event.setCancelled(true);
         }
      }
   }

   private void restore() {
      if (this.mc.player != null) {
         this.mc.player.setYaw(this.savedYaw);
         this.mc.player.setPitch(this.savedPitch);
      }

      this.applied = false;
   }

   private boolean isFreecamActive() {
      Freecam freecam = Modules.get().get(Freecam.class);
      return freecam != null && freecam.isActive();
   }

   private boolean hasActiveRocket(ClientPlayerEntity player) {
      for (Entity e : this.mc.world.getEntities()) {
         if (e instanceof FireworkRocketEntity r && r.getOwner() == player) {
            return true;
         }
      }

      return false;
   }

   private boolean shouldRocket(ClientPlayerEntity player) {
      long now = System.currentTimeMillis();
      if ((player.isTouchingWater() || player.isInLava()) && player.getPose() != EntityPose.GLIDING) {
         return false;
      } else if (now < this.rocketSuppressUntil) {
         return false;
      } else {
         long since = now - this.lastRocket;
         if (this.rocketMode.get() == ElytraControl.RocketMode.Static) {
            return since > this.rocketDelay.get().intValue();
         } else {
            return this.hasActiveRocket(player) ? false : since > this.retryDelay.get().intValue();
         }
      }
   }

   private int findRocketInHotbar(ClientPlayerEntity player) {
      for (int i = 0; i < 9; i++) {
         if (player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
            return i;
         }
      }

      return -1;
   }

   private void fireRocket(ClientPlayerEntity player) {
      if (this.mc.interactionManager != null && this.mc.getNetworkHandler() != null) {
         ElytraDurabilitySaver saver = ElytraDurabilitySaver.get();
         if (player.isGliding() && !saver.serverGlideSteady()) {
            saver.holdForRocket();
         } else {
            int hot = this.findRocketInHotbar(player);
            FindItemResult invResult = InvUtils.find(Items.FIREWORK_ROCKET);
            boolean offhandHasRocket = player.getOffHandStack().isOf(Items.FIREWORK_ROCKET);
            if (this.offhand.get()) {
               if (offhandHasRocket || hot != -1 || invResult.found()) {
                  if (!offhandHasRocket) {
                     int rocketSrc = hot != -1 ? hot : invResult.slot();
                     if (ReviveClient.SWAP.isSlotLocked(rocketSrc)) {
                        return;
                     }
                  }

                  this.lastRocket = System.currentTimeMillis();
                  if (offhandHasRocket) {
                     this.sendUse(player, Hand.OFF_HAND);
                  } else {
                     int src = hot != -1 ? hot : invResult.slot();
                     int containerSrc = SlotUtils.indexToId(src);
                     int syncId = player.playerScreenHandler.syncId;
                     this.click(syncId, containerSrc, 40, SlotActionType.SWAP);
                     this.sendUse(player, Hand.OFF_HAND);
                     this.click(syncId, containerSrc, 40, SlotActionType.SWAP);
                  }

                  this.rocketSuppressUntil = System.currentTimeMillis() + this.retryDelay.get().intValue();
                  saver.rocketUsed();
               }
            } else if (hot != -1 || invResult.found()) {
               int selected = player.getInventory().getSelectedSlot();
               if (hot != selected) {
                  int rocketSrc = hot != -1 ? hot : invResult.slot();
                  if (ReviveClient.SWAP.isSlotLocked(rocketSrc)) {
                     return;
                  }
               }

               this.lastRocket = System.currentTimeMillis();
               if (hot == selected) {
                  this.sendUse(player, Hand.MAIN_HAND);
               } else {
                  int src = hot != -1 ? hot : invResult.slot();
                  int containerSrc = SlotUtils.indexToId(src);
                  int syncId = player.playerScreenHandler.syncId;
                  this.click(syncId, containerSrc, selected, SlotActionType.SWAP);
                  this.sendUse(player, Hand.MAIN_HAND);
                  this.click(syncId, containerSrc, selected, SlotActionType.SWAP);
               }

               this.rocketSuppressUntil = System.currentTimeMillis() + this.retryDelay.get().intValue();
               saver.rocketUsed();
            }
         }
      }
   }

   private void sendUse(ClientPlayerEntity player, Hand hand) {
      this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, this.nextSequence(), this.flightYaw, this.flightPitch));
   }

   private void click(int syncId, int slotId, int button, SlotActionType action) {
      this.mc.interactionManager.clickSlot(syncId, slotId, button, action, this.mc.player);
   }

   private int nextSequence() {
      return ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
   }

   public static enum ControlMode {
      Smooth,
      Snap;
   }

   public static enum IdleMode {
      Hover,
      Stop,
      Freeze;
   }

   public static enum RocketMode {
      Off,
      Automatic,
      Static;
   }

   public static enum RotationMode {
      Motion,
      Physics;
   }
}
