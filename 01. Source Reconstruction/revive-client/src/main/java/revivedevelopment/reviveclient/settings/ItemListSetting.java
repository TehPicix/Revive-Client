package revivedevelopment.reviveclient.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class ItemListSetting extends Setting<List<Item>> {
   public final Predicate<Item> filter;
   private final boolean bypassFilterWhenSavingAndLoading;

   public ItemListSetting(
      String name,
      String description,
      List<Item> defaultValue,
      Consumer<List<Item>> onChanged,
      Consumer<Setting<List<Item>>> onModuleActivated,
      IVisible visible,
      Predicate<Item> filter,
      boolean bypassFilterWhenSavingAndLoading
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.filter = filter;
      this.bypassFilterWhenSavingAndLoading = bypassFilterWhenSavingAndLoading;
   }

   protected List<Item> parseImpl(String str) {
      String[] values = str.split(",");
      List<Item> items = new ArrayList<>(values.length);

      try {
         for (String value : values) {
            Item item = (Item)parseId(Registries.ITEM, value);
            if (item != null && (this.filter == null || this.filter.test(item))) {
               items.add(item);
            }
         }
      } catch (Exception var9) {
      }

      return items;
   }

   @Override
   public void resetImpl() {
      this.value = new ArrayList<>(this.defaultValue);
   }

   protected boolean isValueValid(List<Item> value) {
      return true;
   }

   @Override
   public Iterable<Identifier> getIdentifierSuggestions() {
      return Registries.ITEM.getIds();
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtList valueTag = new NbtList();

      for (Item item : this.get()) {
         if (this.bypassFilterWhenSavingAndLoading || this.filter == null || this.filter.test(item)) {
            valueTag.add(NbtString.of(Registries.ITEM.getId(item).toString()));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   public List<Item> load(NbtCompound tag) {
      this.get().clear();

      for (NbtElement tagI : tag.getListOrEmpty("value")) {
         Item item = (Item)Registries.ITEM.get(Identifier.of(tagI.asString().orElse("")));
         if (this.bypassFilterWhenSavingAndLoading || this.filter == null || this.filter.test(item)) {
            this.get().add(item);
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<ItemListSetting.Builder, List<Item>, ItemListSetting> {
      private Predicate<Item> filter;
      private boolean bypassFilterWhenSavingAndLoading;

      public Builder() {
         super(new ArrayList<>(0));
      }

      public ItemListSetting.Builder defaultValue(Item... defaults) {
         return this.defaultValue((List<Item>)(defaults != null ? Arrays.asList(defaults) : new ArrayList<>()));
      }

      public ItemListSetting.Builder filter(Predicate<Item> filter) {
         this.filter = filter;
         return this;
      }

      public ItemListSetting.Builder bypassFilterWhenSavingAndLoading() {
         this.bypassFilterWhenSavingAndLoading = true;
         return this;
      }

      public ItemListSetting build() {
         return new ItemListSetting(
            this.name,
            this.description,
            this.defaultValue,
            this.onChanged,
            this.onModuleActivated,
            this.visible,
            this.filter,
            this.bypassFilterWhenSavingAndLoading
         );
      }
   }
}
