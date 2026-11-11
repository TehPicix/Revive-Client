package revivedevelopment.reviveclient.systems.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class AngleCalculator extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<BlockPos> to = this.sgGeneral
      .add(
         new BlockPosSetting.Builder().name("to").description("Target coordinate to travel straight towards.").defaultValue(new BlockPos(0, 64, 1000)).build()
      );
   private final Setting<Boolean> snapPitch = this.sgGeneral
      .add(new BoolSetting.Builder().name("level-pitch").description("Also set your pitch to 0 (level) when applying the angle.").defaultValue(true).build());
   private float targetYaw;

   public AngleCalculator() {
      super(Categories.Movement, "angle-calculator", "Continuously locks your view towards a target coordinate to travel straight along it (highway trails).");
   }

   @Override
   public void onActivate() {
      if (this.mc.player == null || this.mc.world == null) {
         this.toggle();
      } else if (!this.computeYaw()) {
         this.info("You are already at the target point - nothing to aim at.", new Object[0]);
         this.toggle();
      } else {
         double dx = this.to.get().getX() + 0.5 - this.mc.player.getX();
         double dz = this.to.get().getZ() + 0.5 - this.mc.player.getZ();
         double dist = Math.sqrt(dx * dx + dz * dz);
         this.info(String.format("Locking yaw to target: §a%.2f°§r  (distance §a%.1f§r blocks).", this.targetYaw, dist), new Object[0]);
         this.applyView();
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.computeYaw()) {
         this.applyView();
      }
   }

   private boolean computeYaw() {
      if (this.mc.player == null) {
         return false;
      } else {
         double dx = this.to.get().getX() + 0.5 - this.mc.player.getX();
         double dz = this.to.get().getZ() + 0.5 - this.mc.player.getZ();
         if (dx == 0.0 && dz == 0.0) {
            return false;
         } else {
            this.targetYaw = MathHelper.wrapDegrees((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            return true;
         }
      }
   }

   private void applyView() {
      if (this.mc.player != null) {
         this.mc.player.setYaw(this.targetYaw);
         if (this.snapPitch.get()) {
            this.mc.player.setPitch(0.0F);
         }

         if (this.mc.player.hasVehicle() && this.mc.player.getVehicle() != null) {
            this.mc.player.getVehicle().setYaw(this.targetYaw);
            if (this.snapPitch.get()) {
               this.mc.player.getVehicle().setPitch(0.0F);
            }
         }
      }
   }
}
