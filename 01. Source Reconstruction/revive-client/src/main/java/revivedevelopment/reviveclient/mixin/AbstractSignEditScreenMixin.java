package revivedevelopment.reviveclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.stream.Stream;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.text.KeybindTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.text.PlainTextContent.Literal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({AbstractSignEditScreen.class})
public abstract class AbstractSignEditScreenMixin {
   @ModifyExpressionValue(
      method = {"<init>(Lnet/minecraft/class_2625;ZZLnet/minecraft/class_2561;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/stream/IntStream;mapToObj(Ljava/util/function/IntFunction;)Ljava/util/stream/Stream;"
      )}
   )
   private Stream<Text> modifyTranslatableText(Stream<Text> original) {
      return original.map(this::modifyText);
   }

   @Unique
   private Text modifyText(Text message) {
      MutableText modified = MutableText.of(message.getContent());
      if (message.getContent() instanceof KeybindTextContent content) {
         String key = content.getKey();
         if (key.contains("revive-client")) {
            modified = MutableText.of(new Literal(key));
         }
      }

      if (message.getContent() instanceof TranslatableTextContent contentx) {
         String key = contentx.getKey();
         if (key.contains("revive-client")) {
            modified = MutableText.of(new Literal(key));
         }
      }

      modified.setStyle(message.getStyle());

      for (Text sibling : message.getSiblings()) {
         modified.append(this.modifyText(sibling));
      }

      return modified;
   }
}
