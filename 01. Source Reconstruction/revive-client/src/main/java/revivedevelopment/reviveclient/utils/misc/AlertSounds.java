package revivedevelopment.reviveclient.utils.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public final class AlertSounds {
   public static final SoundEvent UH_OH = SoundEvent.of(Identifier.of("revive-client", "uh_oh"));

   private AlertSounds() {
   }

   public static void playAlert(MinecraftClient mc, AlertSounds.AlertSound sound, double volume, boolean stupidDekto) {
      if (mc != null && mc.player != null) {
         SoundEvent event = stupidDekto ? SoundEvents.ENTITY_VILLAGER_NO : forPreset(sound);
         float pitch = stupidDekto ? 0.8F : 1.0F;
         mc.getSoundManager().play(PositionedSoundInstance.master(event, pitch, (float)Math.max(0.0, volume)));
      }
   }

   private static SoundEvent forPreset(AlertSounds.AlertSound sound) {
      return switch (sound) {
         case UhOh -> UH_OH;
         case ExperienceOrb -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
         case Bell -> (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
         case Pling -> (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
         case Bit -> (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_BIT.value();
         case Anvil -> SoundEvents.BLOCK_ANVIL_LAND;
         case Ding -> (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
      };
   }

   public static enum AlertSound {
      UhOh,
      ExperienceOrb,
      Bell,
      Pling,
      Bit,
      Anvil,
      Ding;
   }
}
