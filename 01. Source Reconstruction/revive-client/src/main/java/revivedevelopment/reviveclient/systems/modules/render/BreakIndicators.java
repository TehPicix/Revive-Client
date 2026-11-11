package revivedevelopment.reviveclient.systems.modules.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import revivedevelopment.reviveclient.events.packets.PacketEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.friends.Friends;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.render.color.Color;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

public class BreakIndicators extends Module {
   private static final long START_NANOS = System.nanoTime();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> useDoubleMinePrediction = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("double-mine-prediction")
            .description("Use the capped (two-per-player) prediction set instead of every tracked block.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> rebreakCompletion = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("rebreak-completion")
            .description("Completion fraction a rebreak block fills to (double-mine rebreak ~0.7).")
            .defaultValue(0.7)
            .min(0.0)
            .max(1.5)
            .sliderMax(1.5)
            .build()
      );
   private final Setting<Double> fullCompletion = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("full-completion")
            .description("Completion fraction a normal block fills to.")
            .defaultValue(1.0)
            .min(0.0)
            .max(1.5)
            .sliderMax(1.5)
            .build()
      );
   private final Setting<Double> forceRemoveCompletion = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("force-remove-completion")
            .description("Drop a tracked block once its estimated progress passes this.")
            .defaultValue(1.3)
            .min(0.0)
            .max(1.5)
            .sliderMax(1.5)
            .build()
      );
   private final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-friends").description("Don't render blocks that friends are mining.").defaultValue(false).build());
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Render the blocks opponents are mining.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the shapes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("Side color.")
            .defaultValue(new SettingColor(255, 0, 80, 10))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("Line color.")
            .defaultValue(new SettingColor(255, 255, 255, 40))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
      );
   private final Queue<BreakIndicators.Tracked> incoming = new ConcurrentLinkedQueue<>();
   private final Map<BlockPos, BreakIndicators.Tracked> raw = new HashMap<>();
   private final Map<BlockPos, BreakIndicators.Tracked> prediction = new HashMap<>();

   public BreakIndicators() {
      super(Categories.Render, "break-indicators", "Shows and tracks blocks other players are mining.");
   }

   @Override
   public void onActivate() {
      this.clear();
   }

   @Override
   public void onDeactivate() {
      this.clear();
   }

   private void clear() {
      this.incoming.clear();
      this.raw.clear();
      this.prediction.clear();
   }

   private static double currentTick() {
      return (System.nanoTime() - START_NANOS) / 5.0E7;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (this.mc.world != null) {
         if (event.packet instanceof BlockBreakingProgressS2CPacket packet) {
            Entity entity = this.mc.world.getEntityById(packet.getEntityId());
            this.incoming.add(new BreakIndicators.Tracked(packet.getPos().toImmutable(), currentTick(), entity));
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         double tick = currentTick();
         this.drainIncoming();
         this.prune(this.raw, tick);
         this.prune(this.prediction, tick);
         this.recomputeRebreak();
      }
   }

   private void drainIncoming() {
      BreakIndicators.Tracked t;
      while ((t = this.incoming.poll()) != null) {
         BreakIndicators.Tracked tf = t;
         if (tf.entity instanceof PlayerEntity) {
            List<BreakIndicators.Tracked> samePlayer = this.raw
               .values()
               .stream()
               .filter(x -> x.entity == tf.entity && !x.pos.equals(tf.pos))
               .sorted(Comparator.comparingDouble(x -> x.startTick))
               .toList();
            if (samePlayer.size() >= 2) {
               this.prediction.remove(samePlayer.get(samePlayer.size() - 1).pos);
            }
         }

         this.raw.putIfAbsent(t.pos, t);
         this.prediction.putIfAbsent(t.pos, t);
      }
   }

   private void prune(Map<BlockPos, BreakIndicators.Tracked> map, double tick) {
      Iterator<Entry<BlockPos, BreakIndicators.Tracked>> it = map.entrySet().iterator();

      while (it.hasNext()) {
         Entry<BlockPos, BreakIndicators.Tracked> entry = it.next();
         BlockPos pos = entry.getKey();
         BlockState state = this.mc.world.getBlockState(pos);
         if (state.isAir() || entry.getValue().progress(tick) > this.forceRemoveCompletion.get() || !this.isMinable(pos, state)) {
            it.remove();
         }
      }
   }

   private void recomputeRebreak() {
      for (BreakIndicators.Tracked t : this.prediction.values()) {
         t.isRebreak = false;
      }

      Map<PlayerEntity, List<BreakIndicators.Tracked>> byPlayer = new HashMap<>();

      for (BreakIndicators.Tracked t : this.prediction.values()) {
         if (t.entity instanceof PlayerEntity player) {
            byPlayer.computeIfAbsent(player, k -> new ArrayList<>()).add(t);
         }
      }

      for (List<BreakIndicators.Tracked> list : byPlayer.values()) {
         list.sort(Comparator.comparingDouble(x -> x.startTick));
         if (list.size() >= 2) {
            list.get(list.size() - 1).isRebreak = true;
         }
      }
   }

   private boolean isMinable(BlockPos pos, BlockState state) {
      return !this.mc.player.isCreative() && state.getHardness(this.mc.world, pos) < 0.0F ? false : !state.getCollisionShape(this.mc.world, pos).isEmpty();
   }

   public Map<BlockPos, BreakIndicators.TrackedBlock> getTracked() {
      Map<BlockPos, BreakIndicators.TrackedBlock> out = new HashMap<>();
      double tick = currentTick();
      Map<BlockPos, BreakIndicators.Tracked> source = this.useDoubleMinePrediction.get() ? this.prediction : this.raw;

      for (BreakIndicators.Tracked t : source.values()) {
         PlayerEntity player = t.entity instanceof PlayerEntity p ? p : null;
         out.put(t.pos, new BreakIndicators.TrackedBlock(t.pos, player, Math.min(1.0, t.progress(tick)), t.isRebreak));
      }

      return Collections.unmodifiableMap(out);
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.render.get() && this.mc.player != null && this.mc.world != null) {
         double tick = currentTick();
         Map<BlockPos, BreakIndicators.Tracked> source = this.useDoubleMinePrediction.get() ? this.prediction : this.raw;

         for (BreakIndicators.Tracked t : source.values()) {
            if (!this.ignoreFriends.get() || !(t.entity instanceof PlayerEntity p && Friends.get().isFriend(p))) {
               this.renderEntry(event, t, tick);
            }
         }
      }
   }

   private void renderEntry(Render3DEvent event, BreakIndicators.Tracked t, double tick) {
      BlockState state = this.mc.world.getBlockState(t.pos);
      VoxelShape shape = state.getOutlineShape(this.mc.world, t.pos);
      double target = t.isRebreak ? this.rebreakCompletion.get() : this.fullCompletion.get();
      double remaining = 1.0 - (target <= 0.0 ? 1.0 : MathHelper.clamp(t.progress(tick) / target, 0.0, 1.0));
      Color side = this.sideColor.get();
      Color line = this.lineColor.get();
      if (shape != null && !shape.isEmpty()) {
         Box bb = shape.getBoundingBox();
         double sx = (bb.maxX - bb.minX) * remaining / 2.0;
         double sy = (bb.maxY - bb.minY) * remaining / 2.0;
         double sz = (bb.maxZ - bb.minZ) * remaining / 2.0;
         event.renderer
            .box(
               t.pos.getX() + bb.minX + sx,
               t.pos.getY() + bb.minY + sy,
               t.pos.getZ() + bb.minZ + sz,
               t.pos.getX() + bb.maxX - sx,
               t.pos.getY() + bb.maxY - sy,
               t.pos.getZ() + bb.maxZ - sz,
               side,
               line,
               this.shapeMode.get(),
               0
            );
      } else {
         event.renderer.box(t.pos, side, line, this.shapeMode.get(), 0);
      }
   }

   private class Tracked {
      final BlockPos pos;
      final double startTick;
      final Entity entity;
      boolean isRebreak;

      Tracked(BlockPos pos, double startTick, Entity entity) {
         this.pos = pos;
         this.startTick = startTick;
         this.entity = entity;
      }

      double progress(double tick) {
         BlockState state = BreakIndicators.this.mc.world.getBlockState(this.pos);
         FindItemResult tool = InvUtils.findFastestToolHotbar(state);
         int slot = tool.found() ? tool.slot() : BreakIndicators.this.mc.player.getInventory().getSelectedSlot();
         return BlockUtils.getBreakDelta(slot, state) * (tick - this.startTick);
      }
   }

   public static class TrackedBlock {
      public final BlockPos pos;
      public final PlayerEntity player;
      public final double progress;
      public final boolean isRebreak;

      public TrackedBlock(BlockPos pos, PlayerEntity player, double progress, boolean isRebreak) {
         this.pos = pos;
         this.player = player;
         this.progress = progress;
         this.isRebreak = isRebreak;
      }
   }
}
