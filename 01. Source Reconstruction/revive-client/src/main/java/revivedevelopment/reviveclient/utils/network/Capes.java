package revivedevelopment.reviveclient.utils.network;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.utils.PreInit;

public class Capes {
   private static final String API_HOST = apiHost();
   private static final String CAPE_OWNERS_URL = API_HOST + "/api/capeowners";
   private static final String CAPES_URL = API_HOST + "/api/capes";
   private static final int REFRESH_TICKS = 6000;
   private static final long RETRY_MIN_MS = 10000L;
   private static final long RETRY_MAX_MS = 300000L;
   private static final int DEFAULT_FRAME_TICKS = 2;
   private static final long MS_PER_TICK = 50L;
   private static final long REQUEST_TIMEOUT_SECONDS = 20L;
   private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).followRedirects(Redirect.NORMAL).build();
   private static volatile Map<UUID, Capes.Cape> OWNERS = Map.of();
   private static final Map<String, Capes.Cape> CAPES = new ConcurrentHashMap<>();
   private static final Queue<Runnable> MAIN_THREAD = new ConcurrentLinkedQueue<>();
   private static final Object FETCH_LOCK = new Object();
   private static int fetchCounter;
   private static int publishedFetch;
   private static int refreshTimer;

   private Capes() {
   }

   @PreInit(
      dependencies = {ReviveExecutor.class}
   )
   public static void init() {
      OWNERS = Map.of();
      CAPES.clear();
      MAIN_THREAD.clear();
      refreshTimer = 6000;
      ReviveExecutor.execute(Capes::fetch);
      ReviveClient.EVENT_BUS.subscribe(Capes.class);
   }

   @EventHandler
   private static void onTick(TickEvent.Post event) {
      Runnable task;
      while ((task = MAIN_THREAD.poll()) != null) {
         task.run();
      }

      if (--refreshTimer <= 0) {
         refreshTimer = 6000;
         ReviveExecutor.execute(Capes::fetch);
      }
   }

   public static Identifier get(PlayerEntity player) {
      Capes.Cape cape = OWNERS.get(player.getUuid());
      return cape == null ? null : cape.texture();
   }

   private static void fetch() {
      int fetchId;
      synchronized (FETCH_LOCK) {
         fetchId = ++fetchCounter;
      }

      String capes = getString(CAPES_URL);
      if (capes != null) {
         String owners = getString(CAPE_OWNERS_URL);
         if (owners != null) {
            for (String line : capes.split("\n")) {
               String[] split = line.trim().split(" ");
               if (split.length >= 2) {
                  String url = resolve(split[1]);
                  if (url != null) {
                     Capes.Cape cape = CAPES.computeIfAbsent(split[0], Capes.Cape::new);
                     cape.url = url;
                     cape.frameTimes = split.length > 2 ? parseFrameTimes(split[2]) : null;
                  }
               }
            }

            Map<UUID, Capes.Cape> map = new HashMap<>();

            for (String linex : owners.split("\n")) {
               String[] split = linex.trim().split(" ");
               if (split.length >= 2) {
                  Capes.Cape cape = CAPES.get(split[1]);
                  if (cape != null) {
                     try {
                        map.put(UUID.fromString(split[0]), cape);
                     } catch (IllegalArgumentException var12) {
                     }
                  }
               }
            }

            synchronized (FETCH_LOCK) {
               if (fetchId < publishedFetch) {
                  return;
               }

               publishedFetch = fetchId;
               OWNERS = Collections.unmodifiableMap(map);
            }

            Set<Capes.Cape> inUse = new HashSet<>(map.values());
            MAIN_THREAD.add(() -> inUse.forEach(Capes.Cape::texture));
         }
      }
   }

   static int[] parseFrameTimes(String spec) {
      String[] parts = spec.split(",");
      int[] times = new int[parts.length];

      for (int i = 0; i < parts.length; i++) {
         try {
            times[i] = Integer.parseInt(parts[i].trim());
         } catch (NumberFormatException var5) {
            return null;
         }

         if (times[i] <= 0) {
            return null;
         }
      }

      return times;
   }

   private static String resolve(String url) {
      try {
         URI uri = URI.create(API_HOST + "/").resolve(url);
         String scheme = uri.getScheme();
         return !"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme) ? null : uri.toString();
      } catch (IllegalArgumentException var3) {
         return null;
      }
   }

   private static String getString(String url) {
      byte[] body = getBytes(url);
      return body == null ? null : new String(body, StandardCharsets.UTF_8);
   }

   private static byte[] getBytes(String url) {
      HttpRequest request;
      try {
         request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20L)).header("User-Agent", "Revive-Client").GET().build();
      } catch (IllegalArgumentException var5) {
         ReviveClient.LOG.warn("Invalid cape URL {}", url);
         return null;
      }

      CompletableFuture<HttpResponse<byte[]>> future = null;

      try {
         future = CLIENT.sendAsync(request, BodyHandlers.ofByteArray());
         HttpResponse<byte[]> response = future.get(20L, TimeUnit.SECONDS);
         if (response.statusCode() != 200) {
            ReviveClient.LOG.warn("Could not load {}: HTTP {}", url, response.statusCode());
            return null;
         }

         return response.body();
      } catch (TimeoutException var6) {
         future.cancel(true);
         ReviveClient.LOG.warn("Could not load {}: timed out", url);
      } catch (InterruptedException var7) {
         future.cancel(true);
         Thread.currentThread().interrupt();
      } catch (ExecutionException var8) {
         Throwable cause = (Throwable)(var8.getCause() != null ? var8.getCause() : var8);
         ReviveClient.LOG.warn("Could not load {}: {}", url, cause.toString());
      } catch (RuntimeException var9) {
         ReviveClient.LOG.warn("Could not load {}: {}", url, var9.toString());
      }

      return null;
   }

   private static String apiHost() {
      String host = System.getProperty("revive.loader.host");
      if (host == null || host.isBlank()) {
         host = System.getenv("REVIVE_LOADER_HOST");
      }

      if (host == null || host.isBlank()) {
         host = "https://revive-client.com";
      }

      host = host.trim();

      while (host.endsWith("/")) {
         host = host.substring(0, host.length() - 1);
      }

      return host;
   }

   private static class Cape {
      private final String name;
      private final Identifier identifier;
      private volatile String url;
      private volatile int[] frameTimes;
      private String loadedUrl;
      private String failedUrl;
      private int registeredFrames;
      private boolean downloading;
      private int failures;
      private long retryAt;
      private int[] frameEnds = new int[0];
      private int[] appliedTimes;
      private int cycleTicks;

      Cape(String name) {
         this.name = name;
         this.identifier = ReviveClient.identifier("capes/" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"));
      }

      Identifier texture() {
         String target = this.url;
         if (target != null && this.failedUrl != null && !target.equals(this.failedUrl)) {
            this.failedUrl = null;
            this.failures = 0;
            this.retryAt = 0L;
         }

         if (target != null && !target.equals(this.loadedUrl) && !this.downloading && System.currentTimeMillis() >= this.retryAt) {
            this.download(target);
         }

         if (this.registeredFrames == 0) {
            return null;
         } else {
            return this.registeredFrames == 1 ? this.identifier : this.frameIdentifier(this.currentFrame());
         }
      }

      private Identifier frameIdentifier(int frame) {
         return frame == 0 ? this.identifier : this.identifier.withSuffixedPath("/" + frame);
      }

      private int currentFrame() {
         int[] times = this.frameTimes;
         if (times == null) {
            times = new int[]{2};
         }

         if (this.frameEnds.length != this.registeredFrames || !Arrays.equals(times, this.appliedTimes)) {
            this.appliedTimes = times;
            this.frameEnds = new int[this.registeredFrames];
            int end = 0;

            for (int i = 0; i < this.registeredFrames; i++) {
               end += times[Math.min(i, times.length - 1)];
               this.frameEnds[i] = end;
            }

            this.cycleTicks = end;
         }

         long tick = System.currentTimeMillis() / 50L % this.cycleTicks;

         for (int i = 0; i < this.frameEnds.length; i++) {
            if (tick < this.frameEnds[i]) {
               return i;
            }
         }

         return 0;
      }

      private void download(String target) {
         this.downloading = true;
         ReviveExecutor.execute(() -> {
            NativeImage[] frames = null;

            try {
               byte[] bytes = Capes.getBytes(target);
               if (bytes != null) {
                  frames = this.splitFrames(NativeImage.read(new ByteArrayInputStream(bytes)));
               }
            } catch (Exception var4) {
               ReviveClient.LOG.warn("Could not read cape '{}' texture: {}", this.name, var4.toString());
            }

            NativeImage[] result = frames;
            Capes.MAIN_THREAD.add(() -> this.finish(target, result));
         });
      }

      private NativeImage[] splitFrames(NativeImage image) {
         int width = image.getWidth();
         int frameHeight = width / 2;
         int frameCount = frameHeight == 0 ? 0 : image.getHeight() / frameHeight;
         if (width % 2 != 0 || frameCount == 0 || image.getHeight() % frameHeight != 0) {
            ReviveClient.LOG
               .warn(
                  "Cape '{}' texture is {}x{}, expected a 2:1 image such as 64x32 (or frames of that shape stacked vertically)",
                  new Object[]{this.name, width, image.getHeight()}
               );
            image.close();
            return null;
         } else if (frameCount == 1) {
            return new NativeImage[]{image};
         } else {
            NativeImage[] frames = new NativeImage[frameCount];
            NativeImage var6 = image;

            try {
               for (int i = 0; i < frameCount; i++) {
                  frames[i] = new NativeImage(width, frameHeight, false);
                  copy(image, i * frameHeight, frames[i], frameHeight);
               }
            } catch (Throwable var10) {
               if (image != null) {
                  try {
                     var6.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }
               }

               throw var10;
            }

            if (image != null) {
               image.close();
            }

            return frames;
         }
      }

      private static void copy(NativeImage source, int sourceY, NativeImage target, int height) {
         for (int y = 0; y < height; y++) {
            for (int x = 0; x < source.getWidth(); x++) {
               target.setColorArgb(x, y, source.getColorArgb(x, sourceY + y));
            }
         }
      }

      private void finish(String target, NativeImage[] frames) {
         this.downloading = false;
         if (frames != null) {
            try {
               for (int i = 0; i < frames.length; i++) {
                  String label = frames.length == 1 ? "Revive cape " + this.name : "Revive cape " + this.name + " frame " + i;
                  ReviveClient.mc.getTextureManager().registerTexture(this.frameIdentifier(i), new NativeImageBackedTexture(() -> label, frames[i]));
                  frames[i] = null;
               }

               for (int i = frames.length; i < this.registeredFrames; i++) {
                  ReviveClient.mc.getTextureManager().destroyTexture(this.frameIdentifier(i));
               }

               this.loadedUrl = target;
               this.registeredFrames = frames.length;
               this.failures = 0;
               return;
            } catch (RuntimeException var8) {
               ReviveClient.LOG.warn("Could not register cape '{}' texture: {}", this.name, var8.toString());

               for (NativeImage frame : frames) {
                  if (frame != null) {
                     frame.close();
                  }
               }
            }
         }

         this.failedUrl = target;
         this.failures++;
         this.retryAt = System.currentTimeMillis() + Math.min(300000L, 10000L << Math.min(this.failures - 1, 5));
      }
   }
}
