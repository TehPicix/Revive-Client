package revivedevelopment.reviveclient.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class ParticleTypeListSetting extends Setting<List<ParticleType<?>>> {
   public ParticleTypeListSetting(
      String name,
      String description,
      List<ParticleType<?>> defaultValue,
      Consumer<List<ParticleType<?>>> onChanged,
      Consumer<Setting<List<ParticleType<?>>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new ArrayList<>(this.defaultValue);
   }

   protected List<ParticleType<?>> parseImpl(String str) {
      String[] values = str.split(",");
      List<ParticleType<?>> particleTypes = new ArrayList<>(values.length);

      try {
         for (String value : values) {
            ParticleType<?> particleType = (ParticleType<?>)parseId(Registries.PARTICLE_TYPE, value);
            if (particleType instanceof ParticleEffect) {
               particleTypes.add(particleType);
            }
         }
      } catch (Exception var9) {
      }

      return particleTypes;
   }

   protected boolean isValueValid(List<ParticleType<?>> value) {
      return true;
   }

   @Override
   public Iterable<Identifier> getIdentifierSuggestions() {
      return Registries.PARTICLE_TYPE.getIds();
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtList valueTag = new NbtList();

      for (ParticleType<?> particleType : this.get()) {
         Identifier id = Registries.PARTICLE_TYPE.getId(particleType);
         if (id != null) {
            valueTag.add(NbtString.of(id.toString()));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   public List<ParticleType<?>> load(NbtCompound tag) {
      this.get().clear();

      for (NbtElement tagI : tag.getListOrEmpty("value")) {
         ParticleType<?> particleType = (ParticleType<?>)Registries.PARTICLE_TYPE.get(Identifier.of(tagI.asString().orElse("")));
         if (particleType != null) {
            this.get().add(particleType);
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<ParticleTypeListSetting.Builder, List<ParticleType<?>>, ParticleTypeListSetting> {
      public Builder() {
         super(new ArrayList<>(0));
      }

      public ParticleTypeListSetting.Builder defaultValue(ParticleType<?>... defaults) {
         return this.defaultValue((List<ParticleType<?>>)(defaults != null ? Arrays.asList(defaults) : new ArrayList<>()));
      }

      public ParticleTypeListSetting build() {
         return new ParticleTypeListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
