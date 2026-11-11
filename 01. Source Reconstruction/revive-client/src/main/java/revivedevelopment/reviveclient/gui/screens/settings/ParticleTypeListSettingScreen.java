package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.List;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.misc.Names;

public class ParticleTypeListSettingScreen extends CollectionListSettingScreen<ParticleType<?>> {
   public ParticleTypeListSettingScreen(GuiTheme theme, Setting<List<ParticleType<?>>> setting) {
      super(theme, "Select Particles", setting, setting.get(), Registries.PARTICLE_TYPE);
   }

   protected WWidget getValueWidget(ParticleType<?> value) {
      return this.theme.label(this.getValueName(value));
   }

   protected String getValueName(ParticleType<?> value) {
      return Names.get(value);
   }

   protected boolean skipValue(ParticleType<?> value) {
      return !(value instanceof ParticleEffect);
   }
}
