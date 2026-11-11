package revivedevelopment.reviveclient.systems.modules.player;

import java.util.List;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;

public class FastUse extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<FastUse.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode")).description("Which items to fast use."))
               .defaultValue(FastUse.Mode.All))
            .build()
      );
   private final Setting<List<Item>> items = this.sgGeneral
      .add(
         new ItemListSetting.Builder()
            .name("items")
            .description("Which items should fast place work on in \"Some\" mode.")
            .visible(() -> this.mode.get() == FastUse.Mode.Some)
            .build()
      );
   private final Setting<Boolean> blocks = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("blocks")
            .description("Fast-places blocks if the mode is \"Some\" mode.")
            .visible(() -> this.mode.get() == FastUse.Mode.Some)
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> cooldown = this.sgGeneral
      .add(new IntSetting.Builder().name("cooldown").description("Fast-use cooldown in ticks.").defaultValue(0).min(0).sliderMax(4).build());

   public FastUse() {
      super(Categories.Player, "fast-use", "Allows you to use items at very high speeds.");
   }

   public int getItemUseCooldown(ItemStack itemStack) {
      return this.mode.get() != FastUse.Mode.All && !this.shouldWorkSome(itemStack) ? 4 : this.cooldown.get();
   }

   private boolean shouldWorkSome(ItemStack itemStack) {
      return this.blocks.get() && itemStack.getItem() instanceof BlockItem || this.items.get().contains(itemStack.getItem());
   }

   public static enum Mode {
      All,
      Some;
   }
}
