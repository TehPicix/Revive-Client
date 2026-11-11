package revivedevelopment.reviveclient.systems.hud.elements;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class PlayerModelHud extends HudElement {
   public static final HudElementInfo<PlayerModelHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "player-model", "Displays a model of your player.", PlayerModelHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(2.0)
            .min(1.0)
            .sliderRange(1.0, 5.0)
            .onChanged(aDouble -> this.calculateSize())
            .build()
      );
   private final Setting<Boolean> copyYaw = this.sgGeneral
      .add(new BoolSetting.Builder().name("copy-yaw").description("Makes the player model's yaw equal to yours.").defaultValue(true).build());
   private final Setting<Integer> customYaw = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("custom-yaw")
            .description("Custom yaw for when copy yaw is off.")
            .defaultValue(0)
            .range(-180, 180)
            .sliderRange(-180, 180)
            .visible(() -> !this.copyYaw.get())
            .build()
      );
   private final Setting<Boolean> copyPitch = this.sgGeneral
      .add(new BoolSetting.Builder().name("copy-pitch").description("Makes the player model's pitch equal to yours.").defaultValue(true).build());
   private final Setting<Integer> customPitch = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("custom-pitch")
            .description("Custom pitch for when copy pitch is off.")
            .defaultValue(0)
            .range(-90, 90)
            .sliderRange(-90, 90)
            .visible(() -> !this.copyPitch.get())
            .build()
      );
   private final Setting<PlayerModelHud.CenterOrientation> centerOrientation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("center-orientation"))
                  .description("Which direction the player faces when the HUD model faces directly forward."))
               .defaultValue(PlayerModelHud.CenterOrientation.South))
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());

   public PlayerModelHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void render(HudRenderer renderer) {
      renderer.post(() -> {
         PlayerEntity player = ReviveClient.mc.player;
         if (player != null) {
            float offset = this.centerOrientation.get() == PlayerModelHud.CenterOrientation.North ? 180.0F : 0.0F;
            float yaw = this.copyYaw.get() ? MathHelper.wrapDegrees(player.getYaw() + offset) : this.customYaw.get().intValue();
            float pitch = this.copyPitch.get() ? player.getPitch() : this.customPitch.get().intValue();
            this.drawEntity(renderer.drawContext, this.x, this.y, (int)(30.0 * this.scale.get()), -yaw, -pitch, player);
         }
      });
      if (this.background.get()) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
      } else if (ReviveClient.mc.player == null) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
         renderer.line(this.x, this.y, this.x + this.getWidth(), this.y + this.getHeight(), Color.GRAY);
         renderer.line(this.x + this.getWidth(), this.y, this.x, this.y + this.getHeight(), Color.GRAY);
      }
   }

   private void calculateSize() {
      this.setSize(50.0 * this.scale.get(), 75.0 * this.scale.get());
   }

   private void drawEntity(DrawContext context, int x, int y, int size, float yaw, float pitch, LivingEntity entity) {
      float tanYaw = (float)Math.atan(yaw / 40.0F);
      float tanPitch = (float)Math.atan(pitch / 40.0F);
      Quaternionf quaternion = new Quaternionf().rotateZ((float) Math.PI);
      float previousBodyYaw = entity.bodyYaw;
      float previousYaw = entity.getYaw();
      float previousPitch = entity.getPitch();
      float previousPrevHeadYaw = entity.getHeadYaw();
      float prevHeadYaw = entity.headYaw;
      entity.bodyYaw = 180.0F + tanYaw * 20.0F;
      entity.setYaw(180.0F + tanYaw * 40.0F);
      entity.setPitch(-tanPitch * 20.0F);
      entity.headYaw = entity.getYaw();
      entity.setHeadYaw(entity.getYaw());
      LivingEntityRenderState state = (LivingEntityRenderState)ReviveClient.mc
         .getEntityRenderDispatcher()
         .getRenderer(entity)
         .getAndUpdateRenderState(entity, 1.0F);
      entity.bodyYaw = previousBodyYaw;
      entity.setYaw(previousYaw);
      entity.setPitch(previousPitch);
      entity.setHeadYaw(previousPrevHeadYaw);
      entity.headYaw = prevHeadYaw;
      float s = 1.0F / ReviveClient.mc.getWindow().getScaleFactor();
      int x1 = (int)(x * s);
      int y1 = (int)(y * s);
      int x2 = (int)((x + this.getWidth()) * s);
      int y2 = (int)((y + this.getHeight()) * s);
      context.addEntity(state, size * s, new Vector3f(0.0F, 1.0F, 0.0F), quaternion, null, x1, y1, x2, y2);
   }

   private static enum CenterOrientation {
      North,
      South;
   }
}
