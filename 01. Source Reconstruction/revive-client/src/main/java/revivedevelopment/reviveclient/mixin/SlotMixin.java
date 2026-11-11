package revivedevelopment.reviveclient.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.ISlot;

@Mixin({Slot.class})
public abstract class SlotMixin implements ISlot {
   @Shadow
   public int field_7874;
   @Shadow
   @Final
   private int field_7875;

   @Override
   public int revive$getId() {
      return this.field_7874;
   }

   @Override
   public int revive$getIndex() {
      return this.field_7875;
   }
}
