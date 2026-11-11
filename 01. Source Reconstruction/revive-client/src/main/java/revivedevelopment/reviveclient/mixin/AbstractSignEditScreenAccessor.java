package revivedevelopment.reviveclient.mixin;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AbstractSignEditScreen.class})
public interface AbstractSignEditScreenAccessor {
   @Accessor("field_40424")
   SignBlockEntity getSign();
}
