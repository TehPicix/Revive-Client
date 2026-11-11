package revivedevelopment.reviveclient.commands.commands;

import java.util.List;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.commands.Command;
import revivedevelopment.reviveclient.systems.emotes.EmoteLibrary;
import revivedevelopment.reviveclient.systems.emotes.EmoteManager;
import revivedevelopment.reviveclient.systems.managers.EmoteSyncManager;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Emotes;

public class EmoteCommand extends Command {
   public EmoteCommand() {
      super("emote", "Plays emotes on your player.", "e");
   }

   @Override
   public void execute(String[] args) {
      if (args.length == 0) {
         this.list();
         this.info("Usage: .emote <name|stop|list|reload|status>", new Object[0]);
      } else {
         String sub = args[0].toLowerCase();
         switch (sub) {
            case "list":
            case "ls":
               this.list();
               break;
            case "status":
               this.status();
               break;
            case "reload":
               int n = EmoteLibrary.reload();
               this.info("Reloaded (highlight)%d(default) emotes.", new Object[]{n});
               if (ReviveClient.EMOTES != null && ReviveClient.EMOTES.isConnected()) {
                  ReviveClient.EMOTES.requestLibrarySync();
                  this.info("Re-checking the server library...", new Object[0]);
               }
               break;
            case "stop":
               Emotes module = this.module();
               if (module != null) {
                  module.stop();
               } else {
                  EmoteManager.stopLocal();
               }

               this.noticeIfOff();
               break;
            default:
               this.play(String.join(" ", args));
         }
      }
   }

   private void play(String name) {
      if (ReviveClient.mc.player == null) {
         this.error("Not in a world.", new Object[0]);
      } else if (!EmoteLibrary.has(name)) {
         this.error("Unknown emote (highlight)%s(default). Use .emote list.", new Object[]{name});
      } else {
         Emotes module = this.module();
         boolean ok = module != null ? module.play(name) : EmoteManager.playLocal(name) != null;
         if (!ok) {
            this.error("Could not play emote (highlight)%s(default).", new Object[]{name});
         } else {
            this.noticeIfOff();
         }
      }
   }

   private void list() {
      List<String> names = EmoteLibrary.names();
      if (names.isEmpty()) {
         this.info("No emotes loaded.", new Object[0]);
      } else {
         this.info("Emotes (highlight)(%d)(default): %s", new Object[]{names.size(), String.join(", ", names)});
      }
   }

   private void status() {
      EmoteSyncManager emotes = ReviveClient.EMOTES;
      if (emotes != null && emotes.isEnabled()) {
         String url = emotes.getServerUrl();
         if (!emotes.isConnected()) {
            String err = emotes.getLastError();
            this.warning("Connecting to %s...%s", new Object[]{url, err != null ? " (last error: " + err + ")" : ""});
         } else {
            long ping = emotes.getPingMillis();
            this.info(
               "Connected to %s as %s%s, ping %s.",
               new Object[]{
                  url,
                  emotes.getSelfName() != null ? emotes.getSelfName() : "?",
                  emotes.getReviveAccount() != null ? " (Revive: " + emotes.getReviveAccount() + ")" : "",
                  ping >= 0L ? ping + " ms" : "?"
               }
            );
            String lib = emotes.getLibraryVersion();
            String synced = emotes.getSyncedVersion();
            this.info(
               "Library: %d emotes, server version %s%s. %d player(s) emoting right now.",
               new Object[]{
                  EmoteLibrary.count(), lib != null ? lib : "?", lib != null && !lib.equals(synced) ? " (downloading...)" : "", emotes.getRemoteEmotes().size()
               }
            );
         }
      } else {
         this.info("Emote sync is off. Enable the emotes module so others can see your emotes.", new Object[0]);
      }
   }

   private void noticeIfOff() {
      Emotes module = this.module();
      if (module == null || !module.isActive()) {
         this.info("The emotes module is off - the emote plays for you only.", new Object[0]);
      }
   }

   private Emotes module() {
      Modules modules = Modules.get();
      return modules == null ? null : modules.get(Emotes.class);
   }
}
