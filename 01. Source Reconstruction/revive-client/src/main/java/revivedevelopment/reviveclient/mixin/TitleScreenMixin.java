package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.gui.titlemenu.CustomTitleScreen;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.utils.player.TitleScreenCredits;

@Mixin({TitleScreen.class})
public abstract class TitleScreenMixin extends Screen {
   public TitleScreenMixin(Text title) {
      super(title);
   }

   @Inject(
      method = {"method_25426"},
      at = {@At("RETURN")}
   )
   private void onInit(CallbackInfo ci) {
      MinecraftClient.getInstance().setScreen(new CustomTitleScreen());
   }

   @Inject(
      method = {"method_25394"},
      at = {@At("TAIL")}
   )
   private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (Config.get().titleScreenCredits.get()) {
         TitleScreenCredits.render(context);
      }
   }

   @Inject(
      method = {"method_25402"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> info) {
      if (Config.get().titleScreenCredits.get() && click.button() == 0 && TitleScreenCredits.onClicked(click.x(), click.y())) {
         info.setReturnValue(true);
      }
   }
}
