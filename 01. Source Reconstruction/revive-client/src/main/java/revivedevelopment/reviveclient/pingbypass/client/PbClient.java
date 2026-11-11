package revivedevelopment.reviveclient.pingbypass.client;

import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.pingbypass.PbCrypto;
import revivedevelopment.reviveclient.pingbypass.PbProtocol;

public class PbClient {
   private static final int MAX_QUEUED = 512;
   private static final JsonObject CLOSE = new JsonObject();
   private final String host;
   private final int port;
   private final String password;
   private final String user;
   private final UUID uuid;
   private final PbClient.Listener listener;
   private volatile boolean running = true;
   private volatile boolean closing;
   private volatile Runnable onClosed;
   private final AtomicBoolean closedFired = new AtomicBoolean();
   private volatile PbClient.State state = PbClient.State.CONNECTING;
   private volatile String lastError;
   private volatile long rttMs = -1L;
   private volatile long retryDelayMs = 3000L;
   private volatile Socket socket;
   private volatile PbClient.Session session;
   private final Map<String, CompletableFuture<JsonObject>> pendingTickets = new ConcurrentHashMap<>();
   private final Thread thread;

   public PbClient(String host, int port, String password, String user, UUID uuid, PbClient.Listener listener) {
      this.host = host;
      this.port = port;
      this.password = password;
      this.user = user;
      this.uuid = uuid;
      this.listener = listener;
      this.thread = new Thread(this::run, "PingBypass-control");
      this.thread.setDaemon(true);
      this.thread.start();
   }

   public PbClient.State getState() {
      return this.state;
   }

   public boolean isConnected() {
      return this.state == PbClient.State.CONNECTED;
   }

   public String getLastError() {
      return this.lastError;
   }

   public long getRtt() {
      return this.rttMs;
   }

   public String getHost() {
      return this.host;
   }

   public InetAddress getRemoteAddress() {
      Socket s = this.socket;
      return s != null && s.isConnected() ? s.getInetAddress() : null;
   }

   public int getPort() {
      return this.port;
   }

   public String getUser() {
      return this.user;
   }

   public void close() {
      this.running = false;
      this.thread.interrupt();
      this.closeSocket();
   }

   public void closeGracefully(long timeoutMs, Runnable whenClosed) {
      this.onClosed = whenClosed;
      PbClient.Session current = this.session;
      if (current != null && current.open && this.state == PbClient.State.CONNECTED && current.queue.offer(CLOSE)) {
         Thread timer = new Thread(() -> {
            try {
               Thread.sleep(timeoutMs);
            } catch (InterruptedException var4x) {
            }

            this.close();
            this.fireClosed();
         }, "PingBypass-close");
         timer.setDaemon(true);
         timer.start();
      } else {
         this.close();
         this.fireClosed();
      }
   }

   private void fireClosed() {
      Runnable r = this.onClosed;
      if (r != null && this.closedFired.compareAndSet(false, true)) {
         r.run();
      }
   }

   private static String describe(Exception e) {
      if (e instanceof UnknownHostException) {
         return "Unknown host " + e.getMessage();
      } else if (e instanceof EOFException) {
         return "VPS closed the connection";
      } else {
         return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      }
   }

   private void run() {
      while (this.running) {
         boolean wasConnected = false;

         try {
            wasConnected = this.connectOnce();
         } catch (Exception var8) {
            if (this.running) {
               this.lastError = describe(var8);
               ReviveClient.LOG.warn("[PingBypass] control connection: {}", var8.toString());
            }
         } finally {
            this.closeSocket();
            this.pendingTickets.values().forEach(f -> f.cancel(false));
            this.pendingTickets.clear();
            if (wasConnected || this.state == PbClient.State.CONNECTED) {
               this.listener.onDisconnected(this);
            }
         }

         if (this.closing) {
            this.fireClosed();
         }

         if (!this.running || this.closing) {
            break;
         }

         this.state = PbClient.State.FAILED;
         this.rttMs = -1L;

         try {
            Thread.sleep(this.retryDelayMs);
         } catch (InterruptedException var7) {
            break;
         }
      }
   }

   private boolean connectOnce() throws Exception {
      this.state = PbClient.State.CONNECTING;
      Socket s = new Socket();
      s.setTcpNoDelay(true);
      this.socket = s;
      s.connect(new InetSocketAddress(this.host, this.port), 5000);
      s.setSoTimeout(10000);
      DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
      DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
      o.write(PbProtocol.MAGIC);
      o.flush();
      this.state = PbClient.State.AUTHENTICATING;
      JsonObject challenge = PbProtocol.decode(readFrame(in));
      if ("denied".equals(PbProtocol.type(challenge))) {
         throw this.denied(challenge);
      } else if (!"challenge".equals(PbProtocol.type(challenge))) {
         throw new IOException("Unexpected VPS reply");
      } else {
         int version = challenge.has("v") ? challenge.get("v").getAsInt() : 0;
         if (version != 8) {
            this.retryDelayMs = 30000L;
            throw new IOException("VPS runs PingBypass protocol " + version + ", this client 8 - use matching builds");
         } else {
            byte[] serverNonce = PbProtocol.unb64(challenge.get("nonce").getAsString());
            byte[] clientNonce = PbCrypto.random(32);
            String salt = PbProtocol.str(challenge, "salt", "");
            String pinned = this.listener.pinnedSalt();
            if (pinned != null && !pinned.isEmpty() && !pinned.equals(salt)) {
               this.retryDelayMs = 30000L;
               throw new IOException("VPS salt changed (accept-new-salt)");
            } else {
               byte[] pwKey = PbCrypto.passwordKey(this.password, salt);
               JsonObject auth = PbProtocol.message("auth");
               auth.addProperty("v", 8);
               auth.addProperty("nonce", PbProtocol.b64(clientNonce));
               auth.addProperty("user", this.user);
               auth.addProperty("uuid", this.uuid == null ? "" : this.uuid.toString());
               auth.addProperty("mac", PbProtocol.b64(PbCrypto.clientProof(pwKey, serverNonce, clientNonce, this.user)));
               auth.addProperty("bind", PbRouting.currentBinding());
               writeFrame(o, PbProtocol.encode(auth));
               JsonObject welcome = PbProtocol.decode(readFrame(in));
               if ("denied".equals(PbProtocol.type(welcome))) {
                  throw this.denied(welcome);
               } else {
                  byte[] proof = PbProtocol.unb64(PbProtocol.str(welcome, "mac", ""));
                  if (!PbCrypto.equals(proof, PbCrypto.serverProof(pwKey, serverNonce, clientNonce, this.user))) {
                     throw new IOException("VPS could not prove the password - wrong address or password?");
                  } else {
                     this.listener.onSaltConfirmed(salt);
                     PbCrypto.FrameCipher[] ciphers = PbCrypto.sessionCiphers(pwKey, serverNonce, clientNonce, false);
                     PbCrypto.FrameCipher recvCipher = ciphers[1];
                     PbClient.Session current = new PbClient.Session(o, ciphers[0]);
                     this.session = current;
                     this.startWriter(current);
                     s.setSoTimeout(20000);
                     this.state = PbClient.State.CONNECTED;
                     this.lastError = null;
                     this.retryDelayMs = 3000L;
                     this.listener.onConnected(this);

                     while (this.running) {
                        JsonObject msg = PbProtocol.decode(recvCipher.open(readFrame(in)));
                        String var18 = PbProtocol.type(msg);
                        switch (var18) {
                           case "pong":
                              this.rttMs = System.currentTimeMillis() - msg.get("t").getAsLong();
                              break;
                           case "ticket_ok":
                              CompletableFuture<JsonObject> f = this.pendingTickets.remove(PbProtocol.str(msg, "ticket", ""));
                              if (f != null) {
                                 f.complete(msg);
                              }
                              break;
                           case "denied":
                              throw this.denied(msg);
                           default:
                              this.listener.onMessage(this, msg);
                        }
                     }

                     return true;
                  }
               }
            }
         }
      }
   }

   private IOException denied(JsonObject msg) {
      int retry = msg.has("retry") ? msg.get("retry").getAsInt() : 30;
      this.retryDelayMs = Math.max(3, Math.min(60, retry)) * 1000L;
      return new IOException("VPS refused: " + PbProtocol.str(msg, "reason", "?"));
   }

   private static byte[] readFrame(DataInputStream in) throws IOException {
      int len = in.readInt();
      if (len >= 0 && len <= 4194304) {
         byte[] data = new byte[len];
         in.readFully(data);
         return data;
      } else {
         throw new IOException("Bad frame length " + len);
      }
   }

   private static void writeFrame(DataOutputStream o, byte[] data) throws IOException {
      o.writeInt(data.length);
      o.write(data);
      o.flush();
   }

   private void startWriter(PbClient.Session current) {
      Thread writer = new Thread(() -> {
         try {
            while (current.open) {
               JsonObject msg = current.queue.poll(1L, TimeUnit.SECONDS);
               if (msg != null) {
                  if (msg == CLOSE) {
                     this.closing = true;
                     Socket s = this.socket;
                     if (s != null) {
                        s.shutdownOutput();
                     }

                     return;
                  }

                  writeFrame(current.out, current.cipher.seal(PbProtocol.encode(msg)));
               }
            }
         } catch (InterruptedException var4) {
         } catch (IOException var5) {
            if (current.open) {
               this.lastError = var5.getMessage();
            }

            this.closeSocket();
         }
      }, "PingBypass-control-writer");
      writer.setDaemon(true);
      writer.start();
   }

   private void closeSocket() {
      Socket s = this.socket;
      this.socket = null;
      if (s != null) {
         try {
            s.close();
         } catch (IOException var3) {
         }
      }

      PbClient.Session current = this.session;
      this.session = null;
      if (current != null) {
         current.open = false;
      }
   }

   public boolean send(JsonObject message) {
      PbClient.Session current = this.session;
      if (current == null || !current.open || this.state != PbClient.State.CONNECTED) {
         return false;
      } else if (!current.queue.offer(message)) {
         this.lastError = "send queue full";
         return false;
      } else {
         return true;
      }
   }

   public void ping() {
      JsonObject ping = PbProtocol.message("ping");
      ping.addProperty("t", System.currentTimeMillis());
      this.send(ping);
   }

   public PbClient.Ticket requestTicket(long timeoutMs) {
      if (!this.isConnected()) {
         return null;
      } else {
         String ticket = PbCrypto.randomHex(16);
         CompletableFuture<JsonObject> future = new CompletableFuture<>();
         this.pendingTickets.put(ticket, future);
         JsonObject msg = PbProtocol.message("ticket");
         msg.addProperty("ticket", ticket);
         if (!this.send(msg)) {
            this.pendingTickets.remove(ticket);
            return null;
         } else {
            try {
               JsonObject ok = future.get(timeoutMs, TimeUnit.MILLISECONDS);
               JsonObject via = ok.has("via") && ok.get("via").isJsonObject() ? ok.getAsJsonObject("via") : null;
               return new PbClient.Ticket(ticket, PbProtocol.str(ok, "keyHash", ""), via);
            } catch (Exception var8) {
               this.pendingTickets.remove(ticket);
               return null;
            }
         }
      }
   }

   public interface Listener {
      void onConnected(PbClient var1);

      void onMessage(PbClient var1, JsonObject var2);

      void onDisconnected(PbClient var1);

      default String pinnedSalt() {
         return "";
      }

      default void onSaltConfirmed(String salt) {
      }
   }

   private static final class Session {
      final DataOutputStream out;
      final PbCrypto.FrameCipher cipher;
      final LinkedBlockingQueue<JsonObject> queue = new LinkedBlockingQueue<>(512);
      volatile boolean open = true;

      Session(DataOutputStream out, PbCrypto.FrameCipher cipher) {
         this.out = out;
         this.cipher = cipher;
      }
   }

   public static enum State {
      CONNECTING,
      AUTHENTICATING,
      CONNECTED,
      FAILED;
   }

   public record Ticket(String ticket, String keyHash, @Nullable JsonObject via) {
   }
}
