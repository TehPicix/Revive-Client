package revivedevelopment.reviveclient.events.game;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class ItemStackTooltipEvent {
   private final ItemStack itemStack;
   private List<Text> list;

   public ItemStackTooltipEvent(ItemStack itemStack, List<Text> list) {
      this.itemStack = itemStack;
      this.list = list;
   }

   public List<Text> list() {
      return this.list;
   }

   public ItemStack itemStack() {
      return this.itemStack;
   }

   public void appendStart(Text text) {
      this.copyIfImmutable();
      int index = this.list.isEmpty() ? 0 : 1;
      this.list.add(index, text);
   }

   public void appendEnd(Text text) {
      this.copyIfImmutable();
      this.list.add(text);
   }

   public void append(int index, Text text) {
      this.copyIfImmutable();
      this.list.add(index, text);
   }

   public void set(int index, Text text) {
      this.copyIfImmutable();
      this.list.set(index, text);
   }

   private void copyIfImmutable() {
      if (List.of().getClass().getSuperclass().isInstance(this.list)) {
         this.list = new ObjectArrayList(this.list);
      }
   }
}
