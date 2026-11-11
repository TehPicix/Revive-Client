package revivedevelopment.reviveclient.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Obf {
   public static String KEY = "rvC9x2Lq8ZpN4mWf";

   public static String s(String enc) {
      byte[] data = Base64.getDecoder().decode(enc);
      byte[] key = KEY.getBytes(StandardCharsets.UTF_8);
      byte[] out = new byte[data.length];

      for (int i = 0; i < data.length; i++) {
         out[i] = (byte)(data[i] ^ key[i % key.length]);
      }

      return new String(out, StandardCharsets.UTF_8);
   }
}
