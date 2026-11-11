package revivedevelopment.reviveclient.systems.modules.hunting;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.ChunkDataEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.mixininterface.IChatHud;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.IntSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.render.RenderUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;

public class ChatSigns extends Module {
   private final SettingGroup sgModes = this.settings.createGroup("Modes");
   private final SettingGroup sgFormat = this.settings.createGroup("Formatting");
   private final SettingGroup sgBlacklist = this.settings.createGroup("Blacklist");
   private final SettingGroup sgEsp = this.settings.createGroup("ESP");
   private final Setting<ChatSigns.ChatMode> chatMode = this.sgModes
      .add(((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("chat-mode")).defaultValue(ChatSigns.ChatMode.Both)).build());
   private final Setting<ChatSigns.RepeatMode> repeatMode = this.sgModes
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("repeat-mode"))
                  .defaultValue(ChatSigns.RepeatMode.Cooldown))
               .visible(() -> this.chatMode.get() != ChatSigns.ChatMode.ESP))
            .build()
      );
   private final Setting<Integer> repeatSeconds = this.sgModes
      .add(
         new IntSetting.Builder()
            .name("repeat-cooldown")
            .range(1, 3600)
            .sliderRange(1, 120)
            .defaultValue(10)
            .visible(() -> this.repeatMode.get() == ChatSigns.RepeatMode.Cooldown && this.chatMode.get() != ChatSigns.ChatMode.ESP)
            .build()
      );
   private final Setting<Integer> chatSpeed = this.sgModes
      .add(
         new IntSetting.Builder()
            .name("chat-speed")
            .description("Ticks to wait before printing the next queued sign.")
            .range(0, 500)
            .sliderRange(0, 100)
            .defaultValue(0)
            .build()
      );
   private final Setting<Boolean> ignoreDuplicates = this.sgModes.add(new BoolSetting.Builder().name("ignore-duplicates").defaultValue(false).build());
   private final Setting<Boolean> showCoords = this.sgFormat.add(new BoolSetting.Builder().name("show-coordinates").defaultValue(false).build());
   private final Setting<Boolean> fancyDisplay = this.sgFormat
      .add(new BoolSetting.Builder().name("fancy-display").description("Print sign lines on separate chat lines.").defaultValue(false).build());
   private final Setting<Boolean> signBlacklist = this.sgBlacklist
      .add(
         new BoolSetting.Builder()
            .name("sign-text-blacklist")
            .description("Ignore signs containing lines from chatsigns-blacklist.txt.")
            .defaultValue(false)
            .onChanged(value -> {
               if (value && this.isActive()) {
                  this.loadBlacklist();
               }
            })
            .build()
      );
   private final Setting<Boolean> caseSensitive = this.sgBlacklist
      .add(new BoolSetting.Builder().name("case-sensitive").defaultValue(false).visible(this.signBlacklist::get).build());
   private final Setting<Boolean> renderSeenSigns = this.sgEsp
      .add(new BoolSetting.Builder().name("render-seen-signs").description("Render boxes around signs printed to chat.").defaultValue(false).build());
   private final Setting<SettingColor> lineColor = this.sgEsp
      .add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(51, 102, 102, 255)).visible(this.renderSeenSigns::get).build());
   private final Setting<SettingColor> sideColor = this.sgEsp
      .add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(51, 102, 102, 25)).visible(this.renderSeenSigns::get).build());
   private final Set<BlockPos> printedSigns = new HashSet<>();
   private final Set<String> printedTexts = new HashSet<>();
   private final Set<BlockPos> highlightedSigns = new HashSet<>();
   private final Map<BlockPos, Instant> cooldowns = new HashMap<>();
   private final ArrayDeque<ChatSigns.ChatJob> jobs = new ArrayDeque<>();
   private final List<String> blacklist = new ArrayList<>();
   private BlockPos lastFocusedSign;
   private int timer;
   private int chatTimer;

   public ChatSigns() {
      super(Categories.Hunting, "chat-signs", "Read nearby signs in your chat.");
   }

   @Override
   public void onActivate() {
      if (this.signBlacklist.get()) {
         this.loadBlacklist();
      }

      this.scanLoadedChunks();
   }

   @Override
   public void onDeactivate() {
      this.timer = 0;
      this.chatTimer = 0;
      this.printedSigns.clear();
      this.printedTexts.clear();
      this.highlightedSigns.clear();
      this.cooldowns.clear();
      this.jobs.clear();
      this.blacklist.clear();
      this.lastFocusedSign = null;
   }

   @EventHandler
   private void onChunkData(ChunkDataEvent event) {
      if (this.chatMode.get() != ChatSigns.ChatMode.Targeted) {
         this.readSigns(this.signsIn(event.chunk()));
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.world != null && this.mc.player != null) {
         if (++this.chatTimer >= Math.max(1, this.chatSpeed.get()) && !this.jobs.isEmpty()) {
            this.chatTimer = 0;
            ChatSigns.ChatJob job = this.jobs.removeFirst();
            ((IChatHud)this.mc.inGameHud.getChatHud()).revive$add(job.message(), job.hash());
         }

         if (this.chatMode.get() != ChatSigns.ChatMode.ESP) {
            if (++this.timer >= 5) {
               this.timer = 0;
               BlockPos targeted = this.targetedSign();
               if (targeted == null) {
                  this.lastFocusedSign = null;
               } else if (!targeted.equals(this.lastFocusedSign) || this.repeatMode.get() != ChatSigns.RepeatMode.Focus) {
                  this.lastFocusedSign = targeted;
                  Instant now = Instant.now();
                  Instant last = this.cooldowns.get(targeted);
                  if ((
                        this.repeatMode.get() != ChatSigns.RepeatMode.Cooldown
                           || last == null
                           || Duration.between(last, now).toSeconds() >= this.repeatSeconds.get().intValue()
                     )
                     && this.mc.world.getBlockEntity(targeted) instanceof SignBlockEntity sign) {
                     this.readSigns(List.of(sign));
                     this.cooldowns.put(targeted, now);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.renderSeenSigns.get() && this.mc.world != null && this.mc.player != null) {
         double maxDist = (Integer)this.mc.options.getViewDistance().getValue() * 16 + 32;
         this.highlightedSigns.removeIf(posx -> !posx.isWithinDistance(this.mc.player.getBlockPos(), maxDist));

         for (BlockPos pos : this.highlightedSigns) {
            if (this.mc.world.getBlockEntity(pos) instanceof SignBlockEntity) {
               BlockState state = this.mc.world.getBlockState(pos);
               VoxelShape shape = state.getOutlineShape(this.mc.world, pos);
               if (!shape.isEmpty()) {
                  double x1 = pos.getX() + shape.getMin(Axis.X);
                  double y1 = pos.getY() + shape.getMin(Axis.Y);
                  double z1 = pos.getZ() + shape.getMin(Axis.Z);
                  double x2 = pos.getX() + shape.getMax(Axis.X);
                  double y2 = pos.getY() + shape.getMax(Axis.Y);
                  double z2 = pos.getZ() + shape.getMax(Axis.Z);
                  event.renderer.box(x1, y1, z1, x2, y2, z2, this.sideColor.get(), this.lineColor.get(), ShapeMode.Both, 0);
                  Vec3d center = Vec3d.ofCenter(pos);
                  event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, center.x, center.y, center.z, this.lineColor.get());
               }
            }
         }
      }
   }

   private void scanLoadedChunks() {
      if (this.mc.world != null && this.mc.player != null && this.chatMode.get() != ChatSigns.ChatMode.Targeted) {
         BlockPos origin = this.mc.player.getBlockPos();
         int view = (Integer)this.mc.options.getViewDistance().getValue();
         int startX = origin.getX() - view * 16 >> 4;
         int endX = origin.getX() + view * 16 >> 4;
         int startZ = origin.getZ() - view * 16 >> 4;
         int endZ = origin.getZ() + view * 16 >> 4;

         for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
               if (this.mc.world.getChunkManager().isChunkLoaded(x, z)) {
                  this.readSigns(this.signsIn(this.mc.world.getChunk(x, z)));
               }
            }
         }
      }
   }

   private List<SignBlockEntity> signsIn(Chunk chunk) {
      if (chunk instanceof WorldChunk worldChunk) {
         ArrayList signs = new ArrayList();

         for (BlockEntity entity : worldChunk.getBlockEntities().values()) {
            if (entity instanceof SignBlockEntity sign) {
               signs.add(sign);
            }
         }

         return signs;
      } else {
         return List.of();
      }
   }

   private void readSigns(List<SignBlockEntity> signs) {
      if (this.mc.world != null && !signs.isEmpty()) {
         for (SignBlockEntity sign : signs) {
            BlockPos pos = sign.getPos();
            String text = this.signText(sign);
            if (!text.isBlank()
               && !this.isBlacklisted(text)
               && (!this.ignoreDuplicates.get() || !this.printedTexts.contains(text))
               && (this.chatMode.get() != ChatSigns.ChatMode.ESP || !this.printedSigns.contains(pos))
               && (this.chatMode.get() != ChatSigns.ChatMode.Both || !this.printedSigns.contains(pos) || pos.equals(this.lastFocusedSign))) {
               this.printedSigns.add(pos);
               this.printedTexts.add(text);
               this.highlightedSigns.add(pos);
               Text message = Text.literal(this.formatMessage(pos, text));
               if (this.chatSpeed.get() > 0) {
                  this.jobs.addLast(new ChatSigns.ChatJob(message, text.hashCode()));
               } else {
                  ((IChatHud)this.mc.inGameHud.getChatHud()).revive$add(message, text.hashCode());
               }
            }
         }
      }
   }

   private String formatMessage(BlockPos pos, String text) {
      String coords = this.showCoords.get() ? " [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]" : "";
      return "[Sign" + coords + "] " + text;
   }

   private String signText(SignBlockEntity sign) {
      List<String> lines = new ArrayList<>();

      for (Text line : sign.getFrontText().getMessages(false)) {
         lines.add(line.getString().trim());
      }

      for (Text line : sign.getBackText().getMessages(false)) {
         lines.add(line.getString().trim());
      }

      String joiner = this.fancyDisplay.get() ? "\n" : " / ";
      return lines.stream().filter(s -> !s.isBlank()).collect(Collectors.joining(joiner));
   }

   private boolean isBlacklisted(String text) {
      if (this.signBlacklist.get() && !this.blacklist.isEmpty()) {
         String haystack = this.caseSensitive.get() ? text : text.toLowerCase(Locale.ROOT);
         Stream<String> needles = this.blacklist
            .stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> (String)(this.caseSensitive.get() ? s : s.toLowerCase(Locale.ROOT)));
         return needles.anyMatch(haystack::contains);
      } else {
         return false;
      }
   }

   private void loadBlacklist() {
      this.blacklist.clear();
      File file = FabricLoader.getInstance().getGameDir().resolve("revive-client/chatsigns-blacklist.txt").toFile();

      try {
         if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
            return;
         }

         try (Stream<String> lines = Files.lines(file.toPath())) {
            this.blacklist.addAll(lines.toList());
         }
      } catch (Exception var7) {
         this.warning("Could not load blacklist: %s", new Object[]{var7.toString()});
      }
   }

   private BlockPos targetedSign() {
      if (this.mc.player != null && this.mc.world != null && this.mc.getCameraEntity() != null) {
         double reach = ((Integer)this.mc.options.getViewDistance().getValue()).intValue() * 16.0;
         if (this.mc.getCameraEntity().raycast(reach, 0.0F, false) instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            return this.mc.world.getBlockEntity(pos) instanceof SignBlockEntity ? pos : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private record ChatJob(Text message, int hash) {
   }

   public static enum ChatMode {
      ESP,
      Targeted,
      Both;
   }

   public static enum RepeatMode {
      Cooldown,
      Focus;
   }
}
