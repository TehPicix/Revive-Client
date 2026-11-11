package revivedevelopment.reviveclient.utils.misc.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil.Type;
import revivedevelopment.reviveclient.ReviveClient;

public class KeyBinds {
   private static final Category CATEGORY = Category.create(ReviveClient.identifier("revive-client"));
   public static KeyBinding OPEN_GUI = new KeyBinding("key.revive-client.open-gui", Type.KEYSYM, 344, CATEGORY);
   public static KeyBinding OPEN_COMMANDS = new KeyBinding("key.revive-client.open-commands", Type.KEYSYM, 46, CATEGORY);

   private KeyBinds() {
   }

   public static KeyBinding[] apply(KeyBinding[] binds) {
      KeyBinding[] newBinds = new KeyBinding[binds.length + 2];
      System.arraycopy(binds, 0, newBinds, 0, binds.length);
      newBinds[binds.length] = OPEN_GUI;
      newBinds[binds.length + 1] = OPEN_COMMANDS;
      return newBinds;
   }
}
