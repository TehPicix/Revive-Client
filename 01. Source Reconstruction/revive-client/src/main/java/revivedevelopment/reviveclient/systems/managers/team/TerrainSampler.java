package revivedevelopment.reviveclient.systems.managers.team;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public final class TerrainSampler {
   public static final long RECHECK_MS = 30000L;
   private static final int MAX_SAMPLES_PER_TICK = 2;
   private static final long IDLE_BACKOFF_MS = 1000L;
   private static final long PRUNE_EVERY_MS = 5000L;
   private static final int MAX_TRACKED = 20000;
   private static final int MAX_RADIUS = 32;
   private static final int MAX_WATER_DEPTH = 16;
   private static final int B_LOW = 180;
   private static final int B_NORMAL = 220;
   private static final int B_HIGH = 255;
   private final Consumer<String> sink;
   private final BooleanSupplier hasRoom;
   private final Long2ObjectOpenHashMap<TerrainSampler.Entry> tracked = new Long2ObjectOpenHashMap();
   private final Mutable pos = new Mutable();
   private final int[] heights = new int[256];
   private final int[] depths = new int[256];
   private final MapColor[] colors = new MapColor[256];
   private final int[] northHeights = new int[16];
   private final byte[] rgb = new byte[768];
   private ClientWorld lastWorld;
   private String lastDim;
   private long idleUntil;
   private long lastPrune;
   private int colHeight;
   private int colDepth;
   private MapColor colColor;

   public TerrainSampler(Consumer<String> sink, BooleanSupplier hasRoom) {
      this.sink = sink;
      this.hasRoom = hasRoom;
   }

   public void reset() {
      this.tracked.clear();
      this.lastWorld = null;
      this.lastDim = null;
      this.idleUntil = 0L;
      this.lastPrune = 0L;
   }

   public int trackedCount() {
      return this.tracked.size();
   }

   public void tick(ClientWorld world, PlayerEntity player, String dim, int viewDistance) {
      if (world != null && player != null) {
         if (world != this.lastWorld || !dim.equals(this.lastDim)) {
            this.reset();
            this.lastWorld = world;
            this.lastDim = dim;
         }

         long now = System.currentTimeMillis();
         int radius = Math.max(2, Math.min(32, viewDistance));
         int pcx = player.getChunkPos().x;
         int pcz = player.getChunkPos().z;
         if (now - this.lastPrune > 5000L || this.tracked.size() > 20000) {
            this.lastPrune = now;
            this.prune(pcx, pcz, Math.max(8, radius * 2));
         }

         if (now >= this.idleUntil && this.hasRoom.getAsBoolean()) {
            boolean nether = world.getRegistryKey() == World.NETHER;
            int budget = nether ? 1 : 2;
            int startY = Math.min(player.getBlockY() + 16, 120);
            int sampled = 0;

            for (int r = 0; r <= radius; r++) {
               if (r == 0) {
                  if (this.visit(world, pcx, pcz, nether, startY, now)) {
                     sampled++;
                  }
               } else {
                  for (int d = -r; d <= r && sampled < budget; d++) {
                     if (this.visit(world, pcx + d, pcz - r, nether, startY, now)) {
                        sampled++;
                     }

                     if (sampled < budget && this.visit(world, pcx + d, pcz + r, nether, startY, now)) {
                        sampled++;
                     }
                  }

                  for (int d = -r + 1; d <= r - 1 && sampled < budget; d++) {
                     if (this.visit(world, pcx - r, pcz + d, nether, startY, now)) {
                        sampled++;
                     }

                     if (sampled < budget && this.visit(world, pcx + r, pcz + d, nether, startY, now)) {
                        sampled++;
                     }
                  }
               }

               if (sampled >= budget || !this.hasRoom.getAsBoolean()) {
                  return;
               }
            }

            if (sampled == 0) {
               this.idleUntil = now + 1000L;
            }
         }
      }
   }

   private boolean visit(ClientWorld world, int cx, int cz, boolean nether, int startY, long now) {
      long key = ChunkPos.toLong(cx, cz);
      TerrainSampler.Entry e = (TerrainSampler.Entry)this.tracked.get(key);
      if (e != null && now - e.checkedAt < 30000L) {
         return false;
      } else if (!this.hasRoom.getAsBoolean()) {
         return false;
      } else if (world.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof WorldChunk chunk) {
         this.sample(world, chunk, cx, cz, nether, startY);
         int hash = Arrays.hashCode(this.rgb);
         if (e == null) {
            e = new TerrainSampler.Entry();
            this.tracked.put(key, e);
         } else if (e.hash == hash) {
            e.checkedAt = now;
            return true;
         }

         e.hash = hash;
         e.checkedAt = now;
         JsonObject o = new JsonObject();
         o.addProperty("t", "tile");
         o.addProperty("dim", this.lastDim);
         o.addProperty("cx", cx);
         o.addProperty("cz", cz);
         o.addProperty("c", Base64.getEncoder().encodeToString(this.rgb));
         this.sink.accept(o.toString());
         return true;
      } else {
         return false;
      }
   }

   private void prune(int pcx, int pcz, int maxDist) {
      LongIterator it = this.tracked.keySet().iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (Math.abs(ChunkPos.getPackedX(key) - pcx) > maxDist || Math.abs(ChunkPos.getPackedZ(key) - pcz) > maxDist) {
            it.remove();
         }
      }

      if (this.tracked.size() > 20000) {
         this.tracked.clear();
      }
   }

   private void sample(ClientWorld world, WorldChunk chunk, int cx, int cz, boolean nether, int startY) {
      int baseX = cx << 4;
      int baseZ = cz << 4;
      Heightmap hm = nether ? null : chunk.getHeightmap(Type.WORLD_SURFACE);

      for (int z = 0; z < 16; z++) {
         for (int x = 0; x < 16; x++) {
            int i = z * 16 + x;
            this.column(world, chunk, hm, baseX + x, baseZ + z, x, z, nether, startY);
            this.heights[i] = this.colHeight;
            this.colors[i] = this.colColor;
            this.depths[i] = this.colDepth;
         }
      }

      if (world.getChunk(cx, cz - 1, ChunkStatus.FULL, false) instanceof WorldChunk north) {
         Heightmap nhm = nether ? null : north.getHeightmap(Type.WORLD_SURFACE);

         for (int x = 0; x < 16; x++) {
            this.column(world, north, nhm, baseX + x, baseZ - 1, x, 15, nether, startY);
            this.northHeights[x] = this.colHeight;
         }
      } else {
         System.arraycopy(this.heights, 0, this.northHeights, 0, 16);
      }

      for (int z = 0; z < 16; z++) {
         for (int x = 0; x < 16; x++) {
            int i = z * 16 + x;
            MapColor color = this.colors[i];
            int r = 0;
            int g = 0;
            int b = 0;
            if (color != null && color != MapColor.CLEAR) {
               int odd = baseX + x + baseZ + z & 1;
               int bright;
               if (color == MapColor.WATER_BLUE) {
                  double f = this.depths[i] * 0.1 + odd * 0.2;
                  bright = f < 0.5 ? 255 : (f > 0.9 ? 180 : 220);
               } else {
                  int prev = z == 0 ? this.northHeights[x] : this.heights[i - 16];
                  double f = (this.heights[i] - prev) * 4.0 / 5.0 + (odd - 0.5) * 0.4;
                  bright = f > 0.6 ? 255 : (f < -0.6 ? 180 : 220);
               }

               int c = color.color;
               r = (c >> 16 & 0xFF) * bright / 255;
               g = (c >> 8 & 0xFF) * bright / 255;
               b = (c & 0xFF) * bright / 255;
            }

            this.rgb[i * 3] = (byte)r;
            this.rgb[i * 3 + 1] = (byte)g;
            this.rgb[i * 3 + 2] = (byte)b;
         }
      }
   }

   private void column(ClientWorld world, WorldChunk chunk, Heightmap hm, int wx, int wz, int lx, int lz, boolean nether, int startY) {
      int bottom = world.getBottomY();
      this.colDepth = 0;
      this.colColor = MapColor.CLEAR;
      this.colHeight = bottom;
      BlockState state = null;
      int y;
      if (nether) {
         int top = Math.min(startY, bottom + world.getHeight() - 1);
         boolean air = false;

         for (y = top; y >= bottom; y--) {
            this.pos.set(wx, y, wz);
            BlockState s = chunk.getBlockState(this.pos);
            if (s.isAir()) {
               air = true;
            } else if (air) {
               MapColor mc = s.getMapColor(world, this.pos);
               if (mc != MapColor.CLEAR) {
                  state = s;
                  this.colColor = mc;
                  break;
               }
            }
         }

         if (state == null) {
            return;
         }
      } else {
         for (y = hm.get(lx, lz) - 1; y >= bottom; y--) {
            this.pos.set(wx, y, wz);
            BlockState s = chunk.getBlockState(this.pos);
            MapColor mc = s.getMapColor(world, this.pos);
            if (mc != MapColor.CLEAR) {
               state = s;
               this.colColor = mc;
               break;
            }
         }

         if (state == null) {
            return;
         }
      }

      this.colHeight = y;
      if (!state.getFluidState().isEmpty()) {
         int d = 1;

         for (int yy = y - 1; yy >= bottom && d < 16; yy--) {
            this.pos.set(wx, yy, wz);
            if (chunk.getBlockState(this.pos).getFluidState().isEmpty()) {
               break;
            }

            d++;
         }

         this.colDepth = d;
      }
   }

   private static final class Entry {
      int hash;
      long checkedAt;
   }
}
