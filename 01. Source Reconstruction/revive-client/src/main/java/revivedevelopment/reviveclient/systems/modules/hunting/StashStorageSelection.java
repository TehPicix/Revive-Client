package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import revivedevelopment.reviveclient.settings.StorageBlockListSetting;

public final class StashStorageSelection {
   private static final Map<BlockEntityType<?>, Item> TYPES = Arrays.stream(StorageBlockListSetting.STORAGE_BLOCKS)
      .collect(Collectors.toUnmodifiableMap(var0 -> var0, var0 -> (Item)Registries.ITEM.get(Registries.BLOCK_ENTITY_TYPE.getId(var0))));

   private StashStorageSelection() {
   }

   public static List<Item> defaults() {
      return Arrays.stream(StorageBlockListSetting.STORAGE_BLOCKS).map(TYPES::get).toList();
   }

   public static boolean allowed(Item var0) {
      return var0 == Items.CHEST_MINECART || TYPES.containsValue(var0);
   }

   public static boolean matches(List<Item> var0, BlockEntityType<?> var1) {
      Item var2 = TYPES.get(var1);
      return var2 != null && var0.contains(var2);
   }
}
