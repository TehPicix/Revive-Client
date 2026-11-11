package revivedevelopment.reviveclient.gui.widgets.pressable;

import revivedevelopment.reviveclient.gui.widgets.WWidget;

public abstract class WPressable extends WWidget {
   public Runnable action;
   protected boolean pressed;

   @Override
   public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
      if (this.mouseOver && (button == 0 || button == 1) && !used) {
         this.pressed = true;
      }

      return this.pressed;
   }

   @Override
   public boolean onMouseReleased(double mouseX, double mouseY, int button) {
      if (this.pressed) {
         this.onPressed(button);
         if (this.action != null) {
            this.action.run();
         }

         this.pressed = false;
      }

      return false;
   }

   protected void onPressed(int button) {
   }
}
