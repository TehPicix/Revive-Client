package revivedevelopment.reviveclient.utils;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.resource.ResourceReloadLogger.ReloadState;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.inventory.StackWithSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.DyeColor;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Range;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.gui.newgui.NewGuiScreen;
import revivedevelopment.reviveclient.gui.newgui.modern.ModernGuiScreen;
import revivedevelopment.reviveclient.mixin.ContainerComponentAccessor;
import revivedevelopment.reviveclient.mixin.MinecraftClientAccessor;
import revivedevelopment.reviveclient.mixin.MinecraftServerAccessor;
import revivedevelopment.reviveclient.mixin.ProjectionMatrix2Accessor;
import revivedevelopment.reviveclient.mixin.ReloadStateAccessor;
import revivedevelopment.reviveclient.mixin.ResourceReloadLoggerAccessor;
import revivedevelopment.reviveclient.mixininterface.IMinecraftClient;
import revivedevelopment.reviveclient.settings.StatusEffectAmplifierMapSetting;
import revivedevelopment.reviveclient.utils.misc.Names;
import revivedevelopment.reviveclient.utils.player.EChestMemory;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.world.BlockEntityIterator;
import revivedevelopment.reviveclient.utils.world.ChunkIterator;

public class Utils {
   public static final Pattern FILE_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[\\s\\\\/:*?\"<>|]");
   public static final Color WHITE = new Color(255, 255, 255);
   private static final Random random = new Random();
   public static boolean rendering3D = true;
   private static ProjectionMatrix2 matrix;
   public static double frameTime;
   public static Screen screenToOpen;

   private static ProjectionMatrix2 getMatrix() {
      if (matrix == null) {
         matrix = new ProjectionMatrix2("revive-projection-matrix", -10.0F, 100.0F, true);
      }

      return matrix;
   }

   private Utils() {
   }

   @PreInit
   public static void init() {
      ReviveClient.EVENT_BUS.subscribe(Utils.class);
   }

   @EventHandler
   private static void onTick(TickEvent.Post event) {
      if (screenToOpen != null && ReviveClient.mc.currentScreen == null) {
         ReviveClient.mc.setScreen(screenToOpen);
         screenToOpen = null;
      }
   }

   public static Vec3d getPlayerSpeed() {
      if (ReviveClient.mc.player == null) {
         return Vec3d.ZERO;
      } else {
         double tX = ReviveClient.mc.player.getX() - ReviveClient.mc.player.lastX;
         double tY = ReviveClient.mc.player.getY() - ReviveClient.mc.player.lastY;
         double tZ = ReviveClient.mc.player.getZ() - ReviveClient.mc.player.lastZ;
         tX *= 20.0;
         tY *= 20.0;
         tZ *= 20.0;
         return new Vec3d(tX, tY, tZ);
      }
   }

   public static String getWorldTime() {
      if (ReviveClient.mc.world == null) {
         return "00:00";
      } else {
         int ticks = (int)(ReviveClient.mc.world.getTimeOfDay() % 24000L);
         ticks += 6000;
         if (ticks > 24000) {
            ticks -= 24000;
         }

         return String.format("%02d:%02d", ticks / 1000, (int)(ticks % 1000 / 1000.0 * 60.0));
      }
   }

   public static Iterable<Chunk> chunks(boolean onlyWithLoadedNeighbours) {
      return () -> new ChunkIterator(onlyWithLoadedNeighbours);
   }

   public static Iterable<Chunk> chunks() {
      return chunks(false);
   }

   public static Iterable<BlockEntity> blockEntities() {
      return BlockEntityIterator::new;
   }

   public static void getEnchantments(ItemStack itemStack, Object2IntMap<RegistryEntry<Enchantment>> enchantments) {
      enchantments.clear();
      if (!itemStack.isEmpty()) {
         for (Entry<RegistryEntry<Enchantment>> entry : itemStack.getItem() == Items.ENCHANTED_BOOK
            ? ((ItemEnchantmentsComponent)itemStack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT))
               .getEnchantmentEntries()
            : itemStack.getEnchantments().getEnchantmentEntries()) {
            enchantments.put((RegistryEntry)entry.getKey(), entry.getIntValue());
         }
      }
   }

   public static int getEnchantmentLevel(ItemStack itemStack, RegistryKey<Enchantment> enchantment) {
      if (itemStack.isEmpty()) {
         return 0;
      } else {
         Object2IntMap<RegistryEntry<Enchantment>> itemEnchantments = new Object2IntArrayMap();
         getEnchantments(itemStack, itemEnchantments);
         return getEnchantmentLevel(itemEnchantments, enchantment);
      }
   }

   public static int getEnchantmentLevel(Object2IntMap<RegistryEntry<Enchantment>> itemEnchantments, RegistryKey<Enchantment> enchantment) {
      ObjectIterator var2 = Object2IntMaps.fastIterable(itemEnchantments).iterator();

      while (var2.hasNext()) {
         Entry<RegistryEntry<Enchantment>> entry = (Entry<RegistryEntry<Enchantment>>)var2.next();
         if (((RegistryEntry)entry.getKey()).matchesKey(enchantment)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   @SafeVarargs
   public static boolean hasEnchantments(ItemStack itemStack, RegistryKey<Enchantment>... enchantments) {
      if (itemStack.isEmpty()) {
         return false;
      } else {
         Object2IntMap<RegistryEntry<Enchantment>> itemEnchantments = new Object2IntArrayMap();
         getEnchantments(itemStack, itemEnchantments);

         for (RegistryKey<Enchantment> enchantment : enchantments) {
            if (!hasEnchantment(itemEnchantments, enchantment)) {
               return false;
            }
         }

         return true;
      }
   }

   public static boolean hasEnchantment(ItemStack itemStack, RegistryKey<Enchantment> enchantmentKey) {
      if (itemStack.isEmpty()) {
         return false;
      } else {
         Object2IntMap<RegistryEntry<Enchantment>> itemEnchantments = new Object2IntArrayMap();
         getEnchantments(itemStack, itemEnchantments);
         return hasEnchantment(itemEnchantments, enchantmentKey);
      }
   }

   private static boolean hasEnchantment(Object2IntMap<RegistryEntry<Enchantment>> itemEnchantments, RegistryKey<Enchantment> enchantmentKey) {
      ObjectIterator var2 = itemEnchantments.keySet().iterator();

      while (var2.hasNext()) {
         RegistryEntry<Enchantment> enchantment = (RegistryEntry<Enchantment>)var2.next();
         if (enchantment.matchesKey(enchantmentKey)) {
            return true;
         }
      }

      return false;
   }

   public static int getWindowWidth() {
      return ReviveClient.mc.getWindow() == null ? 0 : ReviveClient.mc.getWindow().getFramebufferWidth();
   }

   public static int getWindowHeight() {
      return ReviveClient.mc.getWindow() == null ? 0 : ReviveClient.mc.getWindow().getFramebufferHeight();
   }

   public static void unscaledProjection() {
      float width = ReviveClient.mc.getWindow().getFramebufferWidth();
      float height = ReviveClient.mc.getWindow().getFramebufferHeight();
      RenderSystem.setProjectionMatrix(getMatrix().set(width, height), ProjectionType.ORTHOGRAPHIC);
      RenderUtils.projection.set(((ProjectionMatrix2Accessor)getMatrix()).revive$callGetMatrix(width, height));
      rendering3D = false;
   }

   public static void scaledProjection() {
      float width = ReviveClient.mc.getWindow().getFramebufferWidth() / ReviveClient.mc.getWindow().getScaleFactor();
      float height = ReviveClient.mc.getWindow().getFramebufferHeight() / ReviveClient.mc.getWindow().getScaleFactor();
      RenderSystem.setProjectionMatrix(getMatrix().set(width, height), ProjectionType.PERSPECTIVE);
      RenderUtils.projection.set(((ProjectionMatrix2Accessor)getMatrix()).revive$callGetMatrix(width, height));
      rendering3D = true;
   }

   public static Vec3d vec3d(BlockPos pos) {
      return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
   }

   public static void getItemsInContainerItem(ItemStack itemStack, ItemStack[] items) {
      if (itemStack.getItem() == Items.ENDER_CHEST) {
         for (int i = 0; i < EChestMemory.ITEMS.size(); i++) {
            items[i] = (ItemStack)EChestMemory.ITEMS.get(i);
         }
      } else {
         Arrays.fill(items, ItemStack.EMPTY);
         ComponentMap components = itemStack.getComponents();
         if (components.contains(DataComponentTypes.CONTAINER)) {
            ContainerComponentAccessor container = (ContainerComponentAccessor)(Object)components.get(DataComponentTypes.CONTAINER);
            DefaultedList<ItemStack> stacks = container.getStacks();

            for (int i = 0; i < stacks.size(); i++) {
               if (i >= 0 && i < items.length) {
                  items[i] = (ItemStack)stacks.get(i);
               }
            }
         } else if (components.contains(DataComponentTypes.BLOCK_ENTITY_DATA)) {
            TypedEntityData<BlockEntityType<?>> blockEntityData = (TypedEntityData<BlockEntityType<?>>)components.get(DataComponentTypes.BLOCK_ENTITY_DATA);
            if (blockEntityData == null) {
               return;
            }

            NbtList nbt3 = blockEntityData.copyNbtWithoutId().getListOrEmpty("Items");

            for (int ix = 0; ix < nbt3.size(); ix++) {
               Optional<NbtCompound> compound = nbt3.getCompound(ix);
               if (!compound.isEmpty()) {
                  Optional<Byte> slot = compound.get().getByte("Slot");
                  if (!slot.isEmpty() && slot.get() >= 0 && slot.get() < items.length) {
                     switch (StackWithSlot.CODEC.parse(ReviveClient.mc.player.getRegistryManager().getOps(NbtOps.INSTANCE), (NbtElement)compound.get())) {
                        case Success<StackWithSlot> success:
                           items[slot.get()] = ((StackWithSlot)success.value()).stack();
                           break;
                        case Error<StackWithSlot> ignored:
                           items[slot.get()] = ItemStack.EMPTY;
                           break;
                        default:
                           throw new MatchException(null, null);
                     }
                  }
               }
            }
         }
      }
   }

   public static Color getShulkerColor(ItemStack shulkerItem) {
      if (shulkerItem.getItem() instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         if (block == Blocks.ENDER_CHEST) {
            return new Color(0, 50, 50);
         }

         if (block instanceof ShulkerBoxBlock shulkerBlock) {
            DyeColor dye = shulkerBlock.getColor();
            if (dye == null) {
               return WHITE;
            }

            int color = dye.getEntityColor();
            return new Color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, 255);
         }
      }

      return WHITE;
   }

   public static boolean hasItems(ItemStack itemStack) {
      ContainerComponentAccessor container = (ContainerComponentAccessor)(Object)itemStack.get(DataComponentTypes.CONTAINER);
      if (container != null && !container.getStacks().isEmpty()) {
         return true;
      } else {
         TypedEntityData<BlockEntityType<?>> blockEntityData = (TypedEntityData<BlockEntityType<?>>)itemStack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
         return blockEntityData != null && blockEntityData.contains("Items");
      }
   }

   public static Reference2IntMap<StatusEffect> createStatusEffectMap() {
      return new Reference2IntArrayMap(StatusEffectAmplifierMapSetting.EMPTY_STATUS_EFFECT_MAP);
   }

   public static String getEnchantSimpleName(RegistryEntry<Enchantment> enchantment, int length) {
      String name = Names.get(enchantment);
      return name.length() > length ? name.substring(0, length) : name;
   }

   public static boolean searchTextDefault(String text, String filter, boolean caseSensitive) {
      return searchInWords(text, filter) > 0 || searchLevenshteinDefault(text, filter, caseSensitive) < text.length() / 2;
   }

   public static int searchLevenshteinDefault(String text, String filter, boolean caseSensitive) {
      return levenshteinDistance(caseSensitive ? filter : filter.toLowerCase(Locale.ROOT), caseSensitive ? text : text.toLowerCase(Locale.ROOT), 1, 8, 8);
   }

   public static int searchInWords(String text, String filter) {
      if (filter.isEmpty()) {
         return 1;
      } else {
         int wordsFound = 0;
         text = text.toLowerCase(Locale.ROOT);
         String[] words = filter.toLowerCase(Locale.ROOT).split(" ");

         for (String word : words) {
            if (!text.contains(word)) {
               return 0;
            }

            wordsFound += StringUtils.countMatches(text, word);
         }

         return wordsFound;
      }
   }

   public static int levenshteinDistance(String from, String to, int insCost, int subCost, int delCost) {
      int textLength = from.length();
      int filterLength = to.length();
      if (textLength == 0) {
         return filterLength * insCost;
      } else if (filterLength == 0) {
         return textLength * delCost;
      } else {
         int[][] d = new int[textLength + 1][filterLength + 1];

         for (int i = 0; i <= textLength; i++) {
            d[i][0] = i * delCost;
         }

         for (int j = 0; j <= filterLength; j++) {
            d[0][j] = j * insCost;
         }

         for (int i = 1; i <= textLength; i++) {
            for (int j = 1; j <= filterLength; j++) {
               int sCost = d[i - 1][j - 1] + (from.charAt(i - 1) == to.charAt(j - 1) ? 0 : subCost);
               int dCost = d[i - 1][j] + delCost;
               int iCost = d[i][j - 1] + insCost;
               d[i][j] = Math.min(Math.min(dCost, iCost), sCost);
            }
         }

         return d[textLength][filterLength];
      }
   }

   public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
      double dX = x2 - x1;
      double dY = y2 - y1;
      double dZ = z2 - z1;
      return dX * dX + dY * dY + dZ * dZ;
   }

   public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
      double dX = x2 - x1;
      double dY = y2 - y1;
      double dZ = z2 - z1;
      return Math.sqrt(dX * dX + dY * dY + dZ * dZ);
   }

   public static String getFileWorldName() {
      return FILE_NAME_INVALID_CHARS_PATTERN.matcher(getWorldName()).replaceAll("_");
   }

   public static String getWorldName() {
      if (ReviveClient.mc.isInSingleplayer()) {
         if (ReviveClient.mc.world == null) {
            return "";
         } else if (ReviveClient.mc.getServer() == null) {
            return "FAILED_BECAUSE_LEFT_WORLD";
         } else {
            File folder = ((MinecraftServerAccessor)ReviveClient.mc.getServer())
               .getSession()
               .getWorldDirectory(ReviveClient.mc.world.getRegistryKey())
               .toFile();
            if (folder.toPath().relativize(ReviveClient.mc.runDirectory.toPath()).getNameCount() != 2) {
               folder = folder.getParentFile();
            }

            return folder.getName();
         }
      } else if (ReviveClient.mc.getCurrentServerEntry() != null) {
         return ReviveClient.mc.getCurrentServerEntry().isRealm() ? "realms" : ReviveClient.mc.getCurrentServerEntry().address;
      } else {
         return "";
      }
   }

   public static String nameToTitle(String name) {
      return Arrays.stream(name.split("-")).<CharSequence>map(StringUtils::capitalize).collect(Collectors.joining(" "));
   }

   public static String getKeyName(int key) {
      return switch (key) {
         case -1 -> "Unknown";
         case 32 -> "Space";
         case 39 -> "Apostrophe";
         case 96 -> "Grave Accent";
         case 161 -> "World 1";
         case 162 -> "World 2";
         case 256 -> "Esc";
         case 257 -> "Enter";
         case 258 -> "Tab";
         case 259 -> "Backspace";
         case 260 -> "Insert";
         case 261 -> "Delete";
         case 262 -> "Arrow Right";
         case 263 -> "Arrow Left";
         case 264 -> "Arrow Down";
         case 265 -> "Arrow Up";
         case 266 -> "Page Up";
         case 267 -> "Page Down";
         case 268 -> "Home";
         case 269 -> "End";
         case 280 -> "Caps Lock";
         case 282 -> "Num Lock";
         case 283 -> "Print Screen";
         case 284 -> "Pause";
         case 290 -> "F1";
         case 291 -> "F2";
         case 292 -> "F3";
         case 293 -> "F4";
         case 294 -> "F5";
         case 295 -> "F6";
         case 296 -> "F7";
         case 297 -> "F8";
         case 298 -> "F9";
         case 299 -> "F10";
         case 300 -> "F11";
         case 301 -> "F12";
         case 302 -> "F13";
         case 303 -> "F14";
         case 304 -> "F15";
         case 305 -> "F16";
         case 306 -> "F17";
         case 307 -> "F18";
         case 308 -> "F19";
         case 309 -> "F20";
         case 310 -> "F21";
         case 311 -> "F22";
         case 312 -> "F23";
         case 313 -> "F24";
         case 314 -> "F25";
         case 335 -> "Numpad Enter";
         case 340 -> "Left Shift";
         case 341 -> "Left Control";
         case 342 -> "Left Alt";
         case 343 -> "Left Super";
         case 344 -> "Right Shift";
         case 345 -> "Right Control";
         case 346 -> "Right Alt";
         case 347 -> "Right Super";
         case 348 -> "Menu";
         default -> {
            String keyName = GLFW.glfwGetKeyName(key, 0);
            yield keyName == null ? "Unknown" : StringUtils.capitalize(keyName);
         }
      };
   }

   public static String getButtonName(int button) {
      return switch (button) {
         case -1 -> "Unknown";
         case 0 -> "Mouse Left";
         case 1 -> "Mouse Right";
         case 2 -> "Mouse Middle";
         default -> "Mouse " + button;
      };
   }

   public static boolean canUpdate() {
      return ReviveClient.mc != null && ReviveClient.mc.world != null && ReviveClient.mc.player != null;
   }

   public static boolean canOpenGui() {
      return canUpdate()
         ? ReviveClient.mc.currentScreen == null
         : ReviveClient.mc.currentScreen instanceof TitleScreen
            || ReviveClient.mc.currentScreen instanceof MultiplayerScreen
            || ReviveClient.mc.currentScreen instanceof SelectWorldScreen;
   }

   public static boolean canCloseGui() {
      return ReviveClient.mc.currentScreen instanceof NewGuiScreen || ReviveClient.mc.currentScreen instanceof ModernGuiScreen;
   }

   public static int random(int min, int max) {
      return random.nextInt(max - min) + min;
   }

   public static double random(double min, double max) {
      return min + (max - min) * random.nextDouble();
   }

   public static void leftClick() {
      int attackCooldown = ((MinecraftClientAccessor)ReviveClient.mc).getAttackCooldown();
      if (attackCooldown == 10000) {
         ((MinecraftClientAccessor)ReviveClient.mc).setAttackCooldown(0);
      }

      ReviveClient.mc.options.attackKey.setPressed(true);
      ((MinecraftClientAccessor)ReviveClient.mc).leftClick();
      ReviveClient.mc.options.attackKey.setPressed(false);
   }

   public static void rightClick() {
      ((IMinecraftClient)ReviveClient.mc).revive$rightClick();
   }

   public static Color lerp(Color first, Color second, @Range(from = 0L,to = 1L) float v) {
      return new Color((int)(first.r * (1.0F - v) + second.r * v), (int)(first.g * (1.0F - v) + second.g * v), (int)(first.b * (1.0F - v) + second.b * v));
   }

   public static boolean isLoading() {
      ReloadState state = ((ResourceReloadLoggerAccessor)((MinecraftClientAccessor)ReviveClient.mc).getResourceReloadLogger()).getReloadState();
      return state == null || !((ReloadStateAccessor)state).isFinished();
   }

   public static int parsePort(String full) {
      if (full != null && !full.isBlank() && full.contains(":")) {
         int port;
         try {
            port = Integer.parseInt(full.substring(full.lastIndexOf(58) + 1, full.length() - 1));
         } catch (NumberFormatException var3) {
            port = -1;
         }

         return port;
      } else {
         return -1;
      }
   }

   public static String parseAddress(String full) {
      return full != null && !full.isBlank() && full.contains(":") ? full.substring(0, full.lastIndexOf(58)) : full;
   }

   public static boolean resolveAddress(String address) {
      if (address != null && !address.isBlank()) {
         int port = parsePort(address);
         if (port == -1) {
            port = 25565;
         } else {
            address = parseAddress(address);
         }

         return resolveAddress(address, port);
      } else {
         return false;
      }
   }

   public static boolean resolveAddress(String address, int port) {
      if (port > 0 && port <= 65535 && address != null && !address.isBlank()) {
         InetSocketAddress socketAddress = new InetSocketAddress(address, port);
         return !socketAddress.isUnresolved();
      } else {
         return false;
      }
   }

   public static Vector3d set(Vector3d vec, Vec3d v) {
      vec.x = v.x;
      vec.y = v.y;
      vec.z = v.z;
      return vec;
   }

   public static Vector3d set(Vector3d vec, Entity entity, double tickDelta) {
      vec.x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
      vec.y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
      vec.z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
      return vec;
   }

   public static boolean nameFilter(String text, char character) {
      return character >= 'a' && character <= 'z'
         || character >= 'A' && character <= 'Z'
         || character >= '0' && character <= '9'
         || character == '_'
         || character == '-'
         || character == '.'
         || character == ' ';
   }

   public static boolean ipFilter(String text, char character) {
      return text.contains(":") && character == ':'
         ? false
         : character >= 'a' && character <= 'z'
            || character >= 'A' && character <= 'Z'
            || character >= '0' && character <= '9'
            || character == '.'
            || character == '-';
   }
}
