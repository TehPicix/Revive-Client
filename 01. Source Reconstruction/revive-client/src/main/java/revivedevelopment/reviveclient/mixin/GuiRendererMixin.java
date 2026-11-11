package revivedevelopment.reviveclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.gui.newgui.GuiTextMesh;

@Mixin({GuiRenderer.class})
public class GuiRendererMixin {
   @Inject(
      method = {"method_70890"},
      at = {@At("TAIL")},
      require = 0
   )
   private void revive$renderCrispText(GpuBufferSlice slice, CallbackInfo ci) {
      GuiTextMesh.renderQueued();
   }
}
