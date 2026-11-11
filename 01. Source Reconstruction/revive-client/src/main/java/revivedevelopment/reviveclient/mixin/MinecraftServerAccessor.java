package revivedevelopment.reviveclient.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorage.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({MinecraftServer.class})
public interface MinecraftServerAccessor {
   @Accessor("field_23784")
   Session getSession();
}
