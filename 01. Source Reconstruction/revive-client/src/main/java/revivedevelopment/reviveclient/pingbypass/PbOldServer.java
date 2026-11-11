package revivedevelopment.reviveclient.pingbypass;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import revivedevelopment.reviveclient.utils.Utils;

public final class PbOldServer {
   public static final int ENCHANTMENT_ATTRIBUTES = 766;
   public static final int OLD_STEP_UP = 766;
   public static final int AXIS_VELOCITY_FLOOR = 769;
   public static final int OLD_SNEAK_EDGE = 769;
   public static final int FLUSH_SNEAK_EDGE = 765;
   public static final int LOW_EYE_IN_WATER = 765;
   public static final int JUMP_SETS_VELOCITY = 767;
   public static final int FIREWORK_ON_BLOCK = 770;
   public static final int LENIENT_PACKET_ERRORS = 765;
   public static final int NEGATIVE_AMPLIFIERS = 765;

   private PbOldServer() {
   }

   public static boolean emulates(Entity entity, int protocol) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return entity != null && mc != null && entity == mc.player && PbVia.emulatesAtMost(protocol);
   }

   public static boolean emulatesMovement(Entity entity, int protocol) {
      MinecraftClient mc = MinecraftClient.getInstance();
      return entity != null && mc != null && mc.player != null
         ? (entity == mc.player || entity == mc.player.getControllingVehicle()) && PbVia.emulatesAtMost(protocol)
         : false;
   }

   public static void tickAttributes(ClientPlayerEntity player) {
      if (emulates(player, 766)) {
         int efficiency = level(player, EquipmentSlot.MAINHAND, Enchantments.EFFICIENCY);
         set(player, EntityAttributes.WATER_MOVEMENT_EFFICIENCY, level(player, EquipmentSlot.FEET, Enchantments.DEPTH_STRIDER) / 3.0);
         setMovementEfficiency(player);
         set(player, EntityAttributes.MINING_EFFICIENCY, efficiency > 0 ? efficiency * efficiency + 1.0 : 0.0);
         set(player, EntityAttributes.SNEAKING_SPEED, 0.3 + level(player, EquipmentSlot.LEGS, Enchantments.SWIFT_SNEAK) * 0.15);
         set(player, EntityAttributes.SUBMERGED_MINING_SPEED, level(player, EquipmentSlot.HEAD, Enchantments.AQUA_AFFINITY) <= 0 ? 0.2 : 1.0);
         set(player, EntityAttributes.ATTACK_KNOCKBACK, level(player, EquipmentSlot.MAINHAND, Enchantments.KNOCKBACK));
      }
   }

   public static void setMovementEfficiency(LivingEntity entity) {
      boolean soulBlock = entity.getEntityWorld().getBlockState(entity.getVelocityAffectingPos()).isIn(BlockTags.SOUL_SPEED_BLOCKS);
      set(entity, EntityAttributes.MOVEMENT_EFFICIENCY, soulBlock && level(entity, EquipmentSlot.FEET, Enchantments.SOUL_SPEED) > 0 ? 1.0 : 0.0);
   }

   private static int level(LivingEntity entity, EquipmentSlot slot, RegistryKey<Enchantment> enchantment) {
      return Utils.getEnchantmentLevel(entity.getEquippedStack(slot), enchantment);
   }

   private static void set(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, double value) {
      EntityAttributeInstance instance = entity.getAttributeInstance(attribute);
      if (instance != null) {
         instance.clearModifiers();
         instance.setBaseValue(value);
      }
   }

   public static Vec3d collide1_20_5(Entity entity, Vec3d movement) {
      World world = entity.getEntityWorld();
      Box box = entity.getBoundingBox();
      List<VoxelShape> collisions = world.getEntityCollisions(entity, box.stretch(movement));
      Vec3d adjusted = movement.lengthSquared() == 0.0 ? movement : Entity.adjustMovementForCollisions(entity, movement, box, world, collisions);
      boolean changedX = movement.x != adjusted.x;
      boolean changedY = movement.y != adjusted.y;
      boolean changedZ = movement.z != adjusted.z;
      boolean mayTouchGround = entity.isOnGround() || changedY && movement.y < 0.0;
      float step = entity.getStepHeight();
      if (step > 0.0F && mayTouchGround && (changedX || changedZ)) {
         Vec3d stepped = Entity.adjustMovementForCollisions(entity, new Vec3d(movement.x, step, movement.z), box, world, collisions);
         Vec3d up = Entity.adjustMovementForCollisions(entity, new Vec3d(0.0, step, 0.0), box.stretch(movement.x, 0.0, movement.z), world, collisions);
         if (up.y < step) {
            Vec3d across = Entity.adjustMovementForCollisions(entity, new Vec3d(movement.x, 0.0, movement.z), box.offset(up), world, collisions).add(up);
            if (across.horizontalLengthSquared() > stepped.horizontalLengthSquared()) {
               stepped = across;
            }
         }

         if (stepped.horizontalLengthSquared() > adjusted.horizontalLengthSquared()) {
            adjusted = stepped.add(
               Entity.adjustMovementForCollisions(entity, new Vec3d(0.0, -stepped.y + movement.y, 0.0), box.offset(stepped), world, collisions)
            );
         }
      }

      return adjusted;
   }

   public static boolean spaceBelowEmpty(LivingEntity player, double offsetX, double offsetZ, double depth, boolean flush) {
      double extra = flush ? 0.0 : 1.0E-5F;
      Box box = player.getBoundingBox();
      return player.getEntityWorld()
         .isSpaceEmpty(player, new Box(box.minX + offsetX, box.minY - depth - extra, box.minZ + offsetZ, box.maxX + offsetX, box.minY, box.maxZ + offsetZ));
   }
}
