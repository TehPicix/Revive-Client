package revivedevelopment.reviveclient;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class Main {
   public static void main(String[] args) throws UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException, IllegalAccessException {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      int option = JOptionPane.showOptionDialog(
         null,
         "To install Revive Client you need to put it in your mods folder and run Fabric for latest Minecraft version.",
         "Revive Client",
         0,
         0,
         null,
         new String[]{"Open Wiki", "Open Mods Folder"},
         null
      );
      switch (option) {
         case 0:
            getOS().open("https://reviveclient.com/faq/installation");
            break;
         case 1:
            String path;
            switch (getOS()) {
               case WINDOWS:
                  path = System.getenv("AppData") + "/.minecraft/mods";
                  break;
               case OSX:
                  path = System.getProperty("user.home") + "/Library/Application Support/minecraft/mods";
                  break;
               default:
                  path = System.getProperty("user.home") + "/.minecraft";
            }

            File mods = new File(path);
            if (!mods.exists()) {
               mods.mkdirs();
            }

            getOS().open(mods);
      }
   }

   private static Main.OperatingSystem getOS() {
      String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
      if (os.contains("linux") || os.contains("unix")) {
         return Main.OperatingSystem.LINUX;
      } else if (os.contains("mac")) {
         return Main.OperatingSystem.OSX;
      } else {
         return os.contains("win") ? Main.OperatingSystem.WINDOWS : Main.OperatingSystem.UNKNOWN;
      }
   }

   private static enum OperatingSystem {
      LINUX,
      WINDOWS {
         @Override
         protected String[] getURLOpenCommand(URL url) {
            return new String[]{"rundll32", "url.dll,FileProtocolHandler", url.toString()};
         }
      },
      OSX {
         @Override
         protected String[] getURLOpenCommand(URL url) {
            return new String[]{"open", url.toString()};
         }
      },
      UNKNOWN;

      private OperatingSystem() {
      }

      public void open(URL url) {
         try {
            Runtime.getRuntime().exec(this.getURLOpenCommand(url));
         } catch (IOException var3) {
            var3.printStackTrace();
         }
      }

      public void open(String url) {
         try {
            this.open(new URI(url).toURL());
         } catch (MalformedURLException | URISyntaxException var3) {
            var3.printStackTrace();
         }
      }

      public void open(File file) {
         try {
            this.open(file.toURI().toURL());
         } catch (MalformedURLException var3) {
            var3.printStackTrace();
         }
      }

      protected String[] getURLOpenCommand(URL url) {
         String string = url.toString();
         if ("file".equals(url.getProtocol())) {
            string = string.replace("file:", "file://");
         }

         return new String[]{"xdg-open", string};
      }
   }
}
