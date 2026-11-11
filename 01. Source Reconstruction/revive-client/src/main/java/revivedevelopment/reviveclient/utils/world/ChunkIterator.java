package revivedevelopment.reviveclient.utils.world;

import java.util.Iterator;
import net.minecraft.world.chunk.Chunk;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixin.ClientChunkManagerAccessor;
import revivedevelopment.reviveclient.mixin.ClientChunkMapAccessor;

public class ChunkIterator implements Iterator<Chunk> {
   private final ClientChunkMapAccessor map = (ClientChunkMapAccessor)(Object)((ClientChunkManagerAccessor)ReviveClient.mc.world.getChunkManager()).getChunks();
   private final boolean onlyWithLoadedNeighbours;
   private int i = 0;
   private Chunk chunk;

   public ChunkIterator(boolean onlyWithLoadedNeighbours) {
      this.onlyWithLoadedNeighbours = onlyWithLoadedNeighbours;
      this.getNext();
   }

   private Chunk getNext() {
      Chunk prev = this.chunk;
      this.chunk = null;

      while (this.i < this.map.getChunks().length()) {
         this.chunk = (Chunk)this.map.getChunks().get(this.i++);
         if (this.chunk != null && (!this.onlyWithLoadedNeighbours || this.isInRadius(this.chunk))) {
            break;
         }
      }

      return prev;
   }

   private boolean isInRadius(Chunk chunk) {
      int x = chunk.getPos().x;
      int z = chunk.getPos().z;
      return ReviveClient.mc.world.getChunkManager().isChunkLoaded(x + 1, z)
         && ReviveClient.mc.world.getChunkManager().isChunkLoaded(x - 1, z)
         && ReviveClient.mc.world.getChunkManager().isChunkLoaded(x, z + 1)
         && ReviveClient.mc.world.getChunkManager().isChunkLoaded(x, z - 1);
   }

   @Override
   public boolean hasNext() {
      return this.chunk != null;
   }

   public Chunk next() {
      return this.getNext();
   }
}
