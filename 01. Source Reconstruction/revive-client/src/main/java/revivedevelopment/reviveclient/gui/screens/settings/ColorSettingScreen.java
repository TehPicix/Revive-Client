package revivedevelopment.reviveclient.gui.screens.settings;

import net.minecraft.util.math.MathHelper;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.widgets.WQuad;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.input.WIntEdit;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.gui.widgets.pressable.WCheckbox;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ColorSettingScreen extends WindowScreen {
   private static final Color[] HUE_COLORS = new Color[]{
      new Color(255, 0, 0),
      new Color(255, 255, 0),
      new Color(0, 255, 0),
      new Color(0, 255, 255),
      new Color(0, 0, 255),
      new Color(255, 0, 255),
      new Color(255, 0, 0)
   };
   private static final Color WHITE = new Color(255, 255, 255);
   private static final Color BLACK = new Color(0, 0, 0);
   public Runnable action;
   private final Setting<SettingColor> setting;
   private WQuad displayQuad;
   private ColorSettingScreen.WBrightnessQuad brightnessQuad;
   private ColorSettingScreen.WHueQuad hueQuad;
   private WIntEdit rItb;
   private WIntEdit gItb;
   private WIntEdit bItb;
   private WIntEdit aItb;
   private WCheckbox rainbow;

   public ColorSettingScreen(GuiTheme theme, Setting<SettingColor> setting) {
      super(theme, "Select Color");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      this.displayQuad = this.add(this.theme.quad(this.setting.get())).expandX().widget();
      this.brightnessQuad = this.add(new ColorSettingScreen.WBrightnessQuad()).expandX().widget();
      this.hueQuad = this.add(new ColorSettingScreen.WHueQuad()).expandX().widget();
      WTable rgbaTable = this.add(this.theme.table()).expandX().widget();
      rgbaTable.add(this.theme.label("R:"));
      this.rItb = rgbaTable.add(this.theme.intEdit(this.setting.get().r, 0, 255, 0, 255, false)).expandX().widget();
      this.rItb.action = this::rgbaChanged;
      rgbaTable.row();
      rgbaTable.add(this.theme.label("G:"));
      this.gItb = rgbaTable.add(this.theme.intEdit(this.setting.get().g, 0, 255, 0, 255, false)).expandX().widget();
      this.gItb.action = this::rgbaChanged;
      rgbaTable.row();
      rgbaTable.add(this.theme.label("B:"));
      this.bItb = rgbaTable.add(this.theme.intEdit(this.setting.get().b, 0, 255, 0, 255, false)).expandX().widget();
      this.bItb.action = this::rgbaChanged;
      rgbaTable.row();
      rgbaTable.add(this.theme.label("A:"));
      this.aItb = rgbaTable.add(this.theme.intEdit(this.setting.get().a, 0, 255, 0, 255, false)).expandX().widget();
      this.aItb.action = this::rgbaChanged;
      WHorizontalList rainbowList = this.add(this.theme.horizontalList()).expandX().widget();
      rainbowList.add(this.theme.label("Rainbow: "));
      this.rainbow = this.theme.checkbox(this.setting.get().rainbow);
      this.rainbow.action = () -> {
         this.setting.get().rainbow = this.rainbow.checked;
         this.setting.onChanged();
      };
      rainbowList.add(this.rainbow).expandCellX().right();
      WHorizontalList bottomList = this.add(this.theme.horizontalList()).expandX().widget();
      WButton backButton = bottomList.add(this.theme.button("Back")).expandX().widget();
      backButton.action = this::close;
      WButton copyButton = bottomList.add(this.theme.button(GuiRenderer.COPY)).widget();
      copyButton.action = this::toClipboard;
      copyButton.tooltip = "Copy config";
      WButton pasteButton = bottomList.add(this.theme.button(GuiRenderer.PASTE)).widget();
      pasteButton.action = this::fromClipboard;
      pasteButton.tooltip = "Paste config";
      WButton resetButton = bottomList.add(this.theme.button(GuiRenderer.RESET)).widget();
      resetButton.action = () -> {
         this.setting.reset();
         this.setFromSetting();
         this.callAction();
      };
      this.hueQuad.calculateFromSetting(false);
      this.brightnessQuad.calculateFromColor(this.setting.get(), false);
   }

   private void setFromSetting() {
      SettingColor c = this.setting.get();
      if (c.r != this.rItb.get()) {
         this.rItb.set(c.r);
      }

      if (c.g != this.gItb.get()) {
         this.gItb.set(c.g);
      }

      if (c.b != this.bItb.get()) {
         this.bItb.set(c.b);
      }

      if (c.a != this.aItb.get()) {
         this.aItb.set(c.a);
      }

      this.rainbow.checked = c.rainbow;
      this.displayQuad.color.set(this.setting.get());
      this.hueQuad.calculateFromSetting(true);
      this.brightnessQuad.calculateFromColor(this.setting.get(), true);
   }

   private void callAction() {
      if (this.action != null) {
         this.action.run();
      }
   }

   public void tick() {
      super.tick();
      if (this.setting.get().rainbow) {
         this.setFromSetting();
      }
   }

   private void rgbaChanged() {
      Color c = this.setting.get();
      c.r = this.rItb.get();
      c.g = this.gItb.get();
      c.b = this.bItb.get();
      c.a = this.aItb.get();
      c.validate();
      if (c.r != this.rItb.get()) {
         this.rItb.set(c.r);
      }

      if (c.g != this.gItb.get()) {
         this.gItb.set(c.g);
      }

      if (c.b != this.bItb.get()) {
         this.bItb.set(c.b);
      }

      if (c.a != this.aItb.get()) {
         this.aItb.set(c.a);
      }

      this.displayQuad.color.set(c);
      this.hueQuad.calculateFromSetting(true);
      this.brightnessQuad.calculateFromColor(this.setting.get(), true);
      this.setting.onChanged();
      this.callAction();
   }

   private void hsvChanged() {
      double r = 0.0;
      double g = 0.0;
      double b = 0.0;
      boolean calculated = false;
      if (this.brightnessQuad.saturation <= 0.0) {
         r = this.brightnessQuad.value;
         g = this.brightnessQuad.value;
         b = this.brightnessQuad.value;
         calculated = true;
      }

      if (!calculated) {
         double hh = this.hueQuad.hueAngle;
         if (hh >= 360.0) {
            hh = 0.0;
         }

         hh /= 60.0;
         int i = (int)hh;
         double ff = hh - i;
         double p = this.brightnessQuad.value * (1.0 - this.brightnessQuad.saturation);
         double q = this.brightnessQuad.value * (1.0 - this.brightnessQuad.saturation * ff);
         double t = this.brightnessQuad.value * (1.0 - this.brightnessQuad.saturation * (1.0 - ff));
         switch (i) {
            case 0:
               r = this.brightnessQuad.value;
               g = t;
               b = p;
               break;
            case 1:
               r = q;
               g = this.brightnessQuad.value;
               b = p;
               break;
            case 2:
               r = p;
               g = this.brightnessQuad.value;
               b = t;
               break;
            case 3:
               r = p;
               g = q;
               b = this.brightnessQuad.value;
               break;
            case 4:
               r = t;
               g = p;
               b = this.brightnessQuad.value;
               break;
            default:
               r = this.brightnessQuad.value;
               g = p;
               b = q;
         }
      }

      Color c = this.setting.get();
      c.r = (int)(r * 255.0);
      c.g = (int)(g * 255.0);
      c.b = (int)(b * 255.0);
      c.validate();
      this.rItb.set(c.r);
      this.gItb.set(c.g);
      this.bItb.set(c.b);
      this.displayQuad.color.set(c);
      this.setting.onChanged();
      this.callAction();
   }

   @Override
   public boolean toClipboard() {
      return NbtUtils.toClipboard(this.setting.get());
   }

   @Override
   public boolean fromClipboard() {
      if (!NbtUtils.fromClipboard(this.setting.get())) {
         String clipboard = ReviveClient.mc.keyboard.getClipboard().trim();
         SettingColor parsed = this.parseRGBA(clipboard);
         if (parsed == null) {
            parsed = this.parseHex(clipboard);
            if (parsed == null) {
               return false;
            }
         }

         this.setting.set(parsed);
      }

      this.setting.get().validate();
      if (this.parent instanceof WidgetScreen p) {
         p.reload();
      }

      this.reload();
      return true;
   }

   private SettingColor parseRGBA(String string) {
      String[] rgba = string.replaceAll("[^0-9|,]", "").split(",");
      if (rgba.length >= 3 && rgba.length <= 4) {
         try {
            SettingColor color = new SettingColor(Integer.parseInt(rgba[0]), Integer.parseInt(rgba[1]), Integer.parseInt(rgba[2]));
            if (rgba.length == 4) {
               color.a = Integer.parseInt(rgba[3]);
            }

            return color;
         } catch (NumberFormatException var5) {
            return null;
         }
      } else {
         return null;
      }
   }

   private SettingColor parseHex(String string) {
      if (!string.startsWith("#")) {
         return null;
      } else {
         String hex = string.toLowerCase().replaceAll("[^0-9a-f]", "");
         if (hex.length() != 6 && hex.length() != 8) {
            return null;
         } else {
            try {
               SettingColor color = new SettingColor(
                  Integer.parseInt(hex.substring(0, 2), 16), Integer.parseInt(hex.substring(2, 4), 16), Integer.parseInt(hex.substring(4, 6), 16)
               );
               if (hex.length() == 8) {
                  color.a = Integer.parseInt(hex.substring(6, 8), 16);
               }

               return color;
            } catch (NumberFormatException var5) {
               return null;
            }
         }
      }
   }

   private class WBrightnessQuad extends WWidget {
      double saturation;
      double value;
      double handleX;
      double handleY;
      boolean dragging;
      double lastMouseX;
      double lastMouseY;
      double fixedHeight = -1.0;

      @Override
      protected void onCalculateSize() {
         double s = this.theme.scale(75.0);
         this.width = s;
         this.height = s;
         if (this.fixedHeight != -1.0) {
            this.height = this.fixedHeight;
            this.fixedHeight = -1.0;
         }
      }

      void calculateFromColor(Color c, boolean calculateNow) {
         double min = Math.min(Math.min(c.r, c.g), c.b);
         double max = Math.max(Math.max(c.r, c.g), c.b);
         double delta = max - min;
         this.value = max / 255.0;
         if (delta == 0.0) {
            this.saturation = 0.0;
         } else {
            this.saturation = delta / max;
         }

         if (calculateNow) {
            this.handleX = this.saturation * this.width;
            this.handleY = (1.0 - this.value) * this.height;
         }
      }

      @Override
      public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
         if (used) {
            return false;
         } else if (this.mouseOver) {
            this.dragging = true;
            this.handleX = this.lastMouseX - this.x;
            this.handleY = this.lastMouseY - this.y;
            this.handleMoved();
            return true;
         } else {
            return false;
         }
      }

      @Override
      public boolean onMouseReleased(double mouseX, double mouseY, int button) {
         if (this.dragging) {
            this.dragging = false;
         }

         return false;
      }

      @Override
      public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
         if (this.dragging) {
            if (mouseX >= this.x && mouseX <= this.x + this.width) {
               this.handleX += mouseX - lastMouseX;
            } else if (this.handleX > 0.0 && mouseX < this.x) {
               this.handleX = 0.0;
            } else if (this.handleX < this.width && mouseX > this.x + this.width) {
               this.handleX = this.width;
            }

            if (mouseY >= this.y && mouseY <= this.y + this.height) {
               this.handleY += mouseY - lastMouseY;
            } else if (this.handleY > 0.0 && mouseY < this.y) {
               this.handleY = 0.0;
            } else if (this.handleY < this.height && mouseY > this.y + this.height) {
               this.handleY = this.height;
            }

            this.handleMoved();
         }

         this.lastMouseX = mouseX;
         this.lastMouseY = mouseY;
      }

      void handleMoved() {
         double handleXPercentage = this.handleX / this.width;
         double handleYPercentage = this.handleY / this.height;
         this.saturation = handleXPercentage;
         this.value = 1.0 - handleYPercentage;
         ColorSettingScreen.this.hsvChanged();
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         if (this.height != this.width) {
            this.fixedHeight = this.width;
            this.invalidate();
            this.handleX = this.saturation * this.width;
            this.handleY = (1.0 - this.value) * this.fixedHeight;
         }

         ColorSettingScreen.this.hueQuad.calculateColor();
         renderer.quad(
            this.x,
            this.y,
            this.width,
            this.height,
            ColorSettingScreen.WHITE,
            ColorSettingScreen.this.hueQuad.color,
            ColorSettingScreen.BLACK,
            ColorSettingScreen.BLACK
         );
         double s = this.theme.scale(2.0);
         renderer.quad(this.x + this.handleX - s / 2.0, this.y + this.handleY - s / 2.0, s, s, ColorSettingScreen.WHITE);
      }
   }

   private class WHueQuad extends WWidget {
      private double hueAngle;
      private double handleX;
      private final Color color = new Color();
      private boolean dragging;
      private double lastMouseX;
      private boolean calculateHandleXOnLayout;

      @Override
      protected void onCalculateSize() {
         this.width = this.theme.scale(75.0);
         this.height = this.theme.scale(10.0);
      }

      void calculateFromSetting(boolean calculateNow) {
         Color c = ColorSettingScreen.this.setting.get();
         boolean calculated = false;
         double min = Math.min(c.r, c.g);
         min = min < c.b ? min : c.b;
         double max = Math.max(c.r, c.g);
         max = max > c.b ? max : c.b;
         double delta = max - min;
         if (delta < 1.0E-5) {
            this.hueAngle = 0.0;
            calculated = true;
         }

         if (!calculated) {
            if (max <= 0.0) {
               this.hueAngle = 0.0;
               calculated = true;
            }

            if (!calculated) {
               if (c.r >= max) {
                  this.hueAngle = (c.g - c.b) / delta;
               } else if (c.g >= max) {
                  this.hueAngle = 2.0 + (c.b - c.r) / delta;
               } else {
                  this.hueAngle = 4.0 + (c.r - c.g) / delta;
               }

               this.hueAngle *= 60.0;
               if (this.hueAngle < 0.0) {
                  this.hueAngle += 360.0;
               }
            }
         }

         if (calculateNow) {
            double huePercentage = this.hueAngle / 360.0;
            this.handleX = huePercentage * this.width;
         } else {
            this.calculateHandleXOnLayout = true;
         }
      }

      @Override
      protected void onCalculateWidgetPositions() {
         if (this.calculateHandleXOnLayout) {
            double huePercentage = this.hueAngle / 360.0;
            this.handleX = huePercentage * this.width;
            this.calculateHandleXOnLayout = false;
         }

         super.onCalculateWidgetPositions();
      }

      void calculateColor() {
         double hh = this.hueAngle;
         if (hh >= 360.0) {
            hh = 0.0;
         }

         hh /= 60.0;
         int i = (int)hh;
         double ff = hh - i;
         double p = 0.0;
         double q = 1.0 * (1.0 - 1.0 * ff);
         double t = 1.0 * (1.0 - 1.0 * (1.0 - ff));
         double r;
         double g;
         double b;
         switch (i) {
            case 0:
               r = 1.0;
               g = t;
               b = p;
               break;
            case 1:
               r = q;
               g = 1.0;
               b = p;
               break;
            case 2:
               r = p;
               g = 1.0;
               b = t;
               break;
            case 3:
               r = p;
               g = q;
               b = 1.0;
               break;
            case 4:
               r = t;
               g = p;
               b = 1.0;
               break;
            default:
               r = 1.0;
               g = p;
               b = q;
         }

         this.color.r = (int)(r * 255.0);
         this.color.g = (int)(g * 255.0);
         this.color.b = (int)(b * 255.0);
         this.color.validate();
      }

      @Override
      public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
         if (used) {
            return false;
         } else if (this.mouseOver) {
            this.dragging = true;
            this.handleX = this.lastMouseX - this.x;
            this.calculateHueAngleFromHandleX();
            ColorSettingScreen.this.hsvChanged();
            return true;
         } else {
            return false;
         }
      }

      @Override
      public boolean onMouseReleased(double mouseX, double mouseY, int button) {
         if (this.dragging) {
            this.dragging = false;
         }

         return this.mouseOver;
      }

      @Override
      public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
         if (this.dragging) {
            if (mouseX >= this.x && mouseX <= this.x + this.width) {
               this.handleX += mouseX - lastMouseX;
               this.handleX = MathHelper.clamp(this.handleX, 0.0, this.width);
            } else if (this.handleX > 0.0 && mouseX < this.x) {
               this.handleX = 0.0;
            } else if (this.handleX < this.width && mouseX > this.x + this.width) {
               this.handleX = this.width;
            }

            this.calculateHueAngleFromHandleX();
            ColorSettingScreen.this.hsvChanged();
         }

         this.lastMouseX = mouseX;
      }

      void calculateHueAngleFromHandleX() {
         double handleXPercentage = this.handleX / (this.width - 4.0);
         this.hueAngle = handleXPercentage * 360.0;
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         double sectionWidth = this.width / (ColorSettingScreen.HUE_COLORS.length - 1);
         double sectionX = this.x;

         for (int i = 0; i < ColorSettingScreen.HUE_COLORS.length - 1; i++) {
            renderer.quad(
               sectionX,
               this.y,
               sectionWidth,
               this.height,
               ColorSettingScreen.HUE_COLORS[i],
               ColorSettingScreen.HUE_COLORS[i + 1],
               ColorSettingScreen.HUE_COLORS[i + 1],
               ColorSettingScreen.HUE_COLORS[i]
            );
            sectionX += sectionWidth;
         }

         double s = this.theme.scale(2.0);
         renderer.quad(this.x + this.handleX - s / 2.0, this.y, s, this.height, ColorSettingScreen.WHITE);
      }
   }
}
