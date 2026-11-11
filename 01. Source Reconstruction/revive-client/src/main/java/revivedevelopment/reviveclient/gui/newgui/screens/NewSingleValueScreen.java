package revivedevelopment.reviveclient.gui.newgui.screens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
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

public class NewSingleValueScreen<T> extends Screen {
   private final Setting<T> setting;
   private final Iterable<T> registry;
   private final Function<T, String> nameFn;
   private final Function<T, ItemStack> iconFn;
   private final Predicate<T> skipFn;
   private final Consumer<T> onPick;
   private String filterText = "";
   private boolean filterFocused = true;
   private int scroll = 0;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private int searchY;
   private static final int PADDING = 6;
   private static final int SEARCH_HEIGHT_MULT = 2;
   private static final int ICON_SIZE = 16;

   public NewSingleValueScreen(
      String title,
      Setting<T> setting,
      Iterable<T> registry,
      Function<T, String> nameFn,
      Function<T, ItemStack> iconFn,
      Predicate<T> skipFn,
      Consumer<T> onPick
   ) {
      super(Text.literal(title));
      this.setting = setting;
      this.registry = registry;
      this.nameFn = nameFn;
      this.iconFn = iconFn;
      this.skipFn = skipFn;
      this.onPick = onPick;
   }

   protected void init() {
      super.init();
      int marginX = Math.max(40, this.width / 4);
      this.panelX = marginX;
      this.panelWidth = this.width - marginX * 2;
      int searchH = FontManager.get().getRowHeight() * 2;
      this.searchY = 20;
      this.panelY = this.searchY + searchH + 8;
      this.panelHeight = this.height - this.panelY - 20;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      FontManager fm = FontManager.get();
      super.render(context, mouseX, mouseY, deltaTicks);
      int lineColor = fm.primaryAlpha(180);
      int headerH = fm.getHeaderHeight();
      int rowH = fm.getRowHeight();
      int effectiveRowH = this.iconFn != null ? Math.max(rowH, 18) : rowH;
      fm.drawText(context, this.title.getString(), this.panelX, 4, fm.getTextColor());
      int searchH = rowH * 2;
      context.fill(
         this.panelX, this.searchY, this.panelX + this.panelWidth, this.searchY + searchH, this.filterFocused ? fm.secondaryAlpha(220) : fm.secondaryAlpha(150)
      );
      RenderUtils.drawThinOutline(context, this.panelX, this.searchY, this.panelWidth, searchH, lineColor);
      String searchLabel = "Search: ";
      int labelW = fm.getTextWidth(searchLabel);
      int searchTextY = this.searchY + (searchH - fm.getTextHeight()) / 2;
      fm.drawText(context, searchLabel, this.panelX + 6, searchTextY, GuiColors.TEXT_SETTING_VALUE);
      fm.drawText(context, this.filterText, this.panelX + 6 + labelW, searchTextY, fm.getTextColor());
      if (this.filterFocused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
         int caretX = this.panelX + 6 + labelW + fm.getTextWidth(this.filterText);
         context.fill(caretX, this.searchY + 3, caretX + 1, this.searchY + searchH - 3, fm.getTextColor());
      }

      String clearHint = "[Clear]";
      fm.drawText(context, clearHint, this.panelX + this.panelWidth - fm.getTextWidth(clearHint) - 6, searchTextY, GuiColors.TEXT_SETTING_VALUE);
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, fm.primaryAlpha(120));
      RenderUtils.drawThinOutline(context, this.panelX, this.panelY, this.panelWidth, this.panelHeight, lineColor);
      List<NewSingleValueScreen.Ranked<T>> items = this.buildFilteredList();
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + headerH, fm.secondaryAlpha(200));
      RenderUtils.drawThinHLine(context, this.panelX, this.panelY + headerH, this.panelWidth, lineColor);
      String heading = "Choose (" + items.size() + ")";
      fm.drawText(context, heading, this.panelX + 6, this.panelY + (headerH - fm.getTextHeight()) / 2, fm.getTextColor());
      int rowY = this.panelY + headerH + 1 - this.scroll;
      int rowEnd = this.panelY + this.panelHeight;
      context.enableScissor(this.panelX + 1, this.panelY + headerH + 1, this.panelX + this.panelWidth - 1, rowEnd - 1);
      T current = this.setting.get();

      for (NewSingleValueScreen.Ranked<T> r : items) {
         if (rowY + effectiveRowH < this.panelY + headerH + 1) {
            rowY += effectiveRowH;
         } else {
            if (rowY > rowEnd) {
               break;
            }

            boolean hovered = hit(mouseX, mouseY, this.panelX + 1, rowY, this.panelWidth - 2, effectiveRowH)
               && mouseY >= this.panelY + headerH + 1
               && mouseY <= rowEnd - 1;
            boolean isCurrent = current != null && current.equals(r.value);
            if (hovered || isCurrent) {
               context.fill(this.panelX + 1, rowY, this.panelX + this.panelWidth - 1, rowY + effectiveRowH, fm.secondaryAlpha(hovered ? 150 : 90));
            }

            int tcolor = !hovered && !isCurrent ? fm.getTextSecondary() : fm.getTextColor();
            int textX = this.panelX + 6;
            if (this.iconFn != null) {
               ItemStack icon = this.iconFn.apply(r.value);
               if (icon != null && !icon.isEmpty()) {
                  int iconY = rowY + (effectiveRowH - 16) / 2;
                  context.drawItem(icon, textX, iconY);
                  textX += 20;
               }
            }

            fm.drawText(context, r.name, textX, rowY + (effectiveRowH - fm.getTextHeight()) / 2, tcolor);
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
      int effectiveRowH = this.iconFn != null ? Math.max(rowH, 18) : rowH;
      int searchH = rowH * 2;
      if (hit((int)mouseX, (int)mouseY, this.panelX, this.searchY, this.panelWidth, searchH)) {
         this.filterFocused = true;
         String clearHint = "[Clear]";
         int clearX = this.panelX + this.panelWidth - fm.getTextWidth(clearHint) - 6;
         if (mouseX >= clearX) {
            this.filterText = "";
            this.scroll = 0;
         }

         return true;
      } else {
         this.filterFocused = false;
         if (!hit((int)mouseX, (int)mouseY, this.panelX, this.panelY, this.panelWidth, this.panelHeight)) {
            return super.mouseClicked(click, doubled);
         } else {
            int rowY = this.panelY + headerH + 1 - this.scroll;
            int rowEnd = this.panelY + this.panelHeight;

            for (NewSingleValueScreen.Ranked<T> r : this.buildFilteredList()) {
               if (mouseY >= rowY && mouseY < rowY + effectiveRowH && mouseY >= this.panelY + headerH + 1 && mouseY <= rowEnd - 1) {
                  this.setting.set(r.value);
                  if (this.onPick != null) {
                     this.onPick.accept(r.value);
                  }

                  this.close();
                  return true;
               }

               rowY += effectiveRowH;
            }

            return super.mouseClicked(click, doubled);
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (!hit((int)mouseX, (int)mouseY, this.panelX, this.panelY, this.panelWidth, this.panelHeight)) {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      } else {
         FontManager fm = FontManager.get();
         int rowH = fm.getRowHeight();
         int effectiveRowH = this.iconFn != null ? Math.max(rowH, 18) : rowH;
         int headerH = fm.getHeaderHeight();
         int count = 0;

         for (T v : this.registry) {
            if ((this.skipFn == null || !this.skipFn.test(v)) && rankMatch(this.nameFn.apply(v), this.filterText) != Integer.MIN_VALUE) {
               count++;
            }
         }

         int max = Math.max(0, count * effectiveRowH - (this.panelHeight - headerH));
         this.scroll -= (int)(verticalAmount * 14.0);
         this.scroll = Math.max(0, Math.min(this.scroll, max));
         return true;
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

            this.scroll = 0;
            return true;
         }

         if (keyCode == 256 || keyCode == 257) {
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
         this.scroll = 0;
         return true;
      } else {
         return super.charTyped(input);
      }
   }

   public boolean shouldPause() {
      return false;
   }

   private List<NewSingleValueScreen.Ranked<T>> buildFilteredList() {
      List<NewSingleValueScreen.Ranked<T>> list = new ArrayList<>();

      for (T value : this.registry) {
         if (this.skipFn == null || !this.skipFn.test(value)) {
            String name = this.nameFn.apply(value);
            int rank = rankMatch(name, this.filterText);
            if (rank != Integer.MIN_VALUE) {
               list.add(new NewSingleValueScreen.Ranked<>(value, name, rank));
            }
         }
      }

      if (this.filterText.isEmpty()) {
         list.sort(Comparator.comparing(r -> r.name));
      } else {
         list.sort((a, b) -> b.rank - a.rank);
      }

      return list;
   }

   private static boolean hit(double mx, double my, int x, int y, int w, int h) {
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
