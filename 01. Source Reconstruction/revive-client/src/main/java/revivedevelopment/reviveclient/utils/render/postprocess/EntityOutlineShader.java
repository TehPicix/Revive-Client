package revivedevelopment.reviveclient.utils.render.postprocess;

import net.minecraft.entity.Entity;
import revivedevelopment.reviveclient.renderer.MeshRenderer;
import revivedevelopment.reviveclient.renderer.ReviveRenderPipelines;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.ESP;

public class EntityOutlineShader extends EntityShader {
   private static ESP esp;

   public EntityOutlineShader() {
      super(ReviveRenderPipelines.POST_OUTLINE);
   }

   @Override
   public boolean shouldDraw() {
      if (esp == null) {
         esp = Modules.get().get(ESP.class);
      }

      return esp.isShader();
   }

   @Override
   public boolean shouldDraw(Entity entity) {
      return !this.shouldDraw() ? false : !esp.shouldSkip(entity);
   }

   @Override
   protected void setupPass(MeshRenderer renderer) {
      renderer.uniform(
         "OutlineData",
         OutlineUniforms.write(esp.outlineWidth.get(), esp.fillOpacity.get().floatValue(), esp.shapeMode.get().ordinal(), esp.glowMultiplier.get().floatValue())
      );
   }
}
