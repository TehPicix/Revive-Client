package revivedevelopment.reviveclient.gui.screens.settings;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.Strings;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.widgets.WItemWithLabel;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.settings.ItemSetting;
import revivedevelopment.reviveclient.utils.misc.Names;

public class ItemSettingScreen extends WindowScreen {
   private final ItemSetting setting;
   private WTable table;
   private WTextBox filter;
   private String filterText = "";

   public ItemSettingScreen(GuiTheme theme, ItemSetting setting) {
      super(theme, "Select item");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      this.filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      this.filter.setFocused(true);
      this.filter.action = () -> {
         this.filterText = this.filter.get().trim();
         this.table.clear();
         this.initTable();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initTable();
   }

   public void initTable() {
      for (Item item : Registries.ITEM) {
         if ((this.setting.filter == null || this.setting.filter.test(item)) && item != Items.AIR) {
            WItemWithLabel itemLabel = this.theme.itemWithLabel(item.getDefaultStack(), Names.get(item));
            if (this.filterText.isEmpty() || Strings.CI.contains(itemLabel.getLabelText(), this.filterText)) {
               this.table.add(itemLabel);
               WButton select = this.table.add(this.theme.button("Select")).expandCellX().right().widget();
               select.action = () -> {
                  this.setting.set(item);
                  this.close();
               };
               this.table.row();
            }
         }
      }
   }
}
