package revivedevelopment.reviveclient.systems.modules.hunting;

import java.util.WeakHashMap;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class LoreLocator extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRares = this.settings.createGroup("Rares Settings");
   private final SettingGroup sgUniques = this.settings.createGroup("Uniques Settings");
   private final Setting<Boolean> illegalEnchants = this.sgRares
      .add(
         new BoolSetting.Builder()
            .name("illegal-enchants")
            .description("Highlight items with illegal enchantments like Mending/Infinity, or stacked Protection.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> onlySilkyShears = this.sgRares
      .add(
         new BoolSetting.Builder()
            .name("exclusive-silky-shears")
            .description("Highlight silk touch shears only if they have no other enchants.")
            .defaultValue(true)
            .visible(this.illegalEnchants::get)
            .build()
      );
   private final Setting<Boolean> onlyInfinityMending = this.sgRares
      .add(
         new BoolSetting.Builder()
            .name("exclusive-mending-infinity")
            .description("Highlight bows & books that have ONLY mending & infinity applied.")
            .defaultValue(true)
            .visible(this.illegalEnchants::get)
            .build()
      );
   private final Setting<Boolean> negativeDurability = this.sgRares
      .add(
         new BoolSetting.Builder()
            .name("negative-durability")
            .description("Highlight items with negative true durability (they show in-game as 0 durability in 1.21).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> petrifiedSlabs = this.sgRares
      .add(new BoolSetting.Builder().name("alpha-slabs").description("Highlight alpha slabs (now petrified oak slabs).").defaultValue(true).build());
   private final Setting<Boolean> lagRockets = this.sgRares
      .add(new BoolSetting.Builder().name("lag-rockets").description("Highlight lag rockets.").defaultValue(true).build());
   private final Setting<Boolean> illegalFish = this.sgRares
      .add(
         new BoolSetting.Builder()
            .name("illegal-fish")
            .description("Highlight illegal tropical fish with black as one of their colors. No longer obtainable as of 1.21.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> renamedItems = this.sgUniques
      .add(new BoolSetting.Builder().name("renamed-items").description("Highlight renamed items in GUIs.").defaultValue(false).build());
   private final Setting<Boolean> writtenBooks = this.sgUniques
      .add(new BoolSetting.Builder().name("written-books").description("Highlight written books.").defaultValue(true).build());
   private final Setting<String> metadataSearch = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("metadata-search")
            .description("Fuzzy search for item NBT data (book authors, item names, enchants).")
            .defaultValue("")
            .build()
      );
   private final Setting<Boolean> splitQueries = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("split-queries")
            .description("Split search queries into multiple items separated by commas. Disable to treat commas literally.")
            .defaultValue(true)
            .build()
      );
   public final Setting<SettingColor> color = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("highlight-color")
            .description("Highlight color for matched slots.")
            .defaultValue(new SettingColor(254, 0, 255, 119))
            .build()
      );
   private final Setting<Boolean> ownInventory = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("inventory-highlight")
            .description("Highlight matching items on the player inventory screen.")
            .defaultValue(true)
            .build()
      );
   private static final String[] NO_QUERIES = new String[0];
   private final WeakHashMap<ItemStack, Boolean> highlightCache = new WeakHashMap<>();
   private int cachedFlags = -1;
   private String cachedQuery = null;
   private String[] cachedQueries = NO_QUERIES;
   private String[] cachedNbtQueries = NO_QUERIES;

   public LoreLocator() {
      super(Categories.Hunting, "lore-locator", "Slot highlighter for rare, unique, and anomalous items.");
   }

   private int enchantmentsCount(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0;
      } else {
         return stack.getItem() == Items.ENCHANTED_BOOK
            ? ((ItemEnchantmentsComponent)stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT))
               .getEnchantmentEntries()
               .size()
            : stack.getEnchantments().getEnchantmentEntries().size();
      }
   }

   private boolean shouldIgnoreCurrentScreenHandler(ClientPlayerEntity player) {
      if (this.mc.currentScreen == null) {
         return true;
      } else if (player.currentScreenHandler == null) {
         return true;
      } else {
         ScreenHandler handler = player.currentScreenHandler;
         return handler instanceof PlayerScreenHandler
            ? !this.ownInventory.get()
            : !(handler instanceof AbstractFurnaceScreenHandler)
               && !(handler instanceof GenericContainerScreenHandler)
               && !(handler instanceof Generic3x3ContainerScreenHandler)
               && !(handler instanceof ShulkerBoxScreenHandler)
               && !(handler instanceof HopperScreenHandler)
               && !(handler instanceof HorseScreenHandler);
      }
   }

   private int settingsFlags() {
      return (this.illegalEnchants.get() ? 1 : 0)
         | (this.onlySilkyShears.get() ? 2 : 0)
         | (this.onlyInfinityMending.get() ? 4 : 0)
         | (this.negativeDurability.get() ? 8 : 0)
         | (this.petrifiedSlabs.get() ? 16 : 0)
         | (this.lagRockets.get() ? 32 : 0)
         | (this.illegalFish.get() ? 64 : 0)
         | (this.renamedItems.get() ? 128 : 0)
         | (this.writtenBooks.get() ? 256 : 0)
         | (this.splitQueries.get() ? 512 : 0);
   }

   private void refreshCache() {
      int flags = this.settingsFlags();
      String query = this.metadataSearch.get();
      if (flags != this.cachedFlags || !query.equals(this.cachedQuery)) {
         this.cachedFlags = flags;
         this.cachedQuery = query;
         String lowerCase = query.toLowerCase();
         if (lowerCase.trim().isEmpty()) {
            this.cachedQueries = NO_QUERIES;
            this.cachedNbtQueries = NO_QUERIES;
         } else {
            String[] queries = this.splitQueries.get() && lowerCase.contains(",") ? lowerCase.split(",") : new String[]{lowerCase};
            this.cachedQueries = new String[queries.length];
            this.cachedNbtQueries = new String[queries.length];

            for (int i = 0; i < queries.length; i++) {
               this.cachedQueries[i] = queries[i].trim();
               this.cachedNbtQueries[i] = this.cachedQueries[i].replace(" ", "_");
            }
         }

         this.highlightCache.clear();
      }
   }

   public boolean shouldHighlightSlot(ItemStack stack) {
      if (this.mc.player == null) {
         return false;
      } else if (!stack.isEmpty() && !this.shouldIgnoreCurrentScreenHandler(this.mc.player)) {
         this.refreshCache();
         Boolean cached = this.highlightCache.get(stack);
         if (cached != null) {
            return cached;
         } else {
            boolean result = this.computeHighlight(stack);
            this.highlightCache.put(stack, result);
            return result;
         }
      } else {
         return false;
      }
   }

   private boolean computeHighlight(ItemStack stack) {
      if (this.matchesItemDirect(stack)) {
         return true;
      } else {
         if (Utils.hasItems(stack)) {
            ItemStack[] stacks = new ItemStack[27];
            Utils.getItemsInContainerItem(stack, stacks);

            for (ItemStack s : stacks) {
               if (s != null && !s.isEmpty() && this.computeHighlight(s)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private boolean matchesMetadataSearch(ItemStack stack) {
      ComponentMap metadata = stack.getComponents();
      if (metadata != null) {
         String components = metadata.toString().toLowerCase();

         for (int i = 0; i < this.cachedQueries.length; i++) {
            if (components.contains(this.cachedQueries[i]) || components.contains(this.cachedNbtQueries[i])) {
               return true;
            }
         }
      }

      String name = stack.getName().getString().toLowerCase();
      String defaultName = stack.getItem().getDefaultStack().getName().getString().toLowerCase();

      for (String query : this.cachedQueries) {
         if (name.contains(query) || defaultName.contains(query)) {
            return true;
         }
      }

      return false;
   }

   private boolean matchesItemDirect(ItemStack stack) {
      if (this.cachedQueries.length > 0 && this.matchesMetadataSearch(stack)) {
         return true;
      } else {
         if (this.lagRockets.get() && stack.contains(DataComponentTypes.FIREWORKS)) {
            FireworksComponent firework = (FireworksComponent)stack.get(DataComponentTypes.FIREWORKS);
            if (firework != null && firework.explosions().size() == 7) {
               return true;
            }
         }

         if (this.illegalFish.get() && stack.isOf(Items.TROPICAL_FISH_BUCKET)) {
            NbtComponent data = (NbtComponent)stack.get(DataComponentTypes.BUCKET_ENTITY_DATA);
            if (data != null && !data.isEmpty()) {
               NbtCompound nbt = data.copyNbt();
               int variant = nbt.getInt("BucketVariantTag", 0);
               int baseColor = variant >> 16 & 0xFF;
               int patternColor = variant >> 24 & 0xFF;
               if (baseColor == 15 || patternColor == 15) {
                  return true;
               }
            }
         }

         if (this.writtenBooks.get() && stack.getItem() == Items.WRITTEN_BOOK) {
            return true;
         } else if (this.petrifiedSlabs.get() && stack.getItem() == Items.PETRIFIED_OAK_SLAB) {
            return true;
         } else if (this.renamedItems.get() && stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            return true;
         } else if (this.negativeDurability.get()
            && stack.isDamageable()
            && (Integer)stack.getOrDefault(DataComponentTypes.DAMAGE, stack.getDamage()) >= stack.getMaxDamage()) {
            return true;
         } else {
            if (this.illegalEnchants.get() && (stack.getItem() == Items.ENCHANTED_BOOK || !stack.getEnchantments().getEnchantmentEntries().isEmpty())) {
               int enchantmentsCount = this.enchantmentsCount(stack);
               if (stack.getItem() == Items.SHEARS && Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH)) {
                  return enchantmentsCount == 1 || !this.onlySilkyShears.get();
               }

               if (stack.getItem() == Items.ENCHANTED_BOOK && (enchantmentsCount == 0 || enchantmentsCount > 7)) {
                  return true;
               }

               boolean hasProtection = Utils.hasEnchantment(stack, Enchantments.PROTECTION);
               if (Utils.hasEnchantment(stack, Enchantments.FIRE_PROTECTION)) {
                  if (hasProtection) {
                     return true;
                  }

                  hasProtection = true;
               }

               if (Utils.hasEnchantment(stack, Enchantments.BLAST_PROTECTION)) {
                  if (hasProtection) {
                     return true;
                  }

                  hasProtection = true;
               }

               if (Utils.hasEnchantment(stack, Enchantments.PROJECTILE_PROTECTION) && hasProtection) {
                  return true;
               }

               if (Utils.hasEnchantment(stack, Enchantments.INFINITY) && Utils.hasEnchantment(stack, Enchantments.MENDING)) {
                  return enchantmentsCount == 2 || !this.onlyInfinityMending.get();
               }
            }

            return false;
         }
      }
   }
}
