package revivedevelopment.reviveclient.gui.utils;

import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.utils.misc.ISerializable;

public class WindowConfig implements ISerializable<WindowConfig> {
   public boolean expanded = true;
   public double x = -1.0;
   public double y = -1.0;

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putBoolean("expanded", this.expanded);
      tag.putDouble("x", this.x);
      tag.putDouble("y", this.y);
      return tag;
   }

   public WindowConfig fromTag(NbtCompound tag) {
      tag.getBoolean("expanded").ifPresent(bool -> this.expanded = bool);
      tag.getDouble("x").ifPresent(x1 -> this.x = x1);
      tag.getDouble("y").ifPresent(y1 -> this.y = y1);
      return this;
   }
}
