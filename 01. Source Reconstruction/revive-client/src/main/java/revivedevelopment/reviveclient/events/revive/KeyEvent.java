package revivedevelopment.reviveclient.events.revive;

import revivedevelopment.reviveclient.events.Cancellable;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

public class KeyEvent extends Cancellable {
   private static final KeyEvent INSTANCE = new KeyEvent();
   public int key;
   public int modifiers;
   public KeyAction action;

   public static KeyEvent get(int key, int modifiers, KeyAction action) {
      INSTANCE.setCancelled(false);
      INSTANCE.key = key;
      INSTANCE.modifiers = modifiers;
      INSTANCE.action = action;
      return INSTANCE;
   }
}
