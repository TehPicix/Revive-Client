package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.ChestStealer;

@Mixin({HandledScreen.class})
public abstract class ChestStealerScreenMixin extends Screen {
   @Shadow
   protected int field_2776;
   @Shadow
   protected int field_2800;
   @Shadow
   protected int field_2792;

   protected ChestStealerScreenMixin(Text title) {
      super(title);
   }

   @Inject(
      method = {"method_25426"},
      at = {@At("TAIL")}
   )
   private void revive$addStealButtons(CallbackInfo ci) {
      ChestStealer stealer = Modules.get().get(ChestStealer.class);
      if (stealer != null && stealer.showButtons()) {
         HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
         if (stealer.isStealableHandler(screen.getScreenHandler())) {
            int bx = this.field_2776 + this.field_2792 + 4;
            int bw = 90;
            this.addDrawableChild(
               ButtonWidget.builder(Text.literal("Steal All"), b -> stealer.requestStealAll()).dimensions(bx, this.field_2800, bw, 20).build()
            );
            this.addDrawableChild(
               ButtonWidget.builder(Text.literal("Steal Selected"), b -> stealer.requestStealSelected()).dimensions(bx, this.field_2800 + 24, bw, 20).build()
            );
         }
      }
   }
}
