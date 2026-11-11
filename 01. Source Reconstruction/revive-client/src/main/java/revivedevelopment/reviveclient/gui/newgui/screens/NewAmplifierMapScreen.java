package revivedevelopment.reviveclient.gui.newgui.screens;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.components.ModuleButton;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.settings.StatusEffectAmplifierMapSetting;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.Names;

public class NewAmplifierMapScreen extends Screen {
   private final StatusEffectAmplifierMapSetting setting;
   private final Reference2IntMap<StatusEffect> map;
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
   private static final int ROW_H_MIN = 18;
   private static final int MAX_AMP = 255;

   public NewAmplifierMapScreen(StatusEffectAmplifierMapSetting setting) {
      super(Text.literal("Modify Amplifiers — " + setting.name));
      this.setting = setting;
      this.map = setting.get();
   }

   protected void init() {
      super.init();
      int margin = Math.max(40, this.width / 4);
      this.panelX = margin;
      this.panelWidth = this.width - margin * 2;
      int searchH = FontManager.get().getRowHeight() * 2;
      this.searchY = 20;
      this.panelY = this.searchY + searchH + 8;
      this.panelHeight = this.height - this.panelY - 20;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      FontManager fm = FontManager.get();
      super.render(context, mouseX, mouseY, deltaTicks);
      int lineColor = fm.primaryAlpha(180);
      int rowH = Math.max(fm.getRowHeight(), 18);
      int headerH = fm.getHeaderHeight();
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
      List<StatusEffect> items = this.buildFilteredList();
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + headerH, fm.secondaryAlpha(200));
      RenderUtils.drawThinHLine(context, this.panelX, this.panelY + headerH, this.panelWidth, lineColor);
      int activeCount = 0;
      ObjectIterator heading = this.map.keySet().iterator();

      while (heading.hasNext()) {
         StatusEffect e = (StatusEffect)heading.next();
         if (this.map.getInt(e) > 0) {
            activeCount++;
         }
      }

      String headingx = "Effects (" + activeCount + " active)";
      fm.drawText(context, headingx, this.panelX + 6, this.panelY + (headerH - fm.getTextHeight()) / 2, fm.getTextColor());
      int rowY = this.panelY + headerH + 1 - this.scroll;
      int rowEnd = this.panelY + this.panelHeight;
      context.enableScissor(this.panelX + 1, this.panelY + headerH + 1, this.panelX + this.panelWidth - 1, rowEnd - 1);

      for (StatusEffect effect : items) {
         if (rowY + rowH < this.panelY + headerH + 1) {
            rowY += rowH;
         } else {
            if (rowY > rowEnd) {
               break;
            }

            boolean hovered = hit(mouseX, mouseY, this.panelX + 1, rowY, this.panelWidth - 2, rowH)
               && mouseY >= this.panelY + headerH + 1
               && mouseY <= rowEnd - 1;
            int amp = this.map.getInt(effect);
            boolean active = amp > 0;
            if (hovered || active) {
               context.fill(this.panelX + 1, rowY, this.panelX + this.panelWidth - 1, rowY + rowH, fm.secondaryAlpha(hovered ? 150 : 90));
            }

            int iconX = this.panelX + 6;
            int iconY = rowY + (rowH - 16) / 2;
            ItemStack icon = ModuleButton.statusEffectIcon(effect);
            if (icon != null && !icon.isEmpty()) {
               context.drawItem(icon, iconX, iconY);
            }

            int textX = iconX + 16 + 4;
            int tcolor = active ? fm.getTextColor() : fm.getTextSecondary();
            fm.drawText(context, Names.get(effect), textX, rowY + (rowH - fm.getTextHeight()) / 2, tcolor);
            String minus = "[-]";
            String plus = "[+]";
            String val = String.valueOf(amp);
            int plusX = this.panelX + this.panelWidth - fm.getTextWidth(plus) - 6;
            int valX = plusX - 4 - fm.getTextWidth(val);
            int minusX = valX - 4 - fm.getTextWidth(minus);
            int ampColor = active ? fm.getTextColor() : GuiColors.TEXT_DISABLED;
            fm.drawText(context, minus, minusX, rowY + (rowH - fm.getTextHeight()) / 2, amp > 0 ? fm.getTextColor() : GuiColors.TEXT_DISABLED);
            fm.drawText(context, val, valX, rowY + (rowH - fm.getTextHeight()) / 2, ampColor);
            fm.drawText(context, plus, plusX, rowY + (rowH - fm.getTextHeight()) / 2, amp < 255 ? fm.getTextColor() : GuiColors.TEXT_DISABLED);
            rowY += rowH;
         }
      }

      context.disableScissor();
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      FontManager fm = FontManager.get();
      int rowH = Math.max(fm.getRowHeight(), 18);
      int headerH = fm.getHeaderHeight();
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

            for (StatusEffect effect : this.buildFilteredList()) {
               if (mouseY >= rowY && mouseY < rowY + rowH && mouseY >= this.panelY + headerH + 1 && mouseY <= rowEnd - 1) {
                  int amp = this.map.getInt(effect);
                  String minus = "[-]";
                  String plus = "[+]";
                  String val = String.valueOf(amp);
                  int plusX = this.panelX + this.panelWidth - fm.getTextWidth(plus) - 6;
                  int valX = plusX - 4 - fm.getTextWidth(val);
                  int minusX = valX - 4 - fm.getTextWidth(minus);
                  if (mouseX >= minusX && mouseX < minusX + fm.getTextWidth(minus) + 2) {
                     if (amp > 0) {
                        this.map.put(effect, amp - 1);
                        this.setting.onChanged();
                     }

                     return true;
                  }

                  if (mouseX >= plusX - 2 && mouseX <= this.panelX + this.panelWidth - 6) {
                     if (amp < 255) {
                        this.map.put(effect, amp + 1);
                        this.setting.onChanged();
                     }

                     return true;
                  }

                  if (mouseX >= valX - 2 && mouseX < plusX - 2) {
                     if (amp == 0) {
                        this.map.put(effect, 1);
                     } else {
                        this.map.put(effect, 0);
                     }

                     this.setting.onChanged();
                     return true;
                  }

                  return true;
               }

               rowY += rowH;
            }

            return super.mouseClicked(click, doubled);
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (hit((int)mouseX, (int)mouseY, this.panelX, this.panelY, this.panelWidth, this.panelHeight)) {
         FontManager fm = FontManager.get();
         int rowH = Math.max(fm.getRowHeight(), 18);
         int headerH = fm.getHeaderHeight();
         int count = this.buildFilteredList().size();
         int max = Math.max(0, count * rowH - (this.panelHeight - headerH));
         this.scroll -= (int)(verticalAmount * 14.0);
         this.scroll = Math.max(0, Math.min(this.scroll, max));
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

   private List<StatusEffect> buildFilteredList() {
      List<StatusEffect> list = new ArrayList<>();

      for (StatusEffect e : Registries.STATUS_EFFECT) {
         String name = Names.get(e);
         if (!this.filterText.isEmpty()) {
            int words = Utils.searchInWords(name, this.filterText);
            int diff = Utils.searchLevenshteinDefault(name, this.filterText, false);
            if (words == 0 && diff > name.length() / 2) {
               continue;
            }
         }

         list.add(e);
      }

      list.sort((a, b) -> {
         int ampA = this.map.getInt(a);
         int ampB = this.map.getInt(b);
         boolean aActive = ampA > 0;
         boolean bActive = ampB > 0;
         if (aActive != bActive) {
            return aActive ? -1 : 1;
         } else {
            return Names.get(a).compareToIgnoreCase(Names.get(b));
         }
      });
      return list;
   }

   private static boolean hit(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }
}
