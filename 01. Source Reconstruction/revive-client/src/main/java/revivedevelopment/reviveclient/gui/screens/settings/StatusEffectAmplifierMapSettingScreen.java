package revivedevelopment.reviveclient.gui.screens.settings;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.apache.commons.lang3.Strings;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.input.WIntEdit;
import revivedevelopment.reviveclient.gui.widgets.input.WTextBox;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.misc.Names;

public class StatusEffectAmplifierMapSettingScreen extends WindowScreen {
   private final Setting<Reference2IntMap<StatusEffect>> setting;
   private WTable table;
   private String filterText = "";

   public StatusEffectAmplifierMapSettingScreen(GuiTheme theme, Setting<Reference2IntMap<StatusEffect>> setting) {
      super(theme, "Modify Amplifiers");
      this.setting = setting;
   }

   @Override
   public void initWidgets() {
      WTextBox filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      filter.setFocused(true);
      filter.action = () -> {
         this.filterText = filter.get().trim();
         this.table.clear();
         this.initTable();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initTable();
   }

   private void initTable() {
      List<StatusEffect> statusEffects = new ArrayList<>(this.setting.get().keySet());
      statusEffects.sort(Comparator.comparing(Names::get));

      for (StatusEffect statusEffect : statusEffects) {
         String name = Names.get(statusEffect);
         if (Strings.CI.contains(name, this.filterText)) {
            this.table.add(this.theme.itemWithLabel(this.getPotionStack(statusEffect), name)).expandCellX();
            WIntEdit level = this.theme.intEdit(this.setting.get().getInt(statusEffect), 0, Integer.MAX_VALUE, true);
            level.action = () -> {
               this.setting.get().put(statusEffect, level.get());
               this.setting.onChanged();
            };
            this.table.add(level).minWidth(50.0);
            this.table.row();
         }
      }
   }

   private ItemStack getPotionStack(StatusEffect effect) {
      ItemStack potion = Items.POTION.getDefaultStack();
      potion.set(
         DataComponentTypes.POTION_CONTENTS,
         new PotionContentsComponent(
            ((PotionContentsComponent)potion.get(DataComponentTypes.POTION_CONTENTS)).potion(),
            Optional.of(effect.getColor()),
            ((PotionContentsComponent)potion.get(DataComponentTypes.POTION_CONTENTS)).customEffects(),
            Optional.empty()
         )
      );
      return potion;
   }
}
