package revivedevelopment.reviveclient.systems.modules.hunting;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.EntityAddedEvent;
import revivedevelopment.reviveclient.events.entity.player.PlayerMoveEvent;
import revivedevelopment.reviveclient.events.game.GameLeftEvent;
import revivedevelopment.reviveclient.events.game.OpenScreenEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.gui.GuiTheme;
import revivedevelopment.reviveclient.gui.WindowScreen;
import revivedevelopment.reviveclient.gui.widgets.WWidget;
import revivedevelopment.reviveclient.gui.widgets.containers.WTable;
import revivedevelopment.reviveclient.gui.widgets.containers.WVerticalList;
import revivedevelopment.reviveclient.gui.widgets.pressable.WButton;
import revivedevelopment.reviveclient.gui.widgets.pressable.WMinus;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.ItemListSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.settings.StringSetting;
import revivedevelopment.reviveclient.systems.config.Config;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.BaritoneBridge;
import revivedevelopment.reviveclient.utils.Utils;
import revivedevelopment.reviveclient.utils.compat.XaeroCompat;
import revivedevelopment.reviveclient.utils.render.ReviveToast;

public class StashFinderPlus extends Module {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSignals = this.settings.createGroup("Additional Detections");
   private final SettingGroup sgArchive = this.settings.createGroup("Searchable Archive");
   private final Setting<Boolean> saveHitArchive = this.sgArchive
      .add(
         new BoolSetting.Builder()
            .name("save-hit-archive")
            .description("Save all hit types as JSONL in stash-finder-plus/stash-hits.jsonl (client folder), separately from Discord.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> sendDatabase = this.sgArchive
      .add(
         new BoolSetting.Builder()
            .name("send-database")
            .description("Send hits to your private Supabase database; also keeps a local archive. Requires the supplied SQL setup.")
            .defaultValue(false)
            .build()
      );
   private final Setting<String> databaseUrl = this.sgArchive
      .add(
         new StringSetting.Builder()
            .name("database-project-url")
            .description("Your https://PROJECT.supabase.co URL. No upload occurs until Send Database is enabled.")
            .defaultValue("")
            .visible(this.sendDatabase::get)
            .build()
      );
   private final Setting<String> databaseKey = this.sgArchive
      .add(
         new StringSetting.Builder()
            .name("database-publishable-key")
            .description("sb_publishable_ key only. Never enter a service-role or secret/admin key.")
            .defaultValue("")
            .visible(this.sendDatabase::get)
            .build()
      );
   private final Setting<String> databaseToken = this.sgArchive
      .add(
         new StringSetting.Builder()
            .name("database-ingest-token")
            .description("Private 64-hex write-only token from SQL setup. Stored in your local config; do not share it.")
            .defaultValue("")
            .visible(this.sendDatabase::get)
            .build()
      );
   private final Setting<Boolean> detectPearls = this.sgSignals
      .add(
         new BoolSetting.Builder()
            .name("thrown-pearls")
            .description("Alert on loaded thrown ender pearl entities, including suspended pearls. Independent of storage count.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> ignoreOwnPearls = this.sgSignals
      .add(
         new BoolSetting.Builder()
            .name("ignore-own-pearls")
            .description("Skip pearls whose owner is known to be you. Unknown owners are still detected.")
            .defaultValue(true)
            .visible(this.detectPearls::get)
            .build()
      );
   private final Setting<Boolean> detectSigns = this.sgSignals
      .add(
         new BoolSetting.Builder()
            .name("signs")
            .description("Alert on normal and hanging signs, with front/back text and exact XYZ in the webhook. Independent of storage count.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectChestMinecarts = this.sgSignals
      .add(
         new BoolSetting.Builder()
            .name("chest-minecarts")
            .description("Legacy setting migrated to Storage Blocks.")
            .defaultValue(false)
            .visible(() -> false)
            .build()
      );
   private final Setting<Boolean> detectPlayers = this.sgSignals
      .add(
         new BoolSetting.Builder()
            .name("players-in-range")
            .description("Alert once per other loaded player per session. Excludes you and spectators; ignores Only Old Chunks.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> detectWithers = this.sgSignals
      .add(
         new BoolSetting.Builder()
            .name("withers")
            .description("Alert once per loaded live wither per session. Ignores Only Old Chunks.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> entityRange = this.sgSignals
      .add(
         new IntSetting.Builder()
            .name("entity-range")
            .description("Maximum 3D distance for player/wither hits. Only entities sent by the server can be detected.")
            .defaultValue(256)
            .min(1)
            .max(4096)
            .sliderRange(16, 512)
            .visible(() -> this.detectPlayers.get() || this.detectWithers.get())
            .build()
      );
   public List<StashFinderPlus.Chunk> chunks = new ArrayList<>();
   private final Setting<List<Item>> storageBlocks = this.sgGeneral
      .add(
         new ItemListSetting.Builder()
            .name("storage-blocks")
            .description(
               "Select storage types, including Minecart with Chest. Minecarts alert individually, independent of minimum storage count; natural mineshaft carts are included."
            )
            .defaultValue(StashStorageSelection.defaults())
            .filter(StashStorageSelection::allowed)
            .build()
      );
   private final Setting<Integer> minimumStorageCount = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("minimum-storage-count")
            .description("The minimum amount of storage blocks in a chunk to record the chunk.")
            .defaultValue(4)
            .min(1)
            .sliderMin(1)
            .build()
      );
   private final Setting<Boolean> shulkerInstantHit = this.sgGeneral
      .add(new BoolSetting.Builder().name("shulker-instant-hit").description("If a single shulker counts as a stash.").defaultValue(false).build());
   private final Setting<Boolean> crafterInstantHit = this.sgGeneral
      .add(new BoolSetting.Builder().name("crafter-instant-hit").description("If a single auto crafter counts as a stash.").defaultValue(false).build());
   private final Setting<Boolean> disableOnTeleport = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("disable-on-teleport-or-death")
            .description("If on, will disable this module when respawning or teleporting to try to prevent coord leaks.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> disconnectOnStashFound = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("disconnect-on-stash-found")
            .description("Disconnects you from the server when a stash is found.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> ignoreTrialChambers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ignore-trial-chambers")
            .description("Attempts to ignore trial chambers, but may cause false negatives if someone made their base to look like a trial chamber.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> minimumDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("minimum-distance")
            .description("The minimum distance you must be from spawn to record a certain chunk.")
            .defaultValue(0)
            .min(0)
            .sliderMax(10000)
            .build()
      );
   private final Setting<Boolean> onlyOldchunks = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("only-old-chunks").description("Checks that the chunks it scans have already been loaded.").defaultValue(true).build()
      );
   private final Setting<Boolean> saveToWaypoints = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("save-to-waypoints")
            .description("Creates xaeros minimap waypoints for stash finds.")
            .defaultValue(false)
            .onChanged(this::waypointSettingChanged)
            .build()
      );
   private final Setting<Boolean> sendNotifications = this.sgGeneral
      .add(new BoolSetting.Builder().name("notifications").description("Sends Minecraft notifications when new stashes are found.").defaultValue(true).build());
   private final Setting<StashFinderPlus.Mode> notificationMode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("notification-mode"))
                     .description("The mode to use for notifications."))
                  .defaultValue(StashFinderPlus.Mode.Both))
               .visible(this.sendNotifications::get))
            .build()
      );
   private final Setting<Boolean> playSound = this.sgGeneral
      .add(new BoolSetting.Builder().name("play-sound").description("Play a sound when a new stash is found.").defaultValue(true).build());
   private final Setting<StashFinderPlus.StashSound> stashSound = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("sound"))
                     .description("Which sound to play on a normal stash hit."))
                  .defaultValue(StashFinderPlus.StashSound.Pling))
               .visible(this.playSound::get))
            .build()
      );
   private final Setting<StashFinderPlus.StashSound> shulkerSound = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shulker-sound"))
                     .description("Separate sound when the hit contains shulkers, so you can tell the good ones apart while AFK."))
                  .defaultValue(StashFinderPlus.StashSound.Bell))
               .visible(this.playSound::get))
            .build()
      );
   private final Setting<Double> soundVolume = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("sound-volume")
            .description("Volume of the stash sound.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderRange(0.1, 5.0)
            .visible(this.playSound::get)
            .build()
      );
   private final Setting<Double> soundPitch = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("sound-pitch")
            .description("Pitch of the stash sound.")
            .defaultValue(1.0)
            .min(0.5)
            .sliderRange(0.5, 2.0)
            .visible(this.playSound::get)
            .build()
      );
   private final Setting<Integer> soundRepeats = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sound-repeats")
            .description("How many times to replay the sound. More repeats are easier to notice when you're away from the machine.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 10)
            .visible(this.playSound::get)
            .build()
      );
   private final Setting<Integer> soundRepeatDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("sound-repeat-delay")
            .description("Ticks between repeats (20 ticks = 1 second).")
            .defaultValue(8)
            .min(1)
            .sliderRange(1, 40)
            .visible(this.playSound::get)
            .build()
      );
   private final Setting<Boolean> sendWebhook = this.sgGeneral
      .add(new BoolSetting.Builder().name("send-webhook").description("Sends a webhook when a stash is found.").defaultValue(false).build());
   public final Setting<String> webhookLink = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("webhook-link")
            .description("A discord webhook link. Looks like this: https://discord.com/api/webhooks/webhookUserId/webHookTokenOrSomething")
            .defaultValue("")
            .visible(this.sendWebhook::get)
            .build()
      );
   private final Setting<Boolean> notifyDisconnect = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("notify-on-disconnect")
            .description("Send one Discord alert when leaving a multiplayer server, including manual/automatic disconnects and last known coordinates.")
            .defaultValue(true)
            .visible(this.sendWebhook::get)
            .build()
      );
   private final Setting<Boolean> webhookSurfaceMap = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("webhook-surface-map")
            .description(
               "Attach a loaded-chunk surface map to Overworld hit alerts. Images visibly reveal nearby terrain and coordinates to the webhook channel."
            )
            .defaultValue(true)
            .visible(this.sendWebhook::get)
            .build()
      );
   private final Setting<Boolean> webhook3dPreview = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("webhook-3d-preview")
            .description("128x128-block Overworld surface height model. Replaces the flat image; loaded terrain only, no underground or textured blocks.")
            .defaultValue(true)
            .visible(this.sendWebhook::get)
            .build()
      );
   private final Setting<Boolean> ignoreMansionWebhooks = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ignore-likely-mansion-webhooks")
            .description(
               "Conservative loaded-block heuristic for ordinary storage alerts. Keeps local records and all shulker, ender chest, crafter, hopper, barrel and entity/sign hits. Not guaranteed structure detection."
            )
            .defaultValue(true)
            .visible(this.sendWebhook::get)
            .build()
      );
   private final Setting<Integer> surfaceMapRadius = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("surface-map-radius-chunks")
            .description("Chunks around the hit chunk. 3 gives a 7x7 chunk map. Unloaded or time-limited areas stay blank.")
            .defaultValue(3)
            .range(1, 4)
            .sliderRange(1, 4)
            .visible(() -> this.sendWebhook.get() && this.webhookSurfaceMap.get())
            .build()
      );
   public final Setting<Boolean> advancedLogging = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("advanced-logging")
            .description("Will log more information, including the amount of each container found.")
            .defaultValue(false)
            .build()
      );
   public final Setting<Boolean> ping = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ping-for-stash-finder")
            .description("Pings you for stash finder and base finder messages")
            .defaultValue(false)
            .visible(this.sendWebhook::get)
            .build()
      );
   public final Setting<String> discordId = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("discord-ID")
            .description("Your discord ID")
            .defaultValue("")
            .visible(() -> this.sendWebhook.get() && this.ping.get())
            .build()
      );
   private Vec3d lastPosition = null;
   private final StashSignals signalHistory = new StashSignals(8192);
   private final StashSignals.DisconnectSession disconnectSession = new StashSignals.DisconnectSession();
   private final StashArchive hitArchive = new StashArchive(
      new File(ReviveClient.FOLDER, "stash-finder-plus/stash-hits.jsonl").toPath(), ReviveClient.LOG::warn
   );
   private Object signalWorld;
   private final StashSignScan<Long, BlockPos> signScan = new StashSignScan<>();
   private int signScanDelay;
   private int pearlScanDelay;
   private ThreadPoolExecutor signalWebhooks;
   private long lastQueueWarning;
   private long nextSurfaceCapture;
   private final ArrayDeque<StashFinderPlus.Preview> previews = new ArrayDeque<>();
   private boolean signalCapacityWarned;
   private volatile int pendingSounds = 0;
   private volatile SoundEvent pendingSoundEvent = null;
   private int soundTimer = 0;
   private Object chunkListener;

   public StashFinderPlus() {
      super(
         Categories.Hunting,
         "stash-finder-plus",
         "Finds stashes: storage chunks, plus signs, pearls, players, withers and chest minecarts, with archive and Discord webhooks."
      );
   }

   public StashFinderPlus fromTag(NbtCompound tag) {
      super.fromTag(tag);
      if (this.detectChestMinecarts.get()) {
         List<Item> list = new ArrayList<>(this.storageBlocks.get());
         if (!list.contains(Items.CHEST_MINECART)) {
            list.add(Items.CHEST_MINECART);
         }

         this.storageBlocks.set(list);
         this.detectChestMinecarts.set(false);
      }

      return this;
   }

   private boolean chestMinecartsSelected() {
      return this.storageBlocks.get().contains(Items.CHEST_MINECART);
   }

   @Override
   public void onActivate() {
      if (!XaeroCompat.XAEROPLUS) {
         this.error("XaeroPlus (with Xaero's World Map) isn't installed - Stash Finder Plus reads chunks through it. Disabling.", new Object[0]);
         this.toggle();
      } else {
         if (this.saveToWaypoints.get() && !XaeroCompat.MINIMAP) {
            this.warning("Xaero's Minimap isn't installed - stashes won't be saved as waypoints.", new Object[0]);
         }

         this.lastPosition = null;
         this.disconnectSession.clear();
         this.rememberDisconnectLocation();
         this.resetSignals();
         if (this.chunkListener == null) {
            this.chunkListener = XaeroCompat.newChunkListener(this::onChunkData);
         }

         XaeroCompat.register(this.chunkListener);
         this.load();
      }
   }

   @Override
   public void onDeactivate() {
      this.disconnectSession.clear();
      this.resetSignals();
      if (this.signalWebhooks != null) {
         this.signalWebhooks.shutdown();
         this.signalWebhooks = null;
      }

      this.pendingSounds = 0;
      this.soundTimer = 0;
      if (this.chunkListener != null) {
         XaeroCompat.unregister(this.chunkListener);
      }
   }

   private void resetSignals() {
      this.flushPreviews();
      this.signalHistory.clear();
      this.signalWorld = null;
      this.signScan.clear();
      this.signScanDelay = this.pearlScanDelay = 0;
      this.signalCapacityWarned = false;
   }

   @EventHandler(
      priority = 1000
   )
   private void onSignalLeave(GameLeftEvent event) {
      String payload = this.disconnectSession
         .leave(
            this.isActive() && this.notifyDisconnect.get() && this.sendWebhook.get() && !this.webhookLink.get().isBlank(),
            this.ping.get() ? this.discordId.get() : null,
            Instant.now()
         );
      if (payload != null) {
         this.queueSignalWebhook(this.webhookLink.get(), payload);
      }

      this.resetSignals();
   }

   private void rememberDisconnectLocation() {
      ServerInfo server = this.mc.getCurrentServerEntry();
      if (server != null
         && this.mc.player != null
         && this.mc.world != null
         && this.mc.getNetworkHandler() != null
         && this.mc.getNetworkHandler().getConnection().isOpen()) {
         BlockPos pos = this.mc.player.getBlockPos();
         this.disconnectSession
            .observe(
               server.address, this.mc.player.getName().getString(), this.mc.world.getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ()
            );
      }
   }

   private void checkSignalWorld() {
      if (this.signalWorld != this.mc.world) {
         this.resetSignals();
         this.signalWorld = this.mc.world;
      }
   }

   @EventHandler
   private void onPearlAdded(EntityAddedEvent event) {
      if (this.mc.world != null && this.mc.player != null) {
         this.checkSignalWorld();
         this.detectEntity(event.entity);
      }
   }

   @EventHandler
   private void onSignalTick(TickEvent.Post event) {
      if (this.mc.world != null && this.mc.player != null) {
         this.rememberDisconnectLocation();
         this.checkSignalWorld();
         this.processPreviews();
         if ((this.detectPearls.get() || this.detectPlayers.get() || this.detectWithers.get() || this.chestMinecartsSelected()) && this.pearlScanDelay-- <= 0) {
            this.pearlScanDelay = 20;

            for (Entity entity : this.mc.world.getEntities()) {
               if (this.mc.world == null || this.mc.player == null || !this.isActive()) {
                  break;
               }

               this.detectEntity(entity);
            }
         }

         if (this.mc.world != null && this.mc.player != null) {
            if (!this.detectSigns.get()) {
               this.signScan.clear();
               this.signScanDelay = 0;
            } else if (this.signScanDelay-- <= 0) {
               ClientWorld world = this.mc.world;
               if (!this.signScan.active()) {
                  List<Long> loaded = new ArrayList<>();

                  for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
                     loaded.add(chunk.getPos().toLong());
                  }

                  this.signScan.begin(loaded);
               }

               this.signScan
                  .step(
                     8,
                     64,
                     packed -> {
                        if (this.mc.world != world) {
                           return List.of();
                        } else {
                           WorldChunk chunkx = world.getChunkManager()
                              .getChunk(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), ChunkStatus.FULL, false);
                           return chunkx == null ? List.of() : chunkx.getBlockEntities().keySet().stream().<BlockPos>map(BlockPos::toImmutable).toList();
                        }
                     },
                     (packed, pos) -> {
                        if (this.mc.world == world && this.mc.player != null && this.isActive()) {
                           WorldChunk chunkx = world.getChunkManager()
                              .getChunk(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed), ChunkStatus.FULL, false);
                           if (chunkx != null
                              && chunkx.getBlockEntities().get(pos) instanceof SignBlockEntity sign
                              && !sign.isRemoved()
                              && this.acceptSignal(pos)) {
                              this.reportSignal(
                                 new StashSignals.Hit(
                                    StashSignals.Kind.Sign,
                                    world.getRegistryKey().getValue().toString(),
                                    Long.toString(pos.asLong()),
                                    pos.getX(),
                                    pos.getY(),
                                    pos.getZ(),
                                    signLines(sign.getFrontText()),
                                    signLines(sign.getBackText())
                                 )
                              );
                           }
                        } else {
                           this.signScan.clear();
                        }
                     }
                  );
               if (!this.signScan.active()) {
                  this.signScanDelay = 20;
               }
            }
         }
      }
   }

   private static String signLines(SignText text) {
      return String.join("\n", Arrays.stream(text.getMessages(false)).map(Text::getString).toList());
   }

   private boolean isOldEnough(int chunkX, int chunkZ, RegistryKey<World> dimension) {
      return !XaeroCompat.isNewChunk(chunkX, chunkZ, dimension) || XaeroCompat.isOldChunk(chunkX, chunkZ, dimension);
   }

   private boolean acceptSignal(BlockPos pos) {
      if (Math.hypot(pos.getX(), pos.getZ()) < this.minimumDistance.get().intValue()) {
         return false;
      } else {
         return !this.onlyOldchunks.get() ? true : this.isOldEnough(pos.getX() >> 4, pos.getZ() >> 4, this.mc.world.getRegistryKey());
      }
   }

   private void detectPearl(EnderPearlEntity pearl) {
      if (!pearl.isRemoved() && (!this.ignoreOwnPearls.get() || pearl.getOwner() != this.mc.player)) {
         BlockPos pos = pearl.getBlockPos();
         if (this.acceptSignal(pos)) {
            this.reportSignal(
               new StashSignals.Hit(
                  StashSignals.Kind.ThrownPearl,
                  this.mc.world.getRegistryKey().getValue().toString(),
                  pearl.getUuidAsString(),
                  pos.getX(),
                  pos.getY(),
                  pos.getZ(),
                  "",
                  ""
               )
            );
         }
      }
   }

   private void detectEntity(Entity entity) {
      if (this.mc.world != null && this.mc.player != null && !entity.isRemoved()) {
         if (this.detectPearls.get() && entity instanceof EnderPearlEntity pearl) {
            this.detectPearl(pearl);
         } else if (this.chestMinecartsSelected() && entity instanceof ChestMinecartEntity) {
            BlockPos pos = entity.getBlockPos();
            if (this.acceptSignal(pos)) {
               this.reportSignal(
                  new StashSignals.Hit(
                     StashSignals.Kind.ChestMinecart,
                     this.mc.world.getRegistryKey().getValue().toString(),
                     entity.getUuidAsString(),
                     pos.getX(),
                     pos.getY(),
                     pos.getZ(),
                     entity.getName().getString(),
                     "Loaded chest minecart; inventory contents unknown. May be a naturally generated mineshaft cart."
                  )
               );
            }
         } else {
            StashSignals.Kind kind;
            if (this.detectPlayers.get()
               && entity instanceof PlayerEntity player
               && player != this.mc.player
               && !player.getUuid().equals(this.mc.player.getUuid())
               && !player.isSpectator()
               && player.isAlive()) {
               kind = StashSignals.Kind.Player;
            } else {
               if (!this.detectWithers.get() || !(entity instanceof WitherEntity wither) || !wither.isAlive()) {
                  return;
               }

               kind = StashSignals.Kind.Wither;
            }

            double range = this.entityRange.get().intValue();
            if (!(entity.squaredDistanceTo(this.mc.player) > range * range)) {
               BlockPos pos = entity.getBlockPos();
               if (!(Math.hypot(pos.getX(), pos.getZ()) < this.minimumDistance.get().intValue())) {
                  this.reportSignal(
                     new StashSignals.Hit(
                        kind,
                        this.mc.world.getRegistryKey().getValue().toString(),
                        entity.getUuidAsString(),
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        entity.getName().getString(),
                        String.format(Locale.ROOT, "%.1f blocks from you (loaded entity)", Math.sqrt(entity.squaredDistanceTo(this.mc.player)))
                     )
                  );
               }
            }
         }
      }
   }

   private void reportSignal(StashSignals.Hit hit) {
      if (this.signalHistory.size() >= 8192 && !this.signalCapacityWarned) {
         this.warning("Additional detection session history is full; new signals are paused until reconnect or re-enable.", new Object[0]);
         this.signalCapacityWarned = true;
      }

      if (this.signalHistory.firstOrChanged(hit)) {
         this.archiveHit(StashArchive.signal(hit, this.serverName()));
         this.queueStashSound(false);
         if (this.sendNotifications.get()) {
            if (this.notificationMode.get() != StashFinderPlus.Mode.Toast) {
               this.info("%s at (highlight)%s(default) [%s].", new Object[]{hit.title(), hit.coordinates(), hit.dimension()});
            }

            if (this.notificationMode.get() != StashFinderPlus.Mode.Chat) {
               Item icon = switch (hit.kind()) {
                  case Sign -> Items.OAK_SIGN;
                  case ThrownPearl -> Items.ENDER_PEARL;
                  case Player -> Items.PLAYER_HEAD;
                  case Wither -> Items.WITHER_SKELETON_SKULL;
                  case ChestMinecart -> Items.CHEST_MINECART;
               };
               this.mc.getToastManager().add(new ReviveToast(icon, hit.title(), hit.coordinates()));
            }
         }

         if (this.sendWebhook.get() && !this.webhookLink.get().isBlank()) {
            this.queueHitWebhook(this.webhookLink.get(), StashSignals.payload(hit, this.ping.get() ? this.discordId.get() : null), hit.x(), hit.z());
         }

         if (this.saveToWaypoints.get()) {
            Object set = XaeroCompat.getWaypointSet();
            if (set != null && XaeroCompat.findWaypoint(set, hit.x(), hit.z(), null) == null) {
               String initials = switch (hit.kind()) {
                  case Sign -> "S";
                  case ThrownPearl -> "P";
                  case Player -> "U";
                  case Wither -> "W";
                  case ChestMinecart -> "M";
               };
               XaeroCompat.addWaypoint(set, hit.x(), hit.y(), hit.z(), hit.title(), initials, 10, 0, false);
               XaeroCompat.refreshWaypoints();
            }
         }

         if (this.disconnectOnStashFound.get()) {
            this.disconnectOnStashFound.set(false);
            ClientPlayNetworkHandler handler = this.mc.getNetworkHandler();
            if (handler != null) {
               handler.getConnection().disconnect(Text.literal(hit.title() + " at " + hit.coordinates()));
            }
         }
      }
   }

   private StashSurfaceMap.Snapshot captureSurface(int x, int z) {
      if (this.webhookSurfaceMap.get() && this.mc.isOnThread() && this.mc.world != null && this.mc.world.getRegistryKey().equals(World.OVERWORLD)) {
         long start = System.nanoTime();
         if (start >= this.nextSurfaceCapture && (this.signalWebhooks == null || this.signalWebhooks.getQueue().size() < 32)) {
            this.nextSurfaceCapture = start + 50000000L;
            ClientWorld world = this.mc.world;
            Map<Long, WorldChunk> cache = new HashMap<>();
            Mutable mutable = new Mutable();

            try {
               return StashSurfaceMap.capture(x, z, this.surfaceMapRadius.get(), (bx, bz) -> {
                  long key = ChunkPos.toLong(bx >> 4, bz >> 4);
                  WorldChunk chunk = cache.computeIfAbsent(key, k -> world.getChunkManager().getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false));
                  Type type = Type.WORLD_SURFACE;
                  if (chunk != null && chunk.hasHeightmap(type)) {
                     int top = chunk.sampleHeightmap(type, bx & 15, bz & 15);

                     for (int y = top; y >= world.getBottomY() && y > top - 16; y--) {
                        mutable.set(bx, y, bz);
                        BlockState state = chunk.getBlockState(mutable);
                        MapColor color = state.getMapColor(world, mutable);
                        if (!state.isAir() && color != MapColor.CLEAR) {
                           return new StashSurfaceMap.Column(color.color, y);
                        }
                     }

                     return null;
                  } else {
                     return null;
                  }
               }, () -> System.nanoTime() - start >= 8000000L);
            } catch (RuntimeException var9) {
               ReviveClient.LOG.warn("Stash surface capture unavailable ({}); sending text only", var9.getClass().getSimpleName());
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private void queueHitWebhook(String destination, String json, int x, int z) {
      this.queueHitWebhook(destination, json, x, z, false);
   }

   private void queueHitWebhook(String destination, String json, int x, int z, boolean mansionFilterable) {
      boolean render3d = this.webhook3dPreview.get();
      boolean filter = mansionFilterable && this.ignoreMansionWebhooks.get();
      if ((render3d || filter) && this.mc.world != null && this.mc.world.getRegistryKey().equals(World.OVERWORLD)) {
         this.checkSignalWorld();
         StashFinderPlus.Preview preview = new StashFinderPlus.Preview(
            destination, json, this.mc.world, new StashScene.Capture(x, z), render3d ? null : this.captureSurface(x, z), render3d, filter
         );
         if (this.previews.size() >= 16) {
            this.finishPreview(preview);
         } else {
            this.previews.add(preview);
         }
      } else {
         this.queueSignalWebhook(destination, json, this.captureSurface(x, z));
      }
   }

   private void flushPreviews() {
      while (!this.previews.isEmpty()) {
         this.finishPreview(this.previews.remove());
      }
   }

   private void finishPreview(StashFinderPlus.Preview preview) {
      StashScene.Snapshot snapshot = preview.capture().snapshot();
      if (preview.filter() && StashScene.likelyMansion(snapshot)) {
         ReviveClient.LOG.info("StashFinderPlus: suppressed likely-mansion storage webhook at {}, {}; local hit retained", snapshot.x(), snapshot.z());
      } else {
         this.queueSignalWebhook(preview.destination(), preview.json(), preview.surface(), preview.render() ? snapshot : null);
      }
   }

   private void processPreviews() {
      if (this.mc.isOnThread() && !this.previews.isEmpty()) {
         StashFinderPlus.Preview preview = this.previews.peek();
         if (preview.world() == this.mc.world && this.mc.world != null) {
            long deadline = System.nanoTime() + 2000000L;
            ClientWorld world = this.mc.world;
            Map<Long, WorldChunk> cache = new HashMap<>();
            Mutable mutable = new Mutable();

            try {
               boolean done = preview.capture().step((bx, bz) -> {
                  long key = ChunkPos.toLong(bx >> 4, bz >> 4);
                  WorldChunk chunk = cache.computeIfAbsent(key, k -> world.getChunkManager().getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false));
                  Type type = Type.WORLD_SURFACE;
                  if (chunk != null && chunk.hasHeightmap(type)) {
                     int top = chunk.sampleHeightmap(type, bx & 15, bz & 15);
                     int rgb = 0;
                     int surfaceY = top;
                     int evidence = 0;
                     boolean found = false;

                     for (int y = top; y >= world.getBottomY() && y > top - 16; y--) {
                        mutable.set(bx, y, bz);
                        BlockState state = chunk.getBlockState(mutable);
                        MapColor color = state.getMapColor(world, mutable);
                        if (!state.isAir() && color != MapColor.CLEAR) {
                           rgb = color.color;
                           surfaceY = y;
                           found = true;
                           if (state.isOf(Blocks.DARK_OAK_PLANKS) || state.isOf(Blocks.DARK_OAK_STAIRS) || state.isOf(Blocks.DARK_OAK_SLAB)) {
                              evidence |= 1;
                           }

                           if (state.isOf(Blocks.DARK_OAK_STAIRS)) {
                              evidence |= 2;
                           }
                           break;
                        }
                     }

                     if (!found) {
                        return null;
                     } else {
                        if (preview.filter() && (bx & 3) == 0 && (bz & 3) == 0) {
                           for (int yx = surfaceY; yx >= world.getBottomY() && yx > surfaceY - 48; yx--) {
                              mutable.set(bx, yx, bz);
                              BlockState state = chunk.getBlockState(mutable);
                              if (state.isOf(Blocks.BIRCH_PLANKS)) {
                                 evidence |= 4;
                              }

                              if (state.isOf(Blocks.COBBLESTONE)) {
                                 evidence |= 8;
                              }

                              if (state.isOf(Blocks.GLASS_PANE)) {
                                 evidence |= 16;
                              }
                           }
                        }

                        return new StashScene.Column(rgb, surfaceY, evidence);
                     }
                  } else {
                     return null;
                  }
               }, () -> System.nanoTime() >= deadline);
               if (done) {
                  this.previews.remove();
                  this.finishPreview(preview);
               }
            } catch (RuntimeException var8) {
               ReviveClient.LOG.warn("Stash 3D sampling incomplete ({})", var8.getClass().getSimpleName());
               this.previews.remove();
               this.finishPreview(preview);
            }
         } else {
            this.previews.remove();
            this.finishPreview(preview);
         }
      }
   }

   private void queueSignalWebhook(String destination, String json) {
      this.queueSignalWebhook(destination, json, null);
   }

   private void queueSignalWebhook(String destination, String json, StashSurfaceMap.Snapshot surface) {
      this.queueSignalWebhook(destination, json, surface, null);
   }

   private void queueSignalWebhook(String destination, String json, StashSurfaceMap.Snapshot surface, StashScene.Snapshot scene) {
      if (this.signalWebhooks == null || this.signalWebhooks.isShutdown()) {
         this.signalWebhooks = new ThreadPoolExecutor(1, 1, 10L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), r -> {
            Thread t = new Thread(r, "StashFinderPlus-Webhooks");
            t.setDaemon(true);
            return t;
         }, new AbortPolicy());
      }

      try {
         this.signalWebhooks.execute(() -> sendSignalWebhook(destination, json, surface, scene));
      } catch (RejectedExecutionException var6) {
         if (System.nanoTime() - this.lastQueueWarning > 10000000000L) {
            ReviveClient.LOG.warn("Stash notification webhook queue is full; some alerts were not sent.");
            this.lastQueueWarning = System.nanoTime();
         }
      }
   }

   private static void sendSignalWebhook(String destination, String json, StashSurfaceMap.Snapshot surface, StashScene.Snapshot scene) {
      HttpURLConnection connection = null;

      try {
         URI uri = URI.create(destination);
         if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("HTTPS required");
         }

         connection = (HttpURLConnection)uri.toURL().openConnection();
         connection.setConnectTimeout(5000);
         connection.setReadTimeout(10000);
         connection.setInstanceFollowRedirects(false);

         StashWebhook.Body body;
         try {
            body = scene != null
               ? StashWebhook.body(json, StashScene.png(scene), true)
               : StashWebhook.body(json, surface == null ? null : StashSurfaceMap.png(surface));
         } catch (IOException | RuntimeException var19) {
            ReviveClient.LOG.warn("Stash surface image unavailable ({}); sending text only", var19.getClass().getSimpleName());
            body = StashWebhook.body(json, null);
         }

         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", body.contentType());
         connection.setRequestProperty("User-Agent", "StashFinderPlus-Webhook/1.0");
         connection.setDoOutput(true);
         byte[] bytes = body.bytes();
         connection.setFixedLengthStreamingMode(bytes.length);

         try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
         }

         int code = connection.getResponseCode();
         if (code < 200 || code >= 300) {
            ReviveClient.LOG.warn("Stash notification webhook returned HTTP {}", code);
         }

         Thread.sleep(600L);
      } catch (InterruptedException var21) {
         Thread.currentThread().interrupt();
      } catch (Exception var22) {
         ReviveClient.LOG.warn("Stash notification webhook failed ({})", var22.getClass().getSimpleName());
      } finally {
         if (connection != null) {
            connection.disconnect();
         }
      }
   }

   private String serverName() {
      return this.mc.getCurrentServerEntry() == null ? "singleplayer" : this.mc.getCurrentServerEntry().address;
   }

   private void archiveHit(JsonObject hit) {
      if (this.saveHitArchive.get() || this.sendDatabase.get()) {
         StashArchive.Cloud cloud = this.sendDatabase.get()
            ? new StashArchive.Cloud(this.databaseUrl.get(), this.databaseKey.get(), this.databaseToken.get())
            : null;
         this.hitArchive.submit(hit, cloud);
      }
   }

   private void onChunkData(WorldChunk worldChunk) {
      ClientWorld world = this.mc.world;
      if (!this.mc.isOnThread()) {
         this.mc.execute(() -> {
            if (this.isActive() && world != null && this.mc.world == world) {
               this.onChunkData(worldChunk);
            }
         });
      } else if (this.isActive() && world != null && this.mc.player != null) {
         double ax = Math.abs(worldChunk.getPos().x * 16);
         double az = Math.abs(worldChunk.getPos().z * 16);
         if (!(Math.sqrt(ax * ax + az * az) < this.minimumDistance.get().intValue())) {
            StashFinderPlus.Chunk chunk = new StashFinderPlus.Chunk(worldChunk.getPos());
            RegistryKey<World> dimension = this.mc.world.getRegistryKey();
            ChunkPos chunkPos = chunk.chunkPos;
            boolean isNew = XaeroCompat.isNewChunk(chunkPos.x, chunkPos.z, dimension);
            boolean isOld = XaeroCompat.isOldChunk(chunkPos.x, chunkPos.z, dimension);
            if (!this.onlyOldchunks.get() || !isNew || isOld) {
               for (BlockEntity blockEntity : worldChunk.getBlockEntities().values()) {
                  if (StashStorageSelection.matches(this.storageBlocks.get(), blockEntity.getType())) {
                     Block below = this.mc.world.getBlockState(blockEntity.getPos().down()).getBlock();
                     if ((!this.ignoreTrialChambers.get() || !below.equals(Blocks.WAXED_OXIDIZED_CUT_COPPER))
                        && !below.equals(Blocks.TUFF_BRICKS)
                        && !below.equals(Blocks.WAXED_COPPER_BLOCK)
                        && !below.equals(Blocks.WAXED_OXIDIZED_COPPER)) {
                        if (blockEntity instanceof ChestBlockEntity) {
                           chunk.chests++;
                        } else if (blockEntity instanceof BarrelBlockEntity) {
                           chunk.barrels++;
                        } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
                           chunk.shulkers++;
                        } else if (blockEntity instanceof EnderChestBlockEntity) {
                           chunk.enderChests++;
                        } else if (blockEntity instanceof AbstractFurnaceBlockEntity) {
                           chunk.furnaces++;
                        } else if (blockEntity instanceof DispenserBlockEntity) {
                           chunk.dispensersDroppers++;
                        } else if (blockEntity instanceof HopperBlockEntity) {
                           chunk.hoppers++;
                        } else if (blockEntity instanceof CrafterBlockEntity) {
                           chunk.crafters++;
                        }
                     }
                  }
               }

               boolean hit = chunk.getTotal() >= this.minimumStorageCount.get()
                  || this.shulkerInstantHit.get() && chunk.shulkers > 0
                  || this.crafterInstantHit.get() && chunk.crafters > 0;
               if (hit) {
                  StashFinderPlus.Chunk previous = null;
                  int index = this.chunks.indexOf(chunk);
                  if (index < 0) {
                     this.chunks.add(chunk);
                  } else {
                     previous = this.chunks.set(index, chunk);
                  }

                  this.saveJson();
                  this.saveCsv();
                  if (!chunk.equals(previous) || !chunk.countsEqual(previous)) {
                     JsonObject archived = StashArchive.base("stash", dimension.getValue().toString(), this.serverName(), chunk.x, null, chunk.z);
                     JsonObject counts = archived.getAsJsonObject("counts");
                     counts.addProperty("chests", chunk.chests);
                     counts.addProperty("barrels", chunk.barrels);
                     counts.addProperty("shulkers", chunk.shulkers);
                     counts.addProperty("ender_chests", chunk.enderChests);
                     counts.addProperty("hoppers", chunk.hoppers);
                     counts.addProperty("dispensers_droppers", chunk.dispensersDroppers);
                     counts.addProperty("furnaces", chunk.furnaces);
                     counts.addProperty("crafters", chunk.crafters);
                     this.archiveHit(archived);
                     this.queueStashSound(chunk.shulkers > 0);
                     if (this.sendNotifications.get()) {
                        if (this.notificationMode.get() != StashFinderPlus.Mode.Toast) {
                           this.info("Found stash at (highlight)%s(default), (highlight)%s(default).", new Object[]{chunk.x, chunk.z});
                        }

                        if (this.notificationMode.get() != StashFinderPlus.Mode.Chat) {
                           this.mc.getToastManager().add(new ReviveToast(Items.CHEST, "Stash Found!", "Found stash at " + chunk.x + ", " + chunk.z));
                        }
                     }

                     if (this.sendWebhook.get() && !this.webhookLink.get().isEmpty()) {
                        boolean mansionFilterable = chunk.shulkers == 0
                           && chunk.enderChests == 0
                           && chunk.crafters == 0
                           && chunk.hoppers == 0
                           && chunk.barrels == 0;
                        String pingId = this.ping.get() ? this.discordId.get() : null;
                        if (this.advancedLogging.get()) {
                           String chunkType;
                           if (isNew && !isOld) {
                              chunkType = "new";
                           } else if (isNew) {
                              chunkType = "unfollowed 1.12";
                           } else if (!isOld) {
                              chunkType = "1.19";
                           } else {
                              chunkType = "followed 1.12";
                           }

                           String json = "{\"embeds\": [{\"title\": \"Stash Found!\",\"color\": 2154012,\"description\": \"Coordinates: || X: "
                              + chunk.x
                              + " Z: "
                              + chunk.z
                              + "|| in "
                              + chunkType
                              + " chunks in the"
                              + dimension.getValue().getPath()
                              + "\",\"fields\": [{\"name\": \"Chests\",\"value\": "
                              + chunk.chests
                              + ",\"inline\": true},{\"name\": \"Barrels\",\"value\": "
                              + chunk.barrels
                              + ",\"inline\": true},{\"name\": \"Shulkers\",\"value\": "
                              + chunk.shulkers
                              + ",\"inline\": true},{\"name\": \"Ender Chests\",\"value\": "
                              + chunk.enderChests
                              + ",\"inline\": true},{\"name\": \"Hoppers\",\"value\": "
                              + chunk.hoppers
                              + ",\"inline\": true},{\"name\": \"Dispensers/Droppers\",\"value\": "
                              + chunk.dispensersDroppers
                              + ",\"inline\": true},{\"name\": \"Furnaces\",\"value\": "
                              + chunk.furnaces
                              + ",\"inline\": true},{\"name\": \"Crafters\",\"value\": "
                              + chunk.crafters
                              + ",\"inline\": true}]}]}";
                           JsonObject embed = JsonParser.parseString(json).getAsJsonObject();

                           for (JsonElement element : embed.getAsJsonArray("embeds").get(0).getAsJsonObject().getAsJsonArray("fields")) {
                              JsonObject field = element.getAsJsonObject();
                              field.addProperty("value", field.get("value").getAsString());
                           }

                           this.queueHitWebhook(this.webhookLink.get(), StashWebhook.withPing(embed.toString(), pingId), chunk.x, chunk.z, mansionFilterable);
                        } else {
                           this.queueHitWebhook(
                              this.webhookLink.get(),
                              StashWebhook.stash("Found stash at " + chunk.x + ", " + chunk.z + ".", pingId),
                              chunk.x,
                              chunk.z,
                              mansionFilterable
                           );
                        }
                     }

                     if (this.saveToWaypoints.get()) {
                        Object set = XaeroCompat.getWaypointSet();
                        if (set == null) {
                           return;
                        }

                        this.addToWaypoints(set, chunk);
                        XaeroCompat.refreshWaypoints();
                     }

                     if (this.disconnectOnStashFound.get()) {
                        String prefix = Config.get().prefix.get();
                        MutableText reason = Text.literal(
                              String.format(
                                    "%s%s%s%s %s",
                                    Formatting.GRAY,
                                    Formatting.BLUE,
                                    prefix.substring(0, Math.max(0, prefix.length() - 1)),
                                    Formatting.GRAY,
                                    Formatting.RED
                                 )
                                 + String.format("Found stash at %s, %s.", chunk.x, chunk.z)
                           )
                           .append("\n");
                        this.disconnectOnStashFound.set(false);
                        ClientPlayNetworkHandler handler = this.mc.getNetworkHandler();
                        if (handler != null) {
                           handler.getConnection().disconnect(reason);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      this.chunks.sort(Comparator.comparingInt(c -> -c.getTotal()));
      WVerticalList list = theme.verticalList();
      WButton clear = list.add(theme.button("Clear")).widget();
      WButton addAll = list.add(theme.button("Add All to Waypoints")).widget();
      WTable table = new WTable();
      if (!this.chunks.isEmpty()) {
         list.add(table);
      }

      clear.action = () -> {
         this.removeAllStashWaypoints(this.chunks);
         this.chunks.clear();
         table.clear();
      };
      addAll.action = () -> {
         Object set = XaeroCompat.getWaypointSet();
         if (set == null) {
            this.error("Unable to get waypoint set.", new Object[0]);
         } else {
            int added = 0;

            for (StashFinderPlus.Chunk chunk : this.chunks) {
               if (XaeroCompat.findWaypoint(set, chunk.x, chunk.z, null) == null) {
                  this.addToWaypoints(set, chunk);
                  added++;
               }
            }

            XaeroCompat.refreshWaypoints();
            this.info("Added (highlight)%d(default) stashes to waypoints.", new Object[]{added});
         }
      };
      this.fillTable(theme, table);
      return list;
   }

   private void fillTable(GuiTheme theme, WTable table) {
      for (StashFinderPlus.Chunk chunk : this.chunks) {
         table.add(theme.label("Pos: " + chunk.x + ", " + chunk.z));
         table.add(theme.label("Total: " + chunk.getTotal()));
         WButton open = table.add(theme.button("Open")).widget();
         open.action = () -> this.mc.setScreen(new StashFinderPlus.ChunkScreen(theme, chunk));
         WButton gotoBtn = table.add(theme.button("Goto")).widget();
         gotoBtn.action = () -> {
            if (BaritoneBridge.isPresent()) {
               BaritoneBridge.setGoalXZAndPath(chunk.x, chunk.z);
            } else {
               this.error("Baritone is required for Goto.", new Object[0]);
            }
         };
         WMinus delete = table.add(theme.minus()).widget();
         delete.action = () -> {
            if (this.chunks.remove(chunk)) {
               table.clear();
               this.fillTable(theme, table);
               this.saveJson();
               this.saveCsv();
               Object set = XaeroCompat.getWaypointSet();
               if (set != null) {
                  Object waypoint = XaeroCompat.findWaypoint(set, chunk.x, chunk.z, null);
                  if (waypoint != null) {
                     XaeroCompat.removeWaypoint(set, waypoint);
                     XaeroCompat.refreshWaypoints();
                  }
               }
            }
         };
         table.row();
      }
   }

   private void load() {
      boolean loaded = false;
      File file = this.getJsonFile();
      if (!file.exists() && !this.getCsvFile().exists()) {
         File legacy = new File(new File(new File(ReviveClient.FOLDER, "better-stash-finder"), Utils.getFileWorldName()), "stashes.json");
         if (legacy.exists()) {
            file = legacy;
         }
      }

      if (file.exists()) {
         try (FileReader reader = new FileReader(file)) {
            List<StashFinderPlus.Chunk> read = (List<StashFinderPlus.Chunk>)GSON.fromJson(reader, (new TypeToken<List<StashFinderPlus.Chunk>>() {}).getType());
            this.chunks = (List<StashFinderPlus.Chunk>)(read != null ? read : new ArrayList<>());

            for (StashFinderPlus.Chunk chunk : this.chunks) {
               chunk.calculatePos();
            }

            loaded = true;
         } catch (Exception var12) {
            if (this.chunks == null) {
               this.chunks = new ArrayList<>();
            }
         }
      }

      file = this.getCsvFile();
      if (!loaded && file.exists()) {
         try (BufferedReader readerx = new BufferedReader(new FileReader(file))) {
            readerx.readLine();

            String line;
            while ((line = readerx.readLine()) != null) {
               String[] values = line.split(",");
               StashFinderPlus.Chunk chunk = new StashFinderPlus.Chunk(new ChunkPos(Integer.parseInt(values[0]), Integer.parseInt(values[1])));
               chunk.chests = Integer.parseInt(values[2]);
               chunk.barrels = Integer.parseInt(values[3]);
               chunk.shulkers = Integer.parseInt(values[4]);
               chunk.enderChests = Integer.parseInt(values[5]);
               chunk.furnaces = Integer.parseInt(values[6]);
               chunk.dispensersDroppers = Integer.parseInt(values[7]);
               chunk.hoppers = Integer.parseInt(values[8]);
               if (values.length > 9) {
                  chunk.crafters = Integer.parseInt(values[9]);
               }

               this.chunks.add(chunk);
            }
         } catch (Exception var10) {
            if (this.chunks == null) {
               this.chunks = new ArrayList<>();
            }
         }
      }
   }

   private void saveCsv() {
      try {
         File file = this.getCsvFile();
         file.getParentFile().mkdirs();

         try (FileWriter writer = new FileWriter(file)) {
            writer.write("X,Z,Chests,Barrels,Shulkers,EnderChests,Furnaces,DispensersDroppers,Hoppers,Crafters\n");

            for (StashFinderPlus.Chunk chunk : this.chunks) {
               chunk.write(writer);
            }
         }
      } catch (IOException var7) {
         ReviveClient.LOG.warn("StashFinderPlus: could not save stashes.csv", var7);
      }
   }

   private void saveJson() {
      try {
         File file = this.getJsonFile();
         file.getParentFile().mkdirs();

         try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this.chunks, writer);
         }
      } catch (IOException var7) {
         ReviveClient.LOG.warn("StashFinderPlus: could not save stashes.json", var7);
      }
   }

   private File getJsonFile() {
      return new File(new File(new File(ReviveClient.FOLDER, "stash-finder-plus"), Utils.getFileWorldName()), "stashes.json");
   }

   private File getCsvFile() {
      return new File(new File(new File(ReviveClient.FOLDER, "stash-finder-plus"), Utils.getFileWorldName()), "stashes.csv");
   }

   @Override
   public String getInfoString() {
      return String.valueOf(this.chunks.size());
   }

   private void removeAllStashWaypoints(List<StashFinderPlus.Chunk> list) {
      Object set = XaeroCompat.getWaypointSet();
      if (set != null) {
         for (StashFinderPlus.Chunk chunk : list) {
            Object waypoint = XaeroCompat.findWaypoint(set, chunk.x, chunk.z, null);
            if (waypoint != null) {
               XaeroCompat.removeWaypoint(set, waypoint);
            }
         }

         XaeroCompat.refreshWaypoints();
      }
   }

   private void addToWaypoints(Object set, StashFinderPlus.Chunk chunk) {
      if (XaeroCompat.findWaypoint(set, chunk.x, chunk.z, null) == null) {
         int color;
         if (chunk.getTotal() < 15) {
            color = 10;
         } else if (chunk.getTotal() < 50) {
            color = 14;
         } else if (chunk.getTotal() < 100) {
            color = 12;
         } else {
            color = 4;
         }

         XaeroCompat.addWaypoint(set, chunk.x, 70, chunk.z, getWaypointName(chunk), "S", color, 0, false);
      }
   }

   private static String getWaypointName(StashFinderPlus.Chunk chunk) {
      StringBuilder name = new StringBuilder();
      if (chunk.chests > 0) {
         name.append("C:").append(chunk.chests);
      }

      if (chunk.barrels > 0) {
         name.append("B:").append(chunk.barrels);
      }

      if (chunk.shulkers > 0) {
         name.append("S:").append(chunk.shulkers);
      }

      if (chunk.enderChests > 0) {
         name.append("E:").append(chunk.enderChests);
      }

      if (chunk.hoppers > 0) {
         name.append("H:").append(chunk.hoppers);
      }

      if (chunk.dispensersDroppers > 0) {
         name.append("D:").append(chunk.dispensersDroppers);
      }

      if (chunk.furnaces > 0) {
         name.append("F:").append(chunk.furnaces);
      }

      if (chunk.crafters > 0) {
         name.append("A:").append(chunk.crafters);
      }

      return name.toString();
   }

   private void waypointSettingChanged(boolean enabled) {
      if (!enabled) {
         this.removeAllStashWaypoints(this.chunks);
      } else {
         Object set = XaeroCompat.getWaypointSet();
         if (set != null) {
            for (StashFinderPlus.Chunk chunk : this.chunks) {
               this.addToWaypoints(set, chunk);
            }

            XaeroCompat.refreshWaypoints();
         }
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onSoundTick(TickEvent.Post event) {
      if (this.pendingSounds > 0 && this.mc.player != null) {
         if (this.soundTimer > 0) {
            this.soundTimer--;
         } else {
            SoundEvent sound = this.pendingSoundEvent;
            if (sound != null) {
               this.mc.player.playSound(sound, this.soundVolume.get().floatValue(), this.soundPitch.get().floatValue());
            }

            this.pendingSounds--;
            this.soundTimer = this.soundRepeatDelay.get();
         }
      }
   }

   private void queueStashSound(boolean shulkers) {
      if (this.playSound.get()) {
         this.pendingSoundEvent = (shulkers ? this.shulkerSound.get() : this.stashSound.get()).get();
         this.pendingSounds = this.soundRepeats.get();
         this.soundTimer = 0;
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onOpenScreenEvent(OpenScreenEvent event) {
      if (event.screen instanceof DeathScreen && this.disableOnTeleport.get()) {
         this.toggle();
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onPlayerMove(PlayerMoveEvent event) {
      if (this.lastPosition != null && this.disableOnTeleport.get() && this.mc.player.squaredDistanceTo(this.lastPosition) > 256.0) {
         this.toggle();
      }

      this.lastPosition = this.mc.player.getEntityPos();
   }

   public static class Chunk {
      private static final StringBuilder sb = new StringBuilder();
      public ChunkPos chunkPos;
      public transient int x;
      public transient int z;
      public int chests;
      public int barrels;
      public int shulkers;
      public int enderChests;
      public int furnaces;
      public int dispensersDroppers;
      public int hoppers;
      public int crafters;

      public Chunk(ChunkPos chunkPos) {
         this.chunkPos = chunkPos;
         this.calculatePos();
      }

      public void calculatePos() {
         this.x = this.chunkPos.x * 16 + 8;
         this.z = this.chunkPos.z * 16 + 8;
      }

      public int getTotal() {
         return this.chests + this.barrels + this.shulkers + this.enderChests + this.furnaces + this.dispensersDroppers + this.hoppers + this.crafters;
      }

      public void write(Writer writer) throws IOException {
         sb.setLength(0);
         sb.append(this.x)
            .append(',')
            .append(this.z)
            .append(',')
            .append(this.chests)
            .append(',')
            .append(this.barrels)
            .append(',')
            .append(this.shulkers)
            .append(',')
            .append(this.enderChests)
            .append(',')
            .append(this.furnaces)
            .append(',')
            .append(this.dispensersDroppers)
            .append(',')
            .append(this.hoppers)
            .append(',')
            .append(this.crafters)
            .append('\n');
         writer.write(sb.toString());
      }

      public boolean countsEqual(StashFinderPlus.Chunk other) {
         return other == null
            ? false
            : this.chests != other.chests
               || this.barrels != other.barrels
               || this.shulkers != other.shulkers
               || this.enderChests != other.enderChests
               || this.furnaces != other.furnaces
               || this.dispensersDroppers != other.dispensersDroppers
               || this.hoppers != other.hoppers
               || this.crafters != other.crafters;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return o != null && this.getClass() == o.getClass() ? Objects.equals(this.chunkPos, ((StashFinderPlus.Chunk)o).chunkPos) : false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.chunkPos);
      }
   }

   private static class ChunkScreen extends WindowScreen {
      private final StashFinderPlus.Chunk chunk;

      public ChunkScreen(GuiTheme theme, StashFinderPlus.Chunk chunk) {
         super(theme, "Chunk at " + chunk.x + ", " + chunk.z);
         this.chunk = chunk;
      }

      @Override
      public void initWidgets() {
         WTable t = this.add(this.theme.table()).expandX().widget();
         t.add(this.theme.label("Total:"));
         t.add(this.theme.label(this.chunk.getTotal() + ""));
         t.row();
         t.add(this.theme.horizontalSeparator()).expandX();
         t.row();
         this.row(t, "Chests:", this.chunk.chests);
         this.row(t, "Barrels:", this.chunk.barrels);
         this.row(t, "Shulkers:", this.chunk.shulkers);
         this.row(t, "Ender Chests:", this.chunk.enderChests);
         this.row(t, "Furnaces:", this.chunk.furnaces);
         this.row(t, "Dispensers and droppers:", this.chunk.dispensersDroppers);
         this.row(t, "Hoppers:", this.chunk.hoppers);
         t.add(this.theme.label("Crafters:"));
         t.add(this.theme.label(this.chunk.crafters + ""));
      }

      private void row(WTable t, String label, int value) {
         t.add(this.theme.label(label));
         t.add(this.theme.label(value + ""));
         t.row();
      }
   }

   public static enum Mode {
      Chat,
      Toast,
      Both;
   }

   private record Preview(
      String destination, String json, Object world, StashScene.Capture capture, StashSurfaceMap.Snapshot surface, boolean render, boolean filter
   ) {
   }

   public static enum StashSound {
      Pling,
      Bell,
      ExperienceOrb,
      Anvil,
      LevelUp,
      DragonGrowl,
      WitherSpawn,
      Beacon;

      public SoundEvent get() {
         return switch (this) {
            case Pling -> (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case Bell -> SoundEvents.BLOCK_BELL_USE;
            case ExperienceOrb -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case Anvil -> SoundEvents.BLOCK_ANVIL_LAND;
            case LevelUp -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            case DragonGrowl -> SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
            case WitherSpawn -> SoundEvents.ENTITY_WITHER_SPAWN;
            case Beacon -> SoundEvents.BLOCK_BEACON_ACTIVATE;
         };
      }
   }
}
