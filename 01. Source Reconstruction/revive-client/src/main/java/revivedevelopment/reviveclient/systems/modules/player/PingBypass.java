package revivedevelopment.reviveclient.systems.modules.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.exceptions.AuthenticationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.session.Session;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.consume.UseAction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.entity.player.StartBreakingBlockEvent;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.pingbypass.PbCrypto;
import revivedevelopment.reviveclient.pingbypass.PbFakes;
import revivedevelopment.reviveclient.pingbypass.PbFx;
import revivedevelopment.reviveclient.pingbypass.PbModules;
import revivedevelopment.reviveclient.pingbypass.PbProtocol;
import revivedevelopment.reviveclient.pingbypass.PbRenderable;
import revivedevelopment.reviveclient.pingbypass.PbVia;
import revivedevelopment.reviveclient.pingbypass.client.PbClient;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.ModuleListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.friends.Friend;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.ElytraDurabilitySaver;
import revivedevelopment.reviveclient.systems.managers.RotationManager;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.AnchorAura;
import revivedevelopment.reviveclient.systems.modules.combat.AutoArmor;
import revivedevelopment.reviveclient.systems.modules.combat.AutoCrystal;
import revivedevelopment.reviveclient.systems.modules.combat.AutoMine;
import revivedevelopment.reviveclient.systems.modules.combat.AutoTrap;
import revivedevelopment.reviveclient.systems.modules.combat.Criticals;
import revivedevelopment.reviveclient.systems.modules.combat.Offhand;
import revivedevelopment.reviveclient.systems.modules.combat.Surround;
import revivedevelopment.reviveclient.systems.modules.combat.SwordAura;
import revivedevelopment.reviveclient.systems.modules.combat.Velocity;
import revivedevelopment.reviveclient.systems.modules.movement.BoatFly;
import revivedevelopment.reviveclient.systems.modules.movement.ElytraRecast;
import revivedevelopment.reviveclient.systems.modules.movement.ElytraReplace;
import revivedevelopment.reviveclient.systems.modules.movement.ElytraSaver;
import revivedevelopment.reviveclient.systems.modules.movement.EntityControl;
import revivedevelopment.reviveclient.systems.modules.movement.Follower;
import revivedevelopment.reviveclient.systems.modules.movement.GrimEntityBoost;
import revivedevelopment.reviveclient.systems.modules.movement.GrimShulkerLaunch;
import revivedevelopment.reviveclient.systems.modules.movement.NoFall;
import revivedevelopment.reviveclient.systems.modules.movement.NoSlow;
import revivedevelopment.reviveclient.systems.modules.movement.RocketBoost;
import revivedevelopment.reviveclient.systems.modules.movement.Sprint;
import revivedevelopment.reviveclient.systems.modules.movement.Step;
import revivedevelopment.reviveclient.systems.modules.movement.elytrafly.ElytraFly;
import revivedevelopment.reviveclient.systems.modules.render.BreakIndicators;
import revivedevelopment.reviveclient.systems.modules.render.LogoutSpots;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerManager;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class PingBypass extends Module {
   private static final long HAND_BACK_TIMEOUT_MS = 16000L;
   private static final long FORWARDED_AIM_FRESH_NANOS = 250000000L;
   private String lastPassword;
   private boolean loadingSettings;
   private final SettingGroup sgConnection = this.settings.createGroup("Connection");
   private final SettingGroup sgModules = this.settings.createGroup("Modules");
   private final SettingGroup sgVps = this.settings.createGroup("VPS");
   private final SettingGroup sgHud = this.settings.createGroup("HUD");
   private final Setting<String> host = this.sgConnection
      .add(
         new StringSetting.Builder()
            .name("vps-ip")
            .description("IP or hostname of the VPS running the headless PingBypass client.")
            .defaultValue("127.0.0.1")
            .build()
      );
   private final Setting<String> port = this.sgConnection
      .add(
         new StringSetting.Builder()
            .name("vps-port")
            .description("Port the headless client listens on (each headless instance has its own port).")
            .defaultValue("25570")
            .build()
      );
   private final Setting<String> password = this.sgConnection
      .add(
         new StringSetting.Builder()
            .name("password")
            .description("Password set on the VPS (pingbypass.json). Both sides prove they know it; it's never sent.")
            .defaultValue("")
            .onChanged(v -> {
               if (!this.loadingSettings && this.lastPassword != null && !this.lastPassword.equals(v)) {
                  this.pin.set("");
               }

               this.lastPassword = v;
            })
            .build()
      );
   private final Setting<String> pin = this.sgConnection
      .add(new StringSetting.Builder().name("vps-pin").description("Remembered VPS salt (set automatically).").defaultValue("").visible(() -> false).build());
   private final Setting<Boolean> acceptSalt = this.sgConnection
      .add(
         new BoolSetting.Builder()
            .name("accept-new-salt")
            .description("Turn on after you made a new pingbypass.json on the VPS: accepts its new salt once, then turns itself off.")
            .defaultValue(false)
            .onChanged(v -> {
               if (v) {
                  if (!this.loadingSettings) {
                     this.acceptNewSalt();
                  }

                  this.mc.execute(() -> this.acceptSalt.set(false));
               }
            })
            .build()
      );
   private final Setting<Boolean> route = this.sgConnection
      .add(
         new BoolSetting.Builder()
            .name("route-joins")
            .description("Send servers you join through the VPS. Off = stay connected to the VPS but join directly.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> vpsChat = this.sgConnection
      .add(new BoolSetting.Builder().name("vps-messages").description("Show the VPS's module messages in your chat.").defaultValue(true).build());
   private final Setting<List<Module>> offloaded = this.sgModules
      .add(
         new ModuleListSetting.Builder()
            .name("vps-modules")
            .description(
               "Modules that run on the VPS while you're joined through it. SilentMine goes wherever AutoMine goes. Relay mode: keep movement modules local (your client controls movement). Driven mode: Sprint, Step, NoFall, NoSlow, ElytraFly, ElytraRecast, ElytraControl, RocketBoost, BoatFly, EntityControl, GrimEntityBoost, GrimShulkerLaunch, Follower, ElytraReplace and ElytraSaver run on both the VPS (which moves you) and here (which predicts it), and Criticals and Velocity run on the VPS only, whatever this list says."
            )
            .defaultValue(AutoCrystal.class, AnchorAura.class, SwordAura.class, Surround.class, AutoTrap.class, AutoMine.class, Offhand.class, AutoArmor.class)
            .build()
      );
   private final Setting<PingBypass.DriveMode> driveMode = this.sgVps
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description(
                     "Driven: the VPS plays for you - it answers the server's pings and moves you from your keys, so its modules (AutoCrystal, SwordAura...) act at the VPS's own ping, also on Grim. Your movement reaches the server one PC-VPS trip later, and knockback reaches you that much later. Relay: your client plays and the VPS slips its modules' actions in; Grim then makes them wait for your answers. Takes effect on your next join."
                  ))
               .defaultValue(PingBypass.DriveMode.Driven))
            .build()
      );
   private final Setting<Boolean> vpsRotations = this.sgVps
      .add(
         new BoolSetting.Builder()
            .name("rotations")
            .description(
               "VPS modules turn your look on the server for their actions, as they would here. Turn off only on servers that don't check rotations: their actions then go out at once on whatever look the server has."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> aimWhileMoving = this.sgVps
      .add(
         new BoolSetting.Builder()
            .name("aim-while-moving")
            .description(
               "While you walk, this client sends the VPS modules' aim in its movement packets (with movement correction), so they keep placing and hitting. Off: their aimed actions wait until you stop walking."
            )
            .defaultValue(true)
            .visible(this.vpsRotations::get)
            .build()
      );
   private final Setting<Boolean> tickOrder = this.sgVps
      .add(
         new BoolSetting.Builder()
            .name("tick-order")
            .description(
               "VPS module actions reach the server where a vanilla client sends them in your ticks (Grim's Post check). Off: as soon as they're made."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> vpsSwings = this.sgVps
      .add(new BoolSetting.Builder().name("show-swings").description("Swing your hand here when a VPS module swings it.").defaultValue(true).build());
   private final Setting<Boolean> vpsRenders = this.sgVps
      .add(
         new BoolSetting.Builder()
            .name("show-renders")
            .description("Draw the VPS modules' renders here (placements, targets, mining progress), with your render settings.")
            .defaultValue(true)
            .onChanged(v -> {
               if (!v) {
                  this.clearRemoteRenders();
               }
            })
            .build()
      );
   private final Setting<Boolean> infoServerPing = this.sgHud
      .add(
         new BoolSetting.Builder()
            .name("info-server-ping")
            .description("Show the VPS's own ping to the server in the module info (also {pingbypass.server_ping} in text HUDs).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> infoCrystalCps = this.sgHud
      .add(
         new BoolSetting.Builder()
            .name("info-crystal-cps")
            .description("Show how many crystals AutoCrystal breaks per second on the VPS in the module info (also {pingbypass.crystal_cps}).")
            .defaultValue(true)
            .build()
      );
   private PbClient client;
   private volatile String clientKey = "";
   private String pendingKey;
   private int pendingKeyTicks;
   private boolean routedSession;
   private final Map<String, Integer> revs = new HashMap<>();
   private final Map<String, Boolean> sentActive = new HashMap<>();
   private final Map<String, String> sentSettings = new HashMap<>();
   private final Set<String> declared = new HashSet<>();
   private final Set<String> stopsSent = new HashSet<>();
   private final Map<String, Long> handingBack = new HashMap<>();
   private final Map<String, Boolean> sentStateOnly = new HashMap<>();
   private String routedKey;
   private boolean handBackOnClose;
   private volatile long linkLostAt;
   private boolean deferredKeyNotice;
   private boolean badPortNotice;
   private final PingBypass.HandBackTimer handBackTimer = new PingBypass.HandBackTimer();
   private String sentFriends;
   private final PbFakes.Sender fakeSender = new PbFakes.Sender();
   private String sentFakes;
   private int fakeHitsApplied;
   private boolean fakesTooBig;
   private String sentInput;
   private String sentOptions;
   private volatile PingBypass.ForwardedAim forwardedAim;
   private float appliedAimYaw = Float.NaN;
   private boolean movementSent;
   private boolean skipForwardOnce;
   private final Set<Module> remoteRenders = new HashSet<>();
   private long lastFxAt;
   private static final long FX_STALE_MS = 3000L;
   private volatile String vpsState = "";
   private volatile double vpsServerRtt = -1.0;
   private volatile double vpsServerRttMin = -1.0;
   private volatile String vpsServerRttSource = "";
   private volatile int vpsCrystalCps = -1;
   private volatile int vpsCrystalPps = -1;
   private volatile double vpsModulePps = -1.0;
   private volatile long statusAt;
   private int ticks;
   private String lastAbortKey;
   private long lastAbortAt;

   public PingBypass() {
      super(Categories.Player, "ping-bypass", "Runs your combat modules on a headless client on a VPS near the server, at the VPS's ping.");
      this.runInMainMenu = true;
      ReviveClient.EVENT_BUS.subscribe(this.handBackTimer);
   }

   @Override
   public Module fromTag(NbtCompound tag) {
      this.loadingSettings = true;

      Module var2;
      try {
         var2 = super.fromTag(tag);
      } finally {
         this.loadingSettings = false;
         this.lastPassword = this.password.get();
      }

      return var2;
   }

   public PbClient getClient() {
      return this.client;
   }

   private void acceptNewSalt() {
      this.pin.set("");
      this.info("The VPS's salt will be accepted at the next login.", new Object[0]);
      if (this.isActive() && this.client != null && this.client.getState() == PbClient.State.FAILED && !this.routedSession) {
         this.startClient();
      }
   }

   public String linkKey() {
      return this.clientKey;
   }

   public boolean routesJoins() {
      return this.isActive() && this.route.get();
   }

   public boolean drivesJoins() {
      return this.driveMode.get() == PingBypass.DriveMode.Driven;
   }

   private boolean isLocalOnly(Module module) {
      return module == this
         || module instanceof AntiCheatConfig
         || module.category == Categories.Gui
         || module instanceof TeamSync
         || module instanceof PacketLimiter && !PbRouting.routedDriven();
   }

   private boolean isOffloaded(Module module) {
      if (this.isLocalOnly(module) || this.isMovementShaper(module)) {
         return false;
      } else if (this.drivenVpsOnly(module)) {
         return true;
      } else {
         List<Module> list = this.offloaded.get();
         if (module instanceof SilentMine) {
            return list.contains(Modules.get().get(AutoMine.class));
         } else {
            return !(module instanceof HotbarLock)
               ? list.contains(module)
               : list.contains(Modules.get().get(AutoCrystal.class))
                  || list.contains(Modules.get().get(Offhand.class))
                  || list.contains(Modules.get().get(AutoArmor.class))
                  || list.contains(Modules.get().get(AutoMine.class));
         }
      }
   }

   private boolean isCompanion(Module module) {
      return module instanceof BreakIndicators || module instanceof LogoutSpots || this.isMovementShaper(module);
   }

   private boolean isMovementShaper(Module module) {
      return !PbRouting.routedDriven()
         ? false
         : module instanceof Sprint
            || module instanceof Step
            || module instanceof NoFall
            || module instanceof NoSlow
            || module instanceof ElytraFly
            || module instanceof RocketBoost
            || module instanceof BoatFly
            || module instanceof EntityControl
            || module instanceof ElytraRecast
            || module instanceof ElytraControl
            || module instanceof GrimEntityBoost
            || module instanceof GrimShulkerLaunch
            || module instanceof Follower
            || module instanceof ElytraReplace
            || module instanceof ElytraSaver;
   }

   private boolean drivenVpsOnly(Module module) {
      return PbRouting.routedDriven() && (module instanceof Criticals || module instanceof Velocity);
   }

   private boolean isStateMirror(Module module) {
      return module instanceof ChestSwap && !this.isOffloaded(module);
   }

   @Override
   public void onActivate() {
      if (this.currentKey().equals(this.routedKey)) {
         this.handBackTimer.cancel();
      }

      this.startClient();
      this.updateRouting(true);
   }

   @Override
   public void onDeactivate() {
      PbClient c = this.client;
      boolean driven = this.routedSession && PbRouting.routedDriven();
      if (c != null && c.isConnected()) {
         for (Module module : this.syncedModules()) {
            if (!(module instanceof AntiCheatConfig) && (!driven || !this.isMovementShaper(module) && !(module instanceof Velocity))) {
               this.sendState(module, false, null, true);
            }
         }
      } else if (this.routedSession) {
         this.warning("The VPS can't be told to stop its modules right now; they run here once it has stopped them (within 16 seconds).", new Object[0]);
      }

      if (driven) {
         this.warning(
            "The VPS keeps playing this join until you leave the server: your movement modules and Velocity stay on there as they are (changes made here reach it only with ping-bypass on), and Criticals stops working. Rejoin to play without the VPS.",
            new Object[0]
         );
      }

      this.resetSync();
      this.client = null;
      this.vpsState = "";
      if (ReviveClient.PACKET != null) {
         ReviveClient.PACKET.setExternalPps(0.0);
      }

      this.statusAt = 0L;
      if (c != null && c.isConnected() && this.routedSession && this.handBackTimer.pending) {
         c.closeGracefully(1000L, null);
      } else if (c != null && c.isConnected() && this.routedSession) {
         this.handBackOnClose = true;
         c.closeGracefully(1000L, () -> this.mc.execute(() -> {
            this.handBackOnClose = false;
            if (!this.handBackTimer.pending) {
               if (!this.isActive() || !this.clientKey.equals(this.routedKey)) {
                  this.setRouted(false, true);
               }
            }
         }));
      } else if (this.routedSession) {
         if (c != null) {
            c.closeGracefully(1000L, null);
         }

         long lost = this.linkLostAt;
         this.handBackTimer.start((lost > 0L ? lost : System.currentTimeMillis()) + 16000L);
      } else {
         if (c != null) {
            c.closeGracefully(1000L, null);
         }

         this.setRouted(false, true);
      }
   }

   @Override
   public String getInfoString() {
      if (this.client == null) {
         return this.parsePort() < 0 ? "Bad port" : null;
      } else {
         return switch (this.client.getState()) {
            case CONNECTED -> {
               StringBuilder info = new StringBuilder();
               if (!this.vpsState.isEmpty()) {
                  info.append(this.vpsState).append(' ');
               }

               if (this.client.getRtt() >= 0L) {
                  info.append(this.client.getRtt()).append("ms");
               }

               double serverPing = this.getServerPing();
               if (this.infoServerPing.get() && serverPing >= 0.0) {
                  info.append(" | srv ").append(formatMs(serverPing)).append("ms");
               }

               int cps = this.getVpsCrystalCps();
               if (this.infoCrystalCps.get() && cps >= 0) {
                  info.append(" | ").append(cps).append(" CPS");
               }

               String version = this.getServerVersion();
               if (!version.isEmpty()) {
                  info.append(" | ").append(version);
               }

               if (PbRouting.routedDriven()) {
                  info.append(" | driven");
               }

               yield info.toString().trim();
            }
            case CONNECTING, AUTHENTICATING -> "Connecting";
            case FAILED -> this.client.getLastError() != null ? shorten(this.client.getLastError()) : "Offline";
         };
      }
   }

   private static String formatMs(double ms) {
      return ms < 10.0 ? String.format(Locale.ROOT, "%.1f", ms) : String.valueOf(Math.round(ms));
   }

   private boolean statusFresh() {
      PbClient c = this.client;
      return c != null && c.isConnected() && System.currentTimeMillis() - this.statusAt < 3000L;
   }

   public String getVpsState() {
      return this.vpsState;
   }

   public long getLinkPing() {
      PbClient c = this.client;
      return c != null && c.isConnected() ? c.getRtt() : -1L;
   }

   public double getServerPing() {
      return this.statusFresh() ? this.vpsServerRtt : -1.0;
   }

   public double getServerPingMin() {
      return this.statusFresh() ? this.vpsServerRttMin : -1.0;
   }

   public String getServerPingSource() {
      return this.statusFresh() ? this.vpsServerRttSource : "";
   }

   public String getServerVersion() {
      PbVia.Version version = this.routedSession ? PbRouting.routedVersion() : null;
      return version != null ? version.name() : "";
   }

   public int getVpsCrystalCps() {
      AutoCrystal autoCrystal = AutoCrystal.get();
      return this.statusFresh() && autoCrystal != null && autoCrystal.isRemote() && autoCrystal.isActive() ? this.vpsCrystalCps : -1;
   }

   public int getVpsCrystalPps() {
      AutoCrystal autoCrystal = AutoCrystal.get();
      return this.statusFresh() && autoCrystal != null && autoCrystal.isRemote() && autoCrystal.isActive() ? this.vpsCrystalPps : -1;
   }

   private static double num(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : -1.0;
   }

   private static String shorten(String s) {
      return s.length() > 40 ? s.substring(0, 40) + "..." : s;
   }

   private String pinKey() {
      return PbCrypto.sha256Hex(this.clientKey.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
   }

   private String currentKey() {
      int vpsPort = this.parsePort();
      return this.host.get().trim() + ":" + (vpsPort > 0 ? String.valueOf(vpsPort) : this.port.get().trim()) + ":" + this.password.get();
   }

   private int parsePort() {
      String text = this.port.get().trim();
      if (!text.isEmpty() && text.length() <= 5) {
         for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
               return -1;
            }
         }

         int value = Integer.parseInt(text);
         return value >= 1 && value <= 65535 ? value : -1;
      } else {
         return -1;
      }
   }

   private void reportBadPort() {
      String text = this.port.get().trim();
      ReviveClient.LOG.warn("[PingBypass] vps-port '{}' isn't a port (1-65535), not connecting to the VPS", text);
      this.badPortNotice = !Utils.canUpdate();
      if (text.isEmpty()) {
         this.error("vps-port is empty: set it to the VPS's port, 1 to 65535 (the default is %d).", new Object[]{25570});
      } else {
         this.error("vps-port (highlight)%s(default) isn't a port: set it to the VPS's port, 1 to 65535 (the default is %d).", new Object[]{text, 25570});
      }
   }

   private void startClient() {
      this.stopClient();
      Session session = this.mc.getSession();
      this.clientKey = this.currentKey();
      this.pendingKey = null;
      int vpsPort = this.parsePort();
      if (vpsPort < 0) {
         this.reportBadPort();
      } else {
         this.client = new PbClient(
            this.host.get().trim(),
            vpsPort,
            this.password.get(),
            session.getUsername(),
            session.getUuidOrNull(),
            new PbClient.Listener() {
               @Override
               public void onConnected(PbClient c) {
                  if (c == PingBypass.this.client) {
                     PingBypass.this.linkLostAt = 0L;
                  }

                  PingBypass.this.mc.execute(() -> {
                     if (c == PingBypass.this.client) {
                        PingBypass.this.resetSync();
                        PingBypass.this.info("Connected to VPS (highlight)%s:%d(default).", new Object[]{c.getHost(), c.getPort()});
                     }
                  });
               }

               @Override
               public void onMessage(PbClient c, JsonObject message) {
                  PingBypass.this.handleMessage(c, message);
               }

               @Override
               public String pinnedSalt() {
                  String stored = PingBypass.this.pin.get();
                  String prefix = PingBypass.this.pinKey() + ":";
                  return stored.startsWith(prefix) ? stored.substring(prefix.length()) : "";
               }

               @Override
               public void onSaltConfirmed(String salt) {
                  String value = PingBypass.this.pinKey() + ":" + salt;
                  PingBypass.this.mc.execute(() -> {
                     if (!value.equals(PingBypass.this.pin.get())) {
                        PingBypass.this.pin.set(value);
                     }
                  });
               }

               @Override
               public void onDisconnected(PbClient c) {
                  if (c == PingBypass.this.client && PingBypass.this.linkLostAt == 0L) {
                     PingBypass.this.linkLostAt = System.currentTimeMillis();
                  }

                  if (c == PingBypass.this.client) {
                     PingBypass.this.forwardedAim = null;
                  }

                  PingBypass.this.mc
                     .execute(
                        () -> {
                           if (c == PingBypass.this.client) {
                              PingBypass.this.vpsState = "";
                              PingBypass.this.statusAt = 0L;
                              PingBypass.this.warning(
                                 "Lost connection to the VPS (%s). Reconnecting...", new Object[]{c.getLastError() == null ? "closed" : c.getLastError()}
                              );
                           }
                        }
                     );
               }
            }
         );
      }
   }

   private void stopClient() {
      if (this.client != null) {
         this.client.close();
      }

      this.client = null;
      this.vpsState = "";
      this.statusAt = 0L;
   }

   private void resetSync() {
      this.sentActive.clear();
      this.sentSettings.clear();
      this.sentStateOnly.clear();
      this.declared.clear();
      this.stopsSent.clear();
      this.sentFriends = null;
      this.sentFakes = null;
      this.fakeHitsApplied = 0;
      this.fakeSender.reset();
      this.sentInput = null;
      this.sentOptions = null;
      this.forwardedAim = null;
   }

   private void handleMessage(PbClient c, JsonObject msg) {
      String var3 = PbProtocol.type(msg);
      switch (var3) {
         case "join":
            this.handleJoin(c, msg);
            break;
         case "status":
            if (c != this.client) {
               return;
            }

            this.vpsState = PbProtocol.str(msg, "state", "");
            this.vpsServerRtt = num(msg, "rtt");
            this.vpsServerRttMin = num(msg, "rttMin");
            this.vpsServerRttSource = PbProtocol.str(msg, "rttSrc", "");
            this.vpsCrystalCps = (int)num(msg, "acCps");
            this.vpsCrystalPps = (int)num(msg, "acPps");
            this.vpsModulePps = num(msg, "pps");
            this.statusAt = System.currentTimeMillis();
            break;
         case "module_states":
            this.mc.execute(() -> {
               if (c == this.client) {
                  this.applyStates(msg.getAsJsonArray("list"));
               }
            });
            break;
         case "chat":
            String text = PbProtocol.str(msg, "text", "");
            this.mc.execute(() -> {
               if (this.vpsChat.get() && Utils.canUpdate()) {
                  ChatUtils.sendMsg(0, "VPS", Formatting.AQUA, Text.literal(text));
               }
            });
            break;
         case "aim":
            if (c != this.client) {
               return;
            }

            this.forwardedAim = msg.has("y") && msg.has("p")
               ? new PingBypass.ForwardedAim(
                  msg.get("y").getAsFloat(), msg.get("p").getAsFloat(), msg.has("pr") ? msg.get("pr").getAsDouble() : 100.0, System.nanoTime()
               )
               : null;
            break;
         case "fx":
            if (c != this.client || !msg.has("now") || !msg.has("m") || !msg.get("m").isJsonObject()) {
               return;
            }

            PbFx.Clock clock = new PbFx.Clock(System.currentTimeMillis() - msg.get("now").getAsLong());
            this.mc.execute(() -> this.applyFx(c, msg, clock));
            break;
         case "fake_hit":
            this.mc.execute(() -> {
               if (c == this.client) {
                  this.applyFakeHit(msg);
               }
            });
            break;
         case "swing":
            Hand hand = msg.has("h") && msg.get("h").getAsInt() == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
            this.mc.execute(() -> {
               if (c == this.client && this.routedSession && this.vpsSwings.get() && this.mc.player != null) {
                  this.mc.player.swingHand(hand, false);
               }
            });
      }
   }

   private void handleJoin(PbClient c, JsonObject msg) {
      String id = PbProtocol.str(msg, "id", "");
      String serverId = PbProtocol.str(msg, "serverId", "");
      Thread thread = new Thread(() -> {
         JsonObject reply = PbProtocol.message("join_result");
         reply.addProperty("id", id);
         if (!PbRouting.isExpectingJoin(PbProtocol.str(msg, "bind", null))) {
            reply.addProperty("ok", false);
            reply.addProperty("error", "No routed login of this PC's in progress on the VPS.");
         } else {
            try {
               Session session = this.mc.getSession();
               this.mc.getApiServices().sessionService().joinServer(session.getUuidOrNull(), session.getAccessToken(), serverId);
               reply.addProperty("ok", true);
            } catch (AuthenticationException var7) {
               reply.addProperty("ok", false);
               reply.addProperty("error", var7.getMessage() == null ? var7.getClass().getSimpleName() : var7.getMessage());
            } catch (Throwable var8) {
               ReviveClient.LOG.error("[PingBypass] session join failed", var8);
               reply.addProperty("ok", false);
               reply.addProperty("error", String.valueOf(var8.getMessage()));
            }
         }

         c.send(reply);
      }, "PingBypass-join");
      thread.setDaemon(true);
      thread.start();
   }

   @EventHandler(
      priority = 200
   )
   private void onGameJoined(GameJoinedEvent event) {
      boolean onRouted = this.isOnRoutedConnection();
      this.routedKey = onRouted ? PbRouting.routedLinkKey() : null;
      boolean routed = onRouted && this.clientKey.equals(this.routedKey);
      if (!this.handBackTimer.pending || !onRouted) {
         this.setRouted(routed, false);
      }

      if (onRouted) {
         PbRouting.endJoinWindow();
      }

      String notice = PbRouting.takeNotice();
      if (notice != null) {
         this.warning(notice, new Object[0]);
      }

      if (this.badPortNotice && this.client == null && this.parsePort() < 0) {
         this.reportBadPort();
      }

      this.badPortNotice = false;
   }

   @EventHandler(
      priority = -200
   )
   private void onGameLeft(GameLeftEvent event) {
      if (!this.handBackTimer.pending || !this.isOnRoutedConnection()) {
         this.setRouted(false, false);
      }
   }

   private boolean isOnRoutedConnection() {
      return this.mc.getNetworkHandler() != null && PbRouting.isRouted(this.mc.getNetworkHandler().getConnection());
   }

   private void updateRouting(boolean lifecycle) {
      if (!this.handBackOnClose && !this.handBackTimer.pending) {
         boolean routed = Utils.canUpdate() && this.isOnRoutedConnection() && this.clientKey.equals(this.routedKey);
         if (routed != this.routedSession || routed) {
            this.setRouted(routed, lifecycle);
         }
      }
   }

   private void setRouted(boolean routed, boolean lifecycle) {
      this.routedSession = routed;
      if (!routed) {
         this.handingBack.clear();
         this.forwardedAim = null;
      }

      for (Module module : Modules.get().getAll()) {
         boolean remote = routed && this.isOffloaded(module);
         if (module.isRemote() == remote) {
            this.handingBack.remove(module.name);
         } else if (!remote && routed && lifecycle && module.isActive()) {
            if (this.handingBack.putIfAbsent(module.name, System.currentTimeMillis()) == null
               && (this.client == null || !this.client.isConnected() && (this.linkLostAt > 0L || this.client.getState() == PbClient.State.FAILED))) {
               this.warning("The VPS can't be told to stop %s right now; it runs here once the VPS stops it (within 15 seconds).", new Object[]{module.title});
            }
         } else {
            this.handingBack.remove(module.name);
            module.setRemote(remote, lifecycle);
         }
      }
   }

   private void finishHandBacks() {
      if (!this.handingBack.isEmpty()) {
         long now = System.currentTimeMillis();
         Iterator<Entry<String, Long>> it = this.handingBack.entrySet().iterator();

         while (it.hasNext()) {
            Entry<String, Long> e = it.next();
            if (now - e.getValue() >= 16000L) {
               it.remove();
               this.handBack(Modules.get().get(e.getKey()));
            }
         }
      }
   }

   private void handBack(Module module) {
      if (module != null && module.isRemote() && (!this.routedSession || !this.isOffloaded(module))) {
         module.setRemote(false, true);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      this.ticks++;
      this.debounceRestart();
      this.finishHandBacks();
      if (ReviveClient.PACKET != null) {
         ReviveClient.PACKET.setExternalPps(this.routedSession && this.statusFresh() ? Math.max(0.0, this.vpsModulePps) : 0.0);
      }

      if (!this.remoteRenders.isEmpty() && (!this.routedSession || System.currentTimeMillis() - this.lastFxAt > 3000L)) {
         this.clearRemoteRenders();
      }

      if (this.client != null) {
         if (this.client.isConnected()) {
            if (this.ticks % 40 == 0) {
               this.client.ping();
            }

            this.syncOptions();
            this.syncModules();
            this.syncFriends();
            this.syncFakes();
            this.syncInput();
         }

         this.updateRouting(true);
      }
   }

   private void debounceRestart() {
      if (this.client != null && !this.routedSession && !this.client.getUser().equalsIgnoreCase(this.mc.getSession().getUsername())) {
         this.startClient();
      } else {
         String key = this.currentKey();
         if (key.equals(this.clientKey)) {
            this.pendingKey = null;
            this.deferredKeyNotice = false;
         } else if (this.client == null || !this.routedSession && !this.isOnRoutedConnection()) {
            if (!key.equals(this.pendingKey)) {
               this.pendingKey = key;
               this.pendingKeyTicks = 0;
            } else if (++this.pendingKeyTicks >= 20) {
               this.startClient();
            }
         } else {
            if (!this.deferredKeyNotice) {
               this.info("The new VPS settings apply after you leave this server.", new Object[0]);
            }

            this.deferredKeyNotice = true;
            this.pendingKey = null;
         }
      }
   }

   public void forwardAction(KeybindSetting setting) {
      PbClient c = this.client;
      if (c != null && c.isConnected() && setting.module != null) {
         String group = PbModules.groupOf(setting.module, setting);
         if (group != null) {
            JsonObject msg = PbProtocol.message("action");
            msg.addProperty("module", setting.module.name);
            msg.addProperty("group", group);
            msg.addProperty("setting", setting.name);
            c.send(msg);
         }
      }
   }

   private boolean silentMineOnVps() {
      SilentMine silentMine = Modules.get().get(SilentMine.class);
      return this.routedSession && silentMine != null && silentMine.isRemote() && silentMine.isActive() && this.client != null && this.client.isConnected();
   }

   public boolean forwardMine(BlockPos pos, Direction dir, double priority, boolean direct) {
      if (pos == null || dir == null) {
         return false;
      } else if (!this.silentMineOnVps()) {
         return false;
      } else {
         JsonObject msg = PbProtocol.message("mine");
         msg.addProperty("pos", pos.asLong());
         msg.addProperty("dir", dir.getIndex());
         msg.addProperty("priority", priority);
         if (direct) {
            msg.addProperty("direct", true);
         }

         return this.client.send(msg);
      }
   }

   public void forwardAbortRebreak(Collection<BlockPos> positions) {
      if (this.silentMineOnVps() && !positions.isEmpty()) {
         JsonArray list = new JsonArray();

         for (BlockPos pos : positions) {
            if (list.size() >= 32) {
               break;
            }

            list.add(pos.asLong());
         }

         String key = list.toString();
         long now = System.currentTimeMillis();
         if (!key.equals(this.lastAbortKey) || now - this.lastAbortAt >= 250L) {
            this.lastAbortKey = key;
            this.lastAbortAt = now;
            JsonObject msg = PbProtocol.message("abort_rebreak");
            msg.add("pos", list);
            this.client.send(msg);
         }
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onStartBreakingBlock(StartBreakingBlockEvent event) {
      SilentMine silentMine = Modules.get().get(SilentMine.class);
      if (this.routedSession && silentMine != null && silentMine.isRemote() && silentMine.isActive()) {
         PbClient c = this.client;
         if (c != null && c.isConnected()) {
            JsonObject msg = PbProtocol.message("mine");
            msg.addProperty("pos", event.blockPos.asLong());
            msg.addProperty("dir", event.direction.getIndex());
            if (c.send(msg)) {
               event.cancel();
            }
         }
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onTickPre(TickEvent.Pre event) {
      this.appliedAimYaw = Float.NaN;
      this.movementSent = false;
      boolean skip = this.skipForwardOnce;
      this.skipForwardOnce = false;
      PingBypass.ForwardedAim aim = this.forwardedAim;
      if (aim != null && this.routedSession && this.vpsRotations.get() && this.aimWhileMoving.get() && this.mc.player != null) {
         if (System.nanoTime() - aim.receivedNanos() <= 250000000L) {
            if (!skip && this.canCarryAim()) {
               RotationManager rotation = ReviveClient.ROTATION;
               if (rotation != null) {
                  float camera = this.mc.player.getYaw();
                  float yaw = camera + MathHelper.wrapDegrees(aim.yaw() - camera);
                  if (rotation.requestMotionRotation(yaw, aim.pitch(), aim.priority(), true)) {
                     this.appliedAimYaw = yaw;
                  }
               }
            }
         }
      }
   }

   private boolean canCarryAim() {
      if (this.mc.player == null) {
         return false;
      } else if (this.mc.player.isGliding() || this.mc.player.hasVehicle() || this.mc.player.isSwimming()) {
         return false;
      } else if (this.mc.interactionManager != null && this.mc.interactionManager.isBreakingBlock()) {
         return false;
      } else {
         if (this.mc.player.isUsingItem()) {
            UseAction action = this.mc.player.getActiveItem().getUseAction();
            if (action == UseAction.BOW || action == UseAction.TRIDENT || action == UseAction.SPEAR) {
               return false;
            }
         }

         return !this.mc.player.getMainHandStack().contains(DataComponentTypes.PIERCING_WEAPON);
      }
   }

   @EventHandler(
      priority = -200
   )
   private void onOwnAction(PacketEvent.Send event) {
      if (!event.isCancelled() && this.routedSession) {
         Packet<?> packet = event.packet;
         boolean looked = packet instanceof PlayerInteractItemC2SPacket
            || packet instanceof PlayerInteractEntityC2SPacket
            || packet instanceof PlayerInteractBlockC2SPacket
            || packet instanceof PlayerActionC2SPacket action
               && action.getAction() != Action.SWAP_ITEM_WITH_OFFHAND
               && action.getAction() != Action.DROP_ITEM
               && action.getAction() != Action.DROP_ALL_ITEMS;
         if (looked) {
            if (this.movementSent) {
               this.skipForwardOnce = true;
            } else {
               this.ownActionLook();
            }
         }
      }
   }

   @EventHandler(
      priority = 201
   )
   private void onMovementSent(SendMovementPacketsEvent.Post event) {
      this.movementSent = true;
   }

   private void ownActionLook() {
      RotationManager rotation = ReviveClient.ROTATION;
      if (!Float.isNaN(this.appliedAimYaw) && rotation != null && this.mc.player != null) {
         if (rotation.hasMotionRotation() && rotation.getMotionYaw() == this.appliedAimYaw) {
            rotation.requestMotionRotation(this.mc.player.getYaw(), this.mc.player.getPitch(), rotation.getMotionPriority() + 1.0, false);
         }

         this.appliedAimYaw = Float.NaN;
      }
   }

   private void syncOptions() {
      long rtt = this.client.getRtt();
      long bucket = rtt < 0L ? -1L : Math.round(rtt / 10.0) * 10L;
      String key = this.vpsRotations.get()
         + ":"
         + this.tickOrder.get()
         + ":"
         + this.aimWhileMoving.get()
         + ":"
         + this.vpsSwings.get()
         + ":"
         + this.vpsRenders.get()
         + ":"
         + bucket;
      if (!key.equals(this.sentOptions)) {
         JsonObject msg = PbProtocol.message("options");
         msg.addProperty("rot", this.vpsRotations.get());
         msg.addProperty("tick", this.tickOrder.get());
         msg.addProperty("fwd", this.aimWhileMoving.get());
         msg.addProperty("swing", this.vpsSwings.get());
         msg.addProperty("fx", this.vpsRenders.get());
         msg.addProperty("rtt", rtt);
         if (this.client.send(msg)) {
            this.sentOptions = key;
         }
      }
   }

   private void applyFx(PbClient c, JsonObject msg, PbFx.Clock clock) {
      if (c == this.client && this.routedSession && this.vpsRenders.get()) {
         this.lastFxAt = System.currentTimeMillis();
         JsonObject states = msg.getAsJsonObject("m");

         for (Entry<String, JsonElement> e : states.entrySet()) {
            Module module = Modules.get().get(e.getKey());
            if (module instanceof PbRenderable renderable) {
               if (!module.isRemote()) {
                  this.remoteRenders.remove(module);
               } else if (!e.getValue().isJsonObject()) {
                  renderable.clearRemote();
                  this.remoteRenders.remove(module);
               } else {
                  try {
                     renderable.readRemoteRender(e.getValue().getAsJsonObject(), clock);
                     this.remoteRenders.add(module);
                  } catch (RuntimeException var10) {
                     ReviveClient.LOG.warn("[PingBypass] bad render state for {}: {}", module.name, var10.toString());
                     renderable.clearRemote();
                     this.remoteRenders.remove(module);
                  }
               }
            }
         }

         if (msg.has("full")) {
            Iterator<Module> it = this.remoteRenders.iterator();

            while (it.hasNext()) {
               Module module = it.next();
               if (!states.has(module.name) || !module.isRemote()) {
                  if (module.isRemote()) {
                     ((PbRenderable)module).clearRemote();
                  }

                  it.remove();
               }
            }
         }
      }
   }

   private void clearRemoteRenders() {
      for (Module module : this.remoteRenders) {
         if (module.isRemote()) {
            ((PbRenderable)module).clearRemote();
         }
      }

      this.remoteRenders.clear();
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.remoteRenders.isEmpty()) {
         for (Module module : this.remoteRenders) {
            if (module.isRemote() && module.isActive()) {
               ((PbRenderable)module).drawRemote3D(event);
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (!this.remoteRenders.isEmpty()) {
         for (Module module : this.remoteRenders) {
            if (module.isRemote() && module.isActive()) {
               ((PbRenderable)module).drawRemote2D(event);
            }
         }
      }
   }

   private List<Module> syncedModules() {
      List<Module> list = new ArrayList<>();

      for (Module module : Modules.get().getAll()) {
         if (this.isOffloaded(module) || this.isCompanion(module) || this.isStateMirror(module)) {
            list.add(module);
         }
      }

      AntiCheatConfig acc = AntiCheatConfig.get();
      if (acc != null) {
         list.add(acc);
      }

      return list;
   }

   private void syncModules() {
      boolean checkSettings = this.ticks % 10 == 0;
      List<Module> synced = this.syncedModules();

      for (Module module : synced) {
         String name = module.name;
         this.declared.add(name);
         boolean active = module.isActive();
         String settings = null;
         if (!this.isStateMirror(module) && (checkSettings || !this.sentSettings.containsKey(name))) {
            try {
               settings = PbModules.encode(PbModules.settingsTag(module));
            } catch (Exception var12) {
               ReviveClient.LOG.error("[PingBypass] could not encode settings of {}", name, var12);
            }
         }

         boolean mirror = this.isStateMirror(module);
         boolean activeChanged = !Objects.equals(this.sentActive.get(name), active);
         boolean settingsChanged = settings != null && !settings.equals(this.sentSettings.get(name));
         boolean modeChanged = this.sentStateOnly.containsKey(name) && this.sentStateOnly.get(name) != mirror;
         if ((activeChanged || settingsChanged || modeChanged)
            && this.sendState(module, active, settingsChanged ? settings : null, activeChanged || modeChanged)) {
            this.sentActive.put(name, active);
            this.sentStateOnly.put(name, mirror);
            if (settingsChanged) {
               this.sentSettings.put(name, settings);
            }
         }
      }

      Iterator<String> it = this.declared.iterator();

      while (it.hasNext()) {
         String namex = it.next();
         Module module = Modules.get().get(namex);
         if (module == null || !synced.contains(module)) {
            if (module != null && this.sendState(module, false, null, true)) {
               this.stopsSent.add(namex);
            }

            this.sentActive.remove(namex);
            this.sentSettings.remove(namex);
            this.sentStateOnly.remove(namex);
            it.remove();
         }
      }

      if (this.clientKey.equals(this.routedKey) && !this.handBackTimer.pending) {
         for (String namex : this.handingBack.keySet()) {
            if (!this.stopsSent.contains(namex)) {
               Module module = Modules.get().get(namex);
               if (module != null && module.isRemote() && !synced.contains(module) && this.sendState(module, false, null, true)) {
                  this.stopsSent.add(namex);
               }
            }
         }
      }
   }

   private boolean sendState(Module module, boolean active, String settings, boolean bumpRev) {
      int rev = bumpRev ? this.revs.merge(module.name, 1, Integer::sum) : this.revs.getOrDefault(module.name, 0);
      JsonObject msg = PbProtocol.message("module");
      msg.addProperty("name", module.name);
      msg.addProperty("rev", rev);
      msg.addProperty("active", active);
      if (module instanceof AntiCheatConfig) {
         msg.addProperty("settingsOnly", true);
      }

      if (this.isCompanion(module)) {
         msg.addProperty("companion", true);
      }

      if (this.isStateMirror(module)) {
         msg.addProperty("stateOnly", true);
      }

      if (settings != null) {
         msg.addProperty("settings", settings);
      }

      return this.client != null && this.client.send(msg);
   }

   private void applyStates(JsonArray list) {
      if (list != null) {
         for (JsonElement element : list) {
            JsonObject state = element.getAsJsonObject();
            String name = PbProtocol.str(state, "name", "");
            Module module = Modules.get().get(name);
            if (module != null) {
               int rev = state.has("rev") ? state.get("rev").getAsInt() : 0;
               if (!module.isRemote()) {
                  if (this.isMovementShaper(module) && rev == this.revs.getOrDefault(name, 0) && Objects.equals(this.sentActive.get(name), module.isActive())) {
                     boolean active = state.has("active") && state.get("active").getAsBoolean();
                     if (module.isActive() != active) {
                        module.toggle();
                        this.sentActive.put(name, module.isActive());
                     }
                  }
               } else if (this.handingBack.containsKey(name)) {
                  boolean stillOn = state.has("active") && state.get("active").getAsBoolean();
                  if (this.clientKey.equals(this.routedKey) && rev == this.revs.getOrDefault(name, 0) && (!stillOn || this.isStateMirror(module))) {
                     this.handingBack.remove(name);
                     this.handBack(module);
                  }
               } else if (rev == this.revs.getOrDefault(name, 0) && Objects.equals(this.sentActive.get(name), module.isActive())) {
                  module.remoteInfo = PbProtocol.str(state, "info", null);
                  if (module instanceof SilentMine silentMine) {
                     JsonArray own = state.has("own") && state.get("own").isJsonArray() ? state.getAsJsonArray("own") : new JsonArray();
                     long[] positions = new long[Math.min(own.size(), 8)];

                     for (int i = 0; i < positions.length; i++) {
                        positions[i] = own.get(i).getAsLong();
                     }

                     silentMine.setRemoteOwnedPositions(positions);
                  }

                  boolean active = state.has("active") && state.get("active").getAsBoolean();
                  if (module.isActive() != active) {
                     module.setActiveFromRemote(active);
                     this.sentActive.put(name, active);
                  }
               }
            }
         }
      }
   }

   private void syncFriends() {
      String key = friendsKey();
      if (!key.equals(this.sentFriends)) {
         try {
            JsonObject msg = PbProtocol.message("friends");
            msg.addProperty("data", PbModules.encode(Friends.get().toTag()));
            if (this.client.send(msg)) {
               this.sentFriends = key;
            }
         } catch (Exception var3) {
            ReviveClient.LOG.error("[PingBypass] could not encode friends", var3);
         }
      }
   }

   private static String friendsKey() {
      StringBuilder key = new StringBuilder();

      for (Friend friend : Friends.get()) {
         key.append(friend.name).append('\n');
      }

      return key.toString();
   }

   private void syncFakes() {
      if (!this.routedSession) {
         this.sentFakes = null;
      } else if (this.mc.world != null) {
         JsonObject msg = this.fakeSender.write(this.mc.world, this.fakeHitsApplied);
         String key = msg.toString();
         if (!key.equals(this.sentFakes)) {
            if (key.length() > 2097152) {
               if (!this.fakesTooBig) {
                  this.warning("Your fake players carry too much to send to the VPS: remove some (.fakeplayer del).", new Object[0]);
               }

               this.fakesTooBig = true;
            } else {
               this.fakesTooBig = false;
               if (this.client.send(msg)) {
                  this.sentFakes = key;
               }
            }
         }
      }
   }

   private void applyFakeHit(JsonObject msg) {
      this.fakeHitsApplied = Math.max(this.fakeHitsApplied, (int)num(msg, "s"));
      if (this.routedSession && this.mc.world != null && this.mc.player != null && FakePlayerManager.takeDamage) {
         FakePlayerEntity fake = PbFakes.find(PbProtocol.str(msg, "u", ""));
         float damage = (float)num(msg, "d");
         if (fake != null && fake.getEntityWorld() == this.mc.world && damage > 0.0F && Float.isFinite(damage)) {
            fake.simulateDamage(this.mc.world.getDamageSources().playerAttack(this.mc.player), damage);
         }
      }
   }

   private void syncInput() {
      if (this.routedSession) {
         boolean use = this.mc.options.useKey.isPressed();
         boolean attack = this.mc.options.attackKey.isPressed();
         boolean inventory = this.mc.currentScreen instanceof InventoryScreen || this.mc.currentScreen instanceof CreativeInventoryScreen;
         boolean flashing = ElytraDurabilitySaver.get().isFlashing();
         boolean carry = this.canCarryAim();
         boolean hold = ReviveClient.SWAP != null && (ReviveClient.SWAP.isMultiTickSwapActive() || ReviveClient.SWAP.isSilentSwapActive());
         JsonArray held = new JsonArray();

         for (Module module : Modules.get().getAll()) {
            if ((module.isRemote() || this.isCompanion(module)) && module.isActive()) {
               for (SettingGroup group : module.settings) {
                  for (Setting<?> setting : group) {
                     if (setting instanceof KeybindSetting bind && bind.get().isSet() && bind.get().isPressed() && held.size() < 32) {
                        held.add(module.name + "|" + group.name + "|" + setting.name);
                     }
                  }
               }
            }
         }

         String key = use + ":" + attack + ":" + inventory + ":" + flashing + ":" + carry + ":" + hold + ":" + held;
         if (!key.equals(this.sentInput) || this.ticks % 40 == 0) {
            JsonObject msg = PbProtocol.message("input");
            msg.addProperty("use", use);
            msg.addProperty("attack", attack);
            msg.addProperty("inventory", inventory);
            msg.addProperty("flash", flashing);
            msg.addProperty("carry", carry);
            msg.addProperty("hold", hold);
            msg.add("held", held);
            if (this.client.send(msg)) {
               this.sentInput = key;
            }
         }
      }
   }

   public static enum DriveMode {
      Driven,
      Relay;
   }

   private record ForwardedAim(float yaw, float pitch, double priority, long receivedNanos) {
   }

   private class HandBackTimer {
      private long deadline;
      private boolean pending;

      void start(long at) {
         this.deadline = at;
         this.pending = true;
      }

      void cancel() {
         this.pending = false;
      }

      @EventHandler
      private void onTick(TickEvent.Post event) {
         if (this.pending && System.currentTimeMillis() >= this.deadline) {
            this.pending = false;
            if (!PingBypass.this.isActive() || !PingBypass.this.clientKey.equals(PingBypass.this.routedKey)) {
               PingBypass.this.setRouted(false, true);
            }
         }
      }

      @EventHandler(
         priority = -200
      )
      private void onGameLeft(GameLeftEvent event) {
         if (this.pending && !PingBypass.this.isOnRoutedConnection()) {
            this.pending = false;
            PingBypass.this.setRouted(false, false);
         }
      }
   }
}
