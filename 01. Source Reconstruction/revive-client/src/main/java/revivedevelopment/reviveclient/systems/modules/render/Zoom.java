package revivedevelopment.reviveclient.systems.modules.render;

import meteordevelopment.orbit.EventHandler;
import revivedevelopment.reviveclient.events.render.GetFovEvent;
import revivedevelopment.reviveclient.events.revive.MouseScrollEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class Zoom extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> zoomLevel = this.sgGeneral
      .add(new DoubleSetting.Builder().name("zoom").description("How much to magnify by default.").defaultValue(4.0).min(1.0).sliderRange(1.0, 50.0).build());
   private final Setting<Double> scrollSensitivity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scroll-sensitivity")
            .description("How fast scrolling changes the magnification. 0 disables scroll zoom.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderRange(0.0, 5.0)
            .build()
      );
   private final Setting<Boolean> smooth = this.sgGeneral
      .add(new BoolSetting.Builder().name("smooth").description("Eases the FOV in and out instead of snapping.").defaultValue(true).build());
   private final Setting<Boolean> cinematic = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("cinematic-camera")
            .description("Enables the vanilla cinematic camera while zoomed for smoother aiming.")
            .defaultValue(true)
            .build()
      );
   private double zoom;
   private double multiplier = 1.0;
   private boolean wasCinematic;

   public Zoom() {
      super(Categories.Render, "zoom", "Zooms your view like a spyglass, without one.");
      this.chatFeedback = false;
   }

   @Override
   public void onActivate() {
      this.zoom = this.zoomLevel.get();
      this.multiplier = 1.0;
      this.wasCinematic = this.mc.options.smoothCameraEnabled;
      if (this.cinematic.get()) {
         this.mc.options.smoothCameraEnabled = true;
      }
   }

   @Override
   public void onDeactivate() {
      if (this.cinematic.get()) {
         this.mc.options.smoothCameraEnabled = this.wasCinematic;
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onMouseScroll(MouseScrollEvent event) {
      if (!(this.scrollSensitivity.get() <= 0.0) && this.mc.currentScreen == null) {
         this.zoom = Math.max(1.0, this.zoom * Math.pow(1.1, event.value * this.scrollSensitivity.get()));
         event.cancel();
      }
   }

   @EventHandler
   private void onGetFov(GetFovEvent event) {
      double target = 1.0 / this.zoom;
      if (this.smooth.get()) {
         this.multiplier = this.multiplier + (target - this.multiplier) * 0.4;
      } else {
         this.multiplier = target;
      }

      event.fov = event.fov * (float)this.multiplier;
   }
}
