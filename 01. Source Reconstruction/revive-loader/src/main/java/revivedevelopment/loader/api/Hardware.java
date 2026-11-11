package revivedevelopment.loader.api;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import revivedevelopment.loader.crypto.Crypto;

public final class Hardware {
   private Hardware() {
   }

   public static String hwid() {
      StringBuilder material = new StringBuilder();
      material.append(firstMacAddress()).append('|');
      material.append(System.getProperty("os.name", "")).append('|');
      material.append(System.getProperty("os.arch", "")).append('|');
      material.append(System.getenv().getOrDefault("COMPUTERNAME", System.getenv().getOrDefault("HOSTNAME", ""))).append('|');
      material.append(System.getProperty("user.name", ""));
      byte[] digest = Crypto.sha256(material.toString().getBytes(StandardCharsets.UTF_8));
      return Crypto.toHex(digest);
   }

   private static String firstMacAddress() {
      try {
         Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

         while (interfaces.hasMoreElements()) {
            NetworkInterface nic = interfaces.nextElement();
            if (!nic.isLoopback() && !nic.isVirtual() && nic.isUp()) {
               byte[] mac = nic.getHardwareAddress();
               if (mac != null && mac.length > 0) {
                  return Crypto.toHex(mac);
               }
            }
         }
      } catch (Exception ignored) {
      }

      return "no-mac";
   }
}
