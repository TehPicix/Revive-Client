package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({FireworkRocketEntity.class})
public interface FireworkRocketEntityAccessor {
   @Accessor("field_7613")
   int getLife();

   @Accessor("field_7612")
   int getLifeTime();
}
