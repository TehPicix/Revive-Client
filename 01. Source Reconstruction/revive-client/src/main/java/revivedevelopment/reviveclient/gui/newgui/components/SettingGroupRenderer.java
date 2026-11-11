package revivedevelopment.reviveclient.gui.newgui.components;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.gui.GuiThemes;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.gui.newgui.screens.NewAmplifierMapScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewBlockDataScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewBlockEditScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewCollectionListScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewColorListScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewEntityTypeScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewSingleValueScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewStringListScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewVectorEditScreen;
import revivedevelopment.reviveclient.gui.newgui.util.RenderUtils;
import revivedevelopment.reviveclient.gui.utils.IScreenFactory;
import revivedevelopment.reviveclient.renderer.Fonts;
import revivedevelopment.reviveclient.renderer.text.FontFace;
import revivedevelopment.reviveclient.renderer.text.FontFamily;
import revivedevelopment.reviveclient.renderer.text.FontInfo;
import revivedevelopment.reviveclient.settings.ActionSetting;
import revivedevelopment.reviveclient.settings.BlockDataSetting;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.BlockSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorListSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnchantmentListSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.FontFaceSetting;
import revivedevelopment.reviveclient.settings.GenericSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.ItemSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.ModuleListSetting;
import revivedevelopment.reviveclient.settings.PacketListSetting;
import revivedevelopment.reviveclient.settings.ParticleTypeListSetting;
import revivedevelopment.reviveclient.settings.ProvidedStringSetting;
import revivedevelopment.reviveclient.settings.ScreenHandlerListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.SoundEventListSetting;
import revivedevelopment.reviveclient.settings.StatusEffectAmplifierMapSetting;
import revivedevelopment.reviveclient.settings.StatusEffectListSetting;
import revivedevelopment.reviveclient.settings.StorageBlockListSetting;
import revivedevelopment.reviveclient.settings.StringListSetting;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.settings.Vector3dSetting;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.misc.IChangeable;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.misc.Names;
import revivedevelopment.reviveclient.utils.network.PacketUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public final class SettingGroupRenderer {
   static final int ACCENT_X = 3;
   static StringSetting editingString = null;
   static String editingBuffer = "";
   static boolean draggingSlider = false;
   static Setting<?> draggingSetting = null;
   static ColorSetting draggingColorSetting = null;
   static int draggingColorComponent = -1;
   static final Map<ColorSetting, SettingGroupRenderer.PickerState> pickerStates = new HashMap<>();
   private static SettingGroupRenderer.DragCtx dragCtx;
   static final int SV_ROWS = 6;
   static final int PICKER_ROWS = 9;

   private SettingGroupRenderer() {
   }

   public static int renderGroups(DrawContext context, Iterable<SettingGroup> groups, int x, int y, int width, int mouseX, int mouseY) {
      tickDrag(mouseX, mouseY);
      FontManager fm = FontManager.get();
      int currentY = y;

      for (SettingGroup group : groups) {
         currentY = renderSettingGroup(context, fm, group, x, currentY, width, mouseX, mouseY);
      }

      return currentY;
   }

   public static int getGroupsHeight(Iterable<SettingGroup> groups) {
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      int total = 0;

      for (SettingGroup group : groups) {
         total += rowH;
         if (group.sectionExpanded) {
            for (Setting<?> setting : group) {
               if (setting.isVisible()) {
                  total += getSettingRows(setting) * rowH;
               }
            }
         }
      }

      return total;
   }

   public static boolean mouseClickedGroups(
      Iterable<SettingGroup> groups, int x, int y, int width, int mouseX, int mouseY, int button, Supplier<Screen> fallback
   ) {
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      int settingY = y;

      for (SettingGroup group : groups) {
         if (mouseY >= settingY && mouseY < settingY + rowH && (button == 0 || button == 1)) {
            group.sectionExpanded = !group.sectionExpanded;
            return true;
         }

         settingY += rowH;
         if (group.sectionExpanded) {
            for (Setting<?> setting : group) {
               if (setting.isVisible()) {
                  int consumed = handleSettingClick(setting, x, settingY, width, mouseX, mouseY, button, fallback);
                  if (consumed < 0) {
                     return true;
                  }

                  settingY += consumed;
               }
            }
         }
      }

      return false;
   }

   public static void mouseReleasedGroups() {
      draggingSlider = false;
      draggingSetting = null;
      draggingColorSetting = null;
      draggingColorComponent = -1;
   }

   public static boolean onKeyPressed(int key) {
      return onKeyPressed(key, 0);
   }

   public static boolean onKeyPressed(int key, int modifiers) {
      if (editingString == null) {
         return false;
      } else if (key == 257 || key == 335) {
         commitStringEdit();
         return true;
      } else if (key == 256) {
         editingString = null;
         editingBuffer = "";
         return true;
      } else if (key == 259) {
         if (!editingBuffer.isEmpty()) {
            editingBuffer = editingBuffer.substring(0, editingBuffer.length() - 1);
         }

         return true;
      } else if (key == 86 && (modifiers & 2) != 0) {
         String clip = MinecraftClient.getInstance().keyboard.getClipboard();
         if (clip != null && !clip.isEmpty()) {
            clip = clip.replace("\n", "").replace("\r", "");

            for (int i = 0; i < clip.length(); i++) {
               char c = clip.charAt(i);
               String next = editingBuffer + c;
               if (editingString.filter == null || editingString.filter.filter(next, c)) {
                  editingBuffer = next;
               }
            }
         }

         return true;
      } else {
         return true;
      }
   }

   public static boolean onCharTyped(char chr) {
      if (editingString == null) {
         return false;
      } else {
         if (chr >= ' ' && chr != 127) {
            String next = editingBuffer + chr;
            if (editingString.filter == null || editingString.filter.filter(next, chr)) {
               editingBuffer = next;
            }
         }

         return true;
      }
   }

   public static boolean isListening() {
      return NewGuiBindCapture.get().isListeningForSetting() || editingString != null;
   }

   public static void commitStringEdit() {
      if (editingString != null) {
         editingString.set(editingBuffer);
         editingString = null;
         editingBuffer = "";
      }
   }

   public static void tickDrag(int mouseX, int mouseY) {
      if (draggingSlider && draggingSetting instanceof IntSetting is) {
         SettingGroupRenderer.DragCtx ctx = dragCtx;
         if (ctx != null) {
            updateIntSlider(is, ctx.x, ctx.width, mouseX);
         }
      } else if (draggingSlider && draggingSetting instanceof DoubleSetting ds) {
         SettingGroupRenderer.DragCtx ctx = dragCtx;
         if (ctx != null) {
            updateDoubleSlider(ds, ctx.x, ctx.width, mouseX);
         }
      } else if (draggingColorSetting != null && draggingColorComponent >= 0) {
         SettingGroupRenderer.DragCtx ctx = dragCtx;
         if (ctx != null) {
            updateColorSlider(draggingColorSetting, draggingColorComponent, ctx.x + 3 + 1, ctx.width - 3 - 2, mouseX, mouseY, ctx.svTop, ctx.svHeight);
         }
      }
   }

   public static void tickDrag(int mouseX) {
      tickDrag(mouseX, 0);
   }

   static int textY(int y, int rowH) {
      return y + (rowH - FontManager.get().getTextHeight()) / 2;
   }

   static void fillNative(DrawContext context, int x1, int y1, int x2, int y2, int color) {
      float scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
      Matrix3x2fStack matrices = context.getMatrices();
      matrices.pushMatrix();
      matrices.scale(1.0F / scale, 1.0F / scale);
      context.fill((int)(x1 * scale), (int)(y1 * scale), (int)(x2 * scale), (int)(y2 * scale), color);
      matrices.popMatrix();
   }

   private static int renderSettingGroup(DrawContext context, FontManager fm, SettingGroup group, int x, int y, int width, int mouseX, int mouseY) {
      int rowH = fm.getRowHeight();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      boolean hovered = mouseY >= y && mouseY < y + rowH && mouseX >= x && mouseX < x + width;
      fm.drawTextMarquee(context, group, group.name, clipLeft + 3, textY(y, rowH), clipRight - 12, fm.getTextColor(), hovered);
      String toggle = group.sectionExpanded ? "-" : "+";
      fm.drawText(context, toggle, clipRight - fm.getTextWidth(toggle) - 2, textY(y, rowH), fm.getTextColor());
      context.disableScissor();
      y += rowH;
      if (group.sectionExpanded) {
         for (Setting<?> setting : group) {
            if (setting.isVisible()) {
               y = renderSetting(context, fm, setting, x, y, width, mouseX, mouseY);
            }
         }
      }

      return y;
   }

   private static int renderSetting(DrawContext context, FontManager fm, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY) {
      int rowH = fm.getRowHeight();
      boolean hovered = mouseY >= y && mouseY < y + rowH && mouseX >= x && mouseX < x + width;
      if (setting instanceof BoolSetting bs) {
         renderBoolSetting(context, fm, bs, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof EnumSetting<?> es) {
         renderEnumSetting(context, fm, es, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof IntSetting is) {
         renderIntSetting(context, fm, is, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof DoubleSetting ds) {
         renderDoubleSetting(context, fm, ds, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof ColorSetting cs) {
         renderColorSetting(context, fm, cs, x, y, width);
         return y + rowH * (1 + getPickerRows(cs));
      } else if (setting instanceof KeybindSetting ks) {
         renderKeybindSetting(context, fm, ks, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof ProvidedStringSetting pss) {
         renderProvidedStringSetting(context, fm, pss, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof FontFaceSetting fs) {
         renderFontFaceSetting(context, fm, fs, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof BlockPosSetting bps) {
         renderBlockPosSetting(context, fm, bps, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof Vector3dSetting vs) {
         renderVector3dSetting(context, fm, vs, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof StringListSetting sls) {
         renderJumpRow(context, fm, setting, sls.get().size() + " entries", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof ModuleListSetting mls) {
         renderJumpRow(context, fm, setting, mls.get().size() + " modules", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof EntityTypeListSetting etls) {
         renderJumpRow(context, fm, setting, etls.get().size() + " entities", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof EnchantmentListSetting els) {
         renderJumpRow(context, fm, setting, els.get().size() + " enchants", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof BlockListSetting bls) {
         renderJumpRow(context, fm, setting, bls.get().size() + " blocks", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof ItemListSetting ils) {
         renderJumpRow(context, fm, setting, ils.get().size() + " items", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof StatusEffectListSetting sels) {
         renderJumpRow(context, fm, setting, sels.get().size() + " effects", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof SoundEventListSetting sevls) {
         renderJumpRow(context, fm, setting, sevls.get().size() + " sounds", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof ParticleTypeListSetting ptls) {
         renderJumpRow(context, fm, setting, ptls.get().size() + " particles", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof PacketListSetting pls) {
         renderJumpRow(context, fm, setting, pls.get().size() + " packets", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof ScreenHandlerListSetting shls) {
         renderJumpRow(context, fm, setting, shls.get().size() + " handlers", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof StorageBlockListSetting sbls) {
         renderJumpRow(context, fm, setting, sbls.get().size() + " storages", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof BlockSetting bs2) {
         String name = bs2.get() == null ? "(none)" : Names.get(bs2.get());
         renderJumpRow(context, fm, setting, name, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof ItemSetting is2) {
         String name = is2.get() == null ? "(none)" : Names.get(is2.get());
         renderJumpRow(context, fm, setting, name, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof ColorListSetting cls) {
         renderJumpRow(context, fm, setting, cls.get().size() + " colors", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof StatusEffectAmplifierMapSetting seams) {
         renderJumpRow(context, fm, setting, seams.get().size() + " effects", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof BlockDataSetting<?> bds) {
         int overrideCount = 0;

         for (Entry entry : bds.get().entrySet()) {
            Object v = entry.getValue();
            if (v != null && ((IChangeable)v).isChanged()) {
               overrideCount++;
            }
         }

         String summary = overrideCount == 0 ? "no overrides" : overrideCount + " override" + (overrideCount == 1 ? "" : "s");
         renderJumpRow(context, fm, setting, summary, x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof GenericSetting) {
         renderJumpRow(context, fm, setting, "edit", x, y, width, hovered);
         return y + rowH;
      } else if (setting instanceof StringSetting ss) {
         renderStringSetting(context, fm, ss, x, y, width);
         return y + rowH;
      } else if (setting instanceof ActionSetting as) {
         renderJumpRow(context, fm, setting, as.buttonLabel, x, y, width, hovered);
         return y + rowH;
      } else {
         renderStubSetting(context, fm, setting, x, y, width);
         return y + rowH;
      }
   }

   static int getSettingRows(Setting<?> setting) {
      return setting instanceof ColorSetting cs ? 1 + getPickerRows(cs) : 1;
   }

   private static void renderBoolSetting(DrawContext context, FontManager fm, BoolSetting setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      if (setting.get()) {
         fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      }

      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      int textColor = setting.get() ? fm.getTextColor() : fm.getTextSecondary();
      fm.drawTextMarquee(context, setting, setting.name, clipLeft + 3, textY(y, rowH), clipRight - 12, textColor, hovered);
      String toggle = setting.get() ? "+" : "-";
      int toggleColor = setting.get() ? fm.getTextColor() : GuiColors.TEXT_DISABLED;
      fm.drawText(context, toggle, clipRight - fm.getTextWidth(toggle) - 2, textY(y, rowH), toggleColor);
      context.disableScissor();
   }

   private static void renderEnumSetting(DrawContext context, FontManager fm, EnumSetting<?> setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      fm.drawTextMarquee(
         context, setting, setting.name + ": " + setting.get().toString(), clipLeft + 3, textY(y, rowH), clipRight - 2, fm.getTextColor(), hovered
      );
      context.disableScissor();
   }

   private static void renderIntSetting(DrawContext context, FontManager fm, IntSetting setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      int sliderStart = x + 3 + 1;
      if (!setting.noSlider) {
         double range = setting.sliderMax - setting.sliderMin;
         float progress = range > 0.0 ? (float)((setting.get() - setting.sliderMin) / range) : 0.0F;
         progress = Math.max(0.0F, Math.min(1.0F, progress));
         int fillWidth = Math.max(1, (int)((width - 3 - 2) * progress));
         fillNative(context, sliderStart, y, sliderStart + fillWidth, y + rowH, fm.secondaryAlpha(120));
      }

      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      fm.drawTextMarquee(
         context, setting, setting.name + ": " + setting.get(), clipLeft + 3, textY(y, rowH), clipRight - 2, GuiColors.TEXT_SETTING_VALUE, hovered
      );
      context.disableScissor();
   }

   private static void renderDoubleSetting(DrawContext context, FontManager fm, DoubleSetting setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      int sliderStart = x + 3 + 1;
      if (!setting.noSlider) {
         double range = setting.sliderMax - setting.sliderMin;
         float progress = range > 0.0 ? (float)((setting.get() - setting.sliderMin) / range) : 0.0F;
         progress = Math.max(0.0F, Math.min(1.0F, progress));
         int fillWidth = Math.max(1, (int)((width - 3 - 2) * progress));
         fillNative(context, sliderStart, y, sliderStart + fillWidth, y + rowH, fm.secondaryAlpha(120));
      }

      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      int dp = Math.max(1, setting.decimalPlaces);
      String valueStr = String.format("%." + dp + "f", setting.get());
      fm.drawTextMarquee(context, setting, setting.name + ": " + valueStr, clipLeft + 3, textY(y, rowH), clipRight - 2, GuiColors.TEXT_SETTING_VALUE, hovered);
      context.disableScissor();
   }

   private static void renderColorSetting(DrawContext context, FontManager fm, ColorSetting setting, int x, int y, int width) {
      int rowH = fm.getRowHeight();
      SettingColor c = setting.get();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      int swatchSize = Math.min(Math.max(5, rowH - 5), 7);
      int swatchRight = clipRight - 3;
      int swatchLeft = swatchRight - swatchSize;
      int swatchTop = y + (rowH - swatchSize) / 2;
      int swatchBottom = swatchTop + swatchSize;
      drawCheckerboard(context, swatchLeft, swatchTop, swatchSize, swatchSize);
      int argb = (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
      fillNative(context, swatchLeft, swatchTop, swatchRight, swatchBottom, argb);
      RenderUtils.drawThinOutline(context, swatchLeft, swatchTop, swatchSize, swatchSize, GuiColors.BORDER_LIGHT);
      context.enableScissor(clipLeft, y, swatchLeft - 2, y + rowH);
      fm.drawText(context, setting.name, clipLeft + 3, textY(y, rowH), fm.getTextColor());
      context.disableScissor();
      if (isPickerOpen(setting)) {
         renderColorPicker(context, fm, setting, x, y + rowH, width);
      }
   }

   private static void renderColorPicker(DrawContext context, FontManager fm, ColorSetting setting, int x, int y, int width) {
      SettingGroupRenderer.PickerState ps = ensurePicker(setting);
      int rowH = fm.getRowHeight();
      int sliderStart = x + 3 + 1;
      int sliderWidth = width - 3 - 2;
      int svHeight = rowH * 6;
      drawSVGradient(context, ps.hue, sliderStart, y, sliderWidth, svHeight);
      RenderUtils.drawThinOutline(context, sliderStart, y, sliderWidth, svHeight, GuiColors.BORDER);
      int svX = sliderStart + (int)(ps.saturation * (sliderWidth - 1));
      int svY = y + (int)((1.0F - ps.brightness) * (svHeight - 1));
      drawCrosshair(context, svX, svY);
      int hueRow = y + svHeight;
      drawHueStrip(context, sliderStart, hueRow, sliderWidth, rowH);
      RenderUtils.drawThinOutline(context, sliderStart, hueRow, sliderWidth, rowH, GuiColors.BORDER);
      int huePos = sliderStart + (int)(ps.hue * (sliderWidth - 1));
      drawBarMarker(context, huePos, hueRow, rowH);
      int aRow = hueRow + rowH;
      SettingColor c = setting.get();
      drawCheckerboard(context, sliderStart, aRow, sliderWidth, rowH);
      drawAlphaGradient(context, c.r & 0xFF, c.g & 0xFF, c.b & 0xFF, sliderStart, aRow, sliderWidth, rowH);
      RenderUtils.drawThinOutline(context, sliderStart, aRow, sliderWidth, rowH, GuiColors.BORDER);
      int aPos = sliderStart + (int)((c.a & 0xFF) / 255.0F * (sliderWidth - 1));
      drawBarMarker(context, aPos, aRow, rowH);
      String alphaLabel = "A " + (c.a & 0xFF);
      fm.drawText(context, alphaLabel, sliderStart + 3, textY(aRow, rowH), -1);
      int btnRow = aRow + rowH;
      int halfW = sliderWidth / 2;
      drawPickerButton(context, fm, "Copy", sliderStart, btnRow, halfW - 1, rowH);
      drawPickerButton(context, fm, "Paste", sliderStart + halfW + 1, btnRow, sliderWidth - halfW - 1, rowH);
   }

   private static void drawHueStrip(DrawContext context, int x, int y, int w, int h) {
      for (int i = 0; i < w; i++) {
         float hue = (float)i / Math.max(1, w - 1);
         int rgb = Color.HSBtoRGB(hue, 1.0F, 1.0F);
         int argb = 0xFF000000 | rgb & 16777215;
         fillNative(context, x + i, y, x + i + 1, y + h, argb);
      }
   }

   private static void drawSVGradient(DrawContext context, float hue, int x, int y, int w, int h) {
      int pure = Color.HSBtoRGB(hue, 1.0F, 1.0F);
      int hr = pure >> 16 & 0xFF;
      int hg = pure >> 8 & 0xFF;
      int hb = pure & 0xFF;

      for (int i = 0; i < w; i++) {
         float sat = (float)i / Math.max(1, w - 1);
         int r = (int)((1.0F - sat) * 255.0F + sat * hr);
         int g = (int)((1.0F - sat) * 255.0F + sat * hg);
         int b = (int)((1.0F - sat) * 255.0F + sat * hb);
         int baseColor = 0xFF000000 | r << 16 | g << 8 | b;
         fillNative(context, x + i, y, x + i + 1, y + h, baseColor);
      }

      for (int j = 0; j < h; j++) {
         float t = (float)j / Math.max(1, h - 1);
         int alpha = (int)(t * 255.0F);
         int overlay = alpha << 24;
         fillNative(context, x, y + j, x + w, y + j + 1, overlay);
      }
   }

   private static void drawAlphaGradient(DrawContext context, int r, int g, int b, int x, int y, int w, int h) {
      for (int i = 0; i < w; i++) {
         float t = (float)i / Math.max(1, w - 1);
         int alpha = (int)(t * 255.0F);
         int color = alpha << 24 | r << 16 | g << 8 | b;
         fillNative(context, x + i, y, x + i + 1, y + h, color);
      }
   }

   private static void drawCheckerboard(DrawContext context, int x, int y, int w, int h) {
      int square = 3;
      int lightGrey = -3355444;
      int darkGrey = -8355712;
      fillNative(context, x, y, x + w, y + h, lightGrey);

      for (int i = 0; i < w; i += square) {
         for (int j = 0; j < h; j += square) {
            if ((i / square + j / square & 1) != 0) {
               int x2 = Math.min(x + i + square, x + w);
               int y2 = Math.min(y + j + square, y + h);
               fillNative(context, x + i, y + j, x2, y2, darkGrey);
            }
         }
      }
   }

   private static void drawCrosshair(DrawContext context, int cx, int cy) {
      fillNative(context, cx - 1, cy - 1, cx + 2, cy + 2, -16777216);
      fillNative(context, cx, cy, cx + 1, cy + 1, -1);
   }

   private static void drawBarMarker(DrawContext context, int cx, int y, int h) {
      fillNative(context, cx - 1, y - 1, cx + 2, y + h + 1, -16777216);
      fillNative(context, cx, y, cx + 1, y + h, -1);
   }

   private static void drawPickerButton(DrawContext context, FontManager fm, String label, int x, int y, int w, int h) {
      fillNative(context, x, y, x + w, y + h, fm.secondaryAlpha(200));
      RenderUtils.drawThinOutline(context, x, y, w, h, GuiColors.BORDER);
      int textW = fm.getTextWidth(label);
      fm.drawText(context, label, x + (w - textW) / 2, textY(y, h), fm.getTextColor());
   }

   private static void renderBlockPosSetting(DrawContext context, FontManager fm, BlockPosSetting setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      BlockPos p = setting.get();
      String text = setting.name + ": " + p.getX() + "," + p.getY() + "," + p.getZ();
      fm.drawTextMarquee(context, setting, text, clipLeft + 3, textY(y, rowH), clipRight - 2, fm.getTextColor(), hovered);
      context.disableScissor();
   }

   private static void renderVector3dSetting(DrawContext context, FontManager fm, Vector3dSetting setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      Vector3d v = setting.get();
      int dp = Math.max(1, setting.decimalPlaces);
      String fmt = "%." + dp + "f";
      String text = setting.name + ": " + String.format(fmt, v.x) + "," + String.format(fmt, v.y) + "," + String.format(fmt, v.z);
      fm.drawTextMarquee(context, setting, text, clipLeft + 3, textY(y, rowH), clipRight - 2, fm.getTextColor(), hovered);
      context.disableScissor();
   }

   private static void renderJumpRow(DrawContext context, FontManager fm, Setting<?> setting, String summary, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      fm.drawText(context, setting.name, clipLeft + 3, textY(y, rowH), fm.getTextColor());
      String hint = summary + "  ›";
      int hintColor = hovered ? fm.getTextColor() : GuiColors.TEXT_SETTING_VALUE;
      fm.drawText(context, hint, clipRight - fm.getTextWidth(hint) - 2, textY(y, rowH), hintColor);
      context.disableScissor();
   }

   private static void renderStubSetting(DrawContext context, FontManager fm, Setting<?> setting, int x, int y, int width) {
      int rowH = fm.getRowHeight();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.primaryAlpha(60));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      fm.drawText(context, setting.name, clipLeft + 3, textY(y, rowH), GuiColors.TEXT_STUB);
      String hint = "edit →";
      fm.drawText(context, hint, clipRight - fm.getTextWidth(hint) - 2, textY(y, rowH), GuiColors.TEXT_STUB);
      context.disableScissor();
   }

   private static void renderStringSetting(DrawContext context, FontManager fm, StringSetting setting, int x, int y, int width) {
      int rowH = fm.getRowHeight();
      boolean editing = editingString == setting;
      if (editing) {
         fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(200));
      }

      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      String label = setting.name + ": ";
      int labelW = fm.getTextWidth(label);
      fm.drawText(context, label, clipLeft + 3, textY(y, rowH), GuiColors.TEXT_SETTING_VALUE);
      String text = editing ? editingBuffer : setting.get();
      int textX = clipLeft + 3 + labelW;
      int textW = fm.getTextWidth(text);
      int availW = clipRight - 2 - textX;
      if (editing && textW > availW) {
         textX -= textW - availW;
      }

      fm.drawText(context, text, textX, textY(y, rowH), fm.getTextColor());
      if (editing && (System.currentTimeMillis() / 500L & 1L) == 0L) {
         int caretX = textX + textW;
         fillNative(context, caretX, y + 2, caretX + 1, y + rowH - 2, fm.getTextColor());
      }

      context.disableScissor();
   }

   private static void renderProvidedStringSetting(DrawContext context, FontManager fm, ProvidedStringSetting setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      fm.drawTextMarquee(context, setting, setting.name + ": " + setting.get(), clipLeft + 3, textY(y, rowH), clipRight - 2, fm.getTextColor(), hovered);
      context.disableScissor();
   }

   private static void renderFontFaceSetting(DrawContext context, FontManager fm, FontFaceSetting setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      fillNative(context, x + 3 + 1, y, x + width - 1, y + rowH, fm.secondaryAlpha(180));
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      String shown = setting.get() != null ? setting.get().toString() : "(none)";
      fm.drawTextMarquee(context, setting, setting.name + ": " + shown, clipLeft + 3, textY(y, rowH), clipRight - 2, fm.getTextColor(), hovered);
      context.disableScissor();
   }

   private static void renderKeybindSetting(DrawContext context, FontManager fm, KeybindSetting setting, int x, int y, int width, boolean hovered) {
      int rowH = fm.getRowHeight();
      int clipLeft = x + 3 + 1;
      int clipRight = x + width - 1;
      context.enableScissor(clipLeft, y, clipRight, y + rowH);
      String text;
      int color;
      if (NewGuiBindCapture.get().getListeningSetting() == setting) {
         text = setting.name + ": ...";
         color = fm.getTextColor();
      } else {
         Keybind kb = setting.get();
         text = setting.name + ": " + (kb.isSet() ? kb.toString() : "NONE");
         color = GuiColors.TEXT_SETTING_VALUE;
      }

      fm.drawTextMarquee(context, setting, text, clipLeft + 3, textY(y, rowH), clipRight - 2, color, hovered);
      context.disableScissor();
   }

   public static int handleSettingClick(Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, int button, Supplier<Screen> fallback) {
      FontManager fm = FontManager.get();
      int rowH = fm.getRowHeight();
      if (setting instanceof BoolSetting bs) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            bs.set(!bs.get());
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof EnumSetting<?> es) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            cycleEnum(es);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof IntSetting is) {
         if (mouseY >= y && mouseY < y + rowH && button == 0 && !is.noSlider) {
            draggingSlider = true;
            draggingSetting = is;
            dragCtx = new SettingGroupRenderer.DragCtx(x, width);
            updateIntSlider(is, x, width, mouseX);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof DoubleSetting ds) {
         if (mouseY >= y && mouseY < y + rowH && button == 0 && !ds.noSlider) {
            draggingSlider = true;
            draggingSetting = ds;
            dragCtx = new SettingGroupRenderer.DragCtx(x, width);
            updateDoubleSlider(ds, x, width, mouseX);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof ColorSetting cs) {
         if (mouseY >= y && mouseY < y + rowH) {
            if (button == 1) {
               togglePicker(cs);
               return -1;
            } else if (button == 0) {
               togglePicker(cs);
               return -1;
            } else {
               return -1;
            }
         } else if (isPickerOpen(cs)) {
            int baseY = y + rowH;
            int ss = x + 3 + 1;
            int sw = width - 3 - 2;
            int svHeight = rowH * 6;
            int hueRow = baseY + svHeight;
            int alphaRow = hueRow + rowH;
            int btnRow = alphaRow + rowH;
            if (button == 0 && mouseY >= baseY && mouseY < baseY + svHeight) {
               draggingColorSetting = cs;
               draggingColorComponent = 0;
               dragCtx = new SettingGroupRenderer.DragCtx(x, width, baseY, svHeight);
               updateColorSlider(cs, 0, ss, sw, mouseX, mouseY, baseY, svHeight);
               return -1;
            } else if (button == 0 && mouseY >= hueRow && mouseY < hueRow + rowH) {
               draggingColorSetting = cs;
               draggingColorComponent = 1;
               dragCtx = new SettingGroupRenderer.DragCtx(x, width, baseY, svHeight);
               updateColorSlider(cs, 1, ss, sw, mouseX, mouseY, baseY, svHeight);
               return -1;
            } else if (button == 0 && mouseY >= alphaRow && mouseY < alphaRow + rowH) {
               draggingColorSetting = cs;
               draggingColorComponent = 2;
               dragCtx = new SettingGroupRenderer.DragCtx(x, width, baseY, svHeight);
               updateColorSlider(cs, 2, ss, sw, mouseX, mouseY, baseY, svHeight);
               return -1;
            } else if (button == 0 && mouseY >= btnRow && mouseY < btnRow + rowH) {
               int mid = ss + sw / 2;
               if (mouseX < mid) {
                  copyColorToClipboard(cs);
               } else {
                  pasteColorFromClipboard(cs);
               }

               return -1;
            } else {
               return rowH * (1 + getPickerRows(cs));
            }
         } else {
            return rowH;
         }
      } else if (setting instanceof KeybindSetting ks) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            NewGuiBindCapture.get().startListeningForSetting(ks);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof ProvidedStringSetting pss) {
         if (mouseY >= y && mouseY < y + rowH) {
            if (button == 0) {
               cycleProvidedString(pss, pss.supplier.get(), 1);
               return -1;
            }

            if (button == 1) {
               cycleProvidedString(pss, pss.supplier.get(), -1);
               return -1;
            }
         }

         return rowH;
      } else if (setting instanceof FontFaceSetting fs) {
         if (mouseY >= y && mouseY < y + rowH) {
            List<FontFace> faces = buildFontFaces();
            if (button == 0) {
               cycleFontFace(fs, faces, 1);
               return -1;
            }

            if (button == 1) {
               cycleFontFace(fs, faces, -1);
               return -1;
            }
         }

         return rowH;
      } else if (setting instanceof StringSetting ss) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            commitStringEdit();
            editingString = ss;
            editingBuffer = ss.get();
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof BlockPosSetting bps) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            MinecraftClient.getInstance().setScreen(new NewVectorEditScreen(bps));
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof Vector3dSetting v3s) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            MinecraftClient.getInstance().setScreen(new NewVectorEditScreen(v3s));
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof StringListSetting sls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            MinecraftClient.getInstance().setScreen(new NewStringListScreen(sls));
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof ModuleListSetting mls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            NewCollectionListScreen<Module> screen = new NewCollectionListScreen<>(
               "Select Modules — " + mls.name, mls, mls.get(), Modules.get().getAll(), m -> m.title
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof EntityTypeListSetting etls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            MinecraftClient.getInstance().setScreen(new NewEntityTypeScreen(etls));
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof EnchantmentListSetting els) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<RegistryKey<Enchantment>> all = new ArrayList<>();
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld world = mc.world;
            ClientPlayNetworkHandler nh = mc.getNetworkHandler();
            Registry<Enchantment> reg = null;
            if (world != null) {
               reg = (Registry<Enchantment>)world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
            }

            if (reg == null && nh != null) {
               reg = (Registry<Enchantment>)nh.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
            }

            if (reg != null) {
               reg.streamKeys().forEach(all::add);
            } else {
               all.addAll(els.get());
            }

            NewCollectionListScreen<RegistryKey<Enchantment>> screen = new NewCollectionListScreen<>(
               "Select Enchantments — " + els.name, els, els.get(), all, Names::get
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof BlockListSetting bls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<Block> allx = new ArrayList<>();

            for (Block b : Registries.BLOCK) {
               allx.add(b);
            }

            NewCollectionListScreen<Block> screen = new NewCollectionListScreen<>(
               "Select Blocks — " + bls.name, bls, bls.get(), allx, Names::get, b -> b.asItem().getDefaultStack(), null
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof ItemListSetting ils) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<Item> allx = new ArrayList<>();

            for (Item it : Registries.ITEM) {
               allx.add(it);
            }

            NewCollectionListScreen<Item> screen = new NewCollectionListScreen<>(
               "Select Items — " + ils.name, ils, ils.get(), allx, Names::get, Item::getDefaultStack, null
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof StatusEffectListSetting sels) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<StatusEffect> allx = new ArrayList<>();

            for (StatusEffect e : Registries.STATUS_EFFECT) {
               allx.add(e);
            }

            NewCollectionListScreen<StatusEffect> screen = new NewCollectionListScreen<>(
               "Select Effects — " + sels.name, sels, sels.get(), allx, Names::get, SettingGroupRenderer::statusEffectIcon, null
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof SoundEventListSetting sevls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<SoundEvent> allx = new ArrayList<>();

            for (SoundEvent e : Registries.SOUND_EVENT) {
               allx.add(e);
            }

            NewCollectionListScreen<SoundEvent> screen = new NewCollectionListScreen<>(
               "Select Sounds — " + sevls.name, sevls, sevls.get(), allx, e -> e.id().getPath(), null, null
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof ParticleTypeListSetting ptls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<ParticleType<?>> allx = new ArrayList<>();

            for (ParticleType<?> p : Registries.PARTICLE_TYPE) {
               allx.add(p);
            }

            NewCollectionListScreen<ParticleType<?>> screen = new NewCollectionListScreen<>(
               "Select Particles — " + ptls.name, ptls, ptls.get(), allx, Names::get, null, p -> !(p instanceof ParticleEffect)
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof PacketListSetting pls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<Class<? extends Packet<?>>> allx = new ArrayList<>();

            for (Class<? extends Packet<?>> c : PacketUtils.PACKETS) {
               allx.add(c);
            }

            NewCollectionListScreen<Class<? extends Packet<?>>> screen = new NewCollectionListScreen<>(
               "Select Packets — " + pls.name, pls, pls.get(), allx, PacketUtils::getName, null, c -> pls.filter != null && !pls.filter.test(c)
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof ScreenHandlerListSetting shls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<ScreenHandlerType<?>> allx = new ArrayList<>();

            for (ScreenHandlerType<?> t : Registries.SCREEN_HANDLER) {
               allx.add(t);
            }

            NewCollectionListScreen<ScreenHandlerType<?>> screen = new NewCollectionListScreen<>(
               "Select Screen Handlers — " + shls.name, shls, shls.get(), allx, t -> {
                  Identifier id = Registries.SCREEN_HANDLER.getId(t);
                  return id == null ? "(null)" : id.toString();
               }, null, null
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof StorageBlockListSetting sbls) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<BlockEntityType<?>> allx = new ArrayList<>();

            for (BlockEntityType<?> t : StorageBlockListSetting.REGISTRY) {
               allx.add(t);
            }

            NewCollectionListScreen<BlockEntityType<?>> screen = new NewCollectionListScreen<>(
               "Select Storage Blocks — " + sbls.name, sbls, sbls.get(), allx, SettingGroupRenderer::storageBlockName, t -> {
                  Item item = storageBlockItem(t);
                  return item == null ? null : item.getDefaultStack();
               }, null
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (setting instanceof BlockSetting bs2) {
         if (mouseY >= y && mouseY < y + rowH && button == 0) {
            List<Block> allx = new ArrayList<>();

            for (Block b : Registries.BLOCK) {
               allx.add(b);
            }

            NewSingleValueScreen<Block> screen = new NewSingleValueScreen<>(
               "Select Block — " + bs2.name, bs2, allx, Names::get, b -> b.asItem().getDefaultStack(), null, null
            );
            MinecraftClient.getInstance().setScreen(screen);
            return -1;
         } else {
            return rowH;
         }
      } else if (!(setting instanceof ItemSetting is2)) {
         if (setting instanceof ColorListSetting cls) {
            if (mouseY >= y && mouseY < y + rowH && button == 0) {
               MinecraftClient.getInstance().setScreen(new NewColorListScreen(cls));
               return -1;
            } else {
               return rowH;
            }
         } else if (setting instanceof StatusEffectAmplifierMapSetting seams) {
            if (mouseY >= y && mouseY < y + rowH && button == 0) {
               MinecraftClient.getInstance().setScreen(new NewAmplifierMapScreen(seams));
               return -1;
            } else {
               return rowH;
            }
         } else if (setting instanceof BlockDataSetting<?> bds) {
            if (mouseY >= y && mouseY < y + rowH && button == 0) {
               openBlockDataPicker(bds);
               return -1;
            } else {
               return rowH;
            }
         } else if (setting instanceof GenericSetting<?> gs) {
            if (mouseY >= y && mouseY < y + rowH && button == 0) {
               Object factory = gs.get();
               if (factory == null) {
                  return -1;
               } else {
                  CapturingGuiTheme capture = new CapturingGuiTheme();
                  Screen widgetScreen = null;

                  try {
                     widgetScreen = ((IScreenFactory)factory).createScreen(capture);
                     if (widgetScreen instanceof WidgetScreen ws) {
                        ws.initWidgets();
                     }
                  } catch (Throwable var49) {
                  }

                  if (capture.captured != null) {
                     MinecraftClient.getInstance()
                        .setScreen(new NewBlockEditScreen("Configure " + setting.name, capture.captured, MinecraftClient.getInstance().currentScreen));
                  } else if (widgetScreen != null) {
                     MinecraftClient.getInstance().setScreen(widgetScreen);
                  } else {
                     MinecraftClient.getInstance().setScreen(((IScreenFactory)factory).createScreen(GuiThemes.get()));
                  }

                  return -1;
               }
            } else {
               return rowH;
            }
         } else if (setting instanceof ActionSetting as) {
            if (mouseY >= y && mouseY < y + rowH && button == 0) {
               if (as.action != null) {
                  as.action.run();
               }

               return -1;
            } else {
               return rowH;
            }
         } else if (mouseY >= y && mouseY < y + rowH && button == 0 && fallback != null) {
            Screen s = fallback.get();
            if (s != null) {
               MinecraftClient.getInstance().setScreen(s);
            }

            return -1;
         } else {
            return rowH;
         }
      } else if (mouseY >= y && mouseY < y + rowH && button == 0) {
         List<Item> allx = new ArrayList<>();

         for (Item it : Registries.ITEM) {
            allx.add(it);
         }

         NewSingleValueScreen<Item> screen = new NewSingleValueScreen<>("Select Item — " + is2.name, is2, allx, Names::get, Item::getDefaultStack, null, null);
         MinecraftClient.getInstance().setScreen(screen);
         return -1;
      } else {
         return rowH;
      }
   }

   private static void openBlockDataPicker(BlockDataSetting<?> setting) {
      MinecraftClient.getInstance().setScreen(new NewBlockDataScreen<>(setting));
   }

   private static void cycleEnum(EnumSetting setting) {
      Object[] constants = setting.get().getClass().getEnumConstants();
      if (constants != null && constants.length != 0) {
         int idx = ((Enum)setting.get()).ordinal();
         setting.set((Enum)constants[(idx + 1) % constants.length]);
      }
   }

   private static void cycleProvidedString(ProvidedStringSetting setting, String[] opts, int direction) {
      if (opts != null && opts.length != 0) {
         String current = setting.get();
         int idx = -1;

         for (int i = 0; i < opts.length; i++) {
            if (opts[i].equals(current)) {
               idx = i;
               break;
            }
         }

         int next = idx < 0 ? 0 : ((idx + direction) % opts.length + opts.length) % opts.length;
         setting.set(opts[next]);
      }
   }

   private static void cycleFontFace(FontFaceSetting setting, List<FontFace> faces, int direction) {
      if (!faces.isEmpty()) {
         int current = -1;
         FontFace cur = setting.get();
         if (cur != null) {
            for (int i = 0; i < faces.size(); i++) {
               if (faces.get(i).toString().equals(cur.toString())) {
                  current = i;
                  break;
               }
            }
         }

         int next = current < 0 ? 0 : ((current + direction) % faces.size() + faces.size()) % faces.size();
         setting.set(faces.get(next));
      }
   }

   private static List<FontFace> buildFontFaces() {
      List<FontFace> faces = new ArrayList<>();

      for (FontFamily family : Fonts.FONT_FAMILIES) {
         for (FontInfo.Type type : FontInfo.Type.values()) {
            try {
               FontFace face = family.get(type);
               if (face != null) {
                  faces.add(face);
               }
            } catch (Throwable var8) {
            }
         }
      }

      return faces;
   }

   private static void updateIntSlider(IntSetting setting, int x, int width, int mouseX) {
      int ss = x + 3 + 1;
      int sw = width - 3 - 2;
      double progress = Math.max(0.0, Math.min(1.0, (double)(mouseX - ss) / sw));
      int minV = Math.max(setting.min, setting.sliderMin);
      int maxV = Math.min(setting.max, setting.sliderMax);
      int value = (int)Math.round(minV + (maxV - minV) * progress);
      value = Math.max(setting.min, Math.min(setting.max, value));
      if (!setting.get().equals(value)) {
         setting.set(value);
      }
   }

   private static void updateDoubleSlider(DoubleSetting setting, int x, int width, int mouseX) {
      int ss = x + 3 + 1;
      int sw = width - 3 - 2;
      double progress = Math.max(0.0, Math.min(1.0, (double)(mouseX - ss) / sw));
      double minV = Math.max(setting.min, setting.sliderMin);
      double maxV = Math.min(setting.max, setting.sliderMax);
      double value = minV + (maxV - minV) * progress;
      value = Math.max(setting.min, Math.min(setting.max, value));
      if (!setting.get().equals(value)) {
         setting.set(value);
      }
   }

   private static void updateColorSlider(ColorSetting setting, int component, int ss, int sw, int mouseX, int mouseY, int svTop, int svHeight) {
      SettingGroupRenderer.PickerState ps = ensurePicker(setting);
      float progressX = (float)Math.max(0.0, Math.min(1.0, (double)(mouseX - ss) / sw));
      switch (component) {
         case 0:
            ps.saturation = progressX;
            float progressY = svHeight <= 0 ? 0.0F : (float)Math.max(0.0, Math.min(1.0, (double)(mouseY - svTop) / svHeight));
            ps.brightness = 1.0F - progressY;
            break;
         case 1:
            ps.hue = progressX;
            break;
         case 2:
            SettingColor c = setting.get();
            c.a = (int)(progressX * 255.0F);
            setting.onChanged();
            return;
         default:
            return;
      }

      int rgb = Color.HSBtoRGB(ps.hue, ps.saturation, ps.brightness);
      SettingColor c = setting.get();
      c.r = rgb >> 16 & 0xFF;
      c.g = rgb >> 8 & 0xFF;
      c.b = rgb & 0xFF;
      setting.onChanged();
   }

   private static void copyColorToClipboard(ColorSetting setting) {
      SettingColor c = setting.get();
      String hex = String.format("#%02X%02X%02X%02X", c.r & 0xFF, c.g & 0xFF, c.b & 0xFF, c.a & 0xFF);
      MinecraftClient.getInstance().keyboard.setClipboard(hex);
   }

   private static void pasteColorFromClipboard(ColorSetting setting) {
      String raw = MinecraftClient.getInstance().keyboard.getClipboard();
      if (raw != null) {
         String s = raw.trim();
         if (!s.isEmpty()) {
            SettingColor c = setting.get();

            try {
               if (s.startsWith("#")) {
                  s = s.substring(1);
               }

               if (s.length() == 6) {
                  int val = Integer.parseUnsignedInt(s, 16);
                  c.r = val >> 16 & 0xFF;
                  c.g = val >> 8 & 0xFF;
                  c.b = val & 0xFF;
               } else if (s.length() == 8) {
                  long val = Long.parseUnsignedLong(s, 16);
                  c.r = (int)(val >> 24 & 255L);
                  c.g = (int)(val >> 16 & 255L);
                  c.b = (int)(val >> 8 & 255L);
                  c.a = (int)(val & 255L);
               } else {
                  if (!s.contains(",")) {
                     return;
                  }

                  String[] parts = s.split(",");
                  if (parts.length < 3) {
                     return;
                  }

                  c.r = clamp255(Integer.parseInt(parts[0].trim()));
                  c.g = clamp255(Integer.parseInt(parts[1].trim()));
                  c.b = clamp255(Integer.parseInt(parts[2].trim()));
                  if (parts.length >= 4) {
                     c.a = clamp255(Integer.parseInt(parts[3].trim()));
                  }
               }

               setting.onChanged();
               SettingGroupRenderer.PickerState ps = pickerStates.get(setting);
               if (ps != null) {
                  float[] hsb = Color.RGBtoHSB(c.r & 0xFF, c.g & 0xFF, c.b & 0xFF, null);
                  ps.hue = hsb[0];
                  ps.saturation = hsb[1];
                  ps.brightness = hsb[2];
               }
            } catch (Throwable var6) {
            }
         }
      }
   }

   private static int clamp255(int v) {
      return Math.max(0, Math.min(255, v));
   }

   private static SettingGroupRenderer.PickerState ensurePicker(ColorSetting setting) {
      SettingGroupRenderer.PickerState ps = pickerStates.get(setting);
      if (ps == null) {
         ps = new SettingGroupRenderer.PickerState();
         SettingColor c = setting.get();
         float[] hsb = Color.RGBtoHSB(c.r, c.g, c.b, null);
         ps.hue = hsb[0];
         ps.saturation = hsb[1];
         ps.brightness = hsb[2];
         pickerStates.put(setting, ps);
      }

      return ps;
   }

   private static void togglePicker(ColorSetting setting) {
      SettingGroupRenderer.PickerState ps = ensurePicker(setting);
      ps.open = !ps.open;
   }

   private static boolean isPickerOpen(ColorSetting setting) {
      SettingGroupRenderer.PickerState ps = pickerStates.get(setting);
      return ps != null && ps.open;
   }

   static int getPickerRows(ColorSetting setting) {
      return isPickerOpen(setting) ? 9 : 0;
   }

   public static ItemStack statusEffectIcon(StatusEffect effect) {
      ItemStack potion = Items.POTION.getDefaultStack();
      PotionContentsComponent contents = (PotionContentsComponent)potion.get(DataComponentTypes.POTION_CONTENTS);
      if (contents == null) {
         return potion;
      } else {
         potion.set(
            DataComponentTypes.POTION_CONTENTS,
            new PotionContentsComponent(contents.potion(), Optional.of(effect.getColor()), contents.customEffects(), Optional.empty())
         );
         return potion;
      }
   }

   public static String storageBlockName(BlockEntityType<?> type) {
      Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(type);
      if (id == null) {
         return "(unknown)";
      } else {
         String path = id.getPath();
         String[] parts = path.split("_");
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
               sb.append(' ');
            }

            if (!parts[i].isEmpty()) {
               sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
         }

         return sb.toString();
      }
   }

   public static Item storageBlockItem(BlockEntityType<?> type) {
      Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(type);
      if (id == null) {
         return Items.BARRIER;
      } else {
         Item item = (Item)Registries.ITEM.get(id);
         return item == Items.AIR ? Items.BARRIER : item;
      }
   }

   private static class DragCtx {
      int x;
      int width;
      int svTop;
      int svHeight;

      DragCtx(int x, int w) {
         this.x = x;
         this.width = w;
      }

      DragCtx(int x, int w, int svTop, int svHeight) {
         this.x = x;
         this.width = w;
         this.svTop = svTop;
         this.svHeight = svHeight;
      }
   }

   private static class PickerState {
      boolean open;
      float hue;
      float saturation;
      float brightness;
   }
}
