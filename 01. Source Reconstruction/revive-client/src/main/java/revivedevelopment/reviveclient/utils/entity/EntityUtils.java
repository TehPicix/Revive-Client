package revivedevelopment.reviveclient.utils.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.GameMode;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import net.minecraft.world.entity.SimpleEntityLookup;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixin.EntityTrackingSectionAccessor;
import revivedevelopment.reviveclient.mixin.SectionedEntityCacheAccessor;
import revivedevelopment.reviveclient.mixin.SimpleEntityLookupAccessor;
import revivedevelopment.reviveclient.mixin.WorldAccessor;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class EntityUtils {
   private EntityUtils() {
   }

   public static boolean isAttackable(EntityType<?> type) {
      return type != EntityType.AREA_EFFECT_CLOUD
         && type != EntityType.ARROW
         && type != EntityType.FALLING_BLOCK
         && type != EntityType.FIREWORK_ROCKET
         && type != EntityType.ITEM
         && type != EntityType.LLAMA_SPIT
         && type != EntityType.SPECTRAL_ARROW
         && type != EntityType.ENDER_PEARL
         && type != EntityType.EXPERIENCE_BOTTLE
         && type != EntityType.SPLASH_POTION
         && type != EntityType.LINGERING_POTION
         && type != EntityType.TRIDENT
         && type != EntityType.LIGHTNING_BOLT
         && type != EntityType.FISHING_BOBBER
         && type != EntityType.EXPERIENCE_ORB
         && type != EntityType.EGG;
   }

   public static float getTotalHealth(LivingEntity target) {
      return target.getHealth() + target.getAbsorptionAmount();
   }

   public static int getPing(PlayerEntity player) {
      if (ReviveClient.mc.getNetworkHandler() == null) {
         return 0;
      } else {
         PlayerListEntry playerListEntry = ReviveClient.mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
         if (playerListEntry == null && ReviveClient.mc.getNetworkHandler().getPlayerList() != null) {
            for (PlayerListEntry entry : ReviveClient.mc.getNetworkHandler().getPlayerList()) {
               if (entry.getProfile().name().equals(player.getGameProfile().name())) {
                  playerListEntry = entry;
                  break;
               }
            }
         }

         return playerListEntry == null ? 0 : playerListEntry.getLatency();
      }
   }

   public static GameMode getGameMode(PlayerEntity player) {
      if (player == null) {
         return null;
      } else {
         PlayerListEntry playerListEntry = ReviveClient.mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
         if (playerListEntry == null && ReviveClient.mc.getNetworkHandler() != null && ReviveClient.mc.getNetworkHandler().getPlayerList() != null) {
            for (PlayerListEntry entry : ReviveClient.mc.getNetworkHandler().getPlayerList()) {
               if (entry.getProfile().name().equals(player.getGameProfile().name())) {
                  playerListEntry = entry;
                  break;
               }
            }
         }

         return playerListEntry == null ? null : playerListEntry.getGameMode();
      }
   }

   public static boolean isAboveWater(Entity entity) {
      Mutable blockPos = entity.getBlockPos().mutableCopy();
      int i = 0;

      while (i < 64) {
         BlockState state = ReviveClient.mc.world.getBlockState(blockPos);
         if (!state.blocksMovement()) {
            Fluid fluid = state.getFluidState().getFluid();
            if (fluid != Fluids.WATER && fluid != Fluids.FLOWING_WATER) {
               blockPos.move(0, -1, 0);
               i++;
               continue;
            }

            return true;
         }
         break;
      }

      return false;
   }

   public static boolean isInRenderDistance(Entity entity) {
      return entity == null ? false : isInRenderDistance(entity.getX(), entity.getZ());
   }

   public static boolean isInRenderDistance(BlockEntity entity) {
      return entity == null ? false : isInRenderDistance(entity.getPos().getX(), entity.getPos().getZ());
   }

   public static boolean isInRenderDistance(BlockPos pos) {
      return pos == null ? false : isInRenderDistance(pos.getX(), pos.getZ());
   }

   public static boolean isInRenderDistance(double posX, double posZ) {
      double x = Math.abs(ReviveClient.mc.gameRenderer.getCamera().getCameraPos().x - posX);
      double z = Math.abs(ReviveClient.mc.gameRenderer.getCamera().getCameraPos().z - posZ);
      double d = ((Integer)ReviveClient.mc.options.getViewDistance().getValue() + 1) * 16;
      return x < d && z < d;
   }

   public static String getName(Entity entity) {
      if (entity == null) {
         return null;
      } else {
         return entity instanceof PlayerEntity ? entity.getName().getString() : entity.getType().getName().getString();
      }
   }

   public static Color getColorFromDistance(Entity entity) {
      Color distanceColor = new Color(255, 255, 255);
      double distance = PlayerUtils.distanceToCamera(entity);
      double percent = distance / 60.0;
      if (!(percent < 0.0) && !(percent > 1.0)) {
         int r;
         int g;
         if (percent < 0.5) {
            r = 255;
            g = (int)(255.0 * percent / 0.5);
         } else {
            g = 255;
            r = 255 - (int)(255.0 * (percent - 0.5) / 0.5);
         }

         distanceColor.set(r, g, 0, 255);
         return distanceColor;
      } else {
         distanceColor.set(0, 255, 0, 255);
         return distanceColor;
      }
   }

   public static Color getColorFromHealth(Entity entity, Color nonLivingEntityColor) {
      if (entity instanceof LivingEntity living) {
         float health = living.getHealth();
         float maxHealth = living.getMaxHealth();
         if (maxHealth <= 0.0F) {
            return new Color(nonLivingEntityColor);
         } else {
            double percent = health / maxHealth;
            percent = Math.max(0.0, Math.min(1.0, percent));
            int r;
            int g;
            if (percent < 0.5) {
               r = 255;
               g = (int)(255.0 * (percent / 0.5));
            } else {
               g = 255;
               r = 255 - (int)(255.0 * ((percent - 0.5) / 0.5));
            }

            return new Color(r, g, 0, 255);
         }
      } else {
         return new Color(nonLivingEntityColor);
      }
   }

   public static boolean intersectsWithEntity(Box box, Predicate<Entity> predicate) {
      EntityLookup<Entity> entityLookup = ((WorldAccessor)ReviveClient.mc.world).getEntityLookup();
      if (!(entityLookup instanceof SimpleEntityLookup<Entity> simpleEntityLookup)) {
         AtomicBoolean found = new AtomicBoolean(false);
         entityLookup.forEachIntersects(box, entityx -> {
            if (!found.get() && predicate.test(entityx)) {
               found.set(true);
            }
         });
         return found.get();
      } else {
         SectionedEntityCache<Entity> cache = ((SimpleEntityLookupAccessor)simpleEntityLookup).getCache();
         LongSortedSet trackedPositions = ((SectionedEntityCacheAccessor)cache).getTrackedPositions();
         Long2ObjectMap<EntityTrackingSection<Entity>> trackingSections = ((SectionedEntityCacheAccessor)cache).getTrackingSections();
         int i = ChunkSectionPos.getSectionCoord(box.minX - 2.0);
         int j = ChunkSectionPos.getSectionCoord(box.minY - 2.0);
         int k = ChunkSectionPos.getSectionCoord(box.minZ - 2.0);
         int l = ChunkSectionPos.getSectionCoord(box.maxX + 2.0);
         int m = ChunkSectionPos.getSectionCoord(box.maxY + 2.0);
         int n = ChunkSectionPos.getSectionCoord(box.maxZ + 2.0);

         for (int o = i; o <= l; o++) {
            long p = ChunkSectionPos.asLong(o, 0, 0);
            long q = ChunkSectionPos.asLong(o, -1, -1);
            LongBidirectionalIterator longIterator = trackedPositions.subSet(p, q + 1L).iterator();

            while (longIterator.hasNext()) {
               long r = longIterator.nextLong();
               int s = ChunkSectionPos.unpackY(r);
               int t = ChunkSectionPos.unpackZ(r);
               if (s >= j && s <= m && t >= k && t <= n) {
                  EntityTrackingSection<Entity> entityTrackingSection = (EntityTrackingSection<Entity>)trackingSections.get(r);
                  if (entityTrackingSection != null && entityTrackingSection.getStatus().shouldTrack()) {
                     for (Object eObj : ((EntityTrackingSectionAccessor)entityTrackingSection).getCollection()) {
                        Entity entity = (Entity)eObj;
                        if (entity.getBoundingBox().intersects(box) && predicate.test(entity)) {
                           return true;
                        }
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   public static EntityType<?> getGroup(Entity entity) {
      return entity.getType();
   }
}
