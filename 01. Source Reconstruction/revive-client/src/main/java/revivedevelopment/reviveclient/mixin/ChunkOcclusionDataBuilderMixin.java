package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Freecam;
import revivedevelopment.reviveclient.systems.modules.render.NoRender;

@Mixin({ChunkOcclusionDataBuilder.class})
public abstract class ChunkOcclusionDataBuilderMixin {
   private static NoRender revive$noRender;
   private static Freecam revive$freecam;

   @Inject(
      method = {"method_3682"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMarkClosed(BlockPos pos, CallbackInfo info) {
      if (revive$noRender == null) {
         Modules modules = Modules.get();
         if (modules == null) {
            return;
         }

         revive$noRender = modules.get(NoRender.class);
         revive$freecam = modules.get(Freecam.class);
      }

      if (revive$noRender != null && revive$noRender.noCaveCulling() || revive$freecam != null && revive$freecam.disablesCaveCulling()) {
         info.cancel();
      }
   }
}
