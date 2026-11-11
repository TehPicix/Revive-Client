package revivedevelopment.reviveclient.systems.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;

public class Follower extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("How far away a target can be.")
            .defaultValue(60.0)
            .min(0.0)
            .max(128.0)
            .sliderRange(0.0, 128.0)
            .build()
      );
   private final Setting<Follower.SortPriority> sortPriority = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("sort-priority"))
                  .description("Which enemy to chase."))
               .defaultValue(Follower.SortPriority.ClosestAngle))
            .build()
      );
   private final Setting<Boolean> friends = this.sgGeneral
      .add(new BoolSetting.Builder().name("friends").description("Follow friends too.").defaultValue(false).build());
   private final Setting<Boolean> onlyFlying = this.sgGeneral
      .add(new BoolSetting.Builder().name("only-with-elytra-fly").description("Only follow while ElytraFly is on.").defaultValue(true).build());
   private final Setting<Boolean> autoSwap = this.sgGeneral
      .add(new BoolSetting.Builder().name("auto-swap").description("Put the elytra on when there's a target.").defaultValue(true).build());
   private final Setting<Boolean> autoTakeoff = this.sgGeneral
      .add(new BoolSetting.Builder().name("auto-takeoff").description("Jump and open the elytra automatically.").defaultValue(true).build());
   private final Setting<Boolean> swapBack = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("swap-back")
            .description("Put the chestplate back on when there's no target or the module turns off.")
            .defaultValue(true)
            .build()
      );
   private static final long RETURN_MS = 300L;
   private PlayerEntity target;
   private boolean following;
   private boolean holdingForward;
   private boolean holdingJump;
   private long lastReturn;

   public Follower() {
      super(Categories.Movement, "follower", "Flies towards and dives onto the nearest enemy with your elytra.");
   }

   @Override
   public void onActivate() {
      this.lastReturn = 0L;
      this.following = false;
   }

   @Override
   public void onDeactivate() {
      this.target = null;
      this.stop();
      this.restoreChestplate();
   }

   @EventHandler(
      priority = 10
   )
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.mc.getNetworkHandler() != null) {
         this.target = this.findTarget();
         if (this.target == null) {
            this.stop();
            if (this.swapBack.get()) {
               this.restoreChestplate();
            }
         } else if (this.manualMovement()) {
            this.stop();
         } else {
            if (this.autoSwap.get() && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
               PlayerUtils.silentSwapEquipElytra();
               if (this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                  this.stop();
                  return;
               }
            }

            if (this.onlyFlying.get()) {
               ElytraFly fly = Modules.get().get(ElytraFly.class);
               if (fly == null || !fly.isActive() || this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                  this.stop();
                  return;
               }
            }

            this.following = true;
            long now = System.currentTimeMillis();
            boolean climb = now - this.lastReturn <= 300L;
            if (this.mc.player.getY() <= this.target.getY()) {
               this.lastReturn = now;
               climb = true;
            }

            if (this.mc.player.isOnGround()) {
               if (this.autoTakeoff.get()) {
                  this.mc.options.jumpKey.setPressed(true);
                  this.holdingJump = true;
               }

               this.lastReturn = now;
               climb = true;
            } else {
               this.releaseJump();
               if (this.autoTakeoff.get()
                  && !this.mc.player.isGliding()
                  && this.mc.player.getVelocity().y <= 0.25
                  && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                  if (PbPredict.serverPushesOnly()) {
                     if (!this.mc.player.input.playerInput.jump()) {
                        this.mc.options.jumpKey.setPressed(true);
                        this.holdingJump = true;
                     }
                  } else {
                     this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
                  }
               }
            }

            Vec3d eye = this.mc.player.getEyePos();
            float[] aim = ReviveClient.ROTATION.getRotation(eye, closest(eye, this.target.getBoundingBox()));
            this.mc.player.setYaw(aim[0]);
            this.mc.player.setPitch(climb ? -90.0F : aim[1]);
            this.mc.options.forwardKey.setPressed(true);
            this.holdingForward = true;
         }
      } else {
         this.target = null;
         this.stop();
      }
   }

   private void releaseJump() {
      if (this.holdingJump) {
         this.holdingJump = false;
         this.mc.options.jumpKey.setPressed(Input.isPressed(this.mc.options.jumpKey));
      }
   }

   private void stop() {
      this.following = false;
      if (this.mc.options != null) {
         this.releaseJump();
      }

      if (this.holdingForward) {
         this.holdingForward = false;
         this.mc.options.forwardKey.setPressed(Input.isPressed(this.mc.options.forwardKey));
      }
   }

   private boolean manualMovement() {
      return Input.isPressed(this.mc.options.backKey) || Input.isPressed(this.mc.options.leftKey) || Input.isPressed(this.mc.options.rightKey);
   }

   private void restoreChestplate() {
      if (this.mc.player != null && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
         PlayerUtils.silentSwapEquipChestplate();
      }
   }

   private PlayerEntity findTarget() {
      PlayerEntity best = null;

      for (PlayerEntity p : this.mc.world.getPlayers()) {
         if (p != this.mc.player
            && p.isAlive()
            && !p.isSpectator()
            && (this.friends.get() || !Friends.get().isFriend(p))
            && !(this.mc.player.distanceTo(p) > this.range.get())
            && (best == null || this.better(p, best))) {
            best = p;
         }
      }

      return best;
   }

   private boolean better(PlayerEntity a, PlayerEntity b) {
      double da = this.mc.player.distanceTo(a);
      double db = this.mc.player.distanceTo(b);

      return switch ((Follower.SortPriority)this.sortPriority.get()) {
         case ClosestAngle -> this.yawDiff(a) < this.yawDiff(b);
         case LowestDistance -> da < db;
         case LowestHealth -> {
            float ha = a.getHealth() + a.getAbsorptionAmount();
            float hb = b.getHealth() + b.getAbsorptionAmount();
            yield ha < hb || ha == hb && da < db;
         }
      };
   }

   private float yawDiff(PlayerEntity p) {
      float yaw = ReviveClient.ROTATION.getRotation(this.mc.player.getEntityPos(), p.getEntityPos())[0];
      return Math.abs(MathHelper.wrapDegrees(this.mc.player.getYaw() - yaw));
   }

   private static Vec3d closest(Vec3d p, Box box) {
      return new Vec3d(MathHelper.clamp(p.x, box.minX, box.maxX), MathHelper.clamp(p.y, box.minY, box.maxY), MathHelper.clamp(p.z, box.minZ, box.maxZ));
   }

   public boolean isFollowing() {
      return this.following;
   }

   @Override
   public String getInfoString() {
      return this.target == null ? null : this.target.getName().getString();
   }

   public static enum SortPriority {
      ClosestAngle,
      LowestDistance,
      LowestHealth;
   }
}
