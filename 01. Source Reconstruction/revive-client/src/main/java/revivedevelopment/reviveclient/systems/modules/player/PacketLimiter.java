package revivedevelopment.reviveclient.systems.modules.player;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.network.PacketUtils;

public class PacketLimiter extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> limit = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("limit-per-packet")
            .description("Maximum packets of one type sent per interval (Lambda default 99 per 4s, ~25/s).")
            .defaultValue(99)
            .min(1)
            .sliderRange(10, 400)
            .build()
      );
   private final Setting<Integer> swingLimit = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("swing-limit")
            .description("Maximum hand swings per interval. Mining swings every tick (20/s) on top of combat swings, so this sits above the per-packet limit.")
            .defaultValue(200)
            .min(1)
            .sliderRange(50, 400)
            .build()
      );
   private final Setting<Integer> globalLimit = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("global-limit")
            .description("Maximum packets of all limited types together per interval (Lambda default 1000 per 4s).")
            .defaultValue(1000)
            .min(100)
            .sliderRange(100, 5000)
            .build()
      );
   private final Setting<Integer> interval = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("interval")
            .description("Window the limits are counted over, in milliseconds.")
            .defaultValue(4000)
            .min(50)
            .sliderRange(250, 10000)
            .build()
      );
   private final ArrayDeque<Long> global = new ArrayDeque<>();
   private final Map<Class<?>, ArrayDeque<Long>> perType = new HashMap<>();
   private final Map<Class<?>, Integer> droppedSinceReport = new HashMap<>();
   private final Map<Class<?>, Long> lastReportMs = new HashMap<>();

   public PacketLimiter() {
      super(Categories.Player, "packet-limiter", "Drops outgoing packets over a per-type rate limit and logs which type (Lambda's PacketLimiter).");
   }

   @Override
   public void onActivate() {
      synchronized (this) {
         this.global.clear();
         this.perType.clear();
         this.droppedSinceReport.clear();
         this.lastReportMs.clear();
      }
   }

   @EventHandler(
      priority = -100
   )
   private void onSend(PacketEvent.Send event) {
      if (!event.isCancelled()) {
         Packet<?> packet = event.packet;
         if (!isExempt(packet)) {
            long now = System.currentTimeMillis();
            long window = this.interval.get().intValue();
            Class<?> type = packet.getClass();
            String report = null;
            synchronized (this) {
               prune(this.global, now, window);
               ArrayDeque<Long> q = this.perType.computeIfAbsent(type, k -> new ArrayDeque<>());
               prune(q, now, window);
               int typeLimit = packet instanceof HandSwingC2SPacket ? this.swingLimit.get() : this.limit.get();
               boolean overGlobal = this.global.size() >= this.globalLimit.get();
               boolean bypassStart = this.isOutOfWorldStart(packet);
               if (!overGlobal && (bypassStart || q.size() < typeLimit)) {
                  this.global.addLast(now);
                  if (!bypassStart) {
                     q.addLast(now);
                  }

                  return;
               }

               event.cancel();
               int dropped = this.droppedSinceReport.merge(type, 1, Integer::sum);
               if (now - this.lastReportMs.getOrDefault(type, 0L) >= 1000L) {
                  this.lastReportMs.put(type, now);
                  this.droppedSinceReport.put(type, 0);
                  report = "dropped "
                     + dropped
                     + "x "
                     + typeName(type)
                     + " - "
                     + (overGlobal ? "global limit " + this.globalLimit.get() : q.size() + "/" + typeLimit)
                     + " per "
                     + window
                     + "ms";
               }
            }

            if (report != null) {
               String msg = report;
               this.mc.execute(() -> this.warning(msg, new Object[0]));
            }
         }
      }
   }

   public int typeLimit(Class<?> type) {
      return type == HandSwingC2SPacket.class ? this.swingLimit.get() : this.limit.get();
   }

   public int remaining(Class<?> type) {
      long now = System.currentTimeMillis();
      long window = this.interval.get().intValue();
      synchronized (this) {
         prune(this.global, now, window);
         int left = this.globalLimit.get() - this.global.size();
         if (type != null) {
            int used = 0;
            ArrayDeque<Long> q = this.perType.get(type);
            if (q != null) {
               prune(q, now, window);
               used = q.size();
            }

            left = Math.min(left, this.typeLimit(type) - used);
         }

         return Math.max(0, left);
      }
   }

   private boolean isOutOfWorldStart(Packet<?> packet) {
      return packet instanceof PlayerActionC2SPacket action
         && action.getAction() == Action.START_DESTROY_BLOCK
         && this.mc.world != null
         && this.mc.world.isOutOfHeightLimit(action.getPos().getY());
   }

   private static boolean isExempt(Packet<?> packet) {
      return packet instanceof PlayerMoveC2SPacket
         || packet instanceof TeleportConfirmC2SPacket
         || packet instanceof CommonPongC2SPacket
         || packet instanceof KeepAliveC2SPacket
         || packet instanceof UpdateSelectedSlotC2SPacket;
   }

   private static String typeName(Class<?> type) {
      String name = PacketUtils.getName((Class<? extends Packet<?>>)type);
      return name != null ? name : type.getSimpleName();
   }

   private static void prune(ArrayDeque<Long> q, long now, long window) {
      Long head;
      while ((head = q.peekFirst()) != null && now - head > window) {
         q.pollFirst();
      }
   }
}
