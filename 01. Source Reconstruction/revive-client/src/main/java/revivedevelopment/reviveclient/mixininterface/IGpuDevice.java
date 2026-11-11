package revivedevelopment.reviveclient.mixininterface;

import com.mojang.blaze3d.systems.RenderPass;

public interface IGpuDevice {
   void revive$pushScissor(int var1, int var2, int var3, int var4);

   void revive$popScissor();

   @Deprecated
   void revive$onCreateRenderPass(RenderPass var1);
}
