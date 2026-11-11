package revivedevelopment.reviveclient.mixininterface;

import net.minecraft.client.gl.Framebuffer;

public interface IMinecraftClient {
   void revive$rightClick();

   void revive$setFramebuffer(Framebuffer var1);
}
