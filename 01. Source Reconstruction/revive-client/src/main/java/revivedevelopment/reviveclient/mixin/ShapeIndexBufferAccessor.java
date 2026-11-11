package revivedevelopment.reviveclient.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ShapeIndexBuffer.class})
public interface ShapeIndexBufferAccessor {
   @Accessor("field_54299")
   GpuBuffer getBuffer();
}
