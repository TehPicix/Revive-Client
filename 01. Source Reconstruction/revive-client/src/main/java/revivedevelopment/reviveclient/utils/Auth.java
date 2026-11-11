package revivedevelopment.reviveclient.utils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class Auth {
   private static final String ENDPOINT = "https://revive-auth.crownizzle.workers.dev/";

   public static String fetchSecret() {
      try {
         Path keyFile = Path.of("revive-key.txt");
         if (!Files.exists(keyFile)) {
            return null;
         } else {
            String userKey = Files.readString(keyFile).trim();
            String url = "https://revive-auth.crownizzle.workers.dev/?key=" + URLEncoder.encode(userKey, StandardCharsets.UTF_8);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10L)).build();
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
            String body = resp.body();
            if (!body.contains("\"authorized\":true")) {
               return null;
            } else {
               int i = body.indexOf("\"secret\":\"") + 10;
               int j = body.indexOf("\"", i);
               return body.substring(i, j);
            }
         }
      } catch (Exception var9) {
         return null;
      }
   }
}
