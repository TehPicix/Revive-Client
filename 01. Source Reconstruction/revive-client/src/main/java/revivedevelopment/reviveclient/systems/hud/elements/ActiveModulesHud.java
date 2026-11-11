package revivedevelopment.reviveclient.systems.hud.elements;

import java.util.ArrayList;
import java.util.List;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ModuleListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Alignment;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class ActiveModulesHud extends HudElement {
   public static final HudElementInfo<ActiveModulesHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "active-modules", "Displays your active modules.", ActiveModulesHud::new
   );
   private static final Color WHITE = new Color();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<Module>> shownModules = this.sgGeneral
      .add(
         new ModuleListSetting.Builder()
            .name("visible-modules")
            .description("Which modules to show in the list. Leave empty to show all active modules.")
            .build()
      );
   private final Setting<ActiveModulesHud.Sort> sort = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("sort")).description("How to sort active modules."))
               .defaultValue(ActiveModulesHud.Sort.Biggest))
            .build()
      );
   private final Setting<Boolean> activeInfo = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("additional-info")
            .description("Shows additional info from the module next to the name in the active modules list.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(new BoolSetting.Builder().name("shadow").description("Renders shadow behind text.").defaultValue(true).build());
   private final Setting<Alignment> alignment = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("alignment")).description("Horizontal alignment."))
               .defaultValue(Alignment.Auto))
            .build()
      );
   private final Setting<Boolean> background = this.sgGeneral
      .add(new BoolSetting.Builder().name("background").description("Draws a new-UI rounded panel behind the list.").defaultValue(true).build());
   private final Setting<Boolean> outlines = this.sgGeneral
      .add(new BoolSetting.Builder().name("outlines").description("Whether or not to render outlines").defaultValue(false).build());
   private final Setting<Integer> outlineWidth = this.sgGeneral
      .add(new IntSetting.Builder().name("outline-width").description("Outline width").defaultValue(2).min(1).sliderMin(1).visible(this.outlines::get).build());
   private final Setting<Boolean> customScale = this.sgGeneral
      .add(new BoolSetting.Builder().name("custom-scale").description("Applies custom text scale rather than the global one.").defaultValue(false).build());
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Custom scale.")
            .visible(this.customScale::get)
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   private final List<Module> modules = new ArrayList<>();
   private double prevX;
   private double prevTextLength;
   private Color prevColor = new Color();

   public ActiveModulesHud() {
      super(INFO);
   }

   @Override
   public void tick(HudRenderer renderer) {
      this.modules.clear();
      List<Module> shown = this.shownModules.get();

      for (Module module : Modules.get().getActive()) {
         if (shown.isEmpty() || shown.contains(module)) {
            this.modules.add(module);
         }
      }

      if (this.modules.isEmpty()) {
         if (this.isInEditor()) {
            this.setSize(renderer.textWidth("Active Modules", this.shadow.get(), this.getScale()), renderer.textHeight(this.shadow.get(), this.getScale()));
         }
      } else {
         this.modules.sort((e1, e2) -> {
            return switch ((ActiveModulesHud.Sort)this.sort.get()) {
               case Alphabetical -> e1.title.compareTo(e2.title);
               case Biggest -> Double.compare(this.getModuleWidth(renderer, e2), this.getModuleWidth(renderer, e1));
               case Smallest -> Double.compare(this.getModuleWidth(renderer, e1), this.getModuleWidth(renderer, e2));
            };
         });
         double width = 0.0;
         double height = 0.0;

         for (int i = 0; i < this.modules.size(); i++) {
            Module modulex = this.modules.get(i);
            width = Math.max(width, this.getModuleWidth(renderer, modulex));
            height += renderer.textHeight(this.shadow.get(), this.getScale());
            if (i > 0) {
               height += 2.0;
            }
         }

         this.setSize(width, height);
      }
   }

   @Override
   public void render(HudRenderer renderer) {
      double x = this.x;
      double y = this.y;
      if (this.modules.isEmpty()) {
         if (this.isInEditor()) {
            renderer.text("Active Modules", x, y, WHITE, this.shadow.get(), this.getScale());
         }
      } else {
         if (this.background.get()) {
            double padX = 3.0;
            double padY = 2.0;
            renderer.panel(x - padX, y - padY, this.getWidth() + padX * 2.0, this.getHeight() + padY * 2.0, HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
         }

         this.prevX = x;

         for (int i = 0; i < this.modules.size(); i++) {
            double offset = this.alignX(this.getModuleWidth(renderer, this.modules.get(i)), this.alignment.get());
            this.renderModule(renderer, this.modules, i, x + offset, y);
            this.prevX = x + offset;
            y += 2.0 + renderer.textHeight(this.shadow.get(), this.getScale());
         }
      }
   }

   private void renderModule(HudRenderer renderer, List<Module> modules, int index, double x, double y) {
      Module module = modules.get(index);
      Color color = HudStyle.TEXT_PRIMARY;
      renderer.text(module.title, x, y, color, this.shadow.get(), this.getScale());
      double emptySpace = renderer.textWidth(" ", this.shadow.get(), this.getScale());
      double textHeight = renderer.textHeight(this.shadow.get(), this.getScale());
      double textLength = renderer.textWidth(module.title, this.shadow.get(), this.getScale());
      if (this.activeInfo.get()) {
         String info = module.getDisplayInfo();
         if (info != null) {
            renderer.text(info, x + emptySpace + textLength, y, HudStyle.TEXT_SECONDARY, this.shadow.get(), this.getScale());
            textLength += emptySpace + renderer.textWidth(info, this.shadow.get(), this.getScale());
         }
      }

      if (this.outlines.get()) {
         if (index == 0) {
            renderer.quad(
               x - 2.0 - this.outlineWidth.get().intValue(),
               y - 2.0,
               this.outlineWidth.get().intValue(),
               textHeight + 4.0,
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            renderer.quad(x + textLength + 2.0, y - 2.0, this.outlineWidth.get().intValue(), textHeight + 4.0, this.prevColor, this.prevColor, color, color);
            renderer.quad(
               x - 2.0 - this.outlineWidth.get().intValue(),
               y - 2.0 - this.outlineWidth.get().intValue(),
               textLength + 4.0 + this.outlineWidth.get() * 2,
               this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            if (index == modules.size() - 1) {
               renderer.quad(
                  x - 2.0 - this.outlineWidth.get().intValue(),
                  y + textHeight + 2.0,
                  textLength + 4.0 + this.outlineWidth.get() * 2,
                  this.outlineWidth.get().intValue(),
                  this.prevColor,
                  this.prevColor,
                  color,
                  color
               );
            }
         } else if (index == modules.size() - 1) {
            renderer.quad(
               x - 2.0 - this.outlineWidth.get().intValue(),
               y,
               this.outlineWidth.get().intValue(),
               textHeight + 2.0 + this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            renderer.quad(
               x + textLength + 2.0,
               y,
               this.outlineWidth.get().intValue(),
               textHeight + 2.0 + this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            renderer.quad(
               x - 2.0 - this.outlineWidth.get().intValue(),
               y + textHeight + 2.0,
               textLength + 4.0 + this.outlineWidth.get() * 2,
               this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
         }

         if (index > 0) {
            if (index < modules.size() - 1) {
               renderer.quad(
                  x - 2.0 - this.outlineWidth.get().intValue(),
                  y,
                  this.outlineWidth.get().intValue(),
                  textHeight + 2.0,
                  this.prevColor,
                  this.prevColor,
                  color,
                  color
               );
               renderer.quad(x + textLength + 2.0, y, this.outlineWidth.get().intValue(), textHeight + 2.0, this.prevColor, this.prevColor, color, color);
            }

            renderer.quad(
               Math.min(this.prevX, x) - 2.0 - this.outlineWidth.get().intValue(),
               Math.max(this.prevX, x) == x ? y : y - this.outlineWidth.get().intValue(),
               Math.max(this.prevX, x) - 2.0 - (Math.min(this.prevX, x) - 2.0 - this.outlineWidth.get().intValue()),
               this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
            renderer.quad(
               Math.min(this.prevX + this.prevTextLength, x + textLength) + 2.0,
               Math.min(this.prevX + this.prevTextLength, x + textLength) == x + textLength ? y : y - this.outlineWidth.get().intValue(),
               Math.max(this.prevX + this.prevTextLength, x + textLength)
                  + 2.0
                  + this.outlineWidth.get().intValue()
                  - (Math.min(this.prevX + this.prevTextLength, x + textLength) + 2.0),
               this.outlineWidth.get().intValue(),
               this.prevColor,
               this.prevColor,
               color,
               color
            );
         }
      }

      this.prevTextLength = textLength;
      this.prevColor = color;
   }

   private double getModuleWidth(HudRenderer renderer, Module module) {
      double width = renderer.textWidth(module.title, this.shadow.get(), this.getScale());
      if (this.activeInfo.get()) {
         String info = module.getDisplayInfo();
         if (info != null) {
            width += renderer.textWidth(" ", this.shadow.get(), this.getScale()) + renderer.textWidth(info, this.shadow.get(), this.getScale());
         }
      }

      return width;
   }

   private double getScale() {
      return this.customScale.get() ? this.scale.get() : -1.0;
   }

   public static enum Sort {
      Alphabetical,
      Biggest,
      Smallest;
   }
}
