package revivedevelopment.reviveclient.pingbypass;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.systems.modules.Module;

public final class PbFx {
   private static final int MAX_MODULE_CHARS = 6000;
   private static final int FULL_EVERY_TICKS = 20;

   private PbFx() {
   }

   public static JsonArray pos(BlockPos pos) {
      JsonArray a = new JsonArray();
      a.add(pos.getX());
      a.add(pos.getY());
      a.add(pos.getZ());
      return a;
   }

   @Nullable
   public static BlockPos pos(@Nullable JsonElement e) {
      if (e != null && e.isJsonArray()) {
         JsonArray a = e.getAsJsonArray();
         if (a.size() < 3) {
            return null;
         } else {
            try {
               return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
            } catch (RuntimeException var3) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static JsonArray posTimes(Map<BlockPos, Long> map, long now, long maxAgeMs, int max) {
      List<Entry<BlockPos, Long>> entries = new ArrayList<>();

      for (Entry<BlockPos, Long> e : map.entrySet()) {
         Long t = e.getValue();
         if (e.getKey() != null && t != null && now - t <= maxAgeMs) {
            entries.add(e);
         }
      }

      entries.sort((ax, b) -> Long.compare(b.getValue(), (Long)ax.getValue()));
      JsonArray out = new JsonArray();

      for (int i = 0; i < entries.size() && i < max; i++) {
         JsonArray a = pos(entries.get(i).getKey());
         a.add(entries.get(i).getValue());
         out.add(a);
      }

      return out;
   }

   public static void readPosTimes(@Nullable JsonElement e, PbFx.Clock clock, Map<BlockPos, Long> into) {
      into.clear();
      if (e != null && e.isJsonArray()) {
         for (JsonElement entry : e.getAsJsonArray()) {
            BlockPos pos = pos(entry);
            if (pos != null && entry.getAsJsonArray().size() >= 4) {
               try {
                  into.put(pos, clock.toLocal(entry.getAsJsonArray().get(3).getAsLong()));
               } catch (RuntimeException var7) {
               }
            }
         }
      }
   }

   public static double num(JsonObject o, String key, double fallback) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
   }

   public static long time(JsonObject o, String key, PbFx.Clock clock, long fallback) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? clock.toLocal(e.getAsLong()) : fallback;
   }

   public record Clock(long offset) {
      public long toLocal(long vpsMillis) {
         return vpsMillis + this.offset;
      }
   }

   public static final class Sender {
      private final Map<String, String> sent = new HashMap<>();
      private int ticks;

      public void reset() {
         this.sent.clear();
         this.ticks = 0;
      }

      @Nullable
      public JsonObject collect(Collection<Module> modules) {
         boolean full = this.ticks++ % 20 == 0;
         JsonObject changed = new JsonObject();
         Set<String> present = new HashSet<>();

         for (Module module : modules) {
            if (module instanceof PbRenderable renderable && module.isActive()) {
               JsonObject state;
               try {
                  state = renderable.writeRemoteRender();
               } catch (Throwable var11) {
                  ReviveClient.LOG.error("[PingBypass] render state of {} failed", module.name, var11);
                  state = null;
               }

               String text = state != null && !state.isEmpty() ? state.toString() : null;
               if (text != null && text.length() > 6000) {
                  text = null;
               }

               if (text != null) {
                  present.add(module.name);
               }

               String last = this.sent.get(module.name);
               if (!Objects.equals(text, last) || full && text != null) {
                  if (text == null) {
                     if (last != null) {
                        this.sent.remove(module.name);
                        changed.add(module.name, JsonNull.INSTANCE);
                     }
                  } else {
                     this.sent.put(module.name, text);
                     changed.add(module.name, state);
                  }
               }
            }
         }

         Iterator<String> it = this.sent.keySet().iterator();

         while (it.hasNext()) {
            String name = it.next();
            if (!present.contains(name)) {
               it.remove();
               changed.add(name, JsonNull.INSTANCE);
            }
         }

         if (changed.isEmpty() && !full) {
            return null;
         } else {
            JsonObject msg = PbProtocol.message("fx");
            msg.addProperty("now", System.currentTimeMillis());
            if (full) {
               msg.addProperty("full", true);
            }

            msg.add("m", changed);
            return msg;
         }
      }
   }
}
