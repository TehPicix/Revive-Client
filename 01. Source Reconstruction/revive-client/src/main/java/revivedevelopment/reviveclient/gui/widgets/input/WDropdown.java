package revivedevelopment.reviveclient.gui.widgets.input;

import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WRoot;
import revivedevelopment.reviveclient.gui.widgets.containers.WVerticalList;
import revivedevelopment.reviveclient.gui.widgets.pressable.WPressable;

public abstract class WDropdown<T> extends WPressable {
   public Runnable action;
   protected T[] values;
   protected T value;
   protected double maxValueWidth;
   protected WDropdown.WDropdownRoot root;
   protected boolean expanded;
   protected double animProgress;

   public WDropdown(T[] values, T value) {
      this.values = values;
      this.set(value);
   }

   @Override
   public void init() {
      this.root = this.createRootWidget();
      this.root.theme = this.theme;
      this.root.spacing = 0.0;

      for (int i = 0; i < this.values.length; i++) {
         WDropdown<T>.WDropdownValue widget = this.createValueWidget();
         widget.theme = this.theme;
         widget.value = this.values[i];
         Cell<?> cell = this.root.add(widget).padHorizontal(2.0).expandWidgetX();
         if (i >= this.values.length - 1) {
            cell.padBottom(2.0);
         }
      }
   }

   protected abstract WDropdown.WDropdownRoot createRootWidget();

   protected abstract WDropdown<T>.WDropdownValue createValueWidget();

   @Override
   protected void onCalculateSize() {
      double pad = this.pad();
      this.maxValueWidth = 0.0;

      for (T value : this.values) {
         double valueWidth = this.theme.textWidth(value.toString());
         this.maxValueWidth = Math.max(this.maxValueWidth, valueWidth);
      }

      this.root.calculateSize();
      this.width = pad + this.maxValueWidth + pad + this.theme.textHeight() + pad;
      this.height = pad + this.theme.textHeight() + pad;
      this.root.width = this.width;
   }

   @Override
   protected void onCalculateWidgetPositions() {
      super.onCalculateWidgetPositions();
      this.root.x = this.x;
      this.root.y = this.y + this.height;
      this.root.calculateWidgetPositions();
   }

   @Override
   protected void onPressed(int button) {
      this.expanded = !this.expanded;
   }

   public T get() {
      return this.value;
   }

   public void set(T value) {
      this.value = value;
   }

   @Override
   public void move(double deltaX, double deltaY) {
      super.move(deltaX, deltaY);
      this.root.move(deltaX, deltaY);
   }

   @Override
   public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      boolean render = super.render(renderer, mouseX, mouseY, delta);
      this.animProgress = this.animProgress + (this.expanded ? 1 : -1) * delta * 14.0;
      this.animProgress = MathHelper.clamp(this.animProgress, 0.0, 1.0);
      if (!render && this.animProgress > 0.0) {
         renderer.absolutePost(() -> {
            renderer.scissorStart(this.x, this.y + this.height, this.width, this.root.height * this.animProgress);
            this.root.render(renderer, mouseX, mouseY, delta);
            renderer.scissorEnd();
         });
      }

      if (this.expanded && this.root.mouseOver) {
         this.theme.disableHoverColor = true;
      }

      return render;
   }

   @Override
   public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
      if (!this.mouseOver && !this.root.mouseOver) {
         this.expanded = false;
      }

      if (super.onMouseClicked(mouseX, mouseY, button, used)) {
         used = true;
      }

      if (this.expanded && this.root.mouseClicked(mouseX, mouseY, button, used)) {
         used = true;
      }

      return used;
   }

   @Override
   public boolean onMouseReleased(double mouseX, double mouseY, int button) {
      return super.onMouseReleased(mouseX, mouseY, button) ? true : this.expanded && this.root.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
      super.onMouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
      if (this.expanded) {
         this.root.mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
      }
   }

   @Override
   public boolean onMouseScrolled(double amount) {
      if (super.onMouseScrolled(amount)) {
         return true;
      } else {
         return this.expanded ? this.root.mouseScrolled(amount) : false;
      }
   }

   @Override
   public boolean onKeyPressed(int key, int mods) {
      return super.onKeyPressed(key, mods) ? true : this.expanded && this.root.keyPressed(key, mods);
   }

   @Override
   public boolean onKeyRepeated(int key, int mods) {
      return super.onKeyRepeated(key, mods) ? true : this.expanded && this.root.keyRepeated(key, mods);
   }

   @Override
   public boolean onCharTyped(char c) {
      return super.onCharTyped(c) ? true : this.expanded && this.root.charTyped(c);
   }

   protected abstract static class WDropdownRoot extends WVerticalList implements WRoot {
      @Override
      public void invalidate() {
      }
   }

   protected abstract class WDropdownValue extends WPressable {
      protected T value;

      @Override
      protected void onPressed(int button) {
         boolean isNew = !WDropdown.this.value.equals(this.value);
         WDropdown.this.value = this.value;
         WDropdown.this.expanded = false;
         if (isNew && WDropdown.this.action != null) {
            WDropdown.this.action.run();
         }
      }
   }
}
