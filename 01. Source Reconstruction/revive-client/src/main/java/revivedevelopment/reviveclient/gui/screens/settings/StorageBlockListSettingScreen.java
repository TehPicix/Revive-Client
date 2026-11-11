package revivedevelopment.reviveclient.gui.screens.settings;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.StorageBlockListSetting;

public class StorageBlockListSettingScreen extends CollectionListSettingScreen<BlockEntityType<?>> {
   private static final Map<BlockEntityType<?>, StorageBlockListSettingScreen.BlockEntityTypeInfo> BLOCK_ENTITY_TYPE_INFO_MAP = new Object2ObjectOpenHashMap();
   private static final StorageBlockListSettingScreen.BlockEntityTypeInfo UNKNOWN = new StorageBlockListSettingScreen.BlockEntityTypeInfo(
      Items.BARRIER, "Unknown"
   );

   public StorageBlockListSettingScreen(GuiTheme theme, Setting<List<BlockEntityType<?>>> setting) {
      super(theme, "Select Storage Blocks", setting, setting.get(), StorageBlockListSetting.REGISTRY);
   }

   protected WWidget getValueWidget(BlockEntityType<?> value) {
      Item item = BLOCK_ENTITY_TYPE_INFO_MAP.getOrDefault(value, UNKNOWN).item();
      return this.theme.itemWithLabel(item.getDefaultStack(), this.getValueName(value));
   }

   protected String getValueName(BlockEntityType<?> value) {
      return BLOCK_ENTITY_TYPE_INFO_MAP.getOrDefault(value, UNKNOWN).name();
   }

   static {
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.BARREL, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.BARREL, "Barrel"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.BLAST_FURNACE, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.BLAST_FURNACE, "Blast Furnace"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.BREWING_STAND, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.BREWING_STAND, "Brewing Stand"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.CAMPFIRE, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.CAMPFIRE, "Campfire"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.CHEST, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.CHEST, "Chest"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(
         BlockEntityType.CHISELED_BOOKSHELF, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.CHISELED_BOOKSHELF, "Chiseled Bookshelf")
      );
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.CRAFTER, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.CRAFTER, "Crafter"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.DISPENSER, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.DISPENSER, "Dispenser"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.DECORATED_POT, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.DECORATED_POT, "Decorated Pot"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.DROPPER, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.DROPPER, "Dropper"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.ENDER_CHEST, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.ENDER_CHEST, "Ender Chest"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.FURNACE, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.FURNACE, "Furnace"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.HOPPER, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.HOPPER, "Hopper"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.SHULKER_BOX, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.SHULKER_BOX, "Shulker Box"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.SMOKER, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.SMOKER, "Smoker"));
      BLOCK_ENTITY_TYPE_INFO_MAP.put(BlockEntityType.TRAPPED_CHEST, new StorageBlockListSettingScreen.BlockEntityTypeInfo(Items.TRAPPED_CHEST, "Trapped Chest"));
   }

   private record BlockEntityTypeInfo(Item item, String name) {
   }
}
