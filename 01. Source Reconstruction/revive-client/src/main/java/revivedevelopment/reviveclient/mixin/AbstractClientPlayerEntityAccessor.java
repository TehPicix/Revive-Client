package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AbstractClientPlayerEntity.class})
public interface AbstractClientPlayerEntityAccessor {
   @Accessor("field_3901")
   void revive$setPlayerListEntry(PlayerListEntry var1);
}
