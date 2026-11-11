package revivedevelopment.reviveclient.utils.misc;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;

public class ItemNbt {
   private ItemNbt() {
   }

   @Nullable
   public static NbtElement encode(ItemStack stack) {
      return ReviveClient.mc.player != null && !stack.isEmpty()
         ? (NbtElement)ItemStack.CODEC.encodeStart(ReviveClient.mc.player.getRegistryManager().getOps(NbtOps.INSTANCE), stack).result().orElse(null)
         : null;
   }

   public static int byteSize(ItemStack stack) {
      NbtElement nbt = encode(stack);
      if (nbt == null) {
         return -1;
      } else {
         ByteCountDataOutput out = ByteCountDataOutput.INSTANCE;
         out.reset();

         byte var4;
         try {
            nbt.write(out);
            return out.getCount();
         } catch (Exception var8) {
            var4 = -1;
         } finally {
            out.reset();
         }

         return var4;
      }
   }

   public static String formatBytes(int bytes) {
      if (bytes < 0) {
         return "unknown size";
      } else if (bytes >= 1048576) {
         return String.format("%.2f MB", bytes / 1048576.0);
      } else {
         return bytes >= 1024 ? String.format("%.2f KB", bytes / 1024.0) : bytes + (bytes == 1 ? " byte" : " bytes");
      }
   }
}
