package revivedevelopment.reviveclient.systems.modules.player;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class Rubberband extends Module {
   private static final int HISTORY = 100;
   private static final long RATE_WINDOW_MS = 10000L;
   private final ArrayDeque<Vec3d> sent = new ArrayDeque<>();
   private final ArrayDeque<Long> recentSetbacks = new ArrayDeque<>();

   public Rubberband() {
      super(Categories.Player, "rubberband", "Logs each server setback: how many move packets it reverted and by how far (Lambda's Rubberband).");
   }

   @Override
   public void onActivate() {
      synchronized (this) {
         this.sent.clear();
         this.recentSetbacks.clear();
      }
   }

   @EventHandler(
      priority = -200
   )
   private void onSend(PacketEvent.Send event) {
      if (!event.isCancelled()) {
         if (event.packet instanceof PlayerMoveC2SPacket packet && packet.changesPosition()) {
            Vec3d pos = new Vec3d(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0));
            synchronized (this) {
               this.sent.addLast(pos);
               if (this.sent.size() > 100) {
                  this.sent.pollFirst();
               }
            }
         }
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onReceive(PacketEvent.Receive event) {
      if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
         Vec3d var19 = packet.change().position();
         long now = System.currentTimeMillis();
         String msg;
         synchronized (this) {
            this.recentSetbacks.addLast(now);

            Long head;
            while ((head = this.recentSetbacks.peekFirst()) != null && now - head > 10000L) {
               this.recentSetbacks.pollFirst();
            }

            int inWindow = this.recentSetbacks.size();
            if (this.sent.isEmpty()) {
               msg = "position reverted (no sent positions yet) - " + inWindow + " setback(s) in 10s";
            } else {
               int best = 0;
               int idx = 0;
               double bestDistSq = Double.MAX_VALUE;

               for (Iterator<Vec3d> it = this.sent.descendingIterator(); it.hasNext(); idx++) {
                  double d = it.next().squaredDistanceTo(var19);
                  if (d < bestDistSq) {
                     bestDistSq = d;
                     best = idx;
                  }
               }

               msg = String.format(
                  Locale.US, "reverted by %d move packet(s) (deviation %.3f) - %d setback(s) in 10s", best + 1, Math.sqrt(bestDistSq), inWindow
               );
            }
         }

         this.mc.execute(() -> this.warning(msg, new Object[0]));
      }
   }
}
