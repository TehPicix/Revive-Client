package revivedevelopment.reviveclient.systems.emotes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.fabricmc.loader.api.FabricLoader;
import revivedevelopment.reviveclient.ReviveClient;

public final class EmoteLibrary {
   private static final String BUNDLED_PREFIX = "/assets/revive-client/emotes/";
   private static final long MAX_FILE_SIZE = 524288L;
   private static final int MAX_EMOTES = 512;
   private static final int MAX_NAME_LENGTH = 32;
   private static volatile Map<String, EmoteLibrary.Emote> BY_NAME = Collections.emptyMap();
   private static volatile Map<UUID, EmoteLibrary.Emote> BY_UUID = Collections.emptyMap();
   private static volatile List<EmoteLibrary.Emote> SORTED = Collections.emptyList();
   private static volatile boolean loaded;

   private EmoteLibrary() {
   }

   public static Path userDir() {
      return FabricLoader.getInstance().getGameDir().resolve("revive").resolve("emotes");
   }

   public static Path serverDir() {
      return userDir().resolve("server");
   }

   private static void ensureLoaded() {
      if (!loaded) {
         reload();
      }
   }

   public static synchronized int reload() {
      Map<UUID, EmoteLibrary.Emote> byUuid = new LinkedHashMap<>();

      try (InputStream index = EmoteLibrary.class.getResourceAsStream("/assets/revive-client/emotes/index.txt")) {
         if (index == null) {
            ReviveClient.LOG.warn("[Emotes] bundled emote index is missing");
         } else {
            for (String line : new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
               String name = line.trim();
               if (!name.isEmpty() && !name.startsWith("#")) {
                  try (InputStream in = EmoteLibrary.class.getResourceAsStream("/assets/revive-client/emotes/" + name + ".json")) {
                     if (in != null) {
                        put(byUuid, in.readAllBytes(), name, EmoteLibrary.Source.BUNDLED);
                     } else {
                        ReviveClient.LOG.warn("[Emotes] missing bundled emote {}", name);
                     }
                  } catch (Throwable var13) {
                     ReviveClient.LOG.warn("[Emotes] skipping bundled emote {}: {}", name, var13.toString());
                  }
               }
            }
         }
      } catch (IOException var15) {
         ReviveClient.LOG.warn("[Emotes] could not read bundled emotes: {}", var15.toString());
      }

      loadDir(byUuid, userDir(), EmoteLibrary.Source.USER);
      loadDir(byUuid, serverDir(), EmoteLibrary.Source.SERVER);
      Map<String, EmoteLibrary.Emote> byName = new HashMap<>();
      List<EmoteLibrary.Emote> sorted = new ArrayList<>(byUuid.values());
      sorted.sort(Comparator.comparing(EmoteLibrary.Emote::name, String.CASE_INSENSITIVE_ORDER));

      for (EmoteLibrary.Emote e : sorted) {
         byName.putIfAbsent(e.name().toLowerCase(Locale.ROOT), e);
      }

      BY_UUID = Collections.unmodifiableMap(byUuid);
      BY_NAME = Collections.unmodifiableMap(byName);
      SORTED = Collections.unmodifiableList(sorted);
      loaded = true;
      ReviveClient.LOG.info("[Emotes] library loaded: {} emotes", sorted.size());
      return sorted.size();
   }

   private static void loadDir(Map<UUID, EmoteLibrary.Emote> into, Path dir, EmoteLibrary.Source source) {
      try {
         Files.createDirectories(dir);
      } catch (IOException var11) {
      }

      File[] files = dir.toFile().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
      if (files != null) {
         Arrays.sort(files, Comparator.comparing(File::getName));

         for (File file : files) {
            if (into.size() >= 512) {
               ReviveClient.LOG.warn("[Emotes] emote cap ({}) reached, ignoring the rest of {}", 512, dir);
               return;
            }

            if (file.length() > 524288L) {
               ReviveClient.LOG.warn("[Emotes] skipping oversized emote file {}", file.getName());
            } else {
               String stem = file.getName().substring(0, file.getName().length() - 5);

               try {
                  put(into, Files.readAllBytes(file.toPath()), stem, source);
               } catch (Throwable var10) {
                  ReviveClient.LOG.warn("[Emotes] skipping emote file {}: {}", file.getName(), var10.toString());
               }
            }
         }
      }
   }

   private static void put(Map<UUID, EmoteLibrary.Emote> into, byte[] bytes, String fallbackName, EmoteLibrary.Source source) throws Exception {
      EmoteAnimation animation = EmoteLoader.load(bytes, fallbackName);
      if (animation.bones().isEmpty()) {
         throw new IOException("file contains no animation");
      } else {
         String name = sanitizeName(animation.name(), fallbackName);
         into.put(animation.uuid(), new EmoteLibrary.Emote(name, animation.uuid(), animation, source));
      }
   }

   private static String sanitizeName(String name, String fallback) {
      StringBuilder sb = new StringBuilder(Math.min(name.length(), 32));

      for (int i = 0; i < name.length() && sb.length() < 32; i++) {
         char c = name.charAt(i);
         if (c >= ' ' && c != 127 && c != 167) {
            sb.append(c);
         }
      }

      String result = sb.toString().trim();
      return result.isEmpty() ? fallback : result;
   }

   @Nullable
   public static EmoteLibrary.Emote get(UUID uuid) {
      ensureLoaded();
      return uuid == null ? null : BY_UUID.get(uuid);
   }

   @Nullable
   public static EmoteLibrary.Emote byName(String name) {
      ensureLoaded();
      return name == null ? null : BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
   }

   public static boolean has(String name) {
      return byName(name) != null;
   }

   public static List<EmoteLibrary.Emote> emotes() {
      ensureLoaded();
      return SORTED;
   }

   public static List<String> names() {
      List<String> names = new ArrayList<>();

      for (EmoteLibrary.Emote e : emotes()) {
         names.add(e.name());
      }

      return names;
   }

   public static int count() {
      ensureLoaded();
      return SORTED.size();
   }

   public record Emote(String name, UUID uuid, EmoteAnimation animation, EmoteLibrary.Source source) {
   }

   public static enum Source {
      BUNDLED,
      USER,
      SERVER;
   }
}
