package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
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
import revivedevelopment.reviveclient.systems.managers.team.RemotePlayer;
import revivedevelopment.reviveclient.systems.managers.team.TeamMate;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.NametagUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ESPPlus extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final SettingGroup sgNametag = this.settings.createGroup("Nametag");
   private final Setting<Boolean> enemies = this.sgGeneral
      .add(new BoolSetting.Builder().name("enemies").description("Boxes for non-teammates seen by a teammate.").defaultValue(true).build());
   private final Setting<Boolean> teammates = this.sgGeneral
      .add(new BoolSetting.Builder().name("teammates").description("Boxes for teammates outside your render distance.").defaultValue(true).build());
   private final Setting<Integer> range = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("range")
            .description("Maximum distance (blocks) to draw.")
            .defaultValue(3000)
            .range(16, 100000)
            .sliderRange(64, 10000)
            .build()
      );
   private final Setting<Integer> staleTime = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("stale-time")
            .description("Hide a player after this many seconds without a fresh position.")
            .defaultValue(30)
            .range(1, 600)
            .sliderRange(5, 300)
            .build()
      );
   private final Setting<Boolean> fade = this.sgGeneral
      .add(new BoolSetting.Builder().name("fade").description("Fades players out as their last position gets older.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<Double> fillOpacity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("fill-opacity")
            .description("The opacity of the shape fill.")
            .visible(() -> this.shapeMode.get() != ShapeMode.Lines)
            .defaultValue(0.3)
            .range(0.0, 1.0)
            .sliderMax(1.0)
            .build()
      );
   private final Setting<SettingColor> enemyColor = this.sgColors
      .add(new ColorSetting.Builder().name("enemy-color").description("Color of non-teammates.").defaultValue(new SettingColor(255, 60, 60)).build());
   private final Setting<SettingColor> teammateColor = this.sgColors
      .add(new ColorSetting.Builder().name("teammate-color").description("Color of teammates.").defaultValue(new SettingColor(60, 160, 255)).build());
   private final Setting<Boolean> friendColors = this.sgColors
      .add(new BoolSetting.Builder().name("friend-colors").description("Uses the friend color for friends that aren't teammates.").defaultValue(true).build());
   private final Setting<Boolean> nametag = this.sgNametag
      .add(
         new BoolSetting.Builder()
            .name("nametag")
            .description("Draws name, health, distance, totem pops and who sees them above the box.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> nametagScale = this.sgNametag
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale of the nametag.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderRange(0.5, 2.0)
            .visible(this.nametag::get)
            .build()
      );
   private final Setting<SettingColor> popsColor = this.sgNametag
      .add(
         new ColorSetting.Builder()
            .name("totem-pops-color")
            .description("Color of the totem pop count.")
            .defaultValue(new SettingColor(225, 120, 20))
            .visible(this.nametag::get)
            .build()
      );
   private final ESPPlus.RemoteTracker tracker = new ESPPlus.RemoteTracker();
   private final Color sideColor = new Color();
   private final Color lineColor = new Color();
   private final Color nameColor = new Color();
   private final Color pops = new Color();
   private final Vector3d nametagPos = new Vector3d();
   private static final Color GRAY = new Color(170, 170, 170);
   private static final Color RED = new Color(255, 25, 25);
   private static final Color AMBER = new Color(255, 105, 25);
   private static final Color GREEN = new Color(25, 252, 25);
   private static final Color BACKGROUND = new Color(0, 0, 0, 75);
   private int count;
   static final double PLAYER_WIDTH = 0.6;
   static final double PLAYER_HEIGHT = 1.8;
   static final long LERP_MS = 250L;
   static final double TELEPORT_SQ = 4096.0;

   public ESPPlus() {
      super(Categories.Render, "esp-plus", "Team sync: boxes for players your teammates see outside your render distance.");
   }

   @Override
   public void onDeactivate() {
      this.tracker.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.tracker.clear();
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      this.count = 0;
      if (this.mc.world != null && this.mc.player != null) {
         List<ESPPlus.RemoteTarget> targets = this.tracker.update(this.enemies.get(), this.teammates.get(), this.range.get(), this.staleTime.get());
         if (!targets.isEmpty()) {
            Vec3d cam = this.mc.gameRenderer.getCamera().getCameraPos();

            for (ESPPlus.RemoteTarget t : targets) {
               Color base = this.colorOf(t);
               double alpha = this.fade.get() ? t.fadeAlpha(this.staleTime.get().intValue() * 1000L) : 1.0;
               this.lineColor.set(base.r, base.g, base.b, (int)(base.a * alpha));
               this.sideColor.set(base.r, base.g, base.b, (int)(base.a * alpha * this.fillOpacity.get()));
               double dx = t.x - cam.x;
               double dy = t.y + 0.9 - cam.y;
               double dz = t.z - cam.z;
               double s = ESPPlus.RemoteTracker.clampScale(dx, dy, dz);
               double cx = cam.x + dx * s;
               double cy = cam.y + dy * s;
               double cz = cam.z + dz * s;
               double hw = 0.3 * s;
               double hh = 0.9 * s;
               event.renderer.box(cx - hw, cy - hh, cz - hw, cx + hw, cy + hh, cz + hw, this.sideColor, this.lineColor, this.shapeMode.get(), 0);
               this.count++;
            }
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.nametag.get() && this.mc.world != null && this.mc.player != null) {
         List<ESPPlus.RemoteTarget> targets = this.tracker.update(this.enemies.get(), this.teammates.get(), this.range.get(), this.staleTime.get());
         if (!targets.isEmpty()) {
            boolean shadow = Config.get().customFont.get();

            for (ESPPlus.RemoteTarget t : targets) {
               this.renderNametag(event.drawContext, t, shadow);
            }
         }
      }
   }

   private void renderNametag(DrawContext ctx, ESPPlus.RemoteTarget t, boolean shadow) {
      this.nametagPos.set(t.x, t.y + 1.8 + 0.5, t.z);
      if (NametagUtils.to2D(this.nametagPos, this.nametagScale.get())) {
         double distance = Math.sqrt(distanceSqToPlayer(t.x, t.y, t.z));
         String name = t.name;
         String health = " " + Math.round(t.hp);
         String dist = " " + Math.round(distance) + "m";
         int popCount = t.mate ? 0 : ReviveClient.INFO.getPops(t.uuid);
         String popsText = popCount > 0 ? " -" + popCount : "";
         String tail = t.mate ? "" : (t.visible ? "" : " " + formatAgo(t.sinceUpdate())) + (t.via != null ? " via " + t.via : "");
         double width = textWidth(name)
            + textWidth(health)
            + textWidth(dist)
            + (popsText.isEmpty() ? 0.0 : textWidth(popsText))
            + (tail.isEmpty() ? 0.0 : textWidth(tail));
         double height = textHeight(shadow);
         double widthHalf = width / 2.0;
         NametagUtils.begin(this.nametagPos, ctx);

         try {
            fill(ctx, -widthHalf - 1.0, -height - 1.0, width + 2.0, height + 2.0, BACKGROUND);
            double x = -widthHalf;
            double y = -height;
            x = text(ctx, name, x, y, this.nameColor.set(this.colorOf(t)).a(255), shadow);
            x = text(ctx, health, x, y, healthColor(t.hp), shadow);
            if (!popsText.isEmpty()) {
               x = text(ctx, popsText, x, y, this.pops.set(this.popsColor.get()).a(255), shadow);
            }

            x = text(ctx, dist, x, y, GRAY, shadow);
            if (!tail.isEmpty()) {
               text(ctx, tail, x, y, GRAY, shadow);
            }
         } finally {
            NametagUtils.end(ctx);
         }
      }
   }

   private Color colorOf(ESPPlus.RemoteTarget t) {
      if (t.mate) {
         return this.teammateColor.get();
      } else {
         return this.friendColors.get() && Friends.get().get(t.name) != null ? Config.get().friendColor.get() : this.enemyColor.get();
      }
   }

   @Override
   public String getInfoString() {
      return Integer.toString(this.count);
   }

   static double textWidth(String text) {
      return ReviveClient.mc.textRenderer.getWidth(text) * 2.0;
   }

   static double textHeight(boolean shadow) {
      return (9 + (shadow ? 1 : 0)) * 2.0;
   }

   static double text(DrawContext ctx, String text, double x, double y, Color color, boolean shadow) {
      Matrix3x2fStack matrices = ctx.getMatrices();
      matrices.pushMatrix();
      matrices.scale(2.0F, 2.0F);
      ctx.drawText(ReviveClient.mc.textRenderer, text, (int)Math.round(x / 2.0), (int)Math.round(y / 2.0), color.getPacked(), shadow);
      matrices.popMatrix();
      return x + textWidth(text);
   }

   static void fill(DrawContext ctx, double x, double y, double width, double height, Color color) {
      ctx.fill((int)Math.round(x), (int)Math.round(y), (int)Math.round(x + width), (int)Math.round(y + height), color.getPacked());
   }

   static Color healthColor(float health) {
      double pct = health / 20.0;
      if (pct <= 0.333) {
         return RED;
      } else {
         return pct <= 0.666 ? AMBER : GREEN;
      }
   }

   static double distanceSqToPlayer(double x, double y, double z) {
      double dx = x - ReviveClient.mc.player.getX();
      double dy = y - ReviveClient.mc.player.getY();
      double dz = z - ReviveClient.mc.player.getZ();
      return dx * dx + dy * dy + dz * dz;
   }

   static String formatAgo(long ms) {
      long seconds = Math.max(0L, ms / 1000L);
      return seconds < 60L ? seconds + "s" : seconds / 60L + "m " + seconds % 60L + "s";
   }

   public static final class RemoteTarget {
      public UUID uuid;
      public String name;
      public boolean mate;
      public boolean visible;
      public String via;
      public float hp;
      public double x;
      public double y;
      public double z;
      private double fromX;
      private double fromY;
      private double fromZ;
      private double toX;
      private double toY;
      private double toZ;
      private long moveStart;
      private long receivedAt = Long.MIN_VALUE;
      private long outMs;
      private long frame;

      public long sinceUpdate() {
         long sinceReceived = Math.max(0L, System.currentTimeMillis() - this.receivedAt);
         return !this.visible && !this.mate ? this.outMs + sinceReceived : sinceReceived;
      }

      public double fadeAlpha(long staleMs) {
         if (staleMs <= 0L) {
            return 1.0;
         } else {
            double age = (double)this.sinceUpdate() / staleMs;
            return Math.max(0.2, Math.min(1.0, 1.0 - age * 0.8));
         }
      }

      void accept(double nx, double ny, double nz, long received, long now) {
         if (received != this.receivedAt) {
            boolean first = this.receivedAt == Long.MIN_VALUE;
            double ddx = nx - this.x;
            double ddy = ny - this.y;
            double ddz = nz - this.z;
            if (!first && !(ddx * ddx + ddy * ddy + ddz * ddz > 4096.0)) {
               this.fromX = this.x;
               this.fromY = this.y;
               this.fromZ = this.z;
            } else {
               this.fromX = nx;
               this.fromY = ny;
               this.fromZ = nz;
               this.x = nx;
               this.y = ny;
               this.z = nz;
            }

            this.toX = nx;
            this.toY = ny;
            this.toZ = nz;
            this.moveStart = now;
            this.receivedAt = received;
         }

         double t = Math.min(1.0, Math.max(0.0, (now - this.moveStart) / 250.0));
         this.x = this.fromX + (this.toX - this.fromX) * t;
         this.y = this.fromY + (this.toY - this.fromY) * t;
         this.z = this.fromZ + (this.toZ - this.fromZ) * t;
      }
   }

   public static final class RemoteTracker {
      static final long LERP_MS = 250L;
      static final double CLAMP_DISTANCE = 96.0;
      private final Map<UUID, ESPPlus.RemoteTarget> targets = new HashMap<>();
      private final List<ESPPlus.RemoteTarget> active = new ArrayList<>();
      private final Set<UUID> inWorld = new HashSet<>();
      private long frame;

      public void clear() {
         this.targets.clear();
         this.active.clear();
         this.inWorld.clear();
      }

      static double clampScale(double dx, double dy, double dz) {
         double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
         return dist > 96.0 ? 96.0 / dist : 1.0;
      }

      public List<ESPPlus.RemoteTarget> update(boolean enemies, boolean mates, int range, int staleSeconds) {
         this.active.clear();
         TeamSyncManager team = ReviveClient.TEAM;
         if (team != null && team.isConnected() && ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
            long now = System.currentTimeMillis();
            long staleMs = staleSeconds * 1000L;
            double rangeSq = (double)range * range;
            String dim = ReviveClient.mc.world.getRegistryKey().getValue().toString();
            UUID self = ReviveClient.mc.player.getUuid();
            this.frame++;
            this.inWorld.clear();

            for (PlayerEntity player : ReviveClient.mc.world.getPlayers()) {
               this.inWorld.add(player.getUuid());
            }

            if (mates) {
               for (TeamMate m : team.getMates()) {
                  if (m.uuid != null && !m.uuid.equals(self) && !this.inWorld.contains(m.uuid) && dim.equals(m.dim)) {
                     ESPPlus.RemoteTarget t = this.targets.computeIfAbsent(m.uuid, id -> new ESPPlus.RemoteTarget());
                     t.uuid = m.uuid;
                     t.name = m.name;
                     t.mate = true;
                     t.visible = true;
                     t.via = null;
                     t.hp = m.hp + m.absorb;
                     t.outMs = 0L;
                     t.accept(m.x, m.y, m.z, m.receivedAt, now);
                     this.addIfShown(t, staleMs, rangeSq);
                  }
               }
            }

            if (enemies) {
               for (RemotePlayer r : team.getRemotePlayers()) {
                  if (r.uuid != null && !r.uuid.equals(self) && !this.inWorld.contains(r.uuid) && dim.equals(r.dim)) {
                     ESPPlus.RemoteTarget t = this.targets.computeIfAbsent(r.uuid, id -> new ESPPlus.RemoteTarget());
                     t.uuid = r.uuid;
                     t.name = r.name;
                     t.mate = false;
                     t.visible = r.visible;
                     t.via = r.seenBy == null ? null : String.join(", ", r.seenBy);
                     if (t.via != null && t.via.isEmpty()) {
                        t.via = null;
                     }

                     t.hp = r.hp + r.absorb;
                     t.outMs = r.outMs;
                     t.accept(r.x, r.y, r.z, r.receivedAt, now);
                     this.addIfShown(t, staleMs, rangeSq);
                  }
               }
            }

            if (this.targets.size() > this.active.size()) {
               this.targets.values().removeIf(tx -> tx.frame != this.frame);
            }

            return this.active;
         } else {
            if (!this.targets.isEmpty()) {
               this.targets.clear();
            }

            return this.active;
         }
      }

      private void addIfShown(ESPPlus.RemoteTarget t, long staleMs, double rangeSq) {
         t.frame = this.frame;
         if (t.sinceUpdate() <= staleMs) {
            if (!(ESPPlus.distanceSqToPlayer(t.x, t.y, t.z) > rangeSq)) {
               this.active.add(t);
            }
         }
      }
   }
}
