package revivedevelopment.reviveclient.systems.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class Velocity extends Module {
   private static final long GRIM_COOLDOWN_MS = 100L;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgKnockback = this.settings.createGroup("Knockback");
   private final SettingGroup sgPush = this.settings.createGroup("Push");
   private final Setting<Velocity.VelocityMode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("How to handle knockback cancellation."))
               .defaultValue(Velocity.VelocityMode.Grim))
            .build()
      );
   private final Setting<Boolean> knockback = this.sgKnockback
      .add(new BoolSetting.Builder().name("knockback").description("Cancel server knockback velocity.").defaultValue(true).build());
   private final Setting<Integer> horizontal = this.sgKnockback
      .add(
         new IntSetting.Builder()
            .name("horizontal")
            .description("Horizontal knockback multiplier. 0% = full cancel.")
            .defaultValue(0)
            .min(0)
            .max(100)
            .sliderRange(0, 100)
            .visible(this.knockback::get)
            .build()
      );
   private final Setting<Integer> vertical = this.sgKnockback
      .add(
         new IntSetting.Builder()
            .name("vertical")
            .description("Vertical knockback multiplier. 0% = full cancel.")
            .defaultValue(0)
            .min(0)
            .max(100)
            .sliderRange(0, 100)
            .visible(this.knockback::get)
            .build()
      );
   private final Setting<Boolean> explosions = this.sgKnockback
      .add(new BoolSetting.Builder().name("explosions").description("Cancel explosion knockback.").defaultValue(true).visible(this.knockback::get).build());
   private final Setting<Boolean> livingEntityKnockback = this.sgKnockback
      .add(new BoolSetting.Builder().name("melee").description("Prevent knockback from melee attacks.").defaultValue(true).visible(this.knockback::get).build());
   private final Setting<Boolean> knockbackPhaseOnly = this.sgKnockback
      .add(
         new BoolSetting.Builder()
            .name("phase-only")
            .description("Only cancel knockback when phased inside a block.")
            .defaultValue(true)
            .visible(this.knockback::get)
            .build()
      );
   private final Setting<Boolean> knockbackPhaseInAir = this.sgKnockback
      .add(
         new BoolSetting.Builder()
            .name("phase-in-air")
            .description("(Good) Don't modify knockback while phased and airborne.")
            .defaultValue(true)
            .visible(() -> this.knockback.get() && this.knockbackPhaseOnly.get())
            .build()
      );
   private final Setting<Boolean> cancelPhasedKnockback = this.sgKnockback
      .add(
         new BoolSetting.Builder()
            .name("phase-cancel-packet")
            .description("(Good) Fully cancel knockback packets while phased — avoids residual rubber-bands.")
            .defaultValue(true)
            .visible(() -> this.knockback.get() && this.knockbackPhaseOnly.get())
            .build()
      );
   private final Setting<Boolean> push = this.sgPush
      .add(new BoolSetting.Builder().name("push").description("Cancel velocity from being pushed by entities, blocks, or liquids.").defaultValue(true).build());
   private final Setting<Boolean> blockPush = this.sgPush
      .add(new BoolSetting.Builder().name("blocks").description("Prevent being pushed out of blocks.").defaultValue(true).visible(this.push::get).build());
   private final Setting<Boolean> entityPush = this.sgPush
      .add(new BoolSetting.Builder().name("entities").description("Prevent entities from pushing you.").defaultValue(true).visible(this.push::get).build());
   private final Setting<Boolean> liquids = this.sgPush
      .add(new BoolSetting.Builder().name("liquids").description("Prevent being pushed by flowing liquids.").defaultValue(true).visible(this.push::get).build());
   private final Setting<Boolean> sinking = this.sgPush
      .add(
         new BoolSetting.Builder()
            .name("sinking")
            .description("Prevent sinking in liquids.")
            .defaultValue(false)
            .visible(() -> this.push.get() && this.liquids.get())
            .build()
      );
   private final Setting<Boolean> pulling = this.sgGeneral
      .add(new BoolSetting.Builder().name("pulling").description("Prevent being pulled by fishing rods.").defaultValue(false).build());
   private boolean ncpPending;
   private long lastGrimCancelTime;

   public Velocity() {
      super(Categories.Combat, "velocity", "Prevents you from being moved by external forces.");
   }

   @Override
   public void onDeactivate() {
      this.ncpPending = false;
      this.lastGrimCancelTime = 0L;
   }

   public Velocity.VelocityMode getMode() {
      return this.mode.get();
   }

   public void flagNcpPending() {
      this.ncpPending = true;
   }

   public boolean isGrimCooldownReady() {
      return System.currentTimeMillis() - this.lastGrimCancelTime >= 100L;
   }

   public void stampGrimCancel() {
      this.lastGrimCancelTime = System.currentTimeMillis();
   }

   public void onHurtAnimation() {
      if (this.mode.get() == Velocity.VelocityMode.GrimV3) {
         if (this.isPlayerPhased()) {
            ClientPlayerEntity player = this.mc.player;
            if (player != null && this.mc.getNetworkHandler() != null) {
               this.mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, false)));
               this.mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, false, true)));
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      ClientPlayerEntity player = this.mc.player;
      if (player != null) {
         if (this.ncpPending && this.mode.get() == Velocity.VelocityMode.NCP) {
            if (this.mc.getNetworkHandler() != null) {
               this.mc
                  .getNetworkHandler()
                  .sendPacket(
                     new Full(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), player.isOnGround(), player.horizontalCollision)
                  );
               this.mc
                  .getNetworkHandler()
                  .sendPacket(
                     new PlayerActionC2SPacket(
                        Action.STOP_DESTROY_BLOCK, player.isSneaking() ? player.getBlockPos() : player.getBlockPos().up(), Direction.DOWN
                     )
                  );
            }

            this.ncpPending = false;
         }

         if (this.mode.get() == Velocity.VelocityMode.Good
            && this.knockback.get()
            && this.knockbackPhaseOnly.get()
            && this.cancelPhasedKnockback.get()
            && this.isPlayerPhased()) {
            player.setVelocity(0.0, 0.0, 0.0);
         }

         if (this.sinking.get() && this.push.get() && this.liquids.get()) {
            if (!this.mc.options.jumpKey.isPressed() && !this.mc.options.sneakKey.isPressed()) {
               if ((player.isTouchingWater() || player.isInLava()) && player.getVelocity().y < 0.0) {
                  BlockPos feetPos = player.getBlockPos();
                  if (this.mc.world.getBlockState(feetPos).isOf(Blocks.BUBBLE_COLUMN) || this.mc.world.getBlockState(feetPos.down()).isOf(Blocks.BUBBLE_COLUMN)
                     )
                   {
                     return;
                  }

                  player.setVelocity(player.getVelocity().multiply(1.0, 0.0, 1.0));
               }
            }
         }
      }
   }

   public boolean shouldCancelKnockback() {
      if (!this.isActive() || !this.knockback.get()) {
         return false;
      } else {
         return this.knockbackPhaseOnly.get() ? this.isPlayerPhased() : true;
      }
   }

   public boolean isKnockbackEnabled() {
      return this.knockback.get();
   }

   public boolean isKnockbackPhaseOnly() {
      return this.knockbackPhaseOnly.get();
   }

   public boolean isKnockbackPhaseInAir() {
      return this.knockbackPhaseInAir.get();
   }

   public boolean isCancelPhasedKnockback() {
      return this.cancelPhasedKnockback.get();
   }

   public boolean shouldCancelExplosions() {
      return this.explosions.get() && this.shouldCancelKnockback();
   }

   public boolean shouldCancelEntityPush() {
      return this.isActive() && this.entityPush.get() && this.push.get();
   }

   public boolean shouldCancelBlockPush() {
      return this.isActive() && this.blockPush.get() && this.push.get();
   }

   public boolean shouldCancelLiquids() {
      if (!this.isActive() || !this.liquids.get() || !this.push.get()) {
         return false;
      } else {
         return this.isPlayerPhased() ? true : !this.isMovementKeyPressed();
      }
   }

   private boolean isMovementKeyPressed() {
      return this.mc.options.forwardKey.isPressed()
         || this.mc.options.backKey.isPressed()
         || this.mc.options.leftKey.isPressed()
         || this.mc.options.rightKey.isPressed()
         || this.mc.options.jumpKey.isPressed()
         || this.mc.options.sneakKey.isPressed();
   }

   public boolean shouldCancelLivingEntityKnockback() {
      return this.livingEntityKnockback.get() && this.shouldCancelKnockback();
   }

   public boolean shouldCancelPulling() {
      return this.isActive() && this.pulling.get();
   }

   public double getHorizontalMultiplier() {
      return this.horizontal.get().intValue() / 100.0;
   }

   public double getVerticalMultiplier() {
      return this.vertical.get().intValue() / 100.0;
   }

   public boolean isFullCancel() {
      return this.horizontal.get() == 0 && this.vertical.get() == 0;
   }

   public boolean isPlayerPhased() {
      ClientPlayerEntity player = this.mc.player;
      ClientWorld world = this.mc.world;
      if (player != null && world != null) {
         Box raw = player.getBoundingBox();
         Box box = new Box(raw.minX + 0.001, raw.minY + 0.001, raw.minZ + 0.001, raw.maxX - 0.001, raw.maxY - 0.001, raw.maxZ - 0.001);
         VoxelShape playerShape = VoxelShapes.cuboid(box);
         int minX = (int)Math.floor(box.minX);
         int minY = (int)Math.floor(box.minY);
         int minZ = (int)Math.floor(box.minZ);
         int maxX = (int)Math.floor(box.maxX);
         int maxY = (int)Math.floor(box.maxY);
         int maxZ = (int)Math.floor(box.maxZ);

         for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
               for (int bz = minZ; bz <= maxZ; bz++) {
                  BlockPos bp = new BlockPos(bx, by, bz);
                  BlockState state = world.getBlockState(bp);
                  if (!state.isAir()) {
                     VoxelShape shape = state.getCollisionShape(world, bp);
                     if (!shape.isEmpty()) {
                        VoxelShape moved = shape.offset(bx, by, bz);
                        if (VoxelShapes.matchesAnywhere(playerShape, moved, BooleanBiFunction.AND)) {
                           return true;
                        }
                     }
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean isCollidingWithEntities() {
      ClientPlayerEntity player = this.mc.player;
      ClientWorld world = this.mc.world;
      return player != null && world != null ? !world.getOtherEntities(player, player.getBoundingBox()).isEmpty() : false;
   }

   public static enum VelocityMode {
      Cancel,
      Good,
      Grim,
      GrimV3,
      NCP;
   }
}
