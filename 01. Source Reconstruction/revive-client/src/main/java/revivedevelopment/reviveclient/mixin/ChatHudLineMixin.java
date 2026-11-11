package revivedevelopment.reviveclient.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import revivedevelopment.reviveclient.mixininterface.IChatHudLine;

@Mixin({ChatHudLine.class})
public abstract class ChatHudLineMixin implements IChatHudLine {
   @Shadow
   @Final
   private Text comp_893;
   @Unique
   private int id;
   @Unique
   private GameProfile sender;

   @Override
   public String revive$getText() {
      return this.comp_893.getString();
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
}
