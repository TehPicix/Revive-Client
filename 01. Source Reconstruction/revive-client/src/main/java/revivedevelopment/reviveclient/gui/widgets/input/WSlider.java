package revivedevelopment.reviveclient.gui.widgets.input;

import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.gui.widgets.WWidget;

public abstract class WSlider extends WWidget {
   public Runnable action;
   public Runnable actionOnRelease;
   protected double value;
   protected double min;
   protected double max;
   protected double scrollHandleX;
   protected double scrollHandleY;
   protected double scrollHandleH;
   protected boolean scrollHandleMouseOver;
   protected boolean handleMouseOver;
   protected boolean dragging;
   protected double valueAtDragStart;

   public WSlider(double value, double min, double max) {
      this.value = MathHelper.clamp(value, min, max);
      this.min = min;
      this.max = max;
   }

   protected double handleSize() {
      return this.theme.textHeight();
   }

   @Override
   protected void onCalculateSize() {
      double s = this.handleSize();
      this.width = s;
      this.height = s;
   }

   @Override
   public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
      if (this.mouseOver && !used) {
         this.valueAtDragStart = this.value;
         double handleSize = this.handleSize();
         double valueWidth = mouseX - (this.x + handleSize / 2.0);
         this.set(valueWidth / (this.width - handleSize) * (this.max - this.min) + this.min);
         if (this.action != null) {
            this.action.run();
         }

         this.dragging = true;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
      double valueWidth = this.valueWidth();
      double s = this.handleSize();
      double s2 = s / 2.0;
      double x = this.x + s2 + valueWidth - this.height / 2.0;
      this.handleMouseOver = mouseX >= x && mouseX <= x + this.height && mouseY >= this.y && mouseY <= this.y + this.height;
      if (!this.scrollHandleMouseOver) {
         this.scrollHandleX = x;
         this.scrollHandleY = this.y;
         this.scrollHandleH = this.height;
         if (this.handleMouseOver) {
            this.scrollHandleMouseOver = true;
         }
      } else {
         this.scrollHandleMouseOver = mouseX >= this.scrollHandleX
            && mouseX <= this.scrollHandleX + this.scrollHandleH
            && mouseY >= this.scrollHandleY
            && mouseY <= this.scrollHandleY + this.scrollHandleH;
      }

      boolean mouseOverX = mouseX >= this.x + s2 && mouseX <= this.x + s2 + this.width - s;
      this.mouseOver = mouseOverX && mouseY >= this.y && mouseY <= this.y + this.height;
      if (this.dragging) {
         if (mouseOverX) {
            valueWidth += mouseX - lastMouseX;
            valueWidth = MathHelper.clamp(valueWidth, 0.0, this.width - s);
            this.set(valueWidth / (this.width - s) * (this.max - this.min) + this.min);
            if (this.action != null) {
               this.action.run();
            }
         } else if (this.value > this.min && mouseX < this.x + s2) {
            this.value = this.min;
            if (this.action != null) {
               this.action.run();
            }
         } else if (this.value < this.max && mouseX > this.x + s2 + this.width - s) {
            this.value = this.max;
            if (this.action != null) {
               this.action.run();
            }
         }
      }
   }

   @Override
   public boolean onMouseReleased(double mouseX, double mouseY, int button) {
      if (this.dragging) {
         if (this.value != this.valueAtDragStart && this.actionOnRelease != null) {
            this.actionOnRelease.run();
         }

         this.dragging = false;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean onMouseScrolled(double amount) {
      if (!this.scrollHandleMouseOver && this.handleMouseOver) {
         this.scrollHandleX = this.x;
         this.scrollHandleY = this.y;
         this.scrollHandleH = this.height;
         this.scrollHandleMouseOver = true;
      }

      if (this.scrollHandleMouseOver) {
         if (this.parent instanceof WIntEdit) {
            this.set(this.value + amount);
         } else {
            this.set(this.value + 0.05 * amount);
         }

         if (this.action != null) {
            this.action.run();
         }

         return true;
      } else {
         return false;
      }
   }

   public void set(double value) {
      this.value = MathHelper.clamp(value, this.min, this.max);
   }

   public double get() {
      return this.value;
   }

   protected double valueWidth() {
      double valuePercentage = (this.value - this.min) / (this.max - this.min);
      return valuePercentage * (this.width - this.handleSize());
   }
}
