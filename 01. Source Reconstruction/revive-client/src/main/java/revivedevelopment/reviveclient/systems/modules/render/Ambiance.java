package revivedevelopment.reviveclient.systems.modules.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.Camera;
import org.joml.Vector3f;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;
import revivedevelopment.reviveclient.utils.render.postprocess.SkyShaderRenderer;

public class Ambiance extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Ambiance.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("Solid custom sky color or one of the Chams shaders."))
               .defaultValue(Ambiance.Mode.Color))
            .build()
      );
   public final Setting<SettingColor> skyColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("sky-color")
            .description("The solid sky color.")
            .defaultValue(new SettingColor(20, 120, 255, 255))
            .visible(() -> this.mode.get() == Ambiance.Mode.Color)
            .build()
      );
   public final Setting<SettingColor> primaryColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("primary-color")
            .description("Primary tint for the shader sky (white = the shader's original palette).")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> this.mode.get() != Ambiance.Mode.Color)
            .build()
      );
   public final Setting<SettingColor> secondaryColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("secondary-color")
            .description("Secondary tint for the shader sky.")
            .defaultValue(new SettingColor(120, 0, 255, 255))
            .visible(() -> this.mode.get() != Ambiance.Mode.Color)
            .build()
      );
   public final Setting<Double> opacity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("opacity")
            .description("How strongly the custom sky blends over the vanilla sky.")
            .defaultValue(1.0)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .build()
      );

   public Ambiance() {
      super(Categories.Render, "ambiance", "Render a custom sky color or a Chams shader as the sky.");
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world != null) {
         SettingColor primary = this.mode.get() == Ambiance.Mode.Color ? this.skyColor.get() : this.primaryColor.get();
         SettingColor secondary = this.secondaryColor.get();
         float alpha = (float)(this.opacity.get() * (primary.a / 255.0));
         if (!(alpha <= 0.0F)) {
            Camera camera = this.mc.gameRenderer.getCamera();
            double yaw = Math.toRadians(wrapDegrees(camera.getYaw()));
            double pitch = Math.toRadians(Math.max(-89.9, Math.min(89.9, (double)camera.getPitch())));
            double cp = Math.cos(pitch);
            double sp = Math.sin(pitch);
            double cy = Math.cos(yaw);
            double sy = Math.sin(yaw);
            double fx = -sy * cp;
            double fy = -sp;
            double fz = cy * cp;
            double rl = Math.sqrt(fz * fz + fx * fx);
            double rx = -fz / rl;
            double rz = fx / rl;
            double ux = -rz * fy;
            double uy = rz * fx - rx * fz;
            double uz = rx * fy;
            double vFov = ((Integer)this.mc.options.getFov().getValue()).intValue();
            float tanHalfFov = (float)Math.tan(Math.toRadians(vFov) / 2.0);
            int w = this.mc.getWindow().getFramebufferWidth();
            int h = this.mc.getWindow().getFramebufferHeight();
            float aspect = h > 0 ? (float)w / h : 1.0F;
            SkyShaderRenderer.render(
               this.mode.get().ordinal(),
               alpha,
               rgb(primary),
               rgb(secondary),
               new Vector3f((float)fx, (float)fy, (float)fz),
               new Vector3f((float)rx, 0.0F, (float)rz),
               new Vector3f((float)ux, (float)uy, (float)uz),
               tanHalfFov,
               aspect
            );
         }
      }
   }

   private static int rgb(SettingColor c) {
      return (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
   }

   private static double wrapDegrees(double degrees) {
      double d = degrees % 360.0;
      if (d >= 180.0) {
         d -= 360.0;
      } else if (d < -180.0) {
         d += 360.0;
      }

      return d;
   }

   public static enum Mode {
      Color,
      Warp,
      Nebula,
      Plasma,
      Space,
      Gspace;
   }
}
