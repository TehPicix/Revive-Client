package revivedevelopment.reviveclient.gui.newgui.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudGroup;
import revivedevelopment.reviveclient.systems.hud.screens.HudEditorScreen;
import revivedevelopment.reviveclient.utils.Utils;

public class NewAddHudElementScreen extends Screen {
   private final int spawnX;
   private final int spawnY;
   private final Screen returnTo;
   private String searchQuery = "";
   private int scroll = 0;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private static final int PANEL_WIDTH = 170;
   private static final int PADDING = 2;
   private static final int SEARCH_ROW_GAP = 2;

   public NewAddHudElementScreen(int spawnX, int spawnY, Screen returnTo) {
      super(Text.literal("Add HUD element"));
      this.spawnX = spawnX;
      this.spawnY = spawnY;
      this.returnTo = returnTo;
   }

   public boolean shouldPause() {
      return false;
   }

   protected void init() {
      super.init();
      this.positionPanel();
   }

   private void positionPanel() {
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      double sf = MinecraftClient.getInstance().getWindow().getScaleFactor();
      this.panelWidth = 170;
      int totalRows = this.countTotalRows();
      int desiredH = rowH + rowH + 2 + totalRows * rowH + 4;
      int maxH = this.height - 10;
      this.panelHeight = Math.min(desiredH, maxH);
      int targetX = (int)(this.spawnX / sf);
      int targetY = (int)(this.spawnY / sf);
      this.panelX = Math.max(4, Math.min(this.width - this.panelWidth - 4, targetX));
      this.panelY = Math.max(4, Math.min(this.height - this.panelHeight - 4, targetY));
   }

   private int countTotalRows() {
      int total = 0;

      for (Entry<HudGroup, List<NewAddHudElementScreen.Item>> e : this.buildGrouped().entrySet()) {
         total = ++total + e.getValue().size();
      }

      return total;
   }

   private Map<HudGroup, List<NewAddHudElementScreen.Item>> buildGrouped() {
      Map<HudGroup, List<NewAddHudElementScreen.Item>> grouped = new LinkedHashMap<>();

      for (HudElementInfo<?> info : Hud.get().infos.values()) {
         if (info.hasPresets() && !this.searchQuery.isEmpty()) {
            for (HudElementInfo<?>.Preset preset : info.presets) {
               String title = info.title + "  -  " + preset.title;
               if (Utils.searchTextDefault(title, this.searchQuery, false)) {
                  grouped.computeIfAbsent(info.group, g -> new ArrayList<>()).add(new NewAddHudElementScreen.Item(title, preset));
               }
            }
         } else if (Utils.searchTextDefault(info.title, this.searchQuery, false)) {
            grouped.computeIfAbsent(info.group, g -> new ArrayList<>()).add(new NewAddHudElementScreen.Item(info.title, info));
         }
      }

      return grouped;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      Utils.unscaledProjection();
      HudEditorScreen.renderElements(context);
      Utils.scaledProjection();
      this.positionPanel();
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      int outlineColor = fm.primaryAlpha(170);
      int headerColor = fm.primaryAlpha(220);
      int bg = -268435456 | GuiColors.BG_PANEL & 16777215;
      RenderUtils.fillNative(context, this.panelX, this.panelY, this.panelWidth, this.panelHeight, bg);
      RenderUtils.drawThickOutline(context, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 3, outlineColor);
      RenderUtils.fillNative(context, this.panelX, this.panelY, this.panelWidth, rowH, headerColor);
      context.enableScissor(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + rowH);
      String title = "Add HUD element";
      fm.drawText(context, title, this.panelX + 4, this.panelY + (rowH - fm.getTextHeight()) / 2, fm.getTextColor());
      String close = "✕";
      int closeX = this.panelX + this.panelWidth - fm.getTextWidth(close) - 4;
      fm.drawText(context, close, closeX, this.panelY + (rowH - fm.getTextHeight()) / 2, fm.getTextColor());
      context.disableScissor();
      int searchY = this.panelY + rowH + 2;
      int searchX = this.panelX + 2;
      int searchW = this.panelWidth - 4;
      RenderUtils.fillNative(context, searchX, searchY, searchW, rowH, fm.secondaryAlpha(200));
      String label = this.searchQuery.isEmpty() ? "Search…" : this.searchQuery;
      int textColor = this.searchQuery.isEmpty() ? GuiColors.TEXT_DISABLED : fm.getTextColor();
      fm.drawText(context, label, searchX + 3, searchY + (rowH - fm.getTextHeight()) / 2, textColor);
      if (!this.searchQuery.isEmpty() && (System.currentTimeMillis() / 500L & 1L) == 0L) {
         int caretX = searchX + 3 + fm.getTextWidth(this.searchQuery);
         context.fill(caretX, searchY + 2, caretX + 1, searchY + rowH - 2, fm.getTextColor());
      }

      int listY = searchY + rowH + 1;
      int listBottom = this.panelY + this.panelHeight - 2;
      int listH = Math.max(rowH, listBottom - listY);
      int listX = this.panelX + 2;
      int listW = this.panelWidth - 4;
      context.enableScissor(listX, listY, listX + listW, listY + listH);
      int drawY = listY - this.scroll;
      int totalH = 0;
      Map<HudGroup, List<NewAddHudElementScreen.Item>> grouped = this.buildGrouped();

      for (Entry<HudGroup, List<NewAddHudElementScreen.Item>> entry : grouped.entrySet()) {
         RenderUtils.fillNative(context, listX, drawY, listW, rowH, fm.secondaryAlpha(230));
         fm.drawText(context, entry.getKey().title(), listX + 3, drawY + (rowH - fm.getTextHeight()) / 2, fm.getTextColor());
         drawY += rowH;
         totalH += rowH;

         for (NewAddHudElementScreen.Item item : entry.getValue()) {
            boolean isInfoWithPresets = item.object() instanceof HudElementInfo<?> info && info.hasPresets();
            int btnX = listX + listW - rowH;
            boolean rowHovered = mouseX >= listX && mouseX < listX + listW && mouseY >= drawY && mouseY < drawY + rowH;
            if (rowHovered) {
               RenderUtils.fillNative(context, listX, drawY, listW, rowH, fm.secondaryAlpha(140));
            }

            context.enableScissor(listX, drawY, btnX - 2, drawY + rowH);
            fm.drawText(context, item.title(), listX + 3, drawY + (rowH - fm.getTextHeight()) / 2, fm.getTextColor());
            context.disableScissor();
            String btnLabel = isInfoWithPresets ? ">" : "+";
            boolean btnHovered = mouseX >= btnX && mouseX < btnX + rowH && mouseY >= drawY && mouseY < drawY + rowH;
            int btnFill = isInfoWithPresets
               ? (btnHovered ? fm.secondaryAlpha(240) : fm.secondaryAlpha(200))
               : (btnHovered ? GuiColors.ENABLED : -872415232 | GuiColors.ENABLED & 16777215);
            RenderUtils.fillNative(context, btnX, drawY, rowH, rowH, btnFill);
            fm.drawText(context, btnLabel, btnX + (rowH - fm.getTextWidth(btnLabel)) / 2, drawY + (rowH - fm.getTextHeight()) / 2, fm.getTextColor());
            drawY += rowH;
            totalH += rowH;
         }
      }

      context.disableScissor();
      int maxScroll = Math.max(0, totalH - listH);
      if (maxScroll > 0) {
         int trackH = Math.max(1, listH - 2);
         int thumbH = Math.max(8, (int)((long)listH * trackH / Math.max(1, totalH)));
         int thumbY = listY + 1 + (int)((long)(trackH - thumbH) * this.scroll / maxScroll);
         int sbX = this.panelX + this.panelWidth - 2;
         context.fill(sbX, thumbY, sbX + 1, thumbY + thumbH, fm.primaryAlpha(220));
      }

      if (grouped.isEmpty()) {
         String msg = this.searchQuery.isEmpty() ? "(no elements)" : "No matches.";
         fm.drawText(context, msg, listX + (listW - fm.getTextWidth(msg)) / 2, listY + (listH - fm.getTextHeight()) / 2, GuiColors.TEXT_DISABLED);
      }
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      if (!(mouseX < this.panelX) && !(mouseX >= this.panelX + this.panelWidth) && !(mouseY < this.panelY) && !(mouseY >= this.panelY + this.panelHeight)) {
         String close = "✕";
         int closeX = this.panelX + this.panelWidth - fm.getTextWidth(close) - 4;
         if (mouseX >= closeX - 2 && mouseY >= this.panelY && mouseY < this.panelY + rowH) {
            MinecraftClient.getInstance().setScreen(this.returnTo);
            return true;
         } else {
            int searchY = this.panelY + rowH + 2;
            int listY = searchY + rowH + 1;
            int listBottom = this.panelY + this.panelHeight - 2;
            int listH = Math.max(rowH, listBottom - listY);
            int listX = this.panelX + 2;
            int listW = this.panelWidth - 4;
            if (!(mouseX < listX) && !(mouseX >= listX + listW) && !(mouseY < listY) && !(mouseY >= listY + listH)) {
               int drawY = listY - this.scroll;

               for (Entry<HudGroup, List<NewAddHudElementScreen.Item>> entry : this.buildGrouped().entrySet()) {
                  drawY += rowH;

                  for (NewAddHudElementScreen.Item item : entry.getValue()) {
                     if (mouseY >= drawY && mouseY < drawY + rowH) {
                        this.runObject(item.object());
                        return true;
                     }

                     drawY += rowH;
                  }
               }

               return super.mouseClicked(click, doubled);
            } else {
               return super.mouseClicked(click, doubled);
            }
         }
      } else {
         MinecraftClient.getInstance().setScreen(this.returnTo);
         return true;
      }
   }

   private void runObject(Object object) {
      if (object != null) {
         if (object instanceof HudElementInfo.Preset preset) {
            Hud.get().add(preset, this.spawnX, this.spawnY);
            MinecraftClient.getInstance().setScreen(this.returnTo);
         } else {
            HudElementInfo<?> info = (HudElementInfo<?>)object;
            if (info.hasPresets()) {
               MinecraftClient.getInstance().setScreen(new NewHudElementPresetsScreen(info, this.spawnX, this.spawnY, this, this.returnTo));
            } else {
               Hud.get().add(info, this.spawnX, this.spawnY);
               MinecraftClient.getInstance().setScreen(this.returnTo);
            }
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (!(mouseX < this.panelX) && !(mouseX >= this.panelX + this.panelWidth) && !(mouseY < this.panelY) && !(mouseY >= this.panelY + this.panelHeight)) {
         FontManager fm = FontManager.get();
         int rowH = fm.getRowHeight();
         int listH = Math.max(rowH, this.panelHeight - rowH - (rowH + 2) - 1 - 2);
         int maxScroll = Math.max(0, this.countTotalRows() * rowH - listH);
         this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int)(verticalAmount * rowH * 2.0)));
         return true;
      } else {
         return false;
      }
   }

   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int modifiers = input.modifiers();
      if (keyCode == 256) {
         MinecraftClient.getInstance().setScreen(this.returnTo);
         return true;
      } else if (keyCode == 259 && !this.searchQuery.isEmpty()) {
         this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
         this.scroll = 0;
         return true;
      } else {
         if (keyCode == 257 || keyCode == 335) {
            Map<HudGroup, List<NewAddHudElementScreen.Item>> grouped = this.buildGrouped();

            for (List<NewAddHudElementScreen.Item> items : grouped.values()) {
               if (!items.isEmpty()) {
                  this.runObject(items.get(0).object());
                  return true;
               }
            }
         }

         return super.keyPressed(input);
      }
   }

   public boolean charTyped(CharInput input) {
      char chr = (char)input.codepoint();
      int modifiers = input.modifiers();
      if (chr >= ' ' && chr != 127) {
         this.searchQuery = this.searchQuery + chr;
         this.scroll = 0;
         return true;
      } else {
         return super.charTyped(input);
      }
   }

   public void close() {
      MinecraftClient.getInstance().setScreen(this.returnTo);
   }

   private record Item(String title, Object object) {
   }
}
