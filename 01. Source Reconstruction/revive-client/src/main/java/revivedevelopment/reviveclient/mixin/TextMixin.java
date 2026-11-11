package revivedevelopment.reviveclient.mixin;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import revivedevelopment.reviveclient.mixininterface.IText;

@Mixin({Text.class})
public interface TextMixin extends IText {
   @Override
   default void revive$invalidateCache() {
   }
}
