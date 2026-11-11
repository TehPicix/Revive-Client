package revivedevelopment.reviveclient.systems.modules.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.newgui.GuiShaderBackground;
import revivedevelopment.reviveclient.gui.newgui.screens.GuiStyleEditorScreen;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Gui extends Module {
   private final SettingGroup sgStyle = this.settings.createGroup("Style");
   private final SettingGroup sgLayout = this.settings.createGroup("Layout");
   private final SettingGroup sgModern = this.settings.createGroup("Modern Colours");
   private final SettingGroup sgText = this.settings.createGroup("Text");
   private final SettingGroup sgTheme = this.settings.createGroup("Theme");
   private final SettingGroup sgAdv = this.settings.createGroup("Advanced Colours");
   private final SettingGroup sgHud = this.settings.createGroup("HUD");
   public final Setting<Gui.GuiStyle> style = this.sgStyle
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("gui-style"))
                  .description(
                     "Which ClickGui opens: Classic and Clean are the two column GUIs, Modern is the new centred window / floating panels GUI. The open GUI switches immediately."
                  ))
               .defaultValue(Gui.GuiStyle.Modern))
            .build()
      );
   public final Setting<Boolean> openStyleEditor = this.sgStyle
      .add(
         new BoolSetting.Builder()
            .name("style-editor")
            .description(
               "Open the GUI Style editor: pick, create, duplicate, rename, delete and share ClickGui styles. Built-in styles (Classic/Clean/Modern) are locked but can be duplicated into your own."
            )
            .defaultValue(false)
            .onChanged(this::onOpenStyleEditor)
            .build()
      );
   public final Setting<Gui.ModernLayout> modernLayout = this.sgStyle
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("modern-layout"))
                     .description(
                        "Modern GUI: floating, draggable category panels, or one centred window with a category sidebar, module cards and a settings drawer."
                     ))
                  .defaultValue(Gui.ModernLayout.Panels))
               .visible(() -> this.style.get() == Gui.GuiStyle.Modern))
            .build()
      );
   public final Setting<Double> modernScale = this.sgStyle
      .add(
         new DoubleSetting.Builder()
            .name("modern-scale")
            .description(
               "Size of the Modern GUI. 1.0 matches the mockup: the centred window is 57% of the screen width and body text is 13px on a 1080p screen, at any Minecraft GUI scale."
            )
            .defaultValue(1.0)
            .min(0.6)
            .max(1.6)
            .sliderRange(0.6, 1.6)
            .visible(() -> this.style.get() == Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> modernAnimations = this.sgStyle
      .add(
         new BoolSetting.Builder()
            .name("modern-animations")
            .description("Animate the Modern GUI: window open, drawer slide, tab underline and switches.")
            .defaultValue(true)
            .visible(() -> this.style.get() == Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> modernBlur = this.sgStyle
      .add(
         new BoolSetting.Builder()
            .name("modern-blur")
            .description("Blur and dim the game behind the Modern GUI (when the shader background is off).")
            .defaultValue(true)
            .visible(() -> this.style.get() == Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Integer> rowHeight = this.sgLayout
      .add(
         new IntSetting.Builder()
            .name("row-height")
            .description("Height of each module/setting row in the Classic/Clean GUI. 0 = automatic (each style's default).")
            .defaultValue(0)
            .min(0)
            .sliderMax(24)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Integer> headerHeight = this.sgLayout
      .add(
         new IntSetting.Builder()
            .name("header-height")
            .description("Height of each category panel header in the Classic/Clean GUI. 0 = automatic (each style's default).")
            .defaultValue(0)
            .min(0)
            .sliderMax(28)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Integer> cornerRadius = this.sgLayout
      .add(
         new IntSetting.Builder()
            .name("corner-radius")
            .description("Corner rounding of panels in the Classic/Clean GUI. 0 = square corners.")
            .defaultValue(2)
            .min(0)
            .sliderMax(6)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Gui.AccentBar> accentBar = this.sgLayout
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("accent-bar"))
                     .description("The active-module bar on the left of a row. Auto uses each style's default (Classic: glow, Clean: rail)."))
                  .defaultValue(Gui.AccentBar.Auto))
               .visible(() -> this.style.get() != Gui.GuiStyle.Modern))
            .build()
      );
   public final Setting<Integer> panelWidth = this.sgLayout
      .add(
         new IntSetting.Builder()
            .name("panel-width")
            .description("Width of each category panel in the Classic/Clean GUI. 0 = automatic (fit all categories across the screen).")
            .defaultValue(0)
            .min(0)
            .sliderMax(240)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Gui.Striping> rowStriping = this.sgLayout
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("row-striping"))
                     .description("Alternating-row shading in the Classic/Clean GUI. Auto = each style's default (Classic on, Clean off)."))
                  .defaultValue(Gui.Striping.Auto))
               .visible(() -> this.style.get() != Gui.GuiStyle.Modern))
            .build()
      );
   public final Setting<Double> textSize = this.sgLayout
      .add(
         new DoubleSetting.Builder()
            .name("text-size")
            .description("Multiplier on module/setting text size in the Classic/Clean GUI (on top of the auto-fit).")
            .defaultValue(1.0)
            .min(0.5)
            .max(1.6)
            .sliderRange(0.5, 1.6)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Integer> panelOpacity = this.sgLayout
      .add(
         new IntSetting.Builder()
            .name("panel-opacity")
            .description("Opacity of the Classic/Clean panels (percent). Lower it to let a gradient or animated background show through the module list.")
            .defaultValue(100)
            .min(20)
            .sliderRange(20, 100)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> modernWindow = this.sgModern
      .add(
         new ColorSetting.Builder()
            .name("window")
            .description("Modern GUI window and panel background.")
            .defaultValue(new SettingColor(11, 11, 13, 255))
            .build()
      );
   public final Setting<SettingColor> modernCard = this.sgModern
      .add(
         new ColorSetting.Builder()
            .name("cards")
            .description("Modern GUI module cards and selected rows.")
            .defaultValue(new SettingColor(20, 20, 22, 255))
            .build()
      );
   public final Setting<SettingColor> modernText = this.sgModern
      .add(
         new ColorSetting.Builder()
            .name("text")
            .description("Modern GUI primary text and white controls.")
            .defaultValue(new SettingColor(242, 242, 243, 255))
            .build()
      );
   public final Setting<SettingColor> modernTextDim = this.sgModern
      .add(
         new ColorSetting.Builder()
            .name("text-dim")
            .description("Modern GUI secondary text: settings summaries, values, inactive modules.")
            .defaultValue(new SettingColor(139, 139, 146, 255))
            .build()
      );
   public final Setting<SettingColor> modernAccent = this.sgModern
      .add(
         new ColorSetting.Builder()
            .name("accent")
            .description(
               "Modern GUI accent. By default only the favourite star uses it; turn on accent-on-controls to push it into switches, sliders, the tab underline and the active profile."
            )
            .defaultValue(new SettingColor(240, 132, 43, 255))
            .build()
      );
   public final Setting<Boolean> accentOnControls = this.sgModern
      .add(
         new BoolSetting.Builder()
            .name("accent-on-controls")
            .description("Use the accent colour on switches, sliders, the active tab underline and the active profile instead of white.")
            .defaultValue(false)
            .build()
      );
   public final Setting<FontManager.MenuFont> font = this.sgText
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("font"))
                     .description(
                        "Typeface for the ClickGui. Default and Uniform are Minecraft's bitmap fonts and look blocky at small scales; the rest are TrueType and render smoothly."
                     ))
                  .defaultValue(FontManager.MenuFont.Comfortaa))
               // NOTE (source reconstruction): the repeated raw "(EnumSetting.Builder)" casts in
               // this chain erase the builder's generic parameter, so "f" arrives as Object here.
               .onChanged(f -> FontManager.get().setFont((FontManager.MenuFont) f)))
            .build()
      );
   public final Setting<FontManager.MenuFontStyle> fontStyle = this.sgText
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("style"))
                     .description("Which cut of the typeface to draw: Regular, Bold, Italic or Bold Italic."))
                  .defaultValue(FontManager.MenuFontStyle.Regular))
               // NOTE (source reconstruction): same raw-type erasure as the "font" setting above.
               .onChanged(s -> FontManager.get().setFontStyle((FontManager.MenuFontStyle) s)))
            .build()
      );
   public final Setting<Double> textScale = this.sgText
      .add(
         new DoubleSetting.Builder()
            .name("text-scale")
            .description("Size of the ClickGui's text.")
            .defaultValue(0.5)
            .min(0.3)
            .max(1.5)
            .sliderRange(0.3, 1.5)
            .onChanged(s -> FontManager.get().setScale(s.floatValue()))
            .build()
      );
   public final Setting<Double> guiScale = this.sgText
      .add(
         new DoubleSetting.Builder()
            .name("row-scale")
            .description("Height of module and setting rows.")
            .defaultValue(0.8)
            .min(0.4)
            .max(2.0)
            .sliderRange(0.4, 2.0)
            .onChanged(s -> FontManager.get().setGuiScale(s.floatValue()))
            .build()
      );
   public final Setting<Boolean> textShadow = this.sgText
      .add(
         new BoolSetting.Builder()
            .name("text-shadow")
            .description("Draw a drop shadow behind the ClickGui's text. Can look embossed and soften small text.")
            .defaultValue(false)
            .onChanged(b -> FontManager.get().setTextShadow(b))
            .build()
      );
   public final Setting<SettingColor> hudTextPrimary = this.sgHud
      .add(
         new ColorSetting.Builder()
            .name("hud-text-primary")
            .description("Primary color for the in-game HUD text elements.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .onChanged(c -> FontManager.get().setHudTextPrimary(packRGB(c)))
            .build()
      );
   public final Setting<SettingColor> hudTextSecondary = this.sgHud
      .add(
         new ColorSetting.Builder()
            .name("hud-text-secondary")
            .description("Secondary color for the in-game HUD text elements.")
            .defaultValue(new SettingColor(176, 176, 176, 255))
            .onChanged(c -> FontManager.get().setHudTextSecondary(packRGB(c)))
            .build()
      );
   public final Setting<Boolean> hudTextBackground = this.sgHud
      .add(
         new BoolSetting.Builder()
            .name("text-background")
            .description("Draw the panel background behind the Text HUD elements. Turn off to remove it.")
            .defaultValue(true)
            .onChanged(b -> FontManager.get().setHudTextBackground(b))
            .build()
      );
   public final Setting<SettingColor> primary = this.sgTheme
      .add(
         new ColorSetting.Builder()
            .name("primary")
            .description("Line and border color.")
            .defaultValue(new SettingColor(255, 105, 190, 255))
            .onChanged(c -> FontManager.get().setPrimaryColor(packRGB(c)))
            .build()
      );
   public final Setting<SettingColor> secondary = this.sgTheme
      .add(
         new ColorSetting.Builder()
            .name("secondary")
            .description("Header fill and highlight color.")
            .defaultValue(new SettingColor(255, 105, 190, 255))
            .onChanged(c -> FontManager.get().setSecondaryColor(packRGB(c)))
            .build()
      );
   public final Setting<GuiShaderBackground.Mode> shader = this.sgTheme
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shader"))
                  .description("Animated GLSL shader background for the ClickGui, themed by your primary/secondary colors."))
               .defaultValue(GuiShaderBackground.Mode.Off))
            .build()
      );
   public final Setting<Double> shaderOpacity = this.sgTheme
      .add(
         new DoubleSetting.Builder()
            .name("shader-opacity")
            .description("Opacity of the shader background.")
            .defaultValue(0.7)
            .min(0.0)
            .max(1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.shader.get() != GuiShaderBackground.Mode.Off)
            .build()
      );
   public final Setting<Boolean> bgGradient = this.sgTheme
      .add(
         new BoolSetting.Builder()
            .name("background-gradient")
            .description(
               "Fill the ClickGui background with a two-colour gradient. When the shader background is on, the shader is themed by these two colours instead of primary/secondary."
            )
            .defaultValue(false)
            .build()
      );
   public final Setting<SettingColor> bgGradientTop = this.sgTheme
      .add(
         new ColorSetting.Builder()
            .name("gradient-top")
            .description("Top colour of the background gradient.")
            .defaultValue(new SettingColor(38, 20, 48, 255))
            .visible(() -> this.bgGradient.get())
            .build()
      );
   public final Setting<SettingColor> bgGradientBottom = this.sgTheme
      .add(
         new ColorSetting.Builder()
            .name("gradient-bottom")
            .description("Bottom colour of the background gradient.")
            .defaultValue(new SettingColor(10, 8, 16, 255))
            .visible(() -> this.bgGradient.get())
            .build()
      );
   public final Setting<Integer> bgGradientOpacity = this.sgTheme
      .add(
         new IntSetting.Builder()
            .name("gradient-opacity")
            .description("Opacity of the gradient background (percent). Lower it to let the game behind the ClickGui show through the gradient.")
            .defaultValue(100)
            .min(0)
            .sliderRange(0, 100)
            .visible(() -> this.bgGradient.get())
            .build()
      );
   public final Setting<SettingColor> textColor = this.sgTheme
      .add(
         new ColorSetting.Builder()
            .name("text-color")
            .description("Primary text color (active modules, headers).")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .onChanged(c -> FontManager.get().setTextColor(packRGB(c)))
            .build()
      );
   public final Setting<SettingColor> textSecondary = this.sgTheme
      .add(
         new ColorSetting.Builder()
            .name("text-secondary")
            .description("Secondary text color (inactive modules).")
            .defaultValue(new SettingColor(235, 210, 225, 255))
            .onChanged(c -> FontManager.get().setTextSecondary(packRGB(c)))
            .build()
      );
   public final Setting<Boolean> ovPanel = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-panel-bg")
            .description("Override the panel background colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colPanel = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("panel-bg")
            .description("Custom panel background.")
            .defaultValue(new SettingColor(18, 16, 24, 220))
            .visible(() -> this.ovPanel.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovHeader = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-header-bg")
            .description("Override the panel header background colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colHeader = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("header-bg")
            .description("Custom header background.")
            .defaultValue(new SettingColor(0, 0, 0, 70))
            .visible(() -> this.ovHeader.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovHover = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-hover-bg")
            .description("Override the hovered-row background colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colHover = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("hover-bg")
            .description("Custom hovered-row background.")
            .defaultValue(new SettingColor(255, 255, 255, 28))
            .visible(() -> this.ovHover.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovActive = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-active-bg")
            .description("Override the active-module row background colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colActive = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("active-bg")
            .description("Custom active-module row background.")
            .defaultValue(new SettingColor(255, 105, 190, 30))
            .visible(() -> this.ovActive.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovStripe = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-stripe")
            .description("Override the alternating-row stripe colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colStripe = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("stripe")
            .description("Custom alternating-row stripe.")
            .defaultValue(new SettingColor(255, 105, 190, 12))
            .visible(() -> this.ovStripe.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovBorder = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-border")
            .description("Override the panel border / separator colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colBorder = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("border")
            .description("Custom panel border / separator.")
            .defaultValue(new SettingColor(255, 105, 190, 140))
            .visible(() -> this.ovBorder.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovGlow = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-glow")
            .description("Override the active-module glow colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colGlow = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("glow")
            .description("Custom active-module glow (its alpha sets the intensity).")
            .defaultValue(new SettingColor(255, 105, 190, 110))
            .visible(() -> this.ovGlow.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovScroll = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-scrollbar")
            .description("Override the scrollbar colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colScroll = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("scrollbar")
            .description("Custom scrollbar.")
            .defaultValue(new SettingColor(255, 105, 190, 110))
            .visible(() -> this.ovScroll.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovPopup = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-popup-bg")
            .description("Override the settings popup background colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colPopup = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("popup-bg")
            .description("Custom settings popup background.")
            .defaultValue(new SettingColor(20, 16, 26, 240))
            .visible(() -> this.ovPopup.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<Boolean> ovTooltip = this.sgAdv
      .add(
         new BoolSetting.Builder()
            .name("custom-tooltip-bg")
            .description("Override the description tooltip background colour.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );
   public final Setting<SettingColor> colTooltip = this.sgAdv
      .add(
         new ColorSetting.Builder()
            .name("tooltip-bg")
            .description("Custom description tooltip background.")
            .defaultValue(new SettingColor(0, 0, 0, 235))
            .visible(() -> this.ovTooltip.get() && this.style.get() != Gui.GuiStyle.Modern)
            .build()
      );

   public Gui() {
      super(Categories.Gui, "gui", "Controls the appearance of the ClickGui: which GUI opens, its layout, colours and fonts.");
      this.applyToFontManager();
   }

   private void onOpenStyleEditor(Boolean value) {
      if (value != null && value) {
         MinecraftClient mc = MinecraftClient.getInstance();
         mc.execute(() -> {
            mc.setScreen(new GuiStyleEditorScreen());
            this.openStyleEditor.set(false);
         });
      }
   }

   @Override
   public void onActivate() {
      this.applyToFontManager();
   }

   public void applyToFontManager() {
      FontManager fm = FontManager.get();
      fm.setFont(this.font.get());
      fm.setFontStyle(this.fontStyle.get());
      fm.setScale(this.textScale.get().floatValue());
      fm.setGuiScale(this.guiScale.get().floatValue());
      fm.setTextShadow(this.textShadow.get());
      fm.setPrimaryColor(packRGB(this.primary.get()));
      fm.setSecondaryColor(packRGB(this.secondary.get()));
      fm.setTextColor(packRGB(this.textColor.get()));
      fm.setTextSecondary(packRGB(this.textSecondary.get()));
      fm.setHudTextPrimary(packRGB(this.hudTextPrimary.get()));
      fm.setHudTextSecondary(packRGB(this.hudTextSecondary.get()));
      fm.setHudTextBackground(this.hudTextBackground.get());
   }

   @Override
   public Module fromTag(NbtCompound tag) {
      super.fromTag(tag);
      this.applyToFontManager();
      return this;
   }

   private static int packRGB(SettingColor c) {
      return (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
   }

   public static enum AccentBar {
      Auto,
      Glow,
      Rail,
      None;
   }

   public static enum GuiStyle {
      Classic,
      Clean,
      Modern;
   }

   public static enum ModernLayout {
      Panels,
      Centered;
   }

   public static enum Striping {
      Auto,
      On,
      Off;
   }
}
