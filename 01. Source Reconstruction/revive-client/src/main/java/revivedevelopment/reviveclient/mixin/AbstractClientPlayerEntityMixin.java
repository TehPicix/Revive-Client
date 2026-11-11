package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.misc.FakeClientPlayer;

@Mixin({AbstractClientPlayerEntity.class})
public abstract class AbstractClientPlayerEntityMixin {
   @Inject(
      method = {"method_3123"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetPlayerListEntry(CallbackInfoReturnable<PlayerListEntry> info) {
      if (ReviveClient.mc.getNetworkHandler() == null) {
         info.setReturnValue(FakeClientPlayer.getPlayerListEntry());
      }
   }
}
