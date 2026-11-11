package revivedevelopment.reviveclient.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import revivedevelopment.reviveclient.utils.misc.IChangeable;
import revivedevelopment.reviveclient.utils.misc.ICopyable;
import revivedevelopment.reviveclient.utils.misc.IGetter;
import revivedevelopment.reviveclient.utils.misc.ISerializable;

public class BlockDataSetting<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> extends Setting<Map<Block, T>> {
   public final IGetter<T> defaultData;

   public BlockDataSetting(
      String name,
      String description,
      Map<Block, T> defaultValue,
      Consumer<Map<Block, T>> onChanged,
      Consumer<Setting<Map<Block, T>>> onModuleActivated,
      IGetter<T> defaultData,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.defaultData = defaultData;
   }

   @Override
   public void resetImpl() {
      this.value = new HashMap<>(this.defaultValue.size());

      for (Entry<Block, T> entry : this.defaultValue.entrySet()) {
         this.value.put(entry.getKey(), entry.getValue().copy());
      }
   }

   protected Map<Block, T> parseImpl(String str) {
      return new HashMap<>(0);
   }

   protected boolean isValueValid(Map<Block, T> value) {
      return true;
   }

   @Override
   protected NbtCompound save(NbtCompound tag) {
      NbtCompound valueTag = new NbtCompound();

      for (Block block : this.get().keySet()) {
         valueTag.put(Registries.BLOCK.getId(block).toString(), this.get().get(block).toTag());
      }

      tag.put("value", valueTag);
      return tag;
   }

   protected Map<Block, T> load(NbtCompound tag) {
      this.get().clear();
      NbtCompound valueTag = tag.getCompoundOrEmpty("value");

      for (String key : valueTag.getKeys()) {
         this.get().put((Block)Registries.BLOCK.get(Identifier.of(key)), this.defaultData.get().copy().fromTag(valueTag.getCompoundOrEmpty(key)));
      }

      return this.get();
   }

   public static class Builder<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>>
      extends Setting.SettingBuilder<BlockDataSetting.Builder<T>, Map<Block, T>, BlockDataSetting<T>> {
      private IGetter<T> defaultData;

      public Builder() {
         super(new HashMap<>(0));
      }

      public BlockDataSetting.Builder<T> defaultData(IGetter<T> defaultData) {
         this.defaultData = defaultData;
         return this;
      }

      public BlockDataSetting<T> build() {
         return new BlockDataSetting<>(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.defaultData, this.visible);
      }
   }
}
