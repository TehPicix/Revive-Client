package revivedevelopment.reviveclient.utils.misc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import revivedevelopment.reviveclient.ReviveClient;

public class NbtUtils {
   public static <T extends ISerializable<?>> NbtList listToTag(Iterable<T> list) {
      NbtList tag = new NbtList();

      for (T item : list) {
         tag.add(item.toTag());
      }

      return tag;
   }

   public static <T> List<T> listFromTag(NbtList tag, NbtUtils.ToValue<T> toItem) {
      List<T> list = new ArrayList<>(tag.size());

      for (NbtElement itemTag : tag) {
         T value = toItem.toValue(itemTag);
         if (value != null) {
            list.add(value);
         }
      }

      return list;
   }

   public static boolean toClipboard(ISerializable<?> serializable) {
      return toClipboard(serializable.toTag());
   }

   public static boolean toClipboard(NbtCompound tag) {
      String preClipboard = ReviveClient.mc.keyboard.getClipboard();

      try {
         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         NbtIo.writeCompressed(tag, byteArrayOutputStream);
         ReviveClient.mc.keyboard.setClipboard(Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray()));
         return true;
      } catch (Exception var3) {
         ReviveClient.LOG.error("Error copying NBT to clipboard!", var3);
         ReviveClient.mc.keyboard.setClipboard(preClipboard);
         return false;
      }
   }

   public static boolean fromClipboard(ISerializable<?> serializable) {
      NbtCompound tag = fromClipboard();
      if (tag == null) {
         return false;
      } else {
         NbtCompound sourceTag = serializable.toTag();

         for (String key : sourceTag.getKeys()) {
            if (!tag.contains(key)) {
               return false;
            }
         }

         serializable.fromTag(tag);
         return true;
      }
   }

   public static NbtCompound fromClipboard() {
      try {
         byte[] data = Base64.getDecoder().decode(ReviveClient.mc.keyboard.getClipboard().trim());
         ByteArrayInputStream bis = new ByteArrayInputStream(data);
         return NbtIo.readCompressed(new DataInputStream(bis), NbtSizeTracker.ofUnlimitedBytes());
      } catch (Exception var2) {
         ReviveClient.LOG.error("Invalid NBT data pasted!", var2);
         return null;
      }
   }

   public interface ToValue<T> {
      T toValue(NbtElement var1);
   }
}
