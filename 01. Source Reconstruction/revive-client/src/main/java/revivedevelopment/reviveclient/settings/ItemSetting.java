package revivedevelopment.reviveclient.settings;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class ItemSetting extends Setting<Item> {
   public final Predicate<Item> filter;

   public ItemSetting(
      String name,
      String description,
      Item defaultValue,
      Consumer<Item> onChanged,
      Consumer<Setting<Item>> onModuleActivated,
      IVisible visible,
      Predicate<Item> filter
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.filter = filter;
   }

   protected Item parseImpl(String str) {
      return parseId(Registries.ITEM, str);
   }

   protected boolean isValueValid(Item value) {
      return this.filter == null || this.filter.test(value);
   }

   @Override
   public Iterable<Identifier> getIdentifierSuggestions() {
      return Registries.ITEM.getIds();
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      tag.putString("value", Registries.ITEM.getId(this.get()).toString());
      return tag;
   }

   public Item load(NbtCompound tag) {
      this.value = (Item)Registries.ITEM.get(Identifier.of(tag.getString("value", "")));
      if (this.filter != null && !this.filter.test(this.value)) {
         for (Item item : Registries.ITEM) {
            if (this.filter.test(item)) {
               this.value = item;
               break;
            }
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<ItemSetting.Builder, Item, ItemSetting> {
      private Predicate<Item> filter;

      public Builder() {
         super(null);
      }

      public ItemSetting.Builder filter(Predicate<Item> filter) {
         this.filter = filter;
         return this;
      }

      public ItemSetting build() {
         return new ItemSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible, this.filter);
      }
   }
}
