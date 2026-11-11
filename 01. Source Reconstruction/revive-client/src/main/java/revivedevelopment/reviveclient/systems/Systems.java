package revivedevelopment.reviveclient.systems;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import meteordevelopment.orbit.EventHandler;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.profiles.Profiles;

public class Systems {
   private static final Map<Class<? extends System>, System<?>> systems = new Reference2ReferenceOpenHashMap();
   private static final List<Runnable> preLoadTasks = new ArrayList<>(1);

   public static void addPreLoadTask(Runnable task) {
      preLoadTasks.add(task);
   }

   public static void init() {
      add(new Modules());
      Config config = new Config();
      System<?> configSystem = add(config);
      configSystem.init();
      configSystem.load();
      config.settings.registerColorSettings(null);
      add(new Friends());
      add(new Profiles());
      add(new Hud());
      ReviveClient.EVENT_BUS.subscribe(Systems.class);
   }

   public static System<?> add(System<?> system) {
      systems.put((Class<? extends System>)system.getClass(), system);
      ReviveClient.EVENT_BUS.subscribe(system);
      system.init();
      return system;
   }

   @EventHandler
   private static void onGameLeft(GameLeftEvent event) {
      save();
   }

   public static void save(File folder) {
      long start = java.lang.System.currentTimeMillis();
      ReviveClient.LOG.info("Saving");

      for (System<?> system : systems.values()) {
         try {
            system.save(folder);
         } catch (Throwable var6) {
            ReviveClient.LOG.error("Failed to save system '{}'", system.getName(), var6);
         }
      }

      ReviveClient.LOG.info("Saved in {} milliseconds.", java.lang.System.currentTimeMillis() - start);
   }

   public static void save() {
      save(null);
   }

   public static void load(File folder) {
      long start = java.lang.System.currentTimeMillis();
      ReviveClient.LOG.info("Loading");

      for (Runnable task : preLoadTasks) {
         task.run();
      }

      for (System<?> system : systems.values()) {
         try {
            system.load(folder);
         } catch (Throwable var6) {
            ReviveClient.LOG.error("Failed to load system '{}'", system.getName(), var6);
         }
      }

      ReviveClient.LOG.info("Loaded in {} milliseconds", java.lang.System.currentTimeMillis() - start);
   }

   public static void load() {
      load(null);
   }

   public static <T extends System<?>> T get(Class<T> klass) {
      return (T)systems.get(klass);
   }
}
