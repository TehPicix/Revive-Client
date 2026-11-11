package revivedevelopment.reviveclient.systems.modules.render;

import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.Target;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Tracers extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final Setting<Boolean> dynamicColor = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("dynamic-color")
            .description("Colour mobs and non-friend players by distance (green far -> red close).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> opacity = this.sgGeneral
      .add(
         new IntSetting.Builder().name("opacity").description("Transparency of the tracer lines.").defaultValue(180).range(0, 255).sliderRange(0, 255).build()
      );
   private final Setting<Integer> range = this.sgGeneral
      .add(new IntSetting.Builder().name("range").description("Maximum distance to draw tracers.").defaultValue(128).range(1, 256).sliderRange(1, 256).build());
   private final Setting<Target> target = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target"))
                  .description("Which part of the entity to draw the tracer to."))
               .defaultValue(Target.Body))
            .build()
      );
   private final Setting<Boolean> spine = this.sgGeneral
      .add(new BoolSetting.Builder().name("spine").description("Draw a vertical line through the entity (feet to head).").defaultValue(false).build());
   private final Setting<Boolean> players = this.sgTargets
      .add(new BoolSetting.Builder().name("players").description("Tracers to players.").defaultValue(true).build());
   private final Setting<Boolean> self = this.sgTargets
      .add(
         new BoolSetting.Builder()
            .name("self")
            .description("Tracer to yourself (third person / freecam).")
            .defaultValue(false)
            .visible(this.players::get)
            .build()
      );
   private final Setting<Boolean> friends = this.sgTargets
      .add(new BoolSetting.Builder().name("friends").description("Tracers to friends.").defaultValue(true).visible(this.players::get).build());
   private final Setting<Boolean> others = this.sgTargets
      .add(new BoolSetting.Builder().name("others").description("Tracers to non-friend players.").defaultValue(true).visible(this.players::get).build());
   private final Setting<Boolean> ignoreNakeds = this.sgTargets
      .add(new BoolSetting.Builder().name("ignore-nakeds").description("Skip players with no armor.").defaultValue(false).visible(this.players::get).build());
   private final Setting<Boolean> mobs = this.sgTargets.add(new BoolSetting.Builder().name("mobs").description("Tracers to mobs.").defaultValue(false).build());
   private final Setting<Boolean> hostile = this.sgTargets
      .add(new BoolSetting.Builder().name("hostile").description("Tracers to hostile mobs.").defaultValue(true).visible(this.mobs::get).build());
   private final Setting<Boolean> passive = this.sgTargets
      .add(new BoolSetting.Builder().name("passive").description("Tracers to passive mobs.").defaultValue(false).visible(this.mobs::get).build());
   private final Setting<Boolean> items = this.sgTargets
      .add(new BoolSetting.Builder().name("items").description("Tracers to dropped items.").defaultValue(false).build());
   private final Setting<Boolean> crystals = this.sgTargets
      .add(new BoolSetting.Builder().name("crystals").description("Tracers to end crystals.").defaultValue(false).build());
   private final Setting<Boolean> misc = this.sgTargets
      .add(new BoolSetting.Builder().name("misc").description("Tracers to projectiles and other entities.").defaultValue(false).build());
   private final Setting<Set<EntityType<?>>> excluded = this.sgTargets
      .add(new EntityTypeListSetting.Builder().name("excluded").description("Entity types to never draw tracers to.").build());
   private final Setting<SettingColor> playersColor = this.sgColors
      .add(new ColorSetting.Builder().name("players-color").defaultValue(new SettingColor(255, 255, 255)).visible(() -> !this.dynamicColor.get()).build());
   private final Setting<SettingColor> friendsColor = this.sgColors
      .add(new ColorSetting.Builder().name("friends-color").defaultValue(new SettingColor(0, 255, 180)).visible(() -> !this.dynamicColor.get()).build());
   private final Setting<SettingColor> hostileColor = this.sgColors
      .add(new ColorSetting.Builder().name("hostile-color").defaultValue(new SettingColor(255, 145, 145)).visible(() -> !this.dynamicColor.get()).build());
   private final Setting<SettingColor> passiveColor = this.sgColors
      .add(new ColorSetting.Builder().name("passive-color").defaultValue(new SettingColor(145, 255, 145)).visible(() -> !this.dynamicColor.get()).build());
   private final Setting<SettingColor> itemsColor = this.sgColors
      .add(new ColorSetting.Builder().name("items-color").defaultValue(new SettingColor(255, 255, 0)).build());
   private final Setting<SettingColor> crystalsColor = this.sgColors
      .add(new ColorSetting.Builder().name("crystals-color").defaultValue(new SettingColor(200, 0, 255)).build());
   private final Setting<SettingColor> miscColor = this.sgColors
      .add(new ColorSetting.Builder().name("misc-color").defaultValue(new SettingColor(145, 145, 145)).build());
   private final Color color = new Color();

   public Tracers() {
      super(Categories.Render, "tracers", "Draws lines to entities.");
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world != null && this.mc.player != null && !this.mc.options.hudHidden) {
         if (this.opacity.get() > 0) {
            double rangeSq = (double)this.range.get().intValue() * this.range.get().intValue();

            for (Entity entity : this.mc.world.getEntities()) {
               if (!(entity instanceof ArmorStandEntity)
                  && !entity.isInvisible()
                  && !this.excluded.get().contains(entity.getType())
                  && (entity != this.mc.player || this.self.get() && (this.isFreecamActive() || !this.mc.options.getPerspective().isFirstPerson()))) {
                  double dx = entity.getX() - this.mc.player.getX();
                  double dz = entity.getZ() - this.mc.player.getZ();
                  if (!(dx * dx + dz * dz > rangeSq) && EntityUtils.isInRenderDistance(entity) && this.resolveColor(entity)) {
                     double x = entity.lastRenderX + (entity.getX() - entity.lastRenderX) * event.tickDelta;
                     double y = entity.lastRenderY + (entity.getY() - entity.lastRenderY) * event.tickDelta;
                     double z = entity.lastRenderZ + (entity.getZ() - entity.lastRenderZ) * event.tickDelta;
                     double height = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
                     double ty = y;
                     if (this.target.get() == Target.Head) {
                        ty = y + height;
                     } else if (this.target.get() == Target.Body) {
                        ty = y + height / 2.0;
                     }

                     event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, ty, z, this.color);
                     if (this.spine.get()) {
                        event.renderer.line(x, y, z, x, y + height, z, this.color);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isFreecamActive() {
      Module freecam = Modules.get().get("freecam");
      return freecam != null && freecam.isActive();
   }

   private boolean resolveColor(Entity entity) {
      boolean nonFriendPlayer = entity instanceof PlayerEntity p && p != this.mc.player && !Friends.get().isFriend(p);
      boolean livingMob = entity instanceof LivingEntity && !(entity instanceof PlayerEntity);
      if (!this.dynamicColor.get() || !nonFriendPlayer && !livingMob) {
         SettingColor base = this.categoryColor(entity);
         if (base == null) {
            return false;
         } else {
            this.color.set(base.r, base.g, base.b, this.scaleAlpha(base.a));
            return true;
         }
      } else {
         Color c = EntityUtils.getColorFromDistance(entity);
         if (c == null) {
            return false;
         } else if (!this.isTargetEnabled(entity)) {
            return false;
         } else {
            this.color.set(c.r, c.g, c.b, this.scaleAlpha(c.a));
            return true;
         }
      }
   }

   private boolean isTargetEnabled(Entity entity) {
      return this.categoryColor(entity) != null;
   }

   private SettingColor categoryColor(Entity entity) {
      if (entity instanceof PlayerEntity player) {
         if (!this.players.get()) {
            return null;
         } else if (player == this.mc.player) {
            return this.self.get() ? this.playersColor.get() : null;
         } else if (this.ignoreNakeds.get() && player.getArmor() <= 0) {
            return null;
         } else if (Friends.get().isFriend(player)) {
            return this.friends.get() ? this.friendsColor.get() : null;
         } else {
            return this.others.get() ? this.playersColor.get() : null;
         }
      } else if (entity instanceof ItemEntity) {
         return this.items.get() ? this.itemsColor.get() : null;
      } else if (entity instanceof EndCrystalEntity) {
         return this.crystals.get() ? this.crystalsColor.get() : null;
      } else if (entity instanceof LivingEntity) {
         if (!this.mobs.get()) {
            return null;
         } else {
            boolean isHostile = entity.getType().getSpawnGroup() == SpawnGroup.MONSTER;
            if (isHostile) {
               return this.hostile.get() ? this.hostileColor.get() : null;
            } else {
               return this.passive.get() ? this.passiveColor.get() : null;
            }
         }
      } else {
         return this.misc.get() ? this.miscColor.get() : null;
      }
   }

   private int scaleAlpha(int a) {
      return Math.max(0, Math.min(255, a * this.opacity.get() / 255));
   }
}
