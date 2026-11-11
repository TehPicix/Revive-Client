package revivedevelopment.reviveclient.systems.hud.screens;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewAddHudElementScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewHudEditorScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewHudElementPresetsScreen;
import revivedevelopment.reviveclient.gui.newgui.screens.NewHudElementScreen;
import revivedevelopment.reviveclient.renderer.Renderer2D;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.other.Snapper;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class HudEditorScreen extends WidgetScreen implements Snapper.Container {
   private static final Color SPLIT_LINES_COLOR = new Color(255, 255, 255, 75);
   private static final Color INACTIVE_BG_COLOR = new Color(200, 25, 25, 50);
   private static final Color INACTIVE_OL_COLOR = new Color(200, 25, 25, 200);
   private static final Color HOVER_BG_COLOR = new Color(200, 200, 200, 50);
   private static final Color HOVER_OL_COLOR = new Color(200, 200, 200, 200);
   private static final Color SELECTION_BG_COLOR = new Color(225, 225, 225, 25);
   private static final Color SELECTION_OL_COLOR = new Color(225, 225, 225, 100);
   private final Hud hud;
   private final Snapper snapper;
   private Snapper.Element selectionSnapBox;
   private int lastMouseX;
   private int lastMouseY;
   private boolean pressed;
   private int clickX;
   private int clickY;
   private final List<HudElement> selection = new ArrayList<>();
   private boolean moved;
   private boolean dragging;
   private HudElement addedHoveredToSelectionWhenClickedElement;
   private double splitLinesAnimation;

   public HudEditorScreen(GuiTheme theme) {
      super(theme, "Hud Editor");
      this.hud = Hud.get();
      this.snapper = new Snapper(this);
   }

   @Override
   public void initWidgets() {
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      double s = ReviveClient.mc.getWindow().getScaleFactor();
      mouseX *= s;
      mouseY *= s;
      if (button == 0) {
         this.pressed = true;
         this.selectionSnapBox = null;
         HudElement hovered = this.getHovered((int)mouseX, (int)mouseY);
         this.dragging = hovered != null;
         if (this.dragging) {
            if (!this.selection.contains(hovered)) {
               this.selection.clear();
               this.selection.add(hovered);
               this.addedHoveredToSelectionWhenClickedElement = hovered;
            }
         } else {
            this.selection.clear();
         }

         this.clickX = (int)mouseX;
         this.clickY = (int)mouseY;
      }

      return false;
   }

   @Override
   public void mouseMoved(double mouseX, double mouseY) {
      double s = ReviveClient.mc.getWindow().getScaleFactor();
      mouseX *= s;
      mouseY *= s;
      if (this.dragging && !this.selection.isEmpty()) {
         if (this.selectionSnapBox == null) {
            this.selectionSnapBox = new HudEditorScreen.SelectionBox();
         }

         this.snapper.move(this.selectionSnapBox, (int)mouseX - this.lastMouseX, (int)mouseY - this.lastMouseY);
      }

      if (this.pressed) {
         this.moved = true;
      }

      this.lastMouseX = (int)mouseX;
      this.lastMouseY = (int)mouseY;
   }

   @Override
   public boolean mouseReleased(Click click) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      double s = ReviveClient.mc.getWindow().getScaleFactor();
      mouseX *= s;
      mouseY *= s;
      if (button == 0) {
         this.pressed = false;
      }

      if (this.addedHoveredToSelectionWhenClickedElement != null) {
         this.selection.remove(this.addedHoveredToSelectionWhenClickedElement);
         this.addedHoveredToSelectionWhenClickedElement = null;
      }

      if (this.moved) {
         if (button == 0 && !this.dragging) {
            this.fillSelection((int)mouseX, (int)mouseY);
         }
      } else if (button == 0) {
         HudElement hovered = this.getHovered((int)mouseX, (int)mouseY);
         if (hovered != null) {
            hovered.toggle();
         }
      } else if (button == 1) {
         HudElement hovered = this.getHovered((int)mouseX, (int)mouseY);
         if (hovered != null) {
            ReviveClient.mc.setScreen(new HudElementScreen(this.theme, hovered));
         } else {
            ReviveClient.mc.setScreen(new AddHudElementScreen(this.theme, this.lastMouseX, this.lastMouseY));
         }
      }

      if (button == 0) {
         this.snapper.unsnap();
         this.moved = this.dragging = false;
      }

      return false;
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int modifiers = input.modifiers();
      if (!this.pressed) {
         if (keyCode == 257 || keyCode == 335) {
            HudElement hovered = this.getHovered(this.lastMouseX, this.lastMouseY);
            if (hovered != null) {
               hovered.toggle();
            }
         } else if (keyCode == 261) {
            HudElement hovered = this.getHovered(this.lastMouseX, this.lastMouseY);
            if (hovered != null) {
               hovered.remove();
            } else {
               for (HudElement element : this.selection) {
                  element.remove();
               }

               this.selection.clear();
            }
         }
      }

      return super.keyPressed(input);
   }

   private void fillSelection(int mouseX, int mouseY) {
      int x1 = Math.min(this.clickX, mouseX);
      int x2 = Math.max(this.clickX, mouseX);
      int y1 = Math.min(this.clickY, mouseY);
      int y2 = Math.max(this.clickY, mouseY);

      for (HudElement e : this.hud) {
         if (e.getX() <= x2 && e.getX2() >= x1 && e.getY() <= y2 && e.getY2() >= y1) {
            this.selection.add(e);
         }
      }
   }

   @Override
   public Iterable<Snapper.Element> getElements() {
      return () -> new Iterator<Snapper.Element>() {
         private final Iterator<HudElement> it = HudEditorScreen.this.hud.iterator();

         @Override
         public boolean hasNext() {
            return this.it.hasNext();
         }

         public Snapper.Element next() {
            return this.it.next();
         }
      };
   }

   @Override
   public boolean shouldNotSnapTo(Snapper.Element element) {
      return this.selection.contains((HudElement)element);
   }

   @Override
   public int getSnappingRange() {
      return this.hud.getSnappingRange();
   }

   private void onRender(int mouseX, int mouseY) {
      for (HudElement element : this.hud) {
         if (!element.isActive()) {
            this.renderElement(element, INACTIVE_BG_COLOR, INACTIVE_OL_COLOR);
         }
      }

      if (this.pressed && !this.dragging) {
         this.fillSelection(mouseX, mouseY);
      }

      for (HudElement elementx : this.selection) {
         this.renderElement(elementx, HOVER_BG_COLOR, HOVER_OL_COLOR);
      }

      if (this.pressed && !this.dragging) {
         this.selection.clear();
      }

      if (this.pressed && !this.dragging) {
         int x1 = Math.min(this.clickX, mouseX);
         int x2 = Math.max(this.clickX, mouseX);
         int y1 = Math.min(this.clickY, mouseY);
         int y2 = Math.max(this.clickY, mouseY);
         this.renderQuad(x1, y1, x2 - x1, y2 - y1, SELECTION_BG_COLOR, SELECTION_OL_COLOR);
      }

      if (!this.pressed) {
         HudElement hovered = this.getHovered(mouseX, mouseY);
         if (hovered != null) {
            this.renderElement(hovered, HOVER_BG_COLOR, HOVER_OL_COLOR);
         }
      }
   }

   private HudElement getHovered(int mouseX, int mouseY) {
      for (HudElement element : this.hud) {
         if (mouseX >= element.x && mouseX <= element.x + element.getWidth() && mouseY >= element.y && mouseY <= element.y + element.getHeight()) {
            return element;
         }
      }

      return null;
   }

   private void renderQuad(double x, double y, double w, double h, Color bgColor, Color olColor) {
      Renderer2D.color().quad(x + 1.0, y + 1.0, w - 2.0, h - 2.0, bgColor);
      Renderer2D.color().quad(x, y, w, 1.0, olColor);
      Renderer2D.color().quad(x, y + h - 1.0, w, 1.0, olColor);
      Renderer2D.color().quad(x, y + 1.0, 1.0, h - 2.0, olColor);
      Renderer2D.color().quad(x + w - 1.0, y + 1.0, 1.0, h - 2.0, olColor);
   }

   private void renderElement(HudElement element, Color bgColor, Color olColor) {
      this.renderQuad(element.x, element.y, element.getWidth(), element.getHeight(), bgColor, olColor);
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (!Utils.canUpdate()) {
         this.renderBackground(context, mouseX, mouseY, deltaTicks);
      }

      double s = ReviveClient.mc.getWindow().getScaleFactor();
      mouseX = (int)(mouseX * s);
      mouseY = (int)(mouseY * s);
      Utils.unscaledProjection();
      boolean renderSplitLines = this.pressed && !this.selection.isEmpty() && this.moved;
      if (renderSplitLines || this.splitLinesAnimation > 0.0) {
         this.renderSplitLines(renderSplitLines, deltaTicks / 20.0F);
      }

      renderElements(context);
      Renderer2D.color().begin();
      this.onRender(mouseX, mouseY);
      Renderer2D.color().render();
      Utils.scaledProjection();
      this.runAfterRenderTasks();
   }

   public static void renderElements(DrawContext drawContext) {
      Hud hud = Hud.get();
      HudRenderer.INSTANCE.deferText = true;

      try {
         HudRenderer.INSTANCE.begin(drawContext);

         for (HudElement element : hud) {
            element.updatePos();
            element.render(HudRenderer.INSTANCE);
         }

         HudRenderer.INSTANCE.end();
      } finally {
         HudRenderer.INSTANCE.deferText = false;
      }
   }

   private void renderSplitLines(boolean increment, double delta) {
      if (increment) {
         this.splitLinesAnimation += delta * 6.0;
      } else {
         this.splitLinesAnimation -= delta * 6.0;
      }

      this.splitLinesAnimation = MathHelper.clamp(this.splitLinesAnimation, 0.0, 1.0);
      Renderer2D renderer = Renderer2D.color();
      renderer.begin();
      double w = Utils.getWindowWidth();
      double h = Utils.getWindowHeight();
      double w3 = w / 3.0;
      double h3 = h / 3.0;
      int prevA = SPLIT_LINES_COLOR.a;
      SPLIT_LINES_COLOR.a = (int)(SPLIT_LINES_COLOR.a * this.splitLinesAnimation);
      this.renderSplitLine(renderer, w3, 0.0, w3, h);
      this.renderSplitLine(renderer, w3 * 2.0, 0.0, w3 * 2.0, h);
      this.renderSplitLine(renderer, 0.0, h3, w, h3);
      this.renderSplitLine(renderer, 0.0, h3 * 2.0, w, h3 * 2.0);
      SPLIT_LINES_COLOR.a = prevA;
      renderer.render();
   }

   private void renderSplitLine(Renderer2D renderer, double x, double y, double destX, double destY) {
      double incX = 0.0;
      double incY = 0.0;
      if (x == destX) {
         incY = Utils.getWindowWidth() / 25.0;
      } else {
         incX = Utils.getWindowWidth() / 25.0;
      }

      do {
         renderer.line(x, y, x + incX, y + incY, SPLIT_LINES_COLOR);
         x += incX * 2.0;
         y += incY * 2.0;
      } while (!(x >= destX) || !(y >= destY));
   }

   public static boolean isOpen() {
      Screen s = ReviveClient.mc.currentScreen;
      return s instanceof HudEditorScreen
         || s instanceof AddHudElementScreen
         || s instanceof HudElementPresetsScreen
         || s instanceof HudElementScreen
         || s instanceof NewHudEditorScreen
         || s instanceof NewHudElementScreen
         || s instanceof NewAddHudElementScreen
         || s instanceof NewHudElementPresetsScreen;
   }

   private class SelectionBox implements Snapper.Element {
      private int x;
      private int y;
      private final int width;
      private final int height;

      public SelectionBox() {
         int x1 = Integer.MAX_VALUE;
         int y1 = Integer.MAX_VALUE;
         int x2 = 0;
         int y2 = 0;

         for (HudElement element : HudEditorScreen.this.selection) {
            if (element.getX() < x1) {
               x1 = element.getX();
            } else if (element.getX() > x2) {
               x2 = element.getX();
            }

            if (element.getX2() < x1) {
               x1 = element.getX2();
            } else if (element.getX2() > x2) {
               x2 = element.getX2();
            }

            if (element.getY() < y1) {
               y1 = element.getY();
            } else if (element.getY() > y2) {
               y2 = element.getY();
            }

            if (element.getY2() < y1) {
               y1 = element.getY2();
            } else if (element.getY2() > y2) {
               y2 = element.getY2();
            }
         }

         this.x = x1;
         this.y = y1;
         this.width = x2 - x1;
         this.height = y2 - y1;
      }

      @Override
      public int getX() {
         return this.x;
      }

      @Override
      public int getY() {
         return this.y;
      }

      @Override
      public int getWidth() {
         return this.width;
      }

      @Override
      public int getHeight() {
         return this.height;
      }

      @Override
      public void setPos(int x, int y) {
         for (HudElement element : HudEditorScreen.this.selection) {
            element.setPos(x + (element.x - this.x), y + (element.y - this.y));
         }

         this.x = x;
         this.y = y;
      }

      @Override
      public void move(int deltaX, int deltaY) {
         int prevX = this.x;
         int prevY = this.y;
         int border = Hud.get().getBorder();
         this.x = MathHelper.clamp(this.x + deltaX, border, Utils.getWindowWidth() - this.width - border);
         this.y = MathHelper.clamp(this.y + deltaY, border, Utils.getWindowHeight() - this.height - border);

         for (HudElement element : HudEditorScreen.this.selection) {
            element.move(this.x - prevX, this.y - prevY);
         }
      }
   }
}
