package revivedevelopment.reviveclient.gui.screens.settings;

import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.utils.misc.Names;

public class StatusEffectListSettingScreen extends CollectionListSettingScreen<StatusEffect> {
   public StatusEffectListSettingScreen(GuiTheme theme, Setting<List<StatusEffect>> setting) {
      super(theme, "Select Effects", setting, setting.get(), Registries.STATUS_EFFECT);
   }

   protected WWidget getValueWidget(StatusEffect value) {
      return this.theme.itemWithLabel(this.getPotionStack(value), this.getValueName(value));
   }

   protected String getValueName(StatusEffect value) {
      return Names.get(value);
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
