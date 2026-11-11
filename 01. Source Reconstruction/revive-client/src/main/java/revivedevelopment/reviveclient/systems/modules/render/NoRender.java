package revivedevelopment.reviveclient.systems.modules.render;

import java.util.List;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import revivedevelopment.reviveclient.events.world.ParticleEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.WorldAccessor;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.ParticleTypeListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class NoRender extends Module {
   private final SettingGroup sgOverlay = this.settings.createGroup("Overlay");
   private final SettingGroup sgHUD = this.settings.createGroup("HUD");
   private final SettingGroup sgWorld = this.settings.createGroup("World");
   private final SettingGroup sgEntity = this.settings.createGroup("Entity");
   private final Setting<Boolean> noPortalOverlay = this.sgOverlay
      .add(new BoolSetting.Builder().name("portal-overlay").description("Disables rendering of the nether portal overlay.").defaultValue(false).build());
   private final Setting<Boolean> noSpyglassOverlay = this.sgOverlay
      .add(new BoolSetting.Builder().name("spyglass-overlay").description("Disables rendering of the spyglass overlay.").defaultValue(false).build());
   private final Setting<Boolean> noFireOverlay = this.sgOverlay
      .add(new BoolSetting.Builder().name("fire-overlay").description("Disables rendering of the fire overlay.").defaultValue(false).build());
   private final Setting<Boolean> noLiquidOverlay = this.sgOverlay
      .add(new BoolSetting.Builder().name("liquid-overlay").description("Disables rendering of the liquid overlay.").defaultValue(false).build());
   private final Setting<Boolean> noInWallOverlay = this.sgOverlay
      .add(new BoolSetting.Builder().name("in-wall-overlay").description("Disables rendering of the overlay when inside blocks.").defaultValue(false).build());
   private final Setting<Boolean> noVignette = this.sgOverlay
      .add(new BoolSetting.Builder().name("vignette").description("Disables rendering of the vignette overlay.").defaultValue(false).build());
   private final Setting<Boolean> noGuiBackground = this.sgOverlay
      .add(new BoolSetting.Builder().name("gui-background").description("Disables rendering of the GUI background overlay.").defaultValue(false).build());
   private final Setting<Boolean> noTotemAnimation = this.sgOverlay
      .add(
         new BoolSetting.Builder()
            .name("totem-animation")
            .description("Disables rendering of the totem animation when you pop a totem.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> noEatParticles = this.sgOverlay
      .add(new BoolSetting.Builder().name("eating-particles").description("Disables rendering of eating particles.").defaultValue(false).build());
   private final Setting<Boolean> noHurtCam = this.sgOverlay
      .add(new BoolSetting.Builder().name("hurt-cam").description("Disables camera shake when taking damage.").defaultValue(false).build());
   private final Setting<Boolean> noNausea = this.sgOverlay
      .add(
         new BoolSetting.Builder()
            .name("nausea")
            .description("Disables the screen-warping nausea distortion (from the Nausea effect and nether portals).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> noScoreboard = this.sgHUD
      .add(new BoolSetting.Builder().name("scoreboard").description("Disable rendering of the scoreboard.").defaultValue(false).build());
   private final Setting<Boolean> noCrosshair = this.sgHUD
      .add(new BoolSetting.Builder().name("crosshair").description("Disables rendering of the crosshair.").defaultValue(false).build());
   private final Setting<Boolean> noTitle = this.sgHUD
      .add(new BoolSetting.Builder().name("title").description("Disables rendering of the title.").defaultValue(false).build());
   private final Setting<Boolean> noHeldItemName = this.sgHUD
      .add(new BoolSetting.Builder().name("held-item-name").description("Disables rendering of the held item name.").defaultValue(false).build());
   private final Setting<Boolean> noPotionIcons = this.sgHUD
      .add(new BoolSetting.Builder().name("potion-icons").description("Disables rendering of status effect icons.").defaultValue(false).build());
   private final Setting<Boolean> noWeather = this.sgWorld
      .add(
         new BoolSetting.Builder()
            .name("weather")
            .description("Forces the client to stay in clear weather (no rain/snow precipitation, splashes or sky darkening).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> noFog = this.sgWorld
      .add(new BoolSetting.Builder().name("fog").description("Disables rendering of fog.").defaultValue(false).build());
   private final Setting<Boolean> noDarkness = this.sgWorld
      .add(new BoolSetting.Builder().name("darkness").description("Disables rendering of darkness.").defaultValue(false).build());
   private final Setting<Boolean> noBlockBreakOverlay = this.sgWorld
      .add(
         new BoolSetting.Builder()
            .name("block-break-overlay")
            .description("Disables rendering of the block-break (cracking) overlay.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> noFallingBlocks = this.sgWorld
      .add(new BoolSetting.Builder().name("falling-blocks").description("Disables rendering of falling blocks.").defaultValue(false).build());
   private final Setting<Boolean> noCaveCulling = this.sgWorld
      .add(new BoolSetting.Builder().name("cave-culling").description("Disables Minecraft's cave culling algorithm.").defaultValue(false).onChanged(b -> {
         if (this.mc.worldRenderer != null) {
            this.mc.worldRenderer.reload();
         }
      }).build());
   private final Setting<Boolean> noFireworkExplosions = this.sgWorld
      .add(new BoolSetting.Builder().name("firework-explosions").description("Disables rendering of firework explosion particles.").defaultValue(false).build());
   private final Setting<Boolean> noExplosions = this.sgWorld
      .add(new BoolSetting.Builder().name("explosions").description("Disables explosions for TNT, Crystals, and the works.").defaultValue(false).build());
   private final Setting<Boolean> noPotionParticles = this.sgWorld
      .add(
         new BoolSetting.Builder()
            .name("potion-particles")
            .description("Hides the swirling potion particles around players and mobs (from active status effects).")
            .defaultValue(false)
            .build()
      );
   private final Setting<List<ParticleType<?>>> particles = this.sgWorld
      .add(new ParticleTypeListSetting.Builder().name("particles").description("Particles to not render.").build());
   private final Setting<Set<EntityType<?>>> entities = this.sgEntity
      .add(new EntityTypeListSetting.Builder().name("entities").description("Disables rendering of selected entities.").build());
   private final Setting<Boolean> noInvisibility = this.sgEntity
      .add(new BoolSetting.Builder().name("invisibility").description("Shows invisible entities.").defaultValue(false).build());
   private final Setting<Boolean> noGlowing = this.sgEntity
      .add(new BoolSetting.Builder().name("glowing").description("Disables rendering of the glowing effect").defaultValue(false).build());
   private final Setting<Boolean> noNametags = this.sgEntity
      .add(new BoolSetting.Builder().name("nametags").description("Disables rendering of entity nametags").defaultValue(false).build());

   public NoRender() {
      super(Categories.Render, "no-render", "Disables certain animations or overlays from rendering.");
   }

   @Override
   public void onActivate() {
      if (this.noCaveCulling.get()) {
         this.mc.worldRenderer.reload();
      }
   }

   @Override
   public void onDeactivate() {
      if (this.noCaveCulling.get()) {
         this.mc.worldRenderer.reload();
      }
   }

   public boolean noPortalOverlay() {
      return this.isActive() && this.noPortalOverlay.get();
   }

   public boolean noSpyglassOverlay() {
      return this.isActive() && this.noSpyglassOverlay.get();
   }

   public boolean noFireOverlay() {
      return this.isActive() && this.noFireOverlay.get();
   }

   public boolean noLiquidOverlay() {
      return this.isActive() && this.noLiquidOverlay.get();
   }

   public boolean noInWallOverlay() {
      return this.isActive() && this.noInWallOverlay.get();
   }

   public boolean noVignette() {
      return this.isActive() && this.noVignette.get();
   }

   public boolean noGuiBackground() {
      return this.isActive() && this.noGuiBackground.get();
   }

   public boolean noTotemAnimation() {
      return this.isActive() && this.noTotemAnimation.get();
   }

   public boolean noEatParticles() {
      return this.isActive() && this.noEatParticles.get();
   }

   public boolean noHurtCam() {
      return this.isActive() && this.noHurtCam.get();
   }

   public boolean noNausea() {
      return this.isActive() && this.noNausea.get();
   }

   public boolean noScoreboard() {
      return this.isActive() && this.noScoreboard.get();
   }

   public boolean noCrosshair() {
      return this.isActive() && this.noCrosshair.get();
   }

   public boolean noTitle() {
      return this.isActive() && this.noTitle.get();
   }

   public boolean noHeldItemName() {
      return this.isActive() && this.noHeldItemName.get();
   }

   public boolean noPotionIcons() {
      return this.isActive() && this.noPotionIcons.get();
   }

   public boolean noDarkness() {
      return this.isActive() && this.noDarkness.get();
   }

   public boolean noBlockBreakOverlay() {
      return this.isActive() && this.noBlockBreakOverlay.get();
   }

   public boolean noFallingBlocks() {
      return this.isActive() && this.noFallingBlocks.get();
   }

   public boolean noFog() {
      return this.isActive() && this.noFog.get();
   }

   public boolean noWeather() {
      return this.isActive() && this.noWeather.get();
   }

   public boolean noExplosions() {
      return this.isActive() && this.noExplosions.get();
   }

   public boolean noCaveCulling() {
      return this.isActive() && this.noCaveCulling.get();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.noWeather() && this.mc.world != null) {
         WorldAccessor world = (WorldAccessor)this.mc.world;
         world.revive$setRainGradient(0.0F);
         world.revive$setRainGradientPrev(0.0F);
         world.revive$setThunderGradient(0.0F);
         world.revive$setThunderGradientPrev(0.0F);
      }
   }

   @EventHandler
   private void onAddParticle(ParticleEvent event) {
      if (this.noWeather.get() && event.particle.getType() == ParticleTypes.RAIN) {
         event.cancel();
      } else if (this.noFireworkExplosions.get() && event.particle.getType() == ParticleTypes.FIREWORK) {
         event.cancel();
      } else if (!this.noExplosions.get()
         || event.particle.getType() != ParticleTypes.EXPLOSION
            && event.particle.getType() != ParticleTypes.EXPLOSION_EMITTER
            && event.particle.getType() != ParticleTypes.FLASH
            && event.particle.getType() != ParticleTypes.SMOKE
            && event.particle.getType() != ParticleTypes.POOF) {
         if (this.noPotionParticles.get() && event.particle.getType() == ParticleTypes.ENTITY_EFFECT) {
            event.cancel();
         } else if (this.particles.get().contains(event.particle.getType())) {
            event.cancel();
         }
      } else {
         event.cancel();
      }
   }

   public boolean noEntity(Entity entity) {
      return this.isActive() && this.entities.get().contains(entity.getType());
   }

   public boolean noEntity(EntityType<?> entity) {
      return this.isActive() && this.entities.get().contains(entity);
   }

   public boolean noInvisibility() {
      return this.isActive() && this.noInvisibility.get();
   }

   public boolean noGlowing() {
      return this.isActive() && this.noGlowing.get();
   }

   public boolean noNametags() {
      return this.isActive() && this.noNametags.get();
   }
}
