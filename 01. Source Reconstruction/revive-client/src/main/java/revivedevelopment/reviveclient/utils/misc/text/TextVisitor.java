package revivedevelopment.reviveclient.utils.misc.text;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

@FunctionalInterface
public interface TextVisitor<T> {
   Optional<T> accept(Text var1, Style var2, String var3);

   static <T> Optional<T> visit(Text text, TextVisitor<T> visitor, Style baseStyle) {
      Style style = text.getStyle().withParent(baseStyle);
      Optional<T> result = text.getContent().visit((s, string) -> visitor.accept(text, s, string), style);
      if (result.isPresent()) {
         return result;
      } else {
         for (Text sibling : text.getSiblings()) {
            result = visit(sibling, visitor, style);
            if (result.isPresent()) {
               return result;
            }
         }

         return Optional.empty();
      }
   }

   static ArrayDeque<Text> collectSiblings(Text text) {
      ArrayDeque<Text> queue = new ArrayDeque<>();
      collectSiblings(text, queue);
      return queue;
   }

   private static void collectSiblings(Text text, Queue<Text> queue) {
      if (!(text.getContent() instanceof PlainTextContent ptc && ptc.string().isEmpty())) {
         queue.add(text);
      }

      for (Text sibling : text.getSiblings()) {
         collectSiblings(sibling, queue);
      }
   }
}
