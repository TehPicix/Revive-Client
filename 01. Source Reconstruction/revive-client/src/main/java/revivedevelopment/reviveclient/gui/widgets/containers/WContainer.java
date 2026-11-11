package revivedevelopment.reviveclient.gui.widgets.containers;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import net.minecraft.client.Mouse;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.utils.Utils;

public abstract class WContainer extends WWidget {
   public final List<Cell<?>> cells = new ArrayList<>();

   public <T extends WWidget> Cell<T> add(T widget) {
      widget.parent = this;
      widget.theme = this.theme;
      Cell<T> cell = new Cell<T>(widget).centerY();
      this.cells.add(cell);
      widget.init();
      this.invalidate();
      return cell;
   }

   public void clear() {
      if (!this.cells.isEmpty()) {
         this.cells.clear();
         this.invalidate();
      }
   }

   public void remove(Cell<?> cell) {
      if (this.cells.remove(cell)) {
         this.invalidate();
      }
   }

   @Override
   public void move(double deltaX, double deltaY) {
      super.move(deltaX, deltaY);

      for (Cell<?> cell : this.cells) {
         cell.move(deltaX, deltaY);
      }
   }

   public void moveCells(double deltaX, double deltaY) {
      for (Cell<?> cell : this.cells) {
         cell.move(deltaX, deltaY);
         Mouse mouse = ReviveClient.mc.mouse;
         cell.widget().mouseMoved(mouse.getX(), mouse.getY(), mouse.getX(), mouse.getY());
      }
   }

   @Override
   public void calculateSize() {
      for (Cell<?> cell : this.cells) {
         cell.widget().calculateSize();
      }

      super.calculateSize();
   }

   @Override
   protected void onCalculateSize() {
      this.width = 0.0;
      this.height = 0.0;

      for (Cell<?> cell : this.cells) {
         this.width = Math.max(this.width, cell.padLeft() + cell.widget().width + cell.padRight());
         this.height = Math.max(this.height, cell.padTop() + cell.widget().height + cell.padBottom());
      }
   }

   @Override
   public void calculateWidgetPositions() {
      super.calculateWidgetPositions();

      for (Cell<?> cell : this.cells) {
         cell.widget().calculateWidgetPositions();
      }
   }

   @Override
   protected void onCalculateWidgetPositions() {
      for (Cell<?> cell : this.cells) {
         cell.x = this.x + cell.padLeft();
         cell.y = this.y + cell.padTop();
         cell.width = this.width - cell.padLeft() - cell.padRight();
         cell.height = this.height - cell.padTop() - cell.padBottom();
         cell.alignWidget();
      }
   }

   @Override
   public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (super.render(renderer, mouseX, mouseY, delta)) {
         return true;
      } else {
         for (Cell<?> cell : this.cells) {
            double y = cell.widget().y;
            if (y > Utils.getWindowHeight()) {
               break;
            }

            if (y + cell.widget().height > 0.0) {
               this.renderWidget(cell.widget(), renderer, mouseX, mouseY, delta);
            }
         }

         return false;
      }
   }

   protected void renderWidget(WWidget widget, GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      widget.render(renderer, mouseX, mouseY, delta);
   }

   protected boolean propagateEvents(WWidget widget) {
      return true;
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean used) {
      try {
         for (Cell<?> cell : this.cells) {
            if (this.propagateEvents(cell.widget()) && cell.widget().mouseClicked(mouseX, mouseY, button, used)) {
               used = true;
            }
         }
      } catch (ConcurrentModificationException var9) {
      }

      return super.mouseClicked(mouseX, mouseY, button, used) || used;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      try {
         for (Cell<?> cell : this.cells) {
            if (this.propagateEvents(cell.widget()) && cell.widget().mouseReleased(mouseX, mouseY, button)) {
               return true;
            }
         }
      } catch (ConcurrentModificationException var8) {
      }

      return super.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public void mouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
      try {
         for (Cell<?> cell : this.cells) {
            if (this.propagateEvents(cell.widget())) {
               cell.widget().mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
            }
         }
      } catch (ConcurrentModificationException var11) {
      }

      super.mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
   }

   @Override
   public boolean mouseScrolled(double amount) {
      try {
         for (Cell<?> cell : this.cells) {
            if (this.propagateEvents(cell.widget()) && cell.widget().mouseScrolled(amount)) {
               return true;
            }
         }
      } catch (ConcurrentModificationException var5) {
      }

      return super.mouseScrolled(amount);
   }

   @Override
   public boolean keyPressed(int key, int modifiers) {
      try {
         for (Cell<?> cell : this.cells) {
            if (this.propagateEvents(cell.widget()) && cell.widget().keyPressed(key, modifiers)) {
               return true;
            }
         }
      } catch (ConcurrentModificationException var5) {
      }

      return this.onKeyPressed(key, modifiers);
   }

   @Override
   public boolean keyRepeated(int key, int modifiers) {
      try {
         for (Cell<?> cell : this.cells) {
            if (this.propagateEvents(cell.widget()) && cell.widget().keyRepeated(key, modifiers)) {
               return true;
            }
         }
      } catch (ConcurrentModificationException var5) {
      }

      return this.onKeyRepeated(key, modifiers);
   }

   @Override
   public boolean charTyped(char c) {
      try {
         for (Cell<?> cell : this.cells) {
            if (this.propagateEvents(cell.widget()) && cell.widget().charTyped(c)) {
               return true;
            }
         }
      } catch (ConcurrentModificationException var4) {
      }

      return super.charTyped(c);
   }
}
