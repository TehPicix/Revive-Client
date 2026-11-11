package revivedevelopment.reviveclient.gui.screens;

import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import net.minecraft.util.Util;
import revivedevelopment.reviveclient.addons.GithubRepo;
import revivedevelopment.reviveclient.addons.ReviveAddon;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.utils.network.Http;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;

public class CommitsScreen extends WindowScreen {
   private final ReviveAddon addon;
   private CommitsScreen.Commit[] commits;
   private int statusCode;

   public CommitsScreen(GuiTheme theme, ReviveAddon addon) {
      super(theme, "Commits for " + addon.name);
      this.addon = addon;
      this.locked = true;
      this.lockedAllowClose = true;
      ReviveExecutor.execute(
         () -> {
            GithubRepo repo = addon.getRepo();
            if (addon.getCommit() != null && !addon.getCommit().equals("${commit}")) {
               Http.Request request = Http.get(
                  String.format("https://api.github.com/repos/%s/compare/%s...%s", repo.getOwnerName(), addon.getCommit(), repo.branch())
               );
               repo.authenticate(request);
               HttpResponse<CommitsScreen.Response> res = request.sendJsonResponse(CommitsScreen.Response.class);
               if (res.statusCode() == 200) {
                  this.commits = res.body().commits;
                  this.taskAfterRender = this::populateCommits;
               } else {
                  this.statusCode = res.statusCode();
                  this.taskAfterRender = this::populateError;
               }
            } else {
               this.statusCode = 404;
               this.taskAfterRender = this::populateError;
            }
         }
      );
   }

   @Override
   public void initWidgets() {
   }

   private void populateHeader(String headerMessage) {
      WHorizontalList l = this.add(this.theme.horizontalList()).expandX().widget();
      l.add(this.theme.label(headerMessage)).expandX();
      String website = this.addon.getWebsite();
      if (website != null) {
         l.add(this.theme.button("Website")).widget().action = () -> Util.getOperatingSystem().open(website);
      }

      l.add(this.theme.button("GitHub")).widget().action = () -> {
         GithubRepo repo = this.addon.getRepo();
         Util.getOperatingSystem().open(String.format("https://github.com/%s/tree/%s", repo.getOwnerName(), repo.branch()));
      };
   }

   private void populateError() {
      String errorMessage = switch (this.statusCode) {
         case 400 -> "Connection dropped";
         case 401 -> "Unauthorized";
         default -> "Error Code: " + this.statusCode;
         case 403 -> "Rate-limited";
         case 404 -> "Invalid commit hash";
      };
      this.populateHeader("There was an error fetching commits: " + errorMessage);
      if (this.statusCode == 401) {
         this.add(this.theme.horizontalSeparator()).padVertical(this.theme.scale(8.0)).expandX();
         WHorizontalList l = this.add(this.theme.horizontalList()).expandX().widget();
         l.add(this.theme.label("Consider using an authentication token: ")).expandX();
         l.add(this.theme.button("Authorization Guide")).widget().action = () -> Util.getOperatingSystem()
            .open("https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens");
      }

      this.locked = false;
   }

   private void populateCommits() {
      String text = "There are %d new commits";
      if (this.commits.length == 1) {
         text = "There is %d new commit";
      }

      this.populateHeader(String.format(text, this.commits.length));
      if (this.commits.length > 0) {
         this.add(this.theme.horizontalSeparator()).padVertical(this.theme.scale(8.0)).expandX();
         WTable t = this.add(this.theme.table()).expandX().widget();
         t.horizontalSpacing = 0.0;

         for (CommitsScreen.Commit commit : this.commits) {
            String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
               .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(commit.commit.committer.date));
            t.add(this.theme.label(date)).top().right().widget().color = this.theme.textSecondaryColor();
            t.add(this.theme.label(getMessage(commit))).widget().action = () -> Util.getOperatingSystem()
               .open(String.format("https://github.com/%s/commit/%s", this.addon.getRepo().getOwnerName(), commit.sha));
            t.row();
         }
      }

      this.locked = false;
   }

   private static String getMessage(CommitsScreen.Commit commit) {
      StringBuilder sb = new StringBuilder(" - ");
      String message = commit.commit.message;

      for (int i = 0; i < message.length(); i++) {
         if (i >= 80) {
            sb.append("...");
            break;
         }

         char c = message.charAt(i);
         if (c == '\n') {
            sb.append("...");
            break;
         }

         sb.append(c);
      }

      return sb.toString();
   }

   private static class Commit {
      public String sha;
      public CommitsScreen.CommitInner commit;
   }

   private static class CommitInner {
      public CommitsScreen.Committer committer;
      public String message;
   }

   private static class Committer {
      public String date;
   }

   private static class Response {
      public CommitsScreen.Commit[] commits;
   }
}
