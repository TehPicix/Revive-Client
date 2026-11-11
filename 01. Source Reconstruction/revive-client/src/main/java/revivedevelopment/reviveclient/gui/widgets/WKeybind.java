package revivedevelopment.reviveclient.gui.widgets;

import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.misc.Keybind;

public class WKeybind extends WHorizontalList {
   public Runnable action;
   public Runnable actionOnSet;
   private WButton button;
   private final Keybind keybind;
   private final Keybind defaultValue;
   private boolean listening;

   public WKeybind(Keybind keybind, Keybind defaultValue) {
      this.keybind = keybind;
      this.defaultValue = defaultValue;
   }

   @Override
   public void init() {
      this.button = this.add(this.theme.button("")).widget();
      this.button.action = () -> {
         this.listening = true;
         this.button.set("...");
         if (this.actionOnSet != null) {
            this.actionOnSet.run();
         }
      };
      this.refreshLabel();
   }

   public boolean onClear() {
      if (this.listening) {
         this.keybind.reset();
         this.reset();
         return true;
      } else {
         return false;
      }
   }

   public boolean onAction(boolean isKey, int value, int modifiers) {
      if (this.listening && this.keybind.canBindTo(isKey, value, modifiers)) {
         this.keybind.set(isKey, value, modifiers);
         this.reset();
         return true;
      } else {
         return false;
      }
   }

   public void resetBind() {
      this.keybind.set(this.defaultValue);
      this.reset();
   }

   public void reset() {
      this.listening = false;
      this.refreshLabel();
      if (Modules.get().isBinding()) {
         Modules.get().setModuleToBind(null);
      }
   }

   private void refreshLabel() {
      this.button.set(this.keybind.toString());
   }
}
