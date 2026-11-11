package revivedevelopment.reviveclient.systems.hud.elements;

import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;

public class CompassHud extends HudElement {
   public static final HudElementInfo<CompassHud> INFO = new HudElementInfo<>(Hud.GROUP, "compass", "Displays a compass.", CompassHud::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTextScale = this.settings.createGroup("Text Scale");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<CompassHud.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("type"))
                  .description("Which type of direction information to show."))
               .defaultValue(CompassHud.Mode.Axis))
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(1.0)
            .min(1.0)
            .sliderRange(1.0, 5.0)
            .onChanged(aDouble -> this.calculateSize())
            .build()
      );
   private final Setting<Boolean> shadow = this.sgGeneral.add(new BoolSetting.Builder().name("shadow").description("Text shadow.").defaultValue(false).build());
   private final Setting<Integer> border = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("border")
            .description("How much space to add around the element.")
            .defaultValue(0)
            .onChanged(integer -> this.calculateSize())
            .build()
      );
   private final Setting<Boolean> customTextScale = this.sgTextScale
      .add(new BoolSetting.Builder().name("custom-text-scale").description("Applies custom text scale rather than the global one.").defaultValue(false).build());
   private final Setting<Double> textScale = this.sgTextScale
      .add(
         new DoubleSetting.Builder()
            .name("text-scale")
            .description("Custom text scale.")
            .visible(this.customTextScale::get)
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());

   public CompassHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   private void calculateSize() {
      this.setSize(100.0 * this.scale.get(), 100.0 * this.scale.get());
   }

   @Override
   public void render(HudRenderer renderer) {
      double x = this.x + this.getWidth() / 2.0;
      double y = this.y + this.getHeight() / 2.0;
      double pitch = this.isInEditor() ? 120.0 : MathHelper.clamp(ReviveClient.mc.player.getPitch() + 30.0F, -90.0F, 90.0F);
      pitch = Math.toRadians(pitch);
      double yaw = this.isInEditor() ? 180.0 : MathHelper.wrapDegrees(ReviveClient.mc.player.getYaw());
      yaw = Math.toRadians(yaw);

      for (CompassHud.Direction direction : CompassHud.Direction.values()) {
         String axis = this.mode.get() == CompassHud.Mode.Axis ? direction.getAxis() : direction.name();
         renderer.text(
            axis,
            x + this.getX(direction, yaw) - renderer.textWidth(axis, this.shadow.get(), this.getTextScale()) / 2.0,
            y + this.getY(direction, yaw, pitch) - renderer.textHeight(this.shadow.get(), this.getTextScale()) / 2.0,
            direction == CompassHud.Direction.N ? HudStyle.PRIMARY : HudStyle.TEXT_PRIMARY,
            this.shadow.get(),
            this.getTextScale()
         );
      }

      if (this.background.get()) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
      }
   }

   private double getX(CompassHud.Direction direction, double yaw) {
      return Math.sin(this.getPos(direction, yaw)) * this.scale.get() * 40.0;
   }

   private double getY(CompassHud.Direction direction, double yaw, double pitch) {
      return Math.cos(this.getPos(direction, yaw)) * Math.sin(pitch) * this.scale.get() * 40.0;
   }

   private double getPos(CompassHud.Direction direction, double yaw) {
      return yaw + direction.ordinal() * Math.PI / 2.0;
   }

   private double getTextScale() {
      return this.customTextScale.get() ? this.textScale.get() : -1.0;
   }

   private static enum Direction {
      N("Z-"),
      W("X-"),
      S("Z+"),
      E("X+");

      private final String axis;

      private Direction(String axis) {
         this.axis = axis;
      }

      public String getAxis() {
         return this.axis;
      }
   }

   public static enum Mode {
      Direction,
      Axis;
   }
}
