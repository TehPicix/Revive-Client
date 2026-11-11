package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ProjectileEntity.class})
public interface ProjectileOwnerAccessor {
   @Accessor("field_60558")
   LazyEntityReference<Entity> getOwnerReference();
}
