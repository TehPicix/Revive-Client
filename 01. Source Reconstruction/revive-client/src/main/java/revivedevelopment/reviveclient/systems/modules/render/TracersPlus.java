package revivedevelopment.reviveclient.systems.modules.render;

import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.Target;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class TracersPlus extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final Setting<Boolean> enemies = this.sgGeneral
      .add(new BoolSetting.Builder().name("enemies").description("Tracers to non-teammates seen by a teammate.").defaultValue(true).build());
   private final Setting<Boolean> teammates = this.sgGeneral
      .add(new BoolSetting.Builder().name("teammates").description("Tracers to teammates outside your render distance.").defaultValue(true).build());
   private final Setting<Integer> opacity = this.sgGeneral
      .add(
         new IntSetting.Builder().name("opacity").description("Transparency of the tracer lines.").defaultValue(180).range(0, 255).sliderRange(0, 255).build()
      );
   private final Setting<Integer> range = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("range")
            .description("Maximum distance (blocks) to draw tracers.")
            .defaultValue(3000)
            .range(16, 100000)
            .sliderRange(64, 10000)
            .build()
      );
   private final Setting<Integer> staleTime = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("stale-time")
            .description("Hide a player after this many seconds without a fresh position.")
            .defaultValue(30)
            .range(1, 600)
            .sliderRange(5, 300)
            .build()
      );
   private final Setting<Target> target = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target"))
                  .description("Which part of the player to draw the tracer to."))
               .defaultValue(Target.Body))
            .build()
      );
   private final Setting<Boolean> distanceAlpha = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("distance-alpha")
            .description("Fades tracers out with distance (full opacity close, min-alpha at the range limit).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> minAlpha = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-alpha")
            .description("Opacity multiplier at the range limit.")
            .defaultValue(0.25)
            .range(0.0, 1.0)
            .sliderMax(1.0)
            .visible(this.distanceAlpha::get)
            .build()
      );
   private final Setting<Boolean> fade = this.sgGeneral
      .add(new BoolSetting.Builder().name("fade").description("Fades tracers out as the last known position gets older.").defaultValue(true).build());
   private final Setting<SettingColor> enemyColor = this.sgColors
      .add(new ColorSetting.Builder().name("enemy-color").defaultValue(new SettingColor(255, 60, 60)).build());
   private final Setting<SettingColor> teammateColor = this.sgColors
      .add(new ColorSetting.Builder().name("teammate-color").defaultValue(new SettingColor(60, 160, 255)).build());
   private final Setting<Boolean> friendColors = this.sgColors
      .add(new BoolSetting.Builder().name("friend-colors").description("Uses the friend color for friends that aren't teammates.").defaultValue(true).build());
   private final ESPPlus.RemoteTracker tracker = new ESPPlus.RemoteTracker();
   private final Color color = new Color();

   public TracersPlus() {
      super(Categories.Render, "tracers-plus", "Team sync: tracers to players your teammates see outside your render distance.");
   }

   @Override
   public void onDeactivate() {
      this.tracker.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.tracker.clear();
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world != null && this.mc.player != null && !this.mc.options.hudHidden) {
         if (this.opacity.get() > 0 && RenderUtils.center != null) {
            List<ESPPlus.RemoteTarget> targets = this.tracker.update(this.enemies.get(), this.teammates.get(), this.range.get(), this.staleTime.get());
            if (!targets.isEmpty()) {
               Vec3d cam = this.mc.gameRenderer.getCamera().getCameraPos();

               for (ESPPlus.RemoteTarget t : targets) {
                  SettingColor base = this.colorOf(t);
                  double alpha = this.opacity.get().intValue() / 255.0;
                  if (this.fade.get()) {
                     alpha *= t.fadeAlpha(this.staleTime.get().intValue() * 1000L);
                  }

                  if (this.distanceAlpha.get()) {
                     double dist = Math.sqrt(ESPPlus.distanceSqToPlayer(t.x, t.y, t.z));
                     double f = Math.min(1.0, dist / Math.max(1, this.range.get()));
                     alpha *= 1.0 - f * (1.0 - this.minAlpha.get());
                  }

                  this.color.set(base.r, base.g, base.b, Math.max(0, Math.min(255, (int)(base.a * alpha))));
                  double ty = t.y;
                  if (this.target.get() == Target.Head) {
                     ty++;
                  } else if (this.target.get() == Target.Body) {
                     ty += 0.9;
                  }

                  double dx = t.x - cam.x;
                  double dy = ty - cam.y;
                  double dz = t.z - cam.z;
                  double s = ESPPlus.RemoteTracker.clampScale(dx, dy, dz);
                  event.renderer
                     .line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, cam.x + dx * s, cam.y + dy * s, cam.z + dz * s, this.color);
               }
            }
         }
      }
   }

   private SettingColor colorOf(ESPPlus.RemoteTarget t) {
      if (t.mate) {
         return this.teammateColor.get();
      } else {
         return this.friendColors.get() && Friends.get().get(t.name) != null ? Config.get().friendColor.get() : this.enemyColor.get();
      }
   }
}
