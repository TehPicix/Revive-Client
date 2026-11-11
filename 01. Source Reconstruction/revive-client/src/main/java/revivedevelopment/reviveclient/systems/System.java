package revivedevelopment.reviveclient.systems;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.crash.CrashException;
import org.apache.commons.io.FilenameUtils;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.files.StreamUtils;
import revivedevelopment.reviveclient.utils.misc.ISerializable;

public abstract class System<T> implements ISerializable<T> {
   private final String name;
   private File file;
   protected boolean isFirstInit;
   private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

   public System(String name) {
      this.name = name;
      if (name != null) {
         this.file = new File(ReviveClient.FOLDER, name + ".nbt");
         this.isFirstInit = !this.file.exists();
      }
   }

   public void init() {
   }

   public void save(File folder) {
      File file = this.getFile();
      if (file != null) {
         NbtCompound tag = this.toTag();
         if (tag != null) {
            try {
               File tempFile = File.createTempFile("revive-client", file.getName());
               NbtIo.write(tag, tempFile.toPath());
               if (folder != null) {
                  file = new File(folder, file.getName());
               }

               file.getParentFile().mkdirs();
               StreamUtils.copy(tempFile, file);
               tempFile.delete();
            } catch (IOException var5) {
               var5.printStackTrace();
            }
         }
      }
   }

   public void save() {
      this.save(null);
   }

   public void load(File folder) {
      File file = this.getFile();
      if (file != null) {
         try {
            if (folder != null) {
               file = new File(folder, file.getName());
            }

            if (file.exists()) {
               try {
                  this.fromTag(NbtIo.read(file.toPath()));
               } catch (CrashException var6) {
                  String backupName = FilenameUtils.removeExtension(file.getName()) + "-" + ZonedDateTime.now().format(DATE_TIME_FORMATTER) + ".backup.nbt";
                  File backup = new File(file.getParentFile(), backupName);
                  StreamUtils.copy(file, backup);
                  ReviveClient.LOG.error("Error loading {}. Possibly corrupted?", this.name);
                  ReviveClient.LOG.info("Saved settings backup to '{}'.", backup);
                  var6.printStackTrace();
               }
            }
         } catch (IOException var7) {
            var7.printStackTrace();
         }
      }
   }

   public void load() {
      this.load(null);
   }

   public File getFile() {
      return this.file;
   }

   public String getName() {
      return this.name;
   }

   @Override
   public NbtCompound toTag() {
      return null;
   }

   @Override
   public T fromTag(NbtCompound tag) {
      return null;
   }
}
