package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.passive.AbstractHorseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.IHorseBaseEntity;

@Mixin({AbstractHorseEntity.class})
public abstract class AbstractHorseEntityMixin implements IHorseBaseEntity {
   @Shadow
   protected abstract void method_6769(int var1, boolean var2);

   @Override
   public void setSaddled(boolean saddled) {
      this.method_6769(4, saddled);
   }
}
