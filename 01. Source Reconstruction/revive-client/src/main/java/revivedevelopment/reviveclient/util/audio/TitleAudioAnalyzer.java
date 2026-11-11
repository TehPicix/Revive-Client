package revivedevelopment.reviveclient.util.audio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import javax.sound.sampled.AudioFormat;
import revivedevelopment.reviveclient.ReviveClient;

public class TitleAudioAnalyzer {
   private static final TitleAudioAnalyzer INSTANCE = new TitleAudioAnalyzer();
   private static final int SAMPLE_RATE = 44100;
   private static final int FFT_SIZE = 2048;
   private static final int BASS_MAX_FREQ = 250;
   private static final int MID_MAX_FREQ = 2000;
   private float bassLevel = 0.0F;
   private float midLevel = 0.0F;
   private float trebleLevel = 0.0F;
   private float volumeLevel = 0.0F;
   private float[] spectrum = new float[1024];
   private float bassEnergyAvg = 0.0F;
   private float trebleEnergyAvg = 0.0F;
   private float bassMax = 1.0E-4F;
   private float midMax = 1.0E-4F;
   private float trebleMax = 1.0E-4F;
   private float volumeMax = 1.0E-4F;
   private float bassMin = 0.0F;
   private float midMin = 0.0F;
   private float trebleMin = 0.0F;
   private float volumeMin = 0.0F;
   private long lastBassPeakTime = 0L;
   private long lastTreblePeakTime = 0L;
   private static final long BASS_BEAT_COOLDOWN = 110L;
   private static final long TREBLE_BEAT_COOLDOWN = 80L;
   private static final float BASS_BEAT_SENSITIVITY = 1.3F;
   private static final float TREBLE_BEAT_SENSITIVITY = 1.35F;
   private static final float BEAT_MIN_LEVEL = 0.12F;
   private boolean bassBeat = false;
   private boolean trebleBeat = false;
   private volatile boolean bassBeatPending = false;
   private volatile boolean trebleBeatPending = false;
   private boolean analyzing = false;
   private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
   private TitleAudioAnalyzer.FFT fft;
   private volatile boolean trackMode = false;
   private byte[] trackPcm;
   private float trackSampleRate;
   private int trackFrameSize;
   private int trackChannels;
   private LongSupplier trackPosition;
   private final float bassSmoothing = 0.4F;
   private final float midSmoothing = 0.35F;
   private final float trebleSmoothing = 0.3F;
   private final float volumeSmoothing = 0.2F;

   private TitleAudioAnalyzer() {
      this.fft = new TitleAudioAnalyzer.FFT(2048);
   }

   public static TitleAudioAnalyzer getInstance() {
      return INSTANCE;
   }

   public void stopAnalysis() {
      this.analyzing = false;
      this.trackMode = false;
      this.trackPcm = null;
      this.trackPosition = null;
      this.bassLevel = 0.0F;
      this.midLevel = 0.0F;
      this.trebleLevel = 0.0F;
      this.volumeLevel = 0.0F;
      this.bassMax = this.midMax = this.trebleMax = this.volumeMax = 1.0E-4F;
      this.bassMin = this.midMin = this.trebleMin = this.volumeMin = 0.0F;
      this.bassEnergyAvg = this.trebleEnergyAvg = 0.0F;
   }

   public void analyzeTrack(byte[] pcm, AudioFormat format, LongSupplier positionMicros) {
      this.stopAnalysis();
      this.trackPcm = pcm;
      this.trackSampleRate = format.getSampleRate();
      this.trackFrameSize = format.getFrameSize() > 0 ? format.getFrameSize() : format.getChannels() * 2;
      this.trackChannels = format.getChannels();
      this.trackPosition = positionMicros;
      this.trackMode = true;
      this.analysisExecutor.submit(() -> {
         this.analyzing = true;
         ReviveClient.LOG.info("[TitleScreen] Visualizer locked to track samples");
         this.analyzeTrackLoop();
      });
   }

   private void analyzeTrackLoop() {
      float[] samples = new float[2048];
      long loopStart = System.nanoTime();
      long lastRawPos = -1L;
      int stuckCount = 0;
      boolean useWallClock = false;

      while (this.analyzing && this.trackMode) {
         byte[] pcm = this.trackPcm;
         LongSupplier pos = this.trackPosition;
         if (pcm == null || pos == null) {
            break;
         }

         long rawPos = pos.getAsLong();
         if (rawPos == lastRawPos) {
            stuckCount++;
         } else {
            stuckCount = 0;
         }

         lastRawPos = rawPos;
         if (stuckCount > 6) {
            useWallClock = true;
         }

         long posMicros = !useWallClock && rawPos > 0L ? rawPos : (System.nanoTime() - loopStart) / 1000L;
         long frame = (long)(posMicros / 1000000.0 * this.trackSampleRate);
         int base = (int)(frame * this.trackFrameSize);
         int span = 2048 * this.trackFrameSize;
         if (pcm.length > span) {
            base %= pcm.length - span;
         }

         if (base < 0) {
            base = 0;
         }

         float volume = 0.0F;

         for (int i = 0; i < 2048; i++) {
            int off = base + i * this.trackFrameSize;
            float sample = 0.0F;
            if (off >= 0 && off + 1 < pcm.length) {
               int left = (short)(pcm[off] & 255 | pcm[off + 1] << 8);
               int right = left;
               if (this.trackChannels > 1 && off + 3 < pcm.length) {
                  right = (short)(pcm[off + 2] & 255 | pcm[off + 3] << 8);
               }

               sample = (left + right) / 65536.0F;
            }

            volume += Math.abs(sample);
            samples[i] = sample * (0.5F - 0.5F * (float)Math.cos((Math.PI * 2) * i / 2047.0));
         }

         this.processSamples(samples, volume / 2048.0F);

         try {
            Thread.sleep(16L);
         } catch (InterruptedException var24) {
            Thread.currentThread().interrupt();
            break;
         }
      }
   }

   private static float trackMax(float m, float cur) {
      return cur > m ? cur : m + (cur - m) * 0.01F;
   }

   private static float trackMin(float m, float cur) {
      return cur < m ? cur : m + (cur - m) * 0.01F;
   }

   private static float expand(float cur, float min, float max) {
      float range = Math.max(1.0E-4F, max - min);
      float t = Math.max(0.0F, Math.min(1.0F, (cur - min) / range));
      return t * t * (3.0F - 2.0F * t);
   }

   private void processSamples(float[] samples, float volume) {
      this.fft.forward(samples);
      float currentBass = 0.0F;
      float currentMid = 0.0F;
      float currentTreble = 0.0F;
      int bassCount = 0;
      int midCount = 0;
      int trebleCount = 0;
      int bassMaxBin = 11;
      int midMaxBin = 92;

      for (int i = 1; i < 1024; i++) {
         float magnitude = this.fft.getMagnitude(i);
         this.spectrum[i] = magnitude;
         if (i < bassMaxBin) {
            currentBass += magnitude * 1.5F;
            bassCount++;
         } else if (i > midMaxBin) {
            currentTreble += magnitude * 1.2F;
            trebleCount++;
         } else {
            currentMid += magnitude * 1.3F;
            midCount++;
         }
      }

      if (bassCount > 0) {
         currentBass /= bassCount;
      }

      if (midCount > 0) {
         currentMid /= midCount;
      }

      if (trebleCount > 0) {
         currentTreble /= trebleCount;
      }

      this.bassMax = trackMax(this.bassMax, currentBass);
      this.bassMin = trackMin(this.bassMin, currentBass);
      this.midMax = trackMax(this.midMax, currentMid);
      this.midMin = trackMin(this.midMin, currentMid);
      this.trebleMax = trackMax(this.trebleMax, currentTreble);
      this.trebleMin = trackMin(this.trebleMin, currentTreble);
      this.volumeMax = trackMax(this.volumeMax, volume);
      this.volumeMin = trackMin(this.volumeMin, volume);
      float nBass = expand(currentBass, this.bassMin, this.bassMax);
      float nMid = expand(currentMid, this.midMin, this.midMax);
      float nTreble = expand(currentTreble, this.trebleMin, this.trebleMax);
      float nVolume = expand(volume, this.volumeMin, this.volumeMax);
      this.bassLevel = this.bassLevel * 0.4F + nBass * 0.6F;
      this.midLevel = this.midLevel * 0.35F + nMid * 0.65F;
      this.trebleLevel = this.trebleLevel * 0.3F + nTreble * 0.7F;
      this.volumeLevel = this.volumeLevel * 0.2F + nVolume * 0.8F;
      long currentTime = System.currentTimeMillis();
      this.bassEnergyAvg = this.bassEnergyAvg * 0.95F + currentBass * 0.05F;
      if (currentBass > this.bassEnergyAvg * 1.3F && nBass > 0.12F && currentTime - this.lastBassPeakTime > 110L) {
         this.bassBeat = true;
         this.bassBeatPending = true;
         this.lastBassPeakTime = currentTime;
      } else {
         this.bassBeat = false;
      }

      this.trebleEnergyAvg = this.trebleEnergyAvg * 0.93F + currentTreble * 0.07F;
      if (currentTreble > this.trebleEnergyAvg * 1.35F && nTreble > 0.12F && currentTime - this.lastTreblePeakTime > 80L) {
         this.trebleBeat = true;
         this.trebleBeatPending = true;
         this.lastTreblePeakTime = currentTime;
      } else {
         this.trebleBeat = false;
      }
   }

   public float getBassLevel() {
      return this.bassLevel;
   }

   public float getMidLevel() {
      return this.midLevel;
   }

   public float getTrebleLevel() {
      return this.trebleLevel;
   }

   public float getVolumeLevel() {
      return this.volumeLevel;
   }

   public boolean consumeBassBeat() {
      if (this.bassBeatPending) {
         this.bassBeatPending = false;
         return true;
      } else {
         return false;
      }
   }

   public boolean consumeTrebleBeat() {
      if (this.trebleBeatPending) {
         this.trebleBeatPending = false;
         return true;
      } else {
         return false;
      }
   }

   private static class FFT {
      private final int n;
      private final float[] real;
      private final float[] imag;

      public FFT(int n) {
         this.n = n;
         this.real = new float[n];
         this.imag = new float[n];
      }

      public void forward(float[] samples) {
         for (int i = 0; i < this.n; i++) {
            this.real[i] = i < samples.length ? samples[i] : 0.0F;
            this.imag[i] = 0.0F;
         }

         this.applyWindow();
         int j = 0;

         for (int i = 0; i < this.n - 1; i++) {
            if (i < j) {
               float temp = this.real[i];
               this.real[i] = this.real[j];
               this.real[j] = temp;
               temp = this.imag[i];
               this.imag[i] = this.imag[j];
               this.imag[j] = temp;
            }

            int k;
            for (k = this.n / 2; k <= j; k /= 2) {
               j -= k;
            }

            j += k;
         }

         for (int step = 1; step < this.n; step *= 2) {
            float psr = (float)Math.cos(Math.PI / step);
            float psi = (float)(-Math.sin(Math.PI / step));
            float csr = 1.0F;
            float csi = 0.0F;

            for (int group = 0; group < step; group++) {
               for (int pair = group; pair < this.n; pair += 2 * step) {
                  int match = pair + step;
                  float tr = this.real[match] * csr - this.imag[match] * csi;
                  float ti = this.real[match] * csi + this.imag[match] * csr;
                  this.real[match] = this.real[pair] - tr;
                  this.imag[match] = this.imag[pair] - ti;
                  this.real[pair] = this.real[pair] + tr;
                  this.imag[pair] = this.imag[pair] + ti;
               }

               float next = csr * psr - csi * psi;
               csi = csr * psi + csi * psr;
               csr = next;
            }
         }
      }

      private void applyWindow() {
         for (int i = 0; i < this.n; i++) {
            this.real[i] = this.real[i] * (0.5F * (1.0F - (float)Math.cos((Math.PI * 2) * i / (this.n - 1))));
         }
      }

      public float getMagnitude(int index) {
         return index >= 0 && index < this.n / 2 ? (float)Math.sqrt(this.real[index] * this.real[index] + this.imag[index] * this.imag[index]) : 0.0F;
      }
   }
}
