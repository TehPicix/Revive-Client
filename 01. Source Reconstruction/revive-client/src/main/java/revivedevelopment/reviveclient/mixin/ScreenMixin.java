package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.movement.GUIMove;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.text.RunnableClickEvent;

@Mixin(
   value = {Screen.class},
   priority = 500
)
public abstract class ScreenMixin {
   @Inject(
      method = {"method_71847"},
      at = {@At(
         value = "INVOKE",
         target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V",
         remap = false
      )},
      cancellable = true
   )
   private static void onInvalidClickEvent(ClickEvent clickEvent, MinecraftClient client, Screen screen, CallbackInfo ci) {
      if (clickEvent instanceof RunnableClickEvent runnableClickEvent) {
         runnableClickEvent.runnable.run();
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_52752"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noRenderGuiBackground(DrawContext context, CallbackInfo ci) {
      if (Utils.canUpdate() && Modules.get().get(NoRender.class).noGuiBackground()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_25404"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
      if (!((Object)this instanceof ChatScreen)) {
         Modules modules = Modules.get();
         if (modules != null) {
            GUIMove guiMove = modules.get(GUIMove.class);
            if (guiMove != null && !guiMove.skip()) {
               int key = input.key();
               boolean isArrow = key == 262 || key == 263 || key == 264 || key == 265;
               if (guiMove.disableArrows() && isArrow || guiMove.disableSpace() && key == 32) {
                  cir.setReturnValue(true);
               }
            }
         }
      }
   }
}
