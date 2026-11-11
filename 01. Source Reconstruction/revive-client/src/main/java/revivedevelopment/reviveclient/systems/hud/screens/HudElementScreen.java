package revivedevelopment.reviveclient.systems.hud.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WContainer;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.pressable.WCheckbox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WMinus;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.Settings;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.XAnchor;
import revivedevelopment.reviveclient.systems.hud.YAnchor;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.misc.NbtUtils;

public class HudElementScreen extends WindowScreen {
   private final HudElement element;
   private WContainer settingsC1;
   private WContainer settingsC2;
   private final Settings settings;

   public HudElementScreen(GuiTheme theme, HudElement element) {
      super(theme, element.info.title);
      this.element = element;
      this.settings = new Settings();
      SettingGroup sg = this.settings.createGroup("Anchors");
      sg.add(
         new BoolSetting.Builder()
            .name("auto-anchors")
            .description("Automatically assigns anchors based on the position.")
            .defaultValue(true)
            .onModuleActivated(booleanSetting -> booleanSetting.set(element.autoAnchors))
            .onChanged(aBoolean -> {
               if (aBoolean) {
                  element.box.updateAnchors();
               }

               element.autoAnchors = aBoolean;
            })
            .build()
      );
      sg.add(
         ((((((new EnumSetting.Builder<XAnchor>()
                              .name("x-anchor"))
                           .description("Horizontal anchor."))
                        .defaultValue(XAnchor.Left))
                     .visible(() -> !element.autoAnchors))
                  .onModuleActivated(xAnchorSetting -> xAnchorSetting.set(element.box.xAnchor)))
               .onChanged(element.box::setXAnchor))
            .build()
      );
      sg.add(
         ((((((new EnumSetting.Builder<YAnchor>()
                              .name("y-anchor"))
                           .description("Vertical anchor."))
                        .defaultValue(YAnchor.Top))
                     .visible(() -> !element.autoAnchors))
                  .onModuleActivated(yAnchorSetting -> yAnchorSetting.set(element.box.yAnchor)))
               .onChanged(element.box::setYAnchor))
            .build()
      );
   }

   @Override
   public void initWidgets() {
      this.add(this.theme.label(this.element.info.description, Utils.getWindowWidth() / 2.0));
      if (this.element.settings.sizeGroups() > 0) {
         this.element.settings.onActivated();
         this.settingsC1 = this.add(this.theme.verticalList()).expandX().widget();
         this.settingsC1.add(this.theme.settings(this.element.settings)).expandX();
      }

      this.settings.onActivated();
      this.settingsC2 = this.add(this.theme.verticalList()).expandX().widget();
      this.settingsC2.add(this.theme.settings(this.settings)).expandX();
      this.add(this.theme.horizontalSeparator()).expandX();
      WWidget widget = this.element.getWidget(this.theme);
      if (widget != null) {
         Cell<WWidget> cell = this.add(widget);
         if (widget instanceof WContainer) {
            cell.expandX();
         }

         this.add(this.theme.horizontalSeparator()).expandX();
      }

      WHorizontalList bottomList = this.add(this.theme.horizontalList()).expandX().widget();
      bottomList.add(this.theme.label("Active:"));
      WCheckbox active = bottomList.add(this.theme.checkbox(this.element.isActive())).widget();
      active.action = () -> {
         if (this.element.isActive() != active.checked) {
            this.element.toggle();
         }
      };
      WMinus remove = bottomList.add(this.theme.minus()).expandCellX().right().widget();
      remove.action = () -> {
         this.element.remove();
         this.close();
      };
   }

   public void tick() {
      super.tick();
      if (this.settingsC1 != null) {
         this.element.settings.tick(this.settingsC1, this.theme);
      }

      this.settings.tick(this.settingsC2, this.theme);
   }

   @Override
   protected void onRenderBefore(DrawContext drawContext, float delta) {
      HudEditorScreen.renderElements(drawContext);
   }

   @Override
   public boolean toClipboard() {
      return NbtUtils.toClipboard(this.element.toTag());
   }

   @Override
   public boolean fromClipboard() {
      NbtCompound clipboard = NbtUtils.fromClipboard();
      if (clipboard != null) {
         this.element.fromTag(clipboard);
         return true;
      } else {
         return false;
      }
   }
}
