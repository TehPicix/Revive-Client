package revivedevelopment.reviveclient.systems.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.entity.player.AttackEntityEvent;
import revivedevelopment.reviveclient.mixininterface.IClientWorld;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class Criticals extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Criticals.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("Grim: Lambda's packet sequence - two hair-offset position packets (falling) plus an offhand use/release before the hit."))
               .defaultValue(Criticals.Mode.Grim))
            .build()
      );
   private final Setting<Boolean> playersOnly = this.sgGeneral
      .add(new BoolSetting.Builder().name("players-only").description("Only crit hits on players (mobs are hit normally).").defaultValue(false).build());
   private final Setting<Boolean> debug = this.sgGeneral
      .add(new BoolSetting.Builder().name("debug").description("Log each crit sequence to chat.").defaultValue(false).build());

   public Criticals() {
      super(Categories.Combat, "criticals", "Forces your melee hits to be critical hits (Lambda's Grim packet sequence).");
   }

   @EventHandler(
      priority = -100
   )
   private void onAttack(AttackEntityEvent event) {
      if (!event.isCancelled()) {
         if (this.mc.player != null && this.mc.world != null && this.mc.getNetworkHandler() != null) {
            if (event.entity instanceof LivingEntity target) {
               if (!this.playersOnly.get() || target instanceof PlayerEntity) {
                  if (!this.critPossible()) {
                     this.dbg("skip: vanilla can't crit here (vehicle / ladder / water / blindness / slow falling / not falling-able)");
                  } else {
                     if (this.mode.get() == Criticals.Mode.Grim) {
                        this.sendGrim(target);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean critPossible() {
      if (this.mc.player.hasVehicle()) {
         return false;
      } else if (this.mc.player.isClimbing() || this.mc.player.isTouchingWater()) {
         return false;
      } else if (this.mc.player.hasStatusEffect(StatusEffects.BLINDNESS)) {
         return false;
      } else {
         return this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING) ? false : !this.mc.player.isGliding();
      }
   }

   private void sendGrim(LivingEntity target) {
      Vec3d pos = this.mc.player.getEntityPos();
      boolean col = this.mc.player.horizontalCollision;
      if (this.mc.player.isOnGround()) {
         this.mc.getNetworkHandler().sendPacket(new Full(pos.x, pos.y + 1.0E-8, pos.z, this.mc.player.getYaw(), this.mc.player.getPitch(), false, col));
      }

      Box box = target.getBoundingBox();
      Vec3d center = box.getCenter();
      Vec3d eye = this.mc.player.getEyePos();
      double dx = center.x - eye.x;
      double dy = center.y - eye.y;
      double dz = center.z - eye.z;
      float yaw = MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
      float pitch = MathHelper.clamp((float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))), -90.0F, 90.0F);
      this.mc.getNetworkHandler().sendPacket(new Full(pos.x, pos.y - 1.0E-9, pos.z, yaw, pitch, false, col));
      int seq = ((IClientWorld)this.mc.world).revive$getAndIncrementSequence();
      this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, seq, this.mc.player.getYaw(), this.mc.player.getPitch()));
      this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
      if (this.debug.get()) {
         this.dbg(String.format("crit on %s (%s)", target.getName().getString(), this.mc.player.isOnGround() ? "ground: +1e-8 then -1e-9" : "airborne: -1e-9"));
      }
   }

   private void dbg(String msg) {
      if (this.debug.get()) {
         ChatUtils.info("Criticals: " + msg);
      }
   }

   public static enum Mode {
      Grim;
   }
}
