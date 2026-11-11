package revivedevelopment.reviveclient.gui.screens.settings;

import net.minecraft.client.resource.language.I18n;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.settings.PotionSetting;
import revivedevelopment.reviveclient.utils.misc.MyPotion;

public class PotionSettingScreen extends WindowScreen {
   private final PotionSetting setting;

   public PotionSettingScreen(GuiTheme theme, PotionSetting setting) {
      super(theme, "Select Potion");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      WTable table = this.add(this.theme.table()).expandX().widget();

      for (MyPotion potion : MyPotion.values()) {
         table.add(this.theme.itemWithLabel(potion.potion, I18n.translate(potion.potion.getItem().getTranslationKey(), new Object[0])));
         WButton select = table.add(this.theme.button("Select")).widget();
         select.action = () -> {
            this.setting.set(potion);
            this.close();
         };
         table.row();
      }
   }
}
