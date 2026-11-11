package revivedevelopment.reviveclient.commands.commands;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.commands.Command;
import revivedevelopment.reviveclient.systems.friends.Friend;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;

public class FriendCommand extends Command {
   public FriendCommand() {
      super("friend", "Manages your friends list.", "friends", "f");
   }

   @Override
   public void execute(String[] args) {
      if (args.length == 0) {
         this.list();
      } else {
         String var2 = args[0].toLowerCase();
         switch (var2) {
            case "add":
               this.add(args.length >= 2 ? args[1] : this.lookedAtPlayerName());
               break;
            case "del":
            case "delete":
            case "remove":
            case "rm":
               if (args.length < 2) {
                  this.error("Usage: .friend del <name>", new Object[0]);
                  return;
               }

               this.remove(args[1]);
               break;
            case "clear":
               this.clear();
               break;
            case "list":
            case "ls":
               this.list();
               break;
            default:
               this.add(args[0]);
         }
      }
   }

   private void add(String name) {
      if (name == null || name.isEmpty()) {
         this.error("Usage: .friend add <name> (or look at a player and run .friend add).", new Object[0]);
      } else if (name.contains(" ")) {
         this.error("A friend name can't contain spaces.", new Object[0]);
      } else if (Friends.get().get(name) != null) {
         this.warning("%s is already your friend.", new Object[]{name});
      } else {
         Friend friend = this.onlineFriend(name);
         if (friend == null) {
            friend = new Friend(name);
         }

         if (Friends.get().add(friend)) {
            ReviveExecutor.execute(friend::updateInfo);
            this.info("Added %s to friends.", new Object[]{friend.getName()});
         } else {
            this.error("Couldn't add %s.", new Object[]{name});
         }
      }
   }

   private Friend onlineFriend(String name) {
      if (ReviveClient.mc != null && ReviveClient.mc.getNetworkHandler() != null) {
         for (PlayerListEntry entry : ReviveClient.mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().name().equalsIgnoreCase(name)) {
               return new Friend(entry.getProfile().name(), entry.getProfile().id());
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void remove(String name) {
      Friend friend = Friends.get().get(name);
      if (friend == null) {
         this.error("%s isn't your friend.", new Object[]{name});
      } else {
         Friends.get().remove(friend);
         this.info("Removed %s from friends.", new Object[]{friend.getName()});
      }
   }

   private void clear() {
      int count = Friends.get().count();
      if (count == 0) {
         this.info("You have no friends to clear.", new Object[0]);
      } else {
         for (Friend friend : this.friendsSnapshot()) {
            Friends.get().remove(friend);
         }

         this.info("Cleared %d friend(s).", new Object[]{count});
      }
   }

   private void list() {
      if (Friends.get().isEmpty()) {
         this.info("You have no friends. Add one with .friend add <name>.", new Object[0]);
      } else {
         StringBuilder sb = new StringBuilder();

         for (Friend friend : Friends.get()) {
            if (sb.length() > 0) {
               sb.append(", ");
            }

            sb.append(friend.getName());
         }

         this.info("--- Friends (%d) ---", new Object[]{Friends.get().count()});
         this.info("%s", new Object[]{sb.toString()});
      }
   }

   private String lookedAtPlayerName() {
      return (ReviveClient.mc != null ? ReviveClient.mc.targetedEntity : null) instanceof PlayerEntity player ? player.getName().getString() : null;
   }

   private List<Friend> friendsSnapshot() {
      List<Friend> out = new ArrayList<>();

      for (Friend friend : Friends.get()) {
         out.add(friend);
      }

      return out;
   }
}
