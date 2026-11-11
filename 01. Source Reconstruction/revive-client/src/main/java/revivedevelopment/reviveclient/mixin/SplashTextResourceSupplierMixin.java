package revivedevelopment.reviveclient.mixin;

import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.systems.config.Config;

@Mixin({SplashTextResourceSupplier.class})
public abstract class SplashTextResourceSupplierMixin {
   @Unique
   private boolean override = true;
   @Unique
   private static final Random random = new Random();
   @Unique
   private final List<String> reviveSplashes = getReviveSplashes();

   @Inject(
      method = {"method_18174"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onApply(CallbackInfoReturnable<SplashTextRenderer> cir) {
      if (Config.get() != null && Config.get().titleScreenSplashes.get()) {
         if (this.override) {
            cir.setReturnValue(new SplashTextRenderer(Text.literal(this.reviveSplashes.get(random.nextInt(this.reviveSplashes.size())))));
         }

         this.override = !this.override;
      }
   }

   @Unique
   private static List<String> getReviveSplashes() {
      return List.of(
         "Revive on Crack!",
         "Star Revive Client on GitHub!",
         "Based utility mod.",
         "§6Crownizzle §fbased god",
         "§4reviveclient.com",
         "§4Revive on Crack!",
         "§6Revive on Crack!"
      );
   }
}
