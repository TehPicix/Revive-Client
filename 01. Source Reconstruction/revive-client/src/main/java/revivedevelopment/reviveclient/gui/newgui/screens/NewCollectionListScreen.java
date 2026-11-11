package revivedevelopment.reviveclient.gui.newgui.screens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.Utils;

public class NewCollectionListScreen<T> extends Screen {
   private final Setting<?> setting;
   private final Collection<T> collection;
   private final Iterable<T> registry;
   private final Function<T, String> nameFn;
   private final Function<T, ItemStack> iconFn;
   private final Predicate<T> skipFn;
   private String filterText = "";
   private boolean filterFocused = true;
   private int availableScroll = 0;
   private int addedScroll = 0;
   private int panelWidth;
   private int panelY;
   private int panelHeight;
   private int searchY;
   private int availableX;
   private int addedX;
   private static final int PADDING = 6;
   private static final int SEARCH_HEIGHT_MULT = 2;
   private static final int ICON_SIZE = 16;
   public Screen parent;

   public NewCollectionListScreen(String title, Setting<?> setting, Collection<T> collection, Iterable<T> registry, Function<T, String> nameFn) {
      this(title, setting, collection, registry, nameFn, null, null);
   }

   public void close() {
      if (this.parent != null) {
         this.client.setScreen(this.parent);
      } else {
         super.close();
      }
   }

   public NewCollectionListScreen(
      String title,
      Setting<?> setting,
      Collection<T> collection,
      Iterable<T> registry,
      Function<T, String> nameFn,
      Function<T, ItemStack> iconFn,
      Predicate<T> skipFn
   ) {
      super(Text.literal(title));
      this.setting = setting;
      this.collection = collection;
      this.registry = registry;
      this.nameFn = nameFn;
      this.iconFn = iconFn;
      this.skipFn = skipFn;
   }

   protected void init() {
      super.init();
      int screenW = this.width;
      int screenH = this.height;
      int gap = 8;
      int margin = Math.max(20, screenW / 10);
      this.panelWidth = (screenW - margin * 2 - gap) / 2;
      int searchH = FontManager.get().getRowHeight() * 2;
      this.searchY = 20;
      this.panelY = this.searchY + searchH + gap;
      this.panelHeight = screenH - this.panelY - 20;
      this.availableX = margin;
      this.addedX = margin + this.panelWidth + gap;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      FontManager fm = FontManager.get();
      super.render(context, mouseX, mouseY, deltaTicks);
      int lineColor = fm.primaryAlpha(180);
      int fillColor = fm.secondaryAlpha(200);
      int headerHeight = fm.getHeaderHeight();
      int rowH = fm.getRowHeight();
      fm.drawText(context, this.title.getString(), this.availableX, 4, fm.getTextColor());
      int searchH = rowH * 2;
      int searchW = this.panelWidth * 2 + 8;
      boolean searchHovered = hit(mouseX, mouseY, this.availableX, this.searchY, searchW, searchH);
      context.fill(
         this.availableX, this.searchY, this.availableX + searchW, this.searchY + searchH, this.filterFocused ? fm.secondaryAlpha(220) : fm.secondaryAlpha(150)
      );
      RenderUtils.drawThinOutline(context, this.availableX, this.searchY, searchW, searchH, lineColor);
      String searchLabel = "Search: ";
      int labelW = fm.getTextWidth(searchLabel);
      fm.drawText(context, searchLabel, this.availableX + 6, this.searchY + (searchH - fm.getTextHeight()) / 2, GuiColors.TEXT_SETTING_VALUE);
      fm.drawText(context, this.filterText, this.availableX + 6 + labelW, this.searchY + (searchH - fm.getTextHeight()) / 2, fm.getTextColor());
      if (this.filterFocused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
         int caretX = this.availableX + 6 + labelW + fm.getTextWidth(this.filterText);
         context.fill(caretX, this.searchY + 3, caretX + 1, this.searchY + searchH - 3, fm.getTextColor());
      }

      String clearHint = "[Clear]";
      fm.drawText(
         context,
         clearHint,
         this.availableX + searchW - fm.getTextWidth(clearHint) - 6,
         this.searchY + (searchH - fm.getTextHeight()) / 2,
         GuiColors.TEXT_SETTING_VALUE
      );
      List<NewCollectionListScreen.Ranked<T>> availableList = new ArrayList<>();
      List<NewCollectionListScreen.Ranked<T>> addedList = new ArrayList<>();

      for (T value : this.registry) {
         if (this.skipFn == null || !this.skipFn.test(value)) {
            String name = this.nameFn.apply(value);
            int rank = rankMatch(name, this.filterText);
            if (rank != Integer.MIN_VALUE) {
               if (this.collection.contains(value)) {
                  addedList.add(new NewCollectionListScreen.Ranked<>(value, name, rank));
               } else {
                  availableList.add(new NewCollectionListScreen.Ranked<>(value, name, rank));
               }
            }
         }
      }

      if (this.filterText.isEmpty()) {
         availableList.sort(Comparator.comparing(r -> r.name));
         addedList.sort(Comparator.comparing(r -> r.name));
      } else {
         availableList.sort((a, b) -> b.rank - a.rank);
         addedList.sort((a, b) -> b.rank - a.rank);
      }

      this.renderPanel(
         context,
         fm,
         "Available (" + availableList.size() + ")",
         this.availableX,
         this.panelY,
         this.panelWidth,
         this.panelHeight,
         availableList,
         this.availableScroll,
         mouseX,
         mouseY,
         fillColor,
         lineColor,
         headerHeight,
         rowH,
         true
      );
      this.renderPanel(
         context,
         fm,
         "Added (" + addedList.size() + ")",
         this.addedX,
         this.panelY,
         this.panelWidth,
         this.panelHeight,
         addedList,
         this.addedScroll,
         mouseX,
         mouseY,
         fillColor,
         lineColor,
         headerHeight,
         rowH,
         false
      );
   }

   private void renderPanel(
      DrawContext context,
      FontManager fm,
      String heading,
      int px,
      int py,
      int pw,
      int ph,
      List<NewCollectionListScreen.Ranked<T>> items,
      int scroll,
      int mouseX,
      int mouseY,
      int fillColor,
      int lineColor,
      int headerH,
      int rowH,
      boolean isAvailable
   ) {
      context.fill(px, py, px + pw, py + ph, fm.primaryAlpha(120));
      RenderUtils.drawThinOutline(context, px, py, pw, ph, lineColor);
      context.fill(px, py, px + pw, py + headerH, fillColor);
      RenderUtils.drawThinHLine(context, px, py + headerH, pw, lineColor);
      fm.drawText(context, heading, px + 6, py + (headerH - fm.getTextHeight()) / 2, fm.getTextColor());
      int effectiveRowH = this.iconFn != null ? Math.max(rowH, 18) : rowH;
      int rowY = py + headerH + 1 - scroll;
      int rowEnd = py + ph;
      context.enableScissor(px + 1, py + headerH + 1, px + pw - 1, rowEnd - 1);
      if (items.isEmpty()) {
         String msg = isAvailable
            ? (this.filterText.isEmpty() ? "(no items to add)" : "(no matches)")
            : (this.filterText.isEmpty() ? "(nothing added)" : "(no matches)");
         int tx = px + (pw - fm.getTextWidth(msg)) / 2;
         int ty = py + headerH + (ph - headerH - fm.getTextHeight()) / 2;
         fm.drawText(context, msg, tx, ty, GuiColors.TEXT_DISABLED);
      }

      for (NewCollectionListScreen.Ranked<T> r : items) {
         if (rowY + effectiveRowH < py + headerH + 1) {
            rowY += effectiveRowH;
         } else {
            if (rowY > rowEnd) {
               break;
            }

            boolean rowHovered = hit(mouseX, mouseY, px + 1, rowY, pw - 2, effectiveRowH) && mouseY >= py + headerH + 1 && mouseY <= rowEnd - 1;
            if (rowHovered) {
               context.fill(px + 1, rowY, px + pw - 1, rowY + effectiveRowH, fm.secondaryAlpha(120));
            }

            int tcolor = rowHovered ? fm.getTextColor() : fm.getTextSecondary();
            int textX = px + 6;
            if (this.iconFn != null) {
               ItemStack icon = this.iconFn.apply(r.value);
               if (icon != null && !icon.isEmpty()) {
                  int iconY = rowY + (effectiveRowH - 16) / 2;
                  context.drawItem(icon, textX, iconY);
                  textX += 20;
               }
            }

            String label = this.iconFn != null ? r.name : (isAvailable ? "+ " : "- ") + r.name;
            fm.drawText(context, label, textX, rowY + (effectiveRowH - fm.getTextHeight()) / 2, tcolor);
            rowY += effectiveRowH;
         }
      }

      context.disableScissor();
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      int headerH = fm.getHeaderHeight();
      int searchH = rowH * 2;
      int searchW = this.panelWidth * 2 + 8;
      if (hit((int)mouseX, (int)mouseY, this.availableX, this.searchY, searchW, searchH)) {
         this.filterFocused = true;
         String clearHint = "[Clear]";
         int clearX = this.availableX + searchW - fm.getTextWidth(clearHint) - 6;
         if (mouseX >= clearX) {
            this.filterText = "";
            this.availableScroll = 0;
            this.addedScroll = 0;
         }

         return true;
      } else {
         this.filterFocused = false;
         boolean inAvailable = hit((int)mouseX, (int)mouseY, this.availableX, this.panelY, this.panelWidth, this.panelHeight);
         boolean inAdded = hit((int)mouseX, (int)mouseY, this.addedX, this.panelY, this.panelWidth, this.panelHeight);
         if (!inAvailable && !inAdded) {
            return super.mouseClicked(click, doubled);
         } else {
            if (!inAvailable) {
               ;
            }

            int scroll = inAvailable ? this.availableScroll : this.addedScroll;
            int effectiveRowH = this.iconFn != null ? Math.max(rowH, 18) : rowH;
            int rowY = this.panelY + headerH + 1 - scroll;
            int rowEnd = this.panelY + this.panelHeight;
            List<NewCollectionListScreen.Ranked<T>> items = new ArrayList<>();

            for (T value : this.registry) {
               if (this.skipFn == null || !this.skipFn.test(value)) {
                  String name = this.nameFn.apply(value);
                  int rank = rankMatch(name, this.filterText);
                  if (rank != Integer.MIN_VALUE) {
                     boolean inColl = this.collection.contains(value);
                     if (inAvailable && !inColl) {
                        items.add(new NewCollectionListScreen.Ranked<>(value, name, rank));
                     } else if (inAdded && inColl) {
                        items.add(new NewCollectionListScreen.Ranked<>(value, name, rank));
                     }
                  }
               }
            }

            if (this.filterText.isEmpty()) {
               items.sort(Comparator.comparing(rx -> rx.name));
            } else {
               items.sort((a, b) -> b.rank - a.rank);
            }

            for (NewCollectionListScreen.Ranked<T> r : items) {
               if (mouseY >= rowY && mouseY < rowY + effectiveRowH && mouseY >= this.panelY + headerH + 1 && mouseY <= rowEnd - 1) {
                  if (inAvailable) {
                     this.collection.add(r.value);
                  } else {
                     this.collection.remove(r.value);
                  }

                  this.setting.onChanged();
                  return true;
               }

               rowY += effectiveRowH;
            }

            return super.mouseClicked(click, doubled);
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      FontManager fm = FontManager.get();
      int headerH = fm.getHeaderHeight();
      int rowH = fm.getRowHeight();
      int effectiveRowH = this.iconFn != null ? Math.max(rowH, 18) : rowH;
      int avail = 0;
      int added = 0;

      for (T v : this.registry) {
         if (this.skipFn == null || !this.skipFn.test(v)) {
            String name = this.nameFn.apply(v);
            if (rankMatch(name, this.filterText) != Integer.MIN_VALUE) {
               if (this.collection.contains(v)) {
                  added++;
               } else {
                  avail++;
               }
            }
         }
      }

      int contentAvail = Math.max(0, avail * effectiveRowH - (this.panelHeight - headerH));
      int contentAdded = Math.max(0, added * effectiveRowH - (this.panelHeight - headerH));
      if (hit((int)mouseX, (int)mouseY, this.availableX, this.panelY, this.panelWidth, this.panelHeight)) {
         this.availableScroll -= (int)(verticalAmount * 14.0);
         this.availableScroll = Math.max(0, Math.min(this.availableScroll, contentAvail));
         return true;
      } else if (hit((int)mouseX, (int)mouseY, this.addedX, this.panelY, this.panelWidth, this.panelHeight)) {
         this.addedScroll -= (int)(verticalAmount * 14.0);
         this.addedScroll = Math.max(0, Math.min(this.addedScroll, contentAdded));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int modifiers = input.modifiers();
      if (this.filterFocused) {
         if (keyCode == 259) {
            if (!this.filterText.isEmpty()) {
               this.filterText = this.filterText.substring(0, this.filterText.length() - 1);
            }

            this.availableScroll = 0;
            this.addedScroll = 0;
            return true;
         }

         if (keyCode == 256) {
            this.filterFocused = false;
            return true;
         }

         if (keyCode == 257) {
            this.filterFocused = false;
            return true;
         }
      } else if (keyCode == 256) {
         this.close();
         return true;
      }

      return super.keyPressed(input);
   }

   public boolean charTyped(CharInput input) {
      char chr = (char)input.codepoint();
      int modifiers = input.modifiers();
      if (this.filterFocused && chr >= ' ' && chr != 127) {
         this.filterText = this.filterText + chr;
         this.availableScroll = 0;
         this.addedScroll = 0;
         return true;
      } else {
         return super.charTyped(input);
      }
   }

   public boolean shouldPause() {
      return false;
   }

   private static boolean hit(int mx, int my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private static int rankMatch(String name, String filter) {
      if (filter.isEmpty()) {
         return 0;
      } else {
         int words = Utils.searchInWords(name, filter);
         int diff = Utils.searchLevenshteinDefault(name, filter, false);
         return words <= 0 && diff > name.length() / 2 ? Integer.MIN_VALUE : -diff;
      }
   }

   private static class Ranked<T> {
      final T value;
      final String name;
      final int rank;

      Ranked(T value, String name, int rank) {
         this.value = value;
         this.name = name;
         this.rank = rank;
      }
   }
}
