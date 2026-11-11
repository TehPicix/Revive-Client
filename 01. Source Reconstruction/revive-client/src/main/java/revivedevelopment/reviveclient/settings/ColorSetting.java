package revivedevelopment.reviveclient.settings;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ColorSetting extends Setting<SettingColor> {
   private static final List<String> SUGGESTIONS = List.of("0 0 0 255", "225 25 25 255", "25 225 25 255", "25 25 225 255", "255 255 255 255");

   public ColorSetting(
      String name,
      String description,
      SettingColor defaultValue,
      Consumer<SettingColor> onChanged,
      Consumer<Setting<SettingColor>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   protected SettingColor parseImpl(String str) {
      try {
         String[] strs = str.split(" ");
         return new SettingColor(Integer.parseInt(strs[0]), Integer.parseInt(strs[1]), Integer.parseInt(strs[2]), Integer.parseInt(strs[3]));
      } catch (NumberFormatException | IndexOutOfBoundsException var3) {
         return null;
      }
   }

   @Override
   public void resetImpl() {
      if (this.value == null) {
         this.value = new SettingColor(this.defaultValue);
      } else {
         this.value.set(this.defaultValue);
      }
   }

   protected boolean isValueValid(SettingColor value) {
      value.validate();
      return true;
   }

   @Override
   public List<String> getSuggestions() {
      return SUGGESTIONS;
   }

   @Override
   protected NbtCompound save(NbtCompound tag) {
      tag.put("value", this.get().toTag());
      return tag;
   }

   public SettingColor load(NbtCompound tag) {
      this.get().fromTag(tag.getCompoundOrEmpty("value"));
      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<ColorSetting.Builder, SettingColor, ColorSetting> {
      public Builder() {
         super(new SettingColor());
      }

      public ColorSetting build() {
         return new ColorSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }

      public ColorSetting.Builder defaultValue(SettingColor defaultValue) {
         this.defaultValue.set((Color)defaultValue);
         return this;
      }

      public ColorSetting.Builder defaultValue(Color defaultValue) {
         this.defaultValue.set(defaultValue);
         return this;
      }
   }
}
