package revivedevelopment.loader.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;
import revivedevelopment.loader.crypto.Crypto;

public final class RemoteApi {
   private static final Duration TIMEOUT = Duration.ofSeconds(20L);
   private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
   private final String host = trimTrailingSlash(Meta.remoteApiHost());
   private final String integrity = computeIntegrity();

   public String loaderIntegrity() {
      return this.integrity;
   }

   public RemoteApi.Response postBytes(String path, byte[] body, String bearer) {
      Builder builder = this.base(path)
         .header("Content-Type", "application/octet-stream")
         .header("Accept", "application/json")
         .POST(BodyPublishers.ofByteArray(body));
      if (bearer != null) {
         builder.header("Authorization", "Bearer " + bearer);
      }

      return send(builder);
   }

   public RemoteApi.Response getBytes(String path, String bearer) {
      Builder builder = this.base(path).header("Accept", "application/octet-stream").GET();
      if (bearer != null) {
         builder.header("Authorization", "Bearer " + bearer);
      }

      return send(builder);
   }

   private Builder base(String path) {
      return HttpRequest.newBuilder(URI.create(this.host + path))
         .timeout(TIMEOUT)
         .header("User-Agent", "revive-loader/1.0")
         .header("X-Loader-Integrity", this.integrity);
   }

   private static RemoteApi.Response send(Builder builder) {
      try {
         HttpResponse<byte[]> response = CLIENT.send(builder.build(), BodyHandlers.ofByteArray());
         return new RemoteApi.Response(response.statusCode(), response.body(), response.headers());
      } catch (IOException exception) {
         throw new IllegalStateException("Auth server request failed", exception);
      } catch (InterruptedException exception) {
         Thread.currentThread().interrupt();
         throw new IllegalStateException("Auth server request interrupted", exception);
      }
   }

   private static String trimTrailingSlash(String value) {
      return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
   }

   private static String computeIntegrity() {
      try {
         URI codeSource = RemoteApi.class.getProtectionDomain().getCodeSource().getLocation().toURI();
         Path path = Path.of(codeSource);
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         if (Files.isRegularFile(path)) {
            digest.update(Files.readAllBytes(path));
         } else {
            if (!Files.isDirectory(path)) {
               throw new IllegalStateException("Unsupported loader code source: " + path);
            }

            try (Stream<Path> stream = Files.walk(path)) {
               stream.filter(x$0 -> Files.isRegularFile(x$0)).sorted(Comparator.comparing(p -> path.relativize(p).toString())).forEach(p -> {
                  try {
                     digest.update(path.relativize(p).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                     digest.update((byte)0);
                     digest.update(Files.readAllBytes(p));
                  } catch (IOException exception) {
                     throw new IllegalStateException("Failed to hash " + p, exception);
                  }
               });
            }
         }

         return Crypto.toHex(digest.digest());
      } catch (Exception exception) {
         throw new IllegalStateException("Failed to compute loader integrity", exception);
      }
   }

   public static final class HttpHeaders {
      private final java.net.http.HttpHeaders raw;

      HttpHeaders(java.net.http.HttpHeaders raw) {
         this.raw = raw;
      }

      public String get(String name) {
         return this.raw.firstValue(name).orElse(null);
      }
   }

   public static final class Response {
      public final int status;
      public final byte[] body;
      public final RemoteApi.HttpHeaders headers;

      Response(int status, byte[] body, java.net.http.HttpHeaders headers) {
         this.status = status;
         this.body = body;
         this.headers = new RemoteApi.HttpHeaders(headers);
      }

      public boolean ok() {
         return this.status >= 200 && this.status < 300;
      }

      public String bodyString() {
         return new String(this.body, StandardCharsets.UTF_8);
      }
   }
}
