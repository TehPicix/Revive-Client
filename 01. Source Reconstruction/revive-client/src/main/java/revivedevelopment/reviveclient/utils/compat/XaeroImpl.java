package revivedevelopment.reviveclient.utils.compat;

import java.util.function.Consumer;
import net.lenni0451.lambdaevents.EventHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.map.mods.SupportMods;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.Module;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import xaeroplus.module.impl.PortalSkipDetection;
import xaeroplus.module.impl.Portals;

public final class XaeroImpl {
   private XaeroImpl() {
   }

   static Object newChunkListener(Consumer<WorldChunk> onNewChunk) {
      return new XaeroImpl.ChunkListener(onNewChunk);
   }

   static void register(Object listener) {
      XaeroPlus.EVENT_BUS.register(listener);
   }

   static void unregister(Object listener) {
      XaeroPlus.EVENT_BUS.unregister(listener);
   }

   static boolean isNewChunk(int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return ((PaletteNewChunks)ModuleManager.getModule(PaletteNewChunks.class)).isNewChunk(chunkX, chunkZ, dimension);
   }

   static boolean isInverseNewChunk(int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return ((PaletteNewChunks)ModuleManager.getModule(PaletteNewChunks.class)).isInverseNewChunk(chunkX, chunkZ, dimension);
   }

   static boolean isOldChunk(int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return ((OldChunks)ModuleManager.getModule(OldChunks.class)).isOldChunk(chunkX, chunkZ, dimension);
   }

   static Object getPortalsModule() {
      return ModuleManager.getModule(Portals.class);
   }

   static Object getPortalSkipModule() {
      return ModuleManager.getModule(PortalSkipDetection.class);
   }

   static boolean isModuleEnabled(Object module) {
      return ((Module)module).isEnabled();
   }

   static boolean isPortalChunk(Object portalsModule, int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return ((Portals)portalsModule).isPortalChunk(chunkX, chunkZ, dimension);
   }

   static boolean isPortalSkipChunk(Object portalSkipModule, int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return ((PortalSkipDetection)portalSkipModule).isPortalSkipChunk(chunkX, chunkZ, dimension);
   }

   static Object getWaypointSet() {
      try {
         MinimapSession minimapSession = (MinimapSession)BuiltInHudModules.MINIMAP.getCurrentSession();
         if (minimapSession == null) {
            return null;
         } else {
            MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
            return currentWorld == null ? null : currentWorld.getCurrentWaypointSet();
         }
      } catch (Throwable var2) {
         return null;
      }
   }

   static Object findWaypoint(Object waypointSet, int x, int z, String name) {
      try {
         for (Waypoint waypoint : ((WaypointSet)waypointSet).getWaypoints()) {
            if (waypoint.getX() == x && waypoint.getZ() == z && (name == null || name.equals(waypoint.getName()))) {
               return waypoint;
            }
         }
      } catch (Throwable var6) {
      }

      return null;
   }

   static void addWaypoint(Object waypointSet, int x, int y, int z, String name, String initials, int color, int type, boolean temporary) {
      try {
         Waypoint waypoint = new Waypoint(x, y, z, name, initials, color, type, temporary);
         ((WaypointSet)waypointSet).add(waypoint);
      } catch (Throwable var10) {
      }
   }

   static void removeWaypoint(Object waypointSet, Object waypoint) {
      try {
         ((WaypointSet)waypointSet).remove((Waypoint)waypoint);
      } catch (Throwable var3) {
      }
   }

   static void addWaypointIfAbsent(int x, int y, int z, String name, String initials, int colorIndex) {
      try {
         MinimapSession session = (MinimapSession)BuiltInHudModules.MINIMAP.getCurrentSession();
         if (session == null) {
            return;
         }

         MinimapWorld world = session.getWorldManager().getCurrentWorld();
         if (world == null) {
            return;
         }

         WaypointSet set = world.getCurrentWaypointSet();
         if (set == null) {
            return;
         }

         for (Waypoint wp : set.getWaypoints()) {
            if (wp.getX() == x && wp.getZ() == z) {
               return;
            }
         }

         set.add(new Waypoint(x, y, z, name, initials, WaypointColor.fromIndex(colorIndex), WaypointPurpose.NORMAL));
         SupportMods.xaeroMinimap.requestWaypointsRefresh();
      } catch (Throwable var11) {
      }
   }

   static void refreshWaypoints() {
      try {
         SupportMods.xaeroMinimap.requestWaypointsRefresh();
      } catch (Throwable var1) {
      }
   }

   public static final class ChunkListener {
      private final Consumer<WorldChunk> onNewChunk;

      private ChunkListener(Consumer<WorldChunk> onNewChunk) {
         this.onNewChunk = onNewChunk;
      }

      @EventHandler(
         priority = -1
      )
      public void onChunkData(ChunkDataEvent event) {
         if (!event.seenChunk()) {
            this.onNewChunk.accept(event.chunk());
         }
      }
   }
}
