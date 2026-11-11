package revivedevelopment.reviveclient.utils.misc.text;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class TextUtils {
   private TextUtils() {
   }

   public static List<ColoredText> toColoredTextList(Text text) {
      Deque<ColoredText> stack = new ArrayDeque<>();
      List<ColoredText> coloredTexts = new ArrayList<>();
      preOrderTraverse(text, stack, coloredTexts);
      coloredTexts.removeIf(e -> e.text().isEmpty());
      return coloredTexts;
   }

   public static Color getMostPopularColor(Text text) {
      Entry<Color> biggestEntry = null;
      ObjectIterator var2 = getColoredCharacterCount(toColoredTextList(text)).object2IntEntrySet().iterator();

      while (var2.hasNext()) {
         Entry<Color> entry = (Entry<Color>)var2.next();
         if (biggestEntry == null) {
            biggestEntry = entry;
         } else if (entry.getIntValue() > biggestEntry.getIntValue()) {
            biggestEntry = entry;
         }
      }

      return biggestEntry == null ? new Color(255, 255, 255) : (Color)biggestEntry.getKey();
   }

   public static Object2IntMap<Color> getColoredCharacterCount(List<ColoredText> coloredTexts) {
      Object2IntMap<Color> colorCount = new Object2IntOpenHashMap();

      for (ColoredText coloredText : coloredTexts) {
         if (colorCount.containsKey(coloredText.color())) {
            colorCount.put(coloredText.color(), colorCount.getInt(coloredText.color()) + coloredText.text().length());
         } else {
            colorCount.put(coloredText.color(), coloredText.text().length());
         }
      }

      return colorCount;
   }

   private static void preOrderTraverse(Text text, Deque<ColoredText> stack, List<ColoredText> coloredTexts) {
      if (text != null) {
         String textString = text.getString();
         TextColor mcTextColor = text.getStyle().getColor();
         Color textColor;
         if (mcTextColor == null) {
            if (stack.isEmpty()) {
               textColor = new Color(255, 255, 255);
            } else {
               textColor = stack.peek().color();
            }
         } else {
            textColor = new Color(text.getStyle().getColor().getRgb() | 0xFF000000);
         }

         ColoredText coloredText = new ColoredText(textString, textColor);
         coloredTexts.add(coloredText);
         stack.push(coloredText);

         for (Text child : text.getSiblings()) {
            preOrderTraverse(child, stack, coloredTexts);
         }

         stack.pop();
      }
   }
}
