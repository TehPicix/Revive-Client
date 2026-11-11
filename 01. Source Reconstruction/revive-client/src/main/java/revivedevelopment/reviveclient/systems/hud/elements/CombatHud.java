package revivedevelopment.reviveclient.systems.hud.elements;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4fStack;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixininterface.IPlayerInventory;
import revivedevelopment.reviveclient.renderer.Renderer2D;
import revivedevelopment.reviveclient.renderer.text.TextRenderer;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnchantmentListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.hud.Hud;
import revivedevelopment.reviveclient.systems.hud.HudElement;
import revivedevelopment.reviveclient.systems.hud.HudElementInfo;
import revivedevelopment.reviveclient.systems.hud.HudRenderer;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.SortPriority;
import revivedevelopment.reviveclient.utils.entity.TargetUtils;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class CombatHud extends HudElement {
   private static final Color GREEN = new Color(15, 255, 15);
   private static final Color RED = new Color(255, 15, 15);
   private static final Color BLACK = new Color(0, 0, 0, 255);
   public static final HudElementInfo<CombatHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "combat", "Displays information about your combat target.", CombatHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
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
   private final Setting<Double> range = this.sgGeneral
      .add(new DoubleSetting.Builder().name("range").description("The range to target players.").defaultValue(100.0).min(1.0).sliderMax(200.0).build());
   private final Setting<Boolean> displayPing = this.sgGeneral
      .add(new BoolSetting.Builder().name("ping").description("Shows the player's ping.").defaultValue(true).build());
   private final Setting<Boolean> displayDistance = this.sgGeneral
      .add(new BoolSetting.Builder().name("distance").description("Shows the distance between you and the player.").defaultValue(true).build());
   private final Setting<Set<RegistryKey<Enchantment>>> displayedEnchantments = this.sgGeneral
      .add(
         new EnchantmentListSetting.Builder()
            .name("displayed-enchantments")
            .description("The enchantments that are shown on nametags.")
            .vanillaDefaults()
            .build()
      );
   private final Setting<SettingColor> backgroundColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("background-color").description("Color of background.").defaultValue(new SettingColor(0, 0, 0, 64)).build());
   private final Setting<SettingColor> enchantmentTextColor = this.sgGeneral
      .add(new ColorSetting.Builder().name("enchantment-color").description("Color of enchantment text.").defaultValue(new SettingColor(255, 255, 255)).build());
   private final Setting<SettingColor> pingColor1 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("ping-stage-1")
            .description("Color of ping text when under 75.")
            .defaultValue(new SettingColor(15, 255, 15))
            .visible(this.displayPing::get)
            .build()
      );
   private final Setting<SettingColor> pingColor2 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("ping-stage-2")
            .description("Color of ping text when between 75 and 200.")
            .defaultValue(new SettingColor(255, 150, 15))
            .visible(this.displayPing::get)
            .build()
      );
   private final Setting<SettingColor> pingColor3 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("ping-stage-3")
            .description("Color of ping text when over 200.")
            .defaultValue(new SettingColor(255, 15, 15))
            .visible(this.displayPing::get)
            .build()
      );
   private final Setting<SettingColor> distColor1 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("distance-stage-1")
            .description("The color when a player is within 10 blocks of you.")
            .defaultValue(new SettingColor(255, 15, 15))
            .visible(this.displayDistance::get)
            .build()
      );
   private final Setting<SettingColor> distColor2 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("distance-stage-2")
            .description("The color when a player is within 50 blocks of you.")
            .defaultValue(new SettingColor(255, 150, 15))
            .visible(this.displayDistance::get)
            .build()
      );
   private final Setting<SettingColor> distColor3 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("distance-stage-3")
            .description("The color when a player is greater then 50 blocks away from you.")
            .defaultValue(new SettingColor(15, 255, 15))
            .visible(this.displayDistance::get)
            .build()
      );
   private final Setting<SettingColor> healthColor1 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("health-stage-1")
            .description("The color on the left of the health gradient.")
            .defaultValue(new SettingColor(255, 15, 15))
            .build()
      );
   private final Setting<SettingColor> healthColor2 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("health-stage-2")
            .description("The color in the middle of the health gradient.")
            .defaultValue(new SettingColor(255, 150, 15))
            .build()
      );
   private final Setting<SettingColor> healthColor3 = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("health-stage-3")
            .description("The color on the right of the health gradient.")
            .defaultValue(new SettingColor(15, 255, 15))
            .build()
      );
   private PlayerEntity playerEntity;

   public CombatHud() {
      super(INFO);
      this.calculateSize();
   }

   private void calculateSize() {
      this.setSize(175.0 * this.scale.get(), 95.0 * this.scale.get());
   }

   @Override
   public void tick(HudRenderer renderer) {
      if (ReviveClient.mc.world == null) {
         this.playerEntity = null;
      }
   }

   @Override
   public void render(HudRenderer renderer) {
      renderer.post(
         () -> {
            double x = this.x;
            double y = this.y;
            Color primaryColor = TextHud.getSectionColor(0);
            Color secondaryColor = TextHud.getSectionColor(1);
            if (this.isInEditor()) {
               this.playerEntity = ReviveClient.mc.player;
            } else {
               this.playerEntity = TargetUtils.getPlayerTarget(this.range.get(), SortPriority.LowestDistance);
            }

            if (this.playerEntity != null || this.isInEditor()) {
               Renderer2D.color().begin();
               Renderer2D.color().quad(x, y, this.getWidth(), this.getHeight(), this.backgroundColor.get());
               if (this.playerEntity == null) {
                  if (this.isInEditor()) {
                     renderer.line(x, y, x + this.getWidth(), y + this.getHeight(), Color.GRAY);
                     renderer.line(x + this.getWidth(), y, x, y + this.getHeight(), Color.GRAY);
                     Renderer2D.color().render();
                  }
               } else {
                  Renderer2D.color().render();
                  InventoryScreen.drawEntity(
                     renderer.drawContext,
                     (int)x,
                     (int)y,
                     (int)(x + 25.0 * this.scale.get()),
                     (int)(y + 66.0 * this.scale.get()),
                     (int)(30.0 * this.scale.get()),
                     0.0F,
                     -MathHelper.wrapDegrees(this.playerEntity.getYaw()),
                     -this.playerEntity.getPitch(),
                     this.playerEntity
                  );
                  x += 50.0 * this.scale.get();
                  y += 5.0 * this.scale.get();
                  String breakText = " | ";
                  String nameText = this.playerEntity.getName().getString();
                  Color nameColor = PlayerUtils.getPlayerColor(this.playerEntity, primaryColor);
                  int ping = EntityUtils.getPing(this.playerEntity);
                  String pingText = ping + "ms";
                  Color pingColor;
                  if (ping <= 75) {
                     pingColor = this.pingColor1.get();
                  } else if (ping <= 200) {
                     pingColor = this.pingColor2.get();
                  } else {
                     pingColor = this.pingColor3.get();
                  }

                  double dist = 0.0;
                  if (!this.isInEditor()) {
                     dist = Math.round(ReviveClient.mc.player.distanceTo(this.playerEntity) * 100.0) / 100.0;
                  }

                  String distText = dist + "m";
                  Color distColor;
                  if (dist <= 10.0) {
                     distColor = this.distColor1.get();
                  } else if (dist <= 50.0) {
                     distColor = this.distColor2.get();
                  } else {
                     distColor = this.distColor3.get();
                  }

                  String friendText = "Unknown";
                  Color friendColor = primaryColor;
                  if (Friends.get().isFriend(this.playerEntity)) {
                     friendText = "Friend";
                     friendColor = Config.get().friendColor.get();
                  } else {
                     boolean naked = true;

                     for (int position = 3; position >= 0; position--) {
                        ItemStack itemStack = this.getItem(position);
                        if (!itemStack.isEmpty()) {
                           naked = false;
                        }
                     }

                     if (naked) {
                        friendText = "Naked";
                        friendColor = GREEN;
                     } else {
                        boolean threat = false;

                        for (int positionx = 5; positionx >= 0; positionx--) {
                           ItemStack itemStack = this.getItem(positionx);
                           if (itemStack.isIn(ItemTags.SWORDS)
                              || itemStack.getItem() == Items.END_CRYSTAL
                              || itemStack.getItem() == Items.RESPAWN_ANCHOR
                              || itemStack.getItem() instanceof BedItem) {
                              threat = true;
                           }
                        }

                        if (threat) {
                           friendText = "Threat";
                           friendColor = RED;
                        }
                     }
                  }

                  TextRenderer.get().begin(0.45 * this.scale.get(), false, true);
                  double breakWidth = TextRenderer.get().getWidth(breakText);
                  double pingWidth = TextRenderer.get().getWidth(pingText);
                  double friendWidth = TextRenderer.get().getWidth(friendText);
                  TextRenderer.get().render(nameText, x, y, nameColor != null ? nameColor : primaryColor);
                  y += TextRenderer.get().getHeight();
                  TextRenderer.get().render(friendText, x, y, friendColor);
                  if (this.displayPing.get()) {
                     TextRenderer.get().render(breakText, x + friendWidth, y, secondaryColor);
                     TextRenderer.get().render(pingText, x + friendWidth + breakWidth, y, pingColor);
                     if (this.displayDistance.get()) {
                        TextRenderer.get().render(breakText, x + friendWidth + breakWidth + pingWidth, y, secondaryColor);
                        TextRenderer.get().render(distText, x + friendWidth + breakWidth + pingWidth + breakWidth, y, distColor);
                     }
                  } else if (this.displayDistance.get()) {
                     TextRenderer.get().render(breakText, x + friendWidth, y, secondaryColor);
                     TextRenderer.get().render(distText, x + friendWidth + breakWidth, y, distColor);
                  }

                  TextRenderer.get().end();
                  y += 10.0 * this.scale.get();
                  int slot = 5;
                  Matrix4fStack matrices = RenderSystem.getModelViewStack();
                  matrices.pushMatrix();
                  matrices.scale(this.scale.get().floatValue(), this.scale.get().floatValue(), 1.0F);
                  x /= this.scale.get();
                  y /= this.scale.get();
                  TextRenderer.get().begin(0.35, false, true);

                  for (int positionxx = 0; positionxx < 6; positionxx++) {
                     double armorX = x + positionxx * 20;
                     ItemStack itemStack = this.getItem(slot);
                     renderer.item(itemStack, (int)(armorX * this.scale.get()), (int)(y * this.scale.get()), this.scale.get().floatValue(), true);
                     double armorY = y + 18.0;
                     ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(itemStack);
                     List<ObjectIntPair<RegistryEntry<Enchantment>>> enchantmentsToShow = new ArrayList<>();

                     for (Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
                        if (((RegistryEntry)entry.getKey()).matches(this.displayedEnchantments.get()::contains)) {
                           enchantmentsToShow.add(new ObjectIntImmutablePair((RegistryEntry)entry.getKey(), entry.getIntValue()));
                        }
                     }

                     for (ObjectIntPair<RegistryEntry<Enchantment>> entryx : enchantmentsToShow) {
                        String enchantName = Utils.getEnchantSimpleName((RegistryEntry<Enchantment>)entryx.left(), 3) + " " + entryx.rightInt();
                        double enchX = armorX + 8.0 - TextRenderer.get().getWidth(enchantName) / 2.0;
                        TextRenderer.get()
                           .render(
                              enchantName, enchX, armorY, ((RegistryEntry)entryx.left()).isIn(EnchantmentTags.CURSE) ? RED : this.enchantmentTextColor.get()
                           );
                        armorY += TextRenderer.get().getHeight();
                     }

                     slot--;
                  }

                  TextRenderer.get().end();
                  y = (int)(this.y + 75.0 * this.scale.get());
                  x = this.x;
                  x /= this.scale.get();
                  y /= this.scale.get();
                  x += 5.0;
                  y += 5.0;
                  Renderer2D.color().begin();
                  Renderer2D.color().boxLines(x, y, 165.0, 11.0, BLACK);
                  Renderer2D.color().render();
                  x += 2.0;
                  y += 2.0;
                  float maxHealth = this.playerEntity.getMaxHealth();
                  int maxAbsorb = 16;
                  int maxTotal = (int)(maxHealth + maxAbsorb);
                  int totalHealthWidth = (int)(161.0F * maxHealth / maxTotal);
                  int totalAbsorbWidth = 161 * maxAbsorb / maxTotal;
                  float health = this.playerEntity.getHealth();
                  float absorb = this.playerEntity.getAbsorptionAmount();
                  double healthPercent = Math.min(1.0F, health / maxHealth);
                  double absorbPercent = Math.min(1.0, (double)absorb / maxAbsorb);
                  int healthWidth = (int)(totalHealthWidth * healthPercent);
                  int absorbWidth = (int)(totalAbsorbWidth * absorbPercent);
                  Renderer2D.color().begin();
                  Renderer2D.color()
                     .quad(x, y, healthWidth, 7.0, this.healthColor1.get(), this.healthColor2.get(), this.healthColor2.get(), this.healthColor1.get());
                  Renderer2D.color()
                     .quad(
                        x + healthWidth,
                        y,
                        absorbWidth,
                        7.0,
                        this.healthColor2.get(),
                        this.healthColor3.get(),
                        this.healthColor3.get(),
                        this.healthColor2.get()
                     );
                  Renderer2D.color().render();
                  matrices.popMatrix();
               }
            }
         }
      );
   }

   private ItemStack getItem(int i) {
      if (this.isInEditor()) {
         return switch (i) {
            case 0 -> Items.NETHERITE_BOOTS.getDefaultStack();
            case 1 -> Items.NETHERITE_LEGGINGS.getDefaultStack();
            case 2 -> Items.NETHERITE_CHESTPLATE.getDefaultStack();
            case 3 -> Items.NETHERITE_HELMET.getDefaultStack();
            case 4 -> Items.TOTEM_OF_UNDYING.getDefaultStack();
            case 5 -> Items.END_CRYSTAL.getDefaultStack();
            default -> ItemStack.EMPTY;
         };
      } else if (this.playerEntity == null) {
         return ItemStack.EMPTY;
      } else {
         return switch (i) {
            case 4 -> this.playerEntity.getOffHandStack();
            case 5 -> this.playerEntity.getMainHandStack();
            default -> (ItemStack)((IPlayerInventory)this.playerEntity.getInventory()).revive$getArmor().get(i);
         };
      }
   }
}
