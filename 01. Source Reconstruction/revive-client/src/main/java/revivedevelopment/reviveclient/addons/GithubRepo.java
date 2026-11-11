package revivedevelopment.reviveclient.addons;

import javax.annotation.Nullable;
import revivedevelopment.reviveclient.utils.network.Http;

public record GithubRepo(String owner, String name, String branch, @Nullable String accessToken) {
   public GithubRepo(String owner, String name, @Nullable String accessToken) {
      this(owner, name, "master", accessToken);
   }

   public GithubRepo(String owner, String name) {
      this(owner, name, "master", null);
   }

   public String getOwnerName() {
      return this.owner + "/" + this.name;
   }

   public void authenticate(Http.Request request) {
      if (this.accessToken != null && !this.accessToken.isBlank()) {
         request.bearer(this.accessToken);
      } else {
         String personalAuthToken = System.getenv("revive.github.authorization");
         if (personalAuthToken != null && !personalAuthToken.isBlank()) {
            request.bearer(personalAuthToken);
         }
      }
   }
}
