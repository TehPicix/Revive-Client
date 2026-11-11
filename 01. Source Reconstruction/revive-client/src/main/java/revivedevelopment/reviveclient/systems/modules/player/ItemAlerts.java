package revivedevelopment.reviveclient.systems.modules.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.events.entity.EntityAddedEvent;
import revivedevelopment.reviveclient.events.entity.EntityRemovedEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.AlertSounds;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ItemAlerts extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<List<Item>> items = this.sgGeneral
      .add(new ItemListSetting.Builder().name("items").description("Items to alert for when they are dropped on the ground.").build());
   private final Setting<Boolean> experienceOrbs = this.sgGeneral
      .add(new BoolSetting.Builder().name("experience-orbs").description("Alert for experience orbs on the ground.").defaultValue(false).build());
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("Detection range in blocks. Set to 0 for unlimited.")
            .defaultValue(128.0)
            .min(0.0)
            .sliderRange(0.0, 256.0)
            .decimalPlaces(0)
            .build()
      );
   private final Setting<Boolean> chatAlert = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat-alert").description("Send a chat warning when a selected item is detected.").defaultValue(true).build());
   private final Setting<Boolean> soundAlert = this.sgGeneral
      .add(new BoolSetting.Builder().name("sound-alert").description("Play a sound when a selected item is detected.").defaultValue(true).build());
   private final Setting<AlertSounds.AlertSound> defaultAlertSound = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("default-alert-sound"))
                     .description("Default sound to play for item alerts."))
                  .defaultValue(AlertSounds.AlertSound.ExperienceOrb))
               .visible(this.soundAlert::get))
            .build()
      );
   private final Setting<Double> alertVolume = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("alert-volume")
            .description("Volume for item alert sounds.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderRange(0.0, 2.0)
            .decimalPlaces(2)
            .visible(this.soundAlert::get)
            .build()
      );
   private final Setting<Boolean> stupidDektoAlertSound = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("stupid-dekto-alert-sound")
            .description("Use the stupid Dekto alert sound instead of the selected default alert sound.")
            .defaultValue(false)
            .visible(this.soundAlert::get)
            .build()
      );
   private final Setting<Boolean> drawTracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw tracer lines to detected dropped items.").defaultValue(false).build());
   private final Setting<SettingColor> tracerColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("tracer-color")
            .description("Tracer line color.")
            .defaultValue(new SettingColor(80, 200, 255, 255))
            .visible(this.drawTracers::get)
            .build()
      );
   private final Set<Integer> alerted = new HashSet<>();
   private final Color lineColor = new Color();
   private int nearbyCount = 0;

   public ItemAlerts() {
      super(Categories.Player, "item-alerts", "Alerts when selected items are dropped on the ground.");
   }

   @Override
   public void onDeactivate() {
      this.alerted.clear();
      this.nearbyCount = 0;
   }

   @EventHandler
   private void onEntityAdded(EntityAddedEvent event) {
      if (this.mc.world != null && this.mc.player != null && this.isTarget(event.entity) && this.inRange(event.entity)) {
         this.maybeAlert(event.entity);
      }
   }

   @EventHandler
   private void onEntityRemoved(EntityRemovedEvent event) {
      this.alerted.remove(event.entity.getId());
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.world != null && this.mc.player != null) {
         int count = 0;

         for (Entity entity : this.mc.world.getEntities()) {
            if (this.isTarget(entity) && this.inRange(entity)) {
               count++;
               this.maybeAlert(entity);
            }
         }

         this.nearbyCount = count;
      } else {
         this.nearbyCount = 0;
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.drawTracers.get() && this.mc.world != null && this.mc.player != null) {
         SettingColor c = this.tracerColor.get();
         this.lineColor.set(c.r, c.g, c.b, c.a);

         for (Entity entity : this.mc.world.getEntities()) {
            if (this.isTarget(entity) && this.inRange(entity)) {
               double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX());
               double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) + entity.getHeight() / 2.0;
               double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ());
               event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, this.lineColor);
            }
         }
      }
   }

   private void maybeAlert(Entity entity) {
      if (this.alerted.add(entity.getId())) {
         int x = (int)entity.getX();
         int y = (int)entity.getY();
         int z = (int)entity.getZ();
         if (this.chatAlert.get()) {
            this.warning("%s", new Object[]{this.getAlertMessage(entity, x, y, z)});
         }

         if (this.soundAlert.get()) {
            AlertSounds.playAlert(this.mc, this.defaultAlertSound.get(), this.alertVolume.get(), this.stupidDektoAlertSound.get());
         }
      }
   }

   private String getAlertMessage(Entity entity, int x, int y, int z) {
      if (entity instanceof ItemEntity itemEntity) {
         ItemStack stack = itemEntity.getStack();
         return "Dropped item: " + stack.getName().getString() + " x" + stack.getCount() + " at " + x + " " + y + " " + z;
      } else {
         return "Experience orb at " + x + " " + y + " " + z;
      }
   }

   private boolean isTarget(Entity entity) {
      return entity instanceof ItemEntity itemEntity
         ? !this.items.get().isEmpty() && this.items.get().contains(itemEntity.getStack().getItem())
         : this.experienceOrbs.get() && entity instanceof ExperienceOrbEntity;
   }

   private boolean inRange(Entity entity) {
      return !(this.range.get() <= 0.0) && this.mc.player != null ? this.mc.player.squaredDistanceTo(entity) <= this.range.get() * this.range.get() : true;
   }

   @Override
   public String getInfoString() {
      return this.nearbyCount > 0 ? this.nearbyCount + " nearby" : null;
   }
}
