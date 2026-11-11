package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class Rotation extends Module {
   private final SettingGroup sgYaw = this.settings.createGroup("Yaw");
   private final SettingGroup sgPitch = this.settings.createGroup("Pitch");
   private final Setting<Rotation.LockMode> yawLockMode = this.sgYaw
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("yaw-lock-mode"))
                  .description("The way in which your yaw is locked."))
               .defaultValue(Rotation.LockMode.Simple))
            .build()
      );
   private final Setting<Double> yawAngle = this.sgYaw
      .add(
         new DoubleSetting.Builder()
            .name("yaw-angle")
            .description("Yaw angle in degrees.")
            .defaultValue(0.0)
            .sliderMax(360.0)
            .max(360.0)
            .visible(() -> this.yawLockMode.get() == Rotation.LockMode.Simple)
            .build()
      );
   private final Setting<Rotation.LockMode> pitchLockMode = this.sgPitch
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("pitch-lock-mode"))
                  .description("The way in which your pitch is locked."))
               .defaultValue(Rotation.LockMode.Simple))
            .build()
      );
   private final Setting<Double> pitchAngle = this.sgPitch
      .add(
         new DoubleSetting.Builder()
            .name("pitch-angle")
            .description("Pitch angle in degrees.")
            .defaultValue(0.0)
            .range(-90.0, 90.0)
            .sliderRange(-90.0, 90.0)
            .visible(() -> this.pitchLockMode.get() == Rotation.LockMode.Simple)
            .build()
      );

   public Rotation() {
      super(Categories.Player, "rotation", "Changes/locks your yaw and pitch.");
   }

   @Override
   public void onActivate() {
      this.onTick(null);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      switch ((Rotation.LockMode)this.yawLockMode.get()) {
         case Smart:
            this.setYawAngle(this.getSmartYawDirection());
            break;
         case Simple:
            this.setYawAngle(this.yawAngle.get().floatValue());
      }

      switch ((Rotation.LockMode)this.pitchLockMode.get()) {
         case Smart:
            this.mc.player.setPitch(this.getSmartPitchDirection());
            break;
         case Simple:
            this.mc.player.setPitch(this.pitchAngle.get().floatValue());
      }
   }

   private float getSmartYawDirection() {
      return Math.round((this.mc.player.getYaw() + 1.0F) / 45.0F) * 45.0F;
   }

   private float getSmartPitchDirection() {
      return Math.round((this.mc.player.getPitch() + 1.0F) / 30.0F) * 30.0F;
   }

   private void setYawAngle(float yawAngle) {
      this.mc.player.setYaw(yawAngle);
      this.mc.player.headYaw = yawAngle;
      this.mc.player.bodyYaw = yawAngle;
   }

   public static enum LockMode {
      Smart,
      Simple,
      None;
   }
}
