package revivedevelopment.reviveclient.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import revivedevelopment.reviveclient.mixininterface.IPlayerMoveC2SPacket;

@Mixin({PlayerMoveC2SPacket.class})
public abstract class PlayerMoveC2SPacketMixin implements IPlayerMoveC2SPacket {
   @Unique
   private int tag;

   @Override
   public void revive$setTag(int tag) {
      this.tag = tag;
   }

   @Override
   public int revive$getTag() {
      return this.tag;
   }
}
