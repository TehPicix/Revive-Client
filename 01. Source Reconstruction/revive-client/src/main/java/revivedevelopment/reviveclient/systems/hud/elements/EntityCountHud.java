package revivedevelopment.reviveclient.systems.hud.elements;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;

public class EntityCountHud extends HudElement {
   public static final HudElementInfo<EntityCountHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "entity-count", "Displays entity counts around you.", EntityCountHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> showPlayers = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-players").description("Shows player count.").defaultValue(true).build());
   private final Setting<Boolean> showHostile = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-hostile").description("Shows hostile mob count.").defaultValue(true).build());
   private final Setting<Boolean> showPassive = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-passive").description("Shows passive mob count.").defaultValue(true).build());
   private final Setting<Boolean> showTotal = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-total").description("Shows total entity count.").defaultValue(true).build());
   private final Setting<Boolean> textShadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("text-shadow").description("Renders shadow behind text.").defaultValue(true).build());

   public EntityCountHud() {
      super(INFO);
   }

   @Override
   public void render(HudRenderer renderer) {
      if (ReviveClient.mc.world == null) {
         String text = "Entities: 0";
         renderer.text(text, this.x, this.y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
         this.setSize(renderer.textWidth(text, this.textShadow.get()), renderer.textHeight(this.textShadow.get()));
      } else {
         int players = 0;
         int hostile = 0;
         int passive = 0;
         int total = 0;

         for (Entity entity : ReviveClient.mc.world.getEntities()) {
            if (entity != ReviveClient.mc.player) {
               if (entity instanceof PlayerEntity) {
                  players++;
               } else if (entity instanceof HostileEntity) {
                  hostile++;
               } else if (entity instanceof PassiveEntity) {
                  passive++;
               }

               total++;
            }
         }

         double y = this.y;
         double width = 0.0;
         if (this.showPlayers.get()) {
            String titleText = "Players: ";
            double x1 = renderer.text(titleText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
            String valueText = String.valueOf(players);
            renderer.text(valueText, x1, y, HudStyle.TEXT_SECONDARY, this.textShadow.get());
            width = Math.max(width, renderer.textWidth(titleText + valueText, this.textShadow.get()));
            y += renderer.textHeight(this.textShadow.get()) + 2.0;
         }

         if (this.showHostile.get()) {
            String titleText = "Hostile: ";
            double x1 = renderer.text(titleText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
            String valueText = String.valueOf(hostile);
            renderer.text(valueText, x1, y, HudStyle.TEXT_SECONDARY, this.textShadow.get());
            width = Math.max(width, renderer.textWidth(titleText + valueText, this.textShadow.get()));
            y += renderer.textHeight(this.textShadow.get()) + 2.0;
         }

         if (this.showPassive.get()) {
            String titleText = "Passive: ";
            double x1 = renderer.text(titleText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
            String valueText = String.valueOf(passive);
            renderer.text(valueText, x1, y, HudStyle.TEXT_SECONDARY, this.textShadow.get());
            width = Math.max(width, renderer.textWidth(titleText + valueText, this.textShadow.get()));
            y += renderer.textHeight(this.textShadow.get()) + 2.0;
         }

         if (this.showTotal.get()) {
            String titleText = "Total: ";
            double x1 = renderer.text(titleText, this.x, y, HudStyle.TEXT_PRIMARY, this.textShadow.get());
            String valueText = String.valueOf(total);
            renderer.text(valueText, x1, y, HudStyle.TEXT_SECONDARY, this.textShadow.get());
            width = Math.max(width, renderer.textWidth(titleText + valueText, this.textShadow.get()));
            y += renderer.textHeight(this.textShadow.get()) + 2.0;
         }

         this.setSize(width, Math.max(renderer.textHeight(this.textShadow.get()), y - this.y - 2.0));
      }
   }
}
