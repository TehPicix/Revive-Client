package revivedevelopment.reviveclient.systems.modules.hunting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;

public final class StashSignals {
   private final int capacity;
   private final LinkedHashMap<String, String> seen = new LinkedHashMap<>(16, 0.75F, true);

   public static String waypointPayload(StashSignals.GridWaypoint var0, String var1, Instant var2) {
      JsonObject var3 = new JsonObject();
      JsonObject var4 = new JsonObject();
      JsonObject var5 = new JsonObject();
      JsonArray var6 = new JsonArray();
      JsonArray var7 = new JsonArray();
      JsonArray var8 = new JsonArray();
      var5.add("parse", new JsonArray());
      if (var1 != null && var1.matches("[0-9]{17,20}")) {
         var3.addProperty("content", "<@" + var1 + ">");
         var8.add(var1);
      }

      var5.add("users", var8);
      var3.add("allowed_mentions", var5);
      var4.addProperty("title", "GridFly Waypoint Completed");
      var4.addProperty("color", 3066993);
      var4.addProperty("timestamp", var2.toString());
      var4.addProperty(
         "description", "Waypoint " + var0.completed + " / " + var0.total + (var0.completed == var0.total ? " completed. Grid pass complete." : " completed.")
      );
      var7.add(field("Server", var0.server));
      var7.add(field("Player", var0.player));
      var7.add(field("Dimension", var0.dimension));
      var7.add(field("Waypoint coordinates", String.format(Locale.ROOT, "||X %.1f / Z %.1f||", var0.x, var0.z)));
      var7.add(field("Waypoints remaining", Integer.toString(var0.total - var0.completed)));
      var4.add("fields", var7);
      var6.add(var4);
      var3.add("embeds", var6);
      return var3.toString();
   }

   public StashSignals(int var1) {
      if (var1 < 1) {
         throw new IllegalArgumentException("Positive capacity required");
      } else {
         this.capacity = var1;
      }
   }

   public boolean firstOrChanged(StashSignals.Hit var1) {
      if (var1.kind() == StashSignals.Kind.Sign && arrowOrBlank(var1.front(), var1.back())) {
         return false;
      } else {
         String var2 = var1.dimension() + ":" + var1.kind() + ":" + var1.id();
         String var3 = var1.kind() == StashSignals.Kind.Sign ? var1.front().length() + ":" + var1.front() + var1.back() : "entity";
         return !this.seen.containsKey(var2) && this.seen.size() >= this.capacity ? false : !var3.equals(this.seen.put(var2, var3));
      }
   }

   static boolean arrowOrBlank(String var0, String var1) {
      boolean var2 = false;
      boolean var3 = false;

      for (String var7 : new String[]{var0, var1}) {
         String var8 = var7.replaceAll("(?i)§[0-9a-fk-orx]", "");
         int var9 = 0;

         while (var9 < var8.length()) {
            int var10 = var8.codePointAt(var9);
            var9 += Character.charCount(var10);
            if (!Character.isWhitespace(var10) && !Character.isSpaceChar(var10)) {
               var3 = true;
               if (var10 != 60
                  && var10 != 62
                  && (var10 < 8592 || var10 > 8703)
                  && var10 != 10132
                  && var10 != 10145
                  && var10 != 10148
                  && (var10 < 10224 || var10 > 10239)
                  && (var10 < 10496 || var10 > 10623)
                  && (var10 < 11013 || var10 > 11015)) {
                  if (var10 != 45 && var10 != 61 && var10 != 8211 && var10 != 8212 && var10 != 9472 && var10 != 9473 && var10 != 65038 && var10 != 65039) {
                     return false;
                  }
               } else {
                  var2 = true;
               }
            }
         }
      }

      return var2 || !var3;
   }

   public void clear() {
      this.seen.clear();
   }

   public int size() {
      return this.seen.size();
   }

   public static String payload(StashSignals.Hit var0, String var1) {
      JsonObject var2 = new JsonObject();
      JsonObject var3 = new JsonObject();
      JsonObject var4 = new JsonObject();
      JsonArray var5 = new JsonArray();
      JsonArray var6 = new JsonArray();
      JsonArray var7 = new JsonArray();
      var4.add("parse", new JsonArray());
      if (var1 != null && var1.matches("[0-9]{17,20}")) {
         var2.addProperty("content", "<@" + var1 + ">");
         var7.add(var1);
      }

      var4.add("users", var7);
      var2.add("allowed_mentions", var4);
      var3.addProperty("title", var0.title());
      var3.addProperty("color", 2154012);
      var3.addProperty("description", "Coordinates: ||" + var0.coordinates() + "||\nDimension: " + limit(var0.dimension(), 256));
      if (var0.kind() == StashSignals.Kind.Sign) {
         var6.add(field("Front text", var0.front()));
         var6.add(field("Back text", var0.back()));
      } else if (var0.kind() == StashSignals.Kind.ThrownPearl) {
         var6.add(field("Entity", "Thrown ender pearl (loaded projectile, not an inventory item)."));
      } else {
         var6.add(
            field(
               var0.kind() == StashSignals.Kind.Player ? "Player" : (var0.kind() == StashSignals.Kind.ChestMinecart ? "Chest minecart" : "Wither"),
               var0.front()
            )
         );
         var6.add(field("Entity UUID", var0.id()));
         var6.add(field("Observation", var0.back()));
      }

      var3.add("fields", var6);
      var5.add(var3);
      var2.add("embeds", var5);
      return var2.toString();
   }

   private static JsonObject field(String var0, String var1) {
      JsonObject var2 = new JsonObject();
      var2.addProperty("name", var0);
      var2.addProperty("value", var1.isBlank() ? "(blank)" : limit(var1, 1024));
      var2.addProperty("inline", false);
      return var2;
   }

   static String limit(String var0, int var1) {
      if (var0.length() <= var1) {
         return var0;
      } else {
         int var2 = var1 - 14;
         if (Character.isHighSurrogate(var0.charAt(var2 - 1))) {
            var2--;
         }

         return var0.substring(0, var2) + "\n[truncated]";
      }
   }

   public static final class DisconnectSession {
      private StashSignals.DisconnectSession.Location last;

      public void observe(String var1, String var2, String var3, int var4, int var5, int var6) {
         this.last = new StashSignals.DisconnectSession.Location(var1, var2, var3, var4, var5, var6);
      }

      public void clear() {
         this.last = null;
      }

      public String leave(boolean var1, String var2, Instant var3) {
         StashSignals.DisconnectSession.Location var4 = this.last;
         this.clear();
         if (var1 && var4 != null) {
            JsonObject var5 = new JsonObject();
            JsonObject var6 = new JsonObject();
            JsonObject var7 = new JsonObject();
            JsonArray var8 = new JsonArray();
            JsonArray var9 = new JsonArray();
            JsonArray var10 = new JsonArray();
            var7.add("parse", new JsonArray());
            if (var2 != null && var2.matches("[0-9]{17,20}")) {
               var5.addProperty("content", "<@" + var2 + ">");
               var10.add(var2);
            }

            var7.add("users", var10);
            var5.add("allowed_mentions", var7);
            var6.addProperty("title", "Disconnected From Server");
            var6.addProperty("color", 15158332);
            var6.addProperty("timestamp", var3.toString());
            var6.addProperty("description", "Your Minecraft multiplayer session ended. Includes manual and automatic disconnects.");
            var9.add(StashSignals.field("Server", var4.server));
            var9.add(StashSignals.field("Player", var4.player));
            var9.add(StashSignals.field("Dimension", var4.dimension));
            var9.add(StashSignals.field("Last known coordinates", "||X " + var4.x + " / Y " + var4.y + " / Z " + var4.z + "||"));
            var6.add("fields", var9);
            var8.add(var6);
            var5.add("embeds", var8);
            return var5.toString();
         } else {
            return null;
         }
      }

      private record Location(String server, String player, String dimension, int x, int y, int z) {
      }
   }

   public record GridWaypoint(String server, String player, String dimension, int completed, int total, double x, double z) {
      public GridWaypoint(String server, String player, String dimension, int completed, int total, double x, double z) {
         Objects.requireNonNull(server);
         Objects.requireNonNull(player);
         Objects.requireNonNull(dimension);
         if (completed >= 1 && total >= completed && Double.isFinite(x) && Double.isFinite(z)) {
            this.server = server;
            this.player = player;
            this.dimension = dimension;
            this.completed = completed;
            this.total = total;
            this.x = x;
            this.z = z;
         } else {
            throw new IllegalArgumentException("Invalid completed waypoint");
         }
      }
   }

   public record Hit(StashSignals.Kind kind, String dimension, String id, int x, int y, int z, String front, String back) {
      public Hit(StashSignals.Kind kind, String dimension, String id, int x, int y, int z, String front, String back) {
         Objects.requireNonNull(kind);
         Objects.requireNonNull(dimension);
         Objects.requireNonNull(id);
         front = StashSignals.limit(front == null ? "" : front, 1024);
         back = StashSignals.limit(back == null ? "" : back, 1024);
         this.kind = kind;
         this.dimension = dimension;
         this.id = id;
         this.x = x;
         this.y = y;
         this.z = z;
         this.front = front;
         this.back = back;
      }

      public String title() {
         return switch (this.kind) {
            case ThrownPearl -> "Thrown Ender Pearl Found";
            case Sign -> "Sign Found";
            case Player -> "Player In Range";
            case Wither -> "Wither Found";
            case ChestMinecart -> "Chest Minecart Found";
         };
      }

      public String coordinates() {
         return "X " + this.x + " / Y " + this.y + " / Z " + this.z;
      }
   }

   public static enum Kind {
      ThrownPearl,
      Sign,
      Player,
      Wither,
      ChestMinecart;
   }
}
