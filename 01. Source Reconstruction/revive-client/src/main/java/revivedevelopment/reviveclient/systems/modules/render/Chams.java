package revivedevelopment.reviveclient.systems.modules.render;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Chams extends Module {
   public static final Identifier BLANK = ReviveClient.identifier("textures/blank.png");
   private static Chams INSTANCE;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgTargets = this.settings.createGroup("Targets");
   private final SettingGroup sgCrystals = this.settings.createGroup("Crystals");
   public final Setting<Boolean> crystals = this.sgCrystals
      .add(
         new BoolSetting.Builder()
            .name("crystals")
            .description("Enables end-crystal model tweaks (resize, texture, bounce, rotation).")
            .defaultValue(false)
            .build()
      );
   public final Setting<Double> crystalsScale = this.sgCrystals
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Resizes the crystal model.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderRange(0.0, 3.0)
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<Boolean> crystalsTexture = this.sgCrystals
      .add(
         new BoolSetting.Builder()
            .name("texture")
            .description("Whether to render the crystal's textures.")
            .defaultValue(true)
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<Double> crystalsBounce = this.sgCrystals
      .add(
         new DoubleSetting.Builder()
            .name("bounce")
            .description("How high the crystal bounces.")
            .defaultValue(0.6)
            .min(0.0)
            .sliderRange(0.0, 2.0)
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<Double> crystalsRotationSpeed = this.sgCrystals
      .add(
         new DoubleSetting.Builder()
            .name("rotation-speed")
            .description("Multiplies the crystal's rotation speed.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderRange(0.0, 5.0)
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<Chams.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("Fill style for the new chams render."))
               .defaultValue(Chams.Mode.Default))
            .build()
      );
   public final Setting<SettingColor> color = this.sgGeneral
      .add(new ColorSetting.Builder().name("color").description("Outline color.").defaultValue(new SettingColor(255, 255, 255, 255)).build());
   public final Setting<Boolean> shaderFill = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("shader-fill")
            .description("Let the mode shader drive the fill color instead of the solid fill color.")
            .defaultValue(false)
            .visible(() -> this.mode.get() != Chams.Mode.Default)
            .build()
      );
   public final Setting<SettingColor> fillColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("fill-color")
            .description("Solid fill color.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> this.mode.get() == Chams.Mode.Default || !this.shaderFill.get())
            .build()
      );
   public final Setting<SettingColor> gradientStart = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("gradient-start-color")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> this.mode.get() == Chams.Mode.Gradient)
            .build()
      );
   public final Setting<SettingColor> gradientEnd = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("gradient-end-color")
            .defaultValue(new SettingColor(255, 0, 255, 255))
            .visible(() -> this.mode.get() == Chams.Mode.Gradient)
            .build()
      );
   public final Setting<Chams.GradientDirection> gradientDirection = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("gradient-direction"))
                  .defaultValue(Chams.GradientDirection.Vertical))
               .visible(() -> this.mode.get() == Chams.Mode.Gradient))
            .build()
      );
   public final Setting<Double> transparency = this.sgGeneral
      .add(new DoubleSetting.Builder().name("transparency").defaultValue(0.35).min(0.0).max(1.0).sliderMax(1.0).build());
   public final Setting<Double> rainbowSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-speed")
            .defaultValue(0.1)
            .min(0.001)
            .max(2.0)
            .sliderMax(2.0)
            .visible(() -> this.mode.get() == Chams.Mode.Rainbow)
            .build()
      );
   public final Setting<Double> rainbowSaturation = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-saturation")
            .defaultValue(1.0)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .visible(() -> this.mode.get() == Chams.Mode.Rainbow)
            .build()
      );
   public final Setting<Double> rainbowLightness = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-lightness")
            .defaultValue(0.5)
            .min(0.0)
            .max(1.0)
            .sliderMax(1.0)
            .visible(() -> this.mode.get() == Chams.Mode.Rainbow)
            .build()
      );
   public final Setting<Double> warpSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("warp-speed")
            .defaultValue(0.5)
            .min(0.01)
            .max(3.0)
            .sliderMax(3.0)
            .visible(() -> this.mode.get() == Chams.Mode.Warp)
            .build()
      );
   public final Setting<Double> warpScale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("warp-scale")
            .defaultValue(1.0)
            .min(0.25)
            .max(3.0)
            .sliderMax(3.0)
            .visible(() -> this.mode.get() == Chams.Mode.Warp)
            .build()
      );
   public final Setting<SettingColor> warpColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("warp-color")
            .description("Tint color for the warp shader (white = original).")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> this.mode.get() == Chams.Mode.Warp)
            .build()
      );
   public final Setting<SettingColor> nebulaColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("nebula-color")
            .description("Tint color for the nebula shader (white = original pink).")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> this.mode.get() == Chams.Mode.Nebula)
            .build()
      );
   public final Setting<SettingColor> spaceColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("space-color")
            .description("Tint color for the space shader (white = original).")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> this.mode.get() == Chams.Mode.Space)
            .build()
      );
   public final Setting<SettingColor> gspaceColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("gspace-color")
            .description("Tint color for the gspace galaxy shader (white = original).")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> this.mode.get() == Chams.Mode.Gspace)
            .build()
      );
   public final Setting<SettingColor> plasmaColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("plasma-color")
            .description("Tint color for the plasma shader.")
            .defaultValue(new SettingColor(0, 180, 255, 255))
            .visible(() -> this.mode.get() == Chams.Mode.Plasma)
            .build()
      );
   public final Setting<Double> plasmaSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("plasma-speed")
            .description("Animation speed of the plasma shader.")
            .defaultValue(1.0)
            .min(0.0)
            .max(5.0)
            .sliderMax(5.0)
            .visible(() -> this.mode.get() == Chams.Mode.Plasma)
            .build()
      );
   public final Setting<Boolean> throughWalls = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("through-walls")
            .description("Render chammed world entities through walls.")
            .defaultValue(false)
            .visible(this::hasWorldTargets)
            .build()
      );
   public final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("Max distance to cham world entities.")
            .defaultValue(64.0)
            .min(1.0)
            .max(512.0)
            .sliderMax(512.0)
            .visible(this::hasWorldTargets)
            .build()
      );
   public final Setting<Boolean> onlyWhenHoldingItem = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-when-holding-item")
            .description("Only cham your hands / held item while you're holding something.")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> targetHands = this.sgTargets.add(new BoolSetting.Builder().name("hands").defaultValue(true).build());
   public final Setting<Boolean> targetHeldItem = this.sgTargets.add(new BoolSetting.Builder().name("held-item").defaultValue(true).build());
   public final Setting<Boolean> targetPlayers = this.sgTargets.add(new BoolSetting.Builder().name("players").defaultValue(false).build());
   public final Setting<Boolean> targetEndCrystals = this.sgTargets.add(new BoolSetting.Builder().name("end-crystals").defaultValue(false).build());
   public final Setting<Boolean> targetItems = this.sgTargets.add(new BoolSetting.Builder().name("items").defaultValue(false).build());

   public Chams() {
      super(Categories.Render, "chams", "See entities and your hands through walls with a shader fill / glow.");
      INSTANCE = this;
   }

   public static Chams get() {
      return INSTANCE != null ? INSTANCE : Modules.get().get(Chams.class);
   }

   public boolean shouldRun() {
      return this.isActive();
   }

   public boolean hands() {
      return this.targetHands.get();
   }

   public boolean heldItem() {
      return this.targetHeldItem.get();
   }

   public boolean players() {
      return this.targetPlayers.get();
   }

   public boolean endCrystals() {
      return this.targetEndCrystals.get();
   }

   public boolean items() {
      return this.targetItems.get();
   }

   public boolean hasWorldTargets() {
      return this.players() || this.endCrystals() || this.items();
   }

   public boolean throughWallsEnabled() {
      return this.isActive() && this.throughWalls.get();
   }

   public boolean worldChamsActive() {
      return this.shouldRun() && this.hasWorldTargets();
   }

   public boolean isNewWorld() {
      return this.worldChamsActive();
   }

   public boolean handChamsEnabled() {
      if (!this.isActive() || !this.shouldRun()) {
         return false;
      } else if (!this.hands() && !this.heldItem()) {
         return false;
      } else if (this.mc.player == null || this.mc.world == null) {
         return false;
      } else {
         return !this.onlyWhenHoldingItem.get() ? true : !this.mc.player.getMainHandStack().isEmpty() || !this.mc.player.getOffHandStack().isEmpty();
      }
   }

   public boolean shouldCham(Entity entity) {
      if (!this.worldChamsActive() || entity == null) {
         return false;
      } else if (!this.inRange(entity)) {
         return false;
      } else if (entity instanceof PlayerEntity) {
         return this.players();
      } else {
         return entity instanceof ItemEntity ? this.items() : this.endCrystals() && entity.getType() == EntityType.END_CRYSTAL;
      }
   }

   private boolean inRange(Entity entity) {
      if (this.mc.player == null) {
         return false;
      } else {
         double r = this.range.get();
         return this.mc.player.squaredDistanceTo(entity) <= r * r;
      }
   }

   public Color fillColor() {
      Color base;
      if (this.mode.get() == Chams.Mode.Rainbow) {
         double speed = Math.max(0.001, this.rainbowSpeed.get());
         float hue = (float)(System.currentTimeMillis() / 1000.0 * speed % 1.0);
         float sat = (float)Math.max(0.0, Math.min(1.0, this.rainbowSaturation.get()));
         float bri = (float)Math.max(0.0, Math.min(1.0, this.rainbowLightness.get() * 2.0));
         int rgb = java.awt.Color.HSBtoRGB(hue, sat, bri);
         base = new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255);
      } else if (this.mode.get() == Chams.Mode.Plasma) {
         base = this.plasmaColor.get();
      } else if (this.mode.get() == Chams.Mode.Warp) {
         base = this.warpColor.get();
      } else if (this.mode.get() == Chams.Mode.Nebula) {
         base = this.nebulaColor.get();
      } else if (this.mode.get() == Chams.Mode.Space) {
         base = this.spaceColor.get();
      } else if (this.mode.get() == Chams.Mode.Gspace) {
         base = this.gspaceColor.get();
      } else {
         base = this.mode.get() != Chams.Mode.Default && this.shaderFill.get() ? this.color.get() : this.fillColor.get();
      }

      int a = Math.max(0, Math.min(255, (int)Math.round(base.a * this.transparency.get())));
      return new Color(base.r, base.g, base.b, a);
   }

   public Color outlineColor() {
      return this.color.get();
   }

   public Chams.Mode mode() {
      return this.mode.get();
   }

   public static enum GradientDirection {
      Vertical,
      Horizontal,
      Diagonal;
   }

   public static enum Mode {
      Default,
      Gradient,
      Image,
      Rainbow,
      Warp,
      Nebula,
      Plasma,
      Space,
      Gspace;
   }
}
