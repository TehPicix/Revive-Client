package revivedevelopment.reviveclient.mixininterface;

import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public interface IPlayerInventory {
   DefaultedList<ItemStack> revive$getArmor();

   DefaultedList<ItemStack> revive$getMain();
}
