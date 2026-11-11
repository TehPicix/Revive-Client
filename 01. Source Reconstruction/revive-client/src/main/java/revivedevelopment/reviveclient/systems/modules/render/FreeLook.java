package revivedevelopment.reviveclient.systems.modules.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.events.revive.MouseScrollEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.input.Input;

public class FreeLook extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<FreeLook.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("Player = mouse moves the camera, body stays still. Camera = mouse keeps moving the player."))
               .defaultValue(FreeLook.Mode.Player))
            .build()
      );
   private final Setting<Boolean> togglePerspective = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("toggle-perspective")
            .description("Switches to third person on activate (and back on deactivate).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> cameraDistance = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("camera-distance")
            .description("Distance of the third-person camera from the player.")
            .defaultValue(4.0)
            .min(0.0)
            .max(50.0)
            .sliderRange(0.0, 20.0)
            .build()
      );
   private final Setting<Boolean> scrollZoom = this.sgGeneral
      .add(new BoolSetting.Builder().name("scroll-zoom").description("Scroll wheel adjusts the camera distance.").defaultValue(true).build());
   private final Setting<Double> scrollSensitivity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scroll-sensitivity")
            .description("Scroll wheel sensitivity when zooming.")
            .defaultValue(1.0)
            .min(0.01)
            .max(10.0)
            .sliderRange(0.01, 10.0)
            .visible(this.scrollZoom::get)
            .build()
      );
   private final Setting<Boolean> arrowControl = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("arrow-control").description("Arrow keys rotate the player body while freelook is active.").defaultValue(true).build()
      );
   private final Setting<Double> arrowSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("arrow-speed")
            .description("How fast the arrow keys rotate the player (degrees per tick).")
            .defaultValue(3.0)
            .min(0.1)
            .max(15.0)
            .sliderRange(0.1, 15.0)
            .visible(this.arrowControl::get)
            .build()
      );
   public final Setting<Boolean> noClip = this.sgGeneral
      .add(new BoolSetting.Builder().name("no-clip").description("The camera passes through walls.").defaultValue(true).build());
   public float cameraYaw;
   public float cameraPitch;
   public float extraDistance;
   private Perspective prePerspective;

   public FreeLook() {
      super(Categories.Render, "free-look", "Independent camera rotation in third person.");
   }

   @Override
   public void onActivate() {
      if (this.mc.player != null) {
         this.cameraYaw = this.mc.player.getYaw();
         this.cameraPitch = this.mc.player.getPitch();
         this.extraDistance = (float)(this.cameraDistance.get() - 4.0);
         this.prePerspective = this.mc.options.getPerspective();
         if (this.prePerspective != Perspective.THIRD_PERSON_BACK && this.togglePerspective.get()) {
            this.mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
         }
      }
   }

   @Override
   public void onDeactivate() {
      if (this.prePerspective != null && this.mc.options.getPerspective() != this.prePerspective && this.togglePerspective.get()) {
         this.mc.options.setPerspective(this.prePerspective);
      }
   }

   public boolean playerMode() {
      return this.isActive() && this.mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK && this.mode.get() == FreeLook.Mode.Player;
   }

   public void applyMouseDelta(double deltaYaw, double deltaPitch) {
      this.cameraYaw += (float)deltaYaw;
      this.cameraPitch = MathHelper.clamp(this.cameraPitch + (float)deltaPitch, -90.0F, 90.0F);
   }

   @EventHandler
   private void onScroll(MouseScrollEvent event) {
      if (this.scrollZoom.get() && this.isActive()) {
         if (this.mc.currentScreen == null) {
            if (this.mc.options.getPerspective() != Perspective.FIRST_PERSON) {
               if (!(this.scrollSensitivity.get() <= 0.0)) {
                  double total = 4.0 + this.extraDistance;
                  total -= event.value * 0.25 * (this.scrollSensitivity.get() * total);
                  if (total < 0.0) {
                     total = 0.0;
                  }

                  this.extraDistance = (float)(total - 4.0);
                  event.setCancelled(true);
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null) {
         if (this.playerMode() && this.arrowControl.get() && this.mc.currentScreen == null) {
            float speed = this.arrowSpeed.get().floatValue();
            if (Input.isKeyPressed(263)) {
               this.mc.player.setYaw(this.mc.player.getYaw() - speed);
            }

            if (Input.isKeyPressed(262)) {
               this.mc.player.setYaw(this.mc.player.getYaw() + speed);
            }

            if (Input.isKeyPressed(265)) {
               this.mc.player.setPitch(this.mc.player.getPitch() - speed);
            }

            if (Input.isKeyPressed(264)) {
               this.mc.player.setPitch(this.mc.player.getPitch() + speed);
            }
         }

         this.mc.player.setPitch(MathHelper.clamp(this.mc.player.getPitch(), -90.0F, 90.0F));
         this.cameraPitch = MathHelper.clamp(this.cameraPitch, -90.0F, 90.0F);
      }
   }

   public static enum Mode {
      Player,
      Camera;
   }
}
