package revivedevelopment.reviveclient.pingbypass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.ColorListSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Module;

public final class PbModules {
   private PbModules() {
   }

   public static String encode(NbtCompound tag) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      NbtIo.writeCompressed(tag, out);
      return PbProtocol.b64(out.toByteArray());
   }

   public static NbtCompound decode(String encoded) throws IOException {
      byte[] data = PbProtocol.unb64(encoded);
      return NbtIo.readCompressed(new DataInputStream(new ByteArrayInputStream(data)), NbtSizeTracker.ofUnlimitedBytes());
   }

   private static boolean isSynced(Setting<?> setting) {
      return !(setting instanceof ColorSetting) && !(setting instanceof ColorListSetting);
   }

   public static NbtCompound settingsTag(Module module) {
      NbtCompound root = new NbtCompound();

      for (SettingGroup group : module.settings) {
         NbtCompound groupTag = new NbtCompound();

         for (Setting<?> setting : group) {
            if (isSynced(setting)) {
               try {
                  groupTag.put(setting.name, setting.toTag());
               } catch (Throwable var8) {
                  ReviveClient.LOG.debug("[PingBypass] could not serialise {}.{}", new Object[]{module.name, setting.name, var8});
               }
            }
         }

         root.put(group.name, groupTag);
      }

      return root;
   }

   public static String groupOf(Module module, Setting<?> setting) {
      for (SettingGroup group : module.settings) {
         for (Setting<?> s : group) {
            if (s == setting) {
               return group.name;
            }
         }
      }

      return null;
   }

   public static Setting<?> find(Module module, String groupName, String name) {
      for (SettingGroup group : module.settings) {
         if (group.name.equals(groupName)) {
            for (Setting<?> s : group) {
               if (s.name.equals(name)) {
                  return s;
               }
            }
         }
      }

      return null;
   }

   public static void applySettingsTag(Module module, NbtCompound root) {
      for (SettingGroup group : module.settings) {
         NbtCompound groupTag = root.getCompoundOrEmpty(group.name);

         for (Setting<?> setting : group) {
            if (isSynced(setting) && groupTag.get(setting.name) instanceof NbtCompound wanted) {
               try {
                  if (!wanted.equals(setting.toTag())) {
                     setting.fromTag(wanted);
                  }
               } catch (Throwable var10) {
                  ReviveClient.LOG.warn("[PingBypass] could not apply {}.{}", new Object[]{module.name, setting.name, var10});
               }
            }
         }
      }
   }
}
