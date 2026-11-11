package revivedevelopment.reviveclient.systems.modules.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.OpenScreenEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.revive.KeyEvent;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.events.revive.MouseScrollEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

public class Freecam extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> speed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed")
            .description("Your speed while in freecam.")
            .onChanged(aDouble -> this.speedValue = aDouble)
            .defaultValue(1.0)
            .min(0.0)
            .build()
      );
   private final Setting<Double> speedScrollSensitivity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("speed-scroll-sensitivity")
            .description("Allows you to change speed value using scroll wheel. 0 to disable.")
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(2.0)
            .build()
      );
   private final Setting<Boolean> toggleOnDamage = this.sgGeneral
      .add(new BoolSetting.Builder().name("toggle-on-damage").description("Disables freecam when you take damage.").defaultValue(false).build());
   private final Setting<Boolean> toggleOnDeath = this.sgGeneral
      .add(new BoolSetting.Builder().name("toggle-on-death").description("Disables freecam when you die.").defaultValue(false).build());
   private final Setting<Boolean> toggleOnLog = this.sgGeneral
      .add(new BoolSetting.Builder().name("toggle-on-log").description("Disables freecam when you disconnect from a server.").defaultValue(true).build());
   private final Setting<Boolean> reloadChunks = this.sgGeneral
      .add(new BoolSetting.Builder().name("reload-chunks").description("Disables cave culling.").defaultValue(true).build());
   private final Setting<Boolean> renderHands = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-hands").description("Whether or not to render your hands in freecam.").defaultValue(true).build());
   private final Setting<Boolean> staticView = this.sgGeneral
      .add(new BoolSetting.Builder().name("static").description("Disables settings that move the view.").defaultValue(true).build());
   private final Setting<Boolean> interact = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("interact")
            .description("Break and place blocks at the camera's crosshair, as long as they are within your player's reach.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description("Silently rotates the player toward the block you interact with (for servers that check rotations).")
            .defaultValue(true)
            .visible(this.interact::get)
            .build()
      );
   public final Vector3d pos = new Vector3d();
   public final Vector3d prevPos = new Vector3d();
   private Perspective perspective;
   private double speedValue;
   public float yaw;
   public float pitch;
   public float lastYaw;
   public float lastPitch;
   private double fovScale;
   private boolean bobView;
   private boolean forward;
   private boolean backward;
   private boolean right;
   private boolean left;
   private boolean up;
   private boolean down;

   public Freecam() {
      super(Categories.Render, "freecam", "Allows the camera to move away from the player.");
   }

   @Override
   public void onActivate() {
      if (this.mc.player != null) {
         this.fovScale = (Double)this.mc.options.getFovEffectScale().getValue();
         this.bobView = (Boolean)this.mc.options.getBobView().getValue();
         if (this.staticView.get()) {
            this.mc.options.getFovEffectScale().setValue(0.0);
            this.mc.options.getBobView().setValue(false);
         }

         this.yaw = this.mc.player.getYaw();
         this.pitch = this.mc.player.getPitch();
         this.perspective = this.mc.options.getPerspective();
         this.speedValue = this.speed.get();
         Utils.set(this.pos, this.mc.gameRenderer.getCamera().getCameraPos());
         Utils.set(this.prevPos, this.mc.gameRenderer.getCamera().getCameraPos());
         if (this.mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            this.yaw += 180.0F;
            this.pitch *= -1.0F;
         }

         this.lastYaw = this.yaw;
         this.lastPitch = this.pitch;
         this.forward = Input.isPressed(this.mc.options.forwardKey);
         this.backward = Input.isPressed(this.mc.options.backKey);
         this.right = Input.isPressed(this.mc.options.rightKey);
         this.left = Input.isPressed(this.mc.options.leftKey);
         this.up = Input.isPressed(this.mc.options.jumpKey);
         this.down = Input.isPressed(this.mc.options.sneakKey);
         this.unpress();
         if (this.reloadChunks.get()) {
            this.mc.worldRenderer.reload();
         }
      }
   }

   @Override
   public void onDeactivate() {
      if (this.reloadChunks.get()) {
         this.mc.execute(this.mc.worldRenderer::reload);
      }

      this.mc.options.setPerspective(this.perspective);
      if (this.staticView.get()) {
         this.mc.options.getFovEffectScale().setValue(this.fovScale);
         this.mc.options.getBobView().setValue(this.bobView);
      }
   }

   @EventHandler
   private void onOpenScreen(OpenScreenEvent event) {
      this.unpress();
      this.prevPos.set(this.pos);
      this.lastYaw = this.yaw;
      this.lastPitch = this.pitch;
   }

   private void unpress() {
      this.mc.options.forwardKey.setPressed(false);
      this.mc.options.backKey.setPressed(false);
      this.mc.options.rightKey.setPressed(false);
      this.mc.options.leftKey.setPressed(false);
      this.mc.options.jumpKey.setPressed(false);
      this.mc.options.sneakKey.setPressed(false);
   }

   @EventHandler
   private void onTickPre(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (this.interact.get() && this.rotate.get()) {
            if (this.mc.options.attackKey.isPressed() || this.mc.options.useKey.isPressed()) {
               if (this.mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
                  ReviveClient.ROTATION.snapAt(hit.getPos(), true);
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.getCameraEntity() != null) {
         if (this.mc.getCameraEntity().isInsideWall()) {
            this.mc.getCameraEntity().noClip = true;
         }

         if (!this.mc.options.getPerspective().isFirstPerson()) {
            this.mc.options.setPerspective(Perspective.FIRST_PERSON);
         }

         Vec3d forwardVec = Vec3d.fromPolar(0.0F, this.yaw);
         Vec3d rightVec = Vec3d.fromPolar(0.0F, this.yaw + 90.0F);
         double velX = 0.0;
         double velY = 0.0;
         double velZ = 0.0;
         double s = Input.isPressed(this.mc.options.sprintKey) ? 1.0 : 0.5;
         boolean a = false;
         if (this.forward) {
            velX += forwardVec.x * s * this.speedValue;
            velZ += forwardVec.z * s * this.speedValue;
            a = true;
         }

         if (this.backward) {
            velX -= forwardVec.x * s * this.speedValue;
            velZ -= forwardVec.z * s * this.speedValue;
            a = true;
         }

         boolean b = false;
         if (this.right) {
            velX += rightVec.x * s * this.speedValue;
            velZ += rightVec.z * s * this.speedValue;
            b = true;
         }

         if (this.left) {
            velX -= rightVec.x * s * this.speedValue;
            velZ -= rightVec.z * s * this.speedValue;
            b = true;
         }

         if (a && b) {
            double diagonal = 1.0 / Math.sqrt(2.0);
            velX *= diagonal;
            velZ *= diagonal;
         }

         if (this.up) {
            velY += s * this.speedValue;
         }

         if (this.down) {
            velY -= s * this.speedValue;
         }

         this.prevPos.set(this.pos);
         this.pos.set(this.pos.x + velX, this.pos.y + velY, this.pos.z + velZ);
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onKey(KeyEvent event) {
      if (!Input.isKeyPressed(292)) {
         if (this.mc.currentScreen == null) {
            if (this.onInput(event.key, event.action)) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onMouseButton(MouseButtonEvent event) {
      if (this.mc.currentScreen == null) {
         if (this.onInput(event.button, event.action)) {
            event.setCancelled(true);
         }
      }
   }

   private boolean onInput(int key, KeyAction action) {
      if (Input.getKey(this.mc.options.forwardKey) == key) {
         this.forward = action != KeyAction.Release;
         this.mc.options.forwardKey.setPressed(false);
      } else if (Input.getKey(this.mc.options.backKey) == key) {
         this.backward = action != KeyAction.Release;
         this.mc.options.backKey.setPressed(false);
      } else if (Input.getKey(this.mc.options.rightKey) == key) {
         this.right = action != KeyAction.Release;
         this.mc.options.rightKey.setPressed(false);
      } else if (Input.getKey(this.mc.options.leftKey) == key) {
         this.left = action != KeyAction.Release;
         this.mc.options.leftKey.setPressed(false);
      } else if (Input.getKey(this.mc.options.jumpKey) == key) {
         this.up = action != KeyAction.Release;
         this.mc.options.jumpKey.setPressed(false);
      } else {
         if (Input.getKey(this.mc.options.sneakKey) != key) {
            return false;
         }

         this.down = action != KeyAction.Release;
         this.mc.options.sneakKey.setPressed(false);
      }

      return true;
   }

   @EventHandler(
      priority = -100
   )
   private void onMouseScroll(MouseScrollEvent event) {
      if (this.speedScrollSensitivity.get() > 0.0 && this.mc.currentScreen == null) {
         this.speedValue = this.speedValue + event.value * 0.25 * (this.speedScrollSensitivity.get() * this.speedValue);
         if (this.speedValue < 0.1) {
            this.speedValue = 0.1;
         }

         event.setCancelled(true);
      }
   }

   public boolean disablesCaveCulling() {
      return this.isActive() && this.reloadChunks.get();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      if (this.toggleOnLog.get()) {
         this.toggle();
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (event.packet instanceof DeathMessageS2CPacket packet) {
            Entity entity = this.mc.world.getEntityById(packet.playerId());
            if (entity == this.mc.player && this.toggleOnDeath.get()) {
               this.toggle();
               this.info("Toggled off because you died.", new Object[0]);
            }
         } else if (event.packet instanceof HealthUpdateS2CPacket packetx) {
            if (this.mc.player.getHealth() - packetx.getHealth() > 0.0F && this.toggleOnDamage.get()) {
               this.toggle();
               this.info("Toggled off because you took damage.", new Object[0]);
            }
         } else if (event.packet instanceof PlayerRespawnS2CPacket && this.isActive()) {
            this.toggle();
            this.info("Toggled off because you changed dimensions.", new Object[0]);
         }
      }
   }

   public void changeLookDirection(double deltaX, double deltaY) {
      this.lastYaw = this.yaw;
      this.lastPitch = this.pitch;
      this.yaw += (float)deltaX;
      this.pitch += (float)deltaY;
      this.pitch = MathHelper.clamp(this.pitch, -90.0F, 90.0F);
   }

   public boolean renderHands() {
      return !this.isActive() || this.renderHands.get();
   }

   public boolean retargetsCrosshair() {
      return this.isActive() && this.interact.get() && this.mc.player != null && this.mc.world != null;
   }

   public HitResult raycastFromCamera(float tickDelta) {
      Vec3d start = new Vec3d(this.getX(tickDelta), this.getY(tickDelta), this.getZ(tickDelta));
      Vec3d direction = Vec3d.fromPolar((float)this.getPitch(tickDelta), (float)this.getYaw(tickDelta));
      double reach = this.mc.player.getBlockInteractionRange();
      double rayLength = start.distanceTo(this.mc.player.getEyePos()) + reach + 1.0;
      BlockHitResult hit = this.mc
         .world
         .raycast(new RaycastContext(start, start.add(direction.multiply(rayLength)), ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
      return hit.getType() == Type.BLOCK && new Box(hit.getBlockPos()).squaredMagnitude(this.mc.player.getEyePos()) <= reach * reach
         ? hit
         : BlockHitResult.createMissed(hit.getPos(), hit.getSide(), hit.getBlockPos());
   }

   public double getX(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.prevPos.x, this.pos.x);
   }

   public double getY(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.prevPos.y, this.pos.y);
   }

   public double getZ(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.prevPos.z, this.pos.z);
   }

   public double getYaw(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.lastYaw, this.yaw);
   }

   public double getPitch(float tickDelta) {
      return MathHelper.lerp(tickDelta, this.lastPitch, this.pitch);
   }
}
