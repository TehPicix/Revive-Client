package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.ActionSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Breadcrumbs extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> minDistance = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-distance")
            .description("How far you must move before a new point is recorded, in blocks.")
            .defaultValue(0.5)
            .min(0.1)
            .sliderRange(0.1, 5.0)
            .build()
      );
   private final Setting<Integer> maxPoints = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("max-points")
            .description("Maximum number of recorded points per dimension; the oldest are dropped first.")
            .defaultValue(5000)
            .min(10)
            .sliderRange(100, 20000)
            .build()
      );
   private final Setting<SettingColor> color = this.sgGeneral
      .add(new ColorSetting.Builder().name("color").description("Color of the trail.").defaultValue(new SettingColor(225, 25, 225)).build());
   private final Setting<Boolean> clear = this.sgGeneral
      .add(new ActionSetting.Builder().name("clear").description("Removes all recorded points.").buttonLabel("clear").action(this::clearTrails).build());
   private final Map<RegistryKey<World>, Deque<Vec3d>> trails = new HashMap<>();
   private final Color line = new Color();

   public Breadcrumbs() {
      super(Categories.Render, "breadcrumbs", "Renders a trail along the path you have traveled.");
   }

   private void clearTrails() {
      this.trails.clear();
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.clearTrails();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.world != null) {
         Deque<Vec3d> trail = this.trails.computeIfAbsent(this.mc.world.getRegistryKey(), key -> new ArrayDeque<>());
         Vec3d pos = this.mc.player.getEntityPos();
         Vec3d last = trail.peekLast();
         if (last == null || !(last.squaredDistanceTo(pos) < this.minDistance.get() * this.minDistance.get())) {
            trail.addLast(pos);

            while (trail.size() > this.maxPoints.get()) {
               trail.removeFirst();
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.world != null) {
         Deque<Vec3d> trail = this.trails.get(this.mc.world.getRegistryKey());
         if (trail != null && trail.size() >= 2) {
            this.line.set(this.color.get());
            Vec3d prev = null;

            for (Vec3d pos : trail) {
               if (prev != null) {
                  event.renderer.line(prev.x, prev.y, prev.z, pos.x, pos.y, pos.z, this.line);
               }

               prev = pos;
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      if (this.mc.world == null) {
         return null;
      } else {
         Deque<Vec3d> trail = this.trails.get(this.mc.world.getRegistryKey());
         return trail == null ? "0" : Integer.toString(trail.size());
      }
   }
}
