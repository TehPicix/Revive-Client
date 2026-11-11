package revivedevelopment.reviveclient.pingbypass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;

public final class PbVia {
   public static final int MIN_PROTOCOL = 765;
   public static final int LAST_LEGACY_CLICK_PROTOCOL = 769;
   public static final int FIRST_USE_LOOK_PROTOCOL = 755;
   public static final int LAST_USE_LOOK_PROTOCOL = 766;
   public static final int FIRST_TICK_END_PROTOCOL = 768;
   public static final int LAST_SIGNED_COMMANDS_PROTOCOL = 765;
   private static volatile boolean resolved;
   private static boolean usable;
   private static Method getImpl;
   private static Method getVfpVersion;
   private static Method getTarget;
   private static Method getConnectionTarget;
   private static Method setTarget;
   private static Method connectionSetTarget;
   private static Method pvGetProtocol;
   private static Method pvGetVersionType;
   private static Method pvGetVersion;
   private static Method pvGetName;
   private static Class<?> versionTypeClass;
   private static final int AUTO_DETECT_ID = -2;
   public static volatile boolean connectionVersionIsServers;
   public static volatile boolean viaEmulatesForServer;
   private static volatile ClientConnection emulatedFor;
   private static volatile boolean emulatedForVia;
   private static volatile int emulatedProtocol;
   private static volatile boolean clickResolved;
   private static Method getUserConnection;
   private static Method wrapperCreate;
   private static Method wrapperWrite;
   private static Method wrapperSendToServer;
   private static Method emptyItem;
   private static Object clickType;
   private static Object varInt;
   private static Object shortType;
   private static Object byteType;
   private static Object itemType1_21_4;
   private static Class<?> protocol1_21_4To1_21_5;

   private PbVia() {
   }

   private static synchronized boolean resolve() {
      if (resolved) {
         return usable;
      } else {
         resolved = true;
         if (!FabricLoader.getInstance().isModLoaded("viafabricplus")) {
            return false;
         } else {
            try {
               Class<?> entry = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
               Class<?> base = Class.forName("com.viaversion.viafabricplus.api.ViaFabricPlusBase");
               Class<?> pv = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
               versionTypeClass = Class.forName("com.viaversion.viaversion.api.protocol.version.VersionType");
               Class<?> connectionAccess = Class.forName("com.viaversion.viafabricplus.injection.access.base.IConnection");
               getImpl = entry.getMethod("getImpl");
               getVfpVersion = base.getMethod("getVersion");
               getTarget = base.getMethod("getTargetVersion");
               getConnectionTarget = base.getMethod("getTargetVersion", ClientConnection.class);
               setTarget = base.getMethod("setTargetVersion", pv);
               connectionSetTarget = connectionAccess.getMethod("viaFabricPlus$setTargetVersion", pv);
               pvGetProtocol = pv.getMethod("getProtocol", versionTypeClass, int.class);
               pvGetVersionType = pv.getMethod("getVersionType");
               pvGetVersion = pv.getMethod("getVersion");
               pvGetName = pv.getMethod("getName");
               usable = true;
            } catch (Throwable var4) {
               ReviveClient.LOG.warn("[PingBypass] this ViaFabricPlus build isn't supported, joins with it can't be routed: {}", var4.toString());
               usable = false;
            }

            return usable;
         }
      }
   }

   @Nullable
   private static Object impl() {
      if (!resolve()) {
         return null;
      } else {
         try {
            return getImpl.invoke(null);
         } catch (Throwable var1) {
            return null;
         }
      }
   }

   public static boolean available() {
      return impl() != null;
   }

   public static boolean installed() {
      return FabricLoader.getInstance().isModLoaded("viafabricplus");
   }

   @Nullable
   public static String vfpVersion() {
      Object impl = impl();
      if (impl == null) {
         return null;
      } else {
         try {
            return String.valueOf(getVfpVersion.invoke(impl));
         } catch (Throwable var2) {
            return null;
         }
      }
   }

   @Nullable
   public static Object globalTarget() {
      Object impl = impl();
      if (impl == null) {
         return null;
      } else {
         try {
            return getTarget.invoke(impl);
         } catch (Throwable var2) {
            return null;
         }
      }
   }

   public static boolean setGlobal(@Nullable Object version) {
      Object impl = impl();
      if (impl != null && version != null) {
         try {
            setTarget.invoke(impl, version);
            return true;
         } catch (Throwable var3) {
            ReviveClient.LOG.warn("[PingBypass] could not switch ViaFabricPlus to {}: {}", describeOrNull(version), var3.toString());
            return false;
         }
      } else {
         return false;
      }
   }

   @Nullable
   public static Object connectionTarget(ClientConnection connection) {
      Object impl = impl();
      if (impl != null && connection != null) {
         try {
            return getConnectionTarget.invoke(impl, connection);
         } catch (Throwable var3) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static boolean forceConnection(ClientConnection connection, @Nullable Object version) {
      if (resolve() && connection != null && version != null) {
         try {
            connectionSetTarget.invoke(connection, version);
            return true;
         } catch (Throwable var3) {
            ReviveClient.LOG.warn("[PingBypass] could not set ViaFabricPlus's version on a connection: {}", var3.toString());
            return false;
         }
      } else {
         return false;
      }
   }

   @Nullable
   public static Object nativeVersion() {
      return resolve(new PbVia.Version("RELEASE", SharedConstants.getProtocolVersion(), ""));
   }

   @Nullable
   public static Object resolve(@Nullable PbVia.Version version) {
      if (version != null && resolve()) {
         try {
            Object type = Enum.valueOf((Class)versionTypeClass, version.type());
            return pvGetProtocol.invoke(null, type, version.id());
         } catch (Throwable var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public static PbVia.Version describe(@Nullable Object version) {
      if (version != null && resolve()) {
         try {
            return new PbVia.Version(
               String.valueOf(pvGetVersionType.invoke(version)), (Integer)pvGetVersion.invoke(version), String.valueOf(pvGetName.invoke(version))
            );
         } catch (Throwable var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public static PbVia.Version effective(@Nullable Object version) {
      PbVia.Version v = describe(version);
      if (v == null) {
         return null;
      } else {
         return "SPECIAL".equals(v.type()) && v.id() == -2 ? describe(nativeVersion()) : v;
      }
   }

   public static int emulatedServerProtocol() {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayNetworkHandler handler = mc == null ? null : mc.getNetworkHandler();
      return emulatedServerProtocol(handler == null ? null : handler.getConnection());
   }

   public static int emulatedServerProtocol(@Nullable ClientConnection connection) {
      int own = SharedConstants.getProtocolVersion();
      if (connection == null) {
         return own;
      } else {
         boolean via = viaEmulatesForServer;
         if (connection == emulatedFor && via == emulatedForVia) {
            return emulatedProtocol;
         } else {
            PbVia.Version v = null;
            if (PbRouting.isRouted(connection)) {
               v = PbRouting.routedVersion();
            } else if (connectionVersionIsServers && !via) {
               v = describe(connectionTarget(connection));
            }

            int protocol = v != null && "RELEASE".equals(v.type()) ? v.id() : own;
            emulatedProtocol = protocol;
            emulatedForVia = via;
            emulatedFor = connection;
            return protocol;
         }
      }
   }

   public static boolean emulatesAtMost(int protocol) {
      return emulatedServerProtocol() <= protocol;
   }

   private static synchronized boolean resolveClick() {
      if (clickResolved) {
         return wrapperSendToServer != null;
      } else {
         clickResolved = true;
         if (!resolve()) {
            return false;
         } else {
            try {
               Class<?> connectionAccess = Class.forName("com.viaversion.viafabricplus.injection.access.base.IConnection");
               getUserConnection = connectionAccess.getMethod("viaFabricPlus$getUserConnection");
               Class<?> packetType = Class.forName("com.viaversion.viaversion.api.protocol.packet.PacketType");
               Class<?> userConnection = Class.forName("com.viaversion.viaversion.api.connection.UserConnection");
               Class<?> wrapper = Class.forName("com.viaversion.viaversion.api.protocol.packet.PacketWrapper");
               Class<?> type = Class.forName("com.viaversion.viaversion.api.type.Type");
               wrapperCreate = wrapper.getMethod("create", packetType, userConnection);
               wrapperWrite = wrapper.getMethod("write", type, Object.class);
               Object click = Enum.valueOf(
                  (Class)Class.forName("com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4"), "CONTAINER_CLICK"
               );
               clickType = click;
               Class<?> types = Class.forName("com.viaversion.viaversion.api.type.Types");
               varInt = types.getField("VAR_INT").get(null);
               shortType = types.getField("SHORT").get(null);
               byteType = types.getField("BYTE").get(null);
               Object types1_21_4 = Class.forName("com.viaversion.viaversion.api.type.types.version.VersionedTypes").getField("V1_21_4").get(null);
               itemType1_21_4 = types1_21_4.getClass().getField("item").get(types1_21_4);
               emptyItem = Class.forName("com.viaversion.viaversion.api.minecraft.item.StructuredItem").getMethod("empty");
               protocol1_21_4To1_21_5 = Class.forName("com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5");
               Method send = wrapper.getMethod("sendToServer", Class.class);
               wrapperSendToServer = send;
               return true;
            } catch (Throwable var9) {
               ReviveClient.LOG.warn("[PingBypass] this ViaFabricPlus build can't take inventory clicks for 1.21.4 and older: {}", var9.toString());
               wrapperSendToServer = null;
               return false;
            }
         }
      }
   }

   public static boolean needsLegacyClicks(@Nullable PbVia.Version version) {
      return version != null && "RELEASE".equals(version.type()) && version.id() <= 769;
   }

   public static boolean sendLegacyClick(ClientConnection connection, int syncId, int revision, short slot, byte button, int actionIndex) {
      if (!resolveClick()) {
         return false;
      } else {
         try {
            Object user = getUserConnection.invoke(connection);
            if (user == null) {
               return false;
            } else {
               Object wrapper = wrapperCreate.invoke(null, clickType, user);
               wrapperWrite.invoke(wrapper, varInt, syncId);
               wrapperWrite.invoke(wrapper, varInt, revision);
               wrapperWrite.invoke(wrapper, shortType, slot);
               wrapperWrite.invoke(wrapper, byteType, button);
               wrapperWrite.invoke(wrapper, varInt, actionIndex);
               wrapperWrite.invoke(wrapper, varInt, 0);
               wrapperWrite.invoke(wrapper, itemType1_21_4, emptyItem.invoke(null));
               wrapperSendToServer.invoke(wrapper, protocol1_21_4To1_21_5);
               return true;
            }
         } catch (Throwable var8) {
            ReviveClient.LOG.warn("[PingBypass] couldn't send an inventory click for an old version: {}", var8.toString());
            return false;
         }
      }
   }

   public static String nameOf(int protocol) {
      PbVia.Version v = describe(resolve(new PbVia.Version("RELEASE", protocol, "")));
      return v != null ? v.name() + " (protocol " + protocol + ")" : "protocol " + protocol;
   }

   public static boolean isTranslating(ClientConnection connection) {
      PbVia.Version v = describe(connectionTarget(connection));
      return v != null && !v.isNative();
   }

   private static String describeOrNull(Object version) {
      PbVia.Version v = describe(version);
      return v == null ? String.valueOf(version) : v.toString();
   }

   public record Version(String type, int id, String name) {
      public JsonObject toJson() {
         JsonObject o = new JsonObject();
         o.addProperty("type", this.type);
         o.addProperty("id", this.id);
         o.addProperty("name", this.name);
         return o;
      }

      @Nullable
      public static PbVia.Version fromJson(@Nullable JsonElement e) {
         if (e != null && e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && o.has("id")) {
               try {
                  return new PbVia.Version(o.get("type").getAsString(), o.get("id").getAsInt(), PbProtocol.str(o, "name", "protocol " + o.get("id").getAsInt()));
               } catch (RuntimeException var3) {
                  return null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      }

      public boolean isNative() {
         return "RELEASE".equals(this.type) && this.id == SharedConstants.getProtocolVersion();
      }

      public boolean routable() {
         return "RELEASE".equals(this.type) && this.id >= 765 && this.id <= SharedConstants.getProtocolVersion();
      }

      public boolean atMost(int protocol) {
         return "RELEASE".equals(this.type) && this.id <= protocol;
      }

      @Override
      public String toString() {
         return this.name + " (protocol " + this.id + ")";
      }
   }
}
