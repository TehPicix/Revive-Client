package revivedevelopment.reviveclient.systems.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent.OpenUrl;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.util.Formatting;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.systems.friends.Friend;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.team.RemoteLogout;
import revivedevelopment.reviveclient.systems.managers.team.RemotePlayer;
import revivedevelopment.reviveclient.systems.managers.team.TeamInfo;
import revivedevelopment.reviveclient.systems.managers.team.TeamInventory;
import revivedevelopment.reviveclient.systems.managers.team.TeamMate;
import revivedevelopment.reviveclient.systems.managers.team.TerrainSampler;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.TeamSync;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.network.ReviveExecutor;

public class TeamSyncManager {
   public static final String DEFAULT_URL = "wss://team.revive-client.com/ws";
   public static final int DEFAULT_SELF_HOST_PORT = 8090;
   private static final int PROTOCOL_VERSION = 1;
   private static final long BACKOFF_MIN_MS = 1000L;
   private static final long BACKOFF_MAX_MS = 30000L;
   private static final long PING_INTERVAL_MS = 5000L;
   private static final long IDLE_TIMEOUT_MS = 20000L;
   private static final long WELCOME_TIMEOUT_MS = 15000L;
   private static final long SEND_TIMEOUT_MS = 5000L;
   private static final long POP_PENDING_MS = 3000L;
   private static final long CONFIG_DEBOUNCE_MS = 1000L;
   private static final int STATE_EVERY_TICKS = 5;
   private static final double SEND_RATE = 20.0;
   private static final double SEND_BURST = 40.0;
   private static final long RATE_CLOSE_RETRY_MS = 10000L;
   private static final double TILE_RATE = 8.0;
   private static final double TILE_BURST = 8.0;
   private static final double TILE_TOKEN_RESERVE = 10.0;
   private static final int MAX_TILE_QUEUE = 8;
   private static final int MAX_OUT_QUEUE = 256;
   private static final int MAX_MAIN_TASKS = 256;
   private static final int MAX_PENDING_POPS = 256;
   private static final int MAX_INVITES = 16;
   private static final int MAX_REMOTE_ENTRIES = 2048;
   private static final int MAX_POP_COUNTS = 4096;
   private static final int MAX_INBOUND_CHARS = 4194304;
   private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("Revive-TeamSync"));
   private final ExecutorService ioPool = Executors.newCachedThreadPool(daemonFactory("Revive-TeamSync-IO"));
   private HttpClient httpClient;
   private WebSocket ws;
   private boolean connecting;
   private long backoffMs = 1000L;
   private ScheduledFuture<?> reconnectFuture;
   private ScheduledFuture<?> heartbeatFuture;
   private long openedAt;
   private long lastPingSentAt;
   private int pingIds;
   private double sendTokens = 40.0;
   private long sendTokensAt;
   private boolean drainRetryPending;
   private volatile int generation;
   private volatile boolean wanted;
   private volatile boolean connected;
   private volatile boolean authFailed;
   private volatile long lastRecvAt;
   private volatile TeamSyncManager.ConnectConfig config;
   private volatile String lastError;
   private final long[][] pingSamples = new long[8][];
   private int pingSampleIdx;
   private volatile long clockOffset;
   private volatile long rttMs = -1L;
   private final ArrayDeque<String> outQueue = new ArrayDeque<>();
   private final AtomicReference<String> latestState = new AtomicReference<>();
   private final AtomicReference<String> latestSeen = new AtomicReference<>();
   private final AtomicBoolean drainScheduled = new AtomicBoolean();
   private final AtomicInteger popIds = new AtomicInteger();
   private final ArrayDeque<String> tileQueue = new ArrayDeque<>();
   private double tileTokens = 8.0;
   private long tileTokensAt;
   private final Object stateLock = new Object();
   private volatile Map<UUID, TeamMate> mates = Collections.emptyMap();
   private volatile Map<UUID, RemotePlayer> remotePlayers = Collections.emptyMap();
   private volatile Map<Integer, String> entityNames = Collections.emptyMap();
   private volatile List<RemoteLogout> logouts = Collections.emptyList();
   private final ConcurrentHashMap<UUID, Integer> serverPops = new ConcurrentHashMap<>();
   private final LinkedHashMap<Integer, TeamSyncManager.PendingPop> pendingPops = new LinkedHashMap<>();
   private volatile TeamInfo team;
   private volatile Set<UUID> teammateUuids = Collections.emptySet();
   private volatile Set<String> teammateNames = Collections.emptySet();
   private volatile List<TeamInfo.Invite> invites = Collections.emptyList();
   private volatile String selfName;
   @Nullable
   private volatile String reviveAccount;
   @Nullable
   private volatile String role;
   private final ArrayDeque<Runnable> mainTasks = new ArrayDeque<>();
   private final ArrayDeque<TeamSyncManager.ChatLine> chatLines = new ArrayDeque<>();
   private TeamSync module;
   private int tickCounter;
   private final TerrainSampler terrain = new TerrainSampler(this::enqueueTile, this::hasTileRoom);
   private volatile int terrainEpoch;
   private int terrainEpochSeen;
   private boolean terrainWasOn;
   private long configDirtyAt;
   private volatile boolean inWorld;
   private volatile boolean sharePosition = true;
   private volatile boolean shareSeen = true;
   private volatile boolean sharePops = true;
   private volatile boolean shareLogouts = true;
   private volatile boolean shareSupplies = true;
   private volatile boolean shareTerrain = true;
   private volatile boolean chatNotices = true;
   private volatile boolean autoFriend = true;
   private volatile boolean debug;

   public TeamSyncManager() {
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   public boolean isConnected() {
      return this.connected;
   }

   public boolean isTeammate(UUID uuid) {
      return uuid != null && this.teammateUuids.contains(uuid);
   }

   public boolean isTeammate(String name) {
      return name != null && this.teammateNames.contains(name.toLowerCase(Locale.ROOT));
   }

   public Collection<TeamMate> getMates() {
      return this.mates.values();
   }

   @Nullable
   public TeamMate getMate(UUID uuid) {
      return uuid == null ? null : this.mates.get(uuid);
   }

   @Nullable
   public String nameForEntityId(int eid) {
      return this.entityNames.get(eid);
   }

   private String serverKey() {
      ServerInfo entry = ReviveClient.mc.getCurrentServerEntry();
      return entry != null && entry.address != null ? entry.address.toLowerCase(Locale.ROOT) : "singleplayer";
   }

   public Collection<RemotePlayer> getRemotePlayers() {
      return this.remotePlayers.values();
   }

   @Nullable
   public RemotePlayer getRemote(UUID uuid) {
      return uuid == null ? null : this.remotePlayers.get(uuid);
   }

   public int getTeamPops(UUID uuid) {
      if (uuid == null) {
         return -1;
      } else {
         Integer server = this.serverPops.get(uuid);
         int pending = 0;
         long now = System.currentTimeMillis();
         synchronized (this.pendingPops) {
            for (TeamSyncManager.PendingPop p : this.pendingPops.values()) {
               if (p.uuid.equals(uuid) && now - p.createdAt < 3000L) {
                  pending++;
               }
            }
         }

         return server == null && pending == 0 ? -1 : (server == null ? 0 : server) + pending;
      }
   }

   public long getTeamOutOfRenderMillis(UUID uuid) {
      RemotePlayer p = this.getRemote(uuid);
      if (p == null) {
         return -1L;
      } else {
         return p.visible ? 0L : p.outMs + Math.max(0L, System.currentTimeMillis() - p.receivedAt);
      }
   }

   public Collection<RemoteLogout> getLogouts() {
      return this.logouts;
   }

   public void reportPop(UUID uuid, String name) {
      if (this.canReport(uuid) && this.sharePops) {
         int id = this.popIds.incrementAndGet();
         synchronized (this.pendingPops) {
            while (this.pendingPops.size() >= 256) {
               Iterator<Integer> it = this.pendingPops.keySet().iterator();
               it.next();
               it.remove();
            }

            this.pendingPops.put(id, new TeamSyncManager.PendingPop(uuid, System.currentTimeMillis()));
         }

         JsonObject o = msg("pop");
         o.addProperty("id", id);
         o.addProperty("uuid", uuid.toString());
         o.addProperty("name", name == null ? "" : name);
         o.addProperty("at", this.serverNow());
         this.enqueue(o.toString());
      }
   }

   public void reportDeath(UUID uuid) {
      if (this.canReport(uuid) && this.sharePops) {
         this.dropPending(uuid);
         JsonObject o = msg("death");
         o.addProperty("uuid", uuid.toString());
         o.addProperty("at", this.serverNow());
         this.enqueue(o.toString());
      }
   }

   public void reportRegear(UUID uuid, String kind) {
      if (this.canReport(uuid) && this.sharePops) {
         this.dropPending(uuid);
         JsonObject o = msg("regear");
         o.addProperty("uuid", uuid.toString());
         o.addProperty("at", this.serverNow());
         o.addProperty("kind", kind == null ? "echest" : kind);
         this.enqueue(o.toString());
      }
   }

   public void reportLogout(UUID uuid, String name, String dim, double x, double y, double z, float hp) {
      if (this.canReport(uuid) && this.shareLogouts) {
         JsonObject o = msg("logout");
         o.addProperty("uuid", uuid.toString());
         o.addProperty("name", name == null ? "" : name);
         o.addProperty("dim", dim == null ? "" : dim);
         o.addProperty("x", round2(x));
         o.addProperty("y", round2(y));
         o.addProperty("z", round2(z));
         o.addProperty("hp", fin(hp));
         o.addProperty("at", this.serverNow());
         this.enqueue(o.toString());
      }
   }

   public void reportLogin(UUID uuid) {
      if (this.canReport(uuid) && this.shareLogouts) {
         JsonObject o = msg("login");
         o.addProperty("uuid", uuid.toString());
         o.addProperty("at", this.serverNow());
         this.enqueue(o.toString());
      }
   }

   public void sendTeamOp(String op, @Nullable String name) {
      if (this.connected && op != null) {
         JsonObject o = msg("team");
         o.addProperty("op", op);
         if (name != null && !name.isEmpty()) {
            o.addProperty("name", name);
         }

         this.enqueue(o.toString());
         if ("accept".equals(op) || "deny".equals(op)) {
            this.removeInvite(name);
         }
      }
   }

   public void requestMapLink() {
      if (this.connected) {
         this.enqueue(msg("maplink").toString());
      }
   }

   public static TeamInventory countSupplies(PlayerEntity player) {
      int crystals = 0;
      int totems = 0;
      int pearls = 0;
      int obsidian = 0;
      int gapples = 0;
      int xp = 0;
      if (player == null) {
         return new TeamInventory(0, 0, 0, 0, 0, 0);
      } else {
         for (int i = 0; i <= 36; i++) {
            ItemStack stack = i < 36 ? player.getInventory().getStack(i) : player.getOffHandStack();
            if (stack != null && !stack.isEmpty()) {
               Item item = stack.getItem();
               int n = stack.getCount();
               if (item == Items.END_CRYSTAL) {
                  crystals += n;
               } else if (item == Items.TOTEM_OF_UNDYING) {
                  totems += n;
               } else if (item == Items.ENDER_PEARL) {
                  pearls += n;
               } else if (item == Items.OBSIDIAN) {
                  obsidian += n;
               } else if (item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.GOLDEN_APPLE) {
                  gapples += n;
               } else if (item == Items.EXPERIENCE_BOTTLE) {
                  xp += n;
               }
            }
         }

         return new TeamInventory(crystals, totems, pearls, obsidian, gapples, xp);
      }
   }

   @Nullable
   public TeamInfo getTeam() {
      return this.team;
   }

   public List<TeamInfo.Invite> getInvites() {
      return this.invites;
   }

   public boolean isEnabled() {
      return this.wanted;
   }

   public long getPingMillis() {
      return this.rttMs;
   }

   public long getClockOffset() {
      return this.clockOffset;
   }

   public long serverNow() {
      return System.currentTimeMillis() + this.clockOffset;
   }

   @Nullable
   public String getServerUrl() {
      TeamSyncManager.ConnectConfig c = this.config;
      return c == null ? null : c.url;
   }

   @Nullable
   public String getLastError() {
      return this.lastError;
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
   public String getRole() {
      return this.role;
   }

   public void markConfigDirty() {
      this.configDirtyAt = System.currentTimeMillis();
   }

   public static String normalizeUrl(String raw) {
      String s = raw == null ? "" : raw.trim();
      if (s.isEmpty()) {
         return "wss://team.revive-client.com/ws";
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
            return "wss://team.revive-client.com/ws";
         } else {
            boolean hasPort = hostPort.matches(".*:\\d{1,5}$") && (!hostPort.startsWith("[") || hostPort.contains("]:"));
            if (scheme == null) {
               String host = hasPort ? hostPort.substring(0, hostPort.lastIndexOf(58)) : hostPort;
               boolean local = isIpOrLocal(host);
               scheme = !hasPort && !local ? "wss" : "ws";
               if (!hasPort && local) {
                  hostPort = hostPort + ":8090";
               }
            }

            if (path.isEmpty() || path.equals("/")) {
               path = "/ws";
            }

            return scheme + "://" + hostPort + path;
         }
      }
   }

   private static boolean isIpOrLocal(String host) {
      return host.equalsIgnoreCase("localhost") || host.startsWith("[") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      TeamSync m = this.module();
      boolean active = m != null && m.isActive();
      this.inWorld = Utils.canUpdate();
      if (m != null) {
         this.sharePosition = m.sharePosition.get();
         this.shareSeen = m.shareSeenPlayers.get();
         this.sharePops = m.sharePops.get();
         this.shareLogouts = m.shareLogouts.get();
         this.shareSupplies = m.shareSupplies.get();
         this.shareTerrain = m.shareTerrain.get();
         this.chatNotices = m.chatNotices.get();
         this.autoFriend = m.autoFriend.get();
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
      this.prunePendingPops();
      if (this.connected && this.inWorld && ++this.tickCounter >= 5) {
         this.tickCounter = 0;
         this.gatherWorldState();
      }

      this.tickTerrain();
   }

   private void tickTerrain() {
      boolean on = this.connected && this.inWorld && this.shareTerrain && this.team != null;
      int epoch = this.terrainEpoch;
      if (on != this.terrainWasOn || epoch != this.terrainEpochSeen) {
         this.terrain.reset();
         this.terrainEpochSeen = epoch;
      }

      this.terrainWasOn = on;
      if (on) {
         this.terrain
            .tick(
               ReviveClient.mc.world,
               ReviveClient.mc.player,
               ReviveClient.mc.world.getRegistryKey().getValue().toString(),
               (Integer)ReviveClient.mc.options.getViewDistance().getValue()
            );
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.inWorld = false;
      this.latestState.set(null);
      this.latestSeen.set(null);
      if (this.connected) {
         this.enqueue(msg("leaveworld").toString());
      }

      this.terrain.reset();
      synchronized (this.tileQueue) {
         this.tileQueue.clear();
      }

      synchronized (this.stateLock) {
         this.mates = Collections.emptyMap();
         this.remotePlayers = Collections.emptyMap();
         this.entityNames = Collections.emptyMap();
      }

      synchronized (this.pendingPops) {
         this.pendingPops.clear();
      }
   }

   private void gatherWorldState() {
      String dim = ReviveClient.mc.world.getRegistryKey().getValue().toString();
      PlayerEntity self = ReviveClient.mc.player;
      if (this.sharePosition) {
         JsonObject s = msg("state");
         s.addProperty("dim", dim);
         s.addProperty("x", round2(self.getX()));
         s.addProperty("y", round2(self.getY()));
         s.addProperty("z", round2(self.getZ()));
         s.addProperty("yaw", round2(self.getYaw()));
         s.addProperty("hp", fin(self.getHealth()));
         s.addProperty("absorb", fin(self.getAbsorptionAmount()));
         s.addProperty("eid", self.getId());
         s.addProperty("server", this.serverKey());
         if (this.shareSupplies) {
            TeamInventory supplies = countSupplies(self);
            JsonObject inv = new JsonObject();
            inv.addProperty("crystals", supplies.crystals);
            inv.addProperty("totems", supplies.totems);
            inv.addProperty("pearls", supplies.pearls);
            inv.addProperty("obsidian", supplies.obsidian);
            inv.addProperty("gapples", supplies.gapples);
            inv.addProperty("xp", supplies.xp);
            s.add("inv", inv);
         }

         this.latestState.set(s.toString());
      }

      JsonArray players = new JsonArray();
      if (this.shareSeen) {
         for (PlayerEntity p : ReviveClient.mc.world.getPlayers()) {
            if (p != self && !p.getUuid().equals(self.getUuid())) {
               JsonObject e = new JsonObject();
               e.addProperty("uuid", p.getUuid().toString());
               e.addProperty("name", p.getName().getString());
               e.addProperty("x", round2(p.getX()));
               e.addProperty("y", round2(p.getY()));
               e.addProperty("z", round2(p.getZ()));
               e.addProperty("yaw", round2(p.getYaw()));
               e.addProperty("hp", fin(p.getHealth()));
               e.addProperty("absorb", fin(p.getAbsorptionAmount()));
               e.addProperty("eid", p.getId());
               players.add(e);
               if (players.size() >= 256) {
                  break;
               }
            }
         }
      }

      JsonObject seen = msg("seen");
      seen.addProperty("dim", dim);
      seen.addProperty("server", this.serverKey());
      seen.add("players", players);
      this.latestSeen.set(seen.toString());
      this.requestDrain();
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

            TeamSync m = this.module();

            while (true) {
               TeamSyncManager.ChatLine line;
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
            ReviveClient.LOG.warn("[TeamSync] main-thread task failed: {}", var6.toString());
         }
      }
   }

   private void prunePendingPops() {
      long now = System.currentTimeMillis();
      synchronized (this.pendingPops) {
         if (!this.pendingPops.isEmpty()) {
            this.pendingPops.values().removeIf(p -> now - p.createdAt >= 3000L);
         }
      }
   }

   @Nullable
   private TeamSync module() {
      if (this.module == null) {
         Modules modules = Modules.get();
         if (modules != null) {
            this.module = modules.get(TeamSync.class);
         }
      }

      return this.module;
   }

   private void runOnMain(Runnable r) {
      synchronized (this.mainTasks) {
         while (this.mainTasks.size() >= 256) {
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

         this.chatLines.addLast(new TeamSyncManager.ChatLine(level, text));
      }
   }

   private void debugLog(String message, Object... args) {
      if (this.debug) {
         ReviveClient.LOG.info("[TeamSync] " + message, args);
      }
   }

   private void post(Runnable r) {
      try {
         this.scheduler.execute(() -> {
            try {
               r.run();
            } catch (Throwable var2) {
               ReviveClient.LOG.warn("[TeamSync] task failed: {}", var2.toString());
            }
         });
      } catch (RejectedExecutionException var3) {
      }
   }

   private void connectNow() {
      this.reconnectFuture = null;
      if (this.wanted && this.ws == null && !this.connecting) {
         TeamSyncManager.ConnectConfig cfg = this.config;
         if (cfg != null) {
            URI uri;
            try {
               uri = URI.create(cfg.url);
               if (uri.getHost() == null) {
                  throw new IllegalArgumentException("no host");
               }
            } catch (IllegalArgumentException var7) {
               this.reportError("Invalid server-url: " + cfg.url);
               return;
            }

            if (cfg.mcUuid != null && !cfg.mcName.isEmpty()) {
               String credential;
               if (cfg.authMode == TeamSyncManager.AuthMode.REVIVE) {
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
                  if (this.httpClient == null) {
                     this.httpClient = HttpClient.newBuilder().executor(this.ioPool).connectTimeout(Duration.ofSeconds(10L)).build();
                  }

                  String cred = credential;
                  this.httpClient
                     .newWebSocketBuilder()
                     .connectTimeout(Duration.ofSeconds(10L))
                     .buildAsync(uri, new TeamSyncManager.Listener(gen))
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

   private void onConnectResult(int gen, WebSocket socket, Throwable err, TeamSyncManager.ConnectConfig cfg, String credential) {
      if (gen == this.generation && this.wanted) {
         this.connecting = false;
         if (err == null && socket != null) {
            this.ws = socket;
            long now = System.currentTimeMillis();
            this.openedAt = now;
            this.lastRecvAt = now;
            this.lastPingSentAt = 0L;
            this.sendTokens = 40.0;
            this.sendTokensAt = System.nanoTime();
            this.tileTokens = 8.0;
            this.tileTokensAt = this.sendTokensAt;
            JsonObject hello = msg("hello");
            hello.addProperty("v", 1);
            JsonObject auth = new JsonObject();
            if (cfg.authMode == TeamSyncManager.AuthMode.REVIVE) {
               auth.addProperty("token", credential);
            } else {
               auth.addProperty("key", credential);
            }

            hello.add("auth", auth);
            hello.addProperty("mcUuid", cfg.mcUuid.toString());
            hello.addProperty("mcName", cfg.mcName);
            hello.addProperty("client", "revive/" + ReviveClient.VERSION);
            this.spendSendToken();
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
      if (!this.wanted) {
         synchronized (this.stateLock) {
            this.team = null;
            this.teammateUuids = Collections.emptySet();
            this.teammateNames = Collections.emptySet();
         }
      }

      synchronized (this.outQueue) {
         this.outQueue.clear();
      }

      synchronized (this.tileQueue) {
         this.tileQueue.clear();
      }

      this.latestState.set(null);
      this.latestSeen.set(null);
      this.debugLog("teardown: {}", reason);
      if (was && this.chatNotices && !"module disabled".equals(reason)) {
         this.chat(1, "Disconnected from team server (" + reason + ").");
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
                  this.chat(2, "Team server: another session with your Minecraft account connected. Team Sync paused; toggle the module to reconnect.");
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

         synchronized (this.tileQueue) {
            this.tileQueue.clear();
         }

         this.latestState.set(null);
         this.latestSeen.set(null);
         this.lastError = reason;
         this.debugLog("connection lost: {}", reason);
         if (was && this.chatNotices) {
            this.chat(1, "Lost connection to team server (" + reason + ")" + (retryDelay == -2L ? "." : ", reconnecting..."));
         }

         if (this.wanted && retryDelay != -2L) {
            this.scheduleReconnect(retryDelay);
         }
      }
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
                     ReviveClient.LOG.warn("[TeamSync] connect failed: {}", var2.toString());
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
               ReviveClient.LOG.warn("[TeamSync] heartbeat failed: {}", var2.toString());
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
               this.spendSendToken();
               this.sendNow(p.toString());
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
   private static String readToken(TeamSyncManager.ConnectConfig cfg) {
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
         while (this.outQueue.size() >= 256) {
            this.outQueue.pollFirst();
         }

         this.outQueue.addLast(json);
      }

      this.requestDrain();
   }

   private void enqueueTile(String json) {
      if (this.connected) {
         synchronized (this.tileQueue) {
            while (this.tileQueue.size() >= 8) {
               this.tileQueue.pollFirst();
            }

            this.tileQueue.addLast(json);
         }

         this.requestDrain();
      }
   }

   private boolean hasTileRoom() {
      synchronized (this.tileQueue) {
         return this.tileQueue.size() < 8;
      }
   }

   private void requestDrain() {
      if (this.drainScheduled.compareAndSet(false, true)) {
         try {
            this.scheduler.execute(this::drain);
         } catch (RejectedExecutionException var2) {
            this.drainScheduled.set(false);
         }
      }
   }

   private void drain() {
      this.drainScheduled.set(false);
      if (this.ws != null && this.connected) {
         if (this.sendLatest(this.latestState) && this.sendLatest(this.latestSeen)) {
            while (this.ws != null && this.connected) {
               String next;
               synchronized (this.outQueue) {
                  if (this.outQueue.isEmpty()) {
                     break;
                  }

                  if (!this.takeSendToken()) {
                     this.retryDrainLater();
                     return;
                  }

                  next = this.outQueue.pollFirst();
               }

               if (!this.sendNow(next)) {
                  return;
               }
            }

            this.drainTiles();
         }
      }
   }

   private void drainTiles() {
      while (this.ws != null && this.connected) {
         String next;
         synchronized (this.tileQueue) {
            if (this.tileQueue.isEmpty()) {
               return;
            }

            long now = System.nanoTime();
            if (this.tileTokensAt != 0L) {
               this.tileTokens = Math.min(8.0, this.tileTokens + (now - this.tileTokensAt) / 1.0E9 * 8.0);
            }

            this.tileTokensAt = now;
            this.refillSendTokens();
            if (this.tileTokens < 1.0) {
               this.retryDrainLater(Math.max(10L, (long)Math.ceil((1.0 - this.tileTokens) / 8.0 * 1000.0)));
               return;
            }

            if (this.sendTokens < 11.0) {
               this.retryDrainLater(Math.max(10L, (long)Math.ceil((11.0 - this.sendTokens) / 20.0 * 1000.0)));
               return;
            }

            this.tileTokens--;
            this.sendTokens--;
            next = this.tileQueue.pollFirst();
         }

         if (!this.sendNow(next)) {
            return;
         }
      }
   }

   private boolean sendLatest(AtomicReference<String> slot) {
      String s = slot.getAndSet(null);
      if (s == null) {
         return true;
      } else if (!this.takeSendToken()) {
         slot.compareAndSet(null, s);
         this.retryDrainLater();
         return false;
      } else {
         return this.sendNow(s);
      }
   }

   private boolean takeSendToken() {
      this.refillSendTokens();
      if (this.sendTokens < 1.0) {
         return false;
      } else {
         this.sendTokens--;
         return true;
      }
   }

   private void spendSendToken() {
      this.refillSendTokens();
      this.sendTokens--;
   }

   private void refillSendTokens() {
      long now = System.nanoTime();
      if (this.sendTokensAt != 0L) {
         this.sendTokens = Math.min(40.0, this.sendTokens + (now - this.sendTokensAt) / 1.0E9 * 20.0);
      }

      this.sendTokensAt = now;
   }

   private void retryDrainLater() {
      this.retryDrainLater(Math.max(10L, (long)Math.ceil((1.0 - this.sendTokens) / 20.0 * 1000.0)));
   }

   private void retryDrainLater(long waitMs) {
      if (!this.drainRetryPending) {
         this.drainRetryPending = true;

         try {
            this.scheduler.schedule(() -> {
               this.drainRetryPending = false;
               this.requestDrain();
            }, waitMs, TimeUnit.MILLISECONDS);
         } catch (RejectedExecutionException var4) {
            this.drainRetryPending = false;
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
            case "snapshot":
               this.onSnapshot(gen, o);
               break;
            case "team":
               this.onTeam(gen, (JsonElement)(o.has("team") ? o.get("team") : (o.has("members") ? o : null)));
               break;
            case "invite":
               this.onInvite(gen, o);
               break;
            case "notice":
               String m = str(o, "message", "");
               if (!m.isEmpty() && this.chatNotices) {
                  this.chat(0, m);
               }
               break;
            case "error":
               this.onServerError(o);
               break;
            case "pops":
               this.onPops(gen, o);
               break;
            case "logouts":
               this.onLogouts(gen, o);
               break;
            case "maplink":
               this.onMapLink(gen, o);
         }
      }
   }

   private void onWelcome(int gen, JsonObject o) {
      JsonObject you = obj(o, "you");
      String name = you != null ? str(you, "mcName", null) : null;
      String revive = you != null ? str(you, "revive", null) : null;
      String r = you != null ? str(you, "role", null) : null;
      List<TeamInfo.Invite> inv = new ArrayList<>();
      JsonArray arr = arr(o, "invites");
      if (arr != null) {
         for (JsonElement e : arr) {
            if (e.isJsonObject()) {
               TeamInfo.Invite i = parseInvite(e.getAsJsonObject());
               if (i != null && inv.size() < 16) {
                  inv.add(i);
               }
            }
         }
      }

      synchronized (this.stateLock) {
         if (gen != this.generation) {
            return;
         }

         this.selfName = name;
         this.reviveAccount = revive;
         this.role = r;
         this.invites = Collections.unmodifiableList(inv);
         this.connected = true;
         this.authFailed = false;
         this.lastError = null;
      }

      this.terrainEpoch++;
      this.onTeam(gen, o.get("team"));
      this.post(() -> {
         if (gen == this.generation) {
            this.backoffMs = 1000L;
            this.lastPingSentAt = 0L;
         }
      });
      this.requestDrain();
      this.debugLog("welcome received");
      if (this.chatNotices) {
         TeamInfo t = this.team;
         this.chat(0, t != null ? "Connected. Team: " + t.name + " (" + t.members.size() + " members)." : "Connected to team server. Not in a team.");
      }

      for (TeamInfo.Invite i : inv) {
         this.chat(0, i.from + " invited you to team " + i.team + ". Use .team accept " + i.from);
      }
   }

   private void onPong(JsonObject o) {
      long sent = lng(o, "sent", -1L);
      long now = lng(o, "now", -1L);
      if (sent >= 0L && now >= 0L) {
         long rtt = System.currentTimeMillis() - sent;
         if (rtt >= 0L && rtt <= 60000L) {
            long offset = now - (sent + rtt / 2L);
            synchronized (this.pingSamples) {
               this.pingSamples[this.pingSampleIdx] = new long[]{rtt, offset};
               this.pingSampleIdx = (this.pingSampleIdx + 1) % this.pingSamples.length;
               long[] best = null;

               for (long[] s : this.pingSamples) {
                  if (s != null && (best == null || s[0] < best[0])) {
                     best = s;
                  }
               }

               if (best != null) {
                  this.clockOffset = best[1];
               }
            }

            this.rttMs = rtt;
         }
      }
   }

   private static void collectEntityName(Map<Integer, String> into, JsonObject e, String here) {
      int eid = intv(e, "eid", -1);
      String name = str(e, "name", "");
      if (eid >= 0 && !name.isEmpty() && here.equals(str(e, "server", ""))) {
         into.put(eid, name);
      }
   }

   private void onSnapshot(int gen, JsonObject o) {
      if (this.inWorld) {
         long localNow = System.currentTimeMillis();
         Map<UUID, TeamMate> newMates = new LinkedHashMap<>();
         Map<Integer, String> newEntityNames = new HashMap<>();
         String here = this.serverKey();
         JsonArray ma = arr(o, "mates");
         if (ma != null) {
            for (JsonElement e : ma) {
               if (e.isJsonObject() && newMates.size() < 2048) {
                  JsonObject m = e.getAsJsonObject();
                  UUID uuid = uuid(m, "uuid");
                  if (uuid != null) {
                     TeamInventory inv = null;
                     JsonObject io = obj(m, "inv");
                     if (io != null) {
                        inv = new TeamInventory(
                           intv(io, "crystals", 0),
                           intv(io, "totems", 0),
                           intv(io, "pearls", 0),
                           intv(io, "obsidian", 0),
                           intv(io, "gapples", 0),
                           intv(io, "xp", 0)
                        );
                     }

                     collectEntityName(newEntityNames, m, here);
                     newMates.put(
                        uuid,
                        new TeamMate(
                           uuid,
                           str(m, "name", ""),
                           str(m, "dim", ""),
                           dbl(m, "x", 0.0),
                           dbl(m, "y", 0.0),
                           dbl(m, "z", 0.0),
                           (float)dbl(m, "yaw", 0.0),
                           (float)dbl(m, "hp", 0.0),
                           (float)dbl(m, "absorb", 0.0),
                           inv,
                           localNow - Math.max(0L, lng(m, "age", 0L))
                        )
                     );
                  }
               }
            }
         }

         Map<UUID, RemotePlayer> newPlayers = new LinkedHashMap<>();
         Map<UUID, Integer> popUpdates = new LinkedHashMap<>();
         JsonArray pa = arr(o, "players");
         if (pa != null) {
            for (JsonElement ex : pa) {
               if (ex.isJsonObject() && newPlayers.size() < 2048) {
                  JsonObject p = ex.getAsJsonObject();
                  UUID uuid = uuid(p, "uuid");
                  if (uuid != null) {
                     List<String> seenBy = new ArrayList<>();
                     JsonArray sb = arr(p, "seenBy");
                     if (sb != null) {
                        for (JsonElement s : sb) {
                           if (s.isJsonPrimitive() && seenBy.size() < 32) {
                              seenBy.add(s.getAsString());
                           }
                        }
                     }

                     collectEntityName(newEntityNames, p, here);
                     int pops = intv(p, "pops", -1);
                     if (pops >= 0) {
                        popUpdates.put(uuid, pops);
                     }

                     newPlayers.put(
                        uuid,
                        new RemotePlayer(
                           uuid,
                           str(p, "name", ""),
                           str(p, "dim", ""),
                           dbl(p, "x", 0.0),
                           dbl(p, "y", 0.0),
                           dbl(p, "z", 0.0),
                           (float)dbl(p, "yaw", 0.0),
                           (float)dbl(p, "hp", 0.0),
                           (float)dbl(p, "absorb", 0.0),
                           Collections.unmodifiableList(seenBy),
                           bool(p, "visible", false),
                           Math.max(0L, lng(p, "outMs", 0L)),
                           Math.max(0, pops),
                           localNow - Math.max(0L, lng(p, "age", 0L))
                        )
                     );
                  }
               }
            }
         }

         synchronized (this.stateLock) {
            if (gen == this.generation) {
               this.mates = Collections.unmodifiableMap(newMates);
               this.remotePlayers = Collections.unmodifiableMap(newPlayers);
               this.entityNames = Collections.unmodifiableMap(newEntityNames);
               if (!popUpdates.isEmpty()) {
                  Set<UUID> pendingTargets = new HashSet<>();
                  synchronized (this.pendingPops) {
                     for (TeamSyncManager.PendingPop p : this.pendingPops.values()) {
                        pendingTargets.add(p.uuid);
                     }
                  }

                  for (Entry<UUID, Integer> en : popUpdates.entrySet()) {
                     if (!pendingTargets.contains(en.getKey())) {
                        this.putPopCount(en.getKey(), en.getValue());
                     }
                  }
               }
            }
         }
      }
   }

   private void onTeam(int gen, @Nullable JsonElement el) {
      TeamInfo t = el != null && el.isJsonObject() ? parseTeam(el.getAsJsonObject()) : null;
      Set<UUID> uuids = new HashSet<>();
      Set<String> names = new HashSet<>();
      if (t != null) {
         for (TeamInfo.Member m : t.members) {
            if (m.uuid != null) {
               uuids.add(m.uuid);
            }

            if (!m.name.isEmpty()) {
               names.add(m.name.toLowerCase(Locale.ROOT));
            }
         }
      }

      TeamInfo old = this.team;
      synchronized (this.stateLock) {
         if (gen != this.generation) {
            return;
         }

         if (t != null && (old == null || !old.id.equals(t.id))) {
            this.terrainEpoch++;
         }

         this.team = t;
         this.teammateUuids = Collections.unmodifiableSet(uuids);
         this.teammateNames = Collections.unmodifiableSet(names);
         if (t == null) {
            this.mates = Collections.emptyMap();
         }
      }

      if (t != null && this.autoFriend) {
         this.runOnMain(() -> this.addFriends(t));
      }
   }

   private void onInvite(int gen, JsonObject o) {
      TeamInfo.Invite i = parseInvite(o);
      if (i != null) {
         synchronized (this.stateLock) {
            if (gen != this.generation) {
               return;
            }

            List<TeamInfo.Invite> list = new ArrayList<>();

            for (TeamInfo.Invite old : this.invites) {
               if (!old.from.equalsIgnoreCase(i.from)) {
                  list.add(old);
               }
            }

            while (list.size() >= 16) {
               list.remove(0);
            }

            list.add(i);
            this.invites = Collections.unmodifiableList(list);
         }

         this.chat(0, i.from + " invited you to team " + i.team + ". Use .team accept " + i.from);
      }
   }

   private void onServerError(JsonObject o) {
      String code = str(o, "code", "error");
      String message = str(o, "message", "");
      if ("forbidden".equals(code)) {
         this.chat(2, "Combat map: your Revive account role doesn't have map access" + (message.isEmpty() ? "." : " (" + message + ")."));
      } else if ("map_disabled".equals(code)) {
         this.chat(2, "Combat map: this team server has the map turned off.");
      } else {
         boolean repeatAuth = "auth".equals(code) && this.authFailed;
         if ("auth".equals(code)) {
            this.authFailed = true;
         }

         this.lastError = code + (message.isEmpty() ? "" : ": " + message);
         if (!repeatAuth) {
            this.chat(2, "Team server: " + (message.isEmpty() ? code : message) + " [" + code + "]");
         }
      }
   }

   private void onPops(int gen, JsonObject o) {
      UUID uuid = uuid(o, "uuid");
      int count = intv(o, "count", -1);
      JsonArray acks = arr(o, "acks");
      synchronized (this.stateLock) {
         if (gen != this.generation) {
            return;
         }

         if (uuid != null && count >= 0) {
            this.putPopCount(uuid, count);
         }
      }

      if (acks != null) {
         synchronized (this.pendingPops) {
            for (JsonElement a : acks) {
               if (a.isJsonPrimitive()) {
                  try {
                     this.pendingPops.remove(a.getAsInt());
                  } catch (NumberFormatException var11) {
                  }
               }
            }
         }
      }
   }

   private void onLogouts(int gen, JsonObject o) {
      long offset = this.clockOffset;
      List<RemoteLogout> list = new ArrayList<>();
      JsonArray spots = arr(o, "spots");
      if (spots != null) {
         for (JsonElement e : spots) {
            if (e.isJsonObject() && list.size() < 2048) {
               JsonObject s = e.getAsJsonObject();
               UUID uuid = uuid(s, "uuid");
               if (uuid != null) {
                  list.add(
                     new RemoteLogout(
                        uuid,
                        str(s, "name", ""),
                        str(s, "dim", ""),
                        dbl(s, "x", 0.0),
                        dbl(s, "y", 0.0),
                        dbl(s, "z", 0.0),
                        (float)dbl(s, "hp", 0.0),
                        Math.max(0, intv(s, "pops", 0)),
                        lng(s, "at", this.serverNow()) - offset,
                        str(s, "by", "")
                     )
                  );
               }
            }
         }
      }

      synchronized (this.stateLock) {
         if (gen == this.generation) {
            this.logouts = Collections.unmodifiableList(list);
         }
      }
   }

   private void onMapLink(int gen, JsonObject o) {
      if (gen == this.generation) {
         String url = str(o, "url", "");
         long expires = lng(o, "expires", 0L);
         if (expires > 0L && expires < 100000000000L) {
            expires *= 1000L;
         }

         long left = expires > 0L ? expires - this.serverNow() : -1L;
         long minutes = left > 0L && left < 86400000L ? Math.max(1L, Math.round(left / 60000.0)) : 5L;
         if (url.isEmpty()) {
            this.chat(2, "Team server sent an empty map link.");
         } else {
            this.runOnMain(() -> this.showMapLink(url, minutes));
         }
      }
   }

   private void showMapLink(String url, long minutes) {
      URI uri;
      try {
         uri = URI.create(url);
      } catch (IllegalArgumentException var9) {
         this.chat(2, "Team server sent an invalid map link.");
         return;
      }

      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      TeamSync m = this.module();
      if (this.inWorld && m != null && (scheme.equals("https") || scheme.equals("http"))) {
         MutableText link = Text.literal("[Open combat map]")
            .formatted(new Formatting[]{Formatting.AQUA, Formatting.UNDERLINE})
            .styled(style -> style.withClickEvent(new OpenUrl(uri)).withHoverEvent(new ShowText(Text.literal(url).formatted(Formatting.GRAY))));
         MutableText text = Text.literal("Combat map: ")
            .append(link)
            .append(Text.literal(" (one-time link, valid for " + minutes + " min, don't share it)").formatted(Formatting.GRAY));
         m.info(text);
      } else {
         this.chat(0, "Combat map: " + url);
      }
   }

   private void putPopCount(UUID uuid, int count) {
      if (this.serverPops.size() < 4096 || this.serverPops.containsKey(uuid)) {
         this.serverPops.put(uuid, count);
      }
   }

   private void clearRemoteState() {
      synchronized (this.stateLock) {
         this.mates = Collections.emptyMap();
         this.remotePlayers = Collections.emptyMap();
         this.entityNames = Collections.emptyMap();
         this.logouts = Collections.emptyList();
         this.serverPops.clear();
         this.invites = Collections.emptyList();
      }

      synchronized (this.pendingPops) {
         this.pendingPops.clear();
      }
   }

   private void addFriends(TeamInfo t) {
      if (this.autoFriend) {
         Friends friends = Friends.get();
         if (friends != null) {
            String self = this.selfName != null
               ? this.selfName
               : (ReviveClient.mc != null && ReviveClient.mc.getSession() != null ? ReviveClient.mc.getSession().getUsername() : null);
            int added = 0;

            for (TeamInfo.Member m : t.members) {
               if (!m.name.isEmpty() && !m.name.contains(" ") && (self == null || !m.name.equalsIgnoreCase(self)) && friends.get(m.name) == null) {
                  Friend friend = new Friend(m.name, m.uuid);
                  if (friends.add(friend)) {
                     ReviveExecutor.execute(friend::updateInfo);
                     added++;
                  }
               }
            }

            if (added > 0 && this.chatNotices) {
               this.chat(0, "Added " + added + " teammate(s) to friends.");
            }
         }
      }
   }

   private boolean canReport(UUID uuid) {
      if (uuid == null || !this.connected || this.team == null) {
         return false;
      } else if (this.isTeammate(uuid)) {
         return false;
      } else {
         TeamSyncManager.ConnectConfig c = this.config;
         return c == null || !uuid.equals(c.mcUuid);
      }
   }

   private void dropPending(UUID uuid) {
      synchronized (this.pendingPops) {
         this.pendingPops.values().removeIf(p -> p.uuid.equals(uuid));
      }
   }

   private void removeInvite(@Nullable String from) {
      synchronized (this.stateLock) {
         if (!this.invites.isEmpty()) {
            if (from != null && !from.isEmpty()) {
               List<TeamInfo.Invite> list = new ArrayList<>();

               for (TeamInfo.Invite i : this.invites) {
                  if (!i.from.equalsIgnoreCase(from)) {
                     list.add(i);
                  }
               }

               this.invites = Collections.unmodifiableList(list);
            } else {
               if (this.invites.size() == 1) {
                  this.invites = Collections.emptyList();
               }
            }
         }
      }
   }

   @Nullable
   private static TeamInfo parseTeam(JsonObject o) {
      List<TeamInfo.Member> members = new ArrayList<>();
      JsonArray arr = arr(o, "members");
      if (arr != null) {
         for (JsonElement e : arr) {
            if (e.isJsonObject() && members.size() < 64) {
               JsonObject m = e.getAsJsonObject();
               members.add(new TeamInfo.Member(uuid(m, "uuid"), str(m, "name", ""), bool(m, "online", false), str(m, "revive", null)));
            }
         }
      }

      return new TeamInfo(str(o, "id", ""), str(o, "name", ""), str(o, "leader", ""), Collections.unmodifiableList(members));
   }

   @Nullable
   private static TeamInfo.Invite parseInvite(JsonObject o) {
      String from = str(o, "from", "");
      if (from.isEmpty()) {
         return null;
      } else {
         JsonElement t = o.get("team");
         String teamName;
         if (t != null && t.isJsonObject()) {
            teamName = str(t.getAsJsonObject(), "name", "");
         } else {
            teamName = str(o, "team", "");
         }

         return new TeamInfo.Invite(from, uuid(o, "fromUuid"), teamName);
      }
   }

   private static JsonObject msg(String type) {
      JsonObject o = new JsonObject();
      o.addProperty("t", type);
      return o;
   }

   private static float fin(float v) {
      return Float.isFinite(v) ? v : 0.0F;
   }

   private static double round2(double v) {
      return Math.round(v * 100.0) / 100.0;
   }

   private static String str(JsonObject o, String key, String def) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
   }

   private static double dbl(JsonObject o, String key, double def) {
      try {
         JsonElement e = o.get(key);
         return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
      } catch (NumberFormatException var5) {
         return def;
      }
   }

   private static long lng(JsonObject o, String key, long def) {
      try {
         JsonElement e = o.get(key);
         return e != null && e.isJsonPrimitive() ? e.getAsLong() : def;
      } catch (NumberFormatException var5) {
         return def;
      }
   }

   private static int intv(JsonObject o, String key, int def) {
      try {
         JsonElement e = o.get(key);
         return e != null && e.isJsonPrimitive() ? e.getAsInt() : def;
      } catch (NumberFormatException var4) {
         return def;
      }
   }

   private static boolean bool(JsonObject o, String key, boolean def) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
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

   @Nullable
   private static UUID uuid(JsonObject o, String key) {
      String s = str(o, key, null);
      if (s == null) {
         return null;
      } else {
         s = s.trim();

         try {
            if (s.length() == 32) {
               s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20);
            }

            return UUID.fromString(s);
         } catch (IllegalArgumentException var4) {
            return null;
         }
      }
   }

   private static ThreadFactory daemonFactory(String name) {
      AtomicInteger n = new AtomicInteger();
      return r -> {
         Thread t = new Thread(r, name + "-" + n.incrementAndGet());
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
      public final TeamSyncManager.AuthMode authMode;
      final String key;
      final String tokenOverride;
      @Nullable
      final UUID mcUuid;
      final String mcName;

      public ConnectConfig(String url, TeamSyncManager.AuthMode authMode, String key, String tokenOverride, @Nullable UUID mcUuid, String mcName) {
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
            if (this.buf.length() + data.length() > 4194304) {
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
            if (text != null && this.gen == TeamSyncManager.this.generation) {
               try {
                  TeamSyncManager.this.handleMessage(this.gen, text);
               } catch (Throwable var6) {
                  TeamSyncManager.this.debugLog("bad message: {}", var6.toString());
               }
            }
         }

         webSocket.request(1L);
         return null;
      }

      @Override
      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
         String r = reason != null && !reason.isEmpty() ? reason : "closed " + statusCode;
         TeamSyncManager.this.post(() -> TeamSyncManager.this.onSocketClosed(this.gen, statusCode, r));
         return null;
      }

      @Override
      public void onError(WebSocket webSocket, Throwable error) {
         String r = error == null ? "error" : error.getClass().getSimpleName();
         TeamSyncManager.this.post(() -> TeamSyncManager.this.onSocketLost(this.gen, r));
      }
   }

   private record PendingPop(UUID uuid, long createdAt) {
   }
}
