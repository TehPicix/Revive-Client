package revivedevelopment.reviveclient.systems.modules.hunting;

import com.mojang.authlib.GameProfile;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.ReceiveMessageEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.compat.XaeroCompat;

public class DiscordNotifs extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<String> webhookURL = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("webhook-link")
            .description("The discord webhook to use: https://discord.com/api/webhooks/<id>/<token>")
            .defaultValue("")
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("message-delay")
            .description("The delay between messages in milliseconds.")
            .defaultValue(2000)
            .min(0)
            .sliderRange(0, 10000)
            .build()
      );
   private final Setting<Boolean> queueMessages = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("queue-messages")
            .description("Queue messages sent too quickly instead of dropping them. A busy server can build a long backlog.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> timestamp = this.sgGeneral
      .add(new BoolSetting.Builder().name("timestamp").description("Prefix each message with a HH:mm timestamp.").defaultValue(false).build());
   private final Setting<Boolean> logAll = this.sgGeneral
      .add(new BoolSetting.Builder().name("all-messages").description("Logs all messages.").defaultValue(false).build());
   private final Setting<Boolean> connections = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("disconnect")
            .description("Log a message when you leave the server.")
            .defaultValue(false)
            .visible(() -> !this.logAll.get())
            .build()
      );
   private final Setting<Boolean> playerRange = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("player-range")
            .description("Log when players enter/exit your render distance.")
            .defaultValue(false)
            .visible(() -> !this.logAll.get())
            .build()
      );
   private final Setting<Boolean> queue = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("2b2t-queue")
            .description("Log your position in the 2b2t queue.")
            .defaultValue(false)
            .visible(() -> !this.logAll.get())
            .build()
      );
   private final Setting<Boolean> whisper = this.sgGeneral
      .add(new BoolSetting.Builder().name("whisper").description("Log whispers.").defaultValue(false).visible(() -> !this.logAll.get()).build());
   private final Setting<Boolean> chat = this.sgGeneral
      .add(new BoolSetting.Builder().name("chat-messages").description("Log chat messages.").defaultValue(false).visible(() -> !this.logAll.get()).build());
   private final Setting<Boolean> commands = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("commands-client-info")
            .description("Log commands and most messages from clients.")
            .defaultValue(false)
            .visible(() -> !this.logAll.get())
            .build()
      );
   private final Setting<Boolean> deathMessages = this.sgGeneral
      .add(new BoolSetting.Builder().name("death-messages").description("Log death messages.").defaultValue(false).visible(() -> !this.logAll.get()).build());
   private final Setting<Boolean> portalDetected = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("portal-detected")
            .description("Log when XaeroPlus's Portals highlighting marks a chunk near you (requires XaeroPlus with Portals enabled).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> portalSkip = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("portal-skip")
            .description("Log when XaeroPlus's PortalSkipDetection highlighting marks a chunk near you (requires XaeroPlus with PortalSkipDetection enabled).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> portalSkipWaypoint = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("portal-skip-waypoint")
            .description("Also adds a Xaero's minimap waypoint at each reported portal skip so you can find it later.")
            .defaultValue(true)
            .visible(this.portalSkip::get)
            .build()
      );
   private static final int MIN_WEBHOOK_SPACING_TICKS = 8;
   private long delayTimer = 0L;
   private int lastQueuePos;
   private final Queue<String> messageQueue = new LinkedList<>();
   private final Set<GameProfile> playersInRange = ConcurrentHashMap.newKeySet();
   private static final int PORTAL_SCAN_INTERVAL = 100;
   private static final int PORTAL_SCAN_RADIUS = 16;
   private Object portalsModule;
   private Object skipModule;
   private boolean xpResolveAttempted;
   private boolean xpAvailable;
   private int portalScanTimer;
   private Object lastDimension;
   private final Set<Long> reportedPortals = new HashSet<>();
   private final Set<Long> reportedSkips = new HashSet<>();

   public DiscordNotifs() {
      super(Categories.Hunting, "discord-notifs", "Sends notifications to a Discord webhook.");
   }

   @Override
   public void onActivate() {
      this.messageQueue.clear();
      this.playersInRange.clear();
      this.delayTimer = 0L;
      this.portalScanTimer = 0;
      this.reportedPortals.clear();
      this.reportedSkips.clear();
      this.lastDimension = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.delayTimer > 0L) {
         this.delayTimer--;
      } else if (this.queueMessages.get() && !this.messageQueue.isEmpty()) {
         this.sendWebhookMessage(this.messageQueue.poll());
      }

      if (this.mc.world != null && this.mc.player != null) {
         Set<UUID> uuidsCurrentlyInRange = new HashSet<>();
         if (this.playerRange.get()) {
            for (Entity entity : this.mc.world.getEntities()) {
               if (!entity.getUuid().equals(this.mc.player.getUuid()) && entity instanceof PlayerEntity playerEntity) {
                  uuidsCurrentlyInRange.add(playerEntity.getUuid());
                  if (!this.playersInRange.contains(playerEntity.getGameProfile())) {
                     this.playersInRange.add(playerEntity.getGameProfile());
                     this.handleMessage(playerEntity.getGameProfile().name() + " has entered visual range!", DiscordNotifs.MessageType.PLAYER_RANGE);
                  }
               }
            }
         }

         for (GameProfile profile : this.playersInRange) {
            if (!uuidsCurrentlyInRange.contains(profile.id())) {
               this.playersInRange.remove(profile);
               this.handleMessage(profile.name() + " has left visual range!", DiscordNotifs.MessageType.PLAYER_RANGE);
            }
         }

         if ((this.portalDetected.get() || this.portalSkip.get()) && ++this.portalScanTimer >= 100) {
            this.portalScanTimer = 0;
            this.resolveXaeroPlus();
            if (this.xpAvailable) {
               this.scanPortalHighlights();
            }
         }
      }
   }

   private void scanPortalHighlights() {
      Object dim = this.mc.world.getRegistryKey();
      if (!dim.equals(this.lastDimension)) {
         this.lastDimension = dim;
         this.reportedPortals.clear();
         this.reportedSkips.clear();
      }

      int cx = this.mc.player.getChunkPos().x;
      int cz = this.mc.player.getChunkPos().z;
      boolean checkPortals = this.portalDetected.get() && this.portalsModule != null && XaeroCompat.isModuleEnabled(this.portalsModule);
      boolean checkSkips = this.portalSkip.get() && this.skipModule != null && XaeroCompat.isModuleEnabled(this.skipModule);
      if (checkPortals || checkSkips) {
         RegistryKey<World> dimKey = this.mc.world.getRegistryKey();

         for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
               int x = cx + dx;
               int z = cz + dz;
               if (this.mc.world.getChunkManager().isChunkLoaded(x, z)) {
                  long key = ChunkPos.toLong(x, z);
                  if (checkPortals && !this.reportedPortals.contains(key) && XaeroCompat.isPortalChunk(this.portalsModule, x, z, dimKey)) {
                     this.reportedPortals.add(key);
                     this.handleMessage("Portal detected at " + (x * 16 + 8) + ", " + (z * 16 + 8) + this.dimSuffix(), DiscordNotifs.MessageType.PORTAL);
                  }

                  if (checkSkips && !this.reportedSkips.contains(key) && XaeroCompat.isPortalSkipChunk(this.skipModule, x, z, dimKey)) {
                     this.reportedSkips.add(key);
                     this.handleMessage(
                        "Portal skip detected at " + (x * 16 + 8) + ", " + (z * 16 + 8) + this.dimSuffix(), DiscordNotifs.MessageType.PORTAL_SKIP
                     );
                     if (this.portalSkipWaypoint.get()) {
                        this.addSkipWaypoint(x * 16 + 8, z * 16 + 8);
                     }
                  }
               }
            }
         }
      }
   }

   private String dimSuffix() {
      if (this.mc.world == null) {
         return "";
      } else {
         String path = this.mc.world.getRegistryKey().getValue().getPath();
         return " (" + path + ")";
      }
   }

   private void addSkipWaypoint(int x, int z) {
      XaeroCompat.addWaypointIfAbsent(x, 70, z, "Portal Skip", "P", 12);
   }

   private void resolveXaeroPlus() {
      if (!this.xpResolveAttempted) {
         this.xpResolveAttempted = true;

         try {
            this.portalsModule = XaeroCompat.getPortalsModule();
            this.skipModule = XaeroCompat.getPortalSkipModule();
            this.xpAvailable = this.portalsModule != null || this.skipModule != null;
         } catch (Throwable var2) {
            this.xpAvailable = false;
         }

         if (!this.xpAvailable) {
            this.info("XaeroPlus not found - portal webhooks disabled.", new Object[0]);
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onReceivePacket(PacketEvent.Receive event) {
      if (event.packet instanceof SubtitleS2CPacket packet) {
         String message = packet.text().getString();
         int queueIndex = message.indexOf("Position in queue: ");
         if (queueIndex != -1) {
            try {
               int queuePos = Integer.parseInt(message.substring(queueIndex + 19).trim());
               if (queuePos != this.lastQueuePos) {
                  this.handleMessage(message, DiscordNotifs.MessageType.QUEUE);
                  this.lastQueuePos = queuePos;
               }
            } catch (NumberFormatException var6) {
            }
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onMessageReceive(ReceiveMessageEvent event) {
      Text message = event.getMessage();

      for (Text sibling : message.getSiblings()) {
         TextColor color = sibling.getStyle().getColor();
         if (color != null && color.getRgb() == 43690) {
            this.handleMessage(message.getString(), DiscordNotifs.MessageType.DEATH);
            return;
         }
      }

      this.handleMessage(message.getString(), DiscordNotifs.MessageType.NORMAL);
   }

   public void handleMessage(String message, DiscordNotifs.MessageType messageType) {
      if (!this.webhookURL.get().isBlank()) {
         if (this.logAll.get()) {
            this.sendWebhookMessage(message);
         } else if (this.connections.get() && messageType == DiscordNotifs.MessageType.DISCONNECT) {
            this.sendWebhookMessage(message);
         } else if (this.playerRange.get() && messageType == DiscordNotifs.MessageType.PLAYER_RANGE) {
            this.sendWebhookMessage(message);
         } else if (this.queue.get() && messageType == DiscordNotifs.MessageType.QUEUE) {
            this.sendWebhookMessage(message);
         } else if (this.portalDetected.get() && messageType == DiscordNotifs.MessageType.PORTAL) {
            this.sendWebhookMessage(message);
         } else if (this.portalSkip.get() && messageType == DiscordNotifs.MessageType.PORTAL_SKIP) {
            this.sendWebhookMessage(message);
         } else if ((!this.whisper.get() || message.startsWith("<") || !message.contains("whispers: ")) && !message.startsWith("to ")) {
            if (this.chat.get() && message.startsWith("<")) {
               this.sendWebhookMessage(message);
            } else if (this.deathMessages.get() && messageType == DiscordNotifs.MessageType.DEATH) {
               this.sendWebhookMessage(message);
            } else if (this.commands.get() && !message.startsWith("<") && !message.startsWith("to ") && messageType == DiscordNotifs.MessageType.NORMAL) {
               this.sendWebhookMessage(message);
            }
         } else {
            this.sendWebhookMessage(message);
         }
      }
   }

   @EventHandler
   private void onDisconnect(GameLeftEvent event) {
      this.handleMessage("Disconnected", DiscordNotifs.MessageType.DISCONNECT);
   }

   private void sendWebhookMessage(String message) {
      if (this.delayTimer > 0L) {
         if (this.queueMessages.get()) {
            this.messageQueue.offer(message);
         }
      } else {
         this.delayTimer = Math.max(8L, Math.round(this.delay.get().intValue() / 50.0));
         String body = message;
         if (this.timestamp.get()) {
            body = "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "] " + message;
         }

         String safe = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
         String json = "{\"embeds\": [{\"description\": \"" + safe + "\"}]}";
         new Thread(() -> HuntingUtils.sendWebhook(this.webhookURL.get(), json, null)).start();
      }
   }

   public static enum MessageType {
      NORMAL,
      DEATH,
      QUEUE,
      DISCONNECT,
      PLAYER_RANGE,
      PORTAL,
      PORTAL_SKIP;
   }
}
