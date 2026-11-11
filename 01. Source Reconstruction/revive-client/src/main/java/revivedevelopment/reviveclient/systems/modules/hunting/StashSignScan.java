package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class StashSignScan<C, P> {
   private List<C> chunks = List.of();
   private List<P> positions = List.of();
   private C chunk;
   private int chunkIndex;
   private int positionIndex;
   private long generation;
   private boolean active;

   boolean active() {
      return this.active;
   }

   void clear() {
      this.generation++;
      this.active = false;
      this.chunks = List.of();
      this.positions = List.of();
      this.chunk = null;
      this.chunkIndex = this.positionIndex = 0;
   }

   void begin(Collection<C> var1) {
      this.clear();
      this.chunks = List.copyOf(var1);
      this.active = !this.chunks.isEmpty();
   }

   void step(int var1, int var2, Function<C, ? extends Collection<P>> var3, BiConsumer<C, P> var4) {
      if (var1 >= 1 && var2 >= 1) {
         long var5 = this.generation;
         int var7 = 0;
         int var8 = 0;

         while (this.active && this.generation == var5) {
            if (this.positionIndex == this.positions.size()) {
               this.positions = List.of();
               this.positionIndex = 0;
               if (this.chunkIndex == this.chunks.size()) {
                  this.clear();
                  return;
               }

               if (var7 == var1 || var8 == var2) {
                  return;
               }

               this.chunk = this.chunks.get(this.chunkIndex++);
               Collection<P> var10 = (Collection<P>)var3.apply(this.chunk);
               if (this.generation != var5) {
                  return;
               }

               this.positions = List.copyOf(var10);
               var7++;
            } else {
               if (var8 == var2) {
                  return;
               }

               P var9 = this.positions.get(this.positionIndex++);
               var8++;
               var4.accept(this.chunk, var9);
            }
         }
      } else {
         throw new IllegalArgumentException("Positive scan budgets required");
      }
   }
}
