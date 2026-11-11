package revivedevelopment.reviveclient.events.game;

import revivedevelopment.reviveclient.events.Cancellable;

public class SendMessageEvent extends Cancellable {
   private static final SendMessageEvent INSTANCE = new SendMessageEvent();
   public String message;

   public static SendMessageEvent get(String message) {
      INSTANCE.setCancelled(false);
      INSTANCE.message = message;
      return INSTANCE;
   }
}
