package revivedevelopment.reviveclient.systems.hud.elements;

import net.minecraft.entity.player.PlayerEntity;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.SortPriority;

public class TargetHud extends HudElement {
   public static final HudElementInfo<TargetHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "target", "Displays information about your current target.", TargetHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(new DoubleSetting.Builder().name("range").description("The range to target players.").defaultValue(100.0).min(1.0).sliderMax(200.0).build());
   private final Setting<Boolean> showHealth = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-health").description("Shows the target's health.").defaultValue(true).build());
   private final Setting<Boolean> showDistance = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-distance").description("Shows the distance to the target.").defaultValue(true).build());
   private final Setting<Boolean> showPing = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-ping").description("Shows the target's ping.").defaultValue(true).build());
   private final Setting<Boolean> textShadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("text-shadow").description("Renders shadow behind text.").defaultValue(true).build());
   private final Setting<Boolean> background = this.sgGeneral
      .add(new BoolSetting.Builder().name("background").description("Draws a new-UI rounded panel behind the target info.").defaultValue(true).build());

   public TargetHud() {
      super(INFO);
   }

   @Override
   public void render(HudRenderer renderer) {
      PlayerEntity target = ReviveClient.TARGET.getPlayerTarget(this.range.get(), SortPriority.LowestDistance);
      if (this.background.get() && this.getWidth() > 0 && this.getHeight() > 0) {
         double pad = 2.0;
         renderer.panel(
            this.x - pad, this.y - pad, this.getWidth() + pad * 2.0, this.getHeight() + pad * 2.0, HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT
         );
      }

      if (target == null && !this.isInEditor()) {
         String text = "Target: None";
         renderer.text(text, this.x, this.y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
         this.setSize(renderer.textWidth(text, this.textShadow.get()), renderer.textHeight(this.textShadow.get()));
      } else {
         if (this.isInEditor()) {
            target = ReviveClient.mc.player;
         }

         double y = this.y;
         double width = 0.0;
         String nameText = "Target: " + (target != null ? target.getName().getString() : "None");
         renderer.text(nameText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
         width = Math.max(width, renderer.textWidth(nameText, this.textShadow.get()));
         y += renderer.textHeight(this.textShadow.get()) + 2.0;
         if (target != null) {
            if (this.showHealth.get()) {
               double health = Math.round(target.getHealth() * 10.0) / 10.0;
               double maxHealth = Math.round(target.getMaxHealth() * 10.0) / 10.0;
               String healthText = "Health: ";
               double x1 = renderer.text(healthText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
               String healthValue = health + "/" + maxHealth;
               renderer.text(healthValue, x1, y, HudStyle.TEXT_SECONDARY, this.textShadow.get());
               width = Math.max(width, renderer.textWidth(healthText + healthValue, this.textShadow.get()));
               y += renderer.textHeight(this.textShadow.get()) + 2.0;
            }

            if (this.showDistance.get() && !this.isInEditor()) {
               double dist = Math.round(ReviveClient.mc.player.distanceTo(target) * 10.0) / 10.0;
               String distText = "Distance: ";
               double x1 = renderer.text(distText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
               String distValue = dist + "m";
               renderer.text(distValue, x1, y, HudStyle.TEXT_SECONDARY, this.textShadow.get());
               width = Math.max(width, renderer.textWidth(distText + distValue, this.textShadow.get()));
               y += renderer.textHeight(this.textShadow.get()) + 2.0;
            }

            if (this.showPing.get()) {
               int ping = EntityUtils.getPing(target);
               String pingText = "Ping: ";
               double x1 = renderer.text(pingText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
               String pingValue = ping + "ms";
               renderer.text(pingValue, x1, y, HudStyle.TEXT_SECONDARY, this.textShadow.get());
               width = Math.max(width, renderer.textWidth(pingText + pingValue, this.textShadow.get()));
               y += renderer.textHeight(this.textShadow.get()) + 2.0;
            }
         }

         this.setSize(width, y - this.y - 2.0);
      }
   }
}
