package revivedevelopment.reviveclient.mixin;

import java.nio.file.Path;
import net.minecraft.client.texture.PlayerSkinProvider.FileCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({FileCache.class})
public interface FileCacheAccessor {
   @Accessor("field_45640")
   Path getDirectory();
}
