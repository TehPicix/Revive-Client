package revivedevelopment.reviveclient.settings;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class EnchantmentListSetting extends Setting<Set<RegistryKey<Enchantment>>> {
   public EnchantmentListSetting(
      String name,
      String description,
      Set<RegistryKey<Enchantment>> defaultValue,
      Consumer<Set<RegistryKey<Enchantment>>> onChanged,
      Consumer<Setting<Set<RegistryKey<Enchantment>>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new ObjectOpenHashSet(this.defaultValue);
   }

   protected Set<RegistryKey<Enchantment>> parseImpl(String str) {
      String[] values = str.split(",");
      Set<RegistryKey<Enchantment>> enchs = new ObjectOpenHashSet(values.length);

      for (String value : values) {
         String name = value.trim();
         Identifier id;
         if (name.contains(":")) {
            id = Identifier.of(name);
         } else {
            id = Identifier.ofVanilla(name);
         }

         enchs.add(RegistryKey.of(RegistryKeys.ENCHANTMENT, id));
      }

      return enchs;
   }

   protected boolean isValueValid(Set<RegistryKey<Enchantment>> value) {
      return true;
   }

   @Override
   public Iterable<Identifier> getIdentifierSuggestions() {
      return Optional.ofNullable(MinecraftClient.getInstance().getNetworkHandler())
         .flatMap(networkHandler -> networkHandler.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT))
         .<Set<Identifier>>map(Registry::getIds)
         .orElse(Set.of());
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtList valueTag = new NbtList();

      for (RegistryKey<Enchantment> ench : this.get()) {
         valueTag.add(NbtString.of(ench.getValue().toString()));
      }

      tag.put("value", valueTag);
      return tag;
   }

   public Set<RegistryKey<Enchantment>> load(NbtCompound tag) {
      this.get().clear();

      for (NbtElement tagI : tag.getListOrEmpty("value")) {
         this.get().add(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(tagI.asString().orElse(""))));
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<EnchantmentListSetting.Builder, Set<RegistryKey<Enchantment>>, EnchantmentListSetting> {
      private static final Set<RegistryKey<Enchantment>> VANILLA_DEFAULTS = Arrays.stream(Enchantments.class.getDeclaredFields())
         .filter(field -> field.accessFlags().containsAll(List.of(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL)))
         .filter(field -> field.getType() == RegistryKey.class)
         .map(field -> {
            try {
               return field.get(null);
            } catch (IllegalAccessException var2) {
               return null;
            }
         })
         .filter(Objects::nonNull)
         // NOTE (source reconstruction): a bare "RegistryKey.class::cast" only yields the raw
         // type (class literals erase generics); this explicit cast restores it so the stream
         // can collect into Set<RegistryKey<Enchantment>> below.
         .map(field -> (RegistryKey<Enchantment>) RegistryKey.class.cast(field))
         .filter(registryKey -> registryKey.getRegistryRef() == RegistryKeys.ENCHANTMENT)
         .collect(Collectors.toSet());

      public Builder() {
         super(new ObjectOpenHashSet());
      }

      public EnchantmentListSetting.Builder vanillaDefaults() {
         return this.defaultValue(VANILLA_DEFAULTS);
      }

      @SafeVarargs
      public final EnchantmentListSetting.Builder defaultValue(RegistryKey<Enchantment>... defaults) {
         return this.defaultValue(defaults != null ? new ObjectOpenHashSet(defaults) : new ObjectOpenHashSet());
      }

      public EnchantmentListSetting build() {
         return new EnchantmentListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
