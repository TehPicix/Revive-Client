package revivedevelopment.reviveclient.systems.hud.elements;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StatusEffectListSetting;
import revivedevelopment.reviveclient.systems.hud.Alignment;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.misc.Names;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class PotionTimersHud extends HudElement {
   public static final HudElementInfo<PotionTimersHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "potion-timers", "Displays active potion effects with timers.", PotionTimersHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<List<StatusEffect>> hiddenEffects = this.sgGeneral
      .add(new StatusEffectListSetting.Builder().name("hidden-effects").description("Which effects not to show in the list.").build());
   private final Setting<Boolean> showAmbient = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("show-ambient")
            .description("Whether to show ambient effects like from beacons and conduits.")
            .defaultValue(true)
            .build()
      );
   private final Setting<PotionTimersHud.ColorMode> colorMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("color-mode"))
                  .description("What color to use for effects."))
               .defaultValue(PotionTimersHud.ColorMode.Effect))
            .build()
      );
   private final Setting<Double> rainbowSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-speed")
            .description("Rainbow speed of rainbow color mode.")
            .defaultValue(0.05)
            .sliderMin(0.01)
            .sliderMax(0.2)
            .decimalPlaces(4)
            .visible(() -> this.colorMode.get() == PotionTimersHud.ColorMode.Rainbow)
            .build()
      );
   private final Setting<Double> rainbowSpread = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-spread")
            .description("Rainbow spread of rainbow color mode.")
            .defaultValue(0.01)
            .sliderMin(0.001)
            .sliderMax(0.05)
            .decimalPlaces(4)
            .visible(() -> this.colorMode.get() == PotionTimersHud.ColorMode.Rainbow)
            .build()
      );
   private final Setting<Double> rainbowSaturation = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-saturation")
            .description("Saturation of rainbow color mode.")
            .defaultValue(1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.colorMode.get() == PotionTimersHud.ColorMode.Rainbow)
            .build()
      );
   private final Setting<Double> rainbowBrightness = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rainbow-brightness")
            .description("Brightness of rainbow color mode.")
            .defaultValue(1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.colorMode.get() == PotionTimersHud.ColorMode.Rainbow)
            .build()
      );
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("shadow").description("Renders shadow behind text.").defaultValue(true).build());
   private final Setting<Alignment> alignment = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("alignment")).description("Horizontal alignment."))
               .defaultValue(Alignment.Auto))
            .build()
      );
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
   private final List<Pair<StatusEffectInstance, String>> texts = new ArrayList<>();
   private double rainbowHue;

   public PotionTimersHud() {
      super(INFO);
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   @Override
   protected double alignX(double width, Alignment alignment) {
      return this.box.alignX(this.getWidth() - this.border.get() * 2, width, alignment);
   }

   @Override
   public void tick(HudRenderer renderer) {
      if (ReviveClient.mc.player != null && (!this.isInEditor() || !this.hasNoVisibleEffects())) {
         double width = 0.0;
         double height = 0.0;
         this.texts.clear();

         for (StatusEffectInstance statusEffectInstance : ReviveClient.mc.player.getStatusEffects()) {
            if (!this.hiddenEffects.get().contains(statusEffectInstance.getEffectType().value())
               && (this.showAmbient.get() || !statusEffectInstance.isAmbient())) {
               String text = this.getString(statusEffectInstance);
               this.texts.add(new ObjectObjectImmutablePair(statusEffectInstance, text));
               width = Math.max(width, renderer.textWidth(text, this.shadow.get(), this.getScale()));
               height += renderer.textHeight(this.shadow.get(), this.getScale());
            }
         }

         this.setSize(width, height);
      } else {
         this.setSize(renderer.textWidth("Potion Timers 0:00", this.shadow.get(), this.getScale()), renderer.textHeight(this.shadow.get(), this.getScale()));
      }
   }

   @Override
   public void render(HudRenderer renderer) {
      double x = this.x + this.border.get();
      double y = this.y + this.border.get();
      if (this.background.get()) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
      }

      if (ReviveClient.mc.player != null && (!this.isInEditor() || !this.hasNoVisibleEffects())) {
         this.rainbowHue = this.rainbowHue + this.rainbowSpeed.get() * renderer.delta;
         if (this.rainbowHue > 1.0) {
            this.rainbowHue--;
         } else if (this.rainbowHue < -1.0) {
            this.rainbowHue++;
         }

         double localRainbowHue = this.rainbowHue;

         for (Pair<StatusEffectInstance, String> potionEffectEntry : this.texts) {
            Color color = switch ((PotionTimersHud.ColorMode)this.colorMode.get()) {
               case Effect -> {
                  int c = ((StatusEffect)((StatusEffectInstance)potionEffectEntry.left()).getEffectType().value()).getColor();
                  yield new Color(c).a(255);
               }
               case Flat -> HudStyle.PRIMARY;
               case Rainbow -> {
                  localRainbowHue += this.rainbowSpread.get();
                  int c = java.awt.Color.HSBtoRGB((float)localRainbowHue, this.rainbowSaturation.get().floatValue(), this.rainbowBrightness.get().floatValue());
                  yield new Color(c);
               }
            };
            String text = (String)potionEffectEntry.right();
            renderer.text(
               text,
               x + this.alignX(renderer.textWidth(text, this.shadow.get(), this.getScale()), this.alignment.get()),
               y,
               color,
               this.shadow.get(),
               this.getScale()
            );
            y += renderer.textHeight(this.shadow.get(), this.getScale());
         }
      } else {
         renderer.text("Potion Timers 0:00", x, y, HudStyle.TEXT_PRIMARY, this.shadow.get(), this.getScale());
      }
   }

   private String getString(StatusEffectInstance statusEffectInstance) {
      return String.format(
         "%s %d (%s)",
         Names.get((StatusEffect)statusEffectInstance.getEffectType().value()),
         statusEffectInstance.getAmplifier() + 1,
         StatusEffectUtil.getDurationText(statusEffectInstance, 1.0F, ReviveClient.mc.world.getTickManager().getTickRate()).getString()
      );
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : -1.0;
   }

   private boolean hasNoVisibleEffects() {
      for (StatusEffectInstance statusEffectInstance : ReviveClient.mc.player.getStatusEffects()) {
         if (!this.hiddenEffects.get().contains(statusEffectInstance.getEffectType().value()) && (this.showAmbient.get() || !statusEffectInstance.isAmbient())) {
            return false;
         }
      }

      return true;
   }

   public static enum ColorMode {
      Effect,
      Flat,
      Rainbow;
   }
}
