package revivedevelopment.reviveclient.gui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import revivedevelopment.reviveclient.gui.renderer.packer.GuiTexture;
import revivedevelopment.reviveclient.gui.utils.CharFilter;
import revivedevelopment.reviveclient.gui.utils.SettingsWidgetFactory;
import revivedevelopment.reviveclient.gui.utils.WindowConfig;
import revivedevelopment.reviveclient.gui.widgets.WHorizontalSeparator;
import revivedevelopment.reviveclient.gui.widgets.WItem;
import revivedevelopment.reviveclient.gui.widgets.WItemWithLabel;
import revivedevelopment.reviveclient.gui.widgets.WKeybind;
import revivedevelopment.reviveclient.gui.widgets.WLabel;
import revivedevelopment.reviveclient.gui.widgets.WQuad;
import revivedevelopment.reviveclient.gui.widgets.WTexture;
import revivedevelopment.reviveclient.gui.widgets.WTooltip;
import revivedevelopment.reviveclient.gui.widgets.WVerticalSeparator;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.containers.WSection;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.containers.WVerticalList;
import revivedevelopment.reviveclient.gui.widgets.containers.WView;
import revivedevelopment.reviveclient.gui.widgets.containers.WWindow;
import revivedevelopment.reviveclient.gui.widgets.input.WBlockPosEdit;
import revivedevelopment.reviveclient.gui.widgets.input.WDoubleEdit;
import revivedevelopment.reviveclient.gui.widgets.input.WDropdown;
import revivedevelopment.reviveclient.gui.widgets.input.WIntEdit;
import revivedevelopment.reviveclient.gui.widgets.input.WSlider;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.gui.widgets.pressable.WCheckbox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WFavorite;
import revivedevelopment.reviveclient.gui.widgets.pressable.WMinus;
import revivedevelopment.reviveclient.gui.widgets.pressable.WPlus;
import revivedevelopment.reviveclient.gui.widgets.pressable.WTriangle;
import revivedevelopment.reviveclient.renderer.Texture;
import revivedevelopment.reviveclient.renderer.text.TextRenderer;
import revivedevelopment.reviveclient.settings.Settings;
import revivedevelopment.reviveclient.utils.misc.ISerializable;
import revivedevelopment.reviveclient.utils.misc.Keybind;
import revivedevelopment.reviveclient.utils.misc.Names;
import revivedevelopment.reviveclient.utils.render.color.Color;

public abstract class GuiTheme implements ISerializable<GuiTheme> {
   public static final double TITLE_TEXT_SCALE = 1.25;
   public final String name;
   public final Settings settings = new Settings();
   public boolean disableHoverColor;
   protected SettingsWidgetFactory settingsFactory;
   protected final Map<String, WindowConfig> windowConfigs = new HashMap<>();

   public GuiTheme(String name) {
      this.name = name;
   }

   public void beforeRender() {
      this.disableHoverColor = false;
   }

   public abstract WWindow window(WWidget var1, String var2);

   public WWindow window(String title) {
      return this.window(null, title);
   }

   public abstract WLabel label(String var1, boolean var2, double var3);

   public WLabel label(String text, boolean title) {
      return this.label(text, title, 0.0);
   }

   public WLabel label(String text, double maxWidth) {
      return this.label(text, false, maxWidth);
   }

   public WLabel label(String text) {
      return this.label(text, false);
   }

   public abstract WHorizontalSeparator horizontalSeparator(String var1);

   public WHorizontalSeparator horizontalSeparator() {
      return this.horizontalSeparator(null);
   }

   public abstract WVerticalSeparator verticalSeparator();

   protected abstract WButton button(String var1, GuiTexture var2);

   public WButton button(String text) {
      return this.button(text, null);
   }

   public WButton button(GuiTexture texture) {
      return this.button(null, texture);
   }

   public abstract WMinus minus();

   public abstract WPlus plus();

   public abstract WCheckbox checkbox(boolean var1);

   public abstract WSlider slider(double var1, double var3, double var5);

   public abstract WTextBox textBox(String var1, String var2, CharFilter var3, Class<? extends WTextBox.Renderer> var4);

   public WTextBox textBox(String text, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
      return this.textBox(text, null, filter, renderer);
   }

   public WTextBox textBox(String text, String placeholder, CharFilter filter) {
      return this.textBox(text, placeholder, filter, null);
   }

   public WTextBox textBox(String text, CharFilter filter) {
      return this.textBox(text, filter, null);
   }

   public WTextBox textBox(String text, String placeholder) {
      return this.textBox(text, placeholder, (text1, c) -> true, null);
   }

   public WTextBox textBox(String text) {
      return this.textBox(text, (text1, c) -> true, null);
   }

   public abstract <T> WDropdown<T> dropdown(T[] var1, T var2);

   public <T extends Enum<?>> WDropdown<T> dropdown(T value) {
      Class<?> klass = value.getDeclaringClass();
      T[] values = (T[])klass.getEnumConstants();
      return this.dropdown(values, value);
   }

   public abstract WTriangle triangle();

   public abstract WTooltip tooltip(String var1);

   public abstract WView view();

   public WVerticalList verticalList() {
      return this.w(new WVerticalList());
   }

   public WHorizontalList horizontalList() {
      return this.w(new WHorizontalList());
   }

   public WTable table() {
      return this.w(new WTable());
   }

   public abstract WSection section(String var1, boolean var2, WWidget var3);

   public WSection section(String title, boolean expanded) {
      return this.section(title, expanded, null);
   }

   public WSection section(String title) {
      return this.section(title, true);
   }

   public abstract WQuad quad(Color var1);

   public abstract WFavorite favorite(boolean var1);

   public WItem item(ItemStack itemStack) {
      return this.w(new WItem(itemStack));
   }

   public WItemWithLabel itemWithLabel(ItemStack stack, String name) {
      return this.w(new WItemWithLabel(stack, name));
   }

   public WItemWithLabel itemWithLabel(ItemStack stack) {
      return this.itemWithLabel(stack, Names.get(stack.getItem()));
   }

   public WTexture texture(double width, double height, double rotation, Texture texture) {
      return this.w(new WTexture(width, height, rotation, texture));
   }

   public WIntEdit intEdit(int value, int min, int max, int sliderMin, int sliderMax, boolean noSlider) {
      return this.w(new WIntEdit(value, min, max, sliderMin, sliderMax, noSlider));
   }

   public WIntEdit intEdit(int value, int min, int max, int sliderMin, int sliderMax) {
      return this.w(new WIntEdit(value, min, max, sliderMin, sliderMax, false));
   }

   public WIntEdit intEdit(int value, int min, int max, boolean noSlider) {
      return this.w(new WIntEdit(value, min, max, 0, 0, noSlider));
   }

   public WDoubleEdit doubleEdit(double value, double min, double max, double sliderMin, double sliderMax, int decimalPlaces, boolean noSlider) {
      return this.w(new WDoubleEdit(value, min, max, sliderMin, sliderMax, decimalPlaces, noSlider));
   }

   public WDoubleEdit doubleEdit(double value, double min, double max, double sliderMin, double sliderMax) {
      return this.w(new WDoubleEdit(value, min, max, sliderMin, sliderMax, 3, false));
   }

   public WDoubleEdit doubleEdit(double value, double min, double max) {
      return this.w(new WDoubleEdit(value, min, max, 0.0, 10.0, 3, false));
   }

   public WBlockPosEdit blockPosEdit(BlockPos value) {
      return this.w(new WBlockPosEdit(value));
   }

   public WKeybind keybind(Keybind keybind) {
      return this.keybind(keybind, Keybind.none());
   }

   public WKeybind keybind(Keybind keybind, Keybind defaultValue) {
      return this.w(new WKeybind(keybind, defaultValue));
   }

   public WWidget settings(Settings settings, String filter) {
      return this.settingsFactory.create(this, settings, filter);
   }

   public WWidget settings(Settings settings) {
      return this.settings(settings, "");
   }

   public abstract Color textColor();

   public abstract Color textSecondaryColor();

   public abstract Color starscriptTextColor();

   public abstract Color starscriptBraceColor();

   public abstract Color starscriptParenthesisColor();

   public abstract Color starscriptDotColor();

   public abstract Color starscriptCommaColor();

   public abstract Color starscriptOperatorColor();

   public abstract Color starscriptStringColor();

   public abstract Color starscriptNumberColor();

   public abstract Color starscriptKeywordColor();

   public abstract Color starscriptAccessedObjectColor();

   public abstract TextRenderer textRenderer();

   public abstract double scale(double var1);

   public abstract boolean categoryIcons();

   public abstract boolean hideHUD();

   public double textWidth(String text, int length, boolean title) {
      return this.scale(this.textRenderer().getWidth(text, length, false) * (title ? 1.25 : 1.0));
   }

   public double textWidth(String text) {
      return this.textWidth(text, text.length(), false);
   }

   public double textHeight(boolean title) {
      return this.scale(this.textRenderer().getHeight() * (title ? 1.25 : 1.0));
   }

   public double textHeight() {
      return this.textHeight(false);
   }

   public double pad() {
      return this.scale(6.0);
   }

   public WindowConfig getWindowConfig(String id) {
      WindowConfig config = this.windowConfigs.get(id);
      if (config != null) {
         return config;
      } else {
         config = new WindowConfig();
         this.windowConfigs.put(id, config);
         return config;
      }
   }

   public void clearWindowConfigs() {
      this.windowConfigs.clear();
   }

   protected <T extends WWidget> T w(T widget) {
      widget.theme = this;
      return widget;
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("name", this.name);
      tag.put("settings", this.settings.toTag());
      NbtCompound configs = new NbtCompound();

      for (String id : this.windowConfigs.keySet()) {
         configs.put(id, this.windowConfigs.get(id).toTag());
      }

      tag.put("windowConfigs", configs);
      return tag;
   }

   public GuiTheme fromTag(NbtCompound tag) {
      tag.getCompound("settings").ifPresent(this.settings::fromTag);
      tag.getCompound("windowConfigs").ifPresent(configs -> {
         for (String id : configs.getKeys()) {
            this.windowConfigs.put(id, new WindowConfig().fromTag((NbtCompound)configs.getCompound(id).get()));
         }
      });
      return this;
   }
}
