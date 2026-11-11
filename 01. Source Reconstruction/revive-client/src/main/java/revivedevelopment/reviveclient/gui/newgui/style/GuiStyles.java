package revivedevelopment.reviveclient.gui.newgui.style;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.gui.Gui;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;

public final class GuiStyles {
   private static final File DIR = new File(ReviveClient.FOLDER, "gui-styles");
   private static final String ACTIVE_FILE = "_active.nbt";
   private static final List<GuiStyleDef> BUILTINS = List.of(
      GuiStyleDef.builtin("Classic", Gui.GuiStyle.Classic),
      GuiStyleDef.builtin("Clean", Gui.GuiStyle.Clean),
      GuiStyleDef.builtin("Modern", Gui.GuiStyle.Modern)
   );
   private static final List<GuiStyleDef> CUSTOM = new ArrayList<>();
   private static String activeName = "Modern";
   private static boolean loaded = false;

   private GuiStyles() {
   }

   public static List<GuiStyleDef> all() {
      ensureLoaded();
      List<GuiStyleDef> out = new ArrayList<>(BUILTINS);
      out.addAll(CUSTOM);
      return out;
   }

   public static List<GuiStyleDef> builtins() {
      return BUILTINS;
   }

   public static List<GuiStyleDef> custom() {
      ensureLoaded();
      return CUSTOM;
   }

   public static GuiStyleDef get(String name) {
      for (GuiStyleDef d : all()) {
         if (d.name.equalsIgnoreCase(name)) {
            return d;
         }
      }

      return null;
   }

   public static GuiStyleDef active() {
      ensureLoaded();
      GuiStyleDef d = get(activeName);
      if (d != null) {
         return d;
      } else {
         Gui gui = gui();
         if (gui != null) {
            for (GuiStyleDef b : BUILTINS) {
               if (b.layout == gui.style.get()) {
                  return b;
               }
            }
         }

         return BUILTINS.get(2);
      }
   }

   public static boolean isActive(GuiStyleDef d) {
      return d != null && d.name.equalsIgnoreCase(activeName);
   }

   public static void select(GuiStyleDef def) {
      if (def != null) {
         ensureLoaded();
         Gui gui = gui();
         if (gui != null) {
            if (!def.builtin && def.settings != null) {
               gui.settings.fromTag(def.settings);
               gui.style.set(def.layout);
               gui.applyToFontManager();
            } else {
               gui.style.set(def.layout);
            }
         }

         activeName = def.name;
         saveActive();
      }
   }

   public static GuiStyleDef saveCurrentAs(String name) {
      ensureLoaded();
      Gui gui = gui();
      if (gui == null) {
         return null;
      } else {
         GuiStyleDef def = GuiStyleDef.custom(uniqueName(name), gui.style.get(), gui.settings.toTag());
         CUSTOM.add(def);
         writeToDisk(def);
         select(def);
         return def;
      }
   }

   public static GuiStyleDef duplicate(GuiStyleDef src) {
      ensureLoaded();
      Gui gui = gui();
      if (gui == null) {
         return null;
      } else {
         NbtCompound snap;
         if (src != null && !src.builtin && src.settings != null) {
            snap = src.settings.copy();
         } else {
            snap = gui.settings.toTag();
         }

         String base = (src == null ? "Style" : src.name) + " copy";
         GuiStyleDef def = GuiStyleDef.custom(uniqueName(base), src == null ? gui.style.get() : src.layout, snap);
         CUSTOM.add(def);
         writeToDisk(def);
         select(def);
         return def;
      }
   }

   public static void syncFromCurrent(GuiStyleDef def) {
      if (def != null && !def.builtin) {
         Gui gui = gui();
         if (gui != null) {
            def.settings = gui.settings.toTag();
            def.layout = gui.style.get();
         }
      }
   }

   public static void persist(GuiStyleDef def) {
      if (def != null && !def.builtin) {
         writeToDisk(def);
      }
   }

   public static void updateFromCurrent(GuiStyleDef def) {
      syncFromCurrent(def);
      persist(def);
   }

   public static boolean rename(GuiStyleDef def, String newName) {
      if (def != null && !def.builtin) {
         ensureLoaded();
         String unique = uniqueName(newName);
         deleteFile(def.name);
         boolean wasActive = isActive(def);
         def.name = unique;
         writeToDisk(def);
         if (wasActive) {
            activeName = unique;
            saveActive();
         }

         return true;
      } else {
         return false;
      }
   }

   public static void delete(GuiStyleDef def) {
      if (def != null && !def.builtin) {
         ensureLoaded();
         CUSTOM.remove(def);
         deleteFile(def.name);
         if (isActive(def)) {
            select(BUILTINS.get(2));
         }
      }
   }

   public static boolean exportToClipboard(GuiStyleDef def) {
      if (def == null) {
         return false;
      } else {
         Gui gui = gui();
         NbtCompound settings = !def.builtin && def.settings != null ? def.settings : (gui != null ? gui.settings.toTag() : new NbtCompound());
         GuiStyleDef share = GuiStyleDef.custom(def.name, def.layout, settings);
         return NbtUtils.toClipboard(share.toTag());
      }
   }

   public static GuiStyleDef importFromClipboard() {
      ensureLoaded();
      NbtCompound tag = NbtUtils.fromClipboard();
      if (tag == null) {
         return null;
      } else {
         GuiStyleDef def = GuiStyleDef.fromTag(tag);
         def.name = uniqueName(def.name);
         CUSTOM.add(def);
         writeToDisk(def);
         select(def);
         return def;
      }
   }

   public static void load() {
      CUSTOM.clear();
      loaded = true;

      try {
         if (DIR.isDirectory()) {
            File[] files = DIR.listFiles((d, n) -> n.endsWith(".nbt") && !n.startsWith("_"));
            if (files != null) {
               for (File f : files) {
                  try {
                     NbtCompound t = NbtIo.read(f.toPath());
                     if (t != null) {
                        CUSTOM.add(GuiStyleDef.fromTag(t));
                     }
                  } catch (Exception var6) {
                     ReviveClient.LOG.error("Failed to read GUI style {}", f.getName(), var6);
                  }
               }
            }
         }

         File active = new File(DIR, "_active.nbt");
         if (active.exists()) {
            NbtCompound t = NbtIo.read(active.toPath());
            if (t != null) {
               activeName = t.getString("active", activeName);
            }
         }
      } catch (Exception var7) {
         ReviveClient.LOG.error("Failed to load GUI styles", var7);
      }
   }

   private static void ensureLoaded() {
      if (!loaded) {
         load();
      }
   }

   private static void writeToDisk(GuiStyleDef def) {
      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         NbtIo.write(def.toTag(), new File(DIR, fileName(def.name)).toPath());
      } catch (Exception var2) {
         ReviveClient.LOG.error("Failed to save GUI style {}", def.name, var2);
      }
   }

   private static void deleteFile(String name) {
      File f = new File(DIR, fileName(name));
      if (f.exists()) {
         f.delete();
      }
   }

   private static void saveActive() {
      try {
         if (!DIR.exists()) {
            DIR.mkdirs();
         }

         NbtCompound t = new NbtCompound();
         t.putString("active", activeName);
         NbtIo.write(t, new File(DIR, "_active.nbt").toPath());
      } catch (Exception var1) {
         ReviveClient.LOG.error("Failed to save active GUI style", var1);
      }
   }

   private static String uniqueName(String requested) {
      String base = requested != null && !requested.isBlank() ? requested.trim() : "Style";
      String name = base;
      int n = 2;

      while (nameTaken(name)) {
         name = base + " " + n++;
      }

      return name;
   }

   private static boolean nameTaken(String name) {
      for (GuiStyleDef d : BUILTINS) {
         if (d.name.equalsIgnoreCase(name)) {
            return true;
         }
      }

      for (GuiStyleDef dx : CUSTOM) {
         if (dx.name.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   private static String fileName(String name) {
      return name.replaceAll("[^a-zA-Z0-9-_ ]", "_") + ".nbt";
   }

   private static Gui gui() {
      return Modules.get().get(Gui.class);
   }
}
