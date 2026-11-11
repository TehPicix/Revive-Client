package revivedevelopment.reviveclient.mixin;

import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({CrossbowItem.class})
public interface CrossbowItemAccessor {
   @Invoker("method_20309")
   static float getSpeed(ChargedProjectilesComponent itemStack) {
      return 0.0F;
   }
}
