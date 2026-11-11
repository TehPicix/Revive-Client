package revivedevelopment.reviveclient.systems.profiles;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.apache.commons.io.FileUtils;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.Settings;
import revivedevelopment.reviveclient.settings.StringListSetting;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.ISerializable;

public class Profile implements ISerializable<Profile> {
   public final Settings settings = new Settings();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSave = this.settings.createGroup("Save");
   public Setting<String> name = this.sgGeneral
      .add(new StringSetting.Builder().name("name").description("The name of the profile.").filter(Utils::nameFilter).build());
   public Setting<List<String>> loadOnJoin = this.sgGeneral
      .add(
         new StringListSetting.Builder()
            .name("load-on-join")
            .description("Which servers to set this profile as active when joining.")
            .filter(Utils::ipFilter)
            .build()
      );
   public Setting<Boolean> hud = this.sgSave
      .add(new BoolSetting.Builder().name("hud").description("Whether the profile should save hud.").defaultValue(false).build());
   public Setting<Boolean> modules = this.sgSave
      .add(new BoolSetting.Builder().name("modules").description("Whether the profile should save modules.").defaultValue(false).build());

   public Profile() {
   }

   public Profile(NbtElement tag) {
      this.fromTag((NbtCompound)tag);
   }

   public void load() {
      File folder = this.getFile();
      if (this.hud.get()) {
         Hud.get().load(folder);
      }

      if (this.modules.get()) {
         Modules.get().load(folder);
      }
   }

   public void save() {
      File folder = this.getFile();
      if (this.hud.get()) {
         Hud.get().save(folder);
      }

      if (this.modules.get()) {
         Modules.get().save(folder);
      }
   }

   public void delete() {
      try {
         FileUtils.deleteDirectory(this.getFile());
      } catch (IOException var2) {
         var2.printStackTrace();
      }
   }

   private File getFile() {
      return new File(Profiles.FOLDER, this.name.get());
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.put("settings", this.settings.toTag());
      return tag;
   }

   public Profile fromTag(NbtCompound tag) {
      if (tag.contains("settings")) {
         this.settings.fromTag(tag.getCompoundOrEmpty("settings"));
      }

      return this;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Profile profile = (Profile)o;
         return Objects.equals(profile.name.get(), this.name.get());
      } else {
         return false;
      }
   }
}
