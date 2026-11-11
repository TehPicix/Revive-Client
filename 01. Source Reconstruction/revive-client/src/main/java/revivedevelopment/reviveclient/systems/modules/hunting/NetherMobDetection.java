package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.HashSet;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.events.entity.EntityAddedEvent;
import revivedevelopment.reviveclient.events.entity.EntityRemovedEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.AlertSounds;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class NetherMobDetection extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgMobs = this.settings.createGroup("Mob Lists");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> detectInNether = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("detect-in-nether").description("Detect out-of-dimension mobs while you are in the Nether.").defaultValue(true).build()
      );
   private final Setting<Boolean> detectInOverworld = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("detect-in-overworld")
            .description("Detect out-of-dimension mobs while you are in the Overworld.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> detectInEnd = this.sgGeneral
      .add(new BoolSetting.Builder().name("detect-in-end").description("Detect out-of-dimension mobs while you are in the End.").defaultValue(true).build());
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
      .add(
         new BoolSetting.Builder()
            .name("chat-alert")
            .description("Send a chat warning when an out-of-dimension mob enters range for the first time.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> soundAlert = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("sound-alert")
            .description("Play a sound when an out-of-dimension mob enters range for the first time.")
            .defaultValue(true)
            .build()
      );
   private final Setting<AlertSounds.AlertSound> defaultAlertSound = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("default-alert-sound"))
                     .description("Default sound to play for out-of-dimension mob alerts."))
                  .defaultValue(AlertSounds.AlertSound.ExperienceOrb))
               .visible(this.soundAlert::get))
            .build()
      );
   private final Setting<Double> alertVolume = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("alert-volume")
            .description("Volume for out-of-dimension mob alert sounds.")
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
   private final Setting<Set<EntityType<?>>> netherMobs = this.sgMobs
      .add(
         new EntityTypeListSetting.Builder()
            .name("nether-mobs")
            .description("Nether mobs to flag when found outside the Nether.")
            .defaultValue(
               EntityType.PIGLIN,
               EntityType.ZOMBIFIED_PIGLIN,
               EntityType.PIGLIN_BRUTE,
               EntityType.HOGLIN,
               EntityType.ZOGLIN,
               EntityType.BLAZE,
               EntityType.GHAST,
               EntityType.MAGMA_CUBE,
               EntityType.WITHER_SKELETON,
               EntityType.STRIDER
            )
            .build()
      );
   private final Setting<Set<EntityType<?>>> overworldMobs = this.sgMobs
      .add(
         new EntityTypeListSetting.Builder()
            .name("overworld-mobs")
            .description("Overworld mobs to flag when found outside the Overworld.")
            .defaultValue(
               EntityType.COW,
               EntityType.PIG,
               EntityType.SHEEP,
               EntityType.CHICKEN,
               EntityType.HORSE,
               EntityType.DONKEY,
               EntityType.MULE,
               EntityType.LLAMA,
               EntityType.FOX,
               EntityType.WOLF,
               EntityType.CAT,
               EntityType.OCELOT,
               EntityType.PARROT,
               EntityType.RABBIT,
               EntityType.POLAR_BEAR,
               EntityType.PANDA,
               EntityType.BEE,
               EntityType.VILLAGER,
               EntityType.IRON_GOLEM,
               EntityType.SNOW_GOLEM,
               EntityType.SQUID,
               EntityType.GLOW_SQUID,
               EntityType.DOLPHIN,
               EntityType.TURTLE,
               EntityType.COD,
               EntityType.SALMON,
               EntityType.PUFFERFISH,
               EntityType.TROPICAL_FISH,
               EntityType.AXOLOTL,
               EntityType.BAT,
               EntityType.FROG,
               EntityType.TADPOLE,
               EntityType.GOAT,
               EntityType.ALLAY,
               EntityType.CAMEL,
               EntityType.SNIFFER,
               EntityType.ARMADILLO
            )
            .build()
      );
   private final Setting<Set<EntityType<?>>> endMobs = this.sgMobs
      .add(
         new EntityTypeListSetting.Builder()
            .name("end-mobs")
            .description("End mobs to flag when found outside the End.")
            .defaultValue(EntityType.ENDERMITE, EntityType.SHULKER, EntityType.ENDER_DRAGON)
            .build()
      );
   private final Setting<Boolean> drawEsp = this.sgRender
      .add(new BoolSetting.Builder().name("esp").description("Draw boxes around detected out-of-dimension mobs.").defaultValue(true).build());
   private final Setting<Boolean> drawTracers = this.sgRender
      .add(new BoolSetting.Builder().name("tracers").description("Draw tracer lines to detected out-of-dimension mobs.").defaultValue(false).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the ESP boxes are drawn."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> espColor = this.sgRender
      .add(new ColorSetting.Builder().name("color").description("Base color for ESP boxes.").defaultValue(new SettingColor(255, 140, 0, 255)).build());
   private final Setting<Integer> fillAlpha = this.sgRender
      .add(new IntSetting.Builder().name("fill-alpha").description("Opacity of the box fill.").defaultValue(40).min(0).sliderRange(0, 255).build());
   private final Set<Integer> alerted = new HashSet<>();
   private int nearbyCount = 0;
   private final Color sideColor = new Color();
   private final Color outlineColor = new Color();

   public NetherMobDetection() {
      super(Categories.Hunting, "nether-mob-detection", "Alerts you to out-of-dimension mobs that came through portals.");
   }

   @Override
   public void onDeactivate() {
      this.alerted.clear();
      this.nearbyCount = 0;
   }

   @EventHandler
   private void onEntityAdded(EntityAddedEvent event) {
      if (this.detectionEnabledHere()) {
         String origin = this.outOfDimensionOrigin(event.entity);
         if (origin != null && this.inRange(event.entity)) {
            this.maybeAlert(event.entity, origin);
         }
      }
   }

   @EventHandler
   private void onEntityRemoved(EntityRemovedEvent event) {
      this.alerted.remove(event.entity.getId());
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.world != null && this.mc.player != null) {
         if (!this.detectionEnabledHere()) {
            if (!this.alerted.isEmpty()) {
               this.alerted.clear();
            }

            this.nearbyCount = 0;
         } else {
            int count = 0;

            for (Entity entity : this.mc.world.getEntities()) {
               String origin = this.outOfDimensionOrigin(entity);
               if (origin != null && this.inRange(entity)) {
                  count++;
                  this.maybeAlert(entity, origin);
               }
            }

            this.nearbyCount = count;
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world != null && this.detectionEnabledHere() && (this.drawEsp.get() || this.drawTracers.get())) {
         SettingColor c = this.espColor.get();
         this.sideColor.set(c.r, c.g, c.b, this.fillAlpha.get());
         this.outlineColor.set(c.r, c.g, c.b, c.a);

         for (Entity entity : this.mc.world.getEntities()) {
            if (this.outOfDimensionOrigin(entity) != null && this.inRange(entity)) {
               LivingEntity living = (LivingEntity)entity;
               double ix = MathHelper.lerp(event.tickDelta, living.lastRenderX, living.getX());
               double iy = MathHelper.lerp(event.tickDelta, living.lastRenderY, living.getY());
               double iz = MathHelper.lerp(event.tickDelta, living.lastRenderZ, living.getZ());
               double hw = living.getWidth() / 2.0;
               double h = living.getHeight();
               if (this.drawEsp.get()) {
                  event.renderer.box(ix - hw, iy, iz - hw, ix + hw, iy + h, iz + hw, this.sideColor, this.outlineColor, this.shapeMode.get(), 0);
               }

               if (this.drawTracers.get()) {
                  event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, ix, iy + h / 2.0, iz, this.outlineColor);
               }
            }
         }
      }
   }

   private void maybeAlert(Entity entity, String origin) {
      if (this.alerted.add(entity.getId())) {
         if (this.chatAlert.get()) {
            this.warning(
               "Out-of-dimension mob (from %s): %s at %d %d %d",
               new Object[]{origin, entity.getName().getString(), (int)entity.getX(), (int)entity.getY(), (int)entity.getZ()}
            );
         }

         if (this.soundAlert.get()) {
            AlertSounds.playAlert(this.mc, this.defaultAlertSound.get(), this.alertVolume.get(), this.stupidDektoAlertSound.get());
         }
      }
   }

   private String outOfDimensionOrigin(Entity entity) {
      if (this.mc.world == null || !(entity instanceof LivingEntity) || entity instanceof PlayerEntity) {
         return null;
      } else if (this.isChickenJockeyChicken(entity)) {
         return null;
      } else {
         EntityType<?> type = entity.getType();
         RegistryKey<World> dim = this.mc.world.getRegistryKey();
         if (dim.equals(World.NETHER)) {
            if (this.overworldMobs.get().contains(type)) {
               return "Overworld";
            }

            if (this.endMobs.get().contains(type)) {
               return "End";
            }
         } else if (dim.equals(World.OVERWORLD)) {
            if (this.netherMobs.get().contains(type)) {
               return "Nether";
            }

            if (this.endMobs.get().contains(type)) {
               return "End";
            }
         } else if (dim.equals(World.END)) {
            if (this.overworldMobs.get().contains(type)) {
               return "Overworld";
            }

            if (this.netherMobs.get().contains(type)) {
               return "Nether";
            }
         }

         return null;
      }
   }

   private boolean detectionEnabledHere() {
      if (this.mc.world == null) {
         return false;
      } else {
         RegistryKey<World> dim = this.mc.world.getRegistryKey();
         if (dim.equals(World.NETHER)) {
            return this.detectInNether.get();
         } else if (dim.equals(World.OVERWORLD)) {
            return this.detectInOverworld.get();
         } else {
            return dim.equals(World.END) ? this.detectInEnd.get() : false;
         }
      }
   }

   private boolean isChickenJockeyChicken(Entity entity) {
      if (entity.getType() == EntityType.CHICKEN && entity.hasPassengers()) {
         for (Entity passenger : entity.getPassengerList()) {
            if (passenger instanceof LivingEntity && !(passenger instanceof PlayerEntity)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean inRange(Entity entity) {
      return !(this.range.get() <= 0.0) && this.mc.player != null ? this.mc.player.squaredDistanceTo(entity) <= this.range.get() * this.range.get() : true;
   }

   @Override
   public String getInfoString() {
      return this.nearbyCount > 0 ? this.nearbyCount + " nearby" : null;
   }
}
