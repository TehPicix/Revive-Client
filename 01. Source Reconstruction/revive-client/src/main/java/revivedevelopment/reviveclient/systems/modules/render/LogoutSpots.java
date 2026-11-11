package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.EntityRemovedEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.PlayerJoinLeaveEvent;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.render.SubmitEntitiesEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.managers.TeamSyncManager;
import revivedevelopment.reviveclient.systems.managers.team.RemoteLogout;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.NametagUtils;
import revivedevelopment.reviveclient.utils.render.SkinEntityRenderer;
import revivedevelopment.reviveclient.utils.render.WireframeEntityRenderer;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class LogoutSpots extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> range = this.sgGeneral
      .add(
         new IntSetting.Builder().name("range").description("Maximum render distance (XZ blocks).").defaultValue(100).range(1, 256).sliderRange(1, 256).build()
      );
   private final Setting<Boolean> ignoreNakeds = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-nakeds").description("Skip players wearing no armor.").defaultValue(true).build());
   private final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-friends").description("Skip friends.").defaultValue(false).build());
   private final Setting<Boolean> notify = this.sgGeneral
      .add(new BoolSetting.Builder().name("notify").description("Chat messages for logout and rejoin events.").defaultValue(true).build());
   private final Setting<LogoutSpots.Mode> mode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("The rendering style of logout markers."))
               .defaultValue(LogoutSpots.Mode.Wireframe))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the shapes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(() -> this.mode.get() != LogoutSpots.Mode.Skin))
            .build()
      );
   private final Setting<Double> fillOpacity = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("fill-opacity")
            .description("The opacity of the shape fill.")
            .visible(() -> this.mode.get() != LogoutSpots.Mode.Skin && this.shapeMode.get() != ShapeMode.Lines)
            .defaultValue(0.3)
            .range(0.0, 1.0)
            .sliderMax(1.0)
            .build()
      );
   private final Setting<SettingColor> color = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("color")
            .description("The color of logout spot rendering.")
            .defaultValue(new SettingColor(255, 85, 85))
            .visible(() -> this.mode.get() != LogoutSpots.Mode.Skin)
            .build()
      );
   private final Setting<Boolean> nametag = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("nametag")
            .description(
               "Renders a nametag (name, health, totem pops, offline time) above the ghost in every mode. It is drawn as a normal HUD nametag, independent of the marker's colors / opacity."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> totemPops = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("totem-pops")
            .description("Shows how many totems the player had popped. Kept while they're logged out so it's still there when they log back in.")
            .defaultValue(true)
            .visible(this.nametag::get)
            .build()
      );
   private final Setting<SettingColor> totemPopsColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("totem-pops-color")
            .description("Color of the totem pop count.")
            .defaultValue(new SettingColor(225, 120, 20))
            .visible(() -> this.nametag.get() && this.totemPops.get())
            .build()
      );
   private final Setting<Boolean> teamSpots = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("team-spots")
            .description("Team sync: also shows logout spots reported by teammates (same dimension). Drawn as boxes, the nametag says who reported it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> scale = this.sgRender
      .add(new DoubleSetting.Builder().name("scale").description("The scale of the nametag.").defaultValue(1.1).min(0.1).visible(this.nametag::get).build());
   private final Map<UUID, LogoutSpots.LogoutEntry> logoutSpots = new ConcurrentHashMap<>();
   private final Map<UUID, LogoutSpots.RemovedPlayer> recentlyRemoved = new ConcurrentHashMap<>();
   private static final long REMOVED_GRACE_MS = 3000L;
   private static final int MAX_SPOTS = 100;
   private final Color sideColor = new Color();
   private final Color lineColor = new Color();
   private final Vector3d nametagPos = new Vector3d();
   private final Color nameColor = new Color();
   private final Color popsColor = new Color();
   private static final Color RED = new Color(255, 25, 25);
   private static final Color AMBER = new Color(255, 105, 25);
   private static final Color GREEN = new Color(25, 252, 25);
   private static final Color GRAY = new Color(150, 150, 150);
   private static final Color BACKGROUND = new Color(0, 0, 0, 75);
   private final List<RemoteLogout> remoteSpots = new ArrayList<>();

   public LogoutSpots() {
      super(Categories.Render, "logout-spots", "Marks where players logged out.");
   }

   @Override
   public void onActivate() {
      this.logoutSpots.clear();
      this.recentlyRemoved.clear();
   }

   @Override
   public void onDeactivate() {
      this.logoutSpots.clear();
      this.recentlyRemoved.clear();
      this.remoteSpots.clear();
   }

   @EventHandler
   private void onEntityRemoved(EntityRemovedEvent event) {
      if (event.entity instanceof PlayerEntity player && player != this.mc.player) {
         long now = System.currentTimeMillis();
         this.recentlyRemoved.values().removeIf(removed -> now - removed.time() > 3000L);
         this.recentlyRemoved.put(player.getUuid(), new LogoutSpots.RemovedPlayer(player, now));
      }
   }

   @EventHandler
   private void onPlayerLeave(PlayerJoinLeaveEvent.Leave event) {
      if (this.mc.world != null && this.mc.player != null) {
         PlayerListEntry listEntry = event.entry;
         if (listEntry != null) {
            UUID uuid = listEntry.getProfile().id();
            if (!uuid.equals(this.mc.player.getUuid())) {
               long logoutTime = System.currentTimeMillis();
               PlayerEntity player = this.findPlayer(uuid);
               if (player == null) {
                  LogoutSpots.RemovedPlayer removed = this.recentlyRemoved.remove(uuid);
                  if (removed != null && logoutTime - removed.time() <= 3000L) {
                     player = removed.player();
                  }
               }

               if (player != null) {
                  PlayerEntity source = player;
                  this.mc.execute(() -> this.addSpot(uuid, source, listEntry, logoutTime));
               }
            }
         }
      }
   }

   private void addSpot(UUID uuid, PlayerEntity player, PlayerListEntry listEntry, long logoutTime) {
      if (this.isActive() && this.mc.world != null && this.mc.player != null) {
         if (!this.ignoreFriends.get() || !Friends.get().isFriend(player)) {
            if (!this.ignoreNakeds.get() || player.getArmor() > 0) {
               LogoutSpots.LogoutEntry entry = new LogoutSpots.LogoutEntry(player, listEntry, logoutTime);
               this.logoutSpots.put(uuid, entry);

               while (this.logoutSpots.size() > 100) {
                  UUID oldest = null;
                  long oldestTime = Long.MAX_VALUE;

                  for (Entry<UUID, LogoutSpots.LogoutEntry> e : this.logoutSpots.entrySet()) {
                     if (e.getValue().logoutTime < oldestTime) {
                        oldestTime = e.getValue().logoutTime;
                        oldest = e.getKey();
                     }
                  }

                  if (oldest == null) {
                     break;
                  }

                  this.logoutSpots.remove(oldest);
               }

               if (this.notify.get()) {
                  this.info("(highlight)%s(default) logged out.", new Object[]{entry.name});
               }
            }
         }
      }
   }

   @EventHandler
   private void onPlayerJoin(PlayerJoinLeaveEvent.Join event) {
      PlayerListEntry listEntry = event.entry;
      if (listEntry != null) {
         UUID uuid = listEntry.getProfile().id();
         this.recentlyRemoved.remove(uuid);
         this.mc.execute(() -> {
            LogoutSpots.LogoutEntry removed = this.logoutSpots.remove(uuid);
            if (removed != null) {
               if (this.notify.get()) {
                  this.info("(highlight)%s(default) logged back in after (highlight)%s(default).", new Object[]{removed.name, removed.getOfflineDuration()});
               }
            }
         });
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.world != null) {
         this.refreshRemoteSpots();

         for (LogoutSpots.LogoutEntry entry : this.logoutSpots.values()) {
            entry.ghost.bindWorld(this.mc.world);
         }

         if (!this.recentlyRemoved.isEmpty()) {
            long now = System.currentTimeMillis();
            this.recentlyRemoved.values().removeIf(removed -> now - removed.time() > 3000L);
         }
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.logoutSpots.clear();
      this.recentlyRemoved.clear();
      this.remoteSpots.clear();
   }

   @EventHandler
   private void onSubmitEntities(SubmitEntitiesEvent event) {
      if (this.mode.get() == LogoutSpots.Mode.Skin && !this.logoutSpots.isEmpty() && this.mc.world != null) {
         double maxRangeSq = (double)this.range.get().intValue() * this.range.get().intValue();
         RegistryKey<World> currentDim = this.mc.world.getRegistryKey();

         for (LogoutSpots.LogoutEntry entry : this.logoutSpots.values()) {
            if (entry.dimension.equals(currentDim) && !(this.squaredDistanceToCamera(entry) > maxRangeSq)) {
               entry.ghost.bindWorld(this.mc.world);
               SkinEntityRenderer.render(event, entry.ghost, 1.0);
            }
         }
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.world != null) {
         this.renderRemoteBoxes(event);
         if (!this.logoutSpots.isEmpty() && this.mode.get() != LogoutSpots.Mode.Skin) {
            double maxRangeSq = (double)this.range.get().intValue() * this.range.get().intValue();
            RegistryKey<World> currentDim = this.mc.world.getRegistryKey();

            for (LogoutSpots.LogoutEntry entry : this.logoutSpots.values()) {
               if (entry.dimension.equals(currentDim) && !(this.squaredDistanceToCamera(entry) > maxRangeSq)) {
                  this.lineColor.set(this.color.get());
                  this.sideColor.set(this.color.get()).a((int)(this.sideColor.a * this.fillOpacity.get()));
                  if (this.mode.get() == LogoutSpots.Mode.Wireframe) {
                     entry.ghost.bindWorld(this.mc.world);
                     WireframeEntityRenderer.render(event, entry.ghost, 1.0, this.sideColor, this.lineColor, this.shapeMode.get());
                  } else {
                     Box box = entry.ghost.getBoundingBox();
                     event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, this.sideColor, this.lineColor, this.shapeMode.get(), 0);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.nametag.get() && this.mc.world != null) {
         if (!this.logoutSpots.isEmpty() || !this.remoteSpots.isEmpty()) {
            double maxRangeSq = (double)this.range.get().intValue() * this.range.get().intValue();
            RegistryKey<World> currentDim = this.mc.world.getRegistryKey();
            boolean shadow = Config.get().customFont.get();

            for (LogoutSpots.LogoutEntry entry : this.logoutSpots.values()) {
               if (entry.dimension.equals(currentDim) && !(this.squaredDistanceToCamera(entry) > maxRangeSq)) {
                  this.renderNametag(event.drawContext, entry, shadow);
               }
            }

            for (RemoteLogout spot : this.remoteSpots) {
               if (!(PlayerUtils.squaredDistanceToCamera(spot.x, spot.y, spot.z) > maxRangeSq)) {
                  this.renderRemoteNametag(event.drawContext, spot, shadow);
               }
            }
         }
      }
   }

   private void refreshRemoteSpots() {
      this.remoteSpots.clear();
      if (this.teamSpots.get() && this.mc.world != null && this.mc.player != null) {
         TeamSyncManager team = ReviveClient.TEAM;
         if (team != null && team.isConnected()) {
            String dim = this.mc.world.getRegistryKey().getValue().toString();

            for (RemoteLogout spot : team.getLogouts()) {
               if (spot.uuid != null
                  && dim.equals(spot.dim)
                  && !this.logoutSpots.containsKey(spot.uuid)
                  && !spot.uuid.equals(this.mc.player.getUuid())
                  && !team.isTeammate(spot.uuid)
                  && (this.mc.getNetworkHandler() == null || this.mc.getNetworkHandler().getPlayerListEntry(spot.uuid) == null)
                  && (!this.ignoreFriends.get() || Friends.get().get(spot.name) == null)) {
                  this.remoteSpots.add(spot);
               }
            }
         }
      }
   }

   private void renderRemoteBoxes(Render3DEvent event) {
      if (!this.remoteSpots.isEmpty()) {
         double maxRangeSq = (double)this.range.get().intValue() * this.range.get().intValue();

         for (RemoteLogout spot : this.remoteSpots) {
            if (!(PlayerUtils.squaredDistanceToCamera(spot.x, spot.y, spot.z) > maxRangeSq)) {
               this.lineColor.set(this.color.get());
               this.sideColor.set(this.color.get()).a((int)(this.sideColor.a * this.fillOpacity.get()));
               event.renderer
                  .box(
                     spot.x - 0.3,
                     spot.y,
                     spot.z - 0.3,
                     spot.x + 0.3,
                     spot.y + 1.8,
                     spot.z + 0.3,
                     this.sideColor,
                     this.lineColor,
                     this.mode.get() == LogoutSpots.Mode.Skin ? ShapeMode.Both : this.shapeMode.get(),
                     0
                  );
            }
         }
      }
   }

   private void renderRemoteNametag(DrawContext ctx, RemoteLogout spot, boolean shadow) {
      this.nametagPos.set(spot.x, spot.y + 1.62 + 0.5, spot.z);
      if (NametagUtils.to2D(this.nametagPos, this.scale.get())) {
         String name = spot.name;
         String health = " " + Math.round(spot.hp);
         String duration = " " + formatDuration(spot.at);
         TeamSyncManager team = ReviveClient.TEAM;
         int teamPops = team != null && team.isConnected() ? team.getTeamPops(spot.uuid) : -1;
         int popCount = teamPops >= 0 ? teamPops : spot.pops;
         String pops = this.totemPops.get() && popCount > 0 ? " -" + popCount : "";
         String by = spot.by != null && !spot.by.isEmpty() ? " (by " + spot.by + ")" : "";
         double width = this.textWidth(name)
            + this.textWidth(health)
            + (pops.isEmpty() ? 0.0 : this.textWidth(pops))
            + this.textWidth(duration)
            + (by.isEmpty() ? 0.0 : this.textWidth(by));
         double height = this.textHeight(shadow);
         double widthHalf = width / 2.0;
         NametagUtils.begin(this.nametagPos, ctx);

         try {
            fill(ctx, -widthHalf - 1.0, -height - 1.0, width + 2.0, height + 2.0, BACKGROUND);
            double x = -widthHalf;
            double y = -height;
            boolean friend = Friends.get().get(spot.name) != null;
            x = this.text(ctx, name, x, y, opaque(this.nameColor, friend ? Config.get().friendColor.get() : Color.WHITE), shadow);
            x = this.text(ctx, health, x, y, healthColor(spot.hp, 20.0F), shadow);
            if (!pops.isEmpty()) {
               x = this.text(ctx, pops, x, y, opaque(this.popsColor, this.totemPopsColor.get()), shadow);
            }

            x = this.text(ctx, duration, x, y, GRAY, shadow);
            if (!by.isEmpty()) {
               this.text(ctx, by, x, y, GRAY, shadow);
            }
         } finally {
            NametagUtils.end(ctx);
         }
      }
   }

   private void renderNametag(DrawContext ctx, LogoutSpots.LogoutEntry entry, boolean shadow) {
      FakePlayerEntity ghost = entry.ghost;
      this.nametagPos.set(entry.x, entry.y + ghost.getEyeHeight(ghost.getPose()) + 0.5, entry.z);
      if (NametagUtils.to2D(this.nametagPos, this.scale.get())) {
         String name = entry.name;
         String health = " " + Math.round(entry.health);
         String duration = " " + entry.getOfflineDuration();
         int popCount = ReviveClient.INFO.getPops(entry.uuid);
         String pops = this.totemPops.get() && popCount > 0 ? " -" + popCount : "";
         double width = this.textWidth(name) + this.textWidth(health) + (pops.isEmpty() ? 0.0 : this.textWidth(pops)) + this.textWidth(duration);
         double height = this.textHeight(shadow);
         double widthHalf = width / 2.0;
         NametagUtils.begin(this.nametagPos, ctx);

         try {
            fill(ctx, -widthHalf - 1.0, -height - 1.0, width + 2.0, height + 2.0, BACKGROUND);
            double x = -widthHalf;
            double y = -height;
            x = this.text(ctx, name, x, y, this.relationColor(entry), shadow);
            x = this.text(ctx, health, x, y, healthColor(entry.health, entry.maxHealth), shadow);
            if (!pops.isEmpty()) {
               x = this.text(ctx, pops, x, y, opaque(this.popsColor, this.totemPopsColor.get()), shadow);
            }

            this.text(ctx, duration, x, y, GRAY, shadow);
         } finally {
            NametagUtils.end(ctx);
         }
      }
   }

   private double textWidth(String text) {
      return this.mc.textRenderer.getWidth(text) * 2.0;
   }

   private double textHeight(boolean shadow) {
      return (9 + (shadow ? 1 : 0)) * 2.0;
   }

   private double text(DrawContext ctx, String text, double x, double y, Color color, boolean shadow) {
      Matrix3x2fStack matrices = ctx.getMatrices();
      matrices.pushMatrix();
      matrices.scale(2.0F, 2.0F);
      ctx.drawText(this.mc.textRenderer, text, (int)Math.round(x / 2.0), (int)Math.round(y / 2.0), color.getPacked(), shadow);
      matrices.popMatrix();
      return x + this.textWidth(text);
   }

   private static void fill(DrawContext ctx, double x, double y, double width, double height, Color color) {
      ctx.fill((int)Math.round(x), (int)Math.round(y), (int)Math.round(x + width), (int)Math.round(y + height), color.getPacked());
   }

   private static Color opaque(Color target, Color source) {
      return target.set(source).a(255);
   }

   private PlayerEntity findPlayer(UUID uuid) {
      try {
         for (PlayerEntity player : this.mc.world.getPlayers()) {
            if (player.getUuid().equals(uuid)) {
               return player;
            }
         }
      } catch (RuntimeException var4) {
      }

      return null;
   }

   private double squaredDistanceToCamera(LogoutSpots.LogoutEntry entry) {
      return PlayerUtils.squaredDistanceToCamera(entry.x, entry.y, entry.z);
   }

   private static Color healthColor(float health, float maxHealth) {
      double pct = maxHealth <= 0.0F ? 1.0 : health / maxHealth;
      if (pct <= 0.333) {
         return RED;
      } else {
         return pct <= 0.666 ? AMBER : GREEN;
      }
   }

   private Color relationColor(LogoutSpots.LogoutEntry entry) {
      Color source = entry.friend ? Config.get().friendColor.get() : PlayerUtils.getPlayerColor(entry.ghost, Color.WHITE);
      return opaque(this.nameColor, source);
   }

   @Override
   public String getInfoString() {
      return Integer.toString(this.logoutSpots.size() + this.remoteSpots.size());
   }

   public Map<UUID, LogoutSpots.LogoutEntry> getLogoutSpots() {
      return this.logoutSpots;
   }

   static String formatDuration(long since) {
      long totalSec = Math.max(0L, (System.currentTimeMillis() - since) / 1000L);
      long days = totalSec / 86400L;
      long hours = totalSec % 86400L / 3600L;
      long minutes = totalSec % 3600L / 60L;
      long seconds = totalSec % 60L;
      return days > 0L
         ? String.format(Locale.US, "%dd %02d:%02d:%02d", days, hours, minutes, seconds)
         : String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
   }

   public static class LogoutEntry {
      final FakePlayerEntity ghost;
      final String name;
      final UUID uuid;
      final RegistryKey<World> dimension;
      final double x;
      final double y;
      final double z;
      final float health;
      final float maxHealth;
      final boolean friend;
      final long logoutTime;

      LogoutEntry(PlayerEntity player, PlayerListEntry listEntry, long logoutTime) {
         this.name = player.getGameProfile().name();
         this.uuid = player.getUuid();
         this.dimension = ReviveClient.mc.world.getRegistryKey();
         this.x = player.getX();
         this.y = player.getY();
         this.z = player.getZ();
         this.health = player.getHealth() + player.getAbsorptionAmount();
         this.maxHealth = player.getMaxHealth() + player.getAbsorptionAmount();
         this.friend = Friends.get().isFriend(player);
         this.logoutTime = logoutTime;
         this.ghost = new FakePlayerEntity(player, player.getGameProfile(), this.health, true);
         this.ghost.setSkinSource(listEntry);
         this.ghost.setPosition(this.x, this.y, this.z);
         this.ghost.lastRenderX = this.x;
         this.ghost.lastRenderY = this.y;
         this.ghost.lastRenderZ = this.z;
      }

      public PlayerEntity ghost() {
         return this.ghost;
      }

      public Vec3d pos() {
         return new Vec3d(this.x, this.y, this.z);
      }

      public RegistryKey<World> dimension() {
         return this.dimension;
      }

      String getOfflineDuration() {
         return LogoutSpots.formatDuration(this.logoutTime);
      }
   }

   public static enum Mode {
      Box,
      Wireframe,
      Skin;
   }

   private record RemovedPlayer(PlayerEntity player, long time) {
   }
}
