package revivedevelopment.reviveclient.settings;

import net.minecraft.block.Block;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.utils.misc.IChangeable;
import revivedevelopment.reviveclient.utils.misc.ICopyable;
import revivedevelopment.reviveclient.utils.misc.ISerializable;

public interface IBlockData<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> {
   WidgetScreen createScreen(GuiTheme var1, Block var2, BlockDataSetting<T> var3);
}
