package revivedevelopment.reviveclient.utils.misc.input;

import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.GuiKeyEvents;
import revivedevelopment.reviveclient.mixin.KeyBindingAccessor;
import revivedevelopment.reviveclient.utils.misc.CursorStyle;

public class Input {
   private static final boolean[] keys = new boolean[512];
   private static final boolean[] buttons = new boolean[16];
   private static CursorStyle lastCursorStyle = CursorStyle.Default;

   private Input() {
   }

   public static void setKeyState(int key, boolean pressed) {
      if (key >= 0 && key < keys.length) {
         keys[key] = pressed;
      }
   }

   public static void setButtonState(int button, boolean pressed) {
      if (button >= 0 && button < buttons.length) {
         buttons[button] = pressed;
      }
   }

   public static int getKey(KeyBinding bind) {
      return ((KeyBindingAccessor)bind).getKey().getCode();
   }

   public static void setKeyState(KeyBinding bind, boolean pressed) {
      setKeyState(getKey(bind), pressed);
   }

   public static boolean isPressed(KeyBinding bind) {
      return isKeyPressed(getKey(bind));
   }

   public static boolean isKeyPressed(int key) {
      if (!GuiKeyEvents.canUseKeys) {
         return false;
      } else {
         return key == -1 ? false : key < keys.length && keys[key];
      }
   }

   public static boolean isButtonPressed(int button) {
      return button == -1 ? false : button < buttons.length && buttons[button];
   }

   public static void setCursorStyle(CursorStyle style) {
      if (lastCursorStyle != style) {
         GLFW.glfwSetCursor(ReviveClient.mc.getWindow().getHandle(), style.getGlfwCursor());
         lastCursorStyle = style;
      }
   }

   public static int getModifier(int key) {
      return switch (key) {
         case 340, 344 -> 1;
         case 341, 345 -> 2;
         case 342, 346 -> 4;
         case 343, 347 -> 8;
         default -> 0;
      };
   }
}
