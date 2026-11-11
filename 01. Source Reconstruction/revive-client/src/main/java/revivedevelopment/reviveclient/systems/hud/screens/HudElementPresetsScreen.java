package revivedevelopment.reviveclient.systems.hud.screens;

import net.minecraft.client.gui.DrawContext;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WPlus;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.utils.Utils;

public class HudElementPresetsScreen extends WindowScreen {
   private final HudElementInfo<?> info;
   private final int x;
   private final int y;
   private final WTextBox searchBar;
   private HudElementInfo<?>.Preset firstPreset;

   public HudElementPresetsScreen(GuiTheme theme, HudElementInfo<?> info, int x, int y) {
      super(theme, "Select preset for " + info.title);
      this.info = info;
      this.x = x + 9;
      this.y = y;
      this.searchBar = theme.textBox("");
      this.searchBar.action = () -> {
         this.clear();
         this.initWidgets();
      };
      this.enterAction = () -> {
         Hud.get().add(this.firstPreset, x, y);
         this.close();
      };
   }

   @Override
   public void initWidgets() {
      this.firstPreset = null;
      this.add(this.searchBar).expandX();
      this.searchBar.setFocused(true);

      for (HudElementInfo<?>.Preset preset : this.info.presets) {
         if (Utils.searchTextDefault(preset.title, this.searchBar.get(), false)) {
            WHorizontalList l = this.add(this.theme.horizontalList()).expandX().widget();
            l.add(this.theme.label(preset.title));
            WPlus add = l.add(this.theme.plus()).expandCellX().right().widget();
            add.action = () -> {
               Hud.get().add(preset, this.x, this.y);
               this.close();
            };
            if (this.firstPreset == null) {
               this.firstPreset = preset;
            }
         }
      }
   }

   @Override
   protected void onRenderBefore(DrawContext drawContext, float delta) {
      HudEditorScreen.renderElements(drawContext);
   }
}
