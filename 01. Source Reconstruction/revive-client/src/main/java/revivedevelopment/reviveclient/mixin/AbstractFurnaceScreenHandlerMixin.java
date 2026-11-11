package revivedevelopment.reviveclient.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.IAbstractFurnaceScreenHandler;

@Mixin({AbstractFurnaceScreenHandler.class})
public abstract class AbstractFurnaceScreenHandlerMixin implements IAbstractFurnaceScreenHandler {
   @Shadow
   protected abstract boolean method_7640(ItemStack var1);

   @Override
   public boolean revive$isItemSmeltable(ItemStack itemStack) {
      return this.method_7640(itemStack);
   }
}
