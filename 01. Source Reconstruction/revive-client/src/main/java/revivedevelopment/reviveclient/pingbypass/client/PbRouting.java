package revivedevelopment.reviveclient.pingbypass.client;

import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.UnknownCustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.ChangeGameModeC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixin.ClientConnectionAccessor;
import revivedevelopment.reviveclient.pingbypass.PbCrypto;
import revivedevelopment.reviveclient.pingbypass.PbProtocol;
import revivedevelopment.reviveclient.pingbypass.PbVia;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.PingBypass;

public final class PbRouting {
   private static final Object LOCK = new Object();
   private static int attempt;
   private static int armedAttempt;
   private static volatile String ticket;
   private static volatile String expectedKeyHash;
   private static volatile Thread ticketThread;
   private static volatile InetSocketAddress vpsAddress;
   private static volatile boolean redirected;
   private static volatile ClientConnection routedConnection;
   private static volatile String routedKeyHash;
   private static volatile boolean routedKeyVerified;
   private static volatile String routedBinding;
   private static volatile String routedTicket;
   private static volatile String routedHost;
   private static volatile String routedLinkKey;
   private static volatile String pendingLinkKey;
   private static volatile int routedPort;
   private static volatile String pendingHost;
   private static volatile int pendingPort;
   private static volatile long joinWindowUntil;
   private static volatile String notice;
   private static volatile boolean decided;
   private static volatile JsonObject ticketVia;
   private static volatile Object viaChoice;
   private static volatile PbVia.Version pendingVersion;
   private static volatile PbVia.Version routedVersion;
   private static volatile boolean pendingDriven;
   private static volatile boolean routedDriven;
   private static final AtomicReference<PbRouting.ViaSwitch> viaSwitch = new AtomicReference<>();
   private static volatile PbRouting.ViaSwitch connectSwitch;
   private static final int TICKET_SUFFIX_LENGTH = "\u0000RVPB\u0000".length() + 32;
   private static boolean vanillaMove;
   private static final ThreadLocal<Boolean> MARKING = ThreadLocal.withInitial(() -> false);

   private PbRouting() {
   }

   public static void onConnectScreen(ServerAddress address, ServerInfo info) {
      synchronized (LOCK) {
         PbRouting.ViaSwitch stale = viaSwitch.get();
         ClientConnection previous = routedConnection;
         if (stale != null && (previous == null || !previous.isOpen())) {
            restoreVia(stale);
         }

         attempt++;
         armedAttempt = 0;
         notice = null;
         ticket = null;
         expectedKeyHash = null;
         ticketThread = null;
         vpsAddress = null;
         redirected = false;
         routedConnection = null;
         routedKeyHash = null;
         routedKeyVerified = false;
         routedBinding = null;
         routedTicket = null;
         routedHost = null;
         pendingHost = null;
         routedLinkKey = null;
         pendingLinkKey = null;
         joinWindowUntil = 0L;
         decided = false;
         ticketVia = null;
         viaChoice = null;
         pendingVersion = null;
         routedVersion = null;
         pendingDriven = false;
         routedDriven = false;
         connectSwitch = null;
         PingBypass pb = Modules.get().get(PingBypass.class);
         if (pb != null && pb.routesJoins()) {
            if (info == null || !info.isLocal()) {
               String host = address.getAddress().toLowerCase();
               if (!isLocalHost(host)) {
                  if (host.length() + TICKET_SUFFIX_LENGTH > 255) {
                     notice = "Server address too long to route through the VPS - you joined directly.";
                  } else {
                     armedAttempt = attempt;
                     if (PbVia.available()) {
                        PbRouting.ViaSwitch pending = viaSwitch.get();
                        viaChoice = pending != null ? pending.restoreTo() : PbVia.globalTarget();
                     }
                  }
               }
            }
         }
      }
   }

   public static void prepareTicket() {
      int myAttempt;
      synchronized (LOCK) {
         if (armedAttempt == 0) {
            return;
         }

         myAttempt = armedAttempt;
         armedAttempt = 0;
      }

      PingBypass pb = Modules.get().get(PingBypass.class);
      PbClient client = pb == null ? null : pb.getClient();
      String reason = null;
      PbClient.Ticket t = null;
      long deadline = System.currentTimeMillis() + 3000L;

      while (
         client != null
            && (client.getState() == PbClient.State.CONNECTING || client.getState() == PbClient.State.AUTHENTICATING)
            && System.currentTimeMillis() < deadline
      ) {
         try {
            Thread.sleep(50L);
         } catch (InterruptedException var12) {
            return;
         }
      }

      String account = MinecraftClient.getInstance().getSession().getUsername();
      InetAddress vpsIp = client == null ? null : client.getRemoteAddress();
      if (client != null && client.isConnected() && vpsIp != null) {
         if (!client.getUser().equalsIgnoreCase(account)) {
            reason = "The VPS link is still logged in as " + client.getUser() + " - you joined directly.";
         } else {
            t = client.requestTicket(5000L);
            if (t == null) {
               reason = "VPS didn't answer - you joined directly, combat modules run on this PC.";
            }
         }
      } else {
         reason = "VPS not reachable - you joined directly, combat modules run on this PC.";
      }

      synchronized (LOCK) {
         if (myAttempt == attempt) {
            if (t == null) {
               notice = reason;
               ReviveClient.LOG.warn("[PingBypass] not routing this join: {}", reason);
            } else {
               vpsAddress = new InetSocketAddress(vpsIp, client.getPort());
               pendingLinkKey = pb.linkKey();
               pendingDriven = pb.drivesJoins();
               ticket = t.ticket();
               expectedKeyHash = t.keyHash();
               ticketVia = t.via();
               ticketThread = Thread.currentThread();
            }
         }
      }
   }

   public static InetSocketAddress redirectSocket(InetSocketAddress target) {
      return decide(target);
   }

   public static void beforeConnect(InetSocketAddress target, ClientConnection connection) {
      decide(target);
      if (redirected && Thread.currentThread() == ticketThread && PbVia.available()) {
         PbVia.forceConnection(connection, PbVia.nativeVersion());
      }
   }

   public static void afterConnect(ClientConnection connection, ChannelFuture future) {
      if (redirected && Thread.currentThread() == ticketThread) {
         PbRouting.ViaSwitch sw = connectSwitch;
         connectSwitch = null;
         if (sw != null) {
            future.channel().closeFuture().addListener(f -> restoreVia(sw));
         }

         if (PbVia.available()) {
            future.addListener((ChannelFutureListener)f -> {
               if (f.isSuccess()) {
                  PbVia.Version v = PbVia.describe(PbVia.connectionTarget(connection));
                  if (v != null && !v.isNative()) {
                     notice = "ViaFabricPlus kept " + v.name() + " on the connection to the VPS, which must be native - the join was stopped.";
                     ReviveClient.LOG.warn("[PingBypass] {}", notice);
                     f.channel().close();
                  }
               }
            });
         }
      }
   }

   public static void onClientStop() {
      PbRouting.ViaSwitch sw = viaSwitch.get();
      if (sw != null) {
         restoreVia(sw);
      }
   }

   private static void restoreVia(PbRouting.ViaSwitch sw) {
      if (viaSwitch.compareAndSet(sw, null)) {
         PbVia.setGlobal(sw.restoreTo());
      }
   }

   private static InetSocketAddress decide(InetSocketAddress target) {
      if (ticket == null || Thread.currentThread() != ticketThread) {
         return target;
      } else if (decided) {
         return redirected ? vpsAddress : target;
      } else {
         decided = true;
         if (isUnroutable(target.getAddress())) {
            ticket = null;
            ticketThread = null;
            notice = "The server is on a private network - you joined directly.";
            return target;
         } else {
            String host = target.getHostString();
            if (host.length() + TICKET_SUFFIX_LENGTH > 255) {
               ticket = null;
               ticketThread = null;
               notice = "Server address too long to route through the VPS - you joined directly.";
               return target;
            } else {
               PbVia.Version version = null;
               if (PbVia.installed()) {
                  String refusal = null;
                  String nativeName = SharedConstants.getGameVersion().name();
                  if (!PbVia.available()) {
                     refusal = "This ViaFabricPlus build isn't supported by PingBypass - you joined directly.";
                  } else {
                     PbVia.Version picked = PbVia.effective(PbVia.globalTarget());
                     if (picked == null) {
                        refusal = "ViaFabricPlus's version couldn't be read - you joined directly.";
                     } else if (!picked.isNative()) {
                        if (ticketVia == null) {
                           refusal = "ViaFabricPlus is set to "
                              + picked.name()
                              + ", but the VPS has no ViaFabricPlus - you joined directly. Put ViaFabricPlus and Fabric API in the VPS's mods folder, or pick "
                              + nativeName
                              + ".";
                        } else if (!picked.routable()) {
                           refusal = picked.name() + " can't be routed through the VPS (1.20.3 to " + nativeName + ") - you joined directly.";
                        } else if (picked.id() < vpsMinProtocol(ticketVia)) {
                           refusal = picked.name()
                              + " needs a newer Revive on the VPS (it routes "
                              + PbVia.nameOf(vpsMinProtocol(ticketVia))
                              + " and newer) - you joined directly.";
                        } else {
                           version = picked;
                        }
                     }
                  }

                  if (refusal != null) {
                     ticket = null;
                     ticketThread = null;
                     notice = refusal;
                     ReviveClient.LOG.warn("[PingBypass] not routing this join: {}", refusal);
                     return target;
                  }
               }

               if (version != null) {
                  Object choice = viaChoice != null ? viaChoice : PbVia.globalTarget();
                  if (!PbVia.setGlobal(PbVia.nativeVersion())) {
                     ticket = null;
                     ticketThread = null;
                     notice = "ViaFabricPlus couldn't be switched for the VPS - you joined directly.";
                     return target;
                  }

                  PbRouting.ViaSwitch sw = new PbRouting.ViaSwitch(choice);
                  viaSwitch.set(sw);
                  connectSwitch = sw;
               }

               redirected = true;
               pendingHost = host;
               pendingPort = target.getPort();
               pendingVersion = version;
               return vpsAddress;
            }
         }
      }
   }

   private static int vpsMinProtocol(@Nullable JsonObject via) {
      if (via == null) {
         return 765;
      } else {
         try {
            return via.has("min") ? via.get("min").getAsInt() : 765;
         } catch (RuntimeException var2) {
            return 765;
         }
      }
   }

   public static String routedLinkKey() {
      return routedLinkKey;
   }

   public static boolean routedDriven() {
      ClientConnection c = routedConnection;
      return routedDriven && c != null && c.isOpen();
   }

   @Nullable
   public static PbVia.Version routedVersion() {
      return routedVersion;
   }

   public static String tagHandshake(ClientConnection connection, String host) {
      String t = ticket;
      if (t != null && redirected && Thread.currentThread() == ticketThread) {
         ticket = null;
         ticketThread = null;
         routedConnection = connection;
         routedKeyHash = expectedKeyHash;
         routedKeyVerified = false;
         routedBinding = null;
         routedTicket = t;
         String target = pendingHost;
         routedHost = target;
         routedLinkKey = pendingLinkKey;
         routedPort = pendingPort;
         routedVersion = pendingVersion;
         routedDriven = pendingDriven;
         if (routedDriven) {
            PbPredict.begin(connection);
         }

         joinWindowUntil = System.currentTimeMillis() + 60000L;
         return target + "\u0000RVPB\u0000" + t;
      } else {
         return host;
      }
   }

   public static boolean isTrustedServerKey(ClientConnection connection, byte[] publicKey) {
      if (!isRouted(connection)) {
         return true;
      } else {
         String expected = routedKeyHash;
         boolean trusted = expected != null && !expected.isEmpty() && expected.equals(PbCrypto.sha256Hex(publicKey));
         if (trusted) {
            routedKeyVerified = true;
         }

         return trusted;
      }
   }

   public static void onKeySent(ClientConnection connection, byte[] encryptedSecretKey) {
      if (isRouted(connection) && routedKeyVerified) {
         String binding = PbCrypto.sha256Hex(encryptedSecretKey);
         routedBinding = binding;
         PingBypass pb = Modules.get().get(PingBypass.class);
         PbClient client = pb == null ? null : pb.getClient();
         String host = routedHost;
         String ticket = routedTicket;
         if (client != null && host != null && ticket != null) {
            JsonObject msg = PbProtocol.message("bind");
            msg.addProperty("ticket", ticket);
            msg.addProperty("bind", binding);
            msg.addProperty("host", host);
            msg.addProperty("port", routedPort);
            PbVia.Version version = routedVersion;
            if (version != null) {
               msg.add("pv", version.toJson());
            }

            msg.addProperty("drive", routedDriven);
            client.send(msg);
         }
      }
   }

   public static String currentBinding() {
      ClientConnection c = routedConnection;
      String binding = routedBinding;
      return c != null && c.isOpen() && binding != null ? binding : "";
   }

   public static boolean mayFinishLogin(ClientConnection connection) {
      return !isRouted(connection) || routedKeyVerified && connection.isEncrypted();
   }

   private static boolean isLocalHost(String host) {
      if (host.equals("localhost") || host.endsWith(".local")) {
         return true;
      } else if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
         String[] p = host.split("\\.");
         int a = Integer.parseInt(p[0]);
         int b = Integer.parseInt(p[1]);
         return a == 127
            || a == 10
            || a == 0
            || a == 192 && b == 168
            || a == 169 && b == 254
            || a == 172 && b >= 16 && b <= 31
            || a == 100 && b >= 64 && b <= 127;
      } else {
         return !host.contains(":")
            ? false
            : host.equals("::1") || host.equals("::") || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd");
      }
   }

   private static boolean isUnroutable(InetAddress address) {
      if (address == null) {
         return false;
      } else if (!address.isLoopbackAddress() && !address.isAnyLocalAddress() && !address.isSiteLocalAddress() && !address.isLinkLocalAddress()) {
         byte[] b = address.getAddress();
         if (address instanceof Inet4Address) {
            return (b[0] & 255) == 100 && (b[1] & 192) == 64;
         } else {
            return address instanceof Inet6Address ? (b[0] & 254) == 252 : false;
         }
      } else {
         return true;
      }
   }

   public static boolean isRouted(ClientConnection connection) {
      return connection != null && connection == routedConnection;
   }

   public static boolean sendGameModeCommand(ClientPlayNetworkHandler handler, Packet<?> packet) {
      if (packet instanceof ChangeGameModeC2SPacket && routedAtMost(handler.getConnection(), 765)) {
         try {
            if (packet.getClass().getRecordComponents()[0].getAccessor().invoke(packet) instanceof GameMode gameMode) {
               handler.sendChatCommand("gamemode " + gameMode.getId());
               return true;
            } else {
               return false;
            }
         } catch (Throwable var4) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean routedAtMost(@Nullable ClientConnection connection, int protocol) {
      PbVia.Version version = routedVersion;
      return version != null && isRouted(connection) && version.atMost(protocol);
   }

   public static void setVanillaMove(boolean sending) {
      vanillaMove = sending;
   }

   public static boolean isVanillaMove() {
      return vanillaMove;
   }

   public static boolean markOwnMove(ClientConnection connection, Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
      if (packet instanceof PlayerMoveC2SPacket && !vanillaMove && !MARKING.get() && isRouted(connection) && !PbPredict.isDriven(connection)) {
         Packet<?> marker = new CustomPayloadC2SPacket(new UnknownCustomPayload(PbProtocol.OWN_MOVE));
         Channel channel = ((ClientConnectionAccessor)connection).getChannel();
         if (channel != null && channel.isOpen() && !channel.eventLoop().inEventLoop()) {
            channel.eventLoop().execute(() -> {
               MARKING.set(true);

               try {
                  connection.send(marker, null, false);
                  connection.send(packet, listener, flush);
               } finally {
                  MARKING.set(false);
               }
            });
            return true;
         } else {
            connection.send(marker, null, false);
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isExpectingJoin(String binding) {
      ClientConnection c = routedConnection;
      String mine = routedBinding;
      return c != null && c.isOpen() && System.currentTimeMillis() < joinWindowUntil && mine != null && binding != null
         ? MessageDigest.isEqual(mine.getBytes(StandardCharsets.UTF_8), binding.getBytes(StandardCharsets.UTF_8))
         : false;
   }

   public static void endJoinWindow() {
      joinWindowUntil = 0L;
   }

   public static String takeNotice() {
      String n = notice;
      notice = null;
      return n;
   }

   private record ViaSwitch(Object restoreTo) {
   }
}
