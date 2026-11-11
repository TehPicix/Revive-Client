package revivedevelopment.reviveclient.utils.misc;

import org.lwjgl.glfw.GLFW;

public enum CursorStyle {
   Default,
   Click,
   Type;

   private boolean created;
   private long cursor;

   public long getGlfwCursor() {
      if (!this.created) {
         switch (this) {
            case Click:
               this.cursor = GLFW.glfwCreateStandardCursor(221188);
               break;
            case Type:
               this.cursor = GLFW.glfwCreateStandardCursor(221186);
         }

         this.created = true;
      }

      return this.cursor;
   }
}
