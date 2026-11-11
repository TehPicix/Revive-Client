package revivedevelopment.reviveclient.systems.emotes;

import java.util.UUID;
import revivedevelopment.reviveclient.ReviveClient;

public final class EmoteAutoPlay {
   private static boolean driving;
   private static UUID drivenEmote;

   private EmoteAutoPlay() {
   }

   public static void drive(boolean wanted, String emoteName) {
      if (ReviveClient.mc.player == null) {
         driving = false;
         drivenEmote = null;
      } else {
         if (wanted) {
            UUID playing = EmoteManager.localPlaying();
            if (driving && playing != null && playing.equals(drivenEmote)) {
               return;
            }

            UUID uuid = EmoteManager.playLocal(emoteName);
            if (uuid == null) {
               return;
            }

            driving = true;
            drivenEmote = uuid;
            if (ReviveClient.EMOTES != null) {
               ReviveClient.EMOTES.onLocalPlay(uuid);
            }
         } else if (driving) {
            driving = false;
            UUID playingx = EmoteManager.localPlaying();
            if (playingx != null && playingx.equals(drivenEmote)) {
               EmoteManager.stopLocal();
               if (ReviveClient.EMOTES != null) {
                  ReviveClient.EMOTES.onLocalStop(playingx);
               }
            }

            drivenEmote = null;
         }
      }
   }

   public static boolean isDriving() {
      return driving && drivenEmote != null && drivenEmote.equals(EmoteManager.localPlaying());
   }

   public static boolean standsUpright(UUID player) {
      return driving && player != null && ReviveClient.mc.player != null && player.equals(ReviveClient.mc.player.getUuid());
   }
}
