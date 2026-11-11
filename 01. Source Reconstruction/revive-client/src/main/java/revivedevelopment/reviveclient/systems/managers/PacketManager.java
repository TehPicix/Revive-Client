package revivedevelopment.reviveclient.systems.managers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.SendMovementPacketsEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.PacketLimiter;
import revivedevelopment.reviveclient.utils.network.PacketUtils;
import revivedevelopment.reviveclient.utils.player.ChatUtils;

public class PacketManager {
   private static final int GRIM_GLOBAL_LIMIT = 1000;
   private static final double GRIM_GLOBAL_TIMING = 1.0;
   private static final int GRIM_OFFHAND_LIMIT = 16;
   private static final double GRIM_OFFHAND_TIMING = 1.0;
   private static final int GRIM_CLICKSLOT_LIMIT = 8;
   private static final double GRIM_CLICKSLOT_TIMING = 1.0;
   public static final double SERVER_HARD_PPS = 500.0;
   private static final int HARD_MARGIN = 4;
   private static final double COMBAT_HARD_HEADROOM_PPS = 20.0;
   private static final double BASELINE_RESERVE_SECONDS = 1.0;
   private static final double BURST_TICKS = 4.0;
   private static final int MIN_TICK_BUDGET = 2;
   private static final double BASELINE_ALPHA = 0.02;
   private static final int HISTORY_TICKS = 200;
   private static final long MAX_WINDOW_MS = 10000L;
   private static final int HARD_LIMITER_OFFHAND_RESERVE = 4;
   private static final long BREAKDOWN_WINDOW_MS = 5000L;
   private static final int BREAKDOWN_TOP = 5;
   private static final long DEBUG_MIN_INTERVAL_MS = 1000L;
   private PacketLimiter packetLimiterModule;
   private final Map<PacketManager.Category, ArrayDeque<Long>> windows = new EnumMap<>(PacketManager.Category.class);
   private final ArrayDeque<Long> allSends = new ArrayDeque<>();
   private final ArrayDeque<Long> breakdownTimes = new ArrayDeque<>();
   private final ArrayDeque<String> breakdownLabels = new ArrayDeque<>();
   private final Map<Class<?>, String> labelCache = new ConcurrentHashMap<>();
   private final AtomicInteger tickTotal = new AtomicInteger();
   private final AtomicInteger tickBaseline = new AtomicInteger();
   private final AtomicInteger tickModule = new AtomicInteger();
   private final long[] histTime = new long[200];
   private final int[] histTotal = new int[200];
   private final int[] histBaseline = new int[200];
   private int histHead;
   private int histSize;
   private volatile double pps1s;
   private volatile double pps5s;
   private volatile double pps10s;
   private volatile double baselinePps1s;
   private volatile double baselinePerTickEma = -1.0;
   private volatile int peakTick1s;
   private volatile int peakTick10s;
   private volatile boolean wasThrottled;
   private long lastDebugMs;
   private volatile boolean inVanillaMoveSend;
   private volatile double externalPps;

   public PacketManager() {
      for (PacketManager.Category c : PacketManager.Category.values()) {
         this.windows.put(c, new ArrayDeque<>());
      }

      ReviveClient.EVENT_BUS.subscribe(this);
   }

   public boolean canSend(PacketManager.Category category) {
      return this.canSend(category, tierOf(category));
   }

   public boolean canSendCritical(PacketManager.Category category) {
      return this.canSend(category, PacketManager.Tier.CRITICAL);
   }

   private boolean canSend(PacketManager.Category category, PacketManager.Tier tier) {
      ArrayDeque<Long> q = this.windows.get(category);
      synchronized (q) {
         this.prune(q, this.windowMillisFor(category), System.currentTimeMillis());
         if (q.size() >= this.limitFor(category)) {
            return false;
         }
      }

      return this.limiterRemaining(category, tier, true) > 0;
   }

   public int remaining(PacketManager.Category category) {
      ArrayDeque<Long> q = this.windows.get(category);
      int cat;
      synchronized (q) {
         this.prune(q, this.windowMillisFor(category), System.currentTimeMillis());
         cat = Math.max(0, this.limitFor(category) - q.size());
      }

      return Math.min(cat, this.limiterRemaining(category, tierOf(category), true));
   }

   public boolean canBurst(PacketManager.Category category, int count) {
      ArrayDeque<Long> q = this.windows.get(category);
      int cat;
      synchronized (q) {
         this.prune(q, this.windowMillisFor(category), System.currentTimeMillis());
         cat = this.limitFor(category) - q.size();
      }

      return cat >= count && this.limiterRemaining(category, tierOf(category), false) >= count;
   }

   public void record(PacketManager.Category category) {
      this.append(category, System.currentTimeMillis());
   }

   public void recordRelayed(Packet<?> packet, boolean flashing) {
      long now = System.currentTimeMillis();
      this.append(PacketManager.Category.GLOBAL, now);
      synchronized (this.allSends) {
         this.allSends.addLast(now);
         this.prune(this.allSends, 10000L, now);
      }

      this.tickTotal.incrementAndGet();
      this.tickBaseline.incrementAndGet();
      if (breakdownOn()) {
         synchronized (this.breakdownTimes) {
            this.breakdownTimes.addLast(now);
            this.breakdownLabels.addLast("pc:" + this.label(packet, false));

            while (!this.breakdownTimes.isEmpty() && now - this.breakdownTimes.peekFirst() > 5000L) {
               this.breakdownTimes.pollFirst();
               this.breakdownLabels.pollFirst();
            }
         }
      }

      PacketManager.Category c = this.categorize(packet);
      if (c == PacketManager.Category.CLICK_SLOT && flashing) {
         c = null;
      }

      if (c != null) {
         this.append(c, now);
      }
   }

   public void setExternalPps(double pps) {
      this.externalPps = Double.isFinite(pps) && pps > 0.0 ? pps : 0.0;
   }

   private static boolean breakdownOn() {
      AntiCheatConfig c = AntiCheatConfig.get();
      return c != null && c.limiterDebug.get();
   }

   private void append(PacketManager.Category category, long now) {
      ArrayDeque<Long> q = this.windows.get(category);
      long window = AntiCheatConfig.get() != null ? this.windowMillisFor(category) : -1L;
      synchronized (q) {
         if (window >= 0L) {
            this.prune(q, window, now);
         }

         q.addLast(now);
      }
   }

   @EventHandler(
      priority = 200
   )
   private void onSendMovementPre(SendMovementPacketsEvent.Pre event) {
      this.inVanillaMoveSend = true;
   }

   @EventHandler(
      priority = -200
   )
   private void onSendMovementPost(SendMovementPacketsEvent.Post event) {
      this.inVanillaMoveSend = false;
   }

   @EventHandler(
      priority = -200
   )
   private void onSend(PacketEvent.Send event) {
      if (!event.isCancelled()) {
         long now = System.currentTimeMillis();
         this.append(PacketManager.Category.GLOBAL, now);
         synchronized (this.allSends) {
            this.allSends.addLast(now);
            this.prune(this.allSends, 10000L, now);
         }

         boolean extraMove = event.packet instanceof PlayerMoveC2SPacket && !this.inVanillaMoveSend;
         boolean module = extraMove || isDiscretionary(event.packet);
         this.tickTotal.incrementAndGet();
         if (module) {
            this.tickModule.incrementAndGet();
         } else {
            this.tickBaseline.incrementAndGet();
         }

         if (breakdownOn()) {
            synchronized (this.breakdownTimes) {
               String label = this.label(event.packet, extraMove);
               this.breakdownTimes.addLast(now);
               this.breakdownLabels.addLast(module ? "*" + label : label);

               while (!this.breakdownTimes.isEmpty() && now - this.breakdownTimes.peekFirst() > 5000L) {
                  this.breakdownTimes.pollFirst();
                  this.breakdownLabels.pollFirst();
               }
            }
         }

         PacketManager.Category c = this.categorize(event.packet);
         if (c == PacketManager.Category.CLICK_SLOT && ElytraDurabilitySaver.get().isFlashing()) {
            c = null;
         }

         if (c != null) {
            this.append(c, now);
         }
      }
   }

   private PacketManager.Category categorize(Packet<?> packet) {
      if (packet instanceof PlayerInteractBlockC2SPacket) {
         return PacketManager.Category.BLOCK_INTERACT;
      } else if (packet instanceof ClickSlotC2SPacket) {
         return PacketManager.Category.CLICK_SLOT;
      } else {
         return packet instanceof PlayerActionC2SPacket action && action.getAction() == Action.SWAP_ITEM_WITH_OFFHAND ? PacketManager.Category.OFFHAND : null;
      }
   }

   private static PacketManager.Tier tierOf(PacketManager.Category category) {
      return switch (category) {
         case GLOBAL -> PacketManager.Tier.LOW;
         case OFFHAND -> PacketManager.Tier.CRITICAL;
         case CLICK_SLOT, BLOCK_INTERACT -> PacketManager.Tier.COMBAT;
      };
   }

   private static boolean isDiscretionary(Packet<?> packet) {
      return packet instanceof PlayerInteractBlockC2SPacket
         || packet instanceof PlayerInteractEntityC2SPacket
         || packet instanceof PlayerInteractItemC2SPacket
         || packet instanceof ClickSlotC2SPacket
         || packet instanceof PlayerActionC2SPacket
         || packet instanceof HandSwingC2SPacket
         || packet instanceof UpdateSelectedSlotC2SPacket;
   }

   private String label(Packet<?> packet, boolean extraMove) {
      if (packet instanceof PlayerMoveC2SPacket move) {
         String shape = move.changesPosition() ? (move.changesLook() ? "full" : "pos") : (move.changesLook() ? "look" : "ground");
         return (extraMove ? "move+." : "move.") + shape;
      } else if (packet instanceof PlayerActionC2SPacket action) {
         return action.getAction() == Action.START_DESTROY_BLOCK
               && ReviveClient.mc.world != null
               && ReviveClient.mc.world.isOutOfHeightLimit(action.getPos().getY())
            ? "action.START@oow"
            : "action." + action.getAction().name();
      } else {
         return packet instanceof ClientCommandC2SPacket command
            ? "command." + command.getMode().name()
            : this.labelCache.computeIfAbsent(packet.getClass(), PacketManager::typeName);
      }
   }

   private static String typeName(Class<?> type) {
      String name = null;

      try {
         name = PacketUtils.getName((Class<? extends Packet<?>>)type);
      } catch (Throwable var3) {
      }

      if (name == null) {
         name = type.getSimpleName();
      }

      if (name.endsWith("C2SPacket")) {
         name = name.substring(0, name.length() - "C2SPacket".length());
      }

      return name;
   }

   @EventHandler(
      priority = 200
   )
   private void onTickPre(TickEvent.Pre event) {
      long now = System.currentTimeMillis();
      int total = this.tickTotal.getAndSet(0);
      int base = this.tickBaseline.getAndSet(0);
      this.tickModule.set(0);
      this.histTime[this.histHead] = now;
      this.histTotal[this.histHead] = total;
      this.histBaseline[this.histHead] = base;
      this.histHead = (this.histHead + 1) % 200;
      if (this.histSize < 200) {
         this.histSize++;
      }

      double ema = this.baselinePerTickEma;
      this.baselinePerTickEma = ema < 0.0 ? base : ema + 0.02 * (base - ema);
      int sum1 = 0;
      int sum5 = 0;
      int sum10 = 0;
      int base1 = 0;
      int peak1 = 0;
      int peak10 = 0;

      for (int i = 0; i < this.histSize; i++) {
         int idx = Math.floorMod(this.histHead - 1 - i, 200);
         long age = now - this.histTime[idx];
         if (age > 10000L) {
            break;
         }

         int t = this.histTotal[idx];
         sum10 += t;
         peak10 = Math.max(peak10, t);
         if (age <= 5000L) {
            sum5 += t;
         }

         if (age <= 1000L) {
            sum1 += t;
            base1 += this.histBaseline[idx];
            peak1 = Math.max(peak1, t);
         }
      }

      this.pps1s = sum1;
      this.pps5s = sum5 / 5.0;
      this.pps10s = sum10 / 10.0;
      this.baselinePps1s = base1;
      this.peakTick1s = peak1;
      this.peakTick10s = peak10;
      AntiCheatConfig c = AntiCheatConfig.get();
      boolean throttled = this.isThrottled();
      if (throttled != this.wasThrottled && now - this.lastDebugMs >= 1000L) {
         this.wasThrottled = throttled;
         this.lastDebugMs = now;
         if (c != null && c.limiterDebug.get() && ReviveClient.mc.player != null) {
            ChatUtils.infoPrefix("PacketLimiter", "%s", (throttled ? "Throttling low-priority modules: " : "Released: ") + this.getDebugInfo());
            ChatUtils.infoPrefix("PacketLimiter", "%s", this.getBreakdown());
         }
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      synchronized (this.allSends) {
         this.allSends.clear();
      }

      synchronized (this.breakdownTimes) {
         this.breakdownTimes.clear();
         this.breakdownLabels.clear();
      }

      this.tickTotal.set(0);
      this.tickBaseline.set(0);
      this.tickModule.set(0);
      this.histSize = 0;
      this.histHead = 0;
      this.baselinePerTickEma = -1.0;
      this.pps1s = this.pps5s = this.pps10s = this.baselinePps1s = 0.0;
      this.peakTick1s = this.peakTick10s = 0;
      this.wasThrottled = false;
      this.inVanillaMoveSend = false;
   }

   private static boolean limiterEnabled(AntiCheatConfig c) {
      return c != null && c.packetLimiter.get();
   }

   private int sentInWindow(AntiCheatConfig c, long now) {
      long windowMs = (long)(1000.0 * c.limiterWindow.get());
      int external = (int)Math.round(this.externalPps * Math.min(windowMs, 10000L) / 1000.0);
      synchronized (this.allSends) {
         this.prune(this.allSends, Math.min(windowMs, 10000L), now);
         return this.allSends.size() + external;
      }
   }

   public double getBaselinePps() {
      double ema = this.baselinePerTickEma;
      return ema < 0.0 ? 0.0 : ema * 20.0;
   }

   private int limiterRemaining(PacketManager.Category category, PacketManager.Tier tier, boolean meterPerTick) {
      return Math.min(this.adaptiveRemaining(tier, meterPerTick), this.hardLimiterRemaining(category, tier));
   }

   private int hardLimiterRemaining(PacketManager.Category category, PacketManager.Tier tier) {
      PacketLimiter module = this.packetLimiterModule();
      if (module != null && module.isActive()) {
         Class<?> type = switch (category) {
            case GLOBAL -> null;
            case OFFHAND, CLICK_SLOT -> ClickSlotC2SPacket.class;
            case BLOCK_INTERACT -> PlayerInteractBlockC2SPacket.class;
         };
         int left = module.remaining(type);
         if (tier != PacketManager.Tier.CRITICAL) {
            left -= 4;
         }

         return Math.max(0, left);
      } else {
         return Integer.MAX_VALUE;
      }
   }

   private PacketLimiter packetLimiterModule() {
      if (this.packetLimiterModule == null && Modules.get() != null) {
         this.packetLimiterModule = Modules.get().get(PacketLimiter.class);
      }

      return this.packetLimiterModule;
   }

   private static double tierCap(AntiCheatConfig c, PacketManager.Tier tier) {
      double low = c.limiterMaxPps.get().intValue();

      return switch (tier) {
         case LOW -> low;
         case COMBAT -> Math.max(low, Math.min((double)c.limiterCombatPps.get().intValue(), 480.0));
         case CRITICAL -> 500.0;
      };
   }

   private int adaptiveRemaining(PacketManager.Tier tier, boolean meterPerTick) {
      AntiCheatConfig c = AntiCheatConfig.get();
      if (!limiterEnabled(c)) {
         return Integer.MAX_VALUE;
      } else {
         long now = System.currentTimeMillis();
         double windowSec = c.limiterWindow.get();
         int inWindow = this.sentInWindow(c, now);
         if (tier == PacketManager.Tier.CRITICAL) {
            int hard = (int)Math.floor(500.0 * windowSec) - 4;
            return Math.max(0, hard - inWindow);
         } else {
            double cap = tierCap(c, tier);
            boolean auto = c.limiterAuto.get();
            double reserve = auto ? this.getBaselinePps() * 1.0 : 0.0;
            int windowLeft = (int)Math.floor(cap * windowSec - inWindow - reserve);
            if (windowLeft <= 0) {
               return 0;
            } else {
               return meterPerTick && tier == PacketManager.Tier.LOW && auto && inWindow >= cap * windowSec * c.limiterPressure.get()
                  ? Math.max(0, Math.min(windowLeft, this.tickBudget(c) - this.tickModule.get()))
                  : windowLeft;
            }
         }
      }
   }

   private int tickBudget(AntiCheatConfig c) {
      double moduleRate = Math.max(0.0, c.limiterMaxPps.get().intValue() - this.getBaselinePps());
      return Math.max(2, (int)Math.ceil(moduleRate / 20.0 * 4.0));
   }

   public int getAllowance() {
      return this.limiterRemaining(PacketManager.Category.GLOBAL, PacketManager.Tier.LOW, true);
   }

   public int getCombatAllowance() {
      return this.limiterRemaining(PacketManager.Category.BLOCK_INTERACT, PacketManager.Tier.COMBAT, true);
   }

   public boolean isThrottled() {
      return this.getAllowance() <= 0;
   }

   public boolean isRateCapped() {
      return this.isRateCapped(false);
   }

   public boolean isRateCapped(boolean combat) {
      PacketManager.Tier tier = combat ? PacketManager.Tier.COMBAT : PacketManager.Tier.LOW;
      return Math.min(this.adaptiveRemaining(tier, false), this.hardLimiterRemaining(PacketManager.Category.GLOBAL, tier)) <= 0;
   }

   public double getPacketsPerSecond() {
      AntiCheatConfig c = AntiCheatConfig.get();
      return c == null ? this.pps1s : this.sentInWindow(c, System.currentTimeMillis()) / c.limiterWindow.get();
   }

   public double getPps1s() {
      return this.pps1s;
   }

   public double getPps5s() {
      return this.pps5s;
   }

   public double getPps10s() {
      return this.pps10s;
   }

   public double getPacketsPerTick() {
      return this.pps1s / 20.0;
   }

   public int getPeakTick1s() {
      return this.peakTick1s;
   }

   public int getPeakTick10s() {
      return this.peakTick10s;
   }

   public String getDebugInfo() {
      AntiCheatConfig c = AntiCheatConfig.get();
      int allowance = this.getAllowance();
      int combat = this.getCombatAllowance();
      boolean on = limiterEnabled(c);
      return String.format(
         "pps win=%.1f 1s=%.0f 5s=%.1f 10s=%.1f | base=%.1f (1s %.0f) | peak/tick=%d | cap=%s | allow low=%s combat=%s%s",
         this.getPacketsPerSecond(),
         this.pps1s,
         this.pps5s,
         this.pps10s,
         this.getBaselinePps(),
         this.baselinePps1s,
         this.peakTick10s,
         on ? c.limiterMaxPps.get() + "/" + (int)tierCap(c, PacketManager.Tier.COMBAT) : "off",
         allowance == Integer.MAX_VALUE ? "inf" : String.valueOf(allowance),
         combat == Integer.MAX_VALUE ? "inf" : String.valueOf(combat),
         allowance <= 0 ? (combat <= 0 ? " THROTTLED (combat too)" : " THROTTLED (low only)") : ""
      );
   }

   public String getBreakdown() {
      Map<String, Integer> counts = new HashMap<>();
      int module = 0;
      int total = 0;
      long now = System.currentTimeMillis();
      synchronized (this.breakdownTimes) {
         Iterator<Long> times = this.breakdownTimes.iterator();
         Iterator<String> labels = this.breakdownLabels.iterator();

         while (times.hasNext() && labels.hasNext()) {
            long t = times.next();
            String l = labels.next();
            if (now - t <= 5000L) {
               total++;
               if (l.startsWith("*")) {
                  module++;
               }

               counts.merge(l, 1, Integer::sum);
            }
         }
      }

      double sec = 5.0;
      List<Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
      sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
      StringBuilder sb = new StringBuilder(
         String.format("last %.0fs: %.1f/s (module %.1f, base %.1f) | top:", sec, total / sec, module / sec, (total - module) / sec)
      );

      for (int i = 0; i < Math.min(5, sorted.size()); i++) {
         Entry<String, Integer> e = sorted.get(i);
         sb.append(String.format(" %s %.1f", e.getKey(), e.getValue().intValue() / sec));
         if (i < Math.min(5, sorted.size()) - 1) {
            sb.append(',');
         }
      }

      return sb.toString();
   }

   private void prune(ArrayDeque<Long> q, long window, long now) {
      Long head;
      while ((head = q.peekFirst()) != null && now - head > window) {
         q.pollFirst();
      }
   }

   private int limitFor(PacketManager.Category category) {
      AntiCheatConfig c = AntiCheatConfig.get();

      return switch (category) {
         case GLOBAL -> custom(c.globalPreset) ? c.globalLimit.get() : 1000;
         case OFFHAND -> custom(c.offhandPreset) ? c.offhandLimit.get() : 16;
         case CLICK_SLOT -> custom(c.clickSlotPreset) ? c.clickSlotLimit.get() : 8;
         case BLOCK_INTERACT -> c.blockInteractLimit.get();
      };
   }

   private long windowMillisFor(PacketManager.Category category) {
      AntiCheatConfig c = AntiCheatConfig.get();

      return switch (category) {
         case GLOBAL -> (long)(1000.0 * (custom(c.globalPreset) ? c.globalTiming.get() : 1.0));
         case OFFHAND -> (long)(1000.0 * (custom(c.offhandPreset) ? c.offhandTiming.get() : 1.0));
         case CLICK_SLOT -> (long)(1000.0 * (custom(c.clickSlotPreset) ? c.clickSlotTiming.get() : 1.0));
         case BLOCK_INTERACT -> c.blockInteractTimeframe.get().intValue();
      };
   }

   private static boolean custom(Setting<AntiCheatConfig.PacketPreset> preset) {
      return preset.get() == AntiCheatConfig.PacketPreset.Custom;
   }

   public static enum Category {
      GLOBAL,
      OFFHAND,
      CLICK_SLOT,
      BLOCK_INTERACT;
   }

   private static enum Tier {
      LOW,
      COMBAT,
      CRITICAL;
   }
}
