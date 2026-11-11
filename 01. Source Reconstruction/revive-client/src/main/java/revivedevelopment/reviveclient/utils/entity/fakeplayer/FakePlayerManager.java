package revivedevelopment.reviveclient.utils.entity.fakeplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixin.PlayerInteractEntityC2SPacketAccessor;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.MaceUtils;

public class FakePlayerManager {
   private static final List<FakePlayerEntity> ENTITIES = new ArrayList<>();
   public static boolean takeDamage = true;
   public static volatile BiConsumer<FakePlayerEntity, Float> hitListener;
   private static final FakePlayerManager INSTANCE = new FakePlayerManager();

   private FakePlayerManager() {
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      ENTITIES.clear();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (ReviveClient.mc.world != null && !ENTITIES.isEmpty()) {
         ENTITIES.removeIf(fp -> fp.getEntityWorld() != ReviveClient.mc.world);
      }
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
         if (event.packet instanceof PlayerInteractEntityC2SPacket packet) {
            PlayerInteractEntityC2SPacketAccessor var8 = (PlayerInteractEntityC2SPacketAccessor)packet;
            if ("ATTACK".equals(var8.revive$getType().getType().name())) {
               if (ReviveClient.mc.world.getEntityById(var8.revive$getEntityId()) instanceof FakePlayerEntity fp && ENTITIES.contains(fp)) {
                  event.setCancelled(true);
                  if (takeDamage) {
                     float damage = computeMeleeDamage(ReviveClient.mc.player, fp);
                     fp.simulateDamage(ReviveClient.mc.world.getDamageSources().playerAttack(ReviveClient.mc.player), damage);
                     BiConsumer<FakePlayerEntity, Float> listener = hitListener;
                     if (listener != null) {
                        listener.accept(fp, damage);
                     }
                  }
               }
            }
         }
      }
   }

   private static float computeMeleeDamage(ClientPlayerEntity p, Entity target) {
      ItemStack weapon = p.getMainHandStack();
      float charge = p.getAttackCooldownProgress(0.5F);
      float damage = (float)p.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
      float enchantDamage = 0.0F;
      int sharpness = Utils.getEnchantmentLevel(weapon, Enchantments.SHARPNESS);
      if (sharpness > 0) {
         enchantDamage += 1.0F + 0.5F * (sharpness - 1);
      }

      damage *= 0.2F + charge * charge * 0.8F;
      enchantDamage *= charge;
      float maceBonus = maceBonusDamage(p, target, weapon, damage);
      if (maceBonus > 0.0F) {
         int density = Utils.getEnchantmentLevel(weapon, Enchantments.DENSITY);
         if (density > 0) {
            maceBonus = (float)(maceBonus + 0.5 * p.fallDistance);
         }

         damage += maceBonus;
      }

      boolean crit = charge > 0.9F
         && p.fallDistance > 0.0
         && !p.isOnGround()
         && !p.isClimbing()
         && !p.isTouchingWater()
         && !p.hasStatusEffect(StatusEffects.BLINDNESS)
         && !p.hasVehicle();
      if (crit) {
         damage *= 1.5F;
      }

      return damage + enchantDamage;
   }

   private static float maceBonusDamage(ClientPlayerEntity p, Entity target, ItemStack weapon, float baseDamage) {
      if (weapon.getItem() instanceof MaceItem mace) {
         return mace.getBonusAttackDamage(target, baseDamage, ReviveClient.mc.world.getDamageSources().playerAttack(p));
      } else {
         boolean fallingStrike = p.fallDistance > 0.0 && !p.isOnGround() && !p.isClimbing();
         if (fallingStrike && isMace(weapon)) {
            float fd = (float)p.fallDistance;
            return fd <= 3.0F ? 4.0F * fd : (fd <= 8.0F ? 12.0F + 2.0F * (fd - 3.0F) : 22.0F + (fd - 8.0F));
         } else {
            return 0.0F;
         }
      }
   }

   private static boolean isMace(ItemStack stack) {
      return MaceUtils.isMace(stack);
   }

   public static List<FakePlayerEntity> getFakePlayers() {
      return ENTITIES;
   }

   public static FakePlayerEntity get(String name) {
      for (FakePlayerEntity fp : ENTITIES) {
         if (fp.getName().getString().equals(name)) {
            return fp;
         }
      }

      return null;
   }

   public static void add(String name, float health, boolean copyInv) {
      add(name, health, copyInv, false);
   }

   public static void add(String name, float health, boolean copyInv, boolean weak) {
      if (Utils.canUpdate()) {
         FakePlayerEntity fakePlayer = new FakePlayerEntity(ReviveClient.mc.player, name, health, copyInv);
         fakePlayer.weak = weak;
         fakePlayer.spawn();
         ENTITIES.add(fakePlayer);
      }
   }

   public static void remove(FakePlayerEntity fp) {
      ENTITIES.removeIf(fp1 -> {
         if (fp1.getName().getString().equals(fp.getName().getString())) {
            fp1.despawn();
            return true;
         } else {
            return false;
         }
      });
   }

   public static void clear() {
      if (!ENTITIES.isEmpty()) {
         ENTITIES.forEach(FakePlayerEntity::despawn);
         ENTITIES.clear();
      }
   }

   public static void forEach(Consumer<FakePlayerEntity> action) {
      for (FakePlayerEntity fakePlayer : ENTITIES) {
         action.accept(fakePlayer);
      }
   }

   public static int count() {
      return ENTITIES.size();
   }

   public static Stream<FakePlayerEntity> stream() {
      return ENTITIES.stream();
   }

   public static boolean contains(FakePlayerEntity fp) {
      return ENTITIES.contains(fp);
   }

   static {
      ReviveClient.EVENT_BUS.subscribe(INSTANCE);
   }
}
