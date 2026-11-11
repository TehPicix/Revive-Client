package revivedevelopment.reviveclient.utils.network;

public class OnlinePlayers {
   private static long lastPingTime;

   private OnlinePlayers() {
   }

   public static void update() {
      long time = System.currentTimeMillis();
      if (time - lastPingTime > 300000L) {
         ReviveExecutor.execute(() -> Http.post("https://reviveclient.com/api/online/ping").ignoreExceptions().send());
         lastPingTime = time;
      }
   }

   public static void leave() {
      ReviveExecutor.execute(() -> Http.post("https://reviveclient.com/api/online/leave").ignoreExceptions().send());
   }
}
