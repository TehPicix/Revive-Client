package revivedevelopment.reviveclient.events.game;

import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.events.Cancellable;

public class ReceiveMessageEvent extends Cancellable {
   private static final ReceiveMessageEvent INSTANCE = new ReceiveMessageEvent();
   private Text message;
   private MessageIndicator indicator;
   private boolean modified;
   public int id;

   public static ReceiveMessageEvent get(Text message, MessageIndicator indicator, int id) {
      INSTANCE.setCancelled(false);
      INSTANCE.message = message;
      INSTANCE.indicator = indicator;
      INSTANCE.modified = false;
      INSTANCE.id = id;
      return INSTANCE;
   }

   public Text getMessage() {
      return this.message;
   }

   public MessageIndicator getIndicator() {
      return this.indicator;
   }

   public void setMessage(Text message) {
      this.message = message;
      this.modified = true;
   }

   public void setIndicator(MessageIndicator indicator) {
      this.indicator = indicator;
      this.modified = true;
   }

   public boolean isModified() {
      return this.modified;
   }
}
