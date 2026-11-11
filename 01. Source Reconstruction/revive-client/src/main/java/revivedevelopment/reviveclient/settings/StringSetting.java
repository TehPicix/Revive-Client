package revivedevelopment.reviveclient.settings;

import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.gui.utils.CharFilter;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;

public class StringSetting extends Setting<String> {
   public final Class<? extends WTextBox.Renderer> renderer;
   public final CharFilter filter;
   public final boolean wide;

   public StringSetting(
      String name,
      String description,
      String defaultValue,
      Consumer<String> onChanged,
      Consumer<Setting<String>> onModuleActivated,
      IVisible visible,
      Class<? extends WTextBox.Renderer> renderer,
      CharFilter filter,
      boolean wide
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.renderer = renderer;
      this.filter = filter;
      this.wide = wide;
   }

   protected String parseImpl(String str) {
      return str;
   }

   protected boolean isValueValid(String value) {
      return true;
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      tag.putString("value", this.get());
      return tag;
   }

   public String load(NbtCompound tag) {
      this.set(tag.getString("value", ""));
      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<StringSetting.Builder, String, StringSetting> {
      private Class<? extends WTextBox.Renderer> renderer;
      private CharFilter filter;
      private boolean wide;

      public Builder() {
         super("");
      }

      public StringSetting.Builder renderer(Class<? extends WTextBox.Renderer> renderer) {
         this.renderer = renderer;
         return this;
      }

      public StringSetting.Builder filter(CharFilter filter) {
         this.filter = filter;
         return this;
      }

      public StringSetting.Builder wide() {
         this.wide = true;
         return this;
      }

      public StringSetting build() {
         return new StringSetting(
            this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible, this.renderer, this.filter, this.wide
         );
      }
   }
}
