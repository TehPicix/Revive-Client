package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.Pair;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WPressable;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.Utils;

public abstract class CollectionListSettingScreen<T> extends WindowScreen {
   protected final Setting<?> setting;
   protected final Collection<T> collection;
   private final Iterable<T> registry;
   private WTextBox filter;
   private String filterText = "";
   private WTable table;

   public CollectionListSettingScreen(GuiTheme theme, String title, Setting<?> setting, Collection<T> collection, Iterable<T> registry) {
      super(theme, title);
      this.registry = registry;
      this.setting = setting;
      this.collection = collection;
   }

   @Override
   public void initWidgets() {
      this.filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      this.filter.setFocused(true);
      this.filter.action = () -> {
         this.filterText = this.filter.get().trim();
         this.table.clear();
         this.initWidgets(this.registry);
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initWidgets(this.registry);
   }

   private void initWidgets(Iterable<T> registry) {
      WTable left = this.abc(pairs -> registry.forEach(t -> {
         if (!this.skipValue((T)t) && !this.collection.contains(t)) {
            int words = Utils.searchInWords(this.getValueName((T)t), this.filterText);
            int diff = Utils.searchLevenshteinDefault(this.getValueName((T)t), this.filterText, false);
            if (words > 0 || diff <= this.getValueName((T)t).length() / 2) {
               pairs.add(new Pair(t, -diff));
            }
         }
      }), true, t -> {
         this.addValue(registry, t);
         T v = this.getAdditionalValue(t);
         if (v != null) {
            this.addValue(registry, v);
         }
      });
      if (!left.cells.isEmpty()) {
         this.table.add(this.theme.verticalSeparator()).expandWidgetY();
      }

      this.abc(pairs -> {
         for (T value : this.collection) {
            if (!this.skipValue(value)) {
               int words = Utils.searchInWords(this.getValueName(value), this.filterText);
               int diff = Utils.searchLevenshteinDefault(this.getValueName(value), this.filterText, false);
               if (words > 0 || diff <= this.getValueName(value).length() / 2) {
                  pairs.add(new Pair(value, -diff));
               }
            }
         }
      }, false, t -> {
         this.removeValue(registry, t);
         T v = this.getAdditionalValue(t);
         if (v != null) {
            this.removeValue(registry, v);
         }
      });
   }

   private void addValue(Iterable<T> registry, T value) {
      if (!this.collection.contains(value)) {
         this.collection.add(value);
         this.setting.onChanged();
         this.table.clear();
         this.initWidgets(registry);
      }
   }

   private void removeValue(Iterable<T> registry, T value) {
      if (this.collection.remove(value)) {
         this.setting.onChanged();
         this.table.clear();
         this.initWidgets(registry);
      }
   }

   private WTable abc(Consumer<List<Pair<T, Integer>>> addValues, boolean isLeft, Consumer<T> buttonAction) {
      Cell<WTable> cell = this.table.add(this.theme.table()).top();
      WTable table = cell.widget();
      Consumer<T> forEach = t -> {
         if (this.includeValue(t)) {
            table.add(this.getValueWidget(t));
            WPressable button = table.add((WPressable)(isLeft ? this.theme.plus() : this.theme.minus())).expandCellX().right().widget();
            button.action = () -> buttonAction.accept((T)t);
            table.row();
         }
      };
      List<Pair<T, Integer>> values = new ArrayList<>();
      addValues.accept(values);
      if (!this.filterText.isEmpty()) {
         values.sort(Comparator.comparingInt(value -> -(Integer)value.getRight()));
      }

      for (Pair<T, Integer> pair : values) {
         forEach.accept((T)pair.getLeft());
      }

      if (!table.cells.isEmpty()) {
         cell.expandX();
      }

      return table;
   }

   protected boolean includeValue(T value) {
      return true;
   }

   protected abstract WWidget getValueWidget(T var1);

   protected abstract String getValueName(T var1);

   protected boolean skipValue(T value) {
      return false;
   }

   protected T getAdditionalValue(T value) {
      return null;
   }
}
