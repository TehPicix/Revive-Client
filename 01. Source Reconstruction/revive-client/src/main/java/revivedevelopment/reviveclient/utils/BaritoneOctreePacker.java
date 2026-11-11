package revivedevelopment.reviveclient.utils;

import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

public final class BaritoneOctreePacker {
   public static final int HEIGHT = 128;
   private static final int SECTIONS = 8;

   private BaritoneOctreePacker() {
   }

   public static void pack(long context, WorldChunk chunk) {
      ChunkPos pos = chunk.getPos();
      long ptr = NetherPathfinder.getOrCreateChunk(context, pos.x, pos.z);
      ChunkSection[] sections = chunk.getSectionArray();
      int first = chunk.getSectionIndex(0);

      for (int s = 0; s < 8; s++) {
         int index = first + s;
         ChunkSection section = index >= 0 && index < sections.length ? sections[index] : null;
         int yBase = s << 4;
         if (section != null && !section.isEmpty()) {
            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     BlockState state = section.getBlockState(x, y, z);
                     Octree.setBlock(ptr, x, yBase + y, z, !state.isAir());
                  }
               }
            }
         } else {
            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     Octree.setBlock(ptr, x, yBase + y, z, false);
                  }
               }
            }
         }
      }

      Octree.setIsFromJava(ptr);
   }
}
