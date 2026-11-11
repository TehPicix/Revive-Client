package revivedevelopment.reviveclient.gui.newgui.screens;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.Vector3dSetting;

public class NewVectorEditScreen extends Screen {
   private final BlockPosSetting blockPosSetting;
   private final Vector3dSetting vectorSetting;
   public Screen parent;
   private String[] fields = new String[3];
   private int focusIndex = 0;
   private int panelX;
   private int panelY;
   private int panelWidth;
   private int panelHeight;
   private static final int PADDING = 8;
   private static final int FIELD_HEIGHT = 22;

   public void close() {
      if (this.parent != null) {
         this.client.setScreen(this.parent);
      } else {
         super.close();
      }
   }

   public NewVectorEditScreen(BlockPosSetting setting) {
      super(Text.literal("Edit " + setting.name));
      this.blockPosSetting = setting;
      this.vectorSetting = null;
      BlockPos p = setting.get();
      this.fields[0] = String.valueOf(p.getX());
      this.fields[1] = String.valueOf(p.getY());
      this.fields[2] = String.valueOf(p.getZ());
   }

   public NewVectorEditScreen(Vector3dSetting setting) {
      super(Text.literal("Edit " + setting.name));
      this.blockPosSetting = null;
      this.vectorSetting = setting;
      Vector3d v = setting.get();
      int dp = Math.max(1, setting.decimalPlaces);
      String fmt = "%." + dp + "f";
      this.fields[0] = String.format(fmt, v.x);
      this.fields[1] = String.format(fmt, v.y);
      this.fields[2] = String.format(fmt, v.z);
   }

   protected void init() {
      super.init();
      this.panelWidth = Math.min(360, this.width - 40);
      this.panelHeight = 140;
      this.panelX = (this.width - this.panelWidth) / 2;
      this.panelY = (this.height - this.panelHeight) / 2;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      FontManager fm = FontManager.get();
      super.render(context, mouseX, mouseY, deltaTicks);
      int lineColor = fm.primaryAlpha(180);
      int headerH = fm.getHeaderHeight();
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, fm.primaryAlpha(160));
      RenderUtils.drawThinOutline(context, this.panelX, this.panelY, this.panelWidth, this.panelHeight, lineColor);
      context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + headerH, fm.secondaryAlpha(200));
      RenderUtils.drawThinHLine(context, this.panelX, this.panelY + headerH, this.panelWidth, lineColor);
      fm.drawText(context, this.title.getString(), this.panelX + 8, this.panelY + (headerH - fm.getTextHeight()) / 2, fm.getTextColor());
      String[] labels = new String[]{"X:", "Y:", "Z:"};
      int y = this.panelY + headerH + 8;
      int fx = this.panelX + 8;
      int fw = this.panelWidth - 16;
      int labelW = fm.getTextWidth("Z:") + 6;

      for (int i = 0; i < 3; i++) {
         fm.drawText(context, labels[i], fx, y + (22 - fm.getTextHeight()) / 2, fm.getTextColor());
         int inputX = fx + labelW;
         int inputW = fw - labelW;
         boolean focused = this.focusIndex == i;
         context.fill(inputX, y, inputX + inputW, y + 22, focused ? fm.secondaryAlpha(220) : fm.secondaryAlpha(150));
         RenderUtils.drawThinOutline(context, inputX, y, inputW, 22, lineColor);
         int textY = y + (22 - fm.getTextHeight()) / 2;
         fm.drawText(context, this.fields[i], inputX + 4, textY, fm.getTextColor());
         if (focused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
            int caretX = inputX + 4 + fm.getTextWidth(this.fields[i]);
            context.fill(caretX, y + 3, caretX + 1, y + 22 - 3, fm.getTextColor());
         }

         y += 30;
      }

      int btnY = y + 4;
      String fromMe = "[From Me]";
      String cancel = "[Cancel]";
      String apply = "[Apply]";
      int fromMeW = fm.getTextWidth(fromMe) + 12;
      int cancelW = fm.getTextWidth(cancel) + 12;
      int applyW = fm.getTextWidth(apply) + 12;
      int applyX = fx + fw - applyW;
      int cancelX = applyX - cancelW - 8;
      this.drawButton(context, fm, fromMe, fx, btnY, fromMeW, 22, lineColor);
      this.drawButton(context, fm, cancel, cancelX, btnY, cancelW, 22, lineColor);
      this.drawButton(context, fm, apply, applyX, btnY, applyW, 22, lineColor);
   }

   private void drawButton(DrawContext context, FontManager fm, String label, int x, int y, int w, int h, int lineColor) {
      context.fill(x, y, x + w, y + h, fm.secondaryAlpha(200));
      RenderUtils.drawThinOutline(context, x, y, w, h, lineColor);
      int tx = x + (w - fm.getTextWidth(label)) / 2;
      int ty = y + (h - fm.getTextHeight()) / 2;
      fm.drawText(context, label, tx, ty, fm.getTextColor());
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      FontManager fm = FontManager.get();
      int headerH = fm.getHeaderHeight();
      int y0 = this.panelY + headerH + 8;
      int fx = this.panelX + 8;
      int fw = this.panelWidth - 16;
      int labelW = fm.getTextWidth("Z:") + 6;
      int inputX = fx + labelW;
      int inputW = fw - labelW;

      for (int i = 0; i < 3; i++) {
         int y = y0 + i * 30;
         if (mouseX >= inputX && mouseX < inputX + inputW && mouseY >= y && mouseY < y + 22) {
            this.focusIndex = i;
            return true;
         }
      }

      int btnY = y0 + 90 + 4;
      String fromMe = "[From Me]";
      String cancel = "[Cancel]";
      String apply = "[Apply]";
      int fromMeW = fm.getTextWidth(fromMe) + 12;
      int cancelW = fm.getTextWidth(cancel) + 12;
      int applyW = fm.getTextWidth(apply) + 12;
      int applyX = fx + fw - applyW;
      int cancelX = applyX - cancelW - 8;
      if (inRect(mouseX, mouseY, fx, btnY, fromMeW, 22)) {
         this.fillFromPlayer();
         return true;
      } else if (inRect(mouseX, mouseY, cancelX, btnY, cancelW, 22)) {
         this.close();
         return true;
      } else if (inRect(mouseX, mouseY, applyX, btnY, applyW, 22)) {
         if (this.commit()) {
            this.close();
         }

         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int modifiers = input.modifiers();
      if (keyCode == 256) {
         this.close();
         return true;
      } else if (keyCode != 257 && keyCode != 335) {
         if (keyCode == 258) {
            this.focusIndex = (this.focusIndex + (hasShift(modifiers) ? 2 : 1)) % 3;
            return true;
         } else if (keyCode == 259) {
            if (!this.fields[this.focusIndex].isEmpty()) {
               this.fields[this.focusIndex] = this.fields[this.focusIndex].substring(0, this.fields[this.focusIndex].length() - 1);
            }

            return true;
         } else {
            return super.keyPressed(input);
         }
      } else {
         if (this.commit()) {
            this.close();
         }

         return true;
      }
   }

   public boolean charTyped(CharInput input) {
      char chr = (char)input.codepoint();
      int modifiers = input.modifiers();
      if (chr >= ' ' && chr != 127) {
         boolean acceptsFloat = this.vectorSetting != null;
         boolean ok = chr >= '0' && chr <= '9' || chr == '-' || acceptsFloat && (chr == '.' || chr == 'e' || chr == 'E' || chr == '+');
         if (ok && this.fields[this.focusIndex].length() < 16) {
            this.fields[this.focusIndex] = this.fields[this.focusIndex] + chr;
         }

         return true;
      } else {
         return super.charTyped(input);
      }
   }

   public boolean shouldPause() {
      return false;
   }

   private boolean commit() {
      try {
         if (this.blockPosSetting != null) {
            int x = Integer.parseInt(this.fields[0].trim());
            int y = Integer.parseInt(this.fields[1].trim());
            int z = Integer.parseInt(this.fields[2].trim());
            this.blockPosSetting.set(new BlockPos(x, y, z));
            return true;
         }

         if (this.vectorSetting != null) {
            double x = Double.parseDouble(this.fields[0].trim());
            double y = Double.parseDouble(this.fields[1].trim());
            double z = Double.parseDouble(this.fields[2].trim());
            x = Math.max(this.vectorSetting.min, Math.min(this.vectorSetting.max, x));
            y = Math.max(this.vectorSetting.min, Math.min(this.vectorSetting.max, y));
            z = Math.max(this.vectorSetting.min, Math.min(this.vectorSetting.max, z));
            this.vectorSetting.set(new Vector3d(x, y, z));
            return true;
         }
      } catch (NumberFormatException var7) {
      }

      return false;
   }

   private void fillFromPlayer() {
      PlayerEntity p = MinecraftClient.getInstance().player;
      if (p != null) {
         if (this.blockPosSetting != null) {
            BlockPos pos = p.getBlockPos();
            this.fields[0] = String.valueOf(pos.getX());
            this.fields[1] = String.valueOf(pos.getY());
            this.fields[2] = String.valueOf(pos.getZ());
         } else if (this.vectorSetting != null) {
            int dp = Math.max(1, this.vectorSetting.decimalPlaces);
            String fmt = "%." + dp + "f";
            this.fields[0] = String.format(fmt, p.getX());
            this.fields[1] = String.format(fmt, p.getY());
            this.fields[2] = String.format(fmt, p.getZ());
         }
      }
   }

   private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private static boolean hasShift(int modifiers) {
      return (modifiers & 1) != 0;
   }
}
