package revivedevelopment.reviveclient.gui;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.resource.language.I18n;
import org.apache.commons.lang3.Strings;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.renderer.GuiRenderer;
import revivedevelopment.reviveclient.gui.screens.settings.BlockDataSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.BlockListSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.BlockSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.ColorSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.EnchantmentListSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.EntityTypeListSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.FontFaceSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.ItemListSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.ItemSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.ModuleListSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.PacketBoolSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.ParticleTypeListSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.PotionSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.ScreenHandlerSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.SoundEventListSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.StatusEffectAmplifierMapSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.StatusEffectListSettingScreen;
import revivedevelopment.reviveclient.gui.screens.settings.StorageBlockListSettingScreen;
import revivedevelopment.reviveclient.gui.themes.revive.widgets.WReviveLabel;
import revivedevelopment.reviveclient.gui.utils.Cell;
import revivedevelopment.reviveclient.gui.utils.CharFilter;
import revivedevelopment.reviveclient.gui.utils.IScreenFactory;
import revivedevelopment.reviveclient.gui.utils.SettingsWidgetFactory;
import revivedevelopment.reviveclient.gui.widgets.WItem;
import revivedevelopment.reviveclient.gui.widgets.WItemWithLabel;
import revivedevelopment.reviveclient.gui.widgets.WKeybind;
import revivedevelopment.reviveclient.gui.widgets.WLabel;
import revivedevelopment.reviveclient.gui.widgets.WQuad;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WContainer;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.gui.widgets.containers.WSection;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.containers.WVerticalList;
import revivedevelopment.reviveclient.gui.widgets.input.WBlockPosEdit;
import revivedevelopment.reviveclient.gui.widgets.input.WDoubleEdit;
import revivedevelopment.reviveclient.gui.widgets.input.WDropdown;
import revivedevelopment.reviveclient.gui.widgets.input.WIntEdit;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.gui.widgets.pressable.WCheckbox;
import revivedevelopment.reviveclient.gui.widgets.pressable.WMinus;
import revivedevelopment.reviveclient.gui.widgets.pressable.WPlus;
import revivedevelopment.reviveclient.renderer.Fonts;
import revivedevelopment.reviveclient.settings.BlockDataSetting;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BlockPosSetting;
import revivedevelopment.reviveclient.settings.BlockSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorListSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnchantmentListSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.FontFaceSetting;
import revivedevelopment.reviveclient.settings.GenericSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.ItemSetting;
import revivedevelopment.reviveclient.settings.KeybindSetting;
import revivedevelopment.reviveclient.settings.ModuleListSetting;
import revivedevelopment.reviveclient.settings.PacketListSetting;
import revivedevelopment.reviveclient.settings.ParticleTypeListSetting;
import revivedevelopment.reviveclient.settings.PotionSetting;
import revivedevelopment.reviveclient.settings.ProvidedStringSetting;
import revivedevelopment.reviveclient.settings.ScreenHandlerListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.Settings;
import revivedevelopment.reviveclient.settings.SoundEventListSetting;
import revivedevelopment.reviveclient.settings.StatusEffectAmplifierMapSetting;
import revivedevelopment.reviveclient.settings.StatusEffectListSetting;
import revivedevelopment.reviveclient.settings.StorageBlockListSetting;
import revivedevelopment.reviveclient.settings.StringListSetting;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.settings.Vector3dSetting;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class DefaultSettingsWidgetFactory extends SettingsWidgetFactory {
   private static final SettingColor WHITE = new SettingColor();

   public DefaultSettingsWidgetFactory(GuiTheme theme) {
      super(theme);
      this.factories.put(BoolSetting.class, (table, setting) -> this.boolW(table, (BoolSetting)setting));
      this.factories.put(IntSetting.class, (table, setting) -> this.intW(table, (IntSetting)setting));
      this.factories.put(DoubleSetting.class, (table, setting) -> this.doubleW(table, (DoubleSetting)setting));
      this.factories.put(StringSetting.class, (table, setting) -> this.stringW(table, (StringSetting)setting));
      this.factories.put(EnumSetting.class, (table, setting) -> this.enumW(table, (EnumSetting)setting));
      this.factories.put(ProvidedStringSetting.class, (table, setting) -> this.providedStringW(table, (ProvidedStringSetting)setting));
      this.factories.put(GenericSetting.class, (table, setting) -> this.genericW(table, (GenericSetting<?>)setting));
      this.factories.put(ColorSetting.class, (table, setting) -> this.colorW(table, (ColorSetting)setting));
      this.factories.put(KeybindSetting.class, (table, setting) -> this.keybindW(table, (KeybindSetting)setting));
      this.factories.put(BlockSetting.class, (table, setting) -> this.blockW(table, (BlockSetting)setting));
      this.factories.put(BlockListSetting.class, (table, setting) -> this.blockListW(table, (BlockListSetting)setting));
      this.factories.put(ItemSetting.class, (table, setting) -> this.itemW(table, (ItemSetting)setting));
      this.factories.put(ItemListSetting.class, (table, setting) -> this.itemListW(table, (ItemListSetting)setting));
      this.factories.put(EntityTypeListSetting.class, (table, setting) -> this.entityTypeListW(table, (EntityTypeListSetting)setting));
      this.factories.put(EnchantmentListSetting.class, (table, setting) -> this.enchantmentListW(table, (EnchantmentListSetting)setting));
      this.factories.put(ModuleListSetting.class, (table, setting) -> this.moduleListW(table, (ModuleListSetting)setting));
      this.factories.put(PacketListSetting.class, (table, setting) -> this.packetListW(table, (PacketListSetting)setting));
      this.factories.put(ParticleTypeListSetting.class, (table, setting) -> this.particleTypeListW(table, (ParticleTypeListSetting)setting));
      this.factories.put(SoundEventListSetting.class, (table, setting) -> this.soundEventListW(table, (SoundEventListSetting)setting));
      this.factories
         .put(StatusEffectAmplifierMapSetting.class, (table, setting) -> this.statusEffectAmplifierMapW(table, (StatusEffectAmplifierMapSetting)setting));
      this.factories.put(StatusEffectListSetting.class, (table, setting) -> this.statusEffectListW(table, (StatusEffectListSetting)setting));
      this.factories.put(StorageBlockListSetting.class, (table, setting) -> this.storageBlockListW(table, (StorageBlockListSetting)setting));
      this.factories.put(ScreenHandlerListSetting.class, (table, setting) -> this.screenHandlerListW(table, (ScreenHandlerListSetting)setting));
      this.factories.put(BlockDataSetting.class, (table, setting) -> this.blockDataW(table, (BlockDataSetting<?>)setting));
      this.factories.put(PotionSetting.class, (table, setting) -> this.potionW(table, (PotionSetting)setting));
      this.factories.put(StringListSetting.class, (table, setting) -> this.stringListW(table, (StringListSetting)setting));
      this.factories.put(BlockPosSetting.class, (table, setting) -> this.blockPosW(table, (BlockPosSetting)setting));
      this.factories.put(ColorListSetting.class, (table, setting) -> this.colorListW(table, (ColorListSetting)setting));
      this.factories.put(FontFaceSetting.class, (table, setting) -> this.fontW(table, (FontFaceSetting)setting));
      this.factories.put(Vector3dSetting.class, (table, setting) -> this.vector3dW(table, (Vector3dSetting)setting));
   }

   @Override
   public WWidget create(GuiTheme theme, Settings settings, String filter) {
      WVerticalList list = theme.verticalList();
      List<DefaultSettingsWidgetFactory.RemoveInfo> removeInfoList = new ArrayList<>();

      for (SettingGroup group : settings.groups) {
         this.group(list, group, filter, removeInfoList);
      }

      list.calculateSize();
      list.minWidth = list.width;

      for (DefaultSettingsWidgetFactory.RemoveInfo removeInfo : removeInfoList) {
         removeInfo.remove(list);
      }

      return list;
   }

   protected double settingTitleTopMargin() {
      return 6.0;
   }

   private void group(WVerticalList list, SettingGroup group, String filter, List<DefaultSettingsWidgetFactory.RemoveInfo> removeInfoList) {
      WSection section = list.add(this.theme.section(group.name, group.sectionExpanded)).expandX().widget();
      section.action = () -> group.sectionExpanded = section.isExpanded();
      WTable table = section.add(this.theme.table()).expandX().widget();
      DefaultSettingsWidgetFactory.RemoveInfo removeInfo = null;

      for (Setting<?> setting : group) {
         if (Strings.CI.contains(setting.title, filter)) {
            boolean visible = setting.isVisible();
            setting.lastWasVisible = visible;
            if (!visible) {
               if (removeInfo == null) {
                  removeInfo = new DefaultSettingsWidgetFactory.RemoveInfo(section, table);
               }

               removeInfo.markRowForRemoval();
            }

            table.add(this.theme.label(setting.title)).top().marginTop(this.settingTitleTopMargin()).widget().tooltip = setting.description;
            SettingsWidgetFactory.Factory factory = this.getFactory(setting.getClass());
            if (factory != null) {
               factory.create(table, setting);
            }

            table.row();
         }
      }

      if (removeInfo != null) {
         removeInfoList.add(removeInfo);
      }
   }

   private void boolW(WTable table, BoolSetting setting) {
      WCheckbox checkbox = table.add(this.theme.checkbox(setting.get())).expandCellX().widget();
      checkbox.action = () -> setting.set(checkbox.checked);
      this.reset(table, setting, () -> checkbox.checked = setting.get());
   }

   private void intW(WTable table, IntSetting setting) {
      WIntEdit edit = table.add(this.theme.intEdit(setting.get(), setting.min, setting.max, setting.sliderMin, setting.sliderMax, setting.noSlider))
         .expandX()
         .widget();
      edit.action = () -> {
         if (!setting.set(edit.get())) {
            edit.set(setting.get());
         }
      };
      this.reset(table, setting, () -> edit.set(setting.get()));
   }

   private void doubleW(WTable table, DoubleSetting setting) {
      WDoubleEdit edit = this.theme
         .doubleEdit(setting.get(), setting.min, setting.max, setting.sliderMin, setting.sliderMax, setting.decimalPlaces, setting.noSlider);
      table.add(edit).expandX();
      Runnable action = () -> {
         if (!setting.set(edit.get())) {
            edit.set(setting.get());
         }
      };
      if (setting.onSliderRelease) {
         edit.actionOnRelease = action;
      } else {
         edit.action = action;
      }

      this.reset(table, setting, () -> edit.set(setting.get()));
   }

   private void stringW(WTable table, StringSetting setting) {
      CharFilter filter = setting.filter == null ? (text, c) -> true : setting.filter;
      Cell<WTextBox> cell = table.add(this.theme.textBox(setting.get(), filter, setting.renderer));
      if (setting.wide) {
         cell.minWidth(Utils.getWindowWidth() - Utils.getWindowWidth() / 4.0);
      }

      WTextBox textBox = cell.expandX().widget();
      textBox.action = () -> setting.set(textBox.get());
      this.reset(table, setting, () -> textBox.set(setting.get()));
   }

   private void stringListW(WTable table, StringListSetting setting) {
      WTable wtable = table.add(this.theme.table()).expandX().widget();
      StringListSetting.fillTable(this.theme, wtable, setting);
   }

   private <T extends Enum<?>> void enumW(WTable table, EnumSetting<T> setting) {
      WDropdown<T> dropdown = table.add(this.theme.dropdown(setting.get())).expandCellX().widget();
      dropdown.action = () -> setting.set(dropdown.get());
      this.reset(table, setting, () -> dropdown.set(setting.get()));
   }

   private void providedStringW(WTable table, ProvidedStringSetting setting) {
      WDropdown<String> dropdown = table.add(this.theme.dropdown(setting.supplier.get(), setting.get())).expandCellX().widget();
      dropdown.action = () -> setting.set(dropdown.get());
      this.reset(table, setting, () -> dropdown.set(setting.get()));
   }

   private void genericW(WTable table, GenericSetting<?> setting) {
      WButton edit = table.add(this.theme.button(GuiRenderer.EDIT)).widget();
      edit.action = () -> ReviveClient.mc.setScreen(((IScreenFactory)setting.get()).createScreen(this.theme));
      this.reset(table, setting, null);
   }

   private void colorW(WTable table, ColorSetting setting) {
      WHorizontalList list = table.add(this.theme.horizontalList()).expandX().widget();
      WQuad quad = list.add(this.theme.quad(setting.get())).widget();
      WButton edit = list.add(this.theme.button(GuiRenderer.EDIT)).widget();
      edit.action = () -> ReviveClient.mc.setScreen(new ColorSettingScreen(this.theme, setting));
      this.reset(table, setting, () -> quad.color = setting.get());
   }

   private void keybindW(WTable table, KeybindSetting setting) {
      WHorizontalList list = table.add(this.theme.horizontalList()).expandX().widget();
      WKeybind keybind = list.add(this.theme.keybind(setting.get(), setting.getDefaultValue())).expandX().widget();
      keybind.action = setting::onChanged;
      setting.widget = keybind;
      WButton reset = list.add(this.theme.button(GuiRenderer.RESET)).expandCellX().right().widget();
      reset.action = keybind::resetBind;
   }

   private void blockW(WTable table, BlockSetting setting) {
      WHorizontalList list = table.add(this.theme.horizontalList()).expandX().widget();
      WItem item = list.add(this.theme.item(setting.get().asItem().getDefaultStack())).widget();
      WButton select = list.add(this.theme.button("Select")).widget();
      select.action = () -> {
         BlockSettingScreen screen = new BlockSettingScreen(this.theme, setting);
         screen.onClosed(() -> item.set(setting.get().asItem().getDefaultStack()));
         ReviveClient.mc.setScreen(screen);
      };
      this.reset(table, setting, () -> item.set(setting.get().asItem().getDefaultStack()));
   }

   private void blockPosW(WTable table, BlockPosSetting setting) {
      WBlockPosEdit edit = table.add(this.theme.blockPosEdit(setting.get())).expandX().widget();
      edit.actionOnRelease = () -> {
         if (!setting.set(edit.get())) {
            edit.set(setting.get());
         }
      };
      this.reset(table, setting, () -> edit.set(setting.get()));
   }

   private void blockListW(WTable table, BlockListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new BlockListSettingScreen(this.theme, setting)));
   }

   private void itemW(WTable table, ItemSetting setting) {
      WHorizontalList list = table.add(this.theme.horizontalList()).expandX().widget();
      WItem item = list.add(this.theme.item(setting.get().asItem().getDefaultStack())).widget();
      WButton select = list.add(this.theme.button("Select")).widget();
      select.action = () -> {
         ItemSettingScreen screen = new ItemSettingScreen(this.theme, setting);
         screen.onClosed(() -> item.set(setting.get().getDefaultStack()));
         ReviveClient.mc.setScreen(screen);
      };
      this.reset(table, setting, () -> item.set(setting.get().getDefaultStack()));
   }

   private void itemListW(WTable table, ItemListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new ItemListSettingScreen(this.theme, setting)));
   }

   private void entityTypeListW(WTable table, EntityTypeListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new EntityTypeListSettingScreen(this.theme, setting)));
   }

   private void enchantmentListW(WTable table, EnchantmentListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new EnchantmentListSettingScreen(this.theme, setting)));
   }

   private void moduleListW(WTable table, ModuleListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new ModuleListSettingScreen(this.theme, setting)));
   }

   private void packetListW(WTable table, PacketListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new PacketBoolSettingScreen(this.theme, setting)));
   }

   private void particleTypeListW(WTable table, ParticleTypeListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new ParticleTypeListSettingScreen(this.theme, setting)));
   }

   private void soundEventListW(WTable table, SoundEventListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new SoundEventListSettingScreen(this.theme, setting)));
   }

   private void statusEffectAmplifierMapW(WTable table, StatusEffectAmplifierMapSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new StatusEffectAmplifierMapSettingScreen(this.theme, setting)));
   }

   private void statusEffectListW(WTable table, StatusEffectListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new StatusEffectListSettingScreen(this.theme, setting)));
   }

   private void storageBlockListW(WTable table, StorageBlockListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new StorageBlockListSettingScreen(this.theme, setting)));
   }

   private void screenHandlerListW(WTable table, ScreenHandlerListSetting setting) {
      this.selectW(table, setting, () -> ReviveClient.mc.setScreen(new ScreenHandlerSettingScreen(this.theme, setting)));
   }

   private void blockDataW(WTable table, BlockDataSetting<?> setting) {
      WButton button = table.add(this.theme.button(GuiRenderer.EDIT)).expandCellX().widget();
      button.action = () -> ReviveClient.mc.setScreen(new BlockDataSettingScreen(this.theme, setting));
      this.reset(table, setting, null);
   }

   private void potionW(WTable table, PotionSetting setting) {
      WHorizontalList list = table.add(this.theme.horizontalList()).expandX().widget();
      WItemWithLabel item = list.add(
            this.theme.itemWithLabel(setting.get().potion, I18n.translate(setting.get().potion.getItem().getTranslationKey(), new Object[0]))
         )
         .widget();
      WButton button = list.add(this.theme.button("Select")).expandCellX().widget();
      button.action = () -> {
         WidgetScreen screen = new PotionSettingScreen(this.theme, setting);
         screen.onClosed(() -> item.set(setting.get().potion));
         ReviveClient.mc.setScreen(screen);
      };
      this.reset(list, setting, () -> item.set(setting.get().potion));
   }

   private void fontW(WTable table, FontFaceSetting setting) {
      WHorizontalList list = table.add(this.theme.horizontalList()).expandX().widget();
      WLabel label = list.add(this.theme.label(setting.get().info.family())).widget();
      WButton button = list.add(this.theme.button("Select")).expandCellX().widget();
      button.action = () -> {
         WidgetScreen screen = new FontFaceSettingScreen(this.theme, setting);
         screen.onClosed(() -> label.set(setting.get().info.family()));
         ReviveClient.mc.setScreen(screen);
      };
      this.reset(list, setting, () -> label.set(Fonts.DEFAULT_FONT.info.family()));
   }

   private void colorListW(WTable table, ColorListSetting setting) {
      WTable tab = table.add(this.theme.table()).expandX().widget();
      WTable t = tab.add(this.theme.table()).expandX().widget();
      tab.row();
      this.colorListWFill(t, setting);
      WPlus add = tab.add(this.theme.plus()).expandCellX().widget();
      add.action = () -> {
         setting.get().add(new SettingColor());
         setting.onChanged();
         t.clear();
         this.colorListWFill(t, setting);
      };
      this.reset(tab, setting, () -> {
         t.clear();
         this.colorListWFill(t, setting);
      });
   }

   private void colorListWFill(WTable t, ColorListSetting setting) {
      int i = 0;

      for (SettingColor color : setting.get()) {
         int _i = i;
         t.add(this.theme.label(i + ":"));
         t.add(this.theme.quad(color)).widget();
         WButton edit = t.add(this.theme.button(GuiRenderer.EDIT)).widget();
         edit.action = () -> {
            SettingColor defaultValue = WHITE;
            if (_i < setting.getDefaultValue().size()) {
               defaultValue = setting.getDefaultValue().get(_i);
            }

            ColorSetting set = new ColorSetting(setting.name, setting.description, defaultValue, settingColor -> {
               setting.get().get(_i).set((Color)settingColor);
               setting.onChanged();
            }, null, null);
            set.set(setting.get().get(_i));
            ReviveClient.mc.setScreen(new ColorSettingScreen(this.theme, set));
         };
         WMinus remove = t.add(this.theme.minus()).expandCellX().right().widget();
         remove.action = () -> {
            setting.get().remove(_i);
            setting.onChanged();
            t.clear();
            this.colorListWFill(t, setting);
         };
         t.row();
         i++;
      }
   }

   private void vector3dW(WTable table, Vector3dSetting setting) {
      WTable internal = table.add(this.theme.table()).expandX().widget();
      WDoubleEdit x = this.addVectorComponent(internal, "X", setting.get().x, val -> setting.get().x = val, setting);
      WDoubleEdit y = this.addVectorComponent(internal, "Y", setting.get().y, val -> setting.get().y = val, setting);
      WDoubleEdit z = this.addVectorComponent(internal, "Z", setting.get().z, val -> setting.get().z = val, setting);
      this.reset(table, setting, () -> {
         x.set(setting.get().x);
         y.set(setting.get().y);
         z.set(setting.get().z);
      });
   }

   private WDoubleEdit addVectorComponent(WTable table, String label, double value, Consumer<Double> update, Vector3dSetting setting) {
      table.add(this.theme.label(label + ": "));
      WDoubleEdit component = table.add(
            this.theme.doubleEdit(value, setting.min, setting.max, setting.sliderMin, setting.sliderMax, setting.decimalPlaces, setting.noSlider)
         )
         .expandX()
         .widget();
      if (setting.onSliderRelease) {
         component.actionOnRelease = () -> update.accept(component.get());
      } else {
         component.action = () -> update.accept(component.get());
      }

      table.row();
      return component;
   }

   private void selectW(WContainer c, Setting<?> setting, Runnable action) {
      boolean addCount = DefaultSettingsWidgetFactory.WSelectedCountLabel.getSize(setting) != -1;
      WContainer c2 = c;
      if (addCount) {
         c2 = c.add(this.theme.horizontalList()).expandCellX().widget();
         ((WHorizontalList)c2).spacing *= 2.0;
      }

      WButton button = c2.add(this.theme.button("Select")).expandCellX().widget();
      button.action = action;
      if (addCount) {
         c2.add(new DefaultSettingsWidgetFactory.WSelectedCountLabel(setting).color(this.theme.textSecondaryColor()));
      }

      this.reset(c, setting, null);
   }

   private void reset(WContainer c, Setting<?> setting, Runnable action) {
      WButton reset = c.add(this.theme.button(GuiRenderer.RESET)).widget();
      reset.action = () -> {
         setting.reset();
         if (action != null) {
            action.run();
         }
      };
   }

   private static class RemoveInfo {
      private final WSection section;
      private final WTable table;
      private final IntList rowIds = new IntArrayList();

      public RemoveInfo(WSection section, WTable table) {
         this.section = section;
         this.table = table;
      }

      public void markRowForRemoval() {
         this.rowIds.add(this.table.rowI());
      }

      public void remove(WVerticalList list) {
         for (int i = 0; i < this.rowIds.size(); i++) {
            this.table.removeRow(this.rowIds.getInt(i) - i);
         }

         if (this.table.cells.isEmpty()) {
            list.cells.removeIf(cell -> cell.widget() == this.section);
         }
      }
   }

   private static class WSelectedCountLabel extends WReviveLabel {
      private final Setting<?> setting;
      private int lastSize = -1;

      public WSelectedCountLabel(Setting<?> setting) {
         super("", false);
         this.setting = setting;
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         int size = getSize(this.setting);
         if (size != this.lastSize) {
            this.set("(" + size + " selected)");
            this.lastSize = size;
         }

         super.onRender(renderer, mouseX, mouseY, delta);
      }

      public static int getSize(Setting<?> setting) {
         if (setting.get() instanceof Collection<?> collection) {
            return collection.size();
         } else {
            return setting.get() instanceof Map<?, ?> map ? map.size() : -1;
         }
      }
   }
}
