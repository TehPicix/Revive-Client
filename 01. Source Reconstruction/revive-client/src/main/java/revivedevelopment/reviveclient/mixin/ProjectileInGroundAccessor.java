package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({PersistentProjectileEntity.class})
public interface ProjectileInGroundAccessor {
   @Invoker("method_65059")
   boolean invokeIsInGround();
}
