package revivedevelopment.reviveclient.systems.managers;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayDeque;
import java.util.Iterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.util.Hand;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IClientPlayerInteractionManager;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.combat.Offhand;
import revivedevelopment.reviveclient.systems.modules.player.SilentMine;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;

public class SwapManager {
   private final AntiCheatConfig antiCheatConfig = AntiCheatConfig.get();
   private final Object swapLock = new Object();
   private SwapManager.SwapState multiTickSwapState = new SwapManager.SwapState();
   private SwapManager.SwapState instantSwapState = new SwapManager.SwapState();
   private String multiTickOwner = null;
   private static final int OFFHAND_CLICK_RESERVE = 2;
   private static final long ROUND_TRIP_ECHO_WINDOW_MS = 1500L;
   private final ArrayDeque<SwapManager.RoundTrip> recentRoundTrips = new ArrayDeque<>();
   private volatile boolean serverPushedSelectedSlot = false;
   private boolean multiTickHoldBrokenExternally = false;

   public SwapManager() {
      ReviveClient.EVENT_BUS.subscribe(this);
   }

   public boolean beginSwap(Item item, boolean instant) {
      FindItemResult result = InvUtils.findInHotbar(item);
      if (this.getItemSwapMode() == SwapManager.SwapMode.None && !result.isMainHand()) {
         return false;
      } else if (this.getItemSwapMode() == SwapManager.SwapMode.SilentHotbar && !result.found()) {
         return false;
      } else {
         if (!result.found()) {
            result = InvUtils.find(item);
         }

         return !result.found() ? false : this.beginSwap(result, instant);
      }
   }

   public boolean beginSwap(FindItemResult result, boolean instant) {
      return this.beginSwap(result, instant, false);
   }

   public boolean beginSwap(FindItemResult result, boolean instant, boolean yieldable) {
      if (!result.found()) {
         this.dbg("begin fail: item not found");
         return false;
      } else {
         this.dbg(
            "begin slot="
               + result.slot()
               + " ("
               + slotItemName(result.slot())
               + ") mainHand="
               + result.isMainHand()
               + " hotbar="
               + result.isHotbar()
               + " mode="
               + this.getItemSwapMode()
               + " instant="
               + instant
               + " yieldable="
               + yieldable
         );
         if (this.getItemSwapMode() == SwapManager.SwapMode.None && !result.isMainHand()) {
            this.dbg("begin fail: mode None and not main hand");
            return false;
         } else if (!instant && ReviveClient.mc.player.isUsingItem() && ReviveClient.mc.player.getActiveHand() == Hand.MAIN_HAND) {
            this.dbg("begin fail: using main-hand item (multi-tick)");
            return false;
         } else {
            if (!instant && this.multiTickSwapState.isSwapped && this.multiTickSwapState.yieldable) {
               boolean ownerRehold = this.multiTickOwner != null && this.multiTickOwner.equals(callerName());
               if (ownerRehold) {
                  this.dbg("owner re-targeting its multi-tick hold (released first)");
               } else {
                  this.dbg("yielding multi-tick hold (owner " + this.multiTickOwner + ") to incoming multi-tick swap");
               }

               this.releaseMultiTickHold();
               if (!ownerRehold) {
                  this.multiTickHoldBrokenExternally = true;
               }
            }

            synchronized (this.swapLock) {
               if (this.instantSwapState.isSwapped) {
                  this.dbg("begin fail: instant swap already held");
                  return false;
               }

               if (this.multiTickSwapState.isSwapped && !instant) {
                  this.dbg("begin fail: multi-tick swap already held");
                  return false;
               }

               this.getSwapState(instant).isSwapped = true;
               this.getSwapState(instant).yieldable = yieldable;
               if (!instant) {
                  this.multiTickOwner = callerName();
               }
            }

            SwapManager.SwapState swapState = this.getSwapState(instant);
            switch (this.getItemSwapMode()) {
               case None:
               default:
                  break;
               case Auto:
                  boolean shouldSilentSwap = !result.isHotbar()
                     || this.multiTickSwapState.isSwapped && instant
                     || instant && result.slot() != ReviveClient.mc.player.getInventory().selectedSlot && miningSessionLive()
                     || ReviveClient.mc.player.isUsingItem() && ReviveClient.mc.player.getActiveHand() == Hand.MAIN_HAND;
                  if (shouldSilentSwap) {
                     if (this.isScreenBlocking() || !this.canRoundTripSilentSwap()) {
                        this.getSwapState(instant).isSwapped = false;
                        if (!instant) {
                           this.multiTickOwner = null;
                        }

                        this.dbg(
                           "begin fail: Auto silent blocked (screen="
                              + this.isScreenBlocking()
                              + " clickSlotRemaining="
                              + ReviveClient.PACKET.remaining(PacketManager.Category.CLICK_SLOT)
                              + ")"
                        );
                        return false;
                     }

                     swapState.silentSwapInventorySlot = result.slot();
                     swapState.silentSwapSelectedSlot = ReviveClient.mc.player.getInventory().selectedSlot;
                     swapState.didSilentSwap = true;
                     swapState.captureSilentSwapStacks();
                     InvUtils.quickSwap().fromId(ReviveClient.mc.player.getInventory().selectedSlot).to(result.slot());
                     this.dbg("begin OK: Auto silent (quickSwap " + swapState.silentSwapSelectedSlot + "<->" + result.slot() + ")");
                  } else {
                     swapState.hotbarSelectedSlot = ReviveClient.mc.player.getInventory().selectedSlot;
                     swapState.hotbarItemSlot = result.slot();
                     swapState.didSilentSwap = false;
                     ReviveClient.mc.player.getInventory().selectedSlot = result.slot();
                     ((IClientPlayerInteractionManager)ReviveClient.mc.interactionManager).revive$syncSelected();
                     this.dbg("begin OK: Auto hotbar (select " + result.slot() + ")");
                  }
                  break;
               case SilentHotbar:
                  swapState.hotbarSelectedSlot = ReviveClient.mc.player.getInventory().selectedSlot;
                  swapState.hotbarItemSlot = result.slot();
                  swapState.didSilentSwap = false;
                  ReviveClient.mc.player.getInventory().selectedSlot = result.slot();
                  ((IClientPlayerInteractionManager)ReviveClient.mc.interactionManager).revive$syncSelected();
                  this.dbg("begin OK: SilentHotbar (select " + result.slot() + ")");
                  break;
               case SilentSwap:
                  if (this.isScreenBlocking() || !this.canRoundTripSilentSwap()) {
                     this.getSwapState(instant).isSwapped = false;
                     if (!instant) {
                        this.multiTickOwner = null;
                     }

                     this.dbg(
                        "begin fail: SilentSwap blocked (screen="
                           + this.isScreenBlocking()
                           + " clickSlotRemaining="
                           + ReviveClient.PACKET.remaining(PacketManager.Category.CLICK_SLOT)
                           + ")"
                     );
                     return false;
                  }

                  swapState.silentSwapInventorySlot = result.slot();
                  swapState.silentSwapSelectedSlot = ReviveClient.mc.player.getInventory().selectedSlot;
                  swapState.didSilentSwap = true;
                  swapState.captureSilentSwapStacks();
                  InvUtils.quickSwap().fromId(ReviveClient.mc.player.getInventory().selectedSlot).to(result.slot());
                  this.dbg("begin OK: SilentSwap (quickSwap " + swapState.silentSwapSelectedSlot + "<->" + result.slot() + ")");
            }

            return true;
         }
      }
   }

   private boolean canRoundTripSilentSwap() {
      return ReviveClient.PACKET.remaining(PacketManager.Category.CLICK_SLOT) >= 2 + offhandClickReserve();
   }

   private static int offhandClickReserve() {
      Offhand offhand = Modules.get().get(Offhand.class);
      return offhand != null && offhand.isActive() ? 2 : 0;
   }

   private void dbg(String msg) {
      if (this.antiCheatConfig.swapDebug.get()) {
         ChatUtils.infoPrefix("Swap", "%s", "(" + callerName() + ") " + msg);
      }
   }

   private static String callerName() {
      return StackWalker.getInstance()
         .walk(
            frames -> frames.map(StackFrame::getClassName)
               .filter(name -> !name.equals(SwapManager.class.getName()))
               .findFirst()
               .map(name -> name.substring(name.lastIndexOf(46) + 1))
               .orElse("?")
         );
   }

   private static String slotItemName(int slot) {
      if (ReviveClient.mc.player == null) {
         return "?";
      } else {
         ItemStack stack;
         if (slot == 45) {
            stack = ReviveClient.mc.player.getOffHandStack();
         } else {
            if (slot < 0 || slot >= ReviveClient.mc.player.getInventory().size()) {
               return "?";
            }

            stack = ReviveClient.mc.player.getInventory().getStack(slot);
         }

         return stack.isEmpty() ? "empty" : stack.getName().getString();
      }
   }

   public boolean canSwap(Item item) {
      return this.getSlot(item).found();
   }

   public FindItemResult getSlot(Item item) {
      FindItemResult result = InvUtils.findInHotbar(item);
      if (this.getItemSwapMode() == SwapManager.SwapMode.None && !result.isMainHand()) {
         return new FindItemResult(-1, 0);
      } else if (this.getItemSwapMode() == SwapManager.SwapMode.SilentHotbar && !result.found()) {
         return new FindItemResult(-1, 0);
      } else {
         if (!result.found()) {
            result = InvUtils.find(item);
         }

         return !result.found() ? new FindItemResult(-1, 0) : result;
      }
   }

   public void endSwap(boolean instantSwap) {
      synchronized (this.swapLock) {
         if (!this.getSwapState(instantSwap).isSwapped) {
            return;
         }

         if (!instantSwap && this.multiTickOwner != null && !callerName().equals(this.multiTickOwner)) {
            this.dbg("end (multi-tick) ignored: hold is owned by " + this.multiTickOwner);
            return;
         }
      }

      if (instantSwap) {
         SwapManager.SwapState swapState = this.instantSwapState;
         if (swapState.didSilentSwap) {
            this.silentSwapBack(swapState);
            this.dbg("end (instant): silent swapBack " + swapState.silentSwapSelectedSlot + "<->" + swapState.silentSwapInventorySlot);
         } else {
            ReviveClient.mc.player.getInventory().selectedSlot = swapState.hotbarSelectedSlot;
            ((IClientPlayerInteractionManager)ReviveClient.mc.interactionManager).revive$syncSelected();
            this.dbg("end (instant): restore slot " + swapState.hotbarSelectedSlot);
         }

         swapState.isSwapped = false;
      } else {
         this.releaseMultiTickHold();
      }
   }

   private void releaseMultiTickHold() {
      SwapManager.SwapState swapState = this.multiTickSwapState;
      if (swapState.didSilentSwap) {
         this.silentSwapBack(swapState);
         this.dbg("end (multi-tick): silent swapBack " + swapState.silentSwapSelectedSlot + "<->" + swapState.silentSwapInventorySlot);
      } else {
         ReviveClient.mc.player.getInventory().selectedSlot = swapState.hotbarSelectedSlot;
         ((IClientPlayerInteractionManager)ReviveClient.mc.interactionManager).revive$syncSelected();
         this.dbg("end (multi-tick): restore slot " + swapState.hotbarSelectedSlot);
      }

      synchronized (this.swapLock) {
         swapState.isSwapped = false;
         this.multiTickOwner = null;
      }
   }

   public SwapManager.SwapMode getItemSwapMode() {
      return this.antiCheatConfig.swapMode.get();
   }

   public SwapManager.SwapMode getSwapMode() {
      return this.getItemSwapMode();
   }

   public boolean isSilentSwapActive() {
      synchronized (this.swapLock) {
         return this.instantSwapState.isSwapped && this.instantSwapState.didSilentSwap;
      }
   }

   public boolean isInstantSwapContainerBased() {
      synchronized (this.swapLock) {
         return this.instantSwapState.isSwapped && this.instantSwapState.didSilentSwap;
      }
   }

   public boolean isMultiTickSwapActive() {
      synchronized (this.swapLock) {
         return this.multiTickSwapState.isSwapped;
      }
   }

   public boolean isYieldableHoldActive() {
      synchronized (this.swapLock) {
         return this.multiTickSwapState.isSwapped && this.multiTickSwapState.yieldable;
      }
   }

   public boolean isSlotLocked(int slot) {
      synchronized (this.swapLock) {
         return this.instantSwapState.isSwapped && this.instantSwapState.didSilentSwap && this.instantSwapState.silentSwapInventorySlot == slot
            ? true
            : this.multiTickSwapState.isSwapped && this.multiTickSwapState.didSilentSwap && this.multiTickSwapState.silentSwapInventorySlot == slot;
      }
   }

   private boolean isScreenBlocking() {
      if (!this.antiCheatConfig.swapAntiScreenClose.get()) {
         return false;
      } else {
         Screen s = ReviveClient.mc.currentScreen;
         return s instanceof HandledScreen && !(s instanceof InventoryScreen) && !(s instanceof CreativeInventoryScreen) && !(s instanceof ChatScreen);
      }
   }

   @EventHandler
   private void onReceive(PacketEvent.Receive event) {
      if (ReviveClient.mc.player != null) {
         if (event.packet instanceof UpdateSelectedSlotS2CPacket) {
            this.serverPushedSelectedSlot = true;
         }

         if (event.packet instanceof ScreenHandlerSlotUpdateS2CPacket packet && packet.getSyncId() == 0) {
            int index = playerContainerIdToIndex(packet.getSlot());
            if (index >= 0 && this.isStaleRoundTripEcho(index, packet.getStack())) {
               event.setCancelled(true);
               return;
            }
         }

         if (this.antiCheatConfig.grimSilentSwapFix.get()) {
            boolean swapActive = this.multiTickSwapState.isSwapped || this.instantSwapState.isSwapped;
            if (swapActive) {
               if (event.packet instanceof ScreenHandlerSlotUpdateS2CPacket packetx && packetx.getSyncId() == 0) {
                  int slot = packetx.getSlot();
                  if (slot >= 36 && slot <= 44) {
                     ItemStack incoming = packetx.getStack();
                     ItemStack main = ReviveClient.mc.player.getMainHandStack();
                     if (!incoming.isEmpty() && !main.isEmpty() && ItemStack.areItemsAndComponentsEqual(incoming, main)) {
                        event.setCancelled(true);
                     }
                  }
               }
            }
         }
      }
   }

   private void silentSwapBack(SwapManager.SwapState swapState) {
      InvUtils.quickSwap().fromId(swapState.silentSwapSelectedSlot).to(swapState.silentSwapInventorySlot);
      this.rememberRoundTrip(
         swapState.silentSwapSelectedSlot, swapState.silentSwapInventorySlot, swapState.silentSwapSelectedStack, swapState.silentSwapInventoryStack
      );
   }

   private void rememberRoundTrip(int slotA, int slotB, ItemStack finalA, ItemStack finalB) {
      if (ReviveClient.mc.player != null && slotA != slotB) {
         if (slotA >= 0 && slotA <= 35 && slotB >= 0 && slotB <= 35) {
            long expiresAt = System.currentTimeMillis() + 1500L;
            synchronized (this.recentRoundTrips) {
               this.recentRoundTrips.removeIf(rt -> rt.slotA == slotA && rt.slotB == slotB || rt.slotA == slotB && rt.slotB == slotA);
               this.recentRoundTrips.addLast(new SwapManager.RoundTrip(slotA, slotB, finalA, finalB, expiresAt));

               while (this.recentRoundTrips.size() > 16) {
                  this.recentRoundTrips.pollFirst();
               }
            }
         }
      }
   }

   private static int playerContainerIdToIndex(int id) {
      if (id >= 36 && id <= 44) {
         return id - 36;
      } else {
         return id >= 9 && id <= 35 ? id : -1;
      }
   }

   private static boolean sameItem(ItemStack a, ItemStack b) {
      return !a.isEmpty() && !b.isEmpty() ? ItemStack.areItemsAndComponentsEqual(a, b) : a.isEmpty() && b.isEmpty();
   }

   private boolean isStaleRoundTripEcho(int index, ItemStack incoming) {
      long now = System.currentTimeMillis();
      synchronized (this.recentRoundTrips) {
         this.recentRoundTrips.removeIf(rtx -> now > rtx.expiresAt);
         Iterator<SwapManager.RoundTrip> it = this.recentRoundTrips.descendingIterator();

         while (it.hasNext()) {
            SwapManager.RoundTrip rt = it.next();
            boolean isA = rt.slotA == index;
            if (isA || rt.slotB == index) {
               ItemStack own = isA ? rt.finalA : rt.finalB;
               ItemStack partner = isA ? rt.finalB : rt.finalA;
               if (sameItem(own, partner)) {
                  return false;
               } else if (sameItem(incoming, own)) {
                  return false;
               } else if (!sameItem(incoming, partner)) {
                  return false;
               } else if (isA ? !rt.droppedA : !rt.droppedB) {
                  if (isA) {
                     rt.droppedA = true;
                  } else {
                     rt.droppedB = true;
                  }

                  if (this.antiCheatConfig.swapDebug.get()) {
                     String name = incoming.isEmpty() ? "empty" : incoming.getName().getString();
                     String line = "dropped stale echo: slot "
                        + index
                        + " -> "
                        + name
                        + " (silent swap "
                        + rt.slotA
                        + "<->"
                        + rt.slotB
                        + " already swapped back)";
                     ReviveClient.mc.execute(() -> this.dbg(line));
                  }

                  return true;
               } else {
                  return false;
               }
            }
         }

         return false;
      }
   }

   private static boolean miningSessionLive() {
      SilentMine sm = SilentMine.get();
      return sm != null && sm.isRunningHere() && !sm.isPacketMode() && sm.hasDelayedDestroy();
   }

   public boolean consumeMultiTickHoldBroken() {
      boolean broken = this.multiTickHoldBrokenExternally;
      this.multiTickHoldBrokenExternally = false;
      return broken;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (ReviveClient.mc.world != null && ReviveClient.mc.player != null) {
         if (this.multiTickSwapState.isSwapped) {
            int expected = this.multiTickSwapState.didSilentSwap ? this.multiTickSwapState.silentSwapSelectedSlot : this.multiTickSwapState.hotbarItemSlot;
            if (expected != ReviveClient.mc.player.getInventory().selectedSlot) {
               this.dbg(
                  "multi-tick hold released: selected slot changed externally (held "
                     + expected
                     + ", now "
                     + ReviveClient.mc.player.getInventory().selectedSlot
                     + (this.serverPushedSelectedSlot ? ", server pushed a selected-slot update this tick" : "")
                     + ")"
               );
               if (this.multiTickSwapState.didSilentSwap) {
                  this.silentSwapBack(this.multiTickSwapState);
               }

               synchronized (this.swapLock) {
                  this.multiTickSwapState.isSwapped = false;
                  this.multiTickOwner = null;
               }

               this.multiTickHoldBrokenExternally = true;
            }
         }

         this.serverPushedSelectedSlot = false;
      }
   }

   private SwapManager.SwapState getSwapState(boolean instantSwap) {
      return instantSwap ? this.instantSwapState : this.multiTickSwapState;
   }

   private static final class RoundTrip {
      final int slotA;
      final int slotB;
      final ItemStack finalA;
      final ItemStack finalB;
      final long expiresAt;
      boolean droppedA;
      boolean droppedB;

      RoundTrip(int slotA, int slotB, ItemStack finalA, ItemStack finalB, long expiresAt) {
         this.slotA = slotA;
         this.slotB = slotB;
         this.finalA = finalA;
         this.finalB = finalB;
         this.expiresAt = expiresAt;
      }
   }

   public static enum SwapMode {
      None,
      Auto,
      SilentHotbar,
      SilentSwap;
   }

   private class SwapState {
      public boolean isSwapped = false;
      public boolean yieldable = false;
      public boolean didSilentSwap = false;
      public int hotbarSelectedSlot = 0;
      public int hotbarItemSlot = 0;
      public int silentSwapSelectedSlot = 0;
      public int silentSwapInventorySlot = 0;
      public ItemStack silentSwapSelectedStack = ItemStack.EMPTY;
      public ItemStack silentSwapInventoryStack = ItemStack.EMPTY;

      void captureSilentSwapStacks() {
         this.silentSwapSelectedStack = this.stackAt(this.silentSwapSelectedSlot);
         this.silentSwapInventoryStack = this.stackAt(this.silentSwapInventorySlot);
      }

      private ItemStack stackAt(int slot) {
         return ReviveClient.mc.player != null && slot >= 0 && slot < ReviveClient.mc.player.getInventory().size()
            ? ReviveClient.mc.player.getInventory().getStack(slot).copy()
            : ItemStack.EMPTY;
      }
   }
}
