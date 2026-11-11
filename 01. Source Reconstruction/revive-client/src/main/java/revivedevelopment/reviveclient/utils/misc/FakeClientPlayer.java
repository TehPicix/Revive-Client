package revivedevelopment.reviveclient.utils.misc;

import com.mojang.authlib.GameProfile;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.network.PlayerListEntry;
import revivedevelopment.reviveclient.ReviveClient;

public class FakeClientPlayer {
   private static PlayerListEntry playerListEntry;
   private static UUID lastId;

   private FakeClientPlayer() {
   }

   public static PlayerListEntry getPlayerListEntry() {
      UUID id = ReviveClient.mc.getSession().getUuidOrNull();
      if (playerListEntry == null || !Objects.equals(id, lastId)) {
         playerListEntry = new PlayerListEntry(new GameProfile(id, ReviveClient.mc.getSession().getUsername()), false);
         lastId = id;
      }

      return playerListEntry;
   }
}
