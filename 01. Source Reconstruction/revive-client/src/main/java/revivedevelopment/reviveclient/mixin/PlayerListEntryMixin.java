package revivedevelopment.reviveclient.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PlayerListEntry.class})
public abstract class PlayerListEntryMixin {
   @Shadow
   public abstract GameProfile method_2966();

   @Inject(
      method = {"method_52810"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetTexture(CallbackInfoReturnable<SkinTextures> info) {
   }
}
