package revivedevelopment.reviveclient.systems.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class Step extends Module {
   private static final Identifier MODIFIER_ID = Identifier.of("revive-client", "step");
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Step.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description(
                     "Jump is a real jump and safe on movement anticheats; Attribute is instant but rubberbands on servers that simulate movement (Grim etc)."
                  ))
               .defaultValue(Step.Mode.Jump))
            .build()
      );
   private final Setting<Double> stepHeight = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("step-height")
            .description("How high you can step up, in blocks.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderRange(0.0, 4.0)
            .visible(() -> this.mode.get() == Step.Mode.Attribute)
            .build()
      );
   private final Setting<Boolean> onlyOnGround = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Only steps while standing on the ground.")
            .defaultValue(false)
            .visible(() -> this.mode.get() == Step.Mode.Attribute)
            .build()
      );
   private final Setting<Double> lookahead = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("lookahead")
            .description("How far ahead of you a step is detected, in blocks, so the jump starts before you touch it.")
            .defaultValue(0.8)
            .min(0.0)
            .sliderRange(0.0, 2.0)
            .visible(() -> this.mode.get() == Step.Mode.Jump)
            .build()
      );
   private boolean jumpPressed;

   public Step() {
      super(Categories.Movement, "step", "Allows you to walk up full blocks instantly.");
   }

   @Override
   public void onDeactivate() {
      this.removeModifier();
      this.releaseJump();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null) {
         if (this.mode.get() == Step.Mode.Jump) {
            this.removeModifier();
            this.tickJump();
         } else {
            this.releaseJump();
            this.tickAttribute();
         }
      }
   }

   private void tickJump() {
      if (this.mc.player.isOnGround()
         && !this.mc.player.isTouchingWater()
         && !this.mc.player.isSneaking()
         && (!this.mc.options.jumpKey.isPressed() || this.jumpPressed)) {
         Vec3d dir = this.movementVector();
         if (dir == null) {
            this.releaseJump();
         } else {
            BlockPos feet = this.mc.player.getBlockPos();
            double maxDist = 0.3 + this.lookahead.get();

            for (double d = 0.05; d <= maxDist; d += 0.1) {
               BlockPos ahead = BlockPos.ofFloored(this.mc.player.getX() + dir.x * d, this.mc.player.getY(), this.mc.player.getZ() + dir.z * d);
               if (!ahead.equals(feet) && !this.mc.world.getBlockState(ahead).getCollisionShape(this.mc.world, ahead).isEmpty()) {
                  if (this.isSteppable(ahead)) {
                     this.mc.options.jumpKey.setPressed(true);
                     this.jumpPressed = true;
                     return;
                  }
                  break;
               }
            }

            this.releaseJump();
         }
      } else {
         this.releaseJump();
      }
   }

   private Vec3d movementVector() {
      int forward = (this.mc.options.forwardKey.isPressed() ? 1 : 0) - (this.mc.options.backKey.isPressed() ? 1 : 0);
      int sideways = (this.mc.options.leftKey.isPressed() ? 1 : 0) - (this.mc.options.rightKey.isPressed() ? 1 : 0);
      if (forward == 0 && sideways == 0) {
         return null;
      } else {
         double yaw = Math.toRadians(this.mc.player.getYaw());
         double x = -Math.sin(yaw) * forward + Math.cos(yaw) * sideways;
         double z = Math.cos(yaw) * forward + Math.sin(yaw) * sideways;
         return new Vec3d(x, 0.0, z).normalize();
      }
   }

   private boolean isSteppable(BlockPos pos) {
      return !this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos).isEmpty()
         && this.mc.world.getBlockState(pos.up()).getCollisionShape(this.mc.world, pos.up()).isEmpty()
         && this.mc.world.getBlockState(pos.up(2)).getCollisionShape(this.mc.world, pos.up(2)).isEmpty();
   }

   private void releaseJump() {
      if (this.jumpPressed) {
         this.mc.options.jumpKey.setPressed(false);
         this.jumpPressed = false;
      }
   }

   private void tickAttribute() {
      if (this.onlyOnGround.get() && !this.mc.player.isOnGround()) {
         this.removeModifier();
      } else {
         EntityAttributeInstance attribute = this.mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
         if (attribute != null) {
            double amount = this.stepHeight.get() - attribute.getBaseValue();
            EntityAttributeModifier existing = attribute.getModifier(MODIFIER_ID);
            if (existing != null) {
               if (existing.value() == amount) {
                  return;
               }

               attribute.removeModifier(MODIFIER_ID);
            }

            attribute.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID, amount, Operation.ADD_VALUE));
         }
      }
   }

   private void removeModifier() {
      if (this.mc.player != null) {
         EntityAttributeInstance attribute = this.mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
         if (attribute != null) {
            attribute.removeModifier(MODIFIER_ID);
         }
      }
   }

   public static enum Mode {
      Jump,
      Attribute;
   }
}
