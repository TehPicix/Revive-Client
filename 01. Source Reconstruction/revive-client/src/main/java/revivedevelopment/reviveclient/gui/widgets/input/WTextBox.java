package revivedevelopment.reviveclient.gui.widgets.input;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.client.util.MacWindowUtil;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.SystemUtils;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.GuiKeyEvents;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.utils.CharFilter;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WContainer;
import revivedevelopment.reviveclient.utils.render.color.Color;

public abstract class WTextBox extends WWidget {
   private static final WTextBox.Renderer DEFAULT_RENDERER = (renderer, x, y, text, color) -> renderer.text(text, x, y, color, false);
   public Runnable action;
   public Runnable actionOnUnfocused;
   protected String text;
   protected String placeholder;
   protected CharFilter filter;
   protected final WTextBox.Renderer renderer;
   protected boolean focused;
   protected DoubleList textWidths = new DoubleArrayList();
   protected int cursor;
   protected double textStart;
   protected boolean selecting;
   protected int selectionStart;
   protected int selectionEnd;
   private int preSelectionCursor;
   private List<String> completions;
   private int completionsStart;
   private WContainer completionsW;

   public WTextBox(String text, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
      this(text, null, filter, renderer);
   }

   public WTextBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
      this.text = text;
      this.placeholder = placeholder;
      this.filter = filter;

      try {
         this.renderer = renderer != null ? renderer.getDeclaredConstructor().newInstance() : DEFAULT_RENDERER;
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException var6) {
         throw new RuntimeException(var6);
      }
   }

   protected abstract WContainer createCompletionsRootWidget();

   protected abstract <T extends WWidget & WTextBox.ICompletionItem> T createCompletionsValueWidth(String var1, boolean var2);

   @Override
   protected void onCalculateSize() {
      double pad = this.pad();
      double s = this.theme.textHeight();
      this.width = pad + s + pad;
      this.height = pad + s + pad;
      this.calculateTextWidths();
      if (this.completionsW != null) {
         this.completionsW.calculateSize();
      }
   }

   @Override
   public void calculateWidgetPositions() {
      super.calculateWidgetPositions();
      if (this.completionsW != null) {
         this.completionsW.x = this.x;
         this.completionsW.y = this.y + this.height;
         this.completionsW.calculateWidgetPositions();
      }
   }

   @Override
   public void move(double deltaX, double deltaY) {
      super.move(deltaX, deltaY);
      if (this.completionsW != null) {
         this.completionsW.move(deltaX, deltaY);
      }
   }

   protected double maxTextWidth() {
      return this.width - this.pad() * 2.0;
   }

   @Override
   public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
      if (this.mouseOver && !used) {
         if (button == 1) {
            if (!this.text.isEmpty()) {
               this.text = "";
               this.cursor = 0;
               this.selectionStart = 0;
               this.selectionEnd = 0;
               this.runAction();
            }
         } else if (button == 0) {
            this.selecting = true;
            double overflowWidth = this.getOverflowWidthForRender();
            double relativeMouseX = mouseX - this.x + overflowWidth;
            double pad = this.pad();
            double smallestDifference = Double.MAX_VALUE;
            this.cursor = this.text.length();

            for (int i = 0; i < this.textWidths.size(); i++) {
               double difference = Math.abs(this.textWidths.getDouble(i) + pad - relativeMouseX);
               if (difference < smallestDifference) {
                  smallestDifference = difference;
                  this.cursor = i;
               }
            }

            this.preSelectionCursor = this.cursor;
            this.resetSelection();
            this.cursorChanged();
         }

         this.setFocused(true);
         return true;
      } else {
         if (this.focused) {
            this.setFocused(false);
         }

         return false;
      }
   }

   @Override
   public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
      if (this.selecting) {
         double overflowWidth = this.getOverflowWidthForRender();
         double relativeMouseX = mouseX - this.x + overflowWidth;
         double pad = this.pad();
         double smallestDifference = Double.MAX_VALUE;

         for (int i = 0; i < this.textWidths.size(); i++) {
            double difference = Math.abs(this.textWidths.getDouble(i) + pad - relativeMouseX);
            if (difference < smallestDifference) {
               smallestDifference = difference;
               if (i < this.preSelectionCursor) {
                  this.selectionStart = i;
                  this.cursor = i;
               } else if (i > this.preSelectionCursor) {
                  this.selectionEnd = i;
                  this.cursor = i;
               } else {
                  this.cursor = this.preSelectionCursor;
                  this.resetSelection();
               }
            }
         }
      }
   }

   @Override
   public boolean onMouseReleased(double mouseX, double mouseY, int button) {
      this.selecting = false;
      if (this.selectionStart < this.preSelectionCursor && this.preSelectionCursor == this.selectionEnd) {
         this.cursor = this.selectionStart;
      } else if (this.selectionEnd > this.preSelectionCursor && this.preSelectionCursor == this.selectionStart) {
         this.cursor = this.selectionEnd;
      }

      return false;
   }

   @Override
   public boolean onKeyPressed(int key, int mods) {
      if (!this.focused) {
         return false;
      } else {
         boolean control = MacWindowUtil.IS_MAC ? mods == 8 : mods == 2;
         if (control && key == 67) {
            if (this.cursor != this.selectionStart || this.cursor != this.selectionEnd) {
               ReviveClient.mc.keyboard.setClipboard(this.text.substring(this.selectionStart, this.selectionEnd));
            }

            return true;
         } else if (control && key == 88) {
            if (this.cursor != this.selectionStart || this.cursor != this.selectionEnd) {
               ReviveClient.mc.keyboard.setClipboard(this.text.substring(this.selectionStart, this.selectionEnd));
               this.clearSelection();
            }

            return true;
         } else {
            if (control && key == 65) {
               this.cursor = this.text.length();
               this.selectionStart = 0;
               this.selectionEnd = this.cursor;
            } else if (mods == ((MacWindowUtil.IS_MAC ? 8 : 2) | 1) && key == 65) {
               this.resetSelection();
            } else {
               if (key == 257 || key == 335) {
                  this.setFocused(false);
                  if (this.actionOnUnfocused != null) {
                     this.actionOnUnfocused.run();
                  }

                  return true;
               }

               if (key == 258 && this.completionsW != null) {
                  String completion = ((WTextBox.ICompletionItem)this.completionsW.cells.get(this.getSelectedCompletion()).widget()).getCompletion();
                  StringBuilder sb = new StringBuilder(this.text.length() + completion.length() + 1);
                  String a = this.text.substring(0, this.cursor);
                  sb.append(a);

                  for (int i = 0; i < completion.length() - 1; i++) {
                     if (a.endsWith(completion.substring(0, completion.length() - i - 1))) {
                        completion = completion.substring(completion.length() - i - 1);
                        break;
                     }
                  }

                  sb.append(completion);
                  if (completion.endsWith("(")) {
                     sb.append(')');
                  }

                  sb.append(this.text, this.cursor, this.text.length());
                  this.text = sb.toString();
                  this.cursor = this.cursor + completion.length();
                  this.resetSelection();
                  this.runAction();
                  return true;
               }
            }

            return this.onKeyRepeated(key, mods);
         }
      }
   }

   @Override
   public boolean onKeyRepeated(int key, int mods) {
      if (!this.focused) {
         return false;
      } else {
         boolean control = MacWindowUtil.IS_MAC ? mods == 8 : mods == 2;
         boolean shift = mods == 1;
         boolean controlShift = mods == ((SystemUtils.IS_OS_WINDOWS ? 4 : (MacWindowUtil.IS_MAC ? 8 : 2)) | 1);
         boolean altShift = mods == ((SystemUtils.IS_OS_WINDOWS ? 2 : 4) | 1);
         if (control && key == 86) {
            this.clearSelection();
            String preText = this.text;
            String clipboard = ReviveClient.mc.keyboard.getClipboard();
            int addedChars = 0;
            StringBuilder sb = new StringBuilder(this.text.length() + clipboard.length());
            sb.append(this.text);

            for (int i = 0; i < clipboard.length(); i++) {
               char c = clipboard.charAt(i);
               if (this.filter.filter(sb.toString(), c)) {
                  sb.insert(this.cursor + addedChars, c);
                  addedChars++;
               }
            }

            this.text = sb.toString();
            this.cursor += addedChars;
            this.resetSelection();
            if (!this.text.equals(preText)) {
               this.runAction();
            }

            return true;
         } else if (key == 259) {
            if (this.cursor > 0 && this.cursor == this.selectionStart && this.cursor == this.selectionEnd) {
               String preText = this.text;
               int count = mods == (SystemUtils.IS_OS_WINDOWS ? 4 : (MacWindowUtil.IS_MAC ? 8 : 2))
                  ? this.cursor
                  : (mods == (SystemUtils.IS_OS_WINDOWS ? 2 : 4) ? this.countToNextSpace(true) : 1);
               this.text = this.text.substring(0, this.cursor - count) + this.text.substring(this.cursor);
               this.cursor -= count;
               this.resetSelection();
               if (!this.text.equals(preText)) {
                  this.runAction();
               }
            } else if (this.cursor != this.selectionStart || this.cursor != this.selectionEnd) {
               this.clearSelection();
            }

            return true;
         } else if (key == 261) {
            if (this.cursor == this.selectionStart && this.cursor == this.selectionEnd) {
               if (this.cursor < this.text.length()) {
                  String preText = this.text;
                  int count = mods == (SystemUtils.IS_OS_WINDOWS ? 4 : (MacWindowUtil.IS_MAC ? 8 : 2))
                     ? this.text.length() - this.cursor
                     : (mods == (SystemUtils.IS_OS_WINDOWS ? 2 : 4) ? this.countToNextSpace(false) : 1);
                  this.text = this.text.substring(0, this.cursor) + this.text.substring(this.cursor + count);
                  if (!this.text.equals(preText)) {
                     this.runAction();
                  }
               }
            } else {
               this.clearSelection();
            }

            return true;
         } else if (key == 263) {
            if (this.cursor > 0) {
               if (mods == (SystemUtils.IS_OS_WINDOWS ? 2 : 4)) {
                  this.cursor = this.cursor - this.countToNextSpace(true);
                  this.resetSelection();
               } else if (mods == (SystemUtils.IS_OS_WINDOWS ? 4 : (MacWindowUtil.IS_MAC ? 8 : 2))) {
                  this.cursor = 0;
                  this.resetSelection();
               } else if (altShift) {
                  if (this.cursor == this.selectionEnd && this.cursor != this.selectionStart) {
                     this.cursor = this.cursor - this.countToNextSpace(true);
                     this.selectionEnd = this.cursor;
                  } else {
                     this.cursor = this.cursor - this.countToNextSpace(true);
                     this.selectionStart = this.cursor;
                  }
               } else if (controlShift) {
                  if (this.cursor == this.selectionEnd && this.cursor != this.selectionStart) {
                     this.selectionEnd = this.selectionStart;
                  }

                  this.selectionStart = 0;
                  this.cursor = 0;
               } else if (shift) {
                  if (this.cursor == this.selectionEnd && this.cursor != this.selectionStart) {
                     this.selectionEnd = this.cursor - 1;
                  } else {
                     this.selectionStart = this.cursor - 1;
                  }

                  this.cursor--;
               } else {
                  if (this.cursor == this.selectionEnd && this.cursor != this.selectionStart) {
                     this.cursor = this.selectionStart;
                  } else {
                     this.cursor--;
                  }

                  this.resetSelection();
               }

               this.cursorChanged();
            } else if (this.selectionStart != this.selectionEnd && this.selectionStart == 0 && mods == 0) {
               this.cursor = 0;
               this.resetSelection();
               this.cursorChanged();
            }

            return true;
         } else if (key != 262) {
            if (key == 264 && this.completionsW != null) {
               int currentI = this.getSelectedCompletion();
               if (currentI == Math.min(5, this.completions.size() - 1)) {
                  if (this.completionsStart + 6 < this.completions.size()) {
                     this.completionsStart++;
                     this.createCompletions(this.completionsStart + currentI);
                  }
               } else {
                  ((WTextBox.ICompletionItem)this.completionsW.cells.get(currentI).widget()).setSelected(false);
                  ((WTextBox.ICompletionItem)this.completionsW.cells.get(currentI + 1).widget()).setSelected(true);
               }

               return true;
            } else if (key == 265 && this.completionsW != null) {
               int currentI = this.getSelectedCompletion();
               if (currentI == 0) {
                  if (this.completionsStart > 0) {
                     this.completionsStart--;
                     this.createCompletions(this.completionsStart + currentI);
                  }
               } else {
                  ((WTextBox.ICompletionItem)this.completionsW.cells.get(currentI).widget()).setSelected(false);
                  ((WTextBox.ICompletionItem)this.completionsW.cells.get(currentI - 1).widget()).setSelected(true);
               }

               return true;
            } else {
               return false;
            }
         } else {
            if (this.cursor < this.text.length()) {
               if (mods == (SystemUtils.IS_OS_WINDOWS ? 2 : 4)) {
                  this.cursor = this.cursor + this.countToNextSpace(false);
                  this.resetSelection();
               } else if (mods == (SystemUtils.IS_OS_WINDOWS ? 4 : (MacWindowUtil.IS_MAC ? 8 : 2))) {
                  this.cursor = this.text.length();
                  this.resetSelection();
               } else if (altShift) {
                  if (this.cursor == this.selectionStart && this.cursor != this.selectionEnd) {
                     this.cursor = this.cursor + this.countToNextSpace(false);
                     this.selectionStart = this.cursor;
                  } else {
                     this.cursor = this.cursor + this.countToNextSpace(false);
                     this.selectionEnd = this.cursor;
                  }
               } else if (controlShift) {
                  if (this.cursor == this.selectionStart && this.cursor != this.selectionEnd) {
                     this.selectionStart = this.selectionEnd;
                  }

                  this.cursor = this.text.length();
                  this.selectionEnd = this.cursor;
               } else if (shift) {
                  if (this.cursor == this.selectionStart && this.cursor != this.selectionEnd) {
                     this.selectionStart = this.cursor + 1;
                  } else {
                     this.selectionEnd = this.cursor + 1;
                  }

                  this.cursor++;
               } else {
                  if (this.cursor == this.selectionStart && this.cursor != this.selectionEnd) {
                     this.cursor = this.selectionEnd;
                  } else {
                     this.cursor++;
                  }

                  this.resetSelection();
               }

               this.cursorChanged();
            } else if (this.selectionStart != this.selectionEnd && this.selectionEnd == this.text.length() && mods == 0) {
               this.cursor = this.text.length();
               this.resetSelection();
               this.cursorChanged();
            }

            return true;
         }
      }
   }

   private int getSelectedCompletion() {
      for (int i = 0; i < this.completionsW.cells.size(); i++) {
         WTextBox.ICompletionItem item = (WTextBox.ICompletionItem)this.completionsW.cells.get(i).widget();
         if (item.isSelected()) {
            return i;
         }
      }

      return -1;
   }

   @Override
   public boolean onCharTyped(char c) {
      if (!this.focused) {
         return false;
      } else if (this.filter.filter(this.text, c)) {
         this.clearSelection();
         this.text = this.text.substring(0, this.cursor) + c + this.text.substring(this.cursor);
         this.cursor++;
         this.resetSelection();
         this.runAction();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (this.isFocused()) {
         GuiKeyEvents.canUseKeys = false;
      }

      if (this.completionsW != null && this.focused) {
         renderer.absolutePost(() -> {
            renderer.beginRender();
            this.completionsW.render(renderer, mouseX, mouseY, delta);
            renderer.endRender();
         });
      }

      return super.render(renderer, mouseX, mouseY, delta);
   }

   private void clearSelection() {
      if (this.selectionStart != this.selectionEnd) {
         String preText = this.text;
         this.text = this.text.substring(0, this.selectionStart) + this.text.substring(this.selectionEnd);
         this.cursor = this.selectionStart;
         this.selectionEnd = this.cursor;
         if (!this.text.equals(preText)) {
            this.runAction();
         }
      }
   }

   private void resetSelection() {
      this.selectionStart = this.cursor;
      this.selectionEnd = this.cursor;
   }

   private int countToNextSpace(boolean toLeft) {
      int count = 0;
      boolean hadNonSpace = false;
      int i = this.cursor;

      while (toLeft ? i >= 0 : i < this.text.length()) {
         int j = i;
         if (toLeft) {
            j = i - 1;
         }

         if (j < this.text.length()) {
            if (j < 0 || hadNonSpace && Character.isWhitespace(this.text.charAt(j))) {
               break;
            }

            if (!Character.isWhitespace(this.text.charAt(j))) {
               hadNonSpace = true;
            }

            count++;
         }

         i += toLeft ? -1 : 1;
      }

      return count;
   }

   private void calculateTextWidths() {
      this.textWidths.clear();

      for (int i = 0; i <= this.text.length(); i++) {
         this.textWidths.add(this.theme.textWidth(this.text, i, false));
      }
   }

   private void runAction() {
      this.calculateTextWidths();
      this.cursorChanged();
      if (this.action != null) {
         this.action.run();
      }
   }

   private double textWidth() {
      return this.textWidths.isEmpty() ? 0.0 : this.textWidths.getDouble(this.textWidths.size() - 1);
   }

   private void cursorChanged() {
      double cursor = this.getCursorTextWidth(-2);
      if (cursor < this.textStart) {
         this.textStart = this.textStart - (this.textStart - cursor);
      }

      cursor = this.getCursorTextWidth(2);
      if (cursor > this.textStart + this.maxTextWidth()) {
         this.textStart = this.textStart + (cursor - (this.textStart + this.maxTextWidth()));
      }

      this.textStart = MathHelper.clamp(this.textStart, 0.0, Math.max(this.textWidth() - this.maxTextWidth(), 0.0));
      this.onCursorChanged();
      this.completions = this.renderer.getCompletions(this.text, this.cursor);
      this.completionsStart = 0;
      this.completionsW = null;
      if (this.completions != null && !this.completions.isEmpty()) {
         this.createCompletions(0);
      }
   }

   protected void onCursorChanged() {
   }

   private void createCompletions(int selected) {
      this.completionsW = this.createCompletionsRootWidget();
      this.completionsW.theme = this.theme;
      int max = Math.min(this.completions.size(), this.completionsStart + 6);

      for (int i = this.completionsStart; i < max; i++) {
         WWidget widget = this.createCompletionsValueWidth(this.completions.get(i), i == selected);
         widget.theme = this.theme;
         Cell<?> cell = this.completionsW.add(widget).expandX().padHorizontal(4.0);
         if (i == max - 1) {
            cell.padBottom(4.0);
         }
      }

      this.completionsW.calculateSize();
      this.completionsW.x = Math.min(
         Math.max(this.x - this.pad() * 2.0 + this.getTextWidth(this.cursor) - this.getOverflowWidthForRender(), this.x),
         this.x + this.width - this.completionsW.width
      );
      this.completionsW.y = this.y + this.height;
      this.completionsW.calculateWidgetPositions();
   }

   protected double getTextWidth(int pos) {
      if (this.textWidths.isEmpty()) {
         return 0.0;
      } else {
         if (pos < 0) {
            pos = 0;
         } else if (pos >= this.textWidths.size()) {
            pos = this.textWidths.size() - 1;
         }

         return this.textWidths.getDouble(pos);
      }
   }

   protected double getCursorTextWidth(int offset) {
      return this.getTextWidth(this.cursor + offset);
   }

   protected double getOverflowWidthForRender() {
      return this.textStart;
   }

   public String get() {
      return this.text;
   }

   public void set(String text) {
      this.text = text;
      this.cursor = MathHelper.clamp(this.cursor, 0, text.length());
      this.selectionStart = this.cursor;
      this.selectionEnd = this.cursor;
      this.calculateTextWidths();
      this.cursorChanged();
   }

   public boolean isFocused() {
      return this.focused;
   }

   public void setFocused(boolean focused) {
      if (this.focused && !focused && this.actionOnUnfocused != null) {
         this.actionOnUnfocused.run();
      }

      boolean wasJustFocused = focused && !this.focused;
      this.focused = focused;
      this.resetSelection();
      if (wasJustFocused) {
         this.onCursorChanged();
      }
   }

   public void setCursorMax() {
      this.cursor = this.text.length();
   }

   public interface ICompletionItem {
      boolean isSelected();

      void setSelected(boolean var1);

      String getCompletion();
   }

   public interface Renderer {
      void render(GuiRenderer var1, double var2, double var4, String var6, Color var7);

      default List<String> getCompletions(String text, int position) {
         return null;
      }
   }
}
