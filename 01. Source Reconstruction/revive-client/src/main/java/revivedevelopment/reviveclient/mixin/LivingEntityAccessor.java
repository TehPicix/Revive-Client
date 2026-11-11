package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({LivingEntity.class})
public interface LivingEntityAccessor {
   @Invoker("method_6010")
   void swimUpwards(TagKey<Fluid> var1);

   @Invoker("method_6043")
   void revive$jump();

   @Accessor("field_6282")
   boolean isJumping();

   @Accessor("field_6228")
   int getJumpCooldown();

   @Accessor("field_6228")
   void setJumpCooldown(int var1);
}
