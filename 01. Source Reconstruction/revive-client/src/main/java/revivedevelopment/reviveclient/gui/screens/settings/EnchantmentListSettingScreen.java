package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.Set;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.misc.Names;

public class EnchantmentListSettingScreen extends DynamicRegistryListSettingScreen<Enchantment> {
   public EnchantmentListSettingScreen(GuiTheme theme, Setting<Set<RegistryKey<Enchantment>>> setting) {
      super(theme, "Select Enchantments", setting, setting.get(), RegistryKeys.ENCHANTMENT);
   }

   @Override
   protected WWidget getValueWidget(RegistryKey<Enchantment> value) {
      return this.theme.label(this.getValueName(value));
   }

   @Override
   protected String getValueName(RegistryKey<Enchantment> value) {
      return Names.get(value);
   }
}
