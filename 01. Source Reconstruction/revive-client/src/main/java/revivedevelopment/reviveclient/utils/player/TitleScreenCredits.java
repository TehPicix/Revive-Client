package revivedevelopment.reviveclient.utils.player;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.addons.AddonManager;
import revivedevelopment.reviveclient.addons.GithubRepo;
import revivedevelopment.reviveclient.addons.ReviveAddon;
import revivedevelopment.reviveclient.gui.GuiThemes;
import revivedevelopment.reviveclient.gui.screens.CommitsScreen;
import revivedevelopment.reviveclient.mixininterface.IText;
import revivedevelopment.reviveclient.utils.network.Http;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;
import revivedevelopment.reviveclient.utils.render.ReviveToast;

public class TitleScreenCredits {
   private static final List<TitleScreenCredits.Credit> credits = new ArrayList<>();

   private TitleScreenCredits() {
   }

   private static void init() {
      for (ReviveAddon addon : AddonManager.ADDONS) {
         add(addon);
      }

      credits.sort(Comparator.comparingInt(value -> value.addon == ReviveClient.ADDON ? Integer.MIN_VALUE : -ReviveClient.mc.textRenderer.getWidth(value.text)));
      ReviveExecutor.execute(
         () -> {
            for (TitleScreenCredits.Credit credit : credits) {
               if (credit.addon.getRepo() != null && credit.addon.getCommit() != null) {
                  GithubRepo repo = credit.addon.getRepo();
                  Http.Request request = Http.get("https://api.github.com/repos/%s/branches/%s".formatted(repo.getOwnerName(), repo.branch()));
                  request.exceptionHandler(e -> ReviveClient.LOG.error("Could not fetch repository information for addon '{}'.", credit.addon.name, e));
                  repo.authenticate(request);
                  HttpResponse<TitleScreenCredits.Response> res = request.sendJsonResponse(TitleScreenCredits.Response.class);
                  switch (res.statusCode()) {
                     case 200:
                        if (!credit.addon.getCommit().equals(res.body().commit.sha)) {
                           synchronized (credit.text) {
                              credit.text.append(Text.literal("*").formatted(Formatting.RED));
                              ((IText)(Object)credit.text).revive$invalidateCache();
                           }
                        }
                        break;
                     case 401:
                        String message = "Invalid authentication token for repository '%s'".formatted(repo.getOwnerName());
                        ReviveClient.mc.getToastManager().add(new ReviveToast(Items.BARRIER, "GitHub: Unauthorized", message));
                        ReviveClient.LOG.warn(message);
                        if (System.getenv("revive.github.authorization") == null) {
                           ReviveClient.LOG.info("Consider setting an authorization token with the 'revive.github.authorization' environment variable.");
                           ReviveClient.LOG
                              .info("See: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens");
                        }
                        break;
                     case 403:
                        ReviveClient.LOG.warn("Could not fetch updates for addon '{}': Rate-limited by GitHub.", credit.addon.name);
                        break;
                     case 404:
                        ReviveClient.LOG
                           .warn("Could not fetch updates for addon '{}': GitHub repository '{}' not found.", credit.addon.name, repo.getOwnerName());
                  }
               }
            }
         }
      );
   }

   private static void add(ReviveAddon addon) {
      TitleScreenCredits.Credit credit = new TitleScreenCredits.Credit(addon);
      credit.text.append(Text.literal(addon.name).styled(style -> style.withColor(addon.color.getPacked())));
      credit.text.append(Text.literal(" by ").formatted(Formatting.GRAY));

      for (int i = 0; i < addon.authors.length; i++) {
         if (i > 0) {
            credit.text.append(Text.literal(i == addon.authors.length - 1 ? " & " : ", ").formatted(Formatting.GRAY));
         }

         credit.text.append(Text.literal(addon.authors[i]).formatted(Formatting.WHITE));
      }

      credits.add(credit);
   }

   public static void render(DrawContext context) {
      if (credits.isEmpty()) {
         init();
      }

      int y = 3;

      for (TitleScreenCredits.Credit credit : credits) {
         synchronized (credit.text) {
            int x = ReviveClient.mc.currentScreen.width - 3 - ReviveClient.mc.textRenderer.getWidth(credit.text);
            context.drawTextWithShadow(ReviveClient.mc.textRenderer, credit.text, x, y, -1);
         }

         y += 9 + 2;
      }
   }

   public static boolean onClicked(double mouseX, double mouseY) {
      int y = 3;

      for (TitleScreenCredits.Credit credit : credits) {
         int width;
         synchronized (credit.text) {
            width = ReviveClient.mc.textRenderer.getWidth(credit.text);
         }

         int x = ReviveClient.mc.currentScreen.width - 3 - width;
         if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 9 + 2 && credit.addon.getRepo() != null && credit.addon.getCommit() != null) {
            ReviveClient.mc.setScreen(new CommitsScreen(GuiThemes.get(), credit.addon));
            return true;
         }

         y += 9 + 2;
      }

      return false;
   }

   private static class Commit {
      public String sha;
   }

   private static class Credit {
      public final ReviveAddon addon;
      public final MutableText text = Text.empty();

      public Credit(ReviveAddon addon) {
         this.addon = addon;
      }
   }

   private static class Response {
      public TitleScreenCredits.Commit commit;
   }
}
