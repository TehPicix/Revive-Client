package revivedevelopment.reviveclient.systems.modules.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import revivedevelopment.reviveclient.ReviveClient;
import revivedevelopment.reviveclient.events.entity.player.StartBreakingBlockEvent;
import revivedevelopment.reviveclient.events.render.Render3DEvent;
import revivedevelopment.reviveclient.events.world.TickEvent;
import revivedevelopment.reviveclient.renderer.ShapeMode;
import revivedevelopment.reviveclient.settings.BlockListSetting;
import revivedevelopment.reviveclient.settings.BoolSetting;
import revivedevelopment.reviveclient.settings.ColorSetting;
import revivedevelopment.reviveclient.settings.DoubleSetting;
import revivedevelopment.reviveclient.settings.EnumSetting;
import revivedevelopment.reviveclient.settings.Setting;
import revivedevelopment.reviveclient.settings.SettingGroup;
import revivedevelopment.reviveclient.systems.config.AntiCheatConfig;
import revivedevelopment.reviveclient.systems.modules.Categories;
import revivedevelopment.reviveclient.systems.modules.Module;
import revivedevelopment.reviveclient.systems.modules.Modules;
import revivedevelopment.reviveclient.utils.player.FindItemResult;
import revivedevelopment.reviveclient.utils.player.InvUtils;
import revivedevelopment.reviveclient.utils.render.color.SettingColor;
import revivedevelopment.reviveclient.utils.world.BlockUtils;

public class Nuker extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> range = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("range")
            .description("The radius around you to break blocks in.")
            .defaultValue(4.5)
            .min(0.0)
            .max(6.0)
            .sliderRange(0.0, 6.0)
            .build()
      );
   private final Setting<Nuker.Shape> shape = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape"))
                  .description("All = a sphere around you; Flat = only at or above your feet."))
               .defaultValue(Nuker.Shape.All))
            .build()
      );
   private final Setting<Nuker.BlockFilter> blockFilter = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("filter"))
                  .description("How to choose which blocks to break."))
               .defaultValue(Nuker.BlockFilter.All))
            .build()
      );
   private final Setting<List<Block>> blocks = this.sgGeneral
      .add(
         new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to whitelist / blacklist.")
            .visible(() -> this.blockFilter.get() == Nuker.BlockFilter.Whitelist || this.blockFilter.get() == Nuker.BlockFilter.Blacklist)
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate toward the block being broken (vanilla fallback only; SilentMine handles its own rotations).")
            .defaultValue(false)
            .visible(() -> {
               SilentMine sm = Modules.get().get(SilentMine.class);
               return sm == null || !sm.isRunningHere();
            })
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(new BoolSetting.Builder().name("render").description("Render the blocks being targeted.").defaultValue(true).build());
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                     .description("How the boxes are rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("side-color")
            .description("The fill color.")
            .defaultValue(new SettingColor(255, 0, 0, 40))
            .visible(this.render::get)
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("line-color")
            .description("The outline color.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(this.render::get)
            .build()
      );
   private final List<BlockPos> targeted = new ArrayList<>();
   private Block selected = null;

   public Nuker() {
      super(Categories.Player, "nuker", "Automatically breaks the blocks around you.");
   }

   @Override
   public void onDeactivate() {
      this.targeted.clear();
      this.selected = null;
   }

   @EventHandler(
      priority = 200
   )
   private void onStartBreakingBlock(StartBreakingBlockEvent event) {
      if (this.mc.world != null && this.blockFilter.get() == Nuker.BlockFilter.Select) {
         BlockState state = this.mc.world.getBlockState(event.blockPos);
         if (!state.isAir() && state.getBlock() != this.selected) {
            this.selected = state.getBlock();
            this.info("Selected: %s", new Object[]{this.selected.getName().getString()});
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.mc.interactionManager != null) {
         this.targeted.clear();
         List<BlockPos> candidates = this.collectCandidates();
         if (!candidates.isEmpty()) {
            SilentMine sm = Modules.get().get(SilentMine.class);
            if (sm != null && sm.isRunningHere()) {
               for (BlockPos pos : candidates) {
                  if (sm.alreadyBreaking(pos) || sm.isQueued(pos)) {
                     this.targeted.add(pos);
                  } else if (sm.inBreakRange(pos) && sm.shouldAcceptBulkRequest(pos)) {
                     sm.requestBreak(pos, BlockUtils.getDirection(pos), 50.0);
                     if (sm.alreadyBreaking(pos) || sm.isQueued(pos)) {
                        this.targeted.add(pos);
                     }
                  }
               }
            } else if (sm != null && sm.isActive() && sm.isRemote() && forwardToVps(candidates.get(0))) {
               this.targeted.add(candidates.get(0));

               for (int i = 1; i < Math.min(3, candidates.size()); i++) {
                  if (forwardToVps(candidates.get(i))) {
                     this.targeted.add(candidates.get(i));
                  }
               }
            } else {
               BlockPos target = candidates.get(0);
               if (this.rotate.get() || AntiCheatConfig.get().blockRotatePlace.get()) {
                  ReviveClient.ROTATION.snapAt(Vec3d.ofCenter(target), true);
               }

               FindItemResult tool = InvUtils.findFastestToolHotbar(this.mc.world.getBlockState(target));
               if (tool.found() && this.mc.player.getInventory().selectedSlot != tool.slot()) {
                  InvUtils.swap(tool.slot(), false);
               }

               BlockUtils.breakBlock(target, true);
               this.targeted.add(target);
            }
         }
      }
   }

   private static boolean forwardToVps(BlockPos pos) {
      PingBypass pingBypass = Modules.get().get(PingBypass.class);
      return pingBypass != null && pingBypass.forwardMine(pos, BlockUtils.getDirection(pos), 50.0, false);
   }

   private List<BlockPos> collectCandidates() {
      List<BlockPos> candidates = new ArrayList<>();
      Vec3d eyes = this.mc.player.getEyePos();
      double r = this.range.get();
      double reach = Math.min(r, 6.0);
      BlockPos foot = this.mc.player.getBlockPos();
      int ri = (int)Math.ceil(reach);

      for (int x = -ri; x <= ri; x++) {
         for (int y = -ri; y <= ri; y++) {
            for (int z = -ri; z <= ri; z++) {
               if (this.shape.get() != Nuker.Shape.Flat || y >= 0) {
                  BlockPos pos = foot.add(x, y, z);
                  if (!(new Box(pos).squaredMagnitude(eyes) > reach * reach)) {
                     BlockState state = this.mc.world.getBlockState(pos);
                     if (!state.isAir() && BlockUtils.canBreak(pos, state) && this.passesFilter(state)) {
                        candidates.add(pos.toImmutable());
                     }
                  }
               }
            }
         }
      }

      candidates.sort(Comparator.comparingDouble(posx -> new Box(posx).squaredMagnitude(eyes)));
      return candidates;
   }

   private boolean passesFilter(BlockState state) {
      if (state != null && !state.isAir()) {
         return switch ((Nuker.BlockFilter)this.blockFilter.get()) {
            case All -> true;
            case Whitelist -> this.blocks.get().contains(state.getBlock());
            case Blacklist -> !this.blocks.get().contains(state.getBlock());
            case Select -> this.selected != null && state.getBlock() == this.selected;
         };
      } else {
         return false;
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.render.get() && this.mc.world != null && !this.targeted.isEmpty()) {
         for (BlockPos pos : this.targeted) {
            BlockState state = this.mc.world.getBlockState(pos);
            if (!state.isAir()) {
               VoxelShape shapeBox = state.getOutlineShape(this.mc.world, pos);
               Box box = shapeBox != null && !shapeBox.isEmpty() ? shapeBox.getBoundingBox().offset(pos.getX(), pos.getY(), pos.getZ()) : new Box(pos);
               event.renderer.box(box, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
            }
         }
      }
   }

   public static enum BlockFilter {
      All,
      Whitelist,
      Blacklist,
      Select;
   }

   public static enum Shape {
      All,
      Flat;
   }
}
