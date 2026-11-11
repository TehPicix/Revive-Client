package revivedevelopment.reviveclient.gui.newgui.screens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.Names;

public class NewEntityTypeScreen extends Screen {
   private static final String S_ANIMALS = "Animals";
   private static final String S_WATER = "Water Animals";
   private static final String S_MONSTERS = "Monsters";
   private static final String S_AMBIENT = "Ambient";
   private static final String S_MISC = "Misc";
   private static final String[] SECTION_ORDER = new String[]{"Animals", "Water Animals", "Monsters", "Ambient", "Misc"};
   private final EntityTypeListSetting setting;
   public Screen parent;
   private final Map<String, List<EntityType<?>>> sections = new LinkedHashMap<>();
   private final Map<String, Boolean> expanded = new LinkedHashMap<>();
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
   private static final int CHECKBOX_SIZE = 10;

   public NewEntityTypeScreen(EntityTypeListSetting setting) {
      super(Text.literal("Select Entities — " + setting.name));
      this.setting = setting;

      for (String s : SECTION_ORDER) {
         this.expanded.put(s, false);
      }

      this.rebuildSections();
      int total = 0;

      for (List<EntityType<?>> l : this.sections.values()) {
         total += l.size();
      }

      if (total <= 30) {
         for (String s : SECTION_ORDER) {
            if (!this.sections.get(s).isEmpty()) {
               this.expanded.put(s, true);
            }
         }
      }
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
      int rowH = fm.getRowHeight();
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
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + headerH, fm.secondaryAlpha(200));
      RenderUtils.drawThinHLine(context, this.panelX, this.panelY + headerH, this.panelWidth, lineColor);
      int addedCount = this.setting.get().size();
      fm.drawText(context, "Entities (" + addedCount + " added)", this.panelX + 6, this.panelY + (headerH - fm.getTextHeight()) / 2, fm.getTextColor());
      int curY = this.panelY + headerH + 1 - this.scroll;
      int rowEnd = this.panelY + this.panelHeight;
      context.enableScissor(this.panelX + 1, this.panelY + headerH + 1, this.panelX + this.panelWidth - 1, rowEnd - 1);

      for (String secName : SECTION_ORDER) {
         List<EntityType<?>> entries = this.sections.get(secName);
         if (!entries.isEmpty()) {
            boolean isExpanded = this.expanded.getOrDefault(secName, false);
            if (curY + rowH >= this.panelY + headerH + 1 && curY <= rowEnd) {
               boolean hovered = hit(mouseX, mouseY, this.panelX + 1, curY, this.panelWidth - 2, rowH);
               context.fill(this.panelX + 1, curY, this.panelX + this.panelWidth - 1, curY + rowH, fm.secondaryAlpha(hovered ? 180 : 140));
               String indicator = isExpanded ? "-" : "+";
               fm.drawText(context, indicator, this.panelX + 6, curY + (rowH - fm.getTextHeight()) / 2, fm.getTextColor());
               fm.drawText(
                  context,
                  secName + " (" + entries.size() + ")",
                  this.panelX + 6 + fm.getTextWidth("+") + 6,
                  curY + (rowH - fm.getTextHeight()) / 2,
                  fm.getTextColor()
               );
               int addedInSec = 0;

               for (EntityType<?> e : entries) {
                  if (this.setting.get().contains(e)) {
                     addedInSec++;
                  }
               }

               boolean allAdded = addedInSec == entries.size();
               boolean someAdded = addedInSec > 0;
               this.drawCheckbox(context, fm, this.panelX + this.panelWidth - 10 - 6, curY + (rowH - 10) / 2, allAdded, someAdded && !allAdded, lineColor);
            }

            curY += rowH;
            if (isExpanded) {
               for (EntityType<?> ex : entries) {
                  if (curY + rowH < this.panelY + headerH + 1) {
                     curY += rowH;
                  } else {
                     if (curY > rowEnd) {
                        break;
                     }

                     boolean hovered = hit(mouseX, mouseY, this.panelX + 1, curY, this.panelWidth - 2, rowH);
                     boolean inColl = this.setting.get().contains(ex);
                     if (hovered || inColl) {
                        context.fill(this.panelX + 1, curY, this.panelX + this.panelWidth - 1, curY + rowH, fm.secondaryAlpha(hovered ? 130 : 70));
                     }

                     int tcolor = !hovered && !inColl ? fm.getTextSecondary() : fm.getTextColor();
                     fm.drawText(context, Names.get(ex), this.panelX + 6 + 14, curY + (rowH - fm.getTextHeight()) / 2, tcolor);
                     this.drawCheckbox(context, fm, this.panelX + this.panelWidth - 10 - 6, curY + (rowH - 10) / 2, inColl, false, fm.primaryAlpha(180));
                     curY += rowH;
                  }
               }
            }
         }
      }

      context.disableScissor();
   }

   public void close() {
      if (this.parent != null) {
         this.client.setScreen(this.parent);
      } else {
         super.close();
      }
   }

   private void drawCheckbox(DrawContext context, FontManager fm, int x, int y, boolean checked, boolean partial, int lineColor) {
      context.fill(x, y, x + 10, y + 10, fm.primaryAlpha(200));
      RenderUtils.drawThinOutline(context, x, y, 10, 10, lineColor);
      if (checked) {
         context.fill(x + 2, y + 2, x + 10 - 2, y + 10 - 2, fm.getTextColor());
      } else if (partial) {
         context.fill(x + 3, y + 4, x + 10 - 3, y + 10 - 4, fm.getTextSecondary());
      }
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      int headerH = fm.getHeaderHeight();
      int searchH = rowH * 2;
      if (hit((int)mouseX, (int)mouseY, this.panelX, this.searchY, this.panelWidth, searchH)) {
         this.filterFocused = true;
         String clearHint = "[Clear]";
         int clearX = this.panelX + this.panelWidth - fm.getTextWidth(clearHint) - 6;
         if (mouseX >= clearX) {
            this.filterText = "";
            this.rebuildSections();
            this.scroll = 0;
         }

         return true;
      } else {
         this.filterFocused = false;
         if (!hit((int)mouseX, (int)mouseY, this.panelX, this.panelY, this.panelWidth, this.panelHeight)) {
            return super.mouseClicked(click, doubled);
         } else {
            int curY = this.panelY + headerH + 1 - this.scroll;
            int rowEnd = this.panelY + this.panelHeight;
            int checkboxX = this.panelX + this.panelWidth - 10 - 6;

            for (String secName : SECTION_ORDER) {
               List<EntityType<?>> entries = this.sections.get(secName);
               if (!entries.isEmpty()) {
                  if (mouseY >= curY && mouseY < curY + rowH && curY >= this.panelY + headerH + 1 && curY + rowH <= rowEnd) {
                     if (mouseX >= checkboxX - 2 && mouseX <= checkboxX + 10 + 2) {
                        boolean anyAdded = false;

                        for (EntityType<?> e : entries) {
                           if (this.setting.get().contains(e)) {
                              anyAdded = true;
                              break;
                           }
                        }

                        if (anyAdded) {
                           this.setting.get().removeAll(entries);
                        } else {
                           this.setting.get().addAll(entries);
                        }

                        this.setting.onChanged();
                        return true;
                     }

                     this.expanded.put(secName, !this.expanded.get(secName));
                     return true;
                  }

                  curY += rowH;
                  if (this.expanded.getOrDefault(secName, false)) {
                     for (EntityType<?> ex : entries) {
                        if (mouseY >= curY && mouseY < curY + rowH && curY >= this.panelY + headerH + 1 && curY + rowH <= rowEnd) {
                           if (this.setting.get().contains(ex)) {
                              this.setting.get().remove(ex);
                           } else {
                              this.setting.get().add(ex);
                           }

                           this.setting.onChanged();
                           return true;
                        }

                        curY += rowH;
                     }
                  }
               }
            }

            return super.mouseClicked(click, doubled);
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (hit((int)mouseX, (int)mouseY, this.panelX, this.panelY, this.panelWidth, this.panelHeight)) {
         FontManager fm = FontManager.get();
         int rowH = fm.getRowHeight();
         int headerH = fm.getHeaderHeight();
         int total = 0;

         for (String secName : SECTION_ORDER) {
            List<EntityType<?>> entries = this.sections.get(secName);
            if (!entries.isEmpty()) {
               total++;
               if (this.expanded.getOrDefault(secName, false)) {
                  total += entries.size();
               }
            }
         }

         int max = Math.max(0, total * rowH - (this.panelHeight - headerH));
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
               this.rebuildSections();
               this.scroll = 0;
            }

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
         this.rebuildSections();
         this.scroll = 0;
         return true;
      } else {
         return super.charTyped(input);
      }
   }

   public boolean shouldPause() {
      return false;
   }

   private void rebuildSections() {
      for (String s : SECTION_ORDER) {
         this.sections.put(s, new ArrayList<>());
      }

      List<EntityType<?>> flat = new ArrayList<>();

      for (EntityType<?> e : Registries.ENTITY_TYPE) {
         if (this.setting.filter == null || this.setting.filter.test(e)) {
            String name = Names.get(e);
            if (!this.filterText.isEmpty()) {
               int words = Utils.searchInWords(name, this.filterText);
               int diff = Utils.searchLevenshteinDefault(name, this.filterText, false);
               if (words == 0 && diff > name.length() / 2) {
                  continue;
               }
            }

            flat.add(e);
         }
      }

      flat.sort(Comparator.comparing(Names::get, String.CASE_INSENSITIVE_ORDER));

      for (EntityType<?> ex : flat) {
         String sec = sectionFor(ex);
         this.sections.get(sec).add(ex);
      }
   }

   private static String sectionFor(EntityType<?> e) {
      SpawnGroup g = e.getSpawnGroup();

      return switch (g) {
         case CREATURE -> "Animals";
         case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> "Water Animals";
         case MONSTER -> "Monsters";
         case AMBIENT -> "Ambient";
         default -> "Misc";
      };
   }

   private static boolean hit(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }
}
