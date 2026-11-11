package revivedevelopment.reviveclient.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ColorListSetting extends Setting<List<SettingColor>> {
   public ColorListSetting(
      String name,
      String description,
      List<SettingColor> defaultValue,
      Consumer<List<SettingColor>> onChanged,
      Consumer<Setting<List<SettingColor>>> onModuleActivated,
      IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   protected List<SettingColor> parseImpl(String str) {
      List<SettingColor> colors = new ArrayList<>();

      try {
         String[] colorsStr = str.replaceAll("\\s+", "").split(";");

         for (String colorStr : colorsStr) {
            String[] strs = colorStr.split(",");
            colors.add(new SettingColor(Integer.parseInt(strs[0]), Integer.parseInt(strs[1]), Integer.parseInt(strs[2]), Integer.parseInt(strs[3])));
         }
      } catch (NumberFormatException | IndexOutOfBoundsException var9) {
      }

      return colors;
   }

   protected boolean isValueValid(List<SettingColor> value) {
      return true;
   }

   @Override
   protected void resetImpl() {
      this.value = new ArrayList<>(this.defaultValue.size());

      for (SettingColor settingColor : this.defaultValue) {
         this.value.add(new SettingColor(settingColor));
      }
   }

   @Override
   protected NbtCompound save(NbtCompound tag) {
      tag.put("value", NbtUtils.listToTag(this.get()));
      return tag;
   }

   protected List<SettingColor> load(NbtCompound tag) {
      this.get().clear();

      for (NbtElement e : tag.getListOrEmpty("value")) {
         this.get().add(new SettingColor().fromTag((NbtCompound)e));
      }

      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<ColorListSetting.Builder, List<SettingColor>, ColorListSetting> {
      public Builder() {
         super(new ArrayList<>());
      }

      public ColorListSetting build() {
         return new ColorListSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
