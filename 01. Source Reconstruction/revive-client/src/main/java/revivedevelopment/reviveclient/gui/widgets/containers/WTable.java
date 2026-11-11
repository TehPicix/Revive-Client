package revivedevelopment.reviveclient.gui.widgets.containers;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WWidget;

public class WTable extends WContainer {
   public double horizontalSpacing = 3.0;
   public double verticalSpacing = 3.0;
   private final List<List<Cell<?>>> rows = new ArrayList<>();
   private int rowI;
   private final DoubleList rowHeights = new DoubleArrayList();
   private final DoubleList columnWidths = new DoubleArrayList();
   private final DoubleList rowWidths = new DoubleArrayList();
   private final IntList rowExpandCellXCounts = new IntArrayList();

   @Override
   public <T extends WWidget> Cell<T> add(T widget) {
      Cell<T> cell = super.add(widget);
      if (this.rows.size() <= this.rowI) {
         List<Cell<?>> row = new ArrayList<>();
         row.add(cell);
         this.rows.add(row);
      } else {
         this.rows.get(this.rowI).add(cell);
      }

      return cell;
   }

   public void row() {
      this.rowI++;
   }

   public int rowI() {
      return this.rowI;
   }

   public void removeRow(int i) {
      for (Cell<?> cell : this.rows.remove(i)) {
         Iterator<Cell<?>> it = this.cells.iterator();

         while (it.hasNext()) {
            if (it.next() == cell) {
               it.remove();
               break;
            }
         }
      }

      this.rowI--;
   }

   public List<Cell<?>> getRow(int i) {
      return i >= 0 && i < this.rows.size() ? this.rows.get(i) : null;
   }

   @Override
   public void clear() {
      super.clear();
      this.rows.clear();
      this.rowI = 0;
   }

   protected double horizontalSpacing() {
      return this.theme.scale(this.horizontalSpacing);
   }

   protected double verticalSpacing() {
      return this.theme.scale(this.verticalSpacing);
   }

   @Override
   protected void onCalculateSize() {
      this.calculateInfo();
      this.rowWidths.clear();
      this.width = 0.0;
      this.height = 0.0;

      for (int rowI = 0; rowI < this.rows.size(); rowI++) {
         List<Cell<?>> row = this.rows.get(rowI);
         double rowWidth = 0.0;

         for (int cellI = 0; cellI < row.size(); cellI++) {
            if (cellI > 0) {
               rowWidth += this.horizontalSpacing();
            }

            rowWidth += this.columnWidths.getDouble(cellI);
         }

         this.rowWidths.add(rowWidth);
         this.width = Math.max(this.width, rowWidth);
         if (rowI > 0) {
            this.height = this.height + this.verticalSpacing();
         }

         this.height = this.height + this.rowHeights.getDouble(rowI);
      }
   }

   @Override
   protected void onCalculateWidgetPositions() {
      double y = this.y;

      for (int rowI = 0; rowI < this.rows.size(); rowI++) {
         List<Cell<?>> row = this.rows.get(rowI);
         if (rowI > 0) {
            y += this.verticalSpacing();
         }

         double x = this.x;
         double rowHeight = this.rowHeights.getDouble(rowI);
         double expandXAdd = this.rowExpandCellXCounts.getInt(rowI) > 0
            ? (this.width - this.rowWidths.getDouble(rowI)) / this.rowExpandCellXCounts.getInt(rowI)
            : 0.0;

         for (int cellI = 0; cellI < row.size(); cellI++) {
            Cell<?> cell = row.get(cellI);
            if (cellI > 0) {
               x += this.horizontalSpacing();
            }

            double columnWidth = this.columnWidths.getDouble(cellI);
            cell.x = x;
            cell.y = y;
            cell.width = columnWidth + (cell.expandCellX ? expandXAdd : 0.0);
            cell.height = rowHeight;
            cell.alignWidget();
            x += columnWidth + (cell.expandCellX ? expandXAdd : 0.0);
         }

         y += rowHeight;
      }
   }

   private void calculateInfo() {
      this.rowHeights.clear();
      this.columnWidths.clear();
      this.rowExpandCellXCounts.clear();

      for (List<Cell<?>> row : this.rows) {
         double rowHeight = 0.0;
         int rowExpandXCount = 0;

         for (int i = 0; i < row.size(); i++) {
            Cell<?> cell = row.get(i);
            rowHeight = Math.max(rowHeight, cell.padTop() + cell.widget().height + cell.padBottom());
            double cellWidth = cell.padLeft() + cell.widget().width + cell.padRight();
            if (this.columnWidths.size() <= i) {
               this.columnWidths.add(cellWidth);
            } else {
               this.columnWidths.set(i, Math.max(this.columnWidths.getDouble(i), cellWidth));
            }

            if (cell.expandCellX) {
               rowExpandXCount++;
            }
         }

         this.rowHeights.add(rowHeight);
         this.rowExpandCellXCounts.add(rowExpandXCount);
      }
   }
}
