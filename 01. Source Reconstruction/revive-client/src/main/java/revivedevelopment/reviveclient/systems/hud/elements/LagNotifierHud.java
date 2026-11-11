package revivedevelopment.reviveclient.systems.hud.elements;

import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.world.TickRate;

public class LagNotifierHud extends HudElement {
   public static final HudElementInfo<LagNotifierHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "lag-notifier", "Displays if the server is lagging in ticks.", LagNotifierHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<Boolean> shadow = this.sgGeneral.add(new BoolSetting.Builder().name("shadow").description("Text shadow.").defaultValue(true).build());
   private final Setting<Integer> border = this.sgGeneral
      .add(new IntSetting.Builder().name("border").description("How much space to add around the element.").defaultValue(0).build());
   private final Setting<Boolean> customScale = this.sgScale
      .add(new BoolSetting.Builder().name("custom-scale").description("Applies custom text scale rather than the global one.").defaultValue(false).build());
   private final Setting<Double> scale = this.sgScale
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Custom scale.")
            .visible(this.customScale::get)
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());
   private static final Color AMBER = new Color(255, 170, 60);

   public LagNotifierHud() {
      super(INFO);
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   @Override
   public void render(HudRenderer renderer) {
      if (this.isInEditor()) {
         this.render(renderer, "4.3", AMBER);
      } else {
         float timeSinceLastTick = TickRate.INSTANCE.getTimeSinceLastTick();
         if (timeSinceLastTick >= 1.0F) {
            Color color;
            if (timeSinceLastTick > 10.0F) {
               color = HudStyle.DISABLED;
            } else if (timeSinceLastTick > 3.0F) {
               color = AMBER;
            } else {
               color = HudStyle.TEXT_PRIMARY;
            }

            this.render(renderer, String.format("%.1f", timeSinceLastTick), color);
         }
      }
   }

   private void render(HudRenderer renderer, String right, Color rightColor) {
      if (this.background.get() && this.getWidth() > 0 && this.getHeight() > 0) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
      }

      double x = this.x + this.border.get();
      double y = this.y + this.border.get();
      double x2 = renderer.text("Time since last tick ", x, y, HudStyle.TEXT_PRIMARY, this.shadow.get(), this.getScale());
      x2 = renderer.text(right, x2, y, rightColor, this.shadow.get(), this.getScale());
      this.setSize(x2 - x, renderer.textHeight(this.shadow.get(), this.getScale()));
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : -1.0;
   }
}
