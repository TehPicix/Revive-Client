package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({World.class})
public interface WorldAccessor {
   @Invoker("method_31592")
   EntityLookup<Entity> getEntityLookup();

   @Accessor("field_9235")
   void revive$setRainGradient(float var1);

   @Accessor("field_9253")
   void revive$setRainGradientPrev(float var1);

   @Accessor("field_9234")
   void revive$setThunderGradient(float var1);

   @Accessor("field_9251")
   void revive$setThunderGradientPrev(float var1);
}
