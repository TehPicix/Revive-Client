package revivedevelopment.reviveclient.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class StatusEffectListSetting extends Setting<List<StatusEffect>> {
   public StatusEffectListSetting(
      String name,
      String description,
      List<StatusEffect> defaultValue,
      Consumer<List<StatusEffect>> onChanged,
      Consumer<Setting<List<StatusEffect>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new ArrayList<>(this.defaultValue);
   }

   protected List<StatusEffect> parseImpl(String str) {
      String[] values = str.split(",");
      List<StatusEffect> effects = new ArrayList<>(values.length);

      try {
         for (String value : values) {
            StatusEffect effect = (StatusEffect)parseId(Registries.STATUS_EFFECT, value);
            if (effect != null) {
               effects.add(effect);
            }
         }
      } catch (Exception var9) {
      }

      return effects;
   }

   protected boolean isValueValid(List<StatusEffect> value) {
      return true;
   }

   @Override
   public Iterable<Identifier> getIdentifierSuggestions() {
      return Registries.STATUS_EFFECT.getIds();
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtList valueTag = new NbtList();

      for (StatusEffect effect : this.get()) {
         Identifier id = Registries.STATUS_EFFECT.getId(effect);
         if (id != null) {
            valueTag.add(NbtString.of(id.toString()));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   public List<StatusEffect> load(NbtCompound tag) {
      this.get().clear();

      for (NbtElement tagI : tag.getListOrEmpty("value")) {
         StatusEffect effect = (StatusEffect)Registries.STATUS_EFFECT.get(Identifier.of(tagI.asString().orElse("")));
         if (effect != null) {
            this.get().add(effect);
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<StatusEffectListSetting.Builder, List<StatusEffect>, StatusEffectListSetting> {
      public Builder() {
         super(new ArrayList<>(0));
      }

      public StatusEffectListSetting.Builder defaultValue(StatusEffect... defaults) {
         return this.defaultValue((List<StatusEffect>)(defaults != null ? Arrays.asList(defaults) : new ArrayList<>()));
      }

      public StatusEffectListSetting build() {
         return new StatusEffectListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
