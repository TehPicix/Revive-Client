package revivedevelopment.loader.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import revivedevelopment.loader.crypto.Crypto;

public final class MojangSession {
   private static final Duration TIMEOUT = Duration.ofSeconds(10L);
   private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

   private MojangSession() {
   }

   public static String serverId(String sessionKeyHex) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-1").digest(("revive-capes:" + sessionKeyHex.toLowerCase()).getBytes(StandardCharsets.UTF_8));
         return Crypto.toHex(digest);
      } catch (Exception exception) {
         throw new IllegalStateException("SHA-1 unavailable", exception);
      }
   }

   public static boolean join(String minecraftAccessToken, String uuid, String serverId) {
      if (minecraftAccessToken != null && !minecraftAccessToken.isBlank() && uuid != null && !uuid.isBlank()) {
         String body = new Json.Writer()
            .put("accessToken", minecraftAccessToken)
            .put("selectedProfile", uuid.replace("-", ""))
            .put("serverId", serverId)
            .build();

         try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(sessionHost() + "/session/minecraft/join"))
               .timeout(TIMEOUT)
               .header("Content-Type", "application/json")
               .header("User-Agent", "revive-loader/1.0")
               .POST(BodyPublishers.ofString(body))
               .build();
            int status = CLIENT.send(request, BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
         } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
         } catch (Exception ignored) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static String sessionHost() {
      String host = System.getProperty("revive.mojang.session");
      if (host == null || host.isBlank()) {
         host = "https://sessionserver.mojang.com";
      }

      return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
   }
}
