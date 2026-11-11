package revivedevelopment.reviveclient.mixin;

import net.minecraft.util.collection.TypeFilterableList;
import net.minecraft.world.entity.EntityTrackingSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({EntityTrackingSection.class})
public interface EntityTrackingSectionAccessor {
   @Accessor("field_27248")
   <T> TypeFilterableList<T> getCollection();
}
