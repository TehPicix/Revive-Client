package revivedevelopment.reviveclient.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.ISlot;

@Mixin(
   targets = {"net/minecraft/class_481$class_484"}
)
public abstract class CreativeSlotMixin implements ISlot {
   @Shadow
   @Final
   Slot field_2898;

   @Override
   public int revive$getId() {
      return this.field_2898.id;
   }

   @Override
   public int revive$getIndex() {
      return this.field_2898.getIndex();
   }
}
