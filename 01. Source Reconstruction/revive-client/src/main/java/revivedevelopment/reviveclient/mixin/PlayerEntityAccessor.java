package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({PlayerEntity.class})
public interface PlayerEntityAccessor {
   @Invoker("method_52558")
   boolean revive$canChangeIntoPose(EntityPose var1);
}
