package revivedevelopment.reviveclient.utils.render.postprocess;

import net.minecraft.entity.Entity;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.SwordAura;

public class SwordAuraShader extends EntityShader {
   private static SwordAura swordAura;

   public SwordAuraShader() {
      super(ReviveRenderPipelines.POST_OUTLINE);
   }

   private static SwordAura aura() {
      if (swordAura == null) {
         swordAura = Modules.get().get(SwordAura.class);
      }

      return swordAura;
   }

   @Override
   public boolean shouldDraw() {
      return aura().isShaderActive();
   }

   @Override
   public boolean shouldDraw(Entity entity) {
      return !this.shouldDraw() ? false : aura().getShaderColor(entity) != null;
   }

   @Override
   protected void setupPass(MeshRenderer renderer) {
      SwordAura aura = aura();
      renderer.uniform(
         "OutlineData",
         OutlineUniforms.write(
            aura.shaderOutlineWidth.get(),
            aura.shaderFillOpacity.get().floatValue(),
            aura.shaderShapeMode.get().ordinal(),
            aura.shaderGlowMultiplier.get().floatValue()
         )
      );
   }
}
