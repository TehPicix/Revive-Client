package revivedevelopment.reviveclient.settings;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.registry.entry.RegistryEntryList.Named;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;

public class StorageBlockListSetting extends Setting<List<BlockEntityType<?>>> {
   public static final BlockEntityType<?>[] STORAGE_BLOCKS = new BlockEntityType[]{
      BlockEntityType.BARREL,
      BlockEntityType.BLAST_FURNACE,
      BlockEntityType.BREWING_STAND,
      BlockEntityType.CAMPFIRE,
      BlockEntityType.CHEST,
      BlockEntityType.CHISELED_BOOKSHELF,
      BlockEntityType.CRAFTER,
      BlockEntityType.DISPENSER,
      BlockEntityType.DECORATED_POT,
      BlockEntityType.DROPPER,
      BlockEntityType.ENDER_CHEST,
      BlockEntityType.FURNACE,
      BlockEntityType.HOPPER,
      BlockEntityType.SHULKER_BOX,
      BlockEntityType.SMOKER,
      BlockEntityType.TRAPPED_CHEST
   };
   public static final Registry<BlockEntityType<?>> REGISTRY = new StorageBlockListSetting.SRegistry();

   public StorageBlockListSetting(
      String name,
      String description,
      List<BlockEntityType<?>> defaultValue,
      Consumer<List<BlockEntityType<?>>> onChanged,
      Consumer<Setting<List<BlockEntityType<?>>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new ArrayList<>(this.defaultValue);
   }

   protected List<BlockEntityType<?>> parseImpl(String str) {
      String[] values = str.split(",");
      List<BlockEntityType<?>> blocks = new ArrayList<>(values.length);

      try {
         for (String value : values) {
            BlockEntityType<?> block = (BlockEntityType<?>)parseId(Registries.BLOCK_ENTITY_TYPE, value);
            if (block != null) {
               blocks.add(block);
            }
         }
      } catch (Exception var9) {
      }

      return blocks;
   }

   protected boolean isValueValid(List<BlockEntityType<?>> value) {
      return true;
   }

   @Override
   public Iterable<Identifier> getIdentifierSuggestions() {
      return Registries.BLOCK_ENTITY_TYPE.getIds();
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtList valueTag = new NbtList();

      for (BlockEntityType<?> type : this.get()) {
         Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(type);
         if (id != null) {
            valueTag.add(NbtString.of(id.toString()));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   public List<BlockEntityType<?>> load(NbtCompound tag) {
      this.get().clear();

      for (NbtElement tagI : tag.getListOrEmpty("value")) {
         BlockEntityType<?> type = (BlockEntityType<?>)Registries.BLOCK_ENTITY_TYPE.get(Identifier.of(tagI.asString().orElse("")));
         if (type != null) {
            this.get().add(type);
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<StorageBlockListSetting.Builder, List<BlockEntityType<?>>, StorageBlockListSetting> {
      public Builder() {
         super(new ArrayList<>(0));
      }

      public StorageBlockListSetting.Builder defaultValue(BlockEntityType<?>... defaults) {
         return this.defaultValue((List<BlockEntityType<?>>)(defaults != null ? Arrays.asList(defaults) : new ArrayList<>()));
      }

      public StorageBlockListSetting build() {
         return new StorageBlockListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }

   private static class SRegistry extends SimpleRegistry<BlockEntityType<?>> {
      public SRegistry() {
         super(RegistryKey.ofRegistry(ReviveClient.identifier("storage-blocks")), Lifecycle.stable());
      }

      public int size() {
         return StorageBlockListSetting.STORAGE_BLOCKS.length;
      }

      @Nullable
      public Identifier getId(BlockEntityType<?> entry) {
         return null;
      }

      public Optional<RegistryKey<BlockEntityType<?>>> getKey(BlockEntityType<?> entry) {
         return Optional.empty();
      }

      public int getRawId(@Nullable BlockEntityType<?> entry) {
         return 0;
      }

      @Nullable
      public BlockEntityType<?> get(@Nullable RegistryKey<BlockEntityType<?>> key) {
         return null;
      }

      @Nullable
      public BlockEntityType<?> get(@Nullable Identifier id) {
         return null;
      }

      public Lifecycle getLifecycle() {
         return null;
      }

      public Set<Identifier> getIds() {
         return null;
      }

      public BlockEntityType<?> getOrThrow(int index) {
         return (BlockEntityType<?>)super.getOrThrow(index);
      }

      public boolean containsId(Identifier id) {
         return false;
      }

      @Nullable
      public BlockEntityType<?> get(int index) {
         return null;
      }

      @NotNull
      public Iterator<BlockEntityType<?>> iterator() {
         return ObjectIterators.wrap(StorageBlockListSetting.STORAGE_BLOCKS);
      }

      public boolean contains(RegistryKey<BlockEntityType<?>> key) {
         return false;
      }

      public Set<Entry<RegistryKey<BlockEntityType<?>>, BlockEntityType<?>>> getEntrySet() {
         return null;
      }

      public Optional<Reference<BlockEntityType<?>>> getRandom(Random random) {
         return Optional.empty();
      }

      public Registry<BlockEntityType<?>> freeze() {
         return null;
      }

      public Reference<BlockEntityType<?>> createEntry(BlockEntityType<?> value) {
         return null;
      }

      public Optional<Reference<BlockEntityType<?>>> getEntry(int rawId) {
         return Optional.empty();
      }

      public Optional<Reference<BlockEntityType<?>>> getEntry(Identifier id) {
         return Optional.empty();
      }

      public Stream<Reference<BlockEntityType<?>>> streamEntries() {
         return null;
      }

      public Stream<Named<BlockEntityType<?>>> streamTags() {
         return null;
      }

      public Set<RegistryKey<BlockEntityType<?>>> getKeys() {
         return null;
      }
   }
}
