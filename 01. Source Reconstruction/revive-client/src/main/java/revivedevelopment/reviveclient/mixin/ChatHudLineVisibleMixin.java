package revivedevelopment.reviveclient.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.hud.ChatHudLine.Visible;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import revivedevelopment.reviveclient.mixininterface.IChatHudLineVisible;

@Mixin({Visible.class})
public abstract class ChatHudLineVisibleMixin implements IChatHudLineVisible {
   @Shadow
   @Final
   private OrderedText comp_896;
   @Unique
   private int id;
   @Unique
   private GameProfile sender;
   @Unique
   private boolean startOfEntry;

   @Override
   public String revive$getText() {
      StringBuilder sb = new StringBuilder();
      this.comp_896.accept((index, style, codePoint) -> {
         sb.appendCodePoint(codePoint);
         return true;
      });
      return sb.toString();
   }

   @Override
   public int revive$getId() {
      return this.id;
   }

   @Override
   public void revive$setId(int id) {
      this.id = id;
   }

   @Override
   public GameProfile revive$getSender() {
      return this.sender;
   }

   @Override
   public void revive$setSender(GameProfile profile) {
      this.sender = profile;
   }

   @Override
   public boolean revive$isStartOfEntry() {
      return this.startOfEntry;
   }

   @Override
   public void revive$setStartOfEntry(boolean start) {
      this.startOfEntry = start;
   }
}
