package revivedevelopment.loader.api;

import java.io.InputStream;
import java.util.Properties;

public final class Meta {
   private static final Properties BUNDLED = loadBundled();
   public static final String DEFAULT_REMOTE_API_HOST = "https://revive-client.com";
   public static final String DEFAULT_BUILD_PUBLIC_KEY_HEX = "0000000000000000000000000000000000000000000000000000000000000000";
   public static final String USER_AGENT = "revive-loader/1.0";

   private static Properties loadBundled() {
      Properties properties = new Properties();

      try (InputStream in = Meta.class.getResourceAsStream("/revive-loader.properties")) {
         if (in != null) {
            properties.load(in);
         }
      } catch (Exception ignored) {
      }

      return properties;
   }

   private Meta() {
   }

   public static String remoteApiHost() {
      return resolve("revive.loader.host", "REVIVE_LOADER_HOST", "host", "https://revive-client.com");
   }

   public static String buildPublicKeyHex() {
      return resolve("revive.loader.pubA", "REVIVE_LOADER_PUBA", "pubA", "0000000000000000000000000000000000000000000000000000000000000000");
   }

   private static String resolve(String systemProperty, String envVar, String bundledKey, String fallback) {
      String value = System.getProperty(systemProperty);
      if (value == null || value.isBlank()) {
         value = System.getenv(envVar);
      }

      if (value == null || value.isBlank()) {
         value = BUNDLED.getProperty(bundledKey);
      }

      return value != null && !value.isBlank() ? value : fallback;
   }
}
