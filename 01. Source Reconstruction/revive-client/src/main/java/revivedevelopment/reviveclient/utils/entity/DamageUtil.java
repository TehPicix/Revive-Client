package revivedevelopment.reviveclient.utils.entity;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collection;
import java.util.function.BiFunction;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;

public final class DamageUtil {
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   public static final DamageUtil.BlockRaycastProvider BLOCK_CHECK = (ctx, pos) -> {
      BlockState state = ReviveClient.mc.world.getBlockState(pos);
      return state.getCollisionShape(ReviveClient.mc.world, pos).raycast(ctx.start(), ctx.end(), pos);
   };

   private DamageUtil() {
   }

   public static DamageUtil.BlockRaycastProvider blockCheck(BlockView world) {
      return (ctx, pos) -> {
         BlockState state = world.getBlockState(pos);
         return state.getCollisionShape(world, pos).raycast(ctx.start(), ctx.end(), pos);
      };
   }

   public static float crystalDamage(
      LivingEntity target,
      Vec3d targetPos,
      Box targetBox,
      Vec3d explosionPos,
      DamageUtil.BlockRaycastProvider provider,
      boolean assumeBestArmor,
      Collection<BlockPos> ignoredBlocks
   ) {
      return crystalDamage(explosionProfile(target, assumeBestArmor), targetPos, targetBox, explosionPos, provider, ignoredBlocks);
   }

   public static float crystalDamage(
      LivingEntity target, Vec3d targetPos, Box targetBox, Vec3d explosionPos, DamageUtil.BlockRaycastProvider provider, boolean assumeBestArmor
   ) {
      return crystalDamage(target, targetPos, targetBox, explosionPos, provider, assumeBestArmor, null);
   }

   public static float crystalDamage(
      DamageUtil.DamageProfile profile,
      Vec3d targetPos,
      Box targetBox,
      Vec3d explosionPos,
      DamageUtil.BlockRaycastProvider provider,
      Collection<BlockPos> ignoredBlocks
   ) {
      float raw = rawExplosionDamage(targetPos, targetBox, explosionPos, 12.0F, wrapIgnored(provider, ignoredBlocks));
      return raw <= 0.0F ? 0.0F : profile.reduce(raw);
   }

   public static float bedDamage(
      LivingEntity target, Vec3d targetPos, Box targetBox, Vec3d explosionPos, DamageUtil.BlockRaycastProvider provider, boolean assumeBestArmor
   ) {
      float raw = rawExplosionDamage(targetPos, targetBox, explosionPos, 10.0F, provider);
      return raw <= 0.0F ? 0.0F : explosionProfile(target, assumeBestArmor).reduce(raw);
   }

   public static float explosionDamage(
      LivingEntity target, Vec3d targetPos, Box targetBox, Vec3d explosionPos, float radius, DamageUtil.BlockRaycastProvider provider, boolean assumeBestArmor
   ) {
      float diameter = Math.max(radius * 2.0F, 0.0F);
      float raw = rawExplosionDamage(targetPos, targetBox, explosionPos, diameter, provider);
      return raw <= 0.0F ? 0.0F : explosionProfile(target, assumeBestArmor).reduce(raw);
   }

   public static float crystalDamage(LivingEntity target, Vec3d explosionPos) {
      return isImmune(target) ? 0.0F : crystalDamage(target, target.getEntityPos(), target.getBoundingBox(), explosionPos, BLOCK_CHECK, false);
   }

   public static float bedDamage(LivingEntity target, Vec3d explosionPos) {
      return isImmune(target) ? 0.0F : bedDamage(target, target.getEntityPos(), target.getBoundingBox(), explosionPos, BLOCK_CHECK, false);
   }

   private static boolean isImmune(LivingEntity target) {
      return target != null && ReviveClient.mc.world != null
         ? target instanceof PlayerEntity player && !(player instanceof FakePlayerEntity) && EntityUtils.getGameMode(player) == GameMode.CREATIVE
         : true;
   }

   private static float rawExplosionDamage(Vec3d targetPos, Box targetBox, Vec3d explosionPos, float diameter, DamageUtil.BlockRaycastProvider provider) {
      if (diameter <= 0.0F) {
         return 0.0F;
      } else {
         double distance = targetPos.distanceTo(explosionPos);
         if (distance > diameter) {
            return 0.0F;
         } else {
            double exposure = calculateExposure(explosionPos, targetBox, provider);
            double impact = (1.0 - distance / diameter) * exposure;
            return impact <= 0.0 ? 0.0F : (float)((impact * impact + impact) / 2.0 * 7.0 * diameter + 1.0);
         }
      }
   }

   private static DamageUtil.BlockRaycastProvider wrapIgnored(DamageUtil.BlockRaycastProvider provider, Collection<BlockPos> ignored) {
      return ignored != null && !ignored.isEmpty() ? (ctx, pos) -> ignored.contains(pos) ? null : provider.apply(ctx, pos) : provider;
   }

   public static DamageUtil.DamageProfile explosionProfile(LivingEntity target, boolean assumeBestArmor) {
      StatusEffectInstance resistance = target.getStatusEffect(StatusEffects.RESISTANCE);
      int protection = 0;

      for (EquipmentSlot slot : ARMOR_SLOTS) {
         ItemStack stack = target.getEquippedStack(slot);
         if (!stack.isEmpty()) {
            if (assumeBestArmor) {
               protection += slot == EquipmentSlot.LEGS ? 8 : 4;
            } else {
               protection += Utils.getEnchantmentLevel(stack, Enchantments.PROTECTION);
               protection += 2 * Utils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION);
            }
         }
      }

      return new DamageUtil.DamageProfile(
         target instanceof PlayerEntity && ReviveClient.mc.world != null ? ReviveClient.mc.world.getDifficulty() : null,
         (float)Math.floor(target.getAttributeValue(EntityAttributes.ARMOR)),
         (float)target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS),
         resistance != null ? resistance.getAmplifier() : -1,
         protection
      );
   }

   public static float applyReductions(float damage, Entity entity, DamageSource source, boolean assumeBestArmor) {
      if (source.isScaledWithDifficulty()) {
         switch (ReviveClient.mc.world.getDifficulty()) {
            case EASY:
               damage = Math.min(damage / 2.0F + 1.0F, damage);
               break;
            case HARD:
               damage *= 1.5F;
         }
      }

      if (entity instanceof LivingEntity living) {
         damage = net.minecraft.entity.DamageUtil.getDamageLeft(
            living,
            damage,
            source,
            (float)Math.floor(living.getAttributeValue(EntityAttributes.ARMOR)),
            (float)living.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS)
         );
         damage = reduceByResistance(living, damage);
         damage = reduceByProtection(living, damage, source, assumeBestArmor);
         return Math.max(damage, 0.0F);
      } else {
         return Math.max(damage, 0.0F);
      }
   }

   private static float reduceByProtection(LivingEntity entity, float damage, DamageSource source, boolean assumeBestArmor) {
      if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         return damage;
      } else {
         int totalProtection = 0;
         if (assumeBestArmor) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
               ItemStack stack = entity.getEquippedStack(slot);
               if (!stack.isEmpty()) {
                  if (slot != EquipmentSlot.LEGS) {
                     totalProtection += 4;
                  }

                  if (slot == EquipmentSlot.LEGS && source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                     totalProtection += 8;
                  }
               }
            }

            return net.minecraft.entity.DamageUtil.getInflictedDamage(damage, totalProtection);
         } else {
            for (EquipmentSlot slotx : ARMOR_SLOTS) {
               ItemStack stack = entity.getEquippedStack(slotx);
               int prot = Utils.getEnchantmentLevel(stack, Enchantments.PROTECTION);
               if (prot > 0) {
                  totalProtection += prot;
               }

               if (source.isIn(DamageTypeTags.IS_FIRE)) {
                  totalProtection += 2 * Utils.getEnchantmentLevel(stack, Enchantments.FIRE_PROTECTION);
               }

               if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
                  totalProtection += 2 * Utils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION);
               }

               if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
                  totalProtection += 2 * Utils.getEnchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION);
               }

               if (source.isIn(DamageTypeTags.IS_FALL)) {
                  totalProtection += 3 * Utils.getEnchantmentLevel(stack, Enchantments.FEATHER_FALLING);
               }
            }

            return net.minecraft.entity.DamageUtil.getInflictedDamage(damage, totalProtection);
         }
      }
   }

   private static float reduceByResistance(LivingEntity entity, float damage) {
      StatusEffectInstance resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
      if (resistance != null) {
         damage *= 1.0F - 0.2F * (resistance.getAmplifier() + 1);
      }

      return Math.max(damage, 0.0F);
   }

   public static float getAttackDamage(LivingEntity attacker, Entity target) {
      float itemDamage = (float)attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
      DamageSource damageSource = attacker instanceof PlayerEntity player
         ? ReviveClient.mc.world.getDamageSources().playerAttack(player)
         : ReviveClient.mc.world.getDamageSources().mobAttack(attacker);
      float damage = modifyAttackDamage(attacker, target, attacker.getWeaponStack(), damageSource, itemDamage);
      return applyReductions(damage, target, damageSource, false);
   }

   private static float modifyAttackDamage(LivingEntity attacker, Entity target, ItemStack weapon, DamageSource damageSource, float damage) {
      Object2IntMap<RegistryEntry<Enchantment>> enchantments = new Object2IntOpenHashMap();
      Utils.getEnchantments(weapon, enchantments);
      float enchantDamage = 0.0F;
      int sharpness = Utils.getEnchantmentLevel(enchantments, Enchantments.SHARPNESS);
      if (sharpness > 0) {
         enchantDamage += 1.0F + 0.5F * (sharpness - 1);
      }

      int bane = Utils.getEnchantmentLevel(enchantments, Enchantments.BANE_OF_ARTHROPODS);
      if (bane > 0 && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
         enchantDamage += 2.5F * bane;
      }

      int impaling = Utils.getEnchantmentLevel(enchantments, Enchantments.IMPALING);
      if (impaling > 0 && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_IMPALING)) {
         enchantDamage += 2.5F * impaling;
      }

      int smite = Utils.getEnchantmentLevel(enchantments, Enchantments.SMITE);
      if (smite > 0 && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_SMITE)) {
         enchantDamage += 2.5F * smite;
      }

      if (attacker instanceof PlayerEntity playerEntity) {
         float charge = playerEntity.getAttackCooldownProgress(0.5F);
         damage *= 0.2F + charge * charge * 0.8F;
         enchantDamage *= charge;
         if (weapon.getItem() instanceof MaceItem item) {
            float bonusDamage = item.getBonusAttackDamage(target, damage, damageSource);
            if (bonusDamage > 0.0F) {
               int density = Utils.getEnchantmentLevel(weapon, Enchantments.DENSITY);
               if (density > 0) {
                  bonusDamage += (float)(0.5 * attacker.fallDistance);
               }

               damage += bonusDamage;
            }
         }

         if (charge > 0.9F
            && attacker.fallDistance > 0.0
            && !attacker.isOnGround()
            && !attacker.isClimbing()
            && !attacker.isTouchingWater()
            && !attacker.hasStatusEffect(StatusEffects.BLINDNESS)
            && !attacker.hasVehicle()) {
            damage *= 1.5F;
         }
      }

      return damage + enchantDamage;
   }

   public static float fallDamage(LivingEntity entity) {
      if (entity instanceof PlayerEntity player && player.getAbilities().flying) {
         return 0.0F;
      } else if (!entity.hasStatusEffect(StatusEffects.SLOW_FALLING) && !entity.hasStatusEffect(StatusEffects.LEVITATION)) {
         int surface = ReviveClient.mc
            .world
            .getWorldChunk(entity.getBlockPos())
            .getHeightmap(Type.MOTION_BLOCKING)
            .get(entity.getBlockX() & 15, entity.getBlockZ() & 15);
         if (entity.getBlockY() >= surface) {
            return fallDamageReductions(entity, surface);
         } else {
            BlockHitResult hit = ReviveClient.mc
               .world
               .raycast(
                  new RaycastContext(
                     entity.getEntityPos(),
                     new Vec3d(entity.getX(), ReviveClient.mc.world.getBottomY(), entity.getZ()),
                     ShapeType.COLLIDER,
                     FluidHandling.WATER,
                     entity
                  )
               );
            return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? 0.0F : fallDamageReductions(entity, hit.getBlockPos().getY());
         }
      } else {
         return 0.0F;
      }
   }

   private static float fallDamageReductions(LivingEntity entity, int surface) {
      int fallHeight = (int)(entity.getY() - surface + entity.fallDistance - 3.0);
      StatusEffectInstance jumpBoost = entity.getStatusEffect(StatusEffects.JUMP_BOOST);
      if (jumpBoost != null) {
         fallHeight -= jumpBoost.getAmplifier() + 1;
      }

      return applyReductions(fallHeight, entity, ReviveClient.mc.world.getDamageSources().fall(), false);
   }

   public static float calculateExposure(Vec3d source, Box box, DamageUtil.BlockRaycastProvider provider) {
      double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
      double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
      double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
      if (!(stepX < 0.0) && !(stepY < 0.0) && !(stepZ < 0.0)) {
         double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
         double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
         int misses = 0;
         int total = 0;
         DamageUtil.ExposureContext ctx = new DamageUtil.ExposureContext(Vec3d.ZERO, Vec3d.ZERO);

         for (double k = 0.0; k <= 1.0; k += stepX) {
            for (double l = 0.0; l <= 1.0; l += stepY) {
               for (double m = 0.0; m <= 1.0; m += stepZ) {
                  Vec3d pos = new Vec3d(
                     MathHelper.lerp(k, box.minX, box.maxX) + offsetX, MathHelper.lerp(l, box.minY, box.maxY), MathHelper.lerp(m, box.minZ, box.maxZ) + offsetZ
                  );
                  ctx.set(pos, source);
                  if (raycast(ctx, provider) == null) {
                     misses++;
                  }

                  total++;
               }
            }
         }

         return total == 0 ? 0.0F : (float)misses / total;
      } else {
         return 0.0F;
      }
   }

   private static BlockHitResult raycast(DamageUtil.ExposureContext context, DamageUtil.BlockRaycastProvider provider) {
      return (BlockHitResult)BlockView.raycast(context.start(), context.end(), context, provider, ctx -> null);
   }

   public static boolean isBroken(ItemStack stack, int threshold) {
      if (!stack.isDamageable()) {
         return false;
      } else {
         int max = stack.getMaxDamage();
         int damage = stack.getDamage();
         int percentRemaining = (int)((float)(max - damage) / max * 100.0F);
         return percentRemaining <= threshold;
      }
   }

   @FunctionalInterface
   public interface BlockRaycastProvider extends BiFunction<DamageUtil.ExposureContext, BlockPos, BlockHitResult> {
   }

   public record DamageProfile(Difficulty difficulty, float armor, float toughness, int resistanceAmp, int explosionProtection) {
      public float reduce(float damage) {
         if (this.difficulty != null) {
            switch (this.difficulty) {
               case PEACEFUL:
                  damage = 0.0F;
                  break;
               case EASY:
                  damage = Math.min(damage / 2.0F + 1.0F, damage);
                  break;
               case HARD:
                  damage *= 1.5F;
            }
         }

         float f = 2.0F + this.toughness / 4.0F;
         float g = MathHelper.clamp(this.armor - damage / f, this.armor * 0.2F, 20.0F);
         damage *= 1.0F - g / 25.0F;
         if (this.resistanceAmp >= 0) {
            damage *= 1.0F - 0.2F * (this.resistanceAmp + 1);
         }

         damage = net.minecraft.entity.DamageUtil.getInflictedDamage(Math.max(damage, 0.0F), this.explosionProtection);
         return Math.max(damage, 0.0F);
      }
   }

   public static final class ExposureContext {
      private Vec3d start;
      private Vec3d end;

      public ExposureContext(Vec3d start, Vec3d end) {
         this.start = start;
         this.end = end;
      }

      public void set(Vec3d start, Vec3d end) {
         this.start = start;
         this.end = end;
      }

      public Vec3d start() {
         return this.start;
      }

      public Vec3d end() {
         return this.end;
      }
   }
}
