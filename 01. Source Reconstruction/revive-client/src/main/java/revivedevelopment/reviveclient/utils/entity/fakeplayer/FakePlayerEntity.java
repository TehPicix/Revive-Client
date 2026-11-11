package revivedevelopment.reviveclient.utils.entity.fakeplayer;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixin.AbstractClientPlayerEntityAccessor;
import revivedevelopment.reviveclient.utils.entity.DamageUtil;

public class FakePlayerEntity extends OtherClientPlayerEntity {
   public boolean doNotPush;
   public boolean weak;
   public int totemPops;
   private boolean ownSkinFallback = true;

   public FakePlayerEntity(PlayerEntity player, String name, float health, boolean copyInv) {
      this(player, new GameProfile(UUID.randomUUID(), name), health, copyInv);
   }

   public FakePlayerEntity(PlayerEntity player, GameProfile profile, float health, boolean copyInv) {
      super(ReviveClient.mc.world, profile);
      this.copyPositionAndRotation(player);
      this.lastYaw = this.getYaw();
      this.lastPitch = this.getPitch();
      this.headYaw = player.headYaw;
      this.lastHeadYaw = this.headYaw;
      this.bodyYaw = player.bodyYaw;
      this.lastBodyYaw = this.bodyYaw;
      this.getAttributes().setFrom(player.getAttributes());
      this.setPose(player.getPose());
      this.getDataTracker().set(PLAYER_MODE_CUSTOMIZATION_ID, (Byte)player.getDataTracker().get(PLAYER_MODE_CUSTOMIZATION_ID));
      this.setMainArm(player.getMainArm());
      if (health <= 20.0F) {
         this.setHealth(health);
      } else {
         this.setHealth(health);
         this.setAbsorptionAmount(health - 20.0F);
      }

      if (copyInv) {
         this.getInventory().clone(player.getInventory());
      }
   }

   public void spawn() {
      this.unsetRemoved();
      ReviveClient.mc.world.addEntity(this);
   }

   public void despawn() {
      ReviveClient.mc.world.removeEntity(this.getId(), RemovalReason.DISCARDED);
      this.setRemoved(RemovalReason.DISCARDED);
   }

   public boolean simulateDamage(DamageSource source, float amount) {
      if (source != null && !(amount <= 0.0F) && !this.isRemoved() && ReviveClient.mc.world != null) {
         float damage = DamageUtil.applyReductions(amount, this, source, false);
         if (this.weak) {
            damage *= 100.0F;
         }

         if (damage <= 0.0F) {
            return false;
         } else {
            float remaining = damage;
            float absorption = this.getAbsorptionAmount();
            if (absorption > 0.0F) {
               float absorbed = Math.min(absorption, damage);
               this.setAbsorptionAmount(Math.max(0.0F, absorption - absorbed));
               remaining = damage - absorbed;
            }

            this.hurtTime = this.maxHurtTime = 10;
            this.playSound(SoundEvents.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
            if (remaining > 0.0F) {
               float newHealth = this.getHealth() - remaining;
               if (newHealth <= 0.0F) {
                  this.popTotem();
               } else {
                  this.setHealth(newHealth);
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private void popTotem() {
      this.totemPops++;
      this.clearStatusEffects();
      this.setHealth(1.0F);
      this.setAbsorptionAmount(Math.max(this.getAbsorptionAmount(), 8.0F));
      this.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
      this.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
      this.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
      this.playSound(SoundEvents.ITEM_TOTEM_USE, 1.0F, 1.0F);
      if (ReviveClient.mc.particleManager != null) {
         ReviveClient.mc.particleManager.addEmitter(this, ParticleTypes.TOTEM_OF_UNDYING, 30);
      }
   }

   public void setSkinSource(@Nullable PlayerListEntry entry) {
      this.ownSkinFallback = false;
      if (entry != null) {
         ((AbstractClientPlayerEntityAccessor)this).revive$setPlayerListEntry(entry);
      }
   }

   public void bindWorld(World world) {
      if (world != null && this.getEntityWorld() != world) {
         this.setWorld(world);
      }
   }

   @Nullable
   protected PlayerListEntry getPlayerListEntry() {
      PlayerListEntry entry = super.getPlayerListEntry();
      if (entry == null && this.ownSkinFallback && ReviveClient.mc.getNetworkHandler() != null && ReviveClient.mc.player != null) {
         entry = ReviveClient.mc.getNetworkHandler().getPlayerListEntry(ReviveClient.mc.player.getUuid());
         ((AbstractClientPlayerEntityAccessor)this).revive$setPlayerListEntry(entry);
      }

      return entry;
   }
}
