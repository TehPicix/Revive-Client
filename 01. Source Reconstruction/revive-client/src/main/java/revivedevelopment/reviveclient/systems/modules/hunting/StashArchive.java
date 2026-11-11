package revivedevelopment.reviveclient.systems.modules.hunting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.function.Consumer;

public final class StashArchive {
   private final Path path;
   private final Consumer<String> warning;
   private final ThreadPoolExecutor writer = worker("MasterHunter-Stash-Archive", 1024);
   private final ThreadPoolExecutor uploader = worker("MasterHunter-Stash-Database", 128);
   private long lastWarning;

   public StashArchive(Path var1, Consumer<String> var2) {
      this.path = var1;
      this.warning = var2;
   }

   private static ThreadPoolExecutor worker(String var0, int var1) {
      ThreadPoolExecutor var2 = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(var1), var1x -> {
         Thread var2x = new Thread(var1x, var0);
         var2x.setDaemon(true);
         return var2x;
      }, new AbortPolicy());
      var2.allowCoreThreadTimeOut(true);
      return var2;
   }

   public static JsonObject base(String var0, String var1, String var2, int var3, Integer var4, int var5) {
      JsonObject var6 = new JsonObject();
      var6.addProperty("id", UUID.randomUUID().toString());
      var6.addProperty("observed_at", Instant.now().toString());
      var6.addProperty("source", "masterhunter");
      var6.addProperty("server", var2 == null ? "" : var2);
      var6.addProperty("hit_type", var0);
      var6.addProperty("dimension", var1);
      var6.addProperty("x", var3);
      var6.addProperty("y", var4);
      var6.addProperty("z", var5);
      var6.add("counts", new JsonObject());
      var6.add("extra", new JsonObject());
      return var6;
   }

   public static JsonObject signal(StashSignals.Hit var0, String var1) {
      String var2 = switch (var0.kind()) {
         case Sign -> "sign";
         case ThrownPearl -> "pearl";
         case Player -> "player";
         case Wither -> "wither";
         case ChestMinecart -> "chest_minecart";
      };
      JsonObject var3 = base(var2, var0.dimension(), var1, var0.x(), var0.y(), var0.z());
      if (var0.kind() == StashSignals.Kind.Sign) {
         var3.addProperty("front_text", var0.front());
         var3.addProperty("back_text", var0.back());
      } else {
         var3.addProperty("entity_id", var0.id());
         var3.addProperty("entity_name", var0.front());
         var3.getAsJsonObject("extra").addProperty("observation", var0.back());
      }

      return var3;
   }

   public void submit(JsonObject var1, StashArchive.Cloud var2) {
      String var3 = var1.toString();

      try {
         this.writer.execute(() -> {
            try {
               Files.createDirectories(this.path.toAbsolutePath().getParent());
               Files.writeString(this.path, var3 + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException var5x) {
               this.warn("Stash local archive write failed; check disk space/permissions.");
               return;
            }

            if (var2 != null) {
               try {
                  this.uploader.execute(() -> this.upload(var3, var2));
               } catch (RejectedExecutionException var4) {
                  this.warn("Stash database queue full. Hits remain in the local archive for import.");
               }
            }
         });
      } catch (RejectedExecutionException var5) {
         this.warn("Stash local archive queue full; a hit could not be saved.");
      }
   }

   public static URI endpoint(StashArchive.Cloud var0) {
      URI var1 = URI.create(var0.project().trim());
      if ("https".equalsIgnoreCase(var1.getScheme())
         && var1.getHost() != null
         && var1.getHost().matches("[a-z0-9-]+\\.supabase\\.co")
         && var1.getUserInfo() == null
         && var1.getPort() == -1
         && var1.getQuery() == null
         && var1.getFragment() == null
         && (var1.getPath().isEmpty() || var1.getPath().equals("/"))) {
         if (var0.publishableKey().matches("sb_publishable_[A-Za-z0-9_-]+") && var0.ingestToken().matches("[a-fA-F0-9]{64}")) {
            return var1.resolve("/rest/v1/rpc/ingest_stash_hit");
         } else {
            throw new IllegalArgumentException("Publishable key and 64-hex ingestion token required");
         }
      } else {
         throw new IllegalArgumentException("Supabase project HTTPS URL required");
      }
   }

   private void upload(String var1, StashArchive.Cloud var2) {
      HttpURLConnection var3 = null;

      try {
         var3 = (HttpURLConnection)endpoint(var2).toURL().openConnection();
         var3.setConnectTimeout(5000);
         var3.setReadTimeout(10000);
         var3.setInstanceFollowRedirects(false);
         var3.setRequestMethod("POST");
         var3.setRequestProperty("apikey", var2.publishableKey());
         var3.setRequestProperty("Content-Type", "application/json; charset=utf-8");
         var3.setDoOutput(true);
         JsonObject var4 = new JsonObject();
         var4.addProperty("p_token", var2.ingestToken());
         var4.add("p_hit", JsonParser.parseString(var1));
         byte[] var5 = var4.toString().getBytes(StandardCharsets.UTF_8);
         var3.setFixedLengthStreamingMode(var5.length);

         try (OutputStream var6 = var3.getOutputStream()) {
            var6.write(var5);
         }

         int var18 = var3.getResponseCode();
         if (var18 < 200 || var18 >= 300) {
            this.warn("Stash database returned HTTP " + var18 + ". Hit retained locally; use archive import to retry.");
         }
      } catch (Exception var161) {
         this.warn("Stash database upload failed (" + var161.getClass().getSimpleName() + "). Hit retained locally.");
      } finally {
         if (var3 != null) {
            var3.disconnect();
         }
      }
   }

   private synchronized void warn(String var1) {
      long var2 = System.nanoTime();
      if (this.lastWarning == 0L || var2 - this.lastWarning > 10000000000L) {
         this.lastWarning = var2;
         this.warning.accept(var1);
      }
   }

   public boolean drain() throws InterruptedException {
      this.writer.shutdown();
      boolean var1 = this.writer.awaitTermination(20L, TimeUnit.SECONDS);
      this.uploader.shutdown();
      return this.uploader.awaitTermination(20L, TimeUnit.SECONDS) && var1;
   }

   public record Cloud(String project, String publishableKey, String ingestToken) {
   }
}
