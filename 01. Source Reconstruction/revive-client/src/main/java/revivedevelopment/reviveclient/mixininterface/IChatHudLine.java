package revivedevelopment.reviveclient.mixininterface;

import com.mojang.authlib.GameProfile;

public interface IChatHudLine {
   String revive$getText();

   int revive$getId();

   void revive$setId(int var1);

   GameProfile revive$getSender();

   void revive$setSender(GameProfile var1);
}
