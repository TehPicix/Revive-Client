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
import revivedevelopment.reviveclient.settings.ColorListSetting;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class NewColorListScreen extends Screen {
   private final ColorListSetting setting;
   private final ArrayList<SettingColor> entries;
   private int editingIndex = -1;
   private String editBuffer = "";
   private int scroll = 0;
   private int listX;
   private int listY;
   private int listWidth;
   private int listHeight;
   private static final int PADDING = 6;
   private static final int SWATCH_SIZE = 14;
   private static final int ROW_HEIGHT_MIN = 18;

   public NewColorListScreen(ColorListSetting setting) {
      super(Text.literal("Edit " + setting.name));
      this.setting = setting;
      this.entries = new ArrayList<>();

      for (SettingColor c : setting.get()) {
         this.entries.add(new SettingColor(c));
      }
   }

   protected void init() {
      super.init();
      int margin = Math.max(40, this.width / 6);
      this.listX = margin;
      this.listY = 30;
      this.listWidth = this.width - margin * 2;
      this.listHeight = this.height - 60;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      FontManager fm = FontManager.get();
      super.render(context, mouseX, mouseY, deltaTicks);
      int lineColor = fm.primaryAlpha(180);
      int headerH = fm.getHeaderHeight();
      int rowH = Math.max(fm.getRowHeight(), 18);
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

            SettingColor c = this.entries.get(i);
            boolean editing = this.editingIndex == i;
            boolean hovered = hit(mouseX, mouseY, this.listX + 1, rowY, this.listWidth - 2, rowH);
            if (hovered || editing) {
               context.fill(this.listX + 1, rowY, this.listX + this.listWidth - 1, rowY + rowH, fm.secondaryAlpha(120));
            }

            int swatchX = this.listX + 6;
            int swatchY = rowY + (rowH - 14) / 2;
            int argb = (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
            context.fill(swatchX, swatchY, swatchX + 14, swatchY + 14, argb);
            RenderUtils.drawThinOutline(context, swatchX, swatchY, 14, 14, lineColor);
            int hexX = swatchX + 14 + 6;
            String hex = editing ? this.editBuffer : toHex(c);
            int hexTextColor = editing ? fm.getTextColor() : fm.getTextSecondary();
            fm.drawText(context, hex, hexX, rowY + (rowH - fm.getTextHeight()) / 2, hexTextColor);
            if (editing && (System.currentTimeMillis() / 500L & 1L) == 0L) {
               int caretX = hexX + fm.getTextWidth(hex);
               context.fill(caretX, rowY + 3, caretX + 1, rowY + rowH - 3, fm.getTextColor());
            }

            String rainbowLabel = c.rainbow ? "[R]" : "[r]";
            int rainbowColor = c.rainbow ? fm.getTextColor() : GuiColors.TEXT_DISABLED;
            int rainbowX = this.listX + this.listWidth - fm.getTextWidth("[x]") - 6 - 6 - fm.getTextWidth(rainbowLabel);
            fm.drawText(context, rainbowLabel, rainbowX, rowY + (rowH - fm.getTextHeight()) / 2, rainbowColor);
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
      int rowH = Math.max(fm.getRowHeight(), 18);
      int headerH = fm.getHeaderHeight();
      String addLabel = "[+ Add]";
      int addX = this.listX + this.listWidth - fm.getTextWidth(addLabel) - 6;
      if (mouseY >= this.listY && mouseY < this.listY + headerH && mouseX >= addX && mouseX <= this.listX + this.listWidth - 2) {
         this.commitEdit();
         this.entries.add(new SettingColor(255, 255, 255, 255));
         this.setting.get().clear();
         this.setting.get().addAll(this.entries);
         this.setting.onChanged();
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
                  this.setting.get().clear();
                  this.setting.get().addAll(this.entries);
                  this.setting.onChanged();
                  if (this.editingIndex == i) {
                     this.editingIndex = -1;
                  } else if (this.editingIndex > i) {
                     this.editingIndex--;
                  }

                  return true;
               }

               String rainbowLabel = this.entries.get(i).rainbow ? "[R]" : "[r]";
               int rainbowX = this.listX + this.listWidth - fm.getTextWidth("[x]") - 6 - 6 - fm.getTextWidth(rainbowLabel);
               int rainbowEndX = this.listX + this.listWidth - fm.getTextWidth("[x]") - 6 - 6;
               if (mouseX >= rainbowX && mouseX < rainbowEndX) {
                  this.commitEdit();
                  this.entries.get(i).rainbow = !this.entries.get(i).rainbow;
                  this.setting.get().clear();
                  this.setting.get().addAll(this.entries);
                  this.setting.onChanged();
                  return true;
               }

               this.commitEdit();
               this.editingIndex = i;
               this.editBuffer = toHex(this.entries.get(i));
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
         FontManager fm = FontManager.get();
         int rowH = Math.max(fm.getRowHeight(), 18);
         int headerH = fm.getHeaderHeight();
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
            this.editBuffer = "";
            return true;
         } else if (keyCode == 259) {
            if (!this.editBuffer.isEmpty()) {
               this.editBuffer = this.editBuffer.substring(0, this.editBuffer.length() - 1);
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
         boolean ok = chr >= '0' && chr <= '9' || chr >= 'a' && chr <= 'f' || chr >= 'A' && chr <= 'F' || chr == '#';
         if (ok && this.editBuffer.length() < 9) {
            this.editBuffer = this.editBuffer + chr;
         }

         return true;
      } else {
         return super.charTyped(input);
      }
   }

   public void close() {
      this.commitEdit();
      super.close();
   }

   public boolean shouldPause() {
      return false;
   }

   private void commitEdit() {
      if (this.editingIndex >= 0) {
         SettingColor parsed = parseHex(this.editBuffer);
         if (parsed != null) {
            boolean wasRainbow = this.entries.get(this.editingIndex).rainbow;
            parsed.rainbow = wasRainbow;
            this.entries.set(this.editingIndex, parsed);
            this.setting.get().clear();
            this.setting.get().addAll(this.entries);
            this.setting.onChanged();
         }

         this.editBuffer = "";
      }
   }

   private static String toHex(SettingColor c) {
      return String.format("#%02X%02X%02X%02X", c.r & 0xFF, c.g & 0xFF, c.b & 0xFF, c.a & 0xFF);
   }

   private static SettingColor parseHex(String s) {
      String t = s.startsWith("#") ? s.substring(1) : s;

      try {
         int a = 255;
         int r;
         int g;
         int b;
         if (t.length() == 3) {
            r = Integer.parseInt("" + t.charAt(0) + t.charAt(0), 16);
            g = Integer.parseInt("" + t.charAt(1) + t.charAt(1), 16);
            b = Integer.parseInt("" + t.charAt(2) + t.charAt(2), 16);
         } else if (t.length() == 4) {
            r = Integer.parseInt("" + t.charAt(0) + t.charAt(0), 16);
            g = Integer.parseInt("" + t.charAt(1) + t.charAt(1), 16);
            b = Integer.parseInt("" + t.charAt(2) + t.charAt(2), 16);
            a = Integer.parseInt("" + t.charAt(3) + t.charAt(3), 16);
         } else if (t.length() == 6) {
            r = Integer.parseInt(t.substring(0, 2), 16);
            g = Integer.parseInt(t.substring(2, 4), 16);
            b = Integer.parseInt(t.substring(4, 6), 16);
         } else {
            if (t.length() != 8) {
               return null;
            }

            r = Integer.parseInt(t.substring(0, 2), 16);
            g = Integer.parseInt(t.substring(2, 4), 16);
            b = Integer.parseInt(t.substring(4, 6), 16);
            a = Integer.parseInt(t.substring(6, 8), 16);
         }

         return new SettingColor(r, g, b, a);
      } catch (NumberFormatException var6) {
         return null;
      }
   }

   private static boolean hit(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }
}
