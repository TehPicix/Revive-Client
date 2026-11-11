package revivedevelopment.reviveclient.systems.modules.hunting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class StashWebhook {
   public static String withPing(String var0, String var1) {
      JsonObject var2 = JsonParser.parseString(var0).getAsJsonObject();
      JsonObject var3 = new JsonObject();
      JsonArray var4 = new JsonArray();
      var3.add("parse", new JsonArray());
      if (var1 != null && var1.matches("[0-9]{17,20}")) {
         var2.addProperty("content", "<@" + var1 + ">");
         var4.add(var1);
      }

      var3.add("users", var4);
      var2.add("allowed_mentions", var3);
      return var2.toString();
   }

   public static String stash(String var0, String var1) {
      JsonObject var2 = new JsonObject();
      JsonObject var3 = new JsonObject();
      JsonArray var4 = new JsonArray();
      var3.addProperty("title", "Stash Found!");
      var3.addProperty("description", var0);
      var3.addProperty("color", 2154012);
      var4.add(var3);
      var2.add("embeds", var4);
      return withPing(var2.toString(), var1);
   }

   public static StashWebhook.Body body(String var0, byte[] var1) throws IOException {
      return body(var0, var1, false);
   }

   public static StashWebhook.Body body(String var0, byte[] var1, boolean var2) throws IOException {
      if (var1 == null) {
         return new StashWebhook.Body("application/json; charset=utf-8", var0.getBytes(StandardCharsets.UTF_8));
      } else if (var1.length > 2000000) {
         throw new IOException("Surface map too large");
      } else {
         String var3 = var2 ? "stash-3d.png" : "stash-surface.png";
         String var4 = "MasterHunter-" + UUID.randomUUID();
         JsonObject var5 = JsonParser.parseString(var0).getAsJsonObject();
         JsonObject var6 = new JsonObject();
         var6.addProperty("url", "attachment://" + var3);
         var5.getAsJsonArray("embeds").get(0).getAsJsonObject().add("image", var6);
         JsonObject var7 = new JsonObject();
         var7.addProperty("id", 0);
         var7.addProperty("filename", var3);
         var7.addProperty(
            "description",
            var2
               ? "128 x 128 block loaded surface height model. Pink marks hit X/Z. Not an underground or textured block view; blank areas unavailable."
               : "Loaded Overworld surface around the hit; pink outline marks its chunk. Blank areas are unavailable."
         );
         JsonArray var8 = new JsonArray();
         var8.add(var7);
         var5.add("attachments", var8);
         ByteArrayOutputStream var9 = new ByteArrayOutputStream();
         var9.write(
            ("--"
                  + var4
                  + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json; charset=utf-8\r\n\r\n"
                  + var5
                  + "\r\n--"
                  + var4
                  + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\""
                  + var3
                  + "\"\r\nContent-Type: image/png\r\n\r\n")
               .getBytes(StandardCharsets.UTF_8)
         );
         var9.write(var1);
         var9.write(("\r\n--" + var4 + "--\r\n").getBytes(StandardCharsets.UTF_8));
         return new StashWebhook.Body("multipart/form-data; boundary=" + var4, var9.toByteArray());
      }
   }

   public record Body(String contentType, byte[] bytes) {
   }
}
