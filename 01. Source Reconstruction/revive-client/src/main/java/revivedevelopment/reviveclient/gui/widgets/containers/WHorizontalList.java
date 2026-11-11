package revivedevelopment.reviveclient.gui.widgets.containers;

import revivedevelopment.reviveclient.gui.utils.Cell;

public class WHorizontalList extends WContainer {
   public double spacing = 3.0;
   protected double calculatedWidth;
   protected int fillXCount;

   protected double spacing() {
      return this.theme.scale(this.spacing);
   }

   @Override
   protected void onCalculateSize() {
      this.width = 0.0;
      this.height = 0.0;
      this.fillXCount = 0;

      for (int i = 0; i < this.cells.size(); i++) {
         Cell<?> cell = this.cells.get(i);
         if (i > 0) {
            this.width = this.width + this.spacing();
         }

         this.width = this.width + (cell.padLeft() + cell.widget().width + cell.padRight());
         this.height = Math.max(this.height, cell.padTop() + cell.widget().height + cell.padBottom());
         if (cell.expandCellX) {
            this.fillXCount++;
         }
      }

      this.calculatedWidth = this.width;
   }

   @Override
   protected void onCalculateWidgetPositions() {
      double x = this.x;
      double fillXWidth = (this.width - this.calculatedWidth) / this.fillXCount;

      for (int i = 0; i < this.cells.size(); i++) {
         Cell<?> cell = this.cells.get(i);
         if (i > 0) {
            x += this.spacing();
         }

         x += cell.padLeft();
         cell.x = x;
         cell.y = this.y + cell.padTop();
         cell.width = cell.widget().width;
         cell.height = this.height - cell.padTop() - cell.padTop();
         if (cell.expandCellX) {
            cell.width += fillXWidth;
         }

         cell.alignWidget();
         x += cell.width + cell.padRight();
      }
   }
}
