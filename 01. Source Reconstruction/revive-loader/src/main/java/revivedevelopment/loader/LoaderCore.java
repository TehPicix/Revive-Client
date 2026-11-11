package revivedevelopment.loader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import revivedevelopment.loader.api.Hardware;
import revivedevelopment.loader.api.Json;
import revivedevelopment.loader.api.Meta;
import revivedevelopment.loader.api.MojangSession;
import revivedevelopment.loader.api.RemoteApi;
import revivedevelopment.loader.crypto.Crypto;
import revivedevelopment.loader.crypto.LoaderSession;

public final class LoaderCore {
   private final RemoteApi api;
   private final LoaderSession session;
   private LoaderCore.MinecraftIdentity minecraft = LoaderCore.MinecraftIdentity.NONE;

   public LoaderCore() {
      this.api = new RemoteApi();
      this.session = new LoaderSession(Meta.buildPublicKeyHex());
   }

   public LoaderCore withMinecraft(LoaderCore.MinecraftIdentity identity) {
      this.minecraft = identity == null ? LoaderCore.MinecraftIdentity.NONE : identity;
      return this;
   }

   public byte[] fetchClient(String accessToken, String minecraftVersion) {
      String challenge = this.authorize(accessToken);
      String bearer = this.session.deriveBearer(challenge);
      return this.download(bearer, minecraftVersion);
   }

   public void verifyAccess(String accessToken) {
      String challenge = this.authorize(accessToken);
      this.session.deriveBearer(challenge);
   }

   private String authorize(String accessToken) {
      boolean joined = this.minecraft.name() != null
         && MojangSession.join(this.minecraft.accessToken(), this.minecraft.uuid(), MojangSession.serverId(this.session.sessionPublicKeyHex()));
      String payload = new Json.Writer()
         .put("accessToken", accessToken)
         .put("nonce", Crypto.toHex(Crypto.randomBytes(32)))
         .put("timestamp", Instant.now().getEpochSecond())
         .put("sessionKey", this.session.sessionPublicKeyHex())
         .putRaw("hardware", new Json.Writer().put("hwid", Hardware.hwid()).build())
         .putRaw("minecraft", new Json.Writer().put("uuid", this.minecraft.uuid()).put("name", this.minecraft.name()).put("joined", joined).build())
         .build();
      byte[] sealed = this.session.sealRequest(payload.getBytes(StandardCharsets.UTF_8));
      RemoteApi.Response response = this.api.postBytes("/api/authorize", sealed, null);
      Map<String, Object> body = tryParse(response.bodyString());
      if (response.ok() && body != null && Json.getBoolean(body, "success")) {
         String challenge = Json.getString(body, "challenge");
         if (challenge == null) {
            throw new LoaderCore.AuthException(response.status, "error", "Authorization failed", "Server did not return a challenge.");
         } else {
            return challenge;
         }
      } else {
         throw asAuthException(response.status, body);
      }
   }

   private byte[] download(String bearer, String minecraftVersion) {
      RemoteApi.Response response = this.api.getBytes("/api/download/" + minecraftVersion + "/client.bin", bearer);
      if (!response.ok()) {
         throw asAuthException(response.status, tryParse(response.bodyString()));
      } else {
         verifyDigest(response);
         return this.session.decryptDownload(response.body);
      }
   }

   private static void verifyDigest(RemoteApi.Response response) {
      String header = response.headers.get("Content-Digest");
      if (header == null) {
         throw new LoaderCore.AuthException(response.status, "error", "Integrity error", "Missing Content-Digest header.");
      } else {
         String[] parts = header.split("=:", 2);
         if (parts.length != 2) {
            throw new LoaderCore.AuthException(response.status, "error", "Integrity error", "Malformed Content-Digest header.");
         } else {
            String algorithm = parts[0].trim().toUpperCase().replace("SHA-", "SHA-");
            String expectedBase64 = parts[1].replaceFirst(":$", "");
            byte[] expected = Base64.getDecoder().decode(expectedBase64);
            byte[] actual = Crypto.sha256(response.body);
            if (!MessageDigest.isEqual(expected, actual)) {
               throw new LoaderCore.AuthException(response.status, "error", "Integrity error", "Downloaded client failed its integrity check.");
            }
         }
      }
   }

   private static LoaderCore.AuthException asAuthException(int status, Map<String, Object> body) {
      return body == null
         ? new LoaderCore.AuthException(status, "error", "Request failed", "The server rejected the request (" + status + ").")
         : new LoaderCore.AuthException(status, Json.getString(body, "type"), Json.getString(body, "title"), Json.getString(body, "message"));
   }

   private static Map<String, Object> tryParse(String text) {
      try {
         return Json.parseObject(text);
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   public static final class AuthException extends RuntimeException {
      public final int status;
      public final String title;
      public final String type;

      public AuthException(int status, String type, String title, String message) {
         super(message == null ? "Authorization failed (" + status + ")" : message);
         this.status = status;
         this.type = type == null ? "error" : type;
         this.title = title;
      }
   }

   public record MinecraftIdentity(String uuid, String name, String accessToken) {
      public static final LoaderCore.MinecraftIdentity NONE = new LoaderCore.MinecraftIdentity(null, null, null);

      @Override
      public String toString() {
         return "MinecraftIdentity[uuid=" + this.uuid + ", name=" + this.name + "]";
      }
   }
}
