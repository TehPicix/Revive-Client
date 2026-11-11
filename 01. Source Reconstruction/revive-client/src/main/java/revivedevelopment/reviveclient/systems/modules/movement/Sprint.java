package revivedevelopment.reviveclient.systems.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.PlayerTickMovementEvent;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;

public class Sprint extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate server-side to the movement direction for omni-directional speed.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> inLiquid = this.sgGeneral
      .add(new BoolSetting.Builder().name("swimming").description("Allow sprinting in liquids.").defaultValue(true).build());
   private long suppressSprintUntilMs = 0L;
   private boolean hasDesiredYaw = false;
   private float desiredYaw = 0.0F;
   private boolean swimmingActive = false;
   private boolean bypassPacketIntercept = false;
   private boolean spoofActive = false;
   private boolean stoodAsideForMoveFix = false;
   private float prevMovementYaw = Float.NaN;
   private boolean settling = false;
   private PlayerInput lastSentInput = null;
   private int elytraStandDown = 0;
   private static final int ELYTRA_STAND_DOWN_TICKS = 10;

   public Sprint() {
      super(Categories.Movement, "sprint", "Makes you sprint at all times.");
   }

   public static Sprint get() {
      return Modules.get().get(Sprint.class);
   }

   @Override
   public void onActivate() {
      this.lastSentInput = null;
   }

   @Override
   public void onDeactivate() {
      ReviveClient.ROTATION.release("sprint");
      this.stoodAsideForMoveFix = false;
      this.elytraStandDown = 0;
      this.hasDesiredYaw = false;
      this.prevMovementYaw = Float.NaN;
      this.settling = false;
      this.swimmingActive = false;
      this.spoofActive = false;
      if (this.mc.player != null
         && this.mc.getNetworkHandler() != null
         && this.lastSentInput != null
         && !this.lastSentInput.equals(this.mc.player.input.playerInput)) {
         this.bypassPacketIntercept = true;
         this.mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(this.mc.player.input.playerInput));
         this.bypassPacketIntercept = false;
      }

      this.lastSentInput = null;
   }

   public void stopSprinting(int ticks) {
      this.suppressSprintUntilMs = Math.max(this.suppressSprintUntilMs, System.currentTimeMillis() + ticks * 50L);
   }

   public boolean isSprintSuppressed() {
      return System.currentTimeMillis() < this.suppressSprintUntilMs;
   }

   public boolean shouldSuppressMovementInput() {
      return this.isActive() && this.spoofActive && this.settling;
   }

   public boolean isMotionSpoofActive() {
      return this.isActive() && this.spoofActive;
   }

   public boolean hasDesiredRotation() {
      return this.isActive() && this.rotate.get() && this.hasDesiredYaw;
   }

   public float getDesiredYaw() {
      return this.desiredYaw;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         if (this.elytraDriverActive()) {
            this.elytraStandDown = 10;
         } else if (this.elytraStandDown > 0) {
            this.elytraStandDown--;
         }

         this.updateRotationState();
         if (this.isSprintSuppressed()) {
            player.setSprinting(false);
         } else {
            if (!this.standingDown() && !this.screenOpen() && this.sprintLegalNow() && this.hasSprintInput()) {
               player.setSprinting(true);
            }

            if (this.rotate.get() && this.hasDesiredYaw) {
               player.bodyYaw = this.desiredYaw;
               player.headYaw = this.desiredYaw;
            }
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onPlayerTickMovement(PlayerTickMovementEvent event) {
      this.reassertSpoofIfMoveFixLost();
   }

   @EventHandler(
      priority = 201
   )
   private void onSendMovementPacketsEarly(SendMovementPacketsEvent.Pre event) {
      this.reassertSpoofIfMoveFixLost();
   }

   private void reassertSpoofIfMoveFixLost() {
      if (this.stoodAsideForMoveFix) {
         if (!ReviveClient.ROTATION.moveFixActive()) {
            this.stoodAsideForMoveFix = false;
            if (this.mc.player != null) {
               this.updateRotationState();
            }
         }
      }
   }

   @EventHandler
   private void onSendMovementPackets(SendMovementPacketsEvent.Pre event) {
      this.stoodAsideForMoveFix = false;
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         if (!this.isSprintSuppressed() && !this.standingDown() && !this.screenOpen() && this.sprintLegalNow() && this.hasSprintInput()) {
            player.setSprinting(true);
         }

         if (this.mc.getNetworkHandler() != null) {
            if (!ElytraDurabilitySaver.get().ownsServerInput()) {
               PlayerInput desired = this.desiredServerInput(player.input.playerInput, player);
               if (!desired.equals(this.lastSentInput)) {
                  this.sendInputPacket(desired);
               }
            }
         }
      }
   }

   public boolean forceSprint() {
      return this.isActive() && !this.isSprintSuppressed() && !this.standingDown() && !this.screenOpen() && this.sprintLegalNow() && this.hasSprintInput();
   }

   private boolean standingDown() {
      return this.elytraStandDown > 0;
   }

   private boolean elytraDriverActive() {
      ClientPlayerEntity player = this.mc.player;
      return player != null && player.isGliding();
   }

   private boolean hasSprintInput() {
      if (this.moveFixBlocksSprint()) {
         return false;
      } else if (this.isMoving()) {
         return true;
      } else {
         ClientPlayerEntity player = this.mc.player;
         return player != null && player.isTouchingWater() && (this.mc.options.jumpKey.isPressed() || this.mc.options.sneakKey.isPressed());
      }
   }

   private boolean moveFixBlocksSprint() {
      if (!ReviveClient.ROTATION.moveFixActive()) {
         return false;
      } else {
         ClientPlayerEntity player = this.mc.player;
         return player == null || !player.input.playerInput.forward();
      }
   }

   private boolean screenOpen() {
      return this.mc.currentScreen != null;
   }

   private boolean sprintLegalNow() {
      ClientPlayerEntity player = this.mc.player;
      return player == null ? true : !player.isTouchingWater() || player.isSwimming() || player.isSubmergedInWater();
   }

   private void updateRotationState() {
      ClientPlayerEntity player = this.mc.player;
      this.stoodAsideForMoveFix = false;
      if (player == null || !this.isActive() || !this.rotate.get()) {
         this.cancelRotation();
      } else if (this.standingDown()) {
         this.cancelRotation();
      } else if (ReviveClient.ROTATION.moveFixActive()) {
         this.cancelRotation();
         this.stoodAsideForMoveFix = true;
      } else if (player.isCrawling() || player.isSneaking() && !player.isTouchingWater() || player.isGliding()) {
         this.cancelRotation();
      } else if (!this.inLiquid.get() && player.isTouchingWater()) {
         this.cancelRotation();
      } else if (this.isSprintSuppressed()) {
         this.cancelRotation();
      } else if (!player.isTouchingWater() || !player.isSwimming() && !player.isSubmergedInWater()) {
         this.swimmingActive = false;
         if (!this.isMoving() || player.hasVehicle()) {
            this.cancelRotation();
         } else if (this.mc.options.forwardKey.isPressed() == this.mc.options.backKey.isPressed()
            && this.mc.options.leftKey.isPressed() == this.mc.options.rightKey.isPressed()) {
            this.cancelRotation();
         } else {
            float yaw = this.computeMovementYaw(player);
            this.desiredYaw = yaw;
            this.hasDesiredYaw = true;
            boolean pressForward = this.mc.options.forwardKey.isPressed();
            boolean pressBack = this.mc.options.backKey.isPressed();
            if (pressForward && !pressBack) {
               ReviveClient.ROTATION.release("sprint");
               this.spoofActive = false;
               this.prevMovementYaw = yaw;
               this.settling = false;
            } else {
               float yawDelta = Float.isNaN(this.prevMovementYaw) ? 0.0F : Math.abs(MathHelper.wrapDegrees(yaw - this.prevMovementYaw));
               this.settling = yawDelta > 45.0F;
               this.prevMovementYaw = yaw;
               float pitch = player.getPitch();
               this.spoofActive = true;
               ReviveClient.ROTATION.requestMotionRotation(yaw, pitch, 2000.0);
            }
         }
      } else {
         this.updateSwimRotation(player);
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onPacketSend(PacketEvent.Send event) {
      if (!this.bypassPacketIntercept && !event.isCancelled()) {
         if (this.isActive()) {
            if (event.packet instanceof PlayerInputC2SPacket pkt) {
               if (!ElytraDurabilitySaver.get().ownsServerInput()) {
                  ClientPlayerEntity player = this.mc.player;
                  if (player != null && this.mc.getNetworkHandler() != null) {
                     PlayerInput desired = this.desiredServerInput(pkt.input(), player);
                     if (pkt.input().equals(desired)) {
                        this.lastSentInput = pkt.input();
                     } else {
                        event.setCancelled(true);
                        this.sendInputPacket(desired);
                     }
                  }
               }
            }
         }
      }
   }

   private PlayerInput desiredServerInput(PlayerInput real, ClientPlayerEntity player) {
      Scaffold scaffold = Modules.get().get(Scaffold.class);
      if (scaffold != null && scaffold.suppressesMovementInput()) {
         return new PlayerInput(false, false, false, false, real.jump(), real.sneak(), false);
      } else if (!this.screenOpen() && !this.standingDown()) {
         boolean sprintBit = real.sprint() || !this.isSprintSuppressed() && this.sprintLegalNow() && this.hasSprintInput();
         if (this.rotate.get() && this.spoofActive && !player.isGliding()) {
            boolean fwd = !this.swimmingActive && !this.settling;
            return new PlayerInput(fwd, false, false, false, real.jump(), real.sneak(), sprintBit);
         } else {
            return new PlayerInput(real.forward(), real.backward(), real.left(), real.right(), real.jump(), real.sneak(), sprintBit);
         }
      } else {
         return real;
      }
   }

   private void sendInputPacket(PlayerInput input) {
      this.bypassPacketIntercept = true;
      this.mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(input));
      this.bypassPacketIntercept = false;
      this.lastSentInput = input;
   }

   private float computeMovementYaw(ClientPlayerEntity player) {
      boolean forward = this.mc.options.forwardKey.isPressed();
      boolean back = this.mc.options.backKey.isPressed();
      boolean left = this.mc.options.leftKey.isPressed();
      boolean right = this.mc.options.rightKey.isPressed();
      float forwardInput = (forward ? 1.0F : 0.0F) - (back ? 1.0F : 0.0F);
      float sidewaysInput = (left ? 1.0F : 0.0F) - (right ? 1.0F : 0.0F);
      if (forwardInput == 0.0F && sidewaysInput == 0.0F) {
         return player.getYaw();
      } else {
         float cameraYaw = player.getYaw();
         double yawRad = Math.toRadians(cameraYaw);
         double sin = Math.sin(yawRad);
         double cos = Math.cos(yawRad);
         double worldX = sidewaysInput * cos - forwardInput * sin;
         double worldZ = forwardInput * cos + sidewaysInput * sin;
         float raw = (float)(Math.toDegrees(Math.atan2(worldZ, worldX)) - 90.0);
         float diff = raw - cameraYaw;
         diff = (diff % 360.0F + 540.0F) % 360.0F - 180.0F;
         return cameraYaw + diff;
      }
   }

   private void cancelRotation() {
      if (this.hasDesiredYaw) {
         ReviveClient.ROTATION.release("sprint");
         this.hasDesiredYaw = false;
         this.prevMovementYaw = Float.NaN;
         this.settling = false;
      }

      this.spoofActive = false;
   }

   private boolean isMoving() {
      return this.mc.options.forwardKey.isPressed()
         || this.mc.options.backKey.isPressed()
         || this.mc.options.leftKey.isPressed()
         || this.mc.options.rightKey.isPressed();
   }

   private void updateSwimRotation(ClientPlayerEntity player) {
      this.settling = false;
      this.prevMovementYaw = Float.NaN;
      GameOptions opts = this.mc.options;
      boolean pressJump = opts.jumpKey.isPressed();
      boolean pressSneak = opts.sneakKey.isPressed();
      boolean pressW = opts.forwardKey.isPressed();
      boolean pressA = opts.leftKey.isPressed();
      boolean pressS = opts.backKey.isPressed();
      boolean pressD = opts.rightKey.isPressed();
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
      boolean hasVertical = pressJump || pressSneak;
      if (!hasHorizontal && !hasVertical) {
         this.swimmingActive = false;
         this.cancelRotation();
      } else {
         player.setSprinting(true);
         boolean verticalOnly = hasVertical && !hasHorizontal;
         this.swimmingActive = verticalOnly;
         float yawBase = player.getYaw();
         float yaw = yawBase;
         if (hasHorizontal) {
            double angle = Math.toDegrees(Math.atan2(dirX, dirZ));
            yaw = MathHelper.wrapDegrees(yawBase + (float)angle);
         }

         this.desiredYaw = yaw;
         this.hasDesiredYaw = true;
         if (pressW && !pressS && !pressA && !pressD && !hasVertical) {
            ReviveClient.ROTATION.release("sprint");
            this.spoofActive = false;
         } else {
            this.spoofActive = true;
            ReviveClient.ROTATION.requestMotionRotation(yaw, player.getPitch(), 2000.0);
         }
      }
   }
}
