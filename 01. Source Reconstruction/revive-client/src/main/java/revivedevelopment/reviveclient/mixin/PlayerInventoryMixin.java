package revivedevelopment.reviveclient.mixin;

import net.minecraft.entity.EntityEquipment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.IPlayerInventory;

@Mixin({PlayerInventory.class})
public abstract class PlayerInventoryMixin implements IPlayerInventory {
   @Shadow
   @Final
   private EntityEquipment field_56552;

   @Shadow
   public abstract DefaultedList<ItemStack> method_67533();

   @Override
   public DefaultedList<ItemStack> revive$getArmor() {
      DefaultedList<ItemStack> armor = DefaultedList.ofSize(4, ItemStack.EMPTY);
      armor.set(0, this.field_56552.get(EquipmentSlot.FEET));
      armor.set(1, this.field_56552.get(EquipmentSlot.LEGS));
      armor.set(2, this.field_56552.get(EquipmentSlot.CHEST));
      armor.set(3, this.field_56552.get(EquipmentSlot.HEAD));
      return armor;
   }

   @Override
   public DefaultedList<ItemStack> revive$getMain() {
      return this.method_67533();
   }
}
