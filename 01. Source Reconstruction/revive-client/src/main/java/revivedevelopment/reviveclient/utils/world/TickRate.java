package revivedevelopment.reviveclient.utils.world;

import java.util.Arrays;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameJoinedEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.utils.Utils;

public class TickRate {
   public static TickRate INSTANCE = new TickRate();
   private final float[] tickRates = new float[20];
   private int nextIndex = 0;
   private long timeLastTimeUpdate = -1L;
   private long timeGameJoined;

   private TickRate() {
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      if (event.packet instanceof WorldTimeUpdateS2CPacket) {
         long now = System.currentTimeMillis();
         float timeElapsed = (float)(now - this.timeLastTimeUpdate) / 1000.0F;
         this.tickRates[this.nextIndex] = MathHelper.clamp(20.0F / timeElapsed, 0.0F, 25.0F);
         this.nextIndex = (this.nextIndex + 1) % this.tickRates.length;
         this.timeLastTimeUpdate = now;
      }
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      Arrays.fill(this.tickRates, 0.0F);
      this.nextIndex = 0;
      this.timeGameJoined = this.timeLastTimeUpdate = System.currentTimeMillis();
   }

   public float getTickRate() {
      if (!Utils.canUpdate()) {
         return 0.0F;
      } else if (System.currentTimeMillis() - this.timeGameJoined < 4000L) {
         return 20.0F;
      } else {
         int numTicks = 0;
         float sumTickRates = 0.0F;

         for (float tickRate : this.tickRates) {
            if (tickRate > 0.0F) {
               sumTickRates += tickRate;
               numTicks++;
            }
         }

         return numTicks == 0 ? 20.0F : MathHelper.clamp(sumTickRates / numTicks, 0.0F, 20.0F);
      }
   }

   public float getTimeSinceLastTick() {
      long now = System.currentTimeMillis();
      return now - this.timeGameJoined < 4000L ? 0.0F : (float)(now - this.timeLastTimeUpdate) / 1000.0F;
   }
}
