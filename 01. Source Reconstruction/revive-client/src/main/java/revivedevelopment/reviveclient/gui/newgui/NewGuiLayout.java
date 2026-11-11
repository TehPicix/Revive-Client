package revivedevelopment.reviveclient.gui.newgui;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import revivedevelopment.reviveclient.ReviveClient;

public final class NewGuiLayout {
   private static final Map<String, int[]> positions = new HashMap<>();
   private static final Map<String, Boolean> collapsed = new HashMap<>();
   private static final Map<String, Integer> heights = new HashMap<>();
   private static final String FILE_NAME = "newgui-layout.nbt";

   private NewGuiLayout() {
   }

   private static Path getFilePath() {
      return new File(ReviveClient.FOLDER, "newgui-layout.nbt").toPath();
   }

   public static void load() {
      positions.clear();
      collapsed.clear();
      heights.clear();

      try {
         Path path = getFilePath();
         File file = path.toFile();
         if (!file.exists()) {
            return;
         }

         NbtCompound root = NbtIo.read(path);
         if (root == null) {
            return;
         }

         for (String key : root.getKeys()) {
            NbtCompound entry = (NbtCompound)root.getCompound(key).orElse(null);
            if (entry != null) {
               int x = entry.getInt("x", 0);
               int y = entry.getInt("y", 0);
               positions.put(key, new int[]{x, y});
               if (entry.getBoolean("collapsed", false)) {
                  collapsed.put(key, true);
               }

               int h = entry.getInt("h", 0);
               if (h > 0) {
                  heights.put(key, h);
               }
            }
         }
      } catch (Throwable var9) {
      }
   }

   public static boolean has(String category) {
      return positions.containsKey(category);
   }

   public static int getX(String category) {
      int[] pos = positions.get(category);
      return pos != null ? pos[0] : 0;
   }

   public static int getY(String category) {
      int[] pos = positions.get(category);
      return pos != null ? pos[1] : 0;
   }

   public static void set(String category, int x, int y) {
      positions.put(category, new int[]{x, y});
   }

   public static boolean isCollapsed(String category) {
      return collapsed.getOrDefault(category, false);
   }

   public static void setCollapsed(String category, boolean value) {
      if (value) {
         collapsed.put(category, true);
      } else {
         collapsed.remove(category);
      }
   }

   public static boolean hasHeight(String category) {
      return heights.containsKey(category);
   }

   public static int getHeight(String category) {
      return heights.getOrDefault(category, 0);
   }

   public static void setHeight(String category, int h) {
      if (h > 0) {
         heights.put(category, h);
      } else {
         heights.remove(category);
      }
   }

   public static void save() {
      try {
         NbtCompound root = new NbtCompound();

         for (Entry<String, int[]> e : positions.entrySet()) {
            NbtCompound entry = new NbtCompound();
            entry.putInt("x", e.getValue()[0]);
            entry.putInt("y", e.getValue()[1]);
            if (isCollapsed(e.getKey())) {
               entry.putBoolean("collapsed", true);
            }

            if (hasHeight(e.getKey())) {
               entry.putInt("h", getHeight(e.getKey()));
            }

            root.put(e.getKey(), entry);
         }

         File folder = ReviveClient.FOLDER;
         if (!folder.exists()) {
            folder.getParentFile().mkdirs();
            folder.mkdir();
         }

         NbtIo.write(root, getFilePath());
      } catch (Throwable var4) {
      }
   }
}
