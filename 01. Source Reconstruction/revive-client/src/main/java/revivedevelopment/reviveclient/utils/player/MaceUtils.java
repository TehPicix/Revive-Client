package revivedevelopment.reviveclient.utils.player;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class MaceUtils {
   public static final int SERVER_MACE_MODEL = 647;

   private MaceUtils() {
   }

   public static boolean isMace(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else if (stack.isOf(Items.MACE)) {
         return true;
      } else if (!stack.isOf(Items.NETHERITE_AXE)) {
         return false;
      } else if (customModelData(stack) == 647) {
         return true;
      } else {
         CustomModelDataComponent cmd = (CustomModelDataComponent)stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
         if (cmd != null) {
            for (Float f : cmd.floats()) {
               if (f != null && Math.round(f) == 647) {
                  return true;
               }
            }
         }

         return isNamedMace(stack);
      }
   }

   public static boolean isNamedMace(ItemStack stack) {
      Text custom = (Text)stack.get(DataComponentTypes.CUSTOM_NAME);
      return custom == null ? false : custom.getString().toLowerCase().contains("mace");
   }

   public static String describe(ItemStack stack) {
      StringBuilder sb = new StringBuilder();
      sb.append("name=\"").append(stack.getName().getString()).append('"');
      CustomModelDataComponent cmd = (CustomModelDataComponent)stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
      if (cmd != null) {
         sb.append(" cmd=").append(cmd.floats()).append(cmd.strings());
      }

      sb.append(" data=").append(describeCustomData(stack));
      return sb.toString();
   }

   public static int customModelData(ItemStack stack) {
      NbtComponent data = (NbtComponent)stack.get(DataComponentTypes.CUSTOM_DATA);
      return data != null && !data.isEmpty() ? data.copyNbt().getInt("CustomModelData", -1) : -1;
   }

   public static String describeCustomData(ItemStack stack) {
      NbtComponent data = (NbtComponent)stack.get(DataComponentTypes.CUSTOM_DATA);
      if (data != null && !data.isEmpty()) {
         String s = data.copyNbt().toString();
         return s.length() > 200 ? s.substring(0, 200) + "..." : s;
      } else {
         return "no custom data";
      }
   }
}
