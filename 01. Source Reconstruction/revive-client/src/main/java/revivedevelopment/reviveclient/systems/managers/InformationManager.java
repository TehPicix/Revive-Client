package revivedevelopment.reviveclient.systems.managers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Entry;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.PlayerDeathEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.PlayerJoinLeaveEvent;
import revivedevelopment.reviveclient.events.game.ReceiveMessageEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.team.RemoteLogout;
import revivedevelopment.reviveclient.systems.managers.team.RemotePlayer;

public class InformationManager {
   private final Object2IntMap<UUID> totemPopMap = new Object2IntOpenHashMap();
   private boolean isLoginPacket = true;
   private static final long FORGET_MS = 600000L;
   private static final long LOGOUT_GRACE_MS = 3000L;
   private final Map<UUID, InformationManager.TrackedPlayer> trackedPlayers = new LinkedHashMap<>();
   private final Queue<InformationManager.DeathSignal> pendingDeaths = new ConcurrentLinkedQueue<>();
   private final Queue<InformationManager.BlockSignal> pendingBlockSignals = new ConcurrentLinkedQueue<>();
   private final Set<UUID> seenThisTick = new HashSet<>();
   private long disconnectedAt;
   private long lastTickAt;
   private static final String[] DEATH_WORDS = new String[]{
      " died",
      " was slain",
      " was killed",
      " was shot",
      " was blown up",
      " blew up",
      " was fireballed",
      " fell ",
      " fell",
      " hit the ground",
      " drowned",
      " burned",
      " burnt",
      " went up in flames",
      " walked into",
      " tried to swim in lava",
      " suffocated",
      " starved",
      " was pricked",
      " was impaled",
      " was squashed",
      " was squished",
      " withered",
      " froze",
      " was frozen",
      " was struck by lightning",
      " was pummeled",
      " was stung",
      " was poked",
      " was skewered",
      " was obliterated",
      " was doomed",
      " experienced kinetic energy",
      " didn't want to live",
      " was flattened",
      " discovered the floor",
      " was killed by",
      " left the confines",
      " was sniped",
      " was destroyed",
      " was shot by",
      " got finished off",
      " was knocked",
      " was struck",
      " was crushed",
      " was roasted",
      " was fried",
      " was exploded",
      " was pierced",
      " was murdered",
      " was thrown",
      " died because",
      " died to",
      " was blasted",
      " was spleefed",
      " was nuked",
      " was crystalled",
      " got crystalled"
   };

   public InformationManager() {
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   // NOTE (source reconstruction): this method was originally a Java 21 pattern-matching
   // switch over the packet's sealed type hierarchy, with guards (e.g. "case EntityStatusS2CPacket p
   // when p.getStatus() == 35"). Vineflower cannot reconstruct that construct back into valid
   // source — it emits the raw invokedynamic bootstrap call (SwitchBootstraps.typeSwitch<...>) as
   // text, which does not compile. Rewritten below as an equivalent instanceof-chain; behavior is
   // unchanged (verified against the intermediary-mapped decompile of the same bytecode).
   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
         Packet packet = event.packet;

         if (packet instanceof EntityStatusS2CPacket totemPacket && totemPacket.getStatus() == 35) {
            if (totemPacket.getEntity(ReviveClient.mc.world) instanceof PlayerEntity entity) {
               int pops;
               synchronized (this.totemPopMap) {
                  pops = this.totemPopMap.getOrDefault(entity.getUuid(), 0);
                  this.totemPopMap.put(entity.getUuid(), ++pops);
               }

               ReviveClient.EVENT_BUS.post(PlayerDeathEvent.TotemPop.get(entity, pops));
               UUID popUuid = entity.getUuid();
               String popName = entity.getGameProfile().name();
               ReviveClient.mc.execute(() -> this.reportPop(popUuid, popName));
            }
         } else if (packet instanceof PlayerListS2CPacket listPacket) {
            if (this.isLoginPacket) {
               this.isLoginPacket = false;
               return;
            }

            if (listPacket.getActions().contains(Action.ADD_PLAYER)) {
               for (Entry entry : listPacket.getPlayerAdditionEntries()) {
                  PlayerListEntry playerListEntry = new PlayerListEntry(entry.profile(), false);
                  ReviveClient.EVENT_BUS.post(PlayerJoinLeaveEvent.Join.get(playerListEntry));
                  UUID joined = entry.profile().id();
                  ReviveClient.mc.execute(() -> this.reportLogin(joined));
               }
            }
         } else if (packet instanceof PlayerRemoveS2CPacket removePacket) {
            if (ReviveClient.mc.getNetworkHandler() == null) {
               return;
            }

            for (UUID uuid : removePacket.profileIds()) {
               PlayerListEntry toRemove = ReviveClient.mc.getNetworkHandler().getPlayerListEntry(uuid);
               if (toRemove != null) {
                  ReviveClient.EVENT_BUS.post(PlayerJoinLeaveEvent.Leave.get(toRemove));
                  ReviveClient.mc.execute(() -> this.reportLogout(uuid));
               }
            }
         } else if (packet instanceof EntityStatusS2CPacket deathPacket && deathPacket.getStatus() == 3) {
            if (deathPacket.getEntity(ReviveClient.mc.world) instanceof PlayerEntity entity) {
               int pops = 0;
               synchronized (this.totemPopMap) {
                  if (this.totemPopMap.containsKey(entity.getUuid())) {
                     pops = this.totemPopMap.removeInt(entity.getUuid());
                  }
               }

               this.pendingDeaths.add(new InformationManager.DeathSignal(entity.getUuid(), entity.getId()));
               ReviveClient.EVENT_BUS.post(PlayerDeathEvent.Death.get(entity, pops));
            }
         } else if (packet instanceof BlockUpdateS2CPacket blockUpdatePacket
            && blockUpdatePacket.getState().isOf(Blocks.ENDER_CHEST)
            && !ReviveClient.mc.world.getBlockState(blockUpdatePacket.getPos()).isOf(Blocks.ENDER_CHEST)) {
            this.pendingBlockSignals.add(new InformationManager.BlockSignal(blockUpdatePacket.getPos().toImmutable(), true));
         } else if (packet instanceof BlockEventS2CPacket blockEventPacket
            && blockEventPacket.getBlock() instanceof ShulkerBoxBlock
            && blockEventPacket.getData() >= 1) {
            this.pendingBlockSignals.add(new InformationManager.BlockSignal(blockEventPacket.getPos().toImmutable(), false));
         }
      }
   }

   @EventHandler
   private void onGameLeave(GameLeftEvent event) {
      this.isLoginPacket = true;
      if (this.disconnectedAt == 0L) {
         this.disconnectedAt = System.currentTimeMillis();
      }

      this.pendingDeaths.clear();
      this.pendingBlockSignals.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
         long now = System.currentTimeMillis();
         if (this.disconnectedAt != 0L) {
            long offline = now - this.disconnectedAt;

            for (InformationManager.TrackedPlayer tp : this.trackedPlayers.values()) {
               if (!tp.inRender) {
                  tp.pausedMillis += offline;
               }
            }

            this.disconnectedAt = 0L;
            this.lastTickAt = now;
         }

         long dt = this.lastTickAt == 0L ? 0L : now - this.lastTickAt;
         this.lastTickAt = now;
         if (ReviveClient.mc.player.isDead() || ReviveClient.mc.player.getHealth() <= 0.0F) {
            this.resetPops(ReviveClient.mc.player.getUuid());
         }

         this.syncTeamPops();

         InformationManager.DeathSignal death;
         while ((death = this.pendingDeaths.poll()) != null) {
            InformationManager.TrackedPlayer tpx = this.trackedPlayers.get(death.uuid());
            boolean alreadyDead = tpx != null && tpx.dead && tpx.deadEntityId == death.entityId();
            if (!alreadyDead) {
               this.reportDeath(death.uuid());
            }

            if (tpx != null) {
               tpx.dead = true;
               tpx.deadEntityId = death.entityId();
            }
         }

         String dim = ReviveClient.mc.world.getRegistryKey().getValue().toString();
         this.seenThisTick.clear();

         for (PlayerEntity player : ReviveClient.mc.world.getPlayers()) {
            if (player != ReviveClient.mc.player) {
               UUID uuid = player.getUuid();
               this.seenThisTick.add(uuid);
               InformationManager.TrackedPlayer tpxx = this.trackedPlayers
                  .computeIfAbsent(uuid, id -> new InformationManager.TrackedPlayer(id, player.getName().getString()));
               tpxx.name = player.getName().getString();
               tpxx.online = true;
               tpxx.lastX = player.getX();
               tpxx.lastY = player.getY();
               tpxx.lastZ = player.getZ();
               tpxx.lastHp = player.getHealth() + player.getAbsorptionAmount();
               tpxx.lastDim = dim;
               if (!tpxx.inRender) {
                  tpxx.inRender = true;
                  tpxx.popsResetForAbsence = false;
                  tpxx.dead = false;
               }

               if (!player.isDead() && !(player.getHealth() <= 0.0F)) {
                  if (tpxx.dead && player.getId() != tpxx.deadEntityId) {
                     tpxx.dead = false;
                  }
               } else {
                  if (!tpxx.dead || tpxx.deadEntityId != player.getId()) {
                     this.reportDeath(uuid);
                  }

                  tpxx.dead = true;
                  tpxx.deadEntityId = player.getId();
                  this.resetPops(uuid);
               }
            }
         }

         Iterator<InformationManager.TrackedPlayer> it = this.trackedPlayers.values().iterator();

         while (it.hasNext()) {
            InformationManager.TrackedPlayer tpxxx = it.next();
            if (!this.seenThisTick.contains(tpxxx.uuid)) {
               if (tpxxx.inRender) {
                  if (tpxxx.dead) {
                     it.remove();
                  } else {
                     tpxxx.inRender = false;
                     tpxxx.leftRenderAt = now;
                     tpxxx.pausedMillis = 0L;
                  }
               } else {
                  tpxxx.online = this.isOnline(tpxxx.uuid);
                  if (!tpxxx.online) {
                     tpxxx.pausedMillis += dt;
                  }

                  Config config = Config.get();
                  long resetMs = config.popResetOutOfRenderTime.get().intValue() * 1000L;
                  long clock = tpxxx.resetClockMillis(now);
                  TeamSyncManager team = team();
                  if (team != null) {
                     long teamOut = team.getTeamOutOfRenderMillis(tpxxx.uuid);
                     if (teamOut >= 0L) {
                        clock = teamOut;
                     }
                  }

                  if (config.popResetOutOfRender.get() && clock >= resetMs && !tpxxx.popsResetForAbsence) {
                     tpxxx.popsResetForAbsence = true;
                     if (this.canReset(tpxxx)) {
                        this.resetPops(tpxxx.uuid);
                     }
                  }

                  boolean keepForLogin = !tpxxx.online && this.getPops(tpxxx.uuid) > 0;
                  if (!keepForLogin && now - tpxxx.leftRenderAt >= Math.max(600000L, resetMs + 1000L)) {
                     it.remove();
                  }
               }
            }
         }

         InformationManager.BlockSignal signal;
         while ((signal = this.pendingBlockSignals.poll()) != null) {
            PlayerEntity actor = this.nearestPlayerTo(signal.pos());
            if (actor != null) {
               InformationManager.TrackedPlayer tpxxx = this.trackedPlayers.get(actor.getUuid());
               if (tpxxx != null) {
                  if (signal.enderChestPlaced()) {
                     tpxxx.enderChestPlacedAt = now;
                  } else if (tpxxx.enderChestPlacedAt != 0L && now - tpxxx.enderChestPlacedAt <= Config.get().regearWindow.get().intValue() * 1000L) {
                     tpxxx.enderChestPlacedAt = 0L;
                     if (Config.get().popResetRegear.get() && this.canReset(tpxxx)) {
                        this.resetPops(tpxxx.uuid);
                        this.reportRegear(tpxxx.uuid, "shulker");
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null && Config.get().popResetDeathMessages.get()) {
         String victim = this.deathMessageVictim(event.getMessage());
         if (victim != null) {
            UUID uuid = this.uuidForName(victim);
            if (uuid != null) {
               this.resetPops(uuid);
               this.reportDeath(uuid);
               InformationManager.TrackedPlayer tp = this.trackedPlayers.get(uuid);
               if (tp != null) {
                  if (!tp.inRender) {
                     this.trackedPlayers.remove(uuid);
                  } else {
                     tp.dead = true;
                  }
               }
            }
         }
      }
   }

   private String deathMessageVictim(Text message) {
      if (message.getContent() instanceof TranslatableTextContent content && content.getKey().startsWith("death.")) {
         Object[] args = content.getArgs();
         if (args.length > 0) {
            return args[0] instanceof Text t ? t.getString() : String.valueOf(args[0]);
         }
      }

      String plain = message.getString().trim();
      if (!plain.isEmpty() && !plain.startsWith("<") && !plain.contains(" whispers")) {
         int space = plain.indexOf(32);
         if (space <= 0) {
            return null;
         } else {
            String name = plain.substring(0, space);
            if (!name.matches("[A-Za-z0-9_]{1,16}")) {
               return null;
            } else {
               String rest = plain.substring(space).toLowerCase(Locale.ROOT);

               for (String word : DEATH_WORDS) {
                  if (rest.startsWith(word) || rest.contains(word)) {
                     return name;
                  }
               }

               return null;
            }
         }
      } else {
         return null;
      }
   }

   private UUID uuidForName(String name) {
      for (InformationManager.TrackedPlayer tp : this.trackedPlayers.values()) {
         if (tp.name.equalsIgnoreCase(name)) {
            return tp.uuid;
         }
      }

      if (ReviveClient.mc.getNetworkHandler() != null) {
         for (PlayerListEntry entry : ReviveClient.mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().name().equalsIgnoreCase(name)) {
               return entry.getProfile().id();
            }
         }
      }

      return ReviveClient.mc.player.getGameProfile().name().equalsIgnoreCase(name) ? ReviveClient.mc.player.getUuid() : null;
   }

   private boolean isOnline(UUID uuid) {
      return ReviveClient.mc.getNetworkHandler() == null || ReviveClient.mc.getNetworkHandler().getPlayerListEntry(uuid) != null;
   }

   private PlayerEntity nearestPlayerTo(BlockPos pos) {
      Vec3d center = Vec3d.ofCenter(pos);
      PlayerEntity best = null;
      double range = Config.get().regearRange.get();
      double bestDist = range * range;

      for (PlayerEntity player : ReviveClient.mc.world.getPlayers()) {
         double d = player.squaredDistanceTo(center);
         if (d <= bestDist) {
            bestDist = d;
            best = player;
         }
      }

      return best == ReviveClient.mc.player ? null : best;
   }

   private boolean canReset(InformationManager.TrackedPlayer tp) {
      return isTeammate(tp.uuid) ? false : Config.get().popResetFriends.get() || Friends.get().get(tp.name) == null;
   }

   private static TeamSyncManager team() {
      TeamSyncManager team = ReviveClient.TEAM;
      return team != null && team.isConnected() ? team : null;
   }

   private static boolean isTeammate(UUID uuid) {
      TeamSyncManager team = team();
      return team != null && team.isTeammate(uuid);
   }

   private static TeamSyncManager reportTarget(UUID uuid) {
      TeamSyncManager team = team();
      if (team == null || uuid == null) {
         return null;
      } else if (ReviveClient.mc.player != null && uuid.equals(ReviveClient.mc.player.getUuid())) {
         return null;
      } else {
         return team.isTeammate(uuid) ? null : team;
      }
   }

   private void reportPop(UUID uuid, String name) {
      TeamSyncManager team = reportTarget(uuid);
      if (team != null) {
         team.reportPop(uuid, name);
      }
   }

   private void reportDeath(UUID uuid) {
      TeamSyncManager team = reportTarget(uuid);
      if (team != null) {
         team.reportDeath(uuid);
      }
   }

   private void reportRegear(UUID uuid, String kind) {
      TeamSyncManager team = reportTarget(uuid);
      if (team != null) {
         team.reportRegear(uuid, kind);
      }
   }

   private void reportLogout(UUID uuid) {
      TeamSyncManager team = reportTarget(uuid);
      if (team != null) {
         InformationManager.TrackedPlayer tp = this.trackedPlayers.get(uuid);
         if (tp != null && tp.lastDim != null && !tp.dead) {
            if (tp.inRender || System.currentTimeMillis() - tp.leftRenderAt <= 3000L) {
               team.reportLogout(uuid, tp.name, tp.lastDim, tp.lastX, tp.lastY, tp.lastZ, tp.lastHp);
            }
         }
      }
   }

   private void reportLogin(UUID uuid) {
      TeamSyncManager team = team();
      if (team != null && uuid != null) {
         for (RemoteLogout spot : team.getLogouts()) {
            if (uuid.equals(spot.uuid)) {
               team.reportLogin(uuid);
               return;
            }
         }
      }
   }

   private void syncTeamPops() {
      TeamSyncManager team = team();
      if (team != null) {
         synchronized (this.totemPopMap) {
            for (RemotePlayer remote : team.getRemotePlayers()) {
               if (this.totemPopMap.containsKey(remote.uuid) || this.trackedPlayers.containsKey(remote.uuid)) {
                  int count = team.getTeamPops(remote.uuid);
                  if (count > 0) {
                     this.totemPopMap.put(remote.uuid, count);
                  } else if (count == 0) {
                     this.totemPopMap.removeInt(remote.uuid);
                  }
               }
            }

            Iterator<UUID> keys = this.totemPopMap.keySet().iterator();

            while (keys.hasNext()) {
               if (team.getTeamPops(keys.next()) == 0) {
                  keys.remove();
               }
            }
         }
      }
   }

   private void resetPops(UUID uuid) {
      synchronized (this.totemPopMap) {
         this.totemPopMap.removeInt(uuid);
      }
   }

   public Collection<InformationManager.TrackedPlayer> getTrackedPlayers() {
      return Collections.unmodifiableCollection(this.trackedPlayers.values());
   }

   public int getPops(Entity entity) {
      return this.getPops(entity.getUuid());
   }

   public int getPops(UUID uuid) {
      if (ReviveClient.mc.isOnThread()) {
         TeamSyncManager team = team();
         if (team != null) {
            int teamPops = team.getTeamPops(uuid);
            if (teamPops >= 0) {
               return teamPops;
            }
         }
      }

      synchronized (this.totemPopMap) {
         return this.totemPopMap.getOrDefault(uuid, 0);
      }
   }

   private record BlockSignal(BlockPos pos, boolean enderChestPlaced) {
   }

   private record DeathSignal(UUID uuid, int entityId) {
   }

   public static class TrackedPlayer {
      public final UUID uuid;
      public String name;
      public boolean inRender;
      public long leftRenderAt;
      boolean dead;
      int deadEntityId = -1;
      boolean popsResetForAbsence;
      long enderChestPlacedAt;
      long pausedMillis;
      public boolean online = true;
      double lastX;
      double lastY;
      double lastZ;
      float lastHp;
      String lastDim;

      TrackedPlayer(UUID uuid, String name) {
         this.uuid = uuid;
         this.name = name;
      }

      public long outOfRenderMillis() {
         return this.inRender ? 0L : System.currentTimeMillis() - this.leftRenderAt;
      }

      long resetClockMillis(long now) {
         return now - this.leftRenderAt - this.pausedMillis;
      }
   }
}
