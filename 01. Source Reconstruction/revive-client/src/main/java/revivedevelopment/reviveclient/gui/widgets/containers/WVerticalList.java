package revivedevelopment.reviveclient.gui.widgets.containers;

import revivedevelopment.reviveclient.gui.utils.Cell;

public class WVerticalList extends WContainer {
   public double spacing = 3.0;
   protected double widthRemove;

   protected double spacing() {
      return this.theme.scale(this.spacing);
   }

   @Override
   protected void onCalculateSize() {
      this.width = 0.0;
      this.height = 0.0;

      for (int i = 0; i < this.cells.size(); i++) {
         Cell<?> cell = this.cells.get(i);
         if (i > 0) {
            this.height = this.height + this.spacing();
         }

         this.width = Math.max(this.width, cell.padLeft() + cell.widget().width + cell.padRight());
         this.height = this.height + (cell.padTop() + cell.widget().height + cell.padBottom());
      }
   }

   @Override
   protected void onCalculateWidgetPositions() {
      double y = this.y;

      for (int i = 0; i < this.cells.size(); i++) {
         Cell<?> cell = this.cells.get(i);
         if (i > 0) {
            y += this.spacing();
         }

         y += cell.padTop();
         cell.x = this.x + cell.padLeft();
         cell.y = y;
         cell.width = this.width - this.widthRemove - cell.padLeft() - cell.padRight();
         cell.height = cell.widget().height;
         cell.alignWidget();
         y += cell.height + cell.padBottom();
      }
   }
}
