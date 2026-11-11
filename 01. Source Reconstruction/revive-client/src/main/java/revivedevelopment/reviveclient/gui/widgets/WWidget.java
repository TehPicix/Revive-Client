package revivedevelopment.reviveclient.gui.widgets;

import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.utils.BaseWidget;

public abstract class WWidget implements BaseWidget {
   public boolean visible = true;
   public GuiTheme theme;
   public double x;
   public double y;
   public double width;
   public double height;
   public double minWidth;
   public WWidget parent;
   public String tooltip;
   public boolean mouseOver;
   protected double mouseOverTimer;

   public void init() {
   }

   public void move(double deltaX, double deltaY) {
      this.x = Math.round(this.x + deltaX);
      this.y = Math.round(this.y + deltaY);
   }

   @Override
   public GuiTheme getTheme() {
      return this.theme;
   }

   public double pad() {
      return this.theme.pad();
   }

   public void calculateSize() {
      this.onCalculateSize();
      double minWidth = this.theme.scale(this.minWidth);
      if (this.width < minWidth) {
         this.width = minWidth;
      }

      this.width = Math.round(this.width);
      this.height = Math.round(this.height);
   }

   protected void onCalculateSize() {
   }

   public void calculateWidgetPositions() {
      this.x = Math.round(this.x);
      this.y = Math.round(this.y);
      this.onCalculateWidgetPositions();
   }

   protected void onCalculateWidgetPositions() {
   }

   public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (!this.visible) {
         return true;
      } else {
         if (this.isOver(mouseX, mouseY)) {
            this.mouseOverTimer += delta;
            if (this.mouseOverTimer >= 1.0 && this.tooltip != null) {
               renderer.tooltip(this.tooltip);
            }
         } else {
            this.mouseOverTimer = 0.0;
         }

         this.onRender(renderer, mouseX, mouseY, delta);
         return false;
      }
   }

   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean used) {
      return this.onMouseClicked(mouseX, mouseY, button, used);
   }

   public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
      return false;
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return this.onMouseReleased(mouseX, mouseY, button);
   }

   public boolean onMouseReleased(double mouseX, double mouseY, int button) {
      return false;
   }

   public void mouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
      this.mouseOver = this.isOver(mouseX, mouseY);
      this.onMouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
   }

   public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
   }

   public boolean mouseScrolled(double amount) {
      return this.onMouseScrolled(amount);
   }

   public boolean onMouseScrolled(double amount) {
      return false;
   }

   public boolean keyPressed(int key, int mods) {
      return this.onKeyPressed(key, mods);
   }

   public boolean onKeyPressed(int key, int mods) {
      return false;
   }

   public boolean keyRepeated(int key, int mods) {
      return this.onKeyRepeated(key, mods);
   }

   public boolean onKeyRepeated(int key, int mods) {
      return false;
   }

   public boolean charTyped(char c) {
      return this.onCharTyped(c);
   }

   public boolean onCharTyped(char c) {
      return false;
   }

   public void invalidate() {
      WWidget root = this.getRoot();
      if (root != null) {
         root.invalidate();
      }
   }

   protected WWidget getRoot() {
      return this.parent != null ? this.parent.getRoot() : (this instanceof WRoot ? this : null);
   }

   public boolean isOver(double x, double y) {
      return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.height;
   }
}
