package revivedevelopment.reviveclient.settings;

import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.gui.utils.IScreenFactory;
import revivedevelopment.reviveclient.utils.misc.ICopyable;
import revivedevelopment.reviveclient.utils.misc.ISerializable;

public class GenericSetting<T extends ICopyable<T> & ISerializable<T> & IScreenFactory> extends Setting<T> {
   public GenericSetting(String name, String description, T defaultValue, Consumer<T> onChanged, Consumer<Setting<T>> onModuleActivated, IVisible visible) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
   }

   @Override
   public void resetImpl() {
      if (this.value == null) {
         this.value = this.defaultValue.copy();
      }

      this.value.set(this.defaultValue);
   }

   protected T parseImpl(String str) {
      return this.defaultValue.copy();
   }

   protected boolean isValueValid(T value) {
      return true;
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      tag.put("value", this.get().toTag());
      return tag;
   }

   public T load(NbtCompound tag) {
      this.get().fromTag(tag.getCompoundOrEmpty("value"));
      return this.get();
   }

   public static class Builder<T extends ICopyable<T> & ISerializable<T> & IScreenFactory>
      extends Setting.SettingBuilder<GenericSetting.Builder<T>, T, GenericSetting<T>> {
      public Builder() {
         super(null);
      }

      public GenericSetting<T> build() {
         return new GenericSetting<>(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible);
      }
   }
}
