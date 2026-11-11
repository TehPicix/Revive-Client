package revivedevelopment.reviveclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.gui.newgui.GuiShaderBackground;
import revivedevelopment.reviveclient.renderer.MeshUniforms;
import revivedevelopment.reviveclient.utils.render.postprocess.ChamsShader;
import revivedevelopment.reviveclient.utils.render.postprocess.OutlineUniforms;
import revivedevelopment.reviveclient.utils.render.postprocess.PostProcessShader;
import revivedevelopment.reviveclient.utils.render.postprocess.SkyShaderRenderer;

@Mixin({RenderSystem.class})
public abstract class RenderSystemMixin {
   @Inject(
      method = {"flipFrame"},
      at = {@At("TAIL")}
   )
   private static void revive$flipFrame(CallbackInfo info) {
      MeshUniforms.flipFrame();
      PostProcessShader.flipFrame();
      ChamsShader.flipFrame();
      OutlineUniforms.flipFrame();
      GuiShaderBackground.flipFrame();
      SkyShaderRenderer.flipFrame();
   }
}
