package revivedevelopment.reviveclient.events.revive;

import revivedevelopment.reviveclient.systems.modules.Module;

public class ModuleBindChangedEvent {
   private static final ModuleBindChangedEvent INSTANCE = new ModuleBindChangedEvent();
   public Module module;

   public static ModuleBindChangedEvent get(Module module) {
      INSTANCE.module = module;
      return INSTANCE;
   }
}
