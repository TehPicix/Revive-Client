package revivedevelopment.reviveclient.systems.hud.elements;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IPlayerInventory;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.systems.hud.HudStyle;

public class ArmorHud extends HudElement {
   public static final HudElementInfo<ArmorHud> INFO = new HudElementInfo<>(Hud.GROUP, "armor", "Displays your armor.", ArmorHud::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDurability = this.settings.createGroup("Durability");
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
   private final Setting<ArmorHud.Orientation> orientation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("orientation"))
                     .description("How to display armor."))
                  .defaultValue(ArmorHud.Orientation.Horizontal))
               .onChanged(val -> this.calculateSize()))
            .build()
      );
   private final Setting<Boolean> flipOrder = this.sgGeneral
      .add(new BoolSetting.Builder().name("flip-order").description("Flips the order of armor items.").defaultValue(true).build());
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(2.0)
            .onChanged(aDouble -> this.calculateSize())
            .min(1.0)
            .sliderRange(1.0, 5.0)
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
   private final Setting<ArmorHud.Durability> durability = this.sgDurability
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("durability"))
                     .description("How to display armor durability."))
                  .defaultValue(ArmorHud.Durability.Bar))
               .onChanged(durability1 -> this.calculateSize()))
            .build()
      );
   private final Setting<Boolean> durabilityShadow = this.sgDurability
      .add(
         new BoolSetting.Builder()
            .name("durability-shadow")
            .description("Text shadow.")
            .visible(() -> this.durability.get() == ArmorHud.Durability.Total || this.durability.get() == ArmorHud.Durability.Percentage)
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(false).build());

   public ArmorHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void setSize(double width, double height) {
      super.setSize(width + this.border.get() * 2, height + this.border.get() * 2);
   }

   private void calculateSize() {
      double length = 18.0 * this.scale.get() * 4.0 - 2.0 * this.scale.get();
      switch ((ArmorHud.Orientation)this.orientation.get()) {
         case Horizontal:
            this.setSize(length, 16.0 * this.scale.get());
            break;
         case Vertical:
            this.setSize(16.0 * this.scale.get(), length);
      }
   }

   @Override
   public void render(HudRenderer renderer) {
      double x = this.x + this.border.get();
      double y = this.y + this.border.get();
      int slot = this.flipOrder.get() ? 3 : 0;

      for (int position = 0; position < 4; position++) {
         ItemStack itemStack = this.getItem(slot);
         double armorX;
         double armorY;
         if (this.orientation.get() == ArmorHud.Orientation.Vertical) {
            armorX = x;
            armorY = y + position * 18 * this.scale.get();
         } else {
            armorX = x + position * 18 * this.scale.get();
            armorY = y;
         }

         renderer.item(
            itemStack, (int)armorX, (int)armorY, this.scale.get().floatValue(), itemStack.isDamageable() && this.durability.get() == ArmorHud.Durability.Bar
         );
         if (itemStack.isDamageable() && this.durability.get() != ArmorHud.Durability.Bar && this.durability.get() != ArmorHud.Durability.None) {
            String message = switch ((ArmorHud.Durability)this.durability.get()) {
               case Total -> Integer.toString(itemStack.getMaxDamage() - itemStack.getDamage());
               case Percentage -> Integer.toString(Math.round((itemStack.getMaxDamage() - itemStack.getDamage()) * 100.0F / itemStack.getMaxDamage()));
               default -> "err";
            };
            double messageWidth = renderer.textWidth(message);
            if (this.orientation.get() == ArmorHud.Orientation.Vertical) {
               armorX = x + 8.0 * this.scale.get() - messageWidth / 2.0;
               armorY = y + 18 * position * this.scale.get() + (18.0 * this.scale.get() - renderer.textHeight());
            } else {
               armorX = x + 18 * position * this.scale.get() + 8.0 * this.scale.get() - messageWidth / 2.0;
               armorY = y + (this.getHeight() - renderer.textHeight());
            }

            renderer.text(message, armorX, armorY, HudStyle.TEXT_PRIMARY, this.durabilityShadow.get());
         }

         if (this.flipOrder.get()) {
            slot--;
         } else {
            slot++;
         }
      }

      if (this.background.get()) {
         renderer.panel(this.x, this.y, this.getWidth(), this.getHeight(), HudStyle.BG_PANEL, HudStyle.BORDER, HudStyle.ACCENT);
      }
   }

   private ItemStack getItem(int i) {
      if (this.isInEditor()) {
         return switch (i) {
            case 1 -> Items.NETHERITE_LEGGINGS.getDefaultStack();
            case 2 -> Items.NETHERITE_CHESTPLATE.getDefaultStack();
            case 3 -> Items.NETHERITE_HELMET.getDefaultStack();
            default -> Items.NETHERITE_BOOTS.getDefaultStack();
         };
      } else {
         return (ItemStack)((IPlayerInventory)ReviveClient.mc.player.getInventory()).revive$getArmor().get(i);
      }
   }

   public static enum Durability {
      None,
      Bar,
      Total,
      Percentage;
   }

   public static enum Orientation {
      Horizontal,
      Vertical;
   }
}
