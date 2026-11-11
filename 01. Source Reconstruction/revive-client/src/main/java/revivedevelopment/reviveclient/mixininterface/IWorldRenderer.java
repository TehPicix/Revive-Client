package revivedevelopment.reviveclient.mixininterface;

import net.minecraft.client.gl.Framebuffer;

public interface IWorldRenderer {
   void revive$pushEntityOutlineFramebuffer(Framebuffer var1);

   void revive$popEntityOutlineFramebuffer();
}
