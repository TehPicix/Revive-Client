package revivedevelopment.reviveclient.utils.player;

import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IVec3d;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.misc.text.TextUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.world.Dimension;

public class PlayerUtils {
   private static final Color color = new Color();

   private PlayerUtils() {
   }

   public static Color getPlayerColor(PlayerEntity entity, Color defaultColor) {
      if (Friends.get().isFriend(entity)) {
         return color.set(Config.get().friendColor.get()).a(defaultColor.a);
      } else {
         return Config.get().useTeamColor.get() && !color.set(TextUtils.getMostPopularColor(entity.getDisplayName())).equals(Utils.WHITE)
            ? color.a(defaultColor.a)
            : defaultColor;
      }
   }

   public static boolean canSeeEntity(Entity entity) {
      Vec3d vec1 = new Vec3d(0.0, 0.0, 0.0);
      Vec3d vec2 = new Vec3d(0.0, 0.0, 0.0);
      ((IVec3d)vec1)
         .revive$set(
            ReviveClient.mc.player.getX(), ReviveClient.mc.player.getY() + ReviveClient.mc.player.getStandingEyeHeight(), ReviveClient.mc.player.getZ()
         );
      ((IVec3d)vec2).revive$set(entity.getX(), entity.getY(), entity.getZ());
      boolean canSeeFeet = ReviveClient.mc
            .world
            .raycast(new RaycastContext(vec1, vec2, ShapeType.COLLIDER, FluidHandling.NONE, ReviveClient.mc.player))
            .getType()
         == Type.MISS;
      ((IVec3d)vec2).revive$set(entity.getX(), entity.getY() + entity.getStandingEyeHeight(), entity.getZ());
      boolean canSeeEyes = ReviveClient.mc
            .world
            .raycast(new RaycastContext(vec1, vec2, ShapeType.COLLIDER, FluidHandling.NONE, ReviveClient.mc.player))
            .getType()
         == Type.MISS;
      return canSeeFeet || canSeeEyes;
   }

   public static boolean shouldPause(boolean ifBreaking, boolean ifEating, boolean ifDrinking) {
      if (ifBreaking && ReviveClient.mc.interactionManager.isBreakingBlock()) {
         return true;
      } else {
         return !ifEating
               || !ReviveClient.mc.player.isUsingItem()
               || !ReviveClient.mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD)
                  && !ReviveClient.mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD)
            ? ifDrinking
               && ReviveClient.mc.player.isUsingItem()
               && (
                  ReviveClient.mc.player.getMainHandStack().getItem() instanceof PotionItem
                     || ReviveClient.mc.player.getOffHandStack().getItem() instanceof PotionItem
               )
            : true;
      }
   }

   public static boolean isMoving() {
      return ReviveClient.mc.player.forwardSpeed != 0.0F || ReviveClient.mc.player.sidewaysSpeed != 0.0F;
   }

   public static boolean isSprinting() {
      return ReviveClient.mc.player.isSprinting() && (ReviveClient.mc.player.forwardSpeed != 0.0F || ReviveClient.mc.player.sidewaysSpeed != 0.0F);
   }

   public static float possibleHealthReductions() {
      return possibleHealthReductions(true, true);
   }

   public static float possibleHealthReductions(boolean entities, boolean fall) {
      float damageTaken = 0.0F;
      if (entities) {
         for (Entity entity : ReviveClient.mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity) {
               float crystalDamage = DamageUtil.crystalDamage(ReviveClient.mc.player, entity.getEntityPos());
               if (crystalDamage > damageTaken) {
                  damageTaken = crystalDamage;
               }
            } else if (entity instanceof PlayerEntity player && !Friends.get().isFriend(player) && isWithin(entity, 5.0)) {
               float attackDamage = DamageUtil.getAttackDamage(player, ReviveClient.mc.player);
               if (attackDamage > damageTaken) {
                  damageTaken = attackDamage;
               }
            }
         }

         if (getDimension() != Dimension.Overworld) {
            for (BlockEntity blockEntity : Utils.blockEntities()) {
               BlockPos bp = blockEntity.getPos();
               Vec3d pos = new Vec3d(bp.getX(), bp.getY(), bp.getZ());
               if (blockEntity instanceof BedBlockEntity) {
                  float explosionDamage = DamageUtil.bedDamage(ReviveClient.mc.player, pos);
                  if (explosionDamage > damageTaken) {
                     damageTaken = explosionDamage;
                  }
               }
            }
         }
      }

      if (fall && ReviveClient.mc.player.fallDistance > 3.0) {
         float damage = DamageUtil.fallDamage(ReviveClient.mc.player);
         if (damage > damageTaken && !EntityUtils.isAboveWater(ReviveClient.mc.player)) {
            damageTaken = damage;
         }
      }

      return damageTaken;
   }

   public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
      return Math.sqrt(squaredDistance(x1, y1, z1, x2, y2, z2));
   }

   public static double distanceTo(Entity entity) {
      return distanceTo(entity.getX(), entity.getY(), entity.getZ());
   }

   public static double distanceTo(BlockPos blockPos) {
      return distanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public static double distanceTo(Vec3d vec3d) {
      return distanceTo(vec3d.getX(), vec3d.getY(), vec3d.getZ());
   }

   public static double distanceTo(double x, double y, double z) {
      return Math.sqrt(squaredDistanceTo(x, y, z));
   }

   public static double squaredDistanceTo(Entity entity) {
      return squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ());
   }

   public static double squaredDistanceTo(BlockPos blockPos) {
      return squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public static double squaredDistanceTo(double x, double y, double z) {
      return squaredDistance(ReviveClient.mc.player.getX(), ReviveClient.mc.player.getY(), ReviveClient.mc.player.getZ(), x, y, z);
   }

   public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
      double f = x1 - x2;
      double g = y1 - y2;
      double h = z1 - z2;
      return org.joml.Math.fma(f, f, org.joml.Math.fma(g, g, h * h));
   }

   public static boolean isWithin(Entity entity, double r) {
      return squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ()) <= r * r;
   }

   public static boolean isWithin(Vec3d vec3d, double r) {
      return squaredDistanceTo(vec3d.getX(), vec3d.getY(), vec3d.getZ()) <= r * r;
   }

   public static boolean isWithin(BlockPos blockPos, double r) {
      return squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ()) <= r * r;
   }

   public static boolean isWithin(double x, double y, double z, double r) {
      return squaredDistanceTo(x, y, z) <= r * r;
   }

   public static double distanceToCamera(double x, double y, double z) {
      return Math.sqrt(squaredDistanceToCamera(x, y, z));
   }

   public static double distanceToCamera(Entity entity) {
      return distanceToCamera(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
   }

   public static double squaredDistanceToCamera(double x, double y, double z) {
      Vec3d cameraPos = ReviveClient.mc.gameRenderer.getCamera().getCameraPos();
      return squaredDistance(cameraPos.x, cameraPos.y, cameraPos.z, x, y, z);
   }

   public static double squaredDistanceToCamera(Entity entity) {
      return squaredDistanceToCamera(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
   }

   public static boolean isWithinCamera(Entity entity, double r) {
      return squaredDistanceToCamera(entity.getX(), entity.getY(), entity.getZ()) <= r * r;
   }

   public static boolean isWithinCamera(Vec3d vec3d, double r) {
      return squaredDistanceToCamera(vec3d.getX(), vec3d.getY(), vec3d.getZ()) <= r * r;
   }

   public static boolean isWithinCamera(BlockPos blockPos, double r) {
      return squaredDistanceToCamera(blockPos.getX(), blockPos.getY(), blockPos.getZ()) <= r * r;
   }

   public static boolean isWithinCamera(double x, double y, double z, double r) {
      return squaredDistanceToCamera(x, y, z) <= r * r;
   }

   public static boolean isWithinReach(Entity entity) {
      return isWithinReach(entity.getX(), entity.getY(), entity.getZ());
   }

   public static boolean isWithinReach(Vec3d vec3d) {
      return isWithinReach(vec3d.getX(), vec3d.getY(), vec3d.getZ());
   }

   public static boolean isWithinReach(BlockPos blockPos) {
      return isWithinReach(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public static boolean isWithinReach(double x, double y, double z) {
      return squaredDistance(ReviveClient.mc.player.getX(), ReviveClient.mc.player.getEyeY(), ReviveClient.mc.player.getZ(), x, y, z)
         <= ReviveClient.mc.player.getBlockInteractionRange() * ReviveClient.mc.player.getBlockInteractionRange();
   }

   public static Dimension getDimension() {
      if (ReviveClient.mc.world == null) {
         return Dimension.Overworld;
      } else {
         String var0 = ReviveClient.mc.world.getRegistryKey().getValue().getPath();

         return switch (var0) {
            case "the_nether" -> Dimension.Nether;
            case "the_end" -> Dimension.End;
            default -> Dimension.Overworld;
         };
      }
   }

   public static GameMode getGameMode() {
      if (ReviveClient.mc.player == null) {
         return null;
      } else {
         PlayerListEntry playerListEntry = ReviveClient.mc.getNetworkHandler().getPlayerListEntry(ReviveClient.mc.player.getUuid());
         return playerListEntry == null ? null : playerListEntry.getGameMode();
      }
   }

   public static float getTotalHealth() {
      return ReviveClient.mc.player.getHealth() + ReviveClient.mc.player.getAbsorptionAmount();
   }

   public static boolean isAlive() {
      return ReviveClient.mc.player.isAlive() && !ReviveClient.mc.player.isDead();
   }

   public static int getPing() {
      if (ReviveClient.mc.getNetworkHandler() == null) {
         return 0;
      } else {
         PlayerListEntry playerListEntry = ReviveClient.mc.getNetworkHandler().getPlayerListEntry(ReviveClient.mc.player.getUuid());
         return playerListEntry == null ? 0 : playerListEntry.getLatency();
      }
   }

   public static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
      double d = movementInput.lengthSquared();
      if (d < 1.0E-7) {
         return Vec3d.ZERO;
      } else {
         Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
         float f = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
         float g = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
         return new Vec3d(vec3d.x * g - vec3d.z * f, vec3d.y, vec3d.z * g + vec3d.x * f);
      }
   }

   public static boolean silentSwapEquipChestplate() {
      if (!ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.DIAMOND_CHESTPLATE)
         && !ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.NETHERITE_CHESTPLATE)) {
         FindItemResult hotbarChestplateSlot = InvUtils.findInHotbar(Items.NETHERITE_CHESTPLATE);
         if (!hotbarChestplateSlot.found()) {
            hotbarChestplateSlot = InvUtils.findInHotbar(Items.DIAMOND_CHESTPLATE);
         }

         if (hotbarChestplateSlot.found()) {
            ReviveClient.mc
               .interactionManager
               .clickSlot(ReviveClient.mc.player.playerScreenHandler.syncId, 6, hotbarChestplateSlot.slot(), SlotActionType.SWAP, ReviveClient.mc.player);
            return true;
         } else {
            FindItemResult inventorySlot = InvUtils.find(Items.NETHERITE_CHESTPLATE);
            if (!inventorySlot.found()) {
               inventorySlot = InvUtils.find(Items.DIAMOND_CHESTPLATE);
            }

            if (!inventorySlot.found()) {
               return false;
            } else {
               InvUtils.move().from(inventorySlot.slot()).toArmor(2);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean silentSwapEquipElytra() {
      if (ReviveClient.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA)) {
         return false;
      } else {
         FindItemResult inventorySlot = InvUtils.findInHotbar(Items.ELYTRA);
         if (inventorySlot.found()) {
            ReviveClient.mc
               .interactionManager
               .clickSlot(ReviveClient.mc.player.playerScreenHandler.syncId, 6, inventorySlot.slot(), SlotActionType.SWAP, ReviveClient.mc.player);
            return true;
         } else {
            inventorySlot = InvUtils.find(Items.ELYTRA);
            if (!inventorySlot.found()) {
               return false;
            } else if (!inventorySlot.isHotbar()) {
               InvUtils.move().from(inventorySlot.slot()).toArmor(2);
               return true;
            } else {
               FindItemResult hotbarSlot = InvUtils.findInHotbar(x -> x.getItem() != Items.TOTEM_OF_UNDYING);
               ReviveClient.mc
                  .interactionManager
                  .clickSlot(
                     ReviveClient.mc.player.playerScreenHandler.syncId,
                     inventorySlot.slot(),
                     hotbarSlot.found() ? hotbarSlot.slot() : 0,
                     SlotActionType.SWAP,
                     ReviveClient.mc.player
                  );
               ReviveClient.mc
                  .interactionManager
                  .clickSlot(
                     ReviveClient.mc.player.playerScreenHandler.syncId,
                     6,
                     hotbarSlot.found() ? hotbarSlot.slot() : 0,
                     SlotActionType.SWAP,
                     ReviveClient.mc.player
                  );
               ReviveClient.mc
                  .interactionManager
                  .clickSlot(
                     ReviveClient.mc.player.playerScreenHandler.syncId,
                     inventorySlot.slot(),
                     hotbarSlot.found() ? hotbarSlot.slot() : 0,
                     SlotActionType.SWAP,
                     ReviveClient.mc.player
                  );
               return true;
            }
         }
      }
   }

   public static boolean isPlayerPhased() {
      return ReviveClient.mc.world.getBlockCollisions(ReviveClient.mc.player, ReviveClient.mc.player.getBoundingBox()).iterator().hasNext();
   }

   public static boolean isPlayerPhased(PlayerEntity player) {
      return ReviveClient.mc.world.getBlockCollisions(player, player.getBoundingBox()).iterator().hasNext();
   }
}
