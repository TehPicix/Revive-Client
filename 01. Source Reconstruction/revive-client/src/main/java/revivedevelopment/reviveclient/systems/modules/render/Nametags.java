package revivedevelopment.reviveclient.systems.modules.render;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3d;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.render.Render2DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnchantmentListSetting;
import revivedevelopment.reviveclient.settings.EntityTypeListSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.entity.EntityUtils;
import revivedevelopment.reviveclient.utils.entity.fakeplayer.FakePlayerEntity;
import revivedevelopment.reviveclient.utils.misc.Names;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.render.NametagUtils;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class Nametags extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPlayers = this.settings.createGroup("Players");
   private final SettingGroup sgItems = this.settings.createGroup("Items");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(
         new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Select entities to draw nametags on.")
            .defaultValue(EntityType.PLAYER, EntityType.ITEM)
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
      .add(new DoubleSetting.Builder().name("scale").description("The scale of the nametag.").defaultValue(1.1).min(0.1).build());
   private final Setting<Boolean> ignoreSelf = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-self").description("Ignore yourself when in third person or freecam.").defaultValue(true).build());
   private final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-friends").description("Ignore rendering nametags for friends.").defaultValue(false).build());
   private final Setting<Boolean> ignoreBots = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-bots").description("Only render non-bot nametags.").defaultValue(true).build());
   private final Setting<Boolean> culling = this.sgGeneral
      .add(new BoolSetting.Builder().name("culling").description("Only render a certain number of nametags at a certain distance.").defaultValue(false).build());
   private final Setting<Double> maxCullRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("culling-range")
            .description("Only render nametags within this distance of your player.")
            .defaultValue(20.0)
            .min(0.0)
            .sliderMax(200.0)
            .visible(this.culling::get)
            .build()
      );
   private final Setting<Integer> maxCullCount = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("culling-count")
            .description("Only render this many nametags.")
            .defaultValue(50)
            .min(1)
            .sliderRange(1, 100)
            .visible(this.culling::get)
            .build()
      );
   private final Setting<Boolean> displayHealth = this.sgPlayers
      .add(new BoolSetting.Builder().name("health").description("Shows the player's health.").defaultValue(true).build());
   private final Setting<Boolean> displayTotemPops = this.sgPlayers
      .add(
         new BoolSetting.Builder()
            .name("totem-pops")
            .description("Shows how many totems the player has popped (shown as -N, hidden at 0).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> displayGameMode = this.sgPlayers
      .add(new BoolSetting.Builder().name("gamemode").description("Shows the player's GameMode.").defaultValue(false).build());
   private final Setting<Boolean> displayDistance = this.sgPlayers
      .add(new BoolSetting.Builder().name("distance").description("Shows the distance between you and the player.").defaultValue(false).build());
   private final Setting<Boolean> displayPing = this.sgPlayers
      .add(new BoolSetting.Builder().name("ping").description("Shows the player's ping.").defaultValue(true).build());
   private final Setting<Boolean> displayItems = this.sgPlayers
      .add(new BoolSetting.Builder().name("items").description("Displays armor and hand items above the name tags.").defaultValue(true).build());
   private final Setting<Double> itemSpacing = this.sgPlayers
      .add(
         new DoubleSetting.Builder()
            .name("item-spacing")
            .description("The spacing between items.")
            .defaultValue(2.0)
            .range(0.0, 10.0)
            .visible(this.displayItems::get)
            .build()
      );
   private final Setting<Boolean> ignoreEmpty = this.sgPlayers
      .add(
         new BoolSetting.Builder()
            .name("ignore-empty-slots")
            .description("Doesn't add spacing where an empty item stack would be.")
            .defaultValue(true)
            .visible(this.displayItems::get)
            .build()
      );
   private final Setting<Nametags.Durability> itemDurability = this.sgPlayers
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("durability"))
                     .description("Displays item durability as either a total, percentage, or neither."))
                  .defaultValue(Nametags.Durability.None))
               .visible(this.displayItems::get))
            .build()
      );
   private final Setting<Boolean> displayEnchants = this.sgPlayers
      .add(
         new BoolSetting.Builder()
            .name("display-enchants")
            .description("Displays item enchantments on the items.")
            .defaultValue(false)
            .visible(this.displayItems::get)
            .build()
      );
   private final Setting<Set<RegistryKey<Enchantment>>> shownEnchantments = this.sgPlayers
      .add(
         new EnchantmentListSetting.Builder()
            .name("shown-enchantments")
            .description("The enchantments that are shown on nametags.")
            .visible(() -> this.displayItems.get() && this.displayEnchants.get())
            .defaultValue(Enchantments.PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.PROJECTILE_PROTECTION)
            .build()
      );
   private final Setting<Nametags.Position> enchantPos = this.sgPlayers
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("enchantment-position"))
                     .description("Where the enchantments are rendered."))
                  .defaultValue(Nametags.Position.Above))
               .visible(() -> this.displayItems.get() && this.displayEnchants.get()))
            .build()
      );
   private final Setting<Integer> enchantLength = this.sgPlayers
      .add(
         new IntSetting.Builder()
            .name("enchant-name-length")
            .description("The length enchantment names are trimmed to.")
            .defaultValue(3)
            .range(1, 5)
            .sliderRange(1, 5)
            .visible(() -> this.displayItems.get() && this.displayEnchants.get())
            .build()
      );
   private final Setting<Double> enchantTextScale = this.sgPlayers
      .add(
         new DoubleSetting.Builder()
            .name("enchant-text-scale")
            .description("The scale of the enchantment text.")
            .defaultValue(1.0)
            .range(0.1, 2.0)
            .sliderRange(0.1, 2.0)
            .visible(() -> this.displayItems.get() && this.displayEnchants.get())
            .build()
      );
   private final Setting<Boolean> itemCount = this.sgItems
      .add(new BoolSetting.Builder().name("show-count").description("Displays the number of items in the stack.").defaultValue(true).build());
   private final Setting<SettingColor> background = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("background-color")
            .description("The color of the nametag background.")
            .defaultValue(new SettingColor(0, 0, 0, 75))
            .build()
      );
   private final Setting<SettingColor> nameColor = this.sgRender
      .add(new ColorSetting.Builder().name("name-color").description("The color of the nametag names.").defaultValue(new SettingColor()).build());
   private final Setting<SettingColor> pingColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("ping-color")
            .description("The color of the nametag ping.")
            .defaultValue(new SettingColor(20, 170, 170))
            .visible(this.displayPing::get)
            .build()
      );
   private final Setting<SettingColor> totemPopsColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("totem-pops-color")
            .description("The color of the nametag totem pops.")
            .defaultValue(new SettingColor(225, 120, 20))
            .visible(this.displayTotemPops::get)
            .build()
      );
   private final Setting<SettingColor> gamemodeColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("gamemode-color")
            .description("The color of the nametag gamemode.")
            .defaultValue(new SettingColor(232, 185, 35))
            .visible(this.displayGameMode::get)
            .build()
      );
   private final Setting<Nametags.DistanceColorMode> distanceColorMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("distance-color-mode"))
                     .description("The mode to color the nametag distance with."))
                  .defaultValue(Nametags.DistanceColorMode.Gradient))
               .visible(this.displayDistance::get))
            .build()
      );
   private final Setting<SettingColor> distanceColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("distance-color")
            .description("The color of the nametag distance.")
            .defaultValue(new SettingColor(150, 150, 150))
            .visible(() -> this.displayDistance.get() && this.distanceColorMode.get() == Nametags.DistanceColorMode.Flat)
            .build()
      );
   private final Color WHITE = new Color(255, 255, 255);
   private final Color RED = new Color(255, 25, 25);
   private final Color AMBER = new Color(255, 105, 25);
   private final Color GREEN = new Color(25, 252, 25);
   private final Color GOLD = new Color(232, 185, 35);
   private final Vector3d pos = new Vector3d();
   private final double[] itemWidths = new double[6];
   private final List<Entity> entityList = new ArrayList<>();

   public Nametags() {
      super(Categories.Render, "nametags", "Displays customizable nametags above players, items and other entities.");
   }

   @Override
   public void onDeactivate() {
      this.entityList.clear();
   }

   private static String ticksToTime(int ticks) {
      if (ticks > 72000) {
         int h = ticks / 20 / 3600;
         return h + " h";
      } else if (ticks > 1200) {
         int m = ticks / 20 / 60;
         return m + " m";
      } else {
         int s = ticks / 20;
         int ms = ticks % 20 / 2;
         return s + "." + ms + " s";
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      this.entityList.clear();
      boolean notThirdPerson = !this.mc.gameRenderer.getCamera().isThirdPerson();
      Vec3d cameraPos = this.mc.gameRenderer.getCamera().getCameraPos();

      for (Entity entity : this.mc.world.getEntities()) {
         EntityType<?> type = entity.getType();
         if (this.entities.get().contains(type)
            && (
               type != EntityType.PLAYER
                  || (!this.ignoreSelf.get() && !notThirdPerson || entity != this.mc.player)
                     && (EntityUtils.getGameMode((PlayerEntity)entity) != null || !this.ignoreBots.get())
                     && (!Friends.get().isFriend((PlayerEntity)entity) || !this.ignoreFriends.get())
            )
            && (!this.culling.get() || PlayerUtils.isWithinCamera(entity, this.maxCullRange.get()))) {
            this.entityList.add(entity);
         }
      }

      this.entityList.sort(Comparator.comparing(e -> e.squaredDistanceTo(cameraPos)));
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      int count = this.getRenderCount();
      boolean shadow = Config.get().customFont.get();

      for (int i = count - 1; i > -1; i--) {
         Entity entity = this.entityList.get(i);
         Utils.set(this.pos, entity, event.tickDelta);
         this.pos.add(0.0, this.getHeight(entity), 0.0);
         EntityType<?> type = entity.getType();
         if (NametagUtils.to2D(this.pos, this.scale.get())) {
            DrawContext ctx = event.drawContext;
            if (type == EntityType.PLAYER) {
               this.renderNametagPlayer(event, (PlayerEntity)entity, shadow);
            } else if (type == EntityType.ITEM) {
               this.renderNametagItem(ctx, ((ItemEntity)entity).getStack(), shadow);
            } else if (type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME) {
               this.renderNametagItem(ctx, ((ItemFrameEntity)entity).getHeldItemStack(), shadow);
            } else if (type == EntityType.TNT) {
               this.renderTntNametag(ctx, ticksToTime(((TntEntity)entity).getFuse()), shadow);
            } else if (type == EntityType.TNT_MINECART && ((TntMinecartEntity)entity).isPrimed()) {
               this.renderTntNametag(ctx, ticksToTime(((TntMinecartEntity)entity).getFuseTicks()), shadow);
            } else if (entity instanceof LivingEntity) {
               this.renderGenericLivingNametag(ctx, (LivingEntity)entity, shadow);
            } else {
               this.renderGenericNametag(ctx, entity, shadow);
            }
         }
      }
   }

   private int getRenderCount() {
      int count = this.culling.get() ? this.maxCullCount.get() : this.entityList.size();
      return MathHelper.clamp(count, 0, this.entityList.size());
   }

   @Override
   public String getInfoString() {
      return Integer.toString(this.getRenderCount());
   }

   private double getHeight(Entity entity) {
      double height = entity.getEyeHeight(entity.getPose());
      if (entity.getType() != EntityType.ITEM && entity.getType() != EntityType.ITEM_FRAME && entity.getType() != EntityType.GLOW_ITEM_FRAME) {
         height += 0.5;
      } else {
         height += 0.2;
      }

      return height;
   }

   private double ctxTextWidth(String text, boolean shadow, double textScale) {
      double s = textScale * 2.0;
      return (this.mc.textRenderer.getWidth(text) + (shadow ? 1 : 0)) * s;
   }

   private double ctxTextHeight(boolean shadow, double textScale) {
      double s = textScale * 2.0;
      return (9 + (shadow ? 1 : 0)) * s;
   }

   private double ctxText(DrawContext ctx, String text, double x, double y, Color color, boolean shadow, double textScale) {
      double s = textScale * 2.0;
      Matrix3x2fStack matrices = ctx.getMatrices();
      matrices.pushMatrix();
      matrices.scale((float)s, (float)s);
      ctx.drawText(this.mc.textRenderer, text, (int)Math.round(x / s), (int)Math.round(y / s), color.getPacked(), shadow);
      matrices.popMatrix();
      return x + this.mc.textRenderer.getWidth(text) * s;
   }

   private void ctxFill(DrawContext ctx, double x, double y, double width, double height, Color color) {
      ctx.fill((int)Math.round(x), (int)Math.round(y), (int)Math.round(x + width), (int)Math.round(y + height), color.getPacked());
   }

   private void renderNametagPlayer(Render2DEvent event, PlayerEntity player, boolean shadow) {
      DrawContext ctx = event.drawContext;
      NametagUtils.begin(this.pos, ctx);
      GameMode gm = EntityUtils.getGameMode(player);
      String gmText = "BOT";
      if (gm != null) {
         gmText = switch (gm) {
            case SPECTATOR -> "Sp";
            case SURVIVAL -> "S";
            case CREATIVE -> "C";
            case ADVENTURE -> "A";
            default -> throw new MatchException(null, null);
         };
      }

      gmText = "[" + gmText + "] ";
      Color nameColor = PlayerUtils.getPlayerColor(player, this.nameColor.get());
      String name = player.getName().getString();
      float absorption = player.getAbsorptionAmount();
      int health = Math.round(player.getHealth() + absorption);
      double healthPercentage = health / (player.getMaxHealth() + absorption);
      String healthText = " " + health;
      Color healthColor;
      if (healthPercentage <= 0.333) {
         healthColor = this.RED;
      } else if (healthPercentage <= 0.666) {
         healthColor = this.AMBER;
      } else {
         healthColor = this.GREEN;
      }

      int pops = player instanceof FakePlayerEntity fake ? fake.totemPops : ReviveClient.INFO.getPops(player);
      boolean renderPops = this.displayTotemPops.get() && pops > 0;
      String popsText = " -" + pops;
      int ping = EntityUtils.getPing(player);
      String pingText = " [" + ping + "ms]";
      double dist = Math.round(PlayerUtils.distanceToCamera(player) * 10.0) / 10.0;
      String distText = " " + dist + "m";
      double gmWidth = this.ctxTextWidth(gmText, shadow, 1.0);
      double nameWidth = this.ctxTextWidth(name, shadow, 1.0);
      double healthWidth = this.ctxTextWidth(healthText, shadow, 1.0);
      double popsWidth = this.ctxTextWidth(popsText, shadow, 1.0);
      double pingWidth = this.ctxTextWidth(pingText, shadow, 1.0);
      double distWidth = this.ctxTextWidth(distText, shadow, 1.0);
      double width = nameWidth;
      boolean renderPlayerDistance = player != this.mc.getCameraEntity();
      if (this.displayHealth.get()) {
         width = nameWidth + healthWidth;
      }

      if (renderPops) {
         width += popsWidth;
      }

      if (this.displayGameMode.get()) {
         width += gmWidth;
      }

      if (this.displayPing.get()) {
         width += pingWidth;
      }

      if (this.displayDistance.get() && renderPlayerDistance) {
         width += distWidth;
      }

      double widthHalf = width / 2.0;
      double heightDown = this.ctxTextHeight(shadow, 1.0);
      this.drawBg(ctx, -widthHalf, -heightDown, width, heightDown);
      double hX = -widthHalf;
      double hY = -heightDown;
      if (this.displayGameMode.get()) {
         hX = this.ctxText(ctx, gmText, hX, hY, this.gamemodeColor.get(), shadow, 1.0);
      }

      hX = this.ctxText(ctx, name, hX, hY, nameColor, shadow, 1.0);
      if (this.displayHealth.get()) {
         hX = this.ctxText(ctx, healthText, hX, hY, healthColor, shadow, 1.0);
      }

      if (renderPops) {
         hX = this.ctxText(ctx, popsText, hX, hY, this.totemPopsColor.get(), shadow, 1.0);
      }

      if (this.displayPing.get()) {
         hX = this.ctxText(ctx, pingText, hX, hY, this.pingColor.get(), shadow, 1.0);
      }

      if (this.displayDistance.get() && renderPlayerDistance) {
         switch ((Nametags.DistanceColorMode)this.distanceColorMode.get()) {
            case Gradient:
               this.ctxText(ctx, distText, hX, hY, EntityUtils.getColorFromDistance(player), shadow, 1.0);
               break;
            case Flat:
               this.ctxText(ctx, distText, hX, hY, this.distanceColor.get(), shadow, 1.0);
         }
      }

      if (this.displayItems.get()) {
         double enchantScale = 0.5 * this.enchantTextScale.get();
         Arrays.fill(this.itemWidths, 0.0);
         boolean hasItems = false;
         int maxEnchantCount = 0;

         for (int i = 0; i < 6; i++) {
            ItemStack itemStack = this.getItem(player, i);
            if (this.itemWidths[i] == 0.0 && (!this.ignoreEmpty.get() || !itemStack.isEmpty())) {
               this.itemWidths[i] = 32.0 + this.itemSpacing.get();
            }

            if (!itemStack.isEmpty()) {
               hasItems = true;
            }

            if (this.displayEnchants.get()) {
               ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(itemStack);
               int size = 0;

               for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
                  if (!enchantment.getKey().isPresent() || this.shownEnchantments.get().contains(enchantment.getKey().get())) {
                     String enchantName = Utils.getEnchantSimpleName(enchantment, this.enchantLength.get()) + " " + enchantments.getLevel(enchantment);
                     this.itemWidths[i] = Math.max(this.itemWidths[i], this.ctxTextWidth(enchantName, shadow, enchantScale));
                     size++;
                  }
               }

               maxEnchantCount = Math.max(maxEnchantCount, size);
            }
         }

         double itemsHeight = hasItems ? 32 : 0;
         double itemWidthTotal = 0.0;

         for (double w : this.itemWidths) {
            itemWidthTotal += w;
         }

         double itemWidthHalf = itemWidthTotal / 2.0;
         double y = -heightDown - 7.0 - itemsHeight;
         double x = -itemWidthHalf;

         for (int i = 0; i < 6; i++) {
            ItemStack stack = this.getItem(player, i);
            RenderUtils.drawItem(ctx, stack, (int)x, (int)y, 2.0F, true, null, false);
            if (stack.isDamageable() && this.itemDurability.get() != Nametags.Durability.None) {
               String damageText = switch ((Nametags.Durability)this.itemDurability.get()) {
                  case Total -> Integer.toString(stack.getMaxDamage() - stack.getDamage());
                  case Percentage -> String.format("%.0f%%", (stack.getMaxDamage() - stack.getDamage()) * 100.0F / stack.getMaxDamage());
                  default -> "err";
               };
               Color damageColor = new Color(stack.getItemBarColor());
               this.ctxText(ctx, damageText, (int)x, (int)y, damageColor.a(255), true, 0.75);
            }

            if (maxEnchantCount > 0 && this.displayEnchants.get()) {
               ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
               Object2IntMap<RegistryEntry<Enchantment>> enchantmentsToShow = new Object2IntOpenHashMap();

               for (RegistryEntry<Enchantment> enchantmentx : enchantments.getEnchantments()) {
                  if (enchantmentx.matches(this.shownEnchantments.get()::contains)) {
                     enchantmentsToShow.put(enchantmentx, enchantments.getLevel(enchantmentx));
                  }
               }

               double aW = this.itemWidths[i];
               double enchantY = 0.0;
               double enchantLineHeight = this.ctxTextHeight(shadow, enchantScale);

               double addY = switch ((Nametags.Position)this.enchantPos.get()) {
                  case Above -> -((enchantmentsToShow.size() + 1) * enchantLineHeight);
                  case OnTop -> (itemsHeight - enchantmentsToShow.size() * enchantLineHeight) / 2.0;
               };

               for (ObjectIterator var74 = Object2IntMaps.fastIterable(enchantmentsToShow).iterator(); var74.hasNext(); enchantY += enchantLineHeight) {
                  Entry<RegistryEntry<Enchantment>> entry = (Entry<RegistryEntry<Enchantment>>)var74.next();
                  String enchantName = Utils.getEnchantSimpleName((RegistryEntry<Enchantment>)entry.getKey(), this.enchantLength.get())
                     + " "
                     + entry.getIntValue();
                  Color enchantColor = this.WHITE;
                  if (((RegistryEntry)entry.getKey()).isIn(EnchantmentTags.CURSE)) {
                     enchantColor = this.RED;
                  }

                  double enchantWidth = this.ctxTextWidth(enchantName, shadow, enchantScale);

                  double enchantX = switch ((Nametags.Position)this.enchantPos.get()) {
                     case Above -> x + aW / 2.0 - enchantWidth / 2.0;
                     case OnTop -> x + (aW - enchantWidth) / 2.0;
                  };
                  this.ctxText(ctx, enchantName, enchantX, y + addY + enchantY, enchantColor, shadow, enchantScale);
               }
            }

            x += this.itemWidths[i];
         }
      } else if (this.displayEnchants.get()) {
         this.displayEnchants.set(false);
      }

      NametagUtils.end(ctx);
   }

   private void renderNametagItem(DrawContext ctx, ItemStack stack, boolean shadow) {
      if (!stack.isEmpty()) {
         NametagUtils.begin(this.pos, ctx);
         String name = Names.get(stack);
         String count = " x" + stack.getCount();
         double nameWidth = this.ctxTextWidth(name, shadow, 1.0);
         double countWidth = this.ctxTextWidth(count, shadow, 1.0);
         double heightDown = this.ctxTextHeight(shadow, 1.0);
         double width = nameWidth;
         if (this.itemCount.get()) {
            width = nameWidth + countWidth;
         }

         double widthHalf = width / 2.0;
         this.drawBg(ctx, -widthHalf, -heightDown, width, heightDown);
         double hX = -widthHalf;
         double hY = -heightDown;
         hX = this.ctxText(ctx, name, hX, hY, this.nameColor.get(), shadow, 1.0);
         if (this.itemCount.get()) {
            this.ctxText(ctx, count, hX, hY, this.GOLD, shadow, 1.0);
         }

         NametagUtils.end(ctx);
      }
   }

   private void renderGenericLivingNametag(DrawContext ctx, LivingEntity entity, boolean shadow) {
      NametagUtils.begin(this.pos, ctx);
      String nameText = entity.getType().getName().getString();
      nameText = nameText + " ";
      float absorption = entity.getAbsorptionAmount();
      int health = Math.round(entity.getHealth() + absorption);
      double healthPercentage = health / (entity.getMaxHealth() + absorption);
      String healthText = String.valueOf(health);
      Color healthColor;
      if (healthPercentage <= 0.333) {
         healthColor = this.RED;
      } else if (healthPercentage <= 0.666) {
         healthColor = this.AMBER;
      } else {
         healthColor = this.GREEN;
      }

      double nameWidth = this.ctxTextWidth(nameText, shadow, 1.0);
      double healthWidth = this.ctxTextWidth(healthText, shadow, 1.0);
      double heightDown = this.ctxTextHeight(shadow, 1.0);
      double width = nameWidth + healthWidth;
      double widthHalf = width / 2.0;
      this.drawBg(ctx, -widthHalf, -heightDown, width, heightDown);
      double hX = -widthHalf;
      double hY = -heightDown;
      hX = this.ctxText(ctx, nameText, hX, hY, this.nameColor.get(), shadow, 1.0);
      this.ctxText(ctx, healthText, hX, hY, healthColor, shadow, 1.0);
      NametagUtils.end(ctx);
   }

   private void renderGenericNametag(DrawContext ctx, Entity entity, boolean shadow) {
      NametagUtils.begin(this.pos, ctx);
      String nameText = entity.getType().getName().getString();
      double nameWidth = this.ctxTextWidth(nameText, shadow, 1.0);
      double heightDown = this.ctxTextHeight(shadow, 1.0);
      double widthHalf = nameWidth / 2.0;
      this.drawBg(ctx, -widthHalf, -heightDown, nameWidth, heightDown);
      this.ctxText(ctx, nameText, -widthHalf, -heightDown, this.nameColor.get(), shadow, 1.0);
      NametagUtils.end(ctx);
   }

   private void renderTntNametag(DrawContext ctx, String fuseText, boolean shadow) {
      NametagUtils.begin(this.pos, ctx);
      double width = this.ctxTextWidth(fuseText, shadow, 1.0);
      double heightDown = this.ctxTextHeight(shadow, 1.0);
      double widthHalf = width / 2.0;
      this.drawBg(ctx, -widthHalf, -heightDown, width, heightDown);
      this.ctxText(ctx, fuseText, -widthHalf, -heightDown, this.nameColor.get(), shadow, 1.0);
      NametagUtils.end(ctx);
   }

   private ItemStack getItem(PlayerEntity entity, int index) {
      return switch (index) {
         case 0 -> entity.getMainHandStack();
         case 1 -> entity.getEquippedStack(EquipmentSlot.HEAD);
         case 2 -> entity.getEquippedStack(EquipmentSlot.CHEST);
         case 3 -> entity.getEquippedStack(EquipmentSlot.LEGS);
         case 4 -> entity.getEquippedStack(EquipmentSlot.FEET);
         case 5 -> entity.getOffHandStack();
         default -> ItemStack.EMPTY;
      };
   }

   private void drawBg(DrawContext ctx, double x, double y, double width, double height) {
      this.ctxFill(ctx, x - 1.0, y - 1.0, width + 2.0, height + 2.0, this.background.get());
   }

   public boolean excludeBots() {
      return this.ignoreBots.get();
   }

   public boolean playerNametags() {
      return this.isActive() && this.entities.get().contains(EntityType.PLAYER);
   }

   public static enum DistanceColorMode {
      Gradient,
      Flat;
   }

   public static enum Durability {
      None,
      Total,
      Percentage;
   }

   public static enum Position {
      Above,
      OnTop;
   }
}
