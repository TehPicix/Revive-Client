package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.utils.misc.Names;

public class ItemListSettingScreen extends CollectionListSettingScreen<Item> {
   public ItemListSettingScreen(GuiTheme theme, ItemListSetting setting) {
      super(theme, "Select Items", setting, setting.get(), Registries.ITEM);
   }

   protected boolean includeValue(Item value) {
      Predicate<Item> filter = ((ItemListSetting)this.setting).filter;
      return filter != null && !filter.test(value) ? false : value != Items.AIR;
   }

   protected WWidget getValueWidget(Item value) {
      return this.theme.itemWithLabel(value.getDefaultStack());
   }

   protected String getValueName(Item value) {
      return Names.get(value);
   }
}
