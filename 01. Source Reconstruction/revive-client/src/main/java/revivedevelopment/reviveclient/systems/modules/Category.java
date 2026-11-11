package revivedevelopment.reviveclient.systems.modules;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class Category {
   public final String name;
   public final ItemStack icon;
   private final int nameHash;

   public Category(String name, ItemStack icon) {
      this.name = name;
      this.nameHash = name.hashCode();
      this.icon = icon == null ? Items.AIR.getDefaultStack() : icon;
   }

   public Category(String name) {
      this(name, null);
   }

   @Override
   public String toString() {
      return this.name;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Category category = (Category)o;
         return this.nameHash == category.nameHash;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return this.nameHash;
   }
}
