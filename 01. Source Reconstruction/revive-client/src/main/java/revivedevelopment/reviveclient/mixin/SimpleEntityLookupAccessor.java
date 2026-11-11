package revivedevelopment.reviveclient.mixin;

import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.SectionedEntityCache;
import net.minecraft.world.entity.SimpleEntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({SimpleEntityLookup.class})
public interface SimpleEntityLookupAccessor {
   @Accessor("field_27259")
   <T extends EntityLike> SectionedEntityCache<T> getCache();
}
