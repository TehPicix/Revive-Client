package revivedevelopment.reviveclient.utils.network;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import revivedevelopment.reviveclient.utils.PreInit;

public class ReviveExecutor {
   public static ExecutorService executor;

   private ReviveExecutor() {
   }

   @PreInit
   public static void init() {
      AtomicInteger threadNumber = new AtomicInteger(1);
      executor = Executors.newCachedThreadPool(task -> {
         Thread thread = new Thread(task);
         thread.setDaemon(true);
         thread.setName("Revive-Executor-" + threadNumber.getAndIncrement());
         return thread;
      });
   }

   public static void execute(Runnable task) {
      executor.execute(task);
   }
}
