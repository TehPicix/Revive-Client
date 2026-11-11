package revivedevelopment.reviveclient.systems.modules.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import revivedevelopment.reviveclient.events.render.ArmRenderEvent;
import revivedevelopment.reviveclient.events.render.HeldItemRendererEvent;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class HandView extends Module {
   private final SettingGroup sgMainHand = this.settings.createGroup("Main Hand");
   private final SettingGroup sgOffHand = this.settings.createGroup("Off Hand");
   private final SettingGroup sgArm = this.settings.createGroup("Arm");
   private final Setting<Double> scaleMain = this.sgMainHand
      .add(new DoubleSetting.Builder().name("scale").description("The scale of your main hand.").defaultValue(1.0).min(0.0).sliderRange(0.0, 5.0).build());
   private final Setting<Double> posMainX = this.sgMainHand
      .add(new DoubleSetting.Builder().name("position-x").description("The X position of your main hand.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());
   private final Setting<Double> posMainY = this.sgMainHand
      .add(new DoubleSetting.Builder().name("position-y").description("The Y position of your main hand.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());
   private final Setting<Double> posMainZ = this.sgMainHand
      .add(new DoubleSetting.Builder().name("position-z").description("The Z position of your main hand.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());
   private final Setting<Double> scaleOff = this.sgOffHand
      .add(new DoubleSetting.Builder().name("scale").description("The scale of your off hand.").defaultValue(1.0).min(0.0).sliderRange(0.0, 5.0).build());
   private final Setting<Double> posOffX = this.sgOffHand
      .add(new DoubleSetting.Builder().name("position-x").description("The X position of your off hand.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());
   private final Setting<Double> posOffY = this.sgOffHand
      .add(new DoubleSetting.Builder().name("position-y").description("The Y position of your off hand.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());
   private final Setting<Double> posOffZ = this.sgOffHand
      .add(new DoubleSetting.Builder().name("position-z").description("The Z position of your off hand.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());
   private final Setting<Double> scaleArm = this.sgArm
      .add(new DoubleSetting.Builder().name("scale").description("The scale of your arm.").defaultValue(1.0).min(0.0).sliderRange(0.0, 5.0).build());
   private final Setting<Double> posArmX = this.sgArm
      .add(new DoubleSetting.Builder().name("position-x").description("The X position of your arm.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());
   private final Setting<Double> posArmY = this.sgArm
      .add(new DoubleSetting.Builder().name("position-y").description("The Y position of your arm.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());
   private final Setting<Double> posArmZ = this.sgArm
      .add(new DoubleSetting.Builder().name("position-z").description("The Z position of your arm.").defaultValue(0.0).sliderRange(-3.0, 3.0).build());

   public HandView() {
      super(Categories.Render, "hand-view", "Alters the way items are rendered in your hands.");
   }

   @EventHandler
   private void onHeldItemRender(HeldItemRendererEvent event) {
      if (event.hand == Hand.MAIN_HAND) {
         this.scale(event.matrix, this.scaleMain.get());
         this.translate(event.matrix, this.posMainX.get(), this.posMainY.get(), this.posMainZ.get());
      } else {
         this.scale(event.matrix, this.scaleOff.get());
         this.translate(event.matrix, this.posOffX.get(), this.posOffY.get(), this.posOffZ.get());
      }
   }

   @EventHandler
   private void onRenderArm(ArmRenderEvent event) {
      this.scale(event.matrix, this.scaleArm.get());
      this.translate(event.matrix, this.posArmX.get(), this.posArmY.get(), this.posArmZ.get());
   }

   private void scale(MatrixStack matrix, double scale) {
      matrix.scale((float)scale, (float)scale, (float)scale);
   }

   private void translate(MatrixStack matrix, double x, double y, double z) {
      matrix.translate((float)x, (float)y, (float)z);
   }
}
