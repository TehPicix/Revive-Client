package revivedevelopment.reviveclient.systems.modules.combat.autocrystal;

import java.util.concurrent.ConcurrentLinkedQueue;

public class Counter {
   private final ConcurrentLinkedQueue<Long> counter = new ConcurrentLinkedQueue<>();

   public void updateCounter() {
      long time = System.currentTimeMillis();
      this.counter.add(time + 1000L);
      this.prune(time);
   }

   public int getPerSecond() {
      this.prune(System.currentTimeMillis());
      return this.counter.size();
   }

   private void prune(long time) {
      while (!this.counter.isEmpty()) {
         Long peek = this.counter.peek();
         if (peek != null && peek < time) {
            this.counter.poll();
            continue;
         }
         break;
      }
   }

   public void reset() {
      this.counter.clear();
   }
}
