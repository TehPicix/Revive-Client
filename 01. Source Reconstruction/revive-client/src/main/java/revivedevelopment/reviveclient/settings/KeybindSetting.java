package revivedevelopment.reviveclient.settings;

import java.util.function.Consumer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.revive.KeyEvent;
import revivedevelopment.reviveclient.events.revive.MouseButtonEvent;
import revivedevelopment.reviveclient.gui.widgets.WKeybind;
import revivedevelopment.reviveclient.mixin.pingbypass.RecipeBookScreenAccessor;
import revivedevelopment.reviveclient.mixin.pingbypass.RecipeBookWidgetAccessor;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.PingBypass;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.misc.input.KeyAction;

public class KeybindSetting extends Setting<Keybind> {
   private final Runnable action;
   public WKeybind widget;
   private static final String REVIVE_GUI = "revivedevelopment.reviveclient.gui.";

   public KeybindSetting(
      String name,
      String description,
      Keybind defaultValue,
      Consumer<Keybind> onChanged,
      Consumer<Setting<Keybind>> onModuleActivated,
      IVisible visible,
      Runnable action
   ) {
      super(name, description, defaultValue, onChanged, onModuleActivated, visible);
      this.action = action;
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   @EventHandler(
      priority = 200
   )
   private void onKeyBinding(KeyEvent event) {
      if (this.widget != null) {
         if (event.action == KeyAction.Press && event.key == 256 && this.widget.onClear()) {
            event.cancel();
         } else if (event.action == KeyAction.Release && this.widget.onAction(true, event.key, event.modifiers)) {
            event.cancel();
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onMouseButtonBinding(MouseButtonEvent event) {
      if (event.action == KeyAction.Press && this.widget != null && this.widget.onAction(false, event.button, 0)) {
         event.cancel();
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onKey(KeyEvent event) {
      if (event.action == KeyAction.Release
         && this.get().matches(true, event.key, event.modifiers)
         && (this.module == null || this.module.isActive())
         && this.action != null
         && screenAllowsBinds()) {
         this.trigger();
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onMouseButton(MouseButtonEvent event) {
      if (event.action == KeyAction.Release
         && this.get().matches(false, event.button, 0)
         && (this.module == null || this.module.isActive())
         && this.action != null
         && screenAllowsBinds()) {
         this.trigger();
      }
   }

   private static boolean screenAllowsBinds() {
      Screen screen = MinecraftClient.getInstance().currentScreen;
      if (screen == null) {
         return true;
      } else if (!(screen instanceof ChatScreen)
         && !(screen instanceof AbstractSignEditScreen)
         && !(screen instanceof BookEditScreen)
         && !(screen instanceof AnvilScreen)
         && !(screen instanceof CreativeInventoryScreen)
         && !(screen instanceof KeybindsScreen)) {
         if (screen.getClass().getName().startsWith("revivedevelopment.reviveclient.gui.")) {
            return false;
         } else {
            if (screen instanceof RecipeBookScreen<?> recipes) {
               RecipeBookWidget<?> book = ((RecipeBookScreenAccessor)recipes).revive$getRecipeBook();
               TextFieldWidget search = book == null ? null : ((RecipeBookWidgetAccessor)book).revive$getSearchField();
               if (book != null && book.isOpen() && search != null && search.isFocused()) {
                  return false;
               }
            }

            Element focused = screen.getFocused();
            return !(focused instanceof TextFieldWidget) && !(focused instanceof EditBoxWidget);
         }
      } else {
         return false;
      }
   }

   private void trigger() {
      if (this.module != null && this.module.isRemote()) {
         PingBypass pingBypass = Modules.get().get(PingBypass.class);
         if (pingBypass != null) {
            pingBypass.forwardAction(this);
         }
      } else {
         this.action.run();
      }
   }

   public void runAction() {
      if (this.action != null && (this.module == null || this.module.isActive())) {
         this.action.run();
      }
   }

   @Override
   public void resetImpl() {
      if (this.value == null) {
         this.value = this.defaultValue.copy();
      } else {
         this.value.set(this.defaultValue);
      }

      if (this.widget != null) {
         this.widget.reset();
      }
   }

   protected Keybind parseImpl(String str) {
      try {
         return Keybind.fromKey(Integer.parseInt(str.trim()));
      } catch (NumberFormatException var3) {
         return null;
      }
   }

   protected boolean isValueValid(Keybind value) {
      return true;
   }

   @Override
   public NbtCompound save(NbtCompound tag) {
      tag.put("value", this.get().toTag());
      return tag;
   }

   public Keybind load(NbtCompound tag) {
      this.get().fromTag(tag.getCompoundOrEmpty("value"));
      return this.get();
   }

   public static class Builder extends Setting.SettingBuilder<KeybindSetting.Builder, Keybind, KeybindSetting> {
      private Runnable action;

      public Builder() {
         super(Keybind.none());
      }

      public KeybindSetting.Builder action(Runnable action) {
         this.action = action;
         return this;
      }

      public KeybindSetting build() {
         return new KeybindSetting(this.name, this.description, this.defaultValue, this.onChanged, this.onModuleActivated, this.visible, this.action);
      }
   }
}
