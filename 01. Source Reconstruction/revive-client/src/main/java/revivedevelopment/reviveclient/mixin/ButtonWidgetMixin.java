package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.LockButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.gui.newgui.FontManager;

@Mixin({PressableWidget.class})
public class ButtonWidgetMixin {
   @Inject(
      method = {"method_48579"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderWidget(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (!((Object)this instanceof TexturedButtonWidget) && !((Object)this instanceof TextIconButtonWidget) && !((Object)this instanceof LockButtonWidget)) {
         Screen screen = MinecraftClient.getInstance().currentScreen;
         if (screen == null || !screen.getClass().getName().startsWith("xaero")) {
            if (!this.getClass().getName().startsWith("xaero")) {
               ClickableWidget self = (ClickableWidget)(Object)this;
               FontManager fm = FontManager.get();
               int rgb = fm.getSecondaryColor() & 16777215;
               int r = rgb >> 16 & 0xFF;
               int g = rgb >> 8 & 0xFF;
               int b = rgb & 0xFF;
               int x = self.getX();
               int y = self.getY();
               int w = self.getWidth();
               int h = self.getHeight();
               int bgAlpha = self.active ? 200 : 100;
               int baseBg = bgAlpha << 24 | (int)(r * 0.55F) << 16 | (int)(g * 0.55F) << 8 | (int)(b * 0.55F);
               int hoverBg = -603979776 | Math.min(255, (int)(r * 0.85F)) << 16 | Math.min(255, (int)(g * 0.85F)) << 8 | Math.min(255, (int)(b * 0.85F));
               int bgColor = self.active && self.isHovered() ? hoverBg : baseBg;
               int borderColor = self.active ? 0xFF000000 | rgb : 1610612736 | rgb;
               context.fill(x, y, x + w, y + h, bgColor);
               context.fill(x, y, x + w, y + 1, borderColor);
               context.fill(x, y + h - 1, x + w, y + h, borderColor);
               context.fill(x, y, x + 1, y + h, borderColor);
               context.fill(x + w - 1, y, x + w, y + h, borderColor);
               int textColor = self.active ? fm.getTextColor() : -6250336;
               context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, self.getMessage(), x + w / 2, y + (h - 8) / 2, textColor);
               ci.cancel();
            }
         }
      }
   }
}
