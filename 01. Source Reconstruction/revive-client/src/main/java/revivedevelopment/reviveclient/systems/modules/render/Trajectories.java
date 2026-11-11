package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Trajectories extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> otherHand = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("offhand")
            .description("Also predicts the projectile in your offhand when the main hand has none.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> fullBowCharge = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("full-bow-charge")
            .description("Shows the fully charged path while holding a bow without drawing it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> maxTicks = this.sgGeneral
      .add(new IntSetting.Builder().name("simulation-ticks").description("How many ticks ahead to simulate.").defaultValue(240).sliderRange(20, 500).build());
   private final Setting<SettingColor> lineColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("line-color").description("Color of the predicted path.").defaultValue(new SettingColor(255, 150, 0, 200)).build());
   private final Setting<SettingColor> hitColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("hit-color").description("Color of the impact marker.").defaultValue(new SettingColor(255, 50, 50, 200)).build());
   private final List<Vec3d> path = new ArrayList<>();
   private final Color line = new Color();
   private final Color hitSide = new Color();
   private final Color hitLine = new Color();

   public Trajectories() {
      super(Categories.Render, "trajectories", "Predicts the flight path of held projectiles.");
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player != null && this.mc.world != null) {
         ItemStack stack = this.mc.player.getMainHandStack();
         Trajectories.ProjectileParams params = this.paramsOf(stack);
         if (params == null && this.otherHand.get()) {
            stack = this.mc.player.getOffHandStack();
            params = this.paramsOf(stack);
         }

         if (params != null) {
            this.path.clear();
            BlockHitResult hit = this.simulate(params);
            this.line.set(this.lineColor.get());

            for (int i = 1; i < this.path.size(); i++) {
               Vec3d a = this.path.get(i - 1);
               Vec3d b = this.path.get(i);
               event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, this.line);
            }

            if (hit != null) {
               Vec3d p = hit.getPos();
               this.hitLine.set(this.hitColor.get());
               this.hitSide.set(this.hitColor.get());
               this.hitSide.a = 60;
               event.renderer.box(p.x - 0.25, p.y - 0.25, p.z - 0.25, p.x + 0.25, p.y + 0.25, p.z + 0.25, this.hitSide, this.hitLine, ShapeMode.Both, 0);
            }
         }
      }
   }

   private Trajectories.ProjectileParams paramsOf(ItemStack stack) {
      Item item = stack.getItem();
      if (!(item instanceof BowItem)) {
         if (item instanceof CrossbowItem) {
            return new Trajectories.ProjectileParams(3.15, 0.05, 0.0F);
         } else if (item instanceof TridentItem) {
            return new Trajectories.ProjectileParams(2.5, 0.05, 0.0F);
         } else if (item instanceof EnderPearlItem || item instanceof SnowballItem || item instanceof EggItem) {
            return new Trajectories.ProjectileParams(1.5, 0.03, 0.0F);
         } else if (item instanceof ExperienceBottleItem) {
            return new Trajectories.ProjectileParams(0.7, 0.07, -20.0F);
         } else {
            return !(item instanceof SplashPotionItem) && !(item instanceof LingeringPotionItem) ? null : new Trajectories.ProjectileParams(0.5, 0.05, -20.0F);
         }
      } else {
         float charge = 1.0F;
         if (this.mc.player.isUsingItem() && this.mc.player.getActiveItem() == stack) {
            charge = BowItem.getPullProgress(this.mc.player.getItemUseTime());
         } else if (!this.fullBowCharge.get()) {
            return null;
         }

         return charge < 0.1F ? null : new Trajectories.ProjectileParams((double)(charge * 3.0F), 0.05, 0.0F);
      }
   }

   private BlockHitResult simulate(Trajectories.ProjectileParams params) {
      float yaw = this.mc.player.getYaw();
      float pitch = this.mc.player.getPitch();
      double dirX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
      double dirY = -Math.sin(Math.toRadians(pitch + params.rollOffset));
      double dirZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
      Vec3d velocity = new Vec3d(dirX, dirY, dirZ).normalize().multiply(params.speed);
      Vec3d playerVel = this.mc.player.getVelocity();
      velocity = velocity.add(playerVel.x, this.mc.player.isOnGround() ? 0.0 : playerVel.y, playerVel.z);
      Vec3d pos = new Vec3d(this.mc.player.getX(), this.mc.player.getEyeY() - 0.1, this.mc.player.getZ());
      this.path.add(pos);

      for (int i = 0; i < this.maxTicks.get(); i++) {
         Vec3d next = pos.add(velocity);
         HitResult result = this.mc.world.raycast(new RaycastContext(pos, next, ShapeType.COLLIDER, FluidHandling.NONE, this.mc.player));
         if (result.getType() == Type.BLOCK) {
            this.path.add(result.getPos());
            return (BlockHitResult)result;
         }

         pos = next;
         this.path.add(next);
         velocity = velocity.multiply(0.99).subtract(0.0, params.gravity, 0.0);
      }

      return null;
   }

   private record ProjectileParams(double speed, double gravity, float rollOffset) {
   }
}
