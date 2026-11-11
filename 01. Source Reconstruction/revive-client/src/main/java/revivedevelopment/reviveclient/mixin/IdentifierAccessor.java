package revivedevelopment.reviveclient.mixin;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Identifier.class})
public interface IdentifierAccessor {
   @Mutable
   @Accessor("field_13355")
   void setPath(String var1);
}
