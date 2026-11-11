package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.packet.Packet;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.PacketListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.network.PacketUtils;

public class PacketBoolSettingScreen extends CollectionListSettingScreen<Class<? extends Packet<?>>> {
   public PacketBoolSettingScreen(GuiTheme theme, Setting<Set<Class<? extends Packet<?>>>> setting) {
      super(theme, "Select Packets", setting, setting.get(), PacketUtils.PACKETS);
   }

   protected boolean includeValue(Class<? extends Packet<?>> value) {
      Predicate<Class<? extends Packet<?>>> filter = ((PacketListSetting)this.setting).filter;
      return filter == null ? true : filter.test(value);
   }

   protected WWidget getValueWidget(Class<? extends Packet<?>> value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(Class<? extends Packet<?>> value) {
      return PacketUtils.getName(value);
   }
}
