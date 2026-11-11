package revivedevelopment.reviveclient.systems.modules.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import revivedevelopment.reviveclient.events.game.ItemStackTooltipEvent;
import revivedevelopment.reviveclient.events.render.TooltipDataEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.ItemNbt;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.player.EChestMemory;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.tooltip.BookTooltipComponent;
import revivedevelopment.reviveclient.utils.tooltip.ContainerTooltipComponent;
import revivedevelopment.reviveclient.utils.tooltip.TextTooltipComponent;

public class BetterTooltips extends Module {
   public static final Color ECHEST_COLOR = new Color(0, 50, 50);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPreviews = this.settings.createGroup("Previews");
   private final SettingGroup sgOther = this.settings.createGroup("Other");
   private final SettingGroup sgHideFlags = this.settings.createGroup("Hide Flags");
   private final Setting<BetterTooltips.DisplayWhen> displayWhen = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("display-when"))
                     .description("When to display previews."))
                  .defaultValue(BetterTooltips.DisplayWhen.Keybind))
               .onChanged(value -> this.updateTooltips = true))
            .build()
      );
   private final Setting<Keybind> keybind = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("keybind")
            .description("The bind for keybind mode.")
            .defaultValue(Keybind.fromKey(342))
            .visible(() -> this.displayWhen.get() == BetterTooltips.DisplayWhen.Keybind)
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> shulkers = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("containers")
            .description("Shows a preview of a containers when hovering over it in an inventory.")
            .defaultValue(true)
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> shulkerFirstItem = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("shulker-first-item")
            .description("Shows a small icon of the first item inside a container in the upper left corner of its icon when viewing it in a container.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> shulkerIconMostCommon = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("shulker-icon-most-common")
            .description("The shulker-first-item icon shows the most common item inside the container instead of the first one.")
            .defaultValue(false)
            .visible(this.shulkerFirstItem::get)
            .build()
      );
   private final Setting<Boolean> shulkerMultiIndicator = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("shulker-multiple-indicator")
            .description("Draw a small \"+\" on the slot when the container holds more than one type of item.")
            .defaultValue(false)
            .visible(this.shulkerFirstItem::get)
            .build()
      );
   private final Setting<Boolean> shulkerIconHotbar = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("shulker-icon-hotbar")
            .description("Also draw the shulker icon on containers in your hotbar and offhand.")
            .defaultValue(true)
            .visible(this.shulkerFirstItem::get)
            .build()
      );
   public final Setting<Boolean> echest = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("echests")
            .description("Shows a preview of your echest when hovering over it in an inventory.")
            .defaultValue(true)
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> books = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("books")
            .description("Shows contents of a book when hovering over it in an inventory.")
            .defaultValue(true)
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   public final Setting<Boolean> byteSize = this.sgOther
      .add(
         new BoolSetting.Builder()
            .name("byte-size")
            .description("Displays an item's size in bytes in the tooltip.")
            .defaultValue(false)
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   public final Setting<Boolean> tooltip = this.sgHideFlags
      .add(new BoolSetting.Builder().name("tooltip").description("Show the tooltip when it's hidden.").defaultValue(false).build());
   private boolean updateTooltips = false;
   private static final ItemStack[] ITEMS = new ItemStack[27];
   private ItemStack byteSizeStack = ItemStack.EMPTY;
   private int byteSizeCount = -1;

   public BetterTooltips() {
      super(Categories.Render, "better-tooltips", "Displays more useful tooltips for certain items.");
   }

   @EventHandler
   private void appendTooltip(ItemStackTooltipEvent event) {
      if (!this.tooltip.get() && event.list().isEmpty()) {
         this.appendPreviewTooltipText(event, false);
      } else {
         if (this.byteSize.get()) {
            int bytes = this.getByteSize(event.itemStack());
            event.appendEnd(
               bytes < 0 ? Text.literal("Error getting bytes.").formatted(Formatting.RED) : Text.literal(ItemNbt.formatBytes(bytes)).formatted(Formatting.GRAY)
            );
         }

         this.appendPreviewTooltipText(event, true);
      }
   }

   @EventHandler
   private void getTooltipData(TooltipDataEvent event) {
      if (this.previewShulkers() && Utils.hasItems(event.itemStack)) {
         Utils.getItemsInContainerItem(event.itemStack, ITEMS);
         event.tooltipData = new ContainerTooltipComponent(ITEMS, Utils.getShulkerColor(event.itemStack));
      } else if (event.itemStack.getItem() == Items.ENDER_CHEST && this.previewEChest()) {
         event.tooltipData = (TooltipData)(EChestMemory.isKnown()
            ? new ContainerTooltipComponent((ItemStack[])EChestMemory.ITEMS.toArray(new ItemStack[27]), ECHEST_COLOR)
            : new TextTooltipComponent(Text.literal("Unknown ender chest inventory.").formatted(Formatting.DARK_RED)));
      } else if ((event.itemStack.getItem() == Items.WRITABLE_BOOK || event.itemStack.getItem() == Items.WRITTEN_BOOK) && this.previewBooks()) {
         Text page = this.getFirstPage(event.itemStack);
         if (page != null) {
            event.tooltipData = new BookTooltipComponent(page);
         }
      }
   }

   private void appendPreviewTooltipText(ItemStackTooltipEvent event, boolean spacer) {
      if (!this.isPressed()
         && (
            this.shulkers.get() && Utils.hasItems(event.itemStack())
               || event.itemStack().getItem() == Items.ENDER_CHEST && this.echest.get()
               || event.itemStack().getItem() == Items.WRITABLE_BOOK && this.books.get()
               || event.itemStack().getItem() == Items.WRITTEN_BOOK && this.books.get()
         )) {
         if (spacer) {
            event.appendEnd(Text.literal(""));
         }

         event.appendEnd(Text.literal("Hold " + Formatting.YELLOW + this.keybind + Formatting.RESET + " to preview"));
      }
   }

   private int getByteSize(ItemStack stack) {
      if (!ItemStack.areEqual(stack, this.byteSizeStack)) {
         this.byteSizeStack = stack.copy();
         this.byteSizeCount = ItemNbt.byteSize(stack);
      }

      return this.byteSizeCount;
   }

   private Text getFirstPage(ItemStack bookItem) {
      if (bookItem.get(DataComponentTypes.WRITABLE_BOOK_CONTENT) != null) {
         List<RawFilteredPair<String>> pages = ((WritableBookContentComponent)bookItem.get(DataComponentTypes.WRITABLE_BOOK_CONTENT)).pages();
         return pages.isEmpty() ? null : Text.literal((String)pages.getFirst().get(false));
      } else if (bookItem.get(DataComponentTypes.WRITTEN_BOOK_CONTENT) != null) {
         List<RawFilteredPair<Text>> pages = ((WrittenBookContentComponent)bookItem.get(DataComponentTypes.WRITTEN_BOOK_CONTENT)).pages();
         return pages.isEmpty() ? null : (Text)pages.getFirst().get(false);
      } else {
         return null;
      }
   }

   public boolean previewShulkers() {
      return this.isActive() && this.isPressed() && this.shulkers.get();
   }

   public boolean shulkerFirstItem() {
      return this.isActive() && this.shulkerFirstItem.get();
   }

   public boolean shulkerIconHotbar() {
      return this.shulkerFirstItem() && this.shulkerIconHotbar.get();
   }

   public boolean shulkerMultiIndicator() {
      return this.shulkerMultiIndicator.get();
   }

   public ItemStack shulkerIconFor(ItemStack[] items) {
      if (this.shulkerIconMostCommon.get()) {
         Map<Item, Integer> counts = new HashMap<>();

         for (ItemStack s : items) {
            if (s != null && !s.isEmpty()) {
               counts.merge(s.getItem(), s.getCount(), Integer::sum);
            }
         }

         Item best = null;
         int bestCount = -1;

         for (Entry<Item, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
               bestCount = e.getValue();
               best = e.getKey();
            }
         }

         return best != null ? new ItemStack(best) : ItemStack.EMPTY;
      } else {
         for (ItemStack sx : items) {
            if (sx != null && !sx.isEmpty()) {
               return sx;
            }
         }

         return ItemStack.EMPTY;
      }
   }

   public boolean shulkerHasMultipleTypes(ItemStack[] items) {
      Item first = null;

      for (ItemStack s : items) {
         if (s != null && !s.isEmpty()) {
            if (first == null) {
               first = s.getItem();
            } else if (s.getItem() != first) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean previewEChest() {
      return this.isPressed() && this.echest.get();
   }

   private boolean previewBooks() {
      return this.isPressed() && this.books.get();
   }

   private boolean isPressed() {
      return this.keybind.get().isPressed() && this.displayWhen.get() == BetterTooltips.DisplayWhen.Keybind
         || this.displayWhen.get() == BetterTooltips.DisplayWhen.Always;
   }

   public boolean updateTooltips() {
      if (this.updateTooltips && this.isActive()) {
         this.updateTooltips = false;
         return true;
      } else {
         return false;
      }
   }

   public static enum DisplayWhen {
      Keybind,
      Always;
   }
}
