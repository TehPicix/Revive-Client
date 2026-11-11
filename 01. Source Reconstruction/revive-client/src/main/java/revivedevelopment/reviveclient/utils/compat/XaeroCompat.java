package revivedevelopment.reviveclient.utils.compat;

import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public final class XaeroCompat {
   public static final boolean MINIMAP = loaded("xaerominimap") || loaded("xaerominimapfair") || loaded("xaerobetterpvp") || loaded("xaerobetterpvpfair");
   public static final boolean WORLDMAP = loaded("xaeroworldmap");
   public static final boolean XAEROPLUS = loaded("xaeroplus");

   private XaeroCompat() {
   }

   private static boolean loaded(String id) {
      return FabricLoader.getInstance().isModLoaded(id);
   }

   public static Object newChunkListener(Consumer<WorldChunk> onNewChunk) {
      return XAEROPLUS ? XaeroImpl.newChunkListener(onNewChunk) : null;
   }

   public static void register(Object listener) {
      if (XAEROPLUS && listener != null) {
         XaeroImpl.register(listener);
      }
   }

   public static void unregister(Object listener) {
      if (XAEROPLUS && listener != null) {
         XaeroImpl.unregister(listener);
      }
   }

   public static boolean isNewChunk(int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return XAEROPLUS && XaeroImpl.isNewChunk(chunkX, chunkZ, dimension);
   }

   public static boolean isInverseNewChunk(int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return XAEROPLUS && XaeroImpl.isInverseNewChunk(chunkX, chunkZ, dimension);
   }

   public static boolean isOldChunk(int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return XAEROPLUS && XaeroImpl.isOldChunk(chunkX, chunkZ, dimension);
   }

   public static Object getPortalsModule() {
      return XAEROPLUS ? XaeroImpl.getPortalsModule() : null;
   }

   public static Object getPortalSkipModule() {
      return XAEROPLUS ? XaeroImpl.getPortalSkipModule() : null;
   }

   public static boolean isModuleEnabled(Object module) {
      return XAEROPLUS && module != null && XaeroImpl.isModuleEnabled(module);
   }

   public static boolean isPortalChunk(Object portalsModule, int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return XAEROPLUS && portalsModule != null && XaeroImpl.isPortalChunk(portalsModule, chunkX, chunkZ, dimension);
   }

   public static boolean isPortalSkipChunk(Object portalSkipModule, int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return XAEROPLUS && portalSkipModule != null && XaeroImpl.isPortalSkipChunk(portalSkipModule, chunkX, chunkZ, dimension);
   }

   public static Object getWaypointSet() {
      return MINIMAP ? XaeroImpl.getWaypointSet() : null;
   }

   public static Object findWaypoint(Object waypointSet, int x, int z, String name) {
      return MINIMAP && waypointSet != null ? XaeroImpl.findWaypoint(waypointSet, x, z, name) : null;
   }

   public static void addWaypoint(Object waypointSet, int x, int y, int z, String name, String initials, int color, int type, boolean temporary) {
      if (MINIMAP && waypointSet != null) {
         XaeroImpl.addWaypoint(waypointSet, x, y, z, name, initials, color, type, temporary);
      }
   }

   public static void removeWaypoint(Object waypointSet, Object waypoint) {
      if (MINIMAP && waypointSet != null && waypoint != null) {
         XaeroImpl.removeWaypoint(waypointSet, waypoint);
      }
   }

   public static void addWaypointIfAbsent(int x, int y, int z, String name, String initials, int colorIndex) {
      if (MINIMAP) {
         XaeroImpl.addWaypointIfAbsent(x, y, z, name, initials, colorIndex);
      }
   }

   public static void refreshWaypoints() {
      if (WORLDMAP) {
         XaeroImpl.refreshWaypoints();
      }
   }
}
