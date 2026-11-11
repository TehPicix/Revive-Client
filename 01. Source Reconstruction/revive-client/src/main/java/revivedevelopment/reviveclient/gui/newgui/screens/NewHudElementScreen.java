package revivedevelopment.reviveclient.gui.newgui.screens;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.components.SettingGroupRenderer;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.Settings;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.XAnchor;
import revivedevelopment.reviveclient.systems.hud.YAnchor;
import revivedevelopment.reviveclient.systems.hud.screens.HudEditorScreen;
import revivedevelopment.reviveclient.utils.Utils;

public class NewHudElementScreen extends Screen {
   private final HudElement element;
   private final Screen returnTo;
   private final Settings anchorSettings;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private int listX;
   private int listY;
   private int listW;
   private int listH;
   private int barY;
   private int barHeight;
   private int scroll = 0;
   private static final int PANEL_WIDTH = 200;

   public NewHudElementScreen(HudElement element, Screen returnTo) {
      super(Text.literal(element.info.title));
      this.element = element;
      this.returnTo = returnTo;
      this.anchorSettings = new Settings();
      SettingGroup sg = this.anchorSettings.createGroup("Anchors");
      sg.add(
         new BoolSetting.Builder()
            .name("auto-anchors")
            .description("Automatically assigns anchors based on the position.")
            .defaultValue(true)
            .onModuleActivated(s -> s.set(element.autoAnchors))
            .onChanged(v -> {
               if (v) {
                  element.box.updateAnchors();
               }

               element.autoAnchors = v;
            })
            .build()
      );
      sg.add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder()
                              .name("x-anchor"))
                           .description("Horizontal anchor."))
                        .defaultValue(XAnchor.Left))
                     .visible(() -> !element.autoAnchors))
                  // NOTE (source reconstruction): the repeated raw "(EnumSetting.Builder)" casts
                  // above erase the builder's generic parameter, so "s"/the onChanged value arrive
                  // as Object; cast explicitly rather than via the (now-mistyped) method reference.
                  .onModuleActivated(s -> ((EnumSetting<XAnchor>) s).set(element.box.xAnchor)))
               .onChanged(v -> element.box.setXAnchor((XAnchor) v)))
            .build()
      );
      sg.add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder()
                              .name("y-anchor"))
                           .description("Vertical anchor."))
                        .defaultValue(YAnchor.Top))
                     .visible(() -> !element.autoAnchors))
                  // NOTE (source reconstruction): same raw-type erasure as the x-anchor setting above.
                  .onModuleActivated(s -> ((EnumSetting<YAnchor>) s).set(element.box.yAnchor)))
               .onChanged(v -> element.box.setYAnchor((YAnchor) v)))
            .build()
      );
      this.anchorSettings.onActivated();
      element.settings.onActivated();
   }

   public boolean shouldPause() {
      return false;
   }

   protected void init() {
      super.init();
      this.layoutPanel();
   }

   private void layoutPanel() {
      FontManager fm = FontManager.get();
      int headerH = fm.getHeaderHeight();
      int rowH = fm.getRowHeight();
      this.barHeight = rowH + 2;
      this.panelWidth = Math.min(200, this.width - 20);
      int totalContent = SettingGroupRenderer.getGroupsHeight(this.anchorSettings) + SettingGroupRenderer.getGroupsHeight(this.element.settings);
      int desiredListH = Math.max(rowH, totalContent);
      int desiredH = headerH + desiredListH + this.barHeight;
      int maxH = this.height - 20;
      this.panelHeight = Math.min(desiredH, maxH);
      this.panelX = (this.width - this.panelWidth) / 2;
      this.panelY = (this.height - this.panelHeight) / 2;
      this.listX = this.panelX;
      this.listY = this.panelY + headerH;
      this.listW = this.panelWidth;
      this.listH = this.panelHeight - headerH - this.barHeight;
      this.barY = this.panelY + this.panelHeight - this.barHeight;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      Utils.unscaledProjection();
      HudEditorScreen.renderElements(context);
      Utils.scaledProjection();
      this.layoutPanel();
      FontManager fm = FontManager.get();
      int headerH = fm.getHeaderHeight();
      int rowH = fm.getRowHeight();
      int outlineColor = fm.primaryAlpha(170);
      int headerColor = fm.primaryAlpha(220);
      int backY = 4;
      int backX = 10;
      int backW = 60;
      boolean backHovered = mouseX >= backX && mouseX < backX + backW && mouseY >= backY && mouseY < backY + rowH + 2;
      int backFill = backHovered ? fm.secondaryAlpha(220) : fm.secondaryAlpha(160);
      context.fill(backX, backY, backX + backW, backY + rowH + 2, backFill);
      RenderUtils.drawThinOutline(context, backX, backY, backW, rowH + 2, outlineColor);
      String backLabel = "← Back";
      fm.drawText(context, backLabel, backX + (backW - fm.getTextWidth(backLabel)) / 2, backY + (rowH + 2 - fm.getTextHeight()) / 2, fm.getTextColor());
      RenderUtils.drawThickOutline(context, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 3, outlineColor);
      RenderUtils.fillNative(context, this.panelX, this.panelY, this.panelWidth, headerH, headerColor);
      context.enableScissor(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + headerH);
      fm.drawText(context, this.element.info.title, this.panelX + 4, this.panelY + (headerH - fm.getTextHeight()) / 2, fm.getTextColor());
      context.disableScissor();
      context.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);
      int drawY = this.listY - this.scroll;
      drawY = SettingGroupRenderer.renderGroups(context, this.anchorSettings, this.listX, drawY, this.listW, mouseX, mouseY);
      if (this.element.settings.sizeGroups() > 0) {
         drawY = SettingGroupRenderer.renderGroups(context, this.element.settings, this.listX, drawY, this.listW, mouseX, mouseY);
      }

      context.disableScissor();
      int totalH = SettingGroupRenderer.getGroupsHeight(this.anchorSettings) + SettingGroupRenderer.getGroupsHeight(this.element.settings);
      int maxScroll = Math.max(0, totalH - this.listH);
      if (maxScroll > 0) {
         int trackH = Math.max(1, this.listH - 2);
         int thumbH = Math.max(8, (int)((long)this.listH * trackH / Math.max(1, totalH)));
         int thumbY = this.listY + 1 + (int)((long)(trackH - thumbH) * this.scroll / maxScroll);
         int sbX = this.panelX + this.panelWidth - 2;
         context.fill(sbX, thumbY, sbX + 1, thumbY + thumbH, fm.primaryAlpha(220));
      }

      if (totalH == 0) {
         String msg = "(no settings)";
         fm.drawText(
            context, msg, this.listX + (this.listW - fm.getTextWidth(msg)) / 2, this.listY + (this.listH - fm.getTextHeight()) / 2, GuiColors.TEXT_DISABLED
         );
      }

      int halfW = this.panelWidth / 2;
      int activeX = this.panelX;
      int removeX = this.panelX + halfW;
      int removeW = this.panelWidth - halfW;
      boolean activeHover = mouseX >= activeX && mouseX < activeX + halfW && mouseY >= this.barY && mouseY < this.barY + this.barHeight;
      boolean removeHover = mouseX >= removeX && mouseX < removeX + removeW && mouseY >= this.barY && mouseY < this.barY + this.barHeight;
      int activeFill = this.element.isActive() ? (activeHover ? -12859808 : -13656499) : (activeHover ? fm.secondaryAlpha(240) : fm.secondaryAlpha(180));
      RenderUtils.fillNative(context, activeX, this.barY, halfW, this.barHeight, activeFill);
      int removeFill = removeHover ? -3657166 : -6411734;
      RenderUtils.fillNative(context, removeX, this.barY, removeW, this.barHeight, removeFill);
      RenderUtils.fillNative(context, removeX, this.barY, 1, this.barHeight, outlineColor);
      RenderUtils.fillNative(context, this.panelX, this.barY, this.panelWidth, 1, outlineColor);
      String activeLabel = this.element.isActive() ? "Active: yes" : "Active: no";
      fm.drawText(
         context, activeLabel, activeX + (halfW - fm.getTextWidth(activeLabel)) / 2, this.barY + (this.barHeight - fm.getTextHeight()) / 2, fm.getTextColor()
      );
      String removeLabel = "Remove";
      fm.drawText(
         context, removeLabel, removeX + (removeW - fm.getTextWidth(removeLabel)) / 2, this.barY + (this.barHeight - fm.getTextHeight()) / 2, fm.getTextColor()
      );
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      int backX = 10;
      int backY = 4;
      int backW = 60;
      if (mouseX >= backX && mouseX < backX + backW && mouseY >= backY && mouseY < backY + rowH + 2) {
         MinecraftClient.getInstance().setScreen(this.returnTo);
         return true;
      } else {
         int halfW = this.panelWidth / 2;
         int activeX = this.panelX;
         int removeX = this.panelX + halfW;
         int removeW = this.panelWidth - halfW;
         if (mouseX >= activeX && mouseX < activeX + halfW && mouseY >= this.barY && mouseY < this.barY + this.barHeight) {
            this.element.toggle();
            return true;
         } else if (mouseX >= removeX && mouseX < removeX + removeW && mouseY >= this.barY && mouseY < this.barY + this.barHeight) {
            this.element.remove();
            MinecraftClient.getInstance().setScreen(this.returnTo);
            return true;
         } else {
            SettingGroupRenderer.commitStringEdit();
            if (mouseX >= this.listX && mouseX < this.listX + this.listW && mouseY >= this.listY && mouseY < this.listY + this.listH) {
               int drawY = this.listY - this.scroll;
               if (SettingGroupRenderer.mouseClickedGroups(this.anchorSettings, this.listX, drawY, this.listW, (int)mouseX, (int)mouseY, button, () -> null)) {
                  return true;
               }

               drawY += SettingGroupRenderer.getGroupsHeight(this.anchorSettings);
               if (SettingGroupRenderer.mouseClickedGroups(this.element.settings, this.listX, drawY, this.listW, (int)mouseX, (int)mouseY, button, () -> null)) {
                  return true;
               }
            }

            return super.mouseClicked(click, doubled);
         }
      }
   }

   public boolean mouseReleased(Click click) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      SettingGroupRenderer.mouseReleasedGroups();
      return super.mouseReleased(click);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (!(mouseX < this.listX) && !(mouseX >= this.listX + this.listW) && !(mouseY < this.listY) && !(mouseY >= this.listY + this.listH)) {
         FontManager fm = FontManager.get();
         int totalH = SettingGroupRenderer.getGroupsHeight(this.anchorSettings) + SettingGroupRenderer.getGroupsHeight(this.element.settings);
         int maxScroll = Math.max(0, totalH - this.listH);
         this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int)(verticalAmount * fm.getRowHeight() * 2.0)));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int modifiers = input.modifiers();
      if (SettingGroupRenderer.onKeyPressed(keyCode, modifiers)) {
         return true;
      } else if (keyCode == 256) {
         MinecraftClient.getInstance().setScreen(this.returnTo);
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public boolean charTyped(CharInput input) {
      char chr = (char)input.codepoint();
      int modifiers = input.modifiers();
      return SettingGroupRenderer.onCharTyped(chr) ? true : super.charTyped(input);
   }

   public void close() {
      SettingGroupRenderer.commitStringEdit();
      MinecraftClient.getInstance().setScreen(this.returnTo);
   }
}
