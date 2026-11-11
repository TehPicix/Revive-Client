package revivedevelopment.reviveclient.systems.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.player.FindItemResult;

public class MaceAura extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("Reach to the closest point of the target's hitbox.")
            .defaultValue(3.0)
            .min(1.0)
            .max(6.0)
            .sliderRange(1.0, 6.0)
            .build()
      );
   private final Setting<Double> minFall = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-fall")
            .description("Blocks fallen before swinging. 1.5 is where the smash starts; more falling hits harder.")
            .defaultValue(1.5)
            .min(0.0)
            .max(20.0)
            .sliderRange(0.0, 20.0)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description("Aim at the target on the move packet before hitting. Off only on servers without rotation checks.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> grimVerticalHitbox = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("grim-vertical-hitbox")
            .description("Treat players' hitboxes as taller (upwards), so a smash from above connects a little earlier.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> verticalExpansion = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("vertical-expansion")
            .description("How many blocks taller the hitbox is treated as.")
            .defaultValue(2.0)
            .min(0.0)
            .max(10.0)
            .sliderRange(0.0, 10.0)
            .visible(this.grimVerticalHitbox::get)
            .build()
      );
   private final Setting<Boolean> players = this.sgTargets
      .add(new BoolSetting.Builder().name("players").description("Hit players (never friends).").defaultValue(true).build());
   private final Setting<Boolean> hostiles = this.sgTargets
      .add(new BoolSetting.Builder().name("hostiles").description("Hit hostile mobs.").defaultValue(false).build());
   private Entity target;
   private Entity staged;
   private FindItemResult stagedMace;
   private float stagedYaw;
   private float stagedPitch;
   private boolean stagedAimOnPacket;

   public MaceAura() {
      super(Categories.Combat, "mace-aura", "Automatically lands mace smash attacks on targets below you while falling.");
   }

   @Override
   public void onDeactivate() {
      this.target = null;
      this.staged = null;
      this.stagedMace = null;
   }

   @EventHandler(
      priority = 50
   )
   private void onTick(TickEvent.Pre event) {
      this.staged = null;
      this.stagedMace = null;
      this.stagedAimOnPacket = false;
      this.target = null;
      if (this.mc.player != null && this.mc.world != null && this.mc.getNetworkHandler() != null) {
         if (!this.mc.player.isDead() && !this.mc.player.isSpectator()) {
            MaceDive dive = Modules.get().get(MaceDive.class);
            if (dive == null || !dive.isActive()) {
               if (!this.mc.player.isOnGround() && !this.mc.player.isGliding() && !(this.mc.player.fallDistance < Math.max(1.5, this.minFall.get()))) {
                  FindItemResult mace = ReviveClient.SWAP.getSlot(Items.MACE);
                  if (mace.found()) {
                     Entity selected = this.findTarget();
                     if (selected != null) {
                        this.target = selected;
                        if (!(this.mc.player.getAttackCooldownProgress(0.5F) < 1.0F)) {
                           Vec3d eye = this.mc.player.getEyePos();
                           float[] aim = ReviveClient.ROTATION.getRotation(eye, closestPoint(eye, this.targetBox(selected)));
                           if (this.rotate.get()) {
                              ReviveClient.ROTATION.requestMotionRotation(aim[0], aim[1], 1000.0);
                              this.stagedAimOnPacket = true;
                           }

                           this.staged = selected;
                           this.stagedMace = mace;
                           this.stagedYaw = aim[0];
                           this.stagedPitch = aim[1];
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onSendMovementPost(SendMovementPacketsEvent.Post event) {
      Entity hit = this.staged;
      FindItemResult mace = this.stagedMace;
      boolean aimed = this.stagedAimOnPacket;
      this.staged = null;
      this.stagedMace = null;
      this.stagedAimOnPacket = false;
      if (hit != null && mace != null && this.mc.player != null && this.mc.getNetworkHandler() != null) {
         if (hit.isAlive()) {
            if (!aimed
               || !(Math.abs(MathHelper.wrapDegrees(ReviveClient.ROTATION.getServerYaw() - this.stagedYaw)) > 1.0F)
                  && !(Math.abs(ReviveClient.ROTATION.getServerPitch() - this.stagedPitch) > 1.0F)) {
               if (mace.isMainHand()) {
                  this.attack(hit);
               } else if (ReviveClient.SWAP.beginSwap(mace, true)) {
                  try {
                     this.attack(hit);
                  } finally {
                     ReviveClient.SWAP.endSwap(true);
                  }
               }
            }
         }
      }
   }

   private void attack(Entity hit) {
      this.mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(hit, this.mc.player.isSneaking()));
      this.mc.player.swingHand(Hand.MAIN_HAND);
   }

   private Entity findTarget() {
      Vec3d eye = this.mc.player.getEyePos();
      double r = this.range.get();
      double up = this.grimVerticalHitbox.get() ? this.verticalExpansion.get() : 0.0;
      Box search = this.mc.player.getBoundingBox().expand(r + 1.0, r + 1.0 + up, r + 1.0);
      Entity best = null;
      double bestDist = Double.MAX_VALUE;

      for (Entity e : this.mc.world.getOtherEntities(this.mc.player, search)) {
         if (this.validTarget(e)) {
            double d = eye.distanceTo(closestPoint(eye, this.targetBox(e)));
            if (d <= r && d < bestDist) {
               best = e;
               bestDist = d;
            }
         }
      }

      return best;
   }

   private boolean validTarget(Entity e) {
      if (!(e instanceof LivingEntity living && living.isAlive() && !living.isDead())) {
         return false;
      } else if (!(e instanceof PlayerEntity p)) {
         return this.hostiles.get() && e instanceof HostileEntity;
      } else {
         return this.players.get() && !p.isCreative() && !p.isSpectator() ? !Friends.get().isFriend(p) : false;
      }
   }

   private Box targetBox(Entity e) {
      Box box = e.getBoundingBox();
      return e instanceof PlayerEntity && this.grimVerticalHitbox.get() ? box.stretch(0.0, this.verticalExpansion.get(), 0.0) : box;
   }

   private static Vec3d closestPoint(Vec3d p, Box box) {
      if (box.contains(p)) {
         return box.getCenter();
      } else {
         double x = MathHelper.clamp(p.x, box.minX, box.maxX);
         double y = MathHelper.clamp(p.y, box.minY, box.maxY);
         double z = MathHelper.clamp(p.z, box.minZ, box.maxZ);
         x = inset(x, box.minX, box.maxX);
         y = inset(y, box.minY, box.maxY);
         z = inset(z, box.minZ, box.maxZ);
         return new Vec3d(x, y, z);
      }
   }

   private static double inset(double v, double min, double max) {
      if (Math.abs(v - min) < 1.0E-9) {
         return Math.min(min + 0.0625, max - 1.0E-9);
      } else {
         return Math.abs(v - max) < 1.0E-9 ? Math.max(max - 0.0625, min + 1.0E-9) : v;
      }
   }

   @Override
   public String getInfoString() {
      return this.target == null ? null : this.target.getName().getString();
   }
}
