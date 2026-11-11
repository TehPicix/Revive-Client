package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextHandler.WidthRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({TextHandler.class})
public interface TextHandlerAccessor {
   @Accessor("field_24216")
   WidthRetriever getWidthRetriever();
}
