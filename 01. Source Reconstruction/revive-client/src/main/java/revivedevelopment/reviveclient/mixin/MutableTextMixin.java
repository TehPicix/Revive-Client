package revivedevelopment.reviveclient.mixin;

import net.minecraft.text.MutableText;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import revivedevelopment.reviveclient.mixininterface.IText;

@Mixin({MutableText.class})
public abstract class MutableTextMixin implements IText {
   @Shadow
   @Nullable
   private Language field_39009;

   @Override
   public void revive$invalidateCache() {
      this.field_39009 = null;
   }
}
