package revivedevelopment.reviveclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.hunting.LoreLocator;
import revivedevelopment.reviveclient.systems.modules.player.InventorySearch;
import revivedevelopment.reviveclient.systems.modules.player.InventorySorter;
import revivedevelopment.reviveclient.systems.modules.player.ShiftDrag;
import revivedevelopment.reviveclient.systems.modules.render.BetterTooltips;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

@Mixin({HandledScreen.class})
public abstract class HandledScreenMixin {
   @Unique
   private static final ItemStack[] REVIVE_FIRST_ITEMS = new ItemStack[27];

   @Unique
   private static InventorySearch revive$search() {
      Modules modules = Modules.get();
      if (modules == null) {
         return null;
      } else {
         InventorySearch s = modules.get(InventorySearch.class);
         return s != null && s.isActive() ? s : null;
      }
   }

   @Inject(
      method = {"method_25394"},
      at = {@At("TAIL")}
   )
   private void revive$renderSearch(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
      InventorySearch s = revive$search();
      if (s != null) {
         s.onRender((HandledScreen<?>)(Object)this, context, mouseX, mouseY);
      }
   }

   @Inject(
      method = {"method_2385"},
      at = {@At("HEAD")}
   )
   private void revive$onDrawSlotSearchPre(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo info) {
      InventorySearch s = revive$search();
      if (s != null) {
         s.onDrawSlotPre(context, slot);
      }
   }

   @Inject(
      method = {"method_2385"},
      at = {@At("TAIL")}
   )
   private void revive$onDrawSlotSearchPost(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo info) {
      InventorySearch s = revive$search();
      if (s != null) {
         s.onDrawSlotPost(context, slot);
      }
   }

   @Inject(
      method = {"method_25402"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$onMouseClickedSearch(Click click, boolean doubled, CallbackInfoReturnable<Boolean> info) {
      InventorySearch s = revive$search();
      if (s != null && s.onMouseClicked((HandledScreen<?>)(Object)this, click.x(), click.y(), click.button())) {
         info.setReturnValue(true);
      }
   }

   @Inject(
      method = {"method_25404"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$onKeyPressedSearch(KeyInput input, CallbackInfoReturnable<Boolean> info) {
      InventorySearch s = revive$search();
      if (s != null && s.onKeyPressed(input.key(), input.modifiers())) {
         info.setReturnValue(true);
      }
   }

   @Inject(
      method = {"method_25402"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void revive$onMouseClickedSort(Click click, boolean doubled, CallbackInfoReturnable<Boolean> info) {
      if (click.button() == 2) {
         InventorySorter sorter = Modules.get().get(InventorySorter.class);
         if (sorter != null && sorter.isActive()) {
            Slot slot = ((HandledScreenAccessor)this).getFocusedSlot();
            if (slot != null) {
               if (sorter.onSlotMiddleClick((HandledScreen<?>)(Object)this, slot)) {
                  info.setReturnValue(true);
               }
            }
         }
      }
   }

   @Inject(
      method = {"method_2385"},
      at = {@At("HEAD")}
   )
   private void revive$onDrawSlotLoreLocator(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo info) {
      LoreLocator lore = Modules.get().get(LoreLocator.class);
      if (lore != null && lore.isActive()) {
         ItemStack stack = slot.getStack();
         if (!stack.isEmpty() && lore.shouldHighlightSlot(stack)) {
            SettingColor c = lore.color.get();
            int argb = (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, argb);
         }
      }
   }

   @Inject(
      method = {"method_2385"},
      at = {@At("TAIL")}
   )
   private void revive$onDrawSlotShiftDrag(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo info) {
      if (slot == ((HandledScreenAccessor)this).getFocusedSlot()) {
         ShiftDrag shiftDrag = Modules.get().get(ShiftDrag.class);
         if (shiftDrag != null && shiftDrag.isActive()) {
            shiftDrag.onHoveredSlot(slot);
         }
      }
   }

   @Inject(
      method = {"method_2385"},
      at = {@At("TAIL")}
   )
   private void revive$onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo info) {
      BetterTooltips tooltips = Modules.get().get(BetterTooltips.class);
      if (tooltips != null && tooltips.shulkerFirstItem()) {
         ItemStack stack = slot.getStack();
         if (!stack.isEmpty() && Utils.hasItems(stack)) {
            Utils.getItemsInContainerItem(stack, REVIVE_FIRST_ITEMS);
            ItemStack icon = tooltips.shulkerIconFor(REVIVE_FIRST_ITEMS);
            if (!icon.isEmpty()) {
               RenderUtils.drawItem(context, icon, slot.x, slot.y, 0.5F, false, null, false);
               if (tooltips.shulkerMultiIndicator() && tooltips.shulkerHasMultipleTypes(REVIVE_FIRST_ITEMS)) {
                  TextRenderer tr = MinecraftClient.getInstance().textRenderer;
                  context.drawText(tr, "+", slot.x + 16 - tr.getWidth("+") - 1, slot.y + 1, -256, true);
               }
            }
         }
      }
   }
}
