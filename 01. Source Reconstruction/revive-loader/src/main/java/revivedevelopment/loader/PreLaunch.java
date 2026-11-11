package revivedevelopment.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import revivedevelopment.loader.api.Meta;
import revivedevelopment.loader.inject.ClientInjector;

public final class PreLaunch implements PreLaunchEntrypoint {
   private static final Logger LOGGER = LoggerFactory.getLogger("revive-loader");

   public void onPreLaunch() {
      String accessToken = resolveAccessToken();
      if (accessToken != null && !accessToken.isBlank()) {
         String minecraftVersion = minecraftVersion();
         boolean gateMode = "gate".equalsIgnoreCase(resolveMode());
         byte[] clientJar = null;

         try {
            LOGGER.info("Authenticating with the Revive auth server...");
            if (isBlank(System.getProperty("revive.loader.host"))) {
               System.setProperty("revive.loader.host", Meta.remoteApiHost());
            }

            LoaderCore core = new LoaderCore().withMinecraft(minecraftIdentity());
            if (gateMode) {
               core.verifyAccess(accessToken);
               LOGGER.info("Account verified (gate mode).");
            } else {
               clientJar = core.fetchClient(accessToken, minecraftVersion);
               LOGGER.info("Client authorised ({} bytes). Injecting...", clientJar.length);
               int classes = ClientInjector.inject(clientJar);
               LOGGER.info("Injected {} client classes.", classes);
            }
         } catch (LoaderCore.AuthException authException) {
            fail(authException.title != null ? authException.title : "Authorization failed", authException.getMessage(), authException);
         } catch (RuntimeException exception) {
            fail("Client error", "Could not start the client. Please try again.", exception);
         } finally {
            if (clientJar != null) {
               Arrays.fill(clientJar, (byte)0);
            }
         }
      } else {
         fail("No account credential", "Launch the client through the Revive launcher so it can log you in.", null);
      }
   }

   private static String resolveAccessToken() {
      String fromProperty = System.getProperty("revive.loader.token");
      if (fromProperty != null && !fromProperty.isBlank()) {
         return fromProperty.trim();
      } else {
         String fromEnv = System.getenv("REVIVE_ACCESS_TOKEN");
         if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
         } else {
            try {
               Path tokenFile = FabricLoader.getInstance().getGameDir().resolve("revive-token.txt");
               if (Files.isRegularFile(tokenFile)) {
                  String contents = Files.readString(tokenFile).trim();
                  if (!contents.isBlank()) {
                     return contents;
                  }
               }
            } catch (Exception ignored) {
            }

            return null;
         }
      }
   }

   private static String resolveMode() {
      String mode = System.getProperty("revive.loader.mode");
      if (mode == null || mode.isBlank()) {
         mode = System.getenv("REVIVE_LOADER_MODE");
      }

      return mode != null && !mode.isBlank() ? mode : "inject";
   }

   private static String findFlag(List<String> args, String flag) {
      for (int i = 0; i < args.size(); i++) {
         String arg = args.get(i);
         if (arg.equals(flag)) {
            return i + 1 < args.size() ? args.get(i + 1) : null;
         }

         String prefix = flag + "=";
         if (arg.startsWith(prefix)) {
            return arg.substring(prefix.length());
         }
      }

      return null;
   }

   private static LoaderCore.MinecraftIdentity minecraftIdentity() {
      String uuid = System.getProperty("revive.minecraft.uuid");
      String name = System.getProperty("revive.minecraft.name");
      String minecraftToken = null;

      try {
         String[] raw = FabricLoader.getInstance().getLaunchArguments(false);
         List<String> args = Arrays.asList(raw);
         if (isBlank(uuid)) {
            uuid = findFlag(args, "--uuid");
         }

         if (isBlank(name)) {
            name = findFlag(args, "--username");
         }

         minecraftToken = findFlag(args, "--accessToken");
         Arrays.fill(raw, null);
      } catch (RuntimeException ignored) {
      }

      if (!isBlank(uuid) && !isBlank(name)) {
         return new LoaderCore.MinecraftIdentity(uuid.trim(), name.trim(), minecraftToken);
      } else {
         LOGGER.warn("Could not determine the Minecraft account; capes will not be linked for this launch.");
         return LoaderCore.MinecraftIdentity.NONE;
      }
   }

   private static boolean isBlank(String value) {
      return value == null || value.isBlank();
   }

   private static String minecraftVersion() {
      return FabricLoader.getInstance()
         .getModContainer("minecraft")
         .map(container -> container.getMetadata().getVersion().getFriendlyString())
         .orElse("unknown");
   }

   private static void fail(String title, String message, Throwable cause) {
      LOGGER.error("Loader aborted: {} - {}", new Object[]{title, message, cause});
      throw new IllegalStateException(title + ": " + message, cause);
   }
}
