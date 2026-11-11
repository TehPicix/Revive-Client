package revivedevelopment.reviveclient.pingbypass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.minecraft.util.Identifier;

public final class PbProtocol {
   public static final int VERSION = 8;
   public static final byte[] MAGIC = new byte[]{82, 86, 80, 66};
   public static final String TICKET_MARKER = "\u0000RVPB\u0000";
   public static final Identifier OWN_MOVE = Identifier.of("revive", "pb_own");
   public static final int DEFAULT_PORT = 25570;
   public static final int MAX_FRAME = 4194304;
   public static final String CHALLENGE = "challenge";
   public static final String AUTH = "auth";
   public static final String WELCOME = "welcome";
   public static final String DENIED = "denied";
   public static final String PING = "ping";
   public static final String PONG = "pong";
   public static final String TICKET = "ticket";
   public static final String TICKET_OK = "ticket_ok";
   public static final String BIND = "bind";
   public static final String JOIN = "join";
   public static final String JOIN_RESULT = "join_result";
   public static final String MODULE = "module";
   public static final String MODULE_STATES = "module_states";
   public static final String STATUS = "status";
   public static final String CHAT = "chat";
   public static final String INPUT = "input";
   public static final String FRIENDS = "friends";
   public static final String MINE = "mine";
   public static final String ABORT_REBREAK = "abort_rebreak";
   public static final String ACTION = "action";
   public static final String OPTIONS = "options";
   public static final String AIM = "aim";
   public static final String FX = "fx";
   public static final String SWING = "swing";
   public static final String FAKES = "fakes";
   public static final String FAKE_HIT = "fake_hit";

   private PbProtocol() {
   }

   public static JsonObject message(String type) {
      JsonObject o = new JsonObject();
      o.addProperty("type", type);
      return o;
   }

   public static String type(JsonObject o) {
      return o.has("type") ? o.get("type").getAsString() : "";
   }

   public static byte[] encode(JsonObject o) {
      return o.toString().getBytes(StandardCharsets.UTF_8);
   }

   public static JsonObject decode(byte[] data) {
      return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
   }

   public static String b64(byte[] data) {
      return Base64.getEncoder().encodeToString(data);
   }

   public static byte[] unb64(String s) {
      return Base64.getDecoder().decode(s);
   }

   public static String str(JsonObject o, String key, String def) {
      return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
   }

   public static String[] splitHandshakeHost(String address) {
      int i = address.indexOf("\u0000RVPB\u0000");
      return i < 0 ? new String[]{address, null} : new String[]{address.substring(0, i), address.substring(i + "\u0000RVPB\u0000".length())};
   }
}
