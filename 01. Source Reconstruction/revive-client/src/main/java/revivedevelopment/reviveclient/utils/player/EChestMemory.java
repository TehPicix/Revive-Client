package revivedevelopment.reviveclient.utils.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.collection.DefaultedList;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.OpenScreenEvent;
import revivedevelopment.reviveclient.events.world.BlockActivateEvent;
import revivedevelopment.reviveclient.utils.PreInit;

public class EChestMemory {
   public static final DefaultedList<ItemStack> ITEMS = DefaultedList.ofSize(27, ItemStack.EMPTY);
   private static int echestOpenedState;
   private static boolean isKnown = false;

   private EChestMemory() {
   }

   @PreInit
   public static void init() {
      ReviveClient.EVENT_BUS.subscribe(EChestMemory.class);
   }

   @EventHandler
   private static void onBlockActivate(BlockActivateEvent event) {
      if (event.blockState.getBlock() instanceof EnderChestBlock && echestOpenedState == 0) {
         echestOpenedState = 1;
      }
   }

   @EventHandler
   private static void onOpenScreenEvent(OpenScreenEvent event) {
      if (echestOpenedState == 1 && event.screen instanceof GenericContainerScreen) {
         echestOpenedState = 2;
      } else if (echestOpenedState != 0) {
         if (ReviveClient.mc.currentScreen instanceof GenericContainerScreen) {
            GenericContainerScreenHandler container = (GenericContainerScreenHandler)((GenericContainerScreen)ReviveClient.mc.currentScreen).getScreenHandler();
            if (container != null) {
               Inventory inv = container.getInventory();

               for (int i = 0; i < 27; i++) {
                  ITEMS.set(i, inv.getStack(i));
               }

               isKnown = true;
               echestOpenedState = 0;
            }
         }
      }
   }

   @EventHandler
   private static void onLeaveEvent(GameLeftEvent event) {
      ITEMS.clear();
      isKnown = false;
   }

   public static boolean isKnown() {
      return isKnown;
   }
}
