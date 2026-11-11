package revivedevelopment.reviveclient.pingbypass;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class PbCrypto {
   private static final SecureRandom RANDOM = new SecureRandom();

   private PbCrypto() {
   }

   public static byte[] random(int bytes) {
      byte[] b = new byte[bytes];
      RANDOM.nextBytes(b);
      return b;
   }

   public static String randomHex(int bytes) {
      StringBuilder sb = new StringBuilder(bytes * 2);

      for (byte b : random(bytes)) {
         sb.append(String.format("%02x", b));
      }

      return sb.toString();
   }

   public static byte[] passwordKey(String password, String salt) {
      try {
         byte[] saltBytes = ("revive-pingbypass-v1:" + salt).getBytes(StandardCharsets.UTF_8);
         PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, 120000, 256);
         return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
      } catch (GeneralSecurityException var4) {
         throw new IllegalStateException(var4);
      }
   }

   public static byte[] hmac(byte[] key, Object... parts) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(key, "HmacSHA256"));

         for (Object part : parts) {
            byte[] bytes = part instanceof byte[] b ? b : String.valueOf(part).getBytes(StandardCharsets.UTF_8);
            mac.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
            mac.update(bytes);
         }

         return mac.doFinal();
      } catch (GeneralSecurityException var9) {
         throw new IllegalStateException(var9);
      }
   }

   public static String sha256Hex(byte[] data) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
      } catch (GeneralSecurityException var2) {
         throw new IllegalStateException(var2);
      }
   }

   public static boolean equals(byte[] a, byte[] b) {
      return MessageDigest.isEqual(a, b);
   }

   public static byte[] clientProof(byte[] pwKey, byte[] serverNonce, byte[] clientNonce, String user) {
      return hmac(pwKey, "client", serverNonce, clientNonce, user);
   }

   public static byte[] serverProof(byte[] pwKey, byte[] serverNonce, byte[] clientNonce, String user) {
      return hmac(pwKey, "server", clientNonce, serverNonce, user);
   }

   public static PbCrypto.FrameCipher[] sessionCiphers(byte[] pwKey, byte[] serverNonce, byte[] clientNonce, boolean isServer) {
      byte[] session = hmac(pwKey, "session", serverNonce, clientNonce);
      PbCrypto.FrameCipher c2s = new PbCrypto.FrameCipher(hmac(session, "c2s"));
      PbCrypto.FrameCipher s2c = new PbCrypto.FrameCipher(hmac(session, "s2c"));
      return isServer ? new PbCrypto.FrameCipher[]{s2c, c2s} : new PbCrypto.FrameCipher[]{c2s, s2c};
   }

   public static final class FrameCipher {
      private final SecretKeySpec key;
      private long counter;

      FrameCipher(byte[] key) {
         this.key = new SecretKeySpec(key, "AES");
      }

      private GCMParameterSpec nextNonce() {
         byte[] nonce = ByteBuffer.allocate(12).putInt(1381388354).putLong(this.counter++).array();
         return new GCMParameterSpec(128, nonce);
      }

      public synchronized byte[] seal(byte[] plain) {
         try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(1, this.key, this.nextNonce());
            return cipher.doFinal(plain);
         } catch (GeneralSecurityException var3) {
            throw new IllegalStateException(var3);
         }
      }

      public synchronized byte[] open(byte[] sealed) throws GeneralSecurityException {
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
         cipher.init(2, this.key, this.nextNonce());
         return cipher.doFinal(sealed);
      }
   }
}
