package revivedevelopment.reviveclient.systems.hud.elements;

import java.util.List;
import meteordevelopment.starscript.Script;
import meteordevelopment.starscript.Section;
import meteordevelopment.starscript.compiler.Compiler;
import meteordevelopment.starscript.compiler.Parser;
import meteordevelopment.starscript.compiler.Parser.Result;
import meteordevelopment.starscript.utils.Error;
import meteordevelopment.starscript.utils.StarscriptError;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.newgui.FontManager;
import revivedevelopment.reviveclient.gui.utils.StarscriptTextBoxRenderer;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.misc.ReviveStarscript;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class TextHud extends HudElement {
   private static final Color WHITE = new Color();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgShown = this.settings.createGroup("Shown");
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private double originalWidth;
   private double originalHeight;
   private boolean needsCompile;
   private boolean recalculateSize;
   private int timer;
   public final Setting<String> text = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("text")
            .description("Text to display with Starscript.")
            .defaultValue(ReviveClient.NAME)
            .onChanged(s -> this.recompile())
            .wide()
            .renderer(StarscriptTextBoxRenderer.class)
            .build()
      );
   public final Setting<Integer> updateDelay = this.sgGeneral
      .add(new IntSetting.Builder().name("update-delay").description("Update delay in ticks").defaultValue(4).onChanged(integer -> {
         if (this.timer > integer) {
            this.timer = integer;
         }
      }).min(0).build());
   public final Setting<Boolean> shadow = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("shadow")
            .description("Renders shadow behind text.")
            .defaultValue(true)
            .onChanged(aBoolean -> this.recalculateSize = true)
            .build()
      );
   public final Setting<Integer> border = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("border")
            .description("How much space to add around the text.")
            .defaultValue(0)
            .onChanged(integer -> super.setSize(this.originalWidth + integer * 2, this.originalHeight + integer * 2))
            .build()
      );
   public final Setting<TextHud.Shown> shown = this.sgShown
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shown"))
                     .description("When this text element is shown."))
                  .defaultValue(TextHud.Shown.Always))
               .onChanged(s -> this.recompile()))
            .build()
      );
   public final Setting<String> condition = this.sgShown
      .add(
         new StringSetting.Builder()
            .name("condition")
            .description("Condition to check when shown is not Always.")
            .visible(() -> this.shown.get() != TextHud.Shown.Always)
            .onChanged(s -> this.recompile())
            .renderer(StarscriptTextBoxRenderer.class)
            .build()
      );
   public final Setting<Boolean> customScale = this.sgScale
      .add(
         new BoolSetting.Builder()
            .name("custom-scale")
            .description("Applies custom text scale rather than the global one.")
            .defaultValue(false)
            .onChanged(integer -> this.recalculateSize = true)
            .build()
      );
   public final Setting<Double> scale = this.sgScale
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Custom scale.")
            .visible(this.customScale::get)
            .defaultValue(1.0)
            .onChanged(integer -> this.recalculateSize = true)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   public final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());
   private Script script;
   private Script conditionScript;
   private Section section;
   private boolean firstTick = true;
   private boolean empty = false;
   private boolean visible;

   public TextHud(HudElementInfo<TextHud> info) {
      super(info);
      this.needsCompile = true;
   }

   private void recompile() {
      this.firstTick = true;
      this.needsCompile = true;
   }

   @Override
   public void setSize(double width, double height) {
      this.originalWidth = width;
      this.originalHeight = height;
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   private void calculateSize(HudRenderer renderer) {
      double width = 0.0;
      if (this.section != null) {
         String str = this.section.toString();
         if (!str.isBlank()) {
            width = renderer.textWidth(str, this.shadow.get(), this.getScale());
         }
      }

      if (width != 0.0) {
         this.setSize(width, renderer.textHeight(this.shadow.get(), this.getScale()));
         this.empty = false;
      } else {
         this.setSize(100.0, renderer.textHeight(this.shadow.get(), this.getScale()));
         this.empty = true;
      }
   }

   @Override
   public void tick(HudRenderer renderer) {
      if (this.recalculateSize) {
         this.calculateSize(renderer);
         this.recalculateSize = false;
      }

      if (this.timer <= 0) {
         this.runTick(renderer);
         this.timer = this.updateDelay.get();
      } else {
         this.timer--;
      }
   }

   private void runTick(HudRenderer renderer) {
      if (this.needsCompile) {
         Result result = Parser.parse(this.text.get());
         if (result.hasErrors()) {
            this.script = null;
            this.section = new Section(0, ((Error)result.errors.getFirst()).toString());
            this.calculateSize(renderer);
         } else {
            this.script = Compiler.compile(result);
         }

         if (this.shown.get() != TextHud.Shown.Always) {
            Result conditionResult = Parser.parse(this.condition.get());
            if (conditionResult.hasErrors()) {
               this.conditionScript = null;
               this.script = null;
               this.section = new Section(0, ((Error)conditionResult.errors.getFirst()).toString());
               this.calculateSize(renderer);
            } else {
               this.conditionScript = Compiler.compile(conditionResult);
            }
         }

         this.needsCompile = false;
      }

      try {
         if (this.script != null) {
            this.section = ReviveStarscript.ss.run(this.script);
            this.calculateSize(renderer);
         }
      } catch (StarscriptError var4) {
         this.section = new Section(0, var4.getMessage());
         this.calculateSize(renderer);
      }

      if (this.shown.get() != TextHud.Shown.Always) {
         if (this.conditionScript == null) {
            this.visible = true;
         } else {
            String text = ReviveStarscript.run(this.conditionScript);
            if (text == null) {
               this.visible = false;
            } else {
               this.visible = this.shown.get() == TextHud.Shown.WhenTrue ? text.equalsIgnoreCase("true") : text.equalsIgnoreCase("false");
            }
         }
      }

      this.firstTick = false;
   }

   @Override
   public void render(HudRenderer renderer) {
      if (this.firstTick) {
         this.runTick(renderer);
      }

      boolean visible = this.shown.get() == TextHud.Shown.Always || this.visible;
      if ((this.empty || !visible) && this.isInEditor()) {
         renderer.line(this.x, this.y, this.x + this.getWidth(), this.y + this.getHeight(), Color.GRAY);
         renderer.line(this.x, this.y + this.getHeight(), this.x + this.getWidth(), this.y, Color.GRAY);
      }

      if (this.section != null && visible) {
         double x = this.x + this.border.get();

         for (Section s = this.section; s != null; s = s.next) {
            x = renderer.text(s.text, x, this.y + this.border.get(), getSectionColor(s.index), this.shadow.get(), this.getScale());
         }

         if (this.background.get() && FontManager.get().isHudTextBackground()) {
            renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
         }
      }
   }

   @Override
   public void onFontChanged() {
      this.recalculateSize = true;
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : -1.0;
   }

   public static Color getSectionColor(int i) {
      List<SettingColor> colors = Hud.get().getTextColors();
      return i >= 0 && i < colors.size() ? colors.get(i) : WHITE;
   }

   public static enum Shown {
      Always,
      WhenTrue,
      WhenFalse;

      @Override
      public String toString() {
         return switch (this) {
            case Always -> "Always";
            case WhenTrue -> "When True";
            case WhenFalse -> "When False";
         };
      }
   }
}
