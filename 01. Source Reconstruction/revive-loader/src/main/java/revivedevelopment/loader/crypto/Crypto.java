package revivedevelopment.loader.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class Crypto {
   private static final NamedParameterSpec X25519 = NamedParameterSpec.X25519;
   private static final SecureRandom RANDOM = new SecureRandom();

   private Crypto() {
   }

   public static Crypto.KeyPairRaw generateKeyPair() {
      try {
         KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
         generator.initialize(X25519);
         KeyPair pair = generator.generateKeyPair();
         return new Crypto.KeyPairRaw(rawPublicKey(pair.getPublic()), rawPrivateKey(pair.getPrivate()));
      } catch (GeneralSecurityException exception) {
         throw new IllegalStateException("Failed to generate X25519 keypair", exception);
      }
   }

   public static byte[] x25519(byte[] privateRaw, byte[] peerPublicRaw) {
      try {
         KeyAgreement agreement = KeyAgreement.getInstance("X25519");
         agreement.init(privateKeyFromRaw(privateRaw));
         agreement.doPhase(publicKeyFromRaw(peerPublicRaw), true);
         return agreement.generateSecret();
      } catch (GeneralSecurityException exception) {
         throw new IllegalStateException("Failed to derive X25519 shared secret", exception);
      }
   }

   public static byte[] hkdfSha256(byte[] inputKeyMaterial, String info, int length) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         byte[] salt = new byte[mac.getMacLength()];
         mac.init(new SecretKeySpec(salt, "HmacSHA256"));
         byte[] prk = mac.doFinal(inputKeyMaterial);
         byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
         byte[] output = new byte[length];
         byte[] previous = new byte[0];
         int offset = 0;

         for (int counter = 1; offset < length; counter++) {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(previous);
            mac.update(infoBytes);
            mac.update((byte)counter);
            previous = mac.doFinal();
            int copy = Math.min(previous.length, length - offset);
            System.arraycopy(previous, 0, output, offset, copy);
            offset += copy;
         }

         return output;
      } catch (GeneralSecurityException exception) {
         throw new IllegalStateException("Failed to derive HKDF-SHA256 key", exception);
      }
   }

   public static byte[] xchachaEncrypt(byte[] key, byte[] nonce24, byte[] plaintext) {
      return xchacha(1, key, nonce24, plaintext, null);
   }

   public static byte[] xchachaDecrypt(byte[] key, byte[] nonce24, byte[] ciphertextWithTag) {
      return xchacha(2, key, nonce24, null, ciphertextWithTag);
   }

   private static byte[] xchacha(int mode, byte[] key, byte[] nonce24, byte[] plaintext, byte[] ciphertext) {
      if (key.length != 32) {
         throw new IllegalArgumentException("XChaCha20 key must be 32 bytes");
      } else if (nonce24.length != 24) {
         throw new IllegalArgumentException("XChaCha20 nonce must be 24 bytes");
      } else {
         byte[] subkey = hchacha20(key, Arrays.copyOf(nonce24, 16));
         byte[] ietfNonce = new byte[12];
         System.arraycopy(nonce24, 16, ietfNonce, 4, 8);

         byte[] result;
         try {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            cipher.init(mode, new SecretKeySpec(subkey, "ChaCha20"), new IvParameterSpec(ietfNonce));
            result = cipher.doFinal(mode == 1 ? plaintext : ciphertext);
         } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("XChaCha20-Poly1305 operation failed", exception);
         } finally {
            Arrays.fill(subkey, (byte)0);
         }

         return result;
      }
   }

   private static byte[] hchacha20(byte[] key, byte[] nonce16) {
      int[] state = new int[16];
      state[0] = 1634760805;
      state[1] = 857760878;
      state[2] = 2036477234;
      state[3] = 1797285236;

      for (int i = 0; i < 8; i++) {
         state[4 + i] = leToInt(key, i * 4);
      }

      for (int i = 0; i < 4; i++) {
         state[12 + i] = leToInt(nonce16, i * 4);
      }

      for (int round = 0; round < 10; round++) {
         qr(state, 0, 4, 8, 12);
         qr(state, 1, 5, 9, 13);
         qr(state, 2, 6, 10, 14);
         qr(state, 3, 7, 11, 15);
         qr(state, 0, 5, 10, 15);
         qr(state, 1, 6, 11, 12);
         qr(state, 2, 7, 8, 13);
         qr(state, 3, 4, 9, 14);
      }

      byte[] out = new byte[32];
      int[] words = new int[]{0, 1, 2, 3, 12, 13, 14, 15};

      for (int i = 0; i < words.length; i++) {
         intToLe(state[words[i]], out, i * 4);
      }

      return out;
   }

   private static void qr(int[] s, int a, int b, int c, int d) {
      s[a] += s[b];
      s[d] = Integer.rotateLeft(s[d] ^ s[a], 16);
      s[c] += s[d];
      s[b] = Integer.rotateLeft(s[b] ^ s[c], 12);
      s[a] += s[b];
      s[d] = Integer.rotateLeft(s[d] ^ s[a], 8);
      s[c] += s[d];
      s[b] = Integer.rotateLeft(s[b] ^ s[c], 7);
   }

   public static byte[] hmacSha256(byte[] key, byte[] message) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(key, "HmacSHA256"));
         return mac.doFinal(message);
      } catch (GeneralSecurityException exception) {
         throw new IllegalStateException("Failed to compute HMAC-SHA256", exception);
      }
   }

   public static byte[] sha256(byte[] input) {
      try {
         return MessageDigest.getInstance("SHA-256").digest(input);
      } catch (GeneralSecurityException exception) {
         throw new IllegalStateException("SHA-256 unavailable", exception);
      }
   }

   public static byte[] randomBytes(int n) {
      byte[] bytes = new byte[n];
      RANDOM.nextBytes(bytes);
      return bytes;
   }

   public static PublicKey publicKeyFromRaw(byte[] raw) {
      try {
         KeyFactory factory = KeyFactory.getInstance("X25519");
         return factory.generatePublic(new XECPublicKeySpec(X25519, leToBigInteger(raw)));
      } catch (GeneralSecurityException exception) {
         throw new IllegalArgumentException("Invalid X25519 public key bytes", exception);
      }
   }

   public static PrivateKey privateKeyFromRaw(byte[] raw) {
      try {
         KeyFactory factory = KeyFactory.getInstance("X25519");
         return factory.generatePrivate(new XECPrivateKeySpec(X25519, (byte[])raw.clone()));
      } catch (GeneralSecurityException exception) {
         throw new IllegalArgumentException("Invalid X25519 private key bytes", exception);
      }
   }

   public static byte[] rawPublicKey(PublicKey key) {
      if (key instanceof XECPublicKey xec) {
         return bigIntegerToLe(xec.getU(), 32);
      } else {
         throw new IllegalArgumentException("Expected X25519 public key");
      }
   }

   public static byte[] rawPrivateKey(PrivateKey key) {
      if (key instanceof XECPrivateKey xec) {
         return (byte[])xec.getScalar().orElseThrow(() -> new IllegalStateException("Missing X25519 scalar")).clone();
      } else {
         throw new IllegalArgumentException("Expected X25519 private key");
      }
   }

   public static String toHex(byte[] bytes) {
      StringBuilder builder = new StringBuilder(bytes.length * 2);

      for (byte value : bytes) {
         builder.append(Character.forDigit(value >> 4 & 15, 16)).append(Character.forDigit(value & 15, 16));
      }

      return builder.toString();
   }

   public static byte[] fromHex(String hex) {
      if ((hex.length() & 1) != 0) {
         throw new IllegalArgumentException("Hex string must have even length");
      } else {
         byte[] bytes = new byte[hex.length() / 2];

         for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte)(Character.digit(hex.charAt(i * 2), 16) << 4 | Character.digit(hex.charAt(i * 2 + 1), 16));
         }

         return bytes;
      }
   }

   private static int leToInt(byte[] in, int off) {
      return in[off] & 0xFF | (in[off + 1] & 0xFF) << 8 | (in[off + 2] & 0xFF) << 16 | (in[off + 3] & 0xFF) << 24;
   }

   private static void intToLe(int value, byte[] out, int off) {
      out[off] = (byte)value;
      out[off + 1] = (byte)(value >>> 8);
      out[off + 2] = (byte)(value >>> 16);
      out[off + 3] = (byte)(value >>> 24);
   }

   private static BigInteger leToBigInteger(byte[] le) {
      byte[] be = new byte[le.length];

      for (int i = 0; i < le.length; i++) {
         be[i] = le[le.length - 1 - i];
      }

      return new BigInteger(1, be);
   }

   private static byte[] bigIntegerToLe(BigInteger value, int size) {
      byte[] be = value.toByteArray();
      byte[] unsigned = be[0] == 0 ? Arrays.copyOfRange(be, 1, be.length) : be;
      if (unsigned.length > size) {
         throw new IllegalArgumentException("Value does not fit in " + size + " bytes");
      } else {
         byte[] le = new byte[size];

         for (int i = 0; i < unsigned.length; i++) {
            le[i] = unsigned[unsigned.length - 1 - i];
         }

         return le;
      }
   }

   public static final class KeyPairRaw {
      public final byte[] publicKey;
      public final byte[] privateKey;

      KeyPairRaw(byte[] publicKey, byte[] privateKey) {
         this.publicKey = publicKey;
         this.privateKey = privateKey;
      }
   }
}
