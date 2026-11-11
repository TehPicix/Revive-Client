package revivedevelopment.reviveclient.addons;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import revivedevelopment.reviveclient.ReviveClient;

public class AddonManager {
   public static final List<ReviveAddon> ADDONS = new ArrayList<>();

   public static void init() {
      ReviveClient.ADDON = new ReviveAddon() {
         @Override
         public void onInitialize() {
         }

         @Override
         public String getPackage() {
            return "revivedevelopment.reviveclient";
         }

         @Override
         public String getWebsite() {
            return "https://reviveclient.com";
         }

         @Override
         public GithubRepo getRepo() {
            return new GithubRepo("ReviveDevelopment", "revive-client");
         }

         @Override
         public String getCommit() {
            String commit = ReviveClient.MOD_META.getCustomValue("revive-client:commit").getAsString();
            return commit.isEmpty() ? null : commit;
         }
      };
      ModMetadata metadata = ((ModContainer)FabricLoader.getInstance().getModContainer("revive-client").get()).getMetadata();
      ReviveClient.ADDON.name = metadata.getName();
      ReviveClient.ADDON.authors = new String[metadata.getAuthors().size()];
      if (metadata.containsCustomValue("revive-client:color")) {
         ReviveClient.ADDON.color.parse(metadata.getCustomValue("revive-client:color").getAsString());
      }

      int i = 0;

      for (Person author : metadata.getAuthors()) {
         ReviveClient.ADDON.authors[i++] = author.getName();
      }

      ADDONS.add(ReviveClient.ADDON);

      for (EntrypointContainer<ReviveAddon> entrypoint : FabricLoader.getInstance().getEntrypointContainers("revive", ReviveAddon.class)) {
         ModMetadata metadatax = entrypoint.getProvider().getMetadata();

         ReviveAddon addon;
         try {
            addon = (ReviveAddon)entrypoint.getEntrypoint();
         } catch (Throwable var7) {
            throw new RuntimeException("Exception during addon init \"%s\".".formatted(metadatax.getName()), var7);
         }

         addon.name = metadatax.getName();
         if (metadatax.getAuthors().isEmpty()) {
            throw new RuntimeException(
               "Addon \"%s\" requires at least 1 author to be defined in it's fabric.mod.json. See https://fabricmc.net/wiki/documentation:fabric_mod_json_spec"
                  .formatted(addon.name)
            );
         }

         addon.authors = new String[metadatax.getAuthors().size()];
         if (metadatax.containsCustomValue("revive-client:color")) {
            addon.color.parse(metadatax.getCustomValue("revive-client:color").getAsString());
         }

         int ix = 0;

         for (Person author : metadatax.getAuthors()) {
            addon.authors[ix++] = author.getName();
         }

         ADDONS.add(addon);
      }
   }
}
