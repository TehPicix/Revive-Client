package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.gui.titlemenu.TitleMusicPlayer;

@Mixin({MusicTracker.class})
public class MusicTrackerMixin {
   @Inject(
      method = {"method_18669"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void suppressTickOnCustomTitleScreen(CallbackInfo ci) {
      if (TitleMusicPlayer.getInstance().isMenuActive()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"method_4858"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void suppressPlayOnCustomTitleScreen(MusicSound music, CallbackInfo ci) {
      if (TitleMusicPlayer.getInstance().isMenuActive()) {
         ci.cancel();
      }
   }
}
