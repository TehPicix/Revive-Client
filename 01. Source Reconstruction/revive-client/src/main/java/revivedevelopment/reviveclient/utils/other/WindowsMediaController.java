package revivedevelopment.reviveclient.utils.other;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class WindowsMediaController {
   public static String getCurrentMedia() {
      try {
         ProcessBuilder builder = new ProcessBuilder(
            "powershell",
            "-Command",
            "(Get-Process -Name vlc, chrome, firefox, msedge -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match '-' }) | Select-Object -ExpandProperty MainWindowTitle"
         );
         builder.redirectErrorStream(true);
         Process process = builder.start();
         BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
         String title = reader.readLine();
         return title != null && !title.isEmpty() ? title : "Not Playing";
      } catch (Exception var4) {
         return "Not Playing";
      }
   }

   public static void playPause() {
      sendMediaCommand("playpause");
   }

   public static void nextTrack() {
      sendMediaCommand("next");
   }

   public static void previousTrack() {
      sendMediaCommand("prev");
   }

   private static void sendMediaCommand(String command) {
      try {
         new ProcessBuilder("powershell", "-Command", "(New-Object -ComObject WScript.Shell).SendKeys('^{VK_MEDIA_" + command.toUpperCase() + "}')").start();
      } catch (IOException var2) {
         System.err.println("Failed to send media command: " + command);
      }
   }
}
