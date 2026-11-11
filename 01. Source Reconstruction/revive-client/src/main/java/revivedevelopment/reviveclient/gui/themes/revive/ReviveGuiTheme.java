package revivedevelopment.reviveclient.gui.themes.revive;

import net.minecraft.client.util.MacWindowUtil;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.DefaultSettingsWidgetFactory;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WidgetScreen;
import revivedevelopment.reviveclient.gui.renderer.packer.GuiTexture;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveHorizontalSeparator;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveLabel;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveMultiLabel;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveQuad;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveSection;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveTooltip;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveVerticalSeparator;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveView;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveWindow;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.input.WReviveDropdown;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.input.WReviveSlider;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.input.WReviveTextBox;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable.WReviveButton;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable.WReviveCheckbox;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable.WReviveFavorite;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable.WReviveMinus;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable.WRevivePlus;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.pressable.WReviveTriangle;
import revivedevelopment.reviveclient.gui.utils.AlignmentX;
import revivedevelopment.reviveclient.gui.utils.CharFilter;
import revivedevelopment.reviveclient.gui.widgets.WHorizontalSeparator;
import revivedevelopment.reviveclient.gui.widgets.WLabel;
import revivedevelopment.reviveclient.gui.widgets.WQuad;
import revivedevelopment.reviveclient.gui.widgets.WTooltip;
import revivedevelopment.reviveclient.gui.widgets.WVerticalSeparator;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WSection;
import revivedevelopment.reviveclient.gui.widgets.containers.WView;
import revivedevelopment.reviveclient.gui.widgets.containers.WWindow;
import revivedevelopment.reviveclient.gui.widgets.input.WDropdown;
import revivedevelopment.reviveclient.gui.widgets.input.WSlider;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.gui.widgets.pressable.WCheckbox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WFavorite;
import revivedevelopment.reviveclient.gui.widgets.pressable.WMinus;
import revivedevelopment.reviveclient.gui.widgets.pressable.WPlus;
import revivedevelopment.reviveclient.gui.widgets.pressable.WTriangle;
import revivedevelopment.reviveclient.renderer.text.TextRenderer;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ReviveGuiTheme extends GuiTheme {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final SettingGroup sgTextColors = this.settings.createGroup("Text");
   private final SettingGroup sgBackgroundColors = this.settings.createGroup("Background");
   private final SettingGroup sgOutline = this.settings.createGroup("Outline");
   private final SettingGroup sgSeparator = this.settings.createGroup("Separator");
   private final SettingGroup sgScrollbar = this.settings.createGroup("Scrollbar");
   private final SettingGroup sgSlider = this.settings.createGroup("Slider");
   private final SettingGroup sgStarscript = this.settings.createGroup("Starscript");
   public final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Scale of the GUI.")
            .defaultValue(1.0)
            .min(0.75)
            .sliderRange(0.75, 4.0)
            .onSliderRelease()
            .onChanged(v -> {
               if (ReviveClient.mc.currentScreen instanceof WidgetScreen) {
                  ((WidgetScreen)ReviveClient.mc.currentScreen).invalidate();
               }
            })
            .build()
      );
   public final Setting<AlignmentX> moduleAlignment = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("module-alignment"))
                  .description("How module titles are aligned."))
               .defaultValue(AlignmentX.Center))
            .build()
      );
   public final Setting<Boolean> categoryIcons = this.sgGeneral
      .add(new BoolSetting.Builder().name("category-icons").description("Adds item icons to module categories.").defaultValue(false).build());
   public final Setting<Boolean> hideHUD = this.sgGeneral
      .add(new BoolSetting.Builder().name("hide-HUD").description("Hide HUD when in GUI.").defaultValue(false).onChanged(v -> {
         if (ReviveClient.mc.currentScreen instanceof WidgetScreen) {
            ReviveClient.mc.options.hudHidden = v;
         }
      }).build());
   public final Setting<SettingColor> accentColor = this.color("accent", "Main accent color.", new SettingColor(0, 150, 255));
   public final Setting<SettingColor> checkboxColor = this.color("checkbox", "Color of checkbox.", new SettingColor(0, 150, 255));
   public final Setting<SettingColor> plusColor = this.color("plus", "Color of plus button.", new SettingColor(40, 220, 120));
   public final Setting<SettingColor> minusColor = this.color("minus", "Color of minus button.", new SettingColor(220, 60, 60));
   public final Setting<SettingColor> favoriteColor = this.color("favorite", "Color of favorite button.", new SettingColor(255, 200, 0));
   public final Setting<SettingColor> textColor = this.color(this.sgTextColors, "text", "Color of text.", new SettingColor(220, 220, 220));
   public final Setting<SettingColor> textSecondaryColor = this.color(
      this.sgTextColors, "text-secondary", "Color of secondary text.", new SettingColor(120, 120, 130)
   );
   public final Setting<SettingColor> textHighlightColor = this.color(
      this.sgTextColors, "text-highlight", "Color of highlight.", new SettingColor(0, 130, 255, 80)
   );
   public final Setting<SettingColor> titleTextColor = this.color(this.sgTextColors, "title-text", "Color of title text.", new SettingColor(255, 255, 255));
   public final Setting<SettingColor> loggedInColor = this.color(this.sgTextColors, "logged-in-text", "Logged-in account name.", new SettingColor(40, 220, 120));
   public final Setting<SettingColor> placeholderColor = this.color(
      this.sgTextColors, "placeholder", "Placeholder text color.", new SettingColor(255, 255, 255, 25)
   );
   public final ReviveGuiTheme.ThreeStateColorSetting backgroundColor = new ReviveGuiTheme.ThreeStateColorSetting(
      this.sgBackgroundColors, "background", new SettingColor(12, 12, 18, 230), new SettingColor(20, 20, 30, 230), new SettingColor(28, 28, 42, 230)
   );
   public final Setting<SettingColor> moduleBackground = this.color(
      this.sgBackgroundColors, "module-background", "Module bg when active.", new SettingColor(0, 100, 200, 60)
   );
   public final ReviveGuiTheme.ThreeStateColorSetting outlineColor = new ReviveGuiTheme.ThreeStateColorSetting(
      this.sgOutline, "outline", new SettingColor(35, 35, 55, 200), new SettingColor(0, 130, 255, 160), new SettingColor(0, 160, 255, 220)
   );
   public final Setting<SettingColor> separatorText = this.color(this.sgSeparator, "separator-text", "Separator text.", new SettingColor(180, 180, 200));
   public final Setting<SettingColor> separatorCenter = this.color(this.sgSeparator, "separator-center", "Separator line.", new SettingColor(0, 130, 255, 200));
   public final Setting<SettingColor> separatorEdges = this.color(this.sgSeparator, "separator-edges", "Separator edges.", new SettingColor(0, 80, 160, 80));
   public final ReviveGuiTheme.ThreeStateColorSetting scrollbarColor = new ReviveGuiTheme.ThreeStateColorSetting(
      this.sgScrollbar, "scrollbar", new SettingColor(0, 100, 200, 160), new SettingColor(0, 130, 255, 200), new SettingColor(0, 160, 255, 240)
   );
   public final ReviveGuiTheme.ThreeStateColorSetting sliderHandle = new ReviveGuiTheme.ThreeStateColorSetting(
      this.sgSlider, "slider-handle", new SettingColor(0, 150, 255), new SettingColor(60, 180, 255), new SettingColor(100, 210, 255)
   );
   public final Setting<SettingColor> sliderLeft = this.color(this.sgSlider, "slider-left", "Filled part of slider.", new SettingColor(0, 130, 255, 200));
   public final Setting<SettingColor> sliderRight = this.color(this.sgSlider, "slider-right", "Unfilled part of slider.", new SettingColor(35, 35, 55, 180));
   public final Setting<SettingColor> starscriptText = this.color(this.sgStarscript, "starscript-text", "Text.", new SettingColor(220, 220, 220));
   public final Setting<SettingColor> starscriptBraces = this.color(this.sgStarscript, "starscript-braces", "Braces.", new SettingColor(0, 150, 255));
   public final Setting<SettingColor> starscriptParenthesis = this.color(
      this.sgStarscript, "starscript-parenthesis", "Parenthesis.", new SettingColor(0, 150, 255)
   );
   public final Setting<SettingColor> starscriptDots = this.color(this.sgStarscript, "starscript-dots", "Dots.", new SettingColor(180, 180, 200));
   public final Setting<SettingColor> starscriptCommas = this.color(this.sgStarscript, "starscript-commas", "Commas.", new SettingColor(180, 180, 200));
   public final Setting<SettingColor> starscriptOperators = this.color(this.sgStarscript, "starscript-operators", "Operators.", new SettingColor(0, 200, 255));
   public final Setting<SettingColor> starscriptStrings = this.color(this.sgStarscript, "starscript-strings", "Strings.", new SettingColor(100, 220, 100));
   public final Setting<SettingColor> starscriptNumbers = this.color(this.sgStarscript, "starscript-numbers", "Numbers.", new SettingColor(255, 160, 60));
   public final Setting<SettingColor> starscriptKeywords = this.color(this.sgStarscript, "starscript-keywords", "Keywords.", new SettingColor(0, 150, 255));
   public final Setting<SettingColor> starscriptAccessedObjects = this.color(
      this.sgStarscript, "starscript-accessed-objects", "Accessed objects.", new SettingColor(0, 200, 255)
   );

   public ReviveGuiTheme() {
      super("Revive");
      this.settingsFactory = new DefaultSettingsWidgetFactory(this);
   }

   private Setting<SettingColor> color(SettingGroup group, String name, String description, SettingColor color) {
      return group.add(new ColorSetting.Builder().name(name + "-color").description(description).defaultValue(color).build());
   }

   private Setting<SettingColor> color(String name, String description, SettingColor color) {
      return this.color(this.sgColors, name, description, color);
   }

   @Override
   public WWindow window(WWidget icon, String title) {
      return this.w(new WReviveWindow(icon, title));
   }

   @Override
   public WLabel label(String text, boolean title, double maxWidth) {
      return maxWidth == 0.0 ? this.w(new WReviveLabel(text, title)) : this.w(new WReviveMultiLabel(text, title, maxWidth));
   }

   @Override
   public WHorizontalSeparator horizontalSeparator(String text) {
      return this.w(new WReviveHorizontalSeparator(text));
   }

   @Override
   public WVerticalSeparator verticalSeparator() {
      return this.w(new WReviveVerticalSeparator());
   }

   @Override
   protected WButton button(String text, GuiTexture texture) {
      return this.w(new WReviveButton(text, texture));
   }

   @Override
   public WMinus minus() {
      return this.w(new WReviveMinus());
   }

   @Override
   public WPlus plus() {
      return this.w(new WRevivePlus());
   }

   @Override
   public WCheckbox checkbox(boolean checked) {
      return this.w(new WReviveCheckbox(checked));
   }

   @Override
   public WSlider slider(double value, double min, double max) {
      return this.w(new WReviveSlider(value, min, max));
   }

   @Override
   public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
      return this.w(new WReviveTextBox(text, placeholder, filter, renderer));
   }

   @Override
   public <T> WDropdown<T> dropdown(T[] values, T value) {
      return this.w(new WReviveDropdown<>(values, value));
   }

   @Override
   public WTriangle triangle() {
      return this.w(new WReviveTriangle());
   }

   @Override
   public WTooltip tooltip(String text) {
      return this.w(new WReviveTooltip(text));
   }

   @Override
   public WView view() {
      return this.w(new WReviveView());
   }

   @Override
   public WSection section(String title, boolean expanded, WWidget headerWidget) {
      return this.w(new WReviveSection(title, expanded, headerWidget));
   }

   @Override
   public WQuad quad(Color color) {
      return this.w(new WReviveQuad(color));
   }

   @Override
   public WFavorite favorite(boolean checked) {
      return this.w(new WReviveFavorite(checked));
   }

   @Override
   public Color textColor() {
      return this.textColor.get();
   }

   @Override
   public Color textSecondaryColor() {
      return this.textSecondaryColor.get();
   }

   @Override
   public Color starscriptTextColor() {
      return this.starscriptText.get();
   }

   @Override
   public Color starscriptBraceColor() {
      return this.starscriptBraces.get();
   }

   @Override
   public Color starscriptParenthesisColor() {
      return this.starscriptParenthesis.get();
   }

   @Override
   public Color starscriptDotColor() {
      return this.starscriptDots.get();
   }

   @Override
   public Color starscriptCommaColor() {
      return this.starscriptCommas.get();
   }

   @Override
   public Color starscriptOperatorColor() {
      return this.starscriptOperators.get();
   }

   @Override
   public Color starscriptStringColor() {
      return this.starscriptStrings.get();
   }

   @Override
   public Color starscriptNumberColor() {
      return this.starscriptNumbers.get();
   }

   @Override
   public Color starscriptKeywordColor() {
      return this.starscriptKeywords.get();
   }

   @Override
   public Color starscriptAccessedObjectColor() {
      return this.starscriptAccessedObjects.get();
   }

   @Override
   public TextRenderer textRenderer() {
      return TextRenderer.get();
   }

   @Override
   public double scale(double value) {
      double scaled = value * this.scale.get();
      if (MacWindowUtil.IS_MAC) {
         scaled /= (double)ReviveClient.mc.getWindow().getWidth() / ReviveClient.mc.getWindow().getFramebufferWidth();
      }

      return scaled;
   }

   @Override
   public boolean categoryIcons() {
      return this.categoryIcons.get();
   }

   @Override
   public boolean hideHUD() {
      return this.hideHUD.get();
   }

   public class ThreeStateColorSetting {
      private final Setting<SettingColor> normal;
      private final Setting<SettingColor> hovered;
      private final Setting<SettingColor> pressed;

      public ThreeStateColorSetting(SettingGroup group, String name, SettingColor c1, SettingColor c2, SettingColor c3) {
         this.normal = ReviveGuiTheme.this.color(group, name, "Color of " + name + ".", c1);
         this.hovered = ReviveGuiTheme.this.color(group, "hovered-" + name, "Color of " + name + " when hovered.", c2);
         this.pressed = ReviveGuiTheme.this.color(group, "pressed-" + name, "Color of " + name + " when pressed.", c3);
      }

      public SettingColor get() {
         return this.normal.get();
      }

      public SettingColor get(boolean pressed, boolean hovered, boolean bypassDisableHoverColor) {
         if (pressed) {
            return this.pressed.get();
         } else {
            return !hovered || !bypassDisableHoverColor && ReviveGuiTheme.this.disableHoverColor ? this.normal.get() : this.hovered.get();
         }
      }

      public SettingColor get(boolean pressed, boolean hovered) {
         return this.get(pressed, hovered, false);
      }
   }
}
