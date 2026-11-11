package revivedevelopment.reviveclient.systems.modules.gui;

import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.gui.newgui.screens.NewHudEditorScreen;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;

public class HudModule extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSharing = this.settings.createGroup("Sharing");
   private final SettingGroup sgEditor = this.settings.createGroup("Editor");
   private final Setting<Boolean> openEditor = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("open-editor")
            .description("Toggle to open the HUD editor (resets itself).")
            .defaultValue(false)
            .onChanged(this::onOpenEditorChanged)
            .build()
      );
   private final Setting<Boolean> customFont = this.sgGeneral
      .add(new BoolSetting.Builder().name("custom-font").description("Text will use the custom font.").defaultValue(true).onChanged(b -> {
         for (HudElement e : Hud.get()) {
            e.onFontChanged();
         }
      }).build());
   private final Setting<Boolean> hideInMenus = this.sgGeneral
      .add(new BoolSetting.Builder().name("hide-in-menus").description("Hides the HUD when in inventory screens or game menus.").defaultValue(false).build());
   private final Setting<Double> textScale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("text-scale")
            .description("Scale of text if not overridden by the element.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   private final Setting<Boolean> autoScale = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("auto-scale")
            .description(
               "Scales the HUD up on high-resolution / fullscreen displays so it stays readable, like the vanilla hotbar. 1080p is the baseline (no change); larger resolutions scale up proportionally."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> hudScale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("hud-scale")
            .description("Manual HUD size multiplier, applied on top of auto-scale.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 3.0)
            .build()
      );
   private final Setting<Boolean> exportHud = this.sgSharing
      .add(
         new BoolSetting.Builder()
            .name("export-to-clipboard")
            .description("Copies the entire HUD layout to your clipboard as a shareable string.")
            .defaultValue(false)
            .onChanged(this::onExportChanged)
            .build()
      );
   private final Setting<Boolean> importHud = this.sgSharing
      .add(
         new BoolSetting.Builder()
            .name("import-from-clipboard")
            .description("Replaces your HUD layout with one copied to your clipboard. This overwrites your current layout.")
            .defaultValue(false)
            .onChanged(this::onImportChanged)
            .build()
      );
   private final Setting<Integer> border = this.sgEditor
      .add(new IntSetting.Builder().name("border").description("Space around the edges of the screen.").defaultValue(4).sliderMax(20).build());
   private final Setting<Integer> snappingRange = this.sgEditor
      .add(new IntSetting.Builder().name("snapping-range").description("Snapping range in the editor.").defaultValue(10).sliderMax(20).build());

   private void onOpenEditorChanged(Boolean value) {
      if (value != null && value) {
         this.mc.execute(() -> {
            this.mc.setScreen(new NewHudEditorScreen());
            this.openEditor.set(false);
         });
      }
   }

   private void onExportChanged(Boolean value) {
      if (value != null && value) {
         this.mc.execute(() -> {
            if (NbtUtils.toClipboard(Hud.get())) {
               this.info("HUD layout copied to clipboard.", new Object[0]);
            } else {
               this.warning("Failed to copy HUD layout.", new Object[0]);
            }

            this.exportHud.set(false);
         });
      }
   }

   private void onImportChanged(Boolean value) {
      if (value != null && value) {
         this.mc.execute(() -> {
            NbtCompound tag = NbtUtils.fromClipboard();
            if (tag != null && tag.contains("elements")) {
               Hud.get().fromTag(tag);
               this.info("HUD layout imported from clipboard.", new Object[0]);
            } else {
               this.warning("No valid HUD layout on the clipboard.", new Object[0]);
            }

            this.importHud.set(false);
         });
      }
   }

   public HudModule() {
      super(Categories.Gui, "hud", "Toggles and configures the in-game HUD.");
   }

   @Override
   public void onActivate() {
      Hud.get().active = true;
   }

   @Override
   public void onDeactivate() {
      Hud.get().active = false;
   }

   public boolean customFont() {
      return this.customFont.get();
   }

   public boolean hideInMenus() {
      return this.hideInMenus.get();
   }

   public double textScale() {
      return this.textScale.get();
   }

   public boolean autoScale() {
      return this.autoScale.get();
   }

   public double hudScale() {
      return this.hudScale.get();
   }

   public int border() {
      return this.border.get();
   }

   public int snappingRange() {
      return this.snappingRange.get();
   }
}
