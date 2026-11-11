package revivedevelopment.reviveclient.systems.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.systems.emotes.EmoteEngine;
import revivedevelopment.reviveclient.systems.emotes.EmoteLibrary;
import revivedevelopment.reviveclient.systems.emotes.EmoteManager;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Emotes;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;

public class EmoteSyncManager {
   public static final String DEFAULT_URL = "wss://emotes.revive-client.com/ws";
   public static final int DEFAULT_SELF_HOST_PORT = 8091;
   private static final int PROTOCOL_VERSION = 1;
   private static final long BACKOFF_MIN_MS = 1000L;
   private static final long BACKOFF_MAX_MS = 30000L;
   private static final long PING_INTERVAL_MS = 5000L;
   private static final long IDLE_TIMEOUT_MS = 20000L;
   private static final long WELCOME_TIMEOUT_MS = 15000L;
   private static final long SEND_TIMEOUT_MS = 5000L;
   private static final long CONFIG_DEBOUNCE_MS = 1000L;
   private static final long RATE_CLOSE_RETRY_MS = 10000L;
   private static final long LIBRARY_RECHECK_MS = 300000L;
   private static final int LOOP_REFRESH_TICKS = 600;
   private static final float RESYNC_TOLERANCE_TICKS = 4.0F;
   private static final int MAX_OUT_QUEUE = 64;
   private static final int MAX_MAIN_TASKS = 128;
   private static final int MAX_REMOTE = 1024;
   private static final int MAX_INBOUND_CHARS = 524288;
   private static final long LIBRARY_MAX_FILE = 524288L;
   private static final int LIBRARY_MAX_FILES = 512;
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).followRedirects(Redirect.NORMAL).build();
   private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("Revive-Emotes"));
   private final ExecutorService ioPool = Executors.newCachedThreadPool(daemonFactory("Revive-Emotes-IO"));
   private HttpClient wsClient;
   private WebSocket ws;
   private boolean connecting;
   private long backoffMs = 1000L;
   private ScheduledFuture<?> reconnectFuture;
   private ScheduledFuture<?> heartbeatFuture;
   private long openedAt;
   private long lastPingSentAt;
   private long lastLibraryCheckAt;
   private int pingIds;
   private volatile int generation;
   private volatile boolean wanted;
   private volatile boolean connected;
   private volatile boolean authFailed;
   private volatile long lastRecvAt;
   private volatile EmoteSyncManager.ConnectConfig config;
   private volatile String lastError;
   private volatile long clockOffset;
   private volatile long rttMs = -1L;
   private volatile String selfName;
   @Nullable
   private volatile String reviveAccount;
   private final ArrayDeque<String> outQueue = new ArrayDeque<>();
   private final AtomicBoolean drainScheduled = new AtomicBoolean();
   private final ConcurrentHashMap<UUID, EmoteSyncManager.RemoteEmote> remote = new ConcurrentHashMap<>();
   private volatile String libraryVersion;
   private volatile String syncedVersion;
   private final AtomicBoolean syncing = new AtomicBoolean();
   private final AtomicInteger syncRequests = new AtomicInteger();
   private final ArrayDeque<Runnable> mainTasks = new ArrayDeque<>();
   private final ArrayDeque<EmoteSyncManager.ChatLine> chatLines = new ArrayDeque<>();
   private Emotes module;
   private long configDirtyAt;
   private UUID announced;
   private int announcedTicks;
   private volatile boolean inWorld;
   private volatile boolean showOthers = true;
   private volatile boolean sendEmotes = true;
   private volatile boolean chatNotices = true;
   private volatile boolean debug;

   public EmoteSyncManager() {
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   public boolean isConnected() {
      return this.connected;
   }

   public boolean isEnabled() {
      return this.wanted;
   }

   @Nullable
   public String getServerUrl() {
      EmoteSyncManager.ConnectConfig c = this.config;
      return c == null ? null : c.url;
   }

   @Nullable
   public String getLastError() {
      return this.lastError;
   }

   public long getPingMillis() {
      return this.rttMs;
   }

   @Nullable
   public String getSelfName() {
      return this.selfName;
   }

   @Nullable
   public String getReviveAccount() {
      return this.reviveAccount;
   }

   @Nullable
   public String getLibraryVersion() {
      return this.libraryVersion;
   }

   @Nullable
   public String getSyncedVersion() {
      return this.syncedVersion;
   }

   public Map<UUID, EmoteSyncManager.RemoteEmote> getRemoteEmotes() {
      return this.remote;
   }

   public long serverNow() {
      return System.currentTimeMillis() + this.clockOffset;
   }

   public void markConfigDirty() {
      this.configDirtyAt = System.currentTimeMillis();
   }

   public void onLocalPlay(UUID emoteUuid) {
      this.announced = emoteUuid;
      this.announcedTicks = 0;
      if (emoteUuid != null && this.connected && this.sendEmotes) {
         this.sendEmote("start", emoteUuid, EmoteManager.localTicks());
      }
   }

   public void onLocalStop(@Nullable UUID emoteUuid) {
      if (this.announced != null && this.connected) {
         this.sendEmote("stop", emoteUuid != null ? emoteUuid : this.announced, 0.0F);
      }

      this.announced = null;
      this.announcedTicks = 0;
   }

   public void requestLibrarySync() {
      this.startLibrarySync(true);
   }

   public static String normalizeUrl(String raw) {
      String s = raw == null ? "" : raw.trim();
      if (s.isEmpty()) {
         return "wss://emotes.revive-client.com/ws";
      } else {
         String lower = s.toLowerCase(Locale.ROOT);
         String scheme = null;
         if (lower.startsWith("wss://")) {
            scheme = "wss";
            s = s.substring(6);
         } else if (lower.startsWith("ws://")) {
            scheme = "ws";
            s = s.substring(5);
         } else if (lower.startsWith("https://")) {
            scheme = "wss";
            s = s.substring(8);
         } else if (lower.startsWith("http://")) {
            scheme = "ws";
            s = s.substring(7);
         }

         int slash = s.indexOf(47);
         String hostPort = slash < 0 ? s : s.substring(0, slash);
         String path = slash < 0 ? "" : s.substring(slash);
         if (hostPort.isEmpty()) {
            return "wss://emotes.revive-client.com/ws";
         } else {
            boolean hasPort = hostPort.matches(".*:\\d{1,5}$") && (!hostPort.startsWith("[") || hostPort.contains("]:"));
            if (scheme == null) {
               String host = hasPort ? hostPort.substring(0, hostPort.lastIndexOf(58)) : hostPort;
               boolean local = host.equalsIgnoreCase("localhost") || host.startsWith("[") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
               scheme = !hasPort && !local ? "wss" : "ws";
               if (!hasPort && local) {
                  hostPort = hostPort + ":8091";
               }
            }

            if (path.isEmpty() || path.equals("/")) {
               path = "/ws";
            }

            return scheme + "://" + hostPort + path;
         }
      }
   }

   static String httpBase(String wsUrl) {
      URI uri = URI.create(wsUrl);
      String scheme = "wss".equalsIgnoreCase(uri.getScheme()) ? "https" : "http";
      String host = uri.getHost();
      if (host != null && host.contains(":") && !host.startsWith("[")) {
         host = "[" + host + "]";
      }

      return scheme + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      EmoteEngine.get().tick();
      Emotes m = this.module();
      boolean active = m != null && m.isActive();
      this.inWorld = Utils.canUpdate();
      if (m != null) {
         this.showOthers = m.showOthers.get();
         this.sendEmotes = m.sendEmotes.get();
         this.chatNotices = m.chatNotices.get();
         this.debug = m.debug.get();
      }

      if (active && !this.wanted) {
         this.config = m.buildConfig();
         this.configDirtyAt = 0L;
         this.authFailed = false;
         this.lastError = null;
         this.wanted = true;
         this.post(this::connectNow);
      } else if (!active && this.wanted) {
         this.wanted = false;
         this.post(() -> this.teardown("module disabled", false));
      } else if (active && this.configDirtyAt != 0L && System.currentTimeMillis() - this.configDirtyAt > 1000L) {
         this.configDirtyAt = 0L;
         this.config = m.buildConfig();
         this.authFailed = false;
         this.lastError = null;
         this.post(() -> this.teardown("settings changed", true));
      } else if (!active) {
         this.configDirtyAt = 0L;
      }

      this.drainMainTasks();
      if (this.inWorld) {
         this.announceLocal();
         this.applyRemote();
      }
   }

   private void announceLocal() {
      UUID playing = EmoteManager.localPlaying();
      if (playing == null) {
         if (this.announced != null) {
            EmoteLibrary.Emote e = EmoteLibrary.get(this.announced);
            if (e != null && e.animation().loops() && this.connected) {
               this.sendEmote("stop", this.announced, 0.0F);
            }

            this.announced = null;
         }

         this.announcedTicks = 0;
      } else if (!playing.equals(this.announced)) {
         this.announced = playing;
         this.announcedTicks = 0;
         if (this.connected && this.sendEmotes) {
            this.sendEmote("start", playing, EmoteManager.localTicks());
         }
      } else {
         EmoteLibrary.Emote e = EmoteLibrary.get(playing);
         if (e != null && e.animation().loops() && ++this.announcedTicks >= 600) {
            this.announcedTicks = 0;
            if (this.connected && this.sendEmotes) {
               this.sendEmote("start", playing, EmoteManager.localTicks());
            }
         }
      }
   }

   private void applyRemote() {
      if (!this.remote.isEmpty()) {
         PlayerEntity self = ReviveClient.mc.player;
         UUID selfUuid = self == null ? null : self.getUuid();
         long now = this.serverNow();
         Iterator<Entry<UUID, EmoteSyncManager.RemoteEmote>> it = this.remote.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, EmoteSyncManager.RemoteEmote> entry = it.next();
            UUID player = entry.getKey();
            if (!player.equals(selfUuid)) {
               EmoteSyncManager.RemoteEmote r = entry.getValue();
               EmoteLibrary.Emote emote = EmoteLibrary.get(r.emote);
               if (emote != null) {
                  float tick = r.tick + (float)(now - r.at) / 50.0F;
                  if (!emote.animation().loops() && tick >= emote.animation().length()) {
                     it.remove();
                  } else if (this.showOthers) {
                     PlayerEntity p = ReviveClient.mc.world.getPlayerByUuid(player);
                     if (p != null && !EmoteManager.isPlaying(player, r.emote)) {
                        EmoteManager.play(player, r.emote, tick);
                        this.debugLog("{} plays {} from tick {}", r.name, emote.name(), (int)tick);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.inWorld = false;
      if (this.announced != null && this.connected) {
         this.sendEmote("stop", this.announced, 0.0F);
      }

      this.announced = null;
      this.announcedTicks = 0;
      EmoteManager.clearSession();
   }

   private void sendEmote(String op, UUID emoteUuid, float tick) {
      JsonObject o = msg("emote");
      o.addProperty("op", op);
      if (emoteUuid != null) {
         o.addProperty("emote", emoteUuid.toString());
      }

      if ("start".equals(op)) {
         o.addProperty("tick", Math.round(tick * 100.0F) / 100.0F);
      }

      this.enqueue(o.toString());
   }

   private void drainMainTasks() {
      while (true) {
         Runnable r;
         synchronized (this.mainTasks) {
            r = this.mainTasks.pollFirst();
         }

         if (r == null) {
            if (!this.inWorld) {
               return;
            }

            Emotes m = this.module();

            while (true) {
               EmoteSyncManager.ChatLine line;
               synchronized (this.chatLines) {
                  line = this.chatLines.pollFirst();
               }

               if (line == null) {
                  return;
               }

               if (m != null) {
                  switch (line.level) {
                     case 1:
                        m.warning("%s", new Object[]{line.text});
                        break;
                     case 2:
                        m.error("%s", new Object[]{line.text});
                        break;
                     default:
                        m.info("%s", new Object[]{line.text});
                  }
               }
            }
         }

         try {
            r.run();
         } catch (Throwable var6) {
            ReviveClient.LOG.warn("[Emotes] main-thread task failed: {}", var6.toString());
         }
      }
   }

   @Nullable
   private Emotes module() {
      if (this.module == null) {
         Modules modules = Modules.get();
         if (modules != null) {
            this.module = modules.get(Emotes.class);
         }
      }

      return this.module;
   }

   private void runOnMain(Runnable r) {
      synchronized (this.mainTasks) {
         while (this.mainTasks.size() >= 128) {
            this.mainTasks.pollFirst();
         }

         this.mainTasks.addLast(r);
      }
   }

   private void chat(int level, String text) {
      synchronized (this.chatLines) {
         while (this.chatLines.size() >= 32) {
            this.chatLines.pollFirst();
         }

         this.chatLines.addLast(new EmoteSyncManager.ChatLine(level, text));
      }
   }

   private void debugLog(String message, Object... args) {
      if (this.debug) {
         ReviveClient.LOG.info("[Emotes] " + message, args);
      }
   }

   private void post(Runnable r) {
      try {
         this.scheduler.execute(() -> {
            try {
               r.run();
            } catch (Throwable var2) {
               ReviveClient.LOG.warn("[Emotes] task failed: {}", var2.toString());
            }
         });
      } catch (RejectedExecutionException var3) {
      }
   }

   private void connectNow() {
      this.reconnectFuture = null;
      if (this.wanted && this.ws == null && !this.connecting) {
         EmoteSyncManager.ConnectConfig cfg = this.config;
         if (cfg != null) {
            URI uri;
            try {
               uri = URI.create(cfg.url);
               if (uri.getHost() == null) {
                  throw new IllegalArgumentException("no host");
               }
            } catch (IllegalArgumentException var7) {
               this.reportError("Invalid emote server-url: " + cfg.url);
               return;
            }

            if (cfg.mcUuid != null && !cfg.mcName.isEmpty()) {
               String credential;
               if (cfg.authMode == EmoteSyncManager.AuthMode.REVIVE) {
                  credential = readToken(cfg);
                  if (credential == null) {
                     this.reportError("No Revive token: set the 'token' setting or start the game from the Revive launcher.");
                     this.scheduleReconnect(30000L);
                     return;
                  }
               } else {
                  credential = cfg.key;
                  if (credential.isEmpty()) {
                     this.reportError("Server Key auth selected but the 'key' setting is empty.");
                     return;
                  }
               }

               this.ensureHeartbeat();
               int gen = ++this.generation;
               this.connecting = true;
               this.debugLog("connecting to {}", cfg.url);

               try {
                  if (this.wsClient == null) {
                     this.wsClient = HttpClient.newBuilder().executor(this.ioPool).connectTimeout(Duration.ofSeconds(10L)).build();
                  }

                  String cred = credential;
                  this.wsClient
                     .newWebSocketBuilder()
                     .connectTimeout(Duration.ofSeconds(10L))
                     .buildAsync(uri, new EmoteSyncManager.Listener(gen))
                     .whenComplete((socket, err) -> this.post(() -> this.onConnectResult(gen, socket, err, cfg, cred)));
               } catch (Throwable var6) {
                  this.connecting = false;
                  this.lastError = var6.getClass().getSimpleName();
                  this.scheduleReconnect(-1L);
               }
            } else {
               this.reportError("Couldn't read your Minecraft session (uuid/name).");
               this.scheduleReconnect(30000L);
            }
         }
      }
   }

   private void onConnectResult(int gen, WebSocket socket, Throwable err, EmoteSyncManager.ConnectConfig cfg, String credential) {
      if (gen == this.generation && this.wanted) {
         this.connecting = false;
         if (err == null && socket != null) {
            this.ws = socket;
            long now = System.currentTimeMillis();
            this.openedAt = now;
            this.lastRecvAt = now;
            this.lastPingSentAt = 0L;
            JsonObject hello = msg("hello");
            hello.addProperty("v", 1);
            JsonObject auth = new JsonObject();
            if (cfg.authMode == EmoteSyncManager.AuthMode.REVIVE) {
               auth.addProperty("token", credential);
            } else {
               auth.addProperty("key", credential);
            }

            hello.add("auth", auth);
            hello.addProperty("mcUuid", cfg.mcUuid.toString());
            hello.addProperty("mcName", cfg.mcName);
            hello.addProperty("client", "revive/" + ReviveClient.VERSION);
            this.sendNow(hello.toString());
            this.debugLog("socket open, hello sent");
         } else {
            Throwable cause = err != null && err.getCause() != null ? err.getCause() : err;
            this.lastError = cause == null
               ? "connect failed"
               : cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
            this.debugLog("connect failed: {}", this.lastError);
            this.scheduleReconnect(-1L);
         }
      } else {
         if (socket != null) {
            socket.abort();
         }
      }
   }

   private void teardown(String reason, boolean reconnect) {
      this.generation++;
      this.connecting = false;
      if (this.reconnectFuture != null) {
         this.reconnectFuture.cancel(false);
         this.reconnectFuture = null;
      }

      WebSocket socket = this.ws;
      this.ws = null;
      boolean was = this.connected;
      this.connected = false;
      if (socket != null) {
         closeQuietly(socket);
      }

      this.clearRemoteState();
      synchronized (this.outQueue) {
         this.outQueue.clear();
      }

      this.debugLog("teardown: {}", reason);
      if (was && this.chatNotices && !"module disabled".equals(reason)) {
         this.chat(1, "Disconnected from emote server (" + reason + ").");
      }

      if (reconnect && this.wanted) {
         this.backoffMs = 1000L;
         this.scheduleReconnect(0L);
      }
   }

   private void onSocketLost(int gen, String reason) {
      this.onSocketLost(gen, reason, this.authFailed ? 30000L : -1L);
   }

   private void onSocketClosed(int gen, int code, String reason) {
      if (gen == this.generation) {
         switch (code) {
            case 4000:
               this.onSocketLost(gen, reason, -2L);
               break;
            case 4001:
               this.authFailed = true;
               this.onSocketLost(gen, reason, 30000L);
               break;
            case 4002:
               if (this.chatNotices) {
                  this.chat(2, "Emote server: another session with your Minecraft account connected. Emote sync paused; toggle the module to reconnect.");
               }

               this.onSocketLost(gen, "replaced by another session", -2L);
               break;
            case 4003:
            case 4004:
            case 4005:
            case 4006:
            case 4007:
            default:
               this.onSocketLost(gen, reason);
               break;
            case 4008:
               this.onSocketLost(gen, reason, 10000L);
         }
      }
   }

   private void onSocketLost(int gen, String reason, long retryDelay) {
      if (gen == this.generation) {
         this.generation++;
         this.connecting = false;
         WebSocket socket = this.ws;
         this.ws = null;
         boolean was = this.connected;
         this.connected = false;
         if (socket != null) {
            socket.abort();
         }

         this.clearRemoteState();
         synchronized (this.outQueue) {
            this.outQueue.clear();
         }

         this.lastError = reason;
         this.debugLog("connection lost: {}", reason);
         if (was && this.chatNotices) {
            this.chat(1, "Lost connection to emote server (" + reason + ")" + (retryDelay == -2L ? "." : ", reconnecting..."));
         }

         if (this.wanted && retryDelay != -2L) {
            this.scheduleReconnect(retryDelay);
         }
      }
   }

   private void clearRemoteState() {
      this.remote.clear();
      this.runOnMain(() -> {
         UUID self = ReviveClient.mc.player == null ? null : ReviveClient.mc.player.getUuid();

         for (UUID uuid : EmoteEngine.get().players()) {
            if (!uuid.equals(self)) {
               EmoteEngine.get().stop(uuid);
            }
         }

         this.announced = null;
      });
   }

   private void scheduleReconnect(long delay) {
      if (this.wanted) {
         if (this.reconnectFuture == null || this.reconnectFuture.isDone()) {
            long d = delay;
            if (delay < 0L) {
               d = this.backoffMs;
               this.backoffMs = Math.min(this.backoffMs * 2L, 30000L);
            }

            try {
               this.reconnectFuture = this.scheduler.schedule(() -> {
                  try {
                     this.connectNow();
                  } catch (Throwable var2) {
                     ReviveClient.LOG.warn("[Emotes] connect failed: {}", var2.toString());
                  }
               }, d, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException var6) {
            }
         }
      }
   }

   private void ensureHeartbeat() {
      if (this.heartbeatFuture == null) {
         this.heartbeatFuture = this.scheduler.scheduleWithFixedDelay(() -> {
            try {
               this.heartbeat();
            } catch (Throwable var2) {
               ReviveClient.LOG.warn("[Emotes] heartbeat failed: {}", var2.toString());
            }
         }, 1L, 1L, TimeUnit.SECONDS);
      }
   }

   private void heartbeat() {
      if (this.ws != null) {
         long now = System.currentTimeMillis();
         if (!this.connected) {
            if (now - this.openedAt > 15000L) {
               this.onSocketLost(this.generation, "no welcome");
            }
         } else if (now - this.lastRecvAt > 20000L) {
            this.onSocketLost(this.generation, "timed out");
         } else {
            if (now - this.lastPingSentAt >= 5000L) {
               this.lastPingSentAt = now;
               JsonObject p = msg("ping");
               p.addProperty("id", ++this.pingIds);
               p.addProperty("sent", now);
               this.sendNow(p.toString());
            }

            if (now - this.lastLibraryCheckAt >= 300000L) {
               this.lastLibraryCheckAt = now;
               this.startLibrarySync(true);
            }
         }
      }
   }

   private static void closeQuietly(WebSocket socket) {
      try {
         socket.sendClose(1000, "bye").orTimeout(2L, TimeUnit.SECONDS).whenComplete((w, e) -> socket.abort());
      } catch (Throwable var2) {
         socket.abort();
      }
   }

   @Nullable
   private static String readToken(EmoteSyncManager.ConnectConfig cfg) {
      String override = cfg.tokenOverride.trim();
      if (!override.isEmpty()) {
         return override;
      } else {
         try {
            Path file = FabricLoader.getInstance().getGameDir().resolve("revive-token.txt");
            if (!Files.isRegularFile(file)) {
               return null;
            } else {
               String token = Files.readString(file, StandardCharsets.UTF_8).trim();
               return token.isEmpty() ? null : token;
            }
         } catch (Throwable var4) {
            return null;
         }
      }
   }

   private void reportError(String text) {
      if (!text.equals(this.lastError)) {
         this.lastError = text;
         this.chat(2, text);
      }
   }

   private void enqueue(String json) {
      synchronized (this.outQueue) {
         while (this.outQueue.size() >= 64) {
            this.outQueue.pollFirst();
         }

         this.outQueue.addLast(json);
      }

      if (this.drainScheduled.compareAndSet(false, true)) {
         try {
            this.scheduler.execute(this::drain);
         } catch (RejectedExecutionException var4) {
            this.drainScheduled.set(false);
         }
      }
   }

   private void drain() {
      this.drainScheduled.set(false);

      while (this.ws != null && this.connected) {
         String next;
         synchronized (this.outQueue) {
            next = this.outQueue.pollFirst();
         }

         if (next == null || !this.sendNow(next)) {
            return;
         }
      }
   }

   private boolean sendNow(String json) {
      WebSocket socket = this.ws;
      if (socket == null) {
         return false;
      } else {
         try {
            socket.sendText(json, true).get(5000L, TimeUnit.MILLISECONDS);
            return true;
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            return false;
         } catch (Exception var5) {
            this.onSocketLost(this.generation, "send failed");
            return false;
         }
      }
   }

   private void handleMessage(int gen, String text) {
      JsonElement root = JsonParser.parseString(text);
      if (root.isJsonObject()) {
         JsonObject o = root.getAsJsonObject();
         this.lastRecvAt = System.currentTimeMillis();
         String var5 = str(o, "t", "");
         switch (var5) {
            case "welcome":
               this.onWelcome(gen, o);
               break;
            case "pong":
               this.onPong(o);
               break;
            case "emote":
               this.onEmote(gen, o);
               break;
            case "library":
               this.onLibrary(o);
               break;
            case "notice":
               String m = str(o, "message", "");
               if (!m.isEmpty() && this.chatNotices) {
                  this.chat(0, m);
               }
               break;
            case "error":
               this.onServerError(o);
         }
      }
   }

   private void onWelcome(int gen, JsonObject o) {
      JsonObject you = obj(o, "you");
      this.selfName = you != null ? str(you, "mcName", null) : null;
      this.reviveAccount = you != null ? str(you, "revive", null) : null;
      this.remote.clear();
      JsonArray arr = arr(o, "emotes");
      if (arr != null) {
         for (JsonElement e : arr) {
            if (e.isJsonObject() && this.remote.size() < 1024) {
               JsonObject r = e.getAsJsonObject();
               UUID player = uuid(r, "player");
               UUID emote = uuid(r, "emote");
               if (player != null && emote != null) {
                  this.remote
                     .put(
                        player,
                        new EmoteSyncManager.RemoteEmote(emote, str(r, "name", ""), (float)dbl(r, "tick", 0.0), lng(r, "at", this.serverNow()), bool(r, "loop"))
                     );
               }
            }
         }
      }

      if (gen == this.generation) {
         this.connected = true;
         this.authFailed = false;
         this.lastError = null;
         this.debugLog("welcome received, {} emote(s) running", this.remote.size());
         JsonObject library = obj(o, "library");
         this.libraryVersion = library != null ? str(library, "version", null) : null;
         this.startLibrarySync(false);
         this.post(() -> {
            if (gen == this.generation) {
               this.backoffMs = 1000L;
               this.lastPingSentAt = 0L;
            }
         });
         this.runOnMain(() -> {
            UUID playing = EmoteManager.localPlaying();
            this.announced = null;
            if (playing != null) {
               this.onLocalPlay(playing);
            }
         });
         if (this.chatNotices) {
            this.chat(0, "Connected to emote server" + (this.remote.isEmpty() ? "." : " (" + this.remote.size() + " player(s) emoting)."));
         }
      }
   }

   private void onPong(JsonObject o) {
      long sent = lng(o, "sent", -1L);
      long now = lng(o, "now", -1L);
      if (sent >= 0L && now >= 0L) {
         long rtt = System.currentTimeMillis() - sent;
         if (rtt >= 0L && rtt <= 60000L) {
            long offset = now - (sent + rtt / 2L);
            if (this.rttMs >= 0L && rtt > this.rttMs) {
               this.clockOffset = (this.clockOffset * 3L + offset) / 4L;
            } else {
               this.clockOffset = offset;
            }

            this.rttMs = rtt;
         }
      }
   }

   private void onEmote(int gen, JsonObject o) {
      UUID player = uuid(o, "player");
      UUID emote = uuid(o, "emote");
      String op = str(o, "op", "");
      if (player != null) {
         String name = str(o, "name", "");
         if ("stop".equals(op)) {
            this.remote.remove(player);
            this.runOnMain(() -> {
               if (gen == this.generation) {
                  if (ReviveClient.mc.player == null || !player.equals(ReviveClient.mc.player.getUuid())) {
                     EmoteManager.stop(player, emote);
                  }
               }
            });
         } else if ("start".equals(op) && emote != null) {
            if (this.remote.size() < 1024 || this.remote.containsKey(player)) {
               float tick = (float)dbl(o, "tick", 0.0);
               long at = lng(o, "at", this.serverNow());
               this.remote.put(player, new EmoteSyncManager.RemoteEmote(emote, name, tick, at, bool(o, "loop")));
               this.runOnMain(() -> {
                  if (gen == this.generation && this.showOthers && this.inWorld) {
                     if (ReviveClient.mc.player == null || !player.equals(ReviveClient.mc.player.getUuid())) {
                        if (ReviveClient.mc.world.getPlayerByUuid(player) != null) {
                           float now = tick + (float)(this.serverNow() - at) / 50.0F;
                           if (EmoteManager.isPlaying(player, emote)) {
                              float local = EmoteEngine.get().ticks(player);
                              EmoteLibrary.Emote e = EmoteLibrary.get(emote);
                              if (e != null && Math.abs(e.animation().normalizeTick(now) - local) <= 4.0F) {
                                 return;
                              }
                           }

                           EmoteManager.play(player, emote, now);
                        }
                     }
                  }
               });
            }
         }
      }
   }

   private void onLibrary(JsonObject o) {
      this.libraryVersion = str(o, "version", null);
      this.startLibrarySync(false);
   }

   private void onServerError(JsonObject o) {
      String code = str(o, "code", "");
      String message = str(o, "message", "");
      this.lastError = message.isEmpty() ? code : message;
      if (!message.isEmpty()) {
         switch (code) {
            case "rate":
            case "not_found":
               this.chat(1, message);
               break;
            case "auth":
               this.chat(2, message);
               break;
            default:
               if (this.chatNotices) {
                  this.chat(1, message);
               }
         }
      }
   }

   private void startLibrarySync(boolean force) {
      EmoteSyncManager.ConnectConfig cfg = this.config;
      if (cfg != null) {
         String version = this.libraryVersion;
         if (force || version == null || !version.equals(this.syncedVersion)) {
            this.syncRequests.incrementAndGet();
            if (this.syncing.compareAndSet(false, true)) {
               ReviveExecutor.execute(() -> {
                  try {
                     while (true) {
                        this.syncRequests.set(0);
                        this.syncLibrary(httpBase(cfg.url));
                        if (this.syncRequests.get() > 0) {
                           continue;
                        }
                     }
                  } finally {
                     this.syncing.set(false);
                  }
               });
            }
         }
      }
   }

   private void syncLibrary(String base) {
      byte[] body = getBytes(base + "/api/emotes");
      if (body != null) {
         JsonObject listing;
         try {
            listing = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
         } catch (RuntimeException var24) {
            ReviveClient.LOG.warn("[Emotes] bad library listing: {}", var24.toString());
            return;
         }

         String version = str(listing, "version", "");
         JsonArray emotes = arr(listing, "emotes");
         if (emotes != null) {
            Path dir = EmoteLibrary.serverDir();

            try {
               Files.createDirectories(dir);
            } catch (IOException var23) {
               ReviveClient.LOG.warn("[Emotes] cannot create {}: {}", dir, var23.toString());
               return;
            }

            Set<String> keep = new HashSet<>();
            boolean changed = false;
            boolean complete = true;
            int count = 0;

            for (JsonElement e : emotes) {
               if (!e.isJsonObject() || ++count > 512) {
                  break;
               }

               JsonObject entry = e.getAsJsonObject();
               String file = str(entry, "file", "");
               String sha = str(entry, "sha256", "").toLowerCase(Locale.ROOT);
               String url = str(entry, "url", "");
               if (file.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}\\.json") && sha.length() == 64 && !url.isEmpty()) {
                  keep.add(file);
                  Path target = dir.resolve(file);
                  if (!Files.isRegularFile(target) || !sha.equals(sha256(target))) {
                     String absolute = !url.startsWith("http://") && !url.startsWith("https://") ? base + (url.startsWith("/") ? "" : "/") + url : url;
                     byte[] data = getBytes(absolute);
                     if (data == null || data.length > 524288L) {
                        complete = false;
                     } else if (!sha.equals(sha256(data))) {
                        ReviveClient.LOG.warn("[Emotes] {} did not match its checksum, skipped", file);
                        complete = false;
                     } else {
                        try {
                           Path tmp = dir.resolve(file + ".part");
                           Files.write(tmp, data);
                           Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                           changed = true;
                        } catch (IOException var22) {
                           ReviveClient.LOG.warn("[Emotes] cannot write {}: {}", target, var22.toString());
                           complete = false;
                        }
                     }
                  }
               }
            }

            try (Stream<Path> stream = Files.list(dir)) {
               // NOTE (source reconstruction): Vineflower dropped the cast needed to use a method
               // reference as a for-each target (Iterable is a single-method functional interface).
               for (Path p : (Iterable<Path>) stream::iterator) {
                  String name = p.getFileName().toString();
                  if (name.endsWith(".json") && !keep.contains(name)) {
                     Files.deleteIfExists(p);
                     changed = true;
                  }
               }
            } catch (IOException var26) {
            }

            if (complete) {
               this.syncedVersion = version;
            }

            if (changed) {
               this.debugLog("library synced: {} ({} files)", version, keep.size());
               this.runOnMain(() -> {
                  int n = EmoteLibrary.reload();
                  if (this.chatNotices) {
                     this.chat(0, "Emote library updated from server: " + n + " emotes.");
                  }
               });
            }
         }
      }
   }

   @Nullable
   private static byte[] getBytes(String url) {
      try {
         HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20L)).header("User-Agent", "Revive-Client").GET().build();
         HttpResponse<byte[]> response = HTTP.send(request, BodyHandlers.ofByteArray());
         if (response.statusCode() != 200) {
            ReviveClient.LOG.warn("[Emotes] could not load {}: HTTP {}", url, response.statusCode());
            return null;
         } else {
            return response.body();
         }
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
         return null;
      } catch (Exception var4) {
         ReviveClient.LOG.warn("[Emotes] could not load {}: {}", url, var4.toString());
         return null;
      }
   }

   private static String sha256(byte[] data) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
      } catch (Exception var2) {
         return "";
      }
   }

   private static String sha256(Path file) {
      try {
         return sha256(Files.readAllBytes(file));
      } catch (IOException var2) {
         return "";
      }
   }

   private static JsonObject msg(String type) {
      JsonObject o = new JsonObject();
      o.addProperty("t", type);
      return o;
   }

   private static String str(JsonObject o, String key, String def) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
   }

   private static long lng(JsonObject o, String key, long def) {
      JsonElement e = o.get(key);

      try {
         return e != null && e.isJsonPrimitive() ? e.getAsLong() : def;
      } catch (RuntimeException var6) {
         return def;
      }
   }

   private static double dbl(JsonObject o, String key, double def) {
      JsonElement e = o.get(key);

      try {
         return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
      } catch (RuntimeException var6) {
         return def;
      }
   }

   private static boolean bool(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
   }

   @Nullable
   private static UUID uuid(JsonObject o, String key) {
      String s = str(o, key, null);
      if (s == null) {
         return null;
      } else {
         try {
            return UUID.fromString(s);
         } catch (IllegalArgumentException var4) {
            return null;
         }
      }
   }

   @Nullable
   private static JsonObject obj(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
   }

   @Nullable
   private static JsonArray arr(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
   }

   private static ThreadFactory daemonFactory(String name) {
      return r -> {
         Thread t = new Thread(r, name);
         t.setDaemon(true);
         return t;
      };
   }

   public static enum AuthMode {
      REVIVE,
      KEY;
   }

   private record ChatLine(int level, String text) {
   }

   public static final class ConnectConfig {
      public final String url;
      public final EmoteSyncManager.AuthMode authMode;
      final String key;
      final String tokenOverride;
      @Nullable
      final UUID mcUuid;
      final String mcName;

      public ConnectConfig(String url, EmoteSyncManager.AuthMode authMode, String key, String tokenOverride, @Nullable UUID mcUuid, String mcName) {
         this.url = url;
         this.authMode = authMode;
         this.key = key == null ? "" : key;
         this.tokenOverride = tokenOverride == null ? "" : tokenOverride;
         this.mcUuid = mcUuid;
         this.mcName = mcName == null ? "" : mcName;
      }
   }

   private final class Listener implements WebSocket.Listener {
      private final int gen;
      private final StringBuilder buf = new StringBuilder();
      private boolean overflow;

      Listener(int gen) {
         this.gen = gen;
      }

      @Override
      public void onOpen(WebSocket webSocket) {
         webSocket.request(1L);
      }

      @Override
      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
         if (!this.overflow) {
            if (this.buf.length() + data.length() > 524288) {
               this.overflow = true;
               this.buf.setLength(0);
            } else {
               this.buf.append(data);
            }
         }

         if (last) {
            String text = this.overflow ? null : this.buf.toString();
            this.buf.setLength(0);
            this.overflow = false;
            if (text != null && this.gen == EmoteSyncManager.this.generation) {
               try {
                  EmoteSyncManager.this.handleMessage(this.gen, text);
               } catch (Throwable var6) {
                  EmoteSyncManager.this.debugLog("bad message: {}", var6.toString());
               }
            }
         }

         webSocket.request(1L);
         return null;
      }

      @Override
      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
         String r = reason != null && !reason.isEmpty() ? reason : "closed " + statusCode;
         EmoteSyncManager.this.post(() -> EmoteSyncManager.this.onSocketClosed(this.gen, statusCode, r));
         return null;
      }

      @Override
      public void onError(WebSocket webSocket, Throwable error) {
         String r = error == null ? "error" : error.getClass().getSimpleName();
         EmoteSyncManager.this.post(() -> EmoteSyncManager.this.onSocketLost(this.gen, r));
      }
   }

   public record RemoteEmote(UUID emote, String name, float tick, long at, boolean loop) {
   }
}
