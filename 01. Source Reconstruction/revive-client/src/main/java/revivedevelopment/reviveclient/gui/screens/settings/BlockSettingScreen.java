package revivedevelopment.reviveclient.gui.screens.settings;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.Strings;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.widgets.WItemWithLabel;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.settings.BlockSetting;
import revivedevelopment.reviveclient.utils.misc.Names;

public class BlockSettingScreen extends WindowScreen {
   private final BlockSetting setting;
   private WTable table;
   private WTextBox filter;
   private String filterText = "";

   public BlockSettingScreen(GuiTheme theme, BlockSetting setting) {
      super(theme, "Select Block");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      this.filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      this.filter.setFocused(true);
      this.filter.action = () -> {
         this.filterText = this.filter.get().trim();
         this.table.clear();
         this.initTable();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initTable();
   }

   private void initTable() {
      for (Block block : Registries.BLOCK) {
         if ((this.setting.filter == null || this.setting.filter.test(block)) && !this.skipValue(block)) {
            WItemWithLabel item = this.theme.itemWithLabel(block.asItem().getDefaultStack(), Names.get(block));
            if (this.filterText.isEmpty() || Strings.CI.contains(item.getLabelText(), this.filterText)) {
               this.table.add(item);
               WButton select = this.table.add(this.theme.button("Select")).expandCellX().right().widget();
               select.action = () -> {
                  this.setting.set(block);
                  this.close();
               };
               this.table.row();
            }
         }
      }
   }

   protected boolean skipValue(Block value) {
      return value == Blocks.AIR || Registries.BLOCK.getId(value).getPath().endsWith("_wall_banner");
   }
}
