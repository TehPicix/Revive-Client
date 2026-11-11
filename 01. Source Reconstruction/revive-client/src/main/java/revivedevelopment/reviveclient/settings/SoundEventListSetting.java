package revivedevelopment.reviveclient.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class SoundEventListSetting extends Setting<List<SoundEvent>> {
   public SoundEventListSetting(
      String name,
      String description,
      List<SoundEvent> defaultValue,
      Consumer<List<SoundEvent>> onChanged,
      Consumer<Setting<List<SoundEvent>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new ArrayList<>(this.defaultValue);
   }

   protected List<SoundEvent> parseImpl(String str) {
      String[] values = str.split(",");
      List<SoundEvent> sounds = new ArrayList<>(values.length);

      try {
         for (String value : values) {
            SoundEvent sound = (SoundEvent)parseId(Registries.SOUND_EVENT, value);
            if (sound != null) {
               sounds.add(sound);
            }
         }
      } catch (Exception var9) {
      }

      return sounds;
   }

   protected boolean isValueValid(List<SoundEvent> value) {
      return true;
   }

   @Override
   public Iterable<Identifier> getIdentifierSuggestions() {
      return Registries.SOUND_EVENT.getIds();
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtList valueTag = new NbtList();

      for (SoundEvent sound : this.get()) {
         Identifier id = Registries.SOUND_EVENT.getId(sound);
         if (id != null) {
            valueTag.add(NbtString.of(id.toString()));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   public List<SoundEvent> load(NbtCompound tag) {
      this.get().clear();

      for (NbtElement tagI : tag.getListOrEmpty("value")) {
         SoundEvent soundEvent = (SoundEvent)Registries.SOUND_EVENT.get(Identifier.of(tagI.asString().orElse("")));
         if (soundEvent != null) {
            this.get().add(soundEvent);
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<SoundEventListSetting.Builder, List<SoundEvent>, SoundEventListSetting> {
      public Builder() {
         super(new ArrayList<>(0));
      }

      public SoundEventListSetting.Builder defaultValue(SoundEvent... defaults) {
         return this.defaultValue((List<SoundEvent>)(defaults != null ? Arrays.asList(defaults) : new ArrayList<>()));
      }

      public SoundEventListSetting build() {
         return new SoundEventListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
