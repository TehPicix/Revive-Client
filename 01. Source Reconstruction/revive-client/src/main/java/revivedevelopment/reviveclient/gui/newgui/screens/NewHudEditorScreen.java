package revivedevelopment.reviveclient.gui.newgui.screens;

import java.util.Iterator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiColors;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.screens.HudEditorScreen;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.other.Snapper;

public class NewHudEditorScreen extends Screen implements Snapper.Container {
   private static final int INACTIVE_BG = 851974425;
   private static final int INACTIVE_OL = -926410471;
   private static final int HOVER_BG = 838860800 | GuiColors.ACCENT & 16777215;
   private static final int HOVER_OL = -939524096 | GuiColors.ACCENT & 16777215;
   private static final int HINT_BG = -1342177280 | GuiColors.BG_PANEL & 16777215;
   private static final int HINT_BORDER = -1342177280 | GuiColors.BORDER_LIGHT & 16777215;
   private final Hud hud = Hud.get();
   private final Snapper snapper = new Snapper(this);
   private HudElement dragging;
   private boolean moved;
   private int lastMouseX;
   private int lastMouseY;
   private int clickX;
   private int clickY;
   private double dragRemainderX;
   private double dragRemainderY;

   public NewHudEditorScreen() {
      super(Text.literal("Hud Editor"));
   }

   public boolean shouldPause() {
      return false;
   }

   public boolean mouseClicked(Click click, boolean doubled) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
      int mx = (int)(mouseX * s);
      int my = (int)(mouseY * s);
      this.clickX = mx;
      this.clickY = my;
      this.lastMouseX = mx;
      this.lastMouseY = my;
      this.dragRemainderX = 0.0;
      this.dragRemainderY = 0.0;
      this.moved = false;
      if (button == 0) {
         this.dragging = this.getHovered(mx, my);
         this.setDragging(true);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseDragged(Click click, double offsetX, double offsetY) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      if (this.dragging != null && button == 0) {
         double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
         double nativeDx = offsetX * s + this.dragRemainderX;
         double nativeDy = offsetY * s + this.dragRemainderY;
         int ddx = (int)nativeDx;
         int ddy = (int)nativeDy;
         this.dragRemainderX = nativeDx - ddx;
         this.dragRemainderY = nativeDy - ddy;
         if (ddx != 0 || ddy != 0) {
            this.snapper.move(this.dragging, ddx, ddy);
            this.moved = true;
         }

         this.lastMouseX += ddx;
         this.lastMouseY += ddy;
         return true;
      } else {
         double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
         this.lastMouseX = (int)(mouseX * s);
         this.lastMouseY = (int)(mouseY * s);
         return false;
      }
   }

   public void mouseMoved(double mouseX, double mouseY) {
      double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
      this.lastMouseX = (int)(mouseX * s);
      this.lastMouseY = (int)(mouseY * s);
   }

   public boolean mouseReleased(Click click) {
      double mouseX = click.x();
      double mouseY = click.y();
      int button = click.button();
      double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
      int mx = (int)(mouseX * s);
      int my = (int)(mouseY * s);
      if (!this.moved) {
         HudElement hovered = this.getHovered(mx, my);
         if (button == 0) {
            if (hovered != null) {
               hovered.toggle();
            }
         } else if (button == 1) {
            if (hovered != null) {
               MinecraftClient.getInstance().setScreen(new NewHudElementScreen(hovered, this));
            } else {
               MinecraftClient.getInstance().setScreen(new NewAddHudElementScreen(mx, my, this));
            }
         }
      }

      if (button == 0) {
         boolean wasMoved = this.moved;
         this.snapper.unsnap();
         this.dragging = null;
         this.moved = false;
         this.dragRemainderX = 0.0;
         this.dragRemainderY = 0.0;
         this.setDragging(false);
         if (wasMoved) {
            try {
               Hud hud = Hud.get();
               if (hud != null) {
                  hud.save();
               }
            } catch (Throwable var13) {
            }
         }
      }

      return false;
   }

   public void close() {
      try {
         Hud hud = Hud.get();
         if (hud != null) {
            hud.save();
         }
      } catch (Throwable var2) {
      }

      super.close();
   }

   public boolean keyPressed(KeyInput input) {
      int keyCode = input.key();
      int scanCode = input.scancode();
      int modifiers = input.modifiers();
      if (keyCode == 261) {
         HudElement hovered = this.getHovered(this.lastMouseX, this.lastMouseY);
         if (hovered != null) {
            hovered.remove();
            return true;
         }
      } else if (keyCode == 257 || keyCode == 335) {
         HudElement hovered = this.getHovered(this.lastMouseX, this.lastMouseY);
         if (hovered != null) {
            hovered.toggle();
            return true;
         }
      }

      return super.keyPressed(input);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (!Utils.canUpdate()) {
         this.renderBackground(context, mouseX, mouseY, deltaTicks);
      }

      Utils.unscaledProjection();
      HudEditorScreen.renderElements(context);
      Utils.scaledProjection();
      double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
      int mx = (int)(mouseX * s);
      int my = (int)(mouseY * s);
      HudElement hovered = this.dragging != null ? this.dragging : this.getHovered(mx, my);

      for (HudElement e : this.hud) {
         if (!e.isActive()) {
            this.drawNativeBox(context, e.x, e.y, e.getWidth(), e.getHeight(), 851974425, -926410471);
         }
      }

      if (hovered != null) {
         this.drawNativeBox(context, hovered.x, hovered.y, hovered.getWidth(), hovered.getHeight(), HOVER_BG, HOVER_OL);
      }

      this.drawHint(context);
   }

   private void drawHint(DrawContext context) {
      FontManager fm = FontManager.get();
      String hint = "Left click: toggle / drag   •   Right click: edit (or add)   •   Delete: remove   •   Esc: close";
      int rowH = fm.getRowHeight();
      int w = fm.getTextWidth(hint) + 10;
      int h = rowH + 4;
      int x = (this.width - w) / 2;
      int y = this.height - h - 4;
      context.fill(x, y, x + w, y + h, HINT_BG);
      context.fill(x, y, x + w, y + 1, HINT_BORDER);
      context.fill(x, y + h - 1, x + w, y + h, HINT_BORDER);
      context.fill(x, y, x + 1, y + h, HINT_BORDER);
      context.fill(x + w - 1, y, x + w, y + h, HINT_BORDER);
      fm.drawText(context, hint, x + 5, y + (h - fm.getTextHeight()) / 2, fm.getTextColor());
   }

   private HudElement getHovered(int mx, int my) {
      HudElement result = null;

      for (HudElement e : this.hud) {
         if (mx >= e.x && mx <= e.x + e.getWidth() && my >= e.y && my <= e.y + e.getHeight()) {
            result = e;
         }
      }

      return result;
   }

   private void drawNativeBox(DrawContext ctx, int x, int y, int w, int h, int bg, int ol) {
      float scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
      Matrix3x2fStack ms = ctx.getMatrices();
      ms.pushMatrix();
      ms.scale(1.0F / scale, 1.0F / scale);
      ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
      ctx.fill(x, y, x + w, y + 1, ol);
      ctx.fill(x, y + h - 1, x + w, y + h, ol);
      ctx.fill(x, y + 1, x + 1, y + h - 1, ol);
      ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, ol);
      ms.popMatrix();
   }

   @Override
   public Iterable<Snapper.Element> getElements() {
      return () -> new Iterator<Snapper.Element>() {
         private final Iterator<HudElement> it = NewHudEditorScreen.this.hud.iterator();

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
      return element == this.dragging;
   }

   @Override
   public int getSnappingRange() {
      return this.hud.getSnappingRange();
   }

   public static boolean matches(Screen s) {
      return s instanceof NewHudEditorScreen || s instanceof NewHudElementScreen;
   }

   private static int clamp(int v, int lo, int hi) {
      return MathHelper.clamp(v, lo, hi);
   }
}
