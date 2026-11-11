package revivedevelopment.reviveclient.settings;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;

public class BoolSetting extends Setting<Boolean> {
   private static final List<String> SUGGESTIONS = List.of("true", "false", "toggle");

   private BoolSetting(
      String name, String description, Boolean defaultValue, Consumer<Boolean> onChanged, Consumer<Setting<Boolean>> onModuleActivated, IVisible visible
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   protected Boolean parseImpl(String str) {
      if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("1")) {
         return true;
      } else if (str.equalsIgnoreCase("false") || str.equalsIgnoreCase("0")) {
         return false;
      } else {
         return str.equalsIgnoreCase("toggle") ? !this.get() : null;
      }
   }

   protected boolean isValueValid(Boolean value) {
      return true;
   }

   @Override
   public List<String> getSuggestions() {
      return SUGGESTIONS;
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      tag.putBoolean("value", this.get());
      return tag;
   }

   public Boolean load(NbtCompound tag) {
      this.set(tag.getBoolean("value", false));
      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<BoolSetting.Builder, Boolean, BoolSetting> {
      public Builder() {
         super(false);
      }

      public BoolSetting build() {
         return new BoolSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
