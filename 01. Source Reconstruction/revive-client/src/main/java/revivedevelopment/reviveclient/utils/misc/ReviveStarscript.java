package revivedevelopment.reviveclient.utils.misc;

import baritone.api.BaritoneAPI;
import baritone.api.process.IBaritoneProcess;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import meteordevelopment.starscript.Script;
import meteordevelopment.starscript.Section;
import meteordevelopment.starscript.StandardLib;
import meteordevelopment.starscript.Starscript;
import meteordevelopment.starscript.compiler.Compiler;
import meteordevelopment.starscript.compiler.Parser;
import meteordevelopment.starscript.compiler.Parser.Result;
import meteordevelopment.starscript.utils.Error;
import meteordevelopment.starscript.utils.StarscriptError;
import meteordevelopment.starscript.value.Value;
import meteordevelopment.starscript.value.ValueMap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Mode;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.StringUtils;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.mixin.ClientPlayerInteractionManagerAccessor;
import revivedevelopment.reviveclient.mixin.MinecraftClientAccessor;
import revivedevelopment.reviveclient.pingbypass.client.PbPredict;
import revivedevelopment.reviveclient.pingbypass.client.PbRouting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.systems.modules.player.PingBypass;
import revivedevelopment.reviveclient.utils.PreInit;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.player.ChatUtils;
import revivedevelopment.reviveclient.utils.player.PlayerUtils;
import revivedevelopment.reviveclient.utils.world.Dimension;
import revivedevelopment.reviveclient.utils.world.TickRate;

public class ReviveStarscript {
   public static Starscript ss = new Starscript();
   private static final Mutable BP = new Mutable();
   private static long lastRequestedStatsTime = 0L;

   @PreInit
   public static void init() {
      StandardLib.init(ss);
      ss.set("mc_version", SharedConstants.getGameVersion().name());
      ss.set("fps", () -> Value.number(MinecraftClientAccessor.getFps()));
      ss.set("ping", ReviveStarscript::ping);
      ss.set("time", () -> Value.string(LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))));
      ss.set("cps", () -> Value.number(CPSUtils.getCpsAverage()));
      ss.set("baritone", new ValueMap().set("process_name", ReviveStarscript::baritoneProcessName));
      ss.set(
         "revive",
         new ValueMap()
            .set("name", ReviveClient.NAME)
            .set(
               "version",
               ReviveClient.VERSION != null
                  ? (ReviveClient.BUILD_NUMBER.isEmpty() ? ReviveClient.VERSION.toString() : ReviveClient.VERSION + " " + ReviveClient.BUILD_NUMBER)
                  : ""
            )
            .set("modules", () -> Value.number(Modules.get().getAll().size()))
            .set("active_modules", () -> Value.number(Modules.get().getActive().size()))
            .set("is_module_active", ReviveStarscript::isModuleActive)
            .set("get_module_info", ReviveStarscript::getModuleInfo)
            .set("get_module_setting", ReviveStarscript::getModuleSetting)
            .set("prefix", ReviveStarscript::getRevivePrefix)
      );
      ss.set(
         "camera",
         new ValueMap()
            .set(
               "pos",
               new ValueMap()
                  .set("_toString", () -> posString(false, true))
                  .set("x", () -> Value.number(ReviveClient.mc.gameRenderer.getCamera().getCameraPos().x))
                  .set("y", () -> Value.number(ReviveClient.mc.gameRenderer.getCamera().getCameraPos().y))
                  .set("z", () -> Value.number(ReviveClient.mc.gameRenderer.getCamera().getCameraPos().z))
            )
            .set(
               "opposite_dim_pos",
               new ValueMap()
                  .set("_toString", () -> posString(true, true))
                  .set("x", () -> oppositeX(true))
                  .set("y", () -> Value.number(ReviveClient.mc.gameRenderer.getCamera().getCameraPos().y))
                  .set("z", () -> oppositeZ(true))
            )
            .set("yaw", () -> yaw(true))
            .set("pitch", () -> pitch(true))
            .set("direction", () -> direction(true))
      );
      ss.set(
         "player",
         new ValueMap()
            .set("_toString", () -> Value.string(ReviveClient.mc.getSession().getUsername()))
            .set("health", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.getHealth() : 0.0))
            .set("absorption", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.getAbsorptionAmount() : 0.0))
            .set("hunger", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.getHungerManager().getFoodLevel() : 0.0))
            .set("saturation", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.getHungerManager().getSaturationLevel() : 0.0))
            .set("speed", () -> Value.number(Utils.getPlayerSpeed().horizontalLength()))
            .set(
               "speed_all",
               new ValueMap()
                  .set("_toString", () -> Value.string(ReviveClient.mc.player != null ? Utils.getPlayerSpeed().toString() : ""))
                  .set("x", () -> Value.number(ReviveClient.mc.player != null ? Utils.getPlayerSpeed().x : 0.0))
                  .set("y", () -> Value.number(ReviveClient.mc.player != null ? Utils.getPlayerSpeed().y : 0.0))
                  .set("z", () -> Value.number(ReviveClient.mc.player != null ? Utils.getPlayerSpeed().z : 0.0))
            )
            .set(
               "breaking_progress",
               () -> Value.number(
                  ReviveClient.mc.interactionManager != null
                     ? ((ClientPlayerInteractionManagerAccessor)ReviveClient.mc.interactionManager).getBreakingProgress()
                     : 0.0
               )
            )
            .set("biome", ReviveStarscript::biome)
            .set("dimension", () -> Value.string(PlayerUtils.getDimension().name()))
            .set("opposite_dimension", () -> Value.string(PlayerUtils.getDimension().opposite().name()))
            .set("gamemode", () -> PlayerUtils.getGameMode() != null ? Value.string(StringUtils.capitalize(PlayerUtils.getGameMode().getId())) : Value.null_())
            .set(
               "pos",
               new ValueMap()
                  .set("_toString", () -> posString(false, false))
                  .set("x", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.getX() : 0.0))
                  .set("y", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.getY() : 0.0))
                  .set("z", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.getZ() : 0.0))
            )
            .set(
               "opposite_dim_pos",
               new ValueMap()
                  .set("_toString", () -> posString(true, false))
                  .set("x", () -> oppositeX(false))
                  .set("y", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.getY() : 0.0))
                  .set("z", () -> oppositeZ(false))
            )
            .set("yaw", () -> yaw(false))
            .set("pitch", () -> pitch(false))
            .set("direction", () -> direction(false))
            .set("hand", () -> ReviveClient.mc.player != null ? wrap(ReviveClient.mc.player.getMainHandStack()) : Value.null_())
            .set("offhand", () -> ReviveClient.mc.player != null ? wrap(ReviveClient.mc.player.getOffHandStack()) : Value.null_())
            .set("hand_or_offhand", ReviveStarscript::handOrOffhand)
            .set("get_item", ReviveStarscript::getItem)
            .set("count_items", ReviveStarscript::countItems)
            .set(
               "xp",
               new ValueMap()
                  .set("level", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.experienceLevel : 0.0))
                  .set("progress", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.experienceProgress : 0.0))
                  .set("total", () -> Value.number(ReviveClient.mc.player != null ? ReviveClient.mc.player.totalExperience : 0.0))
            )
            .set("has_potion_effect", ReviveStarscript::hasPotionEffect)
            .set("get_potion_effect", ReviveStarscript::getPotionEffect)
            .set("get_stat", ReviveStarscript::getStat)
      );
      ss.set("crosshair_target", new ValueMap().set("type", ReviveStarscript::crosshairType).set("value", ReviveStarscript::crosshairValue));
      ss.set(
         "server",
         new ValueMap()
            .set("_toString", () -> Value.string(Utils.getWorldName()))
            .set("tps", () -> Value.number(TickRate.INSTANCE.getTickRate()))
            .set("time", () -> Value.string(Utils.getWorldTime()))
            .set(
               "player_count",
               () -> Value.number(ReviveClient.mc.getNetworkHandler() != null ? ReviveClient.mc.getNetworkHandler().getPlayerList().size() : 0.0)
            )
            .set("difficulty", () -> Value.string(ReviveClient.mc.world != null ? ReviveClient.mc.world.getDifficulty().getName() : ""))
      );
      ss.set(
         "pingbypass",
         new ValueMap()
            .set("_toString", () -> {
               PingBypass p = pingBypass();
               String info = p != null && p.isActive() ? p.getInfoString() : null;
               return Value.string(info != null ? info : "");
            })
            .set("state", () -> Value.string(pingBypass() != null ? pingBypass().getVpsState() : ""))
            .set("link_ping", () -> Value.number(pingBypass() != null ? pingBypass().getLinkPing() : -1.0))
            .set("server_ping", () -> Value.number(pingBypass() != null ? pingBypass().getServerPing() : -1.0))
            .set("server_ping_min", () -> Value.number(pingBypass() != null ? pingBypass().getServerPingMin() : -1.0))
            .set("server_ping_source", () -> Value.string(pingBypass() != null ? pingBypass().getServerPingSource() : ""))
            .set("crystal_cps", () -> Value.number(pingBypass() != null ? pingBypass().getVpsCrystalCps() : -1.0))
            .set("crystal_pps", () -> Value.number(pingBypass() != null ? pingBypass().getVpsCrystalPps() : -1.0))
            .set("server_version", () -> Value.string(pingBypass() != null ? pingBypass().getServerVersion() : ""))
            .set("driven", () -> Value.bool(PbRouting.routedDriven()))
            .set("corrections", () -> Value.number(PbPredict.corrections()))
      );
   }

   private static PingBypass pingBypass() {
      Modules modules = Modules.get();
      return modules == null ? null : modules.get(PingBypass.class);
   }

   public static Script compile(String source) {
      Result result = Parser.parse(source);
      if (!result.hasErrors()) {
         return Compiler.compile(result);
      } else {
         for (Error error : result.errors) {
            printChatError(error);
         }

         return null;
      }
   }

   public static Section runSection(Script script, StringBuilder sb) {
      try {
         return ss.run(script, sb);
      } catch (StarscriptError var3) {
         printChatError(var3);
         return null;
      }
   }

   public static String run(Script script, StringBuilder sb) {
      Section section = runSection(script, sb);
      return section != null ? section.toString() : null;
   }

   public static Section runSection(Script script) {
      return runSection(script, new StringBuilder());
   }

   public static String run(Script script) {
      return run(script, new StringBuilder());
   }

   public static void printChatError(int i, Error error) {
      String caller = getCallerName();
      if (caller != null) {
         if (i != -1) {
            ChatUtils.errorPrefix("Starscript", "%d, %d '%c': %s (from %s)", i, error.character, error.ch, error.message, caller);
         } else {
            ChatUtils.errorPrefix("Starscript", "%d '%c': %s (from %s)", error.character, error.ch, error.message, caller);
         }
      } else if (i != -1) {
         ChatUtils.errorPrefix("Starscript", "%d, %d '%c': %s", i, error.character, error.ch, error.message);
      } else {
         ChatUtils.errorPrefix("Starscript", "%d '%c': %s", error.character, error.ch, error.message);
      }
   }

   public static void printChatError(Error error) {
      printChatError(-1, error);
   }

   public static void printChatError(StarscriptError e) {
      String caller = getCallerName();
      if (caller != null) {
         ChatUtils.errorPrefix("Starscript", "%s (from %s)", e.getMessage(), caller);
      } else {
         ChatUtils.errorPrefix("Starscript", "%s", e.getMessage());
      }
   }

   private static String getCallerName() {
      StackTraceElement[] elements = Thread.currentThread().getStackTrace();
      if (elements.length == 0) {
         return null;
      } else {
         for (int i = 1; i < elements.length; i++) {
            String name = elements[i].getClassName();
            if (!name.startsWith(Starscript.class.getPackageName()) && !name.equals(ReviveStarscript.class.getName())) {
               return name.substring(name.lastIndexOf(46) + 1);
            }
         }

         return null;
      }
   }

   private static Value hasPotionEffect(Starscript ss, int argCount) {
      if (argCount < 1) {
         ss.error("player.has_potion_effect() requires 1 argument, got %d.", new Object[]{argCount});
      }

      if (ReviveClient.mc.player == null) {
         return Value.bool(false);
      } else {
         Identifier name = popIdentifier(ss, "First argument to player.has_potion_effect() needs to a string.");
         Optional<Reference<StatusEffect>> effect = Registries.STATUS_EFFECT.getEntry(name);
         if (effect.isEmpty()) {
            return Value.bool(false);
         } else {
            StatusEffectInstance effectInstance = ReviveClient.mc.player.getStatusEffect((RegistryEntry)effect.get());
            return Value.bool(effectInstance != null);
         }
      }
   }

   private static Value getPotionEffect(Starscript ss, int argCount) {
      if (argCount < 1) {
         ss.error("player.get_potion_effect() requires 1 argument, got %d.", new Object[]{argCount});
      }

      if (ReviveClient.mc.player == null) {
         return Value.null_();
      } else {
         Identifier name = popIdentifier(ss, "First argument to player.get_potion_effect() needs to a string.");
         Optional<Reference<StatusEffect>> effect = Registries.STATUS_EFFECT.getEntry(name);
         if (effect.isEmpty()) {
            return Value.null_();
         } else {
            StatusEffectInstance effectInstance = ReviveClient.mc.player.getStatusEffect((RegistryEntry)effect.get());
            return effectInstance == null ? Value.null_() : wrap(effectInstance);
         }
      }
   }

   private static Value getStat(Starscript ss, int argCount) {
      if (argCount < 1) {
         ss.error("player.get_stat() requires 1 argument, got %d.", new Object[]{argCount});
      }

      if (ReviveClient.mc.player == null) {
         return Value.number(0.0);
      } else {
         long time = System.currentTimeMillis();
         if ((time - lastRequestedStatsTime) / 1000.0 >= 1.0 && ReviveClient.mc.getNetworkHandler() != null) {
            ReviveClient.mc.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(Mode.REQUEST_STATS));
            lastRequestedStatsTime = time;
         }

         String type = argCount > 1 ? ss.popString("First argument to player.get_stat() needs to be a string.") : "custom";
         Identifier name = popIdentifier(ss, (argCount > 1 ? "Second" : "First") + " argument to player.get_stat() needs to be a string.");

         Stat<?> stat = switch (type) {
            case "mined" -> Stats.MINED.getOrCreateStat((Block)Registries.BLOCK.get(name));
            case "crafted" -> Stats.CRAFTED.getOrCreateStat((Item)Registries.ITEM.get(name));
            case "used" -> Stats.USED.getOrCreateStat((Item)Registries.ITEM.get(name));
            case "broken" -> Stats.BROKEN.getOrCreateStat((Item)Registries.ITEM.get(name));
            case "picked_up" -> Stats.PICKED_UP.getOrCreateStat((Item)Registries.ITEM.get(name));
            case "dropped" -> Stats.DROPPED.getOrCreateStat((Item)Registries.ITEM.get(name));
            case "killed" -> Stats.KILLED.getOrCreateStat((EntityType)Registries.ENTITY_TYPE.get(name));
            case "killed_by" -> Stats.KILLED_BY.getOrCreateStat((EntityType)Registries.ENTITY_TYPE.get(name));
            case "custom" -> {
               name = (Identifier)Registries.CUSTOM_STAT.get(name);
               yield name != null ? Stats.CUSTOM.getOrCreateStat(name) : null;
            }
            default -> null;
         };
         return Value.number(stat != null ? ReviveClient.mc.player.getStatHandler().getStat(stat) : 0.0);
      }
   }

   private static Value getModuleInfo(Starscript ss, int argCount) {
      if (argCount != 1) {
         ss.error("revive.get_module_info() requires 1 argument, got %d.", new Object[]{argCount});
      }

      Module module = Modules.get().get(ss.popString("First argument to revive.get_module_info() needs to be a string."));
      if (module != null && module.isActive()) {
         String info = module.getDisplayInfo();
         return Value.string(info == null ? "" : info);
      } else {
         return Value.string("");
      }
   }

   private static Value getModuleSetting(Starscript ss, int argCount) {
      if (argCount != 2) {
         ss.error("revive.get_module_setting() requires 2 arguments, got %d.", new Object[]{argCount});
      }

      String settingName = ss.popString("Second argument to revive.get_module_setting() needs to be a string.");
      String moduleName = ss.popString("First argument to revive.get_module_setting() needs to be a string.");
      Module module = Modules.get().get(moduleName);
      if (module == null) {
         ss.error("Unable to get module %s for revive.get_module_setting()", new Object[]{moduleName});
      }

      Setting<?> setting = module.settings.get(settingName);
      if (setting == null) {
         ss.error("Unable to get setting %s for module %s for revive.get_module_setting()", new Object[]{settingName, moduleName});
      }

      Object value = setting.get();

      return switch (value) {
         case Double d -> Value.number(d);
         case Integer i -> Value.number(i.intValue());
         case Boolean b -> Value.bool(b);
         case List<?> list -> Value.number(list.size());
         case null, default -> Value.string(value.toString());
      };
   }

   private static Value isModuleActive(Starscript ss, int argCount) {
      if (argCount != 1) {
         ss.error("revive.is_module_active() requires 1 argument, got %d.", new Object[]{argCount});
      }

      Module module = Modules.get().get(ss.popString("First argument to revive.is_module_active() needs to be a string."));
      return Value.bool(module != null && module.isActive());
   }

   private static Value getItem(Starscript ss, int argCount) {
      if (argCount != 1) {
         ss.error("player.get_item() requires 1 argument, got %d.", new Object[]{argCount});
      }

      int i = (int)ss.popNumber("First argument to player.get_item() needs to be a number.");
      if (i < 0) {
         ss.error("First argument to player.get_item() needs to be a non-negative integer.", new Object[]{i});
      }

      return ReviveClient.mc.player != null ? wrap(ReviveClient.mc.player.getInventory().getStack(i)) : Value.null_();
   }

   private static Value countItems(Starscript ss, int argCount) {
      if (argCount != 1) {
         ss.error("player.count_items() requires 1 argument, got %d.", new Object[]{argCount});
      }

      String idRaw = ss.popString("First argument to player.count_items() needs to be a string.");
      Identifier id = Identifier.tryParse(idRaw);
      if (id == null) {
         return Value.number(0.0);
      } else {
         Item item = (Item)Registries.ITEM.get(id);
         if (item != Items.AIR && ReviveClient.mc.player != null) {
            int count = 0;

            for (int i = 0; i < ReviveClient.mc.player.getInventory().size(); i++) {
               ItemStack itemStack = ReviveClient.mc.player.getInventory().getStack(i);
               if (itemStack.getItem() == item) {
                  count += itemStack.getCount();
               }
            }

            return Value.number(count);
         } else {
            return Value.number(0.0);
         }
      }
   }

   private static Value getRevivePrefix() {
      return Config.get() == null ? Value.null_() : Value.string(Config.get().prefix.get());
   }

   private static Value oppositeX(boolean camera) {
      double x = camera ? ReviveClient.mc.gameRenderer.getCamera().getCameraPos().x : (ReviveClient.mc.player != null ? ReviveClient.mc.player.getX() : 0.0);
      Dimension dimension = PlayerUtils.getDimension();
      if (dimension == Dimension.Overworld) {
         x /= 8.0;
      } else if (dimension == Dimension.Nether) {
         x *= 8.0;
      }

      return Value.number(x);
   }

   private static Value oppositeZ(boolean camera) {
      double z = camera ? ReviveClient.mc.gameRenderer.getCamera().getCameraPos().z : (ReviveClient.mc.player != null ? ReviveClient.mc.player.getZ() : 0.0);
      Dimension dimension = PlayerUtils.getDimension();
      if (dimension == Dimension.Overworld) {
         z /= 8.0;
      } else if (dimension == Dimension.Nether) {
         z *= 8.0;
      }

      return Value.number(z);
   }

   private static Value yaw(boolean camera) {
      float yaw;
      if (camera) {
         yaw = ReviveClient.mc.gameRenderer.getCamera().getYaw();
      } else {
         yaw = ReviveClient.mc.player != null ? ReviveClient.mc.player.getYaw() : 0.0F;
      }

      yaw %= 360.0F;
      if (yaw < 0.0F) {
         yaw += 360.0F;
      }

      if (yaw > 180.0F) {
         yaw -= 360.0F;
      }

      return Value.number(yaw);
   }

   private static Value pitch(boolean camera) {
      float pitch;
      if (camera) {
         pitch = ReviveClient.mc.gameRenderer.getCamera().getPitch();
      } else {
         pitch = ReviveClient.mc.player != null ? ReviveClient.mc.player.getPitch() : 0.0F;
      }

      pitch %= 360.0F;
      if (pitch < 0.0F) {
         pitch += 360.0F;
      }

      if (pitch > 180.0F) {
         pitch -= 360.0F;
      }

      return Value.number(pitch);
   }

   private static Value direction(boolean camera) {
      float yaw;
      if (camera) {
         yaw = ReviveClient.mc.gameRenderer.getCamera().getYaw();
      } else {
         yaw = ReviveClient.mc.player != null ? ReviveClient.mc.player.getYaw() : 0.0F;
      }

      return wrap(HorizontalDirection.get(yaw));
   }

   private static Value biome() {
      if (ReviveClient.mc.player != null && ReviveClient.mc.world != null) {
         BP.set(ReviveClient.mc.player.getX(), ReviveClient.mc.player.getY(), ReviveClient.mc.player.getZ());
         return ReviveClient.mc
            .world
            .getRegistryManager()
            .getOptional(RegistryKeys.BIOME)
            .map(
               biomeRegistry -> {
                  Identifier id = biomeRegistry.getId((Biome)ReviveClient.mc.world.getBiome(BP).value());
                  return id == null
                     ? Value.string("Unknown")
                     : Value.string(Arrays.stream(id.getPath().split("_")).<CharSequence>map(StringUtils::capitalize).collect(Collectors.joining(" ")));
               }
            )
            .orElse(Value.string("Unknown"));
      } else {
         return Value.string("");
      }
   }

   private static Value handOrOffhand() {
      if (ReviveClient.mc.player == null) {
         return Value.null_();
      } else {
         ItemStack itemStack = ReviveClient.mc.player.getMainHandStack();
         if (itemStack.isEmpty()) {
            itemStack = ReviveClient.mc.player.getOffHandStack();
         }

         return itemStack != null ? wrap(itemStack) : Value.null_();
      }
   }

   private static Value baritoneProcessName() {
      try {
         return Value.string(
            BaritoneAPI.getProvider()
               .getPrimaryBaritone()
               .getPathingControlManager()
               .mostRecentInControl()
               .<String>map(IBaritoneProcess::displayName)
               .orElse("Inactive")
         );
      } catch (Throwable var1) {
         return Value.string("Inactive");
      }
   }

   private static Value ping() {
      if (ReviveClient.mc.getNetworkHandler() != null && ReviveClient.mc.player != null) {
         PlayerListEntry playerListEntry = ReviveClient.mc.getNetworkHandler().getPlayerListEntry(ReviveClient.mc.player.getUuid());
         return Value.number(playerListEntry != null ? playerListEntry.getLatency() : 0.0);
      } else {
         return Value.number(0.0);
      }
   }

   private static Value posString(boolean opposite, boolean camera) {
      Vec3d pos;
      if (camera) {
         pos = ReviveClient.mc.gameRenderer.getCamera().getCameraPos();
      } else {
         pos = ReviveClient.mc.player != null ? ReviveClient.mc.player.getEntityPos() : Vec3d.ZERO;
      }

      double x = pos.x;
      double z = pos.z;
      if (opposite) {
         Dimension dimension = PlayerUtils.getDimension();
         if (dimension == Dimension.Overworld) {
            x /= 8.0;
            z /= 8.0;
         } else if (dimension == Dimension.Nether) {
            x *= 8.0;
            z *= 8.0;
         }
      }

      return posString(x, pos.y, z);
   }

   private static Value posString(double x, double y, double z) {
      return Value.string(String.format("X: %.0f Y: %.0f Z: %.0f", x, y, z));
   }

   private static Value crosshairType() {
      if (ReviveClient.mc.crosshairTarget == null) {
         return Value.string("miss");
      } else {
         return Value.string(switch (ReviveClient.mc.crosshairTarget.getType()) {
            case MISS -> "miss";
            case BLOCK -> "block";
            case ENTITY -> "entity";
            default -> throw new MatchException(null, null);
         });
      }
   }

   private static Value crosshairValue() {
      if (ReviveClient.mc.world != null && ReviveClient.mc.crosshairTarget != null) {
         if (ReviveClient.mc.crosshairTarget.getType() == Type.MISS) {
            return Value.string("");
         } else {
            return ReviveClient.mc.crosshairTarget instanceof BlockHitResult hit
               ? wrap(hit.getBlockPos(), ReviveClient.mc.world.getBlockState(hit.getBlockPos()))
               : wrap(((EntityHitResult)ReviveClient.mc.crosshairTarget).getEntity());
         }
      } else {
         return Value.null_();
      }
   }

   public static Identifier popIdentifier(Starscript ss, String errorMessage) {
      try {
         return Identifier.of(ss.popString(errorMessage));
      } catch (InvalidIdentifierException var3) {
         ss.error(var3.getMessage(), new Object[0]);
         return null;
      }
   }

   public static Value wrap(ItemStack itemStack) {
      String name = itemStack.isEmpty() ? "" : Names.get(itemStack.getItem());
      int durability = 0;
      if (!itemStack.isEmpty() && itemStack.isDamageable()) {
         durability = itemStack.getMaxDamage() - itemStack.getDamage();
      }

      return Value.map(
         new ValueMap()
            .set("_toString", Value.string(itemStack.getCount() <= 1 ? name : String.format("%s %dx", name, itemStack.getCount())))
            .set("name", Value.string(name))
            .set("id", Value.string(Registries.ITEM.getId(itemStack.getItem()).toString()))
            .set("count", Value.number(itemStack.getCount()))
            .set("durability", Value.number(durability))
            .set("max_durability", Value.number(itemStack.getMaxDamage()))
      );
   }

   public static Value wrap(BlockPos blockPos, BlockState blockState) {
      return Value.map(
         new ValueMap()
            .set("_toString", Value.string(Names.get(blockState.getBlock())))
            .set("id", Value.string(Registries.BLOCK.getId(blockState.getBlock()).toString()))
            .set(
               "pos",
               Value.map(
                  new ValueMap()
                     .set("_toString", posString(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
                     .set("x", Value.number(blockPos.getX()))
                     .set("y", Value.number(blockPos.getY()))
                     .set("z", Value.number(blockPos.getZ()))
               )
            )
      );
   }

   public static Value wrap(Entity entity) {
      return Value.map(
         new ValueMap()
            .set("_toString", Value.string(entity.getName().getString()))
            .set("id", Value.string(Registries.ENTITY_TYPE.getId(entity.getType()).toString()))
            .set("health", Value.number(entity instanceof LivingEntity ex ? ex.getHealth() : 0.0))
            .set("absorption", Value.number(entity instanceof LivingEntity e ? e.getAbsorptionAmount() : 0.0))
            .set(
               "pos",
               Value.map(
                  new ValueMap()
                     .set("_toString", posString(entity.getX(), entity.getY(), entity.getZ()))
                     .set("x", Value.number(entity.getX()))
                     .set("y", Value.number(entity.getY()))
                     .set("z", Value.number(entity.getZ()))
               )
            )
      );
   }

   public static Value wrap(HorizontalDirection dir) {
      return Value.map(
         new ValueMap().set("_toString", Value.string(dir.name + " " + dir.axis)).set("name", Value.string(dir.name)).set("axis", Value.string(dir.axis))
      );
   }

   public static Value wrap(StatusEffectInstance effectInstance) {
      return Value.map(new ValueMap().set("duration", effectInstance.getDuration()).set("level", effectInstance.getAmplifier() + 1));
   }
}
