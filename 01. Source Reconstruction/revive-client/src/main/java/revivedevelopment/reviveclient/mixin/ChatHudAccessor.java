package revivedevelopment.reviveclient.mixin;

import java.util.List;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ChatHud.class})
public interface ChatHudAccessor {
   @Accessor("field_2064")
   List<Visible> getVisibleMessages();

   @Accessor("field_2061")
   List<ChatHudLine> getMessages();
}
