package revivedevelopment.reviveclient.gui.newgui.screens;

import java.util.ArrayList;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.gui.utils.CharFilter;
import revivedevelopment.reviveclient.settings.StringListSetting;

public class NewStringListScreen extends Screen {
   private final StringListSetting setting;
   private final ArrayList<String> entries;
   private int editingIndex = -1;
   private int scroll = 0;
   private int margin;
   private int listX;
   private int listY;
   private int listWidth;
   private int listHeight;
   private static final int PADDING = 6;
   public Screen parent;

   public NewStringListScreen(StringListSetting setting) {
      super(Text.literal("Edit " + setting.name));
      this.setting = setting;
      this.entries = new ArrayList<>(setting.get());
   }

   protected void init() {
      super.init();
      this.margin = Math.max(40, this.width / 6);
      this.listX = this.margin;
      this.listY = 30;
      this.listWidth = this.width - this.margin * 2;
      this.listHeight = this.height - 60;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      FontManager fm = FontManager.get();
      super.render(context, mouseX, mouseY, deltaTicks);
      int lineColor = fm.primaryAlpha(180);
      int rowH = fm.getRowHeight();
      int headerH = fm.getHeaderHeight();
      fm.drawText(context, this.title.getString(), this.listX, 8, fm.getTextColor());
      context.fill(this.listX, this.listY, this.listX + this.listWidth, this.listY + this.listHeight, fm.primaryAlpha(120));
      RenderUtils.drawThinOutline(context, this.listX, this.listY, this.listWidth, this.listHeight, lineColor);
      context.fill(this.listX, this.listY, this.listX + this.listWidth, this.listY + headerH, fm.secondaryAlpha(200));
      RenderUtils.drawThinHLine(context, this.listX, this.listY + headerH, this.listWidth, lineColor);
      String heading = this.setting.name + " (" + this.entries.size() + ")";
      fm.drawText(context, heading, this.listX + 6, this.listY + (headerH - fm.getTextHeight()) / 2, fm.getTextColor());
      String addLabel = "[+ Add]";
      fm.drawText(
         context,
         addLabel,
         this.listX + this.listWidth - fm.getTextWidth(addLabel) - 6,
         this.listY + (headerH - fm.getTextHeight()) / 2,
         GuiColors.TEXT_SETTING_VALUE
      );
      int rowY = this.listY + headerH + 1 - this.scroll;
      int rowEnd = this.listY + this.listHeight;
      context.enableScissor(this.listX + 1, this.listY + headerH + 1, this.listX + this.listWidth - 1, rowEnd - 1);

      for (int i = 0; i < this.entries.size(); i++) {
         if (rowY + rowH < this.listY + headerH + 1) {
            rowY += rowH;
         } else {
            if (rowY > rowEnd) {
               break;
            }

            boolean hovered = hit(mouseX, mouseY, this.listX + 1, rowY, this.listWidth - 2, rowH);
            boolean editing = this.editingIndex == i;
            if (hovered || editing) {
               context.fill(this.listX + 1, rowY, this.listX + this.listWidth - 1, rowY + rowH, fm.secondaryAlpha(120));
            }

            int tcolor = editing ? fm.getTextColor() : fm.getTextSecondary();
            String text = this.entries.get(i);
            fm.drawText(context, text, this.listX + 6, rowY + (rowH - fm.getTextHeight()) / 2, tcolor);
            if (editing && (System.currentTimeMillis() / 500L & 1L) == 0L) {
               int caretX = this.listX + 6 + fm.getTextWidth(text);
               context.fill(caretX, rowY + 2, caretX + 1, rowY + rowH - 2, fm.getTextColor());
            }

            String delLabel = "[x]";
            fm.drawText(
               context, delLabel, this.listX + this.listWidth - fm.getTextWidth(delLabel) - 6, rowY + (rowH - fm.getTextHeight()) / 2, GuiColors.TEXT_DISABLED
            );
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
      int rowH = fm.getRowHeight();
      int headerH = fm.getHeaderHeight();
      String addLabel = "[+ Add]";
      int addX = this.listX + this.listWidth - fm.getTextWidth(addLabel) - 6;
      if (mouseY >= this.listY && mouseY < this.listY + headerH && mouseX >= addX && mouseX <= this.listX + this.listWidth - 2) {
         this.commitEdit();
         this.entries.add("");
         this.editingIndex = this.entries.size() - 1;
         this.setting.set(new ArrayList<>(this.entries));
         return true;
      } else {
         int rowY = this.listY + headerH + 1 - this.scroll;

         for (int i = 0; i < this.entries.size(); i++) {
            if (mouseY >= rowY && mouseY < rowY + rowH) {
               String delLabel = "[x]";
               int delX = this.listX + this.listWidth - fm.getTextWidth(delLabel) - 6;
               if (mouseX >= delX) {
                  this.commitEdit();
                  this.entries.remove(i);
                  this.setting.set(new ArrayList<>(this.entries));
                  if (this.editingIndex == i) {
                     this.editingIndex = -1;
                  } else if (this.editingIndex > i) {
                     this.editingIndex--;
                  }

                  return true;
               }

               this.commitEdit();
               this.editingIndex = i;
               return true;
            }

            rowY += rowH;
         }

         this.commitEdit();
         this.editingIndex = -1;
         return super.mouseClicked(click, doubled);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (hit((int)mouseX, (int)mouseY, this.listX, this.listY, this.listWidth, this.listHeight)) {
         int rowH = FontManager.get().getRowHeight();
         int headerH = FontManager.get().getHeaderHeight();
         int max = Math.max(0, this.entries.size() * rowH - (this.listHeight - headerH));
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
      if (this.editingIndex >= 0) {
         if (keyCode == 257 || keyCode == 335) {
            this.commitEdit();
            this.editingIndex = -1;
            return true;
         } else if (keyCode == 256) {
            this.editingIndex = -1;
            return true;
         } else if (keyCode == 259) {
            String cur = this.entries.get(this.editingIndex);
            if (!cur.isEmpty()) {
               this.entries.set(this.editingIndex, cur.substring(0, cur.length() - 1));
            }

            return true;
         } else {
            return true;
         }
      } else if (keyCode == 256) {
         this.commitEdit();
         this.close();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public boolean charTyped(CharInput input) {
      char chr = (char)input.codepoint();
      int modifiers = input.modifiers();
      if (this.editingIndex >= 0 && chr >= ' ' && chr != 127) {
         String cur = this.entries.get(this.editingIndex);
         String next = cur + chr;
         CharFilter f = this.setting.filter;
         if (f == null || f.filter(next, chr)) {
            this.entries.set(this.editingIndex, next);
         }

         return true;
      } else {
         return super.charTyped(input);
      }
   }

   public void close() {
      this.commitEdit();
      if (this.parent != null) {
         this.client.setScreen(this.parent);
      } else {
         super.close();
      }
   }

   private void commitEdit() {
      this.setting.set(new ArrayList<>(this.entries));
   }

   public boolean shouldPause() {
      return false;
   }

   private static boolean hit(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }
}
