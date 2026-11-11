package revivedevelopment.reviveclient.systems.modules.player;

import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;

public class NoRotate extends Module {
   public NoRotate() {
      super(Categories.Player, "no-rotate", "Ignores server-forced rotations (teleports, setbacks) so your camera doesn't snap.");
   }

   public static boolean on() {
      Modules modules = Modules.get();
      if (modules == null) {
         return false;
      } else {
         NoRotate m = modules.get(NoRotate.class);
         return m != null && m.isActive();
      }
   }
}
