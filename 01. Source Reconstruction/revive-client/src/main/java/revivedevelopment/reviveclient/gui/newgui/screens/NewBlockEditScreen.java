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
import revivedevelopment.reviveclient.settings.Settings;

public class NewBlockEditScreen extends Screen {
   private final Settings settings;
   private final Screen returnTo;
   private int contentX;
   private int contentY;
   private int contentWidth;
   private int contentHeight;
   private int scroll = 0;
   private static final int PADDING = 8;
   private static final int TITLE_HEIGHT = 20;
   private static final int BACK_BUTTON_WIDTH = 60;

   public NewBlockEditScreen(String title, Settings settings, Screen returnTo) {
      super(Text.literal(title));
      this.settings = settings;
      this.returnTo = returnTo;
   }

   public boolean shouldPause() {
      return false;
   }

   protected void init() {
      super.init();
      int margin = Math.max(20, this.width / 6);
      this.contentX = margin;
      this.contentWidth = this.width - margin * 2;
      this.contentY = 28;
      this.contentHeight = this.height - this.contentY - 8;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      FontManager fm = FontManager.get();
      super.render(context, mouseX, mouseY, deltaTicks);
      int lineColor = fm.primaryAlpha(180);
      int fillColor = fm.secondaryAlpha(200);
      int rowH = fm.getRowHeight();
      int backY = 4;
      int backX = this.contentX;
      boolean backHovered = mouseX >= backX && mouseX < backX + 60 && mouseY >= backY && mouseY < backY + rowH + 2;
      int backFill = backHovered ? fm.secondaryAlpha(220) : fm.secondaryAlpha(160);
      context.fill(backX, backY, backX + 60, backY + rowH + 2, backFill);
      RenderUtils.drawThinOutline(context, backX, backY, 60, rowH + 2, lineColor);
      String backLabel = "← Back";
      fm.drawText(context, backLabel, backX + (60 - fm.getTextWidth(backLabel)) / 2, backY + (rowH + 2 - fm.getTextHeight()) / 2, fm.getTextColor());
      fm.drawText(context, this.title.getString(), backX + 60 + 8, backY + (rowH + 2 - fm.getTextHeight()) / 2, fm.getTextColor());
      context.fill(this.contentX, this.contentY, this.contentX + this.contentWidth, this.contentY + this.contentHeight, fm.primaryAlpha(120));
      RenderUtils.drawThinOutline(context, this.contentX, this.contentY, this.contentWidth, this.contentHeight, lineColor);
      int innerX = this.contentX + 8;
      int innerY = this.contentY + 8;
      int innerWidth = this.contentWidth - 16;
      int innerHeight = this.contentHeight - 16;
      int totalH = SettingGroupRenderer.getGroupsHeight(this.settings);
      int drawY = innerY - this.scroll;
      context.enableScissor(innerX, innerY, innerX + innerWidth, innerY + innerHeight);
      SettingGroupRenderer.renderGroups(context, this.settings, innerX, drawY, innerWidth, mouseX, mouseY);
      context.disableScissor();
      if (totalH > innerHeight) {
         int sx = this.contentX + this.contentWidth - 3;
         int scrollH = Math.max(10, innerHeight * innerHeight / totalH);
         int scrollY = innerY + this.scroll * (innerHeight - scrollH) / Math.max(1, totalH - innerHeight);
         context.fill(sx, scrollY, sx + 2, scrollY + scrollH, fm.secondaryAlpha(220));
      }

      if (totalH == 0) {
         String msg = "(no settings)";
         fm.drawText(
            context,
            msg,
            this.contentX + (this.contentWidth - fm.getTextWidth(msg)) / 2,
            this.contentY + (this.contentHeight - fm.getTextHeight()) / 2,
            GuiColors.TEXT_DISABLED
         );
      }
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      int backY = 4;
      int backX = this.contentX;
      if (mouseX >= backX && mouseX < backX + 60 && mouseY >= backY && mouseY < backY + rowH + 2) {
         MinecraftClient.getInstance().setScreen(this.returnTo);
         return true;
      } else {
         SettingGroupRenderer.commitStringEdit();
         int innerX = this.contentX + 8;
         int innerY = this.contentY + 8;
         int innerWidth = this.contentWidth - 16;
         int innerHeight = this.contentHeight - 16;
         if (mouseX >= innerX && mouseX < innerX + innerWidth && mouseY >= innerY && mouseY < innerY + innerHeight) {
            int drawY = innerY - this.scroll;
            boolean handled = SettingGroupRenderer.mouseClickedGroups(this.settings, innerX, drawY, innerWidth, (int)mouseX, (int)mouseY, button, () -> null);
            if (handled) {
               return true;
            }
         }

         return super.mouseClicked(click, doubled);
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
      FontManager fm = FontManager.get();
      int innerHeight = this.contentHeight - 16;
      int totalH = SettingGroupRenderer.getGroupsHeight(this.settings);
      int maxScroll = Math.max(0, totalH - innerHeight);
      this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int)(verticalAmount * fm.getRowHeight() * 2.0)));
      return true;
   }

   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int modifiers = input.modifiers();
      if (SettingGroupRenderer.onKeyPressed(keyCode)) {
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
