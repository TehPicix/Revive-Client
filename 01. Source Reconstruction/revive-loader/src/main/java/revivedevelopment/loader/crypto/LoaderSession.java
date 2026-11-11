package revivedevelopment.loader.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class LoaderSession {
   private static final String INFO_REQUEST = "revive-auth-request";
   private static final String INFO_RESPONSE = "revive-auth-response";
   private static final String INFO_DOWNLOAD = "revive-auth-download";
   private static final String INFO_TOKEN = "revive-auth-token";
   private final Crypto.KeyPairRaw sessionKey = Crypto.generateKeyPair();
   private final byte[] pubA;

   public LoaderSession(String buildPublicKeyHex) {
      this.pubA = Crypto.fromHex(buildPublicKeyHex);
   }

   public String sessionPublicKeyHex() {
      return Crypto.toHex(this.sessionKey.publicKey);
   }

   public byte[] sealRequest(byte[] payload) {
      Crypto.KeyPairRaw ephemeral = Crypto.generateKeyPair();
      byte[] shared = Crypto.x25519(ephemeral.privateKey, this.pubA);
      byte[] key = Crypto.hkdfSha256(shared, "revive-auth-request", 32);
      byte[] nonce = Crypto.randomBytes(24);
      byte[] ciphertext = Crypto.xchachaEncrypt(key, nonce, payload);
      return concat(ephemeral.publicKey, nonce, ciphertext);
   }

   public byte[] decryptResponse(byte[] sealed) {
      if (sealed.length < 72) {
         throw new IllegalArgumentException("response too short");
      } else {
         byte[] ePubR = slice(sealed, 0, 32);
         byte[] nonce = slice(sealed, 32, 56);
         byte[] ciphertext = slice(sealed, 56, sealed.length);
         byte[] shared = Crypto.x25519(this.sessionKey.privateKey, ePubR);
         byte[] key = Crypto.hkdfSha256(shared, "revive-auth-response", 32);
         return Crypto.xchachaDecrypt(key, nonce, ciphertext);
      }
   }

   public String deriveBearer(String encryptedChallengeBase64) {
      byte[] sealed = Base64.getDecoder().decode(encryptedChallengeBase64);
      byte[] challengeHex = this.decryptResponse(sealed);
      byte[] shared = Crypto.x25519(this.sessionKey.privateKey, this.pubA);
      byte[] proofKey = Crypto.hkdfSha256(shared, "revive-auth-token", 32);
      return Crypto.toHex(Crypto.hmacSha256(proofKey, challengeHex));
   }

   public byte[] decryptDownload(byte[] body) {
      if (body.length < 40) {
         throw new IllegalArgumentException("download too short");
      } else {
         byte[] nonce = slice(body, 0, 24);
         byte[] ciphertext = slice(body, 24, body.length);
         byte[] shared = Crypto.x25519(this.sessionKey.privateKey, this.pubA);
         byte[] key = Crypto.hkdfSha256(shared, "revive-auth-download", 32);
         byte[] inner = Crypto.xchachaDecrypt(key, nonce, ciphertext);
         return this.decryptResponse(inner);
      }
   }

   private static byte[] concat(byte[]... parts) {
      int total = 0;

      for (byte[] part : parts) {
         total += part.length;
      }

      byte[] out = new byte[total];
      int offset = 0;

      for (byte[] part : parts) {
         System.arraycopy(part, 0, out, offset, part.length);
         offset += part.length;
      }

      return out;
   }

   private static byte[] slice(byte[] src, int from, int to) {
      byte[] out = new byte[to - from];
      System.arraycopy(src, from, out, 0, to - from);
      return out;
   }

   static byte[] utf8(String value) {
      return value.getBytes(StandardCharsets.UTF_8);
   }
}
