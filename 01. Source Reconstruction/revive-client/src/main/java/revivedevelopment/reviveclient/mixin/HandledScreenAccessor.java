package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({HandledScreen.class})
public interface HandledScreenAccessor {
   @Accessor("field_2787")
   Slot getFocusedSlot();

   @Accessor("field_2776")
   int getX();

   @Accessor("field_2800")
   int getY();

   @Accessor("field_2792")
   int getBackgroundWidth();
}
