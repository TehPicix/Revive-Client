package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.texture.MapTextureManager;
import net.minecraft.client.texture.MapTextureManager.MapTexture;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({MapTextureManager.class})
public interface MapTextureManagerAccessor {
   @Invoker("method_62625")
   MapTexture invokeGetMapTexture(MapIdComponent var1, MapState var2);
}
