package revivedevelopment.reviveclient.systems.modules.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.StatusEffectInstanceAccessor;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class Fullbright extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Fullbright.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                     .description("The mode to use for Fullbright."))
                  .defaultValue(Fullbright.Mode.Gamma))
               .onChanged(value -> {
                  if (this.isActive() && value != Fullbright.Mode.Potion) {
                     this.disableNightVision();
                  }
               }))
            .build()
      );

   public Fullbright() {
      super(Categories.Render, "fullbright", "Lights up your world!");
   }

   @Override
   public void onDeactivate() {
      if (this.mode.get() == Fullbright.Mode.Potion) {
         this.disableNightVision();
      }
   }

   public boolean getGamma() {
      return this.isActive() && this.mode.get() == Fullbright.Mode.Gamma;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mode.get() == Fullbright.Mode.Potion) {
         if (this.mc.player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry((StatusEffect)StatusEffects.NIGHT_VISION.value()))) {
            StatusEffectInstance instance = this.mc.player.getStatusEffect(Registries.STATUS_EFFECT.getEntry((StatusEffect)StatusEffects.NIGHT_VISION.value()));
            if (instance != null && instance.getDuration() < 420) {
               ((StatusEffectInstanceAccessor)instance).setDuration(420);
            }
         } else {
            this.mc
               .player
               .addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry((StatusEffect)StatusEffects.NIGHT_VISION.value()), 420, 0));
         }
      }
   }

   private void disableNightVision() {
      if (this.mc.player != null) {
         if (this.mc.player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry((StatusEffect)StatusEffects.NIGHT_VISION.value()))) {
            this.mc.player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry((StatusEffect)StatusEffects.NIGHT_VISION.value()));
         }
      }
   }

   public static enum Mode {
      Gamma,
      Potion;
   }
}
