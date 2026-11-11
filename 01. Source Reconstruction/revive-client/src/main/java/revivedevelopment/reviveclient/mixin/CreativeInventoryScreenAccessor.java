package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({CreativeInventoryScreen.class})
public interface CreativeInventoryScreenAccessor {
   @Accessor("field_2896")
   static ItemGroup getSelectedTab() {
      return null;
   }
}
