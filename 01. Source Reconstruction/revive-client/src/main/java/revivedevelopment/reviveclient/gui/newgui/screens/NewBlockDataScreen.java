package revivedevelopment.reviveclient.gui.newgui.screens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.gui.GuiThemes;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.components.CapturingGuiTheme;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.settings.BlockDataSetting;
import revivedevelopment.reviveclient.settings.IBlockData;
import revivedevelopment.reviveclient.utils.misc.IChangeable;
import revivedevelopment.reviveclient.utils.misc.ICopyable;
import revivedevelopment.reviveclient.utils.misc.ISerializable;
import revivedevelopment.reviveclient.utils.misc.Names;

public class NewBlockDataScreen<T extends ICopyable<T> & ISerializable<T> & IChangeable & IBlockData<T>> extends Screen {
   private final BlockDataSetting<T> setting;
   private String filterText = "";
   private boolean filterFocused = true;
   private int scroll = 0;
   private int panelX;
   private int panelWidth;
   private int panelY;
   private int panelHeight;
   private int searchY;
   private static final int PADDING = 6;
   private static final int SEARCH_HEIGHT_MULT = 2;
   private static final int ICON_SIZE = 16;
   private static final int REMOVE_BTN_WIDTH = 18;

   public NewBlockDataScreen(BlockDataSetting<T> setting) {
      super(Text.literal("Configure Blocks — " + setting.name));
      this.setting = setting;
   }

   protected void init() {
      super.init();
      int screenW = this.width;
      int screenH = this.height;
      int margin = Math.max(20, screenW / 6);
      this.panelWidth = screenW - margin * 2;
      this.panelX = margin;
      int searchH = FontManager.get().getRowHeight() * 2;
      this.searchY = 20;
      this.panelY = this.searchY + searchH + 8;
      this.panelHeight = screenH - this.panelY - 20;
   }

   public boolean shouldPause() {
      return false;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      FontManager fm = FontManager.get();
      super.render(context, mouseX, mouseY, deltaTicks);
      int lineColor = fm.primaryAlpha(180);
      int fillColor = fm.secondaryAlpha(200);
      int headerH = fm.getHeaderHeight();
      int rowH = fm.getRowHeight();
      fm.drawText(context, this.title.getString(), this.panelX, 4, fm.getTextColor());
      int searchH = rowH * 2;
      context.fill(
         this.panelX, this.searchY, this.panelX + this.panelWidth, this.searchY + searchH, this.filterFocused ? fm.secondaryAlpha(220) : fm.secondaryAlpha(150)
      );
      RenderUtils.drawThinOutline(context, this.panelX, this.searchY, this.panelWidth, searchH, lineColor);
      String searchLabel = "Search: ";
      int labelW = fm.getTextWidth(searchLabel);
      fm.drawText(context, searchLabel, this.panelX + 6, this.searchY + (searchH - fm.getTextHeight()) / 2, GuiColors.TEXT_SETTING_VALUE);
      fm.drawText(context, this.filterText, this.panelX + 6 + labelW, this.searchY + (searchH - fm.getTextHeight()) / 2, fm.getTextColor());
      if (this.filterFocused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
         int caretX = this.panelX + 6 + labelW + fm.getTextWidth(this.filterText);
         context.fill(caretX, this.searchY + 3, caretX + 1, this.searchY + searchH - 3, fm.getTextColor());
      }

      String clearHint = "[Clear]";
      fm.drawText(
         context,
         clearHint,
         this.panelX + this.panelWidth - fm.getTextWidth(clearHint) - 6,
         this.searchY + (searchH - fm.getTextHeight()) / 2,
         GuiColors.TEXT_SETTING_VALUE
      );
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, fm.primaryAlpha(120));
      RenderUtils.drawThinOutline(context, this.panelX, this.panelY, this.panelWidth, this.panelHeight, lineColor);
      int overrideCount = this.countOverrides();
      String heading = overrideCount == 0 ? "Blocks (no overrides yet)" : "Blocks (" + overrideCount + " override" + (overrideCount == 1 ? "" : "s") + ")";
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + headerH, fillColor);
      RenderUtils.drawThinHLine(context, this.panelX, this.panelY + headerH, this.panelWidth, lineColor);
      fm.drawText(context, heading, this.panelX + 6, this.panelY + (headerH - fm.getTextHeight()) / 2, fm.getTextColor());
      List<Block> blocks = this.buildFilteredList();
      int effectiveRowH = Math.max(rowH, 18);
      int rowY = this.panelY + headerH + 1 - this.scroll;
      int rowEnd = this.panelY + this.panelHeight;
      context.enableScissor(this.panelX + 1, this.panelY + headerH + 1, this.panelX + this.panelWidth - 1, rowEnd - 1);
      if (blocks.isEmpty()) {
         String msg = this.filterText.isEmpty() ? "(no blocks)" : "(no matches)";
         int tx = this.panelX + (this.panelWidth - fm.getTextWidth(msg)) / 2;
         int ty = this.panelY + headerH + (this.panelHeight - headerH - fm.getTextHeight()) / 2;
         fm.drawText(context, msg, tx, ty, GuiColors.TEXT_DISABLED);
      }

      for (Block block : blocks) {
         if (rowY + effectiveRowH < this.panelY + headerH + 1) {
            rowY += effectiveRowH;
         } else {
            if (rowY > rowEnd) {
               break;
            }

            T data = this.setting.get().get(block);
            boolean hasOverride = data != null && data.isChanged();
            boolean rowHovered = mouseX >= this.panelX + 1
               && mouseX < this.panelX + this.panelWidth - 1
               && mouseY >= rowY
               && mouseY < rowY + effectiveRowH
               && mouseY >= this.panelY + headerH + 1
               && mouseY <= rowEnd - 1;
            if (rowHovered) {
               context.fill(this.panelX + 1, rowY, this.panelX + this.panelWidth - 1, rowY + effectiveRowH, fm.secondaryAlpha(120));
            }

            int tcolor = rowHovered ? fm.getTextColor() : (hasOverride ? fm.getTextColor() : fm.getTextSecondary());
            ItemStack icon = block.asItem().getDefaultStack();
            int iconY = rowY + (effectiveRowH - 16) / 2;
            context.drawItem(icon, this.panelX + 6, iconY);
            String label = Names.get(block) + (hasOverride ? "  *" : "");
            int nameX = this.panelX + 6 + 16 + 4;
            fm.drawText(context, label, nameX, rowY + (effectiveRowH - fm.getTextHeight()) / 2, tcolor);
            if (hasOverride) {
               int xBtnX = this.panelX + this.panelWidth - 18 - 2;
               boolean xBtnHovered = mouseX >= xBtnX && mouseX < xBtnX + 18 && mouseY >= rowY && mouseY < rowY + effectiveRowH;
               int xColor = xBtnHovered ? -40864 : GuiColors.TEXT_DISABLED;
               String xStr = "reset";
               int xw = fm.getTextWidth(xStr);
               fm.drawText(context, xStr, xBtnX + (18 - xw) / 2, rowY + (effectiveRowH - fm.getTextHeight()) / 2, xColor);
            }

            rowY += effectiveRowH;
         }
      }

      context.disableScissor();
      int totalRowsH = blocks.size() * effectiveRowH;
      int visibleRowsH = this.panelHeight - headerH - 2;
      if (totalRowsH > visibleRowsH) {
         int sx = this.panelX + this.panelWidth - 3;
         int scrollH = Math.max(10, visibleRowsH * visibleRowsH / totalRowsH);
         int scrollY = this.panelY + headerH + 2 + this.scroll * (visibleRowsH - scrollH) / Math.max(1, totalRowsH - visibleRowsH);
         context.fill(sx, scrollY, sx + 2, scrollY + scrollH, fm.secondaryAlpha(220));
      }
   }

   private int countOverrides() {
      int n = 0;

      for (Entry<Block, T> e : this.setting.get().entrySet()) {
         if (e.getValue() != null && e.getValue().isChanged()) {
            n++;
         }
      }

      return n;
   }

   private List<Block> buildFilteredList() {
      List<Block> changed = new ArrayList<>();
      List<Block> rest = new ArrayList<>();

      for (Block b : Registries.BLOCK) {
         String name = Names.get(b);
         if (matchesFilter(name, this.filterText)) {
            T data = this.setting.get().get(b);
            if (data != null && data.isChanged()) {
               changed.add(b);
            } else {
               rest.add(b);
            }
         }
      }

      changed.sort(Comparator.comparing(Names::get));
      rest.sort(Comparator.comparing(Names::get));
      changed.addAll(rest);
      return changed;
   }

   private static boolean matchesFilter(String name, String filter) {
      if (filter.isEmpty()) {
         return true;
      } else {
         String a = name.toLowerCase();
         String b = filter.toLowerCase();
         return a.contains(b);
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
      if (mouseX >= this.panelX && mouseX < this.panelX + this.panelWidth && mouseY >= this.searchY && mouseY < this.searchY + searchH) {
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
         if (mouseX >= this.panelX && mouseX < this.panelX + this.panelWidth && mouseY >= this.panelY && mouseY < this.panelY + this.panelHeight) {
            int effectiveRowH = Math.max(rowH, 18);
            int rowY = this.panelY + headerH + 1 - this.scroll;
            int rowEnd = this.panelY + this.panelHeight;

            for (Block block : this.buildFilteredList()) {
               if (mouseY >= rowY && mouseY < rowY + effectiveRowH && mouseY >= this.panelY + headerH + 1 && mouseY <= rowEnd - 1) {
                  T data = this.setting.get().get(block);
                  boolean hasOverride = data != null && data.isChanged();
                  if (hasOverride && button == 0) {
                     int xBtnX = this.panelX + this.panelWidth - 18 - 2;
                     if (mouseX >= xBtnX && mouseX < xBtnX + 18) {
                        this.setting.get().remove(block);
                        this.setting.onChanged();
                        return true;
                     }
                  }

                  if (button == 0) {
                     this.openBlockEdit(block, data);
                     return true;
                  }

                  if (button == 1 && hasOverride) {
                     this.setting.get().remove(block);
                     this.setting.onChanged();
                     return true;
                  }

                  return true;
               }

               rowY += effectiveRowH;
            }

            return super.mouseClicked(click, doubled);
         } else {
            return super.mouseClicked(click, doubled);
         }
      }
   }

   private void openBlockEdit(Block block, T existing) {
      MinecraftClient mc = MinecraftClient.getInstance();
      T data = existing;
      if (existing == null) {
         data = this.setting.defaultData.get().copy();
      }

      this.setting.get().put(block, data);
      CapturingGuiTheme capture = new CapturingGuiTheme();
      Screen widgetScreen = null;

      try {
         widgetScreen = data.createScreen(capture, block, this.setting);
         if (widgetScreen instanceof WidgetScreen ws) {
            ws.initWidgets();
         }
      } catch (Throwable var8) {
      }

      if (capture.captured != null) {
         String title = "Configure " + Names.get(block);
         mc.setScreen(new NewBlockEditScreen(title, capture.captured, new NewBlockDataScreen<>(this.setting)));
      } else if (widgetScreen != null) {
         mc.setScreen(widgetScreen);
      } else {
         mc.setScreen(data.createScreen(GuiThemes.get(), block, this.setting));
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      FontManager fm = FontManager.get();
      int effectiveRowH = Math.max(fm.getRowHeight(), 18);
      int totalRowsH = this.buildFilteredList().size() * effectiveRowH;
      int visibleRowsH = this.panelHeight - fm.getHeaderHeight() - 2;
      int maxScroll = Math.max(0, totalRowsH - visibleRowsH);
      this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int)(verticalAmount * effectiveRowH * 2.0)));
      return true;
   }

   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int modifiers = input.modifiers();
      if (this.filterFocused) {
         if (keyCode == 259) {
            if (!this.filterText.isEmpty()) {
               this.filterText = this.filterText.substring(0, this.filterText.length() - 1);
               this.scroll = 0;
            }

            return true;
         }

         if (keyCode == 256) {
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
}
