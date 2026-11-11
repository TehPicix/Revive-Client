package revivedevelopment.reviveclient.gui.titlemenu;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.FloatControl.Type;
import javazoom.spi.vorbis.sampled.convert.VorbisFormatConversionProvider;
import javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.util.audio.TitleAudioAnalyzer;

public class TitleMusicPlayer {
   private static final TitleMusicPlayer INSTANCE = new TitleMusicPlayer();
   private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
   private static final boolean IS_LINUX = System.getProperty("os.name", "").toLowerCase().contains("linux");
   private static final String[] MENU_MUSIC_PATHS = new String[]{
      "assets/revive-client/music/menu_music4.ogg",
      "assets/revive-client/music/menu_music8.ogg",
      "assets/revive-client/music/menu_music7.ogg",
      "assets/revive-client/music/menu_music6.ogg",
      "assets/revive-client/music/menu_music5.ogg",
      "assets/revive-client/music/menu_music.ogg",
      "assets/revive-client/music/menu_music3.ogg",
      "assets/revive-client/music/menu_music2.ogg"
   };
   private final Random random = new Random();
   private long lastAudioActionTime = 0L;
   private static final long MIN_ACTION_INTERVAL = 200L;
   private boolean isPlaying = false;
   private float volume = 0.6F;
   private boolean menuMusicActive = false;
   private Clip currentClip;
   private FloatControl gainControl;
   private Process linuxProcess;
   private File linuxTempFile;
   private long linuxPlaybackStartNanos;
   private volatile String linuxSinkInputIndex = null;
   private volatile String currentTrackPath = null;
   private volatile String resumeTrackPath = null;
   private volatile long resumePositionMicros = 0L;
   private volatile int playGeneration = 0;

   private TitleMusicPlayer() {
   }

   public static TitleMusicPlayer getInstance() {
      return INSTANCE;
   }

   public static String[] getTrackPaths() {
      return MENU_MUSIC_PATHS;
   }

   public void playMenuMusic() {
      try {
         if (this.menuMusicActive && this.isPlaying) {
            return;
         }

         long now = System.currentTimeMillis();
         if (now - this.lastAudioActionTime < 200L) {
            return;
         }

         this.lastAudioActionTime = now;
         if (this.isPlaying) {
            this.stop();
         }

         this.playGeneration++;
         String track = this.resumeTrackPath;
         long pos = this.resumePositionMicros;
         if (track != null) {
            this.playFromResourceAt(track, pos);
            ReviveClient.LOG.info("[TitleScreen] Resuming '{}' at {}s", track, pos / 1000000L);
         } else {
            this.playRandomTrack();
         }

         this.menuMusicActive = true;
         ReviveClient.LOG.info("[TitleScreen] Menu music started");
      } catch (Exception var6) {
         ReviveClient.LOG.error("[TitleScreen] Failed to start menu music: " + var6.getMessage());
      }
   }

   public void playFromResource(String resourcePath) {
      int gen = this.playGeneration;
      this.audioExecutor.submit(() -> {
         try {
            if (gen != this.playGeneration) {
               return;
            }

            this.resumeTrackPath = null;
            this.resumePositionMicros = 0L;
            this.stopCurrent();
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
               ReviveClient.LOG.error("[TitleScreen] Audio resource not found: " + resourcePath);
               return;
            }

            this.currentTrackPath = resourcePath;
            this.menuMusicActive = true;
            if (IS_LINUX) {
               this.playResourceLinux(is);
            } else {
               this.setupAndPlayWindows(openPcmStream(is), 0L);
            }

            ReviveClient.LOG.info("[TitleScreen] Playing: " + resourcePath);
         } catch (Exception var4) {
            ReviveClient.LOG.error("[TitleScreen] Failed to play audio: {}", var4.getMessage(), var4);
         }
      });
   }

   public void playFromFile(String filePath) {
      int gen = this.playGeneration;
      this.audioExecutor.submit(() -> {
         try {
            if (gen != this.playGeneration) {
               return;
            }

            this.menuMusicActive = false;
            this.stopCurrent();
            File file = new File(filePath);
            if (!file.exists()) {
               ReviveClient.LOG.error("[TitleScreen] File not found: " + filePath);
               return;
            }

            if (IS_LINUX) {
               this.launchPaplay(file);
            } else {
               this.setupAndPlayWindows(openPcmStream(new FileInputStream(file)), 0L);
            }
         } catch (Exception var4) {
            ReviveClient.LOG.error("[TitleScreen] Failed to play file: " + var4.getMessage());
         }
      });
   }

   public void stop() {
      this.playGeneration++;
      this.resumeTrackPath = this.currentTrackPath;
      if (this.resumeTrackPath != null) {
         if (IS_LINUX) {
            this.resumePositionMicros = this.linuxPlaybackStartNanos > 0L ? (System.nanoTime() - this.linuxPlaybackStartNanos) / 1000L : 0L;
         } else {
            try {
               this.resumePositionMicros = this.currentClip != null ? this.currentClip.getMicrosecondPosition() : 0L;
            } catch (Exception var2) {
               this.resumePositionMicros = 0L;
            }
         }
      } else {
         this.resumePositionMicros = 0L;
      }

      this.menuMusicActive = false;
      this.stopCurrent();
      TitleAudioAnalyzer.getInstance().stopAnalysis();
   }

   public void setVolume(float volume) {
      this.volume = Math.max(0.0F, Math.min(1.0F, volume));
      if (this.gainControl != null) {
         float gain = this.volume > 0.0F ? 20.0F * (float)Math.log10(this.volume) : this.gainControl.getMinimum();
         this.gainControl.setValue(Math.max(this.gainControl.getMinimum(), Math.min(0.0F, gain)));
      }

      if (IS_LINUX && this.linuxSinkInputIndex != null && this.linuxProcess != null && this.linuxProcess.isAlive()) {
         this.applyLinuxVolume(this.volume);
      }
   }

   private void applyLinuxVolume(float vol) {
      try {
         int paVolume = Math.round(vol * 65536.0F);
         new ProcessBuilder("pactl", "set-sink-input-volume", this.linuxSinkInputIndex, String.valueOf(paVolume)).redirectErrorStream(true).start();
      } catch (Exception var3) {
         ReviveClient.LOG.warn("[TitleScreen] Live volume update failed: {}", var3.getMessage());
      }
   }

   private void findLinuxSinkInput(long pid) {
      Thread finder = new Thread(() -> {
         for (int attempt = 0; attempt < 15; attempt++) {
            try {
               Thread.sleep(200L);
               if (this.linuxProcess == null || !this.linuxProcess.isAlive()) {
                  return;
               }

               Process proc = new ProcessBuilder("pactl", "list", "sink-inputs").redirectErrorStream(true).start();
               String out = new String(proc.getInputStream().readAllBytes());
               proc.waitFor();

               for (String block : out.split("(?=Sink Input #)")) {
                  if (block.contains("application.process.id = \"" + pid + "\"")) {
                     int hash = block.indexOf(35) + 1;
                     int nl = block.indexOf(10);
                     if (hash > 0 && nl > hash) {
                        this.linuxSinkInputIndex = block.substring(hash, nl).trim();
                        ReviveClient.LOG.info("[TitleScreen] Sink-input index: {}", this.linuxSinkInputIndex);
                        return;
                     }
                  }
               }
            } catch (InterruptedException var12) {
               Thread.currentThread().interrupt();
               return;
            } catch (Exception var13) {
            }
         }
      });
      finder.setDaemon(true);
      finder.setName("revive-sink-finder");
      finder.start();
   }

   public float getVolume() {
      return this.volume;
   }

   public boolean isMenuActive() {
      return this.menuMusicActive;
   }

   public boolean isPlaying() {
      return IS_LINUX
         ? this.isPlaying && this.linuxProcess != null && this.linuxProcess.isAlive()
         : this.isPlaying && this.currentClip != null && this.currentClip.isRunning();
   }

   private static AudioInputStream openPcmStream(InputStream rawIn) throws Exception {
      AudioInputStream in = null;

      byte[] bytes;
      try {
         bytes = in.readAllBytes();
      } catch (Throwable var9) {
         if (rawIn != null) {
            try {
               in.close();
            } catch (Throwable var6) {
               var9.addSuppressed(var6);
            }
         }

         throw var9;
      }

      if (rawIn != null) {
         rawIn.close();
      }

      try {
         in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
      } catch (UnsupportedAudioFileException var8) {
         in = new VorbisAudioFileReader().getAudioInputStream(new ByteArrayInputStream(bytes));
      }

      AudioFormat fmt = in.getFormat();
      if (fmt.getEncoding() != Encoding.PCM_SIGNED) {
         AudioFormat pcm = new AudioFormat(Encoding.PCM_SIGNED, fmt.getSampleRate(), 16, fmt.getChannels(), fmt.getChannels() * 2, fmt.getSampleRate(), false);

         try {
            in = AudioSystem.getAudioInputStream(pcm, in);
         } catch (IllegalArgumentException var7) {
            in = new VorbisFormatConversionProvider().getAudioInputStream(pcm, in);
         }
      }

      return in;
   }

   private void playRandomTrack() {
      this.playFromResource(MENU_MUSIC_PATHS[this.random.nextInt(MENU_MUSIC_PATHS.length)]);
   }

   private void playFromResourceAt(String resourcePath, long skipMicros) {
      int gen = this.playGeneration;
      this.audioExecutor.submit(() -> {
         try {
            if (gen != this.playGeneration) {
               return;
            }

            this.resumeTrackPath = null;
            this.resumePositionMicros = 0L;
            this.stopCurrent();
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
               ReviveClient.LOG.error("[TitleScreen] Resume track not found: " + resourcePath);
               this.playRandomTrack();
               return;
            }

            this.currentTrackPath = resourcePath;
            this.menuMusicActive = true;
            if (IS_LINUX) {
               this.playResourceLinuxAt(is, skipMicros);
            } else {
               this.setupAndPlayWindows(openPcmStream(is), skipMicros);
            }
         } catch (Exception var6) {
            ReviveClient.LOG.error("[TitleScreen] Failed to resume audio: {}", var6.getMessage(), var6);
            this.playRandomTrack();
         }
      });
   }

   private void stopCurrent() {
      if (IS_LINUX) {
         this.stopLinuxProcess();
      } else {
         this.stopClip();
      }
   }

   private void stopClip() {
      Clip c = this.currentClip;
      this.currentClip = null;
      this.gainControl = null;
      if (c != null) {
         try {
            if (c.isRunning()) {
               c.stop();
            }

            c.close();
         } catch (Exception var3) {
         }
      }

      this.isPlaying = false;
   }

   private void setupAndPlayWindows(AudioInputStream audioStream, long skipMicros) {
      try {
         AudioFormat format = audioStream.getFormat();
         if (format.getEncoding() != Encoding.PCM_SIGNED) {
            AudioFormat decoded = new AudioFormat(
               Encoding.PCM_SIGNED, format.getSampleRate(), 16, format.getChannels(), format.getChannels() * 2, format.getSampleRate(), false
            );
            audioStream = AudioSystem.getAudioInputStream(decoded, audioStream);
            format = audioStream.getFormat();
         }

         byte[] pcm = audioStream.readAllBytes();
         int frameSize = format.getFrameSize() > 0 ? format.getFrameSize() : format.getChannels() * 2;
         audioStream = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / frameSize);
         Info info = new Info(Clip.class, format);

         try {
            this.currentClip = (Clip)AudioSystem.getLine(info);
         } catch (Exception var15) {
            for (javax.sound.sampled.Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
               try {
                  Mixer mixer = AudioSystem.getMixer(mixerInfo);
                  if (mixer.isLineSupported(info)) {
                     this.currentClip = (Clip)mixer.getLine(info);
                     break;
                  }
               } catch (Exception var14) {
                  ReviveClient.LOG.warn("[TitleScreen] Mixer failed: {}", var14.getMessage());
               }
            }

            if (this.currentClip == null) {
               throw new LineUnavailableException("No suitable audio line found");
            }
         }

         this.currentClip.open(audioStream);
         if (this.currentClip.isControlSupported(Type.MASTER_GAIN)) {
            this.gainControl = (FloatControl)this.currentClip.getControl(Type.MASTER_GAIN);
            this.setVolume(this.volume);
            ReviveClient.LOG
               .info(
                  "[TitleScreen] MASTER_GAIN set to {}dB (volume={}, range=[{}, {}])",
                  new Object[]{
                     String.format("%.1f", this.gainControl.getValue()),
                     String.format("%.2f", this.volume),
                     String.format("%.1f", this.gainControl.getMinimum()),
                     String.format("%.1f", this.gainControl.getMaximum())
                  }
               );
         } else {
            ReviveClient.LOG.warn("[TitleScreen] MASTER_GAIN not supported — audio plays at system default volume");
         }

         if (skipMicros > 0L) {
            long clipLen = this.currentClip.getMicrosecondLength();
            if (skipMicros < clipLen) {
               this.currentClip.setMicrosecondPosition(skipMicros);
            }
         }

         Clip thisClip = this.currentClip;
         thisClip.addLineListener(event -> {
            if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
               this.isPlaying = false;
               if (this.menuMusicActive && thisClip == this.currentClip && thisClip.getMicrosecondPosition() >= thisClip.getMicrosecondLength()) {
                  this.playRandomTrack();
               }
            }
         });
         this.currentClip.start();
         this.isPlaying = true;
         TitleAudioAnalyzer.getInstance().analyzeTrack(pcm, format, () -> {
            Clip c = this.currentClip;
            return c != null ? c.getMicrosecondPosition() : 0L;
         });
      } catch (Exception var16) {
         ReviveClient.LOG.error("[TitleScreen] Audio setup error: {}", var16.getMessage(), var16);
      }
   }

   private void stopLinuxProcess() {
      this.linuxSinkInputIndex = null;
      Process p = this.linuxProcess;
      this.linuxProcess = null;
      if (p != null && p.isAlive()) {
         p.destroyForcibly();
      }

      File f = this.linuxTempFile;
      this.linuxTempFile = null;
      if (f != null) {
         f.delete();
      }

      this.isPlaying = false;
   }

   private void playResourceLinux(InputStream rawIs) throws Exception {
      AudioInputStream audioStream = openPcmStream(rawIs);
      AudioFormat format = audioStream.getFormat();
      byte[] pcm = audioStream.readAllBytes();
      audioStream.close();
      int frameSize = format.getFrameSize() > 0 ? format.getFrameSize() : format.getChannels() * 2;
      File tempFile = File.createTempFile("revive_music_", ".wav");
      tempFile.deleteOnExit();

      try (
         FileOutputStream fos = new FileOutputStream(tempFile);
         AudioInputStream writeStream = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / frameSize);
      ) {
         AudioSystem.write(writeStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, fos);
      }

      this.linuxTempFile = tempFile;
      this.launchPaplayCore(tempFile, pcm, format);
   }

   private void playResourceLinuxAt(InputStream rawIs, long skipMicros) throws Exception {
      AudioInputStream audioStream = openPcmStream(rawIs);
      AudioFormat format = audioStream.getFormat();
      byte[] fullPcm = audioStream.readAllBytes();
      audioStream.close();
      int frameSize = format.getFrameSize();
      long skipBytes = 0L;
      if (skipMicros > 0L) {
         skipBytes = (long)(skipMicros / 1000000.0 * format.getFrameRate()) * frameSize;
         if (skipBytes >= fullPcm.length) {
            skipBytes = 0L;
         } else {
            skipBytes = skipBytes / frameSize * frameSize;
         }
      }

      byte[] playPcm = skipBytes > 0L ? Arrays.copyOfRange(fullPcm, (int)skipBytes, fullPcm.length) : fullPcm;
      File tempFile = File.createTempFile("revive_music_", ".wav");
      tempFile.deleteOnExit();

      try (
         FileOutputStream fos = new FileOutputStream(tempFile);
         AudioInputStream writeStream = new AudioInputStream(new ByteArrayInputStream(playPcm), format, playPcm.length / frameSize);
      ) {
         AudioSystem.write(writeStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, fos);
      }

      this.linuxTempFile = tempFile;
      this.launchPaplayCore(tempFile, playPcm, format);
   }

   private void launchPaplay(File file) throws Exception {
      AudioInputStream audioStream = openPcmStream(new FileInputStream(file));
      AudioFormat format = audioStream.getFormat();
      byte[] pcm = audioStream.readAllBytes();
      audioStream.close();
      int frameSize = format.getFrameSize() > 0 ? format.getFrameSize() : format.getChannels() * 2;
      File tempFile = File.createTempFile("revive_music_", ".wav");
      tempFile.deleteOnExit();

      try (
         FileOutputStream fos = new FileOutputStream(tempFile);
         AudioInputStream writeStream = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / frameSize);
      ) {
         AudioSystem.write(writeStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, fos);
      }

      this.linuxTempFile = tempFile;
      this.launchPaplayCore(tempFile, pcm, format);
   }

   private void launchPaplayCore(File wavFile, byte[] pcm, AudioFormat format) throws Exception {
      int paVolume = Math.round(this.volume * 65536.0F);
      Process proc = new ProcessBuilder("paplay", "--volume=" + paVolume, wavFile.getAbsolutePath()).redirectErrorStream(true).start();
      this.linuxSinkInputIndex = null;
      this.linuxProcess = proc;
      this.linuxPlaybackStartNanos = System.nanoTime();
      this.isPlaying = true;
      ReviveClient.LOG.info("[TitleScreen] Linux: paplay launched (pid={}, volume={})", proc.pid(), paVolume);
      this.findLinuxSinkInput(proc.pid());
      Thread watcher = new Thread(() -> {
         try {
            int exitCode = proc.waitFor();
            if (exitCode == 0 && this.menuMusicActive && this.linuxProcess == proc) {
               this.isPlaying = false;
               this.playRandomTrack();
            }
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
         }
      });
      watcher.setDaemon(true);
      watcher.setName("revive-music-watcher");
      watcher.start();
      TitleAudioAnalyzer.getInstance()
         .analyzeTrack(
            pcm, format, () -> this.linuxProcess != null && this.linuxProcess.isAlive() ? (System.nanoTime() - this.linuxPlaybackStartNanos) / 1000L : 0L
         );
   }
}
