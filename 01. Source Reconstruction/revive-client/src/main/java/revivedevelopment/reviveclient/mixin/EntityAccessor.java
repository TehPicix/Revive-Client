package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Entity.class})
public interface EntityAccessor {
   @Accessor("field_5957")
   void setInWater(boolean var1);

   @Invoker("method_5729")
   void revive$setFlag(int var1, boolean var2);

   @Accessor("field_5990")
   static TrackedData<Byte> revive$getFlags() {
      throw new AssertionError();
   }
}
