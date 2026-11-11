package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.List;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;

public class ModuleListSettingScreen extends CollectionListSettingScreen<Module> {
   public ModuleListSettingScreen(GuiTheme theme, Setting<List<Module>> setting) {
      super(theme, "Select Modules", setting, setting.get(), Modules.get().getAll());
   }

   protected WWidget getValueWidget(Module value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(Module value) {
      return value.title;
   }
}
