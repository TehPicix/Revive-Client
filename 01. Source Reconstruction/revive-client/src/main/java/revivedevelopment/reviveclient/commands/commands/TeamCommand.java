package revivedevelopment.reviveclient.commands.commands;

import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.commands.Command;
import revivedevelopment.reviveclient.systems.managers.TeamSyncManager;
import revivedevelopment.reviveclient.systems.managers.team.TeamInfo;

public class TeamCommand extends Command {
   public TeamCommand() {
      super("team", "Manages your Team Sync team.");
   }

   @Override
   public void execute(String[] args) {
      String sub = args.length == 0 ? "list" : args[0].toLowerCase();
      String name = args.length >= 2 ? args[1] : null;
      if (sub.equals("status")) {
         this.status();
      } else {
         TeamSyncManager team = ReviveClient.TEAM;
         if (team != null && team.isConnected()) {
            switch (sub) {
               case "invite":
               case "inv":
               case "add":
                  if (name == null) {
                     this.error("Usage: .team invite <name>", new Object[0]);
                     return;
                  }

                  team.sendTeamOp("invite", name);
                  break;
               case "accept":
               case "join":
                  team.sendTeamOp("accept", name);
                  break;
               case "deny":
               case "decline":
                  team.sendTeamOp("deny", name);
                  break;
               case "leave":
               case "quit":
                  team.sendTeamOp("leave", null);
                  break;
               case "kick":
               case "remove":
                  if (name == null) {
                     this.error("Usage: .team kick <name>", new Object[0]);
                     return;
                  }

                  team.sendTeamOp("kick", name);
                  break;
               case "list":
               case "ls":
                  this.list(team);
                  break;
               case "map":
                  if (team.getTeam() == null) {
                     this.error("You need to be in a team to open the combat map.", new Object[0]);
                     return;
                  }

                  team.requestMapLink();
                  this.info("Requesting a combat map link...", new Object[0]);
                  break;
               default:
                  this.error("Usage: .team <invite|accept|deny|leave|kick|list|map|status> [name]", new Object[0]);
            }
         } else {
            this.error("Not connected to a team server. Enable the team-sync module (.team status for details).", new Object[0]);
         }
      }
   }

   private void list(TeamSyncManager team) {
      TeamInfo info = team.getTeam();
      if (info == null) {
         this.info("You're not in a team. Invite someone with .team invite <name>.", new Object[0]);
      } else {
         int online = 0;
         StringBuilder sb = new StringBuilder();

         for (TeamInfo.Member m : info.members) {
            if (m.online) {
               online++;
            }

            if (sb.length() > 0) {
               sb.append(", ");
            }

            sb.append(m.name);
            if (m.name.equalsIgnoreCase(info.leader) || m.uuid != null && m.uuid.toString().equalsIgnoreCase(info.leader)) {
               sb.append(" (leader)");
            }

            if (!m.online) {
               sb.append(" [offline]");
            }
         }

         this.info("--- Team %s (%d/%d online) ---", new Object[]{info.name, online, info.members.size()});
         this.info("%s", new Object[]{sb.toString()});
      }

      for (TeamInfo.Invite i : team.getInvites()) {
         this.info("Pending invite from %s (team %s): .team accept %s", new Object[]{i.from, i.team, i.from});
      }
   }

   private void status() {
      TeamSyncManager team = ReviveClient.TEAM;
      if (team != null && team.isEnabled()) {
         String url = team.getServerUrl();
         if (!team.isConnected()) {
            String err = team.getLastError();
            this.warning("Connecting to %s...%s", new Object[]{url, err != null ? " (last error: " + err + ")" : ""});
         } else {
            TeamInfo info = team.getTeam();
            long ping = team.getPingMillis();
            this.info(
               "Connected to %s as %s%s, ping %s.",
               new Object[]{
                  url,
                  team.getSelfName() != null ? team.getSelfName() : "?",
                  team.getReviveAccount() != null ? " (Revive: " + team.getReviveAccount() + ")" : "",
                  ping >= 0L ? ping + " ms" : "?"
               }
            );
            this.info(
               "Team: %s, %d mate(s) online, %d remote player(s), %d logout spot(s).",
               new Object[]{info != null ? info.name : "none", team.getMates().size(), team.getRemotePlayers().size(), team.getLogouts().size()}
            );
         }
      } else {
         this.info("Team Sync is off. Enable the team-sync module to connect.", new Object[0]);
      }
   }
}
