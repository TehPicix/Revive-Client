package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.List;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.Setting;

public class ScreenHandlerSettingScreen extends CollectionListSettingScreen<ScreenHandlerType<?>> {
   public ScreenHandlerSettingScreen(GuiTheme theme, Setting<List<ScreenHandlerType<?>>> setting) {
      super(theme, "Select Screen Handlers", setting, setting.get(), Registries.SCREEN_HANDLER);
   }

   protected WWidget getValueWidget(ScreenHandlerType<?> value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(ScreenHandlerType<?> type) {
      return Registries.SCREEN_HANDLER.getId(type).toString();
   }
}
