package revivedevelopment.reviveclient.utils.other;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.network.Http;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;

public class SpotifyMediaBridge {
   private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
   private static final boolean LINUX = System.getProperty("os.name", "").toLowerCase().contains("linux");
   private static final long RESTART_BACKOFF_MS = 15000L;
   private static volatile SpotifyMediaBridge.Snapshot snapshot;
   private static volatile SpotifyMediaBridge.ArtData art;
   private static volatile boolean started;
   private static volatile String lastError = "";
   private static Process process;
   private static final Pattern DBUS_NUMBER = Pattern.compile("(?:uint64|int64|uint32|int32|uint16|int16|double)\\s+(-?[\\d.]+)");
   private static final String SCRIPT = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\nAdd-Type -AssemblyName System.Runtime.WindowsRuntime\n\n$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {\n    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and\n    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'\n})[0]\n\nfunction Await($WinRtTask, $ResultType) {\n    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)\n    $netTask = $asTask.Invoke($null, @($WinRtTask))\n    $netTask.Wait(-1) | Out-Null\n    $netTask.Result\n}\n\nfunction Emit($line) {\n    [Console]::Out.WriteLine($line)\n}\n\n$TAB = [string][char]9\n\ntry {\n    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]\n    $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]\n} catch {\n    Emit ('ERR' + $TAB + ('winrt types unavailable: ' + ($_.Exception.Message -replace \"[`t`r`n]\", ' ')))\n}\n\n$lastTrack = $null\n$artSeq = 0\n$mgr = $null\n\nwhile ($true) {\n    try {\n        if ($null -eq $mgr) {\n            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n        }\n\n        $session = $mgr.GetSessions() | Where-Object { $_.SourceAppUserModelId -like '*Spotify*' } | Select-Object -First 1\n        if ($null -eq $session) {\n            Emit 'NONE'\n        } else {\n            $media = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n            $tl = $session.GetTimelineProperties()\n            $pb = $session.GetPlaybackInfo()\n\n            $title = \"$($media.Title)\" -replace \"[`t`r`n]\", ' '\n            $artist = \"$($media.Artist)\" -replace \"[`t`r`n]\", ' '\n            $playing = if (\"$($pb.PlaybackStatus)\" -eq 'Playing') { '1' } else { '0' }\n            $posMs = [long]$tl.Position.TotalMilliseconds\n            $durMs = [long]$tl.EndTime.TotalMilliseconds\n            $updMs = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()\n\n            $track = \"$title|$artist\"\n            if ($track -ne $lastTrack) {\n                $lastTrack = $track\n                $artSeq++\n                $artB64 = ''\n                if ($null -ne $media.Thumbnail) {\n                    try {\n                        $stream = Await ($media.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n                        $size = [uint32]$stream.Size\n                        $reader = New-Object Windows.Storage.Streams.DataReader -ArgumentList $stream\n                        $null = Await ($reader.LoadAsync($size)) ([uint32])\n                        $bytes = New-Object byte[] ($size)\n                        $reader.ReadBytes($bytes)\n                        $reader.Dispose()\n                        $stream.Dispose()\n                        $artB64 = [Convert]::ToBase64String($bytes)\n                    } catch {\n                        Emit ('ERR' + $TAB + ('thumbnail: ' + ($_.Exception.Message -replace \"[`t`r`n]\", ' ')))\n                    }\n                }\n                Emit ('ART' + $TAB + $artSeq + $TAB + $artB64)\n            }\n\n            Emit ([string]::Join($TAB, @('DATA', $playing, $posMs, $durMs, $updMs, $artSeq, $title, $artist)))\n        }\n    } catch {\n        $mgr = $null\n        Emit ('ERR' + $TAB + ('poll: ' + ($_.Exception.Message -replace \"[`t`r`n]\", ' ')))\n        Emit 'NONE'\n    }\n    [Console]::Out.Flush()\n    Start-Sleep -Milliseconds 750\n}\n";

   private SpotifyMediaBridge() {
   }

   public static boolean isSupported() {
      return WINDOWS || LINUX;
   }

   public static SpotifyMediaBridge.Snapshot snapshot() {
      return snapshot;
   }

   public static SpotifyMediaBridge.ArtData art() {
      return art;
   }

   public static synchronized void start() {
      if (!started && isSupported()) {
         started = true;
         Thread thread = new Thread(WINDOWS ? SpotifyMediaBridge::runLoop : SpotifyMediaBridge::runLinuxLoop, "revive-spotify-media");
         thread.setDaemon(true);
         thread.start();
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = process;
            if (p != null) {
               p.destroy();
            }
         }, "revive-spotify-media-shutdown"));
      }
   }

   private static void runLoop() {
      while (true) {
         try {
            File script = writeScript();
            ProcessBuilder builder = new ProcessBuilder(
               "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.getAbsolutePath()
            );
            builder.redirectError(Redirect.DISCARD);
            Process p = builder.start();
            process = p;

            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
               while ((line = reader.readLine()) != null) {
                  parseLine(line);
               }
            }

            p.waitFor();
         } catch (InterruptedException var9) {
            return;
         } catch (Throwable var10) {
         }

         snapshot = null;

         try {
            Thread.sleep(15000L);
         } catch (InterruptedException var6) {
            return;
         }
      }
   }

   private static void parseLine(String line) {
      if (line.startsWith("NONE")) {
         snapshot = null;
      } else if (line.startsWith("ART\t")) {
         String[] parts = line.split("\t", 3);
         if (parts.length >= 2) {
            try {
               int seq = Integer.parseInt(parts[1]);
               byte[] bytes = parts.length >= 3 && !parts[2].isEmpty() ? Base64.getDecoder().decode(parts[2]) : new byte[0];
               art = new SpotifyMediaBridge.ArtData(seq, bytes);
            } catch (IllegalArgumentException var5) {
            }
         }
      } else if (line.startsWith("ERR\t")) {
         String message = line.substring(4);
         if (!message.equals(lastError)) {
            lastError = message;
            System.out.println("[SpotifyMediaBridge] " + message);
         }
      } else if (line.startsWith("DATA\t")) {
         String[] parts = line.split("\t", 8);
         if (parts.length >= 8) {
            try {
               snapshot = new SpotifyMediaBridge.Snapshot(
                  parts[6],
                  parts[7],
                  "1".equals(parts[1]),
                  Long.parseLong(parts[2]),
                  Long.parseLong(parts[3]),
                  Long.parseLong(parts[4]),
                  Integer.parseInt(parts[5])
               );
            } catch (NumberFormatException var4) {
            }
         }
      }
   }

   private static void runLinuxLoop() {
      int artSeq = 0;
      String lastArtUrl = "";

      while (true) {
         try {
            String meta = dbusGet("Metadata");
            if (meta == null) {
               snapshot = null;
            } else {
               String title = dbusString(meta, "xesam:title");
               String artist = dbusString(meta, "xesam:artist");
               long durationUs = dbusNumber(meta, "mpris:length");
               String artUrl = dbusString(meta, "mpris:artUrl");
               String positionOut = dbusGet("Position");
               long positionUs = positionOut == null ? 0L : dbusNumber(positionOut, null);
               String statusOut = dbusGet("PlaybackStatus");
               boolean playing = statusOut != null && statusOut.contains("Playing");
               if (artUrl != null && !artUrl.isEmpty() && !artUrl.equals(lastArtUrl)) {
                  lastArtUrl = artUrl;
                  downloadArt(artUrl, ++artSeq);
               }

               snapshot = new SpotifyMediaBridge.Snapshot(
                  title == null ? "" : title, artist == null ? "" : artist, playing, positionUs / 1000L, durationUs / 1000L, System.currentTimeMillis(), artSeq
               );
            }
         } catch (Throwable var14) {
            snapshot = null;
         }

         try {
            Thread.sleep(750L);
         } catch (InterruptedException var13) {
            return;
         }
      }
   }

   private static void downloadArt(String url, int seq) {
      ReviveExecutor.execute(() -> {
         byte[] bytes = new byte[0];

         try (InputStream in = Http.get(url).sendInputStream()) {
            if (in != null) {
               bytes = in.readAllBytes();
            }
         } catch (Throwable var8) {
            System.out.println("[SpotifyMediaBridge] album art download failed: " + var8);
         }

         SpotifyMediaBridge.ArtData current = art;
         if (current == null || current.seq() < seq) {
            art = new SpotifyMediaBridge.ArtData(seq, bytes);
         }
      });
   }

   private static String dbusGet(String property) {
      return exec(
         "dbus-send",
         "--session",
         "--print-reply",
         "--dest=org.mpris.MediaPlayer2.spotify",
         "/org/mpris/MediaPlayer2",
         "org.freedesktop.DBus.Properties.Get",
         "string:org.mpris.MediaPlayer2.Player",
         "string:" + property
      );
   }

   private static String dbusString(String out, String key) {
      int i = out.indexOf("\"" + key + "\"");
      if (i == -1) {
         return null;
      } else {
         int start = out.indexOf("string \"", i + key.length() + 2);
         if (start == -1) {
            return null;
         } else {
            start += 8;
            int end = out.indexOf(34, start);
            return end == -1 ? null : out.substring(start, end);
         }
      }
   }

   private static long dbusNumber(String out, String key) {
      int from = 0;
      if (key != null) {
         from = out.indexOf("\"" + key + "\"");
         if (from == -1) {
            return 0L;
         }
      }

      Matcher matcher = DBUS_NUMBER.matcher(out);
      if (!matcher.find(from)) {
         return 0L;
      } else {
         try {
            return (long)Double.parseDouble(matcher.group(1));
         } catch (NumberFormatException var5) {
            return 0L;
         }
      }
   }

   private static String exec(String... command) {
      try {
         Process p = new ProcessBuilder(command).redirectError(Redirect.DISCARD).start();
         byte[] out = p.getInputStream().readAllBytes();
         if (!p.waitFor(3L, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return null;
         } else {
            return p.exitValue() != 0 ? null : new String(out, StandardCharsets.UTF_8);
         }
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
         return null;
      } catch (Throwable var4) {
         return null;
      }
   }

   private static File writeScript() throws Exception {
      File folder = ReviveClient.FOLDER;
      if (!folder.exists()) {
         folder.mkdirs();
      }

      File script = new File(folder, "spotify_media.ps1");
      Files.write(
         script.toPath(),
         "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\nAdd-Type -AssemblyName System.Runtime.WindowsRuntime\n\n$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {\n    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and\n    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'\n})[0]\n\nfunction Await($WinRtTask, $ResultType) {\n    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)\n    $netTask = $asTask.Invoke($null, @($WinRtTask))\n    $netTask.Wait(-1) | Out-Null\n    $netTask.Result\n}\n\nfunction Emit($line) {\n    [Console]::Out.WriteLine($line)\n}\n\n$TAB = [string][char]9\n\ntry {\n    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]\n    $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]\n} catch {\n    Emit ('ERR' + $TAB + ('winrt types unavailable: ' + ($_.Exception.Message -replace \"[`t`r`n]\", ' ')))\n}\n\n$lastTrack = $null\n$artSeq = 0\n$mgr = $null\n\nwhile ($true) {\n    try {\n        if ($null -eq $mgr) {\n            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n        }\n\n        $session = $mgr.GetSessions() | Where-Object { $_.SourceAppUserModelId -like '*Spotify*' } | Select-Object -First 1\n        if ($null -eq $session) {\n            Emit 'NONE'\n        } else {\n            $media = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n            $tl = $session.GetTimelineProperties()\n            $pb = $session.GetPlaybackInfo()\n\n            $title = \"$($media.Title)\" -replace \"[`t`r`n]\", ' '\n            $artist = \"$($media.Artist)\" -replace \"[`t`r`n]\", ' '\n            $playing = if (\"$($pb.PlaybackStatus)\" -eq 'Playing') { '1' } else { '0' }\n            $posMs = [long]$tl.Position.TotalMilliseconds\n            $durMs = [long]$tl.EndTime.TotalMilliseconds\n            $updMs = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()\n\n            $track = \"$title|$artist\"\n            if ($track -ne $lastTrack) {\n                $lastTrack = $track\n                $artSeq++\n                $artB64 = ''\n                if ($null -ne $media.Thumbnail) {\n                    try {\n                        $stream = Await ($media.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n                        $size = [uint32]$stream.Size\n                        $reader = New-Object Windows.Storage.Streams.DataReader -ArgumentList $stream\n                        $null = Await ($reader.LoadAsync($size)) ([uint32])\n                        $bytes = New-Object byte[] ($size)\n                        $reader.ReadBytes($bytes)\n                        $reader.Dispose()\n                        $stream.Dispose()\n                        $artB64 = [Convert]::ToBase64String($bytes)\n                    } catch {\n                        Emit ('ERR' + $TAB + ('thumbnail: ' + ($_.Exception.Message -replace \"[`t`r`n]\", ' ')))\n                    }\n                }\n                Emit ('ART' + $TAB + $artSeq + $TAB + $artB64)\n            }\n\n            Emit ([string]::Join($TAB, @('DATA', $playing, $posMs, $durMs, $updMs, $artSeq, $title, $artist)))\n        }\n    } catch {\n        $mgr = $null\n        Emit ('ERR' + $TAB + ('poll: ' + ($_.Exception.Message -replace \"[`t`r`n]\", ' ')))\n        Emit 'NONE'\n    }\n    [Console]::Out.Flush()\n    Start-Sleep -Milliseconds 750\n}\n"
            .getBytes(StandardCharsets.UTF_8)
      );
      return script;
   }

   public record ArtData(int seq, byte[] bytes) {
   }

   public record Snapshot(String title, String artist, boolean playing, long positionMs, long durationMs, long positionReportedAtMs, int artSeq) {
      public long positionNowMs() {
         long pos = this.positionMs;
         if (this.playing) {
            pos += System.currentTimeMillis() - this.positionReportedAtMs;
         }

         if (this.durationMs > 0L) {
            pos = Math.min(pos, this.durationMs);
         }

         return Math.max(0L, pos);
      }
   }
}
