package revivedevelopment.reviveclient.utils.misc.text;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.ClickEvent.Action;

public class ReviveClickEvent implements ClickEvent {
   public final String value;

   public ReviveClickEvent(String value) {
      this.value = value;
   }

   public Action getAction() {
      return Action.RUN_COMMAND;
   }
}
