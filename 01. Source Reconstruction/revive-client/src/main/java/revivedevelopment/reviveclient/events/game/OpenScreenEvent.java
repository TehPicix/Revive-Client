package revivedevelopment.reviveclient.events.game;

import net.minecraft.client.gui.screen.Screen;
import revivedevelopment.reviveclient.events.Cancellable;

public class OpenScreenEvent extends Cancellable {
   private static final OpenScreenEvent INSTANCE = new OpenScreenEvent();
   public Screen screen;

   public static OpenScreenEvent get(Screen screen) {
      INSTANCE.setCancelled(false);
      INSTANCE.screen = screen;
      return INSTANCE;
   }
}
