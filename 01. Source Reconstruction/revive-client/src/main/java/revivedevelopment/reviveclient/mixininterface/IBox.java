package revivedevelopment.reviveclient.mixininterface;

import net.minecraft.util.math.BlockPos;

public interface IBox {
   void revive$expand(double var1);

   void revive$set(double var1, double var3, double var5, double var7, double var9, double var11);

   default void revive$set(BlockPos pos) {
      this.revive$set(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
   }
}
