package revivedevelopment.reviveclient.mixin.pingbypass;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

@Mixin({ConnectScreen.class})
public abstract class PbConnectScreenMixin {
   @Inject(
      method = {"method_2130"},
      at = {@At("HEAD")}
   )
   private void revive$pbRouteJoin(MinecraftClient client, ServerAddress address, ServerInfo info, CookieStorage cookieStorage, CallbackInfo ci) {
      PbRouting.onConnectScreen(address, info);
   }
}
