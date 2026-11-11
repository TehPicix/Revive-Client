package revivedevelopment.reviveclient.utils.other;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import revivedevelopment.reviveclient.utils.network.Http;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;

public class SpotifyArtFetcher {
   private static volatile SpotifyArtFetcher.Result result;
   private static volatile String pendingKey;

   private SpotifyArtFetcher() {
   }

   public static SpotifyArtFetcher.Result result() {
      return result;
   }

   public static void request(String key, String song, String artist) {
      if (!key.equals(pendingKey)) {
         pendingKey = key;
         ReviveExecutor.execute(() -> {
            byte[] bytes = fetch(song, artist);
            if (key.equals(pendingKey)) {
               result = new SpotifyArtFetcher.Result(key, bytes == null ? new byte[0] : bytes);
            }
         });
      }
   }

   private static byte[] fetch(String song, String artist) {
      try {
         String term = URLEncoder.encode((artist + " " + song).trim(), StandardCharsets.UTF_8);
         JsonObject json = Http.get("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + term).sendJson(JsonObject.class);
         if (json == null) {
            return null;
         } else {
            JsonArray results = json.getAsJsonArray("results");
            if (results != null && !results.isEmpty()) {
               JsonElement artUrl = results.get(0).getAsJsonObject().get("artworkUrl100");
               if (artUrl == null) {
                  return null;
               } else {
                  String url = artUrl.getAsString().replace("100x100bb", "600x600bb");

                  byte[] var8;
                  try (InputStream in = Http.get(url).sendInputStream()) {
                     if (in == null) {
                        return null;
                     }

                     var8 = in.readAllBytes();
                  }

                  return var8;
               }
            } else {
               return null;
            }
         }
      } catch (Throwable var12) {
         System.out.println("[SpotifyArtFetcher] album art lookup failed: " + var12);
         return null;
      }
   }

   public record Result(String key, byte[] bytes) {
   }
}
