package revivedevelopment.reviveclient.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.gl.GlBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import revivedevelopment.reviveclient.mixininterface.IGpuDevice;

@Mixin({GlBackend.class})
public abstract class GlBackendMixin implements IGpuDevice {
   @Unique
   private int x;
   @Unique
   private int y;
   @Unique
   private int width;
   @Unique
   private int height;
   @Unique
   private boolean set;

   @Override
   public void revive$pushScissor(int x, int y, int width, int height) {
      if (this.set) {
         throw new IllegalStateException("Currently there can only be one global scissor pushed");
      } else {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.set = true;
      }
   }

   @Override
   public void revive$popScissor() {
      if (!this.set) {
         throw new IllegalStateException("No scissor pushed");
      } else {
         this.set = false;
      }
   }

   @Deprecated
   @Override
   public void revive$onCreateRenderPass(RenderPass pass) {
      if (this.set) {
         pass.enableScissor(this.x, this.y, this.width, this.height);
      }
   }
}
