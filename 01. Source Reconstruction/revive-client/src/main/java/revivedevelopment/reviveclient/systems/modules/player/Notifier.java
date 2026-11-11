package revivedevelopment.reviveclient.systems.modules.player;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Entry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.entity.EntityAddedEvent;
import revivedevelopment.reviveclient.events.entity.EntityRemovedEvent;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.BlockUpdateEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IChatHud;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.hud.elements.NotificationsHud;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.misc.AlertSounds;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Notifier extends Module {
   private static final byte USE_TOTEM_OF_UNDYING = 35;
   private final SettingGroup sgTotemPops = this.settings.createGroup("Totem Pops");
   private final SettingGroup sgVisualRange = this.settings.createGroup("Visual Range");
   private final SettingGroup sgPvpActivity = this.settings.createGroup("PvP Activity");
   private final SettingGroup sgPearl = this.settings.createGroup("Pearl");
   private final SettingGroup sgJoinsLeaves = this.settings.createGroup("Joins/Leaves");
   private final Setting<Boolean> totemPops = this.sgTotemPops
      .add(new BoolSetting.Builder().name("totem-pops").description("Notifies you when a player pops a totem.").defaultValue(true).build());
   private final Setting<Boolean> totemsDistanceCheck = this.sgTotemPops
      .add(
         new BoolSetting.Builder()
            .name("distance-check")
            .description("Limits the distance in which the pops are recognized.")
            .defaultValue(false)
            .visible(this.totemPops::get)
            .build()
      );
   private final Setting<Integer> totemsDistance = this.sgTotemPops
      .add(
         new IntSetting.Builder()
            .name("player-radius")
            .description("The radius in which to log totem pops.")
            .defaultValue(30)
            .sliderRange(1, 50)
            .range(1, 100)
            .visible(() -> this.totemPops.get() && this.totemsDistanceCheck.get())
            .build()
      );
   private final Setting<Boolean> totemsIgnoreOwn = this.sgTotemPops
      .add(new BoolSetting.Builder().name("ignore-own").description("Ignores your own totem pops.").defaultValue(false).build());
   private final Setting<Boolean> totemsIgnoreFriends = this.sgTotemPops
      .add(new BoolSetting.Builder().name("ignore-friends").description("Ignores friends totem pops.").defaultValue(false).build());
   private final Setting<Boolean> totemsIgnoreOthers = this.sgTotemPops
      .add(new BoolSetting.Builder().name("ignore-others").description("Ignores other players totem pops.").defaultValue(false).build());
   private final Setting<Boolean> visualRange = this.sgVisualRange
      .add(new BoolSetting.Builder().name("visual-range").description("Notifies you when an entity enters your render distance.").defaultValue(true).build());
   private final Setting<Notifier.Event> event = this.sgVisualRange
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("event")).description("When to log the entities."))
               .defaultValue(Notifier.Event.Both))
            .build()
      );
   private final Setting<Set<EntityType<?>>> entities = this.sgVisualRange
      .add(new EntityTypeListSetting.Builder().name("entities").description("Which entities to notify about.").defaultValue(EntityType.PLAYER).build());
   private final Setting<Boolean> visualRangeIgnoreFriends = this.sgVisualRange
      .add(new BoolSetting.Builder().name("ignore-friends").description("Ignores friends.").defaultValue(true).build());
   private final Setting<Boolean> visualRangeIgnoreFakes = this.sgVisualRange
      .add(new BoolSetting.Builder().name("ignore-fake-players").description("Ignores fake players.").defaultValue(true).build());
   private final Setting<Boolean> visualRangeIgnoreNakeds = this.sgVisualRange
      .add(new BoolSetting.Builder().name("ignore-nakeds").description("Ignores players without any armor.").defaultValue(false).build());
   private final Setting<Boolean> visualMakeSound = this.sgVisualRange
      .add(new BoolSetting.Builder().name("sound").description("Play an alert sound when a player enters your visual range.").defaultValue(true).build());
   private final Setting<AlertSounds.AlertSound> visualSoundType = this.sgVisualRange
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("sound-type"))
                     .description("Which alert to play. UhOh is the custom clip shipped with the client."))
                  .defaultValue(AlertSounds.AlertSound.UhOh))
               .visible(this.visualMakeSound::get))
            .build()
      );
   private final Setting<Boolean> visualShowDistance = this.sgVisualRange
      .add(new BoolSetting.Builder().name("show-distance").description("Shows the distance to the player when they enter range.").defaultValue(true).build());
   private final Setting<Notifier.DirectionMode> visualDirection = this.sgVisualRange
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("show-direction"))
                  .description("Direction hints: hidden, absolute, or relative to your facing."))
               .defaultValue(Notifier.DirectionMode.Relative))
            .build()
      );
   private final Setting<Boolean> visualPopup = this.sgVisualRange
      .add(
         new BoolSetting.Builder()
            .name("popup")
            .description("Also shows a toast popup (requires the Notifications HUD element to be active).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> visualEquipment = this.sgVisualRange
      .add(
         new BoolSetting.Builder()
            .name("show-equipment")
            .description("Include the player's visible armor and held items in the enter notification.")
            .defaultValue(false)
            .visible(this.visualRange::get)
            .build()
      );
   private final Setting<Boolean> crystalPlace = this.sgPvpActivity
      .add(new BoolSetting.Builder().name("crystal-place").description("Notifies you when a player places an end crystal nearby.").defaultValue(false).build());
   private final Setting<Boolean> crystalPop = this.sgPvpActivity
      .add(
         new BoolSetting.Builder()
            .name("crystal-pop")
            .description("Notifies you when a player pops (destroys) an end crystal nearby.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> anchorPlace = this.sgPvpActivity
      .add(new BoolSetting.Builder().name("anchor-place").description("Notifies you when a respawn anchor is placed nearby.").defaultValue(false).build());
   private final Setting<Boolean> anchorPop = this.sgPvpActivity
      .add(
         new BoolSetting.Builder()
            .name("anchor-pop")
            .description("Notifies you when a respawn anchor is destroyed (exploded) nearby.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> pvpRange = this.sgPvpActivity
      .add(
         new IntSetting.Builder()
            .name("pvp-range")
            .description("Maximum range to attribute crystal/anchor activity to a player.")
            .defaultValue(6)
            .range(1, 32)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Boolean> pvpIgnoreSelf = this.sgPvpActivity
      .add(new BoolSetting.Builder().name("ignore-own").description("Don't attribute crystal/anchor activity to yourself.").defaultValue(true).build());
   private final Setting<Boolean> pearl = this.sgPearl
      .add(new BoolSetting.Builder().name("pearl").description("Notifies you when a player is teleported using an ender pearl.").defaultValue(true).build());
   private final Setting<Boolean> pearlIgnoreOwn = this.sgPearl
      .add(new BoolSetting.Builder().name("ignore-own").description("Ignores your own pearls.").defaultValue(false).build());
   private final Setting<Boolean> pearlIgnoreFriends = this.sgPearl
      .add(new BoolSetting.Builder().name("ignore-friends").description("Ignores friends pearls.").defaultValue(false).build());
   private final Setting<Notifier.JoinLeaveModes> joinsLeavesMode = this.sgJoinsLeaves
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("player-joins-leaves"))
                  .description("How to handle player join/leave notifications."))
               .defaultValue(Notifier.JoinLeaveModes.None))
            .build()
      );
   private final Setting<Integer> notificationDelay = this.sgJoinsLeaves
      .add(
         new IntSetting.Builder()
            .name("notification-delay")
            .description("How long to wait in ticks before posting the next join/leave notification in your chat.")
            .range(0, 1000)
            .sliderRange(0, 100)
            .defaultValue(0)
            .build()
      );
   private final Setting<Boolean> simpleNotifications = this.sgJoinsLeaves
      .add(
         new BoolSetting.Builder()
            .name("simple-notifications")
            .description("Display join/leave notifications without a prefix, to reduce chat clutter.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> replaceNotifications = this.sgJoinsLeaves
      .add(
         new BoolSetting.Builder()
            .name("replace-notifications")
            .description("Join/leave notifications replace each other instead of stacking in chat.")
            .defaultValue(false)
            .build()
      );
   private final Setting<SettingColor> joinBracketColor = this.sgJoinsLeaves
      .add(
         new ColorSetting.Builder()
            .name("join-bracket-color")
            .description("Color of the brackets on join notifications.")
            .defaultValue(new SettingColor(Formatting.GRAY))
            .build()
      );
   private final Setting<SettingColor> joinSymbolColor = this.sgJoinsLeaves
      .add(
         new ColorSetting.Builder()
            .name("join-symbol-color")
            .description("Color of the + symbol on join notifications.")
            .defaultValue(new SettingColor(Formatting.GREEN))
            .build()
      );
   private final Setting<SettingColor> joinNameColor = this.sgJoinsLeaves
      .add(
         new ColorSetting.Builder()
            .name("join-name-color")
            .description("Color of the player name on join notifications.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .build()
      );
   private final Setting<SettingColor> leaveBracketColor = this.sgJoinsLeaves
      .add(
         new ColorSetting.Builder()
            .name("leave-bracket-color")
            .description("Color of the brackets on leave notifications.")
            .defaultValue(new SettingColor(Formatting.GRAY))
            .build()
      );
   private final Setting<SettingColor> leaveSymbolColor = this.sgJoinsLeaves
      .add(
         new ColorSetting.Builder()
            .name("leave-symbol-color")
            .description("Color of the - symbol on leave notifications.")
            .defaultValue(new SettingColor(Formatting.RED))
            .build()
      );
   private final Setting<SettingColor> leaveNameColor = this.sgJoinsLeaves
      .add(
         new ColorSetting.Builder()
            .name("leave-name-color")
            .description("Color of the player name on leave notifications.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .build()
      );
   private int timer;
   private boolean loginPacket = true;
   private final Set<UUID> visualRangeTracked = new HashSet<>();
   private final Map<UUID, String> visualRangeNames = new HashMap<>();
   private boolean visualRangeSkipFirst = true;
   private final Object2IntMap<UUID> totemPopMap = new Object2IntOpenHashMap();
   private final Object2IntMap<UUID> chatIdMap = new Object2IntOpenHashMap();
   private final Map<Integer, Vec3d> pearlStartPosMap = new HashMap<>();
   private final ArrayDeque<Text> messageQueue = new ArrayDeque<>();
   private final Set<BlockPos> trackedAnchors = new HashSet<>();
   private static final int JOIN_LEAVE_CHAT_ID = "notifier-joinleave".hashCode();
   private final Random random = new Random();

   public Notifier() {
      super(Categories.Gui, "notifier", "Notifies you of different events.");
   }

   @EventHandler
   private void onEntityAdded(EntityAddedEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (!event.entity.getUuid().equals(this.mc.player.getUuid())
            && this.entities.get().contains(event.entity.getType())
            && this.visualRange.get()
            && this.event.get() != Notifier.Event.Despawn
            && !(event.entity instanceof PlayerEntity)) {
            MutableText text = Text.literal(event.entity.getType().getName().getString()).formatted(Formatting.WHITE);
            text.append(Text.literal(" has spawned at ").formatted(Formatting.GRAY));
            text.append(ChatUtils.formatCoords(event.entity.getEntityPos()));
            text.append(Text.literal(".").formatted(Formatting.GRAY));
            this.info(text);
         }

         if (this.pearl.get() && event.entity instanceof EnderPearlEntity pearlEntity) {
            this.pearlStartPosMap.put(pearlEntity.getId(), new Vec3d(pearlEntity.getX(), pearlEntity.getY(), pearlEntity.getZ()));
         }

         if (this.crystalPlace.get() && event.entity instanceof EndCrystalEntity crystal) {
            Vec3d pos = crystal.getEntityPos();
            this.notifyPvpActivity(false, this.findNearestPlayer(pos), "an", "End Crystal", (int)pos.x, (int)pos.y, (int)pos.z);
         }
      }
   }

   @EventHandler
   private void onEntityRemoved(EntityRemovedEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (!event.entity.getUuid().equals(this.mc.player.getUuid())
            && this.entities.get().contains(event.entity.getType())
            && this.visualRange.get()
            && this.event.get() != Notifier.Event.Spawn
            && !(event.entity instanceof PlayerEntity)) {
            MutableText text = Text.literal(event.entity.getType().getName().getString()).formatted(Formatting.WHITE);
            text.append(Text.literal(" has despawned at ").formatted(Formatting.GRAY));
            text.append(ChatUtils.formatCoords(event.entity.getEntityPos()));
            text.append(Text.literal(".").formatted(Formatting.GRAY));
            this.info(text);
         }

         if (this.pearl.get()) {
            Entity e = event.entity;
            int i = e.getId();
            if (this.pearlStartPosMap.containsKey(i)) {
               EnderPearlEntity pearl = (EnderPearlEntity)e;
               if (pearl.getOwner() != null && pearl.getOwner() instanceof PlayerEntity p) {
                  double d = this.pearlStartPosMap.get(i).distanceTo(e.getEntityPos());
                  if ((!Friends.get().isFriend(p) || !this.pearlIgnoreFriends.get()) && (!p.equals(this.mc.player) || !this.pearlIgnoreOwn.get())) {
                     this.info(
                        "(highlight)%s's(default) pearl landed at %d, %d, %d (highlight)(%.1fm away, travelled %.1fm)(default).",
                        new Object[]{
                           pearl.getOwner().getName().getString(),
                           pearl.getBlockPos().getX(),
                           pearl.getBlockPos().getY(),
                           pearl.getBlockPos().getZ(),
                           pearl.distanceTo(this.mc.player),
                           d
                        }
                     );
                  }
               }

               this.pearlStartPosMap.remove(i);
            }
         } else if (!this.pearlStartPosMap.isEmpty()) {
            this.pearlStartPosMap.remove(event.entity.getId());
         }

         if (this.crystalPop.get() && event.entity instanceof EndCrystalEntity crystal) {
            Vec3d pos = crystal.getEntityPos();
            this.notifyPvpActivity(true, this.findNearestPlayer(pos), "an", "End Crystal", (int)pos.x, (int)pos.y, (int)pos.z);
         }
      }
   }

   @Override
   public void onActivate() {
      this.totemPopMap.clear();
      this.chatIdMap.clear();
      this.pearlStartPosMap.clear();
      this.visualRangeTracked.clear();
      this.visualRangeNames.clear();
      this.trackedAnchors.clear();
      this.visualRangeSkipFirst = true;
   }

   @Override
   public void onDeactivate() {
      this.timer = 0;
      this.messageQueue.clear();
   }

   @EventHandler
   private void onGameJoin(GameJoinedEvent event) {
      this.timer = 0;
      this.totemPopMap.clear();
      this.chatIdMap.clear();
      this.messageQueue.clear();
      this.pearlStartPosMap.clear();
      this.trackedAnchors.clear();
      this.visualRangeTracked.clear();
      this.visualRangeNames.clear();
      this.visualRangeSkipFirst = false;
   }

   @EventHandler
   private void onGameLeave(GameLeftEvent event) {
      this.loginPacket = true;
      this.trackedAnchors.clear();
   }

   @EventHandler
   private void onBlockUpdate(BlockUpdateEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         if (!this.anchorPlace.get() && !this.anchorPop.get()) {
            if (!this.trackedAnchors.isEmpty()) {
               this.trackedAnchors.clear();
            }
         } else {
            BlockPos pos = event.pos;
            boolean isAnchor = event.newState.getBlock() instanceof RespawnAnchorBlock;
            if (isAnchor && !this.trackedAnchors.contains(pos)) {
               this.trackedAnchors.add(pos.toImmutable());
               if (this.anchorPlace.get()) {
                  this.notifyPvpActivity(false, this.findNearestPlayer(Vec3d.ofCenter(pos)), "a", "Respawn Anchor", pos.getX(), pos.getY(), pos.getZ());
               }
            } else if (!isAnchor && this.trackedAnchors.remove(pos) && this.anchorPop.get()) {
               this.notifyPvpActivity(true, this.findNearestPlayer(Vec3d.ofCenter(pos)), "a", "Respawn Anchor", pos.getX(), pos.getY(), pos.getZ());
            }
         }
      }
   }

   // NOTE (source reconstruction): originally a Java 21 pattern-matching switch with guards;
   // see the same note on InformationManager.onReceivePacket. Rewritten as an equivalent
   // instanceof-chain so this compiles; behavior is unchanged.
   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      Packet packet = event.packet;

      if (packet instanceof PlayerListS2CPacket joinPacket
         && (this.joinsLeavesMode.get().equals(Notifier.JoinLeaveModes.Both) || this.joinsLeavesMode.get().equals(Notifier.JoinLeaveModes.Joins))) {
         if (this.loginPacket) {
            this.loginPacket = false;
            return;
         }

         if (joinPacket.getActions().contains(Action.ADD_PLAYER)) {
            this.createJoinNotifications(joinPacket);
         }
      } else if (packet instanceof PlayerRemoveS2CPacket leavePacket
         && (this.joinsLeavesMode.get().equals(Notifier.JoinLeaveModes.Both) || this.joinsLeavesMode.get().equals(Notifier.JoinLeaveModes.Leaves))) {
         this.createLeaveNotification(leavePacket);
      } else if (packet instanceof EntityStatusS2CPacket totemPacket
         && this.totemPops.get()
         && totemPacket.getStatus() == 35
         && totemPacket.getEntity(this.mc.world) instanceof PlayerEntity entity) {
         if (entity.equals(this.mc.player) && this.totemsIgnoreOwn.get()
            || Friends.get().isFriend(entity) && this.totemsIgnoreOthers.get()
            || !Friends.get().isFriend(entity) && this.totemsIgnoreFriends.get()) {
            return;
         }

         synchronized (this.totemPopMap) {
            int pops = this.totemPopMap.getOrDefault(entity.getUuid(), 0);
            this.totemPopMap.put(entity.getUuid(), ++pops);
            double distance = PlayerUtils.distanceTo(entity);
            if (this.totemsDistanceCheck.get() && distance > this.totemsDistance.get().intValue()) {
               return;
            }

            String name = entity.getName().getString();
            int chatId = this.getChatId(entity);
            int popsF = pops;
            this.mc
               .execute(
                  () -> ChatUtils.sendMsg(
                     chatId, Formatting.GRAY, "(highlight)%s (default)popped (highlight)%d (default)%s.", name, popsF, popsF == 1 ? "totem" : "totems"
                  )
               );
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.world != null && this.mc.player != null) {
         this.pollVisualRange();
      }

      if (this.mc.world != null && !this.trackedAnchors.isEmpty()) {
         this.trackedAnchors.removeIf(pos -> !(this.mc.world.getBlockState(pos).getBlock() instanceof RespawnAnchorBlock));
      }

      if (this.joinsLeavesMode.get() != Notifier.JoinLeaveModes.None) {
         this.timer++;

         while (this.timer >= this.notificationDelay.get() && !this.messageQueue.isEmpty()) {
            this.timer = 0;
            Text msg = this.messageQueue.removeFirst();
            if (this.replaceNotifications.get()) {
               ((IChatHud)this.mc.inGameHud.getChatHud()).revive$add(msg, JOIN_LEAVE_CHAT_ID);
            } else if (this.simpleNotifications.get()) {
               this.mc.player.sendMessage(msg, false);
            } else {
               ChatUtils.sendMsg(msg);
            }
         }
      }

      if (this.totemPops.get()) {
         synchronized (this.totemPopMap) {
            for (PlayerEntity player : this.mc.world.getPlayers()) {
               if (this.totemPopMap.containsKey(player.getUuid()) && (player.deathTime > 0 || player.getHealth() <= 0.0F)) {
                  int pops = this.totemPopMap.removeInt(player.getUuid());
                  ChatUtils.sendMsg(
                     this.getChatId(player),
                     Formatting.GRAY,
                     "(highlight)%s (default)died after popping (highlight)%d (default)%s.",
                     player.getName().getString(),
                     pops,
                     pops == 1 ? "totem" : "totems"
                  );
                  this.chatIdMap.removeInt(player.getUuid());
               }
            }
         }
      }
   }

   private int getChatId(Entity entity) {
      return this.chatIdMap.computeIfAbsent(entity.getUuid(), value -> this.random.nextInt());
   }

   private void pollVisualRange() {
      if (this.visualRange.get() && this.entities.get().contains(EntityType.PLAYER)) {
         boolean suppress = this.visualRangeSkipFirst;
         this.visualRangeSkipFirst = false;
         Set<UUID> visibleNow = new HashSet<>();

         for (PlayerEntity p : this.mc.world.getPlayers()) {
            if (p != this.mc.player
               && !p.getUuid().equals(this.mc.player.getUuid())
               && (!this.visualRangeIgnoreFakes.get() || !(p instanceof FakePlayerEntity))
               && (!this.visualRangeIgnoreFriends.get() || !Friends.get().isFriend(p))
               && (!this.visualRangeIgnoreNakeds.get() || p.getArmor() > 0)) {
               UUID uuid = p.getUuid();
               visibleNow.add(uuid);
               this.visualRangeNames.put(uuid, p.getName().getString());
               if (this.visualRangeTracked.add(uuid) && !suppress && this.event.get() != Notifier.Event.Despawn) {
                  this.notifyVisualRange(p.getName().getString(), p, true);
               }
            }
         }

         Iterator<UUID> it = this.visualRangeTracked.iterator();

         while (it.hasNext()) {
            UUID uuid = it.next();
            if (!visibleNow.contains(uuid)) {
               it.remove();
               String name = this.visualRangeNames.remove(uuid);
               boolean online = this.mc.getNetworkHandler() != null && this.mc.getNetworkHandler().getPlayerListEntry(uuid) != null;
               if (online && !suppress && this.event.get() != Notifier.Event.Spawn && name != null) {
                  this.notifyVisualRange(name, null, false);
               }
            }
         }
      } else {
         this.visualRangeTracked.clear();
         this.visualRangeNames.clear();
      }
   }

   private void notifyVisualRange(String name, PlayerEntity player, boolean entering) {
      StringBuilder extra = new StringBuilder();
      if (entering && player != null && this.visualDirection.get() != Notifier.DirectionMode.Off) {
         double dx = player.getX() - this.mc.player.getX();
         double dz = player.getZ() - this.mc.player.getZ();
         double angle = Math.toDegrees(Math.atan2(-dx, dz));
         double reference = this.visualDirection.get() == Notifier.DirectionMode.Relative ? this.mc.player.getYaw() : 0.0;
         double rel = (angle - reference + 360.0) % 360.0;
         String dir;
         if (rel >= 337.5 || rel < 22.5) {
            dir = "North";
         } else if (rel < 67.5) {
            dir = "North East";
         } else if (rel < 112.5) {
            dir = "East";
         } else if (rel < 157.5) {
            dir = "South East";
         } else if (rel < 202.5) {
            dir = "South";
         } else if (rel < 247.5) {
            dir = "South West";
         } else if (rel < 292.5) {
            dir = "West";
         } else {
            dir = "North West";
         }

         extra.append(' ').append(this.visualDirection.get() == Notifier.DirectionMode.Relative ? "~" : "").append(dir);
      }

      if (entering && player != null && this.visualShowDistance.get()) {
         extra.append(String.format(" (%.0fm)", PlayerUtils.distanceTo(player)));
      }

      if (entering && player != null && this.visualEquipment.get()) {
         String gear = this.getEquipmentString(player);
         if (!gear.isEmpty()) {
            extra.append(" Gear: ").append(gear);
         }
      }

      ChatUtils.sendMsg(
         (name + entering).hashCode(),
         Formatting.GRAY,
         "(highlight)%s(default) has %s your visual range!(highlight)%s(default)",
         name,
         entering ? "entered" : "left",
         extra.toString()
      );
      if (this.visualPopup.get()) {
         NotificationsHud.notify(name, (entering ? "entered range" : "left range") + extra, new ItemStack(Items.PLAYER_HEAD), entering ? 16767291 : 5623039);
      }

      if (this.visualMakeSound.get() && entering) {
         AlertSounds.playAlert(this.mc, this.visualSoundType.get(), 1.0, false);
      }
   }

   private PlayerEntity findNearestPlayer(Vec3d pos) {
      if (this.mc.world == null) {
         return null;
      } else {
         double maxRange = this.pvpRange.get().intValue();
         PlayerEntity nearest = null;
         double nearestDist = Double.MAX_VALUE;

         for (PlayerEntity p : this.mc.world.getPlayers()) {
            if (!this.pvpIgnoreSelf.get() || p != this.mc.player) {
               double dist = p.getEntityPos().distanceTo(pos);
               if (dist <= maxRange && dist < nearestDist) {
                  nearest = p;
                  nearestDist = dist;
               }
            }
         }

         return nearest;
      }
   }

   private String getEquipmentString(PlayerEntity player) {
      List<String> parts = new ArrayList<>();
      ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
      ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
      ItemStack legs = player.getEquippedStack(EquipmentSlot.LEGS);
      ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
      ItemStack mainHand = player.getMainHandStack();
      ItemStack offHand = player.getOffHandStack();
      if (!head.isEmpty()) {
         parts.add(head.getName().getString());
      }

      if (!chest.isEmpty()) {
         parts.add(chest.getName().getString());
      }

      if (!legs.isEmpty()) {
         parts.add(legs.getName().getString());
      }

      if (!feet.isEmpty()) {
         parts.add(feet.getName().getString());
      }

      if (!mainHand.isEmpty()) {
         parts.add(mainHand.getName().getString() + " (hand)");
      }

      if (!offHand.isEmpty()) {
         parts.add(offHand.getName().getString() + " (off)");
      }

      return String.join(", ", parts);
   }

   private void notifyPvpActivity(boolean isPop, PlayerEntity actor, String article, String subject, int x, int y, int z) {
      String name = actor != null ? actor.getName().getString() : "Unknown";
      Formatting color = isPop ? Formatting.RED : Formatting.LIGHT_PURPLE;
      MutableText text = Text.literal(name).formatted(color);
      text.append(Text.literal((isPop ? " popped " : " placed ") + article + " ").formatted(Formatting.GRAY));
      text.append(Text.literal(subject).formatted(color));
      text.append(Text.literal(" at ").formatted(Formatting.GRAY));
      text.append(Text.literal(x + " " + y + " " + z).formatted(Formatting.WHITE));
      this.info(text);
   }

   private MutableText buildNotification(String name, boolean isJoin) {
      SettingColor bracketColor = isJoin ? this.joinBracketColor.get() : this.leaveBracketColor.get();
      SettingColor symbolColor = isJoin ? this.joinSymbolColor.get() : this.leaveSymbolColor.get();
      SettingColor nameColor = isJoin ? this.joinNameColor.get() : this.leaveNameColor.get();
      String symbol = isJoin ? "+" : "-";
      MutableText msg = Text.literal("[").setStyle(bracketColor.toStyle());
      msg.append(Text.literal(symbol).setStyle(symbolColor.toStyle()));
      msg.append(Text.literal("] ").setStyle(bracketColor.toStyle()));
      msg.append(Text.literal(name).setStyle(nameColor.toStyle()));
      return msg;
   }

   private void createJoinNotifications(PlayerListS2CPacket packet) {
      for (Entry entry : packet.getPlayerAdditionEntries()) {
         if (entry.profile() != null) {
            String name = entry.profile().name();
            if (!this.simpleNotifications.get() && !this.replaceNotifications.get()) {
               this.messageQueue.addLast(Text.literal(name).formatted(Formatting.WHITE).append(Text.literal(" joined.").formatted(Formatting.GRAY)));
            } else {
               this.messageQueue.addLast(this.buildNotification(name, true));
            }
         }
      }
   }

   private void createLeaveNotification(PlayerRemoveS2CPacket packet) {
      if (this.mc.getNetworkHandler() != null) {
         for (UUID id : packet.profileIds()) {
            PlayerListEntry toRemove = this.mc.getNetworkHandler().getPlayerListEntry(id);
            if (toRemove != null) {
               String name = toRemove.getProfile().name();
               if (!this.simpleNotifications.get() && !this.replaceNotifications.get()) {
                  this.messageQueue.addLast(Text.literal(name).formatted(Formatting.WHITE).append(Text.literal(" left.").formatted(Formatting.GRAY)));
               } else {
                  this.messageQueue.addLast(this.buildNotification(name, false));
               }
            }
         }
      }
   }

   public static enum DirectionMode {
      Off,
      Absolute,
      Relative;
   }

   public static enum Event {
      Spawn,
      Despawn,
      Both;
   }

   public static enum JoinLeaveModes {
      None,
      Joins,
      Leaves,
      Both;
   }
}
