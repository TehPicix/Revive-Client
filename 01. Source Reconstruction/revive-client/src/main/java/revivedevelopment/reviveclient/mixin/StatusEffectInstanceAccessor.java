package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({StatusEffectInstance.class})
public interface StatusEffectInstanceAccessor {
   @Accessor("field_5895")
   void setDuration(int var1);

   @Accessor("field_5893")
   void setAmplifier(int var1);
}
