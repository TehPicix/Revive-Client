package revivedevelopment.reviveclient.systems.hud.elements;

import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;

public class ItemHud extends HudElement {
   public static final HudElementInfo<ItemHud> INFO = new HudElementInfo<>(Hud.GROUP, "item", "Displays the item count.", ItemHud::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<Item> item = this.sgGeneral
      .add(new ItemSetting.Builder().name("item").description("Item to display").defaultValue(Items.TOTEM_OF_UNDYING).build());
   private final Setting<ItemHud.NoneMode> noneMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("none-mode"))
                  .description("How to render the item when you don't have the specified item in your inventory."))
               .defaultValue(ItemHud.NoneMode.HideCount))
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Scale of the item.")
            .defaultValue(2.0)
            .onChanged(aDouble -> this.calculateSize())
            .min(1.0)
            .sliderRange(1.0, 4.0)
            .build()
      );
   private final Setting<Integer> border = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("border")
            .description("How much space to add around the element.")
            .defaultValue(0)
            .onChanged(integer -> this.calculateSize())
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());

   private ItemHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   private void calculateSize() {
      this.setSize(17.0 * this.scale.get(), 17.0 * this.scale.get());
   }

   @Override
   public void render(HudRenderer renderer) {
      ItemStack itemStack = new ItemStack((ItemConvertible)this.item.get(), InvUtils.find(this.item.get()).count());
      if (this.noneMode.get() != ItemHud.NoneMode.HideItem || !itemStack.isEmpty()) {
         renderer.post(() -> {
            double x = this.x + this.border.get();
            double y = this.y + this.border.get();
            this.render(renderer, itemStack, (int)x, (int)y);
         });
      } else if (this.isInEditor()) {
         renderer.line(this.x, this.y, this.x + this.getWidth(), this.y + this.getHeight(), Color.GRAY);
         renderer.line(this.x, this.y + this.getHeight(), this.x + this.getWidth(), this.y, Color.GRAY);
      }

      if (this.background.get()) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
      }
   }

   private void render(HudRenderer renderer, ItemStack itemStack, int x, int y) {
      if (this.noneMode.get() == ItemHud.NoneMode.HideItem) {
         renderer.item(itemStack, x, y, this.scale.get().floatValue(), true);
      } else {
         String countOverride = null;
         boolean resetToZero = false;
         if (itemStack.isEmpty()) {
            if (this.noneMode.get() == ItemHud.NoneMode.ShowCount) {
               countOverride = "0";
            }

            itemStack.setCount(1);
            resetToZero = true;
         }

         renderer.item(itemStack, x, y, this.scale.get().floatValue(), true, countOverride);
         if (resetToZero) {
            itemStack.setCount(0);
         }
      }
   }

   public static enum NoneMode {
      HideItem,
      HideCount,
      ShowCount;

      @Override
      public String toString() {
         return switch (this) {
            case HideItem -> "Hide Item";
            case HideCount -> "Hide Count";
            case ShowCount -> "Show Count";
         };
      }
   }
}
