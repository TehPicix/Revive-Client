package revivedevelopment.reviveclient.systems.modules.render;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.imageio.ImageIO;
import meteordevelopment.orbit.EventHandler;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;

public class BetterScreenshot extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> notify = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("notify")
            .description("Sends a chat message after a screenshot has been copied to your clipboard.")
            .defaultValue(true)
            .build()
      );
   private final Set<String> processed = new HashSet<>();
   private final Map<String, Long> pending = new HashMap<>();
   private final ConcurrentLinkedQueue<String> copied = new ConcurrentLinkedQueue<>();

   public BetterScreenshot() {
      super(Categories.Render, "better-screenshot", "Copies screenshots to your clipboard as you take them.");
   }

   @Override
   public void onActivate() {
      this.processed.clear();
      this.pending.clear();
      this.copied.clear();
      File[] existing = this.listScreenshots();
      if (existing != null) {
         for (File file : existing) {
            this.processed.add(file.getName());
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      String name;
      while ((name = this.copied.poll()) != null) {
         if (this.notify.get()) {
            this.info("Copied (highlight)%s(default) to your clipboard.", new Object[]{name});
         }
      }

      File[] files = this.listScreenshots();
      if (files != null) {
         for (File file : files) {
            String fileName = file.getName();
            if (!this.processed.contains(fileName)) {
               long modified = file.lastModified();
               Long previous = this.pending.get(fileName);
               if (previous != null && previous == modified) {
                  this.processed.add(fileName);
                  this.pending.remove(fileName);
                  this.copyToClipboard(file);
               } else {
                  this.pending.put(fileName, modified);
               }
            }
         }
      }
   }

   private void copyToClipboard(File file) {
      ReviveExecutor.execute(() -> {
         try {
            BufferedImage image = null;

            for (int attempt = 0; attempt < 20 && image == null; attempt++) {
               if (file.length() > 0L) {
                  try {
                     image = ImageIO.read(file);
                  } catch (IOException var5) {
                  }
               }

               if (image == null) {
                  Thread.sleep(100L);
               }
            }

            if (image == null) {
               this.error("Failed to read (highlight)%s(default).", new Object[]{file.getName()});
               return;
            }

            try {
               Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
               clipboard.setContents(new BetterScreenshot.TransferableImage(image), null);
            } catch (Throwable var6) {
               if (!this.copyWithNativeTool(file)) {
                  throw var6;
               }
            }

            this.copied.add(file.getName());
         } catch (Throwable var7) {
            String reason = var7.getMessage() != null ? var7.getMessage() : var7.getClass().getSimpleName();
            this.error("Failed to copy screenshot to clipboard: %s", new Object[]{reason});
         }
      });
   }

   private boolean copyWithNativeTool(File file) throws Exception {
      String os = System.getProperty("os.name", "").toLowerCase();
      if (!os.contains("win")) {
         return false;
      } else {
         String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; $p = @'\n"
            + file.getAbsolutePath()
            + "\n'@; [System.Windows.Forms.Clipboard]::SetImage([System.Drawing.Bitmap]::FromFile($p))";
         Process process = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", script).redirectErrorStream(true).start();
         return process.waitFor() == 0;
      }
   }

   private File[] listScreenshots() {
      File folder = new File(this.mc.runDirectory, "screenshots");
      return !folder.isDirectory() ? null : folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
   }

   static {
      if (Boolean.getBoolean("java.awt.headless")) {
         System.setProperty("java.awt.headless", "false");
      }
   }

   private record TransferableImage(Image image) implements Transferable {
      @Override
      public DataFlavor[] getTransferDataFlavors() {
         return new DataFlavor[]{DataFlavor.imageFlavor};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
         return DataFlavor.imageFlavor.equals(flavor);
      }

      @Override
      public Object getTransferData(DataFlavor flavor) {
         return this.image;
      }
   }
}
