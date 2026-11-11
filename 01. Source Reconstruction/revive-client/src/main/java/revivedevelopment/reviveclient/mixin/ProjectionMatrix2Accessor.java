package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.render.ProjectionMatrix2;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ProjectionMatrix2.class})
public interface ProjectionMatrix2Accessor {
   @Invoker("method_71094")
   Matrix4f revive$callGetMatrix(float var1, float var2);
}
