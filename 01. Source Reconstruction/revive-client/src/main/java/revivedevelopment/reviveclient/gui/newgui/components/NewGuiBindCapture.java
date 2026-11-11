package revivedevelopment.reviveclient.gui.newgui.components;

import meteordevelopment.orbit.EventHandler;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.revive.KeyEvent;
import revivedevelopment.reviveclient.events.revive.ModuleBindChangedEvent;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

public final class NewGuiBindCapture {
   private static final NewGuiBindCapture INSTANCE = new NewGuiBindCapture();
   private static boolean subscribed = false;
   private KeybindSetting listeningSetting = null;

   private NewGuiBindCapture() {
   }

   public static NewGuiBindCapture get() {
      ensureSubscribed();
      return INSTANCE;
   }

   public static void ensureSubscribed() {
      if (!subscribed) {
         ReviveClient.EVENT_BUS.subscribe(INSTANCE);
         subscribed = true;
      }
   }

   public void startListeningForSetting(KeybindSetting setting) {
      this.listeningSetting = setting;
   }

   public boolean isListeningForSetting() {
      return this.listeningSetting != null;
   }

   public KeybindSetting getListeningSetting() {
      return this.listeningSetting;
   }

   public void cancelSettingListen() {
      this.listeningSetting = null;
   }

   @EventHandler(
      priority = 200
   )
   private void onKey(KeyEvent event) {
      if (this.listeningSetting != null) {
         if (event.action == KeyAction.Press && Modules.isClearKey(event.key)) {
            this.listeningSetting.get().set(Keybind.none());
            this.listeningSetting.onChanged();
            this.listeningSetting = null;
            event.cancel();
         } else {
            if (event.action == KeyAction.Release) {
               Keybind kb = this.listeningSetting.get();
               if (kb.canBindTo(true, event.key, event.modifiers)) {
                  kb.set(true, event.key, event.modifiers);
                  this.listeningSetting.onChanged();
               }

               this.listeningSetting = null;
               event.cancel();
            }
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onMouseButton(MouseButtonEvent event) {
      if (this.listeningSetting != null) {
         if (event.action == KeyAction.Release) {
            Keybind kb = this.listeningSetting.get();
            if (kb.canBindTo(false, event.button, 0)) {
               kb.set(false, event.button, 0);
               this.listeningSetting.onChanged();
            }

            this.listeningSetting = null;
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onModuleBindChanged(ModuleBindChangedEvent event) {
      ModuleButton.clearKeyListeningIfMatches(event.module);
   }
}
