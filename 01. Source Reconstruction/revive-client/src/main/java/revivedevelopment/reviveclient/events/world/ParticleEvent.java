package revivedevelopment.reviveclient.events.world;

import net.minecraft.particle.ParticleEffect;
import revivedevelopment.reviveclient.events.Cancellable;

public class ParticleEvent extends Cancellable {
   private static final ParticleEvent INSTANCE = new ParticleEvent();
   public ParticleEffect particle;

   public static ParticleEvent get(ParticleEffect particle) {
      INSTANCE.setCancelled(false);
      INSTANCE.particle = particle;
      return INSTANCE;
   }
}
