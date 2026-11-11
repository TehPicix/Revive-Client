package revivedevelopment.reviveclient.systems.hud.elements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.hud.Alignment;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.systems.managers.InformationManager;
import revivedevelopment.reviveclient.systems.managers.TeamSyncManager;
import revivedevelopment.reviveclient.systems.managers.team.RemotePlayer;
import revivedevelopment.reviveclient.systems.managers.team.TeamMate;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class PlayerRadarHud extends HudElement {
   public static final HudElementInfo<PlayerRadarHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "player-radar", "Displays players in your visual range.", PlayerRadarHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<Integer> limit = this.sgGeneral
      .add(new IntSetting.Builder().name("limit").description("The max number of players to show.").defaultValue(10).min(1).sliderRange(1, 20).build());
   private final Setting<Boolean> distance = this.sgGeneral
      .add(new BoolSetting.Builder().name("distance").description("Shows the distance to the player next to their name.").defaultValue(false).build());
   private final Setting<Boolean> friends = this.sgGeneral
      .add(new BoolSetting.Builder().name("display-friends").description("Whether to show friends or not.").defaultValue(true).build());
   private final Setting<Boolean> totemPops = this.sgGeneral
      .add(new BoolSetting.Builder().name("totem-pops").description("Shows how many totems each player has popped.").defaultValue(true).build());
   private final Setting<SettingColor> popsColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("totem-pops-color")
            .description("Color of the totem pop count.")
            .defaultValue(new SettingColor(225, 120, 20))
            .visible(this.totemPops::get)
            .build()
      );
   private final Setting<Boolean> outOfRender = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("out-of-render")
            .description(
               "Keeps players that left render distance in the list, struck through, with how long they've been gone. Players that died are not kept. Pop reset timing is in Config -> Totem Pops."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> outOfRenderTime = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("out-of-render-time")
            .description("Seconds a player that left render stays in the list.")
            .defaultValue(300)
            .range(10, 600)
            .sliderRange(30, 600)
            .visible(this.outOfRender::get)
            .build()
      );
   private final Setting<Boolean> teamPlayers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("team-players")
            .description(
               "Team sync: also lists players your teammates see that aren't in your render (same dimension), marked \"via <teammate>\". Struck through with how long they've been gone once no teammate sees them."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> showTeammates = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("show-teammates")
            .description("Team sync: lists your online teammates outside your render with their distance.")
            .defaultValue(false)
            .build()
      );
   private final Setting<SettingColor> teamColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("team-color")
            .description("Color of teammates and of the \"via\" marker.")
            .defaultValue(new SettingColor(60, 160, 255))
            .visible(() -> this.teamPlayers.get() || this.showTeammates.get())
            .build()
      );
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("shadow").description("Renders shadow behind text.").defaultValue(true).build());
   private final Setting<Alignment> alignment = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("alignment")).description("Horizontal alignment."))
               .defaultValue(Alignment.Auto))
            .build()
      );
   private final Setting<Integer> border = this.sgGeneral
      .add(new IntSetting.Builder().name("border").description("How much space to add around the element.").defaultValue(0).build());
   private final Setting<Boolean> customScale = this.sgScale
      .add(new BoolSetting.Builder().name("custom-scale").description("Applies custom text scale rather than the global one.").defaultValue(false).build());
   private final Setting<Double> scale = this.sgScale
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Custom scale.")
            .visible(this.customScale::get)
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());
   private final List<AbstractClientPlayerEntity> players = new ArrayList<>();
   private final List<PlayerRadarHud.Row> rows = new ArrayList<>();
   private final Set<UUID> listed = new HashSet<>();

   public PlayerRadarHud() {
      super(INFO);
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   @Override
   protected double alignX(double width, Alignment alignment) {
      return this.box.alignX(this.getWidth() - this.border.get() * 2, width, alignment);
   }

   @Override
   public void tick(HudRenderer renderer) {
      double width = renderer.textWidth("Players:", this.shadow.get(), this.getScale());
      double height = renderer.textHeight(this.shadow.get(), this.getScale());
      this.rows.clear();
      if (ReviveClient.mc.world == null) {
         this.players.clear();
         this.setSize(width, height);
      } else {
         this.buildRows();

         for (PlayerRadarHud.Row row : this.rows) {
            width = Math.max(width, this.rowWidth(renderer, row));
            height += renderer.textHeight(this.shadow.get(), this.getScale()) + 2.0;
         }

         this.setSize(width, height);
      }
   }

   private void buildRows() {
      this.listed.clear();
      if (ReviveClient.mc.player != null) {
         this.listed.add(ReviveClient.mc.player.getUuid());
      }

      for (PlayerEntity entity : ReviveClient.mc.world.getPlayers()) {
         this.listed.add(entity.getUuid());
      }

      for (PlayerEntity entity : this.getPlayers()) {
         if (!entity.equals(ReviveClient.mc.player) && (this.friends.get() || !Friends.get().isFriend(entity))) {
            String distanceText = this.distance.get() ? String.format("(%sm)", Math.round(ReviveClient.mc.getCameraEntity().distanceTo(entity))) : null;
            this.rows
               .add(
                  new PlayerRadarHud.Row(
                     entity.getName().getString(),
                     new Color(PlayerUtils.getPlayerColor(entity, HudStyle.TEXT_PRIMARY)),
                     this.popsText(ReviveClient.INFO.getPops(entity)),
                     distanceText,
                     false
                  )
               );
         }
      }

      TeamSyncManager team = ReviveClient.TEAM;
      if (team != null && team.isConnected()) {
         if (this.showTeammates.get()) {
            this.addTeammateRows(team);
         }

         if (this.teamPlayers.get()) {
            this.addTeamPlayerRows(team);
         }
      }

      if (this.outOfRender.get() && this.rows.size() < this.limit.get()) {
         long maxMs = this.outOfRenderTime.get().intValue() * 1000L;
         List<InformationManager.TrackedPlayer> gone = new ArrayList<>();

         for (InformationManager.TrackedPlayer tp : ReviveClient.INFO.getTrackedPlayers()) {
            if (!tp.inRender && tp.outOfRenderMillis() <= maxMs && !this.listed.contains(tp.uuid)) {
               boolean friend = Friends.get().get(tp.name) != null;
               if (this.friends.get() || !friend) {
                  gone.add(tp);
               }
            }
         }

         gone.sort(Comparator.comparingLong(InformationManager.TrackedPlayer::outOfRenderMillis));

         for (InformationManager.TrackedPlayer tpx : gone) {
            if (this.rows.size() >= this.limit.get()) {
               break;
            }

            boolean friend = Friends.get().get(tpx.name) != null;
            Color color = friend ? new Color(Config.get().friendColor.get()) : HudStyle.TEXT_SECONDARY;
            this.rows
               .add(new PlayerRadarHud.Row(tpx.name, color, this.popsText(ReviveClient.INFO.getPops(tpx.uuid)), formatGone(tpx.outOfRenderMillis()), true));
         }
      }
   }

   private void addTeammateRows(TeamSyncManager team) {
      String dim = ReviveClient.mc.world.getRegistryKey().getValue().toString();
      List<TeamMate> mates = new ArrayList<>();

      for (TeamMate mate : team.getMates()) {
         if (mate.uuid != null && !this.listed.contains(mate.uuid)) {
            mates.add(mate);
         }
      }

      mates.sort(Comparator.comparingDouble(m -> dim.equals(m.dim) ? distanceTo(m.x, m.y, m.z) : Double.MAX_VALUE));
      Color color = new Color(this.teamColor.get());

      for (TeamMate matex : mates) {
         if (this.rows.size() >= this.limit.get()) {
            return;
         }

         this.listed.add(matex.uuid);
         String where = dim.equals(matex.dim)
            ? String.format("(%sm)", Math.round(distanceTo(matex.x, matex.y, matex.z)))
            : "(" + dimensionName(matex.dim) + ")";
         this.rows.add(new PlayerRadarHud.Row(matex.name, color, null, where, false, null));
      }
   }

   private void addTeamPlayerRows(TeamSyncManager team) {
      String dim = ReviveClient.mc.world.getRegistryKey().getValue().toString();
      long maxMs = this.outOfRenderTime.get().intValue() * 1000L;
      long now = System.currentTimeMillis();
      List<RemotePlayer> remotes = new ArrayList<>();

      for (RemotePlayer remote : team.getRemotePlayers()) {
         if (remote.uuid != null && !this.listed.contains(remote.uuid) && dim.equals(remote.dim) && !team.isTeammate(remote.uuid)) {
            boolean friend = Friends.get().get(remote.name) != null;
            if ((this.friends.get() || !friend) && (remote.visible || this.outOfRender.get() && goneMillis(remote, now) <= maxMs)) {
               remotes.add(remote);
            }
         }
      }

      remotes.sort(
         Comparator.<RemotePlayer>comparingInt(r -> r.visible ? 0 : 1).thenComparingDouble(r -> r.visible ? distanceTo(r.x, r.y, r.z) : goneMillis(r, now))
      );

      for (RemotePlayer remotex : remotes) {
         if (this.rows.size() >= this.limit.get()) {
            return;
         }

         this.listed.add(remotex.uuid);
         boolean friend = Friends.get().get(remotex.name) != null;
         String via = remotex.seenBy != null && !remotex.seenBy.isEmpty() ? "via " + String.join(", ", remotex.seenBy) : "via team";
         Color color;
         String suffix;
         if (remotex.visible) {
            color = friend ? new Color(Config.get().friendColor.get()) : HudStyle.TEXT_PRIMARY;
            suffix = this.distance.get() ? String.format("(%sm)", Math.round(distanceTo(remotex.x, remotex.y, remotex.z))) : null;
         } else {
            color = friend ? new Color(Config.get().friendColor.get()) : HudStyle.TEXT_SECONDARY;
            suffix = "gone " + formatGone(goneMillis(remotex, now));
         }

         this.rows.add(new PlayerRadarHud.Row(remotex.name, color, this.popsText(ReviveClient.INFO.getPops(remotex.uuid)), suffix, !remotex.visible, via));
      }
   }

   private static long goneMillis(RemotePlayer remote, long now) {
      return remote.visible ? 0L : remote.outMs + Math.max(0L, now - remote.receivedAt);
   }

   private static double distanceTo(double x, double y, double z) {
      Entity camera = (Entity)(ReviveClient.mc.getCameraEntity() != null ? ReviveClient.mc.getCameraEntity() : ReviveClient.mc.player);
      double dx = x - camera.getX();
      double dy = y - camera.getY();
      double dz = z - camera.getZ();
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private static String dimensionName(String dim) {
      if (dim == null) {
         return "?";
      } else {
         int colon = dim.indexOf(58);
         return colon >= 0 ? dim.substring(colon + 1) : dim;
      }
   }

   private String popsText(int pops) {
      return this.totemPops.get() && pops > 0 ? "-" + pops : null;
   }

   private static String formatGone(long ms) {
      long seconds = ms / 1000L;
      return seconds < 60L ? seconds + "s" : seconds / 60L + "m " + seconds % 60L + "s";
   }

   private double rowWidth(HudRenderer renderer, PlayerRadarHud.Row row) {
      double spaceWidth = renderer.textWidth(" ", this.shadow.get(), this.getScale());
      double w = renderer.textWidth(row.name(), this.shadow.get(), this.getScale());
      if (row.pops() != null) {
         w += spaceWidth + renderer.textWidth(row.pops(), this.shadow.get(), this.getScale());
      }

      if (row.suffix() != null) {
         w += spaceWidth + renderer.textWidth(row.suffix(), this.shadow.get(), this.getScale());
      }

      if (row.tag() != null) {
         w += spaceWidth + renderer.textWidth(row.tag(), this.shadow.get(), this.getScale());
      }

      return w;
   }

   @Override
   public void render(HudRenderer renderer) {
      double y = this.y + this.border.get();
      if (this.background.get()) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
      }

      renderer.text(
         "Players:",
         this.x + this.border.get() + this.alignX(renderer.textWidth("Players:", this.shadow.get(), this.getScale()), this.alignment.get()),
         y,
         HudStyle.TEXT_SECONDARY,
         this.shadow.get(),
         this.getScale()
      );
      if (ReviveClient.mc.world != null) {
         double spaceWidth = renderer.textWidth(" ", this.shadow.get(), this.getScale());
         double lineHeight = renderer.textHeight(this.shadow.get(), this.getScale());

         for (PlayerRadarHud.Row row : this.rows) {
            double x = this.x + this.border.get() + this.alignX(this.rowWidth(renderer, row), this.alignment.get());
            y += lineHeight + 2.0;
            x = renderer.text(row.name(), x, y, row.color(), this.shadow.get(), this.getScale());
            if (row.struck()) {
               renderer.quad(x, y + lineHeight / 2.0 - 0.5, x - x, 1.0, row.color());
            }

            if (row.pops() != null) {
               x = renderer.text(row.pops(), x + spaceWidth, y, this.popsColor.get(), this.shadow.get(), this.getScale());
            }

            if (row.suffix() != null) {
               x = renderer.text(row.suffix(), x + spaceWidth, y, HudStyle.TEXT_SECONDARY, this.shadow.get(), this.getScale());
            }

            if (row.tag() != null) {
               renderer.text(row.tag(), x + spaceWidth, y, this.teamColor.get(), this.shadow.get(), this.getScale());
            }
         }
      }
   }

   private List<AbstractClientPlayerEntity> getPlayers() {
      this.players.clear();
      this.players.addAll(ReviveClient.mc.world.getPlayers());
      this.players.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(ReviveClient.mc.getCameraEntity())));
      if (this.players.size() > this.limit.get()) {
         this.players.subList(this.limit.get(), this.players.size()).clear();
      }

      return this.players;
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : -1.0;
   }

   private record Row(String name, Color color, String pops, String suffix, boolean struck, String tag) {
      Row(String name, Color color, String pops, String suffix, boolean struck) {
         this(name, color, pops, suffix, struck, null);
      }
   }
}
