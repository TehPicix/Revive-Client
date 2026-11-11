package revivedevelopment.reviveclient.systems.hud.elements;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class InventoryHud extends HudElement {
   public static final HudElementInfo<InventoryHud> INFO = new HudElementInfo<>(Hud.GROUP, "inventory", "Displays your inventory.", InventoryHud::new);
   private static final Identifier TEXTURE = ReviveClient.identifier("textures/container.png");
   private static final Identifier TEXTURE_TRANSPARENT = ReviveClient.identifier("textures/container-transparent.png");
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> containers = this.sgGeneral
      .add(new BoolSetting.Builder().name("containers").description("Shows the contents of a container when holding them.").defaultValue(false).build());
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(2.0)
            .min(1.0)
            .sliderRange(1.0, 5.0)
            .onChanged(aDouble -> this.calculateSize())
            .build()
      );
   private final Setting<InventoryHud.Background> background = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("background"))
                     .description("Background of inventory viewer."))
                  .defaultValue(InventoryHud.Background.Texture))
               .onChanged(bg -> this.calculateSize()))
            .build()
      );
   private final Setting<SettingColor> color = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("background-color")
            .description("Color of the background.")
            .defaultValue(new SettingColor(255, 255, 255))
            .visible(() -> this.background.get() != InventoryHud.Background.None)
            .build()
      );
   private final ItemStack[] containerItems = new ItemStack[27];

   private InventoryHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void render(HudRenderer renderer) {
      double x = this.x;
      double y = this.y;
      ItemStack container = this.getContainer();
      boolean hasContainer = this.containers.get() && container != null;
      if (hasContainer) {
         Utils.getItemsInContainerItem(container, this.containerItems);
      }

      Color drawColor = hasContainer ? Utils.getShulkerColor(container) : this.color.get();
      if (this.background.get() != InventoryHud.Background.None) {
         this.drawBackground(renderer, (int)x, (int)y, drawColor);
      }

      if (ReviveClient.mc.player != null) {
         renderer.post(
            () -> {
               for (int row = 0; row < 3; row++) {
                  for (int i = 0; i < 9; i++) {
                     int index = row * 9 + i;
                     ItemStack stack = hasContainer ? this.containerItems[index] : ReviveClient.mc.player.getInventory().getStack(index + 9);
                     if (stack != null) {
                        int itemX = this.background.get() == InventoryHud.Background.Texture
                           ? (int)(x + (8 + i * 18) * this.scale.get())
                           : (int)(x + (1 + i * 18) * this.scale.get());
                        int itemY = this.background.get() == InventoryHud.Background.Texture
                           ? (int)(y + (7 + row * 18) * this.scale.get())
                           : (int)(y + (1 + row * 18) * this.scale.get());
                        renderer.item(stack, itemX, itemY, this.scale.get().floatValue(), true);
                     }
                  }
               }
            }
         );
      }
   }

   private void calculateSize() {
      this.setSize(this.background.get().width * this.scale.get(), this.background.get().height * this.scale.get());
   }

   private void drawBackground(HudRenderer renderer, int x, int y, Color color) {
      int w = this.getWidth();
      int h = this.getHeight();
      switch ((InventoryHud.Background)this.background.get()) {
         case Texture:
         case Outline:
            renderer.texture(this.background.get() == InventoryHud.Background.Texture ? TEXTURE : TEXTURE_TRANSPARENT, x, y, w, h, color);
            break;
         case Flat:
            renderer.quad(x, y, w, h, color);
      }
   }

   private ItemStack getContainer() {
      if (!this.isInEditor() && ReviveClient.mc.player != null) {
         ItemStack stack = ReviveClient.mc.player.getOffHandStack();
         if (!Utils.hasItems(stack) && stack.getItem() != Items.ENDER_CHEST) {
            stack = ReviveClient.mc.player.getMainHandStack();
            return !Utils.hasItems(stack) && stack.getItem() != Items.ENDER_CHEST ? null : stack;
         } else {
            return stack;
         }
      } else {
         return null;
      }
   }

   public static enum Background {
      None(162, 54),
      Texture(176, 67),
      Outline(162, 54),
      Flat(162, 54);

      private final int width;
      private final int height;

      private Background(int width, int height) {
         this.width = width;
         this.height = height;
      }
   }
}
