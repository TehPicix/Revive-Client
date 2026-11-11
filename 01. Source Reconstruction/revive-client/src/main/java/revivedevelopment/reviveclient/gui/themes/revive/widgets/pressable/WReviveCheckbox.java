package revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable;

import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveGuiTheme;
import revivedevelopment.reviveclient.gui.themes.revive.ReviveWidget;
import revivedevelopment.reviveclient.gui.widgets.pressable.WCheckbox;

public class WReviveCheckbox extends WCheckbox implements ReviveWidget {
   private double animProgress;

   public WReviveCheckbox(boolean checked) {
      super(checked);
      this.animProgress = checked ? 1.0 : 0.0;
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      ReviveGuiTheme theme = this.theme();
      this.animProgress = this.animProgress + (this.checked ? 1 : -1) * delta * 14.0;
      this.animProgress = MathHelper.clamp(this.animProgress, 0.0, 1.0);
      this.renderBackground(renderer, this, this.pressed, this.mouseOver);
      if (this.animProgress > 0.0) {
         double cs = (this.width - theme.scale(2.0)) / 1.75 * this.animProgress;
         renderer.quad(this.x + (this.width - cs) / 2.0, this.y + (this.height - cs) / 2.0, cs, cs, theme.checkboxColor.get());
      }
   }
}
