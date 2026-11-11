package revivedevelopment.reviveclient.settings;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.function.Consumer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class StatusEffectAmplifierMapSetting extends Setting<Reference2IntMap<StatusEffect>> {
   public static final Reference2IntMap<StatusEffect> EMPTY_STATUS_EFFECT_MAP = createStatusEffectMap();

   public StatusEffectAmplifierMapSetting(
      String name,
      String description,
      Reference2IntMap<StatusEffect> defaultValue,
      Consumer<Reference2IntMap<StatusEffect>> onChanged,
      Consumer<Setting<Reference2IntMap<StatusEffect>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new Reference2IntOpenHashMap(this.defaultValue);
   }

   protected Reference2IntMap<StatusEffect> parseImpl(String str) {
      String[] values = str.split(",");
      Reference2IntMap<StatusEffect> effects = new Reference2IntOpenHashMap(EMPTY_STATUS_EFFECT_MAP);

      try {
         for (String value : values) {
            String[] split = value.split(" ");
            StatusEffect effect = (StatusEffect)parseId(Registries.STATUS_EFFECT, split[0]);
            int level = Integer.parseInt(split[1]);
            effects.put(effect, level);
         }
      } catch (Exception var11) {
      }

      return effects;
   }

   protected boolean isValueValid(Reference2IntMap<StatusEffect> value) {
      return true;
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtCompound valueTag = new NbtCompound();
      ObjectIterator var3 = this.get().keySet().iterator();

      while (var3.hasNext()) {
         StatusEffect statusEffect = (StatusEffect)var3.next();
         Identifier id = Registries.STATUS_EFFECT.getId(statusEffect);
         if (id != null) {
            valueTag.putInt(id.toString(), this.get().getInt(statusEffect));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   private static Reference2IntMap<StatusEffect> createStatusEffectMap() {
      Reference2IntMap<StatusEffect> map = new Reference2IntArrayMap(Registries.STATUS_EFFECT.getIds().size());
      Registries.STATUS_EFFECT.forEach(potion -> map.put(potion, 0));
      return map;
   }

   public Reference2IntMap<StatusEffect> load(NbtCompound tag) {
      this.get().clear();
      NbtCompound valueTag = tag.getCompoundOrEmpty("value");

      for (String key : valueTag.getKeys()) {
         StatusEffect statusEffect = (StatusEffect)Registries.STATUS_EFFECT.get(Identifier.of(key));
         if (statusEffect != null) {
            this.get().put(statusEffect, valueTag.getInt(key, 0));
         }
      }

      return this.get();
   }

   public static class Builder
      extends Setting.SettingBuilder<StatusEffectAmplifierMapSetting.Builder, Reference2IntMap<StatusEffect>, StatusEffectAmplifierMapSetting> {
      public Builder() {
         super(new Reference2IntOpenHashMap(0));
      }

      public StatusEffectAmplifierMapSetting build() {
         return new StatusEffectAmplifierMapSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
