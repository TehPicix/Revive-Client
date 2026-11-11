package revivedevelopment.reviveclient.systems.emotes;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.PlayerEntity;
import revivedevelopment.reviveclient.ReviveClient;

public final class EmoteManager {
   private EmoteManager() {
   }

   public static boolean play(UUID player, UUID emoteUuid, float startTick) {
      if (player == null) {
         return false;
      } else {
         EmoteLibrary.Emote emote = EmoteLibrary.get(emoteUuid);
         if (emote == null) {
            return false;
         } else {
            EmoteEngine.get().play(player, emote.animation(), startTick);
            return true;
         }
      }
   }

   public static boolean isPlaying(UUID player, UUID emoteUuid) {
      if (player != null && emoteUuid != null) {
         EmoteAnimation playing = EmoteEngine.get().playing(player);
         return playing != null && emoteUuid.equals(playing.uuid());
      } else {
         return false;
      }
   }

   public static void stop(UUID player, @Nullable UUID emoteUuid) {
      if (player != null) {
         EmoteAnimation current = EmoteEngine.get().playing(player);
         if (emoteUuid == null || current == null || emoteUuid.equals(current.uuid())) {
            EmoteEngine.get().stop(player);
         }
      }
   }

   @Nullable
   public static UUID playLocal(String name) {
      PlayerEntity self = ReviveClient.mc.player;
      if (self != null && name != null) {
         EmoteLibrary.Emote emote = EmoteLibrary.byName(name);
         if (emote == null) {
            return null;
         } else {
            EmoteEngine.get().play(self.getUuid(), emote.animation(), 0.0F);
            return emote.uuid();
         }
      } else {
         return null;
      }
   }

   @Nullable
   public static UUID stopLocal() {
      PlayerEntity self = ReviveClient.mc.player;
      if (self == null) {
         return null;
      } else {
         EmoteAnimation current = EmoteEngine.get().playing(self.getUuid());
         EmoteEngine.get().stop(self.getUuid());
         return current == null ? null : current.uuid();
      }
   }

   @Nullable
   public static UUID localPlaying() {
      PlayerEntity self = ReviveClient.mc.player;
      if (self == null) {
         return null;
      } else {
         EmoteAnimation animation = EmoteEngine.get().playing(self.getUuid());
         return animation == null ? null : animation.uuid();
      }
   }

   public static float localTicks() {
      PlayerEntity self = ReviveClient.mc.player;
      return self == null ? 0.0F : Math.max(EmoteEngine.get().ticks(self.getUuid()), 0.0F);
   }

   public static void clearSession() {
      EmoteEngine.get().clear();
   }
}
