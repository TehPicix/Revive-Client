package revivedevelopment.reviveclient.gui.widgets.containers;

import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.utils.Utils;

public abstract class WView extends WVerticalList {
   public double maxHeight = Double.MAX_VALUE;
   public boolean scrollOnlyWhenMouseOver = true;
   public boolean hasScrollBar = true;
   protected boolean canScroll;
   private double actualHeight;
   private double scroll;
   private double targetScroll;
   private boolean moveAfterPositionWidgets;
   protected boolean handleMouseOver;
   protected boolean handlePressed;

   @Override
   public void init() {
      this.maxHeight = Utils.getWindowHeight() - this.theme.scale(128.0);
   }

   @Override
   protected void onCalculateSize() {
      boolean couldScroll = this.canScroll;
      this.canScroll = false;
      this.widthRemove = 0.0;
      super.onCalculateSize();
      if (this.height > this.maxHeight) {
         this.actualHeight = this.height;
         this.height = this.maxHeight;
         this.canScroll = true;
         if (this.hasScrollBar) {
            this.widthRemove = this.handleWidth() * 2.0;
            this.width = this.width + this.widthRemove;
         }

         if (couldScroll) {
            this.moveAfterPositionWidgets = true;
         }
      } else {
         this.actualHeight = this.height;
         this.scroll = 0.0;
         this.targetScroll = 0.0;
      }
   }

   @Override
   protected void onCalculateWidgetPositions() {
      super.onCalculateWidgetPositions();
      if (this.moveAfterPositionWidgets) {
         this.scroll = MathHelper.clamp(this.scroll, 0.0, this.actualHeight - this.height);
         this.targetScroll = this.scroll;
         this.moveCells(0.0, -this.scroll);
         this.moveAfterPositionWidgets = false;
      }
   }

   @Override
   public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
      if (this.handleMouseOver && button == 0 && !used) {
         this.handlePressed = true;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean onMouseReleased(double mouseX, double mouseY, int button) {
      if (this.handlePressed) {
         this.handlePressed = false;
      }

      return false;
   }

   @Override
   public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
      this.handleMouseOver = false;
      if (this.canScroll && this.hasScrollBar) {
         double x = this.handleX();
         double y = this.handleY();
         if (mouseX >= x && mouseX <= x + this.handleWidth() && mouseY >= y && mouseY <= y + this.handleHeight()) {
            this.handleMouseOver = true;
         }
      }

      if (this.handlePressed) {
         double preScroll = this.scroll;
         double mouseDelta = mouseY - lastMouseY;
         this.scroll = this.scroll + Math.round(mouseDelta * ((this.actualHeight - this.handleHeight() / 2.0) / this.height));
         this.scroll = MathHelper.clamp(this.scroll, 0.0, this.actualHeight - this.height);
         this.targetScroll = this.scroll;
         double delta = this.scroll - preScroll;
         if (delta != 0.0) {
            this.moveCells(0.0, -delta);
         }
      }
   }

   @Override
   public boolean onMouseScrolled(double amount) {
      if (this.scrollOnlyWhenMouseOver && !this.mouseOver) {
         return false;
      } else {
         this.targetScroll = this.targetScroll - Math.round(this.theme.scale(amount * 40.0));
         this.targetScroll = MathHelper.clamp(this.targetScroll, 0.0, this.actualHeight - this.height);
         return true;
      }
   }

   @Override
   public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      this.updateScroll(delta);
      if (this.canScroll) {
         renderer.scissorStart(this.x, this.y, this.width, this.height);
      }

      boolean render = super.render(renderer, mouseX, mouseY, delta);
      if (this.canScroll) {
         renderer.scissorEnd();
      }

      return render;
   }

   private void updateScroll(double delta) {
      double preScroll = this.scroll;
      double max = this.actualHeight - this.height;
      if (Math.abs(this.targetScroll - this.scroll) < 1.0) {
         this.scroll = this.targetScroll;
      } else if (this.targetScroll > this.scroll) {
         this.scroll = this.scroll + Math.round(this.theme.scale(delta * 300.0 + delta * 100.0 * (Math.abs(this.targetScroll - this.scroll) / 10.0)));
         if (this.scroll > this.targetScroll) {
            this.scroll = this.targetScroll;
         }
      } else if (this.targetScroll < this.scroll) {
         this.scroll = this.scroll - Math.round(this.theme.scale(delta * 300.0 + delta * 100.0 * (Math.abs(this.targetScroll - this.scroll) / 10.0)));
         if (this.scroll < this.targetScroll) {
            this.scroll = this.targetScroll;
         }
      }

      this.scroll = MathHelper.clamp(this.scroll, 0.0, max);
      double change = this.scroll - preScroll;
      if (change != 0.0) {
         this.moveCells(0.0, -change);
      }
   }

   @Override
   protected boolean propagateEvents(WWidget widget) {
      return widget.y >= this.y && widget.y <= this.y + this.height
         || widget.y + widget.height >= this.y && widget.y + widget.height <= this.y + this.height
         || this.y >= widget.y && this.y <= widget.y + widget.height
         || this.y + this.height >= widget.y && this.y + this.height <= widget.y + widget.height;
   }

   protected double handleWidth() {
      return this.theme.scale(6.0);
   }

   protected double handleHeight() {
      return this.height / this.actualHeight * this.height;
   }

   protected double handleX() {
      return this.x + this.width - this.handleWidth();
   }

   protected double handleY() {
      return this.y + (this.height - this.handleHeight()) * (this.scroll / (this.actualHeight - this.height));
   }
}
