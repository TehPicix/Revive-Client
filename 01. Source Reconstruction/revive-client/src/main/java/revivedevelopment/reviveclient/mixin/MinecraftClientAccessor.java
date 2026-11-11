package revivedevelopment.reviveclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.resource.ResourceReloadLogger;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.texture.PlayerSkinProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({MinecraftClient.class})
public interface MinecraftClientAccessor {
   @Accessor("field_1738")
   static int getFps() {
      return 0;
   }

   @Mutable
   @Accessor("field_1726")
   void setSession(Session var1);

   @Accessor("field_33697")
   ResourceReloadLogger getResourceReloadLogger();

   @Accessor("field_1771")
   int getAttackCooldown();

   @Accessor("field_1771")
   void setAttackCooldown(int var1);

   @Invoker("method_1536")
   boolean leftClick();

   @Mutable
   @Accessor("field_39068")
   void setProfileKeys(ProfileKeys var1);

   @Mutable
   @Accessor("field_26902")
   void setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("field_1707")
   void setSkinProvider(PlayerSkinProvider var1);

   @Mutable
   @Accessor("field_26842")
   void setSocialInteractionsManager(SocialInteractionsManager var1);

   @Mutable
   @Accessor("field_39492")
   void setAbuseReportContext(AbuseReportContext var1);

   @Mutable
   @Accessor("field_45899")
   void setGameProfileFuture(CompletableFuture<ProfileResult> var1);
}
