package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.List;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.Setting;

public class SoundEventListSettingScreen extends CollectionListSettingScreen<SoundEvent> {
   public SoundEventListSettingScreen(GuiTheme theme, Setting<List<SoundEvent>> setting) {
      super(theme, "Select Sounds", setting, setting.get(), Registries.SOUND_EVENT);
   }

   protected WWidget getValueWidget(SoundEvent value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(SoundEvent value) {
      return value.id().getPath();
   }
}
