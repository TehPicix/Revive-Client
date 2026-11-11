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
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ScreenHandlerListSetting extends Setting<List<ScreenHandlerType<?>>> {
   public ScreenHandlerListSetting(
      String name,
      String description,
      List<ScreenHandlerType<?>> defaultValue,
      Consumer<List<ScreenHandlerType<?>>> onChanged,
      Consumer<Setting<List<ScreenHandlerType<?>>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      this.value = new ArrayList<>(this.defaultValue);
   }

   protected List<ScreenHandlerType<?>> parseImpl(String str) {
      String[] values = str.split(",");
      List<ScreenHandlerType<?>> handlers = new ArrayList<>(values.length);

      try {
         for (String value : values) {
            ScreenHandlerType<?> handler = (ScreenHandlerType<?>)parseId(Registries.SCREEN_HANDLER, value);
            if (handler != null) {
               handlers.add(handler);
            }
         }
      } catch (Exception var9) {
      }

      return handlers;
   }

   protected boolean isValueValid(List<ScreenHandlerType<?>> value) {
      return true;
   }

   @Override
   public Iterable<Identifier> getIdentifierSuggestions() {
      return Registries.SCREEN_HANDLER.getIds();
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      NbtList valueTag = new NbtList();

      for (ScreenHandlerType<?> type : this.get()) {
         Identifier id = Registries.SCREEN_HANDLER.getId(type);
         if (id != null) {
            valueTag.add(NbtString.of(id.toString()));
         }
      }

      tag.put("value", valueTag);
      return tag;
   }

   public List<ScreenHandlerType<?>> load(NbtCompound tag) {
      this.get().clear();

      for (NbtElement tagI : tag.getListOrEmpty("value")) {
         ScreenHandlerType<?> type = (ScreenHandlerType<?>)Registries.SCREEN_HANDLER.get(Identifier.of(tagI.asString().orElse("")));
         if (type != null) {
            this.get().add(type);
         }
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<ScreenHandlerListSetting.Builder, List<ScreenHandlerType<?>>, ScreenHandlerListSetting> {
      public Builder() {
         super(new ArrayList<>(0));
      }

      public ScreenHandlerListSetting.Builder defaultValue(ScreenHandlerType<?>... defaults) {
         return this.defaultValue((List<ScreenHandlerType<?>>)(defaults != null ? Arrays.asList(defaults) : new ArrayList<>()));
      }

      public ScreenHandlerListSetting build() {
         return new ScreenHandlerListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
