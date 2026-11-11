package revivedevelopment.reviveclient.utils.render;

import com.google.gson.Gson;
import java.util.Base64;
import java.util.UUID;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.PostInit;
import revivedevelopment.reviveclient.utils.network.Http;

public class PlayerHeadUtils {
   public static PlayerHeadTexture STEVE_HEAD;

   private PlayerHeadUtils() {
   }

   @PostInit
   public static void init() {
   }

   public static PlayerHeadTexture getSteveHead() {
      if (STEVE_HEAD == null) {
         STEVE_HEAD = new PlayerHeadTexture();
      }

      return STEVE_HEAD;
   }

   public static PlayerHeadTexture fetchHead(UUID id) {
      if (id == null) {
         return null;
      } else {
         String url = getSkinUrl(id);
         return url != null ? new PlayerHeadTexture(url) : null;
      }
   }

   public static String getSkinUrl(UUID id) {
      PlayerHeadUtils.UuidToProfileResponse res2 = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
         .exceptionHandler(e -> ReviveClient.LOG.error("Could not contact mojang session servers.", e))
         .sendJson(PlayerHeadUtils.UuidToProfileResponse.class);
      if (res2 == null) {
         return null;
      } else {
         String base64Textures = res2.getPropertyValue("textures");
         if (base64Textures == null) {
            return null;
         } else {
            PlayerHeadUtils.TexturesJson textures = (PlayerHeadUtils.TexturesJson)new Gson()
               .fromJson(new String(Base64.getDecoder().decode(base64Textures)), PlayerHeadUtils.TexturesJson.class);
            return textures.textures.SKIN == null ? null : textures.textures.SKIN.url;
         }
      }
   }

   public static class TexturesJson {
      public PlayerHeadUtils.TexturesJson.Textures textures;

      public static class Texture {
         public String url;
      }

      public static class Textures {
         public PlayerHeadUtils.TexturesJson.Texture SKIN;
      }
   }

   public static class UuidToProfileResponse {
      public PlayerHeadUtils.UuidToProfileResponse.Property[] properties;

      public String getPropertyValue(String name) {
         for (PlayerHeadUtils.UuidToProfileResponse.Property property : this.properties) {
            if (property.name.equals(name)) {
               return property.value;
            }
         }

         return null;
      }

      public static class Property {
         public String name;
         public String value;
      }
   }
}
