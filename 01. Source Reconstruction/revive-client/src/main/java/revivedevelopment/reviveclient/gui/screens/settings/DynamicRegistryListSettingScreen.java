package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.Pair;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WPressable;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.Utils;

public abstract class DynamicRegistryListSettingScreen<E> extends WindowScreen {
   protected final Setting<?> setting;
   protected final Collection<RegistryKey<E>> collection;
   private final RegistryKey<Registry<E>> registryKey;
   private final Optional<Registry<E>> registry;
   private WTextBox filter;
   private String filterText = "";
   private WTable table;

   public DynamicRegistryListSettingScreen(
      GuiTheme theme, String title, Setting<?> setting, Collection<RegistryKey<E>> collection, RegistryKey<Registry<E>> registryKey
   ) {
      super(theme, title);
      this.registryKey = registryKey;
      this.registry = Optional.ofNullable(MinecraftClient.getInstance().getNetworkHandler())
         .flatMap(networkHandler -> networkHandler.getRegistryManager().getOptional(registryKey));
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
         this.generateWidgets();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.generateWidgets();
   }

   private void generateWidgets() {
      WTable left = this.abc(
         pairs -> this.registry
            .ifPresent(registry -> registry.streamEntries().<Optional>map(Reference::getKey).filter(Optional::isPresent).map(Optional::get).forEach(t -> {
               if (!this.skipValue((RegistryKey<E>)t) && !this.collection.contains(t)) {
                  int words = Utils.searchInWords(this.getValueName((RegistryKey<E>)t), this.filterText);
                  int diff = Utils.searchLevenshteinDefault(this.getValueName((RegistryKey<E>)t), this.filterText, false);
                  if (words > 0 || diff <= this.getValueName((RegistryKey<E>)t).length() / 2) {
                     pairs.add(new Pair(t, -diff));
                  }
               }
            })),
         true,
         t -> {
            this.addValue(t);
            RegistryKey<E> v = this.getAdditionalValue(t);
            if (v != null) {
               this.addValue(v);
            }
         }
      );
      if (!left.cells.isEmpty()) {
         left.add(this.theme.horizontalSeparator()).expandX();
         left.row();
      }

      WHorizontalList manualEntry = left.add(this.theme.horizontalList()).expandX().widget();
      WTextBox textBox = manualEntry.add(this.theme.textBox("minecraft:")).expandX().minWidth(120.0).widget();
      manualEntry.add(this.theme.plus()).expandCellX().right().widget().action = () -> {
         String entry = textBox.get().trim();

         try {
            Identifier id = entry.contains(":") ? Identifier.of(entry) : Identifier.ofVanilla(entry);
            this.addValue(RegistryKey.of(this.registryKey, id));
         } catch (InvalidIdentifierException var4) {
         }
      };
      this.table.add(this.theme.verticalSeparator()).expandWidgetY();
      this.abc(pairs -> {
         for (RegistryKey<E> value : this.collection) {
            if (!this.skipValue(value)) {
               int words = Utils.searchInWords(this.getValueName(value), this.filterText);
               int diff = Utils.searchLevenshteinDefault(this.getValueName(value), this.filterText, false);
               if (words > 0 || diff <= this.getValueName(value).length() / 2) {
                  pairs.add(new Pair(value, -diff));
               }
            }
         }
      }, false, t -> {
         this.removeValue(t);
         RegistryKey<E> v = this.getAdditionalValue(t);
         if (v != null) {
            this.removeValue(v);
         }
      });
   }

   private void addValue(RegistryKey<E> value) {
      if (!this.collection.contains(value)) {
         this.collection.add(value);
         this.setting.onChanged();
         this.table.clear();
         this.generateWidgets();
      }
   }

   private void removeValue(RegistryKey<E> value) {
      if (this.collection.remove(value)) {
         this.setting.onChanged();
         this.table.clear();
         this.generateWidgets();
      }
   }

   private WTable abc(Consumer<List<Pair<RegistryKey<E>, Integer>>> addValues, boolean isLeft, Consumer<RegistryKey<E>> buttonAction) {
      Cell<WTable> cell = this.table.add(this.theme.table()).top();
      WTable table = cell.widget();
      Consumer<RegistryKey<E>> forEach = t -> {
         if (this.includeValue(t)) {
            table.add(this.getValueWidget(t));
            WPressable button = table.add((WPressable)(isLeft ? this.theme.plus() : this.theme.minus())).expandCellX().right().widget();
            button.action = () -> buttonAction.accept(t);
            table.row();
         }
      };
      List<Pair<RegistryKey<E>, Integer>> values = new ArrayList<>();
      addValues.accept(values);
      if (!this.filterText.isEmpty()) {
         values.sort(Comparator.comparingInt(value -> -(Integer)value.getRight()));
      }

      for (Pair<RegistryKey<E>, Integer> pair : values) {
         forEach.accept((RegistryKey<E>)pair.getLeft());
      }

      if (!table.cells.isEmpty()) {
         cell.expandX();
      }

      return table;
   }

   protected boolean includeValue(RegistryKey<E> value) {
      return true;
   }

   protected abstract WWidget getValueWidget(RegistryKey<E> var1);

   protected abstract String getValueName(RegistryKey<E> var1);

   protected boolean skipValue(RegistryKey<E> value) {
      return false;
   }

   protected RegistryKey<E> getAdditionalValue(RegistryKey<E> value) {
      return null;
   }
}
