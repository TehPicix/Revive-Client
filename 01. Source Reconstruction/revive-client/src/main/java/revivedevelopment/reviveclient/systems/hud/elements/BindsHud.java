package revivedevelopment.reviveclient.systems.hud.elements;

import java.util.ArrayList;
import java.util.List;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;

public class BindsHud extends HudElement {
   public static final HudElementInfo<BindsHud> INFO = new HudElementInfo<>(Hud.GROUP, "binds", "Displays modules with keybinds.", BindsHud::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> limit = this.sgGeneral
      .add(new IntSetting.Builder().name("limit").description("Maximum number of binds to display.").defaultValue(10).min(1).sliderRange(1, 20).build());
   private final Setting<Boolean> activeOnly = this.sgGeneral
      .add(new BoolSetting.Builder().name("active-only").description("Only shows binds for active modules.").defaultValue(false).build());
   private final Setting<Boolean> textShadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("text-shadow").description("Renders shadow behind text.").defaultValue(true).build());

   public BindsHud() {
      super(INFO);
   }

   @Override
   public void render(HudRenderer renderer) {
      List<Module> modules = new ArrayList<>();

      for (Module module : Modules.get().getAll()) {
         if (module.keybind.isSet() && (!this.activeOnly.get() || module.isActive())) {
            modules.add(module);
         }
      }

      if (modules.isEmpty()) {
         String text = "No binds";
         renderer.text(text, this.x, this.y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
         this.setSize(renderer.textWidth(text, this.textShadow.get()), renderer.textHeight(this.textShadow.get()));
      } else {
         double y = this.y;
         double width = 0.0;
         int count = 0;

         for (Module modulex : modules) {
            if (count >= this.limit.get()) {
               break;
            }

            String titleText = modulex.title + ": ";
            String bindText = modulex.keybind.toString();
            double lineWidth = renderer.textWidth(titleText + bindText, this.textShadow.get());
            double x1 = renderer.text(titleText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
            renderer.text(bindText, x1, y, HudStyle.TEXT_SECONDARY, this.textShadow.get());
            width = Math.max(width, lineWidth);
            y += renderer.textHeight(this.textShadow.get()) + 2.0;
            count++;
         }

         this.setSize(width, y - this.y - 2.0);
      }
   }
}
