package revivedevelopment.reviveclient.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.MacWindowUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.renderer.GuiDebugRenderer;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WRoot;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WContainer;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.CursorStyle;
import revivedevelopment.reviveclient.utils.misc.input.Input;

public abstract class WidgetScreen extends Screen {
   private static final GuiRenderer RENDERER = new GuiRenderer();
   private static final GuiDebugRenderer DEBUG_RENDERER = new GuiDebugRenderer();
   public Runnable taskAfterRender;
   protected Runnable enterAction;
   public Screen parent;
   private final WContainer root;
   protected final GuiTheme theme;
   public boolean locked;
   public boolean lockedAllowClose;
   private boolean closed;
   private boolean onClose;
   private boolean debug;
   private double lastMouseX;
   private double lastMouseY;
   public double animProgress;
   private List<Runnable> onClosed;
   protected boolean firstInit = true;

   public WidgetScreen(GuiTheme theme, String title) {
      super(Text.literal(title));
      this.parent = ReviveClient.mc.currentScreen;
      this.root = new WidgetScreen.WFullScreenRoot();
      this.theme = theme;
      this.root.theme = theme;
      if (this.parent != null) {
         this.animProgress = 1.0;
      }
   }

   public <W extends WWidget> Cell<W> add(W widget) {
      return this.root.add(widget);
   }

   public void clear() {
      this.root.clear();
   }

   public void invalidate() {
      this.root.invalidate();
   }

   protected void init() {
      ReviveClient.EVENT_BUS.subscribe(this);
      this.closed = false;
      if (this.firstInit) {
         this.firstInit = false;
         this.initWidgets();
      }
   }

   public abstract void initWidgets();

   public void reload() {
      this.clear();
      this.initWidgets();
   }

   public void onClosed(Runnable action) {
      if (this.onClosed == null) {
         this.onClosed = new ArrayList<>(2);
      }

      this.onClosed.add(action);
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.locked) {
         return false;
      } else {
         double s = ReviveClient.mc.getWindow().getScaleFactor();
         double mouseX = click.x() * s;
         double mouseY = click.y() * s;
         return this.root.mouseClicked(mouseX, mouseY, click.button(), false);
      }
   }

   public boolean mouseReleased(Click click) {
      if (this.locked) {
         return false;
      } else {
         double s = ReviveClient.mc.getWindow().getScaleFactor();
         double mouseX = click.x() * s;
         double mouseY = click.y() * s;
         return this.root.mouseReleased(mouseX, mouseY, click.button());
      }
   }

   public void mouseMoved(double mouseX, double mouseY) {
      if (!this.locked) {
         double s = ReviveClient.mc.getWindow().getScaleFactor();
         mouseX *= s;
         mouseY *= s;
         this.root.mouseMoved(mouseX, mouseY, this.lastMouseX, this.lastMouseY);
         this.lastMouseX = mouseX;
         this.lastMouseY = mouseY;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.locked) {
         return false;
      } else {
         this.root.mouseScrolled(verticalAmount);
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   public boolean keyReleased(KeyInput input) {
      if (this.locked) {
         return false;
      } else {
         int keyCode = input.key();
         int modifiers = input.modifiers();
         if ((modifiers == 2 || modifiers == 8) && keyCode == 57) {
            this.debug = !this.debug;
            return true;
         } else if ((keyCode == 257 || keyCode == 335) && this.enterAction != null) {
            this.enterAction.run();
            return true;
         } else {
            return super.keyReleased(input);
         }
      }
   }

   public boolean keyPressed(KeyInput input) {
      if (this.locked) {
         return false;
      } else {
         int keyCode = input.key();
         int modifiers = input.modifiers();
         boolean shouldReturn = this.root.keyPressed(keyCode, modifiers) || super.keyPressed(input);
         if (shouldReturn) {
            return true;
         } else if (keyCode == 258) {
            AtomicReference<WTextBox> firstTextBox = new AtomicReference<>(null);
            AtomicBoolean done = new AtomicBoolean(false);
            AtomicBoolean foundFocused = new AtomicBoolean(false);
            this.loopWidgets(this.root, wWidget -> {
               if (!done.get() && wWidget instanceof WTextBox textBox) {
                  if (foundFocused.get()) {
                     textBox.setFocused(true);
                     textBox.setCursorMax();
                     done.set(true);
                  } else if (textBox.isFocused()) {
                     textBox.setFocused(false);
                     foundFocused.set(true);
                  }

                  if (firstTextBox.get() == null) {
                     firstTextBox.set(textBox);
                  }
               }
            });
            if (!done.get() && firstTextBox.get() != null) {
               firstTextBox.get().setFocused(true);
               firstTextBox.get().setCursorMax();
            }

            return true;
         } else {
            boolean control = MacWindowUtil.IS_MAC ? modifiers == 8 : modifiers == 2;
            return control && keyCode == 67 && this.toClipboard() || control && keyCode == 86 && this.fromClipboard();
         }
      }
   }

   public void keyRepeated(int key, int modifiers) {
      if (!this.locked) {
         this.root.keyRepeated(key, modifiers);
      }
   }

   public boolean charTyped(CharInput input) {
      return this.locked ? false : this.root.charTyped((char)input.codepoint());
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (!Utils.canUpdate()) {
         this.renderBackground(context, mouseX, mouseY, deltaTicks);
      }

      double s = ReviveClient.mc.getWindow().getScaleFactor();
      mouseX = (int)(mouseX * s);
      mouseY = (int)(mouseY * s);
      this.animProgress += deltaTicks / 20.0F * 14.0F;
      this.animProgress = MathHelper.clamp(this.animProgress, 0.0, 1.0);
      GuiKeyEvents.canUseKeys = true;
      Utils.unscaledProjection();
      this.onRenderBefore(context, deltaTicks);
      RENDERER.theme = this.theme;
      this.theme.beforeRender();
      RENDERER.begin(context);
      RENDERER.setAlpha(this.animProgress);
      this.root.render(RENDERER, mouseX, mouseY, deltaTicks / 20.0F);
      RENDERER.setAlpha(1.0);
      RENDERER.end();
      boolean tooltip = RENDERER.renderTooltip(context, mouseX, mouseY, deltaTicks / 20.0F);
      if (this.debug) {
         DEBUG_RENDERER.render(this.root);
         if (tooltip) {
            DEBUG_RENDERER.render(RENDERER.tooltipWidget);
         }
      }

      Utils.scaledProjection();
      this.runAfterRenderTasks();
   }

   protected void runAfterRenderTasks() {
      if (this.taskAfterRender != null) {
         this.taskAfterRender.run();
         this.taskAfterRender = null;
      }
   }

   protected void onRenderBefore(DrawContext drawContext, float delta) {
   }

   public void resize(int width, int height) {
      super.resize(width, height);
      this.root.invalidate();
   }

   public void close() {
      if (!this.locked || this.lockedAllowClose) {
         boolean preOnClose = this.onClose;
         this.onClose = true;
         this.removed();
         this.onClose = preOnClose;
      }
   }

   public void removed() {
      if (!this.closed || this.lockedAllowClose) {
         this.closed = true;
         this.onClosed();
         Input.setCursorStyle(CursorStyle.Default);
         this.loopWidgets(this.root, widget -> {
            if (widget instanceof WTextBox textBox && textBox.isFocused()) {
               textBox.setFocused(false);
            }
         });
         ReviveClient.EVENT_BUS.unsubscribe(this);
         GuiKeyEvents.canUseKeys = true;
         if (this.onClosed != null) {
            for (Runnable action : this.onClosed) {
               action.run();
            }
         }

         if (this.onClose) {
            this.taskAfterRender = () -> {
               this.locked = true;
               ReviveClient.mc.setScreen(this.parent);
            };
         }
      }
   }

   private void loopWidgets(WWidget widget, Consumer<WWidget> action) {
      action.accept(widget);
      if (widget instanceof WContainer) {
         for (Cell<?> cell : ((WContainer)widget).cells) {
            this.loopWidgets(cell.widget(), action);
         }
      }
   }

   protected void onClosed() {
   }

   public boolean toClipboard() {
      return false;
   }

   public boolean fromClipboard() {
      return false;
   }

   public boolean shouldCloseOnEsc() {
      return !this.locked || this.lockedAllowClose;
   }

   public boolean shouldPause() {
      return false;
   }

   private static class WFullScreenRoot extends WContainer implements WRoot {
      private boolean valid;

      @Override
      public void invalidate() {
         this.valid = false;
      }

      @Override
      protected void onCalculateSize() {
         this.width = Utils.getWindowWidth();
         this.height = Utils.getWindowHeight();
      }

      @Override
      protected void onCalculateWidgetPositions() {
         for (Cell<?> cell : this.cells) {
            cell.x = 0.0;
            cell.y = 0.0;
            cell.width = this.width;
            cell.height = this.height;
            cell.alignWidget();
         }
      }

      @Override
      public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         if (!this.valid) {
            this.calculateSize();
            this.calculateWidgetPositions();
            this.valid = true;
            this.mouseMoved(ReviveClient.mc.mouse.getX(), ReviveClient.mc.mouse.getY(), ReviveClient.mc.mouse.getX(), ReviveClient.mc.mouse.getY());
         }

         return super.render(renderer, mouseX, mouseY, delta);
      }
   }
}
