package revivedevelopment.reviveclient.systems.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.revive.MouseScrollEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.render.Freecam;
import revivedevelopment.reviveclient.utils.misc.input.Input;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class AirPlace extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("How far along the crosshair ray the air target may be (capped at AntiCheat's block-interact-range).")
            .defaultValue(4.5)
            .min(1.0)
            .max(6.0)
            .sliderRange(1.0, 6.0)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("delay")
            .description("Ticks between placements while the use key is held (FastUse overrides this when active).")
            .defaultValue(4)
            .min(0)
            .sliderMax(10)
            .build()
      );
   private final Setting<Boolean> swingHand = this.sgGeneral
      .add(new BoolSetting.Builder().name("swing").description("Swing the hand client-side on each placement.").defaultValue(true).build());
   private final Setting<Boolean> scrollRange = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("scroll-range")
            .description("Hold the modifier key and scroll the mouse wheel to push the placement range farther or closer.")
            .defaultValue(true)
            .build()
      );
   private final Setting<AirPlace.ScrollModifier> scrollModifier = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("scroll-modifier"))
                     .description("Key held while scrolling to adjust the range."))
                  .defaultValue(AirPlace.ScrollModifier.Ctrl))
               .visible(this.scrollRange::get))
            .build()
      );
   private final Setting<Double> scrollStep = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scroll-step")
            .description("How much one scroll notch changes the range.")
            .defaultValue(0.5)
            .min(0.1)
            .max(2.0)
            .sliderRange(0.1, 2.0)
            .visible(this.scrollRange::get)
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Render the block position the next placement targets.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the preview box is rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The fill color of the preview box.")
            .defaultValue(new SettingColor(204, 0, 0, 40))
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The outline color of the preview box.")
            .defaultValue(new SettingColor(204, 0, 0, 255))
            .visible(this.render::get)
            .build()
      );
   private BlockPos target = null;
   private int delayTimer = 0;

   public AirPlace() {
      super(Categories.Player, "air-place", "Places the held block where your crosshair points, even in mid-air.");
   }

   @Override
   public void onDeactivate() {
      this.target = null;
      this.delayTimer = 0;
   }

   @EventHandler
   private void onMouseScroll(MouseScrollEvent event) {
      if (this.scrollRange.get() && this.mc.player != null && this.mc.currentScreen == null) {
         if (this.scrollModifier.get().isHeld()) {
            double v = this.range.get() + Math.signum(event.value) * this.scrollStep.get();
            if (this.range instanceof DoubleSetting ds) {
               v = Math.max(ds.min, Math.min(ds.max, v));
            }

            this.range.set(Math.round(v * 10.0) / 10.0);
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         this.delayTimer++;
         BlockItem item = this.heldBlockItem();
         if (item == null) {
            this.target = null;
         } else {
            this.target = this.findTarget(item);
            if (this.target != null) {
               if (this.mc.options.useKey.isPressed()) {
                  ItemStack held = this.mc.player.getMainHandStack();
                  FastUse fastUse = Modules.get().get(FastUse.class);
                  int effectiveDelay = fastUse != null && fastUse.isActive() ? fastUse.getItemUseCooldown(held) : this.delay.get();
                  if (this.delayTimer >= effectiveDelay) {
                     if (ReviveClient.BLOCK.beginPlacement(this.target, this.mc.world.getBlockState(this.target), item)) {
                        try {
                           ReviveClient.BLOCK.placeBlock(item, this.target);
                        } finally {
                           ReviveClient.BLOCK.endPlacement();
                        }

                        if (this.swingHand.get()) {
                           this.mc.player.swingHand(Hand.MAIN_HAND);
                        }

                        this.delayTimer = 0;
                     }
                  }
               }
            }
         }
      }
   }

   private BlockItem heldBlockItem() {
      if (this.mc.player.getMainHandStack().getItem() instanceof BlockItem item) {
         return item;
      } else {
         return this.mc.player.getOffHandStack().getItem() instanceof BlockItem item ? item : null;
      }
   }

   private BlockPos findTarget(BlockItem item) {
      double effectiveRange = Math.min(this.range.get(), AntiCheatConfig.get().blockInteractRange.get());
      BlockPos pos = null;
      HitResult hit = this.mc.crosshairTarget;
      if (hit instanceof BlockHitResult blockHit && hit.getType() == Type.BLOCK) {
         BlockPos hitPos = blockHit.getBlockPos();
         if (!this.mc.world.getBlockState(hitPos).isReplaceable()) {
            hitPos = hitPos.offset(blockHit.getSide());
         }

         if (this.inRange(hitPos, effectiveRange)) {
            pos = hitPos;
         }
      } else if (hit != null && hit.getType() == Type.ENTITY) {
         BlockPos entityPos = BlockPos.ofFloored(hit.getPos());
         if (this.inRange(entityPos, effectiveRange)) {
            pos = entityPos;
         }
      }

      if (pos == null) {
         Vec3d end = this.aimRayEnd(effectiveRange);
         if (end == null) {
            return null;
         }

         pos = BlockPos.ofFloored(end);
         if (!this.inRange(pos, effectiveRange)) {
            return null;
         }
      }

      if (!ReviveClient.BLOCK.checkPlacement(item, pos)) {
         return null;
      } else {
         return !this.mc.world.canPlace(Block.getBlockFromItem(item).getDefaultState(), pos, ShapeContext.of(this.mc.player)) ? null : pos.toImmutable();
      }
   }

   private boolean inRange(BlockPos pos, double range) {
      return new Box(pos).squaredMagnitude(this.mc.player.getEyePos()) <= range * range;
   }

   private Vec3d aimRayEnd(double effectiveRange) {
      Freecam freecam = Modules.get().get(Freecam.class);
      if (freecam != null && freecam.retargetsCrosshair()) {
         Vec3d start = new Vec3d(freecam.getX(1.0F), freecam.getY(1.0F), freecam.getZ(1.0F));
         Vec3d dir = Vec3d.fromPolar((float)freecam.getPitch(1.0F), (float)freecam.getYaw(1.0F));
         Vec3d toStart = start.subtract(this.mc.player.getEyePos());
         double b = dir.dotProduct(toStart);
         double disc = b * b - (toStart.lengthSquared() - effectiveRange * effectiveRange);
         if (disc < 0.0) {
            return null;
         } else {
            double t = -b + Math.sqrt(disc);
            if (t < 0.0) {
               return null;
            } else {
               BlockHitResult rayHit = this.mc
                  .world
                  .raycast(new RaycastContext(start, start.add(dir.multiply(t)), ShapeType.OUTLINE, FluidHandling.NONE, this.mc.player));
               return rayHit.getType() == Type.BLOCK ? rayHit.getPos().subtract(dir.multiply(0.01)) : start.add(dir.multiply(t));
            }
         }
      } else {
         return this.mc.player.raycast(effectiveRange, 1.0F, false).getPos();
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.render.get() && this.target != null && this.mc.world != null) {
         if (this.heldBlockItem() != null) {
            if (this.mc.world.getBlockState(this.target).isReplaceable()) {
               event.renderer.box(new Box(this.target), this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
            }
         }
      }
   }

   public static enum ScrollModifier {
      Ctrl,
      Alt,
      Shift;

      boolean isHeld() {
         return switch (this) {
            case Ctrl -> Input.isKeyPressed(341) || Input.isKeyPressed(345);
            case Alt -> Input.isKeyPressed(342) || Input.isKeyPressed(346);
            case Shift -> Input.isKeyPressed(340) || Input.isKeyPressed(344);
         };
      }
   }
}
