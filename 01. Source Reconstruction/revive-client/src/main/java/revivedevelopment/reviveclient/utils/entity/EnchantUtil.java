package revivedevelopment.reviveclient.utils.entity;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import revivedevelopment.reviveclient.utils.Utils;

public final class EnchantUtil {
   private EnchantUtil() {
   }

   public static int getLevel(ItemStack stack, RegistryKey<Enchantment> key) {
      return stack != null && !stack.isEmpty() ? Utils.getEnchantmentLevel(stack, key) : 0;
   }

   public static boolean has(ItemStack stack, RegistryKey<Enchantment> key) {
      return getLevel(stack, key) > 0;
   }
}
