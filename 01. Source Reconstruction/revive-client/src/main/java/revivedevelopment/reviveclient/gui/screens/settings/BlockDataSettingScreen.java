package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.Strings;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.settings.BlockDataSetting;
import revivedevelopment.reviveclient.settings.IBlockData;
import revivedevelopment.reviveclient.utils.misc.IChangeable;
import revivedevelopment.reviveclient.utils.misc.ICopyable;
import revivedevelopment.reviveclient.utils.misc.ISerializable;
import revivedevelopment.reviveclient.utils.misc.Names;

public class BlockDataSettingScreen extends WindowScreen {
   private static final List<Block> BLOCKS = new ArrayList<>(100);
   private final BlockDataSetting<?> setting;
   private WTable table;
   private String filterText = "";

   public BlockDataSettingScreen(GuiTheme theme, BlockDataSetting<?> setting) {
      super(theme, "Configure Blocks");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      WTextBox filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      filter.setFocused(true);
      filter.action = () -> {
         this.filterText = filter.get().trim();
         this.table.clear();
         this.initTable();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initTable();
   }

   public <T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> void initTable() {
      for (Block block : Registries.BLOCK) {
         T blockData = (T)this.setting.get().get(block);
         if (blockData != null && blockData.isChanged()) {
            BLOCKS.addFirst(block);
         } else {
            BLOCKS.add(block);
         }
      }

      for (Block blockx : BLOCKS) {
         String name = Names.get(blockx);
         if (Strings.CI.contains(name, this.filterText)) {
            T blockData = (T)this.setting.get().get(blockx);
            this.table.add(this.theme.itemWithLabel(blockx.asItem().getDefaultStack(), Names.get(blockx))).expandCellX();
            this.table.add(this.theme.label(blockData != null && blockData.isChanged() ? "*" : " "));
            WButton edit = this.table.add(this.theme.button(GuiRenderer.EDIT)).widget();
            edit.action = () -> {
               T data = blockData;
               if (blockData == null) {
                  data = (T)((ICopyable)this.setting.defaultData.get()).copy();
               }

               ReviveClient.mc.setScreen(data.createScreen(this.theme, blockx, (BlockDataSetting<T>)this.setting));
            };
            WButton reset = this.table.add(this.theme.button(GuiRenderer.RESET)).widget();
            reset.action = () -> {
               this.setting.get().remove(blockx);
               this.setting.onChanged();
               if (blockData != null && blockData.isChanged()) {
                  this.table.clear();
                  this.initTable();
               }
            };
            this.table.row();
         }
      }

      BLOCKS.clear();
   }
}
