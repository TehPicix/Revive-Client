package revivedevelopment.reviveclient.gui.newgui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Category;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;

public class ModuleButton {
   private final Module module;
   private final Category category;
   private static final int ACCENT_X = 3;
   private static ModuleButton listeningForKey = null;
   public static String pendingTipText = null;
   private boolean expanded = false;
   private float expandAnimation = 0.0F;
   private int lastRenderWidth = 0;

   public ModuleButton(Module module, Category category) {
      this.module = module;
      this.category = category;
   }

   private int getRowHeight() {
      return FontManager.get().getRowHeight();
   }

   private int nativePixelGap() {
      float scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
      return Math.max(1, (int)Math.ceil(1.0 / scale));
   }

   private int textY(int y, int rowH) {
      return y + (rowH - FontManager.get().getTextHeight()) / 2;
   }

   private void fillNative(DrawContext context, int x1, int y1, int x2, int y2, int color) {
      float scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(1.0F / scale, 1.0F / scale);
      context.fill((int)(x1 * scale), (int)(y1 * scale), (int)(x2 * scale), (int)(y2 * scale), color);
      matrices.popMatrix();
   }

   public void render(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
      FontManager fm = FontManager.get();
      int rowH = this.getRowHeight();
      this.lastRenderWidth = width;
      float target = this.expanded ? 1.0F : 0.0F;
      this.expandAnimation = this.expandAnimation + (target - this.expandAnimation) * 0.35F;
      if (Math.abs(this.expandAnimation - target) < 0.01F) {
         this.expandAnimation = target;
      }

      if (this.module.isActive()) {
         RenderUtils.fillNativeHInset(context, x, y, width, rowH, 4, fm.secondaryAlpha(160));
      }

      int textColor = this.module.isActive() ? fm.getTextColor() : fm.getTextSecondary();
      boolean titleHovered = mouseY >= y && mouseY < y + rowH && mouseX >= x && mouseX < x + width;
      context.enableScissor(x + 3, y, x + width - 3, y + rowH);
      fm.drawTextMarquee(context, this.module, this.module.title, x + 5, this.textY(y, rowH), x + width - 5, textColor, titleHovered);
      context.disableScissor();
      if (titleHovered && this.module.description != null && !this.module.description.isEmpty()) {
         pendingTipText = this.module.description;
      }

      if (this.expandAnimation > 0.01F && this.hasAnyVisibleSetting()) {
         this.renderSettings(context, x, y + rowH + this.nativePixelGap(), width, mouseX, mouseY);
      }
   }

   private boolean hasAnyVisibleSetting() {
      for (SettingGroup group : this.module.settings) {
         for (Setting<?> setting : group) {
            if (setting.isVisible()) {
               return true;
            }
         }
      }

      return false;
   }

   private void renderSettings(DrawContext context, int x, int y, int width, int mouseX, int mouseY) {
      FontManager fm = FontManager.get();
      int settingsHeight = this.getSettingsHeight();
      int visibleHeight = (int)(settingsHeight * this.expandAnimation);
      context.enableScissor(x, y, x + width, y + visibleHeight);
      RenderUtils.drawThinVLine(context, x + 3, y, settingsHeight, fm.secondaryAlpha(140));
      int descH = this.descriptionHeight(width);
      if (descH > 0) {
         this.renderDescription(context, fm, x, y, width);
      }

      this.renderCopyPasteRow(context, fm, x, y + descH, width, mouseX, mouseY);
      int rowH = this.getRowHeight();
      int currentY = SettingGroupRenderer.renderGroups(context, this.module.settings, x, y + descH + rowH, width, mouseX, mouseY);
      this.renderModuleKeybind(context, fm, x, currentY, width);
      context.disableScissor();
   }

   private List<String> descriptionLines(int width) {
      List<String> lines = new ArrayList<>();
      if (this.module.description != null && !this.module.description.isEmpty() && width > 0) {
         FontManager fm = FontManager.get();
         int maxW = width - 11;
         if (maxW <= 0) {
            return lines;
         } else {
            StringBuilder line = new StringBuilder();

            for (String word : this.module.description.split(" ")) {
               String candidate = line.length() == 0 ? word : line + " " + word;
               if (fm.getTextWidth(candidate) > maxW && line.length() != 0) {
                  lines.add(line.toString());
                  line = new StringBuilder(word);
               } else {
                  line = new StringBuilder(candidate);
               }
            }

            if (line.length() > 0) {
               lines.add(line.toString());
            }

            return lines;
         }
      } else {
         return lines;
      }
   }

   private int descriptionHeight(int width) {
      List<String> lines = this.descriptionLines(width);
      if (lines.isEmpty()) {
         return 0;
      } else {
         int lineH = FontManager.get().getTextHeight() + 1;
         return lines.size() * lineH + 4;
      }
   }

   private void renderDescription(DrawContext context, FontManager fm, int x, int y, int width) {
      List<String> lines = this.descriptionLines(width);
      int lineH = fm.getTextHeight() + 1;
      int lineY = y + 2;

      for (String line : lines) {
         fm.drawText(context, line, x + 3 + 5, lineY, fm.getTextSecondary());
         lineY += lineH;
      }
   }

   private void renderCopyPasteRow(DrawContext context, FontManager fm, int x, int y, int width, int mouseX, int mouseY) {
      int rowH = this.getRowHeight();
      int sliderStart = x + 3 + 1;
      int sliderWidth = width - 3 - 2;
      int halfW = sliderWidth / 2;
      int copyW = halfW - 1;
      boolean copyHovered = mouseX >= sliderStart && mouseX < sliderStart + copyW && mouseY >= y && mouseY < y + rowH;
      this.fillNative(context, sliderStart, y, sliderStart + copyW, y + rowH, copyHovered ? fm.secondaryAlpha(220) : fm.secondaryAlpha(160));
      String copyLabel = "Copy";
      int copyTW = fm.getTextWidth(copyLabel);
      fm.drawText(context, copyLabel, sliderStart + (copyW - copyTW) / 2, this.textY(y, rowH), fm.getTextColor());
      int pasteX = sliderStart + halfW + 1;
      int pasteW = sliderWidth - halfW - 1;
      boolean pasteHovered = mouseX >= pasteX && mouseX < pasteX + pasteW && mouseY >= y && mouseY < y + rowH;
      this.fillNative(context, pasteX, y, pasteX + pasteW, y + rowH, pasteHovered ? fm.secondaryAlpha(220) : fm.secondaryAlpha(160));
      String pasteLabel = "Paste";
      int pasteTW = fm.getTextWidth(pasteLabel);
      fm.drawText(context, pasteLabel, pasteX + (pasteW - pasteTW) / 2, this.textY(y, rowH), fm.getTextColor());
   }

   private void renderModuleKeybind(DrawContext context, FontManager fm, int x, int y, int width) {
      int rowH = this.getRowHeight();
      context.enableScissor(x, y, x + width, y + rowH);
      String text;
      int color;
      if (listeningForKey == this) {
         text = "Key ...";
         color = fm.getTextColor();
      } else {
         text = "Key " + getKeyName(this.module.keybind.getValue());
         color = GuiColors.TEXT_DISABLED;
      }

      fm.drawText(context, text, x + 6, this.textY(y, rowH), color);
      context.disableScissor();
   }

   private static String getKeyName(int key) {
      if (key == -1) {
         return "NONE";
      } else {
         String name = GLFW.glfwGetKeyName(key, 0);
         if (name != null) {
            return name.toUpperCase();
         } else {
            return switch (key) {
               case 32 -> "Space";
               case 256 -> "Esc";
               case 257 -> "Enter";
               case 258 -> "Tab";
               case 259 -> "Back";
               case 260 -> "Ins";
               case 261 -> "Del";
               case 262 -> "Right";
               case 263 -> "Left";
               case 264 -> "Down";
               case 265 -> "Up";
               case 266 -> "PgUp";
               case 267 -> "PgDn";
               case 268 -> "Home";
               case 269 -> "End";
               case 280 -> "Caps";
               case 290 -> "F1";
               case 291 -> "F2";
               case 292 -> "F3";
               case 293 -> "F4";
               case 294 -> "F5";
               case 295 -> "F6";
               case 296 -> "F7";
               case 297 -> "F8";
               case 298 -> "F9";
               case 299 -> "F10";
               case 300 -> "F11";
               case 301 -> "F12";
               case 340 -> "LShift";
               case 341 -> "LCtrl";
               case 342 -> "LAlt";
               case 344 -> "RShift";
               case 345 -> "RCtrl";
               case 346 -> "RAlt";
               default -> "KEY " + key;
            };
         }
      }
   }

   public boolean copyToClipboard() {
      NbtCompound tag = new NbtCompound();
      tag.putString("name", this.module.name);
      NbtCompound settingsTag = this.module.settings.toTag();
      if (!settingsTag.isEmpty()) {
         tag.put("settings", settingsTag);
      }

      return NbtUtils.toClipboard(tag);
   }

   public boolean pasteFromClipboard() {
      NbtCompound tag = NbtUtils.fromClipboard();
      if (tag == null) {
         return false;
      } else if (!tag.getString("name", "").equals(this.module.name)) {
         return false;
      } else {
         Optional<NbtCompound> settings = tag.getCompound("settings");
         if (settings.isPresent()) {
            this.module.settings.fromTag(settings.get());
         } else {
            this.module.settings.reset();
         }

         return true;
      }
   }

   public static boolean onKeyPressed(int key) {
      return SettingGroupRenderer.onKeyPressed(key);
   }

   public static boolean onCharTyped(char chr) {
      return SettingGroupRenderer.onCharTyped(chr);
   }

   public static boolean isListening() {
      return listeningForKey != null || SettingGroupRenderer.isListening();
   }

   public static ItemStack statusEffectIcon(StatusEffect effect) {
      return SettingGroupRenderer.statusEffectIcon(effect);
   }

   public boolean mouseClicked(int x, int y, int width, int mouseX, int mouseY, int button) {
      int rowH = this.getRowHeight();
      if (mouseY >= y && mouseY < y + rowH) {
         if (button == 0) {
            this.module.toggle();
            this.module.sendToggledMsg();
            return true;
         }

         if (button == 1 && this.hasAnyVisibleSetting()) {
            this.expanded = !this.expanded;
            return true;
         }
      }

      if (this.expanded && this.expandAnimation > 0.5F && this.hasAnyVisibleSetting()) {
         int settingsAreaY = y + rowH + this.nativePixelGap();
         int descH = this.descriptionHeight(width);
         int copyPasteY = settingsAreaY + descH;
         if (mouseY >= copyPasteY && mouseY < copyPasteY + rowH && button == 0) {
            int sliderStart = x + 3 + 1;
            int sliderWidth = width - 3 - 2;
            int halfW = sliderWidth / 2;
            int midX = sliderStart + halfW;
            if (mouseX < midX) {
               this.copyToClipboard();
            } else {
               this.pasteFromClipboard();
            }

            return true;
         }

         int settingsY = copyPasteY + rowH;
         if (SettingGroupRenderer.mouseClickedGroups(this.module.settings, x, settingsY, width, mouseX, mouseY, button, () -> null)) {
            return true;
         }

         int settingsHeight = SettingGroupRenderer.getGroupsHeight(this.module.settings);
         int keybindY = settingsY + settingsHeight;
         if (mouseY >= keybindY && mouseY < keybindY + rowH && button == 0) {
            listeningForKey = this;
            Modules.get().setModuleToBind(this.module);
            return true;
         }
      }

      return false;
   }

   public static void clearKeyListeningIfMatches(Module module) {
      if (listeningForKey != null && listeningForKey.module == module) {
         listeningForKey = null;
      }
   }

   public void mouseReleased(int mouseX, int mouseY, int button) {
      SettingGroupRenderer.mouseReleasedGroups();
   }

   public Module getModule() {
      return this.module;
   }

   public int getHeight() {
      int rowH = this.getRowHeight();
      if (this.expandAnimation < 0.01F) {
         return rowH;
      } else {
         int full = rowH + this.getSettingsHeight() + this.nativePixelGap();
         return (int)(rowH + (full - rowH) * this.expandAnimation);
      }
   }

   private int getSettingsHeight() {
      return this.descriptionHeight(this.lastRenderWidth)
         + this.getRowHeight()
         + SettingGroupRenderer.getGroupsHeight(this.module.settings)
         + this.getRowHeight();
   }

   public boolean isExpanded() {
      return this.expanded;
   }
}
