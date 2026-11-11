package revivedevelopment.reviveclient.systems.modules.player;

import java.util.UUID;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.managers.TeamSyncManager;
import revivedevelopment.reviveclient.systems.managers.team.TeamInfo;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class TeamSync extends Module {
   private final SettingGroup sgServer = this.settings.createGroup("Server");
   private final SettingGroup sgShare = this.settings.createGroup("Sharing");
   private final SettingGroup sgMisc = this.settings.getDefaultGroup();
   public final Setting<String> serverUrl = this.sgServer
      .add(
         new StringSetting.Builder()
            .name("server-url")
            .description("Team server address. Accepts host, host:port, ws:// or wss:// URLs.")
            .defaultValue("wss://team.revive-client.com/ws")
            .onChanged(v -> this.configChanged())
            .build()
      );
   public final Setting<TeamSync.AuthMode> authMode = this.sgServer
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("auth-mode"))
                     .description("Revive Account for the official server, Server Key for a self-hosted one."))
                  .defaultValue(TeamSync.AuthMode.ReviveAccount))
               .onChanged(v -> this.configChanged()))
            .build()
      );
   public final Setting<String> key = this.sgServer
      .add(
         new StringSetting.Builder()
            .name("key")
            .description("Password of a self-hosted team server (SERVER_KEY).")
            .defaultValue("")
            .visible(() -> this.authMode.get() == TeamSync.AuthMode.ServerKey)
            .onChanged(v -> this.configChanged())
            .build()
      );
   public final Setting<String> token = this.sgServer
      .add(
         new StringSetting.Builder()
            .name("token")
            .description("Optional Revive access token override. Empty = read revive-token.txt written by the launcher.")
            .defaultValue("")
            .visible(() -> this.authMode.get() == TeamSync.AuthMode.ReviveAccount)
            .onChanged(v -> this.configChanged())
            .build()
      );
   public final Setting<Boolean> sharePosition = this.sgShare
      .add(new BoolSetting.Builder().name("share-position").description("Send your own position / health to teammates.").defaultValue(true).build());
   public final Setting<Boolean> shareSeenPlayers = this.sgShare
      .add(
         new BoolSetting.Builder().name("share-seen-players").description("Send the players in your render distance to teammates.").defaultValue(true).build()
      );
   public final Setting<Boolean> sharePops = this.sgShare
      .add(
         new BoolSetting.Builder()
            .name("share-pops")
            .description("Share observed totem pops, deaths and regears for the team pop counter.")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> shareLogouts = this.sgShare
      .add(new BoolSetting.Builder().name("share-logouts").description("Share logout spots of players in your render distance.").defaultValue(true).build());
   public final Setting<Boolean> shareSupplies = this.sgShare
      .add(
         new BoolSetting.Builder()
            .name("share-supplies")
            .description("Send your crystal / totem / pearl / obsidian / gapple / xp bottle counts to teammates (team-supplies HUD).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> shareTerrain = this.sgShare
      .add(
         new BoolSetting.Builder()
            .name("share-terrain")
            .description("Upload map-style colours of the chunks around you for the team's browser combat map (.team map).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> autoFriend = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("auto-friend")
            .description("Add every teammate to your friends list (never removes friends).")
            .defaultValue(true)
            .build()
      );
   public final Setting<Boolean> chatNotices = this.sgMisc
      .add(
         new BoolSetting.Builder().name("chat-notices").description("Show team notices (joins, leaves, connection status) in chat.").defaultValue(true).build()
      );
   public final Setting<Boolean> debug = this.sgMisc
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Log connection events to the game log (credentials are never logged).")
            .defaultValue(false)
            .build()
      );

   public TeamSync() {
      super(Categories.Player, "team-sync", "Shares positions, seen players, pops, logout spots, supplies and terrain with your team through a team server.");
   }

   private void configChanged() {
      if (ReviveClient.TEAM != null) {
         ReviveClient.TEAM.markConfigDirty();
      }
   }

   public TeamSyncManager.ConnectConfig buildConfig() {
      UUID uuid = null;
      String name = "";
      if (this.mc.getSession() != null) {
         uuid = this.mc.getSession().getUuidOrNull();
         name = this.mc.getSession().getUsername();
      }

      if (uuid == null && this.mc.player != null) {
         uuid = this.mc.player.getUuid();
      }

      return new TeamSyncManager.ConnectConfig(
         TeamSyncManager.normalizeUrl(this.serverUrl.get()),
         this.authMode.get() == TeamSync.AuthMode.ServerKey ? TeamSyncManager.AuthMode.KEY : TeamSyncManager.AuthMode.REVIVE,
         this.key.get(),
         this.token.get(),
         uuid,
         name
      );
   }

   @Override
   public String getInfoString() {
      TeamSyncManager team = ReviveClient.TEAM;
      if (team == null) {
         return null;
      } else if (!team.isConnected()) {
         return "connecting";
      } else {
         TeamInfo info = team.getTeam();
         return info != null ? info.name : "no team";
      }
   }

   public static enum AuthMode {
      ReviveAccount("Revive Account"),
      ServerKey("Server Key");

      private final String title;

      private AuthMode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
