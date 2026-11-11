package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.InteractBlockEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.LivingEntityAccessor;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.managers.PacketManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.player.SlotUtils;

public class ElytraTweaks extends Module {
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
   private final Setting<ElytraTweaks.TakeoffTrigger> takeoffTrigger = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("takeoff-trigger"))
                  .description("What arms the takeoff: double-tapping jump, or a dedicated keybind."))
               .defaultValue(ElytraTweaks.TakeoffTrigger.DoubleTapJump))
            .build()
      );
   private final Setting<Keybind> takeoffBind = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("takeoff-bind")
            .description("Press to arm the elytra takeoff.")
            .defaultValue(Keybind.none())
            .action(this::armTakeoffFromBind)
            .visible(() -> this.takeoffTrigger.get() == ElytraTweaks.TakeoffTrigger.Keybind)
            .build()
      );
   private final Setting<ElytraTweaks.LaunchMode> launchMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("launch-mode"))
                  .description("How the takeoff leaves the ground before the START_FALL_FLYING packet."))
               .defaultValue(ElytraTweaks.LaunchMode.JUMP))
            .build()
      );
   private final Setting<Integer> takeoffRockets = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("takeoff-rockets")
            .description("Fireworks to fire right after takeoff to get you moving. 0 = none.")
            .defaultValue(1)
            .min(0)
            .max(5)
            .sliderRange(0, 5)
            .build()
      );
   private final Setting<Integer> takeoffRocketSpacing = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("takeoff-rocket-spacing")
            .description("Ticks between the takeoff rockets.")
            .defaultValue(8)
            .min(2)
            .max(40)
            .sliderRange(2, 40)
            .visible(() -> this.takeoffRockets.get() > 0)
            .build()
      );
   private static final int TAKEOFF_DOUBLE_PRESS_WINDOW = 7;
   private static final int MASH_QUIET_TICKS = 10;
   private boolean takeoffArmed = false;
   private boolean jumpWasPressed = false;
   private int lastJumpPressTick = -100;
   private int armSuppressedUntil = -100;
   private boolean airborneWhileArmed = false;
   private int takeoffArmTick = 0;
   private int takeoffRocketsFired = 0;
   private int takeoffTicksSinceRocket = Integer.MAX_VALUE;
   private int lastFallFlyingPacketTick = -100;
   private boolean holdingJump;

   public ElytraTweaks() {
      super(Categories.Movement, "elytra-tweaks", "Double-tap jump (or a bound key) to auto-deploy your elytra and rocket-boost. Easy takeoffs on servers.");
      ElytraDurabilitySaver.get().register(this::isActive, this.saveDurability::get);
   }

   @Override
   public void onDeactivate() {
      this.releaseJump();
      this.takeoffArmed = false;
      this.jumpWasPressed = false;
      this.airborneWhileArmed = false;
      this.armSuppressedUntil = -100;
      this.takeoffRocketsFired = 0;
      this.takeoffTicksSinceRocket = Integer.MAX_VALUE;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.releaseJump();
      ClientPlayerEntity player = this.mc.player;
      if (player != null && this.mc.world != null) {
         if (this.takeoffTicksSinceRocket < Integer.MAX_VALUE) {
            this.takeoffTicksSinceRocket++;
         }

         if (this.mc.currentScreen != null) {
            this.takeoffArmed = false;
         } else {
            this.updateTakeoffArming(player);
            this.tickTakeoff(player);
         }
      } else {
         this.takeoffArmed = false;
         this.jumpWasPressed = false;
         this.airborneWhileArmed = false;
         this.armSuppressedUntil = -100;
      }
   }

   @EventHandler
   private void onInteractBlock(InteractBlockEvent event) {
      this.takeoffArmed = false;
   }

   private void updateTakeoffArming(ClientPlayerEntity player) {
      if (player.isTouchingWater()) {
         this.takeoffArmed = false;
         this.jumpWasPressed = this.mc.options.jumpKey.isPressed();
      } else {
         if (this.takeoffTrigger.get() == ElytraTweaks.TakeoffTrigger.DoubleTapJump) {
            boolean jumpDown = this.mc.options.jumpKey.isPressed();
            if (jumpDown && !this.jumpWasPressed) {
               if (!this.takeoffArmed && player.age >= this.armSuppressedUntil) {
                  if (player.age - this.lastJumpPressTick <= 7) {
                     this.takeoffArmed = true;
                     this.takeoffArmTick = player.age;
                     this.airborneWhileArmed = !player.isOnGround();
                  }
               } else {
                  this.takeoffArmed = false;
                  this.armSuppressedUntil = player.age + 10;
               }

               this.lastJumpPressTick = player.age;
            }

            this.jumpWasPressed = jumpDown;
         }

         if (this.takeoffArmed && !player.isOnGround()) {
            this.airborneWhileArmed = true;
         }

         if (this.takeoffArmed && (player.isGliding() || this.airborneWhileArmed && player.isOnGround() || player.age - this.takeoffArmTick > 40)) {
            this.takeoffArmed = false;
         }
      }
   }

   private void armTakeoffFromBind() {
      ClientPlayerEntity player = this.mc.player;
      if (player != null && !player.isTouchingWater() && this.takeoffTrigger.get() == ElytraTweaks.TakeoffTrigger.Keybind) {
         this.takeoffArmed = true;
         this.takeoffArmTick = player.age;
         this.airborneWhileArmed = !player.isOnGround();
      }
   }

   private void tickTakeoff(ClientPlayerEntity player) {
      if (!player.isTouchingWater()) {
         if (player.isGliding()) {
            if (this.takeoffRocketsFired < this.takeoffRockets.get()
               && this.takeoffTicksSinceRocket >= this.takeoffRocketSpacing.get()
               && this.fireRocket(player)) {
               this.takeoffRocketsFired++;
               this.takeoffTicksSinceRocket = 0;
            }
         } else if (this.takeoffArmed) {
            if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
               this.takeoffRocketsFired = 0;
               this.takeoffTicksSinceRocket = Integer.MAX_VALUE;
               this.primeLaunch(player);
            }
         }
      }
   }

   private void primeLaunch(ClientPlayerEntity player) {
      if (!player.isGliding()) {
         if (!player.hasVehicle() && !player.isTouchingWater()) {
            if (player.isOnGround()) {
               if (PbPredict.serverPushesOnly() && this.launchMode.get() != ElytraTweaks.LaunchMode.PACKET) {
                  this.pressJump();
                  return;
               }

               switch ((ElytraTweaks.LaunchMode)this.launchMode.get()) {
                  case JUMP:
                     ((LivingEntityAccessor)player).revive$jump();
                     return;
                  case MICROJUMP:
                     Vec3d vel = player.getVelocity();
                     player.setVelocity(vel.x, Math.max(vel.y, 0.0625), vel.z);
                     return;
                  case PACKET:
               }
            }

            if (player.isOnGround() || !(player.getVelocity().y > 0.0)) {
               if (player.age - this.lastFallFlyingPacketTick >= 4) {
                  if (PbPredict.serverPushesOnly()) {
                     if (!player.isOnGround() && !player.input.playerInput.jump()) {
                        this.lastFallFlyingPacketTick = player.age;
                        this.pressJump();
                     }
                  } else {
                     if (this.mc.getNetworkHandler() != null) {
                        this.lastFallFlyingPacketTick = player.age;
                        this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));
                     }
                  }
               }
            }
         }
      }
   }

   private void pressJump() {
      this.mc.options.jumpKey.setPressed(true);
      this.holdingJump = true;
   }

   private void releaseJump() {
      if (this.holdingJump) {
         this.holdingJump = false;
         if (this.mc.options != null) {
            this.mc.options.jumpKey.setPressed(Input.isPressed(this.mc.options.jumpKey));
         }
      }
   }

   private boolean fireRocket(ClientPlayerEntity player) {
      if (this.mc.interactionManager == null || this.mc.getNetworkHandler() == null) {
         return false;
      } else if (this.alreadyHasActiveRocket(player)) {
         return false;
      } else if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
         this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, this.nextSequence(), player.getYaw(), player.getPitch()));
         return true;
      } else {
         int src = -1;

         for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
               src = i;
               break;
            }
         }

         if (src == -1) {
            FindItemResult inv = InvUtils.find(Items.FIREWORK_ROCKET);
            if (inv.found()) {
               src = inv.slot();
            }
         }

         if (src == -1) {
            return false;
         } else if (ReviveClient.SWAP.isSlotLocked(src)) {
            return false;
         } else if (!ReviveClient.PACKET.canSend(PacketManager.Category.CLICK_SLOT)) {
            return false;
         } else {
            int containerSrc = SlotUtils.indexToId(src);
            int syncId = player.playerScreenHandler.syncId;
            this.mc.interactionManager.clickSlot(syncId, containerSrc, 40, SlotActionType.SWAP, player);
            this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, this.nextSequence(), player.getYaw(), player.getPitch()));
            this.mc.interactionManager.clickSlot(syncId, containerSrc, 40, SlotActionType.SWAP, player);
            return true;
         }
      }
   }

   private boolean alreadyHasActiveRocket(ClientPlayerEntity player) {
      if (this.mc.world == null) {
         return false;
      } else {
         for (Entity e : this.mc.world.getEntities()) {
            if (e instanceof FireworkRocketEntity r && r.getOwner() == player) {
               return true;
            }
         }

         return false;
      }
   }

   private int nextSequence() {
      return ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
   }

   public static enum LaunchMode {
      JUMP,
      MICROJUMP,
      PACKET;
   }

   public static enum TakeoffTrigger {
      DoubleTapJump,
      Keybind;
   }
}
