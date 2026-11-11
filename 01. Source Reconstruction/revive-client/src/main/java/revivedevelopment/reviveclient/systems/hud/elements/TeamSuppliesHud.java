package revivedevelopment.reviveclient.systems.hud.elements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.joml.Matrix3x2fStack;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.systems.managers.TeamSyncManager;
import revivedevelopment.reviveclient.systems.managers.team.TeamInventory;
import revivedevelopment.reviveclient.systems.managers.team.TeamMate;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class TeamSuppliesHud extends HudElement {
   public static final HudElementInfo<TeamSuppliesHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "team-supplies", "Team sync: your teammates' crystals, totems, pearls, obsidian, gapples and xp bottles.", TeamSuppliesHud::new
   );
   private static final long STALE_MS = 5000L;
   private static final int COUNT_WHITE = -1;
   private static final int COUNT_RED = -43691;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgItems = this.settings.createGroup("Items");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<Double> scale = this.sgGeneral
      .add(new DoubleSetting.Builder().name("scale").description("Scale of the item icons.").defaultValue(1.5).min(0.5).sliderRange(0.5, 4.0).build());
   private final Setting<Boolean> showSelf = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-self").description("Shows your own counts as the first row.").defaultValue(false).build());
   private final Setting<Boolean> hideStale = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hide-stale")
            .description("Hides teammates that are offline or haven't sent an update for more than 5 seconds.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> nameColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("name-color").description("Color of teammate names.").defaultValue(new SettingColor(60, 160, 255)).build());
   private final Setting<SettingColor> selfColor = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("self-color")
            .description("Color of your own name.")
            .defaultValue(new SettingColor(255, 255, 255))
            .visible(this.showSelf::get)
            .build()
      );
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("shadow").description("Renders shadow behind names.").defaultValue(true).build());
   private final Setting<Integer> border = this.sgGeneral
      .add(new IntSetting.Builder().name("border").description("How much space to add around the element.").defaultValue(2).min(0).build());
   private final Setting<Boolean> crystals = this.sgItems
      .add(new BoolSetting.Builder().name("crystals").description("Shows end crystals.").defaultValue(true).build());
   private final Setting<Boolean> totems = this.sgItems
      .add(new BoolSetting.Builder().name("totems").description("Shows totems of undying.").defaultValue(true).build());
   private final Setting<Boolean> pearls = this.sgItems
      .add(new BoolSetting.Builder().name("pearls").description("Shows ender pearls.").defaultValue(true).build());
   private final Setting<Boolean> obsidian = this.sgItems
      .add(new BoolSetting.Builder().name("obsidian").description("Shows obsidian.").defaultValue(true).build());
   private final Setting<Boolean> gapples = this.sgItems
      .add(new BoolSetting.Builder().name("gapples").description("Shows golden apples (enchanted + normal).").defaultValue(true).build());
   private final Setting<Boolean> xp = this.sgItems
      .add(new BoolSetting.Builder().name("xp-bottles").description("Shows experience bottles.").defaultValue(true).build());
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());
   private static final TeamInventory PREVIEW_A = new TeamInventory(128, 6, 14, 64, 32, 192);
   private static final TeamInventory PREVIEW_B = new TeamInventory(0, 1, 0, 256, 3, 64);
   private static ItemStack[] icons;
   private final List<TeamSuppliesHud.Row> rows = new ArrayList<>();
   private final boolean[] shown = new boolean[6];
   private int shownCount;
   private double nameWidth;

   private static ItemStack[] icons() {
      if (icons == null) {
         icons = new ItemStack[]{
            new ItemStack(Items.END_CRYSTAL),
            new ItemStack(Items.TOTEM_OF_UNDYING),
            new ItemStack(Items.ENDER_PEARL),
            new ItemStack(Items.OBSIDIAN),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
            new ItemStack(Items.EXPERIENCE_BOTTLE)
         };
      }

      return icons;
   }

   public TeamSuppliesHud() {
      super(INFO);
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   @Override
   public void tick(HudRenderer renderer) {
      this.shown[0] = this.crystals.get();
      this.shown[1] = this.totems.get();
      this.shown[2] = this.pearls.get();
      this.shown[3] = this.obsidian.get();
      this.shown[4] = this.gapples.get();
      this.shown[5] = this.xp.get();
      this.shownCount = 0;

      for (boolean b : this.shown) {
         if (b) {
            this.shownCount++;
         }
      }

      this.buildRows();
      this.nameWidth = 0.0;

      for (TeamSuppliesHud.Row row : this.rows) {
         this.nameWidth = Math.max(this.nameWidth, renderer.textWidth(row.name(), this.shadow.get()));
      }

      if (this.rows.isEmpty()) {
         this.setSize(0.0, 0.0);
      } else {
         this.setSize(this.rowWidth(), this.rows.size() * this.rowHeight(renderer) - this.rowGap());
      }
   }

   private void buildRows() {
      this.rows.clear();
      TeamSyncManager team = ReviveClient.TEAM;
      boolean connected = team != null && team.isConnected() && ReviveClient.mc.world != null;
      if (connected) {
         if (this.showSelf.get() && ReviveClient.mc.player != null) {
            this.rows
               .add(
                  new TeamSuppliesHud.Row(
                     ReviveClient.mc.player.getName().getString(), new Color(this.selfColor.get()), TeamSyncManager.countSupplies(ReviveClient.mc.player)
                  )
               );
         }

         long now = System.currentTimeMillis();
         List<TeamMate> mates = new ArrayList<>();

         for (TeamMate mate : team.getMates()) {
            if (mate.inv != null && (!this.hideStale.get() || now - mate.receivedAt <= 5000L)) {
               mates.add(mate);
            }
         }

         mates.sort(Comparator.comparing(m -> m.name.toLowerCase(Locale.ROOT)));
         Color color = new Color(this.nameColor.get());

         for (TeamMate matex : mates) {
            this.rows.add(new TeamSuppliesHud.Row(matex.name, color, matex.inv));
         }
      }

      if (this.isInEditor() && (!connected || this.rows.isEmpty())) {
         this.rows.clear();
         Color color = new Color(this.nameColor.get());
         this.rows.add(new TeamSuppliesHud.Row("Teammate1", color, PREVIEW_A));
         this.rows.add(new TeamSuppliesHud.Row("Teammate2", color, PREVIEW_B));
      }
   }

   private double iconSize() {
      return 16.0 * this.scale.get();
   }

   private double iconStep() {
      return 17.0 * this.scale.get();
   }

   private double rowGap() {
      return 2.0 * this.scale.get();
   }

   private double rowHeight(HudRenderer renderer) {
      return Math.max(this.iconSize(), renderer.textHeight(this.shadow.get())) + this.rowGap();
   }

   private double rowWidth() {
      double icons = this.shownCount == 0 ? 0.0 : this.iconStep() * this.shownCount - this.scale.get();
      return this.nameWidth + (this.shownCount == 0 ? 0.0 : 4.0 * this.scale.get()) + icons;
   }

   @Override
   public void render(HudRenderer renderer) {
      if (!this.rows.isEmpty()) {
         if (this.background.get()) {
            renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
         }

         double rowH = this.rowHeight(renderer);
         double contentH = rowH - this.rowGap();
         double textH = renderer.textHeight(this.shadow.get());
         double y = this.y + this.border.get();
         double iconsX = this.x + this.border.get() + this.nameWidth + 4.0 * this.scale.get();
         float s = this.scale.get().floatValue();

         for (TeamSuppliesHud.Row row : new ArrayList<>(this.rows)) {
            renderer.text(row.name(), this.x + this.border.get(), y + (contentH - textH) / 2.0, row.color(), this.shadow.get());
            double iconY = y + (contentH - this.iconSize()) / 2.0;
            int col = 0;

            for (int i = 0; i < 6; i++) {
               if (this.shown[i]) {
                  int count = count(row.inv(), i);
                  int ix = (int)(iconsX + col * this.iconStep());
                  int iy = (int)iconY;
                  ItemStack icon = icons()[i];
                  renderer.post(() -> drawIcon(renderer.drawContext, icon, ix, iy, s, count));
                  col++;
               }
            }

            y += rowH;
         }
      }
   }

   private static void drawIcon(DrawContext drawContext, ItemStack stack, int x, int y, float scale, int count) {
      if (drawContext != null) {
         Matrix3x2fStack matrices = drawContext.getMatrices();
         matrices.pushMatrix();
         matrices.scale((float)(1.0 / ReviveClient.mc.getWindow().getScaleFactor()));
         matrices.scale(scale, scale);
         int sx = (int)(x / scale);
         int sy = (int)(y / scale);
         drawContext.drawItem(stack, sx, sy);
         String text = formatCount(count);
         drawContext.drawText(
            ReviveClient.mc.textRenderer, text, sx + 19 - 2 - ReviveClient.mc.textRenderer.getWidth(text), sy + 6 + 3, count <= 0 ? -43691 : -1, true
         );
         matrices.popMatrix();
      }
   }

   private static String formatCount(int count) {
      if (count < 1000) {
         return Integer.toString(Math.max(0, count));
      } else {
         return count < 10000 ? String.format(Locale.ROOT, "%.1fk", count / 1000.0).replace(".0k", "k") : count / 1000 + "k";
      }
   }

   private static int count(TeamInventory inv, int i) {
      return switch (i) {
         case 0 -> inv.crystals;
         case 1 -> inv.totems;
         case 2 -> inv.pearls;
         case 3 -> inv.obsidian;
         case 4 -> inv.gapples;
         default -> inv.xp;
      };
   }

   private record Row(String name, Color color, TeamInventory inv) {
   }
}
