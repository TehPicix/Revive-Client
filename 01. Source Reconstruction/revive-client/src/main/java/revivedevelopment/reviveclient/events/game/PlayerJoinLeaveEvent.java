package revivedevelopment.reviveclient.events.game;

import net.minecraft.client.network.PlayerListEntry;

public class PlayerJoinLeaveEvent {
   public static class Join {
      private static final PlayerJoinLeaveEvent.Join INSTANCE = new PlayerJoinLeaveEvent.Join();
      public PlayerListEntry entry;

      public static PlayerJoinLeaveEvent.Join get(PlayerListEntry entry) {
         INSTANCE.entry = entry;
         return INSTANCE;
      }
   }

   public static class Leave {
      private static final PlayerJoinLeaveEvent.Leave INSTANCE = new PlayerJoinLeaveEvent.Leave();
      public PlayerListEntry entry;

      public static PlayerJoinLeaveEvent.Leave get(PlayerListEntry entry) {
         INSTANCE.entry = entry;
         return INSTANCE;
      }
   }
}
