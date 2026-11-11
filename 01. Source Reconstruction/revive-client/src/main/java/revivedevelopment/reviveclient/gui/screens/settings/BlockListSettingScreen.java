package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.mixin.IdentifierAccessor;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.misc.Names;

public class BlockListSettingScreen extends CollectionListSettingScreen<Block> {
   private static final Identifier ID = Identifier.of("minecraft", "");

   public BlockListSettingScreen(GuiTheme theme, Setting<List<Block>> setting) {
      super(theme, "Select Blocks", setting, setting.get(), Registries.BLOCK);
   }

   protected boolean includeValue(Block value) {
      Predicate<Block> filter = ((BlockListSetting)this.setting).filter;
      return filter == null ? value != Blocks.AIR : filter.test(value);
   }

   protected WWidget getValueWidget(Block value) {
      return this.theme.itemWithLabel(value.asItem().getDefaultStack(), this.getValueName(value));
   }

   protected String getValueName(Block value) {
      return Names.get(value);
   }

   protected boolean skipValue(Block value) {
      return Registries.BLOCK.getId(value).getPath().endsWith("_wall_banner");
   }

   protected Block getAdditionalValue(Block value) {
      String path = Registries.BLOCK.getId(value).getPath();
      if (!path.endsWith("_banner")) {
         return null;
      } else {
         ((IdentifierAccessor)(Object)ID).setPath(path.substring(0, path.length() - 6) + "wall_banner");
         return (Block)Registries.BLOCK.get(ID);
      }
   }
}
