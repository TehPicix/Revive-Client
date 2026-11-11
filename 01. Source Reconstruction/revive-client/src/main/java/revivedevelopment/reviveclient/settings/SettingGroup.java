package revivedevelopment.reviveclient.settings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.NotNull;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.misc.ISerializable;

public class SettingGroup implements ISerializable<SettingGroup>, Iterable<Setting<?>> {
   public final String name;
   public boolean sectionExpanded;
   final List<Setting<?>> settings = new ArrayList<>(1);

   SettingGroup(String name, boolean sectionExpanded) {
      this.name = name;
      this.sectionExpanded = sectionExpanded;
   }

   public Setting<?> get(String name) {
      for (Setting<?> setting : this) {
         if (setting.name.equals(name)) {
            return setting;
         }
      }

      return null;
   }

   public <T> Setting<T> add(Setting<T> setting) {
      this.settings.add(setting);
      return setting;
   }

   public Setting<?> getByIndex(int index) {
      return this.settings.get(index);
   }

   public boolean wasChanged() {
      for (Setting<?> setting : this.settings) {
         if (setting.wasChanged()) {
            return true;
         }
      }

      return false;
   }

   @NotNull
   @Override
   public Iterator<Setting<?>> iterator() {
      return this.settings.iterator();
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("name", this.name);
      tag.putBoolean("sectionExpanded", this.sectionExpanded);
      NbtList settingsTag = new NbtList();

      for (Setting<?> setting : this) {
         try {
            if (setting.wasChanged()) {
               settingsTag.add(setting.toTag());
            }
         } catch (Throwable var6) {
            ReviveClient.LOG.error("Failed to save setting '{}'", setting.name, var6);
         }
      }

      if (!settingsTag.isEmpty()) {
         tag.put("settings", settingsTag);
      }

      return tag;
   }

   public SettingGroup fromTag(NbtCompound tag) {
      this.sectionExpanded = tag.getBoolean("sectionExpanded", false);

      for (NbtElement t : tag.getListOrEmpty("settings")) {
         try {
            NbtCompound settingTag = (NbtCompound)t;
            Setting<?> setting = this.get(settingTag.getString("name", ""));
            if (setting != null) {
               setting.fromTag(settingTag);
            }
         } catch (Throwable var7) {
            ReviveClient.LOG.error("Failed to load a setting in group '{}'", this.name, var7);
         }
      }

      return this;
   }
}
