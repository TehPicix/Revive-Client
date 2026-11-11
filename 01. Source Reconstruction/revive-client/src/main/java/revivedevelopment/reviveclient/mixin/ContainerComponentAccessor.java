package revivedevelopment.reviveclient.mixin;

import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ContainerComponent.class})
public interface ContainerComponentAccessor {
   @Accessor("field_49338")
   DefaultedList<ItemStack> getStacks();
}
