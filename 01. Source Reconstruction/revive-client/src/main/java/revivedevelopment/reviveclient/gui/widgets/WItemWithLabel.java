package revivedevelopment.reviveclient.gui.widgets;

import java.util.Iterator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.gui.widgets.containers.WHorizontalList;
import revivedevelopment.reviveclient.utils.misc.Names;

public class WItemWithLabel extends WHorizontalList {
   private ItemStack itemStack;
   private String name;
   private WItem item;
   private WLabel label;

   public WItemWithLabel(ItemStack itemStack, String name) {
      this.itemStack = itemStack;
      this.name = name;
   }

   @Override
   public void init() {
      this.item = this.add(this.theme.item(this.itemStack)).widget();
      this.label = this.add(this.theme.label(this.name + this.getStringToAppend())).widget();
   }

   private String getStringToAppend() {
      String str = "";
      if (this.itemStack.getItem() == Items.POTION) {
         Iterator<StatusEffectInstance> effects = ((PotionContentsComponent)this.itemStack.getItem().getComponents().get(DataComponentTypes.POTION_CONTENTS))
            .getEffects()
            .iterator();
         if (!effects.hasNext()) {
            return str;
         }

         str = str + " ";
         StatusEffectInstance effect = effects.next();
         if (effect.getAmplifier() > 0) {
            str = str + "%d ".formatted(effect.getAmplifier() + 1);
         }

         str = str
            + "(%s)"
               .formatted(
                  StatusEffectUtil.getDurationText(effect, 1.0F, ReviveClient.mc.world != null ? ReviveClient.mc.world.getTickManager().getTickRate() : 20.0F)
                     .getString()
               );
      }

      return str;
   }

   public void set(ItemStack itemStack) {
      this.itemStack = itemStack;
      this.item.itemStack = itemStack;
      this.name = Names.get(itemStack);
      this.label.set(this.name + this.getStringToAppend());
   }

   public String getLabelText() {
      return this.label == null ? this.name : this.label.get();
   }
}
